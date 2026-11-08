"""Builds the Winter Arc plan (from the "Winter Arc: Push/Pull Plan" doc) as an Open Weight file."""
import json, sys, uuid

NS = uuid.uuid5(uuid.NAMESPACE_URL, "open-weight/winter-arc")
def uid(kind, key): return str(uuid.uuid5(NS, f"{kind}:{key}"))

# name: (muscle group, equipment, compound?)
EX = {
 "Leg press": ("Quads", "Machine", True),
 "Machine chest press": ("Chest", "Machine", True),
 "Seated dumbbell shoulder press": ("Shoulders", "Dumbbells", True),
 "Leg extension": ("Quads", "Machine", False),
 "Cable lateral raise": ("Shoulders", "Cable", False),
 "Cable triceps pushdown": ("Triceps", "Cable", False),
 "Standing calf raise": ("Calves", "Machine", False),
 "Lat pulldown": ("Back", "Cable", True),
 "Seated cable row": ("Back", "Cable", True),
 "Seated leg curl": ("Hamstrings", "Machine", False),
 "Dumbbell Romanian deadlift": ("Hamstrings", "Dumbbells", True),
 "Reverse pec deck": ("Rear delts", "Machine", False),
 "Dumbbell curl": ("Biceps", "Dumbbells", False),
 "Cable crunch": ("Abs", "Cable", False),
 "Hack squat": ("Quads", "Machine", True),
 "Incline dumbbell press": ("Chest", "Dumbbells", True),
 "Pec deck": ("Chest", "Machine", False),
 "Machine shoulder press": ("Shoulders", "Machine", True),
 "Overhead cable triceps extension": ("Triceps", "Cable", False),
 "Seated calf raise": ("Calves", "Machine", False),
 "Chest-supported machine row": ("Back", "Machine", True),
 "Assisted pull-up": ("Back", "Machine", True),
 "Hip thrust": ("Glutes", "Machine or barbell", True),
 "Lying leg curl": ("Hamstrings", "Machine", False),
 "Face pull": ("Rear delts", "Cable", False),
 "Hammer curl": ("Biceps", "Dumbbells", False),
 "Hanging knee raise": ("Abs", "Bodyweight", False),
 "Barbell back squat": ("Quads", "Barbell", True),
 "Barbell bench press": ("Chest", "Barbell", True),
 "Standing overhead press": ("Shoulders", "Barbell", True),
 "Bulgarian split squat": ("Quads", "Dumbbells", True),
 "Conventional deadlift": ("Hamstrings", "Barbell", True),
 "Weighted pull-up": ("Back", "Bodyweight + plates", True),
 "Barbell row": ("Back", "Barbell", True),
 "Barbell curl": ("Biceps", "Barbell", False),
 "Hanging leg raise": ("Abs", "Bodyweight", False),
 "Incline barbell press": ("Chest", "Barbell", True),
 "Weighted dips": ("Chest", "Bodyweight + plates", True),
 "Dumbbell lateral raise": ("Shoulders", "Dumbbells", False),
 "EZ-bar skull crusher": ("Triceps", "EZ bar", False),
 "Romanian deadlift": ("Hamstrings", "Barbell", True),
 "Chest-supported T-bar row": ("Back", "Machine", True),
 "Single-arm lat pulldown": ("Back", "Cable", True),
 "Rear delt cable fly": ("Rear delts", "Cable", False),
 "Incline dumbbell curl": ("Biceps", "Dumbbells", False),
 # Block 3, option 2 and other new ones
 "Front squat": ("Quads", "Barbell", True),
 "Close-grip bench press": ("Triceps", "Barbell", True),
 "Seated barbell press": ("Shoulders", "Barbell", True),
 "Walking lunge": ("Quads", "Dumbbells", True),
 "Lean-away dumbbell lateral raise": ("Shoulders", "Dumbbells", False),
 "Dumbbell overhead triceps extension": ("Triceps", "Dumbbells", False),
 "Trap-bar deadlift": ("Hamstrings", "Trap bar", True),
 "Weighted chin-up": ("Back", "Bodyweight + plates", True),
 "Pendlay row": ("Back", "Barbell", True),
 "Seal row": ("Back", "Barbell", True),
 "Nordic curl": ("Hamstrings", "Bodyweight", False),
 "Preacher curl": ("Biceps", "EZ bar", False),
 "Ab wheel": ("Abs", "Ab wheel", False),
 "Incline Smith press": ("Chest", "Smith machine", True),
 "Pendulum squat": ("Quads", "Machine", True),
 "Arnold press": ("Shoulders", "Dumbbells", True),
 "Low-to-high cable fly": ("Chest", "Cable", False),
 "Stiff-leg deadlift": ("Hamstrings", "Barbell", True),
 "Meadows row": ("Back", "Barbell (landmine)", True),
 "Wide-grip lat pulldown": ("Back", "Cable", True),
 "Kneeling single-arm pulldown": ("Back", "Cable", True),
 "45° back extension": ("Glutes", "Bench", False),
 "Bayesian cable curl": ("Biceps", "Cable", False),
 "Reverse curl": ("Biceps", "EZ bar", False),
}

