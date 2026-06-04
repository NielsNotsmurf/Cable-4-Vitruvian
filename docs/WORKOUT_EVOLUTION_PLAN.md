# Phoenix — Workout Evolution Plan

**Status:** Planning  
**Last updated:** 2026-06-04  
**Author:** Project lead  
**Document type:** Source of truth for the upcoming improvement project

---

## 1. Project Vision

Phoenix is a community-rescue Android app that keeps Vitruvian Trainer machines alive after the company's bankruptcy. The machine hardware is excellent; the software should match it.

The goal of this evolution is to make Phoenix the best possible native companion for real gym use — specifically:

- Multiple people can use the same tablet/phone in a gym setting, each with their own history and stats.
- A workout can start with "I want to train chest today" rather than requiring a pre-built routine.
- Defaults get smarter over time based on what you actually do.

This is not a rewrite. Phoenix already has a solid foundation (Room v26, MVVM, BLE reliability, 200+ exercises, routines, analytics). The evolution layers improvements on top of that foundation.

---

## 2. Current State Summary

| Area | Current behavior |
|---|---|
| Users | Single-user. No userId anywhere. All data is global. |
| Workout start flow | Choose mode → configure weight/reps → press start. Must know what you want upfront. |
| Routines | Pre-built, rigid. Must define exercises, sets, weight, mode in advance. |
| Exercise selection | Exercise library exists (200+). Not surfaced as a workout-start entry point. |
| Defaults | Weight and reps default to last-used globals, not per-user or per-exercise. |
| Analytics | Per-exercise history, PRs, phase stats — all global. |
| BLE | Nordic BLE library, foreground service, stable. Kable migration in progress but disabled. |
| Database | Room schema v26. No userId FK on any entity. |
| Device | Android-only, API 26+, fully offline. |

---

## 3. Desired End State

### 3.1 Multi-user on a shared device
- A local profile exists for each person (name, optional avatar, weight unit preference).
- When starting a workout, the active user is selected (or defaults to last).
- Mid-workout, any set can be attributed to a different user (hand-off between users sharing the machine).
- History, PRs, analytics are per-user.
- No cloud, no accounts, no passwords. Purely local profiles.

### 3.2 Muscle-group-first workout start
- Tap "Start workout" → choose a muscle group (Chest, Back, Legs, etc.).
- See relevant exercises, sorted by: favorites first, then recently used, then by popularity.
- Select an exercise → get sensible defaults (weight, reps, mode) based on past use.
- Add sets freely. No mandatory routine structure.

### 3.3 Smart defaults (medium-term)
- Per-user, per-exercise defaults for weight/reps/mode.
- Defaults improve from history: last used, rolling average, or PR-fraction.
- Optional: automatic weekly progression rule (e.g., +1 kg every 2 weeks if target reps hit).

---

## 4. Scope

### In scope
- Local user profiles (no auth, no server)
- Mid-workout user switch (per-set attribution)
- Muscle-group-first workout start flow
- Exercise sorting by relevance (favorites, recent, popular)
- Per-user and per-exercise default weight/reps/mode
- Per-user analytics, history, PRs
- Database migration to add userId FK

### Out of scope for this plan (deferred, see Section 5)
- Cloud sync or backend
- Health Connect / Google Fit integration
- Calorie / energy tracking
- Tablet-specific layout redesign
- Kable BLE migration
- Automatic progression rules (tracked as future)
- Strength benchmarking

---

## 5. Non-Goals

These are explicitly **not** part of this plan to prevent scope creep:

- No cloud backend, no user accounts, no authentication.
- No nutrition or calorie tracking.
- No server hosting of any kind.
- No major BLE layer changes (the Nordic integration is stable; do not touch it unless a bug forces it).
- No full rewrite of existing screens that are not directly affected.
- No cross-device sync.
- No automated test infrastructure overhaul (tests are additive, not a prerequisite).

---

## 6. Architectural Principles

**Local-first.** All data lives on-device. Profiles are a SQLite table, not accounts. Nothing requires connectivity beyond the BLE machine.

**No backend unless clearly required.** If a feature cannot be built offline, it is deferred or descoped.

**Preserve BLE reliability.** The BLE layer (Nordic + foreground service) is the core value of the app. No phase should touch it unless strictly necessary. BLE regressions are the worst kind because they require physical hardware to verify.

