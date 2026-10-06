# Playtest: pantry and water barrel (E2)

Setup: creative world, cheats on, `/gamerule doDaylightCycle false` (so real time does not interfere), default server config.
Give yourself: `pirates_n_ships:pantry`, `pirates_n_ships:water_barrel`, bread, cooked beef, apples, `pirates_n_ships:hardtack`, `pirates_n_ships:rum`, water bottles, buckets, water buckets, a hopper, a chest.
Commands work on the block you look at (within 8 blocks) plus every pantry and water barrel within 4 blocks of it.

## 1. Pantry container
1. Place a pantry, right-click it. **Expect:** a normal 3-row chest screen titled "Pantry".
2. Put in 16 bread, 8 cooked beef, 8 apples, 32 hardtack, 6 rum, 6 water bottles, 1 cobblestone. Close.
3. Sneak + right-click with an **empty hand** (both hands empty). **Expect** two chat lines:
   - "Pantry: 64 food (… nutrition), 6.0 water rations, 6 rum, weight …" (food = 16+8+8+32 items; nutrition = 16×5 + 8×8 + 8×4 + 32×4 = 304.0)
   - "Next to spoil: 8 Cooked Beef in 5.0 days" (or apples; both are fresh food with the default 5-day shelf life). Bread and hardtack are preserved and never listed.
4. Put a comparator against the pantry. **Expect:** a signal that grows as you add items, 0 when empty.

## 2. Debug commands, crew of 4
1. Look at the pantry: `/pirates provisions show 4`. **Expect:** "1 pantries and 0 water barrels within 4 blocks", the info lines, and "Supplies for a crew of 4: food … days, water 1.5 days, rum … days" (6 bottles / 4 crew).
2. `/pirates provisions advance 1 4`. **Expect:** "Eaten: …" lists cooked beef and apples first (fresh food goes first), 4 water bottles and rum; "Spoiled: nothing"; effects with positive morale (rum issued), work speed 1.00.
3. Open the pantry. **Expect:** fewer beef/apples, 2 water bottles left and **empty glass bottles** in their place (also one per drunk rum).
4. `/pirates provisions advance 2 4`. **Expect:** water runs out: effects show "thirsty true", negative morale, work speed below 1.
5. `/pirates provisions advance 1 4 2 0` (2 prisoners, no rum). **Expect:** more food eaten per day than with 4 crew alone, no rum eaten.

## 3. Spoilage
1. New pantry with 8 cooked beef and 8 hardtack. `/pirates provisions advance 6 0` (nobody eats, 6 days).
2. **Expect:** "Spoiled: 8 Cooked Beef"; the pantry holds 8 hardtack and 8 rotten flesh.
3. Optional: set `provisions.spoiled_food_result = NOTHING` in the server config, repeat. **Expect:** no rotten flesh.
4. Real time: with `doDaylightCycle true`, leave fresh food in a pantry for 5+ in-game days (or `/time add 130000` and wait a minute, the pantry checks every 1200 ticks). **Expect:** it turns into rotten flesh.

## 4. Water barrel
1. Place a crafted / creative water barrel. **Expect:** full (top shows water to the rim). Right-click with an empty hand: action bar "Water barrel: 16 / 16 water rations".
2. Use an empty bucket on it 5 times. **Expect:** 13, 10, 7, 4, 1 rations; the top texture shows less water; the 6th bucket does nothing (only 1 ration).
3. Use a glass bottle: 0 rations, the top looks empty (dark). Comparator next to it: 0.
4. Pour in a water bucket (3) and a water bottle (+1). **Expect:** 4 rations, the hand item comes back empty.
5. Break the barrel with 4 rations and place it again. **Expect:** still 4 rations.
6. Put the barrel next to the pantry and run `/pirates provisions show 4` on the pantry. **Expect:** "1 pantries and 1 water barrels" and more water days. `advance` drinks the pantry's water bottles first, then barrel water.

## 5. Rain catcher
1. Place an empty barrel under open sky, `/weather rain`. Wait a few minutes. **Expect:** the rations slowly rise (about one ration every ~2 minutes on average at random tick speed 3). Under a roof: nothing.
2. Set `provisions.rain_refill_enabled = false`: no more refilling.

## 6. Hoppers
1. Hopper (pointing down) above the pantry, fill it with bread and cobblestone. **Expect:** bread moves into the pantry, cobblestone stays in the hopper.
2. Hopper under the pantry, pointing into a chest. Pantry holds bread, glass bottles and rotten flesh. **Expect:** bottles and rotten flesh move out, the bread stays.

## 7. Breaking and relogging
1. Break a full pantry. **Expect:** the pantry block and all its items drop.
2. Fill a pantry with fresh food, run `/pirates provisions advance 3 0`, save and quit, rejoin. Sneak-use it. **Expect:** same contents, "Next to spoil … in 2.0 days" (ages survive the reload).

Report: screenshots of the chat output for 2.2, 3.2 and the barrel top textures at 16, 7 and 0 rations, plus `logs/latest.log` if anything errors.
