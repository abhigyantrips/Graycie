# fdroiddata submission

`metadata/now.abhi.graycie.yml` is the ready-to-copy metadata file for an
`fdroiddata` merge request. The localized listing, icon, screenshots, and
version-code changelog live in `fastlane/metadata/android/en-US/`, which
F-Droid imports from the tagged source tree.

Before submitting:

1. Make `https://github.com/abhigyantrips/Graycie` publicly accessible.
2. Commit the complete readiness changes and tag that commit `v1.2.0`.
3. Verify the tag from a clean clone, including the unsigned release APK.
4. Copy the YAML file into `fdroiddata/metadata/` and run `fdroid lint`,
   `fdroid checkupdates`, `fdroid scanner`, and an isolated `fdroid build`.
5. Open a tested merge request against `f-droid/fdroiddata`.

The `NonFreeAssets` declaration is intentional. If reviewers do not accept the
bundled Chat Favour terms, obtain explicit redistribution permission or replace
both the font and the derived launcher vector in a follow-up release.
