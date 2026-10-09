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
   (plus the helm). There is no Test Block any more (removed in CT1).
   - **Expected:** the tab's icon is the officer's bicorne (black two-cornered hat with a gold edge), not a blank or
     a boat. Every entry has a readable 16×16 texture (no purple-black checkerboard) and an English name, not a
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
8. **Figureheads (skull, eagle, lion, mermaid):** click the east, south, west and north faces of a hull block: the
   plate sits on the hull and the figure looks at you, even if you look sideways while clicking; on a deck top it
   faces you. Against a planks block the
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
- Every block of the mod has a Blockbench model (the hull patch since ART1a); the rope and chart items got theirs in ART1a.
- The yard and stay cloths are flat grids with a simple belly; the cleat is a placeholder element model (F7e).
- The three flag items and the map tile got their models in ART1b. Still flat (ART1c): kraken beak and ink, the toolkit items.
- The brig bars' transparency relies on a model field that only NeoForge reads. The Fabric port has to register a
  render layer for them.

17. **Brig door and brig key items (F8g, key rebuilt in ART10):** in the inventory the door is an upright iron cell
    door filling the slot height, seen slightly from the front-left (hinge knuckles left, lock plate right). Door in
    the right hand (F5): upright outside the arm, face sideways, not cutting through the leg while walking; left hand
    mirrored. First person: a small upright door at the lower right. Door on the ground about half a block tall, in an
    item frame centred and upright. Hold the door item next to a placed door: same textures. Breaking a door still
    gives anvil particles.
    **Key (ART10):** the key reads as round and deep, not as a flat cut-out, in the slot and in the hand. In the
    inventory: a dark iron key on the diagonal (bow lower-left, bit upper-right) seen three-quarter, so the edge of the
    ring bow and the side of the bit show; the bow is a ring with a clear hole, the three wards are separate, the
    lighter collar and bead rings show on the shank, and nothing pokes out of the slot. Third person: bow in the
    fist, shank forward and a little down, wards hanging down; left hand mirrored. First person: the key at the
    lower right, shank pointing into the screen and slightly left, the bow's ring face towards you. Turn the camera
    around a dropped key and a key in an item frame (bit upper-right): every part has volume, no face flickers.

Addendum (F8h): pistol and musket in the hand: see `firearms.md`.

18. **Doubloon (F8i):** one gold coin in the inventory, tilted so its edge shows at the lower left, cross on the front, about 11 px of the slot, the stack count readable; in third person flat at the fingertips with the cross up; in first person upright in the lower corner; on the ground flat with the cross up (say if it touches the ground at the bottom of the bob); in an item frame the cross faces out; the market screen's coin icon reads as a single coin. Faint seam lines across the field are a known risk at a distance.

19. **Z-fighting (V1):** water barrel (all fills), loaded cannon and helm from 10 and 30 blocks while walking sideways: no shimmer on rim corners, hoops, the water surface, the muzzle face or the helm's ring joints; the black bore ring still shows. Walk past every hand-made block at 10 and 30 blocks: no flickering patches, the desk's ledger lines, the sea chest keyhole and the crate marks still visible, no see-through hairline gaps at joints. Every item in hand, GUI and frame: no flicker on rings, rolls or joints.

