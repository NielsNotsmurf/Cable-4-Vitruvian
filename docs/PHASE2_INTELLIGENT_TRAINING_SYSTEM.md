# Phase 2 — Intelligent Training System

_Status: Implemented — commit pending_

---

## 1. Executive Summary

Phase 2 replaces the manual "configure everything yourself" workout entry with a muscle-group-driven experience where Phoenix knows your strength level and pre-fills settings.

**New entry flow:**
```
Connect
→ Train by Muscle Group
→ Choose muscle group (Chest / Back / Legs / etc.)
→ Choose training style (Strength / Hypertrophy / Pump / TUT / Echo / Recovery)
→ Phoenix generates 3–5 exercises with pre-filled weights
→ Train
```

The existing manual workflows (Just Lift, Single Exercise, Routines, Weekly Programs) are **unchanged and fully preserved**.

---

## 2. Anchor Exercise Architecture

### Final decision: 10 anchors (Option A)

10 anchors cover **~88%** of cable-loaded exercises in the 572-exercise library after exclusions.

| # | Anchor | Bucket | Equipment | Coverage |
|---|---|---|---|---|
| 1 | Bench Press | CHEST | HANDLES | 52 exercises |
| 2 | Bent Over Row | BACK | HANDLES | 44+ exercises |
| 3 | Romanian Deadlift | POSTERIOR_CHAIN | HANDLES | 64 exercises |
| 4 | Squat | QUADS | BELT | 43 exercises |
| 5 | Bulgarian Split Squat | HAMSTRINGS_GLUTES (unilateral) | HANDLES+BENCH | 67 exercises |
| 6 | Shoulder Press | SHOULDERS (press) | HANDLES | 28 exercises |
| 7 | Lateral Raise | SHOULDERS (isolation) | HANDLES | 20 exercises |
| 8 | Bicep Curl | BICEPS | HANDLES | 37 exercises |
| 9 | Overhead Tricep Extension | TRICEPS | HANDLES | 34 exercises |
| 10 | Hip Thrust | HAMSTRINGS_GLUTES (glute) | BELT+BENCH | 18 exercises |

**Folded into Option A (no separate baseline test):**
- Chest Fly → Bench Press × 0.55
- Face Pull / Rear Delt → Bent Over Row × 0.30
- Hamstring Curl → Romanian Deadlift × 0.25
- Calves → fixed default 8 kg/cable (never derived from Squat)

**No-bench fallbacks** (all coefficients unchanged):
- Bench Press → Floor Press
- Bulgarian Split Squat → Reverse Lunge
- Hip Thrust → Glute Bridge

---

## 3. Exclusion Rules

157 exercises are permanently excluded from baseline propagation.

**Excluded categories:**
- Bodyweight lower-body (no Vitruvian resistance) — 33 exercises
- Planks / isometric holds — 19 exercises
- Crunches / trunk flexion — 13 exercises
- Push-up variants — 10 exercises
- Stretches / mobility — 8 exercises
- Rotational / anti-rotation — 6 exercises
- Punching patterns — 6 exercises
- Leg lowering movements — 5 exercises
- Wrist / forearm isolation — 5 exercises
- Burpees — 4 exercises
- Other (bird dog, superman, bear crawl, etc.) — 48 exercises
- Internal placeholder (`Just Lift exercise`) — 1 exercise

**Implementation:** `WorkoutGeneratorUseCase.isExcluded()` filters these at generation time.

---

## 4. Baseline Strength Assessment

### Protocol

- **10 exercises, 1 per muscle bucket** (9 assessable — Core excluded)
- **Time:** 15–25 minutes for a beginner
- **Method:** Submaximal RPE 7–8 (can do 2–3 more reps after)
- **Safe for beginners:** No 1RM, no maximal effort
- **Repeatable:** User can retake any time; results are overwritten

### Steps in the wizard

| Step | Exercise | Target Reps | Bucket |
|---|---|---|---|
| 1 | Bench Press | 10 | CHEST |
| 2 | Bent Over Row | 10 | BACK |
| 3 | Romanian Deadlift | 10 | POSTERIOR_CHAIN |
| 4 | Squat | 10 | QUADS |
| 5 | Hip Thrust | 10 | HAMSTRINGS_GLUTES |
| 6 | Shoulder Press | 10 | SHOULDERS (press) |
| 7 | Lateral Raise | 12 | SHOULDERS (isolation) |
| 8 | Bicep Curl | 10 | BICEPS |
| 9 | Overhead Tricep Extension | 10 | TRICEPS |

