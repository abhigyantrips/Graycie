package now.abhi.graycie

import org.junit.Assert.*
import org.junit.Test

class SecureGrayscaleSettingsTest {
    private val mode = SecureGrayscaleSettings.MODE
    private val enabled = SecureGrayscaleSettings.ENABLED
    private class Access : SecureSettingsAccess {
        var permission = true
        var refuseKey: String? = null
        var revokeAfterWrite = false
        var throwAfterWrite = false
        var afterWrite: (() -> Unit)? = null
        val values = mutableMapOf<String, Int>()
        val writes = mutableListOf<Pair<String, Int?>>()
        override fun hasPermission() = permission
        override fun readInt(key: String) = values[key]
        override fun writeInt(key: String, value: Int) = put(key, value)
        override fun clear(key: String) = put(key, null)
        private fun put(key: String, value: Int?): Boolean {
            if (!permission) throw SecurityException()
            writes.add(key to value)
            if (key == refuseKey) return false
            if (value == null) values.remove(key) else values[key] = value
            afterWrite?.invoke()
            if (revokeAfterWrite) permission = false
            if (throwAfterWrite) throw IllegalStateException("Interrupted after provider write")
            return true
        }
    }
    private val access = Access()
    private val sessions = TestCorrectionSessionStore()
    private fun settings() = SecureGrayscaleSettings(access, sessions)
    private val settings = settings()
    private fun correction(modeValue: Int = 12, enabledValue: Int = 1) {
        access.values.putAll(mapOf(mode to modeValue, enabled to enabledValue))
    }

    @Test fun enablesGrayscaleOnlyAfterSelectingMonochromacy() {
        assertTrue(settings.setGrayscale(true))
        assertEquals(listOf(mode to 0, enabled to 1), access.writes)
    }

    @Test fun colorDisablesCorrectionAndTransitionsSkipUnchangedWrites() {
        correction(0)
        assertTrue(settings.setGrayscale(true))
        assertTrue(settings.setGrayscale(false))
        assertTrue(settings.setGrayscale(false))
        assertTrue(settings.setGrayscale(true))
        assertEquals(listOf(enabled to 0, enabled to 1), access.writes)
    }

    @Test fun colorAppsUseEachSavedNonGrayscaleCorrectionThroughoutManagement() {
        for (chosenMode in listOf(11, 12, 13)) {
            val access = Access()
            access.values.putAll(mapOf(mode to chosenMode, enabled to 1))
            val sessions = TestCorrectionSessionStore()
            val settings = SecureGrayscaleSettings(access, sessions)
            assertTrue(settings.setGrayscale(false))
            assertTrue(access.writes.isEmpty())
            repeat(2) {
                assertTrue(settings.setGrayscale(true))
                assertEquals(mapOf(mode to 0, enabled to 1), access.values)
                assertTrue(settings.setGrayscale(false))
                assertEquals(mapOf(mode to chosenMode, enabled to 1), access.values)
                assertFalse(sessions.session!!.completed)
                access.writes.clear()
                assertTrue(settings.setGrayscale(false))
                assertTrue(access.writes.isEmpty())
            }
            assertTrue(settings.release())
            assertEquals(mapOf(mode to chosenMode, enabled to 1), access.values)
        }
    }