def item(name, sets, lo, hi, note=""):
    assert name in EX, name
    rest = 150 if EX[name][2] else 90
    return {"exerciseId": uid("exercise", name), "notes": note,
            "sets": [{"repsMin": lo, "repsMax": hi, "restSeconds": rest} for _ in range(sets)]}

W = {}  # key -> (name, type, notes, items)
def workout(key, name, typ, notes, items): W[key] = dict(name=name, type=typ, notes=notes, items=items)

# ---- Block 1: Intro ----
workout("b1-push-a", "Push A · Intro", "Push", "Tuesday. Mostly machines, moderate reps; RIR target by week is in the block notes.", [
 item("Leg press", 3, 10, 12), item("Machine chest press", 3, 10, 12), item("Seated dumbbell shoulder press", 3, 10, 12),
 item("Leg extension", 2, 12, 15), item("Cable lateral raise", 3, 12, 15), item("Cable triceps pushdown", 2, 12, 15),
 item("Standing calf raise", 3, 12, 15, "Machine")])
workout("b1-pull-a", "Pull A · Intro", "Pull", "Wednesday. Mostly machines, moderate reps.", [
 item("Lat pulldown", 3, 10, 12), item("Seated cable row", 3, 10, 12), item("Seated leg curl", 3, 10, 12),
 item("Dumbbell Romanian deadlift", 2, 10, 12, "Light"), item("Reverse pec deck", 2, 12, 15),
 item("Dumbbell curl", 2, 10, 12), item("Cable crunch", 2, 12, 15)])
workout("b1-push-b", "Push B · Intro", "Push", "Friday. Same muscles as Push A, more volume, higher reps.", [
 item("Hack squat", 3, 10, 12, "Or Smith machine squat"), item("Incline dumbbell press", 3, 10, 12), item("Pec deck", 2, 12, 15),
 item("Machine shoulder press", 2, 10, 12), item("Cable lateral raise", 3, 12, 15),
 item("Overhead cable triceps extension", 2, 12, 15), item("Seated calf raise", 3, 12, 15)])
workout("b1-pull-b", "Pull B · Intro", "Pull", "Saturday. Same muscles as Pull A, more volume, higher reps.", [
 item("Chest-supported machine row", 3, 10, 12), item("Assisted pull-up", 3, 8, 12, "Or neutral-grip pulldown"),
 item("Hip thrust", 3, 10, 12, "Machine or barbell"), item("Lying leg curl", 2, 10, 12), item("Face pull", 2, 12, 15),
 item("Hammer curl", 2, 10, 12), item("Hanging knee raise", 2, 10, 15)])

