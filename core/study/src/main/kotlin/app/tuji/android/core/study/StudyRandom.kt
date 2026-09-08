package app.tuji.android.core.study

/**
 * A tiny value-type RNG so a shuffle is reproducible for a given seed.
 *
 * SplitMix64, and paired with [studyStableHash] below — which is FNV-1a rather
 * than the platform hash for the same reason iOS avoids Swift's `Hasher`:
 * **it must be stable across launches.** Anything derived from it (option
 * order, spell variant, tile scramble) would otherwise move between app runs,
 * so "the answer was the third one" would sometimes be true and sometimes not.
 *
 * The constants are the published SplitMix64 ones, but **the option order is
 * not identical to iOS's** and is not meant to be: Swift's `shuffled(using:)`
 * walks the array forwards and this walks it backwards, so the same bit stream
 * produces a different permutation. The requirement is stability *within* a
 * platform — the same card laying out the same way on every redraw and every
 * launch — which is what the seed buys. Claiming more than that would be a
 * cross-platform promise nothing checks.
 */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Uniform in `0 until bound`, by rejection so the low bits stay even. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        while (true) {
            val bits = (nextLong() ushr 1)
            val value = (bits % bound).toInt()
            if (bits - value + (bound - 1) >= 0) return value
        }
    }
}

/** Fisher–Yates against a seeded source, so the result is a function of the seed. */
fun <T> List<T>.shuffled(random: SeededRandom): List<T> {
    val out = toMutableList()
    for (i in out.indices.reversed()) {
        val j = random.nextInt(i + 1)
        val tmp = out[i]; out[i] = out[j]; out[j] = tmp
    }
    return out
}

/**
 * FNV-1a 64-bit over UTF-8 — stable across launches, unlike a platform hash.
 */
fun studyStableHash(text: String): Long {
    var hash = -0x340d631b7bdddcdbL // 0xCBF29CE484222325
    for (byte in text.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= 0x100000001B3L
    }
    return hash
}
