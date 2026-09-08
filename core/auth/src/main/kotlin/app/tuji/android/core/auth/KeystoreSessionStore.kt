package app.tuji.android.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the session lives between launches.
 *
 * iOS gets this for free: supabase-swift puts the session in the Keychain, so a
 * refresh token is protected by the platform without anyone writing code. On
 * Android the equivalent has to be built, and the obvious library for it —
 * `androidx.security:security-crypto` / `EncryptedSharedPreferences` — has
 * **deprecated all of its APIs** in favour of using the Android Keystore
 * directly. So that is what this does: AES/GCM with a hardware-backed key that
 * never leaves the Keystore, over a value in the app's private prefs.
 *
 * What is being protected is the **refresh token**, not the access token. The
 * access token expires in an hour; the refresh token is the account until it is
 * revoked, and it is the reason plain SharedPreferences is not good enough on a
 * rooted device.
 *
 * Every failure path here ends at "no session", never at a crash. A Keystore key
 * can genuinely disappear — a device restore, a lock-screen change, an OS
 * upgrade gone sideways — and the correct response to an unreadable session is
 * to ask the user to sign in again, not to make the app unlaunchable.
 */
class KeystoreSessionStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionManager {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun saveSession(session: UserSession) {
        val encoded = runCatching { encrypt(json.encodeToString(session)) }.getOrNull()
        if (encoded == null) {
            // Better to hold no session than a corrupt one: a half-written
            // value would fail to decrypt on the next launch anyway, and this
            // way the failure happens now, while the user is still here.
            prefs.edit().remove(KEY_SESSION).apply()
            return
        }
        prefs.edit().putString(KEY_SESSION, encoded).apply()
    }

    /**
     * The interface declares this non-null and signals absence by throwing, so
     * the real work is in [loadSessionOrNull] and this is the throwing face of
     * it. Both are overridden because either can be the one Supabase calls.
     */
    override suspend fun loadSession(): UserSession =
        loadSessionOrNull() ?: throw NoStoredSession()

    override suspend fun loadSessionOrNull(): UserSession? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        val plain = runCatching { decrypt(stored) }.getOrNull()
        if (plain == null) {
            // The key is gone or the blob no longer matches it. Drop it rather
            // than trying again on every launch forever.
            prefs.edit().remove(KEY_SESSION).apply()
            return null
        }
        return runCatching { json.decodeFromString<UserSession>(plain) }
            .getOrElse {
                prefs.edit().remove(KEY_SESSION).apply()
                null
            }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    // Crypto

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // The IV is generated per encryption and is not secret; it is prefixed
        // rather than stored separately so the two can never drift apart.
        val iv = cipher.iv
        val out = ByteArray(1 + iv.size + body.size)
        out[0] = iv.size.toByte()
        iv.copyInto(out, 1)
        body.copyInto(out, 1 + iv.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = raw[0].toInt()
        val iv = raw.copyOfRange(1, 1 + ivSize)
        val body = raw.copyOfRange(1 + ivSize, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not `setUserAuthenticationRequired(true)`. Tuji
                // is not a banking app, and requiring a device unlock to *read a
                // session* would mean the app cannot restore state in the
                // background — including the study outbox draining after the
                // process was killed.
                .build(),
        )
        return generator.generateKey()
    }

    /** Absence, not failure. A signed-out launch reaches here every time. */
    class NoStoredSession : NoSuchElementException("No stored Supabase session")

    private companion object {
        const val PREFS = "tuji_auth"
        const val KEY_SESSION = "supabase_session"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tuji_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
