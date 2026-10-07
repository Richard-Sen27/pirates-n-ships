# Playtest: cold water and swimming hunger (work package S2)

The freezing rule, every exemption, the warm effect and the swim cost are covered by 19 JUnit tests and 12
GameTests (with the test area turned into a cold ocean). What only a client shows: the frost overlay and timing as
felt, the effect icon, and the hunger bar.

Setup: `./gradlew :neoforge:runClient`, survival, a frozen or cold ocean, rum, a boat, leather armour, a ship.

## Steps
1. **Freeze.** Swim in a frozen or cold ocean: the frost overlay builds over about 7 s, you slow down, then take 1
   damage every 2 s. Leave the water: the overlay fades in about 3.5 s.
2. **Boat.** The same water in a boat: no frost.
3. **Leather.** Wearing only a leather cap: no frost.
4. **Rum.** Drink rum: the "Warm" effect with a flame icon shows in the inventory and HUD for 2:00, no frost in cold
   water; when it ends, freezing resumes.
5. **Ship.** On the deck of an assembled ship in a frozen ocean, and inside the dry hull below the waterline: no frost.
6. **Warm water and creative.** A normal or warm ocean: no frost. Creative: no frost.
7. **Hunger.** Swim 100 blocks with `survival.swim_exhaustion_multiplier` 1.5, then 1.0 (`/data get entity @s
   foodExhaustionLevel` before and after): about 1.5× versus 1.0× the exhaustion.
8. **Toggle.** `survival.cold_water.enabled = false` on the config screen stops the frost at once.
