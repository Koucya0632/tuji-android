package app.tuji.android.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

/**
 * Gets a Google ID token, which [AuthService] then hands to Supabase.
 *
 * The iOS counterpart wraps `GoogleSignIn-iOS`. That SDK has no Android
 * successor by that name — the current API is **Credential Manager**, and the
 * old `com.google.android.gms.auth.api.signin` is deprecated. So this is a
 * different mechanism reaching the same place: an ID token minted for the
 * project's *web* client ID, which is what Supabase's Google provider
 * validates against.
 *
 * Two things must exist outside this code or the flow fails at runtime with a
 * message that says nothing useful:
 *
 *  1. An **Android** OAuth client in GCP bound to the signing certificate's
 *     SHA-1 — one per signing key, so debug and release each need one.
 *  2. The **web** client ID passed here, also listed in Supabase's Google
 *     provider as an authorised client ID.
 *
 * Neither is a code change, and neither can be verified from a build.
 */
class GoogleCredentialBridge(private val serverClientId: String) {

    sealed interface Outcome {
        data class Token(val idToken: String, val nonce: String) : Outcome

        /** The user dismissed the sheet. Not success, and **not** an error. */
        data object Cancelled : Outcome

        data class Failed(val cause: Throwable) : Outcome
    }

    suspend fun requestIdToken(activityContext: Context): Outcome {
        if (serverClientId.isBlank()) {
            return Outcome.Failed(
                IllegalStateException("TUJI_GOOGLE_WEB_CLIENT_ID is empty — see secrets.properties")
            )
        }
        val nonce = randomNonce()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(serverClientId)
                    .setNonce(nonce)
                    .build()
            )
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = response.credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Outcome.Failed(IllegalStateException("Unexpected credential ${credential.type}"))
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            Outcome.Token(token, nonce)
        } catch (e: GetCredentialCancellationException) {
            // Backing out of the sheet must not leave a red line under the
            // button. Apple states the same fact differently: its button
            // filters cancellation out before anyone is told.
            Outcome.Cancelled
        } catch (e: NoCredentialException) {
            // No Google account on the device at all. A distinct case worth its
            // own message, but still a failure rather than a cancellation.
            Outcome.Failed(e)
        } catch (e: Throwable) {
            Outcome.Failed(e)
        }
    }

    /**
     * A nonce is sent even though the Supabase project currently has **skip
     * nonce checks ON** — a setting that exists for the iOS SDK, which cannot
     * supply one.
     *
     * It costs nothing today and buys something real later: the day anyone
     * tightens that project setting, Android keeps working instead of failing
     * with an error nobody will connect to a dashboard toggle changed weeks
     * earlier. The setting is project-level, so it is shared with iOS.
     */
    private fun randomNonce(length: Int = 32): String {
        val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) { append(charset[random.nextInt(charset.length)]) }
        }
    }
}
