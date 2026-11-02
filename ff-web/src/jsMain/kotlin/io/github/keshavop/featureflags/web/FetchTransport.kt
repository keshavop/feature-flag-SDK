package io.github.keshavop.featureflags.web

import io.github.keshavop.featureflags.sync.HttpRequest
import io.github.keshavop.featureflags.sync.HttpResponse
import io.github.keshavop.featureflags.sync.HttpTransport
import io.github.keshavop.featureflags.sync.StreamResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.flow
import kotlin.js.Promise

/**
 * [HttpTransport] on the standard `fetch` API (browsers, Node 18+, workers) — no HTTP library in the bundle.
 * Streams read the response body incrementally instead of using `EventSource`, which cannot send an `Authorization`
 * header. POSTs use `keepalive`, so a flush started while the page is being hidden still completes.
 *
 * JavaScript errors (`TypeError` on network failure, …) are not Kotlin `Exception`s, so they are converted here to keep
 * the shared retry and error handling working.
 */
internal class FetchTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse =
        jsErrorsAsExceptions {
            val response = call(request)
            val text = (response.text() as Promise<String>).await()
            HttpResponse(response.status as Int, headersOf(response), text)
        }

    override suspend fun openStream(request: HttpRequest): StreamResponse = jsErrorsAsExceptions { stream(request) }

    private suspend fun stream(request: HttpRequest): StreamResponse {
        val response = call(request)
        val status = response.status as Int
        val body = response.body
        if (status != 200 || body == null) return StreamResponse(status, flow { })
        val reader = body.getReader()
        val decoder: dynamic = js("new TextDecoder()")
        val lines =
            flow {
                var pending = ""
                while (true) {
                    val chunk: dynamic = jsErrorsAsExceptions { (reader.read() as Promise<dynamic>).await() }
                    if (chunk.done == true) break
                    pending += decoder.decode(chunk.value, js("({ stream: true })")) as String
                    while (true) {
                        val newline = pending.indexOf('\n')
                        if (newline < 0) break
                        emit(pending.substring(0, newline).removeSuffix("\r"))
                        pending = pending.substring(newline + 1)
                    }
                }
            }
        return StreamResponse(status, lines) { reader.cancel() }
    }

    private suspend fun call(request: HttpRequest): dynamic {
        val headers: dynamic = js("({})")
        request.headers
            .filterKeys { !it.equals("User-Agent", ignoreCase = true) } // browsers forbid setting it
            .forEach { (name, value) -> headers[name] = value }
        val init: dynamic = js("({})")
        init.method = request.method
        init.headers = headers
        if (request.body != null) {
            init.body = request.body
            init.keepalive = request.method == "POST"
        }
        // must be called on globalThis: a detached `fetch` throws "Illegal invocation" in browsers
        val url = request.url
        return (js("globalThis.fetch(url, init)") as Promise<dynamic>).await()
    }

    private fun headersOf(response: dynamic): Map<String, String> {
        val result = HashMap<String, String>()
        response.headers.forEach { value: String, name: String -> result[name] = value }
        return result
    }
}

internal inline fun <T> jsErrorsAsExceptions(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e
    } catch (e: Throwable) {
        throw IllegalStateException(e.message ?: "JavaScript error", e)
    }