20. **Hull patch, rope and chart (ART1a):** renders `art/renders/hull_patch.png`, `hull_patch_item.png`, `rope.png`,
    `chart.png`.
    - **Hull patch block:** patch a breach in a ship's hull (or place a few patches in a plank wall). Each side shows
      two spruce boards over the dark tarred planks, a black pitch seam round them and between them, a dark diagonal
      batten from top left to bottom right, and dark nail heads at the board ends and along the batten; top and bottom
      are plain dark planks. Check from **outside and inside the hull**: the patch reads the same from both sides. The
      panel stands about 1.5 px proud of the block on purpose (it is "nailed on"): walking against it, collision is
      still a plain full block, and water still stays out. Next to other hull blocks no board pokes visibly into the
      neighbour, and no face of the neighbour goes missing (see-through gaps).
    - **Hull patch item:** in the inventory a small plank panel seen slightly from the left, batten and nails in front;
      in the right hand (F5) an upright board held at the side like a small shield, the boards facing outwards, not
      cutting through the leg while walking; left hand mirrored; first person a board at the lower right; on the ground
      a small panel; in an item frame the batten side faces out.
    - **Rope:** in the inventory a coil of three turns (a striped front turn and two darker turns peeking out at the
      upper left) with a loose end and a pale whipping hanging down at the lower right; held like a tool (coil in the
      fist, the loose end pointing forward and up; say if it should hang down instead); on the ground and in a frame
      the coil stands upright.
    - **Chart:** in the inventory a parchment roll on the diagonal with a dark cord tie and knot near the lower end and
      a loose sheet standing off the upper end with a black coastline and a red mark; held like a scroll (the bounty
      proof's pose); on the ground and in a frame the same diagonal roll. Opening the chart by use still works.
    - **Z-fighting:** no flicker on the board edges, the pitch seam, the batten, the nail heads, the rope turns or the
      ink on the chart sheet at 10 and 30 blocks (patch) or in hand, GUI and frame (items).

21. **Flag bundles and map tile (ART1b):** renders `art/renders/navy_flag.png`, `merchant_flag.png`,
    `jolly_roger_flag.png`, `map_tile.png` (three-quarter view, GUI, and for navy and map tile the right hand).
    - **Flags in the inventory:** each flag is a rolled cloth bundle standing **upright** in the slot: a wooden toggle
      with a dark knob at each end runs through it, a pale rope eye on top, two dark lashings near the ends, a lighter
      outer fold round the middle and its loose end sticking out to the right. Navy: blue with a white cross (a
      horizontal band all round and a vertical arm on the front, off-centre like the flown flag). Merchant: off-white
      with a red band. Jolly Roger: black with a bone skull (dark eye holes) and a bone below it. The three tell apart
      at a glance in a hotbar row; say if the bundle looks too much like a tin can or a spool.
    - **Flags in the hand:** held like a tool (right hand, F5): the bundle in the fist, the rope eye pointing forward,
      the outer fold and its design on top; left hand mirrored; first person at the lower right. On the ground a
      small bundle on the diagonal; in an item frame the design side faces out (on the diagonal, like a sword).
      Hoisting a flag on a flagpole still works and the flown cloth is unchanged.
    - **Map tile:** in the inventory a square wooden frame with dark nail heads in the corners, seen slightly from
      above-right (the top edge and right side of the frame show), holding a parchment with land on the left (a red X
      and a dotted route of three ink dots), a dark ink coastline and blue sea on the right with an olive island and two
      darker shoals. In the right hand it is held flat like a tray, map up; first person turned towards the player; on
      the ground it lies flat, map up; in an item frame the map faces out. Placing the tile on a wall or floor still
      shows the old flat block model with the drawn map (only the item changed).
    - **Z-fighting:** no flicker on the cross arms, the red band, the skull and its eye holes, the lashings, the coast
      line, the island, the X or the nail heads in hand, GUI and frame.

22. **Kraken drops, shipwright tools and the hook-loaded musket (ART1c):** renders `art/renders/kraken_beak.png`,
    `kraken_ink.png`, `carpenters_hammer.png`, `saw.png`, `nails.png`, `shipwright_toolkit.png`, `musket_hook.png`
    (front as in the GUI, two three-quarter views, side). Give yourself each item (`/give @s pirates_n_ships:<name>`).
    - **Kraken beak:** in the inventory a curved beak on the diagonal: a broad dark-brown horn base at the lower left
      with a pale tan rim round its open end, curling over into a black hook whose point turns down to the right,
      a smaller lower jaw tucked under the hook. Held like a tool (base in the fist, hook forward and up); left hand
      mirrored; on the ground a small beak on the diagonal; in a frame the same diagonal. Say if it reads as a claw
      rather than a beak.
    - **Kraken ink:** in the inventory an upright, bulging blue-black sac about 13 px tall with a pale gloss streak
      and dot on the upper left, a faint dark-blue sheen on the right, a gathered neck tied with a brown cord whose end
      hangs to the right. Held in the fist like the whistle (small, upright); on the ground small and upright; in a
      frame upright. The map board still accepts it as ink.
    - **Carpenter's hammer:** in the inventory a claw hammer on the diagonal: wooden handle from the lower left, a dark
      iron head across the top with a bright striking face at the upper left and a split claw bending back down at the
      right. Held like a vanilla axe (handle in the fist, striking face forward/down); first person at the lower right.
    - **Saw:** in the inventory a hand saw on the diagonal: a closed walnut handle with a finger hole and two brass
      screws at the lower left, a steel blade narrowing towards the upper right with small dark teeth along its
      upper-left edge and a bright back edge. Held like a tool (handle in the fist, teeth down).
    - **Nails:** in the inventory a small bundle of seven nails on the diagonal (heads at the upper right, points at the
      lower left), two pale twine wraps round the middle with a brown knot and two short ends on the front. Held in the
      fist (small, like the whistle); on the ground small; in a frame on the diagonal.
    - **Shipwright's Toolkit:** in the inventory an opened leather tool roll on the diagonal: the rolled part with a
      dark strap and brass buckle along the lower right, the dark inner side of the flap at the upper left with a
      lighter pocket band (two stitch lines) across its lower end, and out of the pockets a small hammer (iron head at
      the top), a steel saw blade with teeth and three nail heads. Held like a tool (the roll's lower end in the fist).
      Say if the tools are too small to read in the hotbar.
    - **Hook-loaded musket:** load a grappling hook into a musket (hook in the off hand, musket in the main hand, as in
      `grapple.md` GR3). While loaded, the inventory icon and the hand show the cocked musket with the hook at the
      muzzle: a short shank in the barrel, a crossbar just ahead of the muzzle and two flukes bent back along the barrel
      on either side. Check first person (the hook at the far end of the barrel, not floating off it) and third person
      F5 from the side and front (the hook sits where the barrel ends, the gun is held exactly as the ball-loaded
      musket). Firing or unloading switches back to the plain musket.
    - **Z-fighting:** no flicker on the beak's rims, the ink gloss, the hammer face, the saw teeth and screws, the
      twine wraps, the toolkit's pockets, stitches and buckle, or the hook's crossbar in hand, GUI and frame.

23. **Ship decor (ART2): lantern, bell, rope coil, stern window, chart table, sea cot.** Renders
    `art/renders/ship_lantern.png`, `ships_bell.png`, `rope_coil.png`, `stern_window.png`, `chart_table.png`,
    `sea_cot.png`. All six are in the creative tab; craft each once (recipes in the recipe book: lantern + 2 gold
    nuggets; bell: 3 sticks / gold ingot / gold, iron nugget, gold; 4 rope -> coil; 2 windows from dark oak planks, glass
    panes and a gold nugget; chart table: map over planks over sticks; cot: planks and wool over two sticks).
    - **Ship's lantern:** click the top of a block (stands on the deck), the underside of a beam or a fence-less ceiling
      (hangs from a brass rose on a hook), the side of a wall (hangs from a brass arm with a diagonal brace, wall block
      behind). Turn it each way: the bracket stays on the wall. Light level 14 at night (compare a vanilla lantern next
      to it). Place one in water: it stays waterlogged, no air pocket. Break the block it hangs on: it drops. Check the
      glass sides (the vanilla lantern glow) have no dark or see-through pixels and the frame posts sit on the corners.
    - **Ship's bell:** on top of a fence post (a small belfry frame, the beam across) and on a wall (bracket and brace).
      Right-click: vanilla bell sound, and the bell swings for about a second (lip out away from the wall), the clapper
      and the lanyard stay hanging straight, then it snaps back. Spam-click: it keeps ringing, no stuck swing. Say if the
      snap back looks too abrupt (it is a two-model swing, no animation).
    - **Rope coil:** place one on deck, then right-click its top with more coils: 2, 3, 4 coils stack, the loose end on
      the top coil runs towards where you stood and drops down the side to the deck. A fifth does nothing. Break a stack
      of four: four coils drop. Waterloggable. Say if the stacked coils read as rope or as a barrel.
    - **Stern window:** build it into a wall from outside: the sill, hood and casing face you, four glass panes (you can
      see through them, cutout like vanilla glass, no black pixels) set near the outside, the window stool inside the
      cabin. Right-click: dark oak shutters with iron straps close on the outside, again to open; trapdoor sounds. Put
      several side by side and above each other: the frames line up, no gaps, no flicker where hoods meet.
    - **Chart table:** the top overhangs the block by a pixel on each side with fiddle rails; a chart turned slightly with
      ink coastlines and a red course line, a pale blue glass weight, brass dividers, an inkwell with a quill. Walk
      round it at 10 blocks: no flicker on the ink or the chart. The drawer side faces you when placed.
    - **Sea cot:** placed like a bed (foot where you click, head away from you): a wooden box bed with taller head posts,
      a pillow and turned-down sheet at the head, a red blanket. Break either half: one cot drops and both halves go.
      At night on land, right-click: you sleep and the spawn is set like a bed; the sleeping player lies on the blanket
      (say how far above it). In the Nether it explodes like a bed (vanilla). In the inventory the item shows the whole
      cot.
    - **On a ship:** put all six on a deck, assemble, sail a little: they ride along with their states (stacked coils,
      closed shutters, a ringing bell), the lantern still lights the deck at night (say if the light moves with the
      ship or stays behind), the window glass is still see-through on the moving ship. Right-click the cot on the ship:
      "You can't sleep in a cot on a ship under way". Ring the bell on the ship. Disassemble: everything is back on the
      grid where it was.
    - **Config:** `ship_decor.sea_cot_sleeping` off: the cot says "This cot is just for show"; `ship_decor.bell_ring_ticks`
      changes how long the bell swings.

18. **Captain's Hat and Officer's Coat (ART6).** `/give @s pirates_n_ships:captains_hat` and
    `/give @s pirates_n_ships:officers_coat` (the coat also crafts: blue wool around, a gold ingot top middle, white
    wool in the centre; the hat only drops from a pirate captain). Renders: `art/renders/captains_hat.png`,
    `art/renders/officers_coat.png` (item) and `art/renders/officers_coat_worn.png` (worn).
    - **GUI:** the hat as a 3D black hat seen from the front three-quarter with the plume on top, fitting the slot like
      the other hats; the coat flat facing you: blue coat with white lapels and gold buttons, white waistcoat, gold
      epaulettes, sleeves hanging out at an angle with red cuffs ringed in gold, a gold hem. Both have English names
      ("Captain's Hat", "Officer's Coat"); neither shows a checkerboard.
    - **In hand:** the hat held like the other hats (small, tilted); the coat held like a flat item (cloth, map) in first
      and third person and the offhand. On the ground both bob at half size; in an item frame both face out, centred.
    - **Worn, hat:** right-click with the hat (or shift-click into the helmet slot): it goes on the head, the old helmet
      comes back to the hand; tooltip "+1 Armor When on Head". In F5 it sits like the captain's (brim ~2 px above the
      eyes, cocked brim and plume on **your** left). Stand next to a spawned captain and compare height and width.
    - **Worn, coat:** right-click with the coat (or shift-click into the chestplate slot): it goes on the chest, a worn
      chestplate comes back to the hand; tooltip "+3 Armor When on Body". F5 front: blue coat over the body and both
      arms, white lapels and waistcoat down the middle with gold buttons, gold epaulettes on top of the shoulders, red
      cuffs with a gold ring at the wrists, a gold band round the waist. Back: blue with a darker vent and two gold
      buttons. It is vanilla armour-style (a shell 1 px off the body), so it ends at the waist: no tails (a known limit,
      say whether you want tails through a custom armour model later).
    - **On others:** an armour stand and a zombie wear both (`/item replace entity @e[type=zombie,limit=1] armor.chest
      with pirates_n_ships:officers_coat`). The left arm's sleeve mirrors the right arm's pixels (vanilla).
    - **Config:** `apparel.officers_coat_armor = 0` after a restart: no tooltip line, no armour; `= 8`: "+8 Armor".
      The coat never loses durability (no bar).

## Coat tails and the captain's clothing (ART9)

`/give @s pirates_n_ships:officers_coat`, `/give @s pirates_n_ships:captains_coat`,
`/give @s pirates_n_ships:captains_breeches`, `/give @s pirates_n_ships:captains_boots`,
`/give @s pirates_n_ships:captains_hat`. Renders (not the game): `art/renders/coat_tails.png`,
`art/renders/captains_set_worn.png`, `art/renders/captains_clothing.png`. Watch the log for a missing model layer
(`pirates_n_ships:coat_armor#main`) or a crash when a coat is first drawn: that would be the client hook failing.
1. **Officer's coat tails, standing (F5):** the coat as in ART6 step 18, plus two knee-long navy tails hanging from the
   waist at the back and both sides, split down the middle of the back with gold edges along the split, a gold hem,
   white lining on their inside, gold buttons at the top of each. They follow the body: turn and look around, they stay
   on the hips. Nothing pokes through the legs.
2. **Walking and sprinting:** each tail swings back with the leg on its side and falls back as the leg comes forward.
   Say if the swing looks too far (at a sprint they lift noticeably), too jerky, or whether you prefer rigid tails;
   say if a leg pokes through a tail at a normal walk (a known limit only at extreme strides, e.g. when falling).
3. **Sneaking:** the tails hang nearly straight down behind the legs instead of sticking out with the leaning body.
4. **Swimming, riding a boat or horse, sleeping in a hammock:** the tails go with the body; say if anything looks broken.
5. **On others:** an armour stand and a zombie wear the coat with tails (`/item replace entity
   @e[type=zombie,limit=1] armor.chest with pirates_n_ships:officers_coat`); a baby zombie wears small tails at the
   right place. An enchanted coat (`/give @s pirates_n_ships:officers_coat[enchantments={levels:{"minecraft:protection":1}}]`)
   shows the glint on the tails too.
6. **Captain's set on you beside a spawned captain** (`/summon pirates_n_ships:pirate_captain` or one at an island):
   wear hat, coat, breeches and boots. Compare: charcoal coat edged in gold, brocade waistcoat with the white jabot in
   the open front, brass buttons, a red sash round the waist with its knot on **your** left hip, a leather baldric from
   your right shoulder across to the left hip (front and back), wide crimson cuffs ringed in gold at the wrists,
   knee-long tails with crimson lining; dark breeches to the knee; tall dark boots with turned-down leather tops and a
   strap with a brass buckle. Say where the colours differ from the captain's.
7. **Slots and armour:** each piece goes into its own slot by right-click (the old piece comes back to the hand) and by
   shift-click; tooltips "+3 Armor When on Body", "+2 Armor When on Legs", "+1 Armor When on Feet"; the armour bar
   shows 6 with the three on (plus 1 with the hat). No durability bar. Config `apparel.captains_coat_armor`,
   `captains_breeches_armor`, `captains_boots_armor` = 0 after a restart: no tooltip line.
8. **Without boots:** the breeches end below the knee in white stockings. Boots without breeches: your own legs show
   above the boot tops.
9. **GUI and ground:** the three items flat facing you in the slot (coat like the officer's coat in charcoal; breeches
   with the waist band and stockings; a pair of boots seen from the side, toes left), English names "Captain's Coat",
   "Captain's Breeches", "Captain's Boots", no checkerboard; held like flat items; on the ground at half size; in an
   item frame facing out.
10. **Drops:** kill a few captains (`mobs.captain.clothing_drop_chance` 0.35 by default): he always drops his hat and
   sometimes one or more pieces; with the value at 1 he drops all three, at 0 none. The recipes show in the recipe book
   (coat: black wool with a gold ingot top middle, red wool centre, leather bottom middle; breeches: black wool
   leggings shape with leather top middle; boots: black wool over leather).

## Paddle (ART8)

Needs SC2's `pirates_n_ships:paddle` item merged; `/give @s pirates_n_ships:paddle`.
- **Inventory:** a 3D paddle lying diagonally like a tool, T-grip lower left, blade upper right with a raised centre
  rib, a twine whipping at the throat and two dark leather wraps on the shaft. No purple-black missing-model cube, no
  missing-model warning in the log.
- **Third person (F5), right and left hand:** held like a sword or shovel, the fist on the lower wrap just above the
  T-grip, the blade up and forward past the shoulder, a little larger than a sword (scale 0.95). Say if it should be
  held lower on the shaft or be longer.
- **First person:** the blade reaches up and out of the right of the screen, the grip in the hand; off hand mirrored.
- **Dropped and in an item frame:** it lies at half size on the ground; in a frame it fills the frame diagonally.
- **On the sea chest:** sit on a floating sea chest with the paddle in hand and paddle: the paddle stays in the hand
  pose above (no rowing animation is part of ART8).

## Ratlines (RL1)

`/give @s pirates_n_ships:ratlines 64` (or craft: string across the middle row, a stick in each corner, gives 4).
Render: `art/renders/ratlines.png`.
- **Look:** two vertical shrouds with four horizontal ratlines 4 px apart, a knot at each crossing, all rope-coloured.
  A hung net sits against the mast side; stacked nets meet without a seam. A sloped net is the same net at 45 degrees,
  ratlines on the upper side; a run of sloped nets is one straight diagonal. In hand and in the inventory: the net flat.
- **Hung on a mast:** build a mast of oak logs and one of spruce fence posts on land. Click the side of each mast: the
  net hangs on that side facing you. Click the net again (not sneaking): the next net goes straight above it, up to the
  top of the mast; above the top nothing is placed. Break the mast block behind a net: the net drops as an item.
- **Sloped from the deck:** stand on a floor facing a mast four blocks away, click the top of the floor block in front
  of you: a sloped net rising toward the mast. Click it three more times: the run grows one up and one forward each
  time and its top touches the mast. Check that the slope rises **away from you** and the low edge sits on the floor.
  Break the floor block under the first net: the run falls net by net within a moment, except the top one leaning on
  the mast.
- **Climbing:** walk into a hung net and hold forward (or jump): you climb like a ladder; sneak to hold on. Walk up a
  sloped run: you step up its ratlines like a stair; no fall damage when you drop off a net. Mobs can climb too.
- **On a yard (RL1b):** build a square sail on land (a mast, a yard of five at the bottom, one of five four blocks
  higher, the mast through both). Click the end of each yard: a net hangs on it. Click the fore or aft side of either
  yard: nothing is placed, the item stays in your hand and the action bar says "The sail's cloth hangs here: hang the
  ratlines on the yard's end". Take the lower yard away and click the upper yard's side again: now the net hangs
  there. With nets on all four yard ends, click the upper yard: the sail still sets (half, full, furled). Lay a sloped
  run toward a yard end beside the mast so its last net sits on the yard: break the net below it, the top net stays.
  Lay one rising into the side of a crow's nest: its top net leans on the nest.
- **On a ship:** build both kinds on a hull with a mast, assemble at the helm, sail a little. Both stay on the ship,
  turn with it, and both still climb as above while the ship moves (GameTests prove it on a ship lying still; check
  under way). Disassemble: both are back in the world in the right orientation.
- **Water:** place a net in water (against a log under water): it holds the water, no air pocket; breaking it leaves
  the water.
- **Config:** `rigging.ratlines_enabled = false`: the item places nothing and says "Ratlines are disabled on this
  server"; nets already placed stay and climb.
- Say whether the net should be denser (more ratlines), thinner or a different colour, and whether walking up the
  sloped run feels right.

## Railings (RAIL1)

`/give @s pirates_n_ships:oak_railing 64`, `/give @s pirates_n_ships:oak_railing_port 16` (or craft: three sticks over
three oak planks gives 4 railings; two sticks in the top corners over three oak planks gives 2 ports). Render:
`art/renders/railing.png` (an edge run with its end posts, a corner, the port with a cannon behind it, a swivel gun on
the rail), `art/renders/railing_item.png`.
- **Item:** in the hotbar and the inventory a short rail between two posts; in hand it reads as a railing.
- **Along the gunwale:** on a hull, stand on deck and click the top of the deck block next to the side, close to the
  side: the rail sits on the outer edge of the block (2 px in from the side), not in the middle. Lay five in a row:
  one straight rail, a post only at each end, the balusters evenly spaced across the joints.
- **Centre click:** click the middle of a deck block: the rail goes on the side you face.
- **Corner:** at the end of the run click the last railing again near its other side: it gets a second edge and one
  corner post; the item count drops by one. Rail the next block along the new side: the run continues with no post at
  the joint.
- **One-block gap:** leave one block out of a run: a post on both sides of the gap. Put a port in the gap: the railings
  on both sides end against the port's posts (no double posts).
- **Hatch surround:** around a 1 x 1 hatch rail the side of each of the four blocks that faces the hatch: four short
  rails with posts at their ends (two posts meet diagonally at each corner of the hatch). Also try a block railed on
  three or four edges.
- **Port and cannon:** put a cannon on the deck right behind a port, facing out. Aim it through every elevation
  (sneak-use) and fire each time: the shot leaves cleanly, the port stays. Compare with a plain railing in front of a
  cannon: only at the lowest elevation (-5 degrees) does the ball hit (and break) the railing; level and raised it flies
  out over or past it (the muzzle is already beyond the rail). Say whether that is fine or the railing should block more.
- **Gangway from the port:** moor next to a pier level with the gunwale, stand on deck behind the port facing out and
  use a boarding plank on the port's sill: the plank starts in the block beyond the port, at deck height (not a block
  up), and reaches the pier. Walk out through the port over the sill and along the plank.
