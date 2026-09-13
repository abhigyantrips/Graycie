# Local state and lifecycle

## Persisted configuration

Graycie stores configuration locally in the `grayscale_manager` preferences file. The durable user intent consists of:

- whether grayscale management is enabled;
- the active grayscale policy;
- selected grayscale packages;
- selected Auto-snooze packages; and
- the package for a pending stable snooze.

The controller also records limited operational state, such as the last recognized package, last applied grayscale value, and a recoverable error. These values help avoid redundant writes and surface failures, but are reset when a service reconnection makes them unreliable.

There is no database, account, cloud sync, analytics, or network storage.

## State ownership

`ManagerController` is the single state owner. It serializes mutations on Android's main thread and publishes immutable `ManagerSnapshot` values through a read-only `StateFlow`. `ManagerViewModel` combines those snapshots with transient interface state such as search, loading, and mutation feedback.

Policy evaluation and secure-setting access are kept behind small, tested boundaries. This allows policy decisions, foreground classification, safe setting edits, and snooze transitions to be tested without depending on the full Android UI.

## Lifecycle behavior

On activity resume, Graycie marks its own screen as the current color destination, refreshes permissions and Accessibility status, and reloads the installed-app catalog.

An unexpected Accessibility service removal disables active management and attempts to restore color. A disconnect caused by an intentional snooze preserves the user's enabled preference and pending recovery instead. When the service reconnects, Graycie clears stale foreground/applied-state assumptions and completes any pending resume.

Provider failures are fail-safe: normal management is stopped, color cleanup is attempted when permission remains available, and the error is exposed to the interface. Unchanged secure-setting values are not rewritten.
