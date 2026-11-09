# Plans

Open Weight import files (format: see the Import/Export Format doc). Import one on the phone with
**Settings → Import from a file** (or open the file from Files or a chat). Importing the same file twice changes nothing.

| File | Plan |
| --- | --- |
| `winter-arc.openweight.json` | Winter Arc Push/Pull, 3 Nov 2026 – 23 Aug 2027: 6 training blocks, 16 workouts, 69 exercises |
| `winter-arc-meal-plan.openweight.json` | Winter Arc Meal Plan (2,900 kcal): 2 diet plans, 10 recipes, 44 ingredients |

`winter_arc.py` generates the Winter Arc file from the plan doc's tables, with stable ids:

```bash
python3 plans/winter_arc.py plans/winter-arc.openweight.json
```

Block 3 of the doc alternates two exercise sets every 6 weeks, and a block's schedule is one
repeating week, so Block 3 is four blocks here (M1–M4, Option 1 / 2 / 1 / 2); M4 also holds the
test and deload weeks. Loads are left empty (the doc sets reps, not weights); rest is 2.5 min on
compounds and 1.5 min on isolations, per the doc's rest rule.

## Winter Arc meal plan

`winter_arc_meal_plan.py` generates it from the meal plan doc:

```bash
python3 plans/winter_arc_meal_plan.py > plans/winter-arc-meal-plan.openweight.json
```

- A recipe's one serving is **your** portion, so the app shows your macros (within a few percent of
  the doc's table; carbs run slightly higher, since the doc counts them a little differently).
  The doc cooks batches for two, split 4:3 (your portion is 4/21 of a 3 + 3 batch, 1/7 of the
  ragù). Batch amounts and her portions are in each recipe's notes.
- Two diet plans: **"Winter Arc – you"** (1 serving per meal) shows your day on the calendar;
  **"Winter Arc – for two"** adds her portion (about ¾ of yours, banana 1 each), so its shopping
  list covers both of you: a week buys 700 g oats and 14 bananas, as the doc's list does.
- The day's snack is the bresaola panino plus a banana. Salt, pepper and dried spices are in the
  steps, not the ingredient lines. Nutrition values are typical label/USDA values per 100 g or ml.