- **Swivel gun on the rail:** place a swivel gun on a railing: it sits on the rail at the edge, not floating in the
  middle of the block; aim and fire it, the shot leaves from the muzzle you see. On a corner railing click near either
  side: it takes that edge. Break the railing: the gun drops. A swivel gun on a fence still sits in the middle. Order a
  crew member to it: they stand on the deck inside the railing.
- **Ratlines:** click the top of a railing facing the mast: a sloped ratline rises from the rail; click the railing's
  side: a net hangs on it.
- **Collision:** try to jump over a railing: you cannot (1.5 blocks). Set `ship_decor.railing_collision_blocks = 1.0`
  and place or change a railing: now you can vault it. Crew and mobs walk around railings, never through.
- **On a ship:** assemble a railed hull and sail: the railings stay, ride level, the ship floats like with fences.
  Disassemble: they come back with their edges and posts.
- **Water:** place a railing in water: it holds the water. **Config:** `ship_decor.railings_enabled = false`: railings
  and ports place nothing and the action bar says "Railings are disabled on this server".
- Say whether the rails should be thicker, the posts taller, or the balusters closer.

### Railing gate, hanging things, the hook on a rail (RAIL3)

`/give @s pirates_n_ships:oak_railing_gate 4` (or craft: stick, iron nugget, stick over three oak planks gives 1),
`/give @s lantern 4`, `/give @s chain 8`. No render yet (models from `tools/gen_railing_models.py`, not yet looked at
in Blockbench).
- **Gate in a run:** lay three railings along a deck edge with a one-block gap, and place the gate in the gap
  (click the deck near the edge, in the half where the hinge should go). The railings either side lose their post
  against the gate: no double posts. Closed it looks like a railing section; two dark iron straps sit on the hinge
  post's end, a latch with a handle on the inboard side at the other end. Click the right half of the edge for the
  other hinge: the straps change sides.
