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

# Playtest: the Almanac's book screen (work package ALM3)

Reading the tome or the book in hand now opens the book screen. The layout (wrapping, spill-over pages, the boxes of
illustrations and item icons, lists, headings), the markup, the page-turn state, the spreads and the bookmark hit
areas are covered by JUnit (`PageLayoutTest`, `MarkupTest`, `PageTurnTest`, `BookNavigatorTest`, `BookmarksTest`);
everything you see and hear needs a client. The textures are placeholders (ART14). Please check **both clients**
(`./gradlew :neoforge:runClient` and `./gradlew :fabric:runClient`) and send screenshots and `latest.log`.

Content: the two example chapters (Welcome Aboard, Cannons). For the markup and spill-over checks, a test datapack
helps: copy `data/pirates_n_ships/almanac/chapters/cannons.json` into a world datapack as
`data/test/almanac/chapters/long.json` and set `"order": 5`. Lang keys cannot hold the text itself (no spaces), so
also add a resource pack with `assets/test/lang/en_us.json` holding one long key, e.g.
`"test.long": "**Bold** and _italic_ and {#2a4580}blue{/} text, an icon {item:minecraft:gunpowder} inline, a link to [[cannons]] and [[welcome|the start]].\nA new line. ..."`
(repeat the sentence until it fills three pages), and point a `text` element at `test.long`. `/reload` afterwards.

## Steps
1. **Open from the tome** (sneak-place it, right-click) and **from the hand** (right-click into the air): a leather
   book with brass corners and two parchment pages, centred on screen, the world visible but blurred behind it. The
   tome stays open while the screen is open and closes when you close the screen (Escape), not 5 s after opening.
2. **First open:** it opens at the first chapter's first spread ("Welcome Aboard"); page numbers 1 and 2 at the
   bottom of the pages.
3. **Bookmarks:** one ribbon per chapter on the right edge, top to bottom in book order, in the chapter's
   `bookmark_color` (dark red, dark grey) with the chapter's icon at the tip; the open chapter's ribbon sticks out a
   little further. Hovering one lightens it and shows the chapter title; clicking jumps to the chapter's first page
   with the page-turn animation.
4. **Page corners:** vanilla's arrows at the bottom corners, only where there is a page to turn to; hovering
   highlights and shows "Next page" / "Previous page"; a click turns. At the end of a chapter the next turn opens the
   next chapter on a left page.
5. **Keys and wheel:** Right / Page Down turn forward, Left / Page Up back; the wheel turns (down = forward). Keys
   and clicks during a turn are ignored (no double turns). Escape closes.
6. **Page-turn animation and sound:** turning forward, the right page narrows toward the spine, darkening, then the
   new left page widens from the spine; turning back is the mirror image. About 0.4 s (`almanac_screen.page_turn_ticks`
   8). One page-turn sound per turn. `page_turn_ticks = 0` turns at once (still with the sound);
   `page_turn_sound = false` silences it.
7. **Elements:** headings bold, dark brown, centred; text dark ink without shadow; the cannon chapter's list has
   bullets with hanging indents; the red bold warning is red; the Jolly Roger illustration on Welcome page 2 is drawn
   at its box with its aspect kept, text never runs over it; item icons (gunpowder, cannonball, the almanac) sit
   where placed with their caption centred under them; hovering an icon shows the item tooltip.
8. **Show me** (Cannons, page 3): a parchment button with the label, lighter on hover, a click sound; it does nothing
   else until ALM4.
9. **Long chapter** (the test datapack): the text spills over several pages and spreads; bold, italic, the blue
   colour, the inline gunpowder icon and the links show; the links are navy and underline on hover; clicking
   "Cannons" jumps there.
10. **Last page:** turn to the cannons chapter's second spread, close, reopen: it opens there. Relog (and die once):
    it still opens there. With server config `almanac.remember_page = false` it opens at the start after a relog.
11. **Small window:** shrink the window or use GUI scale 4 on a small window: the book shrinks to fit with its
    bookmarks, nothing is cut off, clicks still hit the corners and bookmarks. `almanac_screen.scale` 1.5 makes it
    bigger when the window has room.
12. **Empty book:** on a server whose chapters failed to load (or before the sync, hard to provoke), the book shows
    one page "The pages are blank ..." and no bookmarks.

# Playtest: the Almanac's real look (work package ART14)

The tome's texture, the item model and the screen's parchment, leather and bookmarks replace the placeholders. The
textures are checked byte for byte against their scripts (`AlmanacTexturesTest`) and the item model by
`HandMadeModelsTest`; the look needs a client. Reference renders: `art/renders/captains_almanac.png` (the tome closed,
open, turning; the item in the slot) and `art/renders/captains_almanac_screen.png`. Please check both clients and send
screenshots.

## Steps
1. **Tome closed on a table:** sneak-place it on a chart table or crafting table. It lies closed: a dark brown leather
   cover with a tooled frame and a lighter panel, brass caps on the two corners away from the spine, a brass strap at
   the middle of the free edge, parchment page edges between the covers, the spine with two raised bands and a red
   label. No missing (purple-black) texture, no see-through or inside-out faces (the parchment pastedown must not show
   on top of the closed book).
2. **Tome open:** right-click it. It opens as before (vanilla's animation); the pages are parchment with three faint
   ruled lines each and a darker column at the spine; the cover edges around the pages are dark leather; the turning
   pages are parchment on both sides. Close it (Escape) and check the closed look again after it settles.
3. **Breaking:** break it: the particles are brown leather with bits of brass and parchment (the item texture).
4. **Item in a slot:** the tome lies three-quarter in the slot, fore-edge with the brass clasp towards the lower left,
   its thickness and all four brass corners visible, the cover's frame and gilt lozenge readable; it fills the slot
   like a block item without touching the slot's edges. Also in the hotbar and the creative tab.
5. **Item in hand:** third person (F5): held upright in the fist, spine down, cover facing outwards, not cutting
   through the arm; left hand mirrored. First person: the cover faces you at the lower right, fore-edge and clasp to the
   right, not clipping the camera. Dropped on the ground: small, lying flat. In an item frame: cover out, spine on the
   left, centred.
6. **Screen:** open it: leather boards with a tooled line and brass corner plates, two parchment pages with a soft warm
   mottling, faint ruled lines, a darker gutter and crease at the spine, page stacks at the sides and bottom. The
   bookmarks: silk ribbons in the chapter colours with stitched edges and a swallowtail end; the open chapter's sticks
   out further; hover lightens it.
7. **Readability:** read a full page at GUI scale 2 and 3: the dark ink text, the dim page numbers, the navy links and
   the red warning stand out clearly on every part of the parchment, also over the darker mottles and near the
   gutter. Report if the ruled lines fight with the text (they sit one pixel under plain lines of text; headings,
   lists and pictures shift text off them; `RULE_STRENGTH` in `tools/gen_almanac_textures.py` makes them fainter).
8. **Page turn:** turn pages slowly (`almanac_screen.page_turn_ticks` 20): the fold darkens smoothly towards the turning
   page's free edge, no visible vertical bands (32 shade strips now).
