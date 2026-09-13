package app.tuji.android.core.community

/**
 * A collection's colour — iOS's `CollectionIdentityStore.colorHex`.
 *
 * The server's `avatarColor` when it is a usable one, otherwise a colour picked
 * from a fixed palette by a **stable** hash of the id, so a collection with no
 * colour of its own is at least the same colour on every screen and every
 * launch — and the same colour it is on the iPhone beside it.
 */
object CollectionIdentity {

    val palette: List<String> = listOf(
        "#557a95", "#6f6aa8", "#9b626a", "#a06d3f",
        "#4f8175", "#7d6952", "#6b789f", "#8c5f86",
        "#547f9f", "#8f7047", "#567d63", "#78689a",
    )

    fun colorHex(collectionId: String, serverColor: String?): String =
        serverColor?.let(::accepted) ?: palette[stableIndex(collectionId)]

    /**
     * A server colour is used only if it is `#rrggbb` and not near black or
     * near white: the tile is the cover behind white text, and a colour
     * extracted from a photo can be either.
     */
    internal fun accepted(raw: String): String? {
        val color = raw.lowercase()
        if (color.length != 7 || color[0] != '#') return null
        val value = color.substring(1).toLongOrNull(16) ?: return null
        val red = (value shr 16) and 0xFF
        val green = (value shr 8) and 0xFF
        val blue = value and 0xFF
        val luminance = (299 * red + 587 * green + 114 * blue) / 1000
        return if (luminance in 19..236) color else null
    }

    /** 64-bit FNV-1a over the id's UTF-8 bytes, as iOS computes it — wrapping multiply, unsigned modulo. */
    internal fun stableIndex(id: String): Int {
        var hash = -0x340d631b7bdddcdbL // 14_695_981_039_346_656_037 as a signed Long
        for (byte in id.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= 1_099_511_628_211L
        }
        return java.lang.Long.remainderUnsigned(hash, palette.size.toLong()).toInt()
    }
}

/** What 目錄's learn button offers, from how much of the collection is already being studied. */
sealed interface CollectionLearnAction {
    /** Nothing left: a dimmed ✓ 全部學習中. */
    data object AllLearning : CollectionLearnAction

    /** Some already in the queue: 加入其餘 N 個. */
    data class AddRemaining(val count: Int) : CollectionLearnAction

    /** None yet: 全部加入學習. */
    data object AddAll : CollectionLearnAction

    companion object {
        fun of(learning: Int, total: Int): CollectionLearnAction {
            val remaining = (total - learning).coerceAtLeast(0)
            return when {
                remaining == 0 -> AllLearning
                remaining < total -> AddRemaining(remaining)
                else -> AddAll
            }
        }
    }
}