- **Swing:** use the gate: it swings 90 degrees outboard (over the side) with the fence gate's creak, use again: it
  shuts with the fence gate's clap. Put a lever or redstone block next to it: it opens, take the signal away: it shuts.
  Say whether the leaf should swing inboard instead.
- **Collision:** closed, you cannot walk or jump through (1.5 blocks like the railing); open, you walk through over
  the low sill as through a port, on land and aboard a ship.
- **Crew:** with the gate open, order a crew member to a spot on the far side: they walk through the gate. Close it
  and order again: they go around (or stay).
- **Plank:** with a ship alongside, open the gate, hold a boarding plank, face out and click the gate's sill: the
  gangway starts in the block beyond the gate at deck height (the gate does not swing shut from the click). Close the
  gate and try again: the plank goes from the gate's top like from a railing (a block higher) or is refused.
- **Lantern under a post:** build a railing on the edge of an overhang (a balcony or a stern gallery with air below
  the railing block) and look up at its end post's block from below with a lantern: it hangs below the block. A lantern
  also stands on top of a post block, and a soul lantern hangs the same way. Under or on a plain stretch of rail (the
  middle of a run, no post) the lantern does not go. The lantern hangs at the middle of the block, not right under the
  post: say whether that looks wrong.
- **Chain over a post:** place a chain on top of a post block and another under it, hang a lantern from the lower
  chain: everything holds; break the railing: a lantern hanging straight from it drops.
