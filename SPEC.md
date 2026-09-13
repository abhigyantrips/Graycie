# Graycie — Native Android Specification

## Goal and scope

Graycie is an Android-only Kotlin and Jetpack Compose utility. It applies Android's monochromacy color correction according to the focused launchable app. It supports API 26+, stores configuration locally, and uses no DI or navigation framework.

The application ID and namespace are `now.abhi.graycie`; the accessibility component is `now.abhi.graycie.ForegroundAccessibilityService`. This is a new Android identity and cannot upgrade `dev.grayscale.manager`: its preferences, secure-settings grant, and accessibility enablement do not transfer. The `grayscale_manager` preference schema remains unchanged inside Graycie's fresh sandbox.

## Policy

`ONLY_SELECTED` makes selected packages grayscale. `EXCEPT_SELECTED` makes every settled launchable app except selected packages grayscale. Policy and selection changes take effect immediately and selected packages do not change when policy changes.

The manager package is always color and non-selectable. The currently resolved Home package is non-selectable. Settled Home and launcher-hosted Overview are always grayscale.

## Setup and secure settings

The manifest declares `WRITE_SECURE_SETTINGS`, `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED`; the accessibility service is protected by `BIND_ACCESSIBILITY_SERVICE`. Secure settings is granted explicitly through ADB. Notification permission is requested only when the first Auto-snooze app is selected on Android 13+, and a denied selection is not persisted. Both the secure-settings grant and enabled accessibility service are required before management can start.

The generated onboarding command uses the runtime application ID:

```sh
adb shell pm grant now.abhi.graycie android.permission.WRITE_SECURE_SETTINGS
```

Grayscale is enabled by writing mode `0` to `accessibility_display_daltonizer`, then `1` to `accessibility_display_daltonizer_enabled`. Color and cleanup write `0` to the enabled setting. Unchanged provider values are not rewritten. Provider failures disable management, attempt one safe cleanup when possible, and surface an error.

## Accessibility compatibility snooze

Grayscale selection and Auto-snooze selection are independent persisted package sets. The manager package and resolved Home package are excluded from both snooze selection and event triggering. `managerEnabled` remains true while `snoozedForPackage` is present. Snapshots expose disabled, active, snoozing, snoozed, and resuming states; only the stable snooze intent is persisted.

`TYPE_WINDOW_STATE_CHANGED` is inspected before normal debouncing. A configured package can trigger only while management is active and recovery notifications are available. Recovery is posted, color cleanup is attempted, and `disableSelf()` removes automatic accessibility access. Duplicate events and lifecycle disconnects retain the same persisted snooze without repeating cleanup. Cleanup failure is displayed but does not prevent disabling accessibility.

The default-importance `snooze_recovery_active` notification alerts when snooze begins, remains ongoing, and is restored after boot or package replacement. Its immutable explicit **Resume** broadcast `PendingIntent` targets the private `SnoozeRecoveryReceiver`, which invokes the existing controller recovery directly; no activity is required. The body `PendingIntent` only launches Graycie and never resumes implicitly. The live `ENABLED_ACCESSIBILITY_SERVICES` value is parsed and Graycie's component is appended only when absent; ordering and all unrelated services are preserved. A malformed value is left completely untouched and reported as a recoverable failure. `ACCESSIBILITY_ENABLED=1` is then written. Snooze state and notification clear only after `onServiceConnected`. Refusal, permission loss, or an eight-second bind timeout leaves recovery available and exposes Accessibility settings. A timer and another app's foreground state can never initiate resume.

Open safely persists recovery, restores color, removes only this component from the current enabled-services value, confirms removal, and only then launches the resolved launcher activity. Failure to confirm aborts launch. Turning the master switch off while snoozed clears recovery without re-enabling accessibility. A manual re-enable through Android settings is accepted when the service reconnects.

## Foreground classification

Only `AccessibilityWindowInfo` metadata and root package names are copied into the pure classifier. No text, title, node content, gesture, screenshot, usage access, overlay, polling loop, or foreground service is used.

Window events use a 250 ms trailing debounce. A destination is applied only after two matching snapshots 100 ms apart. If multiple non-PiP applications remain layered, the current color is retained while checks repeat every 100 ms for up to one second. A settled destination is then applied; a persistently layered surface is treated as an overlay and produces no settings write. A newer event cancels the pending candidate and older checks. A split-screen divider allows the focused app to settle, and PiP windows do not block settlement. Missing roots, non-launchable transients, and opaque System UI surfaces retain current state.

Home is a first-class destination rather than an ignored window. It settles only when the resolved Home package owns focus or activity without another non-PiP application window. This prevents a launcher revealed temporarily during a navigation gesture from changing color. Overview is grayscale when it is hosted by the resolved Home package; OEM Overview implementations exposed only as System UI remain indeterminate.

Home is resolved from `RoleManager.ROLE_HOME` on API 29+ and the default Home intent on API 26–28. No launcher, Google, Gemini, assistant, or System UI package names are hard-coded. Debug classification logs contain package names and reasons only.

## UI and state

`ManagerController` serializes mutations on the main thread and exposes a read-only `StateFlow<ManagerSnapshot>`. `ManagerViewModel` loads apps and bounded bitmap icons on `Dispatchers.IO`, tracks search/loading/errors/mutations, and ignores stale asynchronous results. Resume refreshes prerequisites and the catalog.

The fixed dark Material 3 UI uses background `#12100E`, surfaces `#1B1815` and `#25211D`, text `#F5EFE7` / `#B9B0A6`, and borders `#403832`. Dark system bars are forced regardless of system appearance. Bold Atkinson Hyperlegible is used for screen titles/headings and regular Atkinson for body/control text. Unmodified Chat Favour Regular is used only for the centered Home wordmark and lowercase-`a` brand glyph. Required Tabler Filled assets are vendored under their MIT license; real app icons remain intact. The Home wordmark is rainbow while OFF and fades to a single desaturated tone while ON. The master control is a borderless two-segment rounded tab with gray OFF and green ON states; the existing app selector controls retain their approved continuous-superellipse shape.

One scaffold and snackbar host contain saved Home, Grayscale Control, Auto-Snooze Apps, and Setup destinations. Direction-aware 280 ms slide/fade transitions move forward left and return right, or are removed when the global animator duration scale is zero. Search and keyboard focus clear when leaving either list. The Home master is one accessible switch; incomplete activation routes to Setup. Grayscale Control maps **Only These** and **Except These** to the existing policies. Auto-snooze retains independent selections, permission gating, success/failure snackbars, and Open safely. The snoozed master stays checked and provides the triggering app plus in-app Resume. Night Light is disabled presentation only.

Privacy remains local: snooze detection uses only the package name already present on accessibility window-state events. The app never reads view text or content, and secure accessibility edits are scoped to its own component. `isAccessibilityTool` remains false and window retrieval remains enabled only while the service is active.

## Out of scope

Root, Shizuku onboarding, schedules, lock-screen policy, work profiles, secondary users, cloud sync, analytics, accounts, database storage, gestures, overlays, and screen-content inspection remain out of scope.
