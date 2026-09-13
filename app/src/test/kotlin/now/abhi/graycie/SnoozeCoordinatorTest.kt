package now.abhi.graycie

import org.junit.Assert.*
import org.junit.Test

class SnoozeCoordinatorTest {
    private class Store(initial: ManagerState) : StateStore {
        var state = initial
        override fun read() = state
        override fun write(state: ManagerState) { this.state = state }
    }
    private class Color(private val order: MutableList<String>) : GrayscaleSettings {
        var cleanupWorks = true
        var releases = 0
        override fun hasPermission() = true
        override fun setGrayscale(enabled: Boolean) = true
        override fun release(): Boolean { releases++; order += "color"; return cleanupWorks }
    }
    private class Notifications(private val order: MutableList<String>) : SnoozeNotifications {
        var ready = true
        var postWorks = true
        var posts = 0
        var cancels = 0
        override fun isReady() = ready
        override fun post(packageName: String): Boolean { posts++; order += "notification"; return ready && postWorks }
        override fun cancel() { cancels++ }
    }
    private class Access(private val order: MutableList<String>) : AccessibilityComponentControl {
        var enabled = true
        var accepts = true
        override fun enableOwnService(): Boolean { order += "enable"; if (accepts) enabled = true; return accepts }
        override fun disableOwnService(): Boolean { order += "disable"; if (accepts) enabled = false; return accepts }
        override fun isOwnServiceEnabled() = enabled
    }
    private class Launcher(private val order: MutableList<String>) : SnoozeAppLauncher {
        var launches = 0
        override fun launch(packageName: String): Boolean { launches++; order += "launch"; return true }
    }

    private val order = mutableListOf<String>()
    private val store = Store(ManagerState(managerEnabled = true, snoozePackages = setOf("bank")))
    private val color = Color(order)
    private val engine = PolicyEngine("manager", store, color, { true })
    private val notifications = Notifications(order)
    private val access = Access(order)
    private val launcher = Launcher(order)
    private val phases = mutableListOf<ManagerStatus>()
    private val subject = SnoozeCoordinator(engine, notifications, access, launcher, phases::add)

    @Test fun automaticSnoozeIsIdempotentAndExpectedDisconnectPreservesMaster() {
        var disables = 0
        assertTrue(subject.automatic("bank") { disables++; order += "disableSelf" })
        assertFalse(subject.automatic("bank") { disables++ })
        engine.onDisconnected()
        engine.onDisconnected()
        assertEquals(1, disables)
        assertEquals(1, notifications.posts)
        assertEquals(1, color.releases)
        assertTrue(engine.state.managerEnabled)
        assertEquals("bank", engine.state.snoozedForPackage)
        assertEquals(listOf("notification", "color", "disableSelf"), order)
    }

    @Test fun missingNotificationNeverDisablesOrPersistsSnooze() {
        notifications.ready = false
        var disabled = false
        assertFalse(subject.automatic("bank") { disabled = true })
        assertFalse(disabled)
        assertNull(engine.state.snoozedForPackage)
        assertNotNull(engine.state.error)
    }

    @Test fun notificationDeliveryFailureRollsBackBeforeDisabling() {
        notifications.postWorks = false
        var disabled = false
        assertFalse(subject.automatic("bank") { disabled = true })
        assertFalse(disabled)
        assertNull(engine.state.snoozedForPackage)
        assertTrue(engine.state.managerEnabled)
    }

    @Test fun cleanupFailureStillDisablesAndKeepsRecoveryError() {
        color.cleanupWorks = false
        assertTrue(subject.automatic("bank") { order += "disableSelf" })
        assertEquals(listOf("notification", "color", "disableSelf"), order)
        assertEquals("bank", engine.state.snoozedForPackage)
        assertTrue(engine.state.error!!.contains("Color"))
    }

    @Test fun safeLaunchDisablesAndConfirmsBeforeLaunching() {
        assertTrue(subject.openSafely("bank"))
        assertEquals(listOf("notification", "color", "disable", "launch"), order)
        assertEquals(1, launcher.launches)
    }

    @Test fun safeLaunchNeverLaunchesWhenDisableFails() {
        access.accepts = false
        assertFalse(subject.openSafely("bank"))
        assertEquals(0, launcher.launches)
        assertNull(engine.state.snoozedForPackage)
        assertEquals(1, notifications.cancels)
    }

    @Test fun resumeOnlyClearsAfterReconnect() {
        subject.automatic("bank") {}
        assertTrue(subject.resume())
        assertEquals("bank", engine.state.snoozedForPackage)
        assertTrue(subject.onServiceConnected())
        assertNull(engine.state.snoozedForPackage)
        assertEquals(1, notifications.cancels)
    }

    @Test fun resumeTimeoutRetainsSnoozeAndRecovery() {
        subject.automatic("bank") {}
        subject.resume()
        subject.resumeTimedOut()
        assertEquals("bank", engine.state.snoozedForPackage)
        assertEquals(0, notifications.cancels)
        assertTrue(engine.state.error!!.contains("did not reconnect"))
    }

    @Test fun persistedSnoozeSurvivesProcessRestartAndMissingServiceCheck() {
        subject.automatic("bank") {}
        val restarted = PolicyEngine("manager", store, color, { false })
        restarted.checkPrerequisites()
        restarted.onDisconnected()
        assertTrue(restarted.state.managerEnabled)
        assertEquals("bank", restarted.state.snoozedForPackage)
    }

    @Test fun turningMasterOffWhileSnoozedDoesNotReenableAccessibility() {
        subject.automatic("bank") {}
        order.clear()
        subject.cancelByTurningOff()
        assertFalse(engine.state.managerEnabled)
        assertNull(engine.state.snoozedForPackage)
        assertFalse("enable" in order)
    }
}
