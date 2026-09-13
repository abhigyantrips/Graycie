package now.abhi.graycie

import android.content.ComponentName

/** Narrow provider boundary for this application's accessibility component only. */
interface AccessibilitySettingsAccess {
    fun hasPermission(): Boolean
    fun readString(key: String): String?
    fun writeString(key: String, value: String): Boolean
    fun writeInt(key: String, value: Int): Boolean
}

interface AccessibilityComponentControl {
    fun enableOwnService(): Boolean
    fun disableOwnService(): Boolean
    fun isOwnServiceEnabled(): Boolean
}

/**
 * Edits the live enabled-services value and never caches or reconstructs another app's
 * accessibility configuration. Unknown/malformed entries are deliberately preserved.
 */
class SecureAccessibilitySettings internal constructor(
    private val ownPackage: String,
    private val ownClass: String,
    private val access: AccessibilitySettingsAccess,
) : AccessibilityComponentControl {

    constructor(ownComponent: ComponentName, access: AccessibilitySettingsAccess) : this(
        ownComponent.packageName, ownComponent.className, access
    )

    override fun enableOwnService(): Boolean {
        requirePermission()
        val current = access.readString(ENABLED_SERVICES).orEmpty()
        entries(current)
        if (containsOwn(current)) return access.writeInt(ACCESSIBILITY_ENABLED, 1)
        val own = "$ownPackage/$ownClass"
        val next = if (current.isEmpty()) own else "$current:$own"
        return access.writeString(ENABLED_SERVICES, next) &&
            access.writeInt(ACCESSIBILITY_ENABLED, 1)
    }

    override fun disableOwnService(): Boolean {
        requirePermission()
        val current = access.readString(ENABLED_SERVICES).orEmpty()
        val next = entries(current)
            .filterNot(::isOwn)
            .joinToString(":")
        return (next == current || access.writeString(ENABLED_SERVICES, next)) && !containsOwn(
            access.readString(ENABLED_SERVICES).orEmpty()
        )
    }

    override fun isOwnServiceEnabled(): Boolean =
        containsOwn(access.readString(ENABLED_SERVICES).orEmpty())

    private fun containsOwn(value: String): Boolean = entries(value).any(::isOwn)

    private fun entries(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return value.split(':').also { entries ->
            if (entries.any { !isComponent(it) }) {
                throw IllegalStateException("Malformed enabled accessibility services value")
            }
        }
    }

    private fun isComponent(entry: String): Boolean {
        val slash = entry.indexOf('/')
        return slash > 0 && slash == entry.lastIndexOf('/') && slash < entry.lastIndex
    }

    private fun isOwn(entry: String): Boolean {
        val slash = entry.indexOf('/')
        if (slash <= 0 || slash == entry.lastIndex) return false
        val packageName = entry.substring(0, slash)
        val rawClass = entry.substring(slash + 1)
        val className = if (rawClass.startsWith('.')) packageName + rawClass else rawClass
        return packageName == ownPackage && className == ownClass
    }

    private fun requirePermission() {
        if (!access.hasPermission()) throw SecurityException("Secure settings permission missing")
    }

    companion object {
        const val ENABLED_SERVICES = "enabled_accessibility_services"
        const val ACCESSIBILITY_ENABLED = "accessibility_enabled"
    }
}
