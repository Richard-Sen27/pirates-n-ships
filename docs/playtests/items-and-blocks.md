# Playtest: basic items and blocks (work package C8)

Registration, recipes, loot, tags and food values are covered by GameTests. This checklist covers what only the client
shows: textures, models, names and the recipe book. All textures are generated placeholders
(`tools/gen_placeholder_textures.py`), so judge readability, not beauty. Not a gate for other work.

Setup: `./gradlew :neoforge:runClient`, a creative world, the **Pirates 'n' Ships** creative tab.
Please send a screenshot of the creative tab and of the blocks placed in a row, and `latest.log` if step 12 finds anything.

## Steps

1. **Creative tab.** It lists: rapier, cutlass, saber, pistol, musket, lead shot, cannonball, grappling hook, doubloon,
   tobacco, spices, cloth, rum, hardtack, salted fish, salt pork, lime, pantry, water barrel, shackles, brig bars,
   brig door, the four figureheads (mermaid, lion, eagle, skull), nameplate, flagpole, cargo crate, cargo barrel
   (plus the helm and the test block).
   - **Expected:** every entry has a readable 16×16 texture (no purple-black checkerboard) and an English name, not a
     raw `item.pirates_n_ships.*` key.
2. **Handheld items.** Hold each sword, the pistol and the musket in first and third person.
   - **Expected:** they are held like tools, angled in the hand. Sword tooltips: rapier 5 damage / 2.0 speed,
     cutlass 7 / 1.2, saber 6 / 1.6.
3. **Eating and drinking.** In survival, while hungry, eat hardtack, salted fish, salt pork and lime, and drink rum.
   - **Expected:** all can be eaten. Rum plays the drink animation and leaves a glass bottle.
4. **Figureheads.** Place each of the four while facing north, east, south and west.
   - **Expected:** the carved front faces the way you look. Sides and top show plain wood. They are full cubes for now.
5. **Nameplate.** Place it on the side of a plank wall from each direction.
   - **Expected:** a thin gold-framed plate sits flat on the wall. It can't be placed in mid-air, and you can't climb it.
6. **Flagpole.** Place one.
   - **Expected:** a thin post whose hitbox and outline match the post.
7. **Brig bars.** Place a row of bars, and a single bar next to a stone block.
   - **Expected:** they connect to each other and to the stone like iron bars. You can see through the gaps
     (no black fill).
8. **Brig door.** Place it and right-click it.
   - **Expected:** it opens and closes by hand with iron door sounds, and also with a button or lever. Both halves look
     right from both sides and with both hinge directions (place two doors next to each other).
9. **Solid blocks.** Place the cargo crate, cargo barrel, pantry and water barrel.
   - **Expected:** they look right from all sides (barrels and pantry have distinct tops). They don't open when
     right-clicked yet.
10. **Drops.** In survival, break every block with the right tool (axe for wood, pickaxe for bars and door).
    - **Expected:** each drops itself once. The door drops one item, whichever half you break.
11. **Recipe book.** Search for each item name.
    - **Expected:** every item except doubloon, tobacco, spices and lime has a recipe. Crafting gives these counts:
      lead shot 4, cannonball 2, hardtack 2, brig bars 6, flagpole 2, everything else 1.
12. **Log.** Search `latest.log` for `pirates_n_ships` together with "model" or "texture".
    - **Expected:** no missing model or missing texture warnings.

## Known placeholders (for the later art pass)
- Figureheads are full cubes with a carved front face.
- The flagpole borrows the vanilla fence post model, and the nameplate borrows the open trapdoor plate.
- The brig bars' transparency relies on a model field that only NeoForge reads. The Fabric port has to register a
  render layer for them.