# ---- Block 2: Core 1 ----
workout("b2-push-a", "Push A · Core 1 (heavy)", "Push", "Tuesday, heavy day: compounds lead, 5–8 reps.", [
 item("Barbell back squat", 4, 5, 7), item("Barbell bench press", 4, 5, 7), item("Standing overhead press", 3, 6, 8),
 item("Bulgarian split squat", 3, 8, 10, "Per leg"), item("Cable lateral raise", 3, 12, 15),
 item("Overhead cable triceps extension", 3, 10, 12), item("Standing calf raise", 3, 8, 12)])
workout("b2-pull-a", "Pull A · Core 1 (heavy)", "Pull", "Wednesday, heavy day.", [
 item("Conventional deadlift", 3, 4, 6), item("Weighted pull-up", 4, 5, 8), item("Barbell row", 3, 6, 10),
 item("Seated leg curl", 3, 8, 12), item("Face pull", 3, 12, 15), item("Barbell curl", 3, 8, 10),
 item("Hanging leg raise", 3, 10, 15)])
workout("b2-push-b", "Push B · Core 1 (volume)", "Push", "Friday, volume day: 8–12 reps.", [
 item("Incline barbell press", 4, 8, 10, "Or incline dumbbell press"), item("Hack squat", 3, 8, 12),
 item("Seated dumbbell shoulder press", 3, 8, 10), item("Weighted dips", 3, 8, 10), item("Leg extension", 2, 12, 15),
 item("Dumbbell lateral raise", 3, 15, 20), item("EZ-bar skull crusher", 2, 10, 12), item("Seated calf raise", 3, 12, 15)])
workout("b2-pull-b", "Pull B · Core 1 (volume)", "Pull", "Saturday, volume day.", [
 item("Romanian deadlift", 3, 6, 10), item("Chest-supported T-bar row", 3, 8, 10),
 item("Single-arm lat pulldown", 3, 10, 12, "Per arm"), item("Lying leg curl", 2, 10, 12),
 item("Rear delt cable fly", 3, 12, 15), item("Incline dumbbell curl", 3, 10, 12), item("Hammer curl", 2, 10, 12),
 item("Cable crunch", 3, 10, 15)])

# ---- Block 3: Core 2, two exercise options ----
def b3(opt):
    o1 = opt == 1
    pick = lambda a, b: a if o1 else b
    tag = f"Option {opt}"
    workout(f"b3o{opt}-push-a", f"Push A · Core 2 · {tag}", "Push", f"Tuesday, heavy day. Block 3 exercise set {opt}.", [
      item(pick("Barbell back squat", "Front squat"), 4, 4, 6), item(pick("Barbell bench press", "Close-grip bench press"), 4, 4, 6),
      item(pick("Seated barbell press", "Standing overhead press"), 3, 6, 8),
      item(pick("Bulgarian split squat", "Walking lunge"), 3, 8, 12, "Per leg"),
      item(pick("Cable lateral raise", "Lean-away dumbbell lateral raise"), 4, 12, 15),
      item(pick("Overhead cable triceps extension", "Dumbbell overhead triceps extension"), 3, 10, 12),
      item("Standing calf raise", 4, 8, 12)])
    workout(f"b3o{opt}-pull-a", f"Pull A · Core 2 · {tag}", "Pull", f"Wednesday, heavy day. Block 3 exercise set {opt}.", [
      item(pick("Conventional deadlift", "Trap-bar deadlift"), 3, 3, 5), item(pick("Weighted pull-up", "Weighted chin-up"), 4, 5, 8),
      item(pick("Pendlay row", "Seal row"), 4, 6, 8),
      item(pick("Seated leg curl", "Nordic curl"), 3, 8, 12, "" if o1 else "Assisted"),
      item(pick("Face pull", "Reverse pec deck"), 3, 12, 15), item(pick("Barbell curl", "Preacher curl"), 3, 8, 10),
      item(pick("Hanging leg raise", "Ab wheel"), 3, 10, 15)])
    workout(f"b3o{opt}-push-b", f"Push B · Core 2 · {tag}", "Push", f"Friday, volume day. Block 3 exercise set {opt}.", [
      item(pick("Incline dumbbell press", "Incline Smith press"), 4, 8, 12),
      item(pick("Hack squat", "Pendulum squat"), 4, 8, 12, "" if o1 else "Or leg press"),
      item(pick("Weighted dips", "Machine chest press"), 3, 8, 12), item(pick("Machine shoulder press", "Arnold press"), 3, 8, 12),
      item("Leg extension", 3, 12, 15), item(pick("Low-to-high cable fly", "Pec deck"), 2, 12, 15),
      item("Dumbbell lateral raise", 3, 15, 20), item(pick("EZ-bar skull crusher", "Cable triceps pushdown"), 3, 10, 15),
      item("Seated calf raise", 3, 12, 15)])
    workout(f"b3o{opt}-pull-b", f"Pull B · Core 2 · {tag}", "Pull", f"Saturday, volume day. Block 3 exercise set {opt}.", [
      item(pick("Romanian deadlift", "Stiff-leg deadlift"), 3, 8, 10), item(pick("Chest-supported T-bar row", "Meadows row"), 4, 8, 12),
      item(pick("Wide-grip lat pulldown", "Kneeling single-arm pulldown"), 3, 10, 12),
      item(pick("Hip thrust", "45° back extension"), 3, 8, 12), item("Lying leg curl", 3, 10, 12),
      item("Rear delt cable fly", 3, 15, 20), item(pick("Incline dumbbell curl", "Bayesian cable curl"), 3, 10, 12),
      item(pick("Hammer curl", "Reverse curl"), 2, 10, 12), item("Cable crunch", 3, 10, 15)])
