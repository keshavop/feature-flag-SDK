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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * Polls [request] every [interval] (±10 %) with `If-None-Match`, so unchanged data costs a 304 (LLD §7.2).
 * [apply] receives each new body and throws [InvalidPayloadException] if it cannot use it.
 */
public class PollingDataSource(
    private val transport: HttpTransport,
    private val request: HttpRequest,
    private val interval: Duration,
    private val apply: (String) -> Unit,
    private val scope: CoroutineScope,
    clock: EpochClock = EpochClock.SYSTEM,
    private val logger: FeatureFlagsLogger = NoOpLogger,
) : DataSource {
    public enum class PollResult { UPDATED, NOT_MODIFIED, FAILED, PERMANENT_FAILURE }

    private val holder = StatusHolder(clock)
    private val lock = Mutex()
    private var etag: String? = null
    private var job: Job? = null

    override val status: StateFlow<DataSourceStatus> = holder.status

    override suspend fun start(): Boolean {
        if (job == null) {
            job =
                scope.launch {
                    while (isActive) {
                        if (pollOnce() == PollResult.PERMANENT_FAILURE) break
                        delay(interval.jittered())
                    }
                }
        }
        return holder.firstResult.await()
    }

    override fun stop() {
        job?.cancel()
        holder.off()
    }

    /** One request now, e.g. when an app returns to the foreground. Concurrent calls are serialized. */
    public suspend fun pollOnce(): PollResult =
        lock.withLock {
            try {
                val headers = etag?.let { request.headers + ("If-None-Match" to it) } ?: request.headers
                val response = transport.execute(request.copy(headers = headers))
                when {
                    response.status == 304 -> {
                        holder.valid()
                        PollResult.NOT_MODIFIED
                    }
                    response.status in 200..299 -> {
                        apply(response.body)
                        etag = response.header("ETag")
                        holder.valid()
                        PollResult.UPDATED
                    }
                    isPermanentFailure(response.status) -> {
                        logger.error("Polling stopped: HTTP ${response.status} (check the SDK key)")
                        holder.permanentHttpError(response.status)
                        PollResult.PERMANENT_FAILURE
                    }
                    else -> {
                        logger.warn("Polling failed with HTTP ${response.status}")
                        holder.error(ErrorInfo.Kind.HTTP, response.status)
                        PollResult.FAILED
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidPayloadException) {
                logger.warn("Polling received invalid data: ${e.message}")
                holder.error(ErrorInfo.Kind.INVALID_DATA, message = e.message)
                PollResult.FAILED
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                logger.warn("Polling request failed: ${e.message}")
                holder.error(ErrorInfo.Kind.NETWORK, message = e.message)
                PollResult.FAILED
            }
        }
}
