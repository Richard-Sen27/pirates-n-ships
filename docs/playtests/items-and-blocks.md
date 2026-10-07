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

## 3D models (F7a helm, anchor, flagpole, nameplate; F7b capstan, sail winch, yard; F7c figureheads; F7d containers and galley)
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
6. **Sail winch:** place it while looking north, east, south and west: the crank is on the side facing you each time,
   the ratchet on the far side. A winch from a world saved before G2 still shows its crank to the east. Alternating
   rope rings on the drum. Using it still cycles the trim; the crew station still works (whistle, assign, seat) and the
   crew member sits in the same spot whatever the facing (known: the seat ignores the facing). After turning a ship
   about 90° and disassembling it, the crank turned with the ship and the sail cloth is right at once, with no
   one-second flash of wrongly turned cloth.
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
9. **Cargo crate and barrel, pantry, water barrel (F7d):** place all four. The crate is a slatted box with braces and
   iron corner caps and a stencilled X on its east side; the barrel bulges with four hoops; the pantry is a larder
   cabinet whose doors always face north (known, a facing is a follow-up); the water barrel is an open barrel with a
   ladle on the rim. Fill the water barrel step by step with bottles or buckets from empty to full and back: the
   surface rises and falls through the five levels, the walls and floor never show through from any angle, the empty
   barrel shows a dark floor. Insert and take goods from crate and barrel, open the pantry, check comparator output:
   unchanged. Neighbouring blocks render normally. In hand, GUI, dropped and in a frame they look like small blocks.
10. **Cleat, bilge pump, brig bars and door (F7e):** a cleat on the floor in all four facings has its horns along the
    facing, rising at both ends; on walls and the ceiling it sits upright inside its outline. A stay and a triangular
    sail still attach near the cleats. The bilge pump's spout and handle point at the player who placed it; it pumps
    as before. Brig bars: alone a capped post; in a row, an L, a T, a cross and next to a stone wall the rails and bars
    continue across blocks, nothing dark or see-through; on a ship they move with the hull. Brig door, left and right
    hinge, each facing: closed, the hinge straps are on the hinge edge and the lock plate on the free edge; open, the
    leaf lies along the hinge edge with the straps at the hinge corner; locked (sneak-use), a brass padlock on the
    lower half on the side you placed it from, staying on that face when the owner opens it. The door item is still
    the flat wooden sprite (known).
11. **Cannon and harbor master's desk (F7f):** the cannon faces the way you placed it with about 2.5 px of muzzle past
    the block; powder in shows a rammer leaning on the barrel, loaded shows a grey ball in the bore, firing clears
    both; loading, aiming and firing still work, also on a moving ship. The desk's bell faces you, the ledger and
    inkwell the far side, the quill does not clip the block above; the market screen still opens when bound. Both
    items fit their slots; the cannon may look large in hand (block defaults).
12. **All of them:** break each block; the particles match the block's wood or material, never the missing texture.
   `latest.log` has no model or texture warnings mentioning `pirates_n_ships`. On a moving ship the models render with
   correct lighting.

## 3D item models (F8a swords; F8b firearms, ammunition, grappling hook; F8c whistle, shackles; F8d provisions; F8e trade goods)
Compare each sword with a vanilla iron sword in the other hand or the next hotbar slot. Renders in `art/renders/`.
1. **First person, right hand:** the grip sits in the fist, blade up and forward at the iron sword's angle. The rapier
   is visibly longer and thin, the cutlass short and broad with a brass basket, the saber curved with the tip bending
   away from the edge.
2. **Third person (F5, or another player):** grip in the hand, knuckle bow or basket on the underside over the fingers,
   edge down; the curved tips of cutlass and saber rise forward.
3. **Offhand (F), first and third person:** mirrored correctly, nothing floats outside the hand.
4. **Attack swing:** nothing pops or clips oddly; the rapier tip may reach further than the iron sword's.
5. **GUI (hotbar, inventory, creative tab):** the diagonal matches the iron sword's footprint; the rapier is slightly
   scaled down and must not overflow the slot; flat lighting, no dark faces; brass and steel read clearly.
