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

Left-hand display entries (ART17; applies to every hand-made item and block-item model):
- **What the game does.** `ItemTransform.apply(leftHand, poseStack)` (1.21.1, `net.minecraft.client.renderer.block.model`)
  negates the y and z rotation and the x translation of the `thirdperson_lefthand` / `firstperson_lefthand` entry:
  the game mirrors the pose across the body. It cannot mirror the geometry. A missing left entry falls back to the
  right one (`ItemTransforms.Deserializer`). Blockbench's display mode does the same (`DisplayMode.updateDisplayBase`),
  so its left-hand preview is what the game shows.
- **The rule: a left-hand entry is written for the right hand; the game mirrors it.** Same translation and scale; the
  rotation is the right hand's rotation `R`, followed (in model space, before `R`) by at most one half-turn about one
  of the model's own axes. With unmirrored geometry one model axis always ends up reversed against a true mirror
  image, and the half-turn chooses which:
  - `R` unchanged: the model's x axis is reversed. Right for models symmetric along x or that read well that way:
    the chain shot, the hull patch, the paper items (the drawing stays towards the holder, unmirrored), every item
    that copies `item/generated` (vanilla's own fallback), hats and `block/block`-style `[75, 45, 0]` entries.
  - `R` turned 180° about the model's y axis (front and back faces swap): sprite-aligned items on the 45° diagonal
    (swords, tools, guns, flag bundles, rope, the key, the brig door). At y = ±90 this equals the right entry with y
    and z negated, which is exactly vanilla `item/handheld`'s `[0, 90, -25]` for `[0, -90, 25]`: every
    handheld-style left entry here has that form and is correct. For other angles compose the matrices
    (`Rx·Ry·Rz·Ry(180)`, then back to XYZ euler), never just negate: the brig key's `[0, -72, -170]` gives
    `[180, 72, -10]`, the door's `[0, -70, 0]` gives `[0, 110, 0]`, `[0, 45, 0]` gives `[0, 225, 0]`.
  - `R` turned 180° about the model's z axis (top and bottom swap): ART16's book in third person.
- **Never write a plain negation of `R` when its y is not ±90.** The game negates it back and the left hand shows the
  right hand's pose unmirrored (the F8g door, the ART10 key, the ART15 chain shot and the ART14 book until
  ART16/ART17).
- Check both hands in display mode and render them side by side (`renders/<item>_hands.png`, ART17).

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
  `CrewContentClient` registers the biome water colour, and for the item the default water colour through the item
  colour hook, ITC1; the projects still carry `blue_ice`, the generated item model names `water_still`). One model per fill level (`water_barrel_fill0..3`, `water_barrel` = full), each with its
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

Capstan bars (CAP2):
- **Two groups in `capstan.bbmodel`** (31 elements, geometry unchanged): `bars` (origin (8, 12.75, 8), on the drum's
  vertical axis through the block centre: the crossed drumhead `head_x`/`head_z`, the four bars, their iron tips, the
  cap and the boss; 12 elements) and `drum` (origin (8, 0, 8): base plate, pawl ring, pawls, drum, whelps; 19).
  Exports (`Codecs.java_block.compile()` with the other group's cubes set to `export = false`, numbers rounded to
  5 decimals, unused textures dropped, `particle` dark oak planks): `capstan` (drum, the block model, no display),
  `capstan_bars` (stand-alone, drawn by `station/capstan/client/CapstanBarsRenderer`) and `capstan_item` (bars then
  drum, the only one with the `gui` display `[30, -135, 0]`, scale 0.5; datagen points the item model at it).
  `HandMadeModelsTest.capstanSplitsIntoDrumAndBars` checks that `capstan_item` is exactly bars + drum and that the bars
  look the same after a quarter turn (every bar part has a partner turned 90° about (8, 8)), so they may rest at any
  multiple of 90°.
- **Drawn turning:** the bars turn about (8, y, 8) by `Axis.YP` (positive = anticlockwise seen from above); raising
  turns them clockwise, dropping anticlockwise, at `crew_stations.capstan.turn_degrees_per_tick` (6), and when the work
  stops they run on to the next quarter turn (`CapstanSpin`). The crew member walks round at `walk_radius` (1.1 blocks)
  at the same rate; the bar tips reach only 11 px (0.69 blocks) from the axis, so his hands meet the bars' line beyond
  their ends (a playtest question: longer bars or a smaller radius).
- **Rebuild:** load the committed project with `loadModelFile` (content read by `fs`), reload the textures from
  `art/vanilla/` (namespace `minecraft`, folder `block`), keep `Project.uuid` in `window.CAP2` and check it before each
  call; the project was saved with `fs` (textures without `source`, `path` `""`, `relative_path`
  `../vanilla/<name>.png`).
- **Render:** `renders/capstan.png`: the assembled block, three-quarter view from the south-east above, offscreen view
  (perspective, fov 35, camera (40, 34, 40) looking at (0, 6, 0)).

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

Market notice board (NB2):
- **Market notice board** (`market_notice_board`, a wood family, legacy id for dark oak): the village square's 2 wide
  × 2 high board on two posts, four blocks (`part` bottom_left/bottom_right/top_left/top_right as the viewer in front of
  the notices sees them; the bottom left is the anchor). Source: `tools/gen_market_notice_board_models.py` (a part list
  in board space x 0..32, y 0..32, notices north; the viewer's left column is x 16..32). Each part's model is the board
  clipped to its cell (faces on a cut are left out; the roof slabs, turned 22.5 degrees about x, are cut along x; a
  turned notice goes whole to the cell of its centre), so every part is lit at its own block.
- Frame (`market_notice_board_<part>`, drawn in every load): `#planks` feet and roof slabs, `#log` posts (x 1.5..4.5 and
  27.5..30.5, z 6.5..9.5, up to y 30) with `#log_top` ends, `#log` rails above and below the board (grain along x),
  a `#log` ridge beam, a fixed `spruce_planks` board x 4.5..27.5, y 7..27 (front z 7), six `gold_block` nails. The
  roof slabs stop 0.1 and 0.2 px short of the ridge's ends so no end faces share a plane.
- Notices (`market_notice_board_<part>_<load>`, no wood, ambient occlusion off): parchment `sandstone_top` and
  `white_terracotta` papers in layers 0.2 px apart from the board, `black_terracotta` ink 0.08 px proud, `red_concrete`
  pin heads, `nether_wart_block` seals. `empty`: six lone pins and two torn scraps; `some`: three notices, one turned;
  `busy`: eight notices in two layers (two turned) and a wanted poster on top (`red_terracotta` header,
  `brown_terracotta` portrait, two reward lines, a seal).
- Item (`market_notice_board_item`): the frame and the "some" notices at half size in one block; display like the
  small board's.
- Block state (datagen, `MarketNoticeBoardData`): multipart, the part's frame (the wood's child) plus the part's
  notices for its load, turned by facing.
- Blockbench: `art/models/market_notice_board.bbmodel` is the board assembled in board space (groups frame, empty,
  some, busy, item; textures embedded when `tools/extract_vanilla_textures.py` has put them in `art/vanilla/`). It is
  written by the script, not edited by hand. Lint: `python3 tools/gen_market_notice_board_models.py --combined DIR`
  writes the frame and each load as one model for `tools/lint_models.py` across models; no fights.
