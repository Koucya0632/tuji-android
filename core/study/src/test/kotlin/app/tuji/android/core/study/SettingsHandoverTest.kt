package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHandoverTest {

    /** Agreement on both fields unless a test says otherwise, so each one
     *  names only the disagreement it is about. */
    private fun decide(
        local: String? = "zh-en",
        server: String = "zh-en",
        device: String = "zh-Hant",
        serverLanguage: String = "zh-Hant",
        done: Boolean,
    ) = SettingsHandover.decide(local, server, device, serverLanguage, done)

    /**
     * The defect this exists to prevent: onboarding wrote 中文→日文 locally and
     * nothing ever told the server, so its row still says `zh-en`. Taking the
     * server's answer would move the user off their deck on the launch after
     * they update.
     */
    @Test fun `an unsynced local choice is pushed up, not overwritten`() {
        assertEquals(SettingsHandover.PushLocal, decide(local = "zh-ja", server = "zh-en", done = false))
    }

    /**
     * The same defect, one field over. Nothing has ever asked this person what
     * language they read, and the server's row holds its own default — so a
     * Japanese phone would turn Chinese on the launch after the update.
     */
    @Test fun `a device language the server never heard is pushed up`() {
        assertEquals(
            SettingsHandover.PushLocal,
            decide(device = "ja", serverLanguage = "zh-Hant", done = false),
        )
    }

    /** Once handed over, the server is authoritative — that is what makes
     *  changing either one on another device work. */
    @Test fun `after the handover the server always wins`() {
        assertEquals(SettingsHandover.TakeServer, decide(local = "zh-ja", server = "zh-en", done = true))
        assertEquals(SettingsHandover.TakeServer, decide(local = "zh-en", server = "zh-ja", done = true))
        assertEquals(
            SettingsHandover.TakeServer,
            decide(device = "ja", serverLanguage = "zh-Hant", done = true),
        )
    }

    /**
     * A device with no local direction can still have a language to hand over.
     * The two fields are decided together but they are not the same fact, and
     * a null direction must not veto the language.
     */
    @Test fun `no local direction does not block the language handover`() {
        assertEquals(
            SettingsHandover.PushLocal,
            decide(local = null, device = "en", serverLanguage = "zh-Hant", done = false),
        )
    }

    @Test fun `a device with no local choice and a matching language takes the server's`() {
        assertEquals(SettingsHandover.TakeServer, decide(local = null, server = "zh-ja", done = false))
    }

    /** Agreement needs no handover, and pushing would be a wasted write. */
    @Test fun `agreement takes the server's`() {
        assertEquals(SettingsHandover.TakeServer, decide(local = "zh-ja", server = "zh-ja", done = false))
    }

    /** A fresh install on a Chinese phone: nothing differs, nothing to do. */
    @Test fun `a fresh install is a plain take`() {
        assertEquals(SettingsHandover.TakeServer, decide(local = null, done = false))
    }
}
