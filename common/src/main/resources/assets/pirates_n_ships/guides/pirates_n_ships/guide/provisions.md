---
navigation:
  title: "Provisions"
  parent: index.md
  position: 60
  icon: pirates_n_ships:pantry
item_ids:
  - pirates_n_ships:pantry
  - pirates_n_ships:sea_chest
  - pirates_n_ships:water_barrel
---

# Provisions

## Pantry
The doors face you when you place it.
A container with 27 slots that opens like a chest. It is the ship's food store.
- Anything edible counts as food, weighted by its nutrition. One crew member needs 6 nutrition per day (about one
  loaf of bread and a bit).
- **Fresh food spoils** after 5 days in the pantry and turns into rotten flesh. Preserved food never spoils: hardtack,
  salted fish, salt pork, bread, cookies, dried kelp, honey bottles, golden carrots and golden apples.
- Sneak-use it with both hands empty to read what it holds: food, water and rum, and what spoils next.
- **Hoppers** can only put provisions in, and can only take out what is not a provision (empty bottles, bowls, rotten
  flesh). So a hopper below works as a waste chute.

## Water barrel
The water in a placed barrel takes the colour of the water around it (blue at sea, murky in a swamp); the item
shows plain blue water.
Holds up to 16 rations of fresh water. One crew member drinks one ration per day.
- A water bucket adds 3 rations, a water bottle 1. An empty bucket takes 3 out, a glass bottle 1.
- Rain slowly refills a barrel under the open sky.
- The top shows the fill level. A broken barrel keeps its water in the item.

## What the rules do
The rules for a crew eating and drinking exist, but no crew eats yet. Try them with `/pirates provisions`:
- A crew eats perishable food first, the food closest to spoiling before the rest.
- Hungry crew lose morale and work at 75% speed. Thirsty crew lose morale faster and work at 50%. After 3 days of
  hunger or 1 day of thirst the crew is ready to desert.
- **Rum:** a ration raises morale. More than the ration makes the crew slow for a while.
- **Scurvy** sets in after 8 days without fresh food or fruit (apple, melon, berries, lime) and is cured by eating some.

## Cold water and swimming
Swimming or wading in frozen or cold oceans and frozen rivers fills the freezing meter like powder snow: after
about 7 seconds you are frozen and take damage every 2 seconds. You are safe in a boat, on a ship's deck or inside
a dry hull, in any piece of leather armour, or after a bottle of rum ("Warm", 2 minutes). Swimming makes you hungry
half again as fast as in vanilla. Server config `survival`.

## Sea chest
Craft it from three leather over iron, chest, iron. It holds 54 stacks and keeps them when broken, carried or
floated. Use it in the air to carry it on your back: you can't jump, sprint or swim, you walk slower, and in water
it drags you under. Place it on water to float it; it drifts with the wind and the current. Use it to open,
sneak-use to pick it up, or hit it to knock it loose as an item. On a ship's deck it is placed as a block. Server
config `sea_chest`.
