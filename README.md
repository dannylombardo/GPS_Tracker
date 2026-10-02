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

## After a drive

When a drive ends, a notification asks **Were you driving?** Tap **Me** or **Someone else**.
Drives marked as someone else's stay in the list but are left out of your totals. If you
ignore the question, the drive counts as yours; you can answer (or change your answer) any
time by tapping the drive in the app.

Each drive shows its average speed (distance over the whole time from start to end, stops
included) and its top speed (the highest GPS speed held across two readings in a row, so a
single glitchy reading doesn't count).

## Weekly overview

The home screen shows one week at a time, Monday to Sunday, with the arrows stepping back
through earlier weeks: kilometres driven, number of drives, time behind the wheel, average and
top speed, money spent on fuel, a bar for each day, and that week's fill-ups and drives.

## Fill-ups and real L/100km

When a drive ends, the app looks at where the car sat still (the end of the drive, plus any
pause of two minutes or more along the way) and asks OpenStreetMap's public Overpass API
whether any of those spots is a gas station. Only those coordinates are sent, once per drive,
with no account or key. If one matches, a notification asks **Filled up at …?**; tap it to
enter the litres and the price per litre. You can turn this off on the home screen, and
**Add fill-up** logs one by hand (with a date and time picker) if a stop is missed.

Consumption is measured full tank to full tank: the litres it takes to fill right up again,
plus any part fills in between, divided by the distance of your drives since the previous full
tank. Drives marked as someone else's are left out of that distance. The first full tank only
starts the count, so the number appears after your second full fill-up. The home screen shows
the overall average, and each fill-up shows the L/100km of the stretch it closed.

The weekly overview adds money spent on fuel, litres bought and number of fill-ups for that week.
Tap a fill-up to edit or delete it.

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
