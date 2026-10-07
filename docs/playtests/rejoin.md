# Playtest: rejoining split ship pieces with the Shipwright's Toolkit (work package RS2)

The rules (same origin, size, alignment, contact, overlap, nails, in that order), the snap onto the keeper's grid
(quarter turns, gap limit) and the rejoin itself (blocks at their places, chest contents, the wreck record gone, crew
station following, refusals, the config toggle) are covered by 19 JUnit tests and 9 GameTests. What only a client
shows: the seam particles, the hammering sounds and messages, how hard it is to line pieces up on water, and the
nameplate.

Setup: `./gradlew :neoforge:runClient`, survival, calm deep water. Craft:
- Carpenter's Hammer: 2 iron ingots over a stick (`II` / ` S`).
- Saw: sticks on the left, an iron ingot top right (`SI` / `S `).
- Nails: 3 iron nuggets (shapeless) give 8 nails.
- Shipwright's Toolkit: hammer + saw + nails + leather (shapeless).

Please send `latest.log` if anything differs.

## Steps
1. **Make a ship that splits.** Build a hull of two halves joined by a single plank (a "dumbbell"), helm on one half,
   a chest with some items and a sail winch on the other half. Assemble with the helm and name the ship.
2. **Split it.** Shoot or break the joining plank. The helm half keeps the name; the other half becomes "Wreck of
   <name>" (`/pirates ship info` while standing on it, or the nameplate). The chest stays on the wreck.
3. **Mark the keeper.** Sneak-use the toolkit on a block of the helm half: the action bar says "Marked for repair:
   <name>. Now use the toolkit on the piece to join to it", the toolkit glints and its tooltip shows the mark.
4. **Seam particles.** Hold the marked toolkit and sail the pieces within about 6 blocks of each other: green sparkles
   appear on the helm half's blocks nearest the wreck (at most ~24, refreshed twice a second). They stop when you put
   the toolkit away. Say whether they are easy to see and not too noisy.
5. **Refusals** (use the toolkit, no sneak, on a wreck block; nothing changes and no nails are used):
   - Pieces apart: "Bring the pieces together"; one block apart: "Add planks to bridge the gap".
   - Wreck turned off line (more than 5° off a quarter turn) or heeled: "The pieces are not lined up ...".
   - Fewer than 4 nails in the inventory: "You need 4 nails".
   - A block of an unrelated ship: "These pieces belong to a different ship".
   - Clicking the marked piece itself: "This is the marked piece ...".
   - With `assembly.rejoin.max_piece_blocks` set below the wreck's block count: "This piece is too big to nail on ...".
6. **Rejoin.** Replace the joining plank on the helm half, bring the wreck back against it, lined up, and use the
   toolkit on the wreck: "Hammering N blocks into place…", about a second of hammer sounds, then "Rejoined N blocks".
   Check: one ship again with the original name on the nameplate (no "Wreck of"); the wreck's blocks are exactly where
   they were (no shift by a block, no turned stairs or chests unless the wreck was turned a quarter); the chest has its
   items; 4 nails and 1 toolkit durability used; the ship still sails and steers from the helm.
7. **Crew and stations.** Before rejoining, order a crew member to the winch on the wreck and stand on the wreck
   yourself. After the rejoin: the crew member sits at the winch again; you are still standing on deck (not inside a
   block, not dropped into the water).
8. **Quarter turn.** Split again, turn the wreck 90° (rotate it with a grappling hook or by bumping it), line it up
   against another side of the ship, rejoin: the wreck's blocks are turned with it, block facings (chest, stairs) turn
   too.
9. **Grappling hook.** Latch a hook onto the wreck, then rejoin it: the hook stays on its block, now part of the ship.
10. **Toggle.** `assembly.rejoin.enabled = false`: sneak-use and use both say "Rejoining ship pieces is disabled on
    this server"; no particles.

## Also worth a look
- Is lining pieces up within 5° and half a block feasible on water, or do `max_angle` / `max_gap` need loosening?
- Does the keeper jump or wobble when the blocks arrive (its centre of mass changes)?