**Avoid big rewrites.** Extend existing patterns. New features follow the existing MVVM + Clean Architecture layering. New DB columns → Room migration, not schema redesign. New screens → new route in the existing nav graph.

**Incremental and testable.** Each phase must leave the app in a shippable state. No phase should span more than 2–3 DB migrations or more than 3 new screens. Database migrations are one-way; design them correctly the first time.

**Per-user defaults are additive.** Existing global defaults remain functional for single-user use. Per-user defaults layer on top, not replace.

---

## 7. Proposed Phases

---

### Phase 0 — Investigation & Migration Planning

**Goal:** Fully understand the current data model and plan the DB migration before writing a single line of feature code.

**Why it matters:** The userId FK must be added correctly to 6+ tables. Getting the migration wrong corrupts existing data permanently.

**Affected areas:**
- `data/local/WorkoutDatabase.kt` (migration version)
- All entity files: `WorkoutSessionEntity`, `PersonalRecordEntity`, `PhaseStatisticsEntity`, `RoutineEntity`, `WeeklyProgramEntity`
- All DAO files
- All repository implementations

**Investigation tasks:**
1. Enumerate every Room entity and confirm which ones need a userId FK.
2. Review all existing migration scripts (v1 → v26) to understand the pattern used.
3. Identify all repository query methods and determine which need a userId parameter added.
4. Check `PreferencesManager` to understand per-user preferences scope.
5. Verify Kable BLE status (which `BleRepository` impl is actually injected by Hilt).
6. Check `exercise_dump.json` vs `exercise_dump_hd.json` — which is loaded, and what the difference is.

**Implementation tasks (high level):**
- None yet. Output is a written migration plan and confirmed entity list.

**Risks:**
- Migration complexity may reveal additional tables not caught in discovery.
- If Kable is partially wired, any DI change during Phase 1 could accidentally enable it.

**Acceptance criteria:**
- Written list of all entities requiring userId FK, with confirmed column names and defaults.
- Confirmed migration version number (v27) and migration strategy (ALTER TABLE vs copy-and-recreate).
- No code changes.

**Do not touch:**
- Everything.

---

### Phase 1 — Local User Profiles

**Goal:** Add a `UserProfile` table and a userId FK to all relevant entities. Add a basic profile management UI (create, select, delete user).

**Why it matters:** This is the foundational change that unblocks per-user history, per-user PRs, and per-user analytics. Every subsequent phase depends on it.

**Affected areas:**
- New entity: `UserProfileEntity` (id, name, avatarEmoji, createdAt, isDefault)
- New DAO: `UserProfileDao`
- New repository: `UserRepository`
- DB migration: add `userId` FK to `WorkoutSessionEntity`, `PersonalRecordEntity`, `PhaseStatisticsEntity`, `RoutineEntity`, `WeeklyProgramEntity`, `ProgramDayEntity`
- New screen: `ProfileSelectionScreen` (list profiles, add new, set active)
- `MainViewModel`: add `activeUserId: StateFlow<Long>`
- `PreferencesManager`: add `activeUserId` key
- Settings screen: add "Switch User" or "Manage Profiles" entry point

**Implementation tasks (high level):**
1. Write `UserProfileEntity` and `UserProfileDao`.
2. Write DB migration v26 → v27: add `userId` FK columns with `DEFAULT 1` (seed user).
3. Seed a "Default" user (id=1) in the migration so all existing data is attributed to them.
4. Add `UserRepository` and Hilt binding.
5. Add `activeUserId` to `PreferencesManager`.
6. Add `activeUserId` to `MainViewModel`.
7. Thread `userId` into all repository query methods (additive — existing callers get default user).
8. Build `ProfileSelectionScreen`.
9. Add navigation route and entry point in Settings.

**Risks:**
- All existing history is attributed to the seed user. If the migration default is wrong, data appears as orphaned.
- DAO query changes must be done everywhere — easy to miss one.
- `PreferencesManager` uses DataStore proto or preferences — confirm which key type to use for userId.

**Acceptance criteria:**
- App starts and functions identically to pre-Phase-1 for single-user use.
- A new profile can be created and set as active.
- New workouts after profile switch appear under the new user in history.
- Old workouts remain attributed to the seed user.
- No BLE behavior change.