- Render: `renders/market_notice_board.png`, the three loads side by side from the front (empty, some, busy), from
  `--render-project` loaded in Blockbench.

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
  `firstperson_righthand` `[0, 90, -25]` / `[-1, 5, 1]` / 0.68 for both. The left-hand entries
  (`[0, -90, -40]`, `[0, -90, -43]`, `[0, -90, 25]`, same translation) are the right-hand rotation turned 180° about
  the model's y axis, which at y = ±90 reads as the negation, like vanilla `item/handheld` (ART17 rule). Third-person translation is in arm space: y moves the item forward/back, z up/down along
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
  leg), y/z lower it so the hand grips it about half-way up. First person `[0, -70, 0]` / `[1.5, 2.5, 1]` / 0.25. Left
  hand (ART17 rule, the right-hand rotation turned 180° about the model's y axis): third person `[70, -90, 0]`,
  first person `[0, 110, 0]`, same translations.
- **Brig key** (39 elements; replaced by ART10 below): an iron skeleton key on the 45 degree diagonal, 14 px long (bow bottom-left, bit
  top-right like a tool sprite; the two teeth on the upper-left side, so they point up when it is held). Parts are
  thirteen shapes in world coordinates, each a bar with a direction in 45 degree steps (an octagonal bow ring of
  eight bars, a collar, the shank, two teeth and the web between them; `F8G.bar`, `F8G.keyShapes`). **Darker edge:**
  each shape is three elements: a 1.2 px deep body with `iron_dark` front and back and `steel_dark` sides, and a
  0.15 px plate in front (`steel`) and behind (`steel_dark`), inset 0.3 px. The plates of neighbouring shapes overlap,
  so the dark rim follows only the outline of the whole key, also in the flat GUI view where side faces do not show.
- **Key display entries** (F8g, replaced by ART10): `item/handheld`'s rotations with smaller scales, since a key at sword size (0.85) looks
  huge: third person `[0, -90, 55]` / `[0, 2.5, 0.5]` / 0.55 (bow in the fist, shank forward and slightly up),
  first person `[0, -90, 25]` / `[1.13, 3.2, 1.13]` / 0.55; `ground`, `head`, `fixed` from `item/generated` like the other items; no `gui`
  entry. Particle `palette`.
- Renders: `renders/brig_door_item.png` and `renders/brig_key.png` are strips of three views: GUI (display mode),
  third person from the right side, first person (the display viewport's camera, copied into an offscreen view of
  the same aspect).
- The old sprites (`textures/item/brig_door.png`, the wooden door from C8, and `brig_key.png` from
  `tools/gen_law_textures.py`) are gone; the law script had no other sprite and was deleted, and
  `gen_placeholder_textures.py` no longer writes the door sprite (it has no item sprites left).

Brig key as a real 3D key (ART10, after the playtest note "the brig key just looks like a 2d thing extrapolated
into 3d"; the F8g key was a 1.5 px slab with rim plates):
- **Geometry** (52 elements, `palette.png` only, particle `palette`). The key is built along **x** (bow at low x, bit
  at high x), its axis at y 7.52, z 8, bounding box x -0.6..16.6, y 4.08..11.92, z 6.4..9.6 (re-centred on
  (8, 8, 8)). Building along an axis instead of the sprite diagonal frees the one element rotation for roundness:
  - Every round part is a regular octagon prism along x from four D x D·tan 22.5° bars (two axis-aligned, two
    turned ±45° about x, 0.05 px shorter at each end): finial D 1.5 and nib D 0.9 on the far side of the bow,
    flange D 3.2 and collar D 2.8 where the bow meets the shank, shank D 2.2 (8.5 px from the bow, 9.6 px with the
    collar), a bead ring D 2.6 in front of the bit, the rounded tip D 1.7 and its end D 1.0.
  - Bow: an octagonal ring in the xy plane, outer radius 3.4, eight segments turned about z in 45° steps. Each
    segment has a chamfered square section of two crossed boxes (radial 1.8 x depth 1.1 and radial 1.1 x depth
    1.8), so the ring reads round from the front, the edge and the top; the hole is 3.2 px across. The diagonal
    segments are 0.05 px thinner in z than the axis-aligned ones (no coplanar front faces).
  - Bit on the +y side of the shank's far end: a web 3.7 x 2.1 px, 2 px deep (z ±1.0, inside the shank's z ±1.1),
    and three wards as separate cubes 1.9 px deep (1.0, 0.7 and 0.65 px wide, 1.95 / 1.25 / 1.95 px long) with
    gaps of 0.65 px between them.
  - Colours: bodies `steel_dark` sides, `steel_light` up faces, `iron_dark` down faces; the ring's inner/outer faces
    `steel`; the flange, collar bead and bead ring `steel` with `steel_light` tops, so the bands read as lighter
    rings on the dark iron; bit `steel_dark` faces with `iron_dark` edges and `steel` ward ends. Lighter iron
    (`steel` fronts) vanished against the grey inventory slot at 16 px.
- **Display** (left hands follow the ART17 rule: the right-hand rotation turned 180° about the model's y axis, same
  translation; MC applies the euler as Rx·Ry·Rz, so z turns the model first):
  - `gui` `[-28.2, -21.6, 34.5]` / `[-0.28, -0.75, 0]` / 1.05: the composition of "turn 35° about the key's own
    axis (x), then 40° about z", which tilts the bow and bit towards the viewer without foreshortening the shank;
    bow lower left, bit upper right, wards on the upper-left side. Projected size 14.5 x 14.7 units, inside the
    16-unit slot (checked with the offscreen orthographic GUI view, about 32 px per unit at 1024 px).
  - third person `[0, -90, 120]` / `[0, 2, 0.5]` / 0.55: `item/handheld`'s frame with the model turned +45° about z
    to the sprite diagonal plus 20° more, so the bow sits in the fist and the shank points forward and 7.5° down
    (world direction of the model's x axis read from `mesh.matrixWorld` in display mode); wards hang down.
  - first person `[0, -72, -170]` / `[0, 3.8, 0]` / 0.42: the shank points into the screen and a little to the
    left and down, the bow lower right with its face towards the player. Left hand `[180, 72, -10]` (ART17; the
    F8g/ART10 negation `[0, 72, 170]` showed the bow end on).
  - `ground` `[0, 0, 45]` / `[0, 2, 0]` / 0.45; `fixed` `[0, 180, 45]` / `[0, 0, 0]` / 0.9 (bit upper right in the
    frame); `head` vanilla `[0, 180, 0]` / `[0, 13, 7]` / 1.
- **Re-export:** the elements came from a Python part list (octagon helper, ring loop, bit cubes, palette patch per
  face, re-centring), loaded cube by cube into the project (`new Cube({from, to, origin, rotation, faces})` with
  `autouv: 0` and the palette UVs `[u+0.5, v+0.5, u+3.5, v+3.5]`). A rotated element needs its own copy of the
  rotation origin in such a script: a shared origin object was shifted twice by the re-centring. To change the model,
  edit `art/models/brig_key.bbmodel` in Blockbench, then `Codecs.java_block.compile({raw: true})`, keep `credit`,
  `gui_light: front`, `textures` `0` and `particle` = `pirates_n_ships:item/palette`, round numbers to 4 decimals and
  write `models/item/brig_key.json` (tab-indented); save the project with the palette embedded and `path` `""`. Run
  `python3 tools/lint_models.py` on the file (0 visible fights). Script not committed; rebuild it from these notes.
- Renders: `renders/brig_key.png` (GUI slot view in display mode, the same at 16 px on the slot grey, third person
  from the front right, first person through the display camera) and `renders/brig_key_turntable.png` (eight views
  in 45° steps, camera 12 px above the key).

Chain shot and grapeshot as 3D items (ART15, after the playtest note "chainshot and grapeshot need a 3d redesign";
CAN3 drew flat sprites with `tools/gen_shot_sprites.py`, both gone):
- **Chain shot** (`chain_shot.bbmodel` -> `models/item/chain_shot.json`, 36 elements, `palette.png` only, particle
  `palette`). Built along **x** like the ART10 key, centred on (8, 8, 8), x -0.3..16.3: two iron balls D 4.6 at x 2 and
  14 and a chain of three links between them. Each ball is three octagon slabs, one about each axis (four bars
  D x D·tan 22.5°, 0.56 D thick, the diagonal pair turned ±45° about the slab's axis and 0.05 px thinner along it):
  12 elements, round from every side, which the tumble in flight needs (the cannonball's belt/shoulder/pole recipe
  reads as a dumbbell plate from the side). Links are open rectangles of four bars (3.6 x 2.4 px outside, bars
  0.7 px; the end bars 0.05 px thinner in depth), alternately in the xy plane (facing the slot) and the xz plane
  (edge on), 2.15 px apart so each link's end bar passes through the next one's hole without touching it; the outer
  links end 0.25 px inside the balls. Colours: balls `iron_dark` with `steel_dark` up and south faces (the lit
  side), links `steel_dark` sides, `steel` fronts and tops (`steel_light` on the facing links' tops), `iron_dark`
  bottoms.
- **Grapeshot** (`grapeshot.bbmodel` -> `models/item/grapeshot.json`, 58 elements, `palette` `#0` and `palette_4`
  `#3`, particle `palette`). Upright about the block's centre line: a wooden sabot (octagon D 9, y 3.5..5, `wood`,
  `wood_dark` below), an iron rod (0.8 px, `steel_dark`) up the middle, two tiers of four 3 px balls (8 in all): the
  lower tier on the axes at radius 2.2 as three crossed boxes (D, 0.6 D, 0.6 D) each, the upper tier on the
  diagonals nestled 2.48 px higher, each a rounder six-element ball (an octagon belt about y 0.47 D high plus two
  crossed caps); balls `steel_dark` sides, `steel` tops, `iron_dark` bottoms (the darker iron of the chain shot made
  a black lump at 16 px). The lower tier stands in a canvas wrap (an octagon of eight 0.35 px boards from the sabot
  to just above the ball centres, `cloth_dark` with a `cloth_light` rim) tied with a cord band (`cord`, 0.2 px
  proud), and a cord knot (`twine` top) closes the rod. Two loose octagon cord rings round the bare tiers read as
  a cage, and a full canvas cup hid the balls.
- **Grapeshot pellet** (`grapeshot_pellet.bbmodel` -> `models/block/grapeshot_pellet.json`, 3 elements: one 3 px ball
  of crossed boxes centred on (8, 8, 8), the grapeshot's colours). Not an item model (the Almanac generator refuses
  an item model without an item), so it sits under `block/` like the cannon's stand-alone parts and is listed in
  `HandMadeModelsTest.BLOCK_MODELS`; `CannonballRenderer` draws it as a registered stand-alone model.
- **Display** (left hands follow the ART17 rule; the chain shot is symmetric, so both its left slots repeat the
  right hand, and the grapeshot's first-person left `[0, 225, 0]` is the right-hand rotation turned 180° about y):
  - chain shot: `gui` `[-17.2, -13.9, 37.9]` / `[0, 0, 0]` / 1 (22° about its own axis, then 38° about z: chain on the
    slot diagonal, the flat links still open; about 14.6 x 13.1 units in the slot); third person `[0, 0, 0]` /
    `[0, 3, 1]` / 0.55 (held out in the fist, a ball either side); first person `[0, -35, 25]` / `[-1, 3.5, 0]` / 0.5
    (both balls in view low right); `ground` `[0, 0, 0]` / `[0, 2, 0]` / 0.45; `fixed` `[0, 180, 45]` / 0.75; `head`
    vanilla.
  - grapeshot: `gui` `[30, 225, 0]` / `[0, 1.5, 0]` / 1.35 (the block view, about 12.1 x 13.4 units); third person
    `[75, 45, 0]` / `[0, 2.5, 0]` / 0.5 (both hands); first person `[0, 45, 0]` (left `[0, 225, 0]`) / `[0, 4.5, 0]` /
    0.5; `ground` `[0, 0, 0]` / `[0, 3, 0]` / 0.4; `fixed` `[0, 0, 0]` / 0.75; `head` vanilla.
  - pellet: `item/generated`-like entries (never shown as an item).
- **In flight** (`combat/cannon/client/CannonballRenderer`, which reads the kind from the synced item): chain shot is its
  item model in `ItemDisplayContext.NONE` (no display transform, centred) at scale 1, its x axis laid along the
  flight (the arrow's `YP(yRot - 90)`, `ZP(xRot)`) and turned about z, across the flight, by
  `ShotTumble.angle(age, partialTick)` (36° a tick); a grapeshot pellet is `block/grapeshot_pellet` at 0.8 (2.4 px,
  0.15 blocks) facing the camera; the cannonball and the swivel's shot stay vanilla's thrown item at 1.2.
- Built like ART1b/ART4: a Python part list (octagon slab ball, link, crossed-box and six-element balls, octagon
  disc and band helpers; patch names from `tools/gen_item_palette.py`) wrote the model JSON, which
  `Codecs.java_block.parse(json, path)` opened in a new `java_block` tab; the display slots were set in
  `Project.display_settings` and checked with an orthographic camera over the GUI slot (in display mode the slot is
  ±3.2 scene units: the display scene is scaled 0.4); the export is `Codecs.java_block.compile({raw: true})` plus
  `credit`, `gui_light: front`, the particle and the full display entries (numbers rounded to 4 decimals,
  tab-indented), the project `Codecs.project.compile({raw: true})` with the sheets embedded, `path` `""` and
  `relative_path` to the sheet under `common/src/main/resources`. The compiled elements match the part list one to
  one. Script and helpers are not committed; rebuild them from these notes. `tools/lint_models.py`: 0 visible fights.
- Renders: `renders/chain_shot.png` and `renders/grapeshot.png` (GUI slot on the slot grey, the same rendered at 16 px,
  third person in the right hand) and `renders/chain_shot_turntable.png`, `grapeshot_turntable.png` (eight views in
  45° steps, camera 12 px above the centre), each drawn with a `THREE.WebGLRenderer` in `risky_eval` (scene objects
  other than the model, its display rig, the lights and the player reference hidden) and written with `fs`.

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
- **Rope** (28 elements, `palette_2` rope colours and `palette_4` twine whipping) and **chart** (14 elements; replaced in CHART3, below,
  `palette`, `palette_3` paper and biscuit, `palette_4` cord; particle `palette_3`): no new colours.
- **Flag bundles** (`navy_flag` 28 elements, `merchant_flag` 26, `jolly_roger_flag` 32 since ART18; one project each, one shared
  part list): the cloth rolled on a halyard toggle, built upright along y around x = z = 8 and turned `z -45` about
  `[8, 8, 8]` like the cloth bolt, so the vanilla handheld slots hold it like a tool (lower knob in the fist). Parts:
  the toggle with two crossed-box knobs and a rope eye on top (`wood`, `wood_dark` of `palette`, `rope` of
  `palette_2`), the roll and the outer fold as two crossed boxes each (the second box 0.05 px shorter at its ends),
  two `rope_dark` lashings, the fold's loose end in two steps to the lower right, and the design on the fold: navy a
  white band all round plus a vertical arm on the front and back, merchant a red band, Jolly Roger a skull (eye holes
  0.08 px proud of it) and a bone, 0.15 px proud of the front, and since ART18 the same on the back (see "Flag bundle
  devices on the back face (ART18)"). The colours follow the flown cloth
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

Wood families: the model convention (WOOD1, `docs/woods.md`):
- A wooden block that comes in every wood is a **family** (`ship/decor/wood/WoodFamily`): its hand-made parent models
  (Blockbench, or a part list in `tools/`) never name a wood's texture for "the wood"; they use three texture
  variables, and datagen writes one child per wood (`block/<wood>_<part>`, e.g. `block/spruce_railing_edge`,
  `block/biomesoplenty_redwood_railing_edge`) that sets them:
  - `#planks`: the wood's planks (`minecraft:block/spruce_planks`);
  - `#log`: the side of the wood's stripped log (`stripped_spruce_log`; bamboo `stripped_bamboo_block`, crimson and
    warped `stripped_<wood>_stem`; a mod's wood its own, see `docs/wood-compat.md`);
  - `#log_top`: the end of that stripped log (`stripped_spruce_log_top`, ...), for cut log ends seen from above.
  The child also sets `particle`, to the planks by default or to the log for a log-only model
  (`WoodFamily.Particle.LOG`: yards, poles).
- **Which texture is "the wood"** in a model with accent woods: the main planks are `#planks` and the main stripped log
  is `#log`; accents (a dark-oak trim on an oak body, iron, brass, cloth) stay fixed textures and do not follow the
  wood. A model that wants a second wood-dependent texture needs a new variable in `WoodFamily` first.
- **Rope is not wood:** `minecraft:block/stripped_birch_log` used as rope (yard bands, the winch rope, halyards, the
  sea chest's handles, the crow's nest's `rope` key) stays a fixed texture, never `#log`; a family model keeps its
  own key for it (`rope`). The stern window's `dark_oak_trapdoor` shutters likewise stay fixed: no `#trapdoor`
  variable is added (decided in WOOD1; a shutter in the wood's own trapdoor needs one, and a mod's trapdoor texture is
  not part of the verified table).
- A renderer part of a family (a part a block entity renderer draws, like the helm's wheel) is a parent model with the
  same variables; `ship/decor/wood/client/WoodModels.register(family, parent)` loads its child for every wood and
  `WoodModels.get(block, parent)` fetches the member's.
- Legacy ids: a family may keep one wood's member under its pre-WOOD1 id (`helm` for dark oak); the models are named by
  wood all the same (`block/dark_oak_helm_wheel`), only the registry id is legacy.

Storage blocks in every wood (WOOD2, `docs/woods.md`): the six Blockbench-only projects of the containers batch, S1-art
and F7f keep their geometry; their texture keys were renamed by script (the exported JSON's `textures` keys and face
`#refs`, the `.bbmodel` texture `id`s; the texture files and UVs are unchanged), the parent's `particle` is `#planks`,
and the parents keep their old textures as defaults. Datagen writes `block/<wood>_<name>` per wood (the legacy member's
too: `block/oak_cargo_crate`, `block/spruce_water_barrel_fill2`). Which texture follows the wood:

| Model | Legacy id (wood) | `#planks` | `#log` / `#log_top` | Stays fixed |
|---|---|---|---|---|
| `cargo_crate` | `cargo_crate` (oak) | the oak slats and lid (was key `1`) | the stripped spruce posts (`3`) and their ends (`4`) | spruce rails `0`, black slots `2`, anvil bands `5`, dark oak inside `6` |
| `cargo_barrel` | `cargo_barrel` (spruce) | the spruce staves (`0`) | none | oak heads `1`, anvil hoops `2`, dark oak bung `3`/`4` |
| `pantry` | `pantry` (spruce) | the spruce planks (`2`) | the stripped spruce frame (`3`) | dark oak carcass `0`/`1`, oak panels `4`, anvil `5`, black `6` |
| `water_barrel`, `_fill0..3` | `water_barrel` (spruce) | the spruce staves (`0`) | none | anvil hoops `1`, dark oak floor `2`, the oak dipper `4`/`5`/`6`, the water `7` (`water_still`, `tintindex 0`, kept) |
| `harbor_desk` | `harbor_desk` (dark oak) | the dark oak body (`0`) | none | stripped spruce drawers `9` (a deliberate accent), birch, gold, wool, iron, paper |
| `sea_chest` | `sea_chest` (dark oak) | the dark oak planks (`0`) | the stripped dark oak lid steps and feet (`1`) | iron `2`, anvil `3`, the rope handles (`stripped_birch_log`, `4`), black `5` |

- The legacy member is a child like every other, so the oak crate's posts are now stripped oak (spruce before) and the
  barrels, pantry and water barrel are spruce as before; the desk and the sea chest are dark oak as before.
- The water barrel's item model per wood is a child of `block/water_barrel` with the wood's textures and `7` set to
  `water_still` again (ITC1's guard against the old `blue_ice` stand-in); `CrewContentClient` tints every wood's
  barrel, block and item.
- The floating sea chest draws its own wood's block model (`SeaChestEntity` carries the wood, synced and saved).
- Blockbench was not opened for the rename (only the `id` field changed; the projects parse as JSON, and the face
  texture indices are untouched).

Wood families of the machines (WOOD4):
- **The rename.** The helm, capstan, sail winch, bilge pump and cannon models name their main wood with the family
  variables; nothing else changed (no element, UV or face moved). The committed JSON was rewritten by key (the texture
  entry and every `"texture": "#<n>"` face reference), and each project's texture `id` was set to the same name (the
  `.bbmodel` faces point at textures by index, so only the `id` changed; `railing.bbmodel` already used named ids).
  Every other key keeps its number. The parents keep the original wood as the default value of each variable, so a
  parent renders as before and `HandMadeModelsTest` (which compares elements with their texture references, and the
  cannon's and pump's parts by the texture they resolve to) holds.
- **Helm** (`helm`, `helm_wheel`, `helm_item`; legacy `helm` = dark oak): `dark_oak_planks` -> `#planks` (pedestal),
  `stripped_dark_oak_log` -> `#log` (pedestal and wheel); the spruce rim and pedestal planks, the stripped spruce log,
  the gold and the iron stay. The wheel is a renderer part: `HelmWheelRenderer` draws
  `block/<wood>_helm_wheel` (`WoodModels`).
- **Capstan** (`capstan`, `capstan_bars`, `capstan_item`; legacy `capstan` = dark oak): `dark_oak_planks` -> `#planks`,
  `stripped_dark_oak_log` -> `#log`, `stripped_dark_oak_log_top` -> `#log_top` (base plate and drumhead in planks, drum and cap in log); the spruce
  whelps (side and end) and the iron stay. `CapstanBarsRenderer` draws `block/<wood>_capstan_bars`.
- **Sail winch** (`sail_winch`; legacy `sail_winch` = spruce): `spruce_planks` -> `#planks`, `stripped_spruce_log` ->
  `#log`, `stripped_spruce_log_top` -> `#log_top`; the rope (`stripped_birch_log`) got the key `rope` and stays fixed,
  the dark oak caps and the iron stay.
- **Bilge pump** (`bilge_pump`, `bilge_pump_item`; legacy `bilge_pump` = dark oak): `dark_oak_planks` -> `#planks`,
  `stripped_spruce_log` -> `#log`, `stripped_spruce_log_top` -> `#log_top` (both follow the wood: the legacy dark oak
  pump's casing is now stripped dark oak, where it was spruce), particle `#log`. `bilge_pump_rod` (iron) and
  `bilge_pump_handle` (the oak accent) carry no wood texture, so they stay one model each and get no child per wood.
- **Cannon** (`cannon_carriage`, `cannon_carriage_rammer`, `cannon_quoin`, the item `cannon`; legacy `cannon` = dark
  oak): the cheeks `dark_oak_planks` -> `#planks`, the axletrees `stripped_dark_oak_log` -> `#log` and their ends
  `stripped_dark_oak_log_top` -> `#log_top`; the spruce bed, trucks and rammer and the iron stay; the barrels have no
  wood and stay one model each. `CannonBarrelRenderer` draws `block/<wood>_cannon_quoin`. `cannon_rear` stays one
  generated particle-only model for every member (nothing of the rear half is drawn), not a family part.
- **The cannon's part list** (`tools/gen_cannon_models.py`) writes the variable names itself: its keys `cheek`,
  `axle` and `axle_end` become `planks`, `log` and `log_top` (`WOOD_VARS`), the rest are numbered as before. The
  CAN2 pipeline (generate, `lint_models.py --fix` with the mirrors, `--compose`) reproduces the seven committed files
  exactly (checked for WOOD4). The projects were not reopened in Blockbench (another agent may hold the one instance):
  only their texture ids changed, which the JSON round trip of the edit checked.

Wood families of the fittings (WOOD3): the texture keys of the parents, renamed in the committed JSON and in the
projects' texture `id` fields (by script, so the `.bbmodel` files differ only in those ids; the projects' faces point at
textures by index, which did not change). Parents keep their old textures as defaults, so they stay valid on their own;
datagen's children per wood set the variables (`block/<wood>_<part>`, the legacy member's too, e.g.
`block/spruce_yard`).
- **Name board** (`name_board*`): already `#planks` (panel) and `#log` (frame) since NAME2; the oak children of NAME2
  became one child per wood. A row joins boards of every wood; each board keeps its own wood, so a mixed row shows its
  boards' woods side by side and the end caps are those of the end boards.
- **Stern window** (`stern_window`, `_shutters`; `tools/gen_decor_models.py`): `dark_oak_planks` -> `#planks` (mullion,
  transoms, casing, hood, corbels, stool), `stripped_dark_oak_log` -> `#log` (stiles, head, bottom rail, hood top);
  the `dark_oak_trapdoor` shutters, glass, brass and iron stay.
- **Chart table** (`chart_table`; the same script): `dark_oak_planks` -> `#planks` (top, aprons), `stripped_dark_oak_log`
  -> `#log` (legs, stretchers, fiddle rails); the `stripped_spruce_log` drawer front stays an accent.
- **Sea cot** (`sea_cot_head`, `_foot`, `_item`, and since ART20 each half's `_left`, `_right`, `_left_joined`,
  `_right_joined`; the same script): `dark_oak_planks` -> `#planks` (boards, knobs),
  `stripped_dark_oak_log` -> `#log` (posts, panels, rails); the wool stays. The script names `planks`, `log` and
  `log_top` only in models flagged `wood` (these three blocks); every other texture keeps a numeric key in first-use
  order, and the lantern, bell and coil models are byte-identical.
- **Notice board** (`notice_board`): `dark_oak_planks` -> `#planks` (feet, rails, roof), the unstripped `dark_oak_log`
  posts and ridge beam -> `#log` and their end `dark_oak_log_top` -> `#log_top` (there is no bark variable, so the posts
  are the wood's stripped log; the parent's defaults and project textures are now `stripped_dark_oak_log(_top)`); the
  `spruce_planks` board behind the notices, the notices, pins, seal and nails stay. Particle: the wood's planks.
- **Flagpole** (`flagpole`, `_bottom`, `_middle`, `_top`): `stripped_spruce_log` -> `#log` (the pole); the dark oak
  truck, gold finial, anvil cleat and birch halyard stay. Particle: the log.
- **Flag pedestal** (`flag_pedestal`, `_pole`): `stripped_spruce_log` -> `#log`, `stripped_spruce_log_top` ->
  `#log_top`; the anvil hoop and socket and the birch halyard stay. The pole stub in `_pole` is `#log` too, so it is in
  the pedestal's own wood. Particle: the log.
- **Crow's nest** (`crows_nest`, `_pole`): `planks` stays the key (staves, floor), `rim` -> `log` (rim, struts); `dark`
  (the dark oak underside), `iron` and `rope` (birch) stay fixed. The pole stub (`#log`) follows the nest's wood.
- **Yard** (`yard`): `stripped_spruce_log` -> `#log`, its end -> `#log_top`; the anvil fittings and the birch rope bands
  stay. Particle: the log.
- **Cleat** (`cleat`): the pad's `stripped_dark_oak_log` -> `#log` and its end -> `#log_top`; the anvil horns stay.
  Particle: the log (was the anvil).
- `HandMadeModelsTest.woodFamilyParentsUseTheWoodVariables` checks every parent's keys.

Railings (RAIL1; the look superseded by "Railing rework (RAIL6)" below):
- **Part list in `tools/gen_railing_models.py`** (committed). It writes the four parent models of the wood family to
  `models/block/` and their project `models/railing.bbmodel` itself (groups `railing_edge` 6 elements, `railing_post`
  2, `railing_port` 5, `railing_item` 10; all at the same place, the first visible; textures `oak_planks` with id
  `planks` and `stripped_oak_log` with id `log`, from `../vanilla/`). Every model uses the texture variables `#planks`
  and `#log`; datagen (`ship/decor/wood/WoodFamily`) writes one child per wood (`block/oak_railing_edge`, ...) that sets
  them to that wood's planks and stripped log. Lint clean (no fights, no warnings).
- **Edge** (north edge, turned by the multipart state's y rotation with uvlock): top rail y 14..16 and lower rail
  y 7..9, z 0.05..2 (0.05 px off the side so a post's outer face never fights them; the end faces are left out, a
  neighbour's rail or a post always covers them), four 1 px balusters at x centres 2, 6, 10, 14 from the deck to the
  top rail (z 0.5..1.5), so the spacing runs on across blocks and the end ones hide in the posts. **Post**
  (north-west corner): a 3 x 3 px stripped-log post y 0..16 and a 1 px plank cap to y 17. **Port**: the two posts at
  the ends of the north edge and a sill x 3..13, y 0..4, z 0.05..2. **Item**: an edge with both posts, centred.
- Checked in Blockbench (RAIL1): the project loads with both textures, and `Codecs.java_block.compile()` of each group
  (the other groups' cubes not exported) matches the part list's JSON element for element (from, to, UVs, texture keys).
- Render `renders/railing.png`: a scratch free-format scene built from the model JSON (a three-block edge run with its
  end posts, a one-block gap, the port with the cannon model behind it, a corner of a north and an east edge with its
  end post, and the swivel gun shifted 6 px onto the rail); `renders/railing_item.png`: the item model.

Name boards (NAME2):
- **Part list in `tools/gen_name_board_models.py`** (committed). It writes the four parent models to `models/block/`
  and their project `models/name_board.bbmodel` (groups `name_board` 3 elements, `name_board_cap_left` 1,
  `name_board_cap_right` 1, `name_board_item` 5; the first three visible, all at the same place; textures `oak_planks`
  with id `planks` and `stripped_oak_log` with id `log`, from `../vanilla/`). Every model uses the texture variables
  `#planks` and `#log` (RAIL1); datagen (`ShipDecorModule#nameBoard`, `WoodFamily.childJson`) writes the oak children
  `block/oak_name_board*` and `item/nameplate` (the block keeps the G8 nameplate's registry id). Lint clean (no fights,
  no warnings).
- **Board** (north-facing, against the south side of its block, 2 px thick, 14 px tall): plank panel x 0..16, y 2..14,
  z 14.5..16 (front and back only: its ends and edges are always covered); stripped-log frame strips x 0..16 at
  y 14..15 and 1..2, z 14..16, the log turned 90 degrees on the long faces so the grain runs along the strip. **Caps**:
  the frame's ends, x 15..16 (viewer's left = east) and x 0..1 (right = west), y 2..14, z 14..15.95 (0.05 px short of
  the back so they never fight the panel's back face); the multipart block state draws `name_board` always and a cap
  only where the row ends (`left=false`, `right=false`), so a joined row has one frame round it. **Item**: a small
  board standing in the block's middle (panel x 2..14, y 4..12, planks on both faces; frame x 1..15, y 3..13, z 7..9);
  display `gui` `[15, -30, 0]` / 0.9, `fixed` `[0, 180, 0]`, `ground` 0.5 raised 3 px; the hands keep `block/block`'s
  entries (the board is symmetric along x: ART17's "R unchanged" case).
- **The name** is not part of the model: `NameplateRenderer` draws it from the row's master on the panel front
  (z 14.45, behind the frame's front), centred on the row, dark brown `#2A1A0E`; the scale rule is `NameplateText`.
- Checked in Blockbench (NAME2): the project loads with both textures, and `Codecs.java_block.compile()` of each group
  (the other groups' cubes not exported) matches the part list's JSON element for element. Renders
  `renders/name_board.png` (one board, caps on both ends), `renders/name_board_row.png` (a row of three: the middle
  and right boards are clones of the board model, the right cap moved to the row's end; the name is a canvas overlay at
  the size the scale rule gives "Black Gull" on three boards, not the game's font), `renders/name_board_item.png`
  (the item at about its gui angle), drawn with a `THREE.WebGLRenderer` in `risky_eval` (offscreen views were all
  taken) and written with `fs`.

Railing gate (RAIL3):
- **Part list in `tools/gen_railing_models.py`** (the RAIL1 script, extended; written without Blockbench, which another
  agent held, so not yet checked there). Five more groups in `models/railing.bbmodel` and parent models in
  `models/block/`: `railing_gate_left` 8 elements, `railing_gate_right` 8, `railing_gate_left_open` 8,
  `railing_gate_right_open` 8, `railing_gate_item` 13; a third texture `anvil` with id `iron` (variable `#iron`, kept by
  the wood family's children, which override only `#planks` and `#log`). Lint clean (no fights, no warnings).
- **The gate is three parts in one multipart state:** the port's own model (`railing_port`: posts and sill) turned by
  `facing`, and one leaf model chosen by `open` and `hinge`. **Leaf** (north frame, between the posts x 3..13): the
  edge's top rail y 14..16 and lower rail y 7..9 (z 0.05..2, end faces against the posts left out), two balusters at x
  centres 6 and 10 from the sill (y 4) to the top rail, two iron hinge straps x 2..6 (1 px onto the hinge post),
  y 13.75..16.25 and 6.75..9.25, z -0.25..2.25 round the rails, a latch bar x 11..14.5, y 11..12, z 2..2.6 on the
  inboard face (its tongue in the latch post) with a handle. `left` = hinged on the west post looking outboard
  (north); `right` mirrored. **Open:** the same leaf turned 90 degrees outboard about the hinge post's inner outer
  corner (x 3 or 13, z 0): x 3.05..5 (or 11..12.95), z -10..0, out of the block like a fence gate's leaf (all faces
  drawn; the balusters' tops stay hidden under the rail). **Item:** posts, sill and the closed left leaf, centred like
  the railing's item.
- Checked in Blockbench and rendered by RAIL4 (see "Bulwark (RAIL4), and the gate check").

Bulwark (RAIL4), and the gate check:
- **Part list in `tools/gen_railing_models.py`** (the RAIL1 script, extended; written by the script, checked in
  Blockbench). Seven more groups in `models/railing.bbmodel` and parent models in `models/block/`: `bulwark_edge` 2
  elements, `bulwark_stanchion` 2, `bulwark_port` 5, `bulwark_port_lid` 5, `bulwark_port_lid_open` 5, `bulwark_item` 6,
  `bulwark_port_item` 10; textures `#planks`, `#log` and (the lid's straps) `#iron`, the wood family's children
  overriding the first two. Lint clean (no fights, no warnings) for the seven.
- **Edge** (north frame): a plank wall x 0..16, y 0..14, z 0.05..2 (north and south faces only: a neighbour's wall or a
  stanchion hides its ends, the cap its top, the deck its foot) and a stripped-log cap rail y 14..16, z 0.05..3.95
  (2 px proud of the wall's inboard face; 3.95, not 4, so its inboard face hides in a stanchion instead of fighting the
  stanchion's). **Stanchion** (north-west corner, 4 x 4 px): a log post y 0..17 and a plank head to y 18. The multipart
  state is the railing's: an edge model per edge, a stanchion per post, uvlock.
- **Gun port** (north frame): the jambs x 0..3 and 13..16 (wall and cap rail ends, their end faces drawn so a lone port
  is closed; beside a wall they hide inside it) and the sill x 3..13, y 0..4. The notch runs from the sill up through
  the cap: a carriage cannon's barrel crosses the wall 12 px (−5°) to 22 px (20°) above the deck, so a lintel would cut
  through it. **Lid** (`bulwark_port_lid`): a plank panel x 3..13, y 4..16, z 0.05..1.05 with two 1 px iron straps
  (x 4.5 and 10.5, from y 6 to 16.45, 0.5 px proud, their inner face left out) and their hinge knuckles at the top.
  **Open** (`_lid_open`): the same parts turned 90 degrees about the hinge (x axis at y 16, z 0), the bottom swung up
  and out: the panel lies flat at y 14.95..15.95, z -12..0, straps on top, all faces drawn. **Items:** the wall with a
  stanchion at either end, and the gun port with its shut lid, both centred (z 6..10).
- Checked in Blockbench (RAIL4, a new tab of `railing.bbmodel`): `Codecs.java_block.compile()` of every group (the
  other groups' cubes not exported) matches the part list's JSON element for element (from, to, UVs, texture keys), the
  sixteen groups of the file, RAIL3's gate included.
- **RAIL3's gate checked:** both hinges closed and open, and the item, rendered from inboard and from outboard-east:
  the straps sit on the hinge post's end of the rails, the latch with its handle on the inboard face at the latch post,
  the open leaf turns 90 degrees outboard about the hinge post, the item shows posts, sill and the closed left leaf.
  Nothing needed fixing.
- Renders (clones of the group meshes placed in the tab's scene, offscreen view, composed with a canvas and written
  with `fs`; `art/vanilla` textures): `renders/railing_gate.png` (top: the gate left and right hinged, closed and open,
  and the item, from inboard; bottom: the same from outboard), `renders/bulwark.png` (top, from inboard: a run with
  its west stanchion, a shut and an open gun port, a corner with two stanchions, the two items; bottom, from outboard:
  the shut lid's straps and the open lid lying flat).

Railing latch (RL5):
- **Part list in `tools/gen_railing_models.py`** (the RAIL1 script, extended; written by the script, not yet looked at in
  Blockbench or rendered). One more group in `models/railing.bbmodel` and the model `models/block/railing_latch`
  (6 elements): an iron eye bolt standing on the cap of the north edge, for the railing and the bulwark alike (both
  caps end at y 16). It is the model of its own block (`ship/rigging/RailingLatchBlock`, id `railing_latch`, no item)
  in the cell over the rail, so it is made in that block's frame, the cap's top at y 0: a washer x 7..9,
  y 0..0.4, z 0.3..2 (inside the railing's 2 px rail as on the bulwark's cap, so it never overhangs), a shank, and a square ring standing across the rail, x 7.6..8.4, outer y 0.55..2.95,
  z 0.3..2.7, 0.6 px bars. The ring's centre (y 1.75, z 1.5) is the net's ring point (`RailingLatchBlock` `RING_INSET`
  1.5 px, `RING_HEIGHT` 1.75 px over the cap).
- **Iron only:** the chainplate's palette (`#metal` = `pirates_n_ships:item/palette_2`, a fourth texture `palette_2`
  with id `metal` in the project, from the mod's textures) with the chainplate's UV patches (plate, bolt, ring), so the
  model has no `#planks` or `#log` and every wood shares it without a child model. Datagen writes only the latch's
  block state, turned by its `facing` without uvlock (fixed palette UVs). Lint clean (no fights).
- Renders `renders/railing_latch.png` and `renders/railing_latch_bulwark.png` (RL5 follow-up): a three-block oak run
  (railing, then bulwark) with the latch on the middle block's north edge and the first block of a net leaving its
  ring as `RatlineNetRenderer` draws a lone strand (two side ropes from the ring, 52 degrees inboard); panels from the
  deck, from outside, close up, and a side view along the rail clipped to the latch's block (the cap's profile, the
  washer on it, the ring and the ropes starting in its hole). Drawn by a scratch three.js scene from the model JSON in
  Blockbench (`risky_eval`, no project opened), vanilla textures from the 1.21.1 client jar. Checked: the washer
  stands on the cap (y 16) without clipping or floating, inside the rail's depth; the ring stands in the plane of the
  pull (along the strand, the way an eye bolt is loaded) and both ropes start in its hole.
- **Why its own block** and not a `latch` state of the railing (the RL5 brief): with WOOD1's 108 woods a five-valued
  property on every railing and bulwark would add about 440,000 block states; the bolt lies entirely in the cell over
  the cap anyway, and the latch block also holds the net's link, so a railing never carries a block entity.

Railing rework (RAIL6):
- **Why:** the human (2026-10-10): the railing "obviously just looks like a fence ... very bulky ... the few openings
  also seem to me so small". Target: an open, slender ship's rail. The part list `tools/gen_railing_models.py` was
  rewritten (the RAIL1, RAIL3, RAIL4 geometry above is superseded where it says otherwise; RL5's latch is unchanged);
  `railing.bbmodel` now holds 20 groups. Lint clean (no fights; two harmless back-to-back pairs in the gate item).
- **Cap rail** (railing, gate leaf, bulwark, the same part): stripped log, y 14..16 on the balusters' line z 1: a body
  y 14..15.5, z -1..3 (4 px, overhanging the balusters 1 px each side and the block's outer face by 1 px) and a band
  y 15.5..16, z -0.45..2.45 on it (the step that makes it read rounded; 2.9 px so its faces lie inside a post's 3 px
  and never fight them). Long faces `#log` turned 90 degrees (`"rotation": 90`, the UV rectangle transposed), so the
  grain runs along the rail; end faces `#log_top`, drawn (the overhang past a post shows them, back-to-back ends of
  neighbours are culled). Top stays y 16: RL5's latch washer (z 0.3..2) stands on the band, RAIL5's leaners rest on it.
- **Toe rail:** planks y 0..2, z 0..2, full width; no middle rail.
- **Balusters** (`railing_baluster_west` x 4, `railing_baluster_east` x 12; 8 px apart across blocks, 6 px openings):
  a 2 px log shaft y 2..14, z 0..2 and a turned bulge y 6..9 (a 3 px belly y 6.5..8.5 between 2.5 px collars). The
  block state draws each only while no post stands at its end of the edge (`when: {<edge>: true, <post>: false}`: the
  west one with the corner keyed by the edge, the east one with the corner keyed clockwise of it), so a post never
  presses against a baluster (an end opening is 8.5 px).
- **Post** (`railing_post`, also the bulwark's and the rope railing's; `bulwark_stanchion` is gone): 3 px log square
  centred on the corner of the two rail lines (x and z -0.5..2.5, so it stands half a pixel past the block's faces), y
  0..16.5 (half a pixel over the cap), a 2 px finial y 16.5..18.5 and a 1 px tip to 19.5; tops `#log_top`. The outline
  keeps its 3 x 3 px box to y 17 and the collision its height: only the look changed.
- **Port:** the two posts and a plank sill x 2.5..13.5, y 0..4, z 0..2. **Gate leaf** (u 0..11 from the hinge post's
  inner face at x 2.5): the cap rail, a plank bottom rail y 4..6 on the sill, one turned baluster at x 8, iron hinge
  bands 1 px tall round the cap's body (y 14.25..15.25) and the bottom rail (y 4.5..5.5), reaching 1 px onto the post, a
  latch bar on the cap's inboard face (y 14.5..15.5) with a grip, its tongue in the latch post. Open: turned 90 degrees
  outboard about the cap's outer corner at the hinge post (x 2.5 or 13.5, z -1), every face drawn.
- **Bulwark:** the plank wall x 0..16, y 0..14, z 0.05..2 under the cap rail; the gun port's jambs carry cap ends; the
  lid is unchanged. The outline's cap is 3 px deep now (`BulwarkRules.CAP_DEPTH`), the overhang is drawn only.
- **Rope railing** (`rope_railing_edge`, `rope_railing_item`; block `<wood>_rope_railing`): a 2 px log stanchion at the
  edge's x-0 end (x 0..2, z 0..2, y 0..16.25: inside the post where a run ends there, so no condition is needed) and two
  rope lines on the line z 0.25..1.75, the top y 14.5..16 (its top where the cap's is, so RL5's latch stands on it) and
  the middle y 7..8.5. Rope texture: `#rope` = `pirates_n_ships:block/rope_line`, made for these lines by
  `tools/gen_rope_line_texture.py` (committed with the PNG): every row the same 6-texel strand (flank, crown, flank,
  shade, groove, shade in the rigging rope's hues) shifted two texels per row, so the 1.5-texel strip a face samples (16
  along the line, 1.5 across; the top and bottom faces the same, the ends hidden in posts and neighbours) shows rounded
  strands laid at a slant. Tried first and dropped, by renders in spruce and dark oak: the rigging's
  `block/rope` (blotchy at 1.5 px), stripped birch turned along the line (reads as a pale wooden rail), a 4-texel ramp,
  a 4-texel rounded strand, the 6-texel strand with a one-texel slant (the twist barely shows). Posts: `railing_post`.
- **Not done:** the belaying-pin rail on the bulwark (three pins per block inboard of the cap). Left out: it would
  stand where RAIL5's leaners put their arms and RL5's nets leave the latch, and a pin rail on every bulwark block reads
  busy; it fits better as its own block (a pin rail placed on a bulwark) if wanted.
- **uvlock off:** the railing family's block states no longer turn with uvlock (with it a top face keeps the world's
  grain, across the cap on the east and west edges); `RailingModelsTest` checks it.
- **Renders** (a scratch three.js scene in Blockbench's `risky_eval`, no project opened: each model JSON built as one
  quad per face with vanilla's face shading, element rotations and face UV rotations, `FrontSide` like the game's
  culling; vanilla textures from the 1.21.1 client jar; the block states emulated in the scene, posts by the
  `RailingRules.posts` rule and the balusters by the multipart conditions above; composed with a canvas and written
  with `fs`): `renders/railing.png` (a run of three with the swivel gun shifted 6 px onto the cap, a one-block gap, a
  block running into the port with the cannon model behind it, a corner; from the deck, from outboard, close up),
  `renders/railing_before_after.png` (the same scene with the pre-RAIL6 model JSON from git and with the new, one
  camera), `renders/railing_item.png`, `renders/railing_gate.png` (each hinge closed and open between two railings,
  from inboard and outboard, and the item), `renders/bulwark.png` (a run, a shut and an open gun port with a cannon, a
  corner, a railing beside it, the items), `renders/rope_railing.png` (oak: a run of three with a latch and a net strand,
  a lone block, a corner, the item; spruce and dark oak runs at deck distance; close-ups of the lines and the latch), and RL5's `renders/railing_latch.png`, `railing_latch_bulwark.png` redrawn on the new
  cap (the washer stands on the cap's band without clipping or floating; the ring and the strand unchanged).

Wide ports and double gates (RAIL7):
- **Why:** the human (2026-10-10): two ports side by side should lose the posts where they touch, "to have a larger
  port", and the gate should "support a double block door". Part list `tools/gen_railing_models.py` extended;
  `railing.bbmodel` now holds 25 groups. Lint clean on the five new models.
- **Port as parts** (RAIL6's baluster pattern): the block state no longer draws `railing_port` (kept as the port's
  item) but `railing_port_sill` (1 element: planks x 0..16, y 0..4, z 0..2, the ends hidden inside the posts where they
  stand) and the railing's `railing_post` turned to each end's corner while `post_left`/`post_right` are true (looking
  outboard; the left end is the corner keyed by the facing, the right the one keyed clockwise of it). A post is left
  out where the next block along the edge is a port of the same facing, so a row of ports reads as one opening on one
  sill. The port's posts were already the railing post's geometry, so a lone port looks exactly as before.
- **Gate as parts:** the same sill, the post the leaf hangs on (always), the latch post (only unpaired), the RAIL6 leaf
  unchanged, and for one leaf of a double gate (`paired`) its run on to the middle: `railing_gate_left_meet` and
  `_right_meet` (4 elements: the cap body and band and the bottom rail from u 11 to 13.5, i.e. x 13.5..16 for a left
  leaf, and a plain 2 px log meeting stile at x 14..16 from the bottom rail to the cap), with `_open` variants turned
  with the leaf (the same transform about the hinge corner). The two stiles meet at the block boundary as a 4 px pair.
- **Bulwark gun port: unchanged.** Its frame has no stanchions but wall jambs (x 0..3 and 13..16, wall and cap ends)
  either side of the notch; two gun ports side by side keep 6 px of wall between their notches. Leaving the jambs out
  would leave each 10 px lid a 3 px gap at either side when shut (the lid and its hinge are sized for one barrel), and a
  bulwark keeps its planking between gun ports; a wide opening in a bulwark is a railing port's job.
- **Renders** (a scratch three.js scene in Blockbench's `risky_eval`, no project opened, the part JSON drawn face by face
  with vanilla's face shading; the block states emulated: railing posts by `RailingRules.posts`, port posts by the
  neighbour rule, gate parts by `paired`; `art/vanilla` textures from `tools/extract_vanilla_textures.py`):
  `renders/railing_wide_port.png` (two ports, three ports and a lone port between railings, from inboard and
  outboard, a close-up of two ports) and `renders/railing_gate.png` (redrawn: a double gate shut, a double gate open
  and a single gate between railings, from inboard and outboard, close-ups of the shut pair inboard and the open pair
  outboard).

Ratline nets (RL2):
- **Part list in `tools/gen_ratline_net_models.py`** (committed). Three projects, one model each:
  `chainplate.bbmodel` -> `block/chainplate.json` (13 elements), `mast_hoop.bbmodel` -> `block/mast_hoop.json` (14),
  `ratline_net.bbmodel` -> `item/ratline_net.json` (12). Palette sheets only: `palette_2` (`#0`: `cast_iron`,
  `cast_iron_dark`, `cast_iron_light` for straps, bands, bolts and rivets; `lead_light`, `lead`, `lead_dark` for the
  rings; `rope`, `rope_dark`, `rope_coil` for the net) and, for the item's sticks, `palette` (`#1`: `wood`,
  `wood_dark`); particle `palette_2`. No new colours.
- **Rings** are the mooring ring's flat octagon (ART5) stood upright in the y-z plane: four axis-aligned bars and the
  top and bottom bars turned +-45 about x round the ring centre (0.05 px shorter at both ends, 0.05 px thinner in x).
  The ring centre is where `NetFitting#ring` ties the drawn net: 0.3 blocks (4.8 px) off the face the fitting is
  mounted on (`NetFitting.RING_FROM_SUPPORT`).
- **Chainplate**, made for the floor facing north and turned like a button by the block state (wall `x 90`, then `y`
  by the facing): a strap x 6.5..9.5, z 3..13, 0.8 px thick with a 0.3 px bevel on top, two bolt heads, a lug
  x 7.5..8.5 up to y 3.2 and the ring round (8, 4.8, 8), outer apothem 2.2, inner 1.4, 1.2 px thick. On a wall the
  model's y becomes the distance from the wall, so the ring sits 4.8 px off it at half height
  (`ChainplateBlock`'s outline turns the same way).
- **Mast hoop**, facing north with the mast block to the south (z 16..32): a band 3 px high (y 6.5..9.5), 0.4 px proud
  of the mast's north face and wrapping round both sides of the mast block to its back edge (x -0.4..0 and
  16..16.4, z 16..32; the model may reach 32, so the back of the mast stays bare), two rivets on the front, a lug
  z 12.6..15.6 and the ring round (8, 8, 11.2), outer apothem 2, inner 1.25. On a fence-post mast the band stands off
  the post (it is drawn for a full log).
- **Rolled net** (item): built upright round x = z = 8 and every element turned `z -45` about (8, 8, 8) like the flag
  bundles, so the vanilla handheld slots hold it like a tool: two crossed boxes of `rope_coil` (the second 0.05 px
  shorter), two lashings of crossed `rope_dark` boxes, the two sticks poking out of both ends (`wood`, `wood_dark`
  ends) and a loose flap with two rungs and a dark edge on the lower right.
- **Display:** the fittings carry the item's display entries and datagen's block-item models point at them.
  Chainplate: `gui` `[30, 225, 0]` / `[0, 2, 0]` / 1.1, `ground` 0.5 raised 3, `fixed` `[-90, 0, 0]` / `[0, 0, -6]` / 1
  (lying flat in the frame), third person `[75, 45, 0]` / `[0, 2.5, 0]` / 0.5, first person `[0, 45, 0]` (left
  `[0, 225, 0]`) / `[0, 3, 0]` / 0.6. Hoop (its band reaches a block back): `gui` `[30, 225, 0]` / `[3.2, 0, 0]` /
  0.55, `ground` 0.4 at `[0, 3, -3]`, `fixed` 0.55 at `[0, 0, -6]`, third person 0.4 and first person 0.4, both
  moved 3 px back. Net item: vanilla `item/handheld` hand slots, `item/generated` `ground`, `head` and `fixed`,
  identity `gui` (the roll lies on the sprite diagonal), `gui_light: front`. The GUI values were not tuned with the
  offscreen GUI check; the playtest says whether the hoop sits in its slot.
- **Built** like ART5: `Codecs.java_block.parse(json, path)` of the generated file in a new `java_block` tab (the
  palette textures resolve from the path), `Codecs.java_block.compile({raw: true})` compared element by element with
  the file (no difference), the project saved with `fs` (palette embedded, `path` `""`, `relative_path` into
  `common/src/main/resources`). After a change: rerun the script, `tools/lint_models.py` (clean: no fights, no
  warnings for these three), rebuild the projects the same way.
- **The net itself** has no model: `RatlineNetRenderer` draws it from fitting A as square rope beams on
  `textures/block/rope.png` (two side ropes, half width 0.4 blocks, narrowing into each ring over 0.6 blocks; a rung
  every 0.5 blocks; v in blocks along each beam, so the texture tiles). The cells are invisible (a particle-only
  generated model of the rope).
- Renders: `renders/chainplate.png` (three-quarter from the north-west above), `renders/mast_hoop.png` (the band and
  ring from the front, the mast block left out), `renders/ratline_net.png` (the item from the front, as in the GUI) and
  `renders/ratline_net_side.png` (three-quarter). Offscreen views of the projects, copied from the tool output.

Ship decor (ART2): ship's lantern, ship's bell, rope coil, stern window, chart table, sea cot:
- **Part lists in `tools/gen_decor_models.py`** (committed, like the cannon's): run it to rewrite the 18 model JSON
  files, then `python3 tools/lint_models.py`, then rebuild the projects. Every model faces north; wall-mounted ones
  hang on the south side (z 16). UVs are vanilla's position UVs wrapped into 0..16; log faces along x or z are turned
  90 degrees with the UV rect transposed; palette faces (`brass`, `brass_dark`, `iron_dark` of `textures/item/palette`)
  take the inner 3 x 3 px of the patch. No new textures.
- **One project per block, one group per exported model**, all groups at the same position, the first visible
  (`ship_lantern`: `ship_lantern`, `_wall`, `_ceiling`; `ships_bell`: see "Ship's bell swing (BELL1)" below; `rope_coil`: `rope_coil_layers1..4`; `stern_window`: `stern_window`, `_shutters`; `chart_table`;
  `sea_cot`: `sea_cot_item`, `sea_cot_head`, `sea_cot_foot`). Export a group with the other groups' cubes set to
  `export = false`; compiling the whole project merges the variants. The projects were rebuilt from the JSON cube by
  cube (`ART2.load` in `risky_eval`: textures from `art/vanilla/` with namespace `minecraft`, folder `block`, our own
  from the resources, embedded) and `Codecs.java_block.compile()` of each group matches its JSON. The `lint --fix`
  project lookup does not know these groups; fix the part list instead.
- **Lantern** (12 / 17 / 14 elements): base, vanilla `lantern` glass sides (uv 0,3..6,9 of the animation frame, so the
  glow animates), brass corner posts, top plate, two caps and a hanging ring. The ceiling variant is raised 2 px onto a
  hook and a rose, the wall variant raised 1 px under a brass arm with a 45 degree brace into a stripped dark oak wall
  block.
- **Bell** (ART2's two-model swing replaced by BELL1, see below): crossed-box sections (crown, top, shoulder, waist
  with a band, lip with a `black_concrete` mouth) of `gold_block`, no 45 degree octagon bars; a `palette` iron clapper
  and a rope lanyard. Floor: a dark oak belfry frame (base, two uprights, log beam with a brass cap) meant to sit on a
  post; wall: backboard, arm and brace.
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
- **Sea cot** (ART2's look, superseded by "Sea cot rework (ART20)" below; head 17, foot 13, item 30 elements): built as one frame (head z -16..0, foot 0..16) and split at the
  seam (seam faces left out); posts, panelled head- and footboard, side boards and rails, a red blanket with a white
  sheet fold, pillow; the mattress shows only at the head. The item model (`block/sea_cot_item`, both halves shifted
  8 px south) carries the display entries (GUI scale 0.42).
- Renders: `renders/ship_lantern.png` (floor, wall, ceiling), `ships_bell.png` (ART2: floor, floor ringing, wall,
  wall ringing; the ringing models are gone since BELL1, see `ships_bell_swing.png`), `rope_coil.png` (one and three coils), `stern_window.png` (open, shutters), `chart_table.png`,
  `sea_cot.png`.

Sea cot rework (ART20):
- **Why:** the human (2026-10-10): the cot "has some visual problems as an item", "the bed itself is way too thin",
  and two cots side by side "should connect".
- **Item diagnosis** (rendered at the gui, first person, ground and item frame transforms; `renders/sea_cot_item.png`,
  before on top): no z-fight and no open face (`lint_models.py` 0 / 0 before and after). The faults were the display
  entries against ART2's sunken geometry: the gui (`[30, 225, 0]`) looked at the cot from its head, so the 15.75 px
  headboard hid the pillow, the mattress lay 1.75 px under the side boards and only a red patch with two white slivers
  (the sheet fold and the pillow's top over the headboard) showed; at 16 px it read as a dark crate with stray white
  pixels. First person (`[0, 45, 0]` at 0.25) laid the 2-block model flat along the bottom edge of the screen, mostly
  out of view; the item frame (`[0, 90, 0]`) showed only its side boards. Fix: gui `[30, -35, 0]` / `[0, 0.5, 0]` /
  0.45 (from the foot: blanket, sheet fold, pillow and headboard in view), first person `[0, -60, 0]` / `[0, 6, 0]` /
  0.3 for both hands (crosswise and higher; the left entry repeats the right one, ART17: the cot is symmetric along x),
  item frame `[-90, 180, 0]` / 0.42 (from above, head up), ground 0.2; third person and head unchanged. The item is
  the core and both edges of both halves, shifted 8 px south, so it shows the block's proportions.
- **Proportions** (part list `cot_core`, `cot_edge_w`, `cot_fill_w` in `tools/gen_decor_models.py`, constants
  `COT_*`; facing north, head z -16..0, foot 0..16 in one frame, parts crossing z 0 cut by `split_seam` with the cut
  faces left out, so the halves meet without a seam): `#log` side rails y 2..5 (3 px) between corner posts that alone
  reach the floor (the head posts to 14 px with a knob, the foot posts to 11), plank bottom boards y 2..3, a white wool
  mattress y 3..8 (5 px, ART2's showed 2 px between the boards), a red blanket y 8..8.6 from z -5 to the footboard with a
  drape down the mattress' sides onto the rail, a sheet fold to 9 px, a full-width pillow y 8..9.25 with a puff to 9.6.
  Panelled headboard to 13 px under a log cap rail (13..13.75), footboard to 10 px under its cap (10..10.75). The
  sleeper lies at vanilla's 11 px (`lyingHeight` unchanged), above the blanket and the footboard cap. Renders:
  `renders/sea_cot_before_after.png` (ART2 and ART20 from the same cameras: three-quarter, from the head, side at
  eye level), `renders/sea_cot.png` (redrawn).
- **Joining** (block state `left` / `right`, `SeaCotBerth`, the name board's row pattern, no block entity): seen from
  the foot, left is `facing` counter-clockwise (north: west, x 0). A half joins the half beside it when that is a sea
  cot of the same facing and part (any wood), so cots side by side join head to head and foot to foot, a row of any
  length pairwise; a crosswise cot or one shifted a block along its length does not join. Each half is a multipart:
  the core (`sea_cot_head`, `sea_cot_foot`: boards x 2.5..13.5, mattress, blanket, sheet fold, pillow) always, and per
  side either the edge (`_left`, `_right`: posts, knobs, rail, drape; while unjoined) or the fill (`_left_joined`,
  `_right_joined`: head- or footboard with its cap, bottom, mattress, blanket, fold and pillow run on to the block's
  side, the faces there left out; no post, no rail). So a joined row reads as one berth with posts only at its ends, one
  bolster across with a pillow puff per sleeper. The east parts are the west ones mirrored (`mirror_x`). Mixed woods
  join: each block draws its own wood, so a mixed row shows its cots' woods side by side in the boards and the end
  posts are the end cots'. Lint: every part, every core-with-sides combination and the item 0 / 0 / 0. Render:
  `renders/sea_cot_joined.png` (a dark oak pair, a spruce, oak, dark oak row of three, a cot beside a crosswise cot,
  the pair from both ends).
- **Outline and collision:** vanilla's 9 px slab over the cot (x 0.5..15.5, to the block's side on a joined side),
  plus the headboard to 14.75 px or the footboard to 11.75 px at the outer end (ART2: a 10.25 px box).
- **Project:** `art/models/sea_cot.bbmodel` is now written by the script (`bb_project`, like the railing's): 11 groups,
  the item first and visible, textures from `art/vanilla/`. Renders by the RAIL6 method (scratch three.js scene in
  Blockbench's `risky_eval`, no project opened; the multipart emulated by the conditions above; item transforms as
  `ItemTransform.apply` and vanilla's first-person arm offset `(±0.56, -0.52, -0.72)` at a 70 degree field of view).

Ship's bell swing (BELL1):
- **Four groups in `ships_bell.bbmodel`** (32 elements): `bell` (origin = the pin (8, 13, 8): the brass `yoke` round the
  pin, 2.8 x 1.4 x 1.4 px, and the bell hanging 0.8 px under it, crown top at y 12.2, lip down to 5.2; 12 elements),
  `clapper` (same origin: the clapper just below the mouth, the lanyard and its knot down to y 2.2; 3), `post` (the
  belfry frame with two iron `hanger` straps from the beam down past the pin either side of the yoke and the brass
  `pin` along x through them; 8) and `wall` (hidden; the bracket with a brass `crosshead` under the arm end holding
  the hangers and the pin at y 11.8, i.e. `ShipsBellSwing.WALL_DROP` = 1.2 px lower, and a short 45 degree knee brace
  from y 11 on the backboard to the arm, kept clear of the swinging bell; 9). One bell and one clapper model serve
  both mounts: the renderer draws them 1.2 px lower on the wall.
- **Exports** (`Codecs.java_block.compile()` with the other groups' cubes set to `export = false`, all from the part
  list in `tools/gen_decor_models.py`): the block models `ships_bell_post` and `ships_bell_wall` (the mounts; the block
  state picks them by `face`, `ringing` no longer changes the model), the stand-alone `ships_bell_bell` and
  `ships_bell_clapper` (drawn by `ShipsBellRenderer`, registered as additional models), and the item model
  `ships_bell` = bell + clapper + post at rest with the GUI display (`HandMadeModelsTest.shipsBellSplitsIntoMountBellAndClapper`).
  The `_ringing` models were removed.
- **Drawn by `ShipsBellRenderer`:** both parts turned about the x axis through the pin (`ShipsBellSwing.partPose`;
  rotating the hanging mouth towards +z, the back of the north-facing model, is a negative `Axis.XP` angle) by
  `ShipsBellSwing.bellDegrees`: vanilla `BellRenderer`'s damped sine `sin(t / pi) / (4 + t / 3)`, scaled so the first
  swing is `bell_visuals.swing_degrees` (20) and faded out over the second half of `ship_decor.bell_ring_ticks`; the
  clapper trails 1.5 ticks behind, never more than 12 degrees off the bell. Struck from the front the mouth swings to
  the back first, from the back to the front, from a side half as far; a raid alarm rings it as from the front.
- **Clearances** (checked up to the config maximum of 25 degrees; at 30 the yoke would graze the crosshead): the
  yoke's corners stay under the beam (13.93 < 14) and the crosshead (12.73 < 12.75), the lip passes the wall trim
  (z 14.29 < 14.7) and the brace, the bell keeps 0.2 px from the uprights in x (it never moves in x). Everything stays inside the block, so the default culling box fits.
- **Rebuilt** from the generated JSON in `risky_eval` (`BELL1` global: new `java_block` project, one group per part
  file, cubes from the JSON elements, unrotated cubes at origin (8, 8, 8) so the export carries no zero rotation,
  textures from `art/vanilla/` with namespace `minecraft` and folder `block`, ours from the resources and embedded);
  every export was compared element by element with its file before saving with `fs`.
- **Render:** `renders/ships_bell_swing.png`: post and wall at rest and at full swing (front three-quarter and side
  views), drawn with a `THREE.WebGLRenderer` from clones of the cube meshes posed like the renderer.

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

Ship commission (ART11):
- `ship_commission.bbmodel` (`java_block` item project, no block) → `models/item/ship_commission.json`, 14 elements,
  textures `palette`, `palette_3`, `palette_4`, `palette_5`, particle `palette_3`; no new colours. Before ART11 the
  item borrowed `item/ship_receipt` through a model written by `CareerModule.gatherData`; that contribution is gone.
- **Look:** a rolled charter on the 45 degree diagonal like the bounty proof (built upright along y, every element
  `z -45` about `[8, 8, 8]`), but longer and fatter (D 5, y 1..15) and with a **pendant** seal, so the two read apart in
  a slot. Roll: two crossed boxes (D x 0.7 D), `paper` sides, `biscuit_light` front and back, `biscuit_dark` ends; the
  inner turns (D 3) stick out 0.45 px at both ends with `biscuit_light` ends; a `biscuit_light` strip 0.05 px proud
  marks the loose edge of the outer sheet. A `gold` ribbon band (`brass` edges) 0.07 to 0.12 px proud round the middle,
  a `brass` knot on the front, two `gold` tails from the knot to a hanging wax seal below the roll (the lower right in
  the slot): two crossed boxes in `red` with `flag_red_dark` sides (0.05 px apart in depth), a raised `flag_red_dark`
  impression and a `spice_dark` mark.
- **Neutral by design.** One model serves both ladders: navy blue or pirate black per stack would need either a
  `tintindex` with an item colour handler (no `ClientEvents` hook for item colours exists yet; it would be a shared
  platform change) or an item property plus an override to a second model. The gold ribbon and red seal suit both.
- **Display:** the bounty proof's slots (held like a baton in third person, along the fingers in first person,
  `ground` 0.5, `fixed`/`head` vanilla); no `gui` entry, so the slot shows the front view of the diagonal scroll.
- Built like ART4: a Python part list wrote the model JSON, the project was opened in a new tab with
  `Codecs.java_block.parse(json, path)`, the compiled export matches the JSON element by element, and the project was
  saved with `Codecs.project.compile({raw: true})` (texture `path` emptied, `relative_path` to the sheets). The script
  is not committed. Lint: no fights; same-look warnings only (the crossed boxes' shared end planes).
- Render: `renders/ship_commission.png`: front (the GUI view), three-quarter, back, third person in the right hand.

The chart held open (CHART3, the human, 2026-10-12: "Chart is held and open in 3d and animated when opening to other
players"):
- **Approach: item model frames plus a PAL arm pose, not a GeckoLib item.** The unroll is one parameter (how far the
  rollers are apart), so five baked frames picked by a vanilla item property do it on both loaders without a
  per-loader item renderer, keep vanilla's display-transform pipeline (and ART17's left-hand rule) and let every frame
  carry its own display entries, which the pose needs (below). The arms are a PAL layer like the guns' and the rope's.
- **Models** (all from `tools/gen_chart_held_models.py`, deterministic; projects `chart.bbmodel`,
  `chart_unroll_0..3.bbmodel`, `chart_open.bbmodel` opened from the JSON with `Codecs.java_block.parse(json, path)` and
  saved with `Codecs.project.compile({raw: true})`, texture `path` emptied, `relative_path` to the sheets): a parchment
  on two wooden rollers, built upright along y facing south (the map face +z). Each roller: the wound parchment
  (`biscuit_light` of `palette_3`, y -2..18), the rod (`wood`, r 0.55, y -3.6..19.6) and two knobs (`wood_dark`,
  r 1.0) as plus-section pairs of boxes (the second 0.05 px shorter at both ends; no element rotation, so the closed
  model can turn every element `z -45`). `chart` (closed, 19 elements): both rolls side by side (r 1.3), a `cord`
  (`palette_4`) tie, knot and loose end, every element `z -45` about `[8, 8, 8]` like the old rolled chart (which it
  replaces; the old 14-element roll is gone); its hand slots are the old ones at 0.34 (it is 25 px long),
  `gui`/`fixed`/`head` 0.62. Frames `chart_unroll_0` (still rolled), `_1`, `_2`, `_3` and `chart_open`: the rollers at
  x = 8 ± 1.3, 6.79, 9.49, 12.8, 15 (open: 30 px of chart between the axes, 20 px high), the wound parchment
  shrinking from r 1.3 to 0.9, the sheet (`paper`, 0.2 px thick, y -1.9..17.9) between them, and the map as inlays
  0.05 px per layer above the face (`palette_5`: `map_land` islands with `map_ink` coasts, `map_sea` shallows, a
  dotted `map_ink` course, a `flag_red` X of two ±45 bars, an ink frame and a compass rose with `map_sea_dark`
  diagonals and a red north tip); a frame shows only the inlays that lie wholly between its rolls. Lint 0 fights.
- **Overrides** on `chart.json`: `pirates_n_ships:chart_open` 0.01, 0.3, 0.5, 0.7, 1.0 to the five frames
  (`ChartHeldMotion.FRAMES`). The property is the unroll progress of the chart in a hand of the entity drawn
  (`ChartHeldClient`), interpolated over the partial tick; 0 everywhere else (GUI of other slots, frames, ground).
- **Arm animations** (`chart_animations/chart_read.json`, `chart_stow.json`, written by the same script; not
  `player_animations/`, which `PalAnimationFilesTest` pins): 0.5 s, only `right_arm` and `left_arm`, linear keys.
  `chart_read`: from rest (right `[-18, 0, 0]`, left `[0, 0, 0]`) both arms to `[-78, ∓25, 0]` by 0.15 s (forward, the
  fists turned in until the 4 px arms touch round the rolled chart), then the yaw back to 0 by 0.5 s (fists at the
  shoulders' width, 12 px apart); `hold_on_last_frame`. `chart_stow`: the same keys backwards, played once. PAL time
  = progress × 0.5 s, so the started tick of either animation (`ChartHeldMotion.readStartTick` / `stowStartTick`)
  meets the frame on show, also when the screen closes half way.
- **Display entries solved, not tuned.** The item rides on the holding arm, which turns while the fists spread, so
  each frame's third-person entry is solved for the arm pose at the middle of its time (0.15, 0.2, 0.3, 0.42, 0.5 s)
  through vanilla 1.21.1's chain (`ModelPart.translateAndRotate` with `rotationZYX`, `ItemInHandLayer`'s
  `Rx(-90) Ry(180) T(±1/16, 2/16, -10/16)`, `ItemTransform.apply`, `ItemRenderer`'s `T(-0.5)`): the model centre at
  the fists' midpoint, model +x to the player's right (the reader's right: the map is not mirrored), the face towards
  the reader tilted 30° up (`FACE_ELEVATION`: readable from the eyes above it and upright enough that others see the
  chart and its unroll), the scale fixed at 0.4 so the open rollers sit in the fists (the fist = the arm box's centre
  column 9 px down). Open: `[-18, 0, 0]` / `[-6, -2, 1]` / 0.4. The left-hand chain
  solves to the same numbers: ART17's "R unchanged" rule for paper items (the game mirrors the pose, the drawing stays
  readable). First person, every frame: `[-45, 0, 0]` / `[-8.96, 3.2, -0.48]` / 0.46: `applyItemArmTransform`'s
  `(±0.56, -0.52, -0.72)` moved to the middle and a little up, the chart tilted away like vanilla's map at the bottom
  of the view (no arms: the PAL layer keeps first person vanilla).
- **Proof:** `ChartHeldModelTest` composes the real `ModelPart`, `PoseStack` and the frames' parsed `ItemTransform`s
  with the arm angles read from `chart_read.json`, for both hands: the centre within 0.3 px of the fists' midpoint,
  each roller within 0.6 px of its fist, the face within 25° of the eyes and 30° ± 1° up, the map reading left to
  right, chest height and in front; first person centred and low; frames, thresholds and PAL files.
- Render: `renders/chart_held.png` (the closed and the open item, the rolled chart with the fists together, unrolling
  and open as others see them, over the shoulder in the right and the left hand, first person at 16:9). Drawn in
  Blockbench's `risky_eval` in a scratch `THREE.Scene`: boxes from the model JSON in their palette colours, a box
  player (head, body, legs, arms as `ModelPart` matrices from the PAL keys) under `scale(-1, -1, 1)`, the item placed by
  the same chain as above in JS; the script is not committed.
- Not drawn: the player's own chart terrain on the face (a baked item model cannot carry a per-player texture; it
  would need a custom item renderer per loader); the face is a fixed painted chart.
- **Smoother (CHART4, the human, 2026-10-12: "is it possible to make the open/close animation smoother?"):** 14 frames
  instead of five and an eased motion. The progress still runs linearly over the 10 ticks (the property is
  interpolated over the partial tick); what it shows follows `ease(p) = 3p² - 2p³` (smoothstep, ease-in-out): the PAL
  keys sample CHART3's linear motion at `ease(t / 0.5) * 0.5` every 25 ms (plus every frame's own time and its mirror,
  so `chart_stow`, the same keys backwards, keys the same instants and the close rolls up along the same curve).
  `chart_unroll_0` (rolled) covers the raise up to its end (progress 0.364, when the fists meet); twelve frames
  `chart_unroll_1..12` split the rest evenly in progress (thresholds 0.3633 + k × 0.0531), each solved for the pose at
  the middle of its span; `chart_open` at 1. Roller half distances 1.3, 5.96, 6.97, 8.05, 9.11, 10.19, 11.23, 12.16,
  13.03, 13.75, 14.35, 14.75, 14.97, 15 (no step above 1.1 px after the first; the last ones small, the ease-out).
  The script now writes the Blockbench projects too (`art/models/chart*.bbmodel`, settings and textures from
  `chart_open.bbmodel`, element uuids derived from the model and element names). `ChartHeldModelTest` checks the 14
  frames, the even split, the ease and the rig for every frame.
  Render `renders/chart_held.png` (CHART4, replacing CHART3's): the closed item and seven of the frames held as others
  see them (front three-quarter view, each at its own PAL time), drawn by `tools/render_chart_held.js` in Blockbench's
  `risky_eval` (`globalThis.CHART_ROOT = '<repository root>'; eval(require('fs').readFileSync(CHART_ROOT + '/tools/render_chart_held.js', 'utf8'))`, optionally `CHART_OUT` for another output file; the
  real model JSON and palette textures, the box player posed from `chart_read.json`, the item placed through vanilla's
  chain as above).

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

Boarding plank ramps (BRD2):
- Three more groups in **`boarding_plank.bbmodel`** at the same position, each exported alone (the other cubes
  `export = false`): `boarding_plank_ramp_low` -> `block/boarding_plank_ramp_low.json`, `boarding_plank_ramp_high` ->
  `_ramp_high.json` (5 elements each) and `boarding_plank_steep` -> `_steep.json` (6). They climb north (block state
  `pitch` `up_low`, `up_high`, `up`); the falling pitches use the same models turned 180 degrees in the block state.
- Each is the middle cell's three boards and cleats, every element rotated about x with `rescale: true` through one
  origin on the board's top surface. Java models allow only 22.5 and 45 degrees, so the 1-in-2 halves (26.6 degrees)
  are 22.5 degree boards centred on the ideal slope plus 1 px: the low half's top runs 1.69 -> 8.31 px, the high half's
  9.69 -> 16.31 px, a 1.4 px lap at the joint that reads as overlapping planks. The steep cell is a 45 degree board,
  1.5 px thick before rescale (about 2 px across), top 1 -> 17 px, with three cleats. Positive x rotation lifts the
  north end.
- Collision is not the model: `PlankRun.steps` gives stepped boxes 2 px above the ideal slope (two steps per half
  ramp, four per 45 degree cell, never more than 0.375 blocks up between steps).
- Textures as BRD1. The project now stores the vanilla textures by `relative_path` (`../vanilla/<name>.png`, run
  `tools/extract_vanilla_textures.py spruce_planks stripped_spruce_log anvil`); BRD1 had embedded them.
- Renders: `renders/boarding_plank_ramps.png` (a run base, ramp low, ramp high, steep, tip: two blocks of climb) and
  `renders/boarding_plank_ramps_side.png` (the same from the east, the far end on the right). The renders show small gaps at the joints
  (Blockbench's preview did not seem to apply `rescale`); check the joints in game.

Boarding plank as one straight board (BRD4):
- The gangway is now one straight board drawn by `BoardingPlankRenderer` (code, not a model) from the run's segment 0:
  14 px wide (the BRD1 boards' x 1..15), 2 px thick, the vanilla `spruce_planks` texture tiled along it, a 3 px lip
  where an end meets an edge, and `stripped_spruce_log` cleats (1.5 px long, 1 px high, 12 px wide) every block on a
  board steeper than 15 degrees, after the BRD1 cleats. The cells' block model is generated particles only
  (`spruce_planks`), like the ratline net's cell.
- Removed: the block models `boarding_plank`, `boarding_plank_base`, `boarding_plank_tip` and the ramps
  `boarding_plank_ramp_low`, `_ramp_high`, `_steep` (nothing references them), the ramp groups of
  `boarding_plank.bbmodel` and their renders `renders/boarding_plank_ramps(_side).png`. The project keeps the
  `boarding_plank_item` group (exported to `item/boarding_plank.json`, still the item's model) and, as the reference
  for the board's look, the BRD1 groups `boarding_plank_base`, `boarding_plank`, `boarding_plank_tip`, which are no
  longer exported; `renders/boarding_plank.png` shows them.

Crow's nest (CN1):
- **`crows_nest.bbmodel`** -> `block/crows_nest.json` (56 elements; vanilla `spruce_planks` staves and floor,
  `stripped_spruce_log` rim and struts, `anvil` hoops, `dark_oak_planks` underside; particle `spruce_planks`). One
  block, never turned (round). A hollow octagonal barrel round the block's column, 24 px across the flats (x/z
  -4..20): staves are eight 1.2 px wall boards per course (the F7d hollow-body recipe), two courses y 0..10 and 10..20
  so the planks keep their texel density, faces turned 90 degrees so the plank seams run upright as staves; each board
  is 0.2 px longer than the octagon side (the outer corners overlap, no crack); the diagonal boards are 0.05 px short
  only at the barrel's foot and rim, not at the course joint (a gap there showed as a white line). Three iron hoops
  (0.3 px proud, 1.6 px high) at y 0.6, 9.2, 17.6; the floor (y 0..1.5, `CrowsNestBlock.FLOOR_HEIGHT`) and the
  underside (y -2..0 across 22 px, y -4..-2 across 14 px) are solid four-bar octagons whose bars step 0.05 px shorter
  at both ends one after the other (x, z, +45, -45), so no two caps share a plane. Four struts (1.5 px square, 10.5 px)
  lean 22.5 degrees out from the faces of a fence post (x/z 6..10) at y -13 up under the bottom. Lint: clean (no
  fights, no warnings). `HandMadeModelsTest.crowsNestFloorAndRimMatchTheBlock` ties floor (1.5) and rim (20) to the
  block.
- **Item:** no `_item` model; the block model carries the display entries (`gui` `[30, 225, 0]` / `[0, 1.75, 0]` /
  0.5, centred with the F7g offscreen GUI check; hands 0.25; ground 0.2; fixed 0.4) and datagen's block-item model
  delegates to it.
- Built from a Python part list (scratch, not committed: `ring` = eight wall boards round the centre at an apothem,
  `octagon` = four solid bars, `box` with vanilla position UVs wrapped into 0..16) that wrote the JSON, then
  `Codecs.java_block.parse(json, path)` in a new `java_block` tab, the four textures reloaded from `art/vanilla/`
  (namespace `minecraft`, folder `block`); `Codecs.java_block.compile()` gives the same 56 elements and faces.
- Render `renders/crows_nest.png`: on a stand-in mast (three-quarter from above), looking into the barrel, and from
  below (struts); a separate `THREE.WebGLRenderer` over clones of `Project.model_3d`, written with `fs`.

Flag pedestal and the pole on the crow's nest (FLG3):
- **`flag_pedestal.bbmodel`** -> `block/flag_pedestal.json` (9 elements, group `pedestal`) and
  `block/flag_pedestal_pole.json` (17, groups `pedestal` + `pole`), exported from the one project with the `pole`
  group's cubes set to `export = false` for the plain model (the HELM1 pattern). A plinth carved from a log in the
  flagpole's `stripped_spruce_log` (sides) with `stripped_spruce_log_top` rings on the up faces: `plinth` x/z 3..13,
  y 0..1.5 (down face culled), `bevel_low` 3.5..12.5 to 2.25, `body` 4..12 to 4.75, `bevel_top` 4.5..11.5 to 5.25
  (stepped 0.5 px bevels), an `anvil` `hoop` 0.3 px proud at y 3.1..3.9 and a `socket_*` collar of four anvil bars
  (outer 5.5..10.5, the 3 px hole 6.5..9.5) at y 5.25..6. Only the plain model carries display entries (`gui`
  `[30, 225, 0]` / `[0, 3.5, 0]` / 1, hands 0.5 and 0.6, ground 0.5, fixed 1); datagen's block-item model delegates
  to it. `FlagPedestalBlock`'s outline is x/z 3..13 below 2.25 px and 4..12 up to 6 px, plus the pole's 4×4 post up
  to 16 px while a pole stands in it.
- **The `pole` group** (both projects, every element named `pole_*`): `pole_stub` is the flagpole's 3 px pole at x/z
  6.5..9.5 from the socket floor (5.25) or the nest's floor (1.5) up to 16 with no up and no down face, so the pole
  block above (part `top` or `middle`, which leave out their down face) joins it without a seam; then the cleat, its
  wound rope and the two halyard lines copied unchanged from `flagpole_bottom` (horn y 6.6..11.6, halyard from 10.7,
  VIS1a-b), on the same diagonal. On the nest an anvil `pole_collar_*` (same four-bar ring, y 1.5..2.5) seats the
  stub on the floor. The nest's halyard texture is a new `rope` key (`stripped_birch_log`); the plain `crows_nest.json`
  was not re-exported and is byte-identical (the plain export of the edited project matches its elements).
- **`crows_nest.bbmodel`** now holds the 56 nest cubes in a `nest` group and the 12 new ones in `pole`;
  `block/crows_nest_pole.json` is the committed nest elements followed by the pole group's. The block state picks the
  `_pole` model from the footing's `pole` property (`FlagpoleFooting.POLE`, true while a flagpole stands on it).
  `HandMadeModelsTest.pedestalAndNestShowThePoleStubWithTheCleat` checks that each `_pole` model is its plain model
  followed by `pole_*` parts only, with the stub ending at 16 px and the cleat and halyard present.
- Built from a Python part list (scratch, not committed) that took the cleat and halyard from `flagpole_bottom.json`:
  `Codecs.java_block.parse` into a new `java_block` tab for the pedestal, and for the nest `loadModelFile` of the
  committed project plus cubes created from the part list (`autouv: 0`, UVs, face rotations and textures by id);
  textures from `art/vanilla/` (namespace `minecraft`, folder `block`); exports compared with the part list.
  `tools/lint_models.py`: no visible fights; the same-look cleat pair of `flagpole_bottom` and hidden faces of the
  collar bars and the stub inside the collar are warnings.
- Renders `renders/flag_pedestal.png` (the empty pedestal, the pedestal with the stub and two stand-in pole sections
  above it, the cleat close up) and `renders/crows_nest_pole.png` (the nest with a pole rising through it, looking into
  the barrel, the collar and cleat on the floor), a `THREE.WebGLRenderer` over clones of `Project.model_3d` (the pole
  above drawn as copies of the stub and halyard), written with `fs`.

Left-hand display audit (ART17, after ART16 found the book's left hand unmirrored):
- Every model under `models/item/` with explicit left-hand entries (60 files; `models/block/` has only
  `block/block`-style and same-as-right entries, all fine) was classed by the ART17 rule: the effective left pose
  (y, z negated) compared with the mirror image of the right pose, axis by axis, then checked in Blockbench's display
  mode for both hands in first and third person.

  | Model(s) | Left entry before | Class | Result |
  |---|---|---|---|
  | `cutlass`, `rapier`, `saber`, `carpenters_hammer`, `saw`, `paddle`, `rope`, `kraken_beak`, `shipwright_toolkit`, `hammock`, `boarding_plank`, `ratline_net`, `navy_flag`, `merchant_flag`, `jolly_roger_flag` (both slots), `ratlines` (first person) | vanilla `item/handheld` (`[0, 90, -55]`, `[0, 90, -25]`) | turn about y at y = ±90 | correct, unchanged (the right-hand values instead made the blade lie edge on, pointing back) |
  | `pistol`, `pistol_loaded`, `musket`, `musket_loaded`, `musket_hook` (both slots) | `[0, -90, -40/-43]`, `[0, -90, 25]` | turn about y at y = ±90 | correct, unchanged (`renders/pistol_hands.png`) |
  | `brig_key` third person, `brig_door` third person | `[0, 90, -120]`, `[70, -90, 0]` | turn about y at y = ±90 | correct, unchanged |
  | `brig_key` first person | `[0, 72, 170]` (negation, y = -72) | wrong: unmirrored, bow end on | **`[180, 72, -10]`** (turn about y) |
  | `brig_door` first person | `[0, 70, 0]` (negation) | wrong: unmirrored, door edge on | **`[0, 110, 0]`** (turn about y; hinge towards the middle, lock plate outside) |
  | `hull_patch` first person | `[0, 70, 0]` (negation) | wrong: unmirrored, edge on | **`[0, -70, 0]`** (= right; the braced face stays towards the holder) |
  | `hull_patch` third person | `[70, -90, 0]` (negation, y = ±90) | mirrored, but the plain back faced out | **`[70, 90, 0]`** (= right, braced face out as in the right hand) |
  | `chain_shot` first person | `[0, 35, -25]` (negation) | wrong: unmirrored, balls end on | **`[0, -35, 25]`** (= right; the shot is symmetric) |
  | `captains_almanac` | negation | wrong | fixed by ART16: first person `[70, -10, -8]` = right, third person `[0, 0, -90]` for `[0, 0, 90]` (turn about z, which here also reads as a negation) |
  | `grapeshot`, hats, `bandana` first person | `[0, 225, 0]` / `[0, -135, 0]` for `[0, 45, 0]` | turn about y | correct, unchanged |
  | the remaining 28 (`item/generated` copies, `doubloon`, `map_tile`, `treasure_map`, `ship_receipt`, third-person `[0, 0, 0]` / `[75, 45, 0]`) | same as right | `R` unchanged | correct, unchanged |
- Inherent limit: a sprite-aligned item shows its other face in the left hand (the geometry is not mirrored), so the
  Jolly Roger bundle's skull, which was on the front only, faced the body in the off hand (fixed by ART18, below); the
  pistol's lock likewise. Vanilla sprites behave the same.
- Edits: only the changed `rotation` numbers in the display block of each model JSON and its project
  (`brig_key`, `brig_door` / `brig_door_item.bbmodel`, `hull_patch` / `hull_patch_item.bbmodel`, `chain_shot`), set in
  Blockbench's display mode and written into both files with every other byte kept (the diff shows only those
  lines); `Codecs.java_block.compile()` of the edited tabs gives the same elements.
- Renders (left hand on the left, right on the right; first person on top, third person from the front three-quarter
  of each side): `renders/brig_key_hands.png`, `brig_door_hands.png`, `hull_patch_hands.png`,
  `chain_shot_hands.png`, and `pistol_hands.png` for the unchanged handheld form.

Flag bundle devices on the back face (ART18, after ART17's finding that the off hand shows the bundle's back):
- **Rule: a device on the front of the fold gets a copy on the back, turned 180° about the bundle's own axis** (the
  element-space y axis through x = z = 8, before the element's `z -45`): `x → 16 − x`, `z → 16 − z`, faces south ↔
  north and east ↔ west, up and down kept, same palette cell per face. A z-only reflection would read mirrored from
  behind; the half-turn reads like the front (the navy's `cross_back`, there since ART1b, is exactly this turn of
  `cross_front`, so its arm sits towards the hoist seen from either side).
- **Jolly Roger** (skull and crossbone, symmetric, so the turn equals a mirror): `skull_back`, `jaw_back`,
  `eye_l_back`, `eye_r_back`, `bone_back`, `bone_l_back`, `bone_r_back` added at z 4.22..4.45, on the deep fold box's
  back face (z 4.45) with the same 0.15 px and eye 0.08 px proud steps as the front. 25 → 32 elements.
- **Navy** (white Nordic cross: the band round the roll plus `cross_front` / `cross_back`) and **merchant** (red band
  round the roll): the roll already carried them all round; only the loose flap showed the band on its front, so the
  back of the flap was plain. `flap_stripe_back` (z 10.59..10.65, behind the flap's north face at 10.65) and
  `flap_tip_stripe_back` (z 10.09..10.15, behind the tip at 10.15, with a west face, since the tip's west end is
  open) carry the band there, same y span and cell as the front stripes. Navy 26 → 28, merchant 24 → 26.
- Edits: the new elements are appended at the end of each model JSON's `elements` and of each project's `elements`
  and `outliner` (uuids are `uuid5` of `pirates_n_ships/<model>/<name>`); every other byte of both files is
  unchanged. The projects, loaded in Blockbench, compile (`Codecs.java_block.compile()`) to exactly the committed
  elements. Lint: no fight or warning involves a new element.
- Render `renders/flag_bundles_hands.png`: each bundle in the off hand (left columns) and the main hand (right
  columns), first person (the display mode's eye, fov 70, the half of the screen with the item) and third person
  (the player model from the outside three-quarter of that hand). In the off hand the skull, cross and band now face
  out; the main hand is unchanged.

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
| `lean` | idle, leaning on the rail (RAIL5; `CrewMember#isLeaning`) | see "Leaning on the rail (RAIL5)" below |

Priority: station pose (ART7) > `work` > `sleep` > `sit` > `lean` > `walk` > `idle` (`crew/npc/CrewPose`). One controller (`body`) plays them with a 5-tick
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
| `capstan_push` | 1.2 s | loop | chest to the bars (waist 30–33°), arms forward on them (−75° to −78°, 14° inwards), walking strides ±25° with a 0.6 px dip; played at the capstan station (CRW3, `station/capstan/CapstanPoses`) while it drops or raises the anchor; since CAP2 the crew member walks round the drum facing along the circle (the strides now carry him), facing the capstan from the station spot only with `crew_stations.capstan.animation` off |

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

### Leaning on the rail (RAIL5)

One more animation on the crew rig, `lean` (6 s, loop), played while an idle crew member leans on a railing
(`crew/lean/RailLean`, `CrewPose.LEAN`, synced as `CrewMember#isLeaning`). **Written by script, not built or viewed in
Blockbench** (Blockbench was taken by another package): `tools/gen_lean_animation.py` writes the entry into
`crew_member.animation.json` and keeps every other byte of the file (it refuses to run if the file is not in
autoStringify form); the four seafarer projects and `crew_member.bbmodel` do not carry it, and there is no render yet.

| Bone | Channel | 0 s and 6 s | 3 s (the breath) |
|---|---|---|---|
| `root` | position | `[-0.5, 0, 0]` (half a pixel to the model's right: the weight on the right leg) | the same |
| `waist` | rotation | `[24, 0, 2]` | `[25.5, 0, 2.5]` |
| `right_arm` | rotation | `[-80, -10, 0]` | `[-81.5, -10, 0]` |
| `left_arm` | rotation | `[-80, 10, 0]` | `[-81.5, 10, 0]` |
| `right_leg` | rotation | `[-2, 0, 1]` (the standing leg) | the same |
| `left_leg` | rotation | `[8, -6, -4]` (relaxed, back and out) | `[9, -6, -4.5]` |

Every key `easeInOutSine`; the head is never keyed (contract above).
- **Signs.** The values are in the file convention of every animation in the file (the vanilla player's signs, which
  GeckoLib's loader flips, x and y negated, on load; GL1 rule 1 is about angles written from code, which this is not):
  `waist` +24 leans the upper body forward over the rail; the arms' −80 swings them forward (the arm hangs from the
  waist, so its angle in the world is the waist's 24 minus 80: 56° forward of straight down); `right_arm` −10 and
  `left_arm` +10 turn both arms inwards (as `capstan_push`'s ±14) so the hands come together; the breath adds 1.5° to
  the waist and takes 1.5° from the arms so the forearms stay on the rail; `left_leg` +8 swings it back and −4 spreads it
  out; root x −0.5 moves the body to the model's right (position x is negated on load too).
- **Where it stands.** In the railing's own block, its front 0.02 blocks short of the rail's inner face
  (`LeanRules.LEAN_OFFSET` = 0.5 − 2/16 − 0.3 − 0.02 outboard of the block centre), facing outboard
  (`StationPoses.yaw` of the railed edge, turned with the ship, the ST5 body turn).
- **What the rig test proves** (`CrewLeanPoseTest`, GeckoLib's baked rig and loaded keys, `RenderUtil.prepMatrixForBone`
  down `root → waist → arm`, the renderer's `180 − bodyYaw` turn, the leaner at its lean point for all four outboard
  sides, at 0, 3 and 6 s): both hands forward of the shoulders, outboard of the rail's inner face (about 0.85 blocks
  ahead of the body), 16–20 px up, the right hand on the leaner's right; the arm cubes' underside over the rail's
  2 px depth lies 0–1.5 px above the rail top (the forearms rest on it: measured 0.5–0.9 px); the neck is more than
  0.15 blocks outboard of the feet. A wrong sign on the waist or an arm puts the hands behind the body or into the rail
  and fails it.
- **The head** is turned from code (`LeanRules.headYaw`, applied to the entity's head yaw, so `EntityModelData` brings
  it to the head bone already flipped, GL1 rule 2): 3 s looking straight out, then a 4 s turn to 35° one side and back,
  the other side the next cycle, within ST5's ±45° of the body; the head pitch is −10° (Minecraft pitch, up), which on
  the 24° waist leaves the face looking about 14° down at the water.
- **Checked in Blockbench (PND4, 2026-10-09).** `crew_member.bbmodel` opened in its own tab, `lean` imported from
  `crew_member.animation.json` (Blockbench's export of it equals the file) and saved into the project (appended as the
  18th animation, every other byte unchanged). A rail reference (16 px wide and tall, 2 px deep, its inner face 5.12 px
  in front of the feet: `0.5 − 2/16 − LeanRules.LEAN_OFFSET` blocks, the front is −z in Blockbench) was added to the
  preview scene for the check only. Sampled with the posed meshes at 0, 1.5 and 3 s: no cube of the rig enters the
  rail; over the rail's depth the arm cubes' underside lies 16.5–16.8 px up (0.5–0.8 px above the 16 px rail top), the
  sleeve and cuff layers 0.0–0.6 px, so the forearms rest on it (the rig test's 0.5–0.9 px agrees); the hands hang about
  6.7 px outboard of the rail's outer face; the right leg stands straight under the shifted root, the left trails back
  and out (the weight on one leg, a quiet shift: half a pixel); the only overlaps are the vanilla rig's own joints
  (arm tops 0.27 px into the body at the shoulder, the legs 0.32 px at the hip). **No key changed**, so
  `tools/gen_lean_animation.py` was not rerun. Render `renders/anim_crew_lean.png`: three frames (0, 1.5, 3 s) with the
  rail reference and a deck, top row three-quarter front, bottom row from the leaner's left side (the head is
  untouched: its turn is code). Open: the seafarer projects (`pirate`, `sailor`, `navy_*`) still do not carry `lean`
  in their `.bbmodel` (they share the animation file, so the game is unaffected).

### Rowing (OAR1)

**Models** (`tools/gen_oar_models.py`, part lists; the script writes the exported JSON and a bare Blockbench project,
textures referenced from `art/vanilla/` (`tools/extract_vanilla_textures.py oak_planks stripped_oak_log`); opened and
rendered in Blockbench 2026-10-09, `python3 tools/lint_models.py` clean):
- `block/oar_bench` (`art/models/oar_bench.bbmodel`, 12 elements, `#planks` and `#log`, a child per wood from the
  wood family): north frame = `facing=north`, the outboard side. A thwart x 4..12 the whole block long, top at 8 px, on
  four stripped-log legs with two stretchers and a spine; at the north end a gunwale board (y 6..9) with a knee under it
  and two thole pins (y 9..13) with a 3 px gap where the oar lies. The item is the block model. Render
  `renders/oar_bench.png` (two three-quarter views and the outboard end).
- `item/oar` (`art/models/oar.bbmodel`, 9 elements, oak planks and stripped oak log): 3 blocks along the model's +y
  (y −16..32): grip and knob, the loom with a leather button, the shaft, throat, shoulder, the blade (6 × 7.5 px) with a
  centre rib. Third-person entries: rotation 0, scale 1.4, translation (0, 22.4, 0), which puts the loom at model y −8 in
  the fist and runs the shaft along the hand's +y (4.2 blocks drawn); the left-hand entry equals the right one, vanilla
  mirrors it (ART17). Render `renders/oar.png`.

**Animations** `row_standing` and `row_seated` (2 s, loop, `easeInOutSine`) on the crew rig, written by
`tools/gen_rowing_animations.py` into `crew_member.animation.json` and imported into `models/entity/crew_member.bbmodel`
(Blockbench's export of the two equals the file; a constant channel carries two keys, since the exporter writes a
one-key channel as a bare vector). Keys at 0 (catch), 0.45, 0.9 (finish), 1.2 (release), 1.6 (recovery), file
convention: waist 22 / 6 / −10 / −6 / 8°; right arm (−88 / −70 / −48 / −55 / −75, −16, 0), the left arm its mirror;
right_hand (1.8, 138.2, 29.6) / (30.5, 139.5, 36.3) / (59.9, 147.3, 37.0) / (76.4, 133.7, 58.9) / (59.9, 125.8, 74.5),
left_hand the mirror (x kept, y and z negated). Standing: feet apart (right leg −14 to −18°, left 12 to 8°). Seated:
root 4 px down onto the thwart, legs −84° forward and splayed 10°. Strips `renders/anim_crew_row_standing.png` and
`anim_crew_row_seated.png` (top row from above and the rower's left front, bottom row from the front; a proxy oar on
`right_hand` for the shot only, not saved).

**The signs (GL1).** The hand keys were solved numerically against the oar's display entry with GeckoLib's transforms
written out (baked pivots x-mirrored, x and y of every key negated, `rotationZYX`, the item frame's x −90, vanilla's
`ItemTransform`), aiming the shaft outboard (the rower's right for the right hand), 36–40° down in the drive and
14–16° in the recovery, swept 35° toward the bow at the catch and 35° toward the stern at the finish; the rower faces
aft, so the blade moves toward the stern while in the water and the handle toward the bow. `crew/npc/OarRigTest`
composes the real files end to end (GeckoLib's loader, `RenderUtil.prepMatrixForBone`, `HumanoidGeoRenderer.ItemFrame`,
the oar's `ItemTransform` from `item/oar.json`, the renderer's `180 − bodyYaw` turn) for all four facings and both
sides, with the handedness `sailing/oars/Rowing#leftHanded` gives (a rower whose outboard side is on its left is made
left-handed, so the oar goes into the left hand and the left_hand keys mirror the stroke): the blade outboard more than
1.8 blocks, below the deck in the drive, sweeping more than a block toward the stern, the handle pulled toward the bow,
lifted more than 0.8 blocks in the recovery and carried back. Blockbench's preview agrees (the proxy blade, measured in
the scene: outboard on the right, 12–13 px below the feet in the drive, moving 37 px forward from catch to finish).

**Wiring.** `CrewPose.ROW_SEATED` / `ROW_STANDING` (station ids 7 and 8): the oar bench's resolver
(`sailing/oars/OarBenchStation`) while its row order works, the rail rower's seat (`sailing/oars/OarSeat`, a
`crew/npc/PosedSeat`) while it rows; both face aft (the stern of the ship's bow).

### Melee poses for the crew (MEL3)

The rig has no melee clips: `crew_member.animation.json` holds no `attack`, `guard`, `parry` or `stagger`. The M3
mobs and, since MEL3, the crew show their sword fights through the same procedural poses instead:
`mob/client/DuelistArmPose` (angles per melee phase, in the animation-file convention) written by
`mob/client/MeleeTelegraph` after GeckoLib ran the controllers, so they win over the body clip (the melee pose beats the
station pose, which beats idle; `CrewPose.choose(..., fighting)` drops the crew's body to idle, walk or sit meanwhile).
They pose the main arm, the waist and, for the thrust, the main hand (the wrist levels the blade: -35° drawn back,
+50° on the lunge), with x and y negated for GeckoLib and y and z mirrored for a left-handed duelist. A bare-handed
vanilla swing (`swing(hand)`, BRD3's unarmed crew) gets `DuelistArmPose.punch` from `getAttackAnim` (the crew and the
mobs run vanilla's swing timer for it; the navy soldier's shove keeps its own clip). The synced pose comes from
`mob/MeleePoseSync`, shared by `SeafarerMob` and `CrewMember`. `CrewMeleePoseTest` composes the real transforms down to
the cutlass's grip and tip: at rest the blade points forward; the slash wind-up holds it over the head, the strike
brings it down, forward and across to the off side; the thrust draws back and lunges with the blade level ahead; the
guard holds it up and across the chest, the parry higher; the stagger flings the arm out and back; the punch reaches
forward at shoulder height; left-handed mirrors. Blockbench clips can replace the poses later (the telegraph would then
choose the clip per phase), but none was needed for MEL3.

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
- Known limit (ART6, lifted by ART9 below): a vanilla armour layer has no skirt or tails, so the worn coat ended at the
  waist.
- The worn texture now comes from `tools/paint_apparel_armor.js` (ART9), which repeats `OC.paintArmor` pixel for
  pixel and adds the tails; `OC.paintArmor` is kept only as the ART6 source.
- Renders: `renders/officers_coat.png` (front, three-quarter, side, back), `renders/officers_coat_worn.png` (on a
  stand-in figure in a throwaway GeckoLib tab; a Bedrock-style project draws every cube with its one texture, so the
  preview merged a base skin and the coat layer into one sheet).

### Coat tails and the captain's clothing (ART9)

**Coat tails** (both coats): `apparel/client/CoatArmorModel`, a `HumanoidModel` from a `LayerDefinition`: vanilla's
outer armour mesh (`HumanoidArmorModel.createBodyLayer(1.0)`, the model the chest slot uses) plus `tail_right` and
`tail_left` on the **body** (pivot y 12, z 3: the plain body's bottom back edge), each a back plate 5x7x1 (x 0.5..5.5
from the centre, so the tails split 1 px at the back) and a side plate 1x7x6 (x 5.5..6.5, z -1.9..4.1 in body space),
hanging to the knee (y 19). Blockbench source `art/models/entity/coat_armor.bbmodel` (Modded Entity, Mojang mappings,
64x32 box UV, the officer's texture embedded); `createLayer()` is its exported `createBodyLayer()` on vanilla's mesh
(Blockbench's modded-entity export maps x to -x and y to 24 - y and keeps z).
- **How it is drawn:** `ClientEvents.registerArmorModel(provider, items...)` (common) -> NeoForge
  `RegisterClientExtensionsEvent` with an `IClientItemExtensions#getHumanoidArmorModel` per item
  (`platform/NeoForgeArmorModels`); NeoForge copies vanilla's pose and part visibility onto our model and
  `HumanoidArmorLayer` draws it with the material's layer texture. Fabric (FAB2): `ArmorRenderer.register` in
  `platform/FabricArmorModels`, which redoes vanilla's layer around the provider (pose and slot visibility onto the
  player armour model, the provider, pose and visibility onto its model, the material layers, trim and glint); ART13
  added `FabricArmorModelsTest`. `ApparelClient` bakes the model once and returns it for the chest slot only.
- **Swing:** nothing calls `setupAnim` on an armour model, so `renderToBuffer` poses the tails from the copied legs and
  body (`apparel/CoatTails`, pure): world pitch = 0.08 + b + 0.5 b^2 for the same-side leg's backward swing b, a small
  flare (0.12 per rad) for a forward leg, at most 1.6; the body's pitch (crouch 0.5) is taken out up to 0.4 so the
  tails hang nearly straight when sneaking. The quadratic term is needed because the leg turns about the hip, 3 px in
  front of the tail's hinge. `CoatArmorModelTest` composes the real `ModelPart#translateAndRotate` chain against a
  boot-clad leg: for leg pitch -0.8..0.8 (vanilla's walk and sprint) standing and sneaking, no leg point lies behind
  the lower 5 px of a tail. At the extreme stride (pitch 1.4, only at full walk speed, e.g. falling) the upper leg still
  cuts the top of a tail, as with vanilla capes.
- **Texture layout** (64x32, both coats): body and arms where vanilla has them; the tails in the head area, which a
  chest-only material never draws: right back plate box UV (0,0), left back plate (0,8), right side plate (12,0), left
  side plate (26,0). The painter (`tools/paint_apparel_armor.js`, Node, deterministic, `--check`) paints cloth, a
  lining on the faces towards the legs, a hem along the bottom, an edge down the split and the side plates' front edge,
  and a button at the top of each back plate. `ModelPart.Cube` runs the west face's u from south to north and the
  east face's from north to south (the front edge columns follow that).

**The captain's clothing** (`captains_coat` chest, `captains_breeches` legs, `captains_boots` feet; `ClothingItem`
`ArmorItem`s, no durability, armour `apparel.captains_*_armor` 3 / 2 / 1). Materials: `captains_coat` (layer 1 with
the tails, drawn with the coat model) and `captains_clothing` (layer 1 the boots on vanilla's outer model, layer 2 the
breeches on the inner model): the coat needs a material of its own because its tails sit in the head area of layer 1.
Colours from the captain's skin palette (`SF.C` of `seafarer_skins.js`, as `SF.pirate_captain` paints his jacket):
charcoal `cc`, gold edging, brass buttons, a brocade waistcoat with a white jabot in the open front, a red sash round
the waist (rows 8..9) with the knot on the left side face, a leather baldric diagonal from the right shoulder to the
left hip front and back, wide crimson cuffs ringed in gold, crimson lining in the tails; breeches `belt_d` over black
to the knee with white stockings below and a leather waist band with a brass buckle on the body's lower rows; boots
`boot` from row 4 with a turned-down leather bucket top (rows 4..6) and an instep strap. His own rig and skin are
unchanged.
- **Item models** (Blockbench, `java_block`, flat like the officer's coat in z 7..9, item/generated's display
  entries): `art/models/captains_clothing.js` (`CC.PARTS`, `CC.build(repo, item)`, `CC.export(repo, item)`) ->
  `art/models/captains_coat.bbmodel` (30 elements: the officer's cut in charcoal with a longer skirt, gold front edges
  and hem, brocade waistcoat, jabot, brass buttons, sash with knot and ends, baldric and buckle turned -45 about z,
  sleeves at 22.5 with crimson cuffs), `captains_breeches.bbmodel` (14: waist band and buckle, seat, two legs with
  knee bands and buttons, stockings, shoes), `captains_boots.bbmodel` (16: a pair seen from the side, toes left, the
  right one 1 px behind). Palette patches only (`palette`, `palette_4` brocade, `palette_5` flag black for the
  charcoal). Lint: 0 visible fights.
- **Renders** (`art/models/entity/apparel_render.js`, a three.js composer in `risky_eval` that builds vanilla's box-UV
  cubes, armour inflation, mirrored left limbs and the `CustomHeadLayer` + `head` display chain for the hat on the
  crew member's skin; not the game's lighting): `renders/coat_tails.png` (officer's coat standing, both strides,
  sneaking; front, side, back, back three-quarter), `renders/captains_set_worn.png` (hat, coat, breeches and boots
  standing from front, front three-quarter, side, back, back three-quarter, and walking),
  `renders/captains_clothing.png` (the three items: front, three-quarter, side, back).

### The officer's outfit (ART13)

**The officer's breeches and boots** (`officers_breeches` legs, `officers_boots` feet; `ClothingItem`s like the
captain's, no durability, armour `apparel.officers_breeches_armor` 2 and `officers_boots_armor` 1) complete the
officer's coat. Material `officers_clothing` (layer 1 the boots on vanilla's outer model, layer 2 the breeches on the
inner model), not the coat's: the coat's layer 1 carries the tails in the head area. No custom armour model: vanilla's
layer draws both on both loaders. Obtained with the coat as the Lieutenant's career gift
(`careers.rewards.lieutenant_items`) and crafted beside it; navy officers drop no clothing (they never dropped the coat
either).
- **Worn textures** (`tools/paint_apparel_armor.js`, `officersBoots`, `officersBreeches`): the legs of ART6's officer
  (`SF.navy_officer`, `SF.navyCoat`, colours of `SF.C`): white breeches shaded with `belt_shade`, a 2 px navy stripe
  down the outer seam (the right leg's right face; vanilla mirrors it onto the left leg's outer side) to a `belt_shade`
  knee band with two brass buttons, a navy-topped white waist band on the body's lower rows with a brass buckle and two
  gold fall-front buttons; boots from row 5 (as the officer's skin) in `boot` over `boot_d`, a two-row turned-down top
  in `boot_l`, the shin highlight column, an instep strap with brass buckles on the front and outer faces. The ART6 and
  ART9 textures are unchanged (`--check` identical).
- **Item models** (`art/models/officers_clothing.js`: `OF.init(repo)` loads `captains_clothing.js` and adds
  `CC.PARTS.officers_breeches` / `officers_boots` plus the navy patches of `palette_5`; `OF.build`, `OF.export` are
  `CC.build`, `CC.export`): `officers_breeches.bbmodel` (17 elements: the captain's breeches' cut in white, navy waist
  band with a brass buckle, gold fall-front buttons, a navy stripe on each leg's outer front edge, knee bands with gold
  buttons, white stockings, black shoes), `officers_boots.bbmodel` (16: the captain's pair recoloured, flag-black
  shafts and feet, `horn` turned-down tops, a black strap with a larger brass buckle). Lint: 0 visible fights.
- **Renders** (`AR.renderOfficers(repo)` in `art/models/entity/apparel_render.js`, the ART9 composer):
  `renders/officers_set_worn.png` (bicorne, coat with tails, breeches, boots; standing from five sides and walking),
  `renders/officers_clothing.png` (coat, breeches and boots items: front, three-quarter, side, back).

### Village people's outfits (LIFE1)

The villagers of the seafarer villages (`mob/village`) are texture variants on the sailor's model, one sheet per
role, like the harbor master (PRT1a): `textures/entity/villager.png` (the plain villager) and
`textures/entity/villager_<role>.png` for `fishwife`, `net_mender`, `dockhand`, `shipwright`, `innkeeper`,
`lamplighter` and `vendor`, all new. They are painted by `art/models/entity/seafarer_skins.js` (`SF.villagerSheet(role)`,
registered in `SF.VARIANTS`) and written with `node tools/paint_seafarer_skins.js villager villager_fishwife ...`; the
sailor's and the harbor master's sheets stay byte-identical (`--check sailor harbor_master`).

Each sheet is built as an outfit of three layers, the names of `mob.village.VillagerOutfit`: a **hat** on the cap's
cubes (`SF.villagerHat`: `felt_cap`, `head_scarf` checked blue and white, `knitted_cap` green and ribbed,
`grey_bandana`, `leather_cap` with a brass rivet, `peaked_cap` with a brass badge, `straw_hat` with a green band;
`none` leaves the cap regions transparent so the innkeeper is bare-headed and balding), a **coat** (the brown or red
waistcoat on the base layer, the fishwife's knitted shawl on the jacket layer and sleeve tops, the shipwright's and
the women's aprons on the body and legs fronts via `SF.apron`, the lamplighter's long grey coat on the jacket, sleeve
and trousers layers like the harbor master's frock coat) and the **clothing** under it (`SF.villagerBase`: face and
hair, shirt or bodice, sleeves rolled to a given row, breeches with stockings, trousers, or a skirt flared on the
trousers layer for the fishwife and the vendor). The sailor's detail cubes (neckerchief, rope belt, buckles, cuffs, the
slop hems) are recoloured through the patch list; a `null` patch hides its cubes (the shipwright and the innkeeper have
no neckerchief).

| role | hat | coat | clothing | face |
|---|---|---|---|---|
| villager | brown felt cap | brown waistcoat, brass buttons | linen shirt, brown breeches, grey stockings | brown hair, beard |
| fishwife | blue-and-white checked scarf | rust knitted shawl, white apron | linen sleeves rolled to the elbow, blue skirt | auburn hair, long |
| net mender | green knitted cap | none | oatmeal smock, rope belt, canvas trousers | grey hair and beard, tanned |
| dockhand | grey bandana | none | white shirt rolled to the shoulder, red neckerchief, canvas slops, red sash | black hair, tanned |
| shipwright | leather cap | leather bib apron dusted with sawdust | blue-grey work shirt, breeches, stockings | ginger hair and beard |
| innkeeper | none (balding) | red waistcoat, long white bar apron | white shirt, brown breeches, white stockings | black moustache |
| lamplighter | grey peaked cap, brass badge | long grey coat with brass buttons and skirts | dark breeches | black hair |
| vendor | straw hat, green band | green-and-white striped apron | green dress, elbow sleeves | dark hair, long |

A hired villager keeps its sheet: `CrewMemberModel` draws the crew member with the sailor's geometry and the role's
texture while its synced `outfit` names a role. No code turns a bone for the villagers: the sleep pose on a bed is the
rig's `sleep` clip with the body held along the bed (`ResidentRoutineGoal.lyingSpot`, on the mattress where the two
halves meet, facing the foot as in a hammock), the dusk `sit` the rig's clip on an invisible `BenchSeat` whose rider's
feet hang 12 px under the stair's top.

**The market square's stalls (LIFE1).** `art/structures/village/street_square_market.py` now holds two stalls under
striped awnings (posts at x 2/5 and 9/12, z 6/9): the provisions stall on the west side (counter at x 5, the vendor at
(4, 1, 7) facing east) and the chandler on the east (counter at x 9, the vendor at (10, 1, 8) facing west), an aisle
x 6..8 from the street to the south building, two benches at (10..11, 1, 12) facing north and a free paved 2×2 spot at
x 3..4, z 11..12 (open to y 3) for the market notice board (NB2). Rebuilt through the lab (`--port 8778`), 275 blocks;
render `art/renders/structures/village/street_square_market.png`. The other pieces kept their blocks: the villagers'
stations, beds and seats are read off their sources (`mob/village/ResidentPosts`).

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
## The Captain's Almanac (ART14)

The tome, its item and the book screen replace the ALM1/ALM3 placeholders (design.md §3.4: "a thick leather-bound tome
with brass corners"). One palette for all three: the GUI kit's leather and brass (below), parchment 196,170,124 /
226,206,162 / 238,224,188 with edge 160,128,84, a red title label 138,30,26.

- **Placed tome** (`textures/entity/captains_almanac_book.png`, 64x32): `AlmanacRenderer` keeps vanilla's
  `BookModel` and its open, close and page-flip animation and only swaps the texture (`AlmanacRenderer.TEXTURE`, a plain
  texture drawn with the model's `entitySolid` render type, not in the block atlas). The texture follows the model's
  box UV layout (`BookModel.createBodyLayer` offsets and sizes, faces from `ModelPart.Cube`: a north face maps u with
  +x, a south face with -x; v runs with +y). Which face is which: the covers have zero depth, and the face turned
  towards the pages is the inside. `left_lid` north u 0..6 = inside of the top cover, south u 6..12 = its outside
  (hinge at u 6); `right_lid` north u 16..22 = inside of the bottom cover, south u 22..28 = outside (hinge at u 28);
  `seam` north u 12..14 = the spine's outside, south u 14..16 = its inside; reading faces `left_pages` south u 7..12
  (spine column u 11) and `right_pages` north u 13..18 (spine column u 13), v 11..19; fore-edges u 6 and u 18, head
  and tail at v 10; the flip page u 24..29 / 29..34, v 10..18. The tome lies closed on its right cover, so the left
  lid's outside is the visible front cover: board edge, a blind-tooled frame, a lighter panel, brass caps on the two
  free corners and the clasp strap with its rivet; the back cover carries the catch. Spine: head and tail caps, two
  raised bands with their shadow, a red label with a gilt dot. Pages: mottled parchment, three faint ruled lines, a
  gutter shade, a deckled fore-edge and page-stack head and tail. Script: `tools/gen_almanac_book_texture.py`.
- **Item** (`models/item/captains_almanac.json`, 15 elements, source `art/models/captains_almanac.bbmodel`, texture
  `textures/item/captains_almanac_tome.png` 32x32 from the same script, one texel per model pixel, so UV = texel / 2;
  also the placed block's breaking particle). A closed tome lying flat, x 2..14 (spine side at low x), y 0..4,
  z 1..15: two 0.75 px leather boards, a page block inset 0.5 px (x 2.6..13.5, y 0.75..3.25, z 1.5..14.5, fore-edge
  and head/tail page stripes), a rounded spine 1.4 px deep (x 1.2..2.6, 0.05 px inside the boards' planes) with three
  raised bands (z 4.5, 8, 11.5), four brass corner caps wrapping the free corners of both boards (1.8 x 0.95 x 1.8 px,
  0.1 to 0.3 px proud), and a clasp: a strap over the fore-edge, a plate and knob on the front board, a catch under
  the back board. The texture's regions are listed in the script's docstring: the cover art 12x14 (tooled double
  frame, gilt lozenge; front and back), the spine 14x4 (label, gilt dots, blind lines), the page edges, and 4x4 colour
  patches used inset like the palette (`[u+0.5, v+0.5, u+3.5, v+3.5]` in texels). Hidden faces are left out;
  `lint_models.py`: 0 visible fights. `AlmanacModule` marks the item `handMadeItem` and points the block's particle at
  the new texture; the placeholder sprite `textures/item/captains_almanac.png` and `tools/gen_almanac_sprite.py` are
  gone.
- **Item display** (hands replaced by ART16 below): `gui` `[30, 225, 0]` / `[0, 4.1, 0]` / 0.8 (fore-edge with the
  clasp lower left, head lower right, the cover's lozenge and all four caps read; 15.5 of 16 units wide, centred with
  the orthographic display check of F7g, which gives 32 px per unit at 1024 px); third person `[90, 0, 90]` /
  `[0, 2, 1]` / 0.5 (upright in the fist, spine down, cover outwards); first person `[90, -25, 80]` / `[-2, 5, 0]` /
  0.32 (cover towards the player, fore-edge right); `ground` 0.4 raised 2 px; `fixed` `[90, 180, 0]` /
  `[-2.2, 0.3, 0]` / 0.95 (cover out, spine left, centred in the frame); `head` `[0, -2.2, 0]` / 0.9 (not checked).
- **Re-export:** the cubes were placed from a spec list in `risky_eval` (`new Cube({from, to, autouv: 0, faces})`,
  hidden faces `texture: null`), display slots set as `DisplaySlot`s. To change the model, edit the project, then
  `Codecs.java_block.compile({raw: true})`, keep `credit`, `gui_light: front`, textures `0` and `particle` =
  `pirates_n_ships:item/captains_almanac_tome`, round to 4 decimals, write tab-indented; save the project with the
  texture embedded, `path` `""` and `relative_path` to the texture. After changing the texture script, reload the
  texture in the project before saving it.
- **Screen** (`textures/gui/almanac/book.png` 292x180, `bookmark.png` 24x16; `tools/gen_almanac_textures.py`, which
  imports the palette, noise and PNG writer from the tome script): the spread at 1:1 with ALM3's geometry unchanged
  (pages x 9..146 and 146..283, y 5..172; stacks x 6..9, 283..286, y 172..175; no screen constant changed). Leather
  boards with value-noise grain, an outline, a blind-tooled line 2 px in, darker head and tail bands where the spine
  runs, brass corner plates (18 px triangles with a rivet; the pages cover their inner part). Parchment: two layers of
  value noise (26 and 9 px cells) between the three parchment shades, a fine grain, five faint foxing spots per page,
  ruled lines at 16 % of a brown ink one pixel under each text line (`CONTENT_TOP + 10k + 9`, over the text box plus
  2 px; they match plain paragraphs, other elements shift text off them), an 18 px quadratic gutter shade to 80 % at
  the spine with a crease, a slight shade along the other edges; the page stacks in three edge shades. The bookmark is
  a light grey silk ribbon (lightest a third down, so the tint keeps most of the chapter colour), stitched with
  2-on-1-off dashes 2 px inside both long edges, outlined, with a 5 px swallowtail end. `RULE_STRENGTH`, the mottle
  contrast and `GUTTER` are constants at the top of the script.
- **Page-turn fold:** over the mottled parchment the fold's 16 alpha strips gave steps of about 4 grey levels on 8 px
  bands at GUI scale 2 (simulated in Python over the new page); `AlmanacScreen.SHADE_STRIPS` is now 32.
- **Regenerate:** `python3 tools/gen_almanac_book_texture.py` and `python3 tools/gen_almanac_textures.py`;
  `AlmanacTexturesTest` fails while the committed PNGs differ from a fresh run of either script (stored deflate, so
  byte-identical everywhere) and checks the sizes.
- **Renders:** `renders/captains_almanac.png` (the placed tome on a spruce block from the block's three-quarter view:
  closed, open, a page turning; then the item in the GUI slot view). The tome panels are drawn in `risky_eval` with a
  `THREE.WebGLRenderer` from a JS copy of `BookModel`'s parts (the `ModelPart.Cube` face and UV rules above), its
  `setupAnim` and `AlmanacBookPose.pose`, so they show the real texture on the real geometry.
  `renders/captains_almanac_screen.png`: the spread at 3x with three bookmarks tinted dark red, grey and blue
  (composited in Python, the open chapter's 3 px further out).

Almanac in hand and the closed fore-edge (ART16, after the playtest note "in hand it looks way too small. on the thin
cover side it also glitches a bit when closed"):
- **Glitch:** not a z-fight (`lint_models.py` reported 0 before and after) but two open boxes at the clasp. The
  `clasp_plate` (x 11.6..14.5, y 3.9..4.3) had no `down` face and the `clasp_catch` (y -0.3..0.1) no `up` face, on the
  assumption that the cover hides them; both run 0.5 px past the cover's fore-edge (x 14), so from below the plate and
  from above the catch the game culled the inner faces and showed the background through a 0.5 x 2 px slot right at
  the page edges, flickering with view bob. Fix: the plate got a `down` face (the corner caps' underside patch, uv
  `[12.25, 2.25, 13.75, 3.75]`), the catch an `up` face (the caps' top patch, `[14.25, 0.25, 15.75, 1.75]`); no element moved,
  the parts inside the covers stay hidden, lint still 0 / 0 / 0. Lesson: a face may only be left out when another
  element covers it over its whole area, not just most of it.
- **Left hands repeat the right-hand values.** `ItemTransform.apply(leftHand, …)` already negates the y and z
  rotation and the x translation of whatever entry it gets (vanilla source, 1.21.1), so an entry that negates them
  itself is mirrored twice and shows the unmirrored pose in a mirrored place. Blockbench's display mode applies the
  same rule, so `loadFirstLeft()` / `loadThirdLeft()` previews are trustworthy. For a book (a chiral object: cover,
  head and fore-edge fix its handedness) the game's mirror also swaps the spine and the fore-edge; a left entry of
  `R · Rz(180)` in model space keeps the fore-edge forward instead (the third-person left entry below, re-centred
  with its own translation). ART17 audited the other models against this: a negation is a real mirror only when y
  is ±90 (vanilla `item/handheld`, the guns), and the rule now sits with the conventions at the top of this file.
- **Display** (measured in display mode with the slot's camera copied into an offscreen view; first-person screen
  extents as NDC, x right, y up, -1..1):
  - first person, both hands, `[70, -10, -8]` / `[4.5, 6.5, -2]` / 0.85 (was right `[90, -25, 80]` / `[-2, 5, 0]` /
    0.32, left `[90, 25, -80]` / same): the cover faces the player, turned slightly towards the screen centre, head
    up and leaning a little; the right hand shows the spine and its bands on the left, the left hand the clasp on the
    right. Screen extent x 0.26..0.87, y 0.27..-0.86 (old x 0.38..0.80, y 0.16..-0.88, with the cover turned away and
    the fore-edge to the player). Vanilla's `book` with `item/generated` (`[0, -90, 25]` / `[1.13, 3.2, 1.13]` / 0.68,
    its sprite 16 x 14 px) reaches from x 0.43 off the screen's right and bottom edge with its top at y 0.26, so the
    tome's top now matches the vanilla book's and the whole book stays in view.
  - third person right `[0, 0, 90]` / `[-1, 2, 1]` / 0.6, left `[0, 0, -90]` / `[6, 2, 1]` / 0.6 (was `[90, 0, ±90]` /
    `[0, 2, 1]` / 0.5, spine down): gripped at the spine, which sits against the fist, fore-edge and clasp pointing
    forward, the front cover outwards and the back cover against the arm; 14 x 12 px at 0.6 is 8.4 x 7.2 px, the
    vanilla book sprite at 0.55 is 8.8 x 7.7 px.
  - `gui`, `ground`, `fixed`, `head` unchanged.
- Renders: `renders/captains_almanac_fore_edge.png` (the closed tome from the fore-edge side at 2x; the clasp at 8x from
  below and from above, before and after) and `renders/captains_almanac_in_hand.png` (first person before, right and
  left after; third person right and left), drawn by offscreen views in Blockbench and composited on a canvas in
  `risky_eval`. Export as in ART14 (the item JSON is `Codecs.java_block.compile({raw: true})` with the ART14 header, 4
  decimals, tab-indented; the project `Codecs.project.compile({raw: true})` with `path` `""` and the old
  `relative_path`).

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

**Script-drawn GUI sprites outside the kit.** Every GUI sprite is drawn by a script, never by hand. A script other
than `gen_gui_textures.py` owns its own folder under `textures/gui/sprites/` and is listed in that script's
`OTHER_OWNERS` (and `GuiTexturesTest`'s), so the kit neither deletes nor checks it; its own test compares a fresh run
with the committed bytes. So far: `order/` from `tools/gen_order_icons.py` (ART19), the whistle orders' 16×16 icons
for orders without a fitting item (`WhistleOrder.Icon.sprite`, drawn with `blitSprite` on the wheel and the helm
screen's Crew tab; `OrderIconsTest`). `order/anchor` is a stock anchor in dark iron (38,42,50 / 64,70,82 /
104,112,128) with a kit-wood stock and a 1 px light parchment rim (238,224,188 at alpha 230) on every side, so it reads
on the wheel's dark sectors and on brass buttons; `--preview FILE.png` shows it at 8× on both grounds.

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
| `starter_sloop` | `starter_sloop.py` | single-mast sloop, forecastle and stern cabin, 685 blocks | shipwright (400), merchant convoys |
| `starter_sloop_basic` | hand-built `.schem` | the same hull, fewer fittings | shipwright (300), merchant convoys |
| `navy_sloop_armed` | `navy_sloop_armed.py` | the starter sloop with four guns, a shot locker, a crow's nest with ratlines on both sides up to it and the flag on a taffrail ensign staff, 717 blocks | navy patrols (not sold) |
| `pirate_sloop_armed` | `pirate_sloop_armed.py` | the same blocks as `navy_sloop_armed` | pirate raiders, pirate captains' voyages (not sold) |
| `brig` | `brig.py` | two-masted brig (SHIP1, reworked in SHIP2): ballast stones in the bottom, hold three rows deep in three compartments, a lower gun deck with eight guns and four shot lockers, the great cabin aft, weather deck, quarterdeck one row over it, 1233 blocks | shipwright (1250), merchant convoys |
| `navy_brig_armed` | `navy_brig_armed.py` | the brig (her guns and lockers included) with a crow's nest, mast hoops and railing latches on the quarterdeck rails (six ratline nets strung on placement) and two swivel guns, 1244 blocks | navy patrols (not sold) |
| `pirate_brig_armed` | `pirate_brig_armed.py` | the same blocks and nets as `navy_brig_armed` | pirate raiders, pirate captains' voyages (not sold) |

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

**The yard rule on the sloops (TPL3):** both yards stand at z 12, one block forward of the mast (z 13), which runs
unbroken from the keelson to the masthead; the jib's head cleat sits right above the upper yard [4, 17, 12], its clew
cleat right below the lower yard [4, 9, 12] (F5b's clew search passes yard blocks since TPL3), the tack on the bowsprit
one block in from its tip [4, 7, 1], so the jib is 0.5 x 8 x 11 = 44 blocks² (42 before). `starter_sloop.py` places
them; `ArmedSloopLayoutTest.theYardsStandForwardOfTheMastAndTheJibStillSets` checks all three sloops with the pure
sail rules. The lab's previews are in `art/renders/ships/`.

**Crow's nest and ratlines (TPL2):** `armed_spec` also applies `lookout()` (after `armed()`): the **crow's nest**
(`pirates_n_ships:crows_nest`) sits on the mast top at [4, 20, 13] where the flag was, so the flag moves to a two-block
**ensign staff** (flagpole `part=bottom` / `part=top`) on the taffrail rail at [4, 9..10, 27], with open air round its
head for the cloth. A run of **ratlines** on each side of the mast (x 3, port, and x 5, starboard) climbs from the
quarterdeck beside the helm to the masthead: ten **sloped** links (`kind=slope, facing=north`, one up and one toward
the bow each) from [x, 8, 22] on the quarterdeck to [x, 17, 13], the last one right beside the mast, above and abaft
the upper yard's end (TPL3: the yards moved one block forward, so it stands on the link below it), then two links **hung** on the mast's west / east face
(`kind=wall, facing=west|east`) at [x, 18..19, 13] straight above it; at the top a climber steps sideways into the
nest. Why this way (TPL2, before the yards moved forward): a hung run on the mast had to pass the yards and the
cloth in front of it, and the jib's stay and cleats occupy the plane x 4 forward of the mast, so the runs stay at x 3
and 5 and aft. The top sloped link touches the mast
and is climbable, so the climb is continuous: no jump from the slope onto the hung net. The sloped links lie outside the square
sail's cloth envelope (the cloth stands off the yards' plane z 12 by `ClothGeometry.clearance` plus the deepest VIS1b
belly and flutter at the default `sail_visuals`, bellied either way), the hung ones above the upper yard; the yards,
the sail (F5a: only air or mast between the yards' middle blocks) and the jib are untouched. Climbing: walk up the
slope toward the bow into its top link beside the mast, face the mast and push against it (no jump) up the
hung net, step over into the nest. `ArmedSloopLayoutTest` pins the nest, the staff and every link (continuity,
supports, the cloth and jib clearance); `ArmedShipGameTests.aPlayerClimbsTheRatlinesToTheNest` climbs it with a mock
player without jumping on the structure placed on land, and `theRatlinesStandOnTheAssembledSloop` checks every link
stands on the assembled sloop afloat. The starter sloop gets no nest: it is the player's ship to rig.

**The brig (SHIP1).** The human (2026-10-12): "create another ship model, a bit larger with multiple sails, cannons,
the railing, crow's nest, ratlines, etc. so an actual full ship utilising all the possibilities of this mod."
Source `art/schematics/brig.py` (one generator; `navy_brig_armed.py` and `pirate_brig_armed.py` call its
`armed_spec`), built through the lab and converted like the sloop. Bow north, 9 wide, 27 high, 32 long over all (hull
z 4..30, the bowsprit z 0..3, the name boards at z 31); dark oak planks and a stripped dark oak wale and keel for the
hull, spruce decks, masts and yards, the railing family in dark oak, like the sloop. All coordinates below are the
template's (the generator lays her out with a two-row hold and `deepen` then lifts everything from the gun deck floor
up by one row; see the comment there).

- **Why a three-row hold:** laid out first with a two-row hold, she floated with her gun deck floor 0.58 under the
  water (Sable's buoyancy plus the dry hull lift against 546 kpg: waterline 4.55 rows over the keel, measured by
  `BrigGameTests`). Her bottom rows were also widened (layer 1 one block in from the sides instead of two, layer 2
  the full width) and the hold got a third row; she now settles about 0.4 of a row above the template's waterline
  row 4, so the gun deck floor and the ports stand clear.
- **Decks** (template y): keel 0 (z 5..27); bottom 1; **hold** 2..4, cut by two dark oak bulkheads at z 14 and 23 into
  three watertight rooms (fore hold z 5..13, main hold 15..22, aft hold 24..29), each with a spruce trapdoor hatch in
  the gun deck floor and a ladder (on the fore mast, the main mast, the after bulkhead); a bottom plank under every
  hollow cell (SH1b), the forefoot (z 5, y 1) closes the foremost hold cell onto the keel. **Lower gun deck** floor 5,
  headroom 6..7, the hull side pierced at y 6 by four gun ports a side (`dark_oak_bulwark_port`, z 13, 17, 21, 25)
  with planking above them (a lintel only for the drawn barrel at the highest elevations: only bulwarks are in
  `barrel_blocking`). **Weather deck** 8 with the dark oak railing at y 9, a wide port (two ports) a side at z 13..14
  and a gate a side at z 17. **Cabin** y 9..10 aft of z 25 under the raised **quarterdeck** (floor 11), its rails at
  y 12: dark oak railing at the sides and the taffrail, a dark oak rope railing along the break (z 25) with gaps over
  the two stair landings (x 2 and 6).
- **Waterline row 4** (`ShipTemplates.BRIG_WATERLINE`): keel, bottom and hold under water; the auto-ballast keeps her
  level (roll and pitch under 2 degrees, not capped).
- **Getting about:** stairs from the weather deck to the quarterdeck on both sides (x 2 and 6, z 22..24, landing on
  z 25; one block in from the rail, so the deck beside the ratline latches stays free for a climber); two
  **companions**, each a three-block opening in the weather deck (x 4; no rope railing round it: the crew's pathfinder
  takes a rope railing's edge for open floor, and a walker stuck pushing against one) with a stair in the
  deck and two below, descending toward the bow in half steps a crew member walks: forward (opening z 14..16, deck stair
  z 17, onto the gun deck at z 14) and aft (opening z 21..23, deck stair z 24 at the cabin door, onto the gun deck at
  z 21). The second one is there for the pathfinder: from the quarterdeck the crew's pathfinder (32 blocks, 512 nodes)
  ran out of nodes before it found the gun deck's after half by way of the forward companion. Hatches and ladders lead
  into the holds (the pathfinder has no ladders: only players and the pump reach the hold).
- **Rig:** fore mast z 11 (y 1..23), main mast z 19 (y 1..25); three yards each, one block forward of the mast (the
  yard rule): fore y 12, 17, 21 (9, 7, 5 long), main y 12, 18, 23, so two square sails a mast (YardLinker: a middle
  yard is the lower yard of one sail and the upper of the next). A sail winch abaft each mast (z 12, 20). The jib on
  the fore mast: head cleat above the upper fore yard [4, 22, 10], clew cleat below the lower [4, 11, 10], tack on the
  bowsprit [4, 10, 1]: 0.5 x 11 x 9 = 49.5 blocks².
- **Stations and gear:** helm on the quarterdeck [4, 12, 27] (facing south), capstan forward [4, 9, 7] (the anchor),
  bilge pump on the gun deck against the starboard side over the main hold [7, 6, 19] (its intake reaches the hold, `pump_reach` 4), the ship's
  bell on the fore mast's after face over the fore winch [4, 10, 12], the flag on a three-block staff in a flag
  pedestal at the taffrail [4, 12..15, 29], a lion figurehead under the bowsprit, three name boards on the transom
  [3..5, 7, 31], three stern windows in the cabin's stern [2|4|6, 10, 30], side lights [1|7, 10, 29], ship's lanterns on the cabin front
  [3|5, 10, 24], the gun deck sides and in the cabin.
- **Below:** eight hammocks: two fore and aft in the fore berth (x 2 and 6, z 9..10, between the hull and a post),
  two across the gun deck abaft the guns (z 26, hull and post), four across the cabin (z 26 between the cabin wall and
  a post, z 28 between the walls and a post in the middle; rows across the gun deck's waist would close its middle lane
  beside the after companion); the galley at the fore end of the gun deck's waist (pantry [1, 6, 12], water
  barrel [7, 6, 12]); cargo crates and barrels in all three holds; a chart table and a sea chest in the cabin; a brig
  cell at the after end of the gun deck (brig bars across z 27 with a brig door). **No sea cots:** a sea cot is an
  entity block without a block entity, which FIX3's structure check (`check_structures.py`,
  `StructureBlockEntityGameTests`) refuses in every structure template, ship templates included; the cabin has
  hammocks instead.
- **Armed (navy and pirate):** a carriage cannon behind every port (master at x 1 facing west / x 7 facing east, rear
  inboard, the crew's place behind it in the gun deck's middle lane), the lids open; four shot lockers (vanilla
  barrels, `shot_lockers`) against the hull between the guns [1|7, 6, 15|23], each feeding the two guns 2 blocks from
  it; the crow's nest on the main masthead [4, 26, 19]; mast hoops right under it on the mast's sides [3|5, 25, 19];
  railing latches on the weather deck rail abaft the main mast [0|8, 10, 22..24]; two swivel guns on the quarterdeck
  rails [1|7, 13, 28]. **The nets:** a structure template cannot carry a strung net (its cells and fittings name each
  other in block entity data the lab does not write), so the template definition lists them (`ShipTemplate.nets`, new
  in SHIP1: pairs of fitting positions) and `ShipTemplatePlacer` strings them after placing, before assembling, as the
  net item would (`RatlineNets.check`/`string`); a net that cannot be strung is logged and its latch removed. Each side
  is a fan of three strands from the latches to the side's hoop (RL4/RL5: one sheet); the latches stand far enough
  aft (z 22..24, the rings 16 to 17 blocks from the hoop) that the sheet stays clear of the main course and topsail at
  full belly. A climber at the foot holds forward toward the mast, the net carries him to the hoop and he steps over
  the hoop into the nest (RL3's top exit).
- **Ports (STR7):** the brig is the largest shipped hull (`ShipSpots.LARGEST_SHIPPED` 9 × 32, `LARGEST_DRAUGHT` 4,
  set by hand from the templates: worldgen carves from these constants without the datapacks; `ShipSpotGameTests`
  checks every loaded template against them). A new port's berth basin is now 54 × 15 columns (32 long, slid up to 16
  seaward, the 3-block clearance round it; the sloops' was 51 × 15) carved 7 blocks under the surface (draught 4 +
  `berth_depth` 3; 5 before), and only where the sea surface row is not ground, so the beach stays untouched; on the
  test beach the pier now gets two extension sections instead of one. A brig ordered where the water is under 7 deep
  is refused with the deep-water message (`ShipSpotGameTests.aBrigNeedsDeeperWaterThanASloop`).
- **Price:** 725 doubloons, the starter sloop's 400 in proportion to her blocks (1235 against 685); the armed brigs
  have 1266 blocks plus the net cells strung on placement.
- **Tests:** `BrigLayoutTest` (JUnit, the committed structures: only the guns, ports, lockers, nest, fittings and
  swivels differ; every gun supplied and firing through a port; the nest and the flag; the nets climb to the nest and
  keep clear of the cloth; the yards and the four square sails and the jib; the hold closed in three rooms);
  `BrigGameTests` (placing north and east, afloat level and dry, sailing and steering, the anchor, a crew of eight,
  the guns, the lookout, the net climb, a plank from a gate, the hammocks, a reload, and every station, door and hatch
  reachable from the helm on foot); `PlayerSleepGameTests.aHammockOfTheBrigSleeps`.
- **Renders:** `art/renders/ships/brig_side.png` (from port), `brig_bow.png`, `brig_deck.png` (from above),
  `brig_quarter.png` (the starboard quarter) and `brig_cutaway.png` (the port half seen from the centre line: hold,
  bulkheads, gun deck, both companions, cabin), all of the armed brig, drawn by a scratch script from
  `tools/iso_render.py`'s shapes and colours (the railing family is drawn as cubes).

**The brig (SHIP2).** The human's first sail in her (2026-10-13): "missing cannons (at least when ordered) and could
really need a polish in its design, cause it looks very bulky right now … not enough of the ship under water, and …
gets strongly tilted to one side." She came to rest heeled 25 to 30 degrees: her centre of mass stood so high that only
the righting torque held her up, and it ran out before she was upright. Where SHIP2 changed what the SHIP1 notes above
say, this section holds; the generator's docstring and `NOTES` are the short form.

- **Hull:** 9 × 28 × 32. The underwater body is fuller toward the ends (`LOW_HW`) and rounder in the bilge (`BODY`: the
  bottom two in from the sides, the keel row three wide amidships), the stem a log from the forefoot to the
  knighthead with the bowsprit stepped on its head, the run rising into a counter stern. Above the gun deck the
  topsides **tumble home**: the weather deck and everything on it stand one block in from the hull side (x 1..7 in the
  waist, `TOP_HW`), the step outboard covered by dark oak stairs (x 0 and 8, y 8). The wale and the rail rise toward
  bow and stern (`sheer`). The quarterdeck is one row over the weather deck (floor y 9 from z 25, helm [4, 10, 27]),
  the great cabin under it on the gun deck (z 27..29 behind a bulkhead and door at z 26: three hammocks, the chart
  table and the sea chest), the brig cell in the aft hold (bars and a brig door across z 26, y 2..3).
- **Ballast:** 114 cobbled deepslate stones, in the bottom (y 1) inside its outer planking and beside the keel log
  (y 0), mirrored across the centre line (`BrigLayoutTest.theBallastLiesLowAndEven`: every block has its twin at
  8 − x, a few light fittings excepted). Cobbled deepslate because Sable gives it the default mass 1.0 against wood's
  0.5 (`refs/sable/.../physics_block_properties/light.json` for planks, logs, slabs and stairs; `normal.json`).
- **Mass budget** (Sable masses, kpg): wood (planks, logs, stairs: 880 blocks × 0.5) 440, ballast 114, cannons
  (16 blocks × 2.0, `physics_block_properties/cannon.json`) 32, yards (42 × 0.1) 4, the rest (railings, bars,
  hammocks, ladders, fittings) about 170: **758** measured for the orderable brig (`BrigTrimGameTests`: 758.35), 760
  for the armed ones; SHIP1's brig was 546. Centre of mass 4.76 rows over the keel's bottom (on the centre line, 0.36
  forward of z 19), centre of buoyancy 2.94.
- **Afloat (measured, NeoForge, `BrigTrimGameTests`):** 5.02 to 5.04 rows under water amidships, so the surface lies
  at the template's waterline row 4 (`BRIG_WATERLINE`: keel, bottom and hold under water; `ShipSpots.LARGEST_DRAUGHT`
  4 unchanged) and the gun port sills stand 0.98 over it; roll 0.00° and pitch 0.07 to 0.08° at rest (the human saw
  25 to 30° of roll); heeled 12° by hand she is back under 1° in 8 to 10 s (|roll| at most 0.75 to 1.02° over the last
  2 s); on a beam reach in wind 6 under all sail she makes 0.32 blocks/s with a steady heel of 10.0° (the sloop's
  target there is 6°: she is tenderer under sail than the sloop), dry, and stands up within 5 s of furling.
- **Guns on the ordered brig:** eight carriage cannons behind open gun ports (z 13, 17, 21, 25) and four shot lockers,
  as on the armed brigs since SHIP1; the armed brigs now differ from her only by the nest, the ratline fittings and
  the swivels. The ratline latches moved to the quarterdeck's side rails [1|7, 11, 26..28] (the weather deck's rail
  stands over the tumblehome), the swivels to [1|7, 11, 29].
- **Getting about:** the after companion is entered from the quarterdeck (a gap in the break's rope railing at x 4:
  its top stair at z 24 lies right under the break, between the break lanterns); the port hammock across the gun deck
  at z 24 leaves x 5..7 open, because the companion's foot closes x 4 at z 23, so the after guns, the aft hatch and the
  cabin door are reached by x 5.
- **Price:** `ShipOrderMath.templatePrice`: the hull blocks (all but the guns' 16) at the starter sloop's price per
  block (400 / 685) plus each gun at its recipe's cost (eleven iron ingots at the trade price 6 and three wood blocks at
  the hull's rate: 67.75), rounded to 25: **1250** (711 + 542). `BrigLayoutTest.thePriceCountsTheStructuresBlocksAndGuns`
  ties `BRIG_BLOCKS` (1233) to the structure.
- **Built** through the lab (`build_structures.Lab` on an agent's own port, then `schem_to_structure.py`), as the
  sloop; the iso renders are drawn by `tools/iso_render.py`.

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
| `pirates_n_ships:street_out` | `pirates_n_ships:street_in` | `pirates_n_ships:village/streets` | aligned | every street piece that leads on (the dock head's is `streethead_out` since STR3) |
| `pirates_n_ships:street_in` | `pirates_n_ships:street_out` | `minecraft:empty` | aligned | street (north end) |
| `pirates_n_ships:building_out` | `pirates_n_ships:building_in` | `pirates_n_ships:village/buildings` | rollable | street (both verges) |
| `pirates_n_ships:building_in` | `pirates_n_ships:building_out` | `minecraft:empty` | rollable | house, tavern, shipwright (north side, y 0) |
| `pirates_n_ships:pier_out` | `pirates_n_ships:pier_in` | `pirates_n_ships:village/pier` | aligned | dock head (north edge) |
| `pirates_n_ships:pier_in` | `pirates_n_ships:pier_out` | `minecraft:empty` | aligned | pier (south end, deck row) |
| `pirates_n_ships:berth` | `minecraft:empty` | `minecraft:empty` | aligned | pier (berth markers) |
| `pirates_n_ships:pier_end` | `pirates_n_ships:pier_extension_in` | `minecraft:empty` | aligned | `pier_extension` (seaward end, deck row; STR7) |
| `pirates_n_ships:pier_extension_in` | `pirates_n_ships:pier_end` | `minecraft:empty` | aligned | `pier_extension` (south end, deck row; STR7) |

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
deeper, the footings hang in the water. Since STR7 the sea floor round the berths is carved six below the surface and
footings standing on carved ground are carried down to the new floor; extension sections stand two rows deeper (see
"Room for ships at ports (STR7)").

**The seafarer village (`village`):**

| piece | size (x×y×z) | blocks | contents | mod blocks |
|---|---|---|---|---|
| `dock_head` (start) | 11×8×11 | 308 | stone quay (mossy towards the sea, cracked inland), harbor master's hut (desk facing the door, cargo, lectern): render on a cobblestone plinth between spruce corner posts, framed door under a hood, windows with sills (shutters where the box allows), spruce band in the gables, rafter ends; notice board beside the door, two lantern posts at the pier landing, mooring rings on the sea edge, crates and barrels, a bench at the hut's west wall, a barrel with a flower pot by the street; `pier_out` [5, 0, 0], `streethead_out` [2, 0, 10] (`street_out` before STR3) | `harbor_desk`, `notice_board`, `mooring_ring`, `cargo_crate`, `cargo_barrel` |
| `pier` | 7×9×20 | 238 | 5 wide plank deck (a few weathered dark oak boards) on spruce piles with mostly mossy footings, cross beams, stripped spruce wales at the waterline, two lantern posts at the seaward end with the deck open between them (STR7: the rail went, an extension carries on from there), a ladder down; at the landward end (clear of the berths) a lantern post and rail on each edge and cargo (barrels, crates); `pier_in` [3, 5, 19], berths [0, 4, 9] and [6, 4, 9] | `mooring_ring`, `cleat`, `cargo_crate`, `cargo_barrel` |
| `pier_extension` (STR7) | 7×11×12 | 155 | the pier's deck carried 12 blocks on: stringers and planks, piles on cobblestone footings at y 0 (six below the surface, the carved floor), cross beams and wales at the waterline row y 6, deck y 7, two lantern posts and a ladder at the seaward end, a mooring ring and a cleat on each side; `pier_extension_in` [3, 7, 11], `pier_end` [3, 7, 0], berths [0, 6, 6] and [6, 6, 6] | `mooring_ring`, `cleat` |
| `street` | 7×4×7 | 54 | cobbled crown with gravel and moss patches between stone brick kerbs (some mossy or cracked), dirt path verges with coarse patches, a lantern post, a barrel with a flower pot (both clear of the doors at z 3); `street_in` [3, 0, 0], `street_out` [3, 0, 6], `building_out` [0, 0, 3] and [6, 0, 3] | |
| `street_end` (terminator) | 7×4×3 | 29 | cobbled turning place, the kerbs turning along the far edge, a lantern post between two benches, two barrels (one with a flower pot); `street_in` [3, 0, 0] | |
| `house_small` | 7×9×9 | 261 | 7×7 cottage: mossy cobblestone plinth, white render between stripped spruce corner posts, framed door under a hood, windows with sills (outside on the front and back, set into the wall on the gables), shutters on the back, spruce wall plate and gable band, rafter ends under both eaves, render gables with attic lights, brick chimney on the east gable over a furnace hearth, red tile roof; bed, table and stool, chest, barrel with lantern, crafting table with flower pot, bookshelf, rug, a lantern on a tie beam; `building_in` [3, 0, 0] | |
| `tavern` | 11×15×11 | 774 | 11×9, two floors: mossy plinth, render between stripped spruce corner and mid posts and oak door posts, spruce band at the upper floor, tall framed ground floor windows and single upper ones with shutters and sills, a gallery rail on a slab ledge and brackets over the porch with a tall window behind it (a french door before STR6), lanterns either side of the door, rafter ends, boarded gables with lights, brick chimney on the east gable over a hearth; bar with barrels, two tables with stools, a hanging lantern, stairs, two beds upstairs, a crate, rugs, a lantern on a tie beam; a dark oak panel above the door for a sign; `building_in` [5, 0, 0] | `cargo_barrel`, `cargo_crate` |
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
| `pirates_n_ships:path_out` | `pirates_n_ships:path_in` | `pirates_n_ships:pirate_island/paths` | aligned | camp (east and west edges), every trail piece that leads on (STR2) |
| `pirates_n_ships:path_in` | `pirates_n_ships:path_out` | `minecraft:empty` | aligned | every trail piece and trail end (north end) |
| `pirates_n_ships:hut_out` | `pirates_n_ships:hut_in` | `pirates_n_ships:pirate_island/huts` | rollable | the trail pieces' sides (STR2; the camp's south edge before STR2) |
| `pirates_n_ships:hut_in` | `pirates_n_ships:hut_out` | `minecraft:empty` | rollable | every building (north side, y 0), the treasure looks (north side, y 2) |
| `pirates_n_ships:berth` | `minecraft:empty` | `minecraft:empty` | aligned | jetty (berth markers, as the village pier) |
| `pirates_n_ships:treasure` | `minecraft:empty` | `minecraft:empty` | aligned | treasure spot (the buried treasure marker) |

`final_state`: gravel where the trails meet (`jetty_out`, `path_out`, `path_in`), dirt path for the camp's
`hut_out`, coarse dirt for the trail's `hut_out`, sand for every `hut_in`, spruce planks for `jetty_in`, water for the
berths, sand for the treasure. Pools: `pirates_n_ships:pirate_island/start` (the camp), `pirate_island/paths`
(`path`), `pirate_island/huts` (`tent`, `tavern_hut`, `captains_hut`, `treasure_spot`), `pirate_island/jetty`
(`jetty`) and `pirate_island/terminators` (`path_end`, the fallback for `path_out` once the depth runs out). The world
module (WG2) defines them. STR2 adds the pools `trailheads` and `decorations`, many pieces and the connectors
`trailhead_out`, `deco_out` and `deco_in`: "Natural layout (STR2)" below.

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
| `camp_start` (start) | 13×8×13 | 399 | sandy clearing with gravel and dirt trails, a campfire on a cobblestone hearth with driftwood log seats and a roasting spit, a keg, the Jolly Roger, a loot heap (barrels, crates, a chest) under a striped sailcloth lean-to, the fence's lean-to shack (desk as the counter, stock, cobwebs; vertical dark and spruce boards with salvaged patches, palm front posts, sill beam, rafter ends, barred and shuttered windows) with the notice board on its south side under a striped awning, a crude palisade of uneven stakes on the inland sides with taller gate posts (a skull and a torch on the south gate), lantern posts, gravel and mossy skirts; `jetty_out` [6, 0, 0], `path_out` [0, 0, 9] and [12, 0, 9], `trailhead_out` [6, 0, 12] (a `hut_out` before STR2) | `flagpole`, `harbor_desk`, `notice_board`, `cargo_crate`, `cargo_barrel` |
| `jetty` | 5×9×16 | 115 | 3 wide patched plank deck (dark oak, a salvaged jungle board, sagging slabs) on mixed bark and stripped posts (two crooked ones leaning on stair braces) with footings, palm bollards and a rope rail at the seaward end, a lantern post (STR7: the west end post went, so the deck runs on into an extension), a ladder down, a crude crane (palm mast, fence jib on a stair brace, chain hook with a crate) and a rope rail at the landward end, barrels and a lantern post; columns x 0 and x 4 stay clear; `jetty_in` [2, 5, 15], berths [0, 4, 7] and [4, 4, 7] | `cleat`, `mooring_ring`, `cargo_crate`, `cargo_barrel` |
| `path` | 7×4×7 | 36 | rebuilt in STR2 (see below): the 3 wide trail bed between driftwood edges, no sand rectangle; `path_in` [3, 0, 0], `path_out` [3, 0, 6], `hut_out` [0, 0, 3] and [6, 0, 3] | |
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

### Natural layout (STR2)
The human, 2026-10-10: "the pirate island I found was just one long straight row with tents, one port and one
headmaster building … They should look natural in their generation." Cause: the paths pool held one straight piece,
the huts pool four buildings, and the structure had no terrain adaptation (design.md §10.1, "Natural layouts").
STR2 gives the island a branching trail network, twenty buildings, a decoration pool, the beard and a check that
proves it headless; STR3 (village) and STR4 (outpost) reuse the check and the render tooling.

**Sources.** The new pieces are BuildSpec generators like every other piece, built by `tools/build_structures.py`.
Shared helpers: `_camp.py` (trails drawn along a centre line: `line`, `arc`, `band`; `trail` for the 3 wide bed of
gravel, trodden dirt, coarse dirt and sand; `edging` for the driftwood edges, flush stripped jungle logs with gaps and
the odd stake; `patch` for trodden ground that fades into the terrain; `rocks`, `palm`, `crate_stack`, `rope_coil`,
`skull_post`, `hanging_sign`), `_trails.py` (the mirrored bend and fork pairs: jigsaw never mirrors), `_huts.py` (the
7 wide hut with options stilts, porch depth and ridge direction), `_captains.py` (the captain's hut, moved out of
`captains_hut.py` byte-identically, with an `extras` hook for its variants) and `_decos.py` (the ten decorations).
New pieces leave every cell they do not need empty, so the terrain shows through: no sand rectangle under a trail or
a hut. A hanging sign's block entity goes out through `piece.jigsaws` like the flagpole's, with `Id`
`minecraft:hanging_sign` overriding the lab's default (the block's name), so `tools/check_structures.py` passes.

**Connectors** (added to the ST2 vocabulary in `_style.py`):

| name | target | pool | joint | selection priority | used by |
|---|---|---|---|---|---|
| `pirates_n_ships:trailhead_out` | `pirates_n_ships:path_in` | `pirates_n_ships:pirate_island/trailheads` | aligned | 3 | camp (south edge, the inland trail) |
| `pirates_n_ships:deco_out` | `pirates_n_ships:deco_in` | `pirates_n_ships:pirate_island/decorations` | rollable | 1 | long trail, forks, clearing |
| `pirates_n_ships:deco_in` | `pirates_n_ships:deco_out` | `minecraft:empty` | rollable | 0 | every decoration (north edge, y 0) |
| `pirates_n_ships:hut_out` | (as ST2) | | | 2 | every trail piece but the ends |

`buildspec.SELECTION_PRIORITY` holds the priorities: vanilla's jigsaw tries a piece's connectors in descending
selection priority, so a trail piece places its buildings, then its decorations, then the next trail piece, and the
camp its inland trail before anything else. The camp's `hut_out` became the `trailhead_out` (its side connectors stay
`path_out`).

**Pools and weights** (`world/island/IslandData`; weights expand in the pool):

| pool | projection | pieces (weight) |
|---|---|---|
| `start` | rigid | `camp_start` |
| `jetty` | rigid | `jetty` |
| `trailheads` (fallback `terminators`) | terrain matching | `path` 3, `path_long` 2, `path_fork_left` 2, `path_fork_right` 2 |
| `paths` (fallback `terminators`) | terrain matching | `path` 3, `path_long` 2, `path_bend_left` 2, `path_bend_right` 2, `path_fork_left` 2, `path_fork_right` 2, `path_junction` 1, `path_clearing` 2 |
| `terminators` | terrain matching | `path_end` 1, `path_end_rocks` 1, `path_end_palm` 1 |
| `huts` | rigid | `tent` 3, `tent_small` 2, `tent_large` 2, `tent_lean` 2, `hut_stilts` 2, `hut_porch` 2, `hut_thatch` 2, `tavern_hut` 1, `tavern_long` 1, `tavern_deck` 1, `captains_hut` 1, `captains_lookout` 1, `captains_cabin` 1, `lookout_tower` 1, `smokehouse` 2, `powder_store` 1, `longboat` 1, `treasure_spot` 1, `treasure_cairn` 1, `treasure_wreck` 1 |
| `decorations` | rigid | `deco_campfire` 2, `deco_crates` 3, `deco_hammock` 2, `deco_rocks` 3, `deco_fish_rack` 2, `deco_washing_line` 2, `deco_rum_cask` 2, `deco_skull_post` 1, `deco_jolly_roger` 1, `deco_palm` 3 |

Why these numbers: a trail piece of the paths pool leads on to 1.2 trail pieces on average (forks and the junction
two, bends and straights one, the clearing none), so the network keeps growing until the depth (6) or the space
runs out, and straights are 5 of 16 draws. With the ends drawn in the paths pool as well, one camp in ten died out
after two or three pieces; with only the fallback ends, 60 of 60 scanned seeds passed the check (16 to 81 pieces,
median 41; aspect 0.52 to 0.99; 4 to 15 distinct buildings). The inland trailhead never draws an end, a bend or the
junction, so every camp has a trunk that climbs inland; the two side trails along the beach draw from the paths pool.

**Processors.** The trails (`paths`, `trailheads`, `terminators`) run the processor list
`pirates_n_ships:pirate_island_trail`: the connections (WG4) and `pirates_n_ships:boardwalk`
(`world/structure/BoardwalkProcessor`), which lays a trail's bed (sand, gravel, coarse dirt, dirt path, the edging
logs) as spruce planks where its final position is water (it works after vanilla's gravity processor, in
`finalizeProcessing`; a `minecraft:rule` processor sees the block before that move). Everything else runs
`pirates_n_ships:connections` as before.

**Terrain and shore.** The structure has `terrain_adaptation: beard_thin`, so the rigid huts and decorations (sunk
one block onto the surface row, as before) get their ground levelled and the slope round them eased. The camp stays
the beach landing: the shore anchor (WG2) puts the camp's north edge on the last land column and its sand one above
the sea surface, the jetty runs out into the water. There is no `project_start_to_heightmap`: the port structure type
sets the start's height from the sea surface (a heightmap projection would lift the camp and the jetty off the water on
a beach that rises inland). New, `land_pieces_on_land: true` (`world/structure/LandFilter`, run by
`PortStructure.plan`): after the jigsaw, a land piece (a trail, or a building or decoration hanging from one) with at
least three of five footprint samples (centre and corners) over open water is dropped, with everything hanging from
it; the start and the jetty stay. Without it, side trails bent out to sea and carried boardwalks and huts over the
open water. The beard works round the rigid jetty too (it lowers the shore beside it to the jetty's foot and raises a
deep seabed toward it, about three blocks of water at the berths), which is accepted; the village and the outpost
keep `none`.

| piece | size (x×y×z) | blocks | contents and connectors |
|---|---|---|---|
| `path` | 7×4×7 | 36 | short straight: the bed between driftwood edges, a barrel, a lantern stake, spurs to the sides; `path_in` [3, 0, 0], `path_out` [3, 0, 6], `hut_out` [0, 0, 3] and [6, 0, 3] |
| `path_long` | 7×4×11 | 55 | long straight wandering half a block east and back, a rope coil, a crate, a lantern stake; `path_in` [3, 0, 0], `path_out` [3, 0, 10], `hut_out` [0, 0, 3] and [6, 0, 8], `deco_out` [6, 0, 3] and [0, 0, 8] |
| `path_bend_left` / `path_bend_right` | 9×4×9 | 64 / 60 | a quarter bend on a radius of 6 to the east / west, a leaning palm and rocks in the inside corner, a lantern stake; `path_in` [2, 0, 0] / [6, 0, 0], `path_out` [8, 0, 6] east / [0, 0, 6] west, `hut_out` on the outside [0, 0, 4] and [3, 0, 8] / [8, 0, 4] and [5, 0, 8] |
| `path_fork_left` / `path_fork_right` | 9×4×11 | 72 / 70 | the trail runs on south and a branch sweeps off east / west on a radius of 5, crates and a lantern in the crotch; `path_in` [3, 0, 0] / [5, 0, 0], `path_out` south and east / west, `hut_out` and `deco_out` on the other side |
| `path_junction` | 11×5×7 | 74 | a T with rounded corners, a skull signpost and a barrel at its head; `path_in` [5, 0, 0], `path_out` [0, 0, 4] and [10, 0, 4], `hut_out` [5, 0, 6] |
| `path_clearing` | 11×5×11 | 113 | a dead end opening into a ragged round clearing with a cold fire pit, a spit, log seats, rocks and a rum keg; `path_in` [5, 0, 0], `hut_out` [0, 0, 6] and [10, 0, 6], `deco_out` [5, 0, 10] |
| `path_end_rocks` | 7×3×4 | 26 | the trail petering out among rocks behind a fallen driftwood log; `path_in` [3, 0, 0] |
| `path_end_palm` | 7×8×5 | 43 | the trail ending under a leaning palm with rocks, a rope coil, a crate and a lantern stake; `path_in` [3, 0, 0] |
| `tent_small` | 5×5×6 | 60 | a closed two-course pup tent of grey and brown canvas, a bed roll, a sea bag, a lantern; `hut_in` [2, 0, 0] |
| `tent_large` | 9×7×9 | 188 | an open-fronted mess tent of red and white striped sailcloth with a fly on poles, two bed rolls, a table, a chest, a pennant; `hut_in` [4, 0, 0] |
| `tent_lean` | 7×5×6 | 82 | a sail lean-to against a driftwood log wall, a bed roll, a crate, a cold fire and a stool; `hut_in` [2, 0, 0] |
| `hut_stilts` | 7×10×8 | 223 | a one-room hut on palm stilts, steps up to a roped porch, salvage under it, gables on the sides; `hut_in` [3, 0, 0] |
| `hut_porch` | 7×8×8 | 200 | a ground hut with a two-deep roped porch under a lower thatch course, a stool and a barrel, a fieldstone chimney; `hut_in` [3, 0, 0] |
| `hut_thatch` | 7×8×6 | 157 | a ground hut under a steep front-to-back thatch with boarded gables and a king post, a chimney, crates by the side; `hut_in` [3, 0, 0] |
| `tavern_long` | 13×10×10 | 430 | a long drinking hall with an open bar front, a long bar, three tables, kegs, a hanging sign "The Rusty Cutlass" on a palm post and fence arm, barrels outside, a chimney; `hut_in` [6, 0, 0] |
| `tavern_deck` | 11×11×12 | 470 | a grog shack behind a slab terrace with barrel tables and a rum cask, a serving hatch under a striped awning, a hanging sign "The Drunken Kraken", barrels against the side; `hut_in` [5, 0, 0] |
| `captains_lookout` | 13×15×9 | 456 | the captain's hut with a lookout platform on palm legs beside it (ladder, rail, lantern, the black banner on the mast); `hut_in` [4, 0, 0] |
| `captains_cabin` | 9×9×11 | 353 | the captain's cabin in a beached ship's stern on log cradles: curved dark oak hull, stern windows, a quarterdeck with a rail and the banner, the map table with a map tile, the sea chest; `hut_in` [4, 0, 0] |
| `lookout_tower` | 5×15×6 | 148 | a crow's nest platform on four tapering palm legs with braces, a ladder to a trapdoor, a ship's bell, a sunshade; `hut_in` [2, 0, 0] |
| `smokehouse` | 9×8×7 | 164 | a fieldstone and board smokehouse with a smoking flue and a smoker, two drying racks of fish (open trapdoors) and kelp; `hut_in` [2, 0, 0] |
| `powder_store` | 5×5×7 | 128 | a squat mossy cobblestone magazine with an iron door, powder kegs, a moss roof, a warning skull, a cauldron of sand; `hut_in` [2, 0, 0] |
| `longboat` | 8×5×12 | 123 | a beached longboat (keel and bottom sunk in the sand, thwarts, oars), nets drying on driftwood poles, rope, a fish basket; `hut_in` [3, 0, 0] |
| `treasure_cairn` | 5×7×5 | 89 | buried treasure under a ring of stones beside a tall cairn with a skull and a broken shovel; surface row y 2, the treasure marker [2, 0, 2]; `hut_in` [2, 2, 0] |
| `treasure_wreck` | 7×6×7 | 169 | buried treasure among the bleached ribs of an old boat, a skeleton, a skull, a broken barrel; surface row y 2, the treasure marker [3, 0, 3]; `hut_in` [3, 2, 0] |
| `deco_*` | 3×4×3 to 7×9×5 | 9 to 43 | `campfire` (a lit fire ring with seats), `crates`, `hammock` (a hammock between two leaning palms), `rocks`, `fish_rack`, `washing_line`, `rum_cask`, `skull_post`, `jolly_roger` (a flagpole), `palm`; `deco_in` in the middle of the north edge, y 0 |

The captain's post (`CaptainPosts.HUT_POST`, (4, 3, 5) on the floor at y 2, the door at (4, 3, 3)) is the same in the
two new captain's huts, and `CaptainPosts.CAPTAINS_HUTS` lists all three: the island's first captain's hut of any kind
holds the captain; only a camp without one puts him at the camp's post.

**The layout check.** `world/structure/StructureLayoutGameTests` (STR3 and STR4 add their structures there):

- `pirateIslandLayoutsAreNatural` plans the island through the real jigsaw assembler (`PortStructure.plan`) for six
  seeds (11, 2024, 4242, 31337, 65536, 99991) at a fixed site on a straight shore (sea to the north), with the trails'
  height from a flat generator at the sand's surface (the jigsaw's collision test is three-dimensional; trails at the
  superflat height would pass under the camp). Per instance: at least 10 pieces, a footprint aspect (shorter side of
  the bounding box over the longer) of at least 0.5, at least 4 distinct buildings of the huts pool, the jetty's far
  end over the water; no two instances with the same piece sequence. Each instance goes to `layout-<seed>.txt`
  (`world/structure/StructureLayout`: the header, then one line per piece with its box).
- `pirateIslandPlaced1..3` place the seeds 11, 2024 and 4242 for real on a 160 by 160 beach (the barrier ceiling
  taken away, or vanilla's gravity processor would lay the trails on it): the trails lie on the sand, the jetty's far
  end stands in water, the port registers with its two berths, and the region (the structure's box and 8 blocks
  round it) is written as a Sponge schematic `pirate_island_<seed>.schem` (`world/structure/RegionSchematic`).

The files go to the folder in the system property `pirates_n_ships.layout.dir`, default `structure-layouts` in the
GameTest run directory (`neoforge/build/gametest/structure-layouts`, `fabric/build/gametest/structure-layouts`); both
GameTest runs forward the Gradle property `-Ppirates_n_ships.layout.dir=<dir>` to it (a relative path is taken from
the run directory, so give an absolute one, e.g. a scratch folder outside the repository). Both
loaders give byte-identical dumps, the layout files and the region schematics alike (`cmp` on all 27 files of
the island's, the village's and the outpost's tests, STR5). The schematics once differed: `RegionSchematic` wrote the region's
absolute world corner as the Sponge `Offset`, and the GameTest framework lays the arenas out at different world
positions on NeoForge and Fabric (and in scoped and full runs); palette, block data and every block were already equal.
`Offset` is now the corner relative to the test's origin (`helper.absolutePos(BlockPos.ZERO)`), so a dump depends on
the placed blocks alone (`RegionSchematicTest`). A seed's layout depends on the site's chunk as well, so the seeds are planned
at a fixed site and moved into the arena; neighbouring seeds can share a layout (`PortStructure.plan` takes the first
of `seed`, `seed + 1`, ... that gives the shore's rotation), so the seeds lie far apart.

`tools/structure_check.py [DIR | layout-*.txt] [--map]` reads the dumps, prints one row per instance (pieces, size,
aspect, buildings, the trail pieces) and, with `--map`, a top-down sketch of the piece boxes, and fails on the same
thresholds (`--min-pieces`, `--min-aspect`, `--min-variants`).

**Renders for the human.** `art/renders/structures/pirate_island_<seed>_top.png` and `_iso.png` for the seeds 11, 2024
and 4242: the placed instances of the GameTest on its flat beach (no real terrain, no beard; the playtest covers
those). To regenerate them:

```sh
./gradlew :neoforge:runGameTestServer -Ppirates_n_ships.gametest.only=StructureLayoutGameTests \
    -Ppirates_n_ships.layout.dir=/tmp/layouts
python3 tools/structure_check.py /tmp/layouts --map
python3 tools/render_generated_structure.py /tmp/layouts/pirate_island_{11,2024,4242}.schem --out art/renders/structures
```

Without `-Ppirates_n_ships.layout.dir` the files stay in `neoforge/build/gametest/structure-layouts`, the folder
`tools/structure_check.py` reads by default.

**A real world.** `art/renders/structures/pirate_island_smoke_top.png` and `_iso.png` show the island the NeoForge
dedicated-server smoke test generates (seed 2718281828, port `pirate_island_-3012_4328`: a narrow sandy spit with the
jetty running west into the sea, real terrain, the beard and the land filter at work). To regenerate:

```sh
tools/server_smoke.sh neoforge
python3 tools/world_region.py neoforge/build/server-smoke/world -3088 4256 -2960 4384 --y0 45 --y1 95 \
    --out /tmp/pirate_island_smoke.schem
python3 tools/render_generated_structure.py /tmp/pirate_island_smoke.schem --out art/renders/structures
```

`tools/world_region.py` cuts a box out of a saved world's Anvil region files as a Sponge schematic (standard library;
chunks not generated up to features come out as air). The first smoke render showed the beard raising a sand bar the
length of the jetty: vanilla's jigsaw gives a rigid child of a rigid parent its parent's ground level (the jetty's
came out one above the sea), so `PortStructure.plan` now copies every rigid piece hanging rigidly from the start
(jetty, pier, quay) with its ground level a thousand blocks down, out of the beard's reach
(`PortStructure.unbearded`; `StructureLayoutGameTests.pirateIslandBeardSparesTheJetty` feeds the island's pieces to
vanilla's beardifier and fails without it). The camp's own beard still widens the beach a few blocks in front of the
landing.

`tools/render_generated_structure.py` draws `_top` (every column's top block from above, lit from the north-west,
water darkened with depth, 8 px per block) and `_iso` (the south-east isometric view with `tools/iso_render.render`,
shapes and rough Minecraft colours, only exposed blocks). `--iso lab --port N` posts the region to the
minecraft-schematic-lab as a build and saves its preview instead; the lab draws whole cubes in its own colour table,
which paints most of a camp's stairs, slabs and wool blue, so it is only a cross-check.

### Natural layout of the village (STR3)
The human's report on the island (design.md §10.1, "Natural layouts") held for the village as well: one straight
`street` piece, three buildings, no terrain adaptation, so every village was the dock head, a pier and one street running
inland with the same three houses. STR3 gives the village a street network, fourteen buildings, a decoration pool, the
beard and the headless check, with STR2's tooling (`StructureLayout`, `RegionSchematic`, `tools/structure_check.py`,
`tools/render_generated_structure.py`, `tools/world_region.py`).

**Sources.** Every new piece is a BuildSpec generator in `art/structures/village/`, built by
`tools/build_structures.py` (STR3 used the lab on port 8771). Shared helpers: `_streets.py` (streets drawn along a
centre line: each cell takes its kind from its distance to the nearest centre line, crown within 1.3, kerb within 2.3,
verge within 3.3, so straights, bends, forks, the crossroads and the squares share ST1's 7 wide cross-section and the
kerbs round the corners where streets meet; `lay`, `footpath`, `green`, `square_paving`; the mirrored `bend` and `fork`
builders, since jigsaw never mirrors), `_houses.py` (`cottage`: ST1's small house generalised, options `storeys`, roof,
gable infill, door column, chimney side; `furnish`), `_decos.py` (the ten decorations) and new fittings in `_style.py`
(`jit`, `tree`, `bench`, `flower`, `awning`, `hanging_sign`, `ground_patch`). The crown is cobblestone with mossy
cobblestone and andesite, never gravel, so a street the boardwalk processor lays over an inlet stays a cobbled
causeway (only its dirt path and coarse dirt verges become planks). Hanging signs go out as `block_entity` operations
with `Id` `minecraft:hanging_sign`, as on the island, so `tools/check_structures.py` passes. The dock head and the pier
keep their blocks; only the dock head's inland jigsaw changed (below).

**Connectors** (added to `buildspec.CONNECTORS` by `village/_style.py`):

| name | target | pool | joint | selection priority | used by |
|---|---|---|---|---|---|
| `pirates_n_ships:streethead_out` | `pirates_n_ships:street_in` | `pirates_n_ships:village/streetheads` | aligned | 3 | dock head (south edge, was `street_out`) |
| `pirates_n_ships:street_out` | (as ST1) | | | 2 | every street piece that leads on |
| `pirates_n_ships:building_out` | (as ST1) | | | 1 | street pieces' verges, squares |
| `pirates_n_ships:deco_out` | `pirates_n_ships:deco_in` | `pirates_n_ships:village/decorations` | rollable | 0 | long street, forks, squares |
| `pirates_n_ships:deco_in` | `pirates_n_ships:deco_out` | `minecraft:empty` | rollable | 0 | every decoration (north edge, y 0) |

The village places its next street piece **before** its buildings (the island does the opposite): with buildings
first, a building claimed the corner a bend, a fork or a square needs, only the narrow straight still fitted, and the
streets ran on in a line (37 % straights against 31 % by weight, one seed in sixteen with an aspect under 0.5). Placed
after the streets, a building that does not fit gives way to a smaller one of the pool.

**Pools and weights** (`world/village/VillageData`; weights expand in the pool):

| pool | projection, processors | pieces (weight) |
|---|---|---|
| `start` | rigid | `dock_head` |
| `pier` | rigid | `pier` |
| `streetheads` (fallback `terminators`) | terrain matching, `village_street` | `street_fork_left` 2, `street_fork_right` 2, `street_crossroads` 1 |
| `streets` (fallback `terminators`) | terrain matching, `village_street` | `street` 3, `street_long` 2, `street_bend_left` 2, `street_bend_right` 2, `street_fork_left` 2, `street_fork_right` 2, `street_crossroads` 1, `street_square_well` 1, `street_square_market` 1 |
| `terminators` | terrain matching, `village_street` | `street_end` 1, `street_end_green` 1, `street_end_loop` 1 |
| `buildings` | rigid | `house_small` 6, `house_tall` 4, `house_garden` 4, `house_fisher` 4, `tavern` 2, `inn` 2, `shipwright` 2, `slipway` 2, `warehouse` 2, `net_loft` 2, `fish_market` 2, `chapel` 2, `lighthouse` 1, `well` 1 |
| `decorations` | rigid | `deco_cart` 2, `deco_barrels` 3, `deco_nets` 2, `deco_rowboat` 1, `deco_flowers` 3, `deco_bench_tree` 2, `deco_lantern` 1, `deco_fish_rack` 2, `deco_woodpile` 2, `deco_laundry` 2 |

Why these numbers: straights are 5 of 16 draws of the streets pool and a street piece leads on to 1.3 street pieces
on average (forks two, the crossroads three, the market square none). The dock head has one way inland, so the
streetheads pool holds only pieces that branch at once and still lead on inland: with the straights and the well square
there as well, every village of a scan of 80 seeds that ran inland in a line had started without a branch. A small
piece that fits anywhere is drawn more often than its weight says (the jigsaw tries the shuffled pool until a piece
fits), so the well and the beacon tower have half the weight of the rest. `max_distance_from_center` went from 80 to
64: with the side streets a village spread up to 153 blocks along the beach and 70 inland.

**Terrain and shore.** `terrain_adaptation: beard_thin`, `land_pieces_on_land: true` (`LandFilter`, as on the island)
and the new `unbearded_start: true` (`PortStructure`): the dock head (the start) is copied out of the beard's reach like
the pier (STR2's `PortStructure.unbearded`), and both copies drop their junctions to rigid pieces, because vanilla's
beardifier also lays ground round every junction and the one between the dock head and the pier sits on the quay's sea
edge. The dock head stays the shore anchor (WG1): its quay edge on the last land column, its paving one above the sea,
the village climbing inland from it. The streets' processor list `pirates_n_ships:village_street` runs the connections
and the boardwalk. A building on the beach beside the dock head may still beard the shore in front of the quay: in a
scan of 100 seeds the beard over the pier's landward half reached 0.15 one below the sea surface, over its seaward half
(the berths and the far end) at most 0.001.

| piece | size (x×y×z) | blocks | contents and connectors |
|---|---|---|---|
| `street_long` | 7×4×13 | 97 | the long straight, a lantern post, a barrel with flowers, a bench; `street_in` [3, 0, 0], `street_out` [3, 0, 12], `building_out` [0, 0, 3] and [6, 0, 9], `deco_out` [6, 0, 3] and [0, 0, 9] |
| `street_bend_left` / `_right` | 11×7×11 | 142 / 139 | a quarter bend on a radius of 7 to the east / west, a green with an oak and a bench in the inside corner, a trodden forecourt on the outside; `street_in` [3, 0, 0] / [7, 0, 0], `street_out` [10, 0, 7] east / [0, 0, 7] west, `building_out` on the outer side face at z 6 and [5, 0, 10] / [5, 0, 10] south |
| `street_fork_left` / `_right` | 11×7×13 | 186 / 182 | on south and a branch sweeping east / west on a radius of 7, a green with an oak inside the branch, a lantern in the crotch; `street_in` [3, 0, 0] / [7, 0, 0], `street_out` south and east / west at z 9, `building_out` z 4 and `deco_out` z 10 on the other side |
| `street_crossroads` | 11×4×11 | 123 | four ways with rounded kerbs, lantern posts, a barrel and a bench in the corners; `street_in` [5, 0, 0], `street_out` west, east, south |
| `street_square_well` | 15×8×15 | 230 | a paved square (stone brick border, cobblestone, andesite and stone brick flags) round a roofed well, benches, lantern posts at the corners; `street_in` [7, 0, 0], `street_out` [7, 0, 14], `building_out` [0, 0, 8] and [14, 0, 8], `deco_out` [0, 0, 12] and [14, 0, 4] |
| `street_square_market` | 15×7×15 | 251 | a paved dead end round a market stall under a striped awning (produce, ice, a cask); `street_in` [7, 0, 0], `building_out` west, east and south, `deco_out` [0, 0, 12] and [14, 0, 12] |
| `street_end_green` | 7×7×6 | 88 | the cobbles stop at a kerb before a little green with a young oak, flowers, two benches and a lantern; `street_in` [3, 0, 0] |
| `street_end_loop` | 11×5×11 | 103 | a cobbled turning loop round a planted island with a lantern post; `street_in` [5, 0, 0] |
| `house_tall` | 7×14×9 | 407 | two storeys (kitchen below, a ladder to two beds above), boarded gables, flower pots on the upper sills; `building_in` [3, 0, 0] |
| `house_garden` | 9×9×14 | 356 | a 7×6 cottage and a fenced kitchen garden behind it (farmland rows round a water channel, a scarecrow, a composter), a side path to the gate; `building_in` [3, 0, 0] |
| `house_fisher` | 11×9×8 | 306 | a cottage under a dark shingle roof with a smoker; in the yard a rowing boat keel-up on trestles, a net drying on a rope frame, a lobster pot, rope and a tar pot; `building_in` [2, 0, 0] |
| `inn` | 9×14×9 | 504 | "The Salty Anchor": two storeys, a hanging sign on a bracket, a bar of barrels and kegs, tables, beds upstairs; `building_in` [4, 0, 0] |
| `slipway` | 11×10×15 | 321 | the second shipwright: a gravel slipway with a hull frame under construction (keel, stem and stern posts, flaring frames, the first strakes), staging with a ladder, sheer legs with a hoist, a steam box, timber; `building_in` [5, 0, 0] |
| `warehouse` | 11×15×10 | 614 | gable-end to the street: a cobblestone ground storey with quoins and barred windows, a boarded upper storey, a loading way, a hoist beam with a crate, stacked cargo inside; `building_in` [5, 0, 0] |
| `net_loft` | 7×13×8 | 301 | a boarded loft on spruce posts, a ladder to a landing before the door, nets drying underneath; `building_in` [3, 0, 0] |
| `fish_market` | 9×6×7 | 155 | an open stall under a striped awning, the catch on ice on a barrel counter, a hanging sign "Fresh Fish"; `building_in` [4, 0, 0] |
| `lighthouse` | 7×21×7 | 420 | a beacon tower of render banded with brick between stone brick quoins, a ladder to a gallery with a rail, a glazed lantern room with glowstone and a sea lantern, a brick pyramid roof; `building_in` [3, 0, 0] |
| `chapel` | 9×17×11 | 478 | a whitewashed chapel gable-end to the street, tall windows, a steep tile roof, an open belfry with a bell, pews, an altar with candles; `building_in` [4, 0, 0] |
| `well` | 7×7×7 | 79 | a roofed well on a small cobbled yard with a bench, a trough, a lantern post and a flower bed; `building_in` [3, 0, 0] |
| `deco_*` | 3×5×3 to 7×8×8 | 8 to 51 | `cart`, `barrels`, `nets`, `rowboat` (on trestles), `flowers` (a raised bed), `bench_tree`, `lantern`, `fish_rack`, `woodpile`, `laundry`; `deco_in` in the middle of the north edge, y 0 |

Renders: `art/renders/structures/village/*.png` (the lab's previews).

**The layout check.** `world/village/VillageLayoutGameTests` (its own class, STR2's pattern):

- `villageLayoutsAreNatural` plans the village through the real jigsaw assembler (`PortStructure.plan`) for six seeds
  (7, 1234, 5150, 27182, 77777, 314159) at a fixed site on a straight shore (sea to the north), the streets' height from
  a flat generator at the sand's surface. Per instance: at least 10 pieces, a footprint aspect of at least 0.5, at
  least 4 distinct buildings of the buildings pool, the pier's far end over the water, the dock head the start; no two
  instances with the same piece sequence. Each instance goes to `village-layout-<seed>.txt`.
- `villageBeardSparesTheBerths` feeds each seed's pieces to vanilla's beardifier: under 0.01 over the pier's seaward
  half (the far end and the berth row, four below the sea surface up to it), and the ground beside a building filled.
- `villagePlaced1..3` place the seeds 7, 1234 and 5150 on a 160 by 160 beach: every piece inside the arena, the pier's
  far end in water, every street on the sand, the port with its two berths (water, open above), the harbor master at
  the dock head's desk, and the region written as `seafarer_village_<seed>.schem`.

Layouts of the six seeds: 7: 49 pieces, 97×90, aspect 0.93, 11 buildings; 1234: 34, 67×90, 0.74, 7; 5150: 33, 104×90,
0.87, 9; 27182: 42, 101×77, 0.76, 8; 77777: 45, 95×88, 0.93, 5; 314159: 58, 129×71, 0.55, 7. A scan of 100 other seeds
(1009 i + 17) gave 14 to 72 pieces (median 39), aspects 0.51 to 1.0 and 3 to 11 distinct buildings; one seed (58539,
16 pieces) had 3 buildings: its trunk ran into the market square and its branch turned seaward and was dropped.

To regenerate the renders (`tools/structure_check.py` takes the village's dumps by name, the island's `layout-*` are in
the same folder):

```sh
./gradlew :neoforge:runGameTestServer -Ppirates_n_ships.gametest.only=VillageLayoutGameTests \
    -Ppirates_n_ships.layout.dir=/tmp/layouts
python3 tools/structure_check.py /tmp/layouts/village-layout-*.txt --map
python3 tools/render_generated_structure.py /tmp/layouts/seafarer_village_{7,1234,5150}.schem --out art/renders/structures
```

`art/renders/structures/seafarer_village_<seed>_{top,iso}.png` show those three; the flat beach has no real terrain
and no beard (the playtest covers those).

**A real world.** `art/renders/structures/seafarer_village_smoke_{top,iso}.png` show the village the NeoForge smoke test
generates (seed 2718281828, port `village_1192_1530`: a sandy spit between a jungle and the sea, the pier running south;
the land filter dropped the streets that ran out over the water, so the village is a crossroads with a handful of buildings round it).
To regenerate:

```sh
tools/server_smoke.sh neoforge
python3 tools/world_region.py neoforge/build/server-smoke/world 1136 1472 1235 1583 --y0 45 --y1 100 \
    --out /tmp/seafarer_village_smoke.schem
python3 tools/render_generated_structure.py /tmp/seafarer_village_smoke.schem --out art/renders/structures
```

The tooling needed nothing new: `PortStructure` gained the codec field `unbearded_start` (and its unbearded copies may
drop their rigid junctions), the rest is data and pieces.

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
| `pirates_n_ships:building_out` | `pirates_n_ships:building_in` | `pirates_n_ships:navy_outpost/buildings` | rollable | fort gate [14, 0, 14] south (gravel) until STR4; since STR4 the court's lanes (see "Natural layout of the outpost (STR4)") |
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
| `quay` | 7×9×18 | 224 | a one-block stone brick deck (x 1..5) on stone pillars (PIER1: a cobblestone footing at y 0 and stone bricks above, at x 1 and 5 every four blocks from z 1, plus one behind the ladder) joined by an edge course at the waterline row y 4, open water between the pillars and under the middle, weathered mossy at and below the waterline; andesite kerbs and bands across the deck, mooring rings and cleats along both edges, four bollards (stone brick walls under upside-down stairs), a crane at the seaward end (spruce log post with dark oak braces, fence jib to the deck edge with a hanging lantern and a hoist chain, a crate counterweight, a blue banner), a capstan, chains on the deck, lantern posts, a ladder down to the water, cargo; berths [0, 4, 9] and [6, 4, 9] | `mooring_ring`, `cleat`, `cargo_crate`, `cargo_barrel` |
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

### Natural layout of the outpost (STR4)
The human's words for the island hold for the fort (design.md §10.1, "Natural layouts"): the ST3 outpost was the gate
with one straight wall and a tower each way and one building on the apron, the same every time. STR4 keeps the fort
gate and the quay as they were (the harbor master's office, the officer posts and the berths depend on them) and gives
the outpost a **wall trace** that bends inland round bastions and towers, a **court** of lanes, buildings and
decorations inside it, the beard for the court and STR2's headless check and renders.

**Sources.** `_walls.py` (the wall pieces in their states, the angles and steps, the round tower and the battery),
`_court.py` (the lanes drawn along a centre line like the island's trails, the parade ground, the dead ends),
`_buildings.py` (the new buildings and variants) and `_decos.py` (the decorations); every piece file is a two-line
stub that calls one builder, as on the island. The palette and fittings stay `_style.py`'s (ST4c). Built with
`python3 tools/build_structures.py --port 8772 navy_outpost/<piece>` (any free port; 8765 is the human's lab).

**The wall trace.** A jigsaw only turns a piece, so the trace is a small state machine of pools, each state's pieces
naming the next state's pool in their out connectors (every wall piece attaches by `wall_east_in` / `wall_west_in`):

| state | pool | drawn by | pieces |
|---|---|---|---|
| gate | `walls_gate` | the gate's `gate_east_out` / `gate_west_out` | the shore's straights, bastion, sally port, stair: never an angle, so every run stands at least one piece along the shore |
| shore | `walls` | `wall_*_out` (shore pieces, the flank's steps) | `wall`, `wall_long`, `wall_bastion`, `wall_sally_port`, `wall_stair`, `wall_angle_east` / `_west` (salient angle: turns the run inland) |
| flank head | `walls_flank_head` | the shore angle's `flankhead_*_out` | the flank's straights only, so the flank runs inland before it may turn |
| flank | `walls_flank` | `flank_*_out` | `flank_wall`, `flank_long`, `flank_bastion`, `flank_sally_port`, `flank_stair`, `flank_angle_east` / `_west` (a second salient angle: turns the run back toward the other run), `flank_step_east` / `_west` (a re-entrant angle: steps the trace outward, back to the shore state) |
| end | `terminators` | the flank angle's `end_*_out`, and every wall pool's fallback | `wall_tower`, `round_tower`, `battery` |

So a run turns inland at most twice and never toward the sea; after its second angle a corner tower closes it (a run
that went on toward the other one ran into the court's first lane, which the gate lays before the walls grow, and lost
its tower). The towers' pool falls back to itself: at the depth limit vanilla tries only a pool's fallback, so an angle
there still gets its tower. The shore and flank pieces of a kind are the same build with other out connectors
(`_walls.straight("flank")`). Symmetric pieces carry both runs' pairs (east pair z 2, west pair z 3); the angles and
steps come in mirrored pairs. **Placement priority:** every wall out connector has `placement_priority` 2
(`buildspec.Piece._jigsaw` gained the parameter, default 0), so vanilla's jigsaw lays both runs end to end before it
processes any lane: the court grows into the space the walls leave.

Geometry of the turns (the next piece's section continues the turned stretch): the east run's salient angle leaves
through its south face at x 4 (the next piece, turned clockwise, has its skin two columns east of its in connector, on
the angle's x 6); the west run's at x 3 (turned anticlockwise, the skin three columns west, on x 0). The steps leave
through the north face, the next piece's skin on the step's x 2 (east) or x 6 (west). `StructurePiecesTest
.anglesHandTheirSectionOn` checks them.

| piece | size | blocks | contents |
|---|---|---|---|
| `wall_long` (`flank_long`) | 11×8×7 | 341 | two embrasures with cannons (x 3 and 7) on dark oak decks, three pilasters, a store niche, the hoarding, a ladder, a bench under the hoarding |
| `wall_bastion` (`flank_bastion`) | 11×11×12 | 766 | a 7 wide gun platform projecting outward from the line with two cannons, merlons round it, the fort's skin on its three faces down to footings three rows under the walls' foundation row (a shore bastion stands in the water); the line on the footings behind it; connectors on y 3, rows z 7 and 8 |
| `wall_sally_port` (`flank_sally_port`) | 9×8×7 | 257 | an arched passage through the body with a spruce door in a stone plug, a keystone, a gun over the passage, lanterns at the inner mouth |
| `wall_stair` (`flank_stair`) | 9×8×7 | 286 | a gun, and a two wide stone stair from the walkway down to the court (z 5..6) |
| `wall_angle_east` / `_west` (`flank_angle_*`) | 7×8×7 | 244 | the wall turning round a corner pilaster with a lantern on its merlon, a gun over the outer face, a gravel yard with the ladder in the inner corner |
| `flank_step_east` / `_west` | 9×8×7 | 244 | the line's skin stepping out at a re-entrant corner, the hoarding round the inner corner, a ladder, stores |
| `round_tower` | 7×12×7 | 278 | a rounded drum with stubs of curtain on both sides (the walkway runs on to the landing's doors), a corbel table under a crenellated roof platform with lanterns, a ladder to the roof, a door landward, a banner |
| `battery` | 5×8×7 | 193 | a squat gun platform: one cannon, merlons on the outer sides, shot heaps, a timber powder hut landward |

**The court.** The gate's `lanehead_out` [7, 0, 14] (on the land gate's cobbles; `building_out` [14, 0, 14] before
STR4) draws from `laneheads` (never an end, a bend or a T, so the court always grows inland). Lanes are terrain matching
and leave every cell they do not need empty: a 3 wide bed of gravel, cobbles, mossy cobbles and andesite setts between
polished andesite kerbs with gaps. Their side connectors are `building_out` (selection priority 2) and `deco_out` (1),
so a lane places its buildings, then its decorations, then the next lane. Lane connectors: `lane_in` (north edge) and
`lane_out` (pool `lanes`, fallback `lane_ends`).

| piece | size | blocks | contents and connectors |
|---|---|---|---|
| `lane` | 7×4×7 | 43 | straight; a lantern post, a barrel; `building_out` both sides |
| `lane_long` | 7×4×11 | 64 | wandering half a block aside and back; a lantern, a crate; two `building_out`, two `deco_out` |
| `lane_bend_left` / `_right` | 9×4×9 | 59 / 53 | a quarter bend on a radius of 6, a lantern and a bench in the inside corner; `building_out` on the outside |
| `lane_fork_left` / `_right` | 9×4×11 | 73 / 77 | on south and a branch sweeping off; a building and a decoration on the other side, a lantern and a crate in the crotch |
| `lane_junction` | 11×4×7 | 69 | a T with a signpost lantern and barrels; `building_out` south |
| `parade_ground` | 13×9×13 | 195 | a paved square in an andesite border, the navy ensign on a five-block pole on a stepped pedestal, a well under a slab roof, lantern posts, benches, a musket rack, a banner; the lane on south, `building_out` east and west |
| `lane_end_store` / `_privy` / `_woodpile` | 7×6×6 / 7×5×5 / 7×5×5 | 110 / 42 / 49 | dead ends against the wall: a lean-to store shed, the garrison's privy with a hay bale, a woodpile under a lean-to with a chopping block |

**Buildings** (pool `buildings`, rigid, `building_in` on the north side, y 0; sunk one row onto the surface like every
building on a lane):

| piece | size | blocks | contents | garrison post |
|---|---|---|---|---|
| `barracks_long` | 15×9×9 | 513 | ten bunks, two hearths with chimneys, two mess tables, weapon racks | soldier [10, 1, 3] |
| `barracks_mess` | 11×10×14 | 555 | a mess hall in front (long table, benches, hearth, dresser of barrels) and a bunk room behind a partition; ridge north-south | soldier [3, 1, 4] |
| `brig_large` | 13×8×10 | 523 | the brig's masonry round a guard room and four cells of brig bars with brig doors (closed, unlocked), straw, buckets, barred windows | soldier [6, 1, 3] facing the cells |
| `signal_tower` | 7×14×7 | 300 | a stone shaft, a gallery on dark oak corbels, a signal fire (a lit campfire on a hay bale in an iron cage) | soldier [1, 11, 1] |
| `officers_quarters` | 11×13×9 | 572 | stone below, timber above, a balcony over the door (a tall window behind its rail since STR6), the captain's office (cartography table, lectern, bookshelves), a dining table, two bedrooms | officer [3, 1, 4], soldier [8, 1, 3] |
| `powder_magazine` | 9×8×9 | 310 | thick stone walls sunk into an earth bank to two rows, a turf vault, an iron door, kegs, crates and shot, no flame | soldier [2, 1, 5] |
| `armoury` | 9×10×9 | 341 | stone walls with barred windows under a spruce gable, a forge (blast furnace, anvil, grindstone, smithing table), racks, chests | soldier [6, 1, 3] |
| `gun_battery` | 11×6×9 | 240 | a raised stone platform with two cannons in embrasures facing away from its lane, gabions (composters), shot | soldier [5, 3, 4] |
| `store_house` | 11×10×9 | 399 | a stable-sized timber store with double doors under a hoist beam, a loft, hay, barrels, crates | soldier [2, 1, 3] |
| `chapel` | 9×14×13 | 434 | a stone nave with buttresses and tall windows, a bell gable over the door, pews, an altar with candles | soldier [2, 1, 2] |

A jigsaw piece cannot carve a room below the ground (`schem_to_structure` drops air, the terrain stays in every cell a
template leaves empty), so the powder magazine sinks by its earth bank, not by its floor. The larger brig's cells are
found by their blocks like the ST3 brig's (`law/brig/WorldCells`: brig bars, brig doors, walls); no code knows the
brig by name.

**Decorations** (pool `decorations`, rigid, `deco_in` in the middle of the north edge): `deco_cannon_balls` (a spare
gun and pyramids of shot on a pallet; vanilla's heavy core is the shot), `deco_crates`, `deco_drill_target` (a target
between two straw men), `deco_lantern_post`, `deco_bell_post` (the alarm bell under a little roof), `deco_notice_board`
(an orders board of boards between posts), `deco_water_trough` (water cauldrons), `deco_cart`, `deco_tree` (an oak in a
stone ring, persistent leaves), `deco_benches`; 3×3 to 5×5, 8 to 56 blocks.

**Pools and weights** (`world/outpost/OutpostData`; weights expand in the pool):

| pool | projection | pieces (weight) |
|---|---|---|
| `start` | rigid | `fort_gate` |
| `quay` | rigid | `quay` |
| `walls_gate` (fallback `terminators`) | rigid | `wall` 2, `wall_long` 3, `wall_bastion` 2, `wall_sally_port` 1, `wall_stair` 1 |
| `walls` (fallback `terminators`) | rigid | `wall` 2, `wall_long` 2, `wall_bastion` 2, `wall_sally_port` 1, `wall_stair` 1, `wall_angle_east` 5, `wall_angle_west` 5 |
| `walls_flank_head` (fallback `terminators`) | rigid | `flank_wall` 2, `flank_long` 3, `flank_bastion` 1, `flank_sally_port` 1, `flank_stair` 1 |
| `walls_flank` (fallback `terminators`) | rigid | `flank_wall` 2, `flank_long` 3, `flank_bastion` 1, `flank_sally_port` 1, `flank_stair` 1, `flank_angle_east` 1, `flank_angle_west` 1, `flank_step_east` 1, `flank_step_west` 1 |
| `terminators` (fallback itself) | rigid | `wall_tower` 2, `round_tower` 2, `battery` 1 |
| `laneheads` (fallback `lane_ends`) | terrain matching | `lane` 2, `lane_long` 2, `parade_ground` 3, `lane_fork_left` 1, `lane_fork_right` 1 |
| `lanes` (fallback `lane_ends`) | terrain matching | `lane` 4, `lane_long` 3, `lane_bend_left` 2, `lane_bend_right` 2, `lane_fork_left` 1, `lane_fork_right` 1, `lane_junction` 1, `parade_ground` 1 |
| `lane_ends` | terrain matching | `lane_end_store` 1, `lane_end_privy` 1, `lane_end_woodpile` 1 |
| `buildings` | rigid | `barracks` 2, `brig` 1, `watchtower` 1, `barracks_long` 2, `barracks_mess` 1, `brig_large` 1, `signal_tower` 1, `officers_quarters` 2, `powder_magazine` 1, `armoury` 2, `gun_battery` 1, `store_house` 2, `chapel` 1 |
| `decorations` | rigid | `deco_cannon_balls` 3, `deco_crates` 3, `deco_drill_target` 2, `deco_lantern_post` 2, `deco_bell_post` 1, `deco_notice_board` 1, `deco_water_trough` 2, `deco_cart` 1, `deco_tree` 2, `deco_benches` 2 |

Size 7 (was 5), `max_distance_from_center` 96 (was 64: a run of seven of the longest pieces and its tower,
7 + 7 × 11 + 7 = 91, still fits). Why these weights: per run an angle is 5 of 13 shore draws, so a run turns inland
after two or three shore pieces as a rule; the flank turns or steps only 2 of 11, so it runs well inland and the court
has room. Forks are rare in the lanes: their branches run parallel to the trunk a few blocks off and leave room only for
the watchtower and the signal tower between them (with forks at 4 of 15 the courts filled with those two). The lanes'
dead ends are only the fallback (drawn in the lanes pool as well, they cut the courts short).

**Garrison and patrols.** `world/outpost/OutpostPieces` names the wall pieces (with each one's guard post and the
patrol's spots at the far end of its walkway for either run), the towers and the buildings; `GarrisonPosts.POSTS` gives
every wall piece its guard (tier walls, facing outward: the sea on the shore, the land on the flanks), every tower its
roof or platform guard and every building its guard, the officers' quarters an officer after the gate's three.
`GarrisonPostsTest` reads every committed template and proves each post a free cell (and the one above) on a sturdy
floor. `mob/squad/SquadRoutes` walks every wall piece of `OutpostPieces.WALLS` (the run decided by the side of the gate
the piece's spots lie on) in walking order: from the gate's walkway each time the nearest piece not yet walked, so a
bent run is followed round its angles; `SquadRoutesTest` checks every spot and the two wide walkway of every straight
piece and both bastions.

**Terrain.** `terrain_adaptation: beard_thin` and `land_pieces_on_land: true` (`LandFilter` drops a lane over open
water with what hangs from it). `PortStructure.rigidChain` (new) keeps the whole rigid chain from the gate out of the
beard: every rigid piece hanging rigidly from the start or from another such piece (the quay, every wall, every tower)
is copied `unbearded`, so the walls stay level and the beard neither raises the sea in front of them nor under the
quay; the gate itself and the court's buildings and decorations keep it. (STR2's rule took only the start's direct
children; the island's and the village's chains are one piece long, so nothing changes for them.) The gate stays the
shore anchor (7, 0) at sea level + 1 with the quay running into the water.

**The layout check.** `world/structure/OutpostLayoutGameTests` (its own class, with `StructureLayout`,
`RegionSchematic` and the island test's site and helpers):

- `navyOutpostLayoutsAreNatural` plans the outpost for the seeds 11, 2024, 4242, 31337, 65536 and 99991 at the island
  test's fixed site; per instance at least 12 pieces, an aspect of at least 0.5, at least 4 distinct buildings, both
  wall runs from the gate ending in a tower (followed piece by piece through `PortStructure.landNodes`), the quay's far
  end over the water; no two instances with the same piece sequence. Dumps go to `navy_outpost-layout-<seed>.txt`.
- `navyOutpostBeardSparesTheQuayAndTheWalls` feeds three plans to vanilla's beardifier: the quay, the walls and the
  towers carry the unbearded ground level, the beard adds nothing at the berths and the quay's far end, and fills the
  ground beside a building.
- `navyOutpostPlaced1..3` place the seeds 11, 2024 and 4242 on the island test's 160 by 160 beach: the berths and the
  quay's far end in water, every wall level with the gate (bastions three rows deeper on their footings), the port with
  two berths, the default garrison (6 + 1) at its posts and not in a block; the region goes to `navy_outpost_<seed>.schem`.

Layout numbers of the six seeds (pieces, size, aspect, distinct buildings): in the STR4 hand-back. A scan of 60 more
seeds (not committed): 24 to 54 pieces (median 37), aspect 0.44 to 0.99 (median 0.83), 3 to 8 distinct buildings
(median 5); 53 of 60 meet every threshold (four courts with only three kinds of building, three wide forts with an aspect
of 0.44 to 0.50); every one of the 120 runs ends in a tower, 116 turn inland, 81 carry a bastion; no two the same.

`NavyOutpostGameTests` and `OutpostFixture` (the squads' fixture) keep the 48 block shore at depth 1 and leave the court
out (`OutpostFixture.withoutTheCourt`): the gate, the quay and one wall piece of any kind each way with its tower.

**Renders for the human:** `art/renders/structures/navy_outpost_{11,2024,4242}_{top,iso}.png` (the placed instances on
the flat beach; no real terrain, no beard) and `navy_outpost_smoke_{top,iso}.png` (the NeoForge smoke world). To
regenerate:

```sh
./gradlew :neoforge:runGameTestServer -Ppirates_n_ships.gametest.only=OutpostLayoutGameTests \
    -Ppirates_n_ships.layout.dir=/tmp/layouts
python3 tools/structure_check.py /tmp/layouts --prefix navy_outpost-layout- --min-pieces 12 --map
python3 tools/render_generated_structure.py /tmp/layouts/navy_outpost_{11,2024,4242}.schem --out art/renders/structures
```

The smoke render shows the outpost the NeoForge dedicated-server smoke test generates (seed 2718281828, port
`navy_outpost_-5240_2455`: real terrain, the beard round the court, the shore bastions in the water; the world's saved
entities there hold the garrison of six soldiers and an officer and the harbor master). To regenerate:

```sh
tools/server_smoke.sh neoforge
python3 tools/world_region.py neoforge/build/server-smoke/world -5330 2370 -5166 2534 --y0 45 --y1 95 \
    --out /tmp/navy_outpost_smoke.schem
python3 tools/render_generated_structure.py /tmp/navy_outpost_smoke.schem --out art/renders/structures
```

`tools/structure_check.py` gained `--prefix` (the dumps' file name prefix in a folder; default `layout-`, the island's)
and lists the outpost's lanes and flank pieces among the trail pieces.

### Room for ships at ports (STR7)
The human (2026-10-12): "Enough space for ships after building, don't place on any sand or similar. Piers either
longer, or/and the waters need to be deeper." Three parts, all in `world/structure` and `world/port`:

**Extension sections.** Each group has one extension piece, hung by `PierExtensions` (not by vanilla's jigsaw) from
the seaward end of the pier, jetty or quay: its landward jigsaw `pirates_n_ships:pier_extension_in` goes one block
beyond the end's deck row, in line with the parent's `pier_in`/`jetty_in`/`quay_in`, and a second section hangs from
the first's `pirates_n_ships:pier_end` (both jigsaws pull from `minecraft:empty`, so vanilla never fills them). The
pier, jetty and quay carry no jigsaw of their own there: one more jigsaw block in their templates would change the
jigsaw's random draws and so the layout of every port generated after the update (and the fixed layouts the tests
pin).

| piece | size (x×y×z) | blocks | contents |
|---|---|---|---|
| `village/pier_extension` | 7×11×12 | 160 | the pier's deck on taller piles (footings at y 0 = surface − 6), a ladder at the west of the end, two berths [0, 6, 6] and [6, 6, 6], `pier_extension_in` [3, 7, 11], `pier_end` [3, 7, 0] |
| `pirate_island/jetty_extension` | 5×11×12 | 100 | the jetty's rough deck on crooked posts, a leaning post with a stair brace, palm bollards, a cleat and a ring, a ladder down the west post; berths [0, 6, 6] and [4, 6, 6], `pier_extension_in` [2, 7, 11], `pier_end` [2, 7, 0] |
| `navy_outpost/quay_extension` | 7×11×12 | 500 | the quay's deck on stone pillars (PIER1: footings at y 0, pillars at z 1, 5, 9 and 11, an edge course at the waterline, open water between, andesite kerbs, a chiseled end course), bollards, a ring and a cleat a side, lantern posts and a ladder at the end; berths [0, 6, 6] and [6, 6, 6], `pier_extension_in` [3, 7, 11], `pier_end` [3, 7, 0] |

The deck row is y 7 and the sea surface row y 6, so the deck runs on level with the pier's (y 5 in a 9 high piece) and
the footings reach two rows deeper. To make room at the joint, the pier's seaward rail (two fences) and the jetty's
west end post (a fence and a cobweb) went; the quay is unchanged. The pools are
`village/pier_extension`, `pirate_island/jetty_extension`, `navy_outpost/quay_extension`, named by the structures'
`extension_pool` field.

**When a section is added** (`PortStructure.plan`, after the jigsaw): the column just beyond the end's deck cell is
read from the generation terrain (`getFirstFreeHeight` on `OCEAN_FLOOR_WG`, real blocks in GameTests); if it is sea
and shallower than the depth rule (its first free block above `sea surface − (2 + world.ports.berth_depth)`), a
section is hung there, rigid, unbearded, level with the end, and the check repeats at the new end, at most twice;
a section that would overlap another piece is not placed. `world.ports.pier_extension` switches it off.

**Carved berths.** `PortStructure.afterPlace` deepens the sea floor round every berth (`BerthBasins`): over every
mooring a ship can take there (alongside on the side away from the pier, centred and slid seaward up to 16 blocks)
for the largest shipped hull (9 by 29, draught 2), grown by `world.ports.ship_clearance` (so it reaches under the
deck), every column whose surface row is not ground gets its ground and plants (sand, gravel, stone, kelp, seagrass)
replaced by water from the surface down to 2 + `berth_depth` below it; structure blocks stay, and a footing or mole
that stood on the carved ground is carried down with its own block to the new floor. The beach and anything at or
above the surface stay. The structure's box is grown by the basin's reach (`adjustBoundingBox`) so worldgen places
those chunks too. `world.ports.carve_berths` switches it off. Ports generated before STR7 keep their berths; the
delivery spot search (`ShipSpots`) finds deep water farther out for them.

Renders: `art/renders/structures/{village/pier_extension,pirate_island/jetty_extension,navy_outpost/quay_extension}.png`
(the lab's coarse preview; not yet seen in a client).

**Piles to the floor (PIER1).** The piles of the village pier and its extension, the jetty's posts and the quay's pillars (and
the extension sections') end in a cobblestone or mossy cobblestone footing on the template's lowest row (y 0), which is the
template's own sea floor. Where the real floor lies deeper (the beard and the berths' basins leave the sea deeper than the
template assumes, the human saw footings in mid water with fish below), `world.structure.PileFootings` runs in
`PortStructure.afterPlace` per chunk after the basins are carved: for every footing column over water it copies the block
above the footing (the pile, post or pillar block) straight down through the water to the first solid block, up to 64
blocks, and moves the footing onto the floor; where the floor is the block under the footing nothing changes.
`world.structures.piles_to_floor` switches it off. The quay (and its extension) is no longer a solid mole: pillars every
four blocks along both edges, a plain edge course under the kerbs, mooring posts, bollards and the crane unchanged on the
deck; the village's dock head is on land and has no mole, the village has no separate quay piece. The pieces
are rebuilt through the lab (`tools/build_structures.py navy_outpost/quay navy_outpost/quay_extension`).

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

### Structure polish (STR6)
The human, 2026-10-12: "Remove blocks in front of doors at generated structures (EVERY single structure has that
problem). Replace half slabs at Leuchtturm with full blocks, looks strange and detached. Add loot. navy_post directly
next to mansion, should not happen." (design.md §10.1, the post-0.1.0 list.)

**The door rule.** Both sides of every door must be walkable: the cell one step along the door's `facing` and the
cell one step against it, on the door's two rows, hold nothing with a collision box (no block at all in the template,
air, water, a carpet, a pressure plate, a torch, a plant, a sign or banner, a rail; a cobweb counts as blocking); a
bottom slab on the door's lower row is a step, and then the two rows above it count. A jigsaw there counts as its
`final_state`. `tools/check_structures.py` checks it on every structure piece (`data/.../structure/<group>`, not the
ship templates nor Ponder scenes; a side beyond the template's box is left to the placed check) and refuses a bad
template at conversion like FIX3's block entity rules; `world/structure/DoorAccess` checks the same rule on the placed
blocks in the island, village and outpost layout GameTests (`pirate_islandPlaced*`, `villagePlaced*`,
`navyOutpostPlaced*`), so a neighbour piece or a decoration in front of a door is caught as well. Pieces that failed
and why:

| piece | what stood there | fix |
|---|---|---|
| `village/tavern` | the upper-floor "french door" opened onto the gallery's fence rail: the box leaves one row in front, so the rail stands right before the door | a tall window (two panes) instead of the door |
| `navy_outpost/officers_quarters` | the same: the balcony door opened onto the balcony's rail on the one front row | a tall window instead of the door |
| `navy_outpost/brig_large` | the inner wall's brick ledge at head height (y 2) ran past the first cell's door on both sides | the ledge stops one row short of the cells' front |
| `navy_outpost/lane_end_privy` | a one-deep booth: the back wall stood right behind the door | the booth is two deep (piece 7×5×6, was 7×5×5), the floor with the seat behind the door |
| `village/lighthouse` | the lantern room's door opened onto the glowstone light in its 1×1 middle | the door is a pane; the lantern room is glazed all round |

Every other door passed in its template and in the placed layouts on the flat test beach. The real cause of "every
structure" showed in the REL3 smoke world (seed 2718281828, ten ports cut out with `tools/world_region.py`): the
**terrain**. The converter drops plain air, so a piece clears nothing it does not build, and where a building, a wall's
sally port or a lane end sits on rising ground (a dune, a grassy slope) the beard does not always take the ground away:
grass blocks, sand and coarse dirt stood on the doors' rows (both sides of the outposts' sally ports, a lane end's
store door, an island hut's door). The fix is in the pipeline: `buildspec.Piece.spec` writes `minecraft:cave_air` into
every free cell inside the box on both sides of every door, on the door's two rows (`door_approaches`), and
`tools/schem_to_structure.py` keeps cave air (it still drops `air`, `void_air` and `structure_void`, so no piece carves
the sea). `DoorAccessGameTests` places every port piece with a door into four rows of dirt and checks its doors. A side
beyond the piece's box (a hut whose door is on its box edge) is the neighbour's cell and can still hold terrain.

**The lighthouse.** Its gallery was a 7×7 ring of bottom stone brick slabs on the corbels, which read as a thin plate
floating on the tower; it is a course of full stone bricks now (`lighthouse.py`, render
`art/renders/structures/village/lighthouse.png`).

**Loot chests.** `buildspec.Piece.loot_chest(x, y, z, facing, table)` writes a vanilla chest whose block entity data
is only `{LootTable: "pirates_n_ships:chests/<table>"}` (no seed: the loot is rolled when a player first opens it; the
FIX3 rules accept it, its id is the chest's). Tables (datagen, `world/structure/StructureLoot`, written to
`data/pirates_n_ships/loot_table/chests/<structure>/<kind>.json`):

| table | pieces | contents (rolls) |
|---|---|---|
| `village/house` | `house_small`, `house_tall`, `house_fisher`, `house_garden`, `chapel` (alms chest), `warehouse` (new chest) | doubloons 1-4 at 50 %; 2-4 of bread, salted fish, apples, potatoes, carrots, string, wool, candles, cloth, limes, a book |
| `village/tavern` | `tavern`, `inn` | doubloons 2-6; 2-4 of rum, bread, cooked cod, salt pork, hardtack, tobacco, glass bottles |
| `village/shipwright` | `shipwright` (new chest), `net_loft` | doubloons 1-3 at 30 %; 3-5 of nails, rope, cloth, spruce planks, iron, a carpenter's hammer, a saw, a shipwright's toolkit |
| `navy_outpost/barracks` | `barracks`, `barracks_long`, `barracks_mess`, `brig`, `brig_large`, `officers_quarters`, `fort_gate` (office) | doubloons 1-4 at 50 %; 2-4 of hardtack, lead shot, gunpowder, salt pork, limes, a cutlass, a pistol |
| `navy_outpost/armoury` | `armoury` | 3-5 of lead shot, gunpowder, cannonballs, iron, cutlass, saber, pistol, musket |
| `navy_outpost/store_house` | `store_house` (new chest), `wall_tower`, `round_tower` (landing) | 3-5 of hardtack, salt pork, salted fish, limes, gunpowder, lead shot, rum, rope |
| `pirate_island/hut` | `tent`, `tent_large`, `hut_porch`, `hut_stilts`, `hut_thatch`, `camp_start` (loot heap) | doubloons 1-6 at 60 %; 2-4 of rum, salted fish, cooked cod, tobacco, gold nuggets, lead shot, bones, spices, an emerald |
| `pirate_island/tavern` | `tavern_hut`, `tavern_deck`, `tavern_long` (new chests) | doubloons 2-8; 2-4 of rum, tobacco, salt pork, cooked cod, spices, gold nuggets |
| `pirate_island/captain` | `captains_hut`, `captains_lookout`, `captains_cabin` (a new vanilla chest beside the sea chest, which stays empty) | a blank treasure map; doubloons 6-16; one of cutlass, pistol, saber, rapier; 1-3 of rum, gold, emeralds, lead shot |

Every pool's rolls are `pirates_n_ships:configured_rolls` (`ConfiguredRolls`): the base times the server config's
`world.structures.loot_rolls` (a fraction rounds at random). The processor list `pirates_n_ships:connections` that every
port pool element names now runs `pirates_n_ships:loot_chests` (`LootChestProcessor`) after the connections: while
`world.structures.loot` is off it places these chests without their table (wrecks and buried treasure keep theirs).
The wreck chests and the buried treasure are unchanged.

**Ports apart.** The navy outposts' structure set has a vanilla `exclusion_zone` of 8 chunks against
`pirates_n_ships:seafarer_villages`; the pirate islands' set 6 chunks against the villages (`exclusion_zone`) and the
outposts (`exclusion_zones`, a list `ConfiguredSpreadPlacement` adds). Why 8 and 6: `OutpostData.VILLAGE_EXCLUSION_CHUNKS`,
`IslandData.PORT_EXCLUSION_CHUNKS`.

### Structure polish, round two (STR8)
The human, 2026-10-12, after a first look at generated ports: piers ending on land, streets "too curly" (his
screenshots: the cobbled streets ride every bump, the kerb stepping up and down), doors still blocked (snow-topped
ground before hut doors), "a structure completely on water", ports in ice biomes, an outpost's wall run out over the
sea, a navy post beside a woodland mansion. All rules live in `world/structure`; playtest steps in
`docs/playtests/structure-polish.md` section STR8.

**Straights after turns.** A bend's, a fork's, a junction's and the crossroads' ways on are the new connectors
`street_straight_out` (village), `path_straight_out` (island) and `lane_straight_out` (outpost): same target
(`street_in`, `path_in`, `lane_in`) and selection priority as the plain way on, but they draw from a straights-only pool,
so two turns never follow each other; the straights lead on into the full pool again. The jigsaw count of every piece is
unchanged (`_trails.py`, `path_junction.py`, `_streets.py`, `street_crossroads.py`, `_court.py`).

| pool | pieces (weight) |
|---|---|
| `village/streets` | `street` 4, `street_long` 4, `street_bend_left` 1, `street_bend_right` 1, `street_fork_left` 2, `street_fork_right` 2, `street_crossroads` 1, `street_square_well` 1, `street_square_market` 1 (were 3, 2, 2, 2, 2, 2, 1, 1, 1) |
| `village/streets_straight` (new, fallback `terminators`) | `street` 3, `street_long` 2 |
| `pirate_island/paths` | `path` 4, `path_long` 4, `path_bend_left` 1, `path_bend_right` 1, `path_fork_left` 2, `path_fork_right` 2, `path_junction` 1, `path_clearing` 2 (were 3, 2, 2, 2, 2, 2, 1, 2) |
| `pirate_island/paths_straight` (new, fallback `terminators`) | `path` 3, `path_long` 2 |
| `navy_outpost/lanes` | `lane` 5, `lane_long` 4, `lane_bend_left` 1, `lane_bend_right` 1, `lane_fork_left` 1, `lane_fork_right` 1, `lane_junction` 1, `parade_ground` 1 (were 4, 3, 2, 2, 1, 1, 1, 1) |
| `navy_outpost/lanes_straight` (new, fallback `lane_ends`) | `lane` 3, `lane_long` 2 |

**Level trails.** The curl the human saw was vertical: vanilla lays a terrain-matching piece column by column on the
surface (its gravity processor), so on uneven ground every kerb steps. Since STR8 `PortStructure.plan` gives each
street, trail and lane piece one bed row (`TrailLevels`): the surface under the piece's centre, clamped to its parent's
row ± 1 (the start's or the trail's it hangs from); a piece more than four rows off its ground is dropped with what
hangs from it; a building or decoration on a trail moves so its connector row lies on the trail's row. The trails' new
processor lists (`village_street_level`: connections, trail bed, boardwalk; `pirate_island_trail_level` the same;
`navy_outpost_lane`: connections, loot chests, trail bed) run `pirates_n_ships:trail_bed` (`TrailBedProcessor`): it puts
the gravity processor's blocks back on the planned row and, inside the chunk and within one block of the bed, cuts
natural ground (soil, sand, stone, snow, leaves, plants) above the bed up to six rows and fills air under it (and the
margin's bed row) up to six rows with the ground below (grass on soil, sand on sand); never over water, where the
boardwalk lays boards. The old lists `village_street` and `pirate_island_trail` stay for ports planned before STR8.
`world.structures.level_trails` switches both halves off.

**Piers end in the sea.** After the jigsaw, the pier's, jetty's or quay's run (in line with its landward connector,
from that connector to its seaward end) and four blocks beyond must be sea by the plan's terrain
(`PierExtensions.endsInWater`); otherwise the next side of `ShoreFacing.ranked` (WG1's order) is planned, and with none
left the site is rejected (logged once per site: `STR8: <kind> site at x z rejected`). `world.structures.piers_end_in_water`.

**Land rule.** `LandFilter`: a land piece needs its footprint's centre and at least four of its five samples (centre,
corners one block in) on land, a building (a rigid land piece) also its four outer corners (STR2 dropped only at three
of five wet). A dropped piece drops its children. **Wall runs** (`WallRuns`): a wall of the rigid chain whose far end
(the last cell of its box in line with the connector it hangs from) is over open water is dropped with its children,
and a tower of the pool's fallback (`terminators`) is hung on the same connector, if one fits, so the run still ends in
a tower on land. The pier, jetty and quay and the towers themselves are exempt.

**Door clearing.** `PortStructure.afterPlace` runs `DoorClearing` per chunk, after every piece and the beard: for every
door (read from the pieces' templates), on both sides, the two cells in front on the door's two rows and the row above,
and the cells diagonally beside the first one. Natural ground and plants go anywhere except the template's own cells;
in a trail or a decoration piece whatever blocks a walker goes too; another building's or wall's blocks stay (a door
opening onto another piece's wall is the generator's to fix, STR6's tavern rule). `world.structures.clear_doors`.

**Cold shores.** Biome tags `pirates_n_ships:port_shores` (`#minecraft:is_beach`, named by every `has_structure` tag)
and `pirates_n_ships:cold_shores` (snowy beach, frozen and deep frozen ocean, frozen river, snowy plains and taiga, ice
spikes, the snowy mountains; `#c:is_snowy` and `#c:is_icy` as optional entries). A site is cold when the biome at the
start or 16 blocks out to sea is in `cold_shores`. Each kind has a second structure (`<kind>_cold`, codec fields
`cold_biomes` and `cold: true`) in its own structure set (`<kinds>_cold`) with the same placement as the kind's set
(salt, config name, exclusion zones), so the two share one grid and at most one takes a site; the cold one rolls
`world.structures.<kind>.cold_frequency` (0.1) instead of `frequency`. Stony shores are not added (they stay as before).

**Vanilla neighbours.** Every port set (and its cold twin) keeps 6 chunks from `minecraft:villages` and 10 from
`minecraft:woodland_mansions` (`PortStructureData.vanillaExclusions`): in `exclusion_zone` for the villages, after the
STR6 zones in `exclusion_zones` for the outposts and islands.

Grapple and anchor poses (GR4):
- **Why:** the human (2026-10-12): "Not just vertical grappling hook, adjusts to the orientation it flies and anchors,
  same for anchor." Neither model is GeckoLib (the hook is the vanilla item model `models/item/grappling_hook.json`
  from `grappling_hook.bbmodel`, the anchor the code-built `AnchorRenderer.createLayer` from `anchor.bbmodel`), so no
  bone is set in code and the GeckoLib sign rule does not apply; no pivot moved, neither model changed.
- **The hook** (`combat/grapple/HookPose`): frame at the crown (model pixel y 2), `+Y` up the shank to the eye ring
  (pixel y 14, where the rope ties on, `HookPose.EYE_FROM_CROWN` 0.375 blocks), drawn at `SCALE` 0.5 with
  `ItemDisplayContext.NONE` through `HookPose.modelMatrix` (`R · scale · translate(0, −CROWN_Y, 0)`). Poses: FLYING
  point first along the velocity (`Ry(yRot)·Rx(−(xRot + 90°))`, vanilla's projectile yaw and pitch), HANGING (rail,
  ring, cleat: shank straight down, flukes up over the hold), FLAT (a face whose normal's y > 0.7: on its side along
  the throw, dipped 10°), BITING (point into any other face), FALLING (point down). A latched hook wobbles about its
  crown for 12 ticks (`settleDegrees`, 18° decaying).
- **The anchor** (`sailing/anchor/AnchorPose`): frame at the crown, `+Y` up the shank to the ring (`HEIGHT` 2.0),
  arms along `±X`, stock `+Z`; drawn through `AnchorPose.modelMatrix` (`translate(lift) · R · translate(0, 2, 0) ·
  scale(−1, −1, 1)`, the old transform with `R` for the yaw). Poses: STOWED and HANGING upright, DROPPING the shank
  along the chain (at most 60° off upright), HOLDING the shank along the bed toward the hawse rising 4° (up to 30°
  toward a taut chain), the arms upright rolled 15° so one fluke digs in, lifted 0.1. The renderer eases between poses
  (`AnchorPose.turn`, 4 ticks).
- **Tests:** `HookPoseTest` and `AnchorPoseTest` push model points (the eye ring, the crown, the anchor's ring, bills
  and stock) through the renderer's own `modelMatrix` and check where they land, so the rope and the chain attach at
  the eye and the ring in every pose.
- **Renders:** `renders/grapple_poses.png` (flying along a lob, hanging on a rail, flat on a deck, biting a wall,
  falling) and `renders/anchor_poses.png` (upright, dropping along the chain, holding on the bed): a scratch three.js
  scene in Blockbench's `risky_eval`, no project opened, flat-coloured boxes from the hook's model JSON and the
  anchor's `addBox` calls (parsed from `AnchorRenderer`), posed with a JavaScript copy of the two pose classes,
  written with `fs`.

## The boom and the triangular-sail scene (SAIL5)

**Model `art/models/boom.bbmodel`** (Blockbench, `java_block`, one group per exported model, all at the same
position, textures from the vanilla jar: stripped spruce log and its top as `#log` / `#log_top`, the anvil as `#2`):
- `gooseneck` -> `models/block/boom.json` (5 elements): built for `facing=north`, the mast to the south. An iron strap
  plate on the mast face (4..12 px, z 15..16), a wooden jaw (5.5..10.5, z 11..15, its south face left out: it sits on the
  strap), two iron cheeks above and below the arm (y 5..6 and 10..11, z 6.5..12) and a vertical iron pivot pin through
  the block's centre (7.5..8.5, y 4..12). The block state turns it by the facing; the sheet is not in the model.
- `arm` -> `models/block/boom_arm.json` (1 element): one block of spar, 4 x 4 px (x and y 6..10) along z 0..16, the log's
  grain along z, the log's end on both ends. `sailing.client.BoomRenderer` draws the wood's child of it
  (`block/<wood>_boom_arm`, `WoodModels`) piece by piece from the pivot (the block's centre, between the cheeks) out to
  the sail's foot, the last piece shortened, turned about the vertical to the boom's direction: the arm toward the tack
  turned clockwise seen from above by the sheet. `BoomRenderer.placeArm`/`placePiece` hold the transforms and
  `BoomArmRigTest` composes them for every facing and sheet step (the arm starts at the pivot and ends at the cloth's
  foot corner; a positive sheet turns clockwise; the cloth bellies the way the boom swung). No GeckoLib bone.
- `item` -> `models/block/boom_item.json` (6 elements): the gooseneck with half a block of spar forward.
- Exported with `Codecs.java_block.compile({raw: true})` and the other groups' cubes `export = false`, `parent
  minecraft:block/block`, the particle the stripped log, texture ids with the `minecraft:` namespace. Lint clean
  (`tools/lint_models.py`: no fights, no hidden faces). Datagen writes the wood children (`#log`, `#log_top`, particle
  log), the item models and the block states (facing only).

**Ponder scene `triangular_sails`** (`almanac/ponder/TriangularSailsScene`, about 36 s): the schematic comes from
`art/structures/ponder/triangular_sails.py` like every scene's, built through the lab
(`python3 tools/build_structures.py ponder/triangular_sails --port <yours>`; `.schem`, render and `.nbt` committed). A
9 x 9 x 9 deck, bow north, an oak mast at x 4, z 6; by the yard rule the head cleat [4, 7, 5] and the clew cleat
[4, 2, 5] stand on its north face one block forward of it, the tack cleat [4, 1, 1] on the deck forward (drop 5, the tack
4 forward: 10 blocks of sail). The scene writes the rope ends into both cleats and the head's cloth from the real rule,
then replaces the clew cleat by a boom facing north (a forward, club-footed boom) and steps its `sheet` as the crew
would for each wind (white arrows): port beam 45 to starboard, starboard beam across to port, starboard quarter 60,
the gybe to 60 to starboard on the port quarter, one hand click and a sneak click. Component the cleat; subjects the
rope, the boom and the winch.
