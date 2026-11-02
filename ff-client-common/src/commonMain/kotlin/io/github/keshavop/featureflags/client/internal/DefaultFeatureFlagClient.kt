package io.github.keshavop.featureflags.client.internal

import io.github.keshavop.featureflags.client.AllFlagsListener
import io.github.keshavop.featureflags.client.CachedSnapshot
import io.github.keshavop.featureflags.client.ClientConfig
import io.github.keshavop.featureflags.client.ClientPlatform
import io.github.keshavop.featureflags.client.FeatureFlagClient
import io.github.keshavop.featureflags.client.FlagChange
import io.github.keshavop.featureflags.client.FlagChangeListener
import io.github.keshavop.featureflags.client.IdentifyResult
import io.github.keshavop.featureflags.client.ListenerHandle
import io.github.keshavop.featureflags.client.PersistentCache
import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.ErrorKind
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.eval.Variations
import io.github.keshavop.featureflags.core.hooks.HookContext
import io.github.keshavop.featureflags.core.hooks.HookRunner
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import io.github.keshavop.featureflags.core.model.SnapshotCodec
import io.github.keshavop.featureflags.events.EventProcessor
import io.github.keshavop.featureflags.events.EventsConfig
import io.github.keshavop.featureflags.events.HttpEventSender
import io.github.keshavop.featureflags.events.InputEvent
import io.github.keshavop.featureflags.sync.Backoff
import io.github.keshavop.featureflags.sync.DataSourceStatus
import io.github.keshavop.featureflags.sync.ErrorInfo
import io.github.keshavop.featureflags.sync.HttpRequest
import io.github.keshavop.featureflags.sync.SseParser
import io.github.keshavop.featureflags.sync.isPermanentFailure
import io.github.keshavop.featureflags.sync.jittered
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Duration.Companion.minutes

