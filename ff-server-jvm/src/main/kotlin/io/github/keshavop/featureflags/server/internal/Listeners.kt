package io.github.keshavop.featureflags.server.internal

import io.github.keshavop.featureflags.core.context.EvaluationContext
import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.core.store.ChangeSet
import io.github.keshavop.featureflags.server.DataSourceStatusListener
import io.github.keshavop.featureflags.server.DataSourceStatusProvider
import io.github.keshavop.featureflags.server.FlagChangeEvent
import io.github.keshavop.featureflags.server.FlagChangeListener
import io.github.keshavop.featureflags.server.FlagTracker
import io.github.keshavop.featureflags.server.FlagValueChangeEvent
import io.github.keshavop.featureflags.server.FlagValueChangeListener
import io.github.keshavop.featureflags.server.ListenerHandle
import io.github.keshavop.featureflags.sync.DataSourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** Delivers flag change notifications on [executor], so slow listeners never stall the data source (LLD §6). */
internal class DefaultFlagTracker(
    private val executor: Executor,
    private val logger: FeatureFlagsLogger,
    private val evaluate: (String, EvaluationContext) -> JsonElement?,
) : FlagTracker {
    private val listeners = CopyOnWriteArrayList<FlagChangeListener>()

    fun onChange(changes: ChangeSet) {
        if (listeners.isEmpty()) return
        executor.execute {
            for (key in changes.flagKeys.sorted()) {
                for (listener in listeners) {
                    runCatching { listener.onFlagChange(FlagChangeEvent(key)) }
                        .onFailure { logger.warn("Flag change listener failed", it) }
                }
            }
        }
    }

    override fun addFlagChangeListener(listener: FlagChangeListener): ListenerHandle {
        listeners += listener
        return ListenerHandle { listeners -= listener }
    }

    override fun addFlagValueChangeListener(
        flagKey: String,
        context: EvaluationContext,
        listener: FlagValueChangeListener,
    ): ListenerHandle {
        var last = evaluate(flagKey, context)
        return addFlagChangeListener { event ->
            if (event.key == flagKey) {
                val current = evaluate(flagKey, context)
                if (current != last) {
                    val old = last
                    last = current
                    listener.onFlagValueChange(FlagValueChangeEvent(flagKey, old, current))
                }
            }
        }
    }
}

internal class DefaultStatusProvider(
    private val flow: StateFlow<DataSourceStatus>,
    private val scope: CoroutineScope,
    private val executor: Executor,
) : DataSourceStatusProvider {
    override val status: DataSourceStatus get() = flow.value

    override fun addStatusListener(listener: DataSourceStatusListener): ListenerHandle {
        val job = scope.launch { flow.collect { status -> executor.execute { listener.onStatusChange(status) } } }
        return ListenerHandle { job.cancel() }
    }

    override fun waitFor(
        state: DataSourceStatus.State,
        timeout: Duration,
    ): Boolean =
        runBlocking {
            withTimeoutOrNull(timeout.toMillis()) {
                flow.first { it.state == state || it.state == DataSourceStatus.State.OFF }.state == state
            } ?: false
        }
}
