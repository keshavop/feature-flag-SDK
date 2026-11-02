package io.github.keshavop.featureflags.sync

import io.github.keshavop.featureflags.core.log.FeatureFlagsLogger
import io.github.keshavop.featureflags.core.log.NoOpLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Server-Sent Events data source with reconnect and polling fallback (LLD §7.1, ADR-003).
 *
 * Connect → apply `put`/`patch`/`delete` → on EOF, error or malformed event reconnect after [backoff]. After
 * [fallbackAfterFailures] consecutive failed connections it polls with [fallback] for [fallbackDuration] between stream
 * attempts, so flags keep updating through proxies that break streaming. 401/403/404 stop it for good.
 */
public class StreamingDataSource(
    private val transport: HttpTransport,
    private val request: HttpRequest,
    private val apply: (SseEvent) -> Boolean,
    private val scope: CoroutineScope,
    private val fallback: PollingDataSource? = null,
    private val fallbackAfterFailures: Int = 3,
    private val fallbackDuration: Duration = 60.seconds,
    private val fallbackPollInterval: Duration = 30.seconds,
    private val backoff: Backoff = Backoff(),
    clock: EpochClock = EpochClock.SYSTEM,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val logger: FeatureFlagsLogger = NoOpLogger,
) : DataSource {
    private val holder = StatusHolder(clock)
    private var job: Job? = null

    override val status: StateFlow<DataSourceStatus> = holder.status

    override suspend fun start(): Boolean {
        if (job == null) job = scope.launch { run() }
        return holder.firstResult.await()
    }

    override fun stop() {
        job?.cancel()
        holder.off()
    }

    private suspend fun run() {
        var consecutiveFailures = 0
        while (scope.isActive) {
            val connectedAt = timeSource.markNow()
            when (val outcome = connectOnce()) {
                Outcome.Permanent -> return
                is Outcome.Ended -> consecutiveFailures = if (outcome.receivedData) 1 else consecutiveFailures + 1
            }
            backoff.connectionEnded(connectedAt.elapsedNow())
            if (fallback != null && consecutiveFailures >= fallbackAfterFailures) {
                logger.warn("Streaming failed $consecutiveFailures times in a row; polling for $fallbackDuration")
                if (pollFor(fallbackDuration)) return
            }
            delay(backoff.nextDelay())
        }
    }

    /** Returns true when polling hit a permanent failure. */
    private suspend fun pollFor(duration: Duration): Boolean {
        val until = timeSource.markNow() + duration
        while (until.hasNotPassedNow()) {
            when (fallback!!.pollOnce()) {
                PollingDataSource.PollResult.PERMANENT_FAILURE -> {
                    holder.off(fallback.status.value.lastError)
                    return true
                }
                PollingDataSource.PollResult.UPDATED, PollingDataSource.PollResult.NOT_MODIFIED -> holder.valid()
                PollingDataSource.PollResult.FAILED -> Unit
            }
            delay(fallbackPollInterval.jittered())
        }
        return false
    }

    private sealed interface Outcome {
        data object Permanent : Outcome

        data class Ended(
            val receivedData: Boolean,
        ) : Outcome
    }

    private suspend fun connectOnce(): Outcome {
        var stream: StreamResponse? = null
        var receivedData = false
        try {
            stream = transport.openStream(request)
            if (stream.status != 200) {
                if (isPermanentFailure(stream.status)) {
                    logger.error("Streaming stopped: HTTP ${stream.status} (check the SDK key)")
                    holder.permanentHttpError(stream.status)
                    return Outcome.Permanent
                }
                logger.warn("Stream connection failed with HTTP ${stream.status}")
                holder.error(ErrorInfo.Kind.HTTP, stream.status)
                return Outcome.Ended(receivedData = false)
            }
            val parser = SseParser()
            stream.lines.collect { line ->
                val event = parser.feed(line) ?: return@collect
                if (apply(event)) {
                    receivedData = true
                    backoff.reset()
                    holder.valid()
                }
            }
            holder.error(ErrorInfo.Kind.NETWORK, message = "stream closed by server")
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidPayloadException) {
            // restart the stream to get a fresh `put` instead of continuing with partial state
            logger.warn("Stream sent invalid data, reconnecting: ${e.message}")
            holder.error(ErrorInfo.Kind.INVALID_DATA, message = e.message)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.warn("Stream connection error: ${e.message}")
            holder.error(ErrorInfo.Kind.NETWORK, message = e.message)
        } finally {
            stream?.close?.invoke()
        }
        return Outcome.Ended(receivedData)
    }
}