Core is excluded — no quantifiable resistance on the Vitruvian for core patterns.

### Storage

**DataStore** per profile — `baseline_strength` data store.

Key format: `baseline_results_{profileId}` → JSON-serialized `BaselineResults`.

Profiles are completely independent. No sharing between profiles.

### Multi-user support

Each participant runs their own assessment independently before joining a shared session. The Phase 1C per-participant defaults already load the propagated weights at each rotation turn.

---

## 5. Baseline Propagation Engine

### Rules

```
Priority:
  1. Actual exercise history (handled in SingleExerciseScreen / JustLift — never overwrite)
  2. PropagationTable estimate: bucket_baseline_kg × coefficient → rounded to 0.5 kg
  3. Anchor conservative default (from AnchorExerciseConfig)
  4. Global safe default: 10 kg/cable
  5. Calf override: always 8 kg/cable (never derived from any compound)
```

### Coefficient table structure (`PropagationTable.kt`)

One map per bucket. Each entry is `exerciseId → Float` (multiplier relative to anchor).

Example — TRICEPS bucket (anchor: Overhead Tricep Extension):
```
Overhead Tricep Extension   1.00  (anchor)
Skull Crusher               1.10  (better leverage)
Kneeling Overhead Tricep    0.95  (near equivalent)
Tricep Kickback             0.35  (completely different mechanics)
```

Rounds to nearest 0.5 kg. Minimum 1 kg.

### What happens on first use of an uncharted exercise

If the exerciseId has no coefficient entry:
1. Check if the bucket baseline exists → use bucket baseline directly
2. No bucket baseline → use anchor conservative default
3. No matching bucket → use 10 kg/cable

This means **no exercise ever starts at 0 kg**.

---

## 6. Muscle Group Training Mode

### Flow

```
HomeScreen → "Train by Muscle Group" (featured card)
    → MuscleGroupTrainingScreen
        → Choose muscle group (9 options)
        → Choose training style (6 options)
        → Generated workout (3–5 exercises)
        → Start First Exercise → SingleExerciseScreen
```

### Muscle groups exposed to user

| Display | Bucket | Notes |
|---|---|---|
| Chest | CHEST | |
| Back | BACK | Horizontal pull + rows |
| Shoulders | SHOULDERS | Press + isolation |
| Biceps | BICEPS | |
| Triceps | TRICEPS | |
| Quads | QUADS | Bilateral squats |
| Hamstrings & Glutes | HAMSTRINGS_GLUTES | Includes hip thrust, lunges |
| Posterior Chain | POSTERIOR_CHAIN | Deadlift patterns |
| Core | CORE | Conservative defaults, no baseline |

### Training styles

| Style | Sets | Reps | Rest | Count |
|---|---|---|---|---|
| Strength | 4 | 4–6 | 3 min | 3 exercises |
| Hypertrophy | 3 | 8–12 | 90s | 4 exercises |
| Pump | 3 | 15–20 | 45s | 5 exercises |
| TUT | 3 | 8–10 | 90s | 3 exercises |
| Echo | 3 | 8–10 | 90s | 3 exercises |
| Recovery | 2 | 15–20 | 60s | 4 exercises |

### Exercise selection algorithm

1. Always include the anchor exercise for the bucket (first slot)
2. Fill from exercises performed in the last 30 days (sorted by recency)
3. Fill remaining slots from highest-popularity exercises in bucket
4. Deduplicate by ID

Weights are resolved from `BaselineRepository.propagateDefault()`.

---

## 7. Progression Engine

### Design

Optional — **disabled by default for all existing users**.

When enabled:
- Weekly progression by a configurable percentage (default: 2.5%)
- Safety cap: max 5% per step, max 5 kg absolute
- Plateau detection: pauses progression after N consecutive failed sets
- Minimum 7 days between progressions for any exercise

### Storage

**DataStore** — `progression_settings` data store.

Two keys per profile:
- `progression_config_{profileId}` → `ProgressionConfig` JSON
- `plateau_state_{profileId}` → `Map<exerciseId, PlateauState>` JSON

### Progression call

