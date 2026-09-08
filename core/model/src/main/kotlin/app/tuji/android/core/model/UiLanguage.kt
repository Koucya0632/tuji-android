package app.tuji.android.core.model

/**
 * Which of the four interface languages this device is in.
 *
 * **Two different things depend on this and they are easy to conflate.** The
 * app's own strings come from Android's resource resolution and need nothing
 * here. This is for everything else:
 *
 *  - the `lang` query parameter, which decides the language the *server* writes
 *    glosses and definitions in;
 *  - the handful of places a name arrives in several languages at once and the
 *    client picks, like a 圖鑑 shelf's `name` / `nameZh`.
 *
 * Hard-coding it — which this app did until M6 — makes a Japanese phone show a
 * fully Japanese interface wrapped around Chinese content, which reads like a
 * half-finished translation rather than a missing parameter.
 */
enum class UiLanguage(val wire: String) {
    ZhHant("zh-Hant"),
    ZhHans("zh-Hans"),
    En("en"),
    Ja("ja");

    val isChinese: Boolean get() = this == ZhHant || this == ZhHans

    companion object {
        /**
         * The language for a device locale.
         *
         * Chinese is resolved by **script, then region**, because the language
         * tag alone does not say which one: `zh-TW` and `zh-HK` are Traditional,
         * `zh-CN` and `zh-SG` are Simplified, and a bare `zh` is neither. The
         * default for an unresolved Chinese is Traditional — that is the
         * language the source strings are written in.
         *
         * Anything else falls back to English, matching what `values/` holds.
         */
        fun of(language: String?, script: String? = null, country: String? = null): UiLanguage {
            return when (language?.lowercase()) {
                "ja" -> Ja
                "zh" -> when {
                    script.equals("Hans", ignoreCase = true) -> ZhHans
                    script.equals("Hant", ignoreCase = true) -> ZhHant
                    country?.uppercase() in setOf("CN", "SG") -> ZhHans
                    else -> ZhHant
                }
                else -> En
            }
        }
    }
}
