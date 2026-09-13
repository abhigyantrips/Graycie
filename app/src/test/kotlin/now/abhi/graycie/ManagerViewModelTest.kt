package now.abhi.graycie

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var source: FakeSource

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = FakeSource()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun collectsControllerStateAndMutatesSelection() = runTest(dispatcher) {
        val viewModel = ManagerViewModel(source, { emptyList() }, dispatcher)
        advanceUntilIdle()
        assertEquals(Policy.ONLY_SELECTED, viewModel.uiState.value.manager?.policy)
        viewModel.setPolicy(Policy.EXCEPT_SELECTED)
        viewModel.togglePackage("browser", true)
        advanceUntilIdle()
        assertEquals(Policy.EXCEPT_SELECTED, source.snapshots.value.policy)
        assertEquals(setOf("browser"), source.snapshots.value.selectedPackages)
    }

    @Test fun snoozeSelectionSafeLaunchAndResumeReachController() = runTest(dispatcher) {
        val viewModel = ManagerViewModel(source, { emptyList() }, dispatcher)
        advanceUntilIdle()
        viewModel.toggleSnoozePackage("bank", true)
        viewModel.openSafely("bank")
        viewModel.resumeFromSnooze()
        assertEquals(setOf("bank"), source.snapshots.value.snoozePackages)
        assertEquals("bank", source.safeLaunch)
        assertEquals(1, source.resumes)
    }

    @Test fun loadingAndFilteringApps() = runTest(dispatcher) {
        val viewModel = ManagerViewModel(source, {
            listOf(app("Browser", "test.browser"), app("Camera", "test.camera"))
        }, dispatcher)
        viewModel.loadApps()
        advanceUntilIdle()
        viewModel.setQuery("BROWSER")
        assertEquals(
            listOf("test.browser"),
            viewModel.uiState.value.visibleApps(emptySet()).map { it.packageName },
        )
        assertFalse(viewModel.uiState.value.loadingApps)
    }

    @Test fun selectedAppsArePinnedAboveTheCatalogOrder() = runTest(dispatcher) {
        val viewModel = ManagerViewModel(source, {
            listOf(app("Alpha", "alpha"), app("Beta", "beta"), app("Gamma", "gamma"))
        }, dispatcher)
        viewModel.loadApps()
        advanceUntilIdle()
        assertEquals(
            listOf("gamma", "alpha", "beta"),
            viewModel.uiState.value.visibleApps(setOf("gamma")).map { it.packageName },
        )
    }

    @Test fun resumeRefreshesPrerequisitesAndApps() = runTest(dispatcher) {
        var loads = 0
        val viewModel = ManagerViewModel(source, { loads++; emptyList() }, dispatcher)
        viewModel.onResume()
        advanceUntilIdle()
        assertEquals(1, source.refreshes)
        assertEquals(1, loads)
    }

    @Test fun mutationFailureIsTransientUiError() = runTest(dispatcher) {
        source.failMutations = true
        val viewModel = ManagerViewModel(source, { emptyList() }, dispatcher)
        viewModel.setEnabled(true)
        assertEquals("Could not save changes.", viewModel.uiState.value.transientError)
        assertFalse(viewModel.uiState.value.mutating)
    }

    @Test fun staleAsynchronousLoadCannotReplaceNewerResult() = runTest(dispatcher) {
        val first = CompletableDeferred<List<InstalledApp>>()
        var call = 0
        val viewModel = ManagerViewModel(source, {
            if (++call == 1) first.await() else listOf(app("New", "new"))
        }, dispatcher)
        viewModel.loadApps()
        dispatcher.scheduler.runCurrent()
        viewModel.loadApps()
        advanceUntilIdle()
        first.complete(listOf(app("Old", "old")))
        advanceUntilIdle()
        assertEquals(listOf("new"), viewModel.uiState.value.apps.map { it.packageName })
    }

    @Test fun appFailureProducesRetryableState() = runTest(dispatcher) {
        val viewModel = ManagerViewModel(source, { error("broken") }, dispatcher)
        viewModel.loadApps()
        advanceUntilIdle()
        assertEquals("Could not load installed apps.", viewModel.uiState.value.appsError)
        assertFalse(viewModel.uiState.value.loadingApps)
    }

    private fun app(label: String, packageName: String) = InstalledApp(packageName, label, null, false)

    private class FakeSource : ManagerStateSource {
        override val snapshots = MutableStateFlow(snapshot())
        var refreshes = 0
        var failMutations = false
        var safeLaunch: String? = null
        var resumes = 0
        override fun setEnabled(enabled: Boolean) = change { it.copy(managerEnabled = enabled) }
        override fun setPolicy(policy: Policy) = change { it.copy(policy = policy) }
        override fun setSelectedPackages(packages: Set<String>) = change { it.copy(selectedPackages = packages) }
        override fun setSnoozePackages(packages: Set<String>) = change { it.copy(snoozePackages = packages) }
        override fun openSafely(packageName: String) { safeLaunch = packageName }
        override fun resumeFromSnooze() { resumes++ }
        override fun refreshPrerequisites() { refreshes++ }
        private fun change(block: (ManagerSnapshot) -> ManagerSnapshot) {
            if (failMutations) error("failure")
            snapshots.value = block(snapshots.value)
        }
        companion object {
            fun snapshot() = ManagerSnapshot(
                "now.abhi.graycie", true, true, false, Policy.ONLY_SELECTED,
                emptySet(), null, null, null,
            )
        }
    }
}
