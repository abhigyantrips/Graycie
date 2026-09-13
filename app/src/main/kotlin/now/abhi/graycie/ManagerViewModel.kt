package now.abhi.graycie

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isHome: Boolean,
)

data class ManagerUiState(
    val manager: ManagerSnapshot? = null,
    val apps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val loadingApps: Boolean = true,
    val mutating: Boolean = false,
    val appsError: String? = null,
    val transientError: String? = null,
) {
    fun visibleApps(selectedPackages: Set<String>): List<InstalledApp> {
        val needle = query.trim().lowercase()
        val filtered = if (needle.isEmpty()) apps else apps.filter {
            it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
        }
        // AppCatalog is already locale-sorted. This stable sort only lifts selections.
        return filtered.sortedByDescending { it.packageName in selectedPackages }
    }
}

class ManagerViewModel internal constructor(
    private val controller: ManagerStateSource,
    private val appLoader: suspend () -> List<InstalledApp>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManagerUiState())
    val uiState: StateFlow<ManagerUiState> = _uiState.asStateFlow()
    private var loadGeneration = 0
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            controller.snapshots.collect { snapshot ->
                _uiState.update { it.copy(manager = snapshot) }
            }
        }
    }

    fun onResume() {
        mutate { controller.refreshPrerequisites() }
        loadApps()
    }

    fun loadApps() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        _uiState.update { it.copy(loadingApps = true, appsError = null) }
        loadJob = viewModelScope.launch {
            try {
                val apps = withContext(ioDispatcher) { appLoader() }
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(apps = apps, loadingApps = false) }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // A newer load owns the state.
            } catch (_: Exception) {
                if (generation == loadGeneration) {
                    _uiState.update {
                        it.copy(loadingApps = false, appsError = "Could not load installed apps.")
                    }
                }
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }
    fun setEnabled(enabled: Boolean) = mutate { controller.setEnabled(enabled) }
    fun setPolicy(policy: Policy) = mutate { controller.setPolicy(policy) }
    fun togglePackage(packageName: String, selected: Boolean) = mutate {
        val packages = _uiState.value.manager?.selectedPackages.orEmpty().toMutableSet()
        if (selected) packages += packageName else packages -= packageName
        controller.setSelectedPackages(packages)
    }

    fun toggleSnoozePackage(packageName: String, selected: Boolean): Boolean {
        val packages = _uiState.value.manager?.snoozePackages.orEmpty().toMutableSet()
        if (selected) packages += packageName else packages -= packageName
        mutate { controller.setSnoozePackages(packages) }
        return (packageName in controller.snapshots.value.snoozePackages) == selected
    }

    fun openSafely(packageName: String) = mutate { controller.openSafely(packageName) }
    fun resumeFromSnooze() = mutate { controller.resumeFromSnooze() }

    fun clearTransientError() = _uiState.update { it.copy(transientError = null) }

    private fun mutate(block: () -> Unit) {
        _uiState.update { it.copy(mutating = true, transientError = null) }
        try {
            block()
        } catch (_: Exception) {
            _uiState.update { it.copy(transientError = "Could not save changes.") }
        } finally {
            _uiState.update { it.copy(mutating = false) }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val controller = ManagerController.get(application)
                    return ManagerViewModel(controller, {
                        AppCatalog(application).apps().map { app ->
                            InstalledApp(app.packageName, app.label, app.icon.toBitmap(), app.isHome)
                        }
                    }) as T
                }
            }
    }
}

private fun (() -> android.graphics.drawable.Drawable).toBitmap(): Bitmap? = runCatching {
    val drawable = invoke()
    val size = 96
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
    }
}.getOrNull()
