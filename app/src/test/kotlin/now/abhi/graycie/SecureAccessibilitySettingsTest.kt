package now.abhi.graycie

import org.junit.Assert.*
import org.junit.Test

class SecureAccessibilitySettingsTest {
    private class Access(var value: String? = null) : AccessibilitySettingsAccess {
        var permission = true
        var accepts = true
        val stringWrites = mutableListOf<String>()
        val intWrites = mutableListOf<Pair<String, Int>>()
        override fun hasPermission() = permission
        override fun readString(key: String) = value
        override fun writeString(key: String, value: String): Boolean {
            if (!accepts) return false
            this.value = value
            stringWrites += value
            return true
        }
        override fun writeInt(key: String, value: Int): Boolean {
            if (!accepts) return false
            intWrites += key to value
            return true
        }
    }

    private fun subject(access: Access) = SecureAccessibilitySettings(
        "now.abhi.graycie", "now.abhi.graycie.ForegroundAccessibilityService", access
    )

    @Test fun enablesFromEmptyList() {
        val access = Access(null)
        assertTrue(subject(access).enableOwnService())
        assertEquals(
            "now.abhi.graycie/now.abhi.graycie.ForegroundAccessibilityService",
            access.value,
        )
    }

    @Test fun appendPreservesOrderingAndOtherServices() {
        val access = Access("talkback/service:reader/.Service")
        assertTrue(subject(access).enableOwnService())
        assertEquals(
            "talkback/service:reader/.Service:" +
                "now.abhi.graycie/now.abhi.graycie.ForegroundAccessibilityService",
            access.value,
        )
        assertEquals(listOf(SecureAccessibilitySettings.ACCESSIBILITY_ENABLED to 1), access.intWrites)
    }

    @Test fun enableIsIdempotentForRelativeAndDuplicateOwnEntries() {
        val access = Access(
            "other/.Service:now.abhi.graycie/.ForegroundAccessibilityService:" +
                "now.abhi.graycie/now.abhi.graycie.ForegroundAccessibilityService"
        )
        assertTrue(subject(access).enableOwnService())
        assertTrue(access.stringWrites.isEmpty())
    }

    @Test fun disableRemovesOnlyOwnComponentAndPreservesEverythingElse() {
        val access = Access(
            "talkback/.TalkBack:now.abhi.graycie/.ForegroundAccessibilityService:" +
                "reader/reader.Service"
        )
        assertTrue(subject(access).disableOwnService())
        assertEquals("talkback/.TalkBack:reader/reader.Service", access.value)
        assertFalse(subject(access).isOwnServiceEnabled())
    }

    @Test fun readsCurrentValueForEveryMutation() {
        val access = Access("one/.Service")
        val subject = subject(access)
        subject.enableOwnService()
        access.value = "concurrently.changed/.Service"
        subject.enableOwnService()
        assertEquals(
            "concurrently.changed/.Service:now.abhi.graycie/now.abhi.graycie.ForegroundAccessibilityService",
            access.value,
        )
    }

    @Test fun refusalAndLostPermissionAreRecoverableResults() {
        val access = Access().apply { accepts = false }
        assertFalse(subject(access).enableOwnService())
        access.permission = false
        assertThrows(SecurityException::class.java) { subject(access).disableOwnService() }
    }

    @Test fun malformedListIsARecoverableFailureAndIsNotRewritten() {
        val access = Access("talkback/.Service:malformed::reader/.Service")
        assertThrows(IllegalStateException::class.java) { subject(access).enableOwnService() }
        assertTrue(access.stringWrites.isEmpty())
        assertEquals("talkback/.Service:malformed::reader/.Service", access.value)
    }
}
