package app.tuji.android.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo

/**
 * Google Play's in-app updates, as the two things the prompt needs: is there a
 * newer build, and install it.
 *
 * Play answers for *this account on this track*, which is why this needs no
 * server and no version number of our own — a closed-test build is offered to
 * the testers and to nobody else. The cost is that only a Play-installed build
 * can ask: a debug or sideloaded one gets an error, read here as "nothing
 * newer", so this path never shows itself during development.
 */
class PlayAppUpdates(context: Context) : AppUpdateLookup {

    private val manager = AppUpdateManagerFactory.create(context.applicationContext)

    override suspend fun availableVersionCode(): Int? {
        val info = manager.requestAppUpdateInfo()
        return if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            info.availableVersionCode()
        } else {
            null
        }
    }

    /**
     * 前往更新: Play's own full-screen update, which downloads, installs and
     * relaunches. If Play will not run that here, its store page is the same
     * button one step further away.
     */
    fun start(activity: Activity) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val offered = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                if (!offered || !launch(info, activity)) openStorePage(activity)
            }
            .addOnFailureListener { openStorePage(activity) }
    }

    /**
     * An update somebody started and then left is still Play's to finish; put
     * its screen back rather than leave the app running half-updated.
     */
    fun resumeIfInProgress(activity: Activity) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                launch(info, activity)
            }
        }
    }

    private fun launch(info: AppUpdateInfo, activity: Activity): Boolean = try {
        manager.startUpdateFlow(info, activity, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        true
    } catch (_: Exception) {
        false
    }

    private fun openStorePage(activity: Activity) {
        val id = activity.packageName
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")),
                )
            } catch (_: ActivityNotFoundException) {
                // No store and no browser: there is nowhere to send them.
            }
        }
    }
}
