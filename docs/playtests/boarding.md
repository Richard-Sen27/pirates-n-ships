# Playtest: boarding plank (work package BRD1)

The server side (a run laid from ship A lands on ship B, segments in A's plot, the far end over B, `between(A, B)`,
a board to stand on, the run stays while the ships lie still, breaking when B is moved three blocks away with exactly
one plank dropped, breaking any segment takes the whole run, refused eight blocks apart, refused off a ship,
`boarding.plank.enabled=false`, the side-face run that ends against a higher deck) is covered by 8 GameTests, and the
run rules (start cell, reach, landings, the break rule) by JUnit. This file checks what only a real world shows:
walking across, the look, the jitter when the hulls roll, and the break when they part. Please send screenshots and
`latest.log` if anything differs.

Setup: a creative world with cheats, calm open sea. Build two small ships side by side about 3 blocks apart (each with
a helm, a deck and, on one of them, a one-block-high rail along the side facing the other ship), assemble both.
`/give @s pirates_n_ships:boarding_plank 8`, `/give @s pirates_n_ships:grappling_hook`. Switch to survival for the
walking parts.

Defaults: `boarding.plank.max_length` 4, `break_distance` 1.5, `check_interval_ticks` 10.

## 1. Haul the hulls together with the grapple
1. From ship A throw the grappling hook into ship B's side and haul (hold sneak with the rope in hand, walk back) until
   the hulls lie side by side (G11: the rope holds without pulling).
2. Expected: the hulls lie alongside, 1 to 3 blocks of water between them.

## 2. Lay the plank and walk across
1. Stand on A's deck at the gunwale facing B. Right-click the **top face** of a gunwale block (the deck's edge block or
   the rail) with the plank.
2. Expected: a plank of up to four cells runs straight out from beside the clicked block, level with its top, and its
   last cell lies on B's deck or rail; iron hooks at the far end bite down into B's deck; a rope lashing at A's end;
   wood place sound; one plank used up (survival).
3. Walk across from A to B and back, also while sprinting and sneaking. Expected: you walk on the planks like on a
   carpet over a slab, no falling through, no snagging at the seams between cells.
4. Right-click the **side face** of A's deck-edge block (outward). Expected: the plank lies at that block's height; if
   B's deck is one block higher there, the plank ends against B's hull side and you hop up onto B's deck.
5. Click a gunwale with nothing within four blocks (the hulls far apart, or the other side of the ship). Expected:
   action bar "No deck within reach", nothing placed, the item kept.
6. Click a block that is not on a ship. Expected: "Lay the plank from a ship's gunwale".

## 3. The rails and a one-block height difference
1. Ship A with a rail, B without (B's deck one block lower than A's rail top): click the rail's top face. Expected: the
   plank runs at rail-top height and its far end hangs one block above B's deck (step down), or rests on B's deck if
   it is level with the rail top.
2. Both ships with rails: click A's rail top facing B's rail. Expected: the plank rests on B's rail top (rail to rail).
3. Tell me whether stepping onto the plank over a rail from A's deck feels fine or needs a ladder or step.

## 4. Rolling hulls and the break
1. With a plank laid and the grapple holding, stand on the plank and watch the hulls for a minute (some waves, or wind
   in the sails). Expected: the plank moves with ship A; the hulls do not shake or jitter where the plank rests on B's
   deck. Tell me if they do (screenshot or video, and whether it stops when you break the plank).
2. Release the grapple and sail ship A away (or push B off). Expected: as soon as the far end has moved about 1.5
   blocks from where it was laid, the whole plank breaks at once with a wood breaking sound, exactly one plank item
   drops (it may fall in the water), and anyone standing on it falls.
3. Lay a plank, then break any one of its cells with an axe (survival). Expected: all cells go, one plank drops.
   In creative: all cells go, nothing drops.
4. Lay a plank, then disassemble ship A at its helm. Expected: the plank breaks (one item), nothing is left floating.
5. Save and quit with a plank laid, reload. Expected: the plank is still there and still breaks when the hulls part.

## 5. Config
1. Set `boarding.plank.enabled = false` in the server config, reload. Expected: using the plank says "Boarding planks
   are disabled on this server"; planks already laid still break when the hulls part.
