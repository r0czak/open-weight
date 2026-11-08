# Training plans

Open Weight import files (format: see the Import/Export Format doc). Import one on the phone with
**Settings → Import a file**. Importing the same file twice changes nothing.

| File | Plan |
| --- | --- |
| `winter-arc.openweight.json` | Winter Arc Push/Pull, 3 Nov 2026 – 23 Aug 2027: 6 training blocks, 16 workouts, 69 exercises |

`winter_arc.py` generates the Winter Arc file from the plan doc's tables, with stable ids:

```bash
python3 plans/winter_arc.py plans/winter-arc.openweight.json
```

Block 3 of the doc alternates two exercise sets every 6 weeks, and a block's schedule is one
repeating week, so Block 3 is four blocks here (M1–M4, Option 1 / 2 / 1 / 2); M4 also holds the
test and deload weeks. Loads are left empty (the doc sets reps, not weights); rest is 2.5 min on
compounds and 1.5 min on isolations, per the doc's rest rule.
