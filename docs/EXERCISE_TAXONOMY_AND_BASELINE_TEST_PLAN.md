# Exercise Taxonomy and Baseline Strength Assessment Plan

_Cable — Design document, not an implementation spec._
_Status: Draft — not yet approved or implemented._

---

## 1. Exercise Inventory and Taxonomy

### 1.1 Current state of the exercise library

The library contains **572 exercises** loaded from `exercise_dump.json`.

| Field | Values |
|-------|--------|
| `muscleGroups` (broad) | LEGS, ARMS, SHOULDERS, BACK, CORE, CHEST |
| `muscles` (fine-grained) | glutes, quads, hamstrings, shoulders, core, upper_back, lats, triceps, biceps, obliques, calves, chest, abductors, traps, lower_back, forearms |
| `movement` (pattern) | row, shoulder_press, bicep_curl, chest_press, fly, shoulder_isolation, deadlift, squat, glute_accessory, tricep_extension, hamstring_curl, leg_extension, unilateral_leg |
| `sidedness` | bilateral (286), unilateral (200), alternating (48), none/null (38) |

### 1.2 Taxonomy problems

**Problem 1 — Coarse muscleGroups**

`LEGS` covers 272 exercises spanning quads, hamstrings, glutes, calves, and hip flexors. `ARMS` covers both biceps and triceps. These are too broad for workout generation or baseline scoping.

Recommended reclassification using the `muscles` field:

| Canonical bucket | Source `muscleGroups` | Primary `muscles` covered |
|---|---|---|
| Chest | CHEST | chest |
| Back — horizontal pull | BACK | upper_back, lats (row movement) |
| Back — posterior chain | LEGS, BACK | lower_back, lats (deadlift movement) |
| Shoulders — press | SHOULDERS | shoulders (shoulder_press movement) |
| Shoulders — isolation | SHOULDERS | shoulders (shoulder_isolation, fly movements) |
| Biceps | ARMS | biceps |
| Triceps | ARMS | triceps |
| Quads | LEGS | quads |
| Hamstrings | LEGS | hamstrings |
| Glutes | LEGS | glutes |
| Calves | LEGS | calves |
| Core | CORE | core, obliques |
| Full Body / Compound | LEGS+BACK+CORE etc. | 4+ muscle groups |

**Problem 2 — Duplicate exercise names**

88 exercise names appear more than once. These are legitimate variants (different equipment) but share identical names, which breaks analytics grouping and PR tracking.

Top duplicates (by count):

| Name | Count | Differentiator |
|---|---|---|
| Seated Row | 8 | SHORT_BAR, BENCH+SHORT_BAR, BENCH+HANDLES, BAR, ROPE |
| Calf Raise | 6 | SHORT_BAR, BAR+BLACK_CABLES, BAR+GREY_CABLES, BELT, HANDLES |
| Skull Crusher | 6 | BENCH+ROPE, BENCH+HANDLES, SHORT_BAR, HANDLES, ROPE |
| Front Raise | 5 | HANDLES, ROPE, SHORT_BAR, BAR+BLACK_CABLES |
| Glute Bridge | 5 | BELT, SHORT_BAR, BAR, none |
| Overhead Tricep Extension | 5 | BAR+GREY_CABLES, BAR+BLACK_CABLES, ROPE, HANDLES |
| Pullover | 5 | SHORT_BAR, BENCH+ROPE, BENCH+SHORT_BAR, HANDLES |
| Seated Shoulder Press | 5 | BENCH+HANDLES, HANDLES |
| SL Romanian Deadlift | 5 | ROPE, HANDLES, BAR, SHORT_BAR |
| Bench Press | 2 | (trailing whitespace variant — data quality issue) |
| Incline Bench Press | 4 | BENCH+HANDLES, BAR+BENCH+BLACK_CABLES |

The duplicates are intentional (equipment variants), but the identical names cause problems for:
- PR tracking (`exerciseId` resolves to different IDs per variant)
- Analytics grouping (sessions split across IDs)
- Workout generation (cannot deterministically pick "the" Bench Press)

