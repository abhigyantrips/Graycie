package now.abhi.graycie

import org.junit.Assert.*
import org.junit.Test

class OffStateColorCorrectionTest {
    private class Store : StateStore {
        var state = ManagerState()
        override fun read() = state
        override fun write(state: ManagerState) { this.state = state }
    }

    private class Access : SecureSettingsAccess {
        val values = mutableMapOf(
            SecureGrayscaleSettings.MODE to 12,
            SecureGrayscaleSettings.ENABLED to 1,
        )
        val writes = mutableListOf<Pair<String, Int>>()
        override fun hasPermission() = true
        override fun readInt(key: String) = values[key]
        override fun writeInt(key: String, value: Int): Boolean {
            writes += key to value
            values[key] = value
            return true
        }
    }

    private val store = Store()
    private val access = Access()
    private fun engine() = PolicyEngine("manager", store, SecureGrayscaleSettings(access), { true })

    @Test fun existingCorrectionSurvivesOffStateConnectionsEventsAndDisconnects() {
        repeat(2) {
            val engine = engine()
            engine.onConnected()
            engine.setSelected(setOf("selected"))
            engine.setPolicy(Policy.EXCEPT_SELECTED)
            engine.onForeground("selected", true)
            engine.onForeground("manager", true)
            engine.onHome("launcher")
            engine.setEnabled(false)
            engine.onDisconnected()
            engine.onDisconnected()
        }
        assertExistingCorrectionUntouched()
    }

    @Test fun existingCorrectionSurvivesOffStateSnoozeAndRecovery() {
        val engine = engine()
        engine.setSnoozePackages(setOf("bank"))
        assertTrue(engine.prepareSnooze("bank"))
        assertTrue(engine.restoreColorForSnooze())
        engine.onDisconnected()
        val restarted = engine()
        restarted.completeResume()
        restarted.onConnected()
        assertExistingCorrectionUntouched()
    }

    @Test fun userCorrectionAfterMasterOffSurvivesLaterServiceLifecycle() {
        val engine = engine()
        engine.setSelected(setOf("selected"))
        engine.setEnabled(true)
        engine.onForeground("selected", true)
        assertEquals(0, access.values[SecureGrayscaleSettings.MODE])
        assertEquals(1, access.values[SecureGrayscaleSettings.ENABLED])
        engine.setEnabled(false)
        assertEquals(0, access.values[SecureGrayscaleSettings.ENABLED])

        access.values[SecureGrayscaleSettings.MODE] = 12
        access.values[SecureGrayscaleSettings.ENABLED] = 1
        access.writes.clear()
        engine.onDisconnected()
        engine().onConnected()
        assertExistingCorrectionUntouched()
    }

    private fun assertExistingCorrectionUntouched() {
        assertEquals(12, access.values[SecureGrayscaleSettings.MODE])
        assertEquals(1, access.values[SecureGrayscaleSettings.ENABLED])
        assertTrue(access.writes.isEmpty())
    }
}
