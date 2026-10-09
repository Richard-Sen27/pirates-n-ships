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

Workflow notes (helm split, HELM1):
- `helm.bbmodel` has two groups: `wheel` (rim, spokes, handles, hub, hub cap; 39 elements, origin = the axle at
  (8, 13, 3.75) px) and `pedestal` (axle, head, post, foot, base, cap; 7 elements). Three files are exported from it
  with `Codecs.java_block.compile()` and the other group's cubes set to `export = false`: `block/helm.json` (pedestal,
  the block model), `block/helm_wheel.json` (wheel, drawn and turned about the axle by `HelmWheelRenderer`) and
  `block/helm_item.json` (both, with the `gui` display; the item model points at it). `HandMadeModelsTest` checks that
  `helm_item` is exactly `helm_wheel` followed by `helm`.
- Render `renders/helm_wheel.png`: the helm at wheel 0° and +60° (clockwise from the helmsman's side = starboard),
  drawn with a `THREE.WebGLRenderer` in `risky_eval` (wheel meshes turned about the axle for the shot only) and
  written with `fs`.

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
  The pump was split by PMP1 into body, rod and handle, see "Bilge pump handle (PMP1)" below.
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
- **Cannon** (F7g; split by CAN2, see "Cannon barrel tilt (CAN2)" below; `cannon_rear` stays a generated
  particle-only model). Everything is drawn from the master block, muzzle north. Barrel axis at x 8, y 14
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

Cannon barrel tilt (CAN2):
- **Three groups per project.** `cannon.bbmodel` (140 elements), `cannon_powder.bbmodel` (145) and
  `cannon_loaded.bbmodel` (153) each hold `barrel` (origin = the trunnion axis (8, 14, 8): tube, rings, cascabel,
  vent, bore, trunnions; powder adds the priming `quill` in the vent, loaded the quill and the `ball`), `quoin`
  (origin (8, 5, 22.9): the wedge and its handle) and `carriage` (origin (8, 0, 8): cheeks, cap squares, straps,
  bed, stool bed, transoms, axletrees, trucks, rings; powder and loaded add the rammer). Exports
  (`Codecs.java_block.compile()` with the other groups' cubes set to `export = false`): from `cannon` the block
  models `cannon_carriage`, the stand-alone `cannon_barrel` and `cannon_quoin`, and the item model `cannon` (all three
  groups, with the display entries); from `cannon_powder` `cannon_carriage_rammer` (the block model for powder and
  loaded) and `cannon_barrel_powder`; from `cannon_loaded` `cannon_barrel_loaded`. `HandMadeModelsTest` checks that
  `cannon` is exactly barrel + quoin + carriage and that each variant starts with its base's elements.
- **Drawn by `CannonBarrelRenderer`:** the barrel of the load state turned about the trunnion axis by the elevation
  (a positive angle lifts the muzzle; `Axis.XP`), the quoin squeezed about the stool top (y 5) and slid up to 1.7 px
  back as the breech drops (`CannonBarrelPose.quoin`: its top keeps the 0.25 px rest gap under the barrel's
  underside); the model shows −20° to 20°.
- **Carriage changes for the tilt:** cheeks 1.3 px thick (x 2.5..3.8 and 12.2..13.5, were 1.8), so the base ring
  (D 8.3) passes between them; the old quoin bed (y 4.3..7.6) is a low `stool_bed` (y 4.3..5), so at 20° the breech
  comes down to 0.1 px above it; the quoin (y 5..9.6, its hidden bottom face left out) became its own group.
- **Regenerating:** `python3 tools/gen_cannon_models.py <scratch>`; in `<scratch>` run `tools/lint_models.py --fix`
  with `--mirror cannon_barrel.json cannon_barrel_powder.json`, `--mirror cannon_carriage.json
  cannon_carriage_rammer.json` and `cannon_quoin.json` in one call, and `--mirror cannon_barrel.json
  cannon_barrel_loaded.json` on a second fresh output (the lint's mirror chain cannot take two variants of one base in
  one call); copy the loaded barrel over, then `gen_cannon_models.py --compose <scratch>` writes the item model from the
  fixed parts. Copy the seven cannon files to `models/block/` (scratch names have no project, so the lint never
  touches `art/models/`). The projects were rebuilt from the copied files (`CAN2.load(name, counts)` in `risky_eval`:
  new `java_block` project, `Codecs.java_block.parse` of a full variant JSON composed from the part files, textures
  from `art/vanilla/` with namespace `minecraft` and folder `block`, three groups filled by element count), and every
  group's export was compared element by element with the files before saving with `fs`.
- **Render:** `renders/cannon_tilt.png`: the loaded gun at 0°, 10° and 20° (three-quarter front, side, rear),
  drawn with a `THREE.WebGLRenderer` from clones of the cube meshes posed like the renderer (barrel turned, quoin
  squeezed and slid). `renders/cannon.png` and `cannon_loaded.png` still show the F7g carriage (thicker cheeks, tall
  quoin bed).

Bilge pump handle (PMP1):
- **Three groups in `bilge_pump.bbmodel`** (27 elements, geometry unchanged from F7e): `body` (origin (8, 0, 8):
  foot, intake ring, casing, bands, cylinder, iron lip, the cheeks and pin of the fulcrum, spout and drips; 24
  elements), `rod` (origin (8, 16.6, 8): the piston rod, 1) and `handle` (origin = the pin (8, 16, 11): the brake beam
  and its grip, 2, both still rotated 22.5° about x through the pin, which is the top of the stroke and the rest pose).
  Exports (`Codecs.java_block.compile()` with the other groups' cubes set to `export = false`, numbers rounded to
  5 decimals so float noise from the compile does not reach the files): `bilge_pump` (body, the block model),
  `bilge_pump_rod` and `bilge_pump_handle` (stand-alone, drawn by `PumpHandleRenderer`), and `bilge_pump_item` (all
  three, the only one with the `gui` display entry; datagen points the item model at it). `HandMadeModelsTest` checks
  that `bilge_pump_item` is exactly body + rod + handle and that the handle's rotation matches `PumpHandlePose`.
- **Drawn by `PumpHandleRenderer`:** the handle turned about the pin by the swing (a positive swing lowers the grip:
  `Axis.XP` by −swing), the rod slid down by `PumpHandlePose.rodLift` so its top stays under the beam's underside (it
  touches it at rest, y 16.6). **Clearance:** the beam's underside passes the front edge of the iron lip (z 5.3,
  top y 14.4) down to a handle angle of about −9.7°, so the swing is capped at `PumpHandlePose.MAX_SWING` (32.2°, 0.05 px
  above the lip; the config allows at most 30); the client default `pump_visuals.stroke_degrees` is 28 (handle at −5.5°, 0.46 px above the lip), the
  rod then sits 1.48 px lower and still shows above the lip. The pin sits between the cheeks, the beam between them, so
  the short arm behind the pin rises freely.