**Recommendation**: For baseline and workout generation, always select by **`id`** not by `name`. Define canonical IDs for each baseline exercise in config.

**Problem 3 — Bodyweight/non-cable exercises mixed in**

Exercises like Burpees, Push-ups, Planks, Bird Dogs, Copenhagen Planks have no load the Vitruvian can track or control. They appear as valid exercises in the library. They cannot be used in baseline assessment (no measurable resistance) and should not be chosen as defaults for workout generation unless the user explicitly selects them.

**Problem 4 — Missing `movement` tag on 37% of exercises**

259 exercises have no `movement` value. Most are compound or accessory movements. Without a movement tag, propagation coefficients (Part 3) cannot be reliably computed. The tag needs to be more complete before automation can rely on it.

**Problem 5 — muscleGroups includes FULL_BODY in ExerciseCategory but not in the data**

`ExerciseCategory.kt` defines `FULL_BODY` as a category. The JSON uses `LEGS`, `ARMS`, `SHOULDERS`, `BACK`, `CORE`, `CHEST`. There is no `FULL_BODY` in the actual data. The UI filter for "Full Body" has no matching exercises.

### 1.3 Recommended exercise buckets for Cable

For all workout generation, baseline testing, and analytics, use these 10 canonical buckets derived from the `muscles` field rather than `muscleGroups`:

1. **Chest** — primary: chest; movement: chest_press, fly
2. **Back / Horizontal Pull** — primary: upper_back, lats; movement: row
3. **Posterior Chain** — primary: hamstrings, lower_back, lats; movement: deadlift
4. **Shoulders** — primary: shoulders; movement: shoulder_press, shoulder_isolation
5. **Biceps** — primary: biceps; movement: bicep_curl
6. **Triceps** — primary: triceps; movement: tricep_extension
7. **Quads** — primary: quads; movement: squat, leg_extension
8. **Hamstrings / Glutes** — primary: glutes, hamstrings; movement: glute_accessory, hamstring_curl, unilateral_leg
9. **Calves** — primary: calves
10. **Core** — primary: core, obliques

Chest and Back are separated by movement direction (push vs pull). Posterior Chain (deadlift pattern) is kept separate from Back (row pattern) because the strength ratio between the two does not generalize cleanly.

---

## 2. Baseline Strength Assessment Design

### 2.1 Purpose

A new user should not start at zero. Defaults of "10 reps × 10 kg" are dangerous for strong users (too light, uninteresting) and intimidating for beginners (may not complete the target reps). An initial assessment of 10–15 minutes gives Cable enough data to populate meaningful defaults for all other exercises.

### 2.2 Constraints

- Must complete in under 15 minutes for a single user
- Must be safe for beginners — no maximal effort, no 1RM
- Must be performable with common Vitruvian attachments (HANDLES preferred)
- Must produce a weight per cable in kg that can be stored in `JustLiftDefaults` or `SingleExerciseDefaults`
- Must be bilaterally symmetric (one cable config) for clean baseline numbers
- Cannot rely on bodyweight-only exercises (no quantifiable resistance)

### 2.3 Assessment protocol

Use a **submaximal estimation protocol**: the user performs 8–12 reps at a self-selected starting weight, then adjusts until they find the weight where they reach RPE 7–8 (hard but could do 2–3 more reps). This is safer than a true 1RM and still produces a reliable baseline.

For each test exercise:
1. Cable suggests a starting weight (based on body weight if known, else conservative default)
2. User performs a set of 10 reps
3. User rates difficulty: "Too easy / About right / Too hard"
4. Cable adjusts weight and repeats if needed (max 2 rounds)
5. Result is the weight at which user completed 10 reps at RPE 7–8

### 2.4 Test exercises (one per bucket)

