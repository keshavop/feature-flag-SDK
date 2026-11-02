package io.github.keshavop.featureflags.client

import io.github.keshavop.featureflags.sync.HttpRequest
import io.github.keshavop.featureflags.sync.HttpResponse
import io.github.keshavop.featureflags.sync.HttpTransport
import io.github.keshavop.featureflags.sync.StreamResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow

/** Answers each request from a handler; a handler may suspend to simulate slow responses. */
class ScriptedTransport(
    var handler: suspend (HttpRequest) -> HttpResponse = { HttpResponse(500, emptyMap(), "") },
) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }

    override suspend fun openStream(request: HttpRequest): StreamResponse {
        requests += request
        return StreamResponse(503, flow { })
    }

    fun evaluations() = requests.filter { it.url.contains("/client/evaluate") }

    fun events() = requests.filter { it.url.contains("/events/bulk") }
}

fun evaluated(vararg flags: Pair<String, Any>): String =
    """{"schemaVersion":1,"flags":{${flags.joinToString(",") { (key, value) ->
        val json = if (value is String) "\"$value\"" else value.toString()
        "\"$key\":{\"value\":$json,\"variation\":1,\"version\":3,\"reason\":{\"kind\":\"RULE_MATCH\",\"ruleIndex\":0,\"ruleId\":\"r1\"}}"
    }}}}"""

fun ok(
    body: String,
    etag: String? = null,
) = HttpResponse(200, etag?.let { mapOf("ETag" to it) } ?: emptyMap(), body)

fun gate() = CompletableDeferred<Unit>()