- **Hook on the rail:** from your ship throw or fire the grappling hook at the railing of another ship: it bites the
  rail (the latch sound), and the ships haul together as with a hook on the hull. Walk away from it: the rope holds
  twice as far as on a plain block (as on a cleat).
- **Tearing it out:** set `grapple.railing_break_force = 50` and hook another ship's railing from your ship: the haul
  tears that railing out at once (it breaks and drops), the hook comes back to you. With the default (180) a ship's haul
  never tears it; hook a railing from the shore, sneak and walk away hard (a hand haul up to 200): the railing should
  tear out. Say whether 180 feels right. `grapple.latches_hold = false`: the hook on a railing holds like on any block.

## Ratline nets (RL2)

`/give @s pirates_n_ships:chainplate 8`, `/give @s pirates_n_ships:mast_hoop 8`, `/give @s pirates_n_ships:ratline_net 8`
(or craft them, see `docs/guide.md` "Ratline nets"). Renders: `art/renders/chainplate.png`, `mast_hoop.png`,
`ratline_net.png`.
- **Look:** the chainplate is a dark iron strap with two bolts and a grey eye ring standing on it; on the side of a
  block the strap lies on the side and the ring sticks out. The hoop is a dark band round the mast block with a grey
  ring standing out of the face you clicked. The net item is a rolled net with two stick ends and a loose flap; check
  it in the hotbar, in hand (first and third person), on the ground and in an item frame.
- **String a net at the rail:** on a hull, put a chainplate on top of the gunwale and a mast of logs about four blocks
  inboard; put a hoop on the mast's side facing the chainplate six blocks above the deck. Use the net on the chainplate:
  "Net tied: now use the other fitting", the net glints. Use it on the hoop: "Net strung through N blocks", one net used.
  Expected: a rope net with two side ropes and a rung every half block runs from ring to ring, narrowing into each ring;
  the rope texture is not stretched.
