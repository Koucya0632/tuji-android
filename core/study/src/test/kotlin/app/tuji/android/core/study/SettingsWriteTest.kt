package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsWriteTest {

    /**
     * The defect: the launch read failed, 設定 showed the seed values, and one
     * tap sent all of them — an empty 學習主題 included — over the account.
     */
    @Test fun `a signed-in change before the settings arrive is refused`() {
        assertEquals(SettingsWrite.Refuse, SettingsWrite.decide(signedIn = true, loaded = false))
    }

    @Test fun `a signed-in change after they arrive is applied and saved`() {
        assertEquals(SettingsWrite.ApplyAndSave, SettingsWrite.decide(signedIn = true, loaded = true))
    }

    /**
     * A guest's read never succeeds — there is no account to read — so gating
     * on it would lock a guest out of 設定 for good.
     */
    @Test fun `a guest's change is applied on the device and never saved`() {
        assertEquals(SettingsWrite.ApplyLocally, SettingsWrite.decide(signedIn = false, loaded = false))
        assertEquals(SettingsWrite.ApplyLocally, SettingsWrite.decide(signedIn = false, loaded = true))
    }
}
