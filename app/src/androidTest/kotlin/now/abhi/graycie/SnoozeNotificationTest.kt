package now.abhi.graycie

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SnoozeNotificationTest {
    @Test fun resumeUsesExplicitPrivateReceiverRatherThanMainActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = SnoozeNotificationManager.resumeIntent(context)

        assertEquals(SnoozeNotificationManager.ACTION_RESUME, intent.action)
        assertEquals(SnoozeRecoveryReceiver::class.java.name, intent.component?.className)
        assertNotEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test fun recoveryActionRoutesToDirectResume() {
        var resumed = 0
        var reposted = 0
        routeRecoveryAction(
            SnoozeNotificationManager.ACTION_RESUME,
            resume = { resumed++ },
            repost = { reposted++ },
        )
        assertEquals(1, resumed)
        assertEquals(0, reposted)

        routeRecoveryAction(Intent.ACTION_BOOT_COMPLETED, { resumed++ }, { reposted++ })
        assertEquals(1, reposted)
    }
}
