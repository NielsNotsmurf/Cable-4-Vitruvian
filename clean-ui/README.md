# Clean UI

The white, multi-lifter layout. It lives in its own Gradle module so it stays apart
from the classic Phoenix code while the classic screens are retired one by one.

## Rules

- `clean-ui` never imports anything from `:app`. It talks to `TrainerGateway`.
- `app/.../cleanui/CleanUiBridge.kt` implements the gateway. It's the only file that knows both sides.
- Hooks in upstream files are kept as small as possible:
  `settings.gradle.kts`, the `:clean-ui` dependency in `app/build.gradle.kts`,
  the branch in `MainActivity`, and the "Switch to the new Clean layout" button in classic Settings.
- Clean UI data (on/off switch, benchmark and manual 1RMs, last % per exercise) is stored in
  its own DataStore (`clean_ui`), not in upstream's Room schema.

## How loads are chosen

1. A lifter's 1RM for the exercise is the higher of their stored 1RM (benchmark or manual) and
   the best Epley estimate from their PR history: weight × (1 + reps / 30), reps capped at 12.
2. Without either, it's scaled from their baseline benchmark of a related exercise and shown with `~`.
3. Starting load = 1RM × chosen %, rounded to 0.5 kg per cable and clamped to 0.5–100 kg.
4. A lifter with no 1RM starts from the load they used last time, or 10 kg.

When several lifters train, each lifter's load is saved as their per-exercise default before
the set starts. The existing rotation then loads the right weight for whoever is up next.

## Upstream

Upstream syncing is frozen (decided 12 Oct 2026). This fork is now its own app.
Upstream (`9thLevelSoftware/VitruvianProjectPhoenix`) rewrote its history, and its next change
renames every package and removes the exercise catalogue, which conflicts with the multi-user
work in 51 files. If you ever want to port a single upstream fix, cherry-pick it by hand.
The module split stays: it keeps the classic code and the new screens apart while the classic
screens are retired one by one.