internal class DefaultFeatureFlagClient(
    private val clientKey: String,
    initialContext: EvaluationContext,
    private val config: ClientConfig,
    private val platform: ClientPlatform,
) : FeatureFlagClient {
    /** Everything evaluations read, swapped atomically. [generation] increases with every identify. */
    private data class State(
        val context: EvaluationContext,
        val generation: Long,
        val snapshot: EvaluatedSnapshot?,
        val etag: String?,
        val fresh: Boolean,
    )

    private val logger = platform.logger
    private val clock = platform.clock
    private val scope =
        CoroutineScope(
            SupervisorJob() + platform.backgroundDispatcher +
                CoroutineExceptionHandler { _, e -> logger.error("Unexpected error in feature flag client", e) },
        )
    private val state = AtomicReference(State(initialContext, 0, config.bootstrap, null, fresh = false))
    private val cache = if (config.persistence) PersistentCache(platform.storage, clientKey, config.maxCachedContexts) else null
    private val hooks = HookRunner(config.hooks, logger)
    private val authHeaders = mapOf("Authorization" to clientKey, "User-Agent" to SdkInfo.userAgent(platform.name))
    private val offline = MutableStateFlow(config.offline)
    private val statusFlow = MutableStateFlow(DataSourceStatus(DataSourceStatus.State.INITIALIZING, clock.nowMillis()))
    private val updates = MutableSharedFlow<FlagChange>(extraBufferCapacity = UPDATE_BUFFER)
    private val keyListeners = AtomicReference(emptyList<Pair<String, FlagChangeListener>>())
    private val allListeners = AtomicReference(emptyList<AllFlagsListener>())
    private val fetchLock = Mutex()
    private val freshSignal = MutableStateFlow(0L)
    private var lastFetchMillis = Long.MIN_VALUE
    private val stopped = AtomicReference(false)
    private val transport = platform.transport?.takeIf { config.endpoints != null }
    private val events: EventProcessor? =
        if (config.events.enabled && transport != null) {
            EventProcessor(
                EventsConfig(
                    capacity = config.events.capacity,
                    flushIntervalMillis = config.events.flushInterval.inWholeMilliseconds,
                    privateAttributes = config.events.privateAttributes,
                    allAttributesPrivate = config.events.allAttributesPrivate,
                ),
                HttpEventSender(transport, config.endpoints!!.eventsUrl, authHeaders),
                scope,
                logger,
            )
        } else {
            null
        }

    override val context: EvaluationContext get() = state.load().context
    override val flagUpdates: Flow<FlagChange> = updates.asSharedFlow()
    override val status: StateFlow<DataSourceStatus> = statusFlow.asStateFlow()

    init {
        events?.record(InputEvent.Identify(clock.nowMillis(), initialContext))
        scope.launch {
            loadCached(initialContext, generation = 0)
            runUpdates()
        }
        scope.launch {
            // flush analytics when the app goes to the background, it may be killed there
            platform.lifecycle.isForeground
                .drop(1)
                .collect { foreground -> if (!foreground) events?.flush() }
        }
    }

    // ---- evaluation (synchronous, any thread) ----

    override fun boolVariation(
        key: String,
        default: Boolean,
    ): Boolean = boolVariationDetail(key, default).value

    override fun stringVariation(
        key: String,
        default: String,
    ): String = stringVariationDetail(key, default).value

    override fun intVariation(
        key: String,
        default: Int,
    ): Int = intVariationDetail(key, default).value

    override fun doubleVariation(
        key: String,
        default: Double,
    ): Double = doubleVariationDetail(key, default).value

    override fun jsonVariation(
        key: String,
        default: JsonElement,
    ): JsonElement = jsonVariationDetail(key, default).value

    override fun boolVariationDetail(
        key: String,
        default: Boolean,
    ): EvaluationDetail<Boolean> = variation(key, default, JsonPrimitive(default), Variations::bool, ::JsonPrimitive)

    override fun stringVariationDetail(
        key: String,
        default: String,
    ): EvaluationDetail<String> = variation(key, default, JsonPrimitive(default), Variations::string, ::JsonPrimitive)

    override fun intVariationDetail(
        key: String,
        default: Int,
    ): EvaluationDetail<Int> = variation(key, default, JsonPrimitive(default), Variations::int, ::JsonPrimitive)

    override fun doubleVariationDetail(
        key: String,
        default: Double,
    ): EvaluationDetail<Double> = variation(key, default, JsonPrimitive(default), Variations::double, ::JsonPrimitive)

    override fun jsonVariationDetail(
        key: String,
        default: JsonElement,
    ): EvaluationDetail<JsonElement> = variation(key, default, default, Variations::json) { it }

    override fun allFlags(): Map<String, JsonElement> =
        state
            .load()
            .snapshot
            ?.flags
            ?.mapValues { it.value.value } ?: emptyMap()

    private inline fun <T> variation(
        key: String,
        default: T,
        defaultJson: JsonElement,
        typed: (EvaluationDetail<JsonElement?>, T) -> EvaluationDetail<T>,
        toJson: (T) -> JsonElement,
    ): EvaluationDetail<T> =
        try {
            val current = state.load()
            val flag = current.snapshot?.flags?.get(key)
            val raw =
                hooks.run({ HookContext(key, current.context, defaultJson) }) {
                    when {
                        current.snapshot == null -> EvaluationDetail(null, null, EvaluationReason.error(ErrorKind.CLIENT_NOT_READY))
                        flag == null -> EvaluationDetail(null, null, EvaluationReason.error(ErrorKind.FLAG_NOT_FOUND))
                        // a service that sends no reason is reported as a fallthrough
                        else -> EvaluationDetail(flag.value, flag.variation, flag.reason?.toReason() ?: EvaluationReason.Fallthrough())
                    }
                }
            val detail = typed(raw, default)
            events?.record(
                InputEvent.Evaluation(
                    clock.nowMillis(),
                    current.context,
                    key,
                    toJson(detail.value),
                    defaultJson,
                    detail.variationIndex,
                    flag?.version,
                    detail.reason,
                    exposure = flag?.trackEvents == true,
                ),
            )
            detail
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.error("Unexpected error evaluating '$key'", e)
            EvaluationDetail(default, null, EvaluationReason.error(ErrorKind.EXCEPTION))
        }

    // ---- context ----

    override suspend fun identify(newContext: EvaluationContext): IdentifyResult {
        val generation = swapContext(newContext)
        events?.record(InputEvent.Identify(clock.nowMillis(), newContext))
        val cached = withContext(platform.backgroundDispatcher) { loadCached(newContext, generation) }
        if (state.load().generation != generation) return IdentifyResult.SUPERSEDED
        val fresh = refresh()
        return when {
            state.load().generation != generation -> IdentifyResult.SUPERSEDED
            fresh -> IdentifyResult.FRESH
            cached -> IdentifyResult.CACHED
            else -> IdentifyResult.NONE
        }
    }

    private fun swapContext(newContext: EvaluationContext): Long {
        while (true) {
            val current = state.load()
            val next = State(newContext, current.generation + 1, snapshot = null, etag = null, fresh = false)
            if (state.compareAndSet(current, next)) {
                publishChanges(current.snapshot, null)
                return next.generation
            }
        }
    }

    /** Serves the cached snapshot for [context] if nothing newer arrived meanwhile. Returns true if one was found. */
    private suspend fun loadCached(
        context: EvaluationContext,
        generation: Long,
    ): Boolean {
        val cached = cache?.load(context) ?: return false
        while (true) {
            val current = state.load()
            if (current.generation != generation || current.fresh) return true
            if (state.compareAndSet(current, current.copy(snapshot = cached.snapshot, etag = cached.etag))) {
                publishChanges(current.snapshot, cached.snapshot)
                return true
            }
        }
    }

    // ---- network ----

    private suspend fun runUpdates() {
        val endpoints = config.endpoints
        if (transport == null || endpoints == null) {
            statusFlow.value = DataSourceStatus(DataSourceStatus.State.OFF, clock.nowMillis())
            return
        }
        combine(platform.lifecycle.isForeground, platform.connectivity.isOnline, offline) { foreground, online, off ->
            foreground &&
                online &&
                !off
        }.distinctUntilChanged()
            .collectLatest { active ->
                if (!active || stopped.load()) return@collectLatest
                // returning to the foreground (or coming online) refreshes at once if the last fetch is stale
                if (clock.nowMillis() - lastFetchMillis >= config.pollInterval.inWholeMilliseconds ||
                    lastFetchMillis == Long.MIN_VALUE
                ) {
                    refresh()
                }
                if (config.streaming) {
                    streamPings(endpoints.streamUrl)
                } else {
                    while (!stopped.load()) {
                        delay(config.pollInterval.jittered())
                        refresh()
                    }
                }
            }
    }

    /** Listens for change pings and refetches on each one; reconnects with backoff, polling slowly meanwhile. */
    private suspend fun streamPings(url: String) {
        val backoff = Backoff()
        while (!stopped.load()) {
            try {
                val stream = transport!!.openStream(HttpRequest("GET", url, authHeaders + ("Accept" to "text/event-stream")))
                try {
                    if (isPermanentFailure(stream.status)) {
                        stop(stream.status)
                        return
                    }
                    if (stream.status == 200) {
                        val parser = SseParser()
                        stream.lines.collect { line ->
                            val event = parser.feed(line) ?: return@collect
                            if (event.event == "ping") {
                                backoff.reset()
                                refresh()
                            }
                        }
                    }
                } finally {
                    stream.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                logger.warn("Flag update stream failed: ${e.message}")
            }
            delay(backoff.nextDelay())
            refresh()
        }
    }

    override suspend fun refresh(): Boolean {
        val endpoints = config.endpoints ?: return false
        val http = transport ?: return false
        if (stopped.load() || offline.value) return false
        return fetchLock.withLock {
            val requested = state.load()
            lastFetchMillis = clock.nowMillis()
            try {
                val headers = requested.etag?.let { authHeaders + ("If-None-Match" to it) } ?: authHeaders
                val body = requested.context.toJson().toString()
                val response =
                    http.execute(HttpRequest("POST", endpoints.evaluateUrl, headers + ("Content-Type" to "application/json"), body))
                when {
                    response.status == 304 -> markFresh(requested, null, requested.etag)
                    response.status in 200..299 -> {
                        val snapshot = SnapshotCodec.json.decodeFromString(EvaluatedSnapshot.serializer(), response.body)
                        markFresh(requested, snapshot, response.header("ETag"))
                    }
                    isPermanentFailure(response.status) -> {
                        stop(response.status)
                        false
                    }
                    else -> {
                        error(ErrorInfo(ErrorInfo.Kind.HTTP, response.status, null, clock.nowMillis()))
                        false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                logger.warn("Fetching flags failed: ${e.message}")
                error(ErrorInfo(ErrorInfo.Kind.NETWORK, null, e.message, clock.nowMillis()))
                false
            }
        }
    }

    /** Applies a fetched snapshot ([snapshot] null = 304) unless the context changed while it was in flight. */
    private suspend fun markFresh(
        requested: State,
        snapshot: EvaluatedSnapshot?,
        etag: String?,
    ): Boolean {
        while (true) {
            val current = state.load()
            if (current.generation != requested.generation) return false
            val next = current.copy(snapshot = snapshot ?: current.snapshot ?: EvaluatedSnapshot(), etag = etag, fresh = true)
            if (state.compareAndSet(current, next)) {
                statusFlow.value = DataSourceStatus(DataSourceStatus.State.VALID, clock.nowMillis())
                if (snapshot != null) {
                    publishChanges(current.snapshot, snapshot)
                    cache?.save(current.context, CachedSnapshot(etag = etag, savedAtMillis = clock.nowMillis(), snapshot = snapshot))
                }
                freshSignal.value = requested.generation + 1
                return true
            }
        }
    }

    private fun stop(statusCode: Int) {
        logger.error("Flag requests rejected with HTTP $statusCode (check the client key); stopping updates")
        stopped.store(true)
        statusFlow.value =
            DataSourceStatus(
                DataSourceStatus.State.OFF,
                clock.nowMillis(),
                ErrorInfo(ErrorInfo.Kind.HTTP, statusCode, null, clock.nowMillis()),
            )
    }

    private fun error(info: ErrorInfo) {
        val current = statusFlow.value.state
        val next = if (current == DataSourceStatus.State.VALID) DataSourceStatus.State.INTERRUPTED else current
        statusFlow.value = DataSourceStatus(next, clock.nowMillis(), info)
    }

    override suspend fun waitForFreshValues(timeoutMillis: Long): Boolean {
        val current = state.load()
        if (current.fresh) return true
        return withTimeoutOrNull(timeoutMillis) { freshSignal.first { it == current.generation + 1 } } != null
    }

    // ---- listeners ----

    private fun publishChanges(
        old: EvaluatedSnapshot?,
        new: EvaluatedSnapshot?,
    ) {
        val oldFlags = old?.flags ?: emptyMap()
        val newFlags = new?.flags ?: emptyMap()
        val changes =
            (oldFlags.keys + newFlags.keys)
                .filter { oldFlags[it]?.value != newFlags[it]?.value }
                .sorted()
                .map { FlagChange(it, oldFlags[it]?.value, newFlags[it]?.value) }
        if (changes.isEmpty()) return
        changes.forEach { updates.tryEmit(it) }
        val byKey = keyListeners.load()
        val all = allListeners.load()
        if (byKey.isEmpty() && all.isEmpty()) return
        scope.launch(platform.listenerDispatcher) {
            for (change in changes) {
                byKey.filter { it.first == change.key }.forEach { (_, listener) -> safely { listener.onChange(change) } }
            }
            all.forEach { listener -> safely { listener.onChange(changes) } }
        }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.warn("Flag listener failed", e)
        }
    }

    override fun addListener(
        key: String,
        listener: FlagChangeListener,
    ): ListenerHandle {
        val entry = key to listener
        update(keyListeners) { it + entry }
        return ListenerHandle { update(keyListeners) { list -> list.filterNot { it === entry } } }
    }

    override fun addAllFlagsListener(listener: AllFlagsListener): ListenerHandle {
        update(allListeners) { it + listener }
        return ListenerHandle { update(allListeners) { list -> list.filterNot { it === listener } } }
    }

    private inline fun <T> update(
        ref: AtomicReference<T>,
        transform: (T) -> T,
    ) {
        while (true) {
            val current = ref.load()
            if (ref.compareAndSet(current, transform(current))) return
        }
    }

    // ---- misc ----

    override fun track(
        eventName: String,
        data: JsonElement?,
        metricValue: Double?,
    ) {
        events?.record(InputEvent.Custom(clock.nowMillis(), state.load().context, eventName, data, metricValue))
    }

    override fun setOffline(offline: Boolean) {
        this.offline.value = offline
    }

    override suspend fun flush() {
        events?.flush()
    }

    override fun close() {
        stopped.store(true)
        scope.launch {
            withTimeoutOrNull(5.minutes) { events?.close() }
            scope.cancel()
        }
    }

    private companion object {
        const val UPDATE_BUFFER = 256
    }
}
