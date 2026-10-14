# vitruvian-

A minimal, personal Android app for training on a Vitruvian Trainer. Black and white,
offline, no cloud accounts. It talks to the trainer directly over Bluetooth.

This README grows with the app: every change that adds or changes a feature also adds a line
to **Features** and **Changelog** below.

## Features

- **First launch:** a short onboarding picks or creates your account, offers a PIN, explains
  Garmin heart rate and offers the app tour.
- **Local accounts:** every profile is an account. A PIN (4 to 6 digits, stored only as a salted
  hash) is asked when the app opens as you, before a protected profile joins a training, and
  before it's deleted. Others on the phone don't see a protected profile's history until it's
  unlocked. A forgotten PIN is reset with the phone's own screen lock.
- **App tour:** arrows and a dimmed overlay point out every part of the app, including Garmin
  heart rate pairing. Each account's tour status is remembered; replay it from Settings → Help.
- **Lifters:** up to 4 people train together and sets rotate between them. Each lifter has a
  profile, a colour and their own defaults.
- **Clean layout:** the default screens. They're built in `:clean-ui`, and
  [clean-ui/FEATURES.md](clean-ui/FEATURES.md) lists every feature and where it lives.
- **% of 1RM loading:** each lifter's 1RM shows next to every exercise, and loads default to a
  percentage of it. A guided 1RM test fills it in. One tested lift gives an estimated 1RM
  for every related exercise (bench 25 kg means incline about 22.5 kg), shown as "estimated"
  until you test it. The ratios are in `clean-ui/src/main/assets/one_rep_max_map.json`.
- **All trainer modes:** Old School, Pump, TUT, TUT Beast, Eccentric Only and Echo, with
  per-set reps, weights, rest, AMRAP and timed sets.
- **Exercise library:** search, muscle and equipment filters, favourites and video demos.
- **Live heart rate:** pair a Garmin watch broadcast or a chest strap per lifter. HR shows
  during a set and in history.
- **Garmin watch app:** the lifter's Garmin watch records the training live: heart rate and
  every set, saved to Garmin Connect as strength training. See `docs/GARMIN_WATCH.md`.
- **History and analytics:** workouts, personal records and volume over time. Analytics opens
  on a Vitruvian body map: each region is coloured by the measure and period you pick (reps,
  volume, … this week, this year, …) and shows its heaviest load. Those two filters drive the
  whole screen: trend bars, headline numbers, insights, balance and exercise cards.
- **Backup and restore:** a full JSON backup saved to `Download/vitruvian-`.
- **Theme:** black and white, with light and dark modes.
- **Settings:** grouped as Account, People, Training, Sound and video, Trainer LED colour,
  Help, Data, and Advanced (connection logs, protocol tester, classic layout) folded away.

## Install

Android only. Every merge to `master` builds a signed APK and publishes it as a GitHub
release. Phones install it and get updates through Obtainium. The full steps are in
[docs/RELEASES.md](docs/RELEASES.md).

## Build

```sh
./gradlew :app:assembleDebug
```

Modules: `:app` (trainer connection, data, classic screens) and `:clean-ui` (the Clean layout,
no imports from `:app`).

## Changelog

- **2026-10-14:** Garmin watch app that records each lifter's training on their own watch.
- **2026-10-14:** the body map shows each region's best 1RM as total load (both cables), and the
  1RM list shows totals with the per-cable load next to them.
- **2026-10-14:** estimated 1RMs for 362 exercises, scaled from your tested lifts.
- **2026-10-13:** first-launch onboarding, local accounts with a PIN, an arrow-and-overlay app
  tour, and Settings regrouped. The Clean layout is now on by default for new installs.
- **2026-10-13:** new Analytics screen with a body map, measure and period filters, trend
  bars, insights and exercise cards. Clean lifting screen during a set.
- **2026-10-13:** rebranded to vitruvian-. New black and white theme, a "V-" icon and splash,
  and no more Phoenix branding.
- **2026-10-13:** signed APK releases on every master push, and live Garmin heart rate.
- **2026-10-12:** Clean layout, per-lifter 1RM and % loading.

## Credits

vitruvian- started as a fork of the MIT-licensed
[Vitruvian Project Phoenix](https://github.com/9thLevelSoftware/VitruvianProjectPhoenix)
Android app, and thanks everyone who reverse-engineered the trainer's BLE protocol. The
original licence notice is kept in [LICENSE](LICENSE).
