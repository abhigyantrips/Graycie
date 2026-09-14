package now.abhi.graycie.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import now.abhi.graycie.InstalledApp
import now.abhi.graycie.ManagerSnapshot
import now.abhi.graycie.ManagerStatus
import now.abhi.graycie.ManagerUiState
import now.abhi.graycie.ManagerViewModel
import now.abhi.graycie.Policy
import now.abhi.graycie.R
import now.abhi.graycie.SnoozeCoordinator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

private enum class Destination { HOME, GRAYSCALE_CONTROL, AUTO_SNOOZE_APPS, SETUP }

/** A superellipse has continuous curvature; it is not a rounded-rectangle approximation. */
object ContinuousSquircle : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        repeat(97) { index ->
            val angle = (Math.PI * 2.0 * index / 96.0)
            val cosine = cos(angle)
            val sine = sin(angle)
            val x = cx + cx * sign(cosine).toFloat() * abs(cosine).pow(0.5).toFloat()
            val y = cy + cy * sign(sine).toFloat() * abs(sine).pow(0.5).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun ManagerScreen(
    viewModel: ManagerViewModel,
    openAccessibilitySettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ManagerContent(
        state = state,
        setQuery = viewModel::setQuery,
        setEnabled = viewModel::setEnabled,
        setPolicy = viewModel::setPolicy,
        togglePackage = viewModel::togglePackage,
        toggleSnoozePackage = viewModel::toggleSnoozePackage,
        openSafely = viewModel::openSafely,
        resumeFromSnooze = viewModel::resumeFromSnooze,
        refresh = viewModel::onResume,
        refreshApps = viewModel::loadApps,
        openAccessibilitySettings = openAccessibilitySettings,
    )
}

@Composable
fun ManagerContent(
    state: ManagerUiState,
    setQuery: (String) -> Unit,
    setEnabled: (Boolean) -> Unit,
    setPolicy: (Policy) -> Unit,
    togglePackage: (String, Boolean) -> Unit,
    toggleSnoozePackage: (String, Boolean) -> Boolean = { _, _ -> true },
    openSafely: (String) -> Unit = {},
    resumeFromSnooze: () -> Unit = {},
    refresh: () -> Unit,
    refreshApps: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    var destinationName by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    var movingForward by remember { mutableStateOf(true) }
    var pendingSnooze by remember { mutableStateOf<InstalledApp?>(null) }
    val destination = runCatching { Destination.valueOf(destinationName) }.getOrDefault(Destination.HOME)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val motionEnabled = rememberMotionEnabled()

    fun showMessage(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun navigate(next: Destination) {
        if (next == destination) return
        if (destination == Destination.GRAYSCALE_CONTROL || destination == Destination.AUTO_SNOOZE_APPS) {
            setQuery("")
            dismissSearch(focusManager) { keyboard?.hide() }
        }
        movingForward = destination == Destination.HOME && next != Destination.HOME
        destinationName = next.name
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val app = pendingSnooze
        pendingSnooze = null
        refresh()
        if (!granted || app == null) {
            showMessage(SnoozeCoordinator.NOTIFICATION_ERROR)
        } else if (toggleSnoozePackage(app.packageName, true)) {
            showMessage("Auto-snooze enabled for ${app.label}.")
        } else {
            showMessage(SnoozeCoordinator.NOTIFICATION_ERROR)
        }
    }

    BackHandler(enabled = destination != Destination.HOME) { navigate(Destination.HOME) }

    Scaffold(
        containerColor = GraycieBackground,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { scaffoldPadding ->
        Box(
            Modifier.fillMaxSize().padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val manager = state.manager
            if (manager == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).testTag("state-loading"),
                    color = GraycieAmber,
                )
            } else {
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (!motionEnabled) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val direction = if (movingForward) 1 else -1
                            (slideInHorizontally(tween(280)) { direction * it / 4 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(280)) { -direction * it / 5 } + fadeOut(tween(180)))
                        }
                    },
                    label = "Graycie destination",
                ) { screen ->
                    when (screen) {
                        Destination.HOME -> HomeScreen(
                            manager = manager,
                            apps = state.apps,
                            busy = state.mutating,
                            error = state.transientError ?: manager.error,
                            motionEnabled = motionEnabled,
                            setEnabled = setEnabled,
                            onSetup = { navigate(Destination.SETUP) },
                            onGrayscale = { navigate(Destination.GRAYSCALE_CONTROL) },
                            onAutoSnooze = { navigate(Destination.AUTO_SNOOZE_APPS) },
                            onResumeSnooze = resumeFromSnooze,
                        )
                        Destination.GRAYSCALE_CONTROL -> AppSelectionScreen(
                            state = state,
                            manager = manager,
                            snoozeMode = false,
                            onBack = { navigate(Destination.HOME) },
                            setQuery = setQuery,
                            setPolicy = setPolicy,
                            togglePackage = togglePackage,
                            toggleSnooze = { _, _ -> },
                            openSafely = openSafely,
                            refreshApps = refreshApps,
                        )
                        Destination.AUTO_SNOOZE_APPS -> AppSelectionScreen(
                            state = state,
                            manager = manager,
                            snoozeMode = true,
                            onBack = { navigate(Destination.HOME) },
                            setQuery = setQuery,
                            setPolicy = setPolicy,
                            togglePackage = togglePackage,
                            toggleSnooze = { app, selected ->
                                if (selected && manager.snoozePackages.isEmpty() && !manager.notificationReady) {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        pendingSnooze = app
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        showMessage(SnoozeCoordinator.NOTIFICATION_ERROR)
                                    }
                                } else if (toggleSnoozePackage(app.packageName, selected)) {
                                    showMessage(
                                        "Auto-snooze ${if (selected) "enabled" else "disabled"} for ${app.label}."
                                    )
                                } else {
                                    showMessage(SnoozeCoordinator.NOTIFICATION_ERROR)
                                }
                            },
                            openSafely = openSafely,
                            refreshApps = refreshApps,
                        )
                        Destination.SETUP -> SetupScreen(
                            manager = manager,
                            error = state.transientError ?: manager.error,
                            onBack = { navigate(Destination.HOME) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Graycie ADB command", manager.adbCommand))
                                showMessage("ADB command copied")
                            },
                            onRefresh = refresh,
                            onOpenAccessibility = openAccessibilitySettings,
                        )
                    }
                }
            }
        }
    }
}

