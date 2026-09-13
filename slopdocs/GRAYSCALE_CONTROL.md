# Grayscale control

## Master control

The Home screen provides one accessible OFF/ON control for grayscale management. Enabling it starts policy-based switching once setup is complete. Disabling it immediately restores color and stops responding to foreground changes.

Graycie itself always remains in color so its controls stay usable. The currently resolved Home app is always treated as grayscale and cannot be selected as an ordinary app.

## App policies

The Grayscale Control screen implements two policies:

- **Only These** makes selected apps grayscale and leaves other settled apps in color.
- **Except These** makes selected apps stay in color and makes other settled apps grayscale.

The selected package set is shared by both policies. Switching policy does not discard selections, and policy or selection changes are evaluated immediately against the current settled destination.

## App selection

The selector shows launchable apps for the current user with their actual icon and localized label. Users can search by app name or package name. Selected apps remain pinned above unselected results while preserving locale-aware ordering within each group.

Graycie and the active Home app are excluded from effective selection. The catalog also includes launcher packages that may not expose a normal app-drawer entry, which allows Home to be identified without hard-coded vendor package names.

## Special destinations

Home is a first-class policy destination and is always grayscale. Launcher-hosted Overview is treated the same way. If an OEM exposes Overview only as an indeterminate System UI surface, Graycie retains the current color rather than guessing.