2. `max_length = 2`: a plank laid from a gunwale top bridges at most one block of water (its last cell lies on the
   other deck); with the default 4 it bridges three.

# BRD2: the gangway

Covered headlessly (18 GameTests, NeoForge and Fabric; 28 JUnit): ship to a pier one and two blocks above the rail
(1-in-2 ramps meeting the pier's edge; a mock player walks from the deck onto the pier without dropping), a pier four
up refused as too steep, a fence post refused as blocked two blocks out, pier to ship (planned from the pier, laid in
the ship's plot from the ship's end), pick-up from the pier end (one plank back, none dropped), a 1.3-block heave held
and a two-block drift breaking it, dock to dock plain blocks with a crew member pathing up the ramp, along a dock
refused, BRD1's ship-to-ship tests (the side-face case now ramps up instead of ending against the higher deck). This
section checks what only a client shows: the ramp models, the preview, walking feel, the heave at a real pier.

Defaults: `boarding.plank.max_length` 6, `max_rise` 2, `rise_run` 2, `steep_fallback` true, `pick_up` true,
`break_distance` 1.5, `break_rise` 2.5; client `boarding_view.preview` true. Delete an old server config or set the new
values (an existing file keeps `max_length` 4).

## 6. Ship to pier, pier to ship
1. Moor a ship (anchor dropped) beside a village quay with 2 to 3 blocks of water between. Hold the plank and look at
   the top of your rail facing the quay. Expected: green outlines of the cells it would lay (steps visible on ramp
   cells) appear before you click; looking at the bottom of a block or straight along the deck shows nothing.
2. Click. Expected: the plank lies from your rail onto the quay; if the quay top is one or two blocks higher (or lower)
   than the rail top, the plank climbs (or falls) on ramp cells whose boards overlap like clinker planks, with cleats.
   Screenshot please, from the side.
3. Walk up and down it, sprinting and sneaking. Expected: no jump needed, no snagging, no falling through at the joints.
4. Stand on the quay and take it up: sneak, empty hand, use the quay end. Expected: the whole plank goes, one plank in
   your inventory. Lay it again from the quay edge toward the ship (top face, facing the ship). Expected: the same run;
   it moves with the ship (watch it while the ship bobs).
5. Watch the planks while the ship bobs at the quay in rain or a thunderstorm (`/weather thunder`). Expected: the plank
   stays (it allows 2.5 blocks of rise and fall); tell me whether the ship jitters or is held up where the plank rests
   on the quay (send a video if so).
6. Weigh anchor and sail off along the quay. Expected: after about a block and a half the plank breaks with a wood
   sound and drops one plank.

## 7. Refusals and short gaps
1. A quay three or more blocks above your rail: "Too steep: a plank climbs or falls 2 blocks at most".
2. A quay seven or more blocks away: "Too far: a plank reaches 6 blocks". Open water: "Nothing to land on within reach".
3. A fence post or a lantern in the way: "Something is in the way N blocks out", the preview shows that cell in red.
4. Facing along the quay from its edge: "No plank from here".
5. Ship touching the quay, the quay one block above the rail: expected a single steep (45 degree) cell; walk it.

## 8. Dock to dock and crew
1. Two piers with a gap of 2 to 4 blocks, one a block higher: lay a plank between them. Expected: a ramp of plain blocks
   (it does not move, breaking it drops one plank).
2. With hired crew aboard a moored ship and a plank to the quay: tell me whether crew ever walk across it on their own
   (they have no order to; there is no "prepare to board" order yet).

# BRD4: one straight board

Covered headlessly (`BoardingGameTests` 23 on NeoForge and Fabric, `PlankRunTest` 30 JUnit): a dock two blocks lower
than the far one, six blocks apart, gets one straight board at 18.4 degrees (three cells on the low row, three a block
up, every cell's board on the one line) and a mock player walks it up and down without a jump or a drop; a crew member
paths across it; three up over three (45 degrees) is laid, four up over three refused as too steep; a pier-to-ship
gangway lifted three blocks breaks past `break_rise`; BRD2 saves load as straight boards (a level one in place, a
ramp laid again); the BRD1/BRD2 ship and pier tests now expect straight boards. This section checks what only a
client shows: the board's look, the walking feel, the preview.

Defaults: `boarding.plank.max_length` 6, `max_pitch` 45, `pick_up` true, `break_distance` 1.5, `break_rise` 2.5. The
BRD2 keys `max_rise`, `rise_run`, `steep_fallback` are gone (an old server config file may still list them; they are
ignored).

## 9. One pitch from end to end
1. Build two stone piers six blocks apart, the far one two blocks lower (or moor a ship whose rail is two blocks above
   a quay six blocks away). Hold a plank and look at the top edge of the high one, facing the low one. Expected: a
   single green box from the edge down to the low pier's top, straight, no level stretch and no steep end.
2. Click. Expected: one straight board at one pitch (about 18 degrees) from the high edge to the low pier, spruce
   planks with the grain along the board, a short lip lying on the high pier's top edge, cleats across the board
   every block (it is steeper than 15 degrees). Screenshot from the side, please.
3. Walk down it, then back up, sprinting and sneaking. Expected: no jump needed, no snagging, no falling through; your
   feet stay on or just above the board (the steps under it are at most a quarter block).
4. Look at the board with an empty hand. Expected: the block outline is a small staircase following the board in the
   cell you look at (that is its collision).
5. Sneak-use the board with an empty hand from either end. Expected: the whole board goes, one plank in the inventory.

## 10. The steepest board
1. A pier three blocks above your rail, three blocks of water between (rail edge to pier side). Expected: the plank is
   laid at 45 degrees; walk it (expected: walkable without jumping, cleats every block).
2. The same pier four blocks above. Expected: refused, "Too steep: at most 45 degrees", nothing laid, no green box.
3. Set `boarding.plank.max_pitch` to 30 and try the 45 degree case again. Expected: "Too steep: at most 30 degrees".
4. A fence or lantern in the board's line on a slope. Expected: "Something is in the way N blocks out" and that cell
   outlined in red where it sits on the slope (not on the start's row).

## 11. Crew and ships
1. With a crew member following you, walk across a sloped board from the ship to the quay and back. Expected: the
   crew member crosses it on foot (no teleport, no getting stuck at the board's ends).
2. A sloped board from a quay onto a moored ship in a thunderstorm (`/weather thunder`). Expected: the board moves with
   the ship as one piece (no gaps between cells), stays while she heaves, and breaks with one plank dropped when she
   drifts a block and a half away.
3. A world with gangways laid before this version: load it. Expected: level gangways look the same (now one board);
   sloped ones are straight boards from their near end (or, rarely, gone with one plank lying there); tell me if any
   went missing without a plank.

# BRD3: the "Board!" order

Covered headlessly (`BoardingOrderGameTests` 9 on NeoForge and Fabric; `BoardingWaysTest`, `BoardingRulesTest`,
`BoardingHopTest` JUnit): three free hands cross a gangway onto a ship three blocks off and stand on her deck while the
gunner keeps his swivel gun; three hands jump two blocks of water; three blocks without a plank is refused; a husk on
her deck is fought and killed (the one hand without a sword takes the cutlass from the chest); "Her deck is yours"
reaches the captain once the deck is clear; "Back aboard" from her deck brings the boarders home and the gunner back to
his gun; a broken gangway strands them, a new one brings them home; a pirate ship two blocks off a player's ship sends
her pirates across after the delay and not with `npc_boarding` off. This section checks what only a client shows: the
walk, the jump, the fight, the labels.

Defaults: `boarding.board_order` true, `board_gap` 2, `boarders_keep_stations` true, `capture_seconds` 10,
`npc_boarding` true, `npc_board_delay` 8, `npc_recall_outnumbered` 2.0, `npc_recall_flooded` 0.3, `take_arms` true,
`unarmed_damage` 2.0, `crew_skill` SAILOR, `cross_timeout_ticks` 200.

## 12. Board over a gangway
1. Two ships three or four blocks apart, side by side. On yours: a helmsman at the helm, a gunner at a cannon, three free
   hands, a chest with one cutlass. Lay a gangway from your rail onto hers. Open the whistle wheel. Expected: eleven
   entries; hover the cutlass: "Board!" with its line below.
2. Pick "Board!". Expected: "3 hands, board her!" on the action bar; the helmsman and the gunner stay; the three walk
   to the gangway's end on your deck (no teleport), one after another along the board, and step onto her deck. One of
   them now holds the cutlass (the chest is empty). Screenshot while they are on the board, please.
3. Open the wheel again. Expected: the entry now reads "Back aboard" (also on the helm's ship screen, Crew tab).
4. (BRD5) Five free hands, one gangway, "Board!". Expected: they cross in file about a block apart, each one steps off
   the board's end and moves aside off the landing (beside or ahead of it), and all five stand on her deck within
   about ten seconds; nobody waits at the board's end or pops onto her deck (that would be the fallback set-down).

## 13. Board over a gap
1. Haul the other ship alongside with the grappling hook until two blocks of water or less are between the rails (no
   gangway). Give "Board!". Expected: each hand walks to your rail opposite her deck and jumps across in a short arc,
   landing on her deck (a deck a block higher or lower works too). Tell me if anyone falls into the water.
2. Three blocks of water or more and no gangway. Expected: "No way across to board", nobody moves.
3. A railing along your whole side. Expected: refused; a railing gate or port in it: they go through the opening.

## 14. The fight
1. An NPC pirate ship alongside (or `/summon pirates_n_ships:pirate` onto the other deck), then "Board!". Expected:
   the boarders attack the pirates on her deck; the one with the cutlass winds up, slashes and parries like a pirate;
   the others punch. The pirates fight back.
2. **The crew's swings (MEL3).** Stand on her deck within a few blocks of the armed boarder, ideally in third person
   from the side, and watch one exchange. Expected, the same procedural poses as the pirates:
   - **Telegraph (wind-up):** for a slash the sword arm rises over the shoulder with the blade up and back, over the
     head; for a thrust the hand draws back low beside the hip with the blade kept level, pointing at the foe.
   - **Strike (hit frames):** the slash comes down and forward across the body to the off side, the blade ending level
     in front of the chest; the thrust punches the arm straight out at shoulder height with the blade level.
   - **Guard / parry:** the blade held up and across the chest in front of him; a parry raises it higher, nearly
     upright; a staggered crew member flings the arm out and leans back.
   - **Bare-handed hands:** each punch swings the arm forward to shoulder height and back.
   - The cutlass renders in the hand throughout. A left-handed rower who boards mirrors all of it.
   - When the fight ends (the pirates dead) the arm drops back into the walk or idle at once, and back home at a
     station the station pose (helm, gun crew, oars) shows again.
   Please send a screenshot of a crew member mid wind-up and one of the guard, and tell me if anything looks inverted
   (a blade pointing backwards on the strike, an arm swinging the wrong way).
3. Boarders with nobody left to fight on her deck stand still. After ten seconds without an enemy aboard: "Her deck is
   yours" on your action bar (only for you, the captain) and the boarders walk home over the nearest way and back to
   their stations.
4. Step aboard a cleared NPC ship yourself and stay a few seconds. Expected: the old capture ("You took the ..."), and
   for a merchant the law's piracy (nothing new here).

## 15. The recall and a lost way back
1. While they are across, give "Back aboard" (from your deck, or from hers if you boarded with them). Expected: "All
   hands back aboard"; they come back over the gangway or jump the gap back, then walk to the stations they left (the
   gunner and helmsman never left).
2. While they are across, break the gangway (or let the ships drift apart). Give "Back aboard". Expected: they stay on
   her deck and keep fighting; lay a new gangway: they come home over it.

## 16. NPC pirates board you
1. Sail into a pirate ship's range and let her come alongside (or lay a gangway between your ship and hers), with
   your free hands on deck. Expected: about eight seconds after she lies alongside, her pirates cross onto your deck
   and attack you and your free hands (crew at stations keep working); your free hands fight back.
2. Kill most of them, or let her flood. Expected: her captain calls the rest back; they cross back to her deck.
3. `boarding.npc_boarding` false: expected nobody comes across.
4. Fly the Jolly Roger near a navy ship that lies alongside: expected her soldiers and officer board you the same way.

## 17. Boarding nets (with RAIL5)
1. String a boarding net along her rail and give "Board!" over a gangway landing at that rail. Expected: your boarders
   are held at her net a moment like any attacker, then climb over.
2. A net along your own rail: boarders coming home over it pass at once (also an NPC captain's pirates at their own net).
