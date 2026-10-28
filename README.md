<p align="center">
  <img src="docs/banner.svg" alt="Cable, a free unofficial app for the Vitruvian Trainer+ and V-Form" width="100%">
</p>

<p align="center">
  <a href="https://github.com/NielsNotsmurf/Cable-4-Vitruvian/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/NielsNotsmurf/Cable-4-Vitruvian?style=flat-square&color=000000&label=release"></a>
  <a href="https://github.com/NielsNotsmurf/Cable-4-Vitruvian/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/NielsNotsmurf/Cable-4-Vitruvian/total?style=flat-square&color=000000"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-000000?style=flat-square&logo=android&logoColor=white">
  <a href="LICENSE"><img alt="MIT licence" src="https://img.shields.io/badge/licence-MIT-000000?style=flat-square"></a>
  <a href="https://github.com/sponsors/NielsNotsmurf"><img alt="Sponsor" src="https://img.shields.io/badge/sponsor-%E2%99%A5-000000?style=flat-square&logo=githubsponsors&logoColor=white"></a>
  <a href="https://github.com/ImranR98/Obtainium"><img alt="Get it on Obtainium" src="https://img.shields.io/badge/get%20it%20on-Obtainium-000000?style=flat-square"></a>
</p>

<h1 align="center">Cable: free app for the Vitruvian Trainer+ &amp; V-Form</h1>

<p align="center">
  <b>Cable: an unofficial app for the Vitruvian V-Form &amp; Trainer+.</b><br>
  Train without a subscription. Not affiliated with Vitruvian.<br>
  Black and white, offline, no cloud accounts. Talks to the trainer directly over Bluetooth.<br>
  <a href="#install">Install</a> · <a href="#web-app-beta-iphone-ipad-and-any-browser">Web app (beta)</a> · <a href="https://nielsnotsmurf.github.io/Cable-4-Vitruvian/demo/">Live demo</a> · <a href="#features">Features</a> · <a href="#changelog">Changelog</a> · <a href="https://github.com/NielsNotsmurf/Cable-4-Vitruvian/issues/new?template=feedback.yml">Feedback</a>
</p>

> [!NOTE]
> Cable is an unofficial, independent community project for the Vitruvian V-Form and Trainer+.
> It is not affiliated with, made, endorsed or supported by Vitruvian. "Vitruvian", "V-Form" and
> "Trainer+" are trademarks of their owner and are only
> used here to say which hardware the app works with. Use it at your own risk.