private fun dismissSearch(focusManager: FocusManager, hideKeyboard: () -> Unit) {
    focusManager.clearFocus(force = true)
    hideKeyboard()
}

@Composable
private fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

@Composable
private fun HomeScreen(
    manager: ManagerSnapshot,
    apps: List<InstalledApp>,
    busy: Boolean,
    error: String?,
    motionEnabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    onSetup: () -> Unit,
    onGrayscale: () -> Unit,
    onAutoSnooze: () -> Unit,
    onResumeSnooze: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).testTag("home-screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("settings") {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                IconButton(
                    onClick = onSetup,
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).size(48.dp)
                        .testTag("open-setup"),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_tabler_settings_filled),
                        contentDescription = "Setup",
                        tint = GraycieText,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        item("master") {
            HomeMasterControl(
                manager = manager,
                apps = apps,
                busy = busy,
                motionEnabled = motionEnabled,
                setEnabled = setEnabled,
                onSetupRequired = onSetup,
                onResumeSnooze = onResumeSnooze,
            )
        }
        error?.let { message ->
            item("error") {
                Text(
                    message,
                    color = GraycieError,
                    modifier = Modifier.fillMaxWidth().testTag("manager-error"),
                )
            }
        }
        item("grayscale-control") {
            FeatureCard(
                icon = R.drawable.ic_tabler_contrast_2_filled,
                title = "Grayscale Control",
                description = "Choose where your phone goes grayscale.",
                onClick = onGrayscale,
                testTag = "feature-grayscale",
            )
        }
        item("night-light") {
            FeatureCard(
                icon = R.drawable.ic_tabler_moon_filled,
                title = "Night Light Control",
                description = "Make evenings easier on your eyes.",
                enabled = false,
                badge = "soon™",
                testTag = "feature-night-light",
            )
        }
        item("auto-snooze") {
            FeatureCard(
                icon = R.drawable.ic_tabler_alarm_snooze_filled,
                title = "Auto-Snooze Apps",
                description = "Choose apps that need accessibility paused.",
                onClick = onAutoSnooze,
                testTag = "feature-auto-snooze",
            )
        }
        item("author") { AuthorFooter() }
    }
}

