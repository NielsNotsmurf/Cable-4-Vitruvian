#!/usr/bin/env python3
"""Generates clean-ui/src/main/assets/one_rep_max_map.json from the exercise library.

Every loadable exercise is mapped to one of the ten 1RM-test anchors with a ratio:
estimated 1RM = anchor 1RM x ratio, in kg per cable. Rules are matched top to bottom
on the exercise name (and equipment where it matters); the first match wins.
Bodyweight, stretch, cardio and core-only moves are left out on purpose.

Ratios lean on common strength-training references (ExRx strength standards,
Symmetric Strength lift ratios, NSCA/Baechle & Earle 1RM relationships) and are
rounded down where sources disagree, so an estimate errs light. Run from the repo root:
    python3 clean-ui/scripts/generate_one_rep_max_map.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/assets/exercise_dump.json"
OUT = ROOT / "clean-ui/src/main/assets/one_rep_max_map.json"

ANCHORS = {
    "bench": ("ZZ92N8QsBdp6HCh3", "Bench Press (handles)"),
    "row": ("31ea9d9e-669c-4752-9f44-80c3fa864021", "Bent Over Row (handles)"),
    "rdl": ("49c4594c-f997-4d70-8008-b293e247e2e4", "Romanian Deadlift (handles)"),
    "squat": ("lpnNPw86Vud67vWQ", "Squat (belt)"),
    "split": ("a255fcc3-af4d-44b1-8f88-8de014b39ed3", "Bulgarian Split Squats (bench + handles)"),
    "press": ("0040d53f-85c7-4564-b14e-9b38c979b461", "Shoulder Press (handles)"),
    "lateral": ("rIjnJFQUK3mDbwBW", "Lateral Raise (handles)"),
    "curl": ("fc5ca114-3cb6-462a-ab54-7ef1233b5fc2", "Bicep Curl (handles)"),
    "triceps": ("bf1437e9-f8ca-4bfe-b766-af5fee1834a5", "Overhead Tricep Extension (handles)"),
    "hip": ("T6Vf4-OIurKVfECK", "Hip Thrust (belt + bench)"),
}

UNI = "one cable; unilateral work per side is a little lighter than bilateral per cable"

# (name regex, anchor, ratio, note). Order matters: specific before general.
RULES = [
    (r"external (shoulder )?rotat", "lateral", 0.45, "rotator cuff isolation"),
    (r"bat crusher", "triceps", 0.90, "rope skull crusher variant"),
    # --- skipped: no meaningful cable load to relate to an anchor
    (r"stretch|pigeon|pidgeon|plank|burpee|jump|jacks|knees|crawl|shuffle|skaters|inchworm|mountain|"
     r"bird dog$|superman|v-up|v-tuck|sit up|crunch|leg raise|leg lower$|leg lower w|toe touch|"
     r"heel tap|hip lifts|russian|bicycle|100s|table top$|dead bug|punch|chop|windmill|rotation|"
     r"twist|pallof|side bend|oblique|upper cut|shoot through|wipers|copenhagen|clam|march|"
     r"scoop|step overs|push up|dips?$|tricep dip|raf's|medial delt|commandos", None, 0, ""),
    # --- combos: the weaker lift limits the load
    (r"lunge w/ bicep curl|squat to bicep curl|rdl to bicep curl", "curl", 0.90, "combo; the curl limits the load"),
    (r"concentration curl deep squat", "curl", 0.85, "concentration curl in a deep squat"),
    (r"lunge w/ shoulder press|squat & press|shoulder press - lunge hold", "press", 0.80, "combo; the press limits the load, balance costs a little"),
    (r"curl and press", "curl", 0.90, "combo; the curl limits the load"),
    (r"upright row to shoulder press", "row", 0.55, "combo; the upright row limits the load"),
    (r"deadlift to upright row|squat to upright row", "row", 0.55, "combo; the upright row limits the load"),
    (r"rdl to s?a? ?row|deadlift to row", "row", 0.85, "combo; the row limits the load"),
    (r"muscle clean & press", "press", 0.80, "clean and press; the press limits the load"),
    (r"^sa thruster", "press", 0.80, "thruster: leg drive helps, the press still limits it; " + UNI),
    (r"thruster", "press", 0.95, "thruster: leg drive helps, the press still limits it"),
    (r"squat to shrug", "squat", 0.70, "squat into a shrug; grip and traps limit it"),
    (r"squat to calf raise", "squat", 0.60, "squat into a calf raise; calves limit it"),
    (r"squat to wood chop", None, 0, ""),
    (r"sumo sa high pull", "row", 0.50, "explosive single-arm high pull"),
    # --- chest (bench press = 1.0)
    (r"incline bench press", "bench", 0.90, "incline press is ~85-90% of flat bench"),
    (r"decline close grip bench", "bench", 0.90, "close grip ~90% of bench; decline about equal to flat"),
    (r"decline bench press", "bench", 0.95, "decline is about equal to flat; slightly rounded down"),
    (r"close grip bench", "bench", 0.90, "close grip bench is ~90% of regular bench"),
    (r"neutral grip floor press w/ glute bridge", "bench", 0.85, "floor press ~90% of bench; neutral grip, bridge adds instability"),
    (r"floor press w/ glute bridge", "bench", 0.85, "floor press ~90% of bench; the bridge adds instability"),
    (r"^sa (bench|floor) press", "bench", 0.85, "single-arm press; " + UNI),
    (r"floor press", "bench", 0.90, "shorter range than bench; ~90% of bench"),
    (r"neutral grip bench", "bench", 0.95, "neutral grip is about equal to flat bench"),
    (r"bench press", "bench", 1.00, "same lift, different attachment"),
    (r"incline fly", "bench", 0.40, "fly ~45-50% of bench; incline is a bit weaker"),
    (r"lying pec fly|lying flys", "bench", 0.45, "dumbbell-style fly ~45-50% of bench"),
    (r"cable fly", "bench", 0.50, "cable fly ~50% of bench"),
    (r"pullover", "row", 0.50, "pullover ~50% of a row"),
    (r"crunch & press|v-sit press|leg lower w press|isometric press|table top sa press", None, 0, ""),
    # --- back (bent over row = 1.0)
    (r"renegade row|plank (& )?(alternating )?row|bird dog row", "row", 0.45, "row from a plank or bird dog; balance limits the load"),
    (r"rear delt fly", "lateral", 0.90, "rear delt fly is about equal to a lateral raise"),
    (r"rear delt row", "row", 0.50, "elbows-out rear delt row ~50% of a row"),
    (r"face pulls?", "row", 0.35, "face pull ~35% of a row"),
    (r"crossover upright row|upright row", "row", 0.60, "upright row ~60% of a row"),
    (r"shrug", "row", 1.20, "shrugs move more than a row (short range, traps)"),
    (r"wide grip", "row", 0.90, "wide grip row is ~90% of a regular row"),
    (r"(^sa |^sl |single arm|lawnmower|kneeling|low|neutral grip sa|seated sa).*row|row sa", "row", 0.90, "one-arm row; braced, so nearly equal per cable; " + UNI),
    (r"row", "row", 1.00, "same pattern (rows of any attachment)"),
    # --- hinge (RDL = 1.0)
    (r"conventional deadlift|sumo deadlift|trap bar deadlift", "rdl", 1.15, "RDL is ~80-85% of a full deadlift"),
    (r"rack pull", "rdl", 1.20, "rack pull is ~105% of a deadlift (shorter range)"),
    (r"snatch grip deadlift", "rdl", 1.00, "snatch grip is ~85% of a regular deadlift"),
    (r"snatch grip rdl", "rdl", 0.85, "wide grip shortens leverage; ~85% of RDL"),
    (r"suitcase deadlift", "rdl", 0.85, "handles at the sides; grip limits it"),
    (r"stiff leg deadlift", "rdl", 0.95, "stiff leg is about equal to RDL"),
    (r"suitcase sl rdl|sl rdl w/ knee raise", "rdl", 0.40, "single-leg RDL with balance work"),
    (r"sl romanian deadlift", "rdl", 0.45, "single-leg RDL ~45-50% of bilateral per cable"),
    (r"split stance (deficit )?rdl", "rdl", 0.70, "split stance shifts most load to one leg"),
    (r"crossover rdl", "rdl", 0.90, "crossed cables change the line of pull"),
    (r"romanian deadlift", "rdl", 1.00, "same lift, different attachment"),
    (r"zercher good morning", "rdl", 0.45, "Zercher hold limits the load"),
    (r"good morning", "rdl", 0.60, "good morning ~60% of RDL"),
    (r"pull through", "rdl", 0.55, "cable pull-through ~55% of RDL"),
    (r"^sl hamstring curl|standing hamstring curl", "rdl", 0.15, "single-leg curl; isolation"),
    (r"hamstring curl", "rdl", 0.30, "lying leg curl ~30% of RDL"),
    # --- single leg (Bulgarian split squat = 1.0)
    (r"front rack bulgarian", "split", 0.85, "front rack hold limits the load"),
    (r"goblet (bulgarian|reverse lunge|lunge)", "split", 0.70, "goblet hold limits the load"),
    (r"bulgarian split squat pulse", "split", 0.80, "partial reps"),
    (r"bulgarian split squat", "split", 1.00, "same lift, different attachment"),
    (r"curtsy", "split", 0.75, "curtsy lunge, crossed stance"),
    (r"lateral lunge|side lunge", "split", 0.70, "lateral lunge"),
    (r"step ?downs?|step up", "split", 0.70, "step-ups/downs ~70% of a split squat"),
    (r"zercher lunge", "split", 0.80, "Zercher hold limits the load"),
    (r"front rack .*lunge|front rack lunge", "split", 0.85, "front rack hold limits the load"),
    (r"suitcase .*lunge", "split", 0.85, "handles at the sides; grip limits it"),
    (r"lunge pulses?", "split", 0.85, "partial reps"),
    (r"lunge", "split", 0.95, "lunges load about like a split squat"),
    # --- squat (belt squat = 1.0)
    (r"overhead squat", "squat", 0.45, "overhead hold limits the load"),
    (r"pistol", "squat", 0.30, "single-leg squat"),
    (r"cossack", "squat", 0.40, "deep lateral single-leg squat"),
    (r"zercher squat", "squat", 0.80, "Zercher squat ~80% of back squat"),
    (r"front rack sumo squat|front rack squat|arms crossed front squat|front squat", "squat", 0.80, "front squat ~80-85% of back squat"),
    (r"goblet squat", "squat", 0.55, "goblet hold limits the load"),
    (r"crossover sumo squat", "squat", 0.70, "crossed cables, handles at the hips"),
    (r"suitcase squat|suitcase sit to stand", "squat", 0.65, "handles at the sides; grip limits it"),
    (r"front rack sit to stand", "squat", 0.70, "front rack hold, box squat"),
    (r"sit to stand", "squat", 0.85, "box squat to a bench, slightly shorter range"),
    (r"squat pulses?|sumo squat pulse", "squat", 0.70, "partial reps under constant tension"),
    (r"kneeling squat", "squat", 0.70, "hip-dominant partial range"),
    (r"seated calf raise", "squat", 0.50, "seated calf raise ~50% of the standing one"),
    (r"^sl calf raise", "squat", 0.40, "single-leg calf raise"),
    (r"calf raise", "squat", 0.80, "standing calf raises take loads close to squat"),
    (r"seated leg extension|lying leg extension", "squat", 0.30, "knee extension isolation"),
    (r"squat", "squat", 1.00, "same pattern (back, high bar, low bar, sumo, belt, handles)"),
    # --- shoulders (shoulder press = 1.0)
    (r"arnold", "press", 0.85, "Arnold press ~85% of shoulder press"),
    (r"z-press", "press", 0.85, "seated on the floor, no leg or back support"),
    (r"kneeling upward press|balance press", "press", 0.55, "single arm with balance demands"),
    (r"(^sa |^seated sa |half kneeling sa |neutral sa ).*press", "press", 0.90, "single-arm press; " + UNI),
    (r"shoulder press", "press", 1.00, "same lift (seated, standing, grip variations)"),
    (r"double arm front raise", "lateral", 1.10, "front raise ~110-120% of a lateral raise"),
    (r"front raise", "lateral", 1.15, "front raise ~110-120% of a lateral raise"),
    (r"bent over sa lateral", "lateral", 0.85, "bent over lateral raise hits rear delts"),
    (r"crossover lateral", "lateral", 0.90, "crossed cables, constant tension"),
    (r"lateral raise", "lateral", 1.00, "same lift, different attachment"),
    (r"external (shoulder )?rotat", "lateral", 0.45, "rotator cuff isolation"),
    # --- arms
    (r"hammer curl", "curl", 1.05, "hammer curl ~100-110% of a curl"),
    (r"pronated seated wrist|pronated wrist", "curl", 0.35, "reverse wrist curl"),
    (r"supinated wrist|wrist curl", "curl", 0.50, "wrist curl"),
    (r"pronated|reverse", "curl", 0.75, "reverse curl ~75% of a curl"),
    (r"preacher|concentration", "curl", 0.85, "strict curl, no body English"),
    (r"bayesian", "curl", 0.75, "stretched-position curl"),
    (r"wide grip .*curl|outward", "curl", 0.90, "wide grip curl ~90%"),
    (r"curl", "curl", 1.00, "same lift (bar, handles, single arm, seated)"),
    (r"skullcrusher w/ leg lower", "triceps", 0.70, "combined with a core move"),
    (r"tricep kick ?back|bent over tricep extension", "triceps", 0.50, "kickback ~50% of an overhead extension"),
    (r"skull ?crusher", "triceps", 1.00, "lying extension loads about like overhead"),
    (r"(sa|half kneeling sa) .*tricep|overhead tricep extension$", "triceps", 0.95, None),
    (r"tricep", "triceps", 1.00, "same lift, different attachment"),
    # --- glutes (hip thrust = 1.0)
    (r"^sl (hip thrust|glute bridge)", "hip", 0.45, "single-leg ~45% of bilateral"),
    (r"glute bridge|table top hip thrust|hip thrust pulses", "hip", 0.80, "bridge from the floor ~80% of a hip thrust"),
    (r"hip thrust", "hip", 1.00, "same lift, different attachment"),
    (r"kickback|donkey kick|45 degree", "hip", 0.15, "ankle-strap glute isolation"),
    (r"abduction|adduction|fire hydrant", "hip", 0.10, "ankle-strap hip isolation"),
]


def rule_for(name, equipment, sidedness):
    n = name.strip().lower()
    for pattern, anchor, ratio, note in RULES:
        if re.search(pattern, n):
            if anchor is None:
                return None
            if note is None:  # overhead extension: one handle is single-arm
                if sidedness == "unilateral" or n.startswith(("sa ", "half kneeling sa", "seated sa")):
                    return anchor, 0.90, "single-arm extension; " + UNI
                return anchor, 1.00, "same lift, different attachment"
            return anchor, ratio, note
    return None


def main():
    exercises = json.loads(SRC.read_text())
    anchor_ids = {aid for aid, _ in ANCHORS.values()}
    mapping, skipped = {}, []
    for e in sorted(exercises, key=lambda e: e["name"].lower()):
        if e.get("archived"):
            continue
        equipment = e.get("equipment") or []
        if not equipment:
            skipped.append(e["name"])
            continue
        if e["id"] in anchor_ids:
            continue  # an anchor is its own reference
        hit = rule_for(e["name"], equipment, e.get("sidedness"))
        if hit is None:
            skipped.append(e["name"].strip())
            continue
        key, ratio, note = hit
        mapping[e["id"]] = {
            "name": f"{e['name'].strip()} ({'+'.join(equipment).lower()})",
            "anchor": ANCHORS[key][0],
            "ratio": ratio,
            "note": note,
        }
    out = {
        "about": "Estimated 1RM = anchor 1RM x ratio, kg per cable. Used only when a lifter has no tested "
                 "or history 1RM for an exercise; a tested 1RM always wins. Generated by "
                 "clean-ui/scripts/generate_one_rep_max_map.py from the exercise library.",
        "sources": "ExRx strength standards, Symmetric Strength lift ratios, NSCA Essentials of Strength "
                   "Training (1RM relationships); rounded down where sources disagree.",
        "anchors": {aid: label for aid, label in ANCHORS.values()},
        "exercises": mapping,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n")
    print(f"mapped {len(mapping)}, skipped {len(skipped)}")
    print("skipped:", ", ".join(sorted(set(skipped))))


if __name__ == "__main__":
    main()
