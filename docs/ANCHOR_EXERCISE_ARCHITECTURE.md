# Anchor Exercise Architecture

_Phoenix / Vitruvian — Design document. Research only. No code._
_Status: Draft — not yet approved or implemented._

---

## Executive Summary

**Yes.** The 572-exercise library can be meaningfully reduced to a small set of anchor exercises.

After excluding 157 non-applicable exercises (mobility, cardio, bodyweight-only, rehab drills), **415 exercises remain as baseline-eligible**. Of these, approximately **380 (92%) fall into 12 well-defined movement clusters**, each representable by a single anchor exercise.

The smallest practical anchor set is **10 exercises**. This covers ~88% of baseline-eligible exercises with high-confidence propagation coefficients. A set of **12 anchors** raises coverage to ~92% and adds unilateral leg work and hip thrust as explicit tests rather than inferences.

---

## 1. Exclusion Pass — What the Anchor Set Does Not Cover

Before clustering, 157 exercises must be removed from baseline consideration entirely.

### Exclusion criteria

| Category | Count | Examples | Why excluded |
|---|---|---|---|
| Bodyweight lower-body (no load) | 33 | Alternating Reverse Lunge, High Knees, Butt Kicks, Deficit Lunge w/ Knee Drive | No Vitruvian resistance; weight cannot be tracked or suggested |
| Planks and isometric holds | 19 | Copenhagen Plank, Alternating Plank Dips, Side Plank | Load is bodyweight-only; cannot quantify per-cable kg |
| Crunches and trunk flexion | 13 | Bicycle Crunch, Crunch Isometric Hold, Crunch & Press, V-Sit | These are mobility or low-load core drills with no progressive loading model |
| Push-up variants | 10 | Push Up, Diamond Push Up, Close Grip Push Up, Decline Push Up | Bodyweight; no cable resistance |
| Stretches and mobility | 8 | Hip Flexor Stretch, Pigeon, Down Dog Calf Stretch, Assisted Inchworm | Explicit rehab / flexibility work |
| Rotational / anti-rotation | 6 | Pallof Press, Bar Rotation, B Stance Bar Rotation | These are stability patterns, not strength anchors |
| Punching patterns | 6 | Alternating Crossover Punch, Crossover Single Arm Punch | Speed-dominant; no meaningful load baseline |
| Leg lowering movements | 5 | Leg Lower w Press, Leg Lower w/ Isometric Press | Floor core work |
| Wrist and forearm isolation | 5 | Wrist Curl, Pronated Seated Wrist Curls, SA Wrist Curl | Hyper-isolated; no compound relationship |
| Burpees | 4 | Burpee, Burpee Jump Over Machine, Burpee with Push-up | Cardio-dominant; no strength baseline |
| Bird Dog variants | 3 | Bird Dog, Bird Dog Row | Motor control / rehab pattern |
| Superman / floor posterior | 3 | Superman, various | Bodyweight isolation |
| Bear / crawl patterns | 3 | Bear Crawl, Bear Shoulder Tap | Locomotion / warmup |
| Other (Dead Bug, Clam, Fire Hydrant, etc.) | 33 | — | Activation drills, warmups, or bodyweight rehab |
| Placeholder / internal | 1 | "Just Lift exercise" | Not a real exercise |
| No equipment + no muscles | 1 | — | No data |

**Total excluded: 157**

### What survives

415 baseline-eligible exercises across 12 clusters. These are cable-loaded movements where a per-cable weight in kg is meaningful, trackable, and progressable.

---

## 2. Movement Clusters

### Cluster map

