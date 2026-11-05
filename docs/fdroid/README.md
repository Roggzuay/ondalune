# Submitting Ondalune to F-Droid

F-Droid builds apps from source; it does not take uploaded APKs. Inclusion happens through a
merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) that adds a build recipe.
Nothing here is submitted yet. Ondalune is not on F-Droid until that merge request is accepted and
F-Droid has built and published it.

## Where things stand (audit of 5 November 2026)

- **Licence and dependencies:** GPL-3.0-or-later; every runtime dependency is free software
  (AndroidX, Kotlin, OkHttp, Coil, Haze, MaterialKolor, WorkManager), from Google Maven and Maven
  Central only. No Google Play Services, Firebase, analytics, ads or proprietary binaries.
- **Scanner:** `fdroid scanner` (fdroidserver 2.4.5) found no problems in the source or in the
  1.5.0 APK. `dependenciesInfo` (Google's encrypted dependency blob) is off.
- **Reproducible builds:** a clean build of a tag reproduces the release APK byte for byte, as
  long as the release APK is built from a clean checkout of that tag (AGP records the commit).
- **Network:** only the user's own servers, Wikipedia (artist bios) and, in the GitHub build
  only, GitHub for update checks. No anti-features expected for the F-Droid build.

## What was changed for F-Droid

- `versionCode` and `versionName` are written literally in `app/build.gradle.kts` and bumped in
  each release commit (F-Droid reads them without running Gradle).
- `-Pondalune.selfUpdate=false` builds without the GitHub update check, the in-app installer, the
  `REQUEST_INSTALL_PACKAGES` permission and its FileProvider. F-Droid's policy only allows
  self-updaters as opt-in, so the F-Droid build doesn't have one; F-Droid delivers its updates.
  A GitHub copy recognises an F-Droid install and points to F-Droid instead of offering an APK.
- `gradle-wrapper.properties` pins the Gradle distribution's SHA-256.
- Store text in `fastlane/metadata/android/en-US/` (title, short and full description, icon,
  screenshots, one changelog per version code) follows F-Droid's limits and allowed HTML.

## Blockers before submitting

1. **Maturity:** F-Droid's Quality Control point 6 asks for about three months of active
   development unless an app has proven users. The public repository started on 30 October 2026,
   so the natural date is around **30 January 2027**.
2. **Release order:** from the next release on, build the APKs only after the release commit is
   tagged, from a clean checkout of the tag (the 1.5.0 APK was built one commit early).

## Checklist for the first F-Droid release

- [ ] Release commit: bump `versionCode`/`versionName`, add `changelogs/<versionCode>.txt`, update
      `CHANGELOG.md`; tag `vX.Y.Z`; push the commit and the tag.
- [ ] From a clean checkout of the tag, with JDK 21:
  - `./gradlew assembleRelease` → `Ondalune-X.Y.Z.apk` (GitHub build)
  - `./gradlew assembleRelease -Pondalune.selfUpdate=false` → `Ondalune-X.Y.Z-fdroid.apk`
  - both signed with the release key; upload both to the GitHub release.
- [ ] Check the `-fdroid` APK against an unsigned rebuild with `apksigcopier compare`.
- [ ] Fill in `docs/fdroid/io.github.roggzuay.ondalune.yml`: version name and code, and the
      full 40-character commit of the tag (`commit:`), `CurrentVersion`, `CurrentVersionCode`.

## Submitting (needs the owner's go-ahead)

1. Fork https://gitlab.com/fdroid/fdroiddata (public fork), and create a branch named
   `io.github.roggzuay.ondalune`.
2. Add `metadata/io.github.roggzuay.ondalune.yml` from this folder, with comments removed.
3. Optionally check it locally: `fdroid readmeta`, `fdroid rewritemeta`, `fdroid lint`,
   `fdroid checkupdates --allow-dirty`, `fdroid build -v -l io.github.roggzuay.ondalune`.
4. Commit as `New App: io.github.roggzuay.ondalune`, push, and wait for the fork's CI to pass.
5. Open a merge request titled **New app: Ondalune** using the default template, mention the
   `selfUpdate=false` build and the developer-signed `-fdroid` APK, and add `/label ~"New App"`.
6. After it is merged, F-Droid builds and publishes the app within a day or two. Later tags are
   picked up automatically (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`), as long as each
   release has its literal version and its `-fdroid` APK uploaded.

Sources: [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/),
[Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/),
[Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/),
[fdroiddata CONTRIBUTING](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md).
