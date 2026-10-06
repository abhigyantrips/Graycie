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

While management is active, Graycie controls Android's Color correction setting directly. To apply grayscale it selects monochromacy first and then enables Color correction. Color destinations use the saved non-grayscale mode and its saved on/off state, so an existing correction for color blindness stays available. If the saved mode is grayscale, color destinations disable correction instead. Changing from grayscale to another correction disables correction before switching modes and then restores the saved on/off state. Values that are already correct are not rewritten.

Before changing correction, Graycie durably saves the existing mode and on/off state. Turning management off, disconnecting the service, or snoozing restores the saved settings. A newer manual correction is preserved. The saved values and pending writes survive process restarts and failed restoration attempts. A session inherited from an older build has no original backup; cleanup can only disable its recognizable grayscale.

If the secure-settings grant is missing, Android refuses a provider write, or another provider error occurs, Graycie disables normal management, attempts a safe color cleanup where possible, and shows an actionable error. Both prerequisites must be present before management can start again.

## Android integration

The app declares the secure-settings, notification, and boot-completed permissions required by its implemented features. The Accessibility service is protected by Android's binding permission and listens only for window-state and window-list changes.

Graycie's application ID is `now.abhi.graycie`. It is separate from the former `dev.grayscale.manager` identity, so an old installation's preferences, ADB grant, and Accessibility enablement do not transfer.