| # | Cluster | Anchor | n (eligible) | Movement pattern | Primary muscles |
|---|---|---|---|---|---|
| 1 | Chest Press | Bench Press | 52 | Horizontal push | chest, triceps |
| 2 | Horizontal Pull | Bent Over Row | 44 | Horizontal pull | upper_back, lats, biceps |
| 3 | Hip Hinge | Romanian Deadlift | 64 | Hip extension under load | hamstrings, glutes, lats, lower_back |
| 4 | Bilateral Squat | Squat | 43 | Bilateral knee/hip flexion | quads, glutes |
| 5 | Unilateral Leg | Bulgarian Split Squat | 67 | Single-leg knee/hip flexion | quads, glutes, hamstrings |
| 6 | Shoulder Press | Shoulder Press | 28 | Vertical push | shoulders, triceps |
| 7 | Shoulder Isolation | Lateral Raise | 20 | Shoulder abduction/raise | shoulders |
| 8 | Bicep Curl | Bicep Curl | 37 | Elbow flexion | biceps |
| 9 | Tricep Extension | Overhead Tricep Extension | 34 | Elbow extension | triceps |
| 10 | Glute / Hip Thrust | Hip Thrust | 18 | Hip extension without spinal load | glutes, hamstrings |
| 11 | Chest Fly | Lying Pec Fly | 7 | Horizontal shoulder adduction | chest |
| 12 | Rear Delt / Upper Trap | Face Pull | 17 | Horizontal shoulder external rotation | upper_back, shoulders, traps |
| — | Calves | Calf Raise | 11 | Plantarflexion | calves |
| — | Hamstring Curl | Lying Hamstring Curl | 6 | Knee flexion | hamstrings |

**Total covered by anchors: ~380 of 415 (~92%)**

---

## 3. Cluster Details and Anchor Justification

### 3.1 Chest Press — anchor: Bench Press

**Why Bench Press:** Highest popularity of any loaded exercise in the library (0.050 for HANDLES variant). Bilateral. Uses HANDLES directly (no bar setup required). Clean `chest_press` movement tag. Representative of all horizontal push patterns.

**Exercises covered (52 total, sample):**
- Bench Press (8 variants — equipment differences only)
- Incline Bench Press (4 variants)
- Decline Bench Press
- Floor Press
- Close Grip Bench Press
- Neutral Grip Bench Press
- SA Bench Press (single cable, lower coefficient)
- Chest Press — Gym Ball
- Lat Pullover (partial — also loads lats)

**Propagation range:** 0.50 (SA Floor Press) to 1.05 (Decline Bench Press)

**Cluster risk — LOW.** All exercises are horizontal push variants. The chest_press movement tag is well-applied to 20 exercises; the remaining 32 are identified by the `chest` muscle tag.

---

### 3.2 Horizontal Pull — anchor: Bent Over Row

**Why Bent Over Row:** Second highest popularity in the row cluster. Bilateral. HANDLES variant requires no additional setup. Most representative compound back movement on the Vitruvian.

**Note on alternatives:** Seated Row is slightly more popular in aggregate (8 variants) but requires a bench setup. Bent Over Row is the better default because it is setup-free.

**Exercises covered (44 tagged + ~20 untagged back exercises, ~64 total):**
- Bent Over Row (all grip variants: standard, reverse grip, wide grip, neutral)
- Crossover Row / Bent Over Crossover Row
- Seated Row (all equipment variants)
- Kneeling Row
- Chest-Supported Row variants
- Upright Row (different plane; coefficient ~0.55)

**Propagation range:** 0.45 (Upright Row — different plane) to 1.05 (Seated Row — braced, better leverage)

**Cluster risk — MEDIUM.** Upright Row has meaningfully different muscle recruitment (traps dominant, shoulder elevation) versus horizontal pull. It belongs in this cluster for default purposes but needs a lower coefficient. Face Pull / Rear Delt Row sits in its own cluster (3.12).

---

### 3.3 Hip Hinge — anchor: Romanian Deadlift (HANDLES)

**Why RDL (HANDLES):** Third most popular loaded exercise. No barbell required. HANDLES variant most accessible. Covers the broadest range of posterior chain exercises.

**Exercises covered (64 total):**
- Romanian Deadlift (all variants)
- Conventional Deadlift (higher coefficient ~1.15)
- Sumo Deadlift
- Stiff Leg Deadlift
- Suitcase Deadlift
- Snatch Grip Deadlift
- Good Morning
- Pull Through
- Crossover RDL
- Glute Bridge (lower coefficient — hip extension without hinge; see also Cluster 10)
- Hip Thrust (lower coefficient — overlaps with Cluster 10)

**Note on Glute Bridge / Hip Thrust:** These appear in the Hip Hinge cluster by muscle overlap (glutes + hamstrings) but mechanically they are hip extension against a fixed spine — no hinge. They are better served by Cluster 10 (Hip Thrust anchor) with its own coefficient.

**Propagation range:** 0.35 (Glute Bridge — short ROM, less posterior chain) to 1.20 (Conventional Deadlift — full range, more upper back)

