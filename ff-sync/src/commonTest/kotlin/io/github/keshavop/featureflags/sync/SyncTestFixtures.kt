package io.github.keshavop.featureflags.sync

fun flagJson(
    key: String,
    version: Long,
    on: Boolean = true,
) = """{"key":"$key","version":$version,"on":$on,"variations":[false,true],"offVariation":0,"fallthrough":{"variation":1}}"""

fun snapshotJson(vararg flags: String) =
    """{"schemaVersion":1,"version":1,"flags":{${flags.joinToString(
        ",",
    ) { "\"" + Regex("\"key\":\"([^\"]+)\"").find(it)!!.groupValues[1] + "\":" + it }}}}"""

fun sse(
    event: String,
    data: String,
) = listOf("event: $event", "data: $data", "")
