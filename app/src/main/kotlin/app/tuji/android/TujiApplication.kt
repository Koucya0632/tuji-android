package app.tuji.android

import android.app.Application
import app.tuji.android.core.auth.AuthService
import app.tuji.android.core.auth.GoogleCredentialBridge
import app.tuji.android.core.auth.SupabaseProvider
import app.tuji.android.core.network.CatalogRepository
import app.tuji.android.core.network.TujiApiClient
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * The object graph, hand-wired.
 *
 * No DI framework, deliberately. iOS reached the same conclusion in ADR-0001:
 * what the app actually needs is narrow role seams a test can substitute, and
 * those are worth having whether or not a container exists. A container added
 * before the seams do is a container that hides their absence.
 */
class TujiApplication : Application() {

    /**
     * Application-lifetime, and `SupervisorJob` on purpose: the auth session
     * collector runs here, and a failure in one child must not take down the
     * scope that every other long-lived collector shares.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val supabase: SupabaseClient by lazy {
        SupabaseProvider.create(
            context = this,
            supabaseUrl = BuildConfig.TUJI_SUPABASE_URL,
            supabaseAnonKey = BuildConfig.TUJI_SUPABASE_ANON_KEY,
        )
    }

    val auth: AuthService by lazy {
        AuthService(
            supabase = supabase,
            google = GoogleCredentialBridge(BuildConfig.TUJI_GOOGLE_WEB_CLIENT_ID),
            scope = appScope,
            // Empty until something account-scoped exists. The seam is here so
            // the store that needs it does not have to discover sign-out.
            accountScopedStores = { emptyList() },
        )
    }

    /**
     * The client takes `auth` as its token source, which is the whole reason
     * `AccessTokenProvider` is a two-method interface rather than the auth
     * service itself: the transport can be stood up in a test without Supabase.
     */
    val api: TujiApiClient by lazy {
        TujiApiClient(baseUrl = BuildConfig.TUJI_BASE_URL, tokens = auth)
    }

    val catalog: CatalogRepository by lazy { CatalogRepository(api) }
}