@Composable
private fun HomeMasterControl(
    manager: ManagerSnapshot,
    apps: List<InstalledApp>,
    busy: Boolean,
    motionEnabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    onSetupRequired: () -> Unit,
    onResumeSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GraycieWordmark(manager.managerEnabled || manager.snoozedForPackage != null, motionEnabled)
        Spacer(Modifier.height(0.dp))
        Text(
            "is",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 28.sp),
            color = GraycieMuted,
            modifier = Modifier.testTag("graycie-is"),
        )
        Spacer(Modifier.height(17.dp))
        MasterSwitch(manager, busy, motionEnabled, setEnabled, onSetupRequired)
        if (manager.snoozedForPackage != null) {
            Spacer(Modifier.height(18.dp))
            SnoozeRecoveryRow(manager, apps, busy, onResumeSnooze)
        }
    }
}

@Composable
private fun GraycieWordmark(enabled: Boolean, motionEnabled: Boolean) {
    val rainbow = listOf(
        Color(0xFFEF6C66),
        Color(0xFFF0A75B),
        Color(0xFFE7D36F),
        Color(0xFF72C49A),
        Color(0xFF65BED1),
        Color(0xFF78AEE8),
        Color(0xFFC28AE7),
    )
    val colors = rainbow.mapIndexed { index, color ->
        val target = if (enabled) Color(0xFFAAA49D) else color
        animateColorAsState(
            targetValue = target,
            animationSpec = if (motionEnabled) tween(420 + index * 25) else snap(),
            label = "Graycie letter $index",
        ).value
    }
    Text(
        text = buildAnnotatedString {
            "Graycie".forEachIndexed { index, character ->
                withStyle(SpanStyle(color = colors[index])) { append(character) }
            }
        },
        style = MaterialTheme.typography.displayMedium.copy(
            fontFamily = ChatFavour,
            fontWeight = FontWeight.Normal,
            fontSize = 120.sp,
            lineHeight = 128.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.fillMaxWidth(0.82f).semantics {
            stateDescription = if (enabled) "Desaturated" else "Rainbow"
        }.testTag("graycie-wordmark"),
    )
}

@Composable
private fun MasterSwitch(
    manager: ManagerSnapshot,
    busy: Boolean,
    motionEnabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    onSetupRequired: () -> Unit,
) {
    val view = LocalView.current
    val checked = manager.managerEnabled || manager.snoozedForPackage != null
    val background by animateColorAsState(
        targetValue = if (checked) Color(0xFF2E7D52) else Color(0xFF3A3733),
        animationSpec = if (motionEnabled) tween(240) else snap(),
        label = "Master background",
    )
    val offAlpha by animateFloatAsState(
        targetValue = if (checked) 0f else 1f,
        animationSpec = if (motionEnabled) tween(180) else snap(),
        label = "Off label alpha",
    )
    val onAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (motionEnabled) tween(180) else snap(),
        label = "On label alpha",
    )
    Row(
        modifier = Modifier.width(168.dp).height(48.dp).clip(RoundedCornerShape(16.dp))
            .background(background)
            .toggleable(
                value = checked,
                enabled = !busy,
                role = Role.Switch,
                onValueChange = { next ->
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (next && !manager.ready && manager.snoozedForPackage == null) onSetupRequired()
                    else setEnabled(next)
                },
            )
            .semantics {
                contentDescription = "Graycie grayscale control"
                stateDescription = if (checked) "On" else "Off"
            }
            .testTag("master-switch").padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = offAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "OFF",
                style = MaterialTheme.typography.labelLarge,
                color = if (checked) Color.White.copy(alpha = 0.65f) else Color.Black,
            )
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = onAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "ON",
                style = MaterialTheme.typography.labelLarge,
                color = if (checked) Color.Black else Color.White.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun SnoozeRecoveryRow(
    manager: ManagerSnapshot,
    apps: List<InstalledApp>,
    busy: Boolean,
    resume: () -> Unit,
) {
    val packageName = manager.snoozedForPackage ?: return
    val label = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
    Row(
        modifier = Modifier.fillMaxWidth().testTag("snooze-recovery"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Snoozed for $label", style = MaterialTheme.typography.titleMedium)
            Text("Accessibility stays off until you resume.", style = MaterialTheme.typography.bodySmall, color = GraycieMuted)
        }
        GraycieButton(
            onClick = resume,
            enabled = !busy && manager.status != ManagerStatus.RESUMING,
            modifier = Modifier.testTag("resume-snooze"),
        ) { Text(if (manager.status == ManagerStatus.RESUMING) "Resuming…" else "Resume") }
    }
}

@Composable
private fun FeatureCard(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit = {},
    testTag: String,
) {
    val interaction = if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (enabled) GraycieSurface else GraycieSurface.copy(alpha = 0.62f))
            .then(interaction).semantics { if (!enabled) disabled() }.testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureIconTile(icon, enabled)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) GraycieText else GraycieDisabled,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) GraycieMuted else GraycieDisabled,
            )
        }
        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                badge,
                style = MaterialTheme.typography.bodySmall,
                color = GraycieMuted,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(GraycieSurfaceRaised.copy(alpha = 0.82f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("feature-soon"),
            )
        } else if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.ic_tabler_chevron_right_filled),
                contentDescription = null,
                tint = GraycieMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FeatureIconTile(@DrawableRes icon: Int, enabled: Boolean) {
    Box(
        modifier = Modifier.size(50.dp).clip(ContinuousSquircle)
            .background(if (enabled) Color(0xFF352A20) else GraycieSurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (enabled) GraycieAmberSoft else GraycieDisabled,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun GraycieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GraycieAmber,
            contentColor = GraycieBackground,
            disabledContainerColor = GraycieAmber.copy(alpha = 0.38f),
            disabledContentColor = GraycieBackground.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
        content = content,
    )
}

@Composable
private fun AuthorFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("Made to just work, by ", style = MaterialTheme.typography.bodySmall, color = GraycieMuted)
        val uriHandler = LocalUriHandler.current
        Text(
            "Abhigyan Trips",
            style = MaterialTheme.typography.bodySmall,
            color = GraycieAmberSoft,
            modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri("https://abhi.now") }
                .testTag("author-link"),
        )
        Text(".", style = MaterialTheme.typography.bodySmall, color = GraycieMuted)
    }
}

