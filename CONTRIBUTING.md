# Contributing to Ondalune

Thanks for wanting to help!

## Bugs and ideas

Use the issue templates. For bugs, the app version (Settings → About & licences), your phone,
your Android version and your music server make a huge difference. Please blur server addresses
and usernames in screenshots.

## Code

1. Open an issue first for anything beyond a small fix, so we can agree on the approach.
2. Fork the repository, create a branch, and keep each pull request focused on one change.
3. Match the style of the surrounding code: Kotlin, Jetpack Compose, Material 3.
4. Run `./gradlew testDebugUnitTest assembleDebug` before opening the pull request.
5. For UI changes, add before and after screenshots to the pull request.

**The audio engine needs extra care.** Changes to the audio engine (`audio/`) and the speed,
pitch and reverb controls affect how every song sounds, so describe how you tested them by ear.

By contributing, you agree that your contribution is licensed under the GPL-3.0-or-later, like
the rest of the project.

## Never commit

Signing keys, `keystore.properties`, `local.properties`, or any real server address or
password, including in tests.