**Cluster risk — MEDIUM.** The cluster spans exercises that are quite different mechanically (RDL vs Glute Bridge). Splitting into Hip Hinge + Hip Thrust as separate anchors (Clusters 3 and 10) resolves this. With 12 anchors, this is already handled.

---

### 3.4 Bilateral Squat — anchor: Squat (HANDLES / BELT)

**Why Squat:** The BELT variant is setup-free. HANDLES variant also available and popular. Highest-popularity bilateral lower-body exercise with a `squat` movement tag.

**Preferred anchor ID:** `lpnNPw86Vud67vWQ` (Squat, BELT equipment) or `6PVJLGhvozpELKtc` (Suitcase Squat, HANDLES — more accessible for new users).

**Exercises covered (43 bilateral):**
- Squat (all bar, belt, handle variants)
- Goblet Squat
- Front Squat
- Front Rack Squat
- Sumo Squat / Sumo Belt Squat / Sumo Back Squat
- Sit-to-Stand variants
- Low Bar Squat / High Bar Squat
- Squat Pulses / Squat to Calf Raise

**Propagation range:** 0.75 (Goblet Squat — stability/mobility constraint) to 1.10 (Low Bar Squat — more posterior chain involvement)

**Cluster risk — LOW for bilateral.** The bilateral squat pattern is cohesive. Equipment variants (BAR vs BELT vs HANDLES) all produce equivalent loads.

---

### 3.5 Unilateral Leg — anchor: Bulgarian Split Squat

**Why Bulgarian Split Squat:** Highest popularity among unilateral leg exercises. Requires BENCH + HANDLES, which is the most common setup for cable leg work. Representative of the lunge/split squat family.

**Why separate from Bilateral Squat:** Unilateral work involves significantly different coordination demand, and users who are strong in bilateral squats are not proportionally strong in single-leg work. A split squat anchor produces more accurate defaults for lunges, step-ups, and single-leg patterns than a bilateral coefficient.

**Exercises covered (67 unilateral + alternating):**
- Bulgarian Split Squat (all variants, ~8 entries)
- Alternating Lunges / Alternating Curtsy Lunge
- Reverse Lunge
- Front Rack Lunge / Front Rack Alternating Lunge
- Deficit Lunge
- Side Lunge
- Cossack Squat
- Step Up / Single Leg Step Down
- Alternating Side Lunge

**Propagation range:** 0.70 (Cossack Squat — extreme ROM) to 1.10 (Reverse Lunge — slightly more accessible)

**Cluster risk — MEDIUM.** Curtsy Lunges and side lunges have different primary muscle emphasis (abductors vs quads). They can still use this anchor but with reduced confidence. The 20 alternating variants are included here; their effective per-cable load is similar.

---

### 3.6 Shoulder Press — anchor: Shoulder Press (HANDLES)

**Why Shoulder Press:** Fourth most popular loaded exercise overall. Clean `shoulder_press` movement tag. HANDLES variant requires no setup.

**Exercises covered (28 tagged):**
- Shoulder Press (standing and seated)
- Arnold Press
- Seated Shoulder Press
- Crossover Upright Row (partially — traps also involved)
- Overhead Press variants
- SA Shoulder Press (lower coefficient, single cable)

**Propagation range:** 0.75 (Arnold Press — rotation adds difficulty) to 1.05 (Seated Shoulder Press — braced position)

**Cluster risk — LOW.** The shoulder press cluster is tight. The primary risk is Upright Row being included here at too high a coefficient; it is better mapped to the Horizontal Pull anchor at 0.55.

---

### 3.7 Shoulder Isolation — anchor: Lateral Raise (HANDLES)

**Why a separate anchor from Shoulder Press:** Lateral raises load the medial deltoid in an isolated, non-pressing movement. A person who can shoulder press 20 kg/cable may only lateral raise 6 kg/cable. The ratio is not consistent enough to derive from Shoulder Press alone.

**Why Lateral Raise:** Most popular shoulder isolation exercise. Bilateral. HANDLES standard.

**Exercises covered (20):**
- Lateral Raise (all variants: crossover, straps, handles, seated)
- Front Raise
- Double Arm Front Raise
- Bent Over SA Lateral Raise
- Bent Over Shrug (partial — also traps)
- Rear Delt raise variants (lower coefficient, see also Cluster 12)