@Composable
private fun AppSelectionScreen(
    state: ManagerUiState,
    manager: ManagerSnapshot,
    snoozeMode: Boolean,
    onBack: () -> Unit,
    setQuery: (String) -> Unit,
    setPolicy: (Policy) -> Unit,
    togglePackage: (String, Boolean) -> Unit,
    toggleSnooze: (InstalledApp, Boolean) -> Unit,
    openSafely: (String) -> Unit,
    refreshApps: () -> Unit,
) {
    val selectedPackages = if (snoozeMode) manager.snoozePackages else manager.selectedPackages
    val visibleApps = state.visibleApps(selectedPackages)
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)
            .testTag(if (snoozeMode) "auto-snooze-screen" else "grayscale-control-screen"),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item("header") {
            ScreenHeader(if (snoozeMode) "Auto-Snooze Apps" else "Grayscale Control", onBack)
        }
        item("intro") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    if (snoozeMode) {
                        "Banking and security-sensitive apps can flag any accessibility service. " +
                            "Graycie temporarily removes its own permission for apps you select. " +
                            "Automatic detection is best-effort; use Open safely to pause Graycie before launch."
                    } else {
                        "Which apps would you like to turn grayscale?"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (snoozeMode) GraycieMuted else GraycieText,
                    modifier = Modifier.testTag(
                        if (snoozeMode) "snooze-explanation" else "selector-question"
                    ),
                )
            }
        }
        if (!snoozeMode) {
            item("policy-tabs") { PolicyTabs(manager.policy, !state.mutating, setPolicy) }
        }
        item("search") { SearchField(state.query, setQuery) }
        item("apps-heading") {
            AppsHeader(
                selected = selectedPackages.size,
                loading = state.loadingApps,
                refresh = refreshApps,
            )
        }
        val shownError = state.transientError ?: manager.error
        if (shownError != null) {
            item("error") {
                Text(
                    shownError,
                    color = GraycieError,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("manager-error"),
                )
            }
        }
        if (state.loadingApps) {
            item("apps-loading") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("apps-loading"),
                    color = GraycieAmber,
                    trackColor = GraycieSurfaceRaised,
                )
            }
        }
        state.appsError?.let { error ->
            item("apps-error") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).testTag("apps-error")) {
                    Text(error)
                    TextButton(onClick = refreshApps) { Text("Retry loading apps") }
                }
            }
        }
        if (!state.loadingApps && state.appsError == null && visibleApps.isEmpty()) {
            item("empty") {
                Text(
                    if (state.query.isBlank()) "No launchable apps found." else "No apps match your search.",
                    modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("apps-empty"),
                    color = GraycieMuted,
                )
            }
        }
        items(visibleApps, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                manager = manager,
                snoozeMode = snoozeMode,
                busy = state.mutating,
                toggle = { selected ->
                    if (snoozeMode) toggleSnooze(app, selected)
                    else togglePackage(app.packageName, selected)
                },
                openSafely = openSafely,
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(start = 8.dp, end = 20.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("navigate-back")) {
            Icon(
                painterResource(R.drawable.ic_tabler_chevron_right_filled),
                contentDescription = "Back",
                tint = GraycieText,
                modifier = Modifier.size(23.dp).graphicsLayer { scaleX = -1f },
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PolicyTabs(policy: Policy, enabled: Boolean, changed: (Policy) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp)).background(GraycieSurface).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        listOf(
            Policy.ONLY_SELECTED to "Only These",
            Policy.EXCEPT_SELECTED to "Except These",
        ).forEach { (value, label) ->
            val isSelected = policy == value
            Box(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) GraycieSurfaceRaised else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.RadioButton) { changed(value) }
                    .semantics {
                        selected = isSelected
                        role = Role.RadioButton
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                        if (!enabled) disabled()
                    }
                    .testTag("policy:${value.name.lowercase()}").padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) GraycieText else GraycieMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, setQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = setQuery,
        placeholder = { Text("Search apps or packages") },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_tabler_search_filled), contentDescription = null)
        },
        trailingIcon = if (query.isEmpty()) null else {{
            IconButton(onClick = { setQuery("") }) {
                Icon(
                    painterResource(R.drawable.ic_tabler_x_filled),
                    contentDescription = "Clear search",
                )
            }
        }},
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GraycieSurface,
            unfocusedContainerColor = GraycieSurface,
            focusedBorderColor = GraycieAmber,
            unfocusedBorderColor = GraycieBorder,
            focusedTextColor = GraycieText,
            unfocusedTextColor = GraycieText,
            focusedLeadingIconColor = GraycieAmberSoft,
            unfocusedLeadingIconColor = GraycieMuted,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("app-search"),
    )
}