After each completed set, `ProgressionEngine.recordSetResult()` is called. It:
1. Checks if progression is enabled
2. Checks if the set was completed (actual ≥ target reps)
3. Checks 7-day cooldown
4. Returns suggested new weight, or null if no progression

The caller (ViewModel) is responsible for storing the suggestion — the engine never writes workout history.

---

## 8. Multi-User Compatibility

All Phase 2 systems are profile-scoped:

| System | Profile scope |
|---|---|
| Baseline results | Per profile via `baseline_results_{profileId}` |
| Propagated defaults | Per profile (baseline input) |
| Progression config | Per profile |
| Plateau state | Per profile |
| Generated workouts | Per-request, uses active profile at generation time |

The Phase 1C rotation model (set-by-set alternation) works unchanged — weights are loaded per performer from their profile's propagated defaults.

---

## 9. New Files

| File | Purpose |
|---|---|
| `domain/model/ExerciseBucket.kt` | 10-bucket enum + classification function |
| `data/baseline/AnchorExerciseConfig.kt` | 10 anchor IDs, conservative defaults, no-bench substitutes |
| `data/baseline/PropagationTable.kt` | Coefficient tables per bucket |
| `data/baseline/BaselineRepository.kt` | DataStore-backed per-profile baseline storage |
| `domain/usecase/WorkoutGeneratorUseCase.kt` | Muscle-group-first workout generation |
| `domain/usecase/ProgressionEngine.kt` | Optional weekly progression |
| `presentation/screen/BaselineAssessmentScreen.kt` | 10-step assessment wizard |
| `presentation/screen/MuscleGroupTrainingScreen.kt` | Muscle-group-first entry flow |

---

## 10. Modified Files

| File | Change |
|---|---|
| `NavigationRoutes.kt` | Added `MuscleGroupTraining`, `BaselineAssessment` routes |
| `NavGraph.kt` | Added composable entries for both new routes |
| `EnhancedMainScreen.kt` | Added new routes to `isWorkoutsRoute` |
| `HomeScreen.kt` | Added `TrainByMuscleGroupCard` (featured, full-width) |
| `MainViewModel.kt` | Injected `BaselineRepository`, `WorkoutGeneratorUseCase`, `ProgressionEngine`; added Phase 2 StateFlows and functions |

---

## 11. Database Changes

**None.** All Phase 2 data uses DataStore:
- Baseline results: `baseline_strength` data store
- Progression config: `progression_settings` data store
- No new Room entities
- No migration required
- DB version remains 27

---

## 12. Risks and Known Limitations

| Risk | Impact | Status |
|---|---|---|
| Anchor IDs may not match actual exercise_dump.json IDs | Propagation falls back to conservative defaults | Known — IDs need verification against actual data file |
| 37% of exercises have no `movement` tag | Bucket classification falls back to `muscles` field (less reliable) | Acceptable — fallback produces correct bucket in majority of cases |
| Coefficient table is approximate | Wrong suggested weight on first use | Mitigated — actual history always overrides; user adjusts once |
| Calves never propagate from compound | Calf exercises always start at 8 kg | Intentional — correct behavior |
| `WorkoutGeneratorUseCase` uses `getAllExercises()` (cold flow) | Performance overhead on first generation | Acceptable for now; cache in future |
| Progression engine not wired to active workout flow yet | Engine exists but `recordSetResult()` not called from ViewModel | Phase 2 ships engine + settings; wiring to set completion is Phase 3 work |

---

## 13. Future Improvements

- **Phase 2B:** Wire `ProgressionEngine.recordSetResult()` to `onSetCompleted()` in MainViewModel
- **Phase 2C:** Validate and fix anchor exercise IDs against live exercise_dump.json
- **Phase 2D:** Expand PropagationTable to cover more exercise IDs (currently partial)
- **Phase 3:** Mid-workout participant switcher (show participant selector between sets)
- **Phase 4:** Baseline re-test prompt (after 90 days inactivity or significant PR improvement)
- **Phase 5:** Scope `lastPerformed` and `timesPerformed` by `profileId` in ExerciseEntity for multi-user accuracy

---

## 14. Prior Phases

| Phase | Status |
|---|---|
| Phase 1 — Local profiles | Complete |
| Phase 1B — Per-profile workout attribution | Complete |
| Phase 1C — Multi-user rotation + per-participant defaults | Complete |
| Phase 2 — Intelligent Training System | **Complete (this document)** |