**Propagation range:** 0.55 (Front Raise — slightly different plane) to 1.10 (Crossover Lateral Raise — better tension curve)

**Cluster risk — LOW.** This cluster is tightly defined.

---

### 3.8 Bicep Curl — anchor: Bicep Curl (HANDLES)

**Why Bicep Curl (HANDLES):** Most popular bicep exercise. Bilateral. No setup. Clear `bicep_curl` movement tag on all 27 tagged variants.

**Exercises covered (37 total including untagged):**
- Bicep Curl (all grip variants: standard, pronated, hammer)
- Hammer Curl
- Concentration Curl
- SA Bicep Curl
- Preacher Curl
- Bayesian Curl
- Wide Grip Bicep Curl
- Seated Bicep Curl

**Propagation range:** 0.65 (Bayesian Curl — long-head emphasis, mechanically disadvantaged) to 1.05 (Seated Bicep Curl — stable position)

**Cluster risk — LOW.** Curl variants are closely related. Hammer Curl uses similar loads (0.95).

---

### 3.9 Tricep Extension — anchor: Overhead Tricep Extension (HANDLES)

**Why Overhead Tricep Extension over Skull Crusher:** Both are popular. Overhead extension puts the long head of the tricep in a fully lengthened position — it is the hardest tricep movement and produces conservative (safe) defaults for all other extensions. Starting a user at a Skull Crusher weight that was derived from Overhead Extension will always be on the safe side.

**Exercises covered (34 total including tagged + untagged):**
- Overhead Tricep Extension (rope, handles, bar)
- Skull Crusher (bench variants)
- Kneeling Overhead Tricep Extension
- Tricep Kickback (much lower coefficient — completely different mechanics)
- Tricep Dip with belt (bodyweight-plus)
- Bent Over Tricep Extension
- Seated Overhead Tricep Extension

**Propagation range:** 0.35 (Tricep Kickback — prone, isolation, very low load) to 1.10 (Skull Crusher — better leverage than overhead)

**Cluster risk — LOW.** The major risk is Tricep Kickback, which loads the tricep at a fraction of overhead positions. Its coefficient (0.35) must be correct — defaulting to 1.0 would result in an unusable weight.

---

### 3.10 Hip Thrust — anchor: Hip Thrust (HANDLES / BELT)

**Why a separate anchor from Hip Hinge (RDL):** Hip Thrust isolates glute extension with the spine braced against a bench, removing the posterior chain tension that makes RDL heavy. A user's hip thrust weight is significantly higher (relative to body size) than their RDL, and the two do not correlate linearly. Treating them as one cluster produces poor defaults for glute bridge / hip thrust exercises specifically.

**Exercises covered (18):**
- Hip Thrust (all equipment variants)
- SL Hip Thrust / SL Glute Bridge
- Glute Bridge
- Feet Raised Glute Bridge
- Hip Thrust Pulses
- Pull Through (partial — hip extension but standing)

**Propagation range:** 0.55 (SL Hip Thrust per cable — single leg) to 1.10 (Feet Raised — elevated position, more ROM)

**Cluster risk — LOW for glute work.** The main risk is overlap with the Hip Hinge cluster for exercises like Pull Through and Good Morning, which sit between hip hinge and hip thrust mechanically.

---

### 3.11 Chest Fly — anchor: Lying Pec Fly (HANDLES)

**Can this be folded into Chest Press?** For simplicity, yes. Chest Fly ← Bench Press × 0.55. If only 10 anchors are desired, this cluster is the first to fold.

**Why to keep it separate at 12 anchors:** Chest Fly trains the pec with a pre-stretched adduction pattern. The load is genuinely ~55% of a Bench Press for most users, but this ratio varies more widely than within the Chest Press cluster (range: 45–65%). A dedicated baseline test produces more accurate defaults.

**Exercises covered (7):**
- Lying Pec Fly / Lying Flys
- Cable Fly (standing)
- Incline Fly / Incline Pec Fly
- SA Upper Cut (partial — also involves shoulders/biceps)

**Cluster risk — LOW.** Cluster is small and cohesive.

---

### 3.12 Rear Delt / Upper Trap — anchor: Face Pull (ROPE)

**Why separate from Horizontal Pull:** Face Pulls, Rear Delt Flies, and Shrugs train the posterior deltoid, upper trapezius, and external rotators — not primarily the lats and mid-back that dominate rows. A user may row 25 kg/cable but only face pull 8 kg/cable. The ratio is too variable to fold into the Row cluster.