@Composable
private fun AppsHeader(selected: Int, loading: Boolean, refresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Installed apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.weight(1f))
        Text("$selected selected", color = GraycieMuted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = refresh, enabled = !loading) { Text("Refresh") }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    manager: ManagerSnapshot,
    snoozeMode: Boolean,
    busy: Boolean,
    toggle: (Boolean) -> Unit,
    openSafely: (String) -> Unit,
) {
    val view = LocalView.current
    val own = app.packageName == manager.applicationId
    val selectable = !own && !app.isHome && !busy
    val checked = !own && !app.isHome && app.packageName in if (snoozeMode)
        manager.snoozePackages else manager.selectedPackages
    val detail = when {
        own -> "${app.packageName}\nAlways in color"
        app.isHome -> "${app.packageName}\nHome and Overview stay grayscale"
        else -> app.packageName
    }
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = selectable,
            role = Role.Checkbox,
            onValueChange = { selected ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                toggle(selected)
            },
        ).semantics {
            stateDescription = if (checked) "Selected" else "Not selected"
            if (!selectable) disabled()
        }.testTag("app:${app.packageName}").padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon == null) {
            Box(
                Modifier.size(44.dp).clip(ContinuousSquircle).background(GraycieSurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_tabler_apps_filled),
                    contentDescription = null,
                    tint = GraycieMuted,
                    modifier = Modifier.size(23.dp),
                )
            }
        } else {
            Image(
                app.icon.asImageBitmap(),
                contentDescription = "${app.label} icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = GraycieMuted)
        }
        if (snoozeMode && checked) {
            IconButton(
                onClick = { openSafely(app.packageName) },
                enabled = !busy && manager.ready && manager.status == ManagerStatus.ACTIVE,
                modifier = Modifier.testTag("open-safely:${app.packageName}"),
            ) {
                Icon(
                    painterResource(R.drawable.ic_tabler_external_link_filled),
                    contentDescription = "Open ${app.label} safely",
                    tint = GraycieAmberSoft,
                )
            }
        }
        SquircleSelector(checked, selectable, app.packageName)
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = GraycieBorder.copy(alpha = 0.55f))
}

