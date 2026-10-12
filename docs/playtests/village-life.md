# Playtest: village life

## LIFE1: the village people, their day, hiring and the stalls

Setup: a new world (or chunks not generated yet), `/locate structure pirates_n_ships:seafarer_village`, fly there.
Operators can put any role down with `/pirates village spawn <role>`; `/time set` moves the day.

1. **Who lives here.** Walk the village by day (`/time set 2000`).
   - Expected: one person per building, each in the outfit of the trade (see `art/README.md`, "Village people's
     outfits"): villagers in felt caps at the cottages, a fishwife in a checked scarf and white apron, an old net mender
     in a green cap under the net loft, a dockhand in a grey bandana in the warehouse, a shipwright in a leather apron,
     an innkeeper in a red waistcoat behind the bar, a lamplighter in a grey coat at the beacon tower, vendors in straw
     hats at the fish market and at both stalls of the market square. The harbor master is at his desk as before.
   - Check: textures sit right on the sailor's model (cap cubes, shawl, aprons, skirts); the innkeeper's cap cubes are
     invisible (no floating hat). Screenshot each role front and back.
2. **Work.** Stand near the fisher's cottage at `/time set 1000`, then `/time set 6000`.
   - Expected: the fishwife works (the `work` animation) at the drying rack in her yard, then walks to the fish
     market and works beside the vendor. Others work at their benches, crates, nets, the hull, the bar.
3. **Dusk.** `/time set 11000`.
   - Expected: villagers walk to the tavern or inn and sit on its chairs (the `sit` animation, hips on the stair, not
     floating or sunk), or on a square's bench or their own chair. The innkeeper stays at the bar.
   - Check the sitting height and facing (towards the table, away from the stair's back).
4. **Night.** `/time set 14000`.
   - Expected: everybody goes to a bed and lies on it (the `sleep` animation) along the bed, head on the pillow
     half; a villager whose bed is upstairs climbs or steps up after about ten seconds. Without a free bed they stand
     at home. Check that sleepers neither sink into the mattress nor float above it.
5. **Lamps.** `/time set 11500` near the beacon tower, then `/time set 22600`.
   - Expected: the lamplighter carries a lantern from lamp post to lamp post, pausing at each with the work animation,
     then goes back to the tower and puts the lantern away. (Lanterns cannot be lit or put out in vanilla.)
6. **Monsters.** Spawn a zombie next to a villager at night (`/summon zombie`).
   - Expected: the zombie hunts the villager; the villager gets up from bed or bench and runs; afterwards it goes back
     to its routine.
7. **Stalls.** By day, use the fish market's vendor, both market square vendors and a shipwright.
   - Expected: the market screen opens with that stall's wares only (fish market: cod, salted fish; provisions: salt
     pork, hardtack, rum; chandler: rope, cloth, nails; shipwright: hull patch, toolkit). Buy and sell; the purse and
     inventory change; the toolkit cannot be sold back. Walk away: the screen closes. At night: "The stall's shut".
   - Check the free 2×2 paved corner south of the west stall (for the notice board) and that the stalls look right.
8. **Hiring.** Moor your own ship with a free hammock at the village's pier, then sneak-use the dockhand twice.
   - Expected: the first names the fee (10), the second hires; the dockhand is gone from the village and stands on
     your deck in the dockhand's clothes, with its name. After three days a new dockhand works at the warehouse
     (`/pirates village respawn` after `/time add 72000` to check quickly).
9. **Config.** `mobs.villagers.routines` off: villagers stroll. `roles` off: new villages get plain villagers only.

Report: screenshots of every outfit, the sitting and sleeping poses, the market screen of a stall; any villager
stuck (where, which role, which time); `logs/latest.log`.
