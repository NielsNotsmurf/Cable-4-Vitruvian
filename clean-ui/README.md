# Clean UI

The white, multi-lifter layout. It lives in its own Gradle module so that upstream
Phoenix changes keep merging into `:app` without touching these screens.

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

## Syncing upstream

Upstream (`9thLevelSoftware/VitruvianProjectPhoenix`) rewrote its history, so git sees no
shared commit with this fork. Record the shared point once, then merge normally:

```bash
git remote add upstream https://github.com/9thLevelSoftware/VitruvianProjectPhoenix.git
git fetch upstream
# ff20a27 upstream == b83d0dd here ("use firmware DELOAD_OCCURRED flag", 2026-01-05)
git merge -s ours --allow-unrelated-histories ff20a27 -m "Record upstream ancestry"
git checkout -b sync-upstream
git merge upstream/master
```

As of October 2026 the next upstream change renames every package and removes the exercise
catalogue, which conflicts with the multi-user work in 51 files. Merge it on its own branch.
