# Playtest: named pirate captains (work package BOS1)

The server side (spawn with a name, the registry, the standing bounty on the notice boards, the duel's truce and the
captain's single target, refusals, the proof, the kill_pirate deed and the drops, the captain's tier at the navy
turn-in, the successor, `mobs.captain.enabled` and `duel_enabled`) is covered by 10 GameTests, and the names, the duel
rules, the day math and the hut post (read from the committed templates) by JUnit. This file checks what only a real
world shows: generation, the look, the duel's feel and the client gesture. Please send screenshots and `latest.log` if
anything differs.

Setup: a **new** survival world with cheats (captains are placed when an island generates; islands of an old world have
none until you use `/pirates mob captain spawn` there), difficulty Easy or higher, default config. `/give @s
pirates_n_ships:cutlass`, `/give @s pirates_n_ships:shackles`, `/give @s pirates_n_ships:doubloon 200`, a stack of
food. `/pirates rep set @s pirates 0`.

Defaults: health 40 (a pirate has 24), skill PIRATE_CAPTAIN, bounty 300, respawn 5 days, truce range 16 blocks, the
challenger leaves the duel beyond 32 blocks, a duel lasts at most 300 s, map drop on.

## 1. Find the captain in his hut
1. `/locate structure pirates_n_ships:pirate_island`, teleport there in creative, then switch to spectator to look around.
2. `/pirates mob captain list`. Expected: a line "`<Name>` of `pirates_n_ships:pirate_island_…` at x, y, z, alive,
   bounty 300 doubloons" for each island generated so far.
3. If the island has a captain's hut (the hut on stilts with a porch and a black banner): the captain stands in the
   middle of the room, one block inside the door, facing it. Without a hut: on the inland trail just south of the
   camp's east-west path, facing the campfire. Expected: the captain's own look (ART6, section 7 below), his name shown
   when you look at him ("Black-Tooth Bartholomew Crowe" style: epithet, first name, surname).
4. Expected: exactly one captain per island (look for a second one in other huts); he does not wander off.
5. The hat you see is part of his model; the `captains_hat` item in his head slot is not drawn a second time (no
   doubled or floating hat).

## 2. The bounty on a notice board
1. Open the notice board in the island camp (or any notice board).
2. Expected: the captain's name with 300 doubloons, marked as a navy bounty, among the other bounties.

## 3. The duel and the truce
1. Survival, cutlass in the main hand, walk into the camp. The crew and the captain attack on sight as usual.
2. Sneak and right-click the captain (keep the button down for a moment: the client sends the challenge when the use
   key goes down). Expected: chat "`<Name>` accepts your challenge! His crew stand back…", the pirates within 16
   blocks stop attacking you (a pirate mid-swing may finish its swing), the captain fights you.
3. While dueling, step back to 20 blocks: the crew stay calm; past 32 blocks the duel ends ("You fled the duel…") and
   within two seconds the crew attack again.
4. Challenge again. Hit a crew member: expected, that one fights back (grudge), the others keep out.
5. Without a sword, or not sneaking: the right-click does nothing special. After hitting the captain, a challenge is
   refused with "You struck … first. No honour, no duel". In creative it is refused ("…a spirit").
6. Try a vanilla iron sword too: the challenge works the same way.
7. Lose a duel on purpose (let him kill you). Expected: "You lost the duel with …", after respawn the crew are hostile.

## 4. The kill and the drops
1. Duel and kill him. Expected chat: "`<Name>` is beaten. The duel is yours!", a Bounty Proof for him in your inventory.
2. Expected drops: his Captain's Hat (not a pirate hat; undamaged, always), 8-16 doubloons, sometimes his cutlass, and a Treasure Map of this island (while it
   has unlooted treasure; open it: it points at this island's buried chest).
3. `/pirates rep`: pirates −18, navy +6 (attack and kill of a pirate).
4. `/pirates mob captain list`: the island's line reads "lost on day N".
5. Hand the proof to a navy officer at an outpost. Expected: 300 doubloons; the notice board no longer lists him.

## 5. Turn in a captured captain
1. On another island (or `/pirates mob captain spawn` in an open field), weaken the captain below the capture threshold
   without killing him, use the shackles on him, lead him to a navy officer (`/pirates mob spawn navy_officer`).
2. Empty main hand, right-click the officer. Expected: "Paid … doubloons": the captain's tier (150) plus his bounty
   alive (300 × 1.5 = 450), 600 in total with default config; he is led away; `/pirates mob captain list` shows him lost.

## 6. The successor
1. After 4. or 5., `/time add 120000` (5 days) and stay near the island (its chunk must be loaded).
2. Within 5 s: a new captain stands at the old post with a different name; `/pirates mob captain list` shows him alive
   with a new bounty of 300; the notice board lists the new name.
3. With `mobs.captain.enabled = false` in the server config no successor appears, and existing captains vanish when
   their chunks tick.

## 7. His look (ART6)
`/pirates mob captain spawn` in an open field at noon, then walk around him in third person (F5) and spectator.
Renders for comparison: `art/renders/pirate_captain.png` (front three-quarter, front, left side, back three-quarter)
and `art/renders/pirate_captain_walk.png` (walk at both strides).
1. **Front:** tanned face, full black beard with two braids tied with gold beads hanging below the chin, a scar
   through his left brow (your right as you face him), both eyes visible (no eyepatch: that is the plain pirate).
   Long charcoal coat open over a gold brocade waistcoat, a white jabot at the throat, gold edging and brass buttons
   down both fronts, a red sash with its knot and two ends on his left hip, a leather baldric from his right shoulder
   to his left hip, wide crimson cuffs with gold rings and brass buttons, dark breeches, tall black boots with turned
   bucket tops.
