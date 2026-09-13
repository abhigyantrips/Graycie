# Auto-snooze compatibility

## What the feature does

Some banking and security-sensitive apps refuse to run while any Accessibility service is enabled. Auto-snooze gives users a separate, opt-in compatibility list. Opening a configured app can restore color and disable Graycie's service without turning off the user's grayscale configuration.

The grayscale and Auto-snooze selections are independent. Graycie and the active Home app cannot be selected for snoozing and cannot trigger it.

## Automatic snooze and Open safely

Automatic detection checks window-state events before normal foreground debouncing. If management is active, the package is selected, and recovery notifications are available, Graycie posts recovery, restores color, and calls Android's `disableSelf()` API. Detection is best-effort because the target app may check Accessibility before Graycie receives its event.

**Open safely** is the deterministic entry path. Graycie prepares recovery, restores color, removes only its own component from the current enabled-services setting, confirms that it is gone, and only then launches the selected app. If removal cannot be confirmed, the launch is aborted.

Once snoozing begins, duplicate events and service disconnect callbacks keep the same recovery state and do not repeat cleanup.

## Notification permission and recovery

Recovery must be available before Graycie disables Accessibility. On Android 13 and newer, selecting the first Auto-snooze app requests notification permission; if permission is denied, that selection is not saved. Disabled app notifications or a disabled recovery channel prevent a new snooze and produce a warning.

While snoozed, an ongoing low-importance notification identifies the triggering app. Its **Resume** action targets a private broadcast receiver and works without opening the activity. Tapping the notification body only opens Graycie. The Home screen provides the same Resume action.

Boot and package-replacement broadcasts restore the notification for a pending snooze.

## Explicit resume

Resume is always user initiated; a timer or foreground-app change never starts it. Graycie reads the live enabled-services value and appends its component only if absent, preserving the order and spelling of every unrelated service. It then enables the global Accessibility switch and waits for `onServiceConnected` to confirm recovery.

Snooze state and the notification clear only after that connection. A refused write, missing grant, malformed enabled-services value, or eight-second bind timeout leaves recovery available and directs the user toward Accessibility settings. A manual re-enable in Android settings is also accepted when the service reconnects.

Turning the master control off while snoozed cancels recovery and leaves Accessibility disabled.

## Runtime states

```text
DISABLED -> ACTIVE -> SNOOZING -> SNOOZED -> RESUMING -> ACTIVE
```

`SNOOZING` and `RESUMING` are transient progress states. The stable snoozed package is persisted so recovery survives activity and process restarts.

## Privacy and platform limits

Detection compares only the event's package name with the local Auto-snooze set. Graycie never reads the target app's screen content. OEM timing can delay events, setting propagation, service binding, notifications, or app launch; users can fall back to Open safely or manual Accessibility settings when necessary.
