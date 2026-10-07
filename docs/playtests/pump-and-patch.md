# Playtest: bilge pump and hull patch (work package G4)

Pump rate, reach, crew pumping, breach closing and the config toggles are covered by 12 JUnit tests and 6 GameTests.
What only a client shows: the feel of pumping, the messages, the looks, and using the items on a moving ship.

Setup: `./gradlew :neoforge:runClient`, a world with deep water; planks, a helm, a Bilge Pump (stick / plank-bucket-plank
/ plank) and a few Hull Patches (2 planks + 1 coal or charcoal → 2). Please send `latest.log` if anything differs.

## Steps
1. Build a closed plank hull with a hold and a helm, launch it into deep water and assemble it. Put a Bilge Pump on the
   hold floor. Expected: the hold stays dry and the pump changes nothing.
2. Break one hull block in the hold wall below the waterline. Expected: water rises in the hold over several seconds,
   not at once, and the ship sits lower.
3. Hold right-click on the pump. Expected: the arm keeps swinging, the action bar counts the water down, the level
   drops by about 1 block per second (it may lose against the inflow). Release the key: pumping stops within about half
   a second.
4. Use a Hull Patch on the face of a hull block next to the hole. Expected: a tarred plank block fills the hole, one
   patch is used up, and the water stops rising at once. Pump the hold dry: "The bilge is dry".
5. Use a Hull Patch on an intact wall face, on the deck and on land. Expected: "Use the patch on a hole in the hull" or
   the "assembled ship" message, and no patch is used.
6. Place a pump on the deck above the flooded hold and pump. Expected: it drains the hold through the deck.
7. Break the patch. Expected: it drops a Hull Patch item and the hole leaks again. Place an oak plank into the hole:
   the leak stops.
8. Looks: the pump model (a placeholder until its Blockbench batch), its rotation when placed facing each direction,
   the item in hand and in the inventory; the patch texture (planks with pitch seams, a tar stripe and nail heads).
9. Config: `flooding.pump_enabled = false` makes the pump say it is disabled and the water stays; `patch_enabled =
   false` makes the patch say it is disabled.
10. On a moving ship: breach, patch and pump while sailing. Expected: the patch goes into the hole and the pump works
    as when still. Report if clicks miss on the moving hull.
11. Multiplayer: two players on two pumps in one room drain twice as fast.

## Also worth a look
- Is 1 block per second a good pump rate against the default inflow? Should pumping be slower or tire you more?