b3(1); b3(2)

RULES = ("Double progression: when every set hits the top of the rep range at the target RIR, add load "
         "(+2.5 kg upper body, +5 kg lower body), then build reps back up. Rest 2–3 min on compounds, 1–2 min on isolations. "
         "Warm-up: 5 min easy cardio, then 2–3 ramp-up sets on the first compound. Sharp or joint pain: swap the exercise.")
B3_RIR = ("Each mesocycle: weeks 1–2 at 3 RIR, weeks 3–4 at 2 RIR, week 5 at 0–1 RIR, week 6 deload (same exercises, half the sets, 4+ RIR). "
          "Add one set to the main lift of each lagging muscle in weeks 3–5. Intensity techniques (myo-reps, drop sets, lengthened partials) "
          "on the last set of isolation exercises only, never on squats or deadlifts. About 14–20 sets per muscle per week.")
CARDIO = "Cardio (low-impact, mostly Zone 2) on Mon, Thu or Sun, or after Pull sessions, never right before squats or deadlifts: "

blocks = [
 ("b1", "Winter Arc 1 · Intro", "2026-11-03", "2026-11-30", "b1",
  "4 weeks of mostly machines at moderate reps, ramping effort each week. About 8–10 sets per muscle per week. Goal: re-teach the body to lift, not to set PRs.\n"
  "RIR by week: week 1 at 4 RIR (find working weights), week 2 at 3 (add reps or load where it felt easy), week 3 at 2 (first real push), week 4 at 1–2 (push it, then roll into Block 2).\n" + RULES),
 ("b2", "Winter Arc 2 · Core 1", "2026-12-01", "2027-02-22", "b2",
  "Compounds lead every session; A days go heavy (5–8 reps), B days go hypertrophy (8–12 reps). About 12–16 sets per muscle per week. Two 6-week waves, each ending in a deload.\n"
  "RIR (compounds / isolations): weeks 1–2 and 7–8: 3 / 2. Weeks 3–4 and 9–10: 2 / 1. Weeks 5 and 11: 1 / 0 (to failure). "
  "Weeks 6 (5–11 Jan) and 12 (16–22 Feb): deload, same exercises, half the sets, 4+ RIR.\n"
  "Week 4 lands on Christmas; if the holidays wreck the schedule, swap it with the week 6 deload.\n"
  "Gauge progress by the big four (squat, bench, deadlift, pull-up): by week 12, clearly more weight or reps than in week 1.\n" + RULES),
 ("b3m1", "Winter Arc 3 · Core 2 · M1", "2027-02-23", "2027-04-05", "b3o1",
  "Block 3, mesocycle 1 (weeks 1–6), exercise set Option 1.\n" + B3_RIR + "\n" + CARDIO + "2 × 25 min Zone 2 a week; deload week 2 × 20 min easy. Plus 8,000–10,000 steps a day.\n" + RULES),
 ("b3m2", "Winter Arc 3 · Core 2 · M2", "2027-04-06", "2027-05-17", "b3o2",
  "Block 3, mesocycle 2 (weeks 7–12), exercise set Option 2.\n" + B3_RIR + "\n" + CARDIO + "3 × 30 min Zone 2 a week; deload week 2 × 20 min easy. Plus 8,000–10,000 steps a day.\n" + RULES),
 ("b3m3", "Winter Arc 3 · Core 2 · M3", "2027-05-18", "2027-06-28", "b3o1",
  "Block 3, mesocycle 3 (weeks 13–18), exercise set Option 1: beat your M1 numbers.\n" + B3_RIR + "\n" + CARDIO + "3 × 35 min Zone 2 a week, plus bike sprints on Thursday: 8 × 20 s hard, 100 s easy. Deload week 2 × 20 min easy.\n" + RULES),
 ("b3m4", "Winter Arc 3 · Core 2 · M4 + test", "2027-06-29", "2027-08-23", "b3o2",
  "Block 3, mesocycle 4 (weeks 19–24, 29 Jun – 9 Aug), exercise set Option 2: beat your M2 numbers. Then the last 2 weeks of the plan:\n"
  "Week 25 (10–16 Aug): work up to heavy triples on the big four (squat, bench, deadlift, pull-up). Week 26 (17–23 Aug): deload.\n"
  + B3_RIR + "\n" + CARDIO + "3 × 40 min Zone 2 a week, plus bike sprints on Thursday: 10 × 20 s hard, 100 s easy. Deload weeks 2 × 20 min easy.\n" + RULES),
]

