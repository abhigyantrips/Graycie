package now.abhi.graycie

import org.junit.Assert.*
import org.junit.Test

class PolicyEngineTest {
    private class Store(var saved: ManagerState = ManagerState()) : StateStore {
        override fun read() = saved
        override fun write(state: ManagerState) { saved = state }
    }
    private class Settings : GrayscaleSettings {
        var permission = true
        var throws = false
        var accepts = true
        var releaseAccepts = true
        var providerThrows = false
        var revokeOnWrite = false
        var releases = 0
        val writes = mutableListOf<Boolean>()
        override fun hasPermission() = permission
        override fun setGrayscale(enabled: Boolean): Boolean {
            if (revokeOnWrite) { permission = false; throw SecurityException() }
            if (providerThrows) throw IllegalStateException("Provider unavailable")
            if (throws) throw SecurityException()
            if (!accepts) return false
            writes.add(enabled)
            return true
        }
        override fun release(): Boolean {
            releases++
            if (throws) throw SecurityException()
            return releaseAccepts
        }
    }
    private val store = Store()
    private val settings = Settings()
    private var serviceEnabled = true
    private fun engine() = PolicyEngine("manager", store, settings, { serviceEnabled })
    private val engine = engine()
    private fun start(policy: Policy = Policy.ONLY_SELECTED) {
        engine.setSelected(setOf("selected", "also.selected"))
        engine.setPolicy(policy)
        engine.setEnabled(true)
    }

    @Test fun onlySelectedTransitionsAndTransientEvents() {
        start()
        engine.onForeground("selected", true)
        engine.onForeground("selected", true)
        engine.onForeground("keyboard", false)
        engine.onForeground("system.ui", false)
        engine.onForeground("also.selected", true)
        assertEquals(listOf(true), settings.writes)
        assertEquals("also.selected", engine.state.lastRecognisedPackage)
        engine.onForeground("launcher", true)
        engine.onForeground("selected", true)
        assertEquals(listOf(true, false, true), settings.writes)
    }

    @Test fun homeIsAlwaysGrayscaleInBothPolicies() {
        start()
        engine.onForeground("other", true)
        engine.onHome("launcher")
        engine.setPolicy(Policy.EXCEPT_SELECTED)
        assertEquals(listOf(false, true), settings.writes)
        assertEquals("launcher", engine.state.lastRecognisedPackage)
    }

    @Test fun returningFromHomeReappliesTheSameApp() {
        start()
        engine.onForeground("other", true)
        engine.onHome("launcher")
        engine.onForeground("other", true)
        assertEquals(listOf(false, true, false), settings.writes)
    }

    @Test fun selectionChangesWhileHomeIsVisibleKeepItGrayscale() {
        start(Policy.EXCEPT_SELECTED)
        engine.onHome("launcher")
        engine.setSelected(setOf("selected", "launcher"))
        assertEquals(listOf(true), settings.writes)
    }

    @Test fun exceptSelectedAlwaysKeepsManagerInColor() {
        start(Policy.EXCEPT_SELECTED)
        engine.onForeground("other", true)
        engine.onForeground("selected", true)
        engine.onForeground("other", true)
        engine.onForeground("manager", false)
        assertEquals(listOf(true, false, true, false), settings.writes)
        engine.setSelected(setOf("manager"))
        assertFalse("manager" in engine.state.selectedPackages)
    }

    @Test fun configurationChangesApplyImmediately() {
        start()
        engine.onForeground("other", true)
        engine.setSelected(setOf("other"))
        engine.setPolicy(Policy.EXCEPT_SELECTED)
        assertEquals(listOf(false, true, false), settings.writes)
    }

    @Test fun masterOffAlwaysRestoresColor() {
        start()
        engine.onForeground("selected", true)
        engine.setEnabled(false)
        engine.onForeground("other", true)
        engine.onForeground("selected", true)
        assertEquals(listOf(true), settings.writes)
        assertEquals(1, settings.releases)
        assertFalse(engine.state.managerEnabled)
    }

