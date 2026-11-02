package io.github.keshavop.featureflags.core.eval

/**
 * MurmurHash3 x86 32-bit (ADR-004), implemented in common Kotlin so every platform produces identical buckets.
 * Kotlin/JS and Kotlin/Native keep `Int` arithmetic 32-bit, so the overflowing multiplications below are portable.
 */
public object Murmur3 {
    private const val C1 = 0xcc9e2d51.toInt()
    private const val C2 = 0x1b873593

    public fun hash32x86(
        data: ByteArray,
        seed: Int = 0,
    ): Int {
        var h1 = seed
        val blocks = data.size / 4
        for (i in 0 until blocks) {
            val offset = i * 4
            val k1 =
                (data[offset].toInt() and 0xff) or
                    ((data[offset + 1].toInt() and 0xff) shl 8) or
                    ((data[offset + 2].toInt() and 0xff) shl 16) or
                    ((data[offset + 3].toInt() and 0xff) shl 24)
            h1 = h1 xor mixK1(k1)
            h1 = h1.rotateLeft(13)
            h1 = h1 * 5 + 0xe6546b64.toInt()
        }
        val tail = blocks * 4
        var k1 = 0
        val remaining = data.size and 3
        if (remaining >= 3) k1 = k1 xor ((data[tail + 2].toInt() and 0xff) shl 16)
        if (remaining >= 2) k1 = k1 xor ((data[tail + 1].toInt() and 0xff) shl 8)
        if (remaining >= 1) {
            k1 = k1 xor (data[tail].toInt() and 0xff)
            h1 = h1 xor mixK1(k1)
        }
        h1 = h1 xor data.size
        return fmix(h1)
    }

    public fun hash32x86(
        text: String,
        seed: Int = 0,
    ): Int = hash32x86(text.encodeToByteArray(), seed)

    private fun mixK1(k: Int): Int {
        var k1 = k * C1
        k1 = k1.rotateLeft(15)
        return k1 * C2
    }

    private fun fmix(h: Int): Int {
        var h1 = h
        h1 = h1 xor (h1 ushr 16)
        h1 *= 0x85ebca6b.toInt()
        h1 = h1 xor (h1 ushr 13)
        h1 *= 0xc2b2ae35.toInt()
        return h1 xor (h1 ushr 16)
    }
}
