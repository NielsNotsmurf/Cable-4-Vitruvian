# Garmin watch app

`watch/` is a Connect IQ watch app (Monkey C) that records a Vitruvian training on the
lifter's Garmin watch. `:app` talks to it through Garmin Connect Mobile with the Connect IQ
Mobile SDK (`data/garmin/GarminWatchLink.kt`).

## Flow

1. A set starts for a profile with a watch: the tablet asks the watch to open the app
   (one tap on the watch), then sends `go`.
2. The set is saved: the tablet sends `set` with exercise, Garmin exercise category, reps
   and total kg. The watch closes a lap with those values in developer fields.
3. "Finish training" on the Clean home screen (or 30 min without a set, on either side)
   sends `end`; the watch saves a strength-training activity that syncs to Garmin Connect.

Messages the watch missed while the app was closed are queued and resent when it reports
`ready`. Set ids are deduplicated on the watch.

## Which watch

`GarminWatches.watchFor` (clean-ui/logic): a watch picked for the profile in the heart rate
dialog wins; otherwise the profile whose paired heart rate sensor has the watch's name; a
single profile with a single watch also matches. A watch belongs to one profile, so in a
rotation only that lifter's sets reach it.

## Limits

Connect IQ cannot write FIT `set` messages, so Garmin Connect shows laps, heart rate and
calories but an empty sets list.

## Building

The release workflow builds `vitruvian-venu441mm.prg` and `vitruvian-venu445mm.prg` and attaches
them to the release. `watch/toolchain` holds the compiler (`monkeybrains.jar` and its API files
from Connect IQ SDK 9.2.0) and the devices' `compiler.json`, copied from the SDK Manager, because
Garmin's own downloads need an interactive login with an email code. Optional
`CIQ_DEVELOPER_KEY_BASE64` keeps one signing key across builds.

Local build (Java 17+): link `watch/toolchain` to `~/.Garmin/ConnectIQ`, then
`java -cp watch/toolchain/sdk-bin/monkeybrains.jar com.garmin.monkeybrains.Monkeybrains -f watch/monkey.jungle -d venu445mm -y key.der -l 2 -o out.prg`.

To add a watch model: download it in the SDK Manager, copy its `compiler.json` to
`watch/toolchain/Devices/<id>/` and add `<iq:product id="<id>"/>` to `watch/manifest.xml`.

Install: copy the .prg to `GARMIN/Apps` on the watch over USB.
