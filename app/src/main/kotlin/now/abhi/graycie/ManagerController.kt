package now.abhi.graycie

import android.Manifest
import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ManagerSnapshot(
    val applicationId: String,
    val permissionGranted: Boolean,
    val serviceEnabled: Boolean,
    val managerEnabled: Boolean,
    val policy: Policy,
    val selectedPackages: Set<String>,
    val lastRecognisedPackage: String?,
    val lastAppliedGrayscale: Boolean?,
    val error: String?,
    val snoozePackages: Set<String> = emptySet(),
    val snoozedForPackage: String? = null,
    val status: ManagerStatus = ManagerStatus.DISABLED,
    val notificationReady: Boolean = false,
) {
    val ready: Boolean get() = permissionGranted && serviceEnabled
    val adbCommand: String
        get() = "adb shell pm grant $applicationId android.permission.WRITE_SECURE_SETTINGS"
}

interface ManagerStateSource {
    val snapshots: StateFlow<ManagerSnapshot>
    fun setEnabled(enabled: Boolean)
    fun setPolicy(policy: Policy)
    fun setSelectedPackages(packages: Set<String>)
    fun setSnoozePackages(packages: Set<String>) = Unit
    fun openSafely(packageName: String) = Unit
    fun resumeFromSnooze() = Unit
    fun refreshPrerequisites()
}

class ManagerController private constructor(private val context: Context) : ManagerStateSource {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _snapshots = MutableStateFlow(emptySnapshot(context.packageName))
    override val snapshots: StateFlow<ManagerSnapshot> = _snapshots.asStateFlow()

    private val secureAccess = object : SecureSettingsAccess, AccessibilitySettingsAccess {
        override fun hasPermission() = context.checkSelfPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
        override fun readInt(key: String): Int? =
            Settings.Secure.getString(context.contentResolver, key)?.toIntOrNull()
        override fun writeInt(key: String, value: Int) =
            Settings.Secure.putInt(context.contentResolver, key, value)
        override fun readString(key: String): String? =
            Settings.Secure.getString(context.contentResolver, key)
        override fun writeString(key: String, value: String) =
            Settings.Secure.putString(context.contentResolver, key, value)
    }
    private val settings = SecureGrayscaleSettings(secureAccess)
    private val accessibilitySettings = SecureAccessibilitySettings(
        ComponentName(context, ForegroundAccessibilityService::class.java), secureAccess
    )
    private val notifications = SnoozeNotificationManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private var transitionStatus: ManagerStatus? = null

    val engine = PolicyEngine(context.packageName, object : StateStore {
        override fun read() = ManagerState(
            managerEnabled = preferences.getBoolean("managerEnabled", false),
            policy = runCatching {
                Policy.valueOf(preferences.getString("policy", null) ?: "ONLY_SELECTED")
            }.getOrDefault(Policy.ONLY_SELECTED),
            selectedPackages = preferences.getStringSet("selectedPackages", emptySet())!!.toSet(),
            snoozePackages = preferences.getStringSet("snoozePackages", emptySet())!!.toSet(),
            snoozedForPackage = preferences.getString("snoozedForPackage", null),
            lastRecognisedPackage = preferences.getString("lastRecognisedPackage", null),
            lastAppliedGrayscale = if (preferences.contains("lastAppliedGrayscale"))
                preferences.getBoolean("lastAppliedGrayscale", false) else null,
            error = preferences.getString("error", null),
        )

        override fun write(state: ManagerState) {
            preferences.edit().apply {
                putBoolean("managerEnabled", state.managerEnabled)
                putString("policy", state.policy.name)
                putStringSet("selectedPackages", state.selectedPackages.toSet())
                putStringSet("snoozePackages", state.snoozePackages.toSet())
                putString("snoozedForPackage", state.snoozedForPackage)
                putString("lastRecognisedPackage", state.lastRecognisedPackage)
                putString("error", state.error)
                state.lastAppliedGrayscale?.let { putBoolean("lastAppliedGrayscale", it) }
                    ?: remove("lastAppliedGrayscale")
            }.apply()
        }
    }, settings, ::isServiceEnabled, ::publishState)

