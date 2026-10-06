package now.abhi.graycie

enum class Policy { ONLY_SELECTED, EXCEPT_SELECTED }

private const val PERMISSION_ERROR = "Secure settings permission is missing. Complete the ADB setup."

data class ManagerState(
    val managerEnabled: Boolean = false,
    val policy: Policy = Policy.ONLY_SELECTED,
    val selectedPackages: Set<String> = emptySet(),
    val lastRecognisedPackage: String? = null,
    val lastAppliedGrayscale: Boolean? = null,
    val error: String? = null,
    val snoozePackages: Set<String> = emptySet(),
    val snoozedForPackage: String? = null,
    val ownsColorCorrection: Boolean = false,
)

interface StateStore {
    fun read(): ManagerState
    fun write(state: ManagerState)
}

interface GrayscaleSettings {
    fun hasPermission(): Boolean
    /** Return false when the provider refuses a write; may throw SecurityException. */
    fun setGrayscale(enabled: Boolean): Boolean
    /** Stop management and restore saved correction, preserving a newer manual choice. */
    fun release(): Boolean
}

/** All entry points run on Android's main thread, including secure-setting writes. */
class PolicyEngine(
    private val ownPackage: String,
    private val store: StateStore,
    private val settings: GrayscaleSettings,
    private val serviceEnabled: () -> Boolean,
    private val changed: () -> Unit = {},
) {
    var state = store.read()
        private set
    private var foregroundTarget: ForegroundTarget? = null

    private fun save(next: ManagerState) {
        if (next == state) return
        state = next
        store.write(next)
        changed()
    }

    fun checkPrerequisites() {
        if (!settings.hasPermission()) {
            save(state.copy(managerEnabled = state.managerEnabled && state.snoozedForPackage != null,
                lastAppliedGrayscale = null,
                error = PERMISSION_ERROR))
        } else if (!serviceEnabled() && state.managerEnabled && state.snoozedForPackage == null) {
            setEnabled(false)
        } else if (state.error == PERMISSION_ERROR) {
            save(state.copy(error = null))
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            save(state.copy(managerEnabled = false, snoozedForPackage = null))
            releaseGrayscale()
            return
        }
        checkPrerequisites()
        if (!settings.hasPermission() || !serviceEnabled()) return
        save(state.copy(managerEnabled = true, error = null,
            lastAppliedGrayscale = if (state.managerEnabled) state.lastAppliedGrayscale else null))
        evaluate()
    }

    fun setPolicy(policy: Policy) {
        save(state.copy(policy = policy))
        checkPrerequisites()
        evaluate()
    }

    fun setSelected(packages: Set<String>) {
        save(state.copy(selectedPackages = packages - ownPackage))
        checkPrerequisites()
        evaluate()
    }

    fun setSnoozePackages(packages: Set<String>) {
        save(state.copy(snoozePackages = packages - ownPackage))
    }

    fun prepareSnooze(packageName: String): Boolean {
        if (state.snoozedForPackage != null || packageName !in state.snoozePackages) {
            return false
        }
        save(state.copy(snoozedForPackage = packageName, error = null))
        return true
    }

    /** Release Graycie's correction without changing the user's master-enabled preference. */
    fun restoreColorForSnooze(): Boolean {
        if (!state.ownsColorCorrection) return true
        return try {
            if (!settings.hasPermission()) throw SecurityException("Permission missing")
            if (!settings.release()) {
                save(state.copy(lastAppliedGrayscale = null,
                    error = "Previous Color correction could not be restored before snoozing. Check Android Color correction settings."))
                false
            } else {
                save(state.copy(lastAppliedGrayscale = null, ownsColorCorrection = false, error = null))
                true
            }
        } catch (_: SecurityException) {
            save(state.copy(lastAppliedGrayscale = null, error = PERMISSION_ERROR))
            false
        } catch (_: RuntimeException) {
            save(state.copy(lastAppliedGrayscale = null,
                error = "Previous Color correction could not be restored before snoozing. Check Android Color correction settings."))
            false
        }
    }

    fun abortSnooze(message: String) {
        save(state.copy(snoozedForPackage = null, error = message))
    }

    fun reportError(message: String) = save(state.copy(error = message))

    fun completeResume() {
        save(state.copy(snoozedForPackage = null, lastRecognisedPackage = null,
            lastAppliedGrayscale = null, error = null))
    }

    fun onConnected() {
        // Persisted foreground and applied state cannot be trusted after reconnect/restart.
        foregroundTarget = null
        save(state.copy(lastRecognisedPackage = null, lastAppliedGrayscale = null))
        checkPrerequisites()
        if (!state.managerEnabled && settings.hasPermission()) releaseGrayscale()
    }

    fun onForeground(packageName: String, isLaunchable: Boolean) {
        // Check before deduplication so revocation is caught even for repeated events.
        checkPrerequisites()
        if (packageName != ownPackage && !isLaunchable) return
        val target = ForegroundTarget.App(packageName)
        if (target == foregroundTarget) return
        foregroundTarget = target
        if (packageName != state.lastRecognisedPackage) {
            save(state.copy(lastRecognisedPackage = packageName))
        }
        evaluate()
    }

    fun onHome(packageName: String?) {
        checkPrerequisites()
        val target = ForegroundTarget.Home(packageName)
        if (target == foregroundTarget) return
        foregroundTarget = target
        if (packageName != null && packageName != state.lastRecognisedPackage) {
            save(state.copy(lastRecognisedPackage = packageName))
        }
        evaluate()
    }

    fun onDisconnected() {
        foregroundTarget = null
        if (state.snoozedForPackage != null) {
            save(state.copy(lastRecognisedPackage = null))
            return
        }
        save(state.copy(managerEnabled = false, lastRecognisedPackage = null))
        releaseGrayscale()
    }

    private fun evaluate() {
        if (!state.managerEnabled) return
        val grayscale = when (val target = foregroundTarget) {
            is ForegroundTarget.Home -> true
            is ForegroundTarget.App -> {
                val selected = target.packageName in state.selectedPackages
                target.packageName != ownPackage && when (state.policy) {
                    Policy.ONLY_SELECTED -> selected
                    Policy.EXCEPT_SELECTED -> !selected
                }
            }
            null -> return
        }
        applyGrayscale(grayscale)
    }

    private fun applyGrayscale(enabled: Boolean) {
        if (state.lastAppliedGrayscale == enabled) return
        try {
            if (!settings.hasPermission()) throw SecurityException("Permission missing")
            // Persist ownership before writing so partial failures and restarts can
            // clean up, without touching corrections when Graycie has never managed them.
            save(state.copy(ownsColorCorrection = true))
            if (!settings.setGrayscale(enabled)) {
                failChange("Android refused the color correction change. Check setup and try again.")
                return
            }
            save(state.copy(lastAppliedGrayscale = enabled, error = null))
        } catch (_: SecurityException) {
            failChange(PERMISSION_ERROR)
        } catch (_: RuntimeException) {
            failChange("Could not change Android Color correction. Check setup and try again.")
        }
    }

    private fun releaseGrayscale() {
        if (!state.ownsColorCorrection) return
        try {
            if (!settings.hasPermission()) throw SecurityException("Permission missing")
            if (!settings.release()) {
                failChange("Android refused to restore previous Color correction. Check Android Color correction settings.",
                    cleanup = false)
                return
            }
            save(state.copy(lastAppliedGrayscale = null, ownsColorCorrection = false, error = null))
        } catch (_: SecurityException) {
            failChange(PERMISSION_ERROR, cleanup = false)
        } catch (_: RuntimeException) {
            failChange("Could not restore previous Color correction. Check Android Color correction settings.",
                cleanup = false)
        }
    }

    private fun failChange(message: String, cleanup: Boolean = true) {
        var error = message
        var ownsColorCorrection = state.ownsColorCorrection
        if (cleanup && ownsColorCorrection && settings.hasPermission()) {
            val restored = try { settings.release() } catch (_: RuntimeException) { false }
            if (restored) ownsColorCorrection = false
            if (!restored) error += " Previous Color correction could not be restored; check Android Color correction settings."
        }
        save(state.copy(managerEnabled = false, lastAppliedGrayscale = null,
            ownsColorCorrection = ownsColorCorrection, error = error))
    }

    private sealed interface ForegroundTarget {
        data class App(val packageName: String) : ForegroundTarget
        data class Home(val packageName: String?) : ForegroundTarget
    }
}
