package now.abhi.graycie

enum class ManagerStatus { DISABLED, ACTIVE, SNOOZING, SNOOZED, RESUMING }

interface SnoozeNotifications {
    fun isReady(): Boolean
    fun post(packageName: String): Boolean
    fun cancel()
}

interface SnoozeAppLauncher {
    fun launch(packageName: String): Boolean
}

/** Coordinates ordering while keeping Android/provider edges injectable for tests. */
class SnoozeCoordinator(
    private val engine: PolicyEngine,
    private val notifications: SnoozeNotifications,
    private val accessibility: AccessibilityComponentControl,
    private val launcher: SnoozeAppLauncher,
    private val phaseChanged: (ManagerStatus) -> Unit,
) {
    fun automatic(packageName: String, disableSelf: () -> Unit): Boolean {
        if (packageName !in engine.state.snoozePackages || !engine.state.managerEnabled ||
            engine.state.snoozedForPackage != null) return false
        if (!notifications.isReady()) {
            engine.reportError(NOTIFICATION_ERROR)
            return false
        }
        phaseChanged(ManagerStatus.SNOOZING)
        if (!engine.prepareSnooze(packageName)) return finishSnoozed(false)
        // Recovery must exist before the service removes its own access.
        if (!runCatching { notifications.post(packageName) }.getOrDefault(false)) {
            engine.abortSnooze(NOTIFICATION_ERROR)
            phaseChanged(ManagerStatus.ACTIVE)
            return false
        }
        engine.restoreColorForSnooze()
        disableSelf()
        return finishSnoozed(true)
    }

    fun openSafely(packageName: String): Boolean {
        if (packageName !in engine.state.snoozePackages || !engine.state.managerEnabled ||
            engine.state.snoozedForPackage != null) return false
        if (!notifications.isReady()) {
            engine.reportError(NOTIFICATION_ERROR)
            return false
        }
        phaseChanged(ManagerStatus.SNOOZING)
        if (!engine.prepareSnooze(packageName)) return finishSnoozed(false)
        if (!runCatching { notifications.post(packageName) }.getOrDefault(false)) {
            engine.abortSnooze(NOTIFICATION_ERROR)
            phaseChanged(ManagerStatus.ACTIVE)
            return false
        }
        engine.restoreColorForSnooze()
        return try {
            val accepted = accessibility.disableOwnService()
            val stillEnabled = accessibility.isOwnServiceEnabled()
            if (!accepted || stillEnabled) {
                val message = "Accessibility could not be disabled, so the app was not opened."
                if (stillEnabled) {
                    engine.abortSnooze(message)
                    notifications.cancel()
                    phaseChanged(ManagerStatus.ACTIVE)
                    false
                } else {
                    engine.reportError(message)
                    finishSnoozed(false)
                }
            } else if (!launcher.launch(packageName)) {
                engine.reportError("The app has no launcher activity and could not be opened.")
                finishSnoozed(false)
            } else finishSnoozed(true)
        } catch (_: SecurityException) {
            failSafeLaunch("Secure settings permission is missing. The app was not opened.")
        } catch (_: RuntimeException) {
            failSafeLaunch("Accessibility could not be disabled, so the app was not opened.")
        }
    }

    /** Starts re-enable; snooze is cleared only by [onServiceConnected]. */
    fun resume(): Boolean {
        if (engine.state.snoozedForPackage == null) return false
        phaseChanged(ManagerStatus.RESUMING)
        return try {
            if (!accessibility.enableOwnService()) {
                resumeFailed("Android refused to re-enable accessibility.")
            } else true
        } catch (_: SecurityException) {
            resumeFailed("Secure settings permission is missing. Re-enable the service in Accessibility settings.")
        } catch (_: RuntimeException) {
            resumeFailed("Accessibility could not be re-enabled. Try Accessibility settings.")
        }
    }

    fun resumeTimedOut() {
        if (engine.state.snoozedForPackage != null) {
            engine.reportError("Accessibility did not reconnect. Try Accessibility settings.")
            phaseChanged(ManagerStatus.SNOOZED)
        }
    }

    fun onServiceConnected(): Boolean {
        if (engine.state.snoozedForPackage == null) return false
        engine.completeResume()
        notifications.cancel()
        phaseChanged(ManagerStatus.ACTIVE)
        return true
    }

    fun cancelByTurningOff() {
        engine.setEnabled(false)
        notifications.cancel()
        phaseChanged(ManagerStatus.DISABLED)
    }

    fun repostRecovery(): Boolean = engine.state.snoozedForPackage?.let(notifications::post) ?: false

    private fun resumeFailed(message: String): Boolean {
        engine.reportError(message)
        phaseChanged(ManagerStatus.SNOOZED)
        return false
    }

    private fun failSafeLaunch(message: String): Boolean {
        val stillEnabled = runCatching { accessibility.isOwnServiceEnabled() }.getOrDefault(true)
        if (stillEnabled) {
            engine.abortSnooze(message)
            notifications.cancel()
            phaseChanged(ManagerStatus.ACTIVE)
            return false
        }
        engine.reportError(message)
        return finishSnoozed(false)
    }

    private fun finishSnoozed(result: Boolean): Boolean {
        phaseChanged(ManagerStatus.SNOOZED)
        return result
    }

    companion object {
        const val NOTIFICATION_ERROR =
            "Recovery notifications are unavailable. Enable notifications before using Auto-snooze."
    }
}
