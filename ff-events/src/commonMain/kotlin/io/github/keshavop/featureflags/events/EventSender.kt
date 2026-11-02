package io.github.keshavop.featureflags.events

import io.github.keshavop.featureflags.core.SdkInfo
import io.github.keshavop.featureflags.sync.HttpRequest
import io.github.keshavop.featureflags.sync.HttpTransport
import kotlinx.coroutines.CancellationException

public enum class SendResult {
    SENT,

    /** Network error, 429 or 5xx: worth one retry. */
    RETRY,

    /** Rejected payload (other 4xx): drop it, keep sending later batches. */
    DROP,

    /** 401/403: the key cannot send events; stop sending. */
    DISABLE,
}

public fun interface EventSender {
    public suspend fun send(
        payload: String,
        payloadId: String,
    ): SendResult
}

/** POSTs gzipped batches to the events endpoint with an idempotency header so retries are not double counted. */
public class HttpEventSender(
    private val transport: HttpTransport,
    private val url: String,
    private val headers: Map<String, String>,
) : EventSender {
    override suspend fun send(
        payload: String,
        payloadId: String,
    ): SendResult {
        val request =
            HttpRequest(
                method = "POST",
                url = url,
                headers =
                    headers +
                        mapOf(
                            "Content-Type" to "application/json",
                            "X-SDK-Payload-Id" to payloadId,
                            "X-SDK-Version" to SdkInfo.version,
                        ),
                body = payload,
                gzip = true,
            )
        val status =
            try {
                transport.execute(request).status
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                return SendResult.RETRY
            }
        return when {
            status in 200..299 -> SendResult.SENT
            status == 401 || status == 403 -> SendResult.DISABLE
            status == 429 || status >= 500 -> SendResult.RETRY
            else -> SendResult.DROP
        }
    }
}
