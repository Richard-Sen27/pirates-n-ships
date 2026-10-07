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

Entity models use the Modded Entity format (Mojang mappings 1.17+); paste the body of the exported
`createBodyLayer()` into the renderer's layer method (example: `AnchorRenderer.createLayer`).
