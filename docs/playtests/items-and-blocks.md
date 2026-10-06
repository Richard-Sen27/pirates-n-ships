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

## 3D models (F7a helm, anchor, flagpole, nameplate; F7b capstan, sail winch, yard; F7c figureheads)
Renders of the intended look are in `art/renders/`.
1. **Helm:** place it facing you. The wheel's face with the gold hub cap points at you, the pedestal stands behind it.
   The block under it and the blocks next to it show no holes. Rotate it four ways. On an assembled ship, clicking the
   left, middle and right thirds still steers. In the inventory the whole wheel is visible and not clipped; check it in
   hand and in third person too. The wheel reaches above its block: note whether it clips into a block placed above.
2. **Flagpole:** place one and hoist each flag kind with the wind in all four directions. The cloth starts at the pole
   with no gap, the cleat and rope never cut through the cloth, the gold finial sits on top. Stack two flagpoles: the
   joint shows a collar band. Check the item in the GUI and in hand.
3. **Nameplate:** place it on a hull wall facing each direction. The board hangs on two iron brackets against the wall,
   nothing floats or sits inside the wall. It works waterlogged. The item is 3D in the GUI now.
4. **Anchor:** stowed at the hull side it looks like an anchor: ring on top, stock across the shank, flukes at the
   bottom. Drop and raise it: the chain still attaches at the ring, size and position are as before.
5. **Capstan:** on deck, base, drum, whelps and drumhead look right, four bars reach about 3 px into the neighbouring
   blocks; neither it nor its neighbours render dark or with missing faces. On a ship the anchor drops and raises as
   before and the model does not change between anchor phases. Full-block hitbox.
6. **Sail winch:** place it facing each way; it looks the same each time, crank on the east side (known, a facing comes
   later). Alternating rope rings on the drum, the pawl touches the ratchet on the west side. Using it still cycles the
   trim; the crew station still works (whistle, assign, seat).
7. **Yard:** yards along x and along z look the same, turned; the spar sits exactly on the hitbox outline; a row of
   yards joins seamlessly with a lashing band on every block. With a square sail hoisted, the cloth lines up with the
   spar at half and at full and does not clip badly through the band or the jackstay.
8. **Figureheads (skull, eagle, lion, mermaid):** place each one facing north, east, south and west: the figure looks
   the way you faced when placing it and the mounting plate is on the side nearest you. Against a planks block the
   plate sits flush with no holes or x-ray around it. From the side: eagle and lion reach about ¾ block ahead, skull
   and mermaid about ½, parts hang below the block (the mermaid's tail almost a full block). From 10 blocks away each
   design is recognisable. In the GUI the figure is visible (not just the back plate); check hand, item frame and
   dropped item. Particles: bone, spruce, yellow terracotta, oak planks. On an assembled ship's bow the overhangs do not
   vanish at section edges. The prismarine tail shimmers (animated texture, intended).
9. **All of them:** break each block; the particles match the block's wood or material, never the missing texture.
   `latest.log` has no model or texture warnings mentioning `pirates_n_ships`. On a moving ship the models render with
   correct lighting.

## Known placeholders (for the later art pass)
- Cargo crate and barrel, pantry, water barrel, brig bars and door still use simple models (F7d, F7e).
- The yard cloth is a flat grid with a simple belly; the stay and triangular sail of F5b arrive with their own look.
- Item sprites are script-made placeholders; 3D item models come with the F8 batches (weapons first).
- The brig bars' transparency relies on a model field that only NeoForge reads. The Fabric port has to register a
  render layer for them.