6. **Dropped on the ground:** about half size, bobbing like the iron sword, no dark or missing faces.
7. **Item frame:** flat and centred. The rapier's tip sticks about 2 px out of the frame (vanilla frame scale); say
   whether that bothers you.
8. **Texture:** no missing-texture faces; the rapier grip shows light and dark wire stripes.
9. **Pistol and musket, GUI:** diagonal, grip hanging down-right, hammer on top; the musket fills the slot's diagonal
   without being clipped. No missing textures.
10. **Guns, first person:** the barrel points forward toward the crosshair and a little up, grip down at the lower
    right, hammer on top. Offhand: a mirror image at the lower left. Say whether they feel too big or too small.
11. **Guns, third person (F5, front and side):** grip in the fist, barrel horizontal to slightly up and pointing where
    the player faces; the musket's butt runs alongside the arm and hip and does not stick out of the back. Also while
    looking up and down. Offhand mirrored.
12. **Lead shot, cannonball, grappling hook:** in the GUI a low pile of grey balls, a dark octagonal ball with a light
    spot at the upper left, and an upright hook with eye, rope coil and four flukes. Held like vanilla flat items,
    fully visible; dropped at half size; the same as in the GUI in an item frame.
13. **Lighting:** the guns are not too dark in shade. Gunmetal and cast iron are dark on purpose; report if they read
    as black.
14. **Captain's whistle and shackles, GUI:** a brass tube from lower left to upper right with a ball at the top, a steel
    ring and a pale lanyard loop below; two dark iron cuffs with lighter upper-left rims joined by a short chain. Held
    like vanilla flat items in first and third person and the offhand, small and turning on the ground, flat in an
    item frame. Using the whistle still opens the radial menu; shackles still work on a villager. Watch for z-fighting
    on the whistle's black hole and the keyholes at a distance.
15. **Provisions (rum, hardtack, lime, salt pork, salted fish), GUI:** an upright green bottle with cork, pale label
    with a dark stripe and a brown liquid band; a tan square biscuit with a 4×4 grid of holes and a darker front edge;
    a green ball with stem, leaf and a light patch; a pink-and-white layered slab with a brown rind and white specks;
    a blue-grey fish on the diagonal, head upper right. None cut off or too dark (the salt pork front and the hardtack
    edge are the likeliest to look dull). Held like vanilla food in first and third person and the offhand; small and
    bobbing on the ground; centred in an item frame. Eating and drinking still work with their effects, the eating
    particles show tans, pinks, greens and blue-grey (never magenta or black), rum still counts as a drink and the
    pantry still accepts the provisions.
16a. **Doubloon and bounty proof (F8f):** in the inventory a tilted gold stack of three coins with a raised cross and a
    darker rim, centred in the slot; in hand, on the ground and in a frame the stack stands upright with the face out.
    The market screen's header icon is the 3D stack. The bounty proof is a diagonal scroll with a red seal and ribbon
    tails, held stick-like. Say if the cross on the coin is too low-contrast in the GUI.
16. **Trade goods (cloth, spices, tobacco), GUI:** an off-white bolt on the diagonal with brown core stubs and a flap
    to the lower right; a brown sack with a dark cord and knot and an orange, red and gold heap; a fan of brown leaves
    from lower left to upper right with tan twine. Say whether the spice sack reads too plain and whether the middle
    tobacco leaves blend together. Held like the food items in first and third person and the offhand; floating at
    half size on the ground; centred in an item frame. Selling at a test market and the cargo containers still work
    and show the 3D icons.

## Known placeholders (for the later art pass)
- Every block and item of the mod now has a Blockbench model except the hull patch and the brig door item (F8g).
- The yard and stay cloths are flat grids with a simple belly; the cleat is a placeholder element model (F7e).
- Only the doubloon, the bounty proof and the hull patch still use sprites (by decision).
- The brig bars' transparency relies on a model field that only NeoForge reads. The Fabric port has to register a
  render layer for them.
