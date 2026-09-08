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

    val catalog: CatalogRepository by lazy { CatalogRepository(api) }

    /**
     * The catalogue, for topping up MCQ options and for 聽句's second picture.
     *
     * A plain field rather than a store: the real `WordsStore` (invalidated by
     * a direction switch, reloaded on launch) arrives with M2's 圖鑑.
     *
     * It used to say an empty pool was "honest", because the only thing reading
     * it was the MCQ top-up and the server fills `choices` itself. **That
     * stopped being true when 聽句 started drawing its distractor picture from
     * here**: an empty pool does not degrade that question, it deletes it — and
     * it deletes it silently, by falling back to 選字, which is also what a
     * card with no sentence does. Nothing was ever assigning this, so 聽句
     * could not have appeared once. It is loaded at [loadCatalog] now.
     */
    @Volatile
    var catalogPool: List<app.tuji.android.core.model.Word> = emptyList()

    /**
     * Fill [catalogPool] once per signed-in session.
     *
     * Failure is deliberately swallowed to a log: the catalogue is a *quality*
     * input — better MCQ options, and 聽句 at all — and a review session
     * without it is degraded, not broken. Blocking the study screen on 557 rows
     * would be the worse trade.
     */
    suspend fun loadCatalog(learning: app.tuji.android.core.model.LearningDirection) {
        if (catalogPool.isNotEmpty()) return
        runCatching { catalog.words(lang = "zh-Hant", learning = learning).words }
            .onSuccess { catalogPool = it }
            .onFailure { android.util.Log.w("TujiApp", "catalogue load failed", it) }
    }

    /** Which language, and whether the intro has been seen. See the class doc
     *  for why the direction is local-only until the settings module lands. */
    val onboarding: OnboardingStore by lazy { OnboardingStore(this) }

    val study: StudyRepository by lazy { StudyRepository(api) }

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
