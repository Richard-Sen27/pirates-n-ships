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

Content: the two example chapters (Welcome Aboard, Cannons); since ALM5 the chapters come from the guide (the
steps below then name the old examples: use The guide book and the Cannons page of Weapons and combat instead). For the markup and spill-over checks, a test datapack
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
8. **Show me** (Cannons, page 3): a parchment button with the label, lighter on hover, a click sound; the click opens
   Ponder's "loading and firing the cannon" scene in place of the book. Ponder's back button (bottom left) or Backspace
   returns to the same book page; Escape closes Ponder and the book together. A placed tome shuts when Ponder opens and
   stays shut on the way back. A chapter whose `show_me` names an unknown scene: the click only clicks, the book stays,
   `latest.log` has `Ponder: no scene for ...`.
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

# Playtest: the Almanac's chapters from the guide (work package ALM5)

The book now holds the player guide: one chapter per section of `docs/guide.md` (0 to 15), generated by
`tools/gen_almanac.py` and `./gradlew :neoforge:runData` (docs/almanac.md, "Generated from the guide"). That every
chapter loads, every lang key exists, every item, link and illustration in the text exists and the book fits its sync
payload is covered by `AlmanacGuideTest` (JUnit) and `AlmanacDataGameTests`; how it reads needs a client. Please check
**both clients** and send screenshots of a few spreads per chapter and `latest.log` (search it for `almanac`: no
"missing texture", no skipped chapter).

## Steps
1. **Bookmarks in order:** 16 ribbons, top to bottom: The guide book (Almanac icon, dark red), Your first ship (helm),
   Ships (hull patch), Sailing (yard, sea blue), Crew (whistle, green), Flags (Jolly Roger, black), Provisions
   (pantry), Cargo and trade (cargo crate, gold), Law, bounties and the brig (shackles, navy blue), Weapons and combat
   (cannon, iron grey), All blocks (capstan), All items (rope), Commands (command block), Configuration (comparator),
   Datapacks (knowledge book), What does not exist yet (barrier). With 16 chapters the ribbons shrink or form a
   second column; every one is clickable and shows its title on hover.
2. **Every chapter opens** on a left page with its title as a bold heading, and each subsection ("### ..." in the
   guide) starts a new page with its own heading. Turn through every chapter once (Sailing is the long one, about 45
   data pages): no raw lang keys (`pirates_n_ships.almanac...`) anywhere, no backslashes or stray `*`, `_` or `{`.
3. **Illustrations** sit under the heading across the page, aspect kept, background transparent on the parchment,
   text flowing below them: the pirate captain (The guide book), the helm (Your first ship), the nameplate (Ships)
   and the bilge pump (Fighting a leak), the yard (Sailing), sail winch, capstan, ratlines, ship's bell (Decor),
   chart; the sailor (Crew), crow's nest, pirate, kraken, shark; the Jolly Roger (Flags); pantry, water barrel, sea
   chest; cargo crate, doubloon, harbor desk; notice board (Law), shackles, brig door; cutlass (Weapons and combat),
   pistol (Firearms), cannon (Cannons), swivel gun, grappling hook; mermaid figurehead (All blocks), captain's hat
   (Hats); rum (All items). Report any that looks cut off, fringed with the render's grey background or too small
   to read.
4. **Show me on the cannon page** (Weapons and combat, Cannons): the button follows the first paragraph; a click
   opens the Ponder scene "loading and firing the cannon"; closing it returns to the book.
