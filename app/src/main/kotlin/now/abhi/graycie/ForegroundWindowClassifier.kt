package now.abhi.graycie

data class WindowMetadata(
    val packageName: String?,
    val focused: Boolean,
    val active: Boolean,
    val type: Int,
    val pictureInPicture: Boolean = false,
    val hasRoot: Boolean = true,
)

sealed interface ForegroundClassification {
    val reason: String
    data class Settled(val packageName: String, override val reason: String) : ForegroundClassification
    data class HomeOverview(val packageName: String?, override val reason: String) : ForegroundClassification
    data class Temporary(override val reason: String) : ForegroundClassification
    data class Overlay(override val reason: String) : ForegroundClassification
    data class NonLaunchable(val packageName: String?, override val reason: String) : ForegroundClassification
}

class ForegroundWindowClassifier {
    fun classify(
        windows: List<WindowMetadata>,
        launchablePackages: Set<String>,
        homePackages: Set<String>,
        retryExpired: Boolean = false,
    ): ForegroundClassification {
        if (windows.isEmpty()) return ForegroundClassification.Temporary("no interactive windows")
        val focused = windows.firstOrNull { it.focused } ?: windows.firstOrNull { it.active }
            ?: return ForegroundClassification.Temporary("no focused or active window")
        if (!focused.hasRoot || focused.packageName == null) {
            return ForegroundClassification.Temporary("focused window root unavailable")
        }
        val applicationWindows = windows.filter {
            it.type == WINDOW_TYPE_APPLICATION && it.hasRoot && it.packageName != null && !it.pictureInPicture
        }
        val hasDivider = windows.any { it.type == WINDOW_TYPE_SPLIT_SCREEN_DIVIDER }
        val layered = applicationWindows.mapNotNull { it.packageName }.distinct().size > 1
        if (layered && !hasDivider) {
            return if (retryExpired) ForegroundClassification.Overlay("application layer persisted")
            else ForegroundClassification.Temporary("destination is layered with another application")
        }
        val candidate = focused.packageName
        if (candidate in homePackages) {
            return ForegroundClassification.HomeOverview(candidate, "Home or Overview settled")
        }
        if (candidate !in launchablePackages) {
            return ForegroundClassification.NonLaunchable(candidate, "focused package is not launchable")
        }
        return ForegroundClassification.Settled(
            candidate,
            if (hasDivider) "focused split-screen application" else "focused application settled",
        )
    }

    companion object {
        const val WINDOW_TYPE_APPLICATION = 1
        const val WINDOW_TYPE_SYSTEM = 3
        const val WINDOW_TYPE_SPLIT_SCREEN_DIVIDER = 5
    }
}

sealed interface TransitionAction {
    data object Apply : TransitionAction
    data object Retry : TransitionAction
    data object Ignore : TransitionAction
}

class TransitionStabilizer(
    private val retryIntervalMs: Long = 100,
    private val retryWindowMs: Long = 1_000,
) {
    private var pendingDestination: DestinationKey? = null

    fun reset() {
        pendingDestination = null
    }

    fun action(classification: ForegroundClassification, elapsedMs: Long): TransitionAction {
        val destination = classification.destinationKey()
        if (destination != null) {
            if (destination == pendingDestination) return TransitionAction.Apply
            if (pendingDestination != null && elapsedMs >= retryWindowMs) {
                pendingDestination = null
                return TransitionAction.Ignore
            }
            pendingDestination = destination
            return TransitionAction.Retry
        }
        pendingDestination = null
        val recoverable = classification is ForegroundClassification.Temporary ||
            classification is ForegroundClassification.NonLaunchable
        return if (recoverable && elapsedMs < retryWindowMs) {
            TransitionAction.Retry
        } else {
            TransitionAction.Ignore
        }
    }

    fun nextDelayMs(): Long = retryIntervalMs
    fun retryExpired(elapsedMs: Long): Boolean = elapsedMs >= retryWindowMs

    private fun ForegroundClassification.destinationKey(): DestinationKey? = when (this) {
        is ForegroundClassification.Settled -> DestinationKey.App(packageName)
        is ForegroundClassification.HomeOverview -> DestinationKey.Home(packageName)
        else -> null
    }

    private sealed interface DestinationKey {
        data class App(val packageName: String) : DestinationKey
        data class Home(val packageName: String?) : DestinationKey
    }
}

/** Monotonic token used to make retries from an older event burst harmless. */
class TransitionSession {
    private var generation = 0L
    fun newEvent(): Long = ++generation
    fun isCurrent(token: Long): Boolean = token == generation
    fun cancel() { generation++ }
}
