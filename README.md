# GPS_Tracker

A personal Android app that logs every drive automatically, with no account or server.
All data stays in an on-device database.

## How drive tracking works

- Android's activity recognition (the low-power motion sensors) reports when you get into
  a vehicle. Only then does the app start a foreground service and turn on GPS.
- GPS fixes are filtered (inaccurate fixes, jitter while stopped, impossible jumps) and summed
  into the trip distance. The route points are saved with the trip.
- The drive ends 3 minutes after Android reports you left the vehicle, or after 10 minutes
  without moving if that report never comes. You can also end it from the notification.
- Trips under 500 m are dropped as false starts.

## Installing

Every push to a pull request builds a debug APK in GitHub Actions
(**Actions → Android build → gps-tracker-debug-apk**). Download it on the phone and install it
(you'll need to allow installs from your browser or file manager). The debug signing key is
checked in at `app/debug.keystore`, so newer builds install over older ones without losing data.

On first launch, walk through the three setup steps and pick **Allow all the time** for location,
then turn on **Track drives automatically**. **Start a drive now** records a drive by hand,
which is handy for testing.

## Building locally

Open the project in Android Studio, or run `./gradlew assembleDebug` with the Android SDK installed.