5. **Item icons in the text:** the first bold item name of a page carries its icon ("**Helm**" in Your first ship,
   "**Cleats**" under Grappling hook); the All blocks and All items lists show one icon per entry (two for "Pistol,
   Musket"); hovering an icon shows the item tooltip.
6. **Links between chapters:** "see Limits" in Your first ship jumps to Ships; the "See ..." links in All blocks
   and All items jump to their chapters; the Contents page of The guide book (its second page) lists every other
   chapter by title, and each entry jumps there.
7. **The reference chapters** (Commands, Configuration, Datapacks): the tables read as bulleted lists, a bold first
   cell then the description; commands and config keys plain text (no backticks), `|` alternatives intact
   (`hoist|reef|furl`). What does not exist yet is the last chapter.
8. **Last page** still works with the new chapters: read into Sailing, close, relog: it reopens there.

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
9. **In hand (ART16, replaces the hand poses of step 5):** in each hand, first person and third person (F5), the book is about the size of a vanilla book held the same way (first person: low at the side, the cover towards you at a slight angle, fully in view; third person: gripped at the spine, fore-edge forward, cover outwards) and its fore-edge is clean while you walk and look up and down (no see-through gaps at the brass clasp above or below the page edges); see `art/renders/captains_almanac_in_hand.png`.

# Playtest: the player's Almanac in its own voice (work package ALM6)

The Almanac's text now comes from `docs/almanac/` (14 chapters, one file per bookmark) instead of `docs/guide.md`.
JUnit checks the structure (`AlmanacGuideTest`: one chapter per file, every key, icon, link and illustration exists, no
config keys, commands or mod ids outside the ledger, about 36 KB on the wire) and `AlmanacDataGameTests` the loading.
What a client must show: the voice, the layout of the longer pages, and that nothing reads like a project readme.

Chapters in order: Welcome aboard, Your first ship, Ships, Sailing, Crew, Flags, Provisions, Cargo and trade, Law,
Weapons and combat, Charts and navigation, The ship's inventory, The Quartermaster's ledger, Not yet charted.

## Steps
1. **Contents:** open the Almanac (`/give @s pirates_n_ships:captains_almanac`). The first chapter is "Welcome aboard"
   with the closed tome pictured; its last page "Contents" lists the 13 other chapters as navy links in book order.
   Click each one: it turns to that chapter's first page. The bookmarks show 14 ribbons with their icons.
2. **Read every chapter**, every page. Report:
   - any raw lang key (`pirates_n_ships.almanac...`), any literal markup (`**`, `{item:`, `[[`, a backslash) or
     a stray `_`;
   - any config key, command, mod id or package name outside "The Quartermaster's ledger";
   - sentences that read like a readme instead of a ship's master speaking to a new hand, or that pastiche too hard;
   - pages so long that the continuation pages lose their heading context, or a heading left alone at a page end.
3. **Tips and warnings:** the paragraphs in green ink (tips) and red ink (warnings) read clearly on the parchment, e.g.
   Sailing "The wind" (tip) and Ships "Flooding" (warning). The welcome page's sample words in green, red and navy
   match the real tips, warnings and links.
4. **Item icons:** inline icons sit in the line before the item's first mention (e.g. Sailing "Square sails" yard,
   The ship's inventory, every entry). No icon is a purple-black missing texture.
5. **Links:** cross-links in the text (e.g. Crew "The captain's whistle" to Ships, Ships "Ordering a ship" to Law and
   Weapons and combat, every entry of The ship's inventory) jump to the right chapter.
6. **Illustrations:** every chapter has a picture under its title; the new ones are the closed tome (Welcome aboard),
   the shipwright's toolkit (Ships, "Patching a breach"), the hammock (Crew), the navy officer (Law), the map tile and
   the treasure map (Charts and navigation), the cargo barrel (The ship's inventory), the ship commission (ledger) and
   the anchor (Not yet charted). Text flows around each without overlapping it.
7. **Show me:** pages with a "Show me" button and their scenes: Your first ship and Ships (`ship_assembly`), Sailing
   (`sails_and_winch`, `steering`, `anchor_and_capstan`, `mooring`, `ships_bell`, `crows_nest`), Ships
   (`pumps_and_flooding`, `hull_repair`), Crew (`crew_orders`, `hammocks_and_rest`), Flags (`flags_and_allegiance`),
   Provisions (`galley_and_provisions`), Cargo and trade (`cargo_and_weight`), Weapons and combat (`cannon_loading`,
   `swivel_gun`, `boarding`). `cannon_loading` plays the cannon scene. Until PND1/PND2 land, the others log a warning
   and the book stays on its page (no crash, no blank screen); after they land, each plays its scene.
8. **The ledger:** "The Quartermaster's ledger" lists the main settings as `section.name: default` and the player and
   operator commands; underscores in the keys show as underscores, not as italics.
9. **Facts:** spot-check a few numbers in game against the text: a sail winch cycles furled, half, full; the capstan
   refuses to drop over water deeper than 32 blocks; a hull patch from 2 planks and a coal makes 2; a water barrel holds
   16 rations; the crow's nest calls "Sail ho!" with a bearing in points.

# Playtest: GuideME retired, the Almanac as first-join gift (work package GUIDE2)

GuideME is gone: no dependency, no `guide` module, no generated pages. The first-join gift is now one Captain's
Almanac (`almanac.give_on_first_join`, default true), pinned by `AlmanacGiftGameTests` (first join gives one, a second
gives none, a lost book is not replaced, the switch off gives none and stores nothing). Please check both loaders.

## Steps
1. **First join:** create a new world (or join with a new player). The inventory holds exactly one Captain's Almanac.
   Relog: still one, no second book. Throw it away, relog: none comes back.
2. **Old world:** open a world where you got the old GuideME guide book before. You get one Almanac at the first login
   (the flag is new), then never again.
3. **No GuideME:** the mod list (Mods screen, Mod Menu on Fabric) shows no GuideME, the log has no `guideme` lines, the
   creative inventory and recipe book have no "Pirates 'n' Ships Guide" book, and `/guideme` is no command. A world
   saved with the old GuideME book in a chest loads without errors (the stack becomes air).
4. **Switch off:** set `almanac.give_on_first_join` to false in the server config, join with a new player: no book.
   Turn it back on and relog: the book arrives then.
5. **Ledger:** the Quartermaster's ledger lists `almanac.give_on_first_join: true` with the other Almanac settings.

# Playtest: chapter markers left first (work package ALM7)

The chapter markers (bookmarks) now run down the **left** edge of the open book first and continue down the **right**
edge when the left is full. One edge holds 9 markers at the normal 2-pixel gap, both together 18; with more chapters
they split evenly (the left takes the odd one), the gap shrinks (up to 10 a side, 20 in all, nothing overlaps) and then
the markers overlap a little, never leaving the edges. The layout, the sides, the pull-out and the hit areas are
covered by JUnit (`BookmarksTest`); the drawing needs a client. Today's book has 14 chapters: 9 on the left, 5 on the
right. Please check **both clients** and send screenshots.

## Steps
1. **Order:** open the Almanac. Markers 1 to 9 ("The guide book" … the ninth chapter) run top to bottom down the left
   edge of the book, chapters 10 to 14 top to bottom down the right edge, the first right marker level with the first
   left one. Each marker has its chapter's colour and icon.
2. **Mirror:** a left marker is the mirror of a right one: the swallowtail end points left (away from the book), the
   icon sits near that outer end, the inner end disappears under the left cover. No marker shows a straight cut end
   outside the book or a swallowtail under the cover.
3. **Open chapter:** the open chapter's marker sticks out 3 pixels further than its neighbours, outward on its own
   side: open a left chapter (it sticks out to the left), then click a right marker (that one sticks out to the right,
   the left one goes back in line). Turning pages across a chapter boundary moves the pull-out to the new chapter's
   marker, whichever side it is on.
4. **Hover and click, both sides:** hovering a marker on either side lifts its colour, makes it one pixel longer
   outward and shows the chapter's title as a tooltip; clicking it turns to that chapter's first spread. The gaps
   between markers, the cover and the pages next to them show no tooltip and turn nothing.
5. **Nothing clipped:** at GUI scale 3 and at Auto, in a normal window and a small one, the book stays centred and both
   edges' markers, including a pulled-out and hovered one, are fully on screen.

# Playtest: index, recipe panels, spotlights and shorter chapters (work package ALM8)

The Almanac now opens on an **index spread** before chapter one, draws **recipe panels** and **item spotlights** in
the text, and every chapter was rewritten to about half its length (13,694 words before, 6,847 after). The layout of
the index, of the panels in the page flow (never split, moved whole to the next page) and the content rules (every
recipe of ours has one panel, every chapter opens with its picture and a spotlight) are covered by JUnit
(`IndexLayoutTest`, `PageLayoutTest`, `RecipeGridTest`, `AlmanacGuideTest`); the drawing needs a client. Please check
**both clients** (`./gradlew :neoforge:runClient`, `./gradlew :fabric:runClient`) and send screenshots.

## Steps
1. **Index on a fresh join:** join a world, open the Almanac (in hand or placed). The first spread is the index: the
   left page lists all 14 chapters top to bottom, each with its icon and its title in its bookmark colour, one per
   ruled line; "The Quartermaster's ledger" is drawn a little smaller. No page numbers on the index, no marker sticks
   out. The right page shows "The Captain's Almanac", a two-line foreword and "New aboard? Begin here:" with a button
   showing the welcome chapter (for a player who never read a page).
2. **Clicking tiles:** hover an index entry: it is shaded and underlined, the tooltip shows the full title. Click it:
   the page turns to that chapter's first spread and its marker sticks out. Press Home, or turn back from the first
   chapter's first spread: the index comes back.
3. **Continue where you left off:** read a few pages into, say, "Sailing", close the book, open it again: it opens
   at that page (same session). Leave the world and join again, open the book: the index, now with "Continue where
   you left off:" and a button "Sailing, p. N" (the left page of the spread you read). Click it: the page turns to
   exactly that spread.
4. **A recipe panel:** in "Your first ship", find the helm's panel: a parchment card with the 3x3 grid, an arrow and
   the helm in a brass-rimmed slot. Hover every slot: the tooltip of that ingredient; hover the result: the helm's
   tooltip. Find a shapeless recipe (the Almanac's own, in "Welcome aboard"): a small two-arrow mark above the result,
   with a tooltip "Shapeless: …". A slot with a tag (planks) cycles through the planks once a second. The nails'
   panel in "Ships" shows the count 8 on the result.
5. **A panel never splits:** page through "Ships" (the fittings pages hold two panels each): no panel is cut at the
   bottom of a page; one that does not fit starts at the top of the next page, and a heading is never left alone
   above a panel on the page before.
6. **A spotlight:** every chapter's first page shows its picture and, under it, the chapter's key item framed at twice
   its size with its bold name under it (the helm in "Your first ship", the writable book in the ledger). Hovering
   the framed item shows its tooltip.
7. **A chapter's new length:** "Sailing" and "Weapons and combat" read in short paragraphs (a few lines each) with
   the panels and plates between them; every "Show me" button is still there and plays its scene.
8. **Datapack change:** with a datapack that changes the helm recipe (or `/reload` after editing one), the helm's
   panel shows the new recipe; a removed recipe shows "This recipe is not in the world".
9. **Nothing clipped:** at GUI scale 3 and at Auto, in a normal window and a small one: the index entries, the
   continue button, the recipe panels (the result's count included) and the spotlight names stay inside the pages;
   nothing overlaps the page arrows.

# Playtest: 3D spotlights (work package ALM10)

Ten places in the Almanac now show a **3D spotlight**: a block or mob turning slowly in a larger framed card, its name
in bold under it. The card's place in the page flow (96 px, never split), the turn's speed and the fit are covered by
JUnit (`PageLayoutTest`, `Spotlight3dViewTest`, `AlmanacGuideTest`); the drawing needs a client. Please check **both
clients** (`./gradlew :neoforge:runClient`, `./gradlew :fabric:runClient`) and send screenshots. Where they are:

| Chapter | Page | Shows |
|---|---|---|
| Your first ship | 2. Set the helm | the helm (block, with its wheel) |
| Ships | Railings | the railing gate, shut ("The railing gate, shut") |
| Sailing | Anchor and capstan | the capstan (block, with its bars) |
| Crew | Hiring hands | a crew member ("A hand of your crew") |
| Law | The brig | the brig door, shut, both halves ("The brig door, shut") |
| Weapons and combat | The cannon | the cannon (carriage and barrel) |
| Weapons and combat | The swivel gun | the swivel gun |
| Weapons and combat | Foes ashore and afloat | a pirate |
| Weapons and combat | Monsters of the deep | the kraken |
| Weapons and combat | Sharks | a shark |

## Steps
1. **Each one turns smoothly:** open each page above. The model turns about its own upright axis, one full turn in 18
   seconds, without stutter (the turn uses the partial tick, so it is smooth at any frame rate above 20), leaning
   toward you a little so you see its top. Leave the book open a minute: the turn never jumps.
2. **Inside its card:** at no angle does any part of the model reach over the card's leather rim or out of the card;
   what would is clipped at the rim. The model is centred in the card and as large as fits.
3. **The kraken fits:** the kraken is drawn small, whole (body and tentacles, as far as its model shows them at rest)
   and inside the card at every angle. Same for the shark (nose to tail) and the cannon (the barrel overhangs the
   carriage).
4. **Blocks as built:** the helm shows its wheel, the capstan its bars, the cannon its barrel, the swivel gun its
   barrel on the yoke; the brig door shows both halves, shut; the railing gate is shut. No block is drawn dark or
   black on one side only because of the light (they are lit as the inventory lights a mob).
5. **Mobs face you, head straight:** the crew member, the pirate, the shark and the kraken turn with the card; the head
   follows nothing (moving the mouse over the book changes nothing). Say which of the GeckoLib mobs (crew member,
   pirate, shark, kraken) **play their idle animation** and which **stand still**: the spotlight advances the mob's
   tick count with the screen, so a mob whose idle animation plays while it stands should play it here; a mob that
   stands still is acceptable, but tell us which. The pirate and the crew member wear their default look.
6. **Names and tooltips:** the name under each card is bold and centred, the caption where the table gives one, else
   the registry name ("Helm", "Capstan", "Kraken"). Hovering a card shows the registry name as a tooltip.
7. **Page turns:** while a page turns, the cards show empty (no model) and come back once it settles; nothing of a
   model is drawn over the turning page.
8. **Nothing clipped at GUI scale 3 and Auto:** at GUI scale 3 and Auto, in a normal window and a small one (the book
   shrinks), every card and its name stay inside the page, the model inside its card; nothing overlaps the page arrows
   or the page number.
9. **Closing and reopening:** close the book and reopen it on the crew page several times, and open a "Show me" scene
   from a page with a spotlight and come back: no error in the log, the mobs come back (a new instance each time the
   screen opens).
10. **Log:** the client log has no "Almanac 3D spotlight ... could not be built" or "failed to draw" warnings. If one
    appears, send it: that card stays empty.
