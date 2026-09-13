package now.abhi.graycie

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

class SnoozeNotificationManager(private val context: Context) : SnoozeNotifications {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init { runCatching { createChannel() } }

    override fun isReady(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return@runCatching false
        manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }.getOrDefault(false)

    override fun post(packageName: String): Boolean {
        if (!isReady()) return false
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)
        val launchPending = PendingIntent.getActivity(
            context,
            LAUNCH_REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val resumePending = PendingIntent.getBroadcast(
            context,
            RESUME_REQUEST_CODE,
            resumeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_graycie_a)
            .setContentTitle("Graycie is taking a break")
            .setContentText("Accessibility is off for $label. Use Resume when you’re ready.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Accessibility is off for $label. Use Resume when you’re ready to leave the app."
            ))
            .setContentIntent(launchPending)
            .addAction(R.drawable.ic_tabler_check_filled, "Resume", resumePending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    override fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Graycie recovery", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when Graycie pauses accessibility and keeps Resume available"
                setShowBadge(false)
            }
        )
    }

    companion object {
        // Channel importance cannot be raised after creation, so use a new ID to
        // migrate installs that already created the former low-importance channel.
        const val CHANNEL_ID = "snooze_recovery_active"
        const val NOTIFICATION_ID = 120
        const val ACTION_RESUME = "now.abhi.graycie.action.RESUME_SNOOZE"
        internal const val LAUNCH_REQUEST_CODE = 42
        internal const val RESUME_REQUEST_CODE = 43

        internal fun resumeIntent(context: Context): Intent =
            Intent(context, SnoozeRecoveryReceiver::class.java).setAction(ACTION_RESUME)
    }
}