| Bucket | Test Exercise | ID to use | Rationale |
|---|---|---|---|
| Chest | Bench Press | `cJt26IdtckFcJsq1` (HANDLES) | Most common chest press on Vitruvian; bilateral HANDLES require no extra setup |
| Back / Pull | Bent Over Row | `67195cbd-7e2b-4d96-804b-0182b8bf2bab` (HANDLES) | Standard horizontal pull; most users can perform safely |
| Posterior Chain | Romanian Deadlift | `WAB_Z7EUGeUxF9ce` (HANDLES) | Hip hinge without bar setup; HANDLES available by default |
| Shoulders | Seated Shoulder Press | `GukBjh5fv1FBUvO1` (BENCH+HANDLES) | Seated removes stability demand; most representative |
| Biceps | Bicep Curl | `fc5ca114-3cb6-462a-ab54-7ef1233b5fc2` (HANDLES) | Standard; no equipment beyond handles |
| Triceps | Overhead Tricep Extension | `e3c59976-efa7-4e7d-8848-77e864ba1d0f` (HANDLES) | Safest overhead tricep movement for beginners |
| Quads | Squat | First `squat`-movement bilateral entry (BELT or no eq) | Bilateral, free-standing; no equipment constraint |
| Hamstrings / Glutes | Hip Thrust | `T6Vf4-OIurKVfECK` (BELT+BENCH) | Isolated glute/hamstring; safer than lunge for beginners |
| Calves | Calf Raise | `j3Y1MpvaeGPy0o99` (SHORT_BAR) | Simple, low injury risk |
| Core | _Skip — no quantifiable resistance_ | — | Core baseline cannot be meaningfully quantified by weight |

**Core exclusion rationale**: Core exercises on the Vitruvian are predominantly anti-rotation and stability patterns (planks, bird dogs, dead bugs). There is no clean mapping from "weight lifted" to "core strength." Core exercise defaults should remain at conservative fixed values until the user has performed them manually.

### 2.5 Can any buckets share a baseline?

Yes, with caution:

- **Hamstrings** can be partially inferred from the posterior chain (RDL) test. The SL Hamstring Curl baseline can be estimated at ~35% of RDL weight per cable.
- **Triceps** and **Chest** are correlated — triceps are always tested secondarily during any chest press. If the user finds the tricep test redundant, it can be made optional.
- **Biceps** and **Back** overlap (biceps are heavily loaded during rows). If assessment time needs to be cut, Biceps can be inferred from Back at ~40%.

Minimum viable assessment (6 exercises, ~8–10 minutes):

1. Bench Press → Chest + (partial Triceps)
2. Bent Over Row → Back + (partial Biceps)
3. Romanian Deadlift → Posterior Chain + (partial Hamstrings/Glutes)
4. Squat → Quads
5. Seated Shoulder Press → Shoulders
6. Hip Thrust → Glutes/Hamstrings (isolation confirmation)

---

## 3. Baseline Propagation

### 3.1 Goal

After completing a baseline for bucket B, all exercises in bucket B (and related buckets) should have a default weight pre-populated. No exercise should show 0 kg or require the user to configure it manually on first use.

### 3.2 Propagation method

Use a **movement coefficient table** applied to the bucket baseline weight. Coefficients are relative to the canonical baseline for that bucket. All values are approximate and should be validated against real user data before being locked in.

**Chest bucket** (baseline: Bench Press)

| Exercise | Coefficient | Notes |
|---|---|---|
| Bench Press | 1.00 | Baseline |
| Incline Bench Press | 0.85 | Anterior delt contribution reduces pec load |
| Decline Bench Press | 0.95 | Slightly stronger angle for most |
| Close Grip Bench Press | 0.90 | More triceps, less pec |
| Neutral Grip Bench Press | 0.95 | Near equivalent |
| Floor Press | 0.90 | Reduced ROM |
| Cable Fly | 0.55 | Isolation, much lower load |
| Incline Fly | 0.50 | Isolation at weaker angle |
| SA Bench Press (per cable) | 0.60 | Single arm, one cable |
| SA Floor Press (per cable) | 0.55 | Single arm floor |
| Chest Press – Gym Ball | 0.75 | Instability reduces load |

**Back / Pull bucket** (baseline: Bent Over Row)

| Exercise | Coefficient | Notes |
|---|---|---|
| Bent Over Row | 1.00 | Baseline |
| Bent Over Row – Wide Grip | 0.95 | Similar load |
| Bent Over Row – Reverse Grip | 1.00 | Equivalent load, different grip |
| Seated Row | 0.95 | Braced; slightly better leverage |
| Upright Row | 0.65 | Different plane; traps dominant |
| Crossover Lateral Raise | 0.25 | Isolation, very low load |
| Rear Delt Fly | 0.20 | Isolation |

