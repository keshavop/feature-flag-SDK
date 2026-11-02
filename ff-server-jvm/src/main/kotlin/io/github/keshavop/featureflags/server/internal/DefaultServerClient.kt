package io.github.keshavop.featureflags.server.internal

import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.eval.ErrorKind
import io.github.keshavop.featureflags.core.eval.EvaluationDetail
import io.github.keshavop.featureflags.core.eval.EvaluationReason
import io.github.keshavop.featureflags.core.eval.FlagEvaluator
import io.github.keshavop.featureflags.core.eval.Variations
import io.github.keshavop.featureflags.core.hooks.HookContext
import io.github.keshavop.featureflags.core.hooks.HookRunner
import io.github.keshavop.featureflags.core.log.LogOnce
import io.github.keshavop.featureflags.core.model.EvaluatedFlag
import io.github.keshavop.featureflags.core.model.EvaluatedSnapshot
import io.github.keshavop.featureflags.core.model.SerializedReason
import io.github.keshavop.featureflags.core.store.InMemoryFlagStore
import io.github.keshavop.featureflags.events.EventProcessor
import io.github.keshavop.featureflags.events.EventsConfig
import io.github.keshavop.featureflags.events.HttpEventSender
import io.github.keshavop.featureflags.events.InputEvent
import io.github.keshavop.featureflags.server.DataSourceStatusProvider
import io.github.keshavop.featureflags.server.FeatureFlagServerClient
import io.github.keshavop.featureflags.server.FlagTracker
import io.github.keshavop.featureflags.server.ServerConfig
import io.github.keshavop.featureflags.server.ServerDataSource
import io.github.keshavop.featureflags.server.Slf4jLogger
import io.github.keshavop.featureflags.sync.Backoff
import io.github.keshavop.featureflags.sync.DataSource
import io.github.keshavop.featureflags.sync.DataSourceContext
import io.github.keshavop.featureflags.sync.EpochClock
import io.github.keshavop.featureflags.sync.HttpRequest
import io.github.keshavop.featureflags.sync.PollingDataSource
import io.github.keshavop.featureflags.sync.StoreUpdater
import io.github.keshavop.featureflags.sync.StreamingDataSource
import io.github.keshavop.featureflags.transport.okhttp.OkHttpTransport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration

