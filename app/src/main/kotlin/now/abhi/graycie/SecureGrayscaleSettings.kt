package now.abhi.graycie

/** Small provider boundary so write ordering and failures can be tested without Android. */
interface SecureSettingsAccess {
    fun hasPermission(): Boolean
    fun readInt(key: String): Int?
    fun writeInt(key: String, value: Int): Boolean
}

class SecureGrayscaleSettings(private val access: SecureSettingsAccess) : GrayscaleSettings {
    override fun hasPermission() = access.hasPermission()

    override fun setGrayscale(enabled: Boolean): Boolean {
        requirePermission()
        // Some Android builds persist MODE = -1 without removing an already-applied
        // monochromacy matrix. Disabling the correction is the reliable way to restore
        // color; when enabling it, select monochromacy before flipping the switch on.
        return if (enabled) {
            putIfChanged(MODE, 0) && putIfChanged(ENABLED, 1)
        } else {
            putIfChanged(ENABLED, 0)
        }
    }

    override fun release(): Boolean {
        requirePermission()
        return putIfChanged(ENABLED, 0)
    }

    private fun requirePermission() {
        if (!hasPermission()) throw SecurityException("Secure settings permission missing")
    }

    private fun putIfChanged(key: String, value: Int): Boolean =
        access.readInt(key) == value || access.writeInt(key, value)

    companion object {
        const val MODE = "accessibility_display_daltonizer"
        const val ENABLED = "accessibility_display_daltonizer_enabled"
    }
}
