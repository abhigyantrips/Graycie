# Implemented features

This directory breaks the [product specification](../SPEC.md) into the major features that are implemented in Graycie today. The documents describe observable behavior and the important guarantees around it; the specification remains the source of truth for exact requirements.

## Feature map

| Feature | What is implemented |
| --- | --- |
| [Setup and prerequisites](SETUP.md) | ADB secure-settings setup, Accessibility setup, prerequisite checks, and safe Color correction writes. |
| [Grayscale control](GRAYSCALE_CONTROL.md) | Master control, two selection policies, installed-app selection, immediate updates, and special handling for Graycie and Home. |
| [Foreground detection](FOREGROUND_DETECTION.md) | Accessibility-window classification, transition settling, Home and Overview handling, and overlay/PiP/split-screen behavior. |
| [Auto-snooze compatibility](SNOOZE.md) | Independent compatibility list, automatic snoozing, Open safely, recovery notification, and explicit resume. |
| [App experience](APP_EXPERIENCE.md) | Four-screen Compose interface, app catalog and search, visual system, accessibility, motion, and error feedback. |
| [Local state and lifecycle](STATE_AND_LIFECYCLE.md) | Persisted configuration, runtime states, refresh behavior, serialization, and failure cleanup. |

## Product boundaries

Graycie is a native Android app for API 26 and newer. It has no network-dependent feature, account, analytics, cloud sync, or database. Configuration remains in local preferences.

Foreground detection is limited to package-level window metadata. Graycie does not inspect screen text or node content, take screenshots, perform gestures, use an overlay, poll app usage, or run a foreground service. Its accessibility configuration changes are scoped to Graycie's own service component.

Root and Shizuku setup, schedules, lock-screen rules, work profiles, secondary users, and Night Light control are not implemented. Night Light appears in the interface only as a disabled preview of a possible future feature.
