# Playtest: the harbor master (work package PRT1a)

The server side (talking opens the desk's market, sneaking and the off hand pass, a player who hit him gets "not now",
he walks back to his post, the registry replaces a lost one after `respawn_days`, `harbor_desks.direct_use` off,
`mobs.harbor_master.enabled` and `at_pirate_islands` off, and his placement behind the desk in the village, outpost and
pirate island tests) is covered by GameTests; the three posts are checked against the committed templates by JUnit.
This file checks what only a real world shows: generation, the look, the gesture and the greeting. Please send
screenshots and `latest.log` if anything differs.

Setup: a **new** survival world with cheats (harbor masters are placed when a port generates; ports of an old world have
none until you `/pirates mob spawn harbor_master` one, and a spawned one has no post or port), default config.
`/give @s pirates_n_ships:doubloon 200`.

Defaults: `mobs.harbor_master.enabled` true, `at_pirate_islands` true, `respawn_days` 3, `return_distance` 2;
`cargo_trade.harbor_desks.direct_use` true.

## 1. One harbor master behind every desk
1. `/locate structure pirates_n_ships:seafarer_village`, teleport there. Walk into the harbor master's hut on the dock
   head (the door on the quay side). Expected: a man behind the desk, facing the door: grey hair and whiskers, a dark
   navy cap with a gold band, a bottle-green coat with brass buttons over a buff waistcoat, white cravat, dark trousers,
   black shoes. He stays behind the desk (no wandering) and looks at you.
2. `/locate structure pirates_n_ships:navy_outpost`: in the harbor master's office of the fort gate (blue door on the
   court side). Expected: he stands behind the desk beside his stool (one block toward the chest), facing the door.
3. `/locate structure pirates_n_ships:pirate_island`: in the fence's shack (north-east corner of the camp). Expected: he
   stands behind the counter (the desk), facing the camp.
4. Expected in each: exactly one harbor master; the garrison of the outpost (6 soldiers, 1 officer) and the island's
   captain are where they were before.
5. Look: does the coat read as a coat (the skirts are on the upper legs and move with them)? Do the cravat and the cap
   look right on the sailor's model? Screenshot from the front and the side.

## 2. Talking opens the desk
1. Survival, empty hand or any item (not a name tag, lead or spawn egg), stand at the desk's customer side and
   right-click the harbor master. Expected: the market screen opens with the port's tabs (as when you use the desk);
   when you close it, chat shows his greeting: village "Welcome ashore, captain. Goods, contracts or a new ship?",
   outpost "State your business, captain. The Crown's ledger is open.", island "Coin first, questions never. What've you
   got?", each prefixed with "Harbor Master:". A trade sound plays.
2. Buy something: works as at the desk. Walk 9+ blocks away from the desk: the screen closes as before (the session is
   the desk's).
3. Sneak and right-click him: nothing opens (sneaking is left for other gestures).
4. Using the desk block directly still opens the market (`direct_use` on).

## 3. Not now
1. Punch him once, then right-click him. Expected: he panics and runs, the action bar shows in red "The harbor master
   waves you off: not now!" and a villager "no" sound. Wait about 30 s (`mobs.grudge_ticks` 600) and try again: the
   desk opens.
2. Spawn a pirate next to the desk (`/pirates mob spawn pirate`) in peaceful-off settings. Expected: he runs from it;
   talking to him gives "not now". Kill the pirate: he walks back behind the desk within a few seconds and turns to face
   the door.

## 4. Back to his post
1. Push him out of the hut (walk into him, or a knockback stick) about 5 blocks. Expected: within a few seconds he walks
   back to his post and faces the door again. He never strolls around on his own.

## 5. Death and the replacement
1. Kill him. Expected: the desk still works directly. `/time add 72000` (3 days), stay near the port. Expected: within
   5 s a new harbor master stands at the post.
2. Kill the new one and `/time add 24000` only: nobody appears yet.

## 6. Config
1. `cargo_trade.harbor_desks.direct_use` false (server config, reload or restart): using the desk shows "Talk to the
   harbor master" on the action bar; talking to him opens the market.
2. `mobs.harbor_master.enabled` false: every harbor master disappears; new ports get none; set it back, and a new one
   appears `respawn_days` later.
3. `mobs.harbor_master.at_pirate_islands` false, then generate a new pirate island: its shack has no harbor master; new
   villages and outposts still have one.