used = sorted({it["exerciseId"] for w in W.values() for it in w["items"]})
ex_by_id = {uid("exercise", n): n for n in EX}
exercises = [{"id": i, "name": ex_by_id[i], "muscleGroup": EX[ex_by_id[i]][0], "equipment": EX[ex_by_id[i]][1]} for i in used]
exercises.sort(key=lambda e: e["name"].lower())
workouts = [{"id": uid("workout", k), "name": w["name"], "type": w["type"], "notes": w["notes"], "exercises": w["items"]} for k, w in W.items()]
training_blocks = []
for key, name, start, end, wset, notes in blocks:
    ids = [uid("workout", f"{wset}-{s}") for s in ("push-a", "pull-a", "push-b", "pull-b")]
    training_blocks.append({"id": uid("block", key), "name": name, "startDate": start, "endDate": end, "notes": notes,
      "workoutIds": ids,
      "schedule": [{"dayOfWeek": d, "workoutId": i} for d, i in zip(("TUESDAY", "WEDNESDAY", "FRIDAY", "SATURDAY"), ids)]})

out = {"format": "open-weight", "formatVersion": 1, "appVersion": "0.1.0", "exportedAt": "2026-11-08T12:00:00Z",
       "scope": {"kind": "items", "description": "Winter Arc: Push/Pull Plan (3 Nov 2026 – 23 Aug 2027)"},
       "data": {"exercises": exercises, "workouts": workouts, "trainingBlocks": training_blocks}}
unused = sorted(set(EX) - {ex_by_id[i] for i in used})
assert not unused, unused
json.dump(out, open(sys.argv[1], "w"), indent=2, ensure_ascii=False)
print(len(exercises), "exercises,", len(workouts), "workouts,", len(training_blocks), "blocks,",
      sum(len(s["sets"]) for w in workouts for s in w["exercises"]), "sets")