    private val snooze = SnoozeCoordinator(
        engine = engine,
        notifications = notifications,
        accessibility = accessibilitySettings,
        launcher = object : SnoozeAppLauncher {
            override fun launch(packageName: String): Boolean {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
        },
        phaseChanged = {
            transitionStatus = if (it == ManagerStatus.SNOOZING || it == ManagerStatus.RESUMING) it else null
            publishState()
        },
    )

    init { publishState() }

    override fun setEnabled(enabled: Boolean) = onMain {
        if (!enabled && engine.state.snoozedForPackage != null) snooze.cancelByTurningOff()
        else engine.setEnabled(enabled)
        publishState()
    }

    override fun setPolicy(policy: Policy) = onMain {
        engine.setPolicy(policy)
        publishState()
    }

    override fun setSelectedPackages(packages: Set<String>) = onMain {
        engine.setSelected(packages - activeHomePackages())
        publishState()
    }

    override fun setSnoozePackages(packages: Set<String>) = onMain {
        val filtered = packages - activeHomePackages() - context.packageName
        if (filtered.isNotEmpty() && engine.state.snoozePackages.isEmpty() && !notifications.isReady()) {
            engine.reportError(SnoozeCoordinator.NOTIFICATION_ERROR)
        } else engine.setSnoozePackages(filtered)
        publishState()
    }

    override fun openSafely(packageName: String) = onMain {
        engine.checkPrerequisites()
        snooze.openSafely(packageName)
        publishState()
    }

    override fun resumeFromSnooze() = onMain {
        if (transitionStatus == ManagerStatus.RESUMING) return@onMain
        if (snooze.resume()) {
            handler.removeCallbacksAndMessages(RESUME_TOKEN)
            handler.postAtTime({
                snooze.resumeTimedOut()
                publishState()
            }, RESUME_TOKEN, android.os.SystemClock.uptimeMillis() + RESUME_TIMEOUT_MS)
        }
        publishState()
    }

    fun automaticSnooze(packageName: String, disableSelf: () -> Unit): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (packageName == context.packageName || packageName in activeHomePackages()) return false
        return snooze.automatic(packageName, disableSelf).also { publishState() }
    }

    fun onServiceConnected() {
        handler.removeCallbacksAndMessages(RESUME_TOKEN)
        snooze.onServiceConnected()
        engine.onConnected()
        publishState()
    }

    fun onServiceDisconnected() {
        engine.onDisconnected()
        publishState()
    }

    fun repostSnoozeRecovery() = onMain {
        snooze.repostRecovery()
        publishState()
    }

    override fun refreshPrerequisites() = onMain {
        engine.checkPrerequisites()
        publishState()
    }

    fun publishState() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Controller state must be published on main" }
        val state = engine.state
        val snoozePackages = state.snoozePackages - activeHomePackages() - context.packageName
        val status = transitionStatus ?: when {
            state.snoozedForPackage != null -> ManagerStatus.SNOOZED
            state.managerEnabled -> ManagerStatus.ACTIVE
            else -> ManagerStatus.DISABLED
        }
        _snapshots.value = ManagerSnapshot(
            applicationId = context.packageName,
            permissionGranted = settings.hasPermission(),
            serviceEnabled = isServiceEnabled(),
            managerEnabled = state.managerEnabled,
            policy = state.policy,
            selectedPackages = state.selectedPackages - activeHomePackages(),
            snoozePackages = snoozePackages,
            snoozedForPackage = state.snoozedForPackage,
            status = status,
            notificationReady = notifications.isReady(),
            lastRecognisedPackage = state.lastRecognisedPackage,
            lastAppliedGrayscale = state.lastAppliedGrayscale,
            error = state.error,
        )
    }

    fun isServiceEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val expected = ComponentName(context, ForegroundAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                ComponentName(service.packageName, service.name) == expected
            }
    }

    fun activeHomePackages(): Set<String> {
        if (Build.VERSION.SDK_INT >= 29) {
            // ROLE_HOME identifies the platform-managed default-Home role. The public
            // RoleManager API does not expose holders, so resolve that role's HOME intent.
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return emptySet()
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let(::setOf).orEmpty()
    }

    private inline fun onMain(block: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Mutations must run on main" }
        block()
    }

    companion object {
        const val PREFERENCES = "grayscale_manager"
        @SuppressLint("StaticFieldLeak") // Holds applicationContext only.
        @Volatile private var instance: ManagerController? = null

        private fun emptySnapshot(packageName: String) = ManagerSnapshot(
            packageName, false, false, false, Policy.ONLY_SELECTED, emptySet(), null, null, null
        )

        private val RESUME_TOKEN = Any()
        private const val RESUME_TIMEOUT_MS = 8_000L

        fun get(context: Context): ManagerController = instance ?: synchronized(this) {
            instance ?: ManagerController(context.applicationContext).also { instance = it }
        }
    }
}
