<p align="center">
  <img src="docs/banner.svg" alt="vitruvian-" width="100%">
</p>

<p align="center">
  <a href="https://github.com/NielsNotsmurf/vitruvian-app/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/NielsNotsmurf/vitruvian-app?style=flat-square&color=000000&label=release"></a>
  <a href="https://github.com/NielsNotsmurf/vitruvian-app/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/NielsNotsmurf/vitruvian-app/total?style=flat-square&color=000000"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-000000?style=flat-square&logo=android&logoColor=white">
  <a href="LICENSE"><img alt="MIT licence" src="https://img.shields.io/badge/licence-MIT-000000?style=flat-square"></a>
  <a href="https://github.com/sponsors/NielsNotsmurf"><img alt="Sponsor" src="https://img.shields.io/badge/sponsor-%E2%99%A5-000000?style=flat-square&logo=githubsponsors&logoColor=white"></a>
  <a href="https://github.com/ImranR98/Obtainium"><img alt="Get it on Obtainium" src="https://img.shields.io/badge/get%20it%20on-Obtainium-000000?style=flat-square"></a>
</p>

<p align="center">
  <b>Train on your Vitruvian Trainer without a subscription.</b><br>
  Black and white, offline, no cloud accounts. Talks to the trainer directly over Bluetooth.<br>
  <a href="#install">Install</a> · <a href="https://nielsnotsmurf.github.io/vitruvian-app/demo/">Live demo</a> · <a href="#features">Features</a> · <a href="#changelog">Changelog</a>
</p>

> [!NOTE]
> vitruvian- is an independent community project. It is not made, endorsed or supported by
> Vitruvian. "Vitruvian" and "Vitruvian Trainer" are trademarks of their owner and are only
> used here to say which hardware the app works with. Use it at your own risk.

## Why vitruvian-

| | |
|---|---|
| **Free, for good** | No subscription, no ads, no tracking. MIT licensed. |
| **Offline first** | Everything stays on your phone. Back up to a single JSON file. |
| **Train together** | Up to 4 lifters per session, sets rotate, each with their own 1RM and PIN. |
| **Smart loading** | Loads as a % of your 1RM, with estimated 1RMs for 362 exercises. |
| **Auto updates** | Install once with Obtainium and new versions arrive by themselves. |

<!-- This README grows with the app: every change that adds or changes a feature also adds a line to Features and Changelog. -->

## Screenshots

<p align="center">
  <img src="docs/screenshots/welcome.png" width="23%" alt="Welcome">
  <img src="docs/screenshots/train.png" width="23%" alt="Training">
  <img src="docs/screenshots/exercises.png" width="23%" alt="Exercises">
  <img src="docs/screenshots/analytics.png" width="23%" alt="Analytics body map">
</p>

**Try it in your browser:** the [live demo](https://nielsnotsmurf.github.io/vitruvian-app/demo/) is a
clickable web version with a simulated trainer and example lifters. Nothing to install.

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
- **Workout builder:** pick one or more muscle groups and a time, and the app builds the
  workout with each lifter's % of 1RM. Swap any exercise for one on other equipment; exercises
  on the same equipment sit next to each other so you switch as little as possible.
- **Exercise library:** search, muscle and equipment filters, favourites and video demos.
- **Live heart rate:** pair a Garmin watch broadcast or a chest strap per lifter. HR shows
  during a set and in history.
- **History and analytics:** workouts, personal records and volume over time. Analytics opens
  on a Vitruvian body map: each region is coloured by the measure and period you pick (reps,
  volume, … this week, this year, …) and shows its heaviest load. Those two filters drive the
  whole screen: trend bars, headline numbers, insights, balance and exercise cards.
- **Sounds:** short synthesized bops and boops for every tap, screen change and dialog, plus
  workout sounds for the countdown, each rep, warm-up done, set done, records, the end of rest and
  1RM attempts. "Menu sounds" and "Workout beeps" each have a switch in Settings; music keeps playing.
- **Backup and restore:** a full JSON backup saved to `Download/vitruvian-`.
- **Theme:** black and white, with light and dark modes.
- **Settings:** grouped as Account, People, Training, Sound and video, Trainer LED colour,
  Help, Data, and Advanced (connection logs, protocol tester, classic layout) folded away.

## Install

Android only (Android 8 or newer) and free.

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases) on your phone or tablet.
   It installs apps straight from GitHub and keeps them updated.
2. In Obtainium, tap **Add app**, paste `https://github.com/NielsNotsmurf/vitruvian-app` and
   tap **Add**, then **Install**. Allow Obtainium to install apps when Android asks.
3. Open vitruvian-, follow the welcome steps and connect to your trainer over Bluetooth.

Without Obtainium: open [Releases](https://github.com/NielsNotsmurf/vitruvian-app/releases)
on your phone and tap the `.apk` of the latest version. You then have to repeat this for
every update.

Every merge to `master` builds a signed APK and publishes it as a release. How the signing
works is in [docs/RELEASES.md](docs/RELEASES.md).

## Build

```sh
./gradlew :app:assembleDebug
```

Modules: `:app` (trainer connection, data, classic screens) and `:clean-ui` (the Clean layout,
no imports from `:app`).

## Changelog

- **2026-10-16:** Settings has a "Training load · % of 1RM" slider (default 60%) that every exercise and
  built workout starts at. Built workouts let you change sets, reps and each lifter's load before you
  start. While the trainer gets ready, counts down or does the warm-up reps you can change the weight
  (during warm-up the warm-up restarts). The exercise video now loads in the background, so it plays
  by the time the countdown starts.
- **2026-10-16:** sounds everywhere: taps, screen changes and dialogs bop (Settings › Menu
  sounds), and the live workout and 1RM test now beep for countdown, reps, set done, records and
  end of rest (Settings › Workout beeps).
- **2026-10-16:** sets and reps per exercise come only from Settings ("Sets per exercise",
  "Reps per set"), never from what you did last time; change them on the set-up screen just before
  you start. The live screen plays the exercise video during get-ready, countdown and warm-up reps,
  and the "Classic" view button is gone.
- **2026-10-16:** new Train home: one dark "Build a workout" card as the main action, then One
  exercise / Just Lift, "Do again" chips and one 1RM card (test + re-tests). The separate muscle
  groups screen and duplicate buttons are gone.
- **2026-10-16:** workout builder by muscle group and time, with equipment-aware swaps. Reps per
  set come from Settings, and only the 1RM test changes a tested 1RM (normal sets no longer do).
  The body map shows chest and back 1RMs as total load (both cables).
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

## Support

vitruvian- is free and stays free. If it saves you a subscription and you want to say thanks,
you can [sponsor me on GitHub](https://github.com/sponsors/NielsNotsmurf). Stars, bug reports
and sharing the app with other Vitruvian owners help just as much.

## Credits

vitruvian- started as a fork of the MIT-licensed
[Vitruvian Project Phoenix](https://github.com/9thLevelSoftware/VitruvianProjectPhoenix)
Android app, and thanks everyone who reverse-engineered the trainer's BLE protocol. The
original licence notice is kept in [LICENSE](LICENSE).
