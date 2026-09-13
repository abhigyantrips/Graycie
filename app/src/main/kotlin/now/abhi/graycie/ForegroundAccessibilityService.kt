package now.abhi.graycie

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class ForegroundAccessibilityService : AccessibilityService() {
    private val controller by lazy { ManagerController.get(this) }
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val classifier = ForegroundWindowClassifier()
    private val stabilizer = TransitionStabilizer()
    private val handler = Handler(Looper.getMainLooper())
    private var launchablePackages = emptySet<String>()
    private var homePackages = emptySet<String>()
    private var callbackRegistered = false
    private var eventGeneration = 0L
    private var transitionStartedAt = 0L

    private val packagesChanged = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshPackages()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshPackages()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshPackages()
        override fun onPackagesAvailable(packages: Array<out String>, user: UserHandle, replacing: Boolean) = refreshPackages()
        override fun onPackagesUnavailable(packages: Array<out String>, user: UserHandle, replacing: Boolean) = refreshPackages()
    }

    private fun refreshPackages() {
        launchablePackages = AppCatalog(this).apps().map { it.packageName }.toSet()
        homePackages = controller.activeHomePackages()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshPackages()
        if (!callbackRegistered) {
            launcherApps.registerCallback(packagesChanged, handler)
            callbackRegistered = true
        }
        controller.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val eventPackage = event.packageName?.toString()
            if (eventPackage != null && controller.automaticSnooze(eventPackage) { disableSelf() }) {
                cancelChecks()
                return
            }
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        eventGeneration++
        val generation = eventGeneration
        transitionStartedAt = SystemClock.uptimeMillis() + SETTLE_DELAY_MS
        stabilizer.reset()
        handler.removeCallbacksAndMessages(RECHECK_TOKEN)
        schedule(generation, SETTLE_DELAY_MS)
    }

    private fun classifyForeground(generation: Long) {
        if (generation != eventGeneration) return
        // The default Home app can change without its package being added, removed, or updated.
        homePackages = controller.activeHomePackages()
        val elapsed = (SystemClock.uptimeMillis() - transitionStartedAt).coerceAtLeast(0)
        val classification = classifier.classify(
            windows = windows.map { it.toMetadata() },
            launchablePackages = launchablePackages + packageName,
            homePackages = homePackages,
            retryExpired = stabilizer.retryExpired(elapsed),
        )
        if (BuildConfig.DEBUG) {
            val packages = windows.mapNotNull { it.root?.packageName?.toString() }.distinct()
            Log.d(TAG, "windows=$packages classification=${classification.javaClass.simpleName} reason=${classification.reason}")
        }
        when (stabilizer.action(classification, elapsed)) {
            TransitionAction.Apply -> {
                when (classification) {
                    is ForegroundClassification.Settled ->
                        controller.engine.onForeground(classification.packageName, true)
                    is ForegroundClassification.HomeOverview ->
                        controller.engine.onHome(classification.packageName)
                    else -> error("Only stable destinations can be applied")
                }
            }
            TransitionAction.Retry -> schedule(generation, stabilizer.nextDelayMs())
            TransitionAction.Ignore -> Unit
        }
    }

    private fun AccessibilityWindowInfo.toMetadata(): WindowMetadata {
        val rootPackage = root?.packageName?.toString()
        return WindowMetadata(
            packageName = rootPackage,
            focused = isFocused,
            active = isActive,
            type = type,
            pictureInPicture = isInPictureInPictureMode,
            hasRoot = rootPackage != null,
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cancelChecks()
        controller.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelChecks()
        controller.onServiceDisconnected()
        if (callbackRegistered) launcherApps.unregisterCallback(packagesChanged)
        super.onDestroy()
    }

    private fun cancelChecks() {
        eventGeneration++
        stabilizer.reset()
        handler.removeCallbacksAndMessages(RECHECK_TOKEN)
    }

    private fun schedule(generation: Long, delayMs: Long) {
        handler.postAtTime(
            { classifyForeground(generation) },
            RECHECK_TOKEN,
            SystemClock.uptimeMillis() + delayMs,
        )
    }

    private companion object {
        const val TAG = "GrayscaleWindows"
        const val SETTLE_DELAY_MS = 250L
        val RECHECK_TOKEN = Any()
    }
}
