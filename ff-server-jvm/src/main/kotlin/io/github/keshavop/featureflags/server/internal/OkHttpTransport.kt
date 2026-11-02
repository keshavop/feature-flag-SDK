package io.github.keshavop.featureflags.server.internal

import io.github.keshavop.featureflags.sync.HttpRequest
import io.github.keshavop.featureflags.sync.HttpResponse
import io.github.keshavop.featureflags.sync.HttpTransport
import io.github.keshavop.featureflags.sync.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.GzipSink
import okio.IOException
import okio.buffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [HttpTransport] on OkHttp. Streams use a 5 minute read timeout to detect half-open connections (LLD §7.1). */
internal class OkHttpTransport(
    userAgent: String,
    base: OkHttpClient = OkHttpClient(),
) : HttpTransport {
    private val client =
        base
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", userAgent)
                        .build(),
                )
            }.build()
    private val streamClient = client.newBuilder().readTimeout(5, TimeUnit.MINUTES).build()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val call = client.newCall(request.toOkHttp())
        val response = call.await()
        return response.use { HttpResponse(it.code, it.headers.toMap(), withContext(Dispatchers.IO) { it.body.string() }) }
    }

    override suspend fun openStream(request: HttpRequest): StreamResponse {
        val call = streamClient.newCall(request.toOkHttp())
        val response = call.await()
        if (!response.isSuccessful) {
            response.close()
            return StreamResponse(response.code, flow { })
        }
        val source = response.body.source()
        val lines =
            flow {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    emit(line)
                }
            }.flowOn(Dispatchers.IO)
        return StreamResponse(response.code, lines) {
            call.cancel()
            response.close()
        }
    }

    private fun HttpRequest.toOkHttp(): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        val requestBody =
            body?.let { text ->
                val type = (headers["Content-Type"] ?: "application/json").toMediaType()
                if (gzip) {
                    builder.header("Content-Encoding", "gzip")
                    val buffer = Buffer()
                    GzipSink(buffer).buffer().use { it.writeUtf8(text) }
                    buffer.readByteArray().toRequestBody(type)
                } else {
                    text.toRequestBody(type)
                }
            }
        return builder.method(method, requestBody).build()
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) = continuation.resume(response) { _, value, _ -> value.close() }

                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) = continuation.resumeWithException(e)
                },
            )
        }
}
