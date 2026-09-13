package now.abhi.graycie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SnoozeRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        routeRecoveryAction(
            action = intent?.action,
            resume = { ManagerController.get(context).resumeFromSnooze() },
            repost = { ManagerController.get(context).repostSnoozeRecovery() },
        )
    }
}

internal inline fun routeRecoveryAction(
    action: String?,
    resume: () -> Unit,
    repost: () -> Unit,
) {
    when (action) {
        SnoozeNotificationManager.ACTION_RESUME -> resume()
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> repost()
    }
}
