"""Builds the Winter Arc meal plan (from the "Winter Arc Meal Plan (2,900 kcal)" doc) as an Open Weight file.

Recipes are one serving = YOUR portion, so the app shows your macros per meal. The doc cooks most
dishes as one batch for both of you (3 + 3 portions, split 4:3 with the bigger share yours), so your
portion is 4/21 of the batch; the ragù makes 8 portions (4 + 4), so yours is 1/7 of it. Batch
amounts and her portions are kept in each recipe's notes.

Two diet plans: "you" (1 serving per meal) and "for two" (your serving plus hers, about 0.7-0.75
of yours, as in the doc's table), which is the one to apply when the shopping list should cover
both of you. Salt, pepper and dried spices are in the steps, not the ingredient lines.

Nutrition per 100 g (or ml) is typical label/USDA values; the doc itself calls its numbers close
estimates. Run: python3 plans/winter_arc_meal_plan.py > plans/winter-arc-meal-plan.openweight.json
"""
import json, sys, uuid

NS = uuid.uuid5(uuid.NAMESPACE_URL, "open-weight/winter-arc-meal-plan")
def uid(kind, key): return str(uuid.uuid5(NS, f"{kind}:{key}"))
T = "2026-11-04T12:00:00Z"

MEAT, DAIRY, CARBS, VEG, PANTRY = "Meat", "Dairy and eggs", "Carbs and bakery", "Fruit and veg", "Pantry"

# name: (unit, shop category, kcal, protein, carbs, fat, grams per piece)
ING = {
  "Oats": ("G", CARBS, 379, 13.2, 67.7, 6.5, None),
  "Milk 2%": ("ML", DAIRY, 50, 3.3, 4.8, 2.0, None),
  "Natural skyr": ("G", DAIRY, 63, 11.0, 4.0, 0.2, None),
  "Honey": ("G", PANTRY, 304, 0.3, 82.0, 0.0, None),
  "Cocoa powder, unsweetened": ("G", PANTRY, 228, 19.6, 57.9, 13.7, None),
  "Dark chocolate 85%": ("G", PANTRY, 584, 12.5, 19.0, 46.0, None),
  "Espresso": ("ML", PANTRY, 2, 0.1, 0.0, 0.2, None),
  "Chicken breast": ("G", MEAT, 120, 22.5, 0.0, 2.6, None),
  "Penne or fusilli": ("G", CARBS, 371, 13.0, 75.0, 1.5, None),
  "Broccoli": ("G", VEG, 34, 2.8, 6.6, 0.4, None),
  "Cherry tomatoes": ("G", VEG, 18, 0.9, 3.9, 0.2, None),
  "Pesto alla genovese": ("G", PANTRY, 450, 5.0, 6.0, 45.0, None),
  "Parmesan": ("G", DAIRY, 392, 35.8, 3.2, 25.8, None),
  "Extra virgin olive oil": ("G", PANTRY, 884, 0.0, 0.0, 100.0, None),
  "Lemon": ("PCS", VEG, 29, 1.1, 9.3, 0.3, 100),
  "Pork tenderloin": ("G", MEAT, 120, 21.0, 0.0, 3.5, None),
  "Potatoes": ("G", VEG, 77, 2.0, 17.0, 0.1, None),
  "Bell pepper": ("PCS", VEG, 31, 1.0, 6.0, 0.3, 160),
  "Red onion": ("PCS", VEG, 40, 1.1, 9.3, 0.1, 150),
  "Yellow onion": ("PCS", VEG, 40, 1.1, 9.3, 0.1, 150),
  "Garlic": ("PCS", VEG, 149, 6.4, 33.0, 0.5, 5),
  "Fennel seeds": ("G", PANTRY, 345, 15.8, 52.0, 14.9, None),
  "Fresh rosemary": ("G", VEG, 131, 3.3, 20.7, 5.9, None),
  "Ciabatta": ("G", CARBS, 270, 9.0, 50.0, 3.5, None),
  "Chicken thighs, boneless skinless": ("G", MEAT, 121, 19.7, 0.0, 4.1, None),
  "Rice": ("G", CARBS, 360, 6.6, 79.0, 0.6, None),
  "Passata": ("G", PANTRY, 30, 1.3, 5.0, 0.2, None),
  "Mushrooms": ("G", VEG, 22, 3.1, 3.3, 0.3, None),
  "Black olives": ("G", PANTRY, 115, 0.8, 6.0, 10.7, None),
  "Dry red wine": ("ML", PANTRY, 85, 0.1, 2.6, 0.0, None),
  "Steak (rump, sirloin or flank)": ("G", MEAT, 135, 22.0, 0.0, 5.0, None),
  "Rocket": ("G", VEG, 25, 2.6, 3.7, 0.7, None),
  "Balsamic glaze": ("G", PANTRY, 260, 0.5, 60.0, 0.0, None),
  "Pork shoulder, boneless": ("G", MEAT, 160, 19.0, 0.0, 9.0, None),
  "Carrot": ("PCS", VEG, 41, 0.9, 9.6, 0.2, 60),
  "Celery stick": ("PCS", VEG, 16, 0.7, 3.0, 0.2, 40),
  "Pappardelle": ("G", CARBS, 380, 14.0, 70.0, 4.0, None),
  "Quick polenta": ("G", CARBS, 362, 8.1, 76.0, 1.8, None),
  "Bresaola": ("G", MEAT, 151, 32.0, 0.4, 2.6, None),
  "Light mozzarella": ("G", DAIRY, 163, 18.0, 1.0, 9.5, None),
  "Eggs": ("PCS", DAIRY, 143, 12.6, 0.7, 9.5, 55),
  "Prosciutto crudo": ("G", MEAT, 250, 26.0, 0.0, 16.0, None),
  "Fresh basil": ("G", VEG, 23, 3.2, 2.7, 0.6, None),
  "Banana": ("PCS", VEG, 89, 1.1, 22.8, 0.3, 118),
}