> [!TIP]
> **Goal: Cable on TestFlight and the Play Store.** Once [sponsors](https://github.com/sponsors/NielsNotsmurf)
> cover about €200 (Apple's developer account at $99 a year for two years, plus Google Play's
> one-time $25), Cable moves to TestFlight for iPhone and iPad and to the Play Store.
> [More in Support](#support).

## Why Cable

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
  <img src="docs/screenshots/welcome.png" width="23%" alt="Cable welcome screen">
  <img src="docs/screenshots/train.png" width="23%" alt="Training with a Vitruvian Trainer+ in Cable">
  <img src="docs/screenshots/exercises.png" width="23%" alt="Exercise list with loads per cable">
  <img src="docs/screenshots/analytics.png" width="23%" alt="Analytics body map with 1RM per muscle group">
</p>

**Try it in your browser:** the [live demo](https://nielsnotsmurf.github.io/Cable-4-Vitruvian/demo/) is a
clickable web version with a simulated trainer and example lifters. Nothing to install.

## Features

**Getting started**

| Feature | What it does |
| --- | --- |
| First launch | Short onboarding: pick or create your account, set a PIN, Garmin heart rate, app tour. |
| Local accounts | Every profile is an account. Optional 4 to 6 digit PIN (salted hash only), asked on open, before a protected profile joins a training and before deleting it. Its history stays hidden until unlocked. Forgot it? Reset with the phone's screen lock. |
| App tour | Arrows and a dimmed overlay explain every part, including heart rate pairing. Remembered per account; replay from Settings → Help. |
| Lifters | Up to 4 people train together, sets rotate between them. Each has a profile, colour and defaults. |

**Training**

| Feature | What it does |
| --- | --- |
| All trainer modes | Old School, Pump, TUT, TUT Beast, Eccentric Only and Echo. Per-set reps, weight, rest, AMRAP and timed sets. |
| % of 1RM loading | Each lifter's 1RM shows next to every exercise and loads default to a % of it. A guided 1RM test fills it in, and one tested lift estimates related ones (bench 25 kg ≈ incline 22.5 kg), marked "estimated" until tested. Ratios: `one_rep_max_map.json` in `clean-ui` assets. |
| 1RMs you control | Only the 1RM test or your own entry sets a 1RM. Tap one in Analytics to correct it. |
| Workout builder | Pick muscle groups and a time; the app builds the workout at each lifter's % of 1RM. Swap exercises for other equipment; same-equipment exercises are grouped to save switching. |
| Exercise library | Short descriptions, search by name, muscle or equipment, region and muscle filters, favourites and video demos. |
| During a workout | End a set early, skip or redo a set, jump to the next exercise, change the % of 1RM for coming sets, or pause and resume later. |
| Live heart rate | Garmin watch broadcast or chest strap per lifter; shown during sets and in history. |
| Sounds | Synthesized bops for taps and screens, plus workout cues (countdown, reps, warm-up, set done, records, rest over, 1RM). Separate switches for menu sounds and workout beeps; music keeps playing. |

**Progress and data**

| Feature | What it does |
| --- | --- |
| History and analytics | Workouts, personal records and volume over time. Opens on a body map coloured by the measure (reps, volume, …) and period (week, year, …) you pick, with each region's heaviest load. Those filters drive the whole screen: trends, headline numbers, insights, balance and exercise cards. |
| Backup and restore | Full JSON backup saved to `Download/Cable`. |

**App**

| Feature | What it does |
| --- | --- |
| Web app (beta) | The same app in the browser and on the home screen of iPhone, iPad, Android and computers; see [Web app](#web-app-beta-iphone-ipad-and-any-browser). |
| Tablets and computers | From 600 dp wide the tabs become a rail on the left and screens stay readable. |
| Clean layout | The default screens, built in `:clean-ui`; [clean-ui/FEATURES.md](clean-ui/FEATURES.md) maps every feature to its code. |
| Theme | Black and white, light and dark mode. |
| Settings | Account, People, Training, Sound and video, Trainer LED colour, Help, Data, and a folded-away Advanced (connection logs, protocol tester, classic layout). |

## Install

Android only (Android 8 or newer) and free.

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases) on your phone or tablet.
   It installs apps straight from GitHub and keeps them updated.
2. In Obtainium, tap **Add app**, paste `https://github.com/NielsNotsmurf/Cable-4-Vitruvian` and
   tap **Add**, then **Install**. Allow Obtainium to install apps when Android asks.
3. Open Cable, follow the welcome steps and connect to your trainer over Bluetooth.

Without Obtainium: open [Releases](https://github.com/NielsNotsmurf/Cable-4-Vitruvian/releases)
on your phone and tap the `.apk` of the latest version. You then have to repeat this for
every update.

### Web app (beta, iPhone, iPad and any browser)

> [!IMPORTANT]
> **The web app is a beta (pre-release).** It has the same screens as the Android app, but it
> is new and has not been tested on every trainer, phone and browser yet. Expect rough edges,
> and please [report what you find](https://github.com/NielsNotsmurf/Cable-4-Vitruvian/issues/new?template=feedback.yml).
> It comes with Cable 2.0 (beta), a pre-release; the last 1.3 release stays the stable Android
> version.

Open [nielsnotsmurf.github.io/Cable-4-Vitruvian/app](https://nielsnotsmurf.github.io/Cable-4-Vitruvian/app/).
On first launch it explains itself and shows how to put it on your home screen.

| Device | Browser | Trainer and heart rate |
| --- | --- | --- |
| Android, Windows, Mac, Linux | Chrome or Edge, then **Install app** | Yes, over Bluetooth |
| iPhone, iPad | **Bluefy** (free) | Yes, over Bluetooth |
| iPhone, iPad | Safari, then Share, **Add to Home Screen** | No; sets run on a simulated trainer |
| Any | Firefox | No; simulated trainer |

**Training on iPhone or iPad with Bluefy.** Apple does not let Safari, or any app built on it,
use Bluetooth from a web page, so in Safari the web app can only show a simulated trainer.
[Bluefy](https://apps.apple.com/app/bluefy-web-ble-browser/id1492822055) is a free browser from
the App Store that adds this Bluetooth. Cable is not made by or affiliated with Bluefy.

1. Install **Bluefy – Web BLE Browser** from the App Store.
2. Open Bluefy and go to `nielsnotsmurf.github.io/Cable-4-Vitruvian/app`. Tip: in Safari's
   version of the web app, the intro has a **Copy this link** button.
3. Allow Bluetooth when Bluefy asks.
4. Start a set. Bluefy shows a list of devices; pick your trainer (its name starts with `Vee`
   or `Vitruvian`). Connect a heart rate strap the same way, from your profile.
5. Bookmark the page in Bluefy, or add it to Bluefy's favourites, to open it quickly next time.

Known limits of the beta:

- It can feel a little slower than the Android app, and Bluetooth in a browser can lag a bit.
- After reloading the page, the trainer and heart rate strap have to be picked again.
- Each browser keeps its own data on that device; Bluefy and Safari do not share it. Settings,
  Backup and restore saves it to a file you can load elsewhere.
- Screens that only exist in the classic Android layout are not on the web.

Every merge to `master` builds a signed APK and publishes it as a release. How the signing
works is in [docs/RELEASES.md](docs/RELEASES.md).

## FAQ

**Is there a free alternative to the official Vitruvian app?**
Yes, Cable. It is a free, open-source Android app for the Vitruvian V-Form and Trainer+ that
connects to the trainer directly over Bluetooth, without a subscription or cloud account.

**Does it work with my V-Form or Trainer+?**
Cable is made for both and talks to them over the trainer's Bluetooth LE protocol. If something
does not work on your unit, please
[open an issue](https://github.com/NielsNotsmurf/Cable-4-Vitruvian/issues).

**Is it official?**
No. Cable is an independent community project, not affiliated with or endorsed by Vitruvian.

**iPhone?**
Yes, through the web app, which is still a beta (pre-release). Safari can only show a simulated
trainer; to train with your trainer, open the web app in the free Bluefy browser. A real iPhone
app through TestFlight comes once the [sponsor goal](#support) is reached. See
[Web app](#web-app-beta-iphone-ipad-and-any-browser).

**Will I lose my data from the official app?**
Cable does not touch your Vitruvian account. Your Cable workouts stay on your phone and can be
backed up to a single JSON file.

## Build

```sh
./gradlew :app:assembleDebug
```

Modules: `:app` (trainer connection, data, classic screens) and `:clean-ui` (the Clean layout,
no imports from `:app`).

## Changelog

- **Sponsor goal:** about €200 for TestFlight (iPhone, iPad) and the Play Store; see [Support](#support).
- **2.0 (beta, pre-release), web app beta:** Cable as an installable web app for iPhone, iPad, tablets and computers,
  with the trainer and heart rate over Web Bluetooth, the same screens as the app, offline use
  and backups. On wide screens (also Android tablets) the tabs move to a rail on the left.
- **2026-10-26:** a small landing page on GitHub Pages and better image descriptions, so search engines find Cable.
- **2026-10-21:** new screenshots in the Cable look.
- **2026-10-20:** move through a workout: end a set early (6 of 8 reps still counts) and go on,
  skip or redo a set, or jump to the next exercise during rest. Back leaves the workout running so
  you can check Analytics, and a "Workout running" bar picks it up again. The % of 1RM slider stays
  on the lifting screen and changes every lifter's coming sets. Home groups the current training per
  exercise. Just Lift is gone from home. Every exercise has a short description, and search and
  filters find muscles such as forearms. A 1RM only changes through the 1RM test or by hand: tap a
  1RM in Analytics to correct it.
- **2026-10-19:** FAQ for people looking for an alternative Vitruvian app, and store listing
  text (fastlane) for F-Droid repos such as IzzyOnDroid.
- **2026-10-19:** rebranded to **Cable**, an unofficial app for the Vitruvian V-Form and Trainer+. New
  cable-and-pulley icon, splash and banner, and a red accent by default. Updates keep working.
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

Cable is free and stays free. If it saves you a subscription and you want to say thanks,
you can [sponsor me on GitHub](https://github.com/sponsors/NielsNotsmurf). Stars, bug reports
and sharing the app with other Vitruvian owners help just as much.

**Goal: about €200 for real iPhone and Play Store apps.**

| What | Cost |
| --- | --- |
| Apple Developer Program, two years | $99 a year, $198 |
| Google Play developer account | $25, once |

When sponsors reach it, Cable moves to TestFlight on iPhone and iPad (no Bluefy needed) and to
the Play Store (updates without Obtainium). Until then the web app and the APK stay the way to
get Cable. Every sponsor goes to these accounts first.

## Credits

Cable started as a fork of the MIT-licensed
[Vitruvian Project Phoenix](https://github.com/9thLevelSoftware/VitruvianProjectPhoenix)
Android app, and thanks everyone who reverse-engineered the trainer's BLE protocol. The
original licence notice is kept in [LICENSE](LICENSE).