@Composable
private fun SquircleSelector(checked: Boolean, enabled: Boolean, packageName: String) {
    Box(
        modifier = Modifier.size(34.dp).clip(ContinuousSquircle)
            .background(if (checked) GraycieAmber else GraycieSurfaceRaised)
            .border(1.dp, if (checked) GraycieAmber else GraycieBorder, ContinuousSquircle)
            .alpha(if (enabled) 1f else 0.45f).testTag("select:$packageName"),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_tabler_check_filled),
                contentDescription = null,
                tint = GraycieBackground,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun SetupScreen(
    manager: ManagerSnapshot,
    error: String?,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).testTag("setup-screen"),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item("header") { ScreenHeader("Setup", onBack) }
        item("intro") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    "Both permissions stay under your control and can be removed at any time.",
                    color = GraycieMuted,
                )
            }
        }
        error?.let { message ->
            item("error") {
                Text(
                    message,
                    color = GraycieError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("manager-error"),
                )
            }
        }
        item("secure-settings") {
            SetupStep(
                number = "01",
                title = "Allow secure settings",
                ready = manager.permissionGranted,
            ) {
                Text(
                    "Connect your phone to a computer with ADB installed. Enable USB debugging, authorize it, then run this one-time command:",
                    color = GraycieMuted,
                )
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(
                        manager.adbCommand,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = GraycieText,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(GraycieBackground).padding(14.dp).testTag("adb-command"),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GraycieButton(onClick = onCopy, modifier = Modifier.testTag("copy-command")) {
                        Icon(
                            painterResource(R.drawable.ic_tabler_copy_filled),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text("Copy Command")
                    }
                    GraycieButton(onClick = onRefresh, modifier = Modifier.testTag("check-again")) {
                        Icon(
                            painterResource(R.drawable.ic_tabler_refresh_filled),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text("Check Again")
                    }
                }
            }
        }
        item("accessibility") {
            SetupStep(
                number = "02",
                title = "Enable accessibility",
                ready = manager.serviceEnabled,
            ) {
                Text(
                    "Graycie uses only the foreground app name. It does not read screen content, perform gestures, or send data. Enable Graycie under installed apps in Accessibility settings.",
                    color = GraycieMuted,
                )
                Spacer(Modifier.height(14.dp))
                GraycieButton(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.testTag("open-accessibility"),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_tabler_settings_filled),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Open Accessibility Settings")
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(R.drawable.ic_tabler_external_link_filled),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        item("privacy") {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xFF102D4F)).padding(16.dp)
                    .testTag("information-block"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_tabler_info_circle_filled),
                        contentDescription = "Information",
                        tint = Color(0xFF8CC8FF),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "For Your Information",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF8CC8FF),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "While enabled, Graycie switches Android Color correction between monochromacy and off. It replaces any existing correction mode. Your selections and app activity remain on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD8EBFF),
                )
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    ready: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)).background(GraycieSurface)
            .border(1.dp, GraycieBorder, RoundedCornerShape(24.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = GraycieAmber)
            Spacer(Modifier.width(11.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(
                    if (ready) Color(0xFF314232) else GraycieSurfaceRaised
                ).padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    if (ready) "Ready" else "Needed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ready) Color(0xFFB9E3BB) else GraycieMuted,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}
