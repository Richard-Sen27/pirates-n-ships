# Playtest: the kraken (work package K1a)

The brain, the tentacle jobs, weak spots, cutting and regrowing, grips that sink a hull, mast strikes, deck swipes,
swimmers dragged under, retreat, the spawner and the toggles are covered by 30 JUnit tests and 13 GameTests. What
only a client shows: the look (a script placeholder until K1b), the tentacle aim signs, how real players are moved by
swipes and holds, and the feel.

Setup: survival, open deep water, a small assembled ship with a mast, `/pirates mob spawn kraken` 6–8 blocks from the
ship while standing on it.

## Steps
1. **Arrival.** It rises with a growl and the boss bar appears; tentacles reach to the hull at the waterline, the mast
   and you; a tentacle pointing the wrong way or mirrored means an aim sign in `KrakenModel`; the eyes face the ship.
2. **Small ship.** It sinks about 1–2 blocks within 5 s and drifts toward the kraken; a big ship (over 400 mass) lists
   toward it with little sinking.
3. **Mast.** One mast block breaks about every 5 s with a crash; say how yards and sails react; the cut-off top may
   float off as its own ship piece (Sable's split).
4. **Deck.** You are hit for 4 and thrown sideways off the ship about every 3 s.
5. **In the water.** Grabbed, dragged several blocks down, 2 damage per second for 3 s, then released.
6. **Weak spots.** An eye hit takes about three times a body hit; 40 damage on a tentacle cuts it (ink puff, stump,
   releases), it regrows after 30 s.
7. **Retreat.** Below 30 % it lets go, sinks away and disappears within 10 s; killing it drops 1 beak and 2–5 ink.
8. **Toggles.** `hazards.kraken.enabled = false` removes it and the command refuses; `mobs.kraken.peaceful = true`
   keeps it deep and passive.
9. **Items.** The spawn egg colours, the beak and the ink sprites.
