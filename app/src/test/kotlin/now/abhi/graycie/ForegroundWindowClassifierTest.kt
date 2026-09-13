package now.abhi.graycie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundWindowClassifierTest {
    private val classifier = ForegroundWindowClassifier()
    private val apps = setOf("app.one", "app.two", "pip")
    private val homes = setOf("launcher")

    private fun app(packageName: String, focused: Boolean = true, pip: Boolean = false) =
        WindowMetadata(packageName, focused, focused, 1, pip)

    @Test fun settledApplicationIsAccepted() {
        val result = classifier.classify(listOf(app("app.one")), apps, homes)
        assertEquals("app.one", (result as ForegroundClassification.Settled).packageName)
    }

    @Test fun homeIsAStableDestination() {
        assertTrue(classifier.classify(listOf(app("launcher")), apps, homes) is ForegroundClassification.HomeOverview)
    }

    @Test fun launcherBehindFocusedAppDuringGestureIsTemporary() {
        val gesture = listOf(app("app.one"), app("launcher", focused = false))
        assertTrue(classifier.classify(gesture, apps, homes) is ForegroundClassification.Temporary)
    }

    @Test fun systemUiOverHomeIsNotMistakenForOverview() {
        val overlay = listOf(
            WindowMetadata("system.ui", true, true, ForegroundWindowClassifier.WINDOW_TYPE_SYSTEM),
            WindowMetadata("launcher", false, false, ForegroundWindowClassifier.WINDOW_TYPE_APPLICATION),
        )
        assertTrue(classifier.classify(overlay, apps, homes) is ForegroundClassification.NonLaunchable)
    }

    @Test fun layeredDestinationRetriesThenBecomesOverlay() {
        val windows = listOf(app("app.two"), app("app.one", focused = false))
        assertTrue(classifier.classify(windows, apps, homes) is ForegroundClassification.Temporary)
        assertTrue(classifier.classify(windows, apps, homes, retryExpired = true) is ForegroundClassification.Overlay)
    }

    @Test fun splitScreenAcceptsFocusedApp() {
        val windows = listOf(
            app("app.two"), app("app.one", focused = false),
            WindowMetadata(null, false, false, ForegroundWindowClassifier.WINDOW_TYPE_SPLIT_SCREEN_DIVIDER),
        )
        assertTrue(classifier.classify(windows, apps, homes) is ForegroundClassification.Settled)
    }

    @Test fun pictureInPictureDoesNotBlockDestination() {
        val result = classifier.classify(listOf(app("app.two"), app("pip", false, true)), apps, homes)
        assertTrue(result is ForegroundClassification.Settled)
    }

    @Test fun transientsAndMissingRootsAreNotApplied() {
        assertTrue(classifier.classify(listOf(app("keyboard")), apps, homes) is ForegroundClassification.NonLaunchable)
        val missing = WindowMetadata(null, true, true, 1, hasRoot = false)
        assertTrue(classifier.classify(listOf(missing), apps, homes) is ForegroundClassification.Temporary)
    }

    @Test fun stabilizerRequiresTwoMatchingDestinationSamples() {
        val stabilizer = TransitionStabilizer()
        val app = ForegroundClassification.Settled("app.one", "settled")
        assertEquals(TransitionAction.Retry, stabilizer.action(app, 0))
        assertEquals(TransitionAction.Apply, stabilizer.action(app, 100))
    }

    @Test fun changedDestinationRestartsConfirmation() {
        val stabilizer = TransitionStabilizer()
        val app = ForegroundClassification.Settled("app.one", "settled")
        val home = ForegroundClassification.HomeOverview("launcher", "settled")
        assertEquals(TransitionAction.Retry, stabilizer.action(app, 0))
        assertEquals(TransitionAction.Retry, stabilizer.action(home, 100))
        assertEquals(TransitionAction.Apply, stabilizer.action(home, 200))
    }

    @Test fun stabilizerStopsRetryingIndeterminateStateAfterOneSecond() {
        val stabilizer = TransitionStabilizer()
        val temporary = ForegroundClassification.Temporary("transition")
        assertEquals(TransitionAction.Retry, stabilizer.action(temporary, 999))
        assertEquals(TransitionAction.Ignore, stabilizer.action(temporary, 1_000))
    }

    @Test fun nonLaunchableSurfaceIsRecheckedDuringSettlementWindow() {
        val stabilizer = TransitionStabilizer()
        val systemUi = ForegroundClassification.NonLaunchable("system.ui", "transient")
        assertEquals(TransitionAction.Retry, stabilizer.action(systemUi, 999))
        assertEquals(TransitionAction.Ignore, stabilizer.action(systemUi, 1_000))
    }

    @Test fun destinationFirstSeenAtDeadlineStillGetsConfirmationSample() {
        val stabilizer = TransitionStabilizer()
        val app = ForegroundClassification.Settled("app.one", "settled")
        assertEquals(TransitionAction.Retry, stabilizer.action(app, 1_000))
        assertEquals(TransitionAction.Apply, stabilizer.action(app, 1_100))
    }

    @Test fun resetInvalidatesPendingDestination() {
        val stabilizer = TransitionStabilizer()
        val app = ForegroundClassification.Settled("app.one", "settled")
        assertEquals(TransitionAction.Retry, stabilizer.action(app, 0))
        stabilizer.reset()
        assertEquals(TransitionAction.Retry, stabilizer.action(app, 100))
    }

    @Test fun newerEventCancelsOldRetryToken() {
        val session = TransitionSession()
        val old = session.newEvent()
        val current = session.newEvent()
        assertFalse(session.isCurrent(old))
        assertTrue(session.isCurrent(current))
        session.cancel()
        assertFalse(session.isCurrent(current))
    }
}
