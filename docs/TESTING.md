# Verification

## Automated checks

Use JDK 17 and run from the repository root:

```sh
./gradlew lint testDebugUnitTest assembleDebug assembleRelease
```

With an emulator or device available:

```sh
./gradlew connectedDebugAndroidTest
```

JVM tests cover snooze transitions, duplicate events/disconnects, notification gating, cleanup failure, safe-launch ordering, reconnect-only recovery, and enabled-accessibility-list parsing/preservation. Compose coverage includes the minimal Home hierarchy and rainbow/desaturated ON/OFF wordmark, feature navigation/back, disabled Night Light, Setup routing and information block, the Home-only author link, policy mapping, Auto-snooze snackbars, safe launch, inline recovery UI, search, and narrow 200% text scrollability. Notification instrumentation verifies that Resume is an explicit receiver action rather than an activity launch and routes to direct recovery.

## Device setup

Disable and uninstall any old `dev.grayscale.manager` build, then install Graycie fresh. Grant secure settings, enable the Graycie accessibility component, and choose two selected apps plus one unselected app.

```sh
adb shell pm grant now.abhi.graycie android.permission.WRITE_SECURE_SETTINGS
adb shell settings get secure accessibility_display_daltonizer
adb shell settings get secure accessibility_display_daltonizer_enabled
```

Grayscale must report mode `0` and enabled `1`; color must report enabled `0`. Master-off and service cleanup must also report enabled `0`.

| Scenario | Expected |
| --- | --- |
| Install beside/over the old identity | Not an upgrade: old preferences, grant, and accessibility enablement do not transfer |
| Selected ↔ unselected | Color follows both policy modes without repeated duplicate writes |
| Manager | Always color and non-selectable |
| Completed Home gesture | Settles to grayscale after the launcher is confirmed |
| Cancelled navigation-pill gesture | Keeps the current app state without flicker |
| Launcher-hosted Overview | Settles to grayscale; selecting an app applies its policy |
| System UI-hosted Overview | Retains the current state when no portable destination can be identified |
| Launcher catalog row | Active Home is disabled and non-selectable |
| Cold or resumed app | Destination applies after settlement |
| Rapid app cancellation | Only the newest event burst can apply |
| Search overlay / keyboard | Retains current state unless a launchable destination settles |
| Notification shade | Retains current state |
| Persistent app-layer overlay | Retains current state after the retry window |
| Split screen | Focused app applies despite the divider |
| PiP over an app | PiP does not block the settled focused app |
| Lock/unlock | No crash; next settled app applies |
| Activity removed from Recents | Accessibility service continues independently |
| Policy or selection changes | Applies immediately; selections survive policy switches |
| Permission revoked | Management disables and setup/error state appears without a crash |
| Large text / narrow screen | All content remains scrollable and controls remain usable |
| First snooze selection on API 33+ | Permission prompt appears; denial does not save the selection |
| Notifications/channel disabled | Snooze refuses to disable accessibility and shows a warning |
| Automatic configured-app open | Recovery posts, color restores, then accessibility disables best-effort |
| Duplicate window/lifecycle events | One notification/cleanup; master remains enabled and snoozed |
| Open safely | Accessibility is confirmed absent before the banking activity starts |
| Safe disable refusal | Target does not launch; recovery/error remains visible |
| Notification body tap | Graycie opens but recovery does not begin |
| Notification Resume while Graycie is closed | Private receiver starts recovery with one tap; activity is not required |
| In-app Resume | Starts the same controller recovery flow |
| Resume refusal / permission loss / timeout | Snooze and notification remain; Accessibility settings is available |
| Manual accessibility re-enable | Service reconnect clears persisted snooze and notification |
| Master off while snoozed | Recovery clears; accessibility remains disabled |
| Reboot / package replacement while snoozed | Recovery notification is reposted |
| Another accessibility service enabled | TalkBack/reader entry and ordering remain unchanged |

Revoke permission for the failure test, then regrant it:

```sh
adb shell pm revoke now.abhi.graycie android.permission.WRITE_SECURE_SETTINGS
```

Force-stop is distinct from dismissing the activity and Android may skip service cleanup. If needed, restore color manually:

```sh
adb shell settings put secure accessibility_display_daltonizer_enabled 0
```

Run device coverage on API 26, API 33, and the current target API. Press notification Resume while Graycie is closed and verify the activity is not required, service reconnection completes, and the notification disappears only after connection. Exercise refusal and the eight-second timeout and confirm recovery remains. Validate at least one affected banking app and record whether automatic detection succeeds before its compatibility check or requires retry. Acceptance always requires Open safely to remove this service before the banking activity becomes foreground. Also revoke notification permission, disable the recovery channel, reboot while snoozed, and replace the Graycie package while recovery is pending.
