package io.github.keshavop.featureflags.ios

import io.github.keshavop.featureflags.sync.HttpRequest
import io.github.keshavop.featureflags.sync.HttpResponse
import io.github.keshavop.featureflags.sync.HttpTransport
import io.github.keshavop.featureflags.sync.StreamResponse
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HttpTransport] on `NSURLSession`, so the iOS framework needs no HTTP library. Event payloads are sent uncompressed
 * (the `gzip` flag is ignored on iOS).
 */
public class UrlSessionTransport(
    private val userAgent: String,
) : HttpTransport {
    private val session =
        NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration.apply { timeoutIntervalForRequest = REQUEST_TIMEOUT_SECONDS },
        )

    override suspend fun execute(request: HttpRequest): HttpResponse =
        suspendCancellableCoroutine { continuation ->
            val task =
                session.dataTaskWithRequest(request.toNative()) { data, response, error ->
                    if (error != null) {
                        continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    } else {
                        val http = response as NSHTTPURLResponse
                        continuation.resume(HttpResponse(http.statusCode.toInt(), http.headerMap(), data?.utf8() ?: ""))
                    }
                }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    override suspend fun openStream(request: HttpRequest): StreamResponse {
        val delegate = StreamDelegate()
        val streamSession =
            NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration.apply { timeoutIntervalForRequest = STREAM_READ_TIMEOUT_SECONDS },
                delegate,
                null,
            )
        val task = streamSession.dataTaskWithRequest(request.toNative())
        task.resume()
        val status =
            try {
                delegate.status.await()
            } catch (e: IllegalStateException) {
                streamSession.invalidateAndCancel()
                throw e
            }
        val close = {
            task.cancel()
            streamSession.invalidateAndCancel()
        }
        if (status != 200) {
            close()
            return StreamResponse(status, flow { })
        }
        return StreamResponse(status, delegate.lines.consumeAsFlow(), close)
    }

    private fun HttpRequest.toNative(): NSMutableURLRequest {
        val native = NSMutableURLRequest.requestWithURL(requireNotNull(NSURL.URLWithString(url)) { "invalid URL $url" })
        native.setHTTPMethod(method)
        native.setValue(userAgent, forHTTPHeaderField = "User-Agent")
        headers.forEach { (name, value) -> native.setValue(value, forHTTPHeaderField = name) }
        body?.let { native.setHTTPBody(NSString.create(string = it).dataUsingEncoding(NSUTF8StringEncoding)) }
        return native
    }

    /** Splits the streamed body into lines as bytes arrive. */
    private class StreamDelegate :
        NSObject(),
        NSURLSessionDataDelegateProtocol {
        val status = CompletableDeferred<Int>()
        val lines = Channel<String>(Channel.UNLIMITED)
        private val pending = StringBuilder()

        @ObjCSignatureOverride
        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveResponse: NSURLResponse,
            completionHandler: (NSURLSessionResponseDisposition) -> Unit,
        ) {
            status.complete((didReceiveResponse as NSHTTPURLResponse).statusCode.toInt())
            completionHandler(NSURLSessionResponseAllow)
        }

        @ObjCSignatureOverride
        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData,
        ) {
            pending.append(didReceiveData.utf8())
            while (true) {
                val newline = pending.indexOf('\n')
                if (newline < 0) break
                lines.trySend(pending.substring(0, newline).removeSuffix("\r"))
                pending.deleteRange(0, newline + 1)
            }
        }

        @ObjCSignatureOverride
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val error = didCompleteWithError?.let { IllegalStateException(it.localizedDescription) }
            status.completeExceptionally(error ?: IllegalStateException("stream ended before a response"))
            lines.close(error)
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30.0
        const val STREAM_READ_TIMEOUT_SECONDS = 300.0
    }
}

internal fun NSData.utf8(): String = NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString() ?: ""

private fun NSHTTPURLResponse.headerMap(): Map<String, String> =
    allHeaderFields.entries.associate { (name, value) -> name.toString() to value.toString() }
