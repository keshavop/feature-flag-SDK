package io.github.keshavop.featureflags.sync

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow

/** Scripted transport: each call takes the next queued response for its URL. */
class FakeTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    private val responses = mutableMapOf<String, ArrayDeque<() -> HttpResponse>>()
    private val streams = ArrayDeque<() -> StreamResponse>()

    fun respond(
        url: String,
        status: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ) {
        responses.getOrPut(url) { ArrayDeque() }.addLast { HttpResponse(status, headers, body) }
    }

    fun fail(
        url: String,
        message: String = "connection refused",
    ) {
        responses.getOrPut(url) { ArrayDeque() }.addLast { throw IllegalStateException(message) }
    }

    /** A stream that emits [lines] and then ends (server closed). */
    fun stream(
        status: Int = 200,
        lines: List<String> = emptyList(),
    ) {
        streams.addLast { StreamResponse(status, flow { lines.forEach { emit(it) } }) }
    }

    /** A stream that stays open; send lines through the returned channel, close it to end the stream. */
    fun openStream(): Channel<String> {
        val channel = Channel<String>(Channel.UNLIMITED)
        streams.addLast { StreamResponse(200, channel.consumeAsFlow()) }
        return channel
    }

    fun failStream(message: String = "connection reset") {
        streams.addLast { throw IllegalStateException(message) }
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val next = responses[request.url]?.removeFirstOrNull() ?: return HttpResponse(500, emptyMap(), "no scripted response")
        return next()
    }

    override suspend fun openStream(request: HttpRequest): StreamResponse {
        requests += request
        val next = streams.removeFirstOrNull() ?: return StreamResponse(503, flow { })
        return next()
    }
}
