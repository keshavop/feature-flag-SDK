package io.github.keshavop.featureflags.sync

/** One Server-Sent Event. Comment lines (`: heartbeat`) are not events. */
public data class SseEvent(
    val event: String,
    val data: String,
    val id: String? = null,
)

/**
 * Incremental parser for the `text/event-stream` format. Feed lines in order; an event is returned when the blank line
 * that terminates it arrives.
 */
public class SseParser {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false
    private var id: String? = null

    public fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "id" -> id = value
        }
        return null
    }

    private fun dispatch(): SseEvent? {
        val result = if (hasData) SseEvent(event ?: "message", data.toString(), id) else null
        event = null
        data.clear()
        hasData = false
        return result
    }
}
