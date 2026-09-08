package app.tuji.android.core.auth

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient

/**
 * One [SupabaseClient] for the whole app, built from the values
 * `secrets.properties` supplies at build time.
 *
 * ⚠️ The project's "dev" and "prod" Supabase refs are **the same project**. A
 * debug build on an emulator writes to real users' data. There is no staging
 * tier to hide behind, on either platform.
 */
object SupabaseProvider {

    /**
     * The redirect a browser-based OAuth flow comes back to.
     *
     * It has to be **stable across build types**, which is why it is not
     * derived from `applicationId` — debug carries a `.debug` suffix, and a
     * redirect URL that differs per build is a second entry somebody has to
     * remember to register in the Supabase dashboard. See
     * `AndroidManifest.xml`, which declares the matching intent filter.
     */
    const val REDIRECT_SCHEME = "app.tuji.android"
    const val REDIRECT_HOST = "auth-callback"

    fun create(
        context: Context,
        supabaseUrl: String,
        supabaseAnonKey: String,
    ): SupabaseClient {
        require(supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()) {
            "TUJI_SUPABASE_URL / TUJI_SUPABASE_ANON_KEY are empty. " +
                "Copy secrets.properties.example to secrets.properties and fill it in."
        }
        return createSupabaseClient(supabaseUrl, supabaseAnonKey) {
            install(Auth) {
                sessionManager = KeystoreSessionStore(context)
                scheme = REDIRECT_SCHEME
                host = REDIRECT_HOST
                // PKCE. The implicit flow puts tokens in the redirect URL, and
                // on Android a redirect URL is an Intent any app can claim.
                flowType = FlowType.PKCE
            }
        }
    }
}
