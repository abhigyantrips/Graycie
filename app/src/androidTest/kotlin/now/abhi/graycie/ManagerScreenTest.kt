package now.abhi.graycie

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import now.abhi.graycie.ui.GraycieTheme
import now.abhi.graycie.ui.ManagerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManagerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeIsMinimalAndShowsRainbowWordmarkWhenOff() {
        setContent(snapshot())
        compose.onNodeWithTag("master-switch").assertIsOff()
        compose.onNodeWithTag("graycie-wordmark").assertTextEquals("Graycie")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Rainbow"))
        compose.onNodeWithTag("graycie-is").assertTextEquals("is")
        compose.onNodeWithTag("open-setup").assertHasClickAction()
        val wordmark = compose.onNodeWithTag("graycie-wordmark").fetchSemanticsNode().boundsInRoot
        val connector = compose.onNodeWithTag("graycie-is").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("master-switch").fetchSemanticsNode().boundsInRoot
        assertTrue(wordmark.bottom <= connector.top && connector.bottom <= toggle.top)
        assertTrue(kotlin.math.abs(wordmark.center.x - toggle.center.x) < 2f)
        compose.onNodeWithTag("graycie-mascot").assertDoesNotExist()
        compose.onNodeWithText("Color, on your terms.").assertDoesNotExist()
        compose.onNodeWithText("A calmer screen is one tap away.").assertDoesNotExist()
        compose.onNodeWithText("Make it yours").assertDoesNotExist()
        compose.onNodeWithText("Made for a quieter screen", substring = true).assertDoesNotExist()
    }

    @Test fun homeShowsOnStateWithDesaturatedWordmark() {
        setContent(snapshot().copy(managerEnabled = true))
        compose.onNodeWithTag("master-switch").assertIsOn()
        compose.onNodeWithTag("graycie-wordmark")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Desaturated"))
    }

    @Test fun featureCardsNavigateAndBackReturnsHome() {
        setContent(snapshot())
        compose.onNodeWithTag("feature-grayscale").performClick()
        compose.onNodeWithTag("grayscale-control-screen").assertExists()
        compose.onNodeWithTag("selector-question")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag("navigate-back").performClick()
        compose.onNodeWithTag("home-screen").assertExists()

        compose.onNodeWithTag("feature-auto-snooze").performScrollTo().performClick()
        compose.onNodeWithTag("auto-snooze-screen").assertExists()
    }

    @Test fun nightLightIsDisabledAndHasNoDestination() {
        setContent(snapshot())
        compose.onNodeWithTag("feature-night-light").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("soon :tm:").assertExists()
    }

    @Test fun missingPrerequisitesRouteMasterToSetupWithNewAdbCommand() {
        setContent(snapshot(permission = false, service = false))
        compose.onNodeWithTag("master-switch").performClick()
        compose.onNodeWithTag("setup-screen").assertExists()
        compose.onNodeWithText("Graycie is ready.").assertDoesNotExist()
        compose.onNodeWithText("Two small steps", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Both permissions stay under your control", substring = true).assertExists()
        compose.onNodeWithTag("information-block").performScrollTo().assertExists()
        compose.onNodeWithText("for your information").assertExists()
        compose.onNodeWithContentDescription("Information").assertExists()
        compose.onNodeWithText(
            "adb shell pm grant now.abhi.graycie android.permission.WRITE_SECURE_SETTINGS"
        ).assertExists()
    }

    @Test fun exactPolicyLabelsMapToExistingPolicies() {
        var policy: Policy? = null
        setContent(snapshot(), setPolicy = { policy = it })
        compose.onNodeWithTag("feature-grayscale").performClick()
        compose.onNodeWithText("Only These").assertExists()
        compose.onNodeWithTag("policy:only_selected").assertIsSelected()
        compose.onNodeWithText("Except These").performClick()
        assertEquals(Policy.EXCEPT_SELECTED, policy)
    }

    @Test fun searchUsesLabelAndPackageAndSelectionIsImmediate() {
        var selection: Pair<String, Boolean>? = null
        compose.setContent {
            var state by remember {
                mutableStateOf(
                    ManagerUiState(
                        snapshot(),
                        listOf(app("Browser", "test.browser"), app("Camera", "test.camera")),
                        loadingApps = false,
                    )
                )
            }
            GraycieTheme {
                ManagerContent(
                    state = state,
                    setQuery = { state = state.copy(query = it) },
                    setEnabled = {},
                    setPolicy = {},
                    togglePackage = { name, checked -> selection = name to checked },
                    refresh = {}, refreshApps = {}, openAccessibilitySettings = {},
                )
            }
        }
        compose.onNodeWithTag("feature-grayscale").performClick()
        compose.onNodeWithTag("app-search").performTextInput("test.browser")
        compose.onNodeWithText("Camera").assertDoesNotExist()
        compose.onNodeWithTag("app:test.browser").performClick()
        assertEquals("test.browser" to true, selection)
    }

    @Test fun autoSnoozeSuccessUsesConfirmationSnackbar() {
        setContent(
            snapshot = snapshot().copy(notificationReady = true),
            apps = listOf(app("Bank", "bank")),
            toggleSnooze = { _, _ -> true },
        )
        compose.onNodeWithTag("feature-auto-snooze").performScrollTo().performClick()
        compose.onNodeWithTag("app:bank").performClick()
        compose.onNodeWithText("Auto-snooze enabled for Bank.").assertIsDisplayed()
    }

    @Test fun autoSnoozeFailureUsesExplanatorySnackbar() {
        setContent(
            snapshot = snapshot().copy(notificationReady = true),
            apps = listOf(app("Vault", "vault")),
            toggleSnooze = { _, _ -> false },
        )
        compose.onNodeWithTag("feature-auto-snooze").performScrollTo().performClick()
        compose.onNodeWithTag("app:vault").performClick()
        compose.onNodeWithText("Recovery notifications are unavailable", substring = true).assertIsDisplayed()
    }

    @Test fun autoSnoozeDisableSnackbarAndOpenSafelyWork() {
        var opened: String? = null
        setContent(
            snapshot = snapshot().copy(
                managerEnabled = true,
                status = ManagerStatus.ACTIVE,
                snoozePackages = setOf("bank"),
                notificationReady = true,
            ),
            apps = listOf(app("Bank", "bank")),
            toggleSnooze = { _, _ -> true },
            openSafely = { opened = it },
        )
        compose.onNodeWithTag("feature-auto-snooze").performScrollTo().performClick()
        compose.onNodeWithTag("open-safely:bank").performClick()
        assertEquals("bank", opened)
        compose.onNodeWithTag("app:bank").performClick()
        compose.onNodeWithText("Auto-snooze disabled for Bank.").assertIsDisplayed()
    }

    @Test fun snoozedHomeKeepsSwitchOnAndOffersResume() {
        var resumes = 0
        setContent(
            snapshot = snapshot(service = false).copy(
                managerEnabled = true,
                snoozedForPackage = "bank",
                status = ManagerStatus.SNOOZED,
            ),
            apps = listOf(app("Bank", "bank")),
            resume = { resumes++ },
        )
        compose.onNodeWithTag("master-switch").assertIsOn()
        compose.onNodeWithText("Snoozed for Bank").assertExists()
        compose.onNodeWithTag("resume-snooze").performClick()
        assertEquals(1, resumes)
    }

    @Test fun autoSnoozeExplanationHasNoAddedHeading() {
        setContent(snapshot())
        compose.onNodeWithTag("feature-auto-snooze").performScrollTo().performClick()
        compose.onNodeWithText("Give sensitive apps a clear path.").assertDoesNotExist()
        compose.onNodeWithTag("snooze-explanation").assertExists()
    }

    @Test fun authorNameAloneOpensAuthorUriAndFooterIsHomeOnly() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                GraycieTheme {
                    ManagerContent(
                        state = ManagerUiState(snapshot(), emptyList(), loadingApps = false),
                        setQuery = {}, setEnabled = {}, setPolicy = {}, togglePackage = { _, _ -> },
                        refresh = {}, refreshApps = {}, openAccessibilitySettings = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Made to just work, by ").assertHasNoClickAction()
        compose.onNodeWithTag("author-link").performClick()
        assertEquals(listOf("https://abhi.now"), opened)
        compose.onNodeWithTag("feature-grayscale").performScrollTo().performClick()
        compose.onNodeWithTag("author-link").assertDoesNotExist()
    }

    @Test fun ownAppAndHomeAreDisabled() {
        setContent(
            snapshot = snapshot(selected = setOf("now.abhi.graycie", "launcher")),
            apps = listOf(app("Graycie", "now.abhi.graycie"), app("Home", "launcher", home = true)),
        )
        compose.onNodeWithTag("feature-grayscale").performClick()
        compose.onNodeWithTag("app:now.abhi.graycie").assertIsNotEnabled()
        compose.onNodeWithTag("app:launcher").assertIsNotEnabled()
        compose.onNodeWithText("Always in color", substring = true).assertExists()
    }

    @Test fun narrowTwoHundredPercentTextRemainsScrollable() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.width(280.dp)) {
                    GraycieTheme {
                        ManagerContent(
                            state = ManagerUiState(snapshot(), listOf(app("Browser", "browser")), loadingApps = false),
                            setQuery = {}, setEnabled = {}, setPolicy = {}, togglePackage = { _, _ -> },
                            refresh = {}, refreshApps = {}, openAccessibilitySettings = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("feature-grayscale").performScrollTo().performClick()
        compose.onNodeWithTag("app:browser").performScrollTo().assertExists()
        compose.onNodeWithTag("selector-question").assertExists()
    }

    private fun setContent(
        snapshot: ManagerSnapshot,
        apps: List<InstalledApp> = emptyList(),
        setEnabled: (Boolean) -> Unit = {},
        setPolicy: (Policy) -> Unit = {},
        toggle: (String, Boolean) -> Unit = { _, _ -> },
        toggleSnooze: (String, Boolean) -> Boolean = { _, _ -> true },
        openSafely: (String) -> Unit = {},
        resume: () -> Unit = {},
    ) {
        compose.setContent {
            GraycieTheme {
                ManagerContent(
                    state = ManagerUiState(snapshot, apps, loadingApps = false),
                    setQuery = {}, setEnabled = setEnabled, setPolicy = setPolicy,
                    togglePackage = toggle, toggleSnoozePackage = toggleSnooze,
                    openSafely = openSafely, resumeFromSnooze = resume,
                    refresh = {}, refreshApps = {}, openAccessibilitySettings = {},
                )
            }
        }
    }

    private fun snapshot(
        permission: Boolean = true,
        service: Boolean = true,
        selected: Set<String> = emptySet(),
    ) = ManagerSnapshot(
        "now.abhi.graycie", permission, service, false, Policy.ONLY_SELECTED,
        selected, null, null, null,
    )

    private fun app(label: String, packageName: String, home: Boolean = false) =
        InstalledApp(packageName, label, null, home)
}
