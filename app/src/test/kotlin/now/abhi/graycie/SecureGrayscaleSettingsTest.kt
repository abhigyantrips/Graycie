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
        val values = mutableMapOf<String, Int>()
        val writes = mutableListOf<Pair<String, Int>>()
        override fun hasPermission() = permission
        override fun readInt(key: String) = values[key]
        override fun writeInt(key: String, value: Int): Boolean {
            if (!permission) throw SecurityException()
            writes.add(key to value)
            if (key == refuseKey) return false
            values[key] = value
            if (revokeAfterWrite) permission = false
            return true
        }
    }
    private val access = Access()
    private val settings = SecureGrayscaleSettings(access)

    @Test fun enablesGrayscaleOnlyAfterSelectingMonochromacy() {
        assertTrue(settings.setGrayscale(true))
        assertEquals(listOf(mode to 0, enabled to 1), access.writes)
    }

    @Test fun colorDisablesCorrectionAndTransitionsSkipUnchangedWrites() {
        access.values.putAll(mapOf(mode to 0, enabled to 1))
        assertTrue(settings.setGrayscale(true))
        assertTrue(settings.setGrayscale(false))
        assertTrue(settings.setGrayscale(false))
        assertTrue(settings.setGrayscale(true))
        assertEquals(listOf(enabled to 0, enabled to 1), access.writes)
        assertEquals(1, access.values[enabled])
    }

    @Test fun reconcilesActualSettingsRatherThanTrustingLocalCache() {
        settings.setGrayscale(true)
        access.values[enabled] = 0
        access.values[mode] = 12
        access.writes.clear()
        assertTrue(settings.setGrayscale(false))
        assertTrue(access.writes.isEmpty())

        assertTrue(settings.setGrayscale(true))
        assertEquals(listOf(mode to 0, enabled to 1), access.writes)
    }

    @Test fun releaseDisablesOnceWithoutWritingMode() {
        access.values.putAll(mapOf(mode to 0, enabled to 1))
        assertTrue(settings.release())
        assertTrue(settings.release())
        assertEquals(listOf(enabled to 0), access.writes)
    }

    @Test fun refusedModeWriteDoesNotEnableFeature() {
        access.values[enabled] = 0
        access.refuseKey = mode
        assertFalse(settings.setGrayscale(true))
        assertEquals(listOf(mode to 0), access.writes)
        assertEquals(0, access.values[enabled])
    }

    @Test fun refusedEnableReportsPartialFailureAndCanRelease() {
        access.values[enabled] = 0
        access.refuseKey = enabled
        assertFalse(settings.setGrayscale(true))
        assertEquals(listOf(mode to 0, enabled to 1), access.writes)
        assertTrue(settings.release())
        assertEquals(2, access.writes.size)
    }

    @Test fun failedReleaseIsNotReportedAsSuccess() {
        access.values[enabled] = 1
        access.refuseKey = enabled
        assertFalse(settings.release())
        assertEquals(listOf(enabled to 0), access.writes)
    }

    @Test fun missingPermissionPreventsAllWritesIncludingRelease() {
        access.permission = false
        assertThrows(SecurityException::class.java) { settings.setGrayscale(true) }
        assertThrows(SecurityException::class.java) { settings.release() }
        assertTrue(access.writes.isEmpty())
    }

    @Test fun permissionRacePropagatesAfterPartialWrite() {
        access.revokeAfterWrite = true
        assertThrows(SecurityException::class.java) { settings.setGrayscale(true) }
        assertEquals(listOf(mode to 0), access.writes)
        assertNull(access.values[enabled])
    }
}