internal class DefaultServerClient(
    sdkKey: String,
    private val config: ServerConfig,
) : FeatureFlagServerClient {
    val logger = config.logger ?: Slf4jLogger()
    private val logOnce = LogOnce()
    private val clock = EpochClock.SYSTEM
    private val closed = AtomicBoolean(false)

    // one worker thread runs sync and events; listeners get their own thread so they cannot stall updates
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(daemonThreads("feature-flags-worker"))
    private val listenerExecutor: ExecutorService = Executors.newSingleThreadExecutor(daemonThreads("feature-flags-listeners"))
    private val scope =
        CoroutineScope(
            SupervisorJob() + worker.asCoroutineDispatcher() +
                CoroutineExceptionHandler { _, e -> logger.error("Unexpected error in background task", e) },
        )

    private val evaluator = FlagEvaluator(logger)
    private val hooks = HookRunner(config.hooks, logger)
    private val tracker = DefaultFlagTracker(listenerExecutor, logger) { key, context -> evaluateRaw(key, context).value }
    private val store = InMemoryFlagStore { tracker.onChange(it) }
    private val authHeaders = mapOf("Authorization" to sdkKey)
    private val transport by lazy { config.transport ?: OkHttpTransport(SdkInfo.userAgent("jvm")) }
    private val dataSource: DataSource = createDataSource()
    private val events: EventProcessor? = createEventProcessor()

    override val dataSourceStatus: DataSourceStatusProvider = DefaultStatusProvider(dataSource.status, scope, listenerExecutor)
    override val flagTracker: FlagTracker = tracker

    /** Completes with true on first data, false on permanent failure. */
    val started: CompletableFuture<Boolean> = scope.future { dataSource.start() }

    private fun createDataSource(): DataSource {
        val context = DataSourceContext(store, scope, null, logger, clock)
        val spec = config.dataSource
        if (spec is ServerDataSource.Custom) return spec.factory.create(context)
        val endpoints =
            requireNotNull(config.endpoints) {
                "ServerConfig.endpoints is required for streaming and polling; use ServerDataSource.Custom for offline/test data"
            }
        val updater = StoreUpdater(store)
        val pollRequest = HttpRequest("GET", endpoints.snapshotUrl, authHeaders)
        return when (spec) {
            is ServerDataSource.Polling ->
                PollingDataSource(transport, pollRequest, spec.pollInterval.toKotlinDuration(), updater::applyPut, scope, clock, logger)
            is ServerDataSource.Streaming ->
                StreamingDataSource(
                    transport = transport,
                    request = HttpRequest("GET", endpoints.streamUrl, authHeaders + ("Accept" to "text/event-stream")),
                    apply = updater::apply,
                    scope = scope,
                    fallback =
                        PollingDataSource(
                            transport,
                            pollRequest,
                            spec.fallbackPollInterval.toKotlinDuration(),
                            updater::applyPut,
                            scope,
                            clock,
                            logger,
                        ),
                    fallbackPollInterval = spec.fallbackPollInterval.toKotlinDuration(),
                    backoff = Backoff(base = spec.initialReconnectDelay.toKotlinDuration()),
                    clock = clock,
                    logger = logger,
                )
            is ServerDataSource.Custom -> error("handled above")
        }
    }

    private fun createEventProcessor(): EventProcessor? {
        val endpoints = config.endpoints ?: return null
        if (!config.events.enabled) return null
        val eventsConfig =
            EventsConfig(
                capacity = config.events.capacity,
                flushIntervalMillis = config.events.flushInterval.toMillis(),
                privateAttributes = config.events.privateAttributes,
                allAttributesPrivate = config.events.allAttributesPrivate,
            )
        return EventProcessor(eventsConfig, HttpEventSender(transport, endpoints.eventsUrl, authHeaders), scope, logger)
    }

    override fun isInitialized(): Boolean = store.isInitialized

    override fun boolVariation(
        key: String,
        context: EvaluationContext,
        default: Boolean,
    ): Boolean = boolVariationDetail(key, context, default).value

    override fun stringVariation(
        key: String,
        context: EvaluationContext,
        default: String,
    ): String = stringVariationDetail(key, context, default).value

    override fun intVariation(
        key: String,
        context: EvaluationContext,
        default: Int,
    ): Int = intVariationDetail(key, context, default).value

    override fun doubleVariation(
        key: String,
        context: EvaluationContext,
        default: Double,
    ): Double = doubleVariationDetail(key, context, default).value

    override fun jsonVariation(
        key: String,
        context: EvaluationContext,
        default: JsonElement,
    ): JsonElement = jsonVariationDetail(key, context, default).value

    override fun boolVariationDetail(
        key: String,
        context: EvaluationContext,
        default: Boolean,
    ): EvaluationDetail<Boolean> = variation(key, context, default, JsonPrimitive(default), Variations::bool, ::JsonPrimitive)

    override fun stringVariationDetail(
        key: String,
        context: EvaluationContext,
        default: String,
    ): EvaluationDetail<String> = variation(key, context, default, JsonPrimitive(default), Variations::string, ::JsonPrimitive)

    override fun intVariationDetail(
        key: String,
        context: EvaluationContext,
        default: Int,
    ): EvaluationDetail<Int> = variation(key, context, default, JsonPrimitive(default), Variations::int, ::JsonPrimitive)

    override fun doubleVariationDetail(
        key: String,
        context: EvaluationContext,
        default: Double,
    ): EvaluationDetail<Double> = variation(key, context, default, JsonPrimitive(default), Variations::double, ::JsonPrimitive)

    override fun jsonVariationDetail(
        key: String,
        context: EvaluationContext,
        default: JsonElement,
    ): EvaluationDetail<JsonElement> = variation(key, context, default, default, Variations::json) { it }

    private inline fun <T> variation(
        key: String,
        context: EvaluationContext,
        default: T,
        defaultJson: JsonElement,
        typed: (EvaluationDetail<JsonElement?>, T) -> EvaluationDetail<T>,
        toJson: (T) -> JsonElement,
    ): EvaluationDetail<T> =
        try {
            val snapshot = store.snapshot()
            val raw = hooks.run({ HookContext(key, context, defaultJson) }) { evaluateRaw(key, context) }
            val detail = typed(raw, default)
            events?.let { processor ->
                val flag = snapshot.flag(key)
                val reason = detail.reason
                val inExperiment =
                    (reason is EvaluationReason.RuleMatch && reason.inExperiment) ||
                        (reason is EvaluationReason.Fallthrough && reason.inExperiment)
                processor.record(
                    InputEvent.Evaluation(
                        clock.nowMillis(),
                        context,
                        key,
                        toJson(detail.value),
                        defaultJson,
                        detail.variationIndex,
                        flag?.version,
                        reason,
                        exposure = flag?.trackEvents == true || inExperiment,
                    ),
                )
            }
            detail
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.error("Unexpected error evaluating '$key'", e)
            EvaluationDetail(default, null, EvaluationReason.error(ErrorKind.EXCEPTION))
        }

    private fun evaluateRaw(
        key: String,
        context: EvaluationContext,
    ): EvaluationDetail<JsonElement?> {
        if (!store.isInitialized) {
            logOnce.run("not-ready") { logger.warn("Evaluated '$key' before flag data arrived; serving the default") }
            return EvaluationDetail(null, null, EvaluationReason.error(ErrorKind.CLIENT_NOT_READY))
        }
        return evaluator.evaluate(store.snapshot(), key, context)
    }

    override fun allFlagsState(
        context: EvaluationContext,
        withReasons: Boolean,
    ): EvaluatedSnapshot {
        val snapshot = store.snapshot()
        val flags =
            evaluator.evaluateAll(snapshot, context).mapNotNull { (key, detail) ->
                val value = detail.value ?: return@mapNotNull null
                val flag = snapshot.flag(key)
                key to
                    EvaluatedFlag(
                        value = value,
                        variation = detail.variationIndex,
                        version = flag?.version ?: 0,
                        reason = if (withReasons) SerializedReason.of(detail.reason) else null,
                        trackEvents = flag?.trackEvents == true,
                    )
            }
        return EvaluatedSnapshot(contextHash = sha256(context.fullyQualifiedKey), flags = flags.toMap())
    }

    override fun track(
        eventName: String,
        context: EvaluationContext,
        data: JsonElement?,
        metricValue: Double?,
    ) {
        events?.record(InputEvent.Custom(clock.nowMillis(), context, eventName, data, metricValue))
    }

    override fun flush() {
        val processor = events ?: return
        runBlocking { withTimeoutOrNull(FLUSH_TIMEOUT) { processor.flush() } }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        dataSource.stop()
        events?.let { processor -> runBlocking { withTimeoutOrNull(FLUSH_TIMEOUT) { processor.close() } } }
        scope.cancel()
        worker.shutdown()
        listenerExecutor.shutdown()
    }

    private companion object {
        val FLUSH_TIMEOUT = 10_000.milliseconds

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        fun daemonThreads(name: String): ThreadFactory {
            val count = AtomicInteger()
            return ThreadFactory { runnable ->
                Thread(runnable, "$name-${count.incrementAndGet()}").apply { isDaemon = true }
            }
        }
    }
}
