package io.github.keshavop.featureflags.core.model

import kotlinx.serialization.json.Json

/** JSON codec for the wire formats in LLD §5. Unknown fields are ignored for forward compatibility. */
public object SnapshotCodec {
    public val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }

    public fun decode(text: String): Snapshot = json.decodeFromString(Snapshot.serializer(), text)

    public fun encode(snapshot: Snapshot): String = json.encodeToString(Snapshot.serializer(), snapshot)

    public fun decodeFlag(text: String): FlagConfig = json.decodeFromString(FlagConfig.serializer(), text)

    public fun decodeSegment(text: String): Segment = json.decodeFromString(Segment.serializer(), text)
}