**Posterior Chain bucket** (baseline: Romanian Deadlift)

| Exercise | Coefficient | Notes |
|---|---|---|
| Romanian Deadlift | 1.00 | Baseline |
| Conventional Deadlift | 1.15 | Full range, slightly heavier |
| Sumo Deadlift | 1.05 | Hip-dominant variant |
| SL Romanian Deadlift (per cable) | 0.55 | Single leg, reduced load |
| Good Morning | 0.65 | Similar but more lower back stress |
| Stiff Leg Deadlift | 0.90 | Less hip extension |

**Shoulders bucket** (baseline: Seated Shoulder Press)

| Exercise | Coefficient | Notes |
|---|---|---|
| Seated Shoulder Press | 1.00 | Baseline |
| Arnold Press | 0.90 | Added rotation reduces effective load |
| Standing Shoulder Press | 0.95 | Core stabilization overhead |
| Lateral Raise | 0.20 | Pure isolation |
| Front Raise | 0.20 | Pure isolation |
| Bent Over SA Lateral Raise | 0.15 | Isolation, awkward angle |

**Biceps bucket** (baseline: derived from Bent Over Row × 0.40 if not tested)

| Exercise | Coefficient | Notes |
|---|---|---|
| Bicep Curl | 1.00 | Baseline |
| Hammer Curl | 0.95 | Near equivalent |
| Bicep Curl – Pronated | 0.85 | Weaker grip position |
| Concentration Curl | 0.80 | Isolation, controlled |
| SA Bicep Curl (per cable) | 0.75 | Single arm |
| Seated Bicep Curl | 1.00 | Equivalent to standing |
| Wide Grip Bicep Curl | 0.90 | Slightly weaker leverage |

**Triceps bucket** (baseline: derived from Bench Press × 0.45 if not tested separately)

| Exercise | Coefficient | Notes |
|---|---|---|
| Overhead Tricep Extension | 1.00 | Baseline |
| Skull Crusher | 0.90 | Floor position, different leverage |
| Tricep Kickback | 0.40 | Isolation, very low load |
| Kneeling Overhead Tricep Extension | 0.95 | Near equivalent |
| Tricep Dip (belt-assisted) | N/A | Bodyweight-based, skip propagation |

**Quads bucket** (baseline: Squat)

| Exercise | Coefficient | Notes |
|---|---|---|
| Squat | 1.00 | Baseline |
| Goblet Squat | 0.85 | Core stability overhead |
| Front Squat | 0.85 | Anterior chain emphasis |
| Bulgarian Split Squat (per cable) | 0.65 | Single leg, stability demand |
| Sumo Squat | 1.00 | Near equivalent |
| Lying Leg Extension (per cable) | 0.30 | Pure quad isolation |

**Hamstrings / Glutes bucket** (baseline: Hip Thrust)

| Exercise | Coefficient | Notes |
|---|---|---|
| Hip Thrust | 1.00 | Baseline |
| Glute Bridge | 0.85 | Shorter ROM |
| SL Hip Thrust (per cable) | 0.60 | Single leg |
| SL Glute Bridge (per cable) | 0.50 | Single leg, no bench elevation |
| Donkey Kicks (per cable) | 0.20 | Isolation accessory |
| Standing Glute Kickback (per cable) | 0.20 | Isolation |
| Lying Hamstring Curl | 0.35 | Different muscle emphasis |
| SL Hamstring Curl (per cable) | 0.25 | Isolation |

**Calves bucket** (baseline: Calf Raise)

| Exercise | Coefficient | Notes |
|---|---|---|
| Calf Raise | 1.00 | Baseline |
| SL Calf Raise (per cable) | 0.65 | Single leg |

### 3.3 Application logic

When a user picks an exercise for the first time (no personal history):

