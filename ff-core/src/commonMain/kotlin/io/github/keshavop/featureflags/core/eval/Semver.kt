package io.github.keshavop.featureflags.core.eval

/** Minimal semantic version (`major.minor.patch[-pre][+build]`); missing minor/patch count as 0. */
internal data class Semver(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>,
) : Comparable<Semver> {
    override fun compareTo(other: Semver): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            // a release ranks above any of its pre-releases
            return other.preRelease.size.coerceAtMost(1) - preRelease.size.coerceAtMost(1)
        }
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val result = compareIdentifier(preRelease[i], other.preRelease[i])
            if (result != 0) return result
        }
        return compareValues(preRelease.size, other.preRelease.size)
    }

    private fun compareIdentifier(
        a: String,
        b: String,
    ): Int {
        val numA = a.toIntOrNull()
        val numB = b.toIntOrNull()
        return when {
            numA != null && numB != null -> compareValues(numA, numB)
            numA != null -> -1
            numB != null -> 1
            else -> a.compareTo(b)
        }
    }

    companion object {
        private val PATTERN = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(text: String): Semver? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return Semver(
                major.toIntOrNull() ?: return null,
                minor.ifEmpty { "0" }.toIntOrNull() ?: return null,
                patch.ifEmpty { "0" }.toIntOrNull() ?: return null,
                if (pre.isEmpty()) emptyList() else pre.split('.'),
            )
        }
    }
}
