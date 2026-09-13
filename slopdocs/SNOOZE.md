# Auto-snooze compatibility

## Motivation

Some banking and security-sensitive apps reject any enabled accessibility service, even one that reads only foreground package metadata. Auto-snooze gives users an explicit, local compatibility list without changing their grayscale policy. When a listed app opens, Graycie restores color and removes its accessibility service while preserving the master configuration.

## Two entry paths

Automatic detection handles `TYPE_WINDOW_STATE_CHANGED` before the normal classifier. It uses `AccessibilityService.disableSelf()` because this is Android's supported way for a service to remove itself without editing other services. Detection is inherently a race: a banking app may perform its check before Graycie receives the event.

Open safely avoids that race. It prepares recovery, restores color, removes only Graycie from the live secure enabled-services list, confirms removal, and then resolves and launches the target. A failed confirmation aborts launch.

## Recovery and user intent

Android does not provide a normal app API for silently enabling an accessibility service. This build assumes the user separately granted `WRITE_SECURE_SETTINGS` through ADB. Even with that grant, re-enable is performed only after the notification **Resume** action or the in-app Resume button. There is no timer and no foreground-based automatic resume.

The notification action is an immutable explicit broadcast `PendingIntent` to Graycie's private recovery receiver. It reads the current `ENABLED_ACCESSIBILITY_SERVICES` string and appends this component only if absent without opening `MainActivity`. It never writes a cached list and never removes, reorders, or reconstructs TalkBack or another service. A malformed list is retained byte-for-byte and treated as a recoverable failure. `ACCESSIBILITY_ENABLED` is set to `1`, then recovery remains pending until `onServiceConnected` confirms the bind. The notification body is a normal Graycie launch and never resumes implicitly.

## Notification and failure behavior

The low-importance `snooze_recovery` notification is ongoing and identifies the triggering app. Notifications are mandatory before starting a snooze. Android 13+ permission is requested on the first selection; denial does not persist that selection. Disabled app notifications or a disabled channel prevents accessibility from being disabled and stores a warning for the next app launch.

Boot and package-replaced broadcasts repost recovery from persisted `snoozedForPackage`. The main screen duplicates the Resume action so delivery problems cannot strand the user. Provider refusal, malformed entries, lost secure-settings permission, or bind timeout keeps snooze and recovery intact and offers the existing Accessibility settings route. Color-cleanup failure is reported, but after recovery exists accessibility is still disabled for compatibility. Turning the master switch off clears recovery without re-enabling.

## Privacy, security, and OEM limits

Only the window event's package name is compared with the local set. No screen text, view nodes, gestures, screenshots, network traffic, analytics, or account data is involved. The accessibility configuration boundary recognizes and edits only `now.abhi.graycie.ForegroundAccessibilityService`; all unrelated tokens remain untouched. `isAccessibilityTool=false` remains declared.

OEMs can delay window events, accessibility-list propagation, service binding, notifications, or launcher resolution. Those limitations make automatic detection best-effort and can require Open safely or manual Accessibility settings. Multi-user/work-profile support is outside the current scope.

## State transitions

```text
DISABLED --master on + prerequisites--> ACTIVE
ACTIVE --configured window / Open safely--> SNOOZING
SNOOZING --recovery posted + access off--> SNOOZED
SNOOZED --notification or in-app Resume--> RESUMING
RESUMING --onServiceConnected--> ACTIVE
RESUMING --write refusal / bind timeout--> SNOOZED
SNOOZED --master off--> DISABLED (access stays off)
ACTIVE --unexpected service removal--> DISABLED + color cleanup
```

Only `managerEnabled`, both package selections, and `snoozedForPackage` are persisted. SNOOZING and RESUMING are transient projections.

## Manual checklist

- Upgrade from 1.1.0 and verify policy/selection retention.
- On API 26, 33, and target API, select a snooze app; deny and later grant notification permission on API 33+.
- Disable notifications and the recovery channel independently; confirm no snooze begins.
- Open a configured banking app normally and record whether automatic detection wins the app's check.
- Use Open safely and confirm accessibility is absent before the banking activity appears.
- Confirm TalkBack or a second service remains present and ordered after safe open and resume.
- Resume from both notification and main screen; simulate secure-write refusal and bind timeout.
- Re-enable manually in Accessibility settings and confirm recovery clears on connection.
- Reboot and update the package while snoozed; confirm recovery is reposted.
- Revoke notification and secure-settings permissions while configured and verify warnings.
- Turn the master switch off while snoozed and confirm accessibility is not re-enabled.
- Test narrow screens, 200% font scale, search, semantics, and screen-reader labels.
