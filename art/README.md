# art

Hand-made model sources (design.md §4.8).

- `models/`: Blockbench projects (`.bbmodel`), one per model. Our own textures are embedded; vanilla textures are
  not (Mojang's assets are never committed) and load from `../vanilla/<name>.png`.
- `renders/`: a render of each model.
- `vanilla/`: ignored by git. Run `python3 tools/extract_vanilla_textures.py` once before opening a project so the
  vanilla textures show (pass block texture names, e.g. `spruce_planks`, to extract more for a new model).

Block model workflow:
1. Build the model in Blockbench (format Java Block/Item); load vanilla textures from `art/vanilla/` with namespace
   `minecraft`, folder `block`.
2. Export with `Codecs.java_block.compile()`.
3. Post-process: prefix textures with `minecraft:`, add `"parent": "minecraft:block/block"` and a `particle` texture.
4. Write it to `common/src/main/resources/assets/pirates_n_ships/models/block/<name>.json`, save the project here
   (without embedded vanilla textures), and point the block state at it in the module's datagen; run
   `./gradlew :neoforge:runData`.
5. The JUnit tests in `core/assets` (`HandMadeModelsTest`, `AssetReferencesTest`) guard the result.

Z-fighting lint (V1, after the playtest note "blocks and items shiver where two coloured layers overlap"):
- `python3 tools/lint_models.py` checks every hand-made model (`--summary`, `--warnings`, `--json`, `--exclude`,
  `--tolerance`, default 0.03 px). A **fight** is two faces of different elements in the same plane (within the
  tolerance), facing the same way and overlapping in area; it is **visible** when they show something different
  there (texture, palette patch, UV mapping, tint, shading). Faces are compared in world space after the element
  rotation, so rotated parts (octagon bars, ring segments) are checked as well; nothing is left unchecked.
  Same-look fights and **hidden** faces (covered by an opposite face of a part sitting on it) are warnings only.
  `HandMadeModelsTest.noVisibleZFighting` runs the same rule in Java on every model.
- Loaded guns (V1b): `--fix --mirror <gun>.json <gun>_loaded.json` fixes the base, gives the variant's identical
  elements the same moves and fixes only the lock parts of the variant, so P6's equality test keeps passing.
- **Keep coplanar parts at least 0.05 px apart.** Insets of 0.005 to 0.02 px (the old "eps" of the octagon bars,
  the 0.02 px bore discs and ledger lines) still flicker at 10 to 30 blocks. `--fix` applies the V1 rules to the
  model JSON and its project together (elements matched in outliner order, only `from`/`to` change, UVs and display
  entries stay): an inlay (keyhole, writing, bore disc, a speck on a heap) moves out to 0.05 px proud; a part that
  runs on through another (post through its cap, tiller into its knob) ends 0.05 px inside it; at a joint of two
  partly overlapping faces the rotated (else the smaller) face steps 0.05 px back. Crossing octagon bars at 0, 45 and
  -45 degrees therefore end up 0.05 and 0.1 px shorter than the axis-aligned pair.
- The cannon and swivel gun JSON come from `tools/gen_cannon_models.py`: after regenerating them, run
  `python3 tools/lint_models.py --fix` (it is deterministic) before rebuilding the projects.
- Renders `renders/zfight_water_barrel.png`, `zfight_cannon_loaded.png`, `zfight_helm.png`: before and after, at
  30 blocks and zoomed on a joint, from a camera 480 px away (near plane 0.8 px, like the game's 0.05 blocks).

Workflow notes (figurehead batch):
- `Codecs.java_block.compile()` drops the `block/` folder (`minecraft:oak_planks`) unless each texture's `folder` and
  `namespace` are set again after `fromPath(...).add()`; post-processing should add `block/` when it is missing.
- Elements outside the block (−16..32) need their position-based UVs wrapped into 0..16 per face.
- Java block rotation sign: on the x axis, −22.5 tilts the top of an element towards north (forward for a model that
  faces north) and swings its bottom towards south.
- `create_offscreen_view` plus `set_camera_angle(view: ...)` renders without moving the human's camera; the returned
  PNG lands in the session's tool-results folder and can be copied straight to `renders/`.
- A block whose model is not a full cube needs `noOcclusion()` in its properties, or neighbours cull their faces
  against it and the model renders dark.

Workflow notes (capstan, sail winch, yard batch):
- Building from a spec list in `risky_eval` works well: a small helper creates each cube with `autouv: 0` and sets
  position-based UVs (north/south `x`, `16-y`; east/west `z`, `16-y`; up/down `x`, `z`, each wrapped into 0..16) and a
  texture per face; `null` leaves a hidden face out of the export.
- Wood grain or rope along x: set `rotation: 90` on the north, south, up and down faces of a log-textured element.
- Round parts without 45° rotations: two crossed boxes give a chamfered (octagon-like) profile; wound rope or a band
  reads well as 1 px rings that alternate between two radii (0.3 px apart).
- Remove textures no face uses before compiling (the export lists every project texture). `Codecs.project.compile`
  with `{raw: true}` gives the project as an object; strip each texture's `source`, set `path` to `""` and
  `relative_path` to `../vanilla/<name>.png` before writing it to `models/`. Desktop Blockbench can write files from
  `risky_eval` with `require('fs')`.
- Put a `display.gui` block (scale about 0.5) into a model whose parts reach past the block, so the item fits its slot.

Workflow notes (containers batch: cargo crate, cargo barrel, pantry, water barrel, F7d):
- **Solid round bodies** (cargo barrel, 29 elements): each section is a regular octagon of four bars
  D × D·tan 22.5° at 0°, 90° and ±45° about y. Two crossed D × 0.7 D boxes plus the same pair at 45° give a spiky
  star, not a 16-gon. The rotated bars sit 0.05 px lower at top and bottom so their up faces (with a 45°-turned
  position UV) never z-fight the axis-aligned ones. Bulge: three stacked sections (D 12 / 13.6 / 12); hoops are the
  same octagons 0.4 px wider and 1 px high, over the section steps and near the ends.
- **Hollow round bodies** (water barrel, 64 to 68 elements): solid octagon bars cannot be hollowed, and their up faces
  would show as floors inside. Build each section from eight 1 px wall boards instead: a north board of length
  D·tan 22.5° (outer face on the octagon side), the same board at ±45° about the centre, the south board likewise,
  and axis-aligned east and west boards; their outer corners meet at the octagon vertices, the inner corners overlap.
  Board ends are hidden (`null`), the outer face carries the staves, the inner face the inside, the up face the rim.
- **Liquid surfaces** are zero-height elements (`from.y == to.y`) with only an up face: an octagon of four bars as wide
  as the wall's centre line, the two rotated bars 0.02 px lower. the surface uses
  `minecraft:block/water_still` with `tintindex 0` on the water faces (the exported models were edited by hand in Q1;
  `CrewContentClient` registers the biome water colour; the projects still carry `blue_ice`, and the item model keeps
  `blue_ice` because items have no colour hook yet). One model per fill level (`water_barrel_fill0..3`, `water_barrel` = full), each with its
  own project file, built from one helper with the level as a parameter.
- **Slatted boxes** (cargo crate, 41 elements): an inner dark box (`dark_oak_planks`) behind recessed slats shows
  through the gaps. Keep the inner faces of parts whose inside can be seen from above (the top rails), or the step
  between rail and lid shows the background. One side's parts are built once and turned in 90° steps around the
  block centre (x and z swap, a z rotation becomes an x rotation).
- Neither cargo block has a load property, so the load does not show; the pantry has no facing, so its doors face
  north (the item in the GUI shows them).
- When a hand-made model replaces a generated one, remove the generated file from version control before `runData`,
  or `processResources` fails on the duplicate entry.

Workflow notes (cleat, bilge pump, brig bars, brig door, F7e):
- **Cleat** (14 elements) and **bilge pump** (27): one model each, turned by the block state as before (the cleat for
  floor, wall and ceiling like a button; the pump by `facing`, spout and handle towards it). A small model gets a
  `display.gui` entry (cleat: scale 0.9, raised 3 px; pump: scale 0.5, lowered 1.5 px, its handle reaches y 20.6).
- **Connecting blocks** (brig bars): a post model plus one arm per side, plugged into the pane-style multipart state.
  `side` is the arm to the north (z 0..7), `side_alt` the same arm mirrored in z for the south; each is turned 90° for
  east and west. An unconnected side needs no model when the post stands on its own, so the `noside` parts went. The
  item uses a fourth model (`brig_bars`: post and both arms in a line). Opaque iron textures need no `render_type`,
  which also spares the Fabric port a render layer registration.
- **Doors**: a door model is the leaf of a door facing east (x 0..3). The hinge is at z 0 for `hinge=left` and at
  z 16 for `hinge=right` (vanilla `DoorBlock#getShape`). Vanilla opens a door by turning the closed model 90° or
  270° about the block centre, not about the hinge, so the model of an open door is the closed leaf turned by 180°
  (x -> 3 - x, z -> 16 - z). A leaf symmetric front to back needs only two models per half (open left = right, open
  right = left); an asymmetric one (the locked half with its padlock on the x < 0 side) needs four. Mirroring a part
  list in z or x (swap the north/south or east/west faces, negate the rotation angles about the other two axes)
  builds all of them from one list.
- `F7E.mz(parts)`, `F7E.mx(parts, c)` and `F7E.bar(name, x, z, y0, y1, w, tex)` (a chamfered round bar of two crossed
  boxes) joined the F7d helpers (copied as `F7E` with its own `ROOT`); they live only in the running app.

Workflow notes (cannon, harbor master's desk, F7f):
- **Round parts along any axis**: `F7F.oct(name, axis, centre, D, a0, a1, side, end0, end1, {four, eps})` builds the
  four-bar octagon of the F7d notes about x, y or z (the two diagonal bars rotate ±45 about that axis and are `eps`
  shorter along it). With `four: true` it gives the cheaper two crossed D × 0.7 D boxes for small parts (knobs, hubs,
  poles). A part that leans is the same list with one extra rotation about its foot (the rammer: `z 22.5`).
- **Cannon** (123 / 127 / 135 elements, `cannon`, `cannon_powder`, `cannon_loaded`, one project each): the barrel runs
  along z with its axis at y 9.5 (`CannonRules.PIVOT_HEIGHT`) and the trunnions at z 8, muzzle north at z −2.45; the
  sections step from D 5.8 at the breech to 3.8 at the chase, with rings and a muzzle swell. The bore is a
  zero-depth black octagon 0.02 px in front of the muzzle; the ball (`loaded`) is a smaller grey octagon plus cap in
  front of it, so a black ring stays visible around it. `coal_block` on black does not read. Wheels are octagon discs
  (`stripped_spruce_log_top` on the faces) inside a slightly larger iron tire octagon whose end faces are left out.
- **Desk** (81 elements): a partner's desk, drawers and a kneehole on both sides with a modesty panel in the middle,
  so the knobs show from the customer's side and in the GUI as well. `stripped_dark_oak_log` drawer fronts vanish on
  a `dark_oak_planks` carcass; `stripped_spruce_log` (grain along x) reads. Writing on the ledger pages is zero-height
  `black_concrete` strips 0.02 px above the page; a coin stack is one octagon (stacking 0.15 px coins tripled the
  element count for no visible gain).

Workflow notes (two-block cannon and swivel gun, F7g):
- **Part lists outside the app.** The seven models were generated as Java block JSON by a small Python part list
  (octagon prisms along x, y or z from the F7d/F7f four-bar recipe, boxes with position UVs wrapped into 0..16,
  per-face texture keys) and then rebuilt cube by cube in Blockbench from that JSON (`F7G.load(name)`: new
  `java_block` project, vanilla textures from `art/vanilla/` with namespace `minecraft` and folder `block`, display
  slots copied into `Project.display_settings`). `Codecs.java_block.compile()` of the rebuilt project gives the same
  elements, so the committed model files and the projects agree. Like the earlier helpers, script and loader are not
  committed; rebuild them from these notes.
- **Cannon** (`cannon` 140, `cannon_powder` 144, `cannon_loaded` 152 elements; one project each; `cannon_rear` stays
  a generated particle-only model). Everything is drawn from the master block, muzzle north. Barrel axis at x 8, y 14
  (`CannonRules.PIVOT_HEIGHT`), muzzle lip at z −15 (bore a black octagon 0.02 px in front), chase D 5.8 to 6.2,
  rings, reinforces D 6.9 and 7.5, base ring D 8.3 at z 21.4..22.6, breech to z 24, neck and cascabel to z 26; a
  vent on the vent ring. Trunnions through (8, 14, 8) under iron cap squares. Carriage z 1..31: dark oak cheeks in
  four steps (tops y 12.6, 11, 9, 7), spruce bed split at the block border (z 16), front and rear transoms, a quoin
  under the breech, axletrees with the grain along x, front trucks D 7.2 at z 4 and rear trucks D 6 at z 27 (wheel
  octagons with log-top faces inside an iron tire octagon, hub caps outside), iron straps, breeching eyes and rings
  at the cheek ends. `cannon_powder` adds the rammer leaning against the right cheek (foot at (14.15, 0.3, 15),
  `x 22.5`, head up and back); `cannon_loaded` adds the ball (grey octagon plus cap, the cap ends at z −16).
- **Cannon item** (`cannon.json` display): `gui` `[30, 225, 0]` / `[0.5, -0.7, 0]` / 0.33 (the 48 px gun fills the
  slot like a block); `fixed` `[0, 90, 0]` / `[0, -0.5, 0]` / 0.33 (side view in the frame); `ground` 0.15;
  third person `[75, 45, 0]` / `[0, 2.5, 0]` / 0.25; first person `[0, 45, 0]` (left `[0, 225, 0]`) / `[0, 1, 0]` /
  0.2; `head` 0.3.
- **Swivel gun** (`swivel_gun_yoke` 19, `swivel_gun_barrel` 43, `swivel_gun_barrel_loaded` 51, `swivel_gun` 62 =
  yoke + barrel, the item's model; one project each). Matches `SwivelGunRenderer`: the yoke (collar and pintle on the
  block's vertical centre line from y 0, a cross bar at y 2.8..3.8, arms at x 4.2..5.2 and 10.8..11.8 ending in
  round bosses at y 6, z 8) turns with the yaw; the barrel (axis x 8, y 6, muzzle lip at z −6, base ring, breech to
  z 14, socket and spruce tiller with an iron knob to z 20, trunnions through (8, 6, 8) into the arm bosses) turns
  about the pivot, so nothing in it may stick out below the pivot near the yoke's cross bar (the barrel's bottom is
  y 4.05 at the pivot, the bar's top 3.8). The loaded barrel adds a ball (D 1.4) at the muzzle.
- **Swivel item** display: `gui` `[30, 225, 0]` / `[-0.45, 1.8, 0]` / 0.65; `fixed` `[0, 90, 0]` / `[0, 2.2, 0]` /
  0.6; `ground` 0.3; third person `[75, 45, 0]` / `[0, 2.5, 0]` / 0.375; first person 0.4.
- **Display check without the human's viewport:** in display mode an offscreen orthographic view at
  `position [0, 0, 32]`, `target [0, 0, 0]`, `zoom 2` shows the GUI slot (16 px per slot unit at 1024 px); the model's
  pixel bounds against the image centre give the translation needed (pixels / 16, y inverted).
- `PlaceholderModel` (the P2 datagen builder) is gone; the generated `cannon_placeholder*` and `swivel_gun*` models
  were removed from version control before `runData` (see the containers batch).
- Renders: `renders/cannon.png` (placed three-quarter view, east side, GUI), `cannon_loaded.png` (ball in the muzzle,
  rammer side) and `swivel_gun.png` (three-quarter view, east side, GUI); composed side by side on a 2D canvas in
  the app and written with `fs`. The part list that generated the geometry is `tools/gen_cannon_models.py` (run it to regenerate the JSON; the projects were rebuilt from its output).

Workflow notes (sea chest, S1-art):
- **Sea chest** (60 elements, `sea_chest`, one project): a seaman's chest facing north (the `FACING` side). Body
  `dark_oak_planks` x 2..14, z 3..13, y 1..6.5 on four 1 px `stripped_dark_oak_log` feet; lid of a rim (0.4 px
  overhang) and four narrowing steps up to y 10 (`stripped_dark_oak_log`, grain along x). Bands are `anvil`: two
  horizontal ones round the body (foot and top edge, 0.2 px proud) and two vertical ones at x 4..5 and 11..12 over
  body and every lid step (0.3 / 0.2 px proud); `iron_block` rivets on the bands, `anvil` hinge straps on the back.
  Front: an `iron_block` lock plate with a `black_concrete` keyhole, an `anvil` hasp hanging from the lid rim and an
  iron staple through it. Sides: rope handles (`stripped_birch_log` like the sail winch and flagpole), a bottom
  strand along z (grain turned) and two legs tilted ±22.5 about x up to `dark_oak_planks` cleats. `iron_block` bands
  looked white and flat; `anvil` bands with `iron_block` rivets read as dark iron.
- Footprint x 1..15 (handles), z 2..14 (staple to hinges), y 0..10.2; `SeaChestBlock` uses `box(1, 0, 2, 15, 10, 14)`
  (x and z swapped for east/west). The floating entity draws the same model scaled to its width (0.875), so it stays
  centred on the block's x/z centre.
- Display: `block/block` values tuned for a model smaller than a block: `gui` `[30, 225, 0]` / `[0, 2.4, 0]` / 0.85
  (front on the right, like any block item at y 225; centred with the offscreen GUI check of F7g), `fixed` 0.65
  raised 1.95, `ground` 0.3 raised 3.5, third person 0.4 raised 3, first person 0.45 raised 2, `head` 0.75.
- Built like F7g: a Python part list (positions, per-face texture keys, vanilla default UVs, log faces turned with
  the UV rect transposed so the grain keeps its texel density) wrote the model JSON, and `S1A.load()` rebuilt it cube
  by cube in a new `java_block` tab; `Codecs.java_block.compile()` of the tab matches the JSON (60 elements, no
  difference). Script and loader are not committed; rebuild them from these notes.
- Render: `renders/sea_chest.png` (placed three-quarter view from the front-west, GUI).

Workflow notes (notice board, N1):
- **Notice board** (34 elements, `notice_board`, one project): a village board in one block, notices on the north
  side (the `FACING` side, unrotated). Two `dark_oak_log` posts x 1..3 and 13..15, z 6.75..8.75, y 0.5..15, standing
  in `dark_oak_planks` feet (y 0..1, so the post bottoms sit inside them); a `spruce_planks` board x 2..14, y 4..14,
  z 7..8.5 (front face at z 7, ends hidden in the posts); `dark_oak_planks` rails across the top and bottom edge in the
  posts' plane (x 3..13, only edge contact with the posts, so no coplanar overlap). Roof: two 1 px `dark_oak_planks`
  slabs x 0..16 (1 px past the posts) turned ∓22.5 about x at the ridge (8, 16, 7.75), the south slab 0.05 px
  shorter at each end so the two slabs' end faces never share area, under a `dark_oak_log` ridge beam (grain along
  x). Four notices 0.2 px proud (z 6.8..7): two `sandstone_top` (the parchment of the U1 cards) and two
  `white_terracotta`, one straight pair and one pair turned +22.5 / −22.5 about z round their own centres; ink lines
  are zero-depth `black_terracotta` strips 0.05 px proud; every notice has a 1 × 1 × 0.6 `red_concrete` pin head,
  the lower tilted one a `nether_wart_block` wax seal (close to the dark red of the GUI seal);
  four `gold_block` nails on the rails. Particle `spruce_planks`. Lint: no fights, no warnings.
- `NoticeBoardBlock` uses `box(1, 0, 6, 15, 16, 10)` (x and z swapped for east/west); the roof overhang stays
  outside the outline.
- Display: `block/block` values except `gui` `[25, 200, 0]` / 0.8 (front three-quarter, notices readable; centred with
  the F7g offscreen GUI check), `fixed` `[0, 180, 0]` (notices out of the frame) and `head` `[0, 180, 0]` / 0.6.
- Built like S1-art: a Python part list (positions, per-face texture keys, vanilla-style position UVs, rotated
  notices checked against each other and the rail band with a separating-axis test) wrote the JSON, rebuilt cube by
  cube in a new `java_block` tab; `Codecs.java_block.compile()` of the tab is the committed model. Script not
  committed; rebuild it from these notes.
- Render: `renders/notice_board.png` (placed three-quarter from the front, front, GUI).

Items (sword batch, F8a):
- Item models are `java_block` projects exported to `common/src/main/resources/assets/pirates_n_ships/models/item/<name>.json`;
  datagen writes no model for them (drop the item's `m.handheldItem(...)` / `m.flatItem(...)` line).
- **No parent.** `"parent": "minecraft:item/handheld"` (or `item/generated`) does not work with elements: vanilla's
  `ModelBakery` bakes every model whose root parent is `builtin/generated` from its `layer0` sprite and ignores the
  elements. The model carries vanilla's display entries itself instead: `thirdperson_righthand/lefthand` and
  `firstperson_righthand/lefthand` from `item/handheld`, `ground`, `head` and `fixed` from `item/generated`, plus
  `"gui_light": "front"`. `HandMadeModelsTest.handMadeItemModelsBringTheirOwnHandheldTransforms` guards this.
- **Sprite alignment.** Model a hand-held item lying in the XY plane like its vanilla sprite (grip bottom-left, tip
  top-right, 16×16 footprint, centred on z = 8, 1 to 3 px thick), so the vanilla transforms hold it like the vanilla
  sword. A sword is built upright along y around x = 8 and every element gets `rotation: z −45` about `[8, 8, 8]`;
  model +x then points to the sprite's lower-right. Curves: an upright element rotated −22.5 about its own joint is
  steeper than the diagonal; an element built along **x** and rotated +22.5 is flatter (67.5° from upright). So one
  blade can run 22.5° → 45° → 67.5° with only vanilla angles (the saber: hilt, blade root, tip).
- **Edge side.** In third person the sprite's upper-left side faces down and the lower-right side faces up. Put the
  edge and the knuckle bow on the upper-left (−x before rotation) and curve the blade towards the lower-right; then the
  edge points down, the bow covers the fingers and the tip rises when the sword is held forward. The perspective
  third-person preview is misleading here; check from the side (see below).
- A model that runs past the 16×16 footprint (the rapier, about 18.4 px) gets a `gui` entry with scale 0.8 and a
  translation that re-centres its bounding box; the hand slots stay vanilla, so the item is longer in the hand.
- **Palette.** Faces take their colour from `textures/item/palette.png` (`tools/gen_item_palette.py`), a 16×16 sheet of
  4×4 patches. Map a face to `[u+0.5, v+0.5, u+3.5, v+3.5]` of its patch (inset against mipmap bleeding; the wire
  patch uses its full height so its stripes show). Never move a patch and never resize the sheet; new colours go
  into `palette_2.png` (see F8b below).

  | v \ u | 0 | 4 | 8 | 12 |
  |---|---|---|---|---|
  | 0 | steel_light | steel | steel_dark | iron_dark |
  | 4 | brass_light | brass | brass_dark | gold |
  | 8 | leather | leather_dark | wood_dark | wood |
  | 12 | bone | black | red | wire (1 px stripes along v) |
- **Display check.** `enter_display_mode` works for `java_block` projects (slots `firstperson_righthand`,
  `thirdperson_righthand` with reference `player`, `gui`). Set `Project.display_settings[slot]` (a `DisplaySlot`) from
  `risky_eval` first so the preview uses the transforms the export carries. An offscreen view renders the display scene
  while display mode is active, so `set_camera_angle(view, locked_angle: "east")` gives a side view of the held item.
  Selecting a project restores the mode it was left in; switch back to `edit` before offscreen renders of the model.
- The project file keeps the palette embedded, with `path: ""` and `relative_path` pointing at the texture in
  `common/src/main/resources`.

Items (firearms, ammunition and grappling hook batch, F8b):
- **UVs are fractions of the sprite.** `FaceBakery` maps `uv / 16` onto the sprite's full width and height, whatever
  its pixel size, so a palette sheet can never grow: a 32×16 `palette.png` would halve every u of the existing models.
  New colours live in a second 16×16 sheet, `textures/item/palette_2.png` (same script), used as texture `#1` next to
  `#0` = `palette.png`. A model may use either or both; the export drops a sheet no face uses. When `palette_2` is
  full, add `palette_3.png`.

  `palette_2.png`:

  | v \ u | 0 | 4 | 8 | 12 |
  |---|---|---|---|---|
  | 0 | gunmetal_light | gunmetal | gunmetal_dark | flint |
  | 4 | walnut_light | walnut | walnut_dark | rope_coil (1 px stripes along v) |
  | 8 | lead_light | lead | lead_dark | cast_iron_light |
  | 12 | cast_iron | cast_iron_dark | rope | rope_dark |
- **Guns** are sprite-aligned (butt bottom-left, muzzle top-right on the 45° diagonal) with the hammer side on the
  upper-left and the grip, trigger guard and ramrod on the lower-right, so they look natural in the GUI. That is the
  opposite side from the swords, so the hand transforms turn the model over: `thirdperson_righthand`
  `[0, 90, 40]` / `[0, 4, 2.5]` / 0.85 (pistol) and `[0, 90, 43]` / `[0, 5.5, 1.5]` / 0.85 (musket);
  `firstperson_righthand` `[0, 90, -25]` / `[-1, 5, 1]` / 0.68 for both. The left hand negates the y and z rotations
  and keeps the translation. Third-person translation is in arm space: y moves the item forward/back, z up/down along
  the arm. The Blockbench player reference already raises the arm like the in-game item pose, so the east side view
  shows the real pitch (barrel about 12° up).
- **Small items** (lead shot, cannonball, grappling hook) copy `item/generated`'s entries. `item/generated` has no
  left-hand entries and vanilla falls back to the right hand, so the left-hand slots repeat the right-hand values.
- **Round shapes.** A regular octagon of width D is the union of four D × (D·tan 22.5°) bars at 0°, 90°, 45° and −45°
  (cannonball: one such ring per depth layer plus an a × a pole through the middle). Small balls (lead shot) are three
  crossed boxes (D, s, s), (s, D, s), (s, s, D) with s ≈ 0.67 D; a lighter `south` face on the z box reads as a
  highlight in the GUI. Rotation is one axis per element, but the axis may be x as well as z: the grappling hook's
  front and back flukes rotate about x.
- Building from a part list in `risky_eval` (centre, direction in 22.5° steps, length, width, z range, palette patch
  per face) and re-centring the bounding box on (8, 8) before building kept each model to a few iterations.

Items (captain's whistle and shackles batch, F8c):
- Both use `item/generated`'s transforms unchanged (left hand repeats the right hand) and need no new colours:
  brass patches of `palette.png` for the whistle, `cast_iron*` of `palette_2.png` (plus `black` for the keyhole) for
  the shackles.
- **Rings** (whistle shackle, cuffs, flat chain links): eight bars around the centre, bar `k` at normal angle
  `k * 45°`, direction `k * 45° + 90°` folded into −90..90, length `2 (r − t/2) tan 22.5° + 0.3..0.45 t`. A longer
  overlap makes the corners stick out as spikes. A **rim highlight** is a second, thinner ring at the outer edge,
  0.1 px proud in z on both sides, with the light patch on the south faces of bars `k = 1..4` (upper-left half, the
  side the GUI light comes from) and on its outer sides.
- **Chains**: alternate an edge-on link (one bar along the chain, 0.8 wide, 2 px deep in z) and a flat link (an
  oval of four bars); let them overlap by about 1 px and end in an eye on each cuff.
- **Cord loops** (lanyard): four segments at ±22.5° around the hanging direction form a narrow diamond, with vanilla
  angles only.
- An item whose flat model used a vanilla sprite (the whistle used `minecraft:item/goat_horn`) has no sprite of
  ours to delete.

Items (provisions batch: rum, hardtack, lime, salt pork, salted fish, F8d):
- All five use `item/generated`'s transforms unchanged (left hand repeats the right hand), like the other small items.
- **`palette_3.png`** (texture `#2`, same script) holds the food and glass colours. It is full; new colours went into
  `palette_4.png` (F8e).

  | v \ u | 0 | 4 | 8 | 12 |
  |---|---|---|---|---|
  | 0 | lime_light | lime | lime_dark | leaf |
  | 4 | biscuit_light | biscuit_dark | meat_light | meat_dark |
  | 8 | fat | rind | fish_back | fish_belly |
  | 12 | glass_dark | glass_highlight | cork | paper |
- **Particle texture.** Eating and drinking particles come from the model's `particle` sprite, so the food models point
  `particle` at `palette_3` (the sheet with their own colours) even when they also use `palette` or `palette_2`.
- **Flat food seen from above.** The GUI looks straight at the south face. A biscuit or slab "lying on a table" gets
  one `x` rotation about `[8, 8, 8]` on every element: the hardtack is built facing south and tilted `-45` (the top
  leans away, the front edge shows as a band at the bottom, the face is foreshortened to 0.71); the salt pork is built
  lying flat in xz and tilted `+22.5` (the layered cut face stays in front, the rind on top shows above it). Blockbench
  shades a cut face tilted downwards darker than the game's front GUI light does.
- **Bottles** are stacked octagon sections (two crossed boxes D × 0.7 D per section: body, shoulder, neck, lip, cork);
  a label is the same section 0.2 px wider. Opaque liquid is a darker lower body section (`walnut_dark` under
  `glass_dark`) whose level shows just above the label.
- **Diagonal bodies with splayed ends** (salted fish): the body is built upright along y and rotated `z -45` about
  `[8, 8, 8]`; parts at other angles (tail lobes at 202.5° and 247.5°) are placed in world coordinates instead, one
  built along x and rotated `+22.5`, the other along y and rotated `-22.5`, each about its own centre.
- Two `risky_eval` helpers kept each model to one or two iterations: `build(parts)` (part list with a default palette
  patch, per-face overrides and an optional rotation) and `exportAll(name)` (drops sheets no face uses, sets the
  display slots, writes the item model and the project file). They live only in the running app; rebuild them from
  these notes after a restart.

Items (trade goods batch: cloth, spices, tobacco, F8e):
- All three use `item/generated`'s transforms unchanged (left hand repeats the right hand); particle `palette_4`.
- **`palette_4.png`** (texture `#3`, same script) holds the trade-good colours. It is full: the next new colour
  needs a `palette_5.png`. `cloth_weave` (lighter and light off-white rows) and `burlap_weave` (light and darker
  sacking rows) are striped; `cloth_shadow` is used only for the cloth's end rings, `spice_dark` is a spare.

  | v \ u | 0 | 4 | 8 | 12 |
  |---|---|---|---|---|
  | 0 | cloth_light | cloth_dark | cloth_weave (stripes) | cloth_shadow |
  | 4 | burlap_light | burlap_dark | cord | twine |
  | 8 | spice_red | spice_orange | spice_gold | spice_dark |
  | 12 | tobacco_light | tobacco_dark | midrib | burlap_weave (stripes) |
- **Cloth** (10 elements): roll, end ring and wooden core (`wood` / `wood_dark` of `palette.png`) are octagons from
  two crossed axis-aligned boxes (D x 0.7 D), so the whole bolt needs only the one `z -45` rotation about
  `[8, 8, 8]`. The flap leaves the roll side and sags towards the table in three z steps (one rotation per element
  leaves no room for a y tilt). The weave runs along the roll: `cloth_weave` with face `rotation: 90` on the side
  faces. A striped patch shows at most four stripes per face, however large the face, so stripes only read on faces
  about 3 to 6 px wide.
- **Spices** (42 elements): stacked octagon sections (base, wide lower body, upper body, shoulder, neck, cord, cuff,
  rolled lip, heap layers); the heap's colour patches are split half-octagons. Striped `burlap_weave` on the large
  body read as barrel hoops in the GUI, so the body is plain `burlap_light` with thin `burlap_dark` creases gathered
  under the cord (0.1 px proud), and the weave only on the rolled lip.
- **Tobacco** (43 elements): leaves fan from one pivot (the tie) with vanilla angles only: upright and `z -22.5`
  (22.5 degrees from upright), upright and `z -45` (three leaves offset sideways by 1.5 px and stacked in z), built
  along x and `z +22.5` (67.5 degrees). Each leaf is a width profile of boxes along its length plus a 0.1 px proud
  midrib; stem stubs reach past the twine wraps so the bundle end reads as cut stems.
- The helpers `F8E.build(parts)` (part list with palette patch names, per-face overrides `{tex, rot}`, one rotation
  per part), `F8E.bbox()` (rotated bounding box, for re-centring on (8, 8)) and `F8E.exportAll(name, particleSheet)`
  live only in the running app, like the F8d ones. One offscreen view renders whichever project is active, so it
  serves several projects in turn.

Items (doubloon and bounty proof, F8f):
- No new colours: the doubloon uses `gold` / `brass_light` / `brass` / `brass_dark` of `palette.png` (particle
  `palette`); the bounty proof uses `paper` and `biscuit_light` / `biscuit_dark` (`palette_3`), `twine` (`palette_4`),
  `red` and `leather_dark` (`palette`) and `spice_dark` (`palette_4`) for the stamp (particle `palette_3`).
- **Four-bar octagons cannot also be tilted.** An element has one rotation axis; the octagon's diagonal bars already
  use it (about the disc's normal), so the hardtack's `x -45` tilt on every element is not available. The doubloon
  (22 elements: three coins of four bars, a 0.3 px face octagon inset as the worn rim, a raised cross pattée of six
  0.5 px bars with `brass_dark` down and west faces as the emboss shadow) is therefore built facing south like a
  sprite, stacked along z, and tilted only in the icon by an extra `gui` display entry, rotation `[-45, 0, 0]`
  (negative x leans the top away, like the hardtack's element tilt). The seven vanilla `item/generated` slots stay
  unchanged, so hand, ground and item frame show the coin face upright like the old sprite.
- **Bounty proof** (12 elements): a rolled scroll on the 45 degree diagonal (built upright along y, `z -45` about
  `[8, 8, 8]` like the cloth), so its rolls are two crossed boxes (D x 0.7 D), not four bars. The wider box's front and
  back faces take the darker `biscuit_light`, so the roll reads round from the front. The inner roll (`twine`, ends
  `biscuit_dark`) is 0.62 D and sticks out 0.5 px at both ends; ribbon band 0.1 px proud, wax seal of two crossed
  boxes with a `spice_dark` stamp, two short ribbon tails towards the lower right.
- Builders: `F8F.octZ(P, name, cx, cy, D, z0, z1, side, front, back, eps)` (four-bar octagon about z, using the F8E
  part format), `F8F.doubloonParts(opts)`, `F8F.proofParts(opts)` / `F8F.proofBuild(opts)` and
  `F8F.exportAll(name, particleSheet, gui)` (F8E's export plus an optional `gui` display entry); like the F8E helpers
  they live only in the running app.
- The market screen draws the doubloon with `GuiGraphics.renderItem`, so the 3D model shows there without changes.

Doubloon as a single coin (F8i, replaces the F8f stack after the playtest note "a doubloon should be a singular
item"):
- **16-gon from eight bars.** A regular 16-gon of width D is the union of eight D x D·tan 11.25° bars, one per
  22.5° direction: bars along x at 0, ±22.5, ±45 and along y at 0, ±22.5 (a y bar at ±22.5 is a 67.5° direction), all
  about the disc centre. Rounder than the four-bar octagon for twice the elements. The ±22.5 bars are 0.01 px and the
  ±45 bars 0.02 px thinner in z so their faces do not fight the axis-aligned ones.
- **Coin** (36 elements, `palette.png` only, particle `palette`): body 16-gon D 10.6, z 7.5..8.5 (1 px thick), faces
  `brass` (the darker rim band), edge `brass_dark`; a field 16-gon D 8.8 0.05 px proud on each side in `gold`; a
  `brass_light` highlight bar on the upper-left rim (`z 45`); the cross pattée on the front (two arms 1.2 wide and
  four end flares, 0.4 px raised, `brass` face, `brass_dark` down and east faces as the emboss shadow); the reverse
  (north) a raised `brass` diamond (`z 45`) and four dots. Built facing south like a sprite, centred on (8, 8, 8).
- **Display** (left-hand slots repeat the right hand; the game mirrors them): `gui` `[-25, 25, 0]` / 1.1 (three-quarter
  view, the edge shows lower left); third person `[0, 25, 0]` / `[0, 2.5, 1]` / 0.45 (the vanilla hand frame turns
  model +z up the arm, so the identity transform already lays the coin flat on the fingers with the cross up; y 25
  rolls it outwards); first person `[0, -70, 25]` / `[1.13, 4.2, 1.13]` / 0.55 (upright, the cross turned towards the
  player); `ground` `[-90, 0, 0]` / `[0, -1.5, 0]` / 0.5 (lying flat, cross up); `fixed` and `head` stay vanilla (the
  cross faces out of the frame). In display mode, the world normal of a face
  (`new THREE.Vector3(0,0,1).transformDirection(cube.mesh.matrixWorld)`) settles which side faces up faster than the
  previews do.
- Built from a Python part list that writes the model JSON, then loaded into a new `java_block` tab with
  `Codecs.java_block.parse` (faces re-pointed to the palette texture, display slots copied into
  `Project.display_settings`); `Codecs.java_block.compile()` of the tab gives the same 36 elements and display. Script
  not committed; rebuild it from these notes. Render `renders/doubloon.png`: GUI, third-person hand, ground, reverse.

Items (brig door and brig key, F8g):
- **Brig door** (34 elements, `brig_door_item`): the unlocked left-hinged door, both halves in one model, built from
  the elements of `brig_door_bottom_left` and `brig_door_top_left` (upper half raised 16 px, so y 0..32). The block
  leaf faces east (x 0..3); the item turns it into the sprite plane: item `(x, y, z)` = block `(z, y, 6.5 + x)`, so
  the hinge is on the left at x 0, the lock plate on the right, the leaf centred on z = 8. Faces are remapped
  (west -> north, east -> south, north -> west, south -> east) and the up/down UVs transposed; the vanilla textures
  (`anvil`, `iron_block`, `black_wool`) stay, so the item matches the placed door. The parts were converted from the
  block JSON in `risky_eval` (`F8G.doorParts()`), no hand placement, so a change to the door's block models can be
  carried over the same way.
- A block item with a hand-made item model needs `m.handMadeItem(item)` in the module's datagen: otherwise the model
  provider writes the automatic `item/<name>` model that delegates to `block/<name>` (the door has none) and
  `processResources` fails on the duplicate.
- **Door display entries** (no vanilla entry fits: vanilla's door items are flat sprites). The model is 32 px tall, so
  every slot scales it and re-centres y (bounding-box centre y 16 -> translation y = -8 * scale): `gui` rotation
  `[0, 25, 0]` (a little of the hinge side shows), translation `[0.1, -4, 0]`, scale 0.5 (fills the slot's height);
  `fixed` `[0, 180, 0]` / `[-0.1, -4, 0]` / 0.5; `ground` 0.25 raised 1 px; `head` 0.5. Third person
  `[70, 90, 0]` / `[2.2, -1, -2]` / 0.3: x 70 stands the door upright against the arm's forward tilt (90 would lean it
  back), y 90 turns its face to the side, translation x moves it outside the arm (at 0 it cuts through the arm and
  leg), y/z lower it so the hand grips it about half-way up. First person `[0, -70, 0]` / `[1.5, 2.5, 1]` / 0.25. The
  left hand negates the y rotation and keeps the translation.
- **Brig key** (39 elements): an iron skeleton key on the 45 degree diagonal, 14 px long (bow bottom-left, bit
  top-right like a tool sprite; the two teeth on the upper-left side, so they point up when it is held). Parts are
  thirteen shapes in world coordinates, each a bar with a direction in 45 degree steps (an octagonal bow ring of
  eight bars, a collar, the shank, two teeth and the web between them; `F8G.bar`, `F8G.keyShapes`). **Darker edge:**
  each shape is three elements: a 1.2 px deep body with `iron_dark` front and back and `steel_dark` sides, and a
  0.15 px plate in front (`steel`) and behind (`steel_dark`), inset 0.3 px. The plates of neighbouring shapes overlap,
  so the dark rim follows only the outline of the whole key, also in the flat GUI view where side faces do not show.
- **Key display entries:** `item/handheld`'s rotations with smaller scales, since a key at sword size (0.85) looks
  huge: third person `[0, -90, 55]` / `[0, 2.5, 0.5]` / 0.55 (bow in the fist, shank forward and slightly up),
  first person `[0, -90, 25]` / `[1.13, 3.2, 1.13]` / 0.55; `ground`, `head`, `fixed` from `item/generated` like the other items; no `gui`
  entry. Particle `palette`.
- Renders: `renders/brig_door_item.png` and `renders/brig_key.png` are strips of three views: GUI (display mode),
  third person from the right side, first person (the display viewport's camera, copied into an offscreen view of
  the same aspect).
- The old sprites (`textures/item/brig_door.png`, the wooden door from C8, and `brig_key.png` from
  `tools/gen_law_textures.py`) are gone; the law script had no other sprite and was deleted, and
  `gen_placeholder_textures.py` no longer writes the door sprite (it has no item sprites left).

Items (wearable hats, H2):
- `pirate_hat`, `bandana`, `navy_hat`, `officer_hat` are written by `tools/gen_hat_items.py` (no Blockbench project):
  part lists copied from the hat cubes of `seafarer_models.js` (soldier's tricorn, officer's bicorne, pirate's bandana
  with knot, tails and bone dots; the pirate hat is the tricorn in black with a skull and crossbones on the front),
  colours from the item palettes. Rerun the script after changing a mob's hat; `HatModelTest` fails when the item and
  the mob's geo drift more than 1 px apart.
- **Worn through `CustomHeadLayer`:** a non-armour head item is drawn at the head's centre (4 px up, 180° about y,
  scale 0.625 / −0.625 / −0.625), then the `head` display. An item point v (blocks, centred, after the display) lands
  at geo `(−10 v.x, 28 + 10 v.y, 10 v.z)` (head bottom y 24). So the script builds each hat 1:1 in head pixels,
  shifted to centre its box on (8, 8, 8) (item = `(8 + x, y − Y0, 8 + z − Z0)`, x = −geo x), and the `head` entry
  is scale 1.6 (10 × 1.6 / 16 = 1), translation `(0, 1.6 (Y0 − 20), 1.6 Z0)`, no rotation.
- **Angles:** GeckoLib negates a geo cube's x and y rotation, so in item space the soldier's right wall (`y −28.4` in
  the geo) is `y +22.5` and the officer's front flap (`x −18`) is `x +22.5`. The tricorn's 28.4° walls become 22.5°
  walls meeting a 2.5 px flat front piece (where the skull goes); edges (white, gold) are separate 0.8 px elements on
  top of each wall or flap piece, so they show on every face.
- Other slots: `gui` `[20, 200, 0]` (front three-quarter, top tilted towards the viewer), scale and translation fitted
  to a 15 px box; `fixed` `[−15, 0, 0]` (front out of the frame); hands copy `block/block`; `ground` 0.5.
- Check render (not the game): `renders/hats.png`, `python3 tools/gen_hat_items.py --render art/renders/hats.png`.
  Per hat a row of front, left side and three-quarter view, each with the item through the head chain (left) beside
  the mob's own hat cubes (right) on an 8 px head.

Entity models use the Modded Entity format (Mojang mappings 1.17+); paste the body of the exported
`createBodyLayer()` into the renderer's layer method (example: `AnchorRenderer.createLayer`).

Player animations (melee, F9):
- Sources: `animations/player_rig.bbmodel` (format Bedrock entity, all 11 animations of
  `MeleeAnimationMapping.ALL`, plus the four firearm animations below) and `animations/player_poses.js` (the pose table and the builder that writes the
  keyframes; run it in `risky_eval` with the rig open to rebuild every animation after editing a pose). Strips:
  `renders/anim_<name>.png`, five evenly spaced frames, front three-quarter view on top, right side below.
- **Rig.** Flat PAL bones, the vanilla parts are siblings: `body` (pivot 0, 12, 0; the whole model, unused) holds
  `head` and `torso` (0, 24, 0), `right_arm` (5, 22, 0), `left_arm` (−5, 22, 0), `right_leg` / `left_leg`
  (±1.9, 12, 0); `right_item` (6, 12, −2) and `left_item` (−6, 12, −2) are children of the arms, at the point where
  vanilla's `ItemInHandLayer` pivots the item (arm centre, 10 px down, 2 px forward). The rig **faces north (−z)
  with the right arm at +x**, matching PAL's `PlayerAnimationController.BONE_POSITIONS`. A proxy sword in
  `right_item` points forward and 10° towards the hand, as vanilla's `item/handheld` third-person transform holds it.
- **Sign convention (checked in PAL's source and in the rig).** PAL writes a file's rotation, in radians, straight
  into the vanilla `ModelPart` (`RenderUtil.translatePartToBone`; the `body` bone negates x and y because it acts
  before the model's `scale(-1, -1, 1)`, which gives the same result). So file values are vanilla angles:
  right arm x −90 = horizontal forward, −180 = straight up, positive = backwards; y positive swings the arm towards
  the player's right; z positive lifts the right arm sideways (outwards) and the left arm inwards. Positions: x
  positive moves a part to the player's left, y positive up, z positive backwards. `right_item` rotations act in the
  arm's frame in the same sense (x positive turns the blade from "perpendicular to the arm" towards "along the
  arm": about +80 makes the sword an extension of the arm; y twists it about the arm), applied in the order Y, Z, X
  (Blockbench previews Z, Y, X; only matters when y and z are both set).
- **Blockbench 5 stores keyframes negated.** `compileBedrockAnimation()` writes rotation `[-x, -y, z]` and
  position `[-x, y, z]` of the stored values, and the preview shows the stored values as plain three.js angles. On
  the north-facing rig the exported file then renders in game exactly like the preview. The builder therefore takes
  file-convention values and stores them negated (`F9.toInternal`); never type file values into the keyframe panel.
- **Export.** `compileBedrockAnimation()` per animation, post-processed into PAL's shape: one animation per file,
  key = file name, `format_version` 1.8.0, every channel as an object of `"time": [x, y, z]` (a single keyframe
  compiles to a bare array; `PalAnimationFilesTest` wants the object), `loop` `"hold_on_last_frame"` (wind-ups,
  actives, guard, parry) or `false` (recoveries, guard_lower, stagger). Only linear keyframes: smooth/Bézier ones
  compile to `{post, lerp_mode}` objects, which PAL reads but the test does not; ease with extra keys instead.
- **What the builder animates.** Per key pose: `right_arm`, `left_arm` and `right_item` rotations, plus a torso
  `twist` (yaw) and `lean` (pitch). The lean is pivoted at the hips, not at the neck where the vanilla torso
  pivots: the torso, head and arms get the matching positions so nothing detaches; the twist moves the shoulders
  around the spine like vanilla's attack swing. Legs and head rotation are never animated (walking and looking stay
  vanilla).
- **Rest pose** is vanilla's "holding an item" arm, right arm `[-18, 0, 0]`: every animation that starts or ends at
  idle starts or ends there, so the hand-over to vanilla does not snap. Chains share their poses exactly
  (wind-up end = active start, active end = recovery start, guard = parry start = guard_lower start).
- Offscreen strips: a separate `THREE.WebGLRenderer({preserveDrawingBuffer: true})` rendering Blockbench's `scene`
  from scripted cameras, drawn onto a 2D canvas and written with `fs`; the human's viewport never moves.
- Measuring the proxy blade's world direction (blade cube corners through `mesh.localToWorld`) was the quickest check for "is the blade level": perspective
  views from above make a level blade look tilted down.

Firearm animations (P3):
- Same rig, same builder, same export (the rig now holds 15 animations: the 11 melee ones plus
  `FirearmAnimationMapping.ALL`). `player_poses.js` adds the pose table `F9.G`, `F9.buildFirearms()` and
  `F9.proxy('sword' | 'pistol' | 'musket')`, which shows one proxy item in `right_item`. The gun proxies
  (`pistol_*`, `musket_*` cubes, hidden by default) are boxes placed where vanilla's third-person transform of the F8b
  gun models (`thirdperson_righthand` rotation `[0, 90, 40]` / `[0, 90, 43]`) puts barrel, lock and grip, computed
  through `ItemInHandLayer`'s chain: in the rest pose the barrel sits about 4 px up the arm from the hand pivot and
  points forward, 1–2° towards the hand (so it is perpendicular to the arm, like the sword).
- Animations (strips `renders/anim_pistol_*.png`, `anim_musket_*.png`; aims five frames, reloads eight):

  | Name | Length | Loop | Content |
  |---|---|---|---|
  | `pistol_aim` | 0.25 s | hold | right arm to `[-92, -5, 0]`, `right_item` `[92, 0, 5]` (barrel level and straight ahead, muzzle at eye height), torso twisted −12° (right shoulder forward), left arm hanging slightly out |
  | `musket_aim` | 0.35 s | hold | vanilla's crossbow-hold arms (right `[-84, -17, 0]`, left `[-86, 34, 0]` under the barrel), `right_item` `[84, 0, 17]` (the z cancels the arm's inward yaw: barrel level and straight ahead), twist +15° (left shoulder forward), lean 5° to the sights |
  | `pistol_reload` | 3.0 s (60 ticks) | once | gun low in front, muzzle up; the left hand pours powder (two shakes), fetches the rod at the belt, rams twice, puts it back; the gun comes level across the body and the left hand cocks the lock |
  | `musket_reload` | 5.0 s (100 ticks) | once | butt on the ground in front of the feet, muzzle at chest height; powder, rod out, ram twice, rod back; raise across the body, left hand under the barrel, then on the lock to cock |

- **Barrel along the aim.** `right_item` x +90 lays the barrel along the arm; with the arm at about −90 that is level
  and forward. Its z yaws the gun about the arm (the arm's own y would otherwise point the barrel inwards); never
  set its y and z together. Values were tuned by measuring the proxy barrel's world direction (yaw and pitch both 0
  in the final aim frames). The aim poses keep the arms level: in game the look pitch and the head's turn against
  the body are added to both arms (`PalFirearmAnimations`' adjustment modifier, like vanilla's bow pose), so the
  barrel follows the crosshair.
- **`right_item` position (`riPos`, new).** PAL translates the item bone in the arm's frame before the hand offset
  (`ItemInHandLayerMixin`: `translate(x, -y, z)` / 16, the same sense as the arm positions), so y negative slides the
  gun along the arm past the hand. The reloads use it to hold the guns lower (pistol −4, musket −8.5: the hand holds the
  fore-stock, the butt reaches the ground). `F9.makeG` keys the position on every pose of an animation, so the
  channel never interpolates against a missing keyframe; the melee animations do not key it (re-exporting them
  reproduces the committed files byte for byte).
- **Left hand on muzzle and lock.** The arms are 10 px long and straight (no bends), so a hand cannot reach every
  point of a 20 px musket. The left-arm angles were solved with a small forward-kinematics search (left arm pivot plus
  the lean/twist offsets, hand = cube bottom centre at `(-1, -10, 0)` from the pivot, Euler order ZYX on the stored
  values) towards targets measured on the proxy: a little above the muzzle (pour, rod down), higher (rod up), the
  lock (cock). The ramming stroke is therefore short (2–5 px).
- Head rotation is still never keyed: the head follows the look, so "head down to the sights" is only the torso
  lean. Cannon fuse animation: not part of P3.

Gun grip in the hand (F8h):
- The F8b third-person translations put the fist on the pistol's butt cap and the musket's butt. Only the translation
  changed (rotations, scales and first person stay): `thirdperson_righthand`/`lefthand` translation pistol
  `[0, 4, 2.5]` -> `[0, 3, 1.25]`, musket `[0, 5.5, 1.5]` -> `[0, 3, 1.5]`. Now the pistol grip enters the fist just
  behind the trigger guard and the butt cap sticks out under it; the musket's wrist sits in the fist with the lock
  just in front and the butt under the forearm. Checked through `ItemInHandLayer`'s chain (item origin in the arm frame
  `(-1, 10, -2)`, display translation `(x, y, z)` moves the item by `(-x, -z, -y)` in the arm frame: y forward, z up
  the arm) and in display mode. Renders: `renders/pistol_hand.png`, `renders/musket_hand.png` (before/after, side and
  front, plus first person). First person has no hand, so its values stayed.
- P3 consistency: the rig's gun proxies moved by the same amount (pistol y −1.25, z +1; musket z +2.5 in the
  `right_item` frame). The aims only translate the gun along/under the barrel, so `pistol_aim` and `musket_aim` are
  unchanged (re-export byte-identical, barrel still straight ahead). The reloads were re-solved: the fore-stock poses'
  `riPos` (pistol `[0, -2.4, -0.1]`, musket `[0, -6.1, -0.65]`) put the guns back exactly where P3 had them (butt on
  the ground, muzzle at the left hand), and the cocking left arms (`P_COCK*`, `M_COCK*`) were solved onto the moved
  locks (Gauss-Newton on the hand point over left-arm x/y, residual < 0.2 px).

Loaded guns: cocked-hammer variants (P6):
- `pistol_loaded` (20 elements) and `musket_loaded` (24), one project each, built from the base projects. Every
  element except the lock is byte-identical to the base (`HandMadeModelsTest.loadedGunsOverrideToTheirCockedVariant`
  checks it), and the display entries, textures and particle are copied verbatim from the base JSON, so the F8h grip
  holds for both states. The base models show the hammer down against the frizzen (fired); the variant shows it
  cocked.
- **Hammer cocked back:** `cock`, `cock_jaw` and `flint` turned together 45° to the rear about the foot of the cock
  (the tumbler on the lock plate). With vanilla angles only: the cock goes from upright `+22.5` (22.5° forward of the
  barrel's normal) to built along x and `-22.5` (22.5° behind it; an element of size (w, h) at angle a equals one of
  size (h, w) at a − 90); jaw and flint go from `-45` to `0`.
- **Frizzen closed:** the frizzen stands on the barrel's normal (`+45`) with its foot on the pan's top, and a
  0.2 px `pan_cover` (frizzen colour, `-45`, along the barrel) lies on the pan up to the frizzen face. The ramrods
  were already seated in the base models.
- **Override:** the exported `pistol.json` / `musket.json` were edited by hand (like the water tint in Q1) to add
  `"overrides": [{"predicate": {"pirates_n_ships:loaded": 1}, "model": "pirates_n_ships:item/<gun>_loaded"}]`; a
  re-export from the base project has to add it again. `FirearmsClient` registers the `pirates_n_ships:loaded`
  property for both items at client setup (1 while the `firearm_loaded` component is true). Vanilla's per-item
  `ItemProperties.register` is private; the access transformer widens it (Fabric port: twin access widener line).
- Helpers `P6.turn(cube, pivot, delta)` (turns a cube about a pivot in the xy plane, switching between y-built and
  x-built when the angle leaves ±45), `P6.apply(opts)` and `P6.export(base, name, uuid)` (compile, round to 5
  decimals, display/textures from the base JSON, write model and project) lived only in the running app.
- Renders: `renders/pistol_loaded.png`, `renders/musket_loaded.png` (top: GUI slot, empty and loaded; bottom: third
  person in the right hand, empty and loaded). In the hand the change is small (the lock sits by the fist); the GUI
  shows it clearly.

## Entities

Animated mobs and NPCs (crew member, pirate, sailor, navy soldier and officer; design.md §9) are GeckoLib models
(GeckoLib 4.9.3). Code models in the Modded Entity format stay for static entities such as the anchor. The crew member
(work package M1) defines the **humanoid rig** every humanoid mob follows; since M2 its files are Blockbench exports on
that contract (`art/models/entity/crew_member.bbmodel`, renders `renders/crew_member.png` and
`renders/anim_crew_<name>.png`).

**Files** (under `common/src/main/resources/assets/pirates_n_ships/`; GeckoLib loads every `*.geo.json` under `geo/`
and every `*.animation.json` under `animations/` of every namespace):
- `geo/<mob>.geo.json`: Bedrock geometry, `format_version` **1.12.0** (GeckoLib rejects 1.14 and 1.21 files),
  `texture_width`/`texture_height` 64.
- `animations/<mob>.animation.json`: Bedrock animations (`format_version` 1.8.0) with `geckolib_format_version` 2.
- `textures/entity/<mob>.png`: 64×64 sheet in the vanilla player skin layout.
- Blockbench project: `art/models/entity/<mob>.bbmodel`.

**Bones** (Bedrock coordinates, 1 unit = 1 pixel, y up, the model faces −z; the same as the vanilla player model with
wide arms):

| Bone | Parent | Pivot | Cubes (origin, size, box UV) |
|---|---|---|---|
| `root` | | 0, 0, 0 | none; moves the whole model |
| `waist` | `root` | 0, 12, 0 | none; bends the upper body over the hips |
| `body` | `waist` | 0, 24, 0 | −4 12 −2, 8×12×4, uv 16 16; jacket layer inflate 0.25, uv 16 32 |
| `head` | `waist` | 0, 24, 0 | −4 24 −4, 8×8×8, uv 0 0 |
| `hat` | `head` | 0, 24, 0 | as the head, inflate 0.5, uv 32 0 |
| `right_arm` | `waist` | −5, 22, 0 | −8 12 −2, 4×12×4, uv 40 16; sleeve inflate 0.25, uv 40 32 |
| `right_hand` | `right_arm` | −6, 12, −2 | none (locator for the held item) |
| `left_arm` | `waist` | 5, 22, 0 | 4 12 −2, 4×12×4, uv 32 48; sleeve inflate 0.25, uv 48 48 |
| `left_hand` | `left_arm` | 6, 12, −2 | none (locator for the held item) |
| `right_leg` | `root` | −1.9, 12, 0 | −3.9 0 −2, 4×12×4, uv 0 16; trousers inflate 0.25, uv 0 32 |
| `left_leg` | `root` | 1.9, 12, 0 | −0.1 0 −2, 4×12×4, uv 16 48; trousers inflate 0.25, uv 0 48 |

- Names are lower snake case and fixed: code looks up `head`, `right_hand` and `left_hand` by name, and animations
  address bones by name. A model may add cubes to these bones and add child bones (a beard, a tricorn on `hat`, a
  sword sheath on `body`), but never renames or re-parents the contract bones.
- `right_hand`/`left_hand` sit where vanilla's hand transform ends (arm centre, bottom face, front edge). The renderer
  (`crew/npc/client/CrewMemberRenderer`) draws the main-hand and off-hand item there with vanilla's third-person item
  transforms, so they hold items like the player does. Keep them empty and keep them at the hand when re-shaping arms.
- `head` turns with the look direction in code (`CrewMemberModel#setCustomAnimations` sets its x and y rotation after
  the animations), so **animations never key the head's x/y rotation** (JUnit `CrewMemberRigTest` checks). Animate
  `hat` or a child bone of the head instead.

**Texture layout:** the vanilla 64×64 player skin layout (Steve, wide arms), outer layer included. Any player skin
works as a test texture. Pixels of the outer layer (hat, jacket, sleeves, trousers) are transparent unless painted
(the default render type cuts out alpha). `tools/gen_entity_textures.py` is the source of `crew_member.png` (Breton
shirt, canvas slops rolled below the knee, bare feet, red dotted bandana on the hat layer, moustache); the project
embeds a copy, so re-import the texture there after running the script.

**Detail cubes (M2):** the crew member's sailor details are extra cubes inside the contract bones, not extra bones
(`CrewMemberRigTest` asserts the crew member's exact bone set; a variant with its own geo file may add child bones as
described below). They use **per-face UV** (the format allows box and per-face UV per cube) onto 2×2 colour patches
in the unused strip u 56..63, v 16..47 of the skin sheet (`PATCHES` in the script; never move a patch). Crew member:
33 cubes, 12 contract cubes (with the inflate layers) and 21 details: bandana knot and two tails (`hat`), a gold
earring (`head`), neckerchief band, flap, tip, point and knot, belt, buckle and buckle hole, a knife lying across the
small of the back in its sheath with guard and handle (`body`), rolled cuffs (arms), rolled trouser hems and toes
(legs). Details sit 0.1 to 0.4 px outside the 0.25 inflate layers; a face hidden against the head gets `null`.

**Animations** (all loop; names without prefix; rotations in degrees with the vanilla player model's signs: negative x
swings an arm or leg forward, positive x leans `waist` forward, positive z lifts the right arm outwards and the left
arm inwards, positive y swings a raised arm towards the mob's right; checked in the Blockbench preview of the
GeckoLib project). Every keyframe uses GeckoLib's `easeInOutSine` easing. Source: `models/entity/crew_member_animations.js`.

| Name | When | Content (M2) |
|---|---|---|
| `idle` | standing still | 4 s: chest swell (`body` scale up to 1.02/1.012/1.05), weight shift from the waist (z ±1.2°, x −1°), arms swaying out of phase (z 3–6°, x ±4°) |
| `walk` | the legs move (GeckoLib's limb swing) | 1 s: legs ±32°, arms ±28° in opposite phase, waist dips 0.6 px at full stride, leans 3° and twists ±3° |
| `work` | at its station while the station carries out an order (winch, pump, cannon, …) | 2 s: hand over hand; each arm reaches to −125° (high front), pulls down to −52° in 1.2 s and swings back up in 0.8 s, the left arm 1 s behind the right; arms turned 10–16° inwards so the hands meet in front of the chest; waist leans 12° and dips to 20° in each pull; right foot forward (−16°), left back (14°) |
| `sit` | riding something that seats it (boat, minecart; **not** the station seat, where it stands) | 4 s: legs −81° x, ±18° y, ±4° z (vanilla riding pose), hands resting on the thighs (−38° to −40°, 10° inwards), leaning back 3–4°, breathing |

Priority: `work` > `sit` > `walk` > `idle` (`crew/npc/CrewPose`). One controller (`body`) plays them with a 5-tick
blend. A mob with more states adds animations with new names and its own controller logic; triggered one-shots
(attack swings, a cannon fuse) go through GeckoLib triggerable animations on a second controller.

**Musket animations (M6)** in the same file, for the navy soldier (any type on the rig can play them): `musket_aim`
(0.35 s, hold on last frame), `musket_reload` (5 s, once; the soldier's controller plays it at `100 /
musket_reload_ticks` speed), `musket_shove` (0.5 s, once, a butt-stroke). Source `models/entity/musket_animations.js`
(run after `crew_member_animations.js`; `SF.make` loads it too), project `crew_member.bbmodel`; strips
`renders/anim_mob_musket_<name>.png` (front three-quarter, left side; `MSK.render`). The poses are the player's P3/F8h
firearm poses (`player_poses.js`, `F9.G`) mapped onto this rig: `right_hand` carries the gun rotation/position (the
held item turns with the locator), `waist` the twist (y) and lean (x); the arms hang from `waist` here, so the script
subtracts the waist's turn from the arm angles. Only `right_arm`, `left_arm`, `right_hand` and `waist` are keyed (legs
keep walking, the head follows the look; `SeafarerRigTest` checks). Checked with the player rig's musket proxy cubes
added to `right_hand` for the preview only (not saved): the aim's barrel is level and straight ahead (measured), the
left hand sits under the rear of the barrel (a 10 px arm cannot reach further). In game `SeafarerModel` adds the look
pitch to both arms while aiming. **Correction (M6b):** a key on `right_hand` turns the held item exactly once, as in
the Blockbench preview and as PAL's `right_item` turns the player's gun, only because `HumanoidGeoRenderer`'s item
layer replaces GeckoLib's `BlockAndItemGeoLayer#renderForBone`, which applies the bone's rotation a second time (the
layer gets the pose with the bone already rotated). With GeckoLib's version the reload's musket lay level at the hips
pointing backwards and the aim pointed 73° down. The `right_hand` position keys were never the problem: GeckoLib's
bone translation and PAL's item translation are the same move in the arm's frame (butt at the feet, muzzle at chest
height, within 1 px of the player's pose). `SeafarerRigTest` checks the drawn musket with GeckoLib's bone transforms:
aim level and straight ahead, reload muzzle up with the butt on the ground, shove butt first.

**Variants:** a pirate, sailor, navy soldier or officer is the same geometry with its own texture (a new
`textures/entity/<mob>.png` painted on the skin layout, its own renderer's `GeoModel` returning that texture) and the
shared `crew_member.animation.json`. A variant that needs extra shapes (coat tails, a tricorn, an epaulette) gets its
own `geo/<mob>.geo.json`, copied from the crew member with child bones added under the contract bones, and keeps the
shared animations working because every contract bone is still there.

**Blockbench export recipe (verified in M2, Blockbench 5.2.1, GeckoLib plugin 4.2.5):**
- **Format.** The plugin registers the format `geckolib_model` ("GeckoLib Animated Model"). Its `new()` opens a
  project settings dialog; from `risky_eval`, `setupProject(Formats.geckolib_model)` creates the project without it.
  Then set `Project.name`, `Project.geometry_name` and `Project.model_identifier` to `<mob>` (the identifier becomes
  `geometry.<mob>`), `Project.texture_width/height = 64` and `Project.visible_box = [3, 3, 1.5]`.
- **Building.** `art/models/entity/crew_member_model.js` rebuilds the whole crew member from a part list (bones,
  contract cubes, detail cubes in file coordinates); `crew_member_animations.js` then builds the four animations. A
  variant copies the model script, changes the detail list and keeps `M2.BONES` and `M2.CONTRACT`.
- **Mirrored x.** The Bedrock codec negates x on export and import, so in Blockbench the right arm sits at internal
  +x (pivot `[5, 22, 0]`), which is the viewer's left when looking at the face; the export writes −5 as the contract
  wants. The model script keeps file coordinates and flips each element (`M2.mirror`: x range, origin x, y and z
  rotations, east/west faces) before building it.
- **Geometry.** The plugin's menu action is `export_geckolib_model` (*File → Export → Export GeckoLib Model*); it
  uses Blockbench's own `Codecs.bedrock`, and the plugin hooks the codec's `compile` event to strip
  `item_display_transforms` and force `format_version` 1.12.0. So `Codecs.bedrock.compile({raw: true})` in a
  GeckoLib project gives exactly the file. Post-processing: only rounding (floats like 3.8000000000000003 to 4
  decimals); write it with `autoStringify` (tab indentation). Box-UV cubes export as `"uv": [u, v]`, per-face cubes as
  `"uv": {face: {uv, uv_size}}` (up and down with negative sizes, which GeckoLib reads), a `null` face is left out.
- **Animations.** `export_geckolib_animations` just triggers Blockbench's `export_animation_file`; the codec is
  `Codecs.bedrock.format.animation_codec`, and the plugin patches `Animator.buildFile` to add
  `"geckolib_format_version": 2`. `Animator.buildFile(null, ['idle', 'walk', 'work', 'sit'])` gives the whole file:
  names without prefix, `"loop": true` for loop mode `'loop'`, `animation_length`, each keyframe as
  `{"vector": [...], "easing": "..."}` when the keyframe has the plugin's `easing` property set (otherwise a bare
  array; a channel with one keyframe compiles to a bare vector). Keyframes are stored negated as in F9
  (rotation `[-x, -y, z]`, position `[-x, y, z]`; scale unchanged); the animation script converts.
- **Project file.** `Codecs.project.compile({raw: true})`, the texture embedded (our own) with `path` `""` and
  `relative_path` pointing at the PNG in `common/src/main/resources`; written to `art/models/entity/<mob>.bbmodel`.
- **Renders.** An extra `THREE.WebGLRenderer({preserveDrawingBuffer: true, alpha: true})` renders `scene` from
  scripted cameras with `three_grid` hidden; `Timeline.setTime(t)` plus `Animator.preview()` poses each frame,
  `Animator.showDefaultPose()` resets for the still. Strips: front three-quarter on top, left side below, evenly
  spaced frames (6, walk 5).
- `risky_eval` rejects code containing `//` anywhere, even inside a string; build such strings from `'/' + '/'`.
Run `./gradlew build` afterwards: `CrewMemberRigTest` parses the files with GeckoLib's loader and checks bones,
pivots, parents and animation names.

### Mob looks (M3-art)

The pirate, sailor, navy soldier and officer are Blockbench models on the crew rig: `art/models/entity/<type>.bbmodel`,
exported to `geo/<type>.geo.json` (identifier `geometry.<type>`) and `textures/entity/<type>.png`, renders
`renders/mob_<type>.png` (front three-quarter, left side, back three-quarter). They share `crew_member.animation.json`.
`HumanoidGeoModel` resolves the geometry from the texture name (`textures/entity/<type>.png` → `geo/<type>.geo.json`),
so `SeafarerModel` needs no change; the crew member keeps `geo/crew_member.geo.json`. `SeafarerRigTest` checks per type:
the crew member's exact bone set, parents, pivots and contract cubes, no rest rotation on a bone, every face UV on the
sheet, GeckoLib bakes it and finds every animated bone, the texture is 64×64.

- **Sources.** `seafarer_skins.js` paints the four skins (seeded, deterministic; the source of the PNGs, which
  `tools/gen_entity_textures.py` no longer writes unless named), `seafarer_models.js` holds the detail cube lists and
  `SF.make(type, repo)` (new GeckoLib project tab: paint, build with `M2.BONES`/`M2.CONTRACT`, the crew animations),
  `SF.exportAll(repo)` (geo, PNG, project file) and `SF.render(repo, file)`. Load `crew_member_model.js` only inside
  `SF.make`: it builds into whatever project is open.
- **Details are cubes in the contract bones** (no extra bones), with per-face UV: a face names a 2×2 colour patch
  (`SF.PATCHES[type]`, packed from u 56, v 16, four per row, at most 32: v 16..31) or a painted region
  (`SF.REGIONS[type]`) in the free corners of the skin layout. `SF.mirror` makes the left copy of a right-side part.
- **Pirate** (36 cubes): bandana with knot and tails (`hat`), eyepatch and earring (`head`), lapels, back tails and side
  skirts of the long coat (tails lean 9° back), red sash with knot and two ends, belt and buckle (`body`), turned-back
  cuffs, boot cuffs and toe caps. Coat on the jacket and sleeve layers with an open front.
- **Sailor** (32): knitted cap of brim, crown and a slouched fold (`hat`), black neckerchief, rope belt with knot and
  ends, rolled sleeve cuffs, slop hems over the trousers layer, shoes with brass buckles.
- **Navy soldier** (39): tricorn of a crown, a back wall and two front walls turned ±28.4° about y meeting at the front
  point, each with a floor plate, a black cockade on the left wall (`hat`); queue and bow (`head`); red collar, brass
  plate where the white cross belts (painted on the jacket layer over a buff waistcoat) cross, cartridge box, coat tails
  and skirts with red turnbacks (`body`); red cuffs; shoes, buckles, gaiter tops (gaiters on the trousers layer).
  The tricorn's crown corners show past the front walls from above (a square head under a triangle).
- **Navy officer** (55): bicorne worn athwart, front and back flaps in three steps each (centre 6 px high), leaning 18°
  towards each other, outer face 0.35 px in front of the head so the head never pokes through, end plates, gold loop,
  button and black cockade on the front flap (`hat`); queue and bow; collar, crimson sash with knot, ends and gold
  tassels on the left hip, coat tails and skirts edged in gold (`body`); epaulettes with fringe on three sides and
  gold-ringed cuffs (arms); boot tops and toe caps.

Sheet allocation (outside the box-UV contract areas; the same free corners in every sheet, used per type):

| Area | Pirate | Sailor | Navy soldier | Navy officer |
|---|---|---|---|---|
| 0..8 × 0..8 | | | tricorn brim outer (0,0,8,4), inner (0,4,8,4) | bicorne flap outer (0,0,8,5), inner (0,5,8,3) |
| 24..32 × 0..8 | | cap brim, crown, fold (3+3+2 rows) | crown sides (3 rows), gaiter top (3 rows) | crown sides (3 rows), cuff (3 rows) |
| 32..40 × 0..8 | | cap top | crown top | crown top |
| 0..4 × 16..20 | lapel | | | |
| 12..20 × 16..20 | boot cuff (8×3) | | collar (8×2) | epaulette top (8×4) |
| 36..44 × 16..20 | coat cuff (8×3) | | cuff (8×3) | fringe (8×2) |
| 56..64 × 16..32 | 22 patches | 17 patches | 17 patches | 17 patches |
| 56..64 × 32..48 | tail back (4×9), side (4×9), tail inside (8×2) | slop hem (8×3) | tail back (4×7), side (4×7) | tail back (4×8), side (4×8), sash (8×2), boot top (8×2) |

### Shark rig (M4)

The shark (design.md §9, §12) is a GeckoLib model on its own rig. M4 shipped a script placeholder
(`tools/gen_shark.py`, removed in M4-art); since M4-art the three files are Blockbench exports of
`art/models/entity/shark.bbmodel` (see "Shark (M4-art)" below), made with the recipe above; `SharkRigTest` checks
the contract. The placeholder columns below record what M4 had.

**Files:** `geo/shark.geo.json` (`format_version` 1.12.0, identifier `geometry.shark`, `texture_width` 64,
`texture_height` **32**), `animations/shark.animation.json` (1.8.0, `geckolib_format_version` 2),
`textures/entity/shark.png` (64×32). The UV layout is free: the placeholder uses per-face UV into an atlas the script
packs; a Blockbench model brings its own layout with its texture.

**Size:** 2.4 blocks long (about 38 px from snout to tail fin), body about 10 px wide and 10 px tall. The hitbox is
0.9 × 0.6 blocks in the middle of the body (like the dolphin, the model is longer than the box). The shark faces −z,
+x is its left (file coordinates, as for the humanoids).

| Bone | Parent | Pivot | Placeholder cubes |
|---|---|---|---|
| `root` | | 0, 5, 0 | none; code pitches it with the swimming direction (the entity's x rotation) |
| `body` | `root` | 0, 5, 0 | torso −5 0 −8, 10×10×14 |
| `head` | `body` | 0, 5, −8 | snout −4 3 −18, 8×6×10; two eye cubes |
| `jaw` | `head` | 0, 3, −9 | lower jaw −3.5 1 −17, 7×2×8 (hinge at its back end) |
| `tail_1` | `body` | 0, 5, 6 | −3.5 2 6, 7×7×8 |
| `tail_2` | `tail_1` | 0, 5, 14 | −2 3 14, 4×5×6, plus the upper and lower lobes of the tail fin |
| `fin_left` | `body` | 5, 2, −4 | pectoral fin 5 1.5 −6, 8×1×5 |
| `fin_right` | `body` | −5, 2, −4 | pectoral fin −13 1.5 −6, 8×1×5 |
| `fin_dorsal` | `body` | 0, 10, −2 | −0.5 10 −4, 1×8×6 |

- Names, parents and pivots are fixed (code looks up `head` and `root`, animations address bones by name). A model may
  add cubes and child bones (gill flaps, a second dorsal fin, teeth as cubes) but never renames or re-parents these.
- **Code drives** `head` (x and y rotation follow the look direction, clamped to ±30°, `mob/client/SharkModel`) and
  `root` (x rotation = swimming pitch, clamped to ±60°). Animations never key the rotation of either (the test checks).

**Animations** (file convention as above: positive x on `jaw` drops its front, i.e. opens the mouth):

| Name | Loop | When | Placeholder content |
|---|---|---|---|
| `swim` | loop, 1 s | moving faster than 0.02 blocks/tick; played faster with speed (×0.6 to ×2.5, ×1 at 0.12 blocks/tick) | `tail_1` y ±14°, `tail_2` y ±20° a quarter behind, `body` y ±3° against the tail, pectoral fins z ±6° |
| `idle` | loop, 3 s | hovering | the same sway at ±6°/±9°/±1.5°, fins ±4°, the jaw breathing 4° |
| `bite` | play once, 0.5 s | triggered at the moment of a bite (second controller `bite`, a synced bite counter) | `jaw` opens to 38° at 0.2 s and snaps shut at 0.3 s; `body` lunges 1.5 px forward |

One controller (`body`, 5-tick blend) switches `swim`/`idle`; the `bite` controller only plays the triggered
animation, on top.

**Texture (placeholder):** grey-blue speckled back and upper sides, a light line, white belly (countershading),
darker fin edges, black eyes (separate eye cubes on one black texel), dark-red mouth lining with white teeth around
the rim of the jaw and the roof of the mouth, a row of teeth on the snout's front edge, three gill slits.

### Shark (M4-art)

Blockbench model, skin and animations on the shark rig; the M4 placeholder script is gone. Sources:
`art/models/entity/shark.bbmodel` (75 cubes), `shark_model.js` (part list, UV packer, skin painter, export) and
`shark_animations.js` (keyframe table); render `renders/shark.png` (left side, three-quarter, bite at 0.22 s, swim
from above).

- **Rebuild.** New GeckoLib project (`setupProject(Formats.geckolib_model)`, name/geometry/identifier `shark`,
  texture 64×32), then in `risky_eval`: `window.SH = {REPO: '<repo>'}`, eval `shark_model.js`, `SH.build()`,
  `SH.paint()`, eval `shark_animations.js`, `SHA.build()`, `SH.export()` (writes the geo, the animation file, the PNG
  and the project). Re-running gives byte-identical geo, animation and PNG files (checked); the project file differs
  only in uuids. **Select the shark tab first and check `Project` in the same call**: `risky_eval` acts on whichever
  tab is selected, and a tab switch between two calls once made the build land in another open project.
- **Coordinates.** The part list uses Blockbench's internal coordinates (x mirrored against the file, so `fin_left`
  sits at internal −x); the shark is symmetric, so only the pectoral fins care. Only the pectoral fins have cube
  rotations (`[0, ±25, ±15]` internal: swept back, tips lowered). Other fins are stepped layers, which read better
  than rotated slabs at this size.
- **Cubes per bone:** `body` 16 (three sections, each a wide box plus a narrower taller core for the flat belly and
  rounded back; five gill slits per side as 0.16 px strips), `head` 22 (skull, brow, throat, snout in three steps,
  pink palate as the mouth line, two eyes, 13 upper teeth hanging from the snout), `jaw` 14 (jaw, jaw tip, 12 lower
  teeth that sit inside the palate while the mouth is shut), `fin_left`/`fin_right` 2 each (base, darker tip),
  `fin_dorsal` 5 (stepped, swept back), `tail_1` 6 (two tapering sections, second dorsal and anal fin, two steps
  each), `tail_2` 8 (peduncle, keel, upper lobe in four steps up to y 13.6, lower lobe in two). Length snout tip
  z −18.6 to tail tip z 21.8 (40 px, 2.5 blocks); width at the gills 10 px, pectoral span 27 px.
- **Sheet (64×32, per-face UV).** `SH.pack` packs one rectangle per visible face on shelves at one texel scale for
  the whole model (0.8 texel per pixel: the largest that fits), leaving the bottom-right 8×2 corner for patches.
  Mirror faces share a rectangle with u reversed: the east face of a centred cube uses its west face's rectangle,
  the right pectoral fin uses the left one's. 2×2 solid patches at v 30..31: tooth white u 56, eye black u 58, gill
  pale u 60 (u 62 free). About a quarter of the sheet is still free (magenta in a viewer), so a later detail can
  get its own area without a repack.
- **Painting.** `SH.paint` reads each cube's mesh (4 vertices per face in Blockbench's face order east, west, up,
  down, south, north) in the rest pose, maps every texel centre to its 3D point and colours it from position and
  face normal: grey-blue back (darker along the spine, sparse darker specks from a texel hash), a soft countershade
  to the white belly around y 4.2 (4.5 on the tail), pink mouth (darker deeper in), darker fin edges and tips,
  nostrils under the snout tip. Shared rectangles are painted twice and must agree (`conflicts: 0`).
- **Animations** (file values; `SHA.wave` keys a sine at its quarter points with `easeOutSine` into a peak and
  `easeInSine` into a zero crossing, which makes GeckoLib's curve an exact sine):

  | Name | Content |
  |---|---|
  | `swim` (1 s, loop) | `tail_1` y ±13°, `tail_2` y ±20° a quarter behind (S-curve), `body` y ±3° against the tail, pectoral fins z ±6° (opposite signs, both tips rise together), `fin_dorsal` z ±2.5° |
  | `idle` (3 s, loop) | the same sway at ±6°/±9°/±1.5°, fins ±3.5°, jaw breathing to 4° |
  | `bite` (0.5 s, once) | `jaw` x to 35° at 0.18 s (`easeOutSine`), held to 0.26 s, snapped shut at 0.32 s (`easeInQuad`); `head` position dips and thrusts (`[0, −0.6, −0.8]` at 0.32 s); `body` position lunges 1.5 px forward |

  `head` and `root` rotation are never keyed; the bite keys only positions on `head` and `body`, so it never fights
  the body controller's yaw or the code's head turn.

### Kraken (K1b)

Blockbench model, skin and animations on the kraken rig of K1a; the placeholder in `tools/gen_kraken.py` is gone (the
script now writes only the two loot sprites, and only when they are named, and keeps the rig contract in its
docstring). Sources: `art/models/entity/kraken.bbmodel` (405 cubes), `kraken_model.js` (part list, UV packer, skin
painter, V1 check, export, render) and `kraken_animations.js`; render `renders/kraken.png` (side, front,
three-quarter from a deck, grab at 0.45 s, from below; the arms lean out 50° in the pictures as the code aims them in
game, nothing is keyed for that).

- **Rig contract** (KrakenRigTest): 1 px per unit, y up, faces −z, +x (file) is its left, hit box 56 × 56 px. Bones
  `root [0,0,0]` → `mantle [0,30,0]` → `eye_left [12,40,−20]`, `eye_right [−12,40,−20]`; `tentacle_<i>_1` (i = 0..7)
  under `root` on a ring of radius 22.4 at y 20, angle a = (i + 0.5)·45°, x = −sin a·22.4, z = −cos a·22.4, with
  `tentacle_<i>_2` 12 px and `tentacle_<i>_3` 24 px above it (the ring is `Kraken#anchor`'s). Rest: arms straight up,
  34 px. Code aims and stretches `root` and every `tentacle_<i>_1`: never key them. Animations `idle` (loop), `surface`
  and `grab` (once), `submerge` (hold). Texture 128×64. Only cubes were added, no bones, no bone rest rotations.
- **Rebuild.** In `risky_eval`: `setupProject(Formats.geckolib_model)`, name/geometry/identifier `kraken`, texture
  128×64, `visible_box [8, 6, 1.5]`; `window.KRK = {REPO: '<repo>', UUID: Project.uuid}`; eval `kraken_model.js`,
  `KRK.build()`, `KRK.paint()`; eval `kraken_animations.js`, `KRKA.build()`; `KRK.export()`; `KRK.render()`. Every
  entry point throws unless the selected project is `KRK.UUID`. Two rebuilds in fresh tabs gave byte-identical geo,
  animation and PNG files, and project files equal apart from uuids (cube colours are set, the texture size is
  written explicitly because the image may still be loading). **Do not name the namespace `KR`**: Blockbench has a
  global `KR` of its own, which shadows `window.KR` inside `risky_eval`.
- **Coordinates.** The body is listed in file coordinates and mirrored in x for the build. The arms are built in
  Blockbench's internal space from one canonical arm (axis on x = z = 0, sucker side +z): arm i is turned about its
  axis by cube rotation y = −a so the suckers face the body's axis, and moved to its ring point; the curled tip cubes
  add a tilt x towards the sucker side (cube rotation `[tilt, −a, 0]`, ZYX like GeckoLib). Measured in the preview:
  the sucker face is the inner one, the rest-pose tip curls 3.7 px inwards.
- **Cubes per bone.** `mantle` 93: head of five round layers (y 12..52, radius 10.8 → 14.6 → 18.4 → 21.4 → 18.2),
  mantle of eight layers tapering from radius 15.6 at y 52 to a 2 px point at y 94.6, each round layer a square core
  plus four side slabs (a rounded square; slabs 0.1 px longer than the core at both ends, so no shared planes); two
  fins of 12 strips each (rhombus, y 62.3..86.3, out to x ±24.3, 2.2 px thick at the middle, thinner at the ends);
  a ring of lips (5) and the beak (upper mandible, lower mandible, hook; black) at the centre of the arm crown on the
  underside. `eye_left`/`eye_right` 4 each: lid rim 11.2 × 11.2, iris disc 9.6 (gold, pale ring, dark edge), pupil
  4 × 4, glint. Per arm: `_1` 12 (two cubes 7 and 6.2 px wide, 10 suckers), `_2` 12 (5.4 and 4.6 px, 10 suckers),
  `_3` 14 (3.8 px straight, then 3.2, 2.5, 1.7 px tilted 14°, 32°, 56°; 10 suckers). Suckers are 1.2 → 0.6 px discs in
  two rows on the inner face, 0.35 px proud.
- **Sheet (128×64, per-face UV).** `KRK.pack` packs one rectangle per primary face on shelves at two densities: fine
  parts (arms, eyes, beak, lips) 1 texel per px, the body 0.68 (its up/down faces 0.6 of that, 0.41: rims seen from
  above and the pale underside). Sharing: all eight arms use arm 0's rectangles; the right eye and fin are x-mirrors of
  the left; centred cubes reuse west for east and north for south (u reversed); each round layer repeats its sides
  four times (core west = north, side slab's outer face = front slab's front, side slab's front = front slab's side;
  the crown pattern has 8 spots per turn, so the repeat has no seam). Faces fully inside another body cube are left
  out. Areas (texels): body sides 3005, body tops and bottoms 1625, fins 191, eyes 646, beak and lips 400, arms 1074;
  patches at u 120..127, v 60..63 (sucker 4×4 pale with a dark rim at u 124, sucker side, pupil, glint, beak 2×2);
  1219 texels free.
- **Painting** (`KRK.paint`, per texel from the rest-pose mesh like the shark): dark reddish-brown skin with a two-octave
  value-noise mottle, paler underside below y 23 and on down faces, a crown pattern above y 69 (rows of pale spots
  with dark rings, offset every other row), fins darkening to their outer edge, arms with a pale sucker face and sides
  lightening towards it, darker tips; ribbed pink lips, black beak with dark-grey flecks; eye rim dark with a lighter
  lid ring.
- **V1 by construction.** `KRK.lint()` (run by `KRK.build`) rejects two unrotated cubes of the body, or of the
  canonical arm, with same-facing faces closer than 0.05 px and overlapping in area.
- **Animations** (`KRKA`; the fins are mantle cubes, as the rig has no fin bones, so they spread, fold and ripple by
  the mantle's x scale). Arm bends are quaternions about the ring's tangent ("curl", the tip moves to the body's axis,
  the sucker side closes) and radial axis ("side"), stored as internal ZYX Euler keys. Smooth sums of waves are sampled
  every 0.5 s with linear keys.

  | Name | Content |
  |---|---|
  | `idle` (4 s, loop) | `mantle` scale: breathing y +3.5 % (one cycle), x +2 % plus a ±3 % ripple (two cycles); a blink at 3 s (eyes scale y 0.12 for 0.2 s); every `_2` sways in an ellipse curl ±8° / side ±5°, `_3` ±12° / ±7° a quarter behind; neighbouring arms a quarter apart |
  | `surface` (2 s, once) | `mantle` rises 3 px with a 0.8 px overshoot at 1.2 s, scale from folded `[0.84, 0.9, 0.86]` to spread `[1.08, 1.04, 1.04]` at 1 s and back to 1; eyes open at 0.8–1.1 s; arms unfurl from curl 45° (`_2`) / 70° (`_3`) |
  | `grab` (1 s, once) | every `_2` curls 38°, `_3` 62° by 0.35 s (`easeOutQuad`), held to 0.55 s, relaxed by 1 s |
  | `submerge` (2 s, hold) | `mantle` sinks 3 px and contracts to `[0.82, 0.86, 0.88]` (fins folded), eyes close by 0.6 s, arms draw in (curl 20° / 32°) |
## GUI kit (U1)

Screen and HUD sprites come from `tools/gen_gui_textures.py` (standard library only; run it with `python3`, commit
its output). They live in vanilla's GUI sprite atlas under
`common/src/main/resources/assets/pirates_n_ships/textures/gui/sprites/{panel,widget,icon,hud}/`, each with a
`.png.mcmeta` (`nine_slice` for frames, panels, buttons, fields, tags, dividers and the scrollbar; `stretch` for
icons and the stamina fills). The ids are in `core/client/gui/GuiSprites`; `GuiTexturesTest` fails while the
committed files differ from a fresh run. Pixel art at 16×16 scale, no anti-aliasing, two or three shades per material;
nine-slice edges and centres repeat cleanly because vanilla tiles them.

| Material | Dark | Mid | Light | Extra |
|---|---|---|---|---|
| Wood (frame, header) | 52,34,20 | 76,51,31 | 100,68,42 | bevel 132,94,58; board 60,40,24 / 50,33,20; outline 24,15,8 |
| Brass (studs, buttons, dividers, trough) | 134,94,34 | 198,150,58 | 238,204,112 | outline 82,54,18; gold 252,216,78 |
| Parchment (panels, cards, tags) | 196,170,124 | 222,201,156 | 238,224,188 | edge 160,128,84 |
| Red wax (seal, own card, alert) | 104,18,18 | 156,32,28 | 206,72,56 | |
| Navy (anchor badge) | 28,40,74 | 48,70,124 | 92,120,180 | |
| Inset (fields, track, trough) | 20,13,8 | 34,23,14 | | |
| Stamina fill | 112,18,16 (deep red) … 240,166,48 (amber) | eight 10 px bands | top row +34, bottom row ×0.72 | |

Text: dark ink `#2C2018` (faded `#7A6852`) without shadow on parchment; light `#EEE0BC` or brass `#F4D27A` with
shadow on wood.

## Ship templates (W0)
Prebuilt ships (the starter sloop now, shipwright orders later) are built in a creative world and exported with
WorldEdit as Sponge schematics. `art/schematics/<name>.schem` is the source; `tools/schem_to_structure.py` (standard
library only) turns it into the vanilla structure `common/src/main/resources/data/pirates_n_ships/structure/ships/<name>.nbt`
(`pirates_n_ships:ships/<name>`), and both files are committed. `SchemToStructureTest` fails while the committed
`starter_sloop.nbt` differs from a fresh conversion.

1. **Build** the ship with its bow pointing **north** (−Z), or note the bow direction for the definition. Put the
   **helm** on it (and the yards, cleats, winch and so on that it should sail with): a template without a helm can
   be placed but not assembled.
2. **Export:** stand anywhere, select the ship with the wand (`//pos1`, `//pos2`, keep the selection tight around
   the ship; air in the box is dropped), then `//copy` and `//schem save <name>` (WorldEdit 7.2+ writes Sponge v3,
   older versions v2; both work). The file lands in `config/worldedit/schematics/` (`.minecraft/config/...` on the
   client). Copy it to `art/schematics/<name>.schem`.
3. **Convert:** `python3 tools/schem_to_structure.py` (all schematics) or `python3 tools/schem_to_structure.py
   art/schematics/<name>.schem`. It prints the size, the block count and the template position of every helm.
   Block entity data (chest contents, names) is kept; entities (item frames, armor stands) are not.
4. **Define** the template in `ship/template/ShipTemplates` (datagen writes
   `data/pirates_n_ships/pirates_n_ships/ship_template/<name>.json`): the structure id, the name key (and its English
   name in `ShipTemplateCommands.lang`), the helm position the converter printed, the **waterline** (the template row
   that sits at the water surface: the lowest dry row is one above it; default helm y − 1) and the price. Run
   `./gradlew :neoforge:runData`.
5. **Test** in game: `/pirates ship templates`, then stand at the shore facing the sea and run
   `/pirates ship place <name>` (or `... assemble` to make it a ship at once, `... force` to overwrite what is in
   the way). The ship appears 3 blocks ahead, bow away from you, centred on you.

Keep templates under the ship block limit (2048 by default), keep every block connected to the helm (the assembler
gathers only connected blocks), and leave no loose terrain blocks (dirt, sand, stone) in the selection: they never
become part of a ship.
