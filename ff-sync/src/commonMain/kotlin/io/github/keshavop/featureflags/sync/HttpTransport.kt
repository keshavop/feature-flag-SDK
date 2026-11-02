package io.github.keshavop.featureflags.sync

import kotlinx.coroutines.flow.Flow

/**
 * The only I/O seam of the SDK. Each platform supplies one (OkHttp on the JVM, Ktor on mobile, `fetch` in browsers),
 * which keeps sync logic, parsing and retries in common code.
 */
public interface HttpTransport {
    public suspend fun execute(request: HttpRequest): HttpResponse

    /**
     * Opens a long-lived response and emits its body line by line (for Server-Sent Events). The flow completes when the
     * server closes the stream and throws on network errors or read timeouts.
     */
    public suspend fun openStream(request: HttpRequest): StreamResponse
}

public data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** Ask the transport to gzip [body] (events). */
    val gzip: Boolean = false,
)

public class HttpResponse(
    public val status: Int,
    public val headers: Map<String, String>,
    public val body: String,
) {
    /** Case-insensitive header lookup. */
    public fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

public class StreamResponse(
    public val status: Int,
    public val lines: Flow<String>,
    /** Closes the underlying connection; safe to call more than once. */
    public val close: () -> Unit = {},
)

/** Status codes after which retrying cannot help (bad or revoked SDK key, unknown environment). */
public fun isPermanentFailure(status: Int): Boolean = status == 401 || status == 403 || status == 404