2. **Hat:** a wide black hat edged in gold with a red band, the brim cocked up on **his left** side (your right as you
   face him), a gold clasp on the crown's front-left and a white plume rising from it, curling back over the left of
   the crown and drooping behind. Expected: the plume on the same side as the cocked brim; tell me if it is on his
   right (a mirrored x).
3. **Side and back:** the coat tails reach to just above the boots (y 2.4 px), with a gold-edged vent and two brass
   buttons at the back; the beard braids show from the side; grey at the temples.
4. **Moving:** while he walks and fights, the hat and plume turn with the head, the tails and skirts stay on the body.
   Known: at full stride the legs pass through the long tails (same as the plain pirate's shorter tails). Say if it
   looks bad enough to shorten them.
5. **Sword:** in a duel his cutlass is in the right hand (the duelist arm pose comes from code, as for every pirate);
   the cuff must not hide the hand or the hilt.
6. **Drop on the ground:** his Captain's Hat is the same hat as on his head (gold edge, red band, cocked left brim,
   white plume); put it on: see items-and-blocks.md, step 18.

## BOS2: the captain's hunted voyage

The server side (a forced voyage with the captain aboard as lead fighter, his identity and health through a
dematerialise/materialise cycle, his death aboard paying the bounty and marking the registry, drowning with his sunk
ship crediting the last shooter, his capture in shackles at sea, the empty post while at sea, the homecoming after an
arrival and after a voyage ended with its ship, `world_simulation.captain.enabled`, the schedule) is covered by 9
GameTests (`CaptainVoyageGameTests`), and the schedule, the routes and the quarry rule by JUnit
(`CaptainVoyageRulesTest`). This section checks what only a real world shows: a real island, a real cruise, the hunt
with guns, the client. Please send screenshots and `latest.log` if anything differs.

Setup: the BOS1 world (creative with cheats is fine for steps 1-3), a pirate island with a living captain
(`/pirates mob captain list`). Defaults: a chance every 2 days at 50 %, cruise 600 blocks out and back, hunt radius 192.

1. **Forced departure.** Stand in the captain's hut and run `/pirates mob captain voyage <island id>` (Tab completes
   it). Expected: "Captain `<Name>` of `<island>` puts to sea (voyage `<id>` toward `<port>`, N blocks out and back)";
   the captain disappears from the hut at once (no death message, no drops); `/pirates mob captain list` says
   "`<Name>` of `<island>` at sea (voyage `<id>`), bounty 300 doubloons"; the notice board still lists his bounty.
2. **The voyage list.** `/pirates world voyages`: a line of kind `captain` from the island, ending in
   "captain `<Name>` aboard".
3. **Aboard.** Fly to the voyage's position (the list's x z) in survival or spectator within the materialise radius,
   or run `/pirates world voyages materialize <id>`. Expected: a pirate ship under the Jolly Roger appears; on its deck
   the captain (his own look and hat, his name above him) stands among the pirates; there is one pirate fewer than on
   a plain pirate ship (he took one's place). He does not try to walk off toward his hut.
4. **Away and back.** Fly more than (materialise radius + 64) blocks away for 10 seconds, then come back (or use
   `dematerialize <id>` and `materialize <id>`). Expected: the ship vanishes and reappears with him aboard, with the
   health he had (hit him once before you leave; the second time he has the same wound). No second captain anywhere
   (look in the hut too).
5. **The hunt.** In survival on your own armed ship: `/pirates career letter @s grant` (or carry a bounty proof:
   `/give @s pirates_n_ships:bounty_proof`), and sail within 192 blocks of his materialised ship. Expected: chat
   "Pirate captain `<Name>` has sighted the `<your ship>` and gives chase!"; his ship turns toward you and circles you
   at about 16 blocks; his crew man the guns and fire at your ship. Without letter and proof (`/pirates career letter
   @s void`, drop the proofs) he leaves you alone after a check or two and sails back to his course; outrun him past
   320 blocks: "…has lost the `<your ship>` and breaks off the chase". Strike your colours: his guns fall silent and
   after 30 s he sails on.
6. **A navy or merchant ship near him** (`/pirates world voyages spawn near patrol` next to his ship): his guns fire
   at it at will (no chase).
7. **Duel aboard.** Board his ship, sneak-use him with a sword: the duel works as on land (step 3 above); kill him.
   Expected: bounty proof, his drops on the deck, `/pirates mob captain list` "lost on day N", the list line of his
   voyage now ends in "without captain `<Name>`". Kill the remaining pirates and stay aboard 5 s: "You took the …!"
   (a capture as for any pirate ship).
8. **Sink him.** Force another voyage on another island (or after the successor), cannon his ship until it sinks.
   Expected: he drowns with it (no swim to shore), the bounty proof lands in your inventory, "lost on day N".
9. **Homecoming.** Force a voyage and then `/pirates world voyages advance <id> 100000` (while it is a record, far from
   you). Expected: the voyage ends; `/pirates mob captain list` shows him alive at his post again; go to the hut: he
   stands at his post facing the door, the same name, his bounty unchanged.
10. **Departure while you are far away.** Leave a world running near nothing for two in-game days (`/time add 48000`
    twice, staying away from the island): `/pirates world voyages` sometimes shows a captain's voyage. Then visit the
    island while he is at sea: the hut is empty (no copy left there, check for a second captain when he comes back).
11. **Toggles.** `world_simulation.captain.enabled = false`: `/pirates mob captain voyage` is refused ("…voyages are
    off…"), no captain leaves on his own; a captain already at sea comes home when his voyage ends.