1. Find the bucket for the exercise.
2. Look up the baseline weight for that bucket (from assessment results).
3. Multiply by the coefficient for this specific exercise.
4. Round to nearest 0.5 kg.
5. Store as `SingleExerciseDefaults` for this profile.

When no baseline has been completed:

- Use a body-weight-derived heuristic (if body weight is known):
  - Chest press: body_weight × 0.25 per cable
  - Row: body_weight × 0.20 per cable
  - Squat: body_weight × 0.35 per cable
  - etc.
- Or fall back to a safe conservative constant (10 kg per cable) until the user has performed the exercise at least once.

### 3.4 Risk: coefficients are estimates

These coefficients are educated approximations based on common strength ratios in the exercise science literature. They will not be accurate for all users. The correct approach is:

1. Apply the coefficient on first use (prevents "0 kg" starts).
2. After the user's first completed set, override with actual weight used.
3. Never re-apply coefficients once real data exists.

---

## 4. Muscle-Group-First Workout Workflow

### 4.1 Proposed workflow

```
Connect machine
       ↓
Start training session
       ↓
Choose muscle group  (e.g., "Back")
       ↓
Cable suggests 3–5 exercises
       ↓
Defaults already populated from history / baseline
       ↓
Workout begins
```

### 4.2 Strengths

- Very low friction: no routine required, no configuration required
- Defaults feel personalized even on first use (after baseline)
- Naturally compatible with the participant rotation model: the same exercise list applies to all participants in turn
- Fits the "I have 20 minutes, I want to train Back" use case well

### 4.3 Weaknesses

- Recommending exercises without knowing user equipment setup risks suggesting exercises the user cannot perform (e.g., BENCH exercises if no bench present)
- "Back" still groups horizontal pull (rows) and posterior chain (deadlifts) together — users training one do not always want the other
- Suggesting 3–5 exercises requires a ranking model — by popularity, by muscle coverage, by recent frequency — which is non-trivial and will produce bad suggestions if not validated
- Single-exercise mode allows users to deviate from suggestions mid-session; the flow could create confusion about whether they are "in a routine" or "free-styling"

### 4.4 UX risks