- **Climb it:** walk from the chainplate into the net and hold forward (no jump): you climb along the net's slope up to
  the hoop at about ladder speed and do not fall out; release: you slide down slowly along it; back: you climb down;
  sneak: you hold still. Jump instead of forward also climbs. Say how it feels at the slope you got (the action bar
  does not show it; about 55° for this layout).
- **Vertical:** a chainplate on the deck right below a hoop on the mast face five blocks up: the net is straight; climb
  it with forward.
- **Refusals:** a chainplate ten blocks from the mast and a hoop two blocks up (flatter than 30°): "Too flat: N°, at
  least 30°", the net stays in hand and still remembers the first fitting. A block in the line: "Blocked at x y z"
  naming that block's position. A chainplate on land and a hoop on a ship: "Both ends on one ship or on land". A
  fitting that already holds a net: "That fitting already holds a net". A net longer than 32 blocks (e.g. a hoop 34 blocks above the chainplate): "Too long: 33.2 of 32 blocks"; with `rigging.net_max_length = 12` a 14 block net: "Too long: 14.2 of 12 blocks". A tall mast: a chainplate at the rail and a hoop 20 blocks up and 8 in strings and climbs to the top.
- **Break a cell:** aim into the middle of a net (a small box outline shows) and break it: the whole net vanishes and
  one ratline net drops at the chainplate (the first fitting you used); both fittings stay. Restring it, then place a
  plank into the net, and then break the hoop: each time the net comes down and drops once.
- **Sail with it:** string a net on a hull, assemble at the helm and sail: the net moves with the ship, no gap or lag
  between the drawn net and the fittings; climb it while the ship lies still and while it sails (the assist works in the
  ship's frame, check it does not throw you off when the ship turns or rolls). Disassemble turned: the net is back in
  the world in the right place and climbable.
- **Climb under way (RL3):** sail fast (all sail, running before the wind) and walk from the deck into the net, hold
  forward: you climb to the hoop and stay on the net, you do not drift aft or off it; do it again while the ship turns
  hard at the helm, again after jumping onto the net from the deck, and once climbing on from the water at the side.
  Hold still (sneak) halfway up for a few seconds while it sails and turns: you stay on the same spot of the net.
- **Off the top (RL3):** end a mast at the hoop's height and put a crow's nest on it (the hoop one block below the
  nest's floor); climb with forward: at the hoop you rise a little over it and forward walks you into the nest, you do
  not hang stuck at the hoop. Walk back out of the nest over the hoop: the net catches you; let go and you slide down
  it to the chainplate, or hold back to climb down. Try it on a vertical net and on a sloped one.
- **Water:** string a net with its lower part under water: the cells hold the water, no air pocket.
- **Config:** `rigging.nets_enabled = false`: "Ratline nets are disabled", nothing tied. `rigging.net_assist = false`:
  forward alone no longer climbs a diagonal net; jump and forward together still do (jerkily).
- Say whether the net should be wider (it is 0.8 blocks), denser or thicker, and whether the small outline box of the
  cells gets in the way when aiming at the mast through the net.
- **Triangle (RL4):** on a hull, put three chainplates on top of the gunwale two blocks apart (e.g. three blocks of rail
  with one free block between each pair), and a hoop on the mast about five blocks up and three or four blocks inboard,
  facing the rail. String each chainplate to the hoop (three net items). Expected: after the first, "Net strung through
  N blocks"; after the second and third, the same. One net is drawn: three ropes from the chainplates meeting at the
  hoop's ring, rungs every half block running from the left rope through the middle one to the right one, the rungs
  getting shorter towards the hoop (a triangle); no separate side ropes per strand; the rope texture is not stretched
  along the rungs. Look at it from the side and from below on a ship that rolls: the triangle stays on its fittings.
- **Climb the middle:** stand on the rail between the first and second chainplate (in the netted space, not on a
  chainplate) and hold forward: you climb straight up the triangle to the hoop without falling out between the ropes;
  try it again from the other gap and from right next to the middle chainplate. Say whether the climb wobbles sideways
  while you go up (the line you follow is re-aimed at each block).
- **Break one strand:** break the middle chainplate: one ratline net drops at it; the two outer ropes stay and the
  triangle is now drawn between them only (rungs from the left rope straight to the right one) and is still climbable
  in the middle, also through the block where the middle chainplate was. String it again (the item tied on the hoop or
  the chainplate): the middle rope is back. Break a net cell in the gap between two strands (aim at the small outline
  near the rail): that gap's strand (the one on its left as you look from the hoop) drops; the rest closes up.
- **Hoop full:** string two more chainplates to the same hoop (five), then a sixth: "Hoop full: at most 5 nets" (also
  when you use the net on the full hoop first).
- **Too far apart:** a chainplate 7 or more blocks along the rail from the nearest one of the fan: "Too far apart for
  one net: 7.0" (the distance); it is drawn as its own two-rope net with nothing between it and the fan.
- **Break the hoop:** every strand drops (three nets, each at its own chainplate), nothing climbable stays.

### Boarding nets along the rail (RAIL5)

Covered by JUnit (`BoardingNetRulesTest`) and 6 GameTests (`ship/rigging/BoardingNetGameTests`: stringing and the
refusals, `rigging.boarding_nets` off, a husk pushed over the rail held for the hold then over, on an assembled hull the
ship's own crew member crossing at once while a husk beside it is held, the net surviving assembly, a cutlass cutting
it). What the tests cannot show: how the net looks, how the hold feels for a player, and a real boarding (BRD3).

Setup: a hull with a straight run of oak railings along one deck edge, two chainplates on top of two railings five
blocks apart (`/give @s pirates_n_ships:chainplate 4`, `/give @s pirates_n_ships:ratline_net 4`), a cutlass.
- **String it:** use the net on one chainplate, then the other: "Boarding net strung along the rail", one net used.
  Expected: an upright net hangs from the rail top to a block above it between the chainplates: a foot rope on the
  rail, a top rope one block up at the chainplates sagging a little in the middle, two ropes between and an upright
  every third of a block; the rope texture is not stretched. Look at it from inboard, outboard and along the rail.
- **Refusals:** chainplates on railings of two different edges (a corner, or north and south rails): "Boarding nets
  run along one rail edge"; on two neighbouring railings: "Leave a block between the chainplates"; ten blocks apart:
  "Too long: 9.0 of 8 blocks"; two chainplates on plain planks at one height: "Too flat: 0°, at least 30°" as before.
- **Held (as a player):** stand outside the ship on a block or another hull level with the rail top, survival mode,
  and walk or jump over the rail into the net. Expected: you are stopped just inside the net's outer edge and slowed
  like in half a cobweb for about a second, then you can climb or walk over the rail inboard (the net is climbable:
  hold forward or jump). Report rubber-banding or jitter (the client and the server each time the hold) and whether a
  second feels right.
- **Held (a mob):** lure a zombie over the rail from a hull lying alongside, or push one with a stick: it is held a
  second at the net, then comes over.
- **The ship's own crew:** spawn a crew member on the deck and have it go over the rail (e.g. stand it on the rail top
  outboard and push it in): it is never held. Crew leaning at the rail (RAIL5) lean through the net. With BRD3 merged:
  the ship's boarders go out over their own net and come back freely.
- **Cut it:** hit a net cell with a cutlass three times (left click, aiming at the small outline box in the net): the
  net comes down and one ratline net drops at the first chainplate you used; the chainplates stay. Bare hands, an axe or
  a pickaxe do nothing to it (it cannot be mined); placing a block into it is refused. In creative mode it breaks at
  once as before.
- **Sail with it:** string it on land, assemble, sail and turn: the net stays on the rail and keeps holding boarders
  from the outboard side; disassemble turned: it is still a boarding net on the same edge.
- **Config:** `rigging.boarding_nets = false`: the second chainplate answers "Too flat" and nets already strung hold
  nobody; `rigging.boarding_net_hold = 0`: nobody is held; `rigging.boarding_net_cut_hits = 1`: one hit cuts it.

## Ship's bell swing (BELL1)

The bell now swings about the pin of its yoke instead of flipping to a second model (replaces the swing check in step
23's ship's bell line). Render: `art/renders/ships_bell_swing.png`. Set up a bell on a fence post (placed while looking
down, so its front faces you) and one on a wall (its front faces away from the wall).
- **Look at rest:** post: the belfry frame with two iron straps hanging from the beam and a brass pin through them; the
  bell hangs from a small brass yoke on the pin, the clapper and rope lanyard below the mouth. Wall: the same bell under
  a brass crosshead at the end of the arm, a short brace from the backboard up to the arm. The item in the inventory
  still shows the whole bell on its frame. No purple-black cube, no missing-model warning in the log.
- **Ring from the front (post):** right-click the side facing you: the bell sound, and the bell swings **away from you**
  first (about 20 degrees), comes back smaller and settles in about a second; the clapper trails the bell a moment and
  stays inside the mouth. Nothing pokes through the uprights or the beam.
- **Ring from the back (post):** walk round and click the far side: it swings away from you again (towards the front
  of the frame first).
- **Ring from a side (post):** click the left or the right side of the bell (along the pin): a half-size swing; the
  bell never swings sideways (it only rocks on its pin), the two sides start opposite ways.
- **Wall bell:** click the front: the mouth swings towards the wall first and does not touch the backboard, its trim or
  the brace; click it from below or from a side: a swing out from the wall or a half swing.
- **Ring again while it swings:** click several times quickly: each click restarts the swing from the start (no jump
  back to rest in between beyond the restart), and the bell is at rest a second after the last click.
- **Other players:** a second player watching sees the same swing in the same direction.
- **Raid alarm:** in a navy outpost or port with a ship's bell in its box, `/pirates world raid <port>` (tab-complete the port; or wait for a raid to be
  sighted): every ship's bell there rings every 5 seconds, each swing as if struck from the front (the mouth to the
  back of the mount first), together with the vanilla bells.