**Can this be folded into Row at a lower coefficient?** For a 10-anchor minimum set, yes. Face Pull ← Bent Over Row × 0.30 is a reasonable approximation.

**Exercises covered (17):**
- Face Pull
- Rear Delt Fly / Crossover Rear Delt Fly
- Shrug
- Bent Over Shrug
- SA Rear Delt Fly (bench supported)

**Propagation range:** 0.70 (Rear Delt Row — slightly more compound) to 1.10 (Shrug — traps dominant, can handle more load)

**Cluster risk — MEDIUM.** Shrugs and Rear Delt Flies are mechanically different despite sharing the upper trap / posterior delt. Shrug defaults should be slightly higher; Rear Delt Fly defaults slightly lower. A single anchor will produce acceptable but not precise estimates.

---

### 3.13 Calves — anchor: Calf Raise (HANDLES)

**Why calves cannot be inferred from another anchor:** Calf strength is largely independent of lower-body compound strength. Some users with average squats have very strong calves; others do not. The ratio between Squat weight and Calf Raise weight varies enormously.

**Recommendation:** Do not propagate calves from another anchor. Either:
- Include Calf Raise as a 13th anchor if a full baseline is run, or
- Set calves to a conservative fixed default (8 kg/cable) until the user has performed the exercise once

**Exercises covered (11):**
- Calf Raise (all equipment: handles, belt, bar, short bar)
- Seated Calf Raise
- SL Calf Raise
- Three Stance Calf Raise
- Squat to Calf Raise (partial — also squats)

**Cluster risk — LOW if standalone.** HIGH if folded into Squat. Do not fold.

---

### 3.14 Hamstring Curl — anchor: Lying Hamstring Curl

**Why hamstring curl cannot be inferred from RDL:** The Lying Hamstring Curl isolates knee flexion (not hip extension). It loads the hamstring at a very different joint angle and produces loads that are a small fraction of any hip hinge exercise. Deriving it from RDL at a coefficient (e.g., 0.25) is a rough approximation but may be good enough given the small cluster size.

**Can this be folded into Hip Hinge?** For a 10-anchor set, yes: Hamstring Curl ← RDL × 0.25. The cluster has only 6 exercises, making a dedicated test hard to justify.

**Exercises covered (6):**
- Lying Hamstring Curl (bilateral)
- SL Hamstring Curl
- Standing Hamstring Curl

---

## 4. Coverage Analysis

### By anchor

| Anchor | Cluster | Directly covered | Via coefficient inference |
|---|---|---|---|
| Bench Press | Chest Press | 52 | — |
| Bent Over Row | Horizontal Pull | 44 | ~20 untagged back rows |
| Romanian Deadlift | Hip Hinge | 40 | ~24 (glute bridges via lower coeff) |
| Squat | Bilateral Squat | 43 | — |
| Bulgarian Split Squat | Unilateral Leg | 67 | — |
| Shoulder Press | Shoulder Press | 28 | — |
| Lateral Raise | Shoulder Isolation | 20 | — |
| Bicep Curl | Bicep Curl | 37 | — |
| Overhead Tricep Extension | Tricep Extension | 34 | — |
| Hip Thrust | Hip Thrust / Glute | 18 | — |
| Lying Pec Fly | Chest Fly | 7 | — |
| Face Pull | Rear Delt / Trap | 17 | — |
| Calf Raise | Calves | 11 | — |
| Lying Hamstring Curl | Hamstring Curl | 6 | — |
| **Total** | | **~424** | **~44 via inference** |

(Numbers exceed 415 because some exercises fall into multiple clusters, counted in each.)

### Exercises not cleanly covered by any anchor (~35 exercises)

These remain uncategorized with reasonable confidence:

| Exercise | Issue |
|---|---|
| Pullover (all variants) | Mixed chest/lats/triceps — sits between Chest Press, Back, and Tricep clusters |
| Deadlift to Row | Compound — both Hip Hinge and Row coefficients apply simultaneously |
| Good Morning (to Row) | Compound — posterior chain + horizontal pull |
| High Plank & Alternating Row | Core-dominant variant of rowing |
| Spiderman Push Up | Compound mobility + bodyweight chest |
| V-Sit Press | Core-dominant — sit-up + press |
| Floor Press w/ Glute Bridge | Chest + glute simultaneous |
| Kneeling Abduction (STRAPS) | Hip abduction — no suitable anchor |
| Side Lying Abduction (STRAPS) | Hip abduction — no suitable anchor |
| Wrist/Forearm exercises | Already excluded; no relevant anchor |
| Seated Spinal Twist | Mobility — already excluded |

