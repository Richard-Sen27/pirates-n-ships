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