B = 4 / 21  # your share of a 3 + 3 batch split 4:3
R = 1 / 7   # your share of the 8-portion ragù (4 yours, 4 hers at about 3/4)

def r(x, step):
  return round(round(x / step) * step, 2)

def batch(name, amount, share=B):
  """Your portion of a batch amount, rounded to something you can weigh."""
  unit = ING[name][0]
  q = amount * share
  q = r(q, 0.05) if unit == "PCS" else (r(q, 1) if q >= 5 else r(q, 0.5))
  return (name, q)

RAGU = [
  batch("Pork shoulder, boneless", 1250, R), batch("Yellow onion", 2, R), batch("Carrot", 2, R),
  batch("Celery stick", 2, R), batch("Garlic", 5, R), batch("Extra virgin olive oil", 35, R),
  batch("Dry red wine", 250, R), batch("Passata", 1000, R), batch("Fresh rosemary", 2, R),
]
RAGU_STEPS = """Brown the pork shoulder (cut into 6 chunks) hard in 2 batches, 8-10 min each. That crust is the flavour, so don't rush it. Take it out.
Cook the finely diced onion, carrot and celery for 8 min until soft and sweet. Add the garlic for 1 min.
Add the wine and reduce by half. Then add the passata, 350 ml water, 3 bay leaves, rosemary, chili, salt and the pork.
Cover and simmer on very low for 2.5-3 hours, until the pork falls apart. Shred it with 2 forks, season, and split it into 8 containers: 4 bigger ones for you, 4 smaller ones for her.
Freeze the last 4 portions (2 each) for next Sunday."""
RAGU_BATCH = "Ragù batch (8 portions: 4 yours, 4 hers; made every other week): 1.25 kg boneless pork shoulder · 2 onions, 2 carrots, 2 celery sticks · 5 garlic cloves · 35 g olive oil · 250 ml dry red wine · 1 kg passata · 3 bay leaves · rosemary · chili · salt."