**Do not touch:**
- BLE layer (VitruvianBleManager, BleRepository)
- Workout state machine (WorkoutState, MainViewModel BLE flow collectors)
- Exercise library data

---

### Phase 2 — Mid-Workout User Switch

**Goal:** Allow the active user to be changed between sets during an active workout session. Each set is attributed to whoever was active when the set was saved.

**Why it matters:** In a shared gym setting, two people often alternate sets on the same machine. Without mid-workout switching, one person must manually reassign every other set after the fact.

**Affected areas:**
- `presentation/screen/ActiveWorkoutScreen` — add user switch control visible between sets
- `MainViewModel` — active user changes propagate to next set save
- `WorkoutSessionEntity` — already has `userId` from Phase 1; no schema change
- `WorkoutRepository` — `saveWorkoutSession()` uses current `activeUserId`

**Implementation tasks (high level):**
1. Add a compact user selector widget to `ActiveWorkoutScreen`, shown in the `SetSummary` or `Resting` states (between sets only — not during an active set).
2. Selecting a different user updates `activeUserId` in `MainViewModel` (in-memory only during workout, not persisted as default unless user confirms).
3. Next set save uses the updated `activeUserId`.
4. After workout complete, offer to restore original active user or keep the switched one.

**Risks:**
- UI real estate on ActiveWorkoutScreen is limited. Widget must be compact and non-intrusive.
- Switching user mid-set (while BLE is streaming) must not touch the BLE layer. The userId is only applied at save time.

**Acceptance criteria:**
- User can switch active profile between sets with ≤2 taps.
- Each saved set has the correct userId in the DB.
- Switching user does not affect BLE connection or workout metrics streaming.
- If only one profile exists, the user switch control is hidden.

**Do not touch:**
- BLE layer
- Workout state machine transitions (Idle → Active → SetSummary flow)
- Exercise library

---

### Phase 3 — Muscle-Group-First Workout Start

**Goal:** Add a new workout start flow: choose muscle group → see relevant exercises → configure set → start.

**Why it matters:** The current flow requires knowing the exercise name upfront or navigating the full library. For casual or varied training, a muscle-group entry point is far more natural.

**Affected areas:**
- New screen: `MuscleGroupPickerScreen`
- New screen: `ExercisePickerScreen` (filtered + sorted by: favorites, recently used by active user, popularity)
- New route in `NavGraph`
- `ExerciseRepository` — add query: exercises by muscleGroup, sorted by (isFavorite DESC, lastPerformed DESC, popularity DESC), filtered by userId activity
- Home screen — add "Train by muscle group" entry point alongside existing buttons

**Implementation tasks (high level):**
1. Add muscle group taxonomy (derive from existing `muscleGroups` field in `ExerciseEntity`).
2. Build `MuscleGroupPickerScreen` (grid of muscle group cards with icons).
3. Add DAO query: `getExercisesForMuscleGroup(group, userId)` sorted by relevance.
4. Build `ExercisePickerScreen`: shows filtered list, search within group, favorite toggle.
5. Selecting an exercise navigates to existing single-exercise setup screen with pre-filled exercise.
6. Add navigation route and wire home screen entry point.

**Risks:**
- `muscleGroups` field in `ExerciseEntity` is a comma-separated string. Filtering requires either a LIKE query or a normalized tag table. Investigate before implementing.
- Exercise relevance sort requires joining workout history per user — may be a complex query. Consider a materialized `lastPerformedByUser` field on exercises per user, or a simpler approach using the existing `lastPerformed` global field as a fallback.

**Acceptance criteria:**
- User can start a workout by selecting a muscle group in ≤3 taps.
- Exercise list is filtered correctly to the chosen muscle group.
- List order: favorites first, then by recency of use by active user.
- Tapping an exercise leads to the existing single-exercise setup flow with the exercise pre-selected.
- Existing routine-based and Just Lift flows are unaffected.

**Do not touch:**
- BLE layer
- Existing routine builder and program builder screens
- WorkoutState machine

---

### Phase 4 — Per-User, Per-Exercise Defaults