- If suggestions are wrong (wrong weight, wrong exercise for the user's goals), trust breaks immediately
- Users who already have a workout in mind will skip the suggestions entirely — the feature needs to feel optional, not mandatory
- Multi-set tracking must handle that "Chest Day" might mean 4 exercises × 3 sets = 12 turns per user; the current `_exerciseTurnCount` model handles single-exercise and routine modes but not a free-form session with multiple exercises chosen ad hoc

### 4.5 Data requirements

To implement this workflow, Cable needs:

| Data | Source | Available now? |
|---|---|---|
| Muscle-group bucket classification per exercise | `muscles` field + derivation | Partially (via `muscles`; not indexed) |
| Canonical exercise per bucket | Hardcoded config | No |
| Equipment availability | User preference / session setup | No |
| Exercise popularity ranking | `popularity` field in JSON | Yes |
| Propagated defaults per exercise per profile | Baseline + coefficients | No (coefficient table not implemented) |
| Last-performed date per exercise per profile | Room DB `timesPerformed`, `lastPerformed` | Yes (in ExerciseEntity, but not scoped per profile) |

---

## 5. Multi-User Compatibility

### 5.1 Baseline assessment with multiple participants

The assessment should be run **per profile, independently**, not as a shared session. Reason: baseline weights are highly individual. Running two users through the same baseline simultaneously would require both users to use the same weight at the same time, which breaks the protocol.

**Recommended flow:**

1. Before the first shared session, Cable detects that participant P has no baseline.
2. Cable prompts: "Niels hasn't completed a strength assessment. Run it now? (5–10 min)"
3. P completes the assessment solo (just Lift mode, one exercise at a time).
4. Assessment results are stored under `profileId = P.id` in the per-profile DataStore.
5. Once both participants have baselines, the shared session starts with accurate defaults for both.

### 5.2 Two participants training together

Participant rotation (Phase 1C model) applies directly:
- Exercise: Bench Press, 3 sets
- Turn order: Niels set 1 → Anna set 1 → Niels set 2 → Anna set 2 → Niels set 3 → Anna set 3
- Defaults loaded per performer at each turn (already implemented in Phase 1C for single-exercise mode)
- Weights shown before each set for that performer

No structural changes needed. Baseline data feeds `SingleExerciseDefaults` per profile, which the existing Phase 1C code already loads.

### 5.3 Three or four participants

The `_exerciseTurnCount` model generalizes to N participants cleanly (already designed for N). No code changes needed.

Physical constraint: With 4 participants, a 3-set exercise = 12 total turns. Rest time between each person's sets increases. The machine will be idle most of the time. The UI should make this visible (e.g., "Your next set in ~6 minutes").

### 5.4 Shared baseline session (optional future feature)

Not recommended. Trying to assess two people simultaneously on one machine adds UX complexity without saving meaningful time. Independent assessments are cleaner.

---

## 6. Risks and Open Questions

### Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Duplicate exercise names break PR tracking | High | Always track by `id`, never by `name` |
| Coefficient estimates are inaccurate for many users | Medium | Override on first real use; treat as initial suggestion only |
| Missing `movement` tags on 37% of exercises | Medium | Cannot auto-propagate without tags; fallback to bucket-level defaults |
| Bodyweight exercises mixed into library | Low | Filter by `equipment != []` OR sidedness is not null before baseline/propagation |
| `LEGS` bucket is too large for meaningful suggestions | Medium | Use `muscles` field for sub-classification instead |
| Baseline tests use specific IDs that could change | Low | Pin IDs in a versioned config file, not hardcoded in ViewModel |

### Open questions

1. **Should the baseline be mandatory or optional?** Optional is safer for adoption; mandatory produces better data. Recommend: optional but surfaced prominently on first launch.

2. **What unit does the baseline store?** Always kg per cable (same as BLE protocol). UI shows kg or lb per user preference.

3. **How should Cable handle a user who is much stronger than the suggested starting weight?** The "Too easy" feedback path must allow quickly jumping to a higher weight without many iterations. Consider a doubling heuristic: if "Too easy," try 2× the previous weight next round.

4. **Should assessment weights be re-tested periodically?** A "re-test prompt" after 90 days of inactivity or after significant performance improvement would keep baselines accurate. Not in scope for Phase 2 but worth designing for.

5. **How to handle Vitruvian-specific weight limits?** The machine has a maximum load per cable. Baseline protocol must cap test weights at a safe value (e.g., 40 kg/cable) regardless of user input.

6. **Should ExerciseCategory be updated to match the 10 canonical buckets?** Currently `ExerciseCategory` has 9 values that do not align with the `muscles` data. Unifying them would make filtering code simpler and reduce confusion.

7. **Per-profile `lastPerformed` and `timesPerformed` in ExerciseEntity** — these fields exist in the database but are not scoped by `profileId`. Analytics and workout suggestions based on recency will be inaccurate in multi-user scenarios until these are scoped.

---

## 7. Implementation Recommendations

In priority order, for when this is implemented:

1. **Define a canonical baseline exercise config** — a `Map<ExerciseBucket, String>` (bucket → exercise ID) stored in a constants file. Never hardcode exercise IDs directly in ViewModel.

2. **Add a `ExerciseBucket` enum** derived from the 10 canonical buckets above. Add a mapping function from `(muscles: List<String>, movement: String?)` to `ExerciseBucket`.

3. **Add a `BaselineRepository`** backed by DataStore (one profile-keyed key per bucket), storing `Float` (kg per cable). Separate from `PreferencesManager` to keep scope clean.

4. **Implement the propagation table as a data class / config** — not logic in ViewModel. A `PropagationTable.kt` file with a `fun estimateDefault(exerciseId: String, bucketBaseline: Float): Float?` function.

5. **Build the assessment UI as a dedicated screen**, not embedded in Just Lift or Single Exercise. It is a distinct workflow.

6. **Scope `lastPerformed` and `timesPerformed` by `profileId`** in the database before using them for per-profile suggestions.

7. **Do not attempt to fix the duplicate exercise names** in the JSON. The IDs are the stable identifiers. Fixing names requires a migration and coordination with the upstream exercise library.
