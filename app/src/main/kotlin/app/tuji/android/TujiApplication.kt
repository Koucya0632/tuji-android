package app.tuji.android

import android.app.Application
import app.tuji.android.core.network.CatalogRepository
import app.tuji.android.core.network.TujiApiClient

/**
 * The object graph, hand-wired.
 *
 * No DI framework yet, deliberately. iOS reached the same conclusion in
 * ADR-0001: what the app actually needs is narrow role seams that a test can
 * substitute, and those are worth having whether or not a container exists.
 * A container added before the seams do is a container that hides their
 * absence.
 */
class TujiApplication : Application() {

    val api: TujiApiClient by lazy {
        TujiApiClient(baseUrl = BuildConfig.TUJI_BASE_URL)
    }

    val catalog: CatalogRepository by lazy { CatalogRepository(api) }
}
