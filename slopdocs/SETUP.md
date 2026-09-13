# Setup and prerequisites

## What the feature does

Graycie has a dedicated Setup screen that guides the user through the two capabilities required for grayscale management:

1. `WRITE_SECURE_SETTINGS`, granted once through ADB.
2. Graycie's Accessibility service, enabled by the user in Android settings.

The screen generates the ADB command from the installed application ID:

```sh
adb shell pm grant now.abhi.graycie android.permission.WRITE_SECURE_SETTINGS
```

The app rechecks both prerequisites when it resumes and when the user asks it to check again. Turning on the Home master control before setup is complete routes the user to Setup instead of partially enabling management.

## Color correction ownership

While management is active, Graycie controls Android's Color correction setting directly. To apply grayscale it selects monochromacy first and then enables Color correction. To restore color it disables Color correction. Values that are already correct are not rewritten.

Turning management off performs the same color cleanup. Graycie does not save and restore a correction mode that existed before it was enabled.

If the secure-settings grant is missing, Android refuses a provider write, or another provider error occurs, Graycie disables normal management, attempts a safe color cleanup where possible, and shows an actionable error. Both prerequisites must be present before management can start again.

## Android integration

The app declares the secure-settings, notification, and boot-completed permissions required by its implemented features. The Accessibility service is protected by Android's binding permission and listens only for window-state and window-list changes.

Graycie's application ID is `now.abhi.graycie`. It is separate from the former `dev.grayscale.manager` identity, so an old installation's preferences, ADB grant, and Accessibility enablement do not transfer.
