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
  as the wall's centre line, the two rotated bars 0.02 px lower. `minecraft:block/water_still` is greyscale (the game
  tints it per biome through a block colour handler, which we do not register), so the surface uses
  `minecraft:block/blue_ice`. One model per fill level (`water_barrel_fill0..3`, `water_barrel` = full), each with its
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

Entity models use the Modded Entity format (Mojang mappings 1.17+); paste the body of the exported
`createBodyLayer()` into the renderer's layer method (example: `AnchorRenderer.createLayer`).

## Entities

Animated mobs and NPCs (crew member, pirate, sailor, navy soldier and officer; design.md §9) are GeckoLib models
(GeckoLib 4.9.3). Code models in the Modded Entity format stay for static entities such as the anchor. The crew member
(work package M1) defines the **humanoid rig** every humanoid mob follows; its placeholder files are hand-written and
get replaced by Blockbench exports on the same contract.

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
(the default render type cuts out alpha). `tools/gen_entity_textures.py` writes the placeholder sailor
(`crew_member.png`: striped shirt, canvas trousers, shoes, a red bandana on the hat layer).

**Animations** (all loop; names without prefix; rotations in degrees with the vanilla player model's signs: negative x
swings an arm or leg forward, positive x leans `waist` forward):

| Name | When | Placeholder content |
|---|---|---|
| `idle` | standing still | 3 s: slow arm sway (z ±5°), slight chest swell (`body` scale) |
| `walk` | the legs move (GeckoLib's limb swing) | 1 s: arms ±30°, legs ±35°, opposite phase |
| `work` | at its station while the station carries out an order (winch, pump, cannon, …) | 1.2 s: hand-over-hand hauling (arms −50°/−105° alternating), `waist` leaning 6–16°, legs braced |
| `sit` | riding something that seats it (boat, minecart; **not** the station seat, where it stands) | legs −81° x and ±18° y, arms −36° (vanilla riding pose) |

Priority: `work` > `sit` > `walk` > `idle` (`crew/npc/CrewPose`). One controller (`body`) plays them with a 5-tick
blend. A mob with more states adds animations with new names and its own controller logic; triggered one-shots
(attack swings, a cannon fuse) go through GeckoLib triggerable animations on a second controller.

**Variants:** a pirate, sailor, navy soldier or officer is the same geometry with its own texture (a new
`textures/entity/<mob>.png` painted on the skin layout, its own renderer's `GeoModel` returning that texture) and the
shared `crew_member.animation.json`. A variant that needs extra shapes (coat tails, a tricorn, an epaulette) gets its
own `geo/<mob>.geo.json`, copied from the crew member with child bones added under the contract bones, and keeps the
shared animations working because every contract bone is still there.

**Blockbench export** (model batches): create the project with **GeckoLib Animated Model** (the GeckoLib plugin's
`geckolib_model` format), type *Entity*; build on the bones above (to keep names and pivots, open
`geo/crew_member.geo.json` as a Bedrock model and convert it with *File → Convert Project*; menu names unverified);
use box UV on a 64×64 texture. Then
- *File → Export → Export GeckoLib Model* → `geo/<mob>.geo.json` (check that it says `"format_version": "1.12.0"`);
- *Animation → Export Animations* → `animations/<mob>.animation.json` (keep the names above);
- *Texture → Save As* → `textures/entity/<mob>.png`;
- save the project to `art/models/entity/<mob>.bbmodel` and a render to `art/renders/`.
Run `./gradlew build` afterwards: `CrewMemberRigTest` parses the files with GeckoLib's loader and checks bones,
pivots, parents and animation names.
