# vitruvian-

A minimal, personal Android app for training on a Vitruvian Trainer. Black and white,
offline, no accounts. It talks to the trainer directly over Bluetooth.

This README grows with the app: every change that adds or changes a feature also adds a line
to **Features** and **Changelog** below.

## Features

- **Lifters:** up to 4 people train together and sets rotate between them. Each lifter has a
  profile, a colour and their own defaults.
- **Clean layout:** the default screens. They're built in `:clean-ui`, and
  [clean-ui/FEATURES.md](clean-ui/FEATURES.md) lists every feature and where it lives.
- **% of 1RM loading:** each lifter's 1RM shows next to every exercise, and loads default to a
  percentage of it. A guided 1RM test fills it in.
- **All trainer modes:** Old School, Pump, TUT, TUT Beast, Eccentric Only and Echo, with
  per-set reps, weights, rest, AMRAP and timed sets.
- **Exercise library:** search, muscle and equipment filters, favourites and video demos.
- **Live heart rate:** pair a Garmin watch broadcast or a chest strap per lifter. HR shows
  during a set and in history.
- **History and analytics:** workouts, personal records and volume over time. Analytics opens
  on a Vitruvian body map: each region is coloured by the measure and period you pick (reps,
  volume, … this week, this year, …) and shows its heaviest load. Those two filters drive the
  whole screen: trend bars, headline numbers, insights, balance and exercise cards.
- **Backup and restore:** a full JSON backup saved to `Download/vitruvian-`.
- **Theme:** black and white, with light and dark modes.

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