- **On a ship:** put both kinds on a hull, assemble and sail, also while turning: ring them; the swing follows the
  ship's pose (it swings in the ship's frame, front and back as on land), no flicker, no bell left behind in the world.
  Disassemble: both bells are back and still ring.
- **Toggles:** client config `bell_visuals.enabled = false`: the bell rings (sound) but hangs still;
  `bell_visuals.swing_degrees = 5` and `25`: a small and a large swing (25 is the maximum; check nothing clips at 25).
  Server config `ship_decor.bell_ring_ticks = 60`: the bell swings longer (about three swings) and still eases to rest.
- Say whether the swing is too fast, too slow or too big, and whether the clapper's lag reads well.

## Water barrel item (ITC1)

- **NeoForge and Fabric client:** the water barrel item shows blue water (not pale blue ice) in the inventory, the
  creative tab, in hand (first and third person), dropped and in an item frame. The water is vanilla's default water
  blue, the same in every biome (an item has no position), and it ripples like water. The placed barrel still takes the
  biome's water colour (compare in a swamp: the placed barrel is greener than the item). Say if anything else on the
  item turned blue or grey.

## The officer's outfit (ART13)

Run each step on the **NeoForge** client (`./gradlew :neoforge:runClient`) and on the **Fabric** client
(`./gradlew :fabric:runClient`), and say on which one anything differs. `/give @s pirates_n_ships:officers_breeches`,
`/give @s pirates_n_ships:officers_boots`, `/give @s pirates_n_ships:officers_coat`,
`/give @s pirates_n_ships:officer_hat`. Renders (not the game): `art/renders/officers_set_worn.png`,
`art/renders/officers_clothing.png`. Watch both logs for a missing texture (`officers_clothing_layer_1/2`, a purple and
black checkerboard on the legs) and for an exception when a coat is first drawn.
1. **Worn set, third person, beside a navy officer** (`/summon pirates_n_ships:navy_officer`, or one at an outpost):
   bicorne, coat, breeches and boots on you. Compare with him: white breeches with a navy stripe down the outer seam of
   each leg (the stripe runs from the hip to the knee on the outside of both legs), a navy waist band with a brass
   buckle (mostly under the coat), tall black boots from just below the knee with turned-down dark leather tops, a
   strap over the instep with a brass buckle in front and on the outer side. Say where the colours differ from his.
