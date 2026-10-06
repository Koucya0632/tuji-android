package app.tuji.android.settings

import android.content.Context
import android.content.Intent

/**
 * What 分享 App hands the share sheet: the public landing page, as on iOS,
 * until there is a store listing to point at instead.
 */
const val SHARE_APP_URL = "https://tuji.nexflow.team/"

/** The platform share sheet with just the link — iOS's `ShareLink(item:)`. */
fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, SHARE_APP_URL)
    }
    context.startActivity(Intent.createChooser(send, null))
}
