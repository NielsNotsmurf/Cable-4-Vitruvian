# Feature coverage

Every feature of the classic app, where it lives in the Clean layout, and how far the Android
Clean screens have got. The web preview (https://claude.ai/artifact/SN62xxxzSGx2CZQvyYrKJi)
covers every row. On Android every row is reachable inside the Clean layout; classic screens
are replaced by clean versions one by one.

Android status:
- **Clean**: built as a Clean screen.
- **Hosted**: the classic screen runs inside the Clean layout (light theme), so nothing is lost.
  Reached from the Train tiles or the History / Analytics / Settings tabs.
- **Not yet**: new in the Clean design and not in the app yet.

| Feature | Classic location | Clean layout | Android |
|---|---|---|---|
| First-launch onboarding: welcome, pick or add your account, optional PIN, Garmin heart rate, tour offer | — (new) | Shown once before the tabs | Clean |
| Local accounts: every profile is one; a PIN locks opening the app as you, joining a training, pairing a sensor and deleting the profile; a protected profile's history is hidden until unlocked; screen lock resets a forgotten PIN | — (new) | Lock screen, Settings → Account | Clean |
| App tour with arrows and overlays across Train, tabs, Exercises, Settings and Profiles (Garmin pairing); remembered per account | — (new) | Settings → Help → App tour | Clean |
| Heart rate setup guide (Garmin broadcast steps, chest straps) | — (new) | Onboarding, Settings → Help | Clean |
| Who's training (up to 4, sets rotate) | Home participants picker | Train → Who's training card | Clean |
| Add profile from training | Profiles screen | Train → + Add | Clean (Manage) |
| Current training: everything done with less than 5 hours between exercises, with sets, reps, time and heart rate | — (new) | Train → Current training card | Clean |
| Live heart rate on the home screen | — (new) | Train → ♥ pill, Current training card | Clean |
| Oldest 1RMs to re-test, with Re-test (1RM test) and Train (set setup) | — (new) | Train → Due for a re-test | Clean |
| Profiles: add with colour, delete, pick who trains | Profiles screen | Settings → Profiles (also Train → Manage) | Clean |
| Heart rate sensor per profile (Garmin watch broadcast or strap): pair, unpair, status | Profiles screen | Profiles → ♥ on a lifter | Clean (pairing dialog hosted) |
| Live heart rate during a set | Active workout | Lifting screen | Clean |
| Heart rate in history (avg / max per set) | History cards | History rows and workout detail | Clean |
| Per-lifter 1RM next to exercises | — (new) | Exercise rows, recent list | Clean |
| Single exercise with full config | Single Exercise | Train → Pick an exercise → Set setup | Clean |
| Train by muscle group | Muscle Group Training | Train → Build a workout (groups + time); one exercise via Exercises (muscle filter) | Clean |
| Exercise search, muscle/equipment filters | Exercise picker | Exercises tab (also Train → Pick an exercise) | Clean |
| Favourites + "favourites only" | Exercise picker | ★ on rows, Favourites chip | Clean |
| Exercise video demos | Exercise picker / config | ▶ on rows and in Set setup | Clean (▶ in Set setup and the 1RM test, played in the app) |
| Modes: Old School, Pump, TUT, TUT Beast, Eccentric Only, Echo | Exercise config | Set setup mode chips + description | Clean |
| Echo level (Hard…Epic) and eccentric load 0–150% | Exercise config | Set setup when Echo is picked | Clean |
| % of 1RM loading per lifter | — (new) | Set setup presets, slider, lifter cards | Clean |
| Estimated 1RM for every related exercise (one_rep_max_map.json) | — (new) | Exercise rows (~), Set setup "estimated" | Clean |
| Per-set reps, weights (%), rest | Exercise config sets list | Set setup → tap a set | Clean |
| AMRAP and timed (duration) sets | Exercise config | Set type: Reps / AMRAP per set; Timed for the whole exercise | Clean |
| Bodyweight exercises | Exercise config | Bodyweight exercises skip loads, timed sets | Clean |
| Single / double cable | Exercise config | Set setup → More options → Cables | Clean |
| Weight change per rep (progression/regression) | Exercise config, Just Lift | More options / Just Lift | Clean (Just Lift hosted) |
| Warm-up reps | Settings | Settings (shown in More options) | Hosted (Settings) |
| Stop at top | Settings | Settings (shown in More options) | Clean |
| Remember as exercise default | Saved per-profile defaults | Automatic per lifter; Set setup starts from last time | Clean |
| Trainer scan, pick, connect, disconnect | Device picker, banner | Connection pill on Train, Settings → Trainer | Hosted (classic dialogs) |
| Bluetooth permissions | Permission screen | Allow Bluetooth sheet | Clean |
| Connection lost / error dialogs | Dialogs | Connection lost sheet | Hosted |
| Auto-connect | Settings | Settings → Connect automatically | Hosted |
| Countdown before a set | Countdown card | Get ready screen | Hosted |
| Live reps, load, velocity, power, cable bars | Active workout | Lifting screen | Clean (velocity, cable bars: Classic button) |
| Performer banner, set X of Y, up next | Active workout | Lifting screen band + up next | Clean |
| Adjust weight mid-set | Active workout | − / + on lifting screen | Hosted (Classic button) |
| PR celebration (heaviest, best volume) | PR animation | New PR overlay | Clean (banner) |
| Set summary: avg, peak, force over time | Set summary card | Rest screen summary card | Hosted |
| Rest timer, skip, adjust next set, up next, end workout | Rest timer card | Rest screen | Clean (adjust next set: Classic button) |
| Autoplay | Settings | Settings → Autoplay | Clean |
| Workout complete summary | Active workout | Done screen | Clean |
| Just Lift with auto-start and auto-stop | Just Lift | Train → Just Lift | Hosted |
| Strength assessment (9 anchors, too easy/right/hard) | Baseline Assessment | Replaced by the 1RM test | Hosted |
| 1RM test: demo, warm-up, single-rep attempts going up until a miss; starts at 90% of a known 1RM | — (new) | Train → 1RM test, or Test 1RM on a lifter card in Set setup | Clean (attempts load on the trainer) |
| Workout history, per session detail, delete, delete all | History tab | History tab | Clean (delete all in Settings, hosted) |
| Analytics: strength score, this week, recent PRs, top exercises | Analytics dashboard | Analytics → Overview | Clean (this week, week streak, 1RMs, recent records, progress per exercise) |
| Body map: Vitruvian figure (front and back) coloured per region by the picked measure and period, with the best 1RM inside each region as total load (both cables); tap a region to narrow the screen to it | — (new) | Analytics → top | Clean |
| Measure (reps, sets, volume, heaviest, workouts, time) and period (week, month, 3 months, year, all) for the whole Analytics screen | — (new) | Analytics → chips and slider | Clean |
| Trend bars, headline numbers against the period before, insights, balance per region, exercise cards | — (new) | Analytics | Clean |
| PR tracker, weight/volume PR, chart or list | Analytics progression | Analytics → Progress | Clean (progress lines); full PR charts hosted under Charts and insights |
| Muscle balance, consistency, volume vs intensity, modes | Insights | Analytics → Insights | Hosted (Analytics → Charts and insights) |
| Safety events (deload, ROM, spotter) | Safety card | Insights + session detail | Hosted (Analytics → Charts and insights) |
| Per-profile filter | Analytics chips | Lifter chips on History and Analytics | Clean |
| Export CSV (workouts, PRs, PR progression) | Analytics | Analytics ⤓, History ⋮, Settings | Hosted (Analytics → Charts and insights) |
| Weight unit kg / lb | Settings | Settings → Weight unit | Clean |
| Default reps | Settings | Settings | Clean |
| Workout beeps, haptics | Settings | Settings | Clean (beeps; the app has no haptics setting) |
| Show exercise videos | Settings | Settings | Clean |
| LED colour scheme | Settings | Settings → LED colour (remembered, and the app takes on the colour) | Clean |
| App accent, centre Train button and a faint edge glow in the LED colour (orange until one is picked) | — (new) | Everywhere | Clean |
| Theme light / dark / system | Settings | Settings → Theme | Not yet (Clean is light only) |
| Backup and restore | Settings | Settings → Data | Hosted (Settings → Data → Backup and restore) |
| Connection logs (search, level filter, export) | Connection logs | Settings → Advanced → Connection logs | Hosted (Settings → Advanced) |
| Protocol tester | Developer screen | Settings → Advanced → Protocol tester | Hosted (Settings → Advanced) |
| Support link, app info | Settings | Settings → About | Hosted (Settings) |
| Switch layout | Settings → Clean layout | Settings → Advanced → Switch to the classic layout | Hosted (Settings, both ways) |

## Web (beta, docs/WEB_TRACK.md)

The web app (`:web-app`) runs the same Clean screens from `clean-ui/src/commonMain`, so every
**Clean** row above works the same in the browser. Differences for now:

- **Hosted** rows (classic screens) show "in the Android app only for now" on the web.
- The trainer connects over Web Bluetooth (Chrome, Edge, Bluefy on iOS) on the first Start,
  with the same protocol code as the app (`clean-ui/.../trainer/TrainerProtocol.kt`). Without
  Web Bluetooth (Safari, iOS home screen), or when the device picker is cancelled, a simulated
  trainer runs the set.
- Heart rate: any strap or watch that broadcasts the standard heart rate service, connected per
  lifter for the browser session (the browser can't reconnect on its own after a reload).
- Rep counting uses the trainer's own warm-up and working counters; a weight change during the
  warm-up restarts the set at the new weight, as in the app.
- Demo videos show their poster; a tap opens the clip in a new tab.
- Browser back (and Android's back gesture in the installed web app) steps back like the app's
  back button.
- Web only: a first-launch intro with the install guide per device (Safari vs Bluefy on iOS),
  and Settings, Backup and restore saves or restores everything the browser stores as a file
  (the app's version saves to Download/Cable). The intro's guide is repeated there.
- Wide screens (600 dp and up, in the app too): the tabs become a rail on the left and screens
  stay at most 720 dp wide.
- Web only: anonymous usage counts (GoatCounter, no cookies or IDs) on the hosted beta, with an
  off switch in Settings, Backup and restore. The Android app keeps download counts only.
- Symbols the web's default font lacks come from a small DejaVu subset (`web-app/.../fonts`).

