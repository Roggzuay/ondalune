<!-- Small, focused pull requests are much easier to review. One change per PR, please. -->

## What and why

<!-- What does this change, and what problem does it solve? Link the issue: "Fixes #123". -->

## How

<!-- The approach, and anything a reviewer should look at closely. -->

## Screenshots or recording

<!-- For anything you can see: before and after. Remove this section if nothing changes on screen. -->

## Testing

- [ ] `./gradlew testDebugUnitTest assembleDebug` passes
- [ ] Tried on a device or emulator (which one, which Android version?)
- [ ] Added or updated tests where it made sense

## Check before merging

- [ ] Possible regressions considered (playback, downloads, sync, themes, tablets)
- [ ] Database changes come with a migration (and existing data survives an update)
- [ ] New dependencies are explained and GPL-3.0 compatible
- [ ] Docs or the changelog are updated if users will notice the change
