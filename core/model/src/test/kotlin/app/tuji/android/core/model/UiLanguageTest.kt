package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLanguageTest {

    @Test fun `japanese and english are their own tags`() {
        assertEquals(UiLanguage.Ja, UiLanguage.of("ja", country = "JP"))
        assertEquals(UiLanguage.En, UiLanguage.of("en", country = "US"))
    }

    @Test fun `chinese is resolved by script first`() {
        assertEquals(UiLanguage.ZhHans, UiLanguage.of("zh", script = "Hans", country = "TW"))
        assertEquals(UiLanguage.ZhHant, UiLanguage.of("zh", script = "Hant", country = "CN"))
    }

    @Test fun `without a script the region decides`() {
        // The tag alone does not say which Chinese: zh-TW and zh-HK are
        // Traditional, zh-CN and zh-SG are Simplified.
        assertEquals(UiLanguage.ZhHans, UiLanguage.of("zh", country = "CN"))
        assertEquals(UiLanguage.ZhHans, UiLanguage.of("zh", country = "SG"))
        assertEquals(UiLanguage.ZhHant, UiLanguage.of("zh", country = "TW"))
        assertEquals(UiLanguage.ZhHant, UiLanguage.of("zh", country = "HK"))
    }

    @Test fun `a bare zh is Traditional, because that is what the source is`() {
        assertEquals(UiLanguage.ZhHant, UiLanguage.of("zh"))
    }

    @Test fun `anything else is English, matching what values holds`() {
        assertEquals(UiLanguage.En, UiLanguage.of("fr", country = "FR"))
        assertEquals(UiLanguage.En, UiLanguage.of("ko"))
        assertEquals(UiLanguage.En, UiLanguage.of(null))
        assertEquals(UiLanguage.En, UiLanguage.of(""))
    }

    @Test fun `case does not matter`() {
        assertEquals(UiLanguage.Ja, UiLanguage.of("JA"))
        assertEquals(UiLanguage.ZhHans, UiLanguage.of("ZH", script = "hans"))
        assertEquals(UiLanguage.ZhHans, UiLanguage.of("zh", country = "cn"))
    }

    @Test fun `the wire values are what the server expects`() {
        assertEquals(listOf("zh-Hant", "zh-Hans", "en", "ja"), UiLanguage.entries.map { it.wire })
    }

    @Test fun `both Chinese variants say so`() {
        assertEquals(2, UiLanguage.entries.count { it.isChinese })
    }

    // The resource folders are `values-zh-rTW` / `values-zh-rCN`, so the
    // locale has to carry a region. A script-tagged `zh-Hant` matches neither
    // folder and resolves to `values/` — English for a Chinese reader.
    @Test fun `the resource locale is spelled the way the folders are`() {
        assertEquals("zh-TW", UiLanguage.ZhHant.locale.toLanguageTag())
        assertEquals("zh-CN", UiLanguage.ZhHans.locale.toLanguageTag())
        assertEquals("ja", UiLanguage.Ja.locale.toLanguageTag())
        assertEquals("en", UiLanguage.En.locale.toLanguageTag())
    }

    // Round-trips: whatever a device reports, the language it resolves to must
    // resolve back to a locale that reports the same language.
    @Test fun `every language's own locale resolves back to itself`() {
        UiLanguage.entries.forEach { language ->
            val locale = language.locale
            assertEquals(
                language,
                UiLanguage.of(locale.language, locale.script, locale.country),
            )
        }
    }
}