**Recommendation for these exercises:** Set a conservative fixed default (10 kg/cable) on first encounter, then override on first real performance. Do not attempt to derive from a partially related anchor — the error would likely be worse than a fixed default.

### Clusters that may be too broad

| Cluster | Risk |
|---|---|
| Unilateral Leg (67) | Curtsy lunges and lateral lunges have different primary muscles (abductors) vs forward lunges (quads). Both use this anchor but with different confidence. |
| Hip Hinge (64) | Hip Thrust is mechanically different from RDL. Resolved by splitting into two anchors (Clusters 3 and 10). |
| Horizontal Pull (44) | Upright Row and Face Pull have meaningfully different mechanics from Bent Over Row. Resolved by Face Pull anchor (Cluster 12). |

---

## 5. Smallest Practical Anchor Sets

### Option A — 10 anchors (recommended minimum)

Fold Chest Fly into Chest Press (×0.55), Rear Delt into Row (×0.30), Hamstring Curl into RDL (×0.25), Calves as fixed default.

| # | Anchor | Covers |
|---|---|---|
| 1 | Bench Press | Chest press + chest fly (via ×0.55) |
| 2 | Bent Over Row | All rows + rear delt/face pull (via ×0.30) |
| 3 | Romanian Deadlift | Hip hinge + hamstring curl (via ×0.25) |
| 4 | Squat | Bilateral squat patterns |
| 5 | Bulgarian Split Squat | Unilateral/alternating leg patterns |
| 6 | Shoulder Press | Vertical push |
| 7 | Lateral Raise | Shoulder isolation |
| 8 | Bicep Curl | All curl variants |
| 9 | Overhead Tricep Extension | All tricep extension variants |
| 10 | Hip Thrust | Hip thrust + glute bridge patterns |

Estimated coverage of eligible exercises: **~88%**

Calf Raise defaults to a fixed 10 kg/cable until first use.

### Option B — 12 anchors (recommended for accuracy)

Add Lying Pec Fly and Face Pull as explicit anchors. Adds ~15 exercises to "precisely covered" rather than "inferred."

| # | Anchor |
|---|---|
| 1–10 | All from Option A |
| 11 | Lying Pec Fly (chest isolation) |
| 12 | Face Pull (rear delt / trap) |

Estimated coverage: **~92%**

### Option C — 13 anchors (full precision)

Add Calf Raise as an explicit test.

Estimated coverage: **~94%** (remaining ~6% are compound / uncategorizable exercises)

### Option D — 8 anchors (aggressive minimum)

Fold Lateral Raise into Shoulder Press (×0.25), Unilateral Leg into Squat (×0.65 per cable), Hip Thrust into RDL (×0.60). Significant loss of accuracy for lower-body isolation exercises.

| # | Anchor |
|---|---|
| 1 | Bench Press |
| 2 | Bent Over Row |
| 3 | Romanian Deadlift |
| 4 | Squat |
| 5 | Shoulder Press |
| 6 | Bicep Curl |
| 7 | Overhead Tricep Extension |
| 8 | Bulgarian Split Squat |

Estimated coverage: **~82%** — usable but noticeably less accurate for shoulder isolation, glute work, and chest fly.

---

## 6. Baseline Readiness Assessment

Could a baseline assessment use only the 10 anchor exercises?

**Yes, with one important constraint:** the user must own or have access to the required equipment for each test.

| Anchor | Equipment required | Notes |
|---|---|---|
| Bench Press | BENCH + HANDLES | Bench required — nearly universal Vitruvian setup |
| Bent Over Row | HANDLES | No extra equipment |
| Romanian Deadlift | HANDLES | No extra equipment |
| Squat | BELT or HANDLES | BELT is slightly better but HANDLES works |
| Bulgarian Split Squat | BENCH + HANDLES | Bench required |
| Shoulder Press | HANDLES | No extra equipment |
| Lateral Raise | HANDLES | No extra equipment |
| Bicep Curl | HANDLES | No extra equipment |
| Overhead Tricep Extension | HANDLES | No extra equipment |
| Hip Thrust | BELT + BENCH or BENCH + HANDLES | Bench required |

