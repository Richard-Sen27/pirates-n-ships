# Playtest: the Captain's Almanac, book and placed tome (work package ALM1)

The open rule, the book animation and the pose of the book on the block are covered by JUnit
(`AlmanacOpenRuleTest`, `AlmanacAnimationTest`, `AlmanacBookPoseTest`); placement, waterlogging, the drop, reading
the tome and the book in hand, the recipe and `almanac.enabled` by `AlmanacGameTests`. Nothing has been seen in game:
the look of the book, the animation and the feel need a client. There is no screen yet (ALM3): reading logs
`Captain's Almanac opened (...)` at debug level and the placed tome lies open for `almanac.open_ticks` (100 ticks, 5 s).

The tome is a placeholder: vanilla's book model with the enchanting table's book texture (ART14 brings the real tome).

## Steps
1. **Item:** `/give @s pirates_n_ships:captains_almanac`, or craft a book + 2 leather + 2 copper ingots (shapeless).
   The slot and hand show a brown leather book with copper corners; it stacks to 1; the tooltip says "Use to read;
   sneak-use on a block to set it down". It is in the mod's creative tab.
2. **Reading in hand:** right-click into the air, and right-click while looking at the floor (not sneaking), main hand
   and off hand: the arm swings, nothing is placed, the book stays in hand (with `-Dlog debug` the log shows the
   open line, "in hand").
3. **Placing:** sneak and right-click the top of a table-like block (a chart table, a crafting table) and the floor:
   the tome lies closed, flat on its cover, centred on the block, spine pointing towards you. On the side of a block
   with air below: nothing is placed. Placing it on a slab top, a fence post: it lies there (centre support).
4. **Opening:** walk right up to it: it stays closed (no opening on approach). Right-click it: it opens over half a
   second, the left cover swinging up and over until both covers lie open in a shallow V with the pages up; a page
   turns as it opens, and every second or two 1 to 3 more pages turn, always the same way. After about 5 s it closes
   again over half a second and lies flat and still. Check that no part of the book sinks into the block below while
   opening or closing, and that the book stays inside its outline box.
5. **Second reader:** while open, right-click again (or a second player does): it stays open 5 s from the last click.
6. **Waterlogged:** place it in a water source (sneak-use on the floor of a 1-deep pool): water surrounds it with no
   gap, no water-face flicker through the book; break the block below: the tome pops off and the water stays.
7. **Breaking:** break it by hand: it drops the almanac; the breaking particles are the leather sprite.
8. **On a ship:** place it on the deck of an assembled ship and right-click it: it opens and moves with the ship.
   (Known risk: the "reader left" check uses the vanilla reach check; on a ship it may close after 5 s even while the
   ALM3 screen is open; with ALM1 it closes after 5 s anyway.)
9. **Switched off:** `almanac.enabled = false` in the server config: right-click on the tome does nothing, the book in
   hand does nothing on right-click into the air, a plain right-click on a block places it.
10. **Lighting:** the book is lit by the block light (a torch nearby) and dark at night outdoors, no full-bright book.