    @Test fun refusesEnableWithoutBothPrerequisites() {
        settings.permission = false
        engine.setEnabled(true)
        assertFalse(engine.state.managerEnabled)
        settings.permission = true
        serviceEnabled = false
        engine.setEnabled(true)
        assertFalse(engine.state.managerEnabled)
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun permissionRevokedOnDuplicateEventDisablesManagement() {
        start()
        engine.onForeground("selected", true)
        settings.permission = false
        engine.onForeground("selected", true)
        assertFalse(engine.state.managerEnabled)
        assertNull(engine.state.lastAppliedGrayscale)
        assertNotNull(engine.state.error)
        assertEquals(listOf(true), settings.writes)
    }

    @Test fun securityExceptionBetweenCheckAndWriteNeverEscapes() {
        start()
        settings.throws = true
        engine.onForeground("selected", true)
        engine.onDisconnected()
        engine.setEnabled(false)
        assertFalse(engine.state.managerEnabled)
        assertNotNull(engine.state.error)
    }

    @Test fun refusedProviderWriteDoesNotCacheSuccess() {
        start()
        settings.accepts = false
        engine.onForeground("selected", true)
        assertFalse(engine.state.managerEnabled)
        assertNull(engine.state.lastAppliedGrayscale)
        assertNotNull(engine.state.error)
        assertEquals(1, settings.releases)
    }

    @Test fun processRestartRetainsPolicyAndSelectionButWaitsForFreshEvent() {
        start(Policy.EXCEPT_SELECTED)
        engine.onForeground("other", true)
        val restarted = engine()
        restarted.onConnected()
        assertTrue(restarted.state.managerEnabled)
        assertEquals(Policy.EXCEPT_SELECTED, restarted.state.policy)
        assertEquals(setOf("selected", "also.selected"), restarted.state.selectedPackages)
        assertNull(restarted.state.lastRecognisedPackage)
        assertEquals(listOf(true), settings.writes)
        restarted.onForeground("selected", true)
        assertEquals(listOf(true, false), settings.writes)
    }

    @Test fun serviceDisconnectRestoresColorAndDisablesManagement() {
        start()
        engine.onForeground("selected", true)
        engine.onDisconnected()
        assertEquals(listOf(true), settings.writes)
        assertEquals(1, settings.releases)
        assertFalse(engine.state.managerEnabled)
    }

    @Test fun rapidTransitionsRemainOrdered() {
        start()
        repeat(100) {
            engine.onForeground("selected", true)
            engine.onForeground("other", true)
        }
        assertEquals(200, settings.writes.size)
        assertEquals(false, settings.writes.last())
    }

    @Test fun disabledConfigurationDoesNotTouchSettings() {
        engine.setSelected(setOf("selected"))
        engine.setPolicy(Policy.EXCEPT_SELECTED)
        engine.onForeground("other", true)
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun regrantClearsSetupErrorWithoutAutomaticallyReenabling() {
        start()
        settings.permission = false
        engine.checkPrerequisites()
        settings.permission = true
        engine.checkPrerequisites()
        assertNull(engine.state.error)
        assertFalse(engine.state.managerEnabled)
    }

    @Test fun colorAppKeepsManagementActiveWithoutReleasing() {
        start()
        engine.onForeground("other", true)
        assertEquals(listOf(false), settings.writes)
        assertEquals(0, settings.releases)
        assertTrue(engine.state.managerEnabled)
    }

    @Test fun reenableInColorAppReacquiresSettingsAfterRelease() {
        start()
        engine.onForeground("other", true)
        engine.setEnabled(false)
        engine.setEnabled(true)
        assertEquals(listOf(false, false), settings.writes)
        assertEquals(1, settings.releases)
    }

    @Test fun disabledStartupReleasesWithoutSelectingColorMode() {
        engine.onConnected()
        assertEquals(1, settings.releases)
        assertTrue(settings.writes.isEmpty())
        assertEquals(false, engine.state.lastAppliedGrayscale)
    }

    @Test fun missingServiceReleasesManagement() {
        start()
        engine.onForeground("selected", true)
        serviceEnabled = false
        engine.checkPrerequisites()
        assertEquals(1, settings.releases)
        assertFalse(engine.state.managerEnabled)
    }

    @Test fun failedCleanupIsReportedWithoutRetrying() {
        start()
        settings.accepts = false
        settings.releaseAccepts = false
        engine.onForeground("selected", true)
        assertEquals(1, settings.releases)
        assertTrue(engine.state.error!!.contains("could not be turned off"))
        assertNull(engine.state.lastAppliedGrayscale)
        engine.onForeground("other", true)
        assertEquals(1, settings.releases)
    }

    @Test fun failedMasterOffDoesNotRetryOrCacheColor() {
        start()
        settings.releaseAccepts = false
        engine.setEnabled(false)
        assertEquals(1, settings.releases)
        assertFalse(engine.state.managerEnabled)
        assertNull(engine.state.lastAppliedGrayscale)
        assertNotNull(engine.state.error)
    }

    @Test fun providerExceptionDisablesAndCleansUpOnce() {
        start()
        settings.providerThrows = true
        engine.onForeground("selected", true)
        assertEquals(1, settings.releases)
        assertFalse(engine.state.managerEnabled)
        assertNull(engine.state.lastAppliedGrayscale)
    }

    @Test fun permissionLostDuringWriteSkipsCleanup() {
        start()
        settings.revokeOnWrite = true
        engine.onForeground("selected", true)
        assertEquals(0, settings.releases)
        assertFalse(engine.state.managerEnabled)
        assertNull(engine.state.lastAppliedGrayscale)
        assertTrue(engine.state.error!!.contains("permission"))
    }
}
