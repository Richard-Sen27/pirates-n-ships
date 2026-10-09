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