**Goal:** When a user picks an exercise, the default weight, reps, and mode are drawn from their own history rather than global last-used values.

**Why it matters:** Currently the app defaults to whatever was last set globally. In multi-user scenarios this is wrong — user B's defaults overwrite user A's. Even in single-user use, per-exercise memory of defaults is significantly better UX.

**Affected areas:**
- New entity or expanded `PreferencesManager`: per-user, per-exercise defaults (userId + exerciseId + cableConfig → weight, reps, mode)
- `MainViewModel`: default resolution logic (per-user last-used → global last-used → hardcoded default)
- `WorkoutRepository`: provide last session for (userId, exerciseId) as default source
- Setup screens (SingleExercise, JustLift, ExercisePicker) — pre-fill from resolved defaults

**Implementation tasks (high level):**
1. Add a `UserExerciseDefaultsEntity` table (userId, exerciseId, cableConfig, lastWeightKg, lastReps, lastMode, updatedAt) — or derive from workout history query.
2. Decide: store explicit defaults table (fast reads, more writes) vs. query history on demand (no extra table, slower). Investigate which is more appropriate given current query patterns.
3. On workout set complete, update per-user defaults for that exercise.
4. On exercise selection, resolve defaults: per-user last-used → global → hardcoded.
5. Pre-fill weight/reps/mode fields in setup screens.

**Risks:**
- If derived from history query on demand, the query must be fast (indexed). If stored as a separate table, must keep it in sync.
- The existing `PreferencesManager` already stores per-exercise defaults keyed by exerciseId+cableConfig. This logic must be extended to key on userId+exerciseId+cableConfig without breaking existing behavior for single-user case.

**Acceptance criteria:**
- Two users with different training history get different default weights for the same exercise.
- After a set, the next time that user picks the same exercise, the last-used weight/reps/mode are pre-filled.
- Single-user behavior is identical to pre-Phase-4 (no regression).

**Do not touch:**
- BLE layer
- Analytics charts and PR detection logic

---

### Phase 5 — Per-User Analytics and PRs

**Goal:** All analytics screens (history, PRs, phase stats, consistency gauge, volume chart) are scoped to the active user.

**Why it matters:** Once multiple users exist, the global analytics become meaningless. User A's heavy sets inflate User B's averages.

**Affected areas:**
- All DAO queries in `WorkoutSessionDao`, `PersonalRecordDao`, `PhaseStatisticsDao`
- All analytics ViewModels
- Analytics screens — add user switcher or filter

**Implementation tasks (high level):**
1. Add userId filter to all analytics DAO queries.
2. Update analytics ViewModels to pass `activeUserId` from `MainViewModel`.
3. Add a user selector to the Analytics tab header (visible only when >1 user exists).
4. PR detection logic (`PersonalRecordEntity` insert) must use userId.

**Risks:**
- Many DAO queries to touch — easy to miss one and leave a global query alongside scoped ones.
- PR detection runs in a use case triggered at set save. Must receive userId at that point.

**Acceptance criteria:**
- All analytics screens show only data for the active user.
- Switching users in the Analytics tab updates all charts.
- PRs are detected and attributed per-user.

**Do not touch:**
- BLE layer
- Chart rendering libraries (Vico, MPAndroidChart)
- Workout state machine

---

## 8. Suggested Order of Execution

```
Phase 0  →  Phase 1  →  Phase 2  →  Phase 3  →  Phase 4  →  Phase 5
(Investigate)  (Users)  (Switch)  (Muscle UX)  (Defaults)  (Analytics)
```

Phases 0 and 1 are blockers. Phases 2, 3, and 4 are independent once Phase 1 is done and can be parallelized if needed. Phase 5 depends on Phase 1 (userId FK) but not on Phases 2–4.

---

## 9. Open Questions