2. **Tails on Fabric too:** on the Fabric client the officer's and the captain's coat show the same knee-long tails as
   on NeoForge, swinging back with each stride, hanging nearly straight when sneaking (ART9 steps 1 to 3). An armour
   stand wearing the coat shows the tails as well. An enchanted coat shimmers on the tails, and a coat with an armour
   trim (if it accepts one) shows the trim like vanilla armour.
3. **Breeches and boots apart:** breeches alone: white legs to the ankle with the stripe and a white knee band with
   brass buttons; boots alone: your own legs show above the boot tops. Both on: the boots sit over the breeches, no
   flicker where they overlap.
4. **Slots and armour:** right-click and shift-click put each piece into its slot (the old one back to the hand);
   tooltips "+2 Armor When on Legs" and "+1 Armor When on Feet"; with coat, breeches and boots the armour bar shows 6
   (plus 1 with the bicorne). No durability bar. Server config `apparel.officers_breeches_armor` /
   `officers_boots_armor` = 0 after a restart: no tooltip line.
5. **Slot icons and the ground:** in the inventory and the creative tab the breeches lie flat facing you (navy waist
   band with a brass buckle, two gold buttons at the front, white legs with the navy stripe on the outer sides, knee
   bands with gold buttons, white stockings, black shoes) and the boots are a pair seen from the side, toes to the left
   (black with dark leather tops and a brass buckle). English names "Officer's Breeches" and "Officer's Boots", no
   checkerboard; held like flat items; on the ground at half size; in an item frame facing out.
6. **Career gear:** a fresh player made Lieutenant (`/pirates career set @s navy lieutenant`, or enlisting with the
   reputation and deeds) receives bicorne, saber, coat, breeches and boots together, once; with a full pack all five
   drop at the feet. A player who was already Lieutenant before this version gets nothing new (by design, LAW3b).
7. **Recipes:** the recipe book shows the breeches (white wool in the leggings shape, blue wool top middle) and the
   boots (gold nuggets over black wool over leather, two columns); the captain's pieces still craft as before.

## JEI (JEI1)

JEI (Just Enough Items) 19.39.0.372 is in both dev clients' runs. Run each step on the **NeoForge** client
(`./gradlew :neoforge:runClient`) and on the **Fabric** client (`./gradlew :fabric:runClient`), and say on which one
anything differs. Watch both logs for an exception that names `compat.jei` or `PiratesJeiPlugin`.
1. **The plugin loads:** after joining a world, the log has our line
   `JEI plugin pirates_n_ships:jei_plugin: 12 market pages, 2 hidden items` (12 = the default trade goods), and JEI's
   own plugin list in its startup log names `pirates_n_ships:jei_plugin` without an error.
2. **Item list:** open the inventory, type `@pirates_n_ships` in JEI's search box: our blocks and items are listed,
   but neither the **Ship Receipt** nor the **Ship Commission** (the two hidden items). The spawn eggs are listed.
3. **Information pages:** press **R** (or U) over the helm in the JEI list: besides its crafting recipe there is an
   "Information" tab: "Helm: launches, steers and takes apart a ship. (see Ships)" with no `**`, `{item:` or
   `[[` in it, and below it "From the Captain's Almanac, chapter "The ship's inventory"." Check the same for the
   hull patch ("Hull patch: closes a breach."), the paddle ("Paddle: rows a floating sea chest.") and the cutlass
   ("Rapier, cutlass, saber: swords. (see Weapons and combat)"). The flag pedestal and the spawn eggs have no
   information page (the Almanac does not describe them).
4. **Harbor Market:** press **U** over the harbor master's desk: the "Harbor Market" category, its tab icon the desk,
   the desk shown as its catalyst on the left. One page per trade good (sugar, cod, oak log, iron ingot, wheat,
   leather, cocoa beans, gunpowder, tobacco, spices, cloth, rum): the good in a slot, an arrow, a doubloon, and six
   lines: "Buy: 2.2 doubloons", "Sell: 1.8 doubloons", "Where it is made: buy for 1.3", "Where it is wanted: sell for
   2.9", "Made in tropical ports", "Cargo weight: 0.25 per item" (sugar with the default config). No line runs over
   the page edge (a cut line ends with "..."). R over sugar shows its market page too, R over a doubloon lists all
   twelve.
5. **Ship's Provisions:** press **U** over the pantry (or the water barrel): "Ship's Provisions", both as catalysts.
   Hardtack: "Food: 4 nutrition per item", "Never spoils", "Cargo weight: 0.25 per item"; a lime adds "Keeps scurvy
   away"; raw beef "Spoils after N days in the pantry"; the water barrel "Water: 16 rations per item"; rum "Rum: 1
   rations per item"; the water bottle is listed. U over a cod shows its provisions page.
6. **On a server whose trade goods differ** (a datapack that changes a `trade_good` price, or `/reload` after editing
   one in a world's datapack): after joining (or the reload) the market pages show the server's prices, not the
   defaults, and no good is listed twice.
7. **Without JEI:** start a client with `-Ppirates_n_ships.no_jei` (`./gradlew :neoforge:runClient
   -Ppirates_n_ships.no_jei`): the game loads with no error mentioning JEI, the mod plays as before, and the log has
   no `JEI plugin` line.

## Off-hand poses (ART17)

1. Hold the brig key, the brig door, the hull patch and the chain shot in the off hand (F to swap), in first person and in third person (F5, front and back), next to the same item in the main hand: each off-hand pose is the mirror image of the main-hand one (key bow lower left, shank into the screen to the right; door hinge towards the middle; hull patch braced face towards you; chain shot both balls low left), never the main-hand pose turned edge on. Spot check a sword and the pistol the same way (unchanged, already mirrored).
2. **Flag bundles (ART18):** hold the Jolly Roger, navy and merchant flag bundles in the off hand, first and third person: the Jolly Roger shows its skull and bone upright towards you and outward (not a plain black roll), the navy its white cross, the merchant its red band, and on the navy and merchant bundles the band runs on across the loose flap; in the main hand they look as before (`art/renders/flag_bundles_hands.png`).