- **Rebuild:** open the committed project (`loadModelFile` with the file's content read by `fs`), keep `Project.uuid`
  in a `window.PMP1` object and check it before every call; `PMP1.exportAll()` wrote the four files and the project
  was saved with `fs` (textures without `source`, `relative_path` `../vanilla/<name>.png`).
- **Render:** `renders/bilge_pump_stroke.png`: the pump at rest (top of the stroke, left) and at the bottom of the
  default 28° stroke (right), front three-quarter above and west side below, drawn with a `THREE.WebGLRenderer` from
  clones of the cube meshes posed like the renderer (handle turned about the pin, rod slid down).

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

Item models (ART1a, ART1b):
- **Block and item with the same name** (the hull patch): the block model and the item model are both
  `<name>.json`, but in different folders, so the projects need different names: `hull_patch.bbmodel` (the block)
  and `hull_patch_item.bbmodel` (the item). `tools/lint_models.py` maps the item to its project in `PROJECT_NAMES`;
  the module's datagen writes only the block state and calls `m.handMadeItem(item)`. A block item whose block model
  stays generated (the map tile, whose placeholder block model is still datagen's) needs no `_item` project: the
  item project is `map_tile.bbmodel` and lint finds it by name.
- **Hull patch** (block 45 elements, item 11; vanilla `spruce_planks`, `dark_oak_planks`, `black_concrete`, `anvil`,
  `stripped_dark_oak_log`): the board panel on each side stands about 1.5 px **proud** of the block on purpose, so it
  reads as nailed on over the hole from outside and inside the hull; collision stays a full block. The item is the
  panel alone, with a `gui` entry `[20, -30, 0]` / 0.85.
- **Rope** (28 elements, `palette_2` rope colours and `palette_4` twine whipping) and **chart** (14 elements,
  `palette`, `palette_3` paper and biscuit, `palette_4` cord; particle `palette_3`): no new colours.
- **Flag bundles** (`navy_flag` 26 elements, `merchant_flag` 24, `jolly_roger_flag` 25; one project each, one shared
  part list): the cloth rolled on a halyard toggle, built upright along y around x = z = 8 and turned `z -45` about
  `[8, 8, 8]` like the cloth bolt, so the vanilla handheld slots hold it like a tool (lower knob in the fist). Parts:
  the toggle with two crossed-box knobs and a rope eye on top (`wood`, `wood_dark` of `palette`, `rope` of
  `palette_2`), the roll and the outer fold as two crossed boxes each (the second box 0.05 px shorter at its ends),
  two `rope_dark` lashings, the fold's loose end in two steps to the lower right, and the design on the fold: navy a
  white band all round plus a vertical arm on the front and back, merchant a red band, Jolly Roger a skull (eye holes
  0.08 px proud of it) and a bone, 0.15 px proud of the front. The colours follow the flown cloth
  (`tools/gen_flag_textures.py`), so the navy bundle is blue with a white cross. On crossed boxes the **front is the
  deep box's face** (z = 8 + w/2), not the wide box's: inlays go there. A `gui` entry `[0, 20, 45]` /
  `[-1.4, 0, 0]` / 0.9 stands the bundle upright in the slot (z +45 undoes the element turn; y 20 shows its side);
  third person scale 0.75, the other slots vanilla handheld / generated.
- **Map tile** (23 elements): a wooden board and frame (`wood`, `wood_dark`, `iron_dark` nails) round a parchment
  (`paper` of `palette_3`) 0.3 px below the frame, land and sea halves split by an ink coastline, an island and shoals
  on the sea, a red X of two bars at +45 and -45 (the second 0.06 px above the first) and three route dots; built
  facing south like a sprite. Display as the doubloon (flat in the hand, `ground` lying flat), `gui` `[25, -20, 0]` /
  0.95 (top edge and right side show).
- **`palette_5.png`** (texture `#4`, same script) holds the flag and map colours and, since ART1c, the kraken beak's
  horn in the two former spare cells. It is full: the next new colour needs a `palette_6.png`.

  | v \ u | 0 | 4 | 8 | 12 |
  |---|---|---|---|---|
  | 0 | navy_light | navy | navy_dark | flag_white |
  | 4 | flag_white_shade | flag_red | flag_red_dark | flag_black |
  | 8 | flag_black_shade | flag_black_light | map_sea | map_sea_dark |
  | 12 | map_land | map_ink | horn | horn_light |
- Built like F8i: a Python part list (patch names resolved through `tools/gen_item_palette.py`'s `SHEETS`) wrote one
  JSON spec per model, and `ART1B.load(name)` in `risky_eval` built it cube by cube in a new `java_block` tab
  (palette textures from `fromPath` with `id`, `folder` and `namespace` set again, display slots into
  `Project.display_settings`); `ART1B.export(name)` wrote `Codecs.java_block.compile()` (plus `credit` and
  `gui_light`) and the project. Script and helpers are not committed; rebuild them from these notes.
- Renders: `renders/navy_flag.png`, `merchant_flag.png`, `jolly_roger_flag.png` (three-quarter view, GUI; navy also
  in the right hand), `map_tile.png` (three-quarter view, GUI, right hand).
- Sprites removed: `textures/block/hull_patch.png` (with its script `tools/gen_hull_textures.py`, which drew nothing
  else) and `textures/item/rope.png` (from `tools/gen_sailing_textures.py`; `textures/block/rope.png` stays, the stay
  renderer uses it). `textures/item/chart.png` and `textures/item/map_tile.png` stay for now because
  `ChartTexturesTest` requires them. The flag sprites (`item/<flag>.png`) and their code in
  `tools/gen_flag_textures.py` were removed in ART1c; the script now writes only the flown cloth (`block/flag_*.png`).

Item models (ART1c: kraken beak and ink, carpenter's hammer, saw, nails, Shipwright's Toolkit, hook-loaded musket):
- One `java_block` project per model, no block of the same name, so lint finds each by name. Built like ART1b from a
  Python part list (patch names through `SHEETS`) written as model JSON, then rebuilt cube by cube in a new tab
  (`ART1C.load(name)`: palette textures from `fromPath` with `id`, `folder`, `namespace`; display slots into
  `Project.display_settings`) and exported with `Codecs.java_block.compile()` plus `credit`, `gui_light`, the particle
  and the source's full display entries (compile drops default rotation/translation/scale, the committed files keep
  them like the older items). Helpers not committed; rebuild them from these notes.
- **Bar helper.** `seg(cx, cy, angle, length, width, z0, z1)` places a bar whose axis points `angle` degrees clockwise
  from up, in 22.5 degree steps: built along y and rotated `-angle` for -45..45, built along x and rotated
  `90 - angle` (or `-90 - angle`) otherwise. `dseg` takes a diagonal frame instead (v along 45 degrees up-right, u
  across towards the lower right), which is how all six new tools are laid out. Curves walk a spine of such bars (the
  beak: 45, 45, 45, 67.5, 90, 112.5, 135 degrees, each 0.5 px longer than its step so the joints close).
- **Kraken beak** (24 elements, `palette_5` only, handheld slots): each spine section is two crossed boxes (wide and
  flat, narrow and deep) so it reads round from the side; base sections `horn`, the hook `flag_black`; a lower jaw on
  the lower-right side under the hook; `horn_light` rims round both open ends.
- **Kraken ink** (22, `item/generated` slots, particle `palette_2`): stacked crossed-box sections like the rum
  bottle, body `gunmetal_dark` (blue-black), neck tied with `cord` (palette_4) and a `twine`/`cord` end; gloss is a
  `steel_light` streak and a `gunmetal_light` dot 0.1 px proud of the deep boxes' south faces (the crossed-box front),
  plus a `navy_dark` sheen on the right.
- **Carpenter's hammer** (13, handheld): `wood` handle (two crossed boxes, `wood_dark` grip end), head across the
  handle top, `steel_light` striking face on the upper-left end (faces down in third person like an axe blade),
  claw on the lower right: a root bar at 135, then two prongs (split 0.24 px in z) at 157.5 and 180 bending back
  towards the handle.
- **Saw** (22, handheld, particle `palette_2`): blade in three steps (the toothed upper-left edge steps in towards
  the tip, the back stays straight), a `steel_light` back strip, teeth as axis-aligned 0.72 px squares on the 45
  degree edge (they stand out as diamonds), a closed `walnut` handle of four bars round a finger hole, a heel horn and
  two brass screws through the front bar.
- **Nails** (26, `item/generated`, particle `palette_2`): seven nails in a 5 + 2 bundle, each shank, point and head;
  lengths differ so the heads never share a plane; head half-depths grow 0.02 px per nail for the same reason. Two
  `twine` wraps, a `cord` knot and two twine ends on the front.
- **Shipwright's Toolkit** (30, handheld): an opened tool roll; the roll (crossed boxes, `wood_dark` core showing at
  the ends) with a `leather_dark` strap and brass buckle on the lower right, the flap's dark inner side on the upper
  left, a lighter pocket band with two twine stitch lines; out of the pockets a 0.6 scale copy of the hammer (the
  hammer builder with `small=True`), a saw blade with six teeth (inside the blade's z range, 0.05 px from its faces)
  and three nails. The open flap is the GUI view, so no `gui` entry.
- **Hook-loaded musket** (`musket_hook`, 33): the 24 elements of `musket_loaded` verbatim, then nine `hook_*` parts
  built in the muzzle frame (origin at the muzzle centre (16.572, 17.584), the barrel's own −45 turn): the shank
  seated 3 px in the bore and 1.7 px out, a crossbar of two crossed boxes just ahead of the muzzle, two arms at 45
  degrees to the barrel (axis-aligned in the model: −x on the upper-left side, −y on the lower right) and the fluke
  blades and points parallel to the barrel, ending beside the muzzle. Display entries and textures are copied from
  `musket.json`, so the hook sits where the ball variant's barrel ends in every slot;
  `HandMadeModelsTest.musketHookIsTheLoadedMusketWithAHookAtTheMuzzle` guards it. It replaced GR3's datagen
  placeholder (`MusketHookModel`, deleted with its test); the override in the hand-made `musket.json` is unchanged.
  If `musket_loaded` changes, rebuild `musket_hook` from it.
- Renders: `renders/<name>.png` for all seven, each a strip of four orthographic views (front as in the GUI, two
  three-quarter views, side) from a separate `THREE.WebGLRenderer` over Blockbench's scene (helpers and grid hidden).
  No hand views (display mode was not used).
- Sprites: `textures/item/kraken_beak.png` and `kraken_ink.png` (`tools/gen_kraken.py`) are no longer used by any model
  but stay because `KrakenRigTest` reads them; `carpenters_hammer`, `saw`, `nails` and `shipwright_toolkit.png`
  (`tools/gen_placeholder_textures.py`) are unused too and can go with their generator code in a cleanup.

Hammock (ART1d):
- `hammock.bbmodel` holds both halves as groups `foot` (block coordinates) and `head` (drawn 16 px north of it, so
  the project shows the whole hammock); `block/hammock_foot.json` is the `foot` group, `block/hammock_head.json` the
  `head` group shifted 16 px south. Both are drawn facing north, the head north of the foot; the head is the foot
  mirrored in z (z -> 16 - z, x rotations negated). 18 elements each: canvas (`white_wool`) flat at y 3..4 for
  |s| <= 5 px from the seam, a 22.5 degree slope rising 3 px to y 6..7 at s 12.24, a level end into the hem; rolled
  side hems (1.25 px, on flat, slope and end); an end hem round a `stripped_spruce_log` spreader bar (x 0.75..15.25,
  grain along x, log top on the ends); four `pirates_n_ships:block/rope` strings (bar ends at 22.5, inner pair at 45)
  into a knot at y 9.1..11.1 on the outer face; a rope from the knot to z 22.5, the middle of a fence post in the next
  block (hidden in walls and solid blocks). Seam faces are left out. The slope parts are 0.05 px narrower than the
  flat ones (V1 joint rule). `HammockBlock` collision: `CANVAS_BOTTOM` 3, `CANVAS_TOP` 7; the sleeper's seat is at
  `HammockSeat.SEAM_CANVAS_TOP` 4.
- Item (`hammock_item.bbmodel`, 16 elements, `item/hammock`, handheld slots, no `gui` entry: the `z -45` turn of every
  element gives the diagonal): the canvas roll as two crossed boxes, spreader bar stubs at both ends, three rope
  windings of crossed boxes 0.3 px proud, a rope run along the front, knot and loop at the top, a tail at the bottom.
- Built like ART1b/ART1c from a Python part list written as model JSON, rebuilt cube by cube in `java_block` tabs;
  `Codecs.java_block.compile()` gives the same geometry. Script not committed.
- **Sleep pose** (`sleep`, 4 s loop, in `crew_member_animations.js` and all five rig projects): root position
  `[0, -11, -4.5]` (hips' back onto the canvas at the seat, body centred on the seam), `waist` x −72.5 (−73.5 at 2 s:
  breathing), legs x −112.5 (z ±2), arms `[-25, 0, ∓25]` (right/left: folded over the belly; −27 at 2 s), `body`
  scale breathing as `sit`. The V (upper body up 17.5, legs up 22.5) was fitted to the canvas profile: the hips' back
  sits 0.7 px into the canvas, nothing floats more than about 1 px. The head is not keyed (rig contract);
  `CrewMemberModel` tilts it 15 degrees chin to chest while resting. `CrewSleepPoseTest` checks hips on the canvas
  and the body within the two blocks. Render `renders/anim_crew_sleep.png` (the hammock cubes added to the rig tab for
  the shot only, frames 0 s and 2 s: three-quarter, side, top).

Ratlines (RL1):
- **Part list in `tools/gen_ratlines_models.py`** (committed). Two block models in one project, `ratlines.bbmodel`,
  groups `ratlines` (the hung net, 14 elements) and `ratlines_slope` (14), plus `ratlines_item.bbmodel` (group
  `ratlines_item`, 14, the item model `item/ratlines`, vanilla `item/generated` transforms, identity `gui`). All on
  `pirates_n_ships:block/rope`, embedded. A net: two shrouds 2 x 2 px at x 2..4 and 12..14 (ends left out so stacked
  nets meet), four ratlines 1.5 x 1.5 px across x 1..15 at heights 2, 6, 10, 14, a knot (2.5 x 2 px) at each crossing.
- **Hung** (facing north like a ladder): shrouds at z 13.5..15.5, 0.5 px off the support; ratlines and knots in front.
- **Sloped** (rising north): the same net built about the centre plane z 8 with every element turned `x -45` round
  (8, 8, 8): shrouds 22.63 px long run corner to corner (bottom south edge to top north edge), so chained links meet
  end to end; the ratlines sit at 8 + (t - 8) * sqrt(2) along the slope for heights t = 2, 6, 10, 14, on the upper
  (pre-turn south) side, 0.5 px above the diagonal, level with the collision treads of `RatlinesRules.boxes`.
  One model serves all four facings through the block state's y rotation; a 45 degree slope cannot come from a block
  state rotation, hence two models.
- Built with `RL1.build(name, [[group, file], ...])` in `risky_eval` (a new `java_block` project per file set, cubes
  from the generated JSON, `autouv: 0`); exported per group with `Codecs.java_block.compile({raw: true})` and the other
  groups' cubes `export = false`, zero rotations stripped, `parent` and `particle` added; the export matches the part
  list. Lint clean (no fights, no warnings).
- Render `renders/ratlines.png`: a scratch Generic project with a four-block hung run on a mast block and a three-link
  sloped run from a deck (top: two views), and the two block models alone (bottom). The mast and deck are flat brown
  stand-ins (no `art/vanilla` textures were extracted).

Ship decor (ART2): ship's lantern, ship's bell, rope coil, stern window, chart table, sea cot:
- **Part lists in `tools/gen_decor_models.py`** (committed, like the cannon's): run it to rewrite the 17 model JSON
  files, then `python3 tools/lint_models.py`, then rebuild the projects. Every model faces north; wall-mounted ones
  hang on the south side (z 16). UVs are vanilla's position UVs wrapped into 0..16; log faces along x or z are turned
  90 degrees with the UV rect transposed; palette faces (`brass`, `brass_dark`, `iron_dark` of `textures/item/palette`)
  take the inner 3 x 3 px of the patch. No new textures.
- **One project per block, one group per exported model**, all groups at the same position, the first visible
  (`ship_lantern`: `ship_lantern`, `_wall`, `_ceiling`; `ships_bell`: `ships_bell`, `_ringing`, `_wall`,
  `_wall_ringing`; `rope_coil`: `rope_coil_layers1..4`; `stern_window`: `stern_window`, `_shutters`; `chart_table`;
  `sea_cot`: `sea_cot_item`, `sea_cot_head`, `sea_cot_foot`). Export a group with the other groups' cubes set to
  `export = false`; compiling the whole project merges the variants. The projects were rebuilt from the JSON cube by
  cube (`ART2.load` in `risky_eval`: textures from `art/vanilla/` with namespace `minecraft`, folder `block`, our own
  from the resources, embedded) and `Codecs.java_block.compile()` of each group matches its JSON. The `lint --fix`
  project lookup does not know these groups; fix the part list instead.
- **Lantern** (12 / 17 / 14 elements): base, vanilla `lantern` glass sides (uv 0,3..6,9 of the animation frame, so the
  glow animates), brass corner posts, top plate, two caps and a hanging ring. The ceiling variant is raised 2 px onto a
  hook and a rose, the wall variant raised 1 px under a brass arm with a 45 degree brace into a stripped dark oak wall
  block.
- **Bell** (19 / 17 elements, the same with `_ringing`): crossed-box sections (crown, top, shoulder, waist with a band,
  lip with a `black_concrete` mouth) of `gold_block`; a `palette` iron clapper and a rope lanyard that stay plumb
  while the bell elements turn 22.5 degrees about x at the hanging point (lip north, away from a wall), which is why
  the bell has no 45 degree octagon bars (one rotation per element). Floor: a dark oak belfry frame (base, two
  uprights, log beam with brass pin and cap) meant to sit on a post; wall: backboard, arm and brace.
- **Rope coil** (28 / 55 / 81 / 107 elements): per coil two octagonal rope rings (eight bars each, the four diagonal
  bars 0.05 px shorter at both ends), a crossed-box heart and one upper ring in the groove, 3.9 px high, 4 px per
  layer; the top coil has the loose end (on a stack it drops down the side) with a wool whipping. `block/rope` at its
  own scale; a squashed or stretched rope UV was tried and read worse.
- **Stern window** (19 / 26 elements, `render_type: minecraft:cutout` for the `glass` panes): full-depth stripped
  dark oak frame, mullion and transoms 2.5..5.5 px behind the outer face with four panes at 4 px, casing, hood and a
  brass sill on corbels outside (proud of the block), a stool inside; shutters: two `dark_oak_trapdoor` leaves with
  `anvil` straps and an iron latch in the opening.
- **Chart table** (46 elements): turned legs, aprons, drawer with a brass knob, H stretcher, a top overhanging 1 px with
  fiddle rails, a `map_tile` chart turned 22.5 degrees with zero-height ink strips (black coast, red course, compass
  cross) 0.05 px above it, a `packed_ice` glass weight, brass dividers (the two legs 0.05 px apart in height), an
  inkwell and a quill.
- **Sea cot** (head 17, foot 13, item 30 elements): built as one frame (head z -16..0, foot 0..16) and split at the
  seam (seam faces left out); posts, panelled head- and footboard, side boards and rails, a red blanket with a white
  sheet fold, pillow; the mattress shows only at the head. The item model (`block/sea_cot_item`, both halves shifted
  8 px south) carries the display entries (GUI scale 0.42).
- Renders: `renders/ship_lantern.png` (floor, wall, ceiling), `ships_bell.png` (floor, floor ringing, wall, wall
  ringing), `rope_coil.png` (one and three coils), `stern_window.png` (open, shutters), `chart_table.png`,
  `sea_cot.png`.

Cloth and flags (ART3):
- **Sail cloth** `textures/block/sail_cloth.png` (32x32, opaque, `tools/gen_sailing_textures.py`, deterministic): one
  tile per block, seamless both ways (the yard renderer maps every block of cloth onto the whole tile, the stay
  renderer repeats it by planar position in blocks). Plain weave (alternate texels +-3, a thread variation per row and
  column), double-stitched vertical panel seams every 8 px (dark fold, light overlap ridge, stitches every 3 px on both
  sides), **one reef band per block** (rows 12..14, doubled darker canvas, stitched along both edges, a tan reef point
  knotted on the band in the middle of every panel and hanging 4 px), stains and faint rain streaks. The furled bundle
  samples rows 0..7 only, so it shows seamed canvas without the band. The sails have no colour variants yet (design.md
  §4.8 "dyeable" is not implemented), so there is one file. An edge drawn into this tile would repeat in mid-sail, so
  the frayed foot is a second tile, `sail_cloth_foot.png`, that the renderers pick for the bottom block of the cloth
  (ART5, see "Mooring ring and sail foot (ART5)" below). Render `renders/sail_cloth.png` (full 3 x 3, half with the
  bundle under the yard, furled; flat planes in a scratch Blockbench project, not saved).
- **Flag cloth** `textures/block/flag_<kind>.png` (32x16, layout unchanged, `tools/gen_flag_textures.py`): every design
  gets `finish()` on top: plain weave over the field (+-5 per texel plus row/column variation; greyscale on the banner
  cloth, which is tinted), a canvas heading tape on hoist column 2 with grommets in rows 1 and 14, a stitch line in
  column 3 (every other row 40 darker), and a frayed fly: rows 3, 7, 8 and 12 of the tip column 23 are transparent
  (the renderer is `entityCutoutNoCull`), column 22 a shade darker there.
- **Ripple** (`ship/decor/flag/FlagRipple`, pure, `FlagRippleTest`; drawn by `FlagClothRenderer`): the cloth of
  `FlagClothModel` (1 x 1.5 blocks, unchanged) is drawn as 8 vertical strips of 3 texture columns. Strip boundary k
  (s = k / 8) is pushed along the cloth normal by `A * s * sin(2 pi (1.25 s - t / 36) + phase)`: the hoist stays on
  the pole, the swing grows to the tip, a crest takes 36 ticks to pass and travels from the pole to the tip. A is 0.6 px
  in calm air and 1.6 px at 12 blocks/s of wind (sampled once per game tick for all flags), the phase is a hash of the
  pole's block position. Normals follow the slope, so the folds shade. Front and back faces per strip, the swatch
  edges per strip, one tip face. The renderer keeps three float arrays and refills them per flag: no allocation per
  frame. Kept in code (the renderer draws quads); `models/flag_cloth.bbmodel` is a reference model only (Generic
  format, the four flags at one moment, each strip a cube turned about y; nothing is exported from it). Render
  `renders/flag_cloth.png` (front three-quarter, the navy flag from above, back). Rebuild: a `free` project, the
  four textures from `textures/block/`, per strip a 1 x 16 px cube from boundary k to k + 1 with east uv
  `[3k, 0, 3k + 3, 16]`, west reversed, up/down on the swatch column 24.
- **Kraken re-render:** `renders/kraken.png` was re-rendered after GL1 with `KRK.render()` on the unchanged
  `kraken.bbmodel` (see "Kraken (K1b)": open the project, `window.KRK = {REPO, UUID: Project.uuid}`, eval
  `kraken_model.js`, `KRK.render()`). The rest pose of the model never pointed the suckers wrong; only the aim did, and
  `KRK.aimQuat` already had the GL1 sucker normal, so the raised arms now hook their tips down and show their suckers
  from below. Model, geo and rig tests unchanged.
- **Blockbench global names:** besides `KR`, Blockbench defines a global `FC`; `window.FC = {...}` does not shadow it
  inside `risky_eval` (properties land on Blockbench's function). ART3 used `window.ART3`.

Flagpole parts (VIS1a, tall poles):
- Four models, one project each: `flagpole` (single: pole, truck, finial, cleat; 12 elements), `flagpole_bottom`
  (pole and cleat, 8), `flagpole_middle` (pole only, 3), `flagpole_top` (pole, truck and finial, 7). The block state
  picks them by `part` (single/bottom/middle/top, from the flagpole neighbours), never turned; the item keeps
  `flagpole`. All four share the 3 px `stripped_spruce_log` pole at x/z 6.5..9.5 with the same position UVs; a part
  with a pole above it (bottom, middle) runs it the full 0..16 px (side UV 0..16) and leaves out the up face, a part on
  a pole (middle, top) leaves out the down face, so stacked segments meet without a seam. The top and single poles
  end at 15.95 under the truck as before.
- **Halyard:** two `stripped_birch_log` lines 0.4 x 0.4 px at local x 10.3..10.7, z 7.15..7.55 and 8.45..8.85, turned
  45 degrees about y round (8, 4, 8) like the cleat, so they stand about 0.6 px off the pole's corner on the cleat's
  diagonal (the cloth flies on the other side). They leave the wound cleat at y 10.7 (single, bottom), run through the
  middle segments 0..16 and end under the truck at 15 (top, single). The single pole's one 0.5 px halyard became the
  same two lines. In the render the pair reads as one thin rope from a few blocks away, which is what a halyard looks
  like; up close the two parts of the loop show.
- **Foot (VIS1a-b, playtest note "the rope only in the top third of the lowest block"):** on `flagpole_bottom` and
  `flagpole` the halyard lines start at y 10.7 (the top third), so the lower two thirds of the foot block are a bare
  pole. The cleat and its wound rope moved up with them by 5.1 px (horn y 6.6..11.6, body 8.35..9.85, rope rings
  7.5..8.3 and 9.9..10.7, rotation origin (8, 9.1, 8)); the anvil and rope side UVs moved with the parts (position
  UVs), the rings' rotated UVs stayed. Done in Blockbench on the committed projects (opened with `loadModelFile` from
  `fs`), re-exported and re-saved like VIS1a; pole, truck, finial, middle and top unchanged.
- Built from a part list (a scratch Python script derived the parts from the single pole's JSON), loaded cube by cube
  into a `java_block` project per model (`VIS1A.load2` in `risky_eval`: textures from `art/vanilla/` with namespace
  `minecraft`, folder `block`), exported with `Codecs.java_block.compile()` (names and zero rotations stripped,
  `minecraft:` prefix, `parent` and `particle` added) and saved with `Codecs.project.compile({raw: true})`; the export
  matches the part list exactly. `HandMadeModelsTest` lists all four; `FlagModelGameTests` checks the block state, the
  crown and cleat per part and the full-height pole of the segments that carry another.
- Render `renders/flagpole_parts.png`: a four-block pole (bottom, two middles, top), the single pole and a two-block
  pole (left: overview; middle: the cleat and halyard at the foot; right: the joint under the top's truck). Rendered
  from a scratch Generic (`free`) project holding all parts stacked, with a `THREE.WebGLRenderer` in `risky_eval`.
  VIS1a-b re-rendered it without a scratch project: each part's `Project.model_3d` cloned (non-mesh children dropped)
  and stacked in a plain `THREE.Scene`, three 1200 x 900 viewports in one 3600 x 900 canvas.

Treasure map and receipt (ART4):
- Two `java_block` item projects, `treasure_map.bbmodel` and `ship_receipt.bbmodel`, no block of the same name. No new
  colours: `paper`, `biscuit_light`, `biscuit_dark` (`palette_3`), `map_land`, `map_ink`, `map_sea_dark`,
  `flag_red`, `flag_red_dark` (`palette_5`), `red` (`palette`), `spice_dark` (`palette_4`); particle `palette_3`
  for both. Datagen writes no model for either item any more (`TreasureMaps.gatherData` dropped its delegation to
  `item/chart`, `ShipOrderData` its flat `minecraft:item/paper` model); they are plain items, so no `handMadeItem`
  call is needed.
- **Treasure map** (47 elements): a parchment folded once down the middle, built facing south like a sprite
  (x 1..15, y 2..14, sheet z 7.75..8.25). The right half lies flat; the left half and everything drawn on it turn
  `y 22.5` about `[8, 8, 8]` (the fold), so its outer edge comes 2.7 px towards the viewer and the map opens like a
  shallow book. Its top-left corner is rolled over towards the front: the sheet stops at y 12.4 under a roll of two
  crossed boxes along x (D 2, `biscuit_dark` ends with a `biscuit_light` core disc, `biscuit_dark` underside),
  turned with the half. Aged edges are rim inlays of uneven width (`biscuit_light`, 0.05 px proud) with
  `biscuit_dark` stains on the corners (0.1 px proud), a `biscuit_dark` crease beside the fold; the back is
  `biscuit_light`. Drawing: left half a mainland coast along the bottom, two wave marks, an ink compass rose with a
  red north tip and three route dots; right half an island of six `map_land` rows over `map_ink` rows 0.35 px wider
  (the ink rows alternate between 0.05 and 0.1 px proud so neighbours never share a plane; land 0.15 px proud), a
  palm as an ink T, a fourth route dot and the red X (two `flag_red` bars at +45 and -45, 0.2 and 0.26 px proud).
  Inlays are zero-depth elements with only their south face. Lint: no fights; two same-look warnings where the
  turned half's top and bottom faces meet the flat half's at the fold (0.012 px², same paper patch).
- **Treasure map display:** held open on the fingers and tilted up towards the holder like a map being read:
  third person `[40, 15, 0]` / `[0, 2.5, 1]` / 0.5; first person (the doubloon's) `[0, -70, 25]` /
  `[1.13, 4.2, 1.13]` / 0.55; `gui` `[20, -15, 0]` / 1.1 (the fold and the roll show); `ground` lying flat, face up,
  `[-90, 0, 0]` / `[0, -1.5, 0]` / 0.5; `fixed` and `head` vanilla (the drawing faces out of the frame). Left-hand
  slots repeat the right hand.
- **Ship receipt** (16 elements): a letter with its top third folded down over the front. Sheet x 2..14, y 3.5..12.5,
  z 7.7..8.3 (`biscuit_light` edges); the flap y 8..12.5, z 8.3..8.5 with a `biscuit_light` crease band along its
  top and a `biscuit_light` shadow band on the sheet just under its folded edge. Ink (`map_ink`, 0.05 px proud):
  three address lines and a signature on the sheet, a heading of two lines and the shipwright's number on the flap.
  The wax seal straddles the flap's edge at (8, 8): two crossed boxes (`red`, `flag_red_dark` sides, 0.05 px apart
  in depth), a raised `spice_dark` stamp and a `flag_red_dark` drip below the seal.
- **Receipt display:** held upright between the fingers like a letter, its face turned outwards: third person
  `[70, -90, 0]` / `[0, 3, 1]` / 0.55; first person `[0, -70, 25]` / `[1.13, 4.2, 1.13]` / 0.6; `gui`
  `[20, -15, 0]` / 1.25; `ground` flat face up as the map; `fixed`, `head` vanilla.
- Built like ART1b-ART1d: a Python part list (patch names through the `SHEETS` cells) wrote the model JSON, and
  `ART4.load(name)` in `risky_eval` opened it in a new `java_block` tab with `Codecs.java_block.parse(json, path)`
  (given the JSON's path under `models/item/`, the parser resolves the palette textures itself, with folder and
  namespace set). `Codecs.java_block.compile()` of the tab gives the same 47 / 16 elements (checked element by
  element); the project is `Codecs.project.compile({raw: true})` with each texture's `path` emptied and
  `relative_path` pointing at the sheet under `common/src/main/resources`. Script and helpers are not committed.
- Renders: `renders/treasure_map.png`, `renders/ship_receipt.png`: front, three-quarter and back views (a separate
  `THREE.WebGLRenderer` over a clone of `Project.model_3d`), then the GUI slot and third person in the right hand
  (display mode, `scene` rendered from the display preview's camera with the gizmo hidden), composed on a 2D canvas.
- **Audit: items whose item model is still not a Blockbench model** (the backlog for the next batch; checked against
  every `item.` / `block.pirates_n_ships.*` lang key; the ART2/ART3 branches may change it when they land):

  | Item | Model now | Declared in |
  |---|---|---|
  | `pirate_spawn_egg`, `sailor_spawn_egg`, `navy_soldier_spawn_egg`, `navy_officer_spawn_egg`, `shark_spawn_egg` | `minecraft:item/template_spawn_egg` | `mob/MobModule.gatherData` (`SPAWN_EGG`) |
  | `kraken_spawn_egg` | `minecraft:item/template_spawn_egg` | `mob/kraken/KrakenContent` (`SPAWN_EGG_MODEL`) |

  Spawn eggs keep vanilla's look by design (design.md §4.8), so there is no real backlog left: the mooring ring, the
  last entry, got its Blockbench model in ART5 (below). Every other item has a hand-made model under `models/item/` or
  delegates to a hand-made block model.
  Unused sprites that could go in a cleanup: `textures/item/carpenters_hammer.png`, `saw.png`, `nails.png`,
  `shipwright_toolkit.png` (ART1c); `chart.png`, `map_tile.png`, `kraken_beak.png`, `kraken_ink.png` stay while tests
  read them.

Mooring ring and sail foot (ART5):
- **Mooring ring** (`mooring_ring.bbmodel`, `block/mooring_ring.json`, 21 elements, textures vanilla `anvil` and
  `palette_2`; particle `anvil`): one model made for the floor, facing north, turned by the block state like a button
  (floor; wall `x 90`; ceiling `x 180`; then `y` by the facing; no uvlock any more, it would smear the palette faces).
  Parts: an 8 x 8 x 0.7 anvil plate (exactly the floor shape's footprint, x/z 4..12) with a 7 x 7 x 0.3 top step for a
  bevel; four bolt heads in the corners (two crossed boxes each, the second 0.05 px lower; `cast_iron_light` tops,
  `cast_iron` sides); the ring, a flat octagon of eight 1 x 1 px bars round the block centre (outer apothem 3, inner 2;
  the four diagonal bars are the north and south bars turned +-45 about y, 0.05 px shorter at both ends and 0.05 px
  thinner at top and bottom; `lead_light` tops, `lead` sides, no down faces on the plate) lying on the plate at y 1..2;
  a `cast_iron` staple across the ring's south bar (a leg inside and one outside the ring, a strap 0.05 px above the
  bar). On a wall the staple comes out on top and the ring hangs below it, its centre on the block centre, where
  `MooringRingBlock.ringCenter` ties the rope. On the ceiling it lies flat against the beam (one model; a hanging
  ceiling ring would leave the block's shape). Everything stays within the shape (x/z 4..12, y 0..2.55);
  `HandMadeModelsTest.mooringRingFitsItsShapeAndIsHeldUpright` checks it.
- **Ring item:** no `_item` project. The block model carries `gui_light: front` and display entries for every held
  slot, and datagen's automatic block-item model points at it. The entries are the doubloon's (ART1) applied to the
  ring "stood up like a sprite": a base turn `[90, 180, 0]` (plate face to the viewer, staple up, ring hanging) composed
  with the doubloon's rotation into one XYZ Euler, scale x 1.25 (the plate is smaller than a coin), and a translation
  that puts the plate's middle (y 1.25, 6.75 px below the model centre) back where the coin's middle is. Values: gui
  `[-115, 0, -155]` / `[3.92, 3.55, 7.62]` / 1.375, third person `[-90, 0, -155]` / `[1.6, 2.5, 4.44]` / 0.5625,
  first person `[-113.66, -8.31, 108.26]` / `[-3.23, 4.2, 2.72]` / 0.6875, fixed `[-90, 0, 0]` / `[0, 0, -8.44]` /
  1.25, ground flat `[0, 0, 0]` / `[0, 3, 0]` / 0.5; left hands repeat the right. Head stays the block default.
- Built like ART4: a Python part list (scratch, not committed) wrote the JSON, `ART5.load()` in `risky_eval` opened it
  with `Codecs.java_block.parse(json, path)` and reloaded `anvil` from `art/vanilla/` (namespace `minecraft`, folder
  `block`); `Codecs.java_block.compile()` gives the same 21 elements. Lint: no fights, no warnings for the ring.
- Render `renders/mooring_ring.png`: on the floor, on a wall (hanging), under a ceiling (each turned by the block
  state's rotation next to a stand-in support block) and the GUI slot, drawn with a separate `THREE.WebGLRenderer`
  over a clone of `Project.model_3d`. No hand view (the Blockbench window was hidden, display mode does not repaint).
- **Sail foot** `textures/block/sail_cloth_foot.png` (32x32, `tools/gen_sailing_textures.py`, deterministic;
  `sail_cloth.png` and `rope.png` are pixel-identical to before; a newer Pillow encodes them with other bytes, so only
  the new file was committed from the run): the plain tile's pixels in rows 0..22, then a foot tabling (rows 24..27,
  doubled darker canvas, stitched in rows 23 and 28) and a frayed edge (rows 28..31 turning grimy towards the edge;
  each column ends 0 to 4 texels short of the edge, at most 1 at the seams; a few loose dark threads). The renderers
  draw `entityCutoutNoCull`, so the notches are transparent.
- **Which block is the foot** (`sailing/client/SailFoot`, pure, `SailFootTest`): the bottom block of the drawn cloth,
  only when the drawn cloth hangs at least 2 blocks (`MIN_HEIGHT`); shorter cloth and the furled bundle keep the plain
  tile. Square sails (`YardClothRenderer`): the cloth's lowest edge is the lower yard (full) or the cloth's lower edge
  (half trim; a square sail is reefed from the head, so its lower edge is still the foot); the last two half-block
  grid rows take the foot tile, and the tile phase is now counted up from the bottom (`yardV0`), so a whole tile always
  ends at the foot (with an even row count, every full or 2/4/6-block sail, the phase is unchanged). Triangular sails
  (`StayClothRenderer`): the foot is the tack-clew edge. The texture v is now the height above that edge
  (`bottom * (1 - u - v)` in the grid's barycentric coordinates, `stayV`), so the tiles and reef bands run parallel to
  the foot; for a horizontal foot (tack level with the clew) this equals the old `v = -y`. A grid triangle takes the
  foot tile when its highest corner is at most 1 block above the foot; the grid's cells are at most half a block, so
  the row on the edge always does. Both tiles share every pixel above row 23, so the jagged switch line never shows.
  Each renderer takes the foot buffer right after the plain rows (asking a buffer source for another render type ends
  the previous shared batch), and the bundle asks for the plain buffer again.

Paddle (ART8):
- `paddle.bbmodel` -> `models/item/paddle.json` (13 elements, `palette` `#0` and `palette_2` `#1`, particle
  `palette_2`; no new colours) for SC2's sea chest paddle (design.md §11). Laid out like ART1c's tools in a diagonal
  frame: every element built along y and turned `z -45` about its own centre, s along the up-right diagonal from
  (8, 8), lower left to upper right: a `wood_dark` T-grip (crossbar plus a crossed box, s -11.3..-9.9), the shaft as
  two crossed boxes (`wood`, ends `wood_dark`, s -9.9..1.8, the second box 0.05 px shorter at both ends), two
  `leather` wraps (`leather_dark` fronts) at the grip and mid-shaft, a `rope_coil` whipping at the throat (s
  -0.6..0.4), then the blade in steps, all `walnut_dark` edges and `walnut_light` faces: shoulder 2.8 wide, blade 4.4
  wide and 0.7 thick (s 2.2..9.4), tip 3.6 wide and a 2.2 wide tip end to round it; a `walnut` centre rib 1.1 deep
  down the blade, narrowing to 0.6 before the tip. The grip end reaches x -1.1 (inside the vanilla -16..32 limit).
- **Display:** vanilla `item/handheld` slots with third person scaled 0.95 (a sword is 0.85; the paddle reads longer);
  first person, ground, head and fixed vanilla; no `gui` entry (the diagonal is the GUI view).
- Lint: no visible or same-look fights, five hidden end-to-end face pairs (warnings). Built from a Python part list
  that writes the model JSON, rebuilt cube by cube in a `java_block` tab (palette textures from `fromPath` with `id`,
  `folder`, `namespace`; display slots as `DisplaySlot`s); `Codecs.java_block.compile()` of the tab gives the same 13
  elements and faces. Script not committed; rebuild it from these notes.
- Render `renders/paddle.png`: front (as in the GUI), two three-quarter views, back. No hand views (display mode not
  used).
- **Wiring:** SC2's datagen placeholder (`ModelTemplates.FLAT_HANDHELD_ITEM` on the wooden shovel texture in
  `SeaChestModule.gatherPaddleData`) has to go when both land: datagen writes nothing for the paddle's item model,
  the hand-made file is found by the item's id.

Boarding plank (BRD1):
- **`boarding_plank.bbmodel`**, one project with four groups at the same position (`boarding_plank_base`,
  `boarding_plank`, `boarding_plank_tip`, `boarding_plank_item`; export a group with the others' cubes set to
  `export = false`), exported to `block/boarding_plank_base.json` (6 elements), `block/boarding_plank.json` (5),
  `block/boarding_plank_tip.json` (10) and the hand-made item model `item/boarding_plank.json` (11, handheld display
  slots, GUI seen from above at 30/225 degrees). Textures vanilla `spruce_planks` (boards), `stripped_spruce_log`
  (cleats), `anvil` (hooks) and our `block/rope` (lashing); particle `spruce_planks`. No new textures.
- The run goes north (block state `facing` = north needs no rotation): three boards x 1..15, y 0..2 (the collision
  box), with 0.1 px gaps; foot cleats 0.6 px high across the boards; the gunwale end (south) has a rope lashing round
  the boards with a knot underneath; the far end (north) has two iron straps that bend down into hooks with barbs,
  3 px below the board, biting into the deck the plank lies on. The hook tops end 0.05 px inside the board (no z-fight).
- Render: `renders/boarding_plank.png` (base, middle and tip as a three-cell run, the far end on the left).

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

### GeckoLib bone rotations in code: read this before any `setRotX/Y/Z` (GL1)

Three packages in a row got a sign wrong when code turns a GeckoLib bone (the shark's body pitch, the kraken's tentacle
tips and suckers pointing up, earlier the musket). The facts below are checked against the GeckoLib 4.9.3 sources
(`software.bernie.geckolib`, sources jar in the Gradle cache) and against what the human saw in game.

**GeckoLib's frame is not vanilla's.** `GeoEntityRenderer.applyRotations` turns the model by `180 − bodyYaw` and
scales by the entity's native scale only. There is **no** `scale(−1, −1, 1)` as in `LivingEntityRenderer`, so y is up
and a bone's local axes are the Bedrock axes. The mirror vanilla gets from that scale is instead baked into the
geometry: `BakedModelFactory` loads every pivot and cube origin with **x negated** (`-pivot.x`, origin `-(x + size.x)`)
and every bone and cube rotation from the file with **x and y negated** (`-rot.x, -rot.y, rot.z`, in radians);
`RenderUtil.translateMatrixToBone` likewise moves by `-posX`. Bones render with `RenderUtil.rotateMatrixAroundBone`,
which is JOML `rotationZYX(rotZ, rotY, rotX)`: right-handed, radians, applied Z then Y then X, exactly the values the
bone holds. Animation keyframes are added onto the bone's initial snapshot (`AnimationProcessor`) in that same space.

**Rule 1: never write a raw Minecraft angle into a bone.** Minecraft's pitch (`getXRot`, `getViewXRot`, the pitch of a
look vector; positive = down) and yaw (`yHeadRot − yBodyRot`, `getViewYRot`) are in vanilla's convention. For a
GeckoLib bone **negate x and y**, keep z: `bone.setRotX(-pitchRad)`, `bone.setRotY(-yawRad)`, `bone.setRotZ(rollRad)`,
the same flip GeckoLib applies to file values. The shark did `root.setRotX(viewXRot)` and pitched the wrong way.

**Rule 2: `EntityModelData` is already flipped.** `GeoEntityRenderer` fills `DataTickets.ENTITY_MODEL_DATA` with
`new EntityModelData(sitting, baby, -netHeadYaw, -headPitch)`. So `head.setRotX(data.headPitch() * DEG_TO_RAD)` and
`head.setRotY(data.netHeadYaw() * DEG_TO_RAD)` are correct as they stand (`HumanoidGeoModel`, the shark's head). Do
not negate these a second time, and do not mix them with raw entity angles in one formula.

**Rule 3: directions from the world need only the yaw turn undone.** A bone aimed at a world direction (the kraken's
tentacles, a turret) takes the direction into the entity's frame by undoing the `180 − bodyYaw` turn, and that *is*
the bone frame: GeckoLib draws with no mirror matrix, the file's x flip is already in the baked pivots and cubes
(`bone.getPivotX()` is `−file x`). So subtract the baked pivot and decompose with `rotationZYX`; do not mirror x
again (that mirrors the aim). Only angles from Minecraft (rule 1) and values in file or Blockbench coordinates need
the flip. (GL1 checked the kraken's frame end to end: it was right; see below.)

**Rule 4: held items are moved by the hand bone once, not twice.** GeckoLib 4.9.3 applies the hand bone in
`renderRecursively` and again in `BlockAndItemGeoLayer.renderForBone` (through `translateAndRotateMatrixForBone`);
`HumanoidGeoRenderer.HeldItemLayer` overrides `renderForBone` to translate to the pivot only (M6b).

**Rule 5: verify the sign before handing back, with a test that includes the frame.** A JUnit test that composes the
real transforms end to end (`GeoEntityRenderer`'s turn, the baked mirror, `rotationZYX`, then the item or cube
offset) and asserts a world-space fact ("the muzzle points forward and level", "a swimming-down shark's nose is below
its tail", "the tentacle tip is below its root") catches every sign error headlessly; `SeafarerRigTest` and
`KrakenRigTest` are the pattern. A render in `art/renders/` from a side view is the second check. Writing
`setRotX(angle)` because it compiled is how the three bugs above shipped.

**What GL1 found** (playtest 2026-10-07; tests `SharkRigTest#theBodyPitchesWithTheSwimmingDirection`,
`KrakenWorldPoseTest`, `SeafarerRigTest#theHeadLooksWhereTheEntityLooks`, all composed with a real `PoseStack`, the
renderer's `Axis.YP.rotationDegrees(180 − bodyYaw)`, the baked model and `RenderUtil.prepMatrixForBone`):
- **Shark:** rule 1. `SharkModel` wrote `root.setRotX(getViewXRot · DEG_TO_RAD)`, Minecraft's pitch unflipped, so a
  shark swimming down raised its nose. Now `SharkPose` negates it once (`rootRotX = −clamp(pitch, 60°)`). The head,
  a child of the body, also took the whole look pitch from `EntityModelData` (it then bent twice as far once the body
  was right); it now takes only what the body's 60° clamp leaves (zero in water up to 60°), within 30°.
- **Kraken:** no frame step was skipped. `KrakenWorldPoseTest` puts every arm's end on its target within 1/16 block for
  body yaws 0°, 90°, 180°, 270° and 37° with the old and the new code. The fault was the sucker direction K1c chose:
  "towards the body's axis, made perpendicular to the arm". For an arm raised to a deck (the 50° attack rest, a hull
  grab) that vector points inwards **and up** (y +0.77 at the attack rest), so the suckers faced the sky and the
  geometry's curled tip and the animations' curls, which close on the sucker side, bent upwards. `KrakenAim.suckerNormal`
  now uses world down tilted outwards by `0.5 · d.y` (`w = down − 0.5·d.y·n0`), made perpendicular to the arm: a raised
  or level arm shows its suckers below and hooks its tip down (straight up: outwards), a hanging arm turns them to the
  axis (straight down: exactly the axis), smoothly in between. `w` is parallel to the arm only for arms pointing
  inwards 24.5° off vertical (through the head, or under the body), where the old fallbacks take over and the roll can
  turn fast. The render script's `KRK.aimQuat` shares the change; `renders/kraken.png` still shows the K1c roll (not
  re-rendered in GL1).

| Bone (class) | Angle source | Status |
|---|---|---|
| shark `root` x (`SharkModel` → `SharkPose.rootRotX`) | raw `getViewXRot` | **fixed** (was unflipped) |
| shark `head` x (`SharkPose.headRotX`) | raw `getViewXRot` minus the body's share, negated | **fixed** (was `EntityModelData` pitch on top of the body's) |
| shark `head` y (`SharkModel`) | `EntityModelData.netHeadYaw` as is | correct |
| humanoid `head` x/y (`HumanoidGeoModel`, so `CrewMemberModel` and every seafarer) | `EntityModelData` as is | correct (test added) |
| seafarer both arms x/y while aiming (`SeafarerModel`, M6) | `EntityModelData` added to the animated value | correct sign (Euler addition, approximate for large turns) |
| seafarer `head` x while aiming (`SeafarerModel`) | constant −6° (nose down) in bone space | correct |
| seafarer sword arm x/y/z, `waist` x (`SeafarerModel.set`, `DuelistArmPose`) | file-convention degrees, x and y negated | correct |
| kraken `tentacle_<i>_1` x/y/z (`KrakenModel` → `KrakenAim.reach`) | world offset, yaw turn undone, baked pivot | frame correct; sucker roll **fixed** |
| animation keyframes written from code | none (all keys come from `.animation.json`) | n/a |

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
| `sleep` | lying in a hammock (ART1d; `CrewMember#isResting`) | 4 s: on the back in the hammock, see "Hammock (ART1d)" above |
| `helm_hold`, `helm_turn_left/right`, `cannon_aim`, `cannon_load`, `cannon_fire`, `capstan_push` | at a station (ART7) | see "Crew station animations (ART7)" below |

Priority: station pose (ART7) > `work` > `sleep` > `sit` > `walk` > `idle` (`crew/npc/CrewPose`). One controller (`body`) plays them with a 5-tick
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

### Crew station animations (ART7)

Seven animations on the crew rig for the stations, in `crew_member.animation.json` (exported from
`models/entity/crew_member.bbmodel`; the four seafarer projects carry copies for preview) and one player animation for
hauling. Source `models/entity/station_animations.js` (run after `crew_member_animations.js`, `ST.build()`; `ST.render`
writes the contact sheet). Values in file convention as in `crew_member_animations.js`; keyframes `easeInOutSine`;
the head is never keyed. Contact sheet `renders/crew_poses_art7.png`: one row per animation, four frames from the front
three-quarter, four from the mob's right side, with proxy props for the shot only (the helm wheel and post, a cylinder
for the gun; not saved).

| Name | Length | Loop | Content |
|---|---|---|---|
| `helm_hold` | 4 s | loop | root 2 px forward, waist 10° forward (10.5° at 2 s), arms `[-52.1, ∓0.3, ∓10.4]`: hands on the rim at 2 and 10 o'clock; legs slightly apart |
| `helm_turn_right` | 1 s | loop | hand over hand clockwise as the helmsman sees it (to starboard): gripping from 0 to 0.6 s, the right hand from 35° to 82° clockwise of the top, the left from −85° to −30°, the waist rocking (lean 2–16°, twist +8° to −8°); back for a new grip by 1 s, the hands about 2 px off the rim |
| `helm_turn_left` | 1 s | loop | `helm_turn_right` mirrored (arms swapped, y and z negated, twist negated) |
| `cannon_aim` | 2 s | loop | leaning in 25–28° over the gun, root 1 px forward, right hand on the breech (12.5 px up, 10 px ahead), left hand on the thigh |
| `cannon_load` | 1.5 s | loop | ramming: both hands forward on an (invisible) level rammer, right hand ahead; drawn back at 0 s, driven home at 0.6–0.85 s with the waist 8° → 22° and the body 1.5 px forward; right foot forward |
| `cannon_fire` | 0.75 s | once | linstock hand raised high (arm −150°), lunge to the touch hole at 0.25 s (waist 30°, root 2 px forward, right leg −30°), held to 0.4 s, standing clear at 0.6 s (leaning back 8°, arm up and out), rest at 0.75 s |
| `capstan_push` | 1.2 s | loop | chest to the bars (waist 30–33°), arms forward on them (−75° to −78°, 14° inwards), walking strides ±25° with a 0.6 px dip; played at the capstan station (CRW3, `station/capstan/CapstanPoses`) while it drops or raises the anchor, facing the capstan from the station spot |

- **Wheel geometry (helm poses).** The helmsman stands at the station spot on the helm's `FACING` side, facing the
  wheel. In the model frame (feet at the origin, facing −z) the axle is 11.75 px ahead (8 px to the block edge plus the
  axle's 3.75 px in `HelmWheelRenderer`) and 12.84 px up (13 px over the deck minus the station seat's 0.16 px); the rim
  radius is 5.9 px. The arm values were solved by forward kinematics with GeckoLib's transforms (baked pivots, x and y
  negated, `rotationZYX`, root position `(-x, y, z)`), the root shift, waist lean and twist searched per key so the
  `right_hand`/`left_hand` locators land on the rim (`helm_hold` 0.2 px off; turn keys within 1 px while gripping).
  A helmsman on another side of the helm faces the helm block but his hands do not reach the rim (StationSpot tries
  north, east, south, west in that order).
- **Wiring.** The server picks the pose every tick (`crew/npc/StationPoses`, one resolver per station kind:
  `station/helm/HelmPoses`, `combat/cannon/npc/GunCrewPoses`) and syncs it on the crew member (`DATA_STATION_POSE`); a
  station pose wins over every other pose (`CrewPose.choose`) and also turns the crew member to face the wheel or gun
  (yaw from the plot direction through the ship's orientation, as `SleepAxis` does for hammocks) and stops its look
  goals. Helm: `helm_hold`, and for 20 ticks after each change of the wheel angle `helm_turn_right` (wheel to
  starboard) or `helm_turn_left` (`HelmTurn`). Cannon: `cannon_load` while the station carries out "Load!",
  `cannon_fire` during "Fire!" (the fuse; played once), `cannon_aim` during "Fire at will" and whenever its crew has a
  target (`Gunnery#aiming`); otherwise the ordinary poses.
- **Rig tests** (`CrewStationPoseTest`, composed like `SeafarerRigTest`: GeckoLib's baked rig and loaded keys,
  `RenderUtil.prepMatrixForBone`, the renderer's `180 − bodyYaw` turn, the wheel placed as `HelmWheelRenderer` places
  it, for helms facing all four directions): both hands within 1 px of the rim at 2 and 10 o'clock in `helm_hold`,
  turning more than 30° clockwise (counter-clockwise) within 1.5 px of the rim in `helm_turn_right` (`left`), the linstock hand well below the
  shoulder and forward at the lunge, the aiming hand at breech height in the next block while the head leans over it.
- **`haul` (player, PAL).** Built by `F9.buildHaul()` in `animations/player_poses.js` on `animations/player_rig.bbmodel`
  (which now also holds `rope_slide`); exported to `rope_animations/haul.json` (one animation, `"loop": true`, 1 s):
  hand over hand (arms −115° ↔ −55°, passing at −85°), the torso leaning 18–22° back pivoted at the hips, right foot
  braced forward (−22°), left back (16°). Strip `renders/anim_haul.png` (proxy sword hidden). Played by
  `combat/grapple/client/anim/PalRopeSlidePoses` on the rope layer while the player hauls: their hook latched, the rope
  taut (synced) and in the hand, sneaking, not riding (`RopeSlidePoses.hauls`). `HaulAnimationFileTest` checks the
  hips stay put while the neck lies 3–4 px behind them.
- **Export.** As in the recipe above: `Animator.buildFile(null, names)` with `autoStringify` plus a newline; the
  project files were saved by appending the new animation entries to the committed JSON (the committed animations are
  kept byte for byte, uuids included).

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

### Pirate captain and officer's coat (ART6)

**Pirate captain** (BOS1's named captain, `mob/captain/PirateCaptain`): his own type on the crew rig,
`art/models/entity/pirate_captain.bbmodel` -> `geo/pirate_captain.geo.json` + `textures/entity/pirate_captain.png`,
built like the M3 types (`SF.make('pirate_captain', repo)` then `SF.exportAll(repo)`; the painter `SF.pirate_captain` in
`seafarer_skins.js`, the cubes `SF.DETAILS.pirate_captain` and `SF.plume()` in `seafarer_models.js`). `MobKind.artId()`
now returns every kind's own id; `SeafarerRigTest` checks the captain like the other types (exact crew bones, pivots and
contract cubes, UVs on the sheet, GeckoLib bakes it, 64x64 texture). **No new bones**: 48 cubes, the details inside the
contract bones, so the shared `crew_member.animation.json` drives him unchanged (`renders/pirate_captain_walk.png`).
- Look: wide black hat (crown, red band, gold-edged brim, the left part of the brim a separate cube turned 60 degrees
  about z to cock it up, a gold clasp and a white plume of five vanes in the yz plane, each turned about x where the
  last ends: 20, 50, 85, 120, 150 degrees), all on `hat`; full black beard cube with two braids and gold beads, a gold
  earring (`head`); a scar through the left brow, grey temples (painted); charcoal coat (jacket and sleeve layers, open
  front edged in gold, brass buttons, back vent) with knee-long tails and skirts (y 2.4), lapels, a white jabot, a gold
  brocade waistcoat, a red sash with knot and ends on the left hip, a leather baldric over the right shoulder with a
  brass buckle (three cubes turned 33.7 degrees about z, front, back and buckle) (`body`); wide crimson cuffs with gold
  rings and brass buttons (arms); bucket-top boot cuffs and toe caps (legs). The hat layer is empty.
- Sheet: 27 patches; regions brim top (0,0,8,8, gold edge), brim under (56,0,8,8), crown sides (24,0,8,3), band
  (24,3,8,2), plume (24,5,8,3), crown top (32,0,8,8), lapel (0,16,4,4), jabot (52,16,4,4), cuff (36,16,8,4), boot cuff
  (12,16,8,4), tail back (56,32,4,12), tail side (60,32,4,12), sash (56,44,8,2), baldric (56,46,8,2).
- Known: at full stride the legs pass through the long tails (as with the plain pirate's).
- Renders: `renders/pirate_captain.png` (front three-quarter, front, left side, back three-quarter),
  `renders/pirate_captain_walk.png`. No sword pose: ART7's poses were not on the branch; his sword arm comes from
  `DuelistArmPose` in code.
- **Pitfall:** `SF.build` on an open project replaces the groups, and the animations made by `SF.make` then point at
  the old group uuids (the preview stops moving and the saved project's animations are detached). Close the tab and
  run `SF.make` again instead of rebuilding in place. Export in a **second** `risky_eval` call after `SF.make`: in the
  same call the texture canvas can still be the 16x16 placeholder (it wrote a 16x16 PNG once; `SeafarerRigTest`
  caught it).

**Captain's hat** (`pirates_n_ships:captains_hat`, a `HatItem` like the four H2 hats, not craftable; the captain wears it
in his head slot and drops it): `art/models/captains_hat.bbmodel` -> `models/item/captains_hat.json`, 10 elements,
palettes `palette` (black, gold, gold_l), `palette_2` (shade), `palette_4` (spice_gold), `palette_5` (flag_white for
the plume, flag_red for the band). The part list is written in head coordinates as in `tools/gen_hat_items.py` (whose
`write()` also gave the display entries: `head` scale 1.6, Y0 27.57, Z0 0) and rebuilt cube by cube in Blockbench
(`Codecs.java_block.parse` of the spec, export through `Codecs.java_block.compile()` plus credit, `gui_light` and the
spec's full display entries). Vanilla element angles only: the cocked brim turns 45 degrees (the mob's 60), the plume
runs 22.5, 45, 90 (unrotated, built along z), 135 (built along z, turned 45) and 157.5 degrees (built downwards,
turned -22.5). `HatModelTest` pairs it with `geo/pirate_captain.geo.json` (within 1 px; x max differs by 0.47 px for the
brim angle). Render `renders/captains_hat.png` (three-quarter, front, side, back three-quarter).

**Officer's coat** (`pirates_n_ships:officers_coat`, `apparel/CoatItem`, design.md §15 "officer gear"): a chest-slot
`ArmorItem` on our own armour material `pirates_n_ships:officers_coat` (registered through `Services.REGISTRY` in
`Registries.ARMOR_MATERIAL`), so vanilla's `HumanoidArmorLayer` draws it on players, armour stands and vanilla humanoids
on both loaders; no new renderer. Armour `apparel.officers_coat_armor` (default 3, like a leather tunic; 0 = none),
computed live by `CoatArmor`; no durability. Recipe: blue wool round a white wool centre with a gold ingot top middle.
- Source script `art/models/officers_coat.js` (load in `risky_eval`): `OC.build(repo)` builds the item model in a new
  `java_block` tab from `OC.PARTS` (palette patches: navy, white, red of `palette_5`, gold and brass of `palette`; no new
  colour), `OC.export(repo)` writes `models/item/officers_coat.json` (item/generated's display entries, particle
  `palette_5`) and `art/models/officers_coat.bbmodel`, `OC.paintArmor(repo)` writes the worn texture
  `textures/models/armor/officers_coat_layer_1.png`.
- Item (28 elements): the coat lies in the XY plane facing south like a sprite (z 7..9): back panel, shoulders, a
  wider skirt 0.05 px inside the panel in z, collar with gold lace, white lapels and waistcoat with gold and brass
  buttons, a gold hem, sleeves hanging out at 22.5 degrees with red cuffs and gold rings, gold epaulettes with fringe.
  Lint: 0 visible fights.
- Worn texture: vanilla's 64x32 armour layer 1, only the body (box UV 16,16) and arm (40,16) areas painted: navy coat,
  white lapels and waistcoat with gold buttons, gold waist band, darker back vent, gold epaulette on the arm tops and
  the shoulder band, red cuffs ringed in gold. The left sleeve mirrors the right one (vanilla). `CoatArmorTest` checks
  the size and that those faces are opaque.
- Known limit: a vanilla armour layer has no skirt or tails, so the worn coat ends at the waist. Tails would need a
  custom armour model through the loaders' armour-model hooks (NeoForge `IClientItemExtensions#getHumanoidArmorModel`,
  Fabric `ArmorRenderer`) behind a platform service.
- Renders: `renders/officers_coat.png` (front, three-quarter, side, back), `renders/officers_coat_worn.png` (on a
  stand-in figure in a throwaway GeckoLib tab; a Bedrock-style project draws every cube with its one texture, so the
  preview merged a base skin and the coat layer into one sheet).

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
docstring). Sources: `art/models/entity/kraken.bbmodel` (533 cubes since K1c), `kraken_model.js` (part list, UV
packer, skin painter, V1 check, export, render) and `kraken_animations.js`; render `renders/kraken.png` (side, front,
three-quarter from a deck, grab at 0.45 s, lurking, from below; the arms are posed in the pictures as the code aims
them in game (`KRK.aimQuat`, the same maths as `KrakenAim`): 50° out at the attack rest, hanging 25° out when
lurking; nothing is keyed for that).

- **Rig contract** (KrakenRigTest): 1 px per unit, y up, faces −z, +x (file) is its left, hit box 56 × 56 px. Bones
  `root [0,0,0]` → `mantle [0,30,0]` → `eye_left [12,40,−20]`, `eye_right [−12,40,−20]`; `tentacle_<i>_1` (i = 0..7)
  under `root` on a ring of radius 22.4 at y 20, angle a = (i + 0.5)·45°, x = −sin a·22.4, z = −cos a·22.4, with
  `tentacle_<i>_2` 20 px and `tentacle_<i>_3` 40 px above it (K1c; K1b had 12 and 24; the ring is `Kraken#anchor`'s).
  Rest: arms straight up, 60 px to the curled tip (`Kraken#TENTACLE_LENGTH` 3.75 blocks, `KrakenModel#TENTACLE_LENGTH`).
  Code aims and stretches `root` and every `tentacle_<i>_1`: never key them. Animations `idle` (loop), `surface`
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
  the sucker face is the inner one, the rest-pose tip curls inwards (3.7 px in K1b; `KrakenRigTest` checks the side).
- **Cubes per bone.** `mantle` 93: head of five round layers (y 12..52, radius 10.8 → 14.6 → 18.4 → 21.4 → 18.2),
  mantle of eight layers tapering from radius 15.6 at y 52 to a 2 px point at y 94.6, each round layer a square core
  plus four side slabs (a rounded square; slabs 0.1 px longer than the core at both ends, so no shared planes); two
  fins of 12 strips each (rhombus, y 62.3..86.3, out to x ±24.3, 2.2 px thick at the middle, thinner at the ends);
  a ring of lips (5) and the beak (upper mandible, lower mandible, hook; black) at the centre of the arm crown on the
  underside. `eye_left`/`eye_right` 4 each: lid rim 11.2 × 11.2, iris disc 9.6 (gold, pale ring, dark edge), pupil
  4 × 4, glint. Per arm (K1c lengths): `_1` 16 (cubes 13 and 11.4 px long, 7 and 6.2 px wide, 14 suckers), `_2` 18
  (10.6 and 10.6 px, 5.4 and 4.6 px wide, 16 suckers), `_3` 20 (3.8 px wide, 10 px straight, then 5, 4.4, 3 px long
  and 3.2, 2.5, 1.7 px wide, tilted 14°, 32°, 56°; 16 suckers). Suckers are 1.2 → 0.55 px discs in two rows on the
  inner face, 0.35 px proud.
- **Sheet (128×64, per-face UV).** `KRK.pack` packs one rectangle per primary face on shelves at two densities: fine
  parts (arms, eyes, beak, lips) 1 texel per px, the body 0.68 (its up/down faces 0.6 of that, 0.41: rims seen from
  above and the pale underside). Sharing: all eight arms use arm 0's rectangles; the right eye and fin are x-mirrors of
  the left; centred cubes reuse west for east and north for south (u reversed); each round layer repeats its sides
  four times (core west = north, side slab's outer face = front slab's front, side slab's front = front slab's side;
  the crown pattern has 8 spots per turn, so the repeat has no seam). Faces fully inside another body cube are left
  out. Patches at u 120..127, v 60..63 (sucker 4×4 pale with a dark rim at u 124, sucker side, pupil, glint, beak
  2×2). Since K1c the longer arms take more of the sheet: the packer settles at body density 0.66 (K1b 0.68), arms
  still 1 texel per px, 1099 texels free.
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
- **Aim (K1c, `KrakenAim`).** The code turns `tentacle_<i>_1` so the arm points at its hit box and, unlike K1b's
  Z·X look-at, also sets the roll: the rest basis (up, inner normal towards the axis, their cross product) goes onto
  (aim, the inner direction made perpendicular to the aim, their cross product), converted to GeckoLib's Z·Y·X angles.
  K1c turned the sucker face (and the curl of the tip and of the animations) towards the body's axis in every pose,
  which showed raised arms' suckers to the sky; since GL1 the sucker face of a raised or level arm turns down and that
  of a hanging arm towards the axis (`KrakenAim.suckerNormal`, see "GeckoLib bone rotations in code"). A tentacle without a known part leans 25° out (straight up it runs
  through the head); `KrakenAimTest` checks 72 aims, the curl side and the head clearance.

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

**Generated templates:** the starter sloop is not built by hand. Its source is the BuildSpec generator
`art/schematics/starter_sloop.py` (the format of the structure pieces below); the schematic lab turns it into
`art/schematics/starter_sloop.schem`, which is converted as in step 3. `tools/build_structures.py` only knows
`art/structures/`, so for a ship run the same steps by hand: start the lab on port 8766, `POST` the generator's JSON to
`/api/session/validate` and `/api/session/build` (no warnings), save `GET /api/session/export.schem?version=2` over the
`.schem` and run `python3 tools/schem_to_structure.py art/schematics/<name>.schem` (the `Lab` class and
`write_if_changed` of `build_structures.py` can be imported for this). Edit the generator, never the `.schem`: the two
must agree. SH1b closed a three-block hole in the sloop's bottom at the stern this way (layer 1 ended before layer 2 at
z 25, so the hold was open to the sea); the generator now puts a bottom plank under every hollow hold cell.
`starter_sloop_basic.schem` has no generator.

**The templates:**

| template | source | what it is | for |
|---|---|---|---|
| `starter_sloop` | `starter_sloop.py` | single-mast sloop, forecastle and stern cabin, 683 blocks | shipwright (400), merchant convoys |
| `starter_sloop_basic` | hand-built `.schem` | the same hull, fewer fittings | shipwright (300), merchant convoys |
| `navy_sloop_armed` | `navy_sloop_armed.py` | the starter sloop with four guns and a shot locker, 689 blocks | navy patrols (not sold) |
| `pirate_sloop_armed` | `pirate_sloop_armed.py` | the same blocks as `navy_sloop_armed` | pirate raiders (not sold) |

**Armed sloops (WS4c):** `navy_sloop_armed.py` and `pirate_sloop_armed.py` import `armed_spec` from
`starter_sloop.py`, which adds to the starter sloop's cells and changes nothing else: two cannons a side in the waist
at z 13 and 15 (masters at x 1 facing west and x 7 facing east, rears inboard at x 2 and 6), a **gun port** in front
of each muzzle (the waist bulwark block at x 0 or 8 is cut out: the bulwark is one block high and the barrel, at
12..20 px, would run into it), and the **shot locker**, a vanilla barrel on a plank stand in the hold beside the mast
step at [4, 3, 14], under the winch. It is within the gun crews' `cannons.crew.supply_range` (4) of all four guns; a
barrel, not a chest, because a chest does not open under the deck. Materialised navy and pirate voyages stock it
(`world_simulation.materialize.cannon_rounds`); any barrel or chest (block tag `pirates_n_ships:shot_lockers`) within
reach of a gun counts as its locker. `ArmedSloopLayoutTest` diffs the armed structures against `starter_sloop.nbt`
and checks the guns, ports and reach. `starter_sloop.py` run on its own still prints the starter sloop byte for byte.
Rebuild all three with the steps above (lab, then `schem_to_structure.py`).

Keep templates under the ship block limit (2048 by default), keep every block connected to the helm (the assembler
gathers only connected blocks), and leave no loose terrain blocks (dirt, sand, stone) in the selection: they never
become part of a ship.

## Structures (ST1)
World structures (design.md §10.1) are vanilla **jigsaw pieces**. Each piece is written as code, not built by hand: a
BuildSpec generator in `art/structures/<group>/<piece>.py` (the format of `minecraft-schematic-lab`, the tool the
human used for `art/schematics/starter_sloop.py`). Sources, schematics, renders and NBT are all committed. The
`world` module (WG1) registers the pools and structure sets that use them.

**Pipeline:** `python3 tools/build_structures.py` (all pieces) or `python3 tools/build_structures.py village/tavern`.
For every source it:
1. runs the generator, which prints the BuildSpec JSON;
2. finds the lab on port **8766** (or `--port`, or `$SCHEMATIC_LAB_PORT`), or starts it there (`npx -y github:SimoneRecchia/minecraft-schematic-lab#v0.1.0`,
   HTTP mode, which never opens a browser) and stops it at the end. Port 8765 is left for the human's own MCP
   instance. The run fails on `valid: false` or on any lab warning (unknown block id, blocks outside the size);
3. saves `art/schematics/structures/<group>/<piece>.schem` (Sponge v2, `export.schem?version=2`) and
   `art/renders/structures/<group>/<piece>.png` (the lab's isometric preview from the south-east, flat block
   colours);
4. converts the schematic with `tools/schem_to_structure.py` to
   `common/src/main/resources/data/pirates_n_ships/structure/<group>/<piece>.nbt`, the structure
   `pirates_n_ships:<group>/<piece>`.

Files are only rewritten when their bytes change, and the lab is deterministic, so a second run changes nothing.
`--views DIR` also saves renders from the other three corners (not committed; useful because the committed render
shows only the south and east faces, while doors face north). `StructurePiecesTest` checks the committed NBT: the
pieces load, stay within 32 blocks per axis, use only existing blocks and states (mod blocks against the datagen
block states), follow the jigsaw convention below, have the expected connectors and berths, and match a fresh
conversion of the committed `.schem`.

**Four-corner review:** `python3 tools/iso_render.py art/schematics/structures/village/tavern.schem --out <dir>`
writes `<piece>_{se,ne,nw,sw}.png`, the piece seen from each corner. It draws slabs, stairs, open and closed
trapdoors, doors, ladders, glass panes, fences, walls, lanterns, beds and carpets as their real shapes in rough
Minecraft colours (everything else as cubes, jigsaws as small magenta cubes), so sills, shutters, rafter ends and
plinths show; `--cut Y` hides the rows above Y to look inside. It is the tool for reviewing a piece from all four
corners. The lab's render (and `--views`) is only a colour check: it draws every block as a whole cube coloured by its
name. Standard library only (it reads the schematic through `tools/schem_to_structure.py`).

**Generators:** `art/structures/buildspec.py` holds the shared `Piece` helper: `put`/`fill`/`ring` take a palette
key or a literal state, `roof_ridge_x`/`roof_ridge_z` build stair roofs with gables, and `connector`/`berth` add the
jigsaws. `Piece.emit` merges runs along x into `box` operations and adds the jigsaws as `block_entity` operations.
`art/structures/<group>/_style.py` holds the group's palette and small fittings (doors, beds, tables, lantern posts).
Files starting with `_` are helpers, not pieces.

**Connecting blocks need no side properties (WG4).** Fences, glass panes, iron bars (and brig bars) and walls get
their `north`/`east`/`south`/`west` (and a wall's `up`) at placement from the processor `pirates_n_ships:connections`
(`world.structure.ConnectionsProcessor`), which every port pool element (processor list `pirates_n_ships:connections`)
and every wreck piece runs. It connects each block to its neighbours in the same template, in the template's frame,
so generators may write plain `spruce_fence` or `iron_bars`; any side values a generator does write are recomputed
for sides whose neighbour is in the template. A side facing outside the template (the terrain, another piece) keeps
the value the template stores, so a rail that must meet a neighbouring piece needs that side written explicitly.

**Coordinates:** y 0 is the piece's **foundation row**. It sits level with the terrain surface (WG1 sinks land
pieces by one). Pieces face **north (−z)**: a building's door and its `building_in` jigsaw are on the −z side,
in front of the door on row z 0 (doorstep, porch or apron). Keep every block inside the piece's box. A jigsaw sits
on the face of the box it points out of, because the child piece is placed in the block beyond it and must not
overlap the parent's box (so no roof overhang on a face that has a connector, except in the connector's own row).

**Jigsaw convention** (vanilla `minecraft:jigsaw`, horizontal, `orientation=<front>_up`, written as `block_entity`
operations):

| name | target | pool | joint | used by |
|---|---|---|---|---|
| `pirates_n_ships:street_out` | `pirates_n_ships:street_in` | `pirates_n_ships:village/streets` | aligned | dock head (south edge), street (south end) |
| `pirates_n_ships:street_in` | `pirates_n_ships:street_out` | `minecraft:empty` | aligned | street (north end) |
| `pirates_n_ships:building_out` | `pirates_n_ships:building_in` | `pirates_n_ships:village/buildings` | rollable | street (both verges) |
| `pirates_n_ships:building_in` | `pirates_n_ships:building_out` | `minecraft:empty` | rollable | house, tavern, shipwright (north side, y 0) |
| `pirates_n_ships:pier_out` | `pirates_n_ships:pier_in` | `pirates_n_ships:village/pier` | aligned | dock head (north edge) |
| `pirates_n_ships:pier_in` | `pirates_n_ships:pier_out` | `minecraft:empty` | aligned | pier (south end, deck row) |
| `pirates_n_ships:berth` | `minecraft:empty` | `minecraft:empty` | aligned | pier (berth markers) |

`*_out` jigsaws pull a piece from their pool. `*_in` jigsaws are where a piece attaches, and they spawn nothing.
`final_state` is the block that belongs in that spot after generation: cobblestone for street ends and doorsteps,
dirt path for the street verges, chiseled or plain stone bricks on the quay, spruce planks on the pier deck, gravel
for the shipwright's apron. The pools are `pirates_n_ships:village/start` (the dock head), `village/streets`,
`village/buildings`, `village/pier` and `village/terminators` (the fallback for `street_out` once the depth runs
out; its piece is `street_end`). The terminators pool must never be empty: vanilla skips a connector whose
fallback pool is empty, so an empty pool would stop every street at the dock head.

**Berth markers:** a `minecraft:jigsaw` named `pirates_n_ships:berth` (target and pool `minecraft:empty`,
`final_state` `minecraft:water`). It sits at sea level, one block out from the pier's side, at the berth's
centre along the pier. Its orientation points along the berth's bow direction (parallel to the pier, toward the
sea). The pier has two, one on each side at `[0, 4, 9]` and `[6, 4, 9]`, both pointing north. When WG1 places the
structure, it reads them into the port registry (shipwright pickups, §4.1) before the jigsaw becomes water. A ship
placed there lies alongside the pier with its bow toward the sea, centred on the marker along the pier, and its near
side in the marker's column (the first water column beside the deck).

**Sea level:** the dock head's paving (its y 0) sits one block above sea level. The pier hangs from it: its deck is
row y 5 (level with the paving), the sea surface is its row y 4, and its piles (y 1..4) stand on cobblestone footings
on its row y 0, five blocks below the paving. WG1 places the dock head with y 0 at sea level + 1. Where the seabed is
deeper, the footings hang in the water.

**The seafarer village (`village`):**

| piece | size (x×y×z) | blocks | contents | mod blocks |
|---|---|---|---|---|
| `dock_head` (start) | 11×8×11 | 308 | stone quay (mossy towards the sea, cracked inland), harbor master's hut (desk facing the door, cargo, lectern): render on a cobblestone plinth between spruce corner posts, framed door under a hood, windows with sills (shutters where the box allows), spruce band in the gables, rafter ends; notice board beside the door, two lantern posts at the pier landing, mooring rings on the sea edge, crates and barrels, a bench at the hut's west wall, a barrel with a flower pot by the street; `pier_out` [5, 0, 0], `street_out` [2, 0, 10] | `harbor_desk`, `notice_board`, `mooring_ring`, `cargo_crate`, `cargo_barrel` |
| `pier` | 7×9×20 | 240 | 5 wide plank deck (a few weathered dark oak boards) on spruce piles with mostly mossy footings, cross beams, stripped spruce wales at the waterline, a rail and two lantern posts at the seaward end, a ladder down; at the landward end (clear of the berths) a lantern post and rail on each edge and cargo (barrels, crates); `pier_in` [3, 5, 19], berths [0, 4, 9] and [6, 4, 9] | `mooring_ring`, `cleat`, `cargo_crate`, `cargo_barrel` |
| `street` | 7×4×7 | 54 | cobbled crown with gravel and moss patches between stone brick kerbs (some mossy or cracked), dirt path verges with coarse patches, a lantern post, a barrel with a flower pot (both clear of the doors at z 3); `street_in` [3, 0, 0], `street_out` [3, 0, 6], `building_out` [0, 0, 3] and [6, 0, 3] | |
| `street_end` (terminator) | 7×4×3 | 29 | cobbled turning place, the kerbs turning along the far edge, a lantern post between two benches, two barrels (one with a flower pot); `street_in` [3, 0, 0] | |
| `house_small` | 7×9×9 | 261 | 7×7 cottage: mossy cobblestone plinth, white render between stripped spruce corner posts, framed door under a hood, windows with sills (outside on the front and back, set into the wall on the gables), shutters on the back, spruce wall plate and gable band, rafter ends under both eaves, render gables with attic lights, brick chimney on the east gable over a furnace hearth, red tile roof; bed, table and stool, chest, barrel with lantern, crafting table with flower pot, bookshelf, rug, a lantern on a tie beam; `building_in` [3, 0, 0] | |
| `tavern` | 11×15×11 | 774 | 11×9, two floors: mossy plinth, render between stripped spruce corner and mid posts and oak door posts, spruce band at the upper floor, tall framed ground floor windows and single upper ones with shutters and sills, a gallery rail on a slab ledge and brackets over the porch with a french door behind it, lanterns either side of the door, rafter ends, boarded gables with lights, brick chimney on the east gable over a hearth; bar with barrels, two tables with stools, a hanging lantern, stairs, two beds upstairs, a crate, rugs, a lantern on a tie beam; a dark oak panel above the door for a sign; `building_in` [5, 0, 0] | `cargo_barrel`, `cargo_crate` |
| `shipwright` | 11×13×10 | 469 | open shed on posts (mossy cobblestone footings, knee braces under the beams) with a dark roof, a king post truss in the open front gable, a back wall of two cobblestone courses, a spruce band and render with three windows, an oak boarded gable with a light; a half-built hull (keel, stem, three frames, a strake) on a gravel slipway, a hoist chain from a collar beam over the bow, sawhorses, stacked logs and planks, crafting and smithing tables, a grindstone, a tar cauldron; `building_in` [5, 0, 0] | |

Renders: `art/renders/structures/village/{dock_head,pier,street,street_end,house_small,tavern,shipwright}.png`.

**ST4a pass (the look):** every village piece follows design.md §10.1 "Look of the buildings" with one palette from
`art/structures/village/_style.py`: spruce for the frame (stripped spruce posts, plates, bands, rafter ends, shutters,
doors, sills), oak for the joinery around openings (stripped oak lintels and door frames, the shipwright's boards),
cobblestone for plinths and footings (mossy towards the ground) and stone bricks for the quay and the street kerbs,
red brick as the accent (tile roofs, ridges, chimneys) with dark shingle roofs on the working buildings, white render
(calcite) as the infill. The fittings: `corner_post`, `plinth` (cobblestone course over a mostly mossy foundation row,
a fixed scatter so the output stays byte-identical), `trim_band` (stripped logs over the infill only), `window` (panes,
an oak lintel, spruce slab sill and open trapdoor shutters outside; where the outside is beyond the box, e.g. the east
and west walls that are the box faces, an upside-down stair set into the wall as the sill and no shutters),
`door_frame` (door, oak posts and lintel, an upside-down stair hood), `rafter_ends` (upside-down stairs under the
eaves), `chimney` (bricks with a brick wall pot, no campfire) and `hearth` (a furnace at its foot). `buildspec.Piece`
gained `inside` and `put_inside` for fittings near the box faces. Box sizes, jigsaws, berths, final states and doors
are unchanged. Note the lab's preview colours whole cubes by name (stairs, trapdoors and fences render as full blocks,
mod blocks in hashed colours), so the committed renders look heavier than the game.

**Adding a piece:** copy a generator in the group's folder, keep the conventions above (foundation row, north
front, connectors on the box faces), then run `python3 tools/build_structures.py <group>/<piece>`. Look at the
render (and `--views`) for floating blocks, roofs that miss walls, doors without a path, and posts that don't reach
the ground. Add the piece to `StructurePiecesTest` (the expected piece list and its connectors), and to its pool
in the world module.

### Pirate island (ST2)
The pirate island camp (`pirate_island`, design.md §10.1) uses the same pipeline and conventions as the village: y 0
is the foundation row, pieces face north (−z), connectors sit on the box face they point out of. Its own jigsaw
vocabulary lives in `art/structures/pirate_island/_style.py` (it adds the names to `buildspec.CONNECTORS`), together
with the group's palette (weathered spruce and dark oak, stripped logs as posts, white, grey and brown wool canvas,
mossy cobblestone, no stone bricks) and fittings (torch posts, palisade stakes, tables, stools, bed rolls, the
treasure marker, the flagpole).

| name | target | pool | joint | used by |
|---|---|---|---|---|
| `pirates_n_ships:jetty_out` | `pirates_n_ships:jetty_in` | `pirates_n_ships:pirate_island/jetty` | aligned | camp (north edge) |
| `pirates_n_ships:jetty_in` | `pirates_n_ships:jetty_out` | `minecraft:empty` | aligned | jetty (south end, deck row) |
| `pirates_n_ships:path_out` | `pirates_n_ships:path_in` | `pirates_n_ships:pirate_island/paths` | aligned | camp (east and west edges), path (south end) |
| `pirates_n_ships:path_in` | `pirates_n_ships:path_out` | `minecraft:empty` | aligned | path, path_end (north end) |
| `pirates_n_ships:hut_out` | `pirates_n_ships:hut_in` | `pirates_n_ships:pirate_island/huts` | rollable | camp (south edge), path (both sides) |
| `pirates_n_ships:hut_in` | `pirates_n_ships:hut_out` | `minecraft:empty` | rollable | tent, tavern hut, captain's hut (north side, y 0), treasure spot (north side, y 2) |
| `pirates_n_ships:berth` | `minecraft:empty` | `minecraft:empty` | aligned | jetty (berth markers, as the village pier) |
| `pirates_n_ships:treasure` | `minecraft:empty` | `minecraft:empty` | aligned | treasure spot (the buried treasure marker) |

`final_state`: gravel where the trails meet (`jetty_out`, `path_out`, `path_in`), dirt path for the camp's
`hut_out`, coarse dirt for the trail's `hut_out`, sand for every `hut_in`, spruce planks for `jetty_in`, water for the
berths, sand for the treasure. Pools: `pirates_n_ships:pirate_island/start` (the camp), `pirate_island/paths`
(`path`), `pirate_island/huts` (`tent`, `tavern_hut`, `captains_hut`, `treasure_spot`), `pirate_island/jetty`
(`jetty`) and `pirate_island/terminators` (`path_end`, the fallback for `path_out` once the depth runs out). The world
module (WG2) defines them.

**Sea level:** as in the village. The camp's sand (its y 0) sits one block above sea level; the jetty's deck is its
row y 5 (level with the sand), the sea surface its row y 4, its posts stand on footings on its row y 0. The berths are
at `[0, 4, 7]` and `[4, 4, 7]`, both pointing north, one block out from the 3 wide deck.

**Buried treasure marker:** a `minecraft:jigsaw` named `pirates_n_ships:treasure` (target and pool `minecraft:empty`,
joint aligned, `final_state` `minecraft:sand`), pointing north. It sits **two blocks under the surface** at the
treasure spot's centre, under two stripped logs crossed on the sand. The treasure spot is the one piece whose
surface is not row y 0: its rows 0 and 1 are the sand the treasure is buried in, its surface is row y 2 and its
`hut_in` sits on that row, so the surface lines up with the trail it hangs from. When WG2 places the structure, it
replaces the marker with a buried chest (a treasure loot table) and records the position as a treasure map target;
left alone, the jigsaw just becomes sand.

**Jolly Roger:** the camp's flagpole is a stack of five `pirates_n_ships:flagpole` blocks. The top one carries
`flag=jolly_roger` (the model) and the block entity data `{flagpole: {kind: "jolly_roger", item: {id:
"pirates_n_ships:jolly_roger_flag", count: 1}}}` (the real state, `FlagpoleState`), written by `_style.flagpole` as a
`block_entity` operation; it flies east over open sand.

| piece | size (x×y×z) | blocks | contents | mod blocks |
|---|---|---|---|---|
| `camp_start` (start) | 13×8×13 | 399 | sandy clearing with gravel and dirt trails, a campfire on a cobblestone hearth with driftwood log seats and a roasting spit, a keg, the Jolly Roger, a loot heap (barrels, crates, a chest) under a striped sailcloth lean-to, the fence's lean-to shack (desk as the counter, stock, cobwebs; vertical dark and spruce boards with salvaged patches, palm front posts, sill beam, rafter ends, barred and shuttered windows) with the notice board on its south side under a striped awning, a crude palisade of uneven stakes on the inland sides with taller gate posts (a skull and a torch on the south gate), lantern posts, gravel and mossy skirts; `jetty_out` [6, 0, 0], `path_out` [0, 0, 9] and [12, 0, 9], `hut_out` [6, 0, 12] | `flagpole`, `harbor_desk`, `notice_board`, `cargo_crate`, `cargo_barrel` |
| `jetty` | 5×9×16 | 117 | 3 wide patched plank deck (dark oak, a salvaged jungle board, sagging slabs) on mixed bark and stripped posts (two crooked ones leaning on stair braces) with footings, palm bollards and a rope rail at the seaward end, a lantern post and a rail with a net, a ladder down, a crude crane (palm mast, fence jib on a stair brace, chain hook with a crate) and a rope rail at the landward end, barrels and a lantern post; columns x 0 and x 4 stay clear; `jetty_in` [2, 5, 15], berths [0, 4, 7] and [4, 4, 7] | `cleat`, `mooring_ring`, `cargo_crate`, `cargo_barrel` |
| `path` | 7×4×7 | 73 | gravel and dirt trail on sand between uneven palisade stakes (palm, stripped spruce, dark oak, fence tips), one propped on a stair, a lantern and a torch, a barrel and dead bushes, gravel at the stakes' feet; `path_in` [3, 0, 0], `path_out` [3, 0, 6], `hut_out` [0, 0, 3] and [6, 0, 3] | |
| `path_end` | 7×4×3 | 42 | the trail ends at a row of uneven stakes with a palm warning post carrying a skull, a lantern, a crate and a dead bush; `path_in` [3, 0, 0] | `cargo_crate` |
| `tent` | 7×6×7 | 125 | A-frame of stepped wool (vanilla has no wool stairs or slabs) in patched sailcloth (white, grey and brown patches, a red patch, brown hem) over a stripped log ridge that runs on over a fly on two poles, a hanging lantern at the front, a red pennant at the back, a guy stake and a crate, a bed roll, a chest, a barrel, lanterns, a rug; `hut_in` [3, 0, 0] | `cargo_crate` |
| `tavern_hut` | 9×8×9 | 309 | open-sided hut (board course of alternating dark and spruce boards, fence rail, palm corner posts, stripped door posts) on a cobblestone plinth going mossy, under a patched dark oak thatch roof with rafter ends; gables on a tie beam with vertical boards, king post and vents; a shuttered back window and a fieldstone chimney with a smoking top; a bar of barrels and a plank counter with a skull, kegs and rum behind it, two tables with stools, lanterns (one over the doorway), cobwebs; `hut_in` [4, 0, 0] | `cargo_barrel` |
| `captains_hut` | 9×9×9 | 336 | room on palm and stripped dark oak stilts (floor y 2, sill beam round it, salvage stowed below), walls of vertical dark and spruce boards between stripped posts, a door between dark frame posts, shuttered windows (sills on the back), gables on a tie beam with king post and vent, a patched thatch roof with rafter ends, two steps up to a porch with rope rails and a skull, a lantern post; a bed, a cartography table with a map tile and a stool, the sea chest, a rug, a black banner, a hanging lantern; `hut_in` [4, 0, 0] | `map_tile`, `sea_chest`, `cargo_crate` |
| `treasure_spot` | 5×5×5 | 89 | sand speckled with gravel and coarse dirt, two crossed stripped logs, a skull, a bone, an old trapdoor lid, dead bushes, a mossy cairn and a palm marker stake; the column over the marker stays sand; the treasure marker [2, 0, 2]; `hut_in` [2, 2, 0] | |

Renders: `art/renders/structures/pirate_island/*.png`.

**ST4b pass (the look of the buildings, design.md §10.1):** every island piece was rebuilt to the camp style with
the same sizes, connectors, berths, treasure marker and doorsteps. `_style.py` holds the camp palette: two woods,
spruce and dark oak, with jungle logs as palm-trunk posts and stripped jungle as pale driftwood; one stone,
cobblestone going mossy where it meets the ground; one accent, sailcloth (white wool with red stripes and brown
patches). It also holds the reusable fittings: `awning` (striped sailcloth on fence poles), `palisade_post` (an uneven
palm, spruce or dark oak stake with a fence tip), `shutter_window` (an opening with a trapdoor sill and open
trapdoor shutters folded against the wall, one or both), `rope_rail` (posts with chain slung between),
`rafter_ends` (upside-down stair corbels under an eave), `campfire_ring` (a hearth, seats and a spit), `sand_skirt`
(the gravel, coarse dirt and mossy gradient at the foot of everything), `weatherboard` (vertical boards of two
woods with the odd salvaged jungle board), `roof_patches` (spruce mends in a dark oak thatch), `chimney` (fieldstone
with a smoking campfire on top), `lantern_post`, and `jit`, a fixed per-position hash that makes the irregularity
repeatable byte for byte. States leave `waterlogged` at its default: the lab's preview colours any state string
containing "water" as water. Every piece was reviewed from all four corners with `tools/iso_render.py`
(shape-aware); the lab's preview draws whole cubes and one colour for all wool.

**Lab port per agent:** `tools/build_structures.py` takes the lab's port from `--port`, else from the
`SCHEMATIC_LAB_PORT` environment variable, else 8766. Agents building pieces in parallel each use their own port
(ST2 was built on 8767: `SCHEMATIC_LAB_PORT=8767 python3 tools/build_structures.py pirate_island/tent`), so they never
post to each other's lab session.

### Navy outpost (ST3)
The navy fort (design.md §10.1) as a third piece set, group `navy_outpost`, built with the same pipeline and
conventions (y 0 the foundation row, pieces face north, the sea to the north). Look: stone bricks and polished
andesite masonry, spruce and dark oak woodwork (since ST4c), blue wool and blue banners for the navy, lanterns on iron bars. The
generators share `art/structures/navy_outpost/_style.py`: the palette, the jigsaw table, the fittings (doors, beds,
tables, stools, lantern posts, brig doors, the cannon, the flagpole) and `curtain()`, the curtain wall's section that
the gate, the wall and the tower share so the walkway runs through: foundation and solid body on z 0..4 up to the
walkway (top row y 4, people stand on y 5), the seaward parapet on z 0 (y 5) with merlons above it.

**ST4c pass (look of the buildings, design.md §10.1):** every outpost piece was rebuilt to the standing rule, with the
same boxes, connectors, berths, garrison posts, gate openings, court level, cannon and cell doors. The set's palette is
one stone (stone bricks with mossy, cracked and chiseled variants; polished andesite for string courses, kerbs and
walkways), two woods (spruce for structure, roofs, hoardings and floors; dark oak for trim: shutters, sills, brackets,
rafter ends, gun decks, door posts) and the navy's blue as the accent. `_style.py` gained the fittings every piece uses:
`fort_face` (the outer skin of a fort wall: pilasters, recessed bays on a sloped plinth, an upside-down stair corbel
table; `curtain()` now builds its seaward skin with it, pilasters at both ends so walls meet flush pilaster to
pilaster), `merlons` (slab-capped), `string_course`, `arrow_slit` (open or barred), `buttress`, `window` (pane or iron
bars, trapdoor sill and open trapdoor shutters where the box has room), `rafter_ends`, `chimney` (a stack with a smoking
campfire), `chain`, `fence_run` (joined fence rails), `bars`/`pane`/`fence`/`stair_shape` (full states), `age` (a
deterministic weathering gradient: mossy on the ground row, less one and two rows up, cracked bricks scattered; a
position hash, never `random`, so rebuilds are byte-identical) and `dry` (drops the default `waterlogged=false` from
the emitted states, because the lab's preview paints any state that mentions water blue). Pieces were reviewed from
all four corners (the lab's renders and `--views`) and as face-on elevations.

**Pools:** `pirates_n_ships:navy_outpost/start` (the fort gate), `navy_outpost/walls` (the wall), `navy_outpost/
buildings` (barracks, brig, watchtower), `navy_outpost/quay` (the quay) and `navy_outpost/terminators` (the wall
tower, the fallback once the walls' depth runs out). WG1 defines them.

**Jigsaws** (all horizontal, `final_state` in brackets):

| name | target | pool | joint | used by |
|---|---|---|---|---|
| `pirates_n_ships:quay_out` | `pirates_n_ships:quay_in` | `pirates_n_ships:navy_outpost/quay` | aligned | fort gate [7, 0, 0] north (chiseled stone bricks) |
| `pirates_n_ships:quay_in` | `pirates_n_ships:quay_out` | `minecraft:empty` | aligned | quay [3, 5, 17] south (stone bricks) |
| `pirates_n_ships:wall_east_out` | `pirates_n_ships:wall_east_in` | `pirates_n_ships:navy_outpost/walls` | aligned | fort gate [14, 0, 2], wall [6, 0, 2]; east (stone bricks) |
| `pirates_n_ships:wall_east_in` | `pirates_n_ships:wall_east_out` | `minecraft:empty` | aligned | wall, wall tower [0, 0, 2]; west (stone bricks) |
| `pirates_n_ships:wall_west_out` | `pirates_n_ships:wall_west_in` | `pirates_n_ships:navy_outpost/walls` | aligned | fort gate, wall [0, 0, 3]; west (stone bricks) |
| `pirates_n_ships:wall_west_in` | `pirates_n_ships:wall_west_out` | `minecraft:empty` | aligned | wall, wall tower [6, 0, 3]; east (stone bricks) |
| `pirates_n_ships:building_out` | `pirates_n_ships:building_in` | `pirates_n_ships:navy_outpost/buildings` | rollable | fort gate [14, 0, 14] south (gravel) |
| `pirates_n_ships:building_in` | `pirates_n_ships:building_out` | `minecraft:empty` | rollable | barracks, brig, watchtower (north side, y 0; cobblestone) |
| `pirates_n_ships:berth` | `minecraft:empty` | `minecraft:empty` | aligned | quay [0, 4, 9] and [6, 4, 9], north (water) |

The names `building_out`/`building_in` are the village's too; the pool keeps the sets apart (a navy gate only pulls
from `navy_outpost/buildings`). **Why the wall has two pairs:** the curtain wall runs both ways from the gate along x,
and a jigsaw only turns a piece, it never mirrors one. A single `wall_in`/`wall_out` pair would turn every wall on the
west run round by 180°, with its guns pointing inland. So each run has its own pair on its own row: the east run
(`wall_east_*`, z 2) grows out of east faces into west faces, the west run (`wall_west_*`, z 3) the other way round.
Every wall piece carries all four; the pair it does not attach by stays unused (its `_out` points back into the piece
it hangs from, finds no room and becomes stone bricks). The tower carries both `_in`s, so it ends either run.

**Sea level:** as the village. The gate's paving (y 0) sits one block above sea level; the quay's deck (y 5) continues
it through the sea gate, its sea surface is row y 4, and its masonry reaches down to its y 0, five blocks below the
paving. WG1 places the gate with y 0 at sea level + 1; the walls share the gate's y 0, so their seaward face stands at
the waterline.

| piece | size (x×y×z) | blocks | contents | mod blocks |
|---|---|---|---|---|
| `fort_gate` (start) | 15×10×15 | 934 | the curtain wall along the sea side (pilasters, recessed bays with arrow slits on a mossy sloped plinth, corbelled parapet, capped merlons) with an arched sea gate (x 6..8, chiseled keystone) to the quay and a timber guard shelter with the alarm bell over it; flanks and landward wall two thick with the same skin, embrasures for the guards; the parade court (andesite path between the gates, the navy flag on a pole on a stepped pedestal, lantern posts, cargo), stone stairs up to the walkway on the east side, the harbor master's office on the west side (door east in a dark oak frame under a blue panel, a shuttered window south; the desk facing the door, a notice board, a cartography table, shelves, a chest, a barrel, a blue banner; its flat roof joins the walkway), and the landward gatehouse: an arched gate between two square towers (lanterns and capped merlons, arrow slits, blue banners), a raised portcullis of iron bars in its slot under the navy's colours, buttresses onto a gravel apron (row z 14) | `harbor_desk`, `notice_board`, `flagpole`, `cargo_crate`, `cargo_barrel` |
| `quay` | 7×9×18 | 591 | a solid stone brick mole (x 1..5) from the seabed to the deck, weathered mossy at and below the waterline; andesite kerbs and bands across the deck, mooring rings and cleats along both edges, four bollards (stone brick walls under upside-down stairs), a crane at the seaward end (spruce log post with dark oak braces, fence jib to the deck edge with a hanging lantern and a hoist chain, a crate counterweight, a blue banner), a capstan, chains on the deck, lantern posts, a ladder down to the water, cargo; berths [0, 4, 9] and [6, 4, 9] | `mooring_ring`, `cleat`, `cargo_crate`, `cargo_barrel` |
| `wall` | 7×8×7 | 213 | a curtain wall segment: pilasters at both ends, a recessed bay with two arrow slits on a mossy sloped plinth, a corbel table under the parapet, slab-capped merlons; an embrasure with a cannon behind it on a dark oak gun deck (master [3, 5, 1], rear [3, 5, 2], muzzle north), a powder barrel [2, 5, 1] and a shot locker [4, 5, 1] against the parapet either side of the muzzle, the walkway behind the gun clear two wide (z 3..4) from end to end (ST3b); landward a timber hoarding (spruce deck on dark oak brackets, fence rail) over an arched store niche with powder and shot, a ladder up, a lantern on the rail beside the ladder's head, a gravel path at the foot | `cannon`, `cargo_barrel`, `cargo_crate` |
| `wall_tower` (terminator) | 7×12×7 | 358 | a crenellated corner tower: a 5×5 core set back between four full-height corner pilasters, sloped mossy plinth, barred arrow slits, a corbel table carrying the 7×7 roof platform (y 9) with capped merlons, lanterns on the corner merlons and the navy flag on a pole in the middle; ground floor (door landward under a blue banner, a barrel with a lantern, a crate), a landing level with the walkway (corbelled ledges outside, doors out onto both sides, powder, a chest), one ladder through all floors, a blue banner on the sea face | `flagpole`, `cargo_barrel`, `cargo_crate` |
| `barracks` | 11×9×9 | 395 | a timber frame (spruce log corners, stripped posts, a beam course) with polished andesite infill on a mossy stone brick plinth, dark oak door posts under a blue panel, shuttered windows with sills front and back, spruce gables with a tie beam and window, a spruce roof with dark oak eaves over dark oak rafter ends, two lanterns hanging by the door, a stone chimney breast and stack with smoke at the back; six bunks (blue beds, two doubled up), sea chests, the hearth with a blue banner over it, a mess table with stools, a weapon rack (fences with pressure plates), stores, a runner, a hanging lantern | `cargo_crate` |
| `brig` | 9×8×9 | 351 | a stone lock-up: pilasters and recessed bays on a mossy sloped plinth on the sides and back (the cells' barred windows in the recesses), corner buttresses and a stone portal (chiseled keystone, slab hood) round the door in front, barred windows, lantern posts, a flat roof behind slab-capped merlons with lanterns on the front corners; a guard room (table, stool, chest, barrel, crate, lanterns, key chain) and two cells, each fronted by brig bars with a brig door, with straw, a cauldron, shackle chains and a barred window | `brig_bars`, `brig_door`, `cargo_crate` |
| `watchtower` | 5×14×5 | 249 | a stone shaft with corner pilasters, set-back sides on a sloped mossy plinth under corbels, an andesite band, buttresses flanking the door, a blue banner high on the front, barred arrow slits, a ladder inside to a hatch in the lookout platform (y 10, overhanging the doorstep on a stair corbel), fence rails, a low hipped spruce roof with dark oak eaves on four bark posts with a hanging lantern | |

Renders: `art/renders/structures/navy_outpost/{fort_gate,quay,wall,wall_tower,barracks,brig,watchtower}.png`.

**Notes for the world module:**
- **The cannon** is two blocks, placed as a bed is: the master (`part=front`, the muzzle end, which owns the block
  entity) and the rear one block behind it (`part=rear`), both with the muzzle's `facing` and `load=empty`. The
  template stores the two states; the master's block entity is created on placement with default data (no elevation,
  no cooldown). A jigsaw rotation turns `facing` with the piece, so the two halves stay together. The gun is unloaded;
  the cargo barrel beside it is the "powder barrel" as decoration, it holds nothing. Firing needs powder and shot
  (§8.2), so a garrison that should fire must be supplied by the world module or a loot table.
- **The flag:** the top block of each flagpole (the gate's [10, 4, 9], the tower's [3, 11, 3]) is
  `flagpole[flag=navy,facing=east,part=top]` with the block entity data `{flagpole: {kind: "navy", item: {id:
  "pirates_n_ships:navy_flag", count: 1}}}`, the format `FlagpoleBlockEntity` saves. The poles below it are bare
  (`flag=none`, no data). The pole turns the flag with the wind once it ticks. `Piece` has no general block entity
  call, so `_style.flagpole` puts the hoisted pole into the piece's `jigsaws` map, which `Piece.spec()` emits as
  `block_entity` operations. **Parts (ST3b):** every flagpole block stores the `part` it shows (VIS1a's tall poles:
  `bottom`, `middle`..., `top`, a lone block `single`; `_style.pole_part`), so a placed pole looks right before its
  first tick. The pirate camp's Jolly Roger pole (`pirate_island/_style.flagpole`) does the same; the ship templates'
  poles are single blocks and store the default `single`.
- **Walkway (ST3b):** the walkway (people stand on y 5) runs clear and two blocks wide through every piece of the
  curtain: rows z 3..4 along the whole wall segment (the powder barrel and the shot locker stand against the parapet
  either side of the muzzle, the lantern sits on the hoarding's rail), rows z 2..3 across the fort gate (between the
  guard shelter's posts and the lantern posts), and all four rows z 1..4 free in the end columns where two pieces
  meet, so mobs walk from the gate along a whole wall run and back (`SquadRoutesTest` checks the lanes in the
  committed templates). The squad route (`mob/squad/SquadRoutes`, MOB2) uses it: after the quay the officer climbs the
  court's stairs, walks out along the east run to the far end of every wall, back along it and the gate to the west
  run and out to the far end of every wall there, and back to the head of the stairs. The towers' roofs (reached only
  by a ladder) stay off the route. A wall guard on the west run walks some 35 blocks to the court (east to the stairs
  and back), longer than vanilla's path search allows for our mobs' 32-block follow range, so the squad plans its
  paths with a wider range (`mob/squad/SquadReach`).
- **Brig doors** are closed and unlocked: a generated door has no owner (`BrigDoorBlockEntity` is created empty on
  placement), and a locked door without an owner would open only with a key.
- Chests and barrels are empty (no loot tables yet).

### Wrecks (ST5)
Wrecks (`wreck`, design.md §10.1) use the ST1 pipeline but are **single pieces**: no jigsaw blocks, no pools. A later
world package places one piece per wreck on the ocean floor. Sources are `art/structures/wreck/*.py`; the group's
`_style.py` holds the drowned palette (dark oak and spruce planks, stripped dark oak and spruce logs, spruce log
masts, mossy and plain cobblestone ballast, tuff and prismarine speckles for the barnacle crust, iron bars and chains,
a sand, gravel and clay seabed), the loot chest, and seabed helpers (`bed_disc` for the ragged bed, `drift` for sand
banked against the wreck, `underpin` so nothing on row y 1 floats over the bed's edge). The random parts (missing
planks, speckles, the bed's edge) come from a fixed `random.Random` seed per piece, so a rebuild changes nothing.

**Coordinates:** y 0 is the **seabed row**: the top row of the terrain's sand or gravel, which the piece's own thin
bed replaces. Everything above it stands in water. Placement puts the piece's y 0 on the ocean floor surface
(`OCEAN_FLOOR_WG` height minus one) and may rotate it freely; the pieces have no front.

**Under water:** every block that has a `waterlogged` property is written with `waterlogged=true` (stairs, slabs,
fences, trapdoors, panes, chains, iron bars, lanterns, walls and the chests; vanilla chests can be waterlogged). Mod
blocks without the property (cargo crates and barrels, the cannon, cleat, nameplate, yard, sea chest) simply take
their cell. Air is never written (the converter drops it), so the sea fills every gap, the hulls' insides included:
the dry-hull system of ships (§4) does not apply to world blocks. A wreck placed so that it reaches above sea level
would carry its waterlogged blocks into the air as water sources, so placement must keep the piece's top under the
surface (the tallest, `mast_stump`, needs 12 blocks of water above its seabed row). The lab's render colours every
state containing `water` blue, so waterlogged blocks show as blue in `art/renders/structures/wreck/*.png`.

**Loot:** every vanilla chest carries `{LootTable: "pirates_n_ships:chests/wreck"}` (no `LootTableSeed`, so the loot is
rolled when a player first opens it), written by `_style.loot_chest` as a `block_entity` operation. The loot table
is datagen (`world/wreck/WreckLoot`, called from `WorldModule.gatherData`): always 3-12 doubloons; two to four rolls
of rum, salted fish, rope, nails, lead shot (8-16) and, at weight 1, a cutlass; kraken ink in one chest of 40. The
sea chest in the sloop's hold is empty: `SeaChestBlockEntity` is a plain container without a loot table.

| piece | size (x×y×z) | blocks | contents | mod blocks |
|---|---|---|---|---|
| `sunken_sloop` | 14×10×27 | 1091 | the starter sloop's hull (same half-widths, rocker, hold, forecastle, stern cabin and quarterdeck) heeled to starboard, the fore half 22°, the after half 27° and one block to starboard; each cross-section turned about the keel by sampling the upright hull, closed where blocks only meet at an edge. Frames (stripped spruce ribs) at the break with jagged planking either side, patches of missing planking (frames left standing in them), barnacle crust on the bottom, the mast snapped above the deck and fallen across the after deck into the sand, the lower yard and its chain beside the hull, sand banked against the starboard side and silting the low side of the hold and cabin, spilled ballast; bow north (−z) | `sea_chest` (forward hold), `cargo_barrel`, `cargo_crate` (slid to starboard in the after hold), `yard` |
| `cargo_field` | 15×4×15 | 231 | a round, ragged bed with ballast stones; crates and barrels set into it with sand over their edges or sitting on it half buried, a toppled stack, kegs on their sides, a broken yard in two pieces with its chain, a cannon with its rear in a sand drift (it turns only horizontally, so it stands upright), loose planks and hatch covers, the chest under a fallen plank | `cargo_crate`, `cargo_barrel`, `yard`, `cannon` |
| `mast_stump` | 7×12×7 | 93 | a broken square of deck planking in the bed with mast partners and heaved-up ballast, the mast (spruce log, an iron band of chain, a stripped fished section, a splintered stub on top), the remains of the fighting top, the yard with one arm snapped and hanging, a lantern hanging from the yard on a short chain (the yard's underside cannot hold a hanging lantern: placement would drop it, WK1), torn rigging (chains), a stepped fence shroud, a cleat with a rope coil (chain), the chest at the foot half buried | `yard`, `cleat` |
| `stern` | 9×8×11 | 441 | the stern of a larger ship, half buried: a framed transom facing north (−z) with quarter posts, trim bands at the cabin floor and the poop deck, four cabin windows round a mullion (two broken out) with sills and a hood, the nameplate above the rudder head, the taffrail; the rudder with iron straps; sides with a wale, a trim band, a quarter window and a frame post, falling away in steps to the broken end with its frames showing; the great cabin with a table and chairs, a keg, a lantern, a hanging lantern and the chest, the poop deck half fallen in, sand in the hold and drifting in at the broken end | `nameplate`, `cargo_crate` |

Renders: `art/renders/structures/wreck/*.png`. `WreckPiecesTest` checks the committed pieces: the four files and no
others, sizes within bounds, every row used, existing blocks and states (mod blocks against the datagen block
states), `waterlogged=true` wherever the block has the property, no jigsaws, the wreck loot table (and no seed) in
every chest, the sea chest in the sloop, the generated loot table's items and doubloon count, and a fresh conversion
equal to the committed NBT. The wrecks were built on lab port 8769.