For users without a bench: Bench Press → Floor Press, Bulgarian Split Squat → Reverse Lunge, Hip Thrust → Glute Bridge. All three substitutes use HANDLES only.

**No-bench fallback:** Run the same 10-anchor protocol using Floor Press, Reverse Lunge, and Glute Bridge as substitutes. All coefficients remain identical; only the anchor exercise changes.

**Completion time:** With a submaximal RPE protocol (1–2 working sets per anchor, rest included), 10 anchors fits in **15–20 minutes** for a conditioned user, **20–25 minutes** for a beginner. This is acceptable for a one-time setup.

---

## 7. Risks and Open Questions

### Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Coefficients will not generalize to all users | High | Treat as initial suggestion only; override after first real use |
| 37% of exercises have no `movement` tag | Medium | Clustering falls back to `muscles` field — less reliable, but sufficient for the 12 anchors identified here |
| Duplicate exercise names make anchor selection by name unsafe | High | Always reference anchor exercises by `id`, never by `name` |
| Bulgarian Split Squat requires BENCH — not always accessible | Medium | Provide no-bench fallback anchor (Reverse Lunge, HANDLES-only) in config |
| Hip Thrust coefficient from RDL will be wrong by ~40% if used without a dedicated anchor | Medium | Use Hip Thrust as explicit anchor even in 10-anchor set |
| Calves do not generalize from any compound movement | High | Never derive calves from Squat; use fixed default or explicit test |
| `unilateral_leg` tagged exercises mix lunges and split squats — different coordination demands | Low | Accept coefficient variance; both exercise types benefit from any baseline over zero |
| Pullover and compound hybrid exercises resist classification | Low | Use fixed default; these are typically accessory work |

### Open questions

1. **Should the anchor IDs be hardcoded in a config file or stored in the database?** Config file is simpler; DB entry would allow future updates via the exercise library sync. Recommendation: config file initially, with a path to DB if the library ever becomes remote-editable.

2. **How should Phoenix present the 10-anchor assessment to the user?** As a wizard ("Complete 10 short tests") or as an optional "Quick Setup" card on first launch? The answer determines the UX design more than the data model.

3. **What happens when a user skips an anchor test?** The app needs a defined behavior: (a) leave exercises in that cluster at a fixed safe default, or (b) ask again on first use of a cluster exercise. Option (a) is simpler. Option (b) is more precise but more friction.

4. **Per-profile anchor results — where do they live?** DataStore, keyed per `profileId`. One float per anchor per profile. 13 floats × N profiles. This is trivial storage.

5. **Can anchor results be transferred between profiles?** For a household scenario (Niels and Anna both new users), they should each run their own assessment. There is no meaningful way to share anchor results.

6. **When should anchor results be invalidated?** Possibly never (user can retake voluntarily), or after 90 days of inactivity. This is a product decision, not a technical one.

7. **Should exercise IDs be pinned to specific variants, or should Phoenix pick the "best" variant at runtime?** Pinning to specific IDs is safer. If the upstream library removes an exercise, the system breaks. Recommendation: pin canonical IDs in config, with a fallback name-match as a recovery path.

8. **Does the `popularity` field represent real usage across all Vitruvian users, or is it arbitrary?** If real, it is valuable signal. If arbitrary, it should not be used for anchor selection. Data provenance is unclear from the JSON alone.

---

## 8. Implementation Recommendations

Not in scope for this document. This is a research output. Implementation planning belongs in a separate spec.

However, the following structural decisions should be made before any implementation begins:

1. **Finalize the anchor set** — choose 10, 12, or 13. The decision must be made before building the baseline flow, the coefficient table, or the propagation logic.

2. **Verify anchor exercise IDs exist** in the current database on first app launch. If an ID is missing (library update removed it), the system must degrade gracefully.

3. **The coefficient table belongs in a single file**, not scattered across ViewModel or Repository. Future updates to coefficients must not require code changes — a data file is preferable.

4. **Clusters and anchors must be invisible to the user.** The user sees exercise names and muscle groups, not cluster IDs. The anchor architecture is purely an internal data model.