    @Test fun colorAppsKeepSavedCorrectionDisabledIfItWasOff() {
        correction(12, 0)
        settings.setGrayscale(true)
        assertTrue(settings.setGrayscale(false))
        assertEquals(mapOf(mode to 12, enabled to 0), access.values)
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 12, enabled to 0), access.values)
    }

    @Test fun colorAppsRestoreCorrectionWithAnAbsentModeKey() {
        access.values[enabled] = 1
        settings.setGrayscale(true)
        assertTrue(settings.setGrayscale(false))
        assertEquals(mapOf(enabled to 1), access.values)
        assertTrue(settings.release())
        assertEquals(mapOf(enabled to 1), access.values)
    }

    @Test fun colorAppsUseOriginalCorrectionAfterProcessRestart() {
        correction()
        settings.setGrayscale(true)
        val restarted = settings()
        assertTrue(restarted.setGrayscale(false))
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
        assertEquals(CorrectionValues(12, 1), sessions.session!!.original)
        assertFalse(sessions.session!!.completed)
    }

    @Test fun refusedColorModeWriteDoesNotEnableWrongCorrectionAndCanRecover() {
        correction()
        settings.setGrayscale(true)
        access.writes.clear()
        access.refuseKey = mode
        assertFalse(settings.setGrayscale(false))
        assertEquals(listOf(enabled to 0, mode to 12), access.writes)
        assertEquals(mapOf(mode to 0, enabled to 0), access.values)
        access.refuseKey = null
        assertTrue(settings().release())
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
    }

    @Test fun restoresEnabledCorrectionInSafeOrderAfterBothColorAndGrayscaleApps() {
        correction()
        assertTrue(settings.setGrayscale(false))
        assertTrue(settings.setGrayscale(true))
        assertTrue(settings.setGrayscale(false))
        assertTrue(settings.setGrayscale(true))
        access.writes.clear()
        assertTrue(settings.release())
        assertEquals(listOf(enabled to 0, mode to 12, enabled to 1), access.writes)
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
        access.writes.clear()
        assertTrue(settings.release())
        assertTrue(access.writes.isEmpty())
    }

    @Test fun restoresDisabledCorrectionModeWithoutEnablingIt() {
        correction(13, 0)
        settings.setGrayscale(true)
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 13, enabled to 0), access.values)
    }

    @Test fun preservesPreviouslyEnabledGrayscale() {
        correction(0)
        settings.setGrayscale(false)
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 0, enabled to 1), access.values)
        access.writes.clear()
        assertTrue(settings().release())
        assertTrue(access.writes.isEmpty())
    }

    @Test fun restoresAbsentKeysInsteadOfInventingDefaults() {
        settings.setGrayscale(true)
        assertTrue(settings.release())
        assertTrue(access.values.isEmpty())
        assertEquals(listOf(mode to 0, enabled to 1, enabled to 0, mode to null, enabled to null), access.writes)
    }

    @Test fun originalCorrectionSurvivesProviderRecreation() {
        correction()
        settings.setGrayscale(true)
        val restarted = settings()
        restarted.setGrayscale(false)
        restarted.setGrayscale(true)
        assertTrue(restarted.release())
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
    }

    @Test fun preservesNewerManualCorrectionOnRelease() {
        correction()
        settings.setGrayscale(true)
        correction(13)
        access.writes.clear()
        assertTrue(settings.release())
        assertTrue(access.writes.isEmpty())
        assertEquals(mapOf(mode to 13, enabled to 1), access.values)
    }

    @Test fun preservesManualDisableOnRelease() {
        correction()
        settings.setGrayscale(true)
        access.values[enabled] = 0
        access.writes.clear()
        assertTrue(settings.release())
        assertTrue(access.writes.isEmpty())
        assertEquals(mapOf(mode to 0, enabled to 0), access.values)
    }

    @Test fun manualCorrectionBecomesBaselineWhenManagementAppliesAgain() {
        correction()
        settings.setGrayscale(true)
        correction(13)
        settings.setGrayscale(false)
        settings.setGrayscale(true)
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 13, enabled to 1), access.values)
    }

    @Test fun nextManagementSessionCapturesNewBaseline() {
        correction()
        settings.setGrayscale(true)
        settings.release()
        correction(11, 0)
        settings.setGrayscale(true)
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 11, enabled to 0), access.values)
    }

    @Test fun refusedModeWriteDoesNotEnableFeatureAndOriginalCanBeReleased() {
        correction(12, 0)
        access.refuseKey = mode
        assertFalse(settings.setGrayscale(true))
        assertEquals(listOf(mode to 0), access.writes)
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 12, enabled to 0), access.values)
    }

    @Test fun refusedEnableStillRestoresPreviousMode() {
        correction(12, 0)
        access.refuseKey = enabled
        assertFalse(settings.setGrayscale(true))
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 12, enabled to 0), access.values)
    }

    @Test fun failedRestoreRetainsOriginalAndCanRetryAfterRestart() {
        correction()
        settings.setGrayscale(true)
        access.refuseKey = mode
        assertFalse(settings.release())
        assertEquals(mapOf(mode to 0, enabled to 0), access.values)
        assertEquals(CorrectionValues(12, 1), sessions.session!!.original)
        access.refuseKey = null
        assertTrue(settings().release())
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
    }

    @Test fun manualChangeAfterFailedRestoreIsPreservedOnRetry() {
        correction()
        settings.setGrayscale(true)
        access.refuseKey = mode
        assertFalse(settings.release())
        correction(13)
        access.refuseKey = null
        access.writes.clear()
        assertTrue(settings().release())
        assertTrue(access.writes.isEmpty())
        assertEquals(mapOf(mode to 13, enabled to 1), access.values)
    }

    @Test fun manualChangeBetweenRestoreStepsIsPreserved() {
        correction()
        settings.setGrayscale(true)
        access.afterWrite = { correction(13) }
        access.writes.clear()
        assertTrue(settings.release())
        assertEquals(listOf(enabled to 0), access.writes)
        assertEquals(mapOf(mode to 13, enabled to 1), access.values)
    }

    @Test fun interruptedApplyCanRestoreAfterPermissionReturns() {
        correction(12, 0)
        access.revokeAfterWrite = true
        assertThrows(SecurityException::class.java) { settings.setGrayscale(true) }
        assertEquals(listOf(mode to 0), access.writes)
        access.permission = true
        access.revokeAfterWrite = false
        assertTrue(settings().release())
        assertEquals(mapOf(mode to 12, enabled to 0), access.values)
    }

    @Test fun interruptedRestoreCanResumeFromPendingWrite() {
        correction()
        settings.setGrayscale(true)
        access.throwAfterWrite = true
        assertThrows(IllegalStateException::class.java) { settings.release() }
        assertEquals(mapOf(mode to 0, enabled to 0), access.values)
        access.throwAfterWrite = false
        assertTrue(settings().release())
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
    }

    @Test fun backupStorageFailurePreventsSecureWrites() {
        correction()
        sessions.accepts = false
        assertThrows(IllegalStateException::class.java) { settings.setGrayscale(true) }
        assertTrue(access.writes.isEmpty())
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
    }

    @Test fun legacyCleanupOnlyDisablesRecognizableGrayscale() {
        correction(0)
        assertTrue(settings.release())
        assertEquals(mapOf(mode to 0, enabled to 0), access.values)
    }

    @Test fun legacyCleanupPreservesOtherCorrectionModes() {
        correction()
        assertTrue(settings.release())
        assertTrue(access.writes.isEmpty())
        assertEquals(mapOf(mode to 12, enabled to 1), access.values)
    }

    @Test fun missingPermissionPreventsAllWritesIncludingRelease() {
        access.permission = false
        assertThrows(SecurityException::class.java) { settings.setGrayscale(true) }
        assertThrows(SecurityException::class.java) { settings.release() }
        assertTrue(access.writes.isEmpty())
    }
}
