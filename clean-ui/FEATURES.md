# Feature coverage

Every feature of the classic app, where it lives in the Clean layout, and how far the Android
Clean screens have got. The web preview (https://claude.ai/artifact/SN62xxxzSGx2CZQvyYrKJi)
covers every row; Android catches up screen by screen.

Android status:
- **Clean**: built as a Clean screen.
- **Hosted**: the classic screen runs inside the Clean layout (white theme).
- **Classic**: only reachable through Settings → Switch to classic layout for now.

| Feature | Classic location | Clean layout | Android |
|---|---|---|---|
| Who's training (up to 4, sets rotate) | Home participants picker | Train → Who's training card | Clean |
| Add profile from training | Profiles screen | Train → + Add | Classic |
| Profiles: add, edit colour, delete, default | Profiles screen | Settings → Profiles (also Train → Manage) | Classic |
| Per-lifter 1RM next to exercises | — (new) | Exercise rows, recent list | Clean |
| Single exercise with full config | Single Exercise | Train → Pick an exercise → Set setup | Clean (partial, see below) |
| Train by muscle group | Muscle Group Training | Train → Muscle groups | Classic |
| Exercise search, muscle/equipment filters | Exercise picker | Exercises screen | Clean (no equipment filter yet) |
| Favourites + "favourites only" | Exercise picker | ★ on rows, Favourites chip | Classic |
| Exercise video demos | Exercise picker / config | ▶ on rows and in Set setup | Classic |
| Modes: Old School, Pump, TUT, TUT Beast, Eccentric Only, Echo | Exercise config | Set setup mode chips + description | Clean |
| Echo level (Hard…Epic) and eccentric load 0–150% | Exercise config | Set setup when Echo is picked | Clean (eccentric only) |
| % of 1RM loading per lifter | — (new) | Set setup presets, slider, lifter cards | Clean |
| Per-set reps, weights (%), rest | Exercise config sets list | Set setup → tap a set | Classic |
| AMRAP and timed (duration) sets | Exercise config | Set type: Reps / AMRAP / Timed | Classic |
| Bodyweight exercises | Exercise config | Bodyweight exercises skip loads | Classic |
| Single / double cable | Exercise config | Set setup → Cables | Classic |
| Weight change per rep (progression/regression) | Exercise config, Just Lift | More options / Just Lift | Clean (Just Lift hosted) |
| Warm-up reps | Active workout | More options + Settings default | Hosted |
| Stop at top | Settings / config | More options + Settings | Classic |
| Remember as exercise default | Saved per-profile defaults | More options → Remember | Clean (automatic) |
| Trainer scan, pick, connect, disconnect | Device picker, banner | Connection pill on Train, Settings → Trainer | Hosted (classic dialogs) |
| Bluetooth permissions | Permission screen | Allow Bluetooth sheet | Clean |
| Connection lost / error dialogs | Dialogs | Connection lost sheet | Hosted |
| Auto-connect | Settings | Settings → Connect automatically | Classic |
| Countdown before a set | Countdown card | Get ready screen | Hosted |
| Live reps, load, velocity, power, cable bars | Active workout | Lifting screen | Hosted |
| Performer banner, set X of Y, up next | Active workout | Lifting screen band + up next | Hosted |
| Adjust weight mid-set | Active workout | − / + on lifting screen | Hosted |
| PR celebration (heaviest, best volume) | PR animation | New PR overlay | Hosted |
| Set summary: avg, peak, force over time | Set summary card | Rest screen summary card | Hosted |
| Rest timer, skip, adjust next set, up next, end workout | Rest timer card | Rest screen | Hosted |
| Autoplay | Settings | Settings → Autoplay | Classic |
| Workout complete summary | Active workout | Done screen | Hosted |
| Just Lift with auto-start and auto-stop | Just Lift | Train → Just Lift | Hosted |
| Strength assessment (9 anchors, too easy/right/hard) | Baseline Assessment | Train → Assessment | Classic |
| Rep-max benchmark → 1RM | — (new) | Assessment → Enter a rep-max | Not yet |
| Workout history, per session detail, delete, delete all | History tab | History tab | Classic |
| Analytics: strength score, this week, recent PRs, top exercises | Analytics dashboard | Analytics → Overview | Classic |
| PR tracker, weight/volume PR, chart or list | Analytics progression | Analytics → Progress | Classic |
| Muscle balance, consistency, volume vs intensity, modes | Insights | Analytics → Insights | Classic |
| Safety events (deload, ROM, spotter) | Safety card | Insights + session detail | Classic |
| Per-profile filter | Analytics chips | Lifter chips on History and Analytics | Classic |
| Export CSV (workouts, PRs, PR progression) | Analytics | Analytics ⤓, History ⋮, Settings | Classic |
| Weight unit kg / lb | Settings | Settings → Weight unit | Clean (reads it) |
| Default reps | Settings | Settings | Classic |
| Workout beeps, haptics | Settings | Settings | Classic |
| Show exercise videos | Settings | Settings | Classic |
| LED colour scheme | Settings | Settings → LED colour | Classic |
| Theme light / dark / system | Settings | Settings → Theme | Classic |
| Backup and restore | Settings | Settings → Data | Classic |
| Connection logs (search, level filter, export) | Connection logs | Settings → Connection logs | Classic |
| Protocol tester | Developer screen | Settings → About → Protocol tester | Classic |
| Support link, app info | Settings | Settings → About | Classic |
| Switch layout | Settings → Clean layout | Settings → Switch to classic layout | Clean |
