# Graycie

Graycie is a native Android app that switches Android Color correction according to the foreground app. It has a local four-destination Jetpack Compose interface and a Kotlin `AccessibilityService`; it has no Flutter runtime, network feature, analytics, account, overlay, or database.

Two policies are available:

- **Only selected apps:** selected apps are grayscale and other apps are color.
- **Except selected apps:** selected apps are color and other apps are grayscale.

Graycie itself always stays in color. Home and launcher-hosted Overview stay grayscale, and the active Home app cannot be selected.

Opt-in **Auto-snooze** supports apps, such as banking apps, that refuse to run while an accessibility service is enabled. Automatic detection restores color, leaves an ongoing recovery notification, and calls Android's `disableSelf()`. **Open safely** is the reliable path: it disables and confirms accessibility before launching the selected app. Management stays logically enabled until the user explicitly resumes or turns it off.

The app identity is `now.abhi.graycie`. It is intentionally distinct from the former `dev.grayscale.manager` application: uninstall or disable the old app first, install Graycie, grant secure settings again, re-enable accessibility, and configure selections again. Preferences and grants do not transfer between application IDs.

## Build

Requirements are JDK 17 and an Android SDK with API 37 installed. Set only `sdk.dir` in the machine-local `local.properties` file.

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

The project uses Gradle 9.3.1, AGP 9.1.1 with built-in Kotlin, Kotlin/Compose compiler plugin 2.4.0, Compose BOM 2026.08.00, `compileSdk 37`, `targetSdk 36`, and `minSdk 26`. Release builds retain the previous debug-key signing behavior until distribution signing is configured. Version 1.2.0 has version code 3.

## Device setup

1. Install the app, enable USB debugging, and authorize the computer.
2. Run the command shown in the setup card:

   ```sh
   adb shell pm grant now.abhi.graycie android.permission.WRITE_SECURE_SETTINGS
   ```

3. Tap **Check again**, open Accessibility settings, and enable **Graycie**.
4. Choose a policy, select apps, and enable management.
5. For compatibility snoozing, open **Auto-Snooze Apps**, select apps, and allow notifications when prompted. Use **Open safely** beside a configured app to launch it after accessibility is confirmed off.

Both prerequisites gate activation of the master control; tapping it while setup is incomplete opens **Setup**. Setup can also be opened with the bare settings button at the top-right of Home. The grayscale policy is chosen with the exact **Only These** / **Except These** control above app search. Policy and selection changes apply immediately and remain selected when the policy changes. In both app lists, selected apps are pinned to the top.

While enabled, the app takes exclusive control of Android Color correction. It selects monochromacy before enabling correction for grayscale and disables correction for color. Disabling management also turns Color correction off; an existing correction mode is not preserved.

When snoozed, press the notification **Resume** action or the in-app **Resume** button. The notification action targets Graycie's private receiver and begins recovery directly, even while the activity is closed. Tapping the notification body only opens Graycie and does not resume. Recovery appends only Graycie's component to the current secure enabled-services list and waits for the service to reconnect. If recovery fails, snooze and its notification remain available. No timer or foreground-app change can initiate recovery.

## Architecture

- `ManagerController` owns the existing `grayscale_manager` preferences and publishes typed `ManagerSnapshot` values through a read-only `StateFlow`.
- `ManagerViewModel` collects native state, loads the localized app catalog on `Dispatchers.IO`, bounds icons as Android `Bitmap` objects, rejects stale loads, and holds transient UI state.
- `PolicyEngine` and `SecureGrayscaleSettings` remain the tested policy and provider boundaries.
- `SnoozeCoordinator`, `SecureAccessibilitySettings`, and `SnoozeNotificationManager` enforce snooze/recovery ordering while preserving every unrelated accessibility component.
- `ForegroundAccessibilityService` debounces events for 250 ms and passes window metadata to a pure Kotlin classifier. Destinations are confirmed by two stable samples; layered app transitions are rechecked every 100 ms for at most one second. Home/Overview, persistent overlays, PiP, and split-screen are handled without inspecting window content.
- `ui/ManagerScreen.kt` provides saved Home, Grayscale Control, Auto-Snooze Apps, and Setup destinations in one scaffold. It uses the fixed warm-dark palette, reduced-motion-aware transitions, rounded shadcn-style CTAs, and accessible controls. The existing selector controls retain their continuous-corner shape.

Unmodified Chat Favour Regular, Atkinson Hyperlegible, and the vendored Tabler Filled icons are bundled with their license/readme notices under `app/src/main/assets/licenses/`. Chat Favour is limited to the Home wordmark and lowercase-`a` brand glyph; interface headings use bold Atkinson. Installed applications continue to use their real launcher icons.

The active Home application is resolved with `RoleManager.ROLE_HOME` on API 29+ and default-Home intent resolution on API 26–28. No launcher, assistant, or vendor package is hard-coded. Debug builds log package names and classifier reasons only.

## Verification

```sh
./gradlew lint testDebugUnitTest assembleDebug assembleRelease
# With an emulator/device:
./gradlew connectedDebugAndroidTest
```

See [docs/TESTING.md](docs/TESTING.md) for device scenarios, [SPEC.md](SPEC.md) for behavior and privacy constraints, and [slopdocs/SNOOZE.md](slopdocs/SNOOZE.md) for the compatibility design and checklist.

## References

- [AGP built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Compose compiler and dependencies](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Android accessibility services](https://developer.android.com/guide/topics/ui/accessibility/service)
- [AOSP ColorDisplayService](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/display/color/ColorDisplayService.java)