1. **UserProfileEntity avatar**: emoji string or a resource ID? Emoji is simpler and has no asset management overhead.
2. **Default user attribution**: Should the seed user created during migration be named "Default" or use the Android device owner name? Device owner name requires a permission and may be null.
3. **MuscleGroup taxonomy**: The existing `muscleGroups` field is comma-separated free text. Should Phase 3 normalize it into a tag table (better queries, more work) or use LIKE queries (easier, but fragile)? Needs investigation in Phase 0.
4. **Per-exercise defaults storage**: Separate table vs. derived from history on demand. Investigate query performance before deciding.
5. **WorkoutSession granularity**: Currently each set is one `WorkoutSessionEntity`. Should a multi-set workout session group them under a shared `sessionGroupId`? This is not required for Phase 1–3 but matters for Phase 5 analytics. Decide before Phase 5.
6. **Kable BLE migration**: Is the Kable module wired or not? Must be confirmed in Phase 0 to avoid accidental enablement during DI changes in Phase 1.
7. **DataStore userId key**: DataStore Preferences key vs. a typed key — which pattern is already used in `PreferencesManager`? Must match existing convention.

---

## 10. Risks and Rollback Considerations

### Database migrations (highest risk)
Room migrations are one-directional. A wrong migration corrupts existing data. Mitigations:
- Write migration tests against a copy of the current v26 schema before applying.
- The seed user in migration v27 must default all existing rows correctly — verify with `SELECT COUNT(*) WHERE userId IS NULL` post-migration.
- Keep fallback: Room's `fallbackToDestructiveMigrationFrom()` is a last resort and loses all user data. Do not enable it in release builds.

### BLE regression (critical)
Any DI change (new Hilt modules, new ViewModel dependencies) risks inadvertently loading the disabled Kable module or changing the BLE service lifecycle. Mitigation: do a real-device BLE connection test after every phase that touches DI or MainViewModel.

### Performance regression in analytics
Adding userId JOINs to all analytics queries on a large workout_metrics table (potentially millions of rows per user over time) can introduce latency. Mitigation: add DB indices on userId+exerciseId where needed. Investigate query plans before Phase 5.

### UI regression on existing screens
Phases 2 and 3 add new UI to existing screens (ActiveWorkoutScreen, HomeScreen). Mitigation: keep additions behind compile-time flags or feature-gated by user count (e.g., user switch button only appears when >1 profile exists).

---

## 11. Testing Strategy

**Unit tests (domain layer):**
- `RepCounterFromMachine` — no change expected, but add regression tests before touching any use case.
- Default resolution logic (Phase 4) — pure functions, fully unit-testable.

**Integration tests (data layer):**
- Room migration tests: write `MigrationTest` using `MigrationTestHelper` for every migration from v26 onward.
- DAO tests: verify per-user filtering returns correct subsets.

**Manual BLE tests:**
- After every phase: connect to machine, complete a full set, verify it saves correctly.
- After Phase 2: complete sets under two different users, verify attribution in history.
- BLE tests cannot be automated without hardware. Document the manual test checklist and run it after each phase.

**UI smoke test checklist (manual, per phase):**
- Launch app, connect, complete a set — no crashes.
- Navigate all 3 bottom tabs — no blank screens.
- Rotate device — no state loss.
- Background app during active workout — foreground service keeps session alive.

---

## 12. Documentation and Update Workflow

- This document is updated at the end of each phase to reflect actual vs. planned.
- Open questions are resolved and removed from Section 9 as they are answered.
- The `## Current State Summary` table in Section 2 is updated as features ship.
- Any significant deviation from the plan is noted inline with a `> **Deviation:**` blockquote and the reason.
- Phase status is tracked with: `Planning` | `In Progress` | `Done` | `Deferred`.

---

## 13. Git Workflow

- Each phase is a separate feature branch: `feature/phase-1-user-profiles`, etc.
- Commits within a phase are logical chunks (one migration, one DAO, one screen, etc.).
- No phase branch is merged until its acceptance criteria are met.
- After merging a phase branch, push to remote and tag: `v0.7.0-phase1`, `v0.7.0-phase2`, etc.
- After each commit, report:
  - Files changed
  - What behavior changed (or "no behavior change" for pure refactors/migrations)
  - Any risk area that should be manually tested

**Commit message convention:**
```
feat(users): add UserProfileEntity and DAO [Phase 1]
fix(migration): seed default user in v27 migration [Phase 1]
feat(workout): add mid-set user switch control [Phase 2]
```

**Never commit:**
- Secrets, API keys, or device identifiers
- Large binary assets without explicit intent
- Commented-out Kable code expansion (keep it as-is until officially migrated)

---

*End of plan. Next action: begin Phase 0 investigation tasks.*
