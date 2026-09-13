# App experience

## Navigation and screens

Graycie is a native Jetpack Compose interface with four saved destinations inside one scaffold:

- **Home** contains the brand, master control, feature cards, current snooze recovery, and Setup shortcut.
- **Grayscale Control** contains policy and grayscale app selection.
- **Auto-Snooze Apps** contains compatibility selection and Open safely actions.
- **Setup** presents prerequisite status and setup actions.

Android Back returns feature screens to Home. Leaving either app list clears search and keyboard focus. Forward and return navigation use direction-aware slide/fade transitions; transitions are removed when the system animator duration scale is zero.

## Feedback and resilience

The interface reflects disabled, active, snoozing, snoozed, and resuming states. Setup problems, app-catalog failures, rejected changes, snooze progress, and recovery outcomes are surfaced through inline status or the shared snackbar host.

The master switch uses a longer haptic response, while adding or removing an app uses a short tap response. Both go through Android's standard view feedback API and therefore follow the phone's system haptics setting.

The app catalog reloads when the activity resumes, along with prerequisite status. Loading runs off the main thread, icons are converted to bounded bitmaps, and stale asynchronous results cannot replace a newer load.

## Visual and accessible design

The UI uses a fixed warm-dark Material 3 palette and forces dark system bars independently of the device theme. Atkinson Hyperlegible is used for interface text; Chat Favour is reserved for the centered Home wordmark and lowercase brand glyph. Vendored Tabler Filled assets are used for interface icons, while installed apps retain their real icons.

The Home wordmark is rainbow while management is off and becomes a desaturated tone while it is on. The master is exposed as one switch to accessibility services, policy tabs expose selection state, headings are marked semantically, disabled features report their disabled state, and icon actions have meaningful labels.

Night Light Control is currently a disabled presentation card with a muted `soon™` badge, not an implemented control.
