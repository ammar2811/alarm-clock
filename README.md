# Alarm Clock

An Android alarm clock that makes you complete challenges before an alarm can be
dismissed.

## Challenges

An alarm can require several challenges, in order, each with its own difficulty:

- **Math**: solve arithmetic problems.
- **Photo**: photograph a chosen object, recognised on device with no network access.
  When an alarm has more than one photo challenge, each one asks for a different object.
- **Memory**: match pairs on a card grid.
- **Retype**: type out a string exactly.
- **Sequence**: repeat a sequence back.

There is no snooze. Until its challenges are done, a ringing alarm can't be switched off,
deleted or edited from the alarm list, and restarting the phone won't silence it.
Auto-silence still applies.

## Install

Download the APK from the [latest release](https://github.com/ammar2811/alarm-clock/releases/latest)
and open it on an Android 7.0+ device (you may need to allow installs from your browser or
file manager). The application ID is `com.ammar.deskclock`, so it installs alongside
Google's Clock.

Release APKs are built and signed by CI with a debug key that changes between builds, so to
move to a newer release, uninstall the old one first.

## Build

Requires JDK 21 and the Android SDK.

```sh
./gradlew assembleDebug   # APK in app/build/outputs/apk/debug/
./gradlew check           # unit tests and lint
```

## Credits and license

This is a fork of [DeskClock](https://android.googlesource.com/platform/packages/apps/DeskClock)
from the Android Open Source Project, ported from Soong to Gradle. Upstream is no longer
maintained. The photo challenge uses the EfficientDet-Lite0 model from Google's MediaPipe
project.

See [NOTICE](NOTICE) for attribution and [LICENSE](LICENSE) for the Apache License 2.0 that
covers both the original code and the changes.
