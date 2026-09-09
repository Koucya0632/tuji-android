package app.tuji.android

import android.app.Application
import app.tuji.android.core.auth.AuthService
import app.tuji.android.core.auth.GoogleCredentialBridge
import app.tuji.android.core.auth.SupabaseProvider
import app.tuji.android.core.network.CatalogRepository
import app.tuji.android.core.network.StudyRepository
import app.tuji.android.core.network.TujiApiClient
import app.tuji.android.core.study.ActiveAccount
import app.tuji.android.core.study.AnswerSubmitting
import app.tuji.android.core.study.DurableAnswerWriter
import app.tuji.android.core.study.StudyAnswerOutbox
import app.tuji.android.onboarding.OnboardingStore
import app.tuji.android.study.AnswerDrainWorker
import java.io.File
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

    /** The transport. [catalog] is the store that holds what it fetched. */
    val catalogReading: CatalogRepository by lazy { CatalogRepository(api) }

    /** The catalogue, once, for 圖鑑 / 搜尋 / 聽句. */
    val catalog: app.tuji.android.atlas.CatalogStore by lazy {
        app.tuji.android.atlas.CatalogStore(catalogReading)
    }

    /**
     * Every word's score, once, for the four surfaces that badge it. See
     * [app.tuji.android.atlas.MasteryStore] for why it has no TTL.
     */
    val masteryStore: app.tuji.android.atlas.MasteryStore by lazy {
        app.tuji.android.atlas.MasteryStore(study)
    }

    /** The streak, the heatmap and the per-theme rows, for 我的 and 主題. */
    val progressStore: app.tuji.android.account.ProgressStore by lazy {
        app.tuji.android.account.ProgressStore(study)
    }

    /** Which language, and whether the intro has been seen. See the class doc
     *  for why the direction is local-only until the settings module lands. */
    val onboarding: OnboardingStore by lazy { OnboardingStore(this) }

    val study: StudyRepository by lazy { StudyRepository(api) }

    /** 物見 — reading, saving, reporting and the 封鎖 list, all one client. */
    val atlas: app.tuji.android.core.network.AtlasRepository by lazy {
        app.tuji.android.core.network.AtlasRepository(api)
    }

    /** One player for the process: its clip cache and its MediaPlayer are both
     *  things there should be exactly one of. */
    val clipPlayer: app.tuji.android.study.ClipPlayer by lazy {
        app.tuji.android.study.ClipPlayer(this)
    }

    /**
     * The network primitive, as the shape `core:study` asks for.
     *
     * The adapter is one line and it is the whole reason the durability rules
     * live in a pure-JVM module: nothing there knows what HTTP is.
     */
    val answerSubmitting: AnswerSubmitting by lazy {
        AnswerSubmitting { study.submitAnswer(it) }
    }

    /**
     * Parked answers, on disk, tagged with whoever was signed in.
     *
     * `filesDir` rather than the cache directory: the system deletes a cache
     * under pressure, and these are ratings the user cannot re-enter.
     */
    val answerOutbox: StudyAnswerOutbox by lazy {
        StudyAnswerOutbox(
            file = File(filesDir, "study-answer-outbox.json"),
            account = ActiveAccount { auth.session.value.signedInUser?.id },
        )
    }

    /** Retries a few times, then parks. Never throws — parking is the fallback. */
    val answerWriter: DurableAnswerWriter by lazy {
        DurableAnswerWriter(submit = answerSubmitting, outbox = answerOutbox)
    }

    override fun onCreate() {
        super.onCreate()
        // Anything parked by a previous run goes out as soon as there is a
        // network, whether or not the user opens 複習 again.
        AnswerDrainWorker.enqueue(this)
    }
}