# name, prep minutes, tags, lines, steps, notes, her share of your portion (for the "for two" plan)
RECIPES = [
  ("Tiramisu overnight oats", 5, "breakfast, meal prep",
   [("Oats", 80), ("Milk 2%", 150), ("Natural skyr", 200), ("Honey", 15), ("Cocoa powder, unsweetened", 10),
    ("Dark chocolate 85%", 10), ("Espresso", 30)],
   """In the jar, stir together the oats, milk, espresso (or 1 tsp instant coffee in 30 ml hot water), a pinch of salt and half the honey.
Mix the skyr with the rest of the honey and spoon it on top as a separate layer.
Dust with cocoa, grate the chocolate over, lid it, and refrigerate overnight. Eat it cold.""",
   "Per jar. Her: 55 g oats · 110 ml milk · 150 g skyr · 10 g honey · 8 g cocoa · 8 g dark chocolate. Sunday prep: 6 jars (Mon-Wed), Thursday: 4 jars (Thu-Fri).",
   0.75),
  ("Prosciutto egg ciabatta", 10, "breakfast, weekend",
   [("Eggs", 3), ("Ciabatta", 100), ("Prosciutto crudo", 30), ("Light mozzarella", 30), ("Cherry tomatoes", 50), ("Fresh basil", 3)],
   """Toast the ciabatta.
Scramble the eggs low and slow, and pull them off while they still look a bit wet.
Pile on the eggs, torn mozzarella, prosciutto, tomatoes and basil. Salt and pepper.""",
   "Sat + Sun. Her: 2 eggs · 70 g ciabatta · 25 g prosciutto · 25 g light mozzarella.",
   0.7),
  ("Chicken pesto pasta", 30, "lunch, meal prep",
   [batch("Chicken breast", 950), batch("Penne or fusilli", 510), batch("Broccoli", 600), batch("Cherry tomatoes", 600),
    batch("Pesto alla genovese", 150), batch("Parmesan", 75), batch("Extra virgin olive oil", 25), batch("Lemon", 2)],
   """Butterfly the chicken so it cooks evenly. Season hard with salt, pepper, garlic powder and oregano.
Sear it in the oil over high heat for 5-6 min a side until the centre hits 74 °C. Rest 5 min, then slice.
Boil the pasta in well-salted water, adding the broccoli for the last 3 min. Save a mug of pasta water before you drain.
Blister the tomatoes in the chicken pan for 3 min to pick up the browned bits.
Off the heat, toss the pasta with pesto, parmesan and a splash of pasta water. Heat makes pesto go dark and bitter.
Split into 6 containers (4:3, yours bigger) and top each with chicken, tomatoes, lemon zest and chili.""",
   "Batch for two (3 + 3, split 4:3): 950 g chicken breast · 510 g pasta · 600 g broccoli · 600 g cherry tomatoes · 150 g pesto · 75 g parmesan · 25 g olive oil · 2 lemons (zest). Her portion: about 130 g chicken, 70 g pasta.",
   0.7),
  ("Porchetta-style pork + roast potatoes", 45, "dinner, meal prep",
   [batch("Pork tenderloin", 1000), batch("Potatoes", 2000), batch("Bell pepper", 5), batch("Red onion", 2),
    batch("Extra virgin olive oil", 75), batch("Garlic", 6), batch("Fennel seeds", 9), batch("Fresh rosemary", 4),
    batch("Lemon", 2), ("Ciabatta", 50)],
   """Heat the oven to 220 °C. Chunk the potatoes, toss with 40 g oil, salt and half the rosemary, and roast 20 min.
Crush the fennel seeds and garlic with the rest of the rosemary, the lemon zest, chili, salt and 15 g oil. Rub it all over the pork.
Sear the pork in 20 g oil for 2 min a side.
Add the sliced peppers and onion to the potato trays, set one tenderloin on each, and roast 15-18 min until the centre reads 63 °C.
Rest 10 min before slicing. Squeeze lemon over everything and spoon on the pan juices.
Eat with ciabatta for mopping up the juices.""",
   "Batch for two (3 + 3, split 4:3): 1 kg pork tenderloin · 2 kg potatoes · 5 bell peppers · 2 red onions · 75 g olive oil · 6 garlic cloves · 1.5 tbsp fennel seeds · 1.5 tbsp rosemary · 2 lemons. Ciabatta: 50 g yours, 35 g hers. Her portion: about 145 g pork, 280 g potatoes.",
   0.7),
  ("Chicken cacciatore + rice", 50, "lunch, meal prep",
   [batch("Chicken thighs, boneless skinless", 1000), batch("Rice", 560), batch("Passata", 800), batch("Mushrooms", 250),
    batch("Bell pepper", 2), batch("Yellow onion", 2), batch("Garlic", 5), batch("Black olives", 75),
    batch("Dry red wine", 175), batch("Extra virgin olive oil", 50)],
   """Season the thighs and brown them in 2 batches in 25 g oil for 4 min a side. Take them out.
In the remaining 25 g oil, cook the onion, pepper and mushrooms for 6 min, then the garlic for 1 min.
Pour in the wine and let it reduce by half while you scrape up the browned bits.
Add the tomatoes, oregano, a rosemary sprig, 2 bay leaves, chili and the olives, return the chicken, cover, and simmer 30 min.
Cook the rice while it simmers. Portion into 6 containers (4:3, yours bigger). It tastes even better on day 2.""",
   "Batch for two (3 + 3, split 4:3): 1 kg chicken thighs · 560 g rice · 800 g passata · 250 g mushrooms · 2 bell peppers · 2 onions · 5 garlic cloves · 75 g black olives · 175 ml red wine · 50 g olive oil. Her portion: about 145 g thighs, 75 g rice.",
   0.7),
  ("Steak tagliata + rosemary potatoes", 15, "dinner",
   [("Steak (rump, sirloin or flank)", 200), ("Extra virgin olive oil", 5), ("Parmesan", 15), ("Rocket", 30),
    ("Cherry tomatoes", 100), ("Balsamic glaze", 10), batch("Potatoes", 2300), batch("Extra virgin olive oil", 50),
    batch("Garlic", 5), batch("Fresh rosemary", 4)],
   """Thursday prep: roast the potatoes with olive oil, rosemary, smashed garlic and salt on 2 trays at 220 °C for 35-40 min, then split into 6 containers (4:3, yours bigger).
Each night: take the steak out 20 min before cooking, pat it dry, and salt it well.
Get the pan smoking hot. Cook 2-3 min a side for medium-rare (2 cm thick), with a garlic clove and rosemary in for the last minute.
Rest 5 min. Meanwhile, crisp the potatoes in the same pan.
Slice thin against the grain. Serve over rocket and halved tomatoes, then add parmesan, balsamic and flaky salt.""",
   "Steak is cooked fresh each night. Potato batch (Thursday, 3 + 3): 2.3 kg potatoes · 50 g olive oil · rosemary · 5 garlic cloves. Her: 150 g steak · 4 g oil · 10 g parmesan · about 320 g potatoes.",
   0.75),
  ("Pork ragù pappardelle", 180, "lunch, sunday",
   RAGU + [("Pappardelle", 100), ("Parmesan", 15)],
   RAGU_STEPS + "\nToss your portion with 100 g pappardelle, 15 g parmesan and a splash of pasta water.",
   RAGU_BATCH + " Her: 70 g pappardelle · 10 g parmesan.",
   0.7),
  ("Pork ragù on polenta", 180, "dinner, sunday",
   RAGU + [("Quick polenta", 90), ("Parmesan", 15)],
   RAGU_STEPS + "\nSpoon your portion over 90 g quick polenta (cooked as the pack says, salted) with 15 g parmesan.",
   RAGU_BATCH + " Her: 65 g polenta · 10 g parmesan.",
   0.7),
  ("Bresaola panino", 2, "snack, daily",
   [("Ciabatta", 100), ("Bresaola", 60), ("Light mozzarella", 60), ("Rocket", 15), ("Balsamic glaze", 5)],
   "Toast the bread, stack bresaola, mozzarella and rocket, add a drizzle of balsamic and black pepper, and eat. On gym days, eat it 1.5-2 hours before training.",
   "Her: 70 g ciabatta · 45 g bresaola · 45 g light mozzarella. Build it fresh: made ahead, the bread goes soggy. Budget swap: lean cooked ham instead of bresaola.",
   0.75),
  ("Banana", None, "snack",
   [("Banana", 1)], "", "With the panino.", 1.0),
]

DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
WEEK = {
  "BREAKFAST": ["Tiramisu overnight oats"] * 5 + ["Prosciutto egg ciabatta"] * 2,
  "LUNCH": ["Chicken pesto pasta"] * 3 + ["Chicken cacciatore + rice"] * 3 + ["Pork ragù pappardelle"],
  "SNACK": ["Bresaola panino"] * 7,
  "DINNER": ["Porchetta-style pork + roast potatoes"] * 3 + ["Steak tagliata + rosemary potatoes"] * 3 + ["Pork ragù on polenta"],
}

def main():
  used = {name for rec in RECIPES for name, _ in rec[3]}
  assert used <= set(ING), used - set(ING)
  ingredients = [
    {"id": uid("ingredient", n), "name": n, "defaultUnit": u, "shopCategory": c, "kcalPer100": k, "proteinPer100": p,
     "carbsPer100": cb, "fatPer100": f, **({"gramsPerPiece": g} if g else {}), "createdAt": T, "updatedAt": T}
    for n, (u, c, k, p, cb, f, g) in ING.items() if n in used
  ]
  recipes = []
  for name, prep, tags, lines, steps, notes, _ in RECIPES:
    rec = {"id": uid("recipe", name), "name": name, "servings": 1, "steps": steps, "tags": tags, "notes": notes,
           "ingredients": [{"ingredientId": uid("ingredient", n), "quantity": q, "unit": ING[n][0]} for n, q in lines],
           "createdAt": T, "updatedAt": T}
    if prep is not None: rec["prepMinutes"] = prep
    recipes.append(rec)
  her = {name: share for name, *_, share in RECIPES}

  def plan(name, notes, for_two):
    meals = []
    for slot, per_day in WEEK.items():
      for day, recipe in zip(DAYS, per_day):
        meals.append({"dayOfWeek": day, "slot": slot, "recipeId": uid("recipe", recipe), "servings": round(1 + her[recipe], 2) if for_two else 1.0})
      if slot == "SNACK":
        for day in DAYS:
          meals.append({"dayOfWeek": day, "slot": slot, "recipeId": uid("recipe", "Banana"), "servings": 2.0 if for_two else 1.0})
    return {"id": uid("plan", name), "name": name, "notes": notes, "meals": meals, "createdAt": T, "updatedAt": T}

  plans = [
    plan("Winter Arc – you (2,900 kcal)",
         "Your meals, about 2,900 kcal and 190 g protein a day. Gym days: Tue, Wed, Fri, Sat. Prep on Sunday (Sun + Mon-Wed) and Thursday (Thu-Sat).",
         False),
    plan("Winter Arc – for two",
         "Your portion plus hers (about 3/4 of yours, banana 1 each) in every meal: apply this one when the shopping list should cover both of you. Her days come to about 2,100 kcal and 140 g protein.",
         True),
  ]
  out = {"format": "open-weight", "formatVersion": 1, "appVersion": "1.0.0", "exportedAt": T,
         "scope": {"kind": "items", "description": "Diet plan: Winter Arc Meal Plan (2,900 kcal)"},
         "data": {"ingredients": ingredients, "recipes": recipes, "dietPlans": plans}}
  json.dump(out, sys.stdout, indent=2, ensure_ascii=False)
  sys.stdout.write("\n")

if __name__ == "__main__":
  main()
