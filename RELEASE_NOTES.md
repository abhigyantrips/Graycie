# Graycie identity migration

Graycie uses the new Android identity `now.abhi.graycie`. It is not an in-place update to `dev.grayscale.manager`.

Before installing Graycie:

1. Disable the old app's accessibility service.
2. Uninstall the old app.
3. Install Graycie.
4. Grant `WRITE_SECURE_SETTINGS` to `now.abhi.graycie` with the ADB command shown in Setup.
5. Enable Graycie in Android Accessibility settings and configure selections again.

Preferences, the ADB grant, and accessibility enablement cannot transfer across application IDs. The version code and version name remain unchanged for this release.
