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


## K1b: the look
1. **Lurking:** a dark reddish-brown body deep down, tentacles trailing below.
2. **Surfacing:** the mantle rises with the fins spreading, then settles; the eyes open about a second in; the arms unfurl. Above water: the tall tapering mantle with its pale-spotted crown and the two side fins.
3. **Eyes:** two large gold eyes with a pale ring and a black pupil face the ship; they blink every 4 s while idle.
4. **Grab:** on each grab every arm's middle and tip curl in with the sucker side closing over the target, then relax; say if an arm curls away from its target or the suckers face away.
5. **Suckers:** two rows of pale discs with dark rims along each arm. **Beak:** dive under it: a black hooked beak in a ring of pink lips at the centre of the arm crown.
6. **Cut tentacle:** a quarter-size stump at the root, not floating or hidden in the head.
7. **Retreat:** fins fold, the mantle contracts and sinks, the eyes close, the arms draw in; the pose holds until it vanishes.
8. **At 30+ blocks:** any flicker on the mantle steps or fins; at screen edges whether the mantle top pops out.


## K1c: the arms
1. **Length:** lurking or retreating, the arms hang about two-thirds of the mantle's height, 25° out; surfacing, they rise to about 50° out, roughly 3.75 blocks long.
2. **Suckers at rest:** on hanging arms the pale sucker rows face the body's centre line, not sideways; raised arms show their dark backs from outside.
3. **No arms through the head** in any state, including the first frames after spawning.
4. **Grab:** tips reach the hull spots at the waterline without looking strongly stretched; during the grab the tips curl toward the body side with the suckers inside the curl.
5. **Mast strike and swipe:** arms reaching the mast or deck stretch up to about 2.7×: say if that looks too thin or too long.
6. **Swimmer drag:** watch for a sudden 180° roll of an arm as it passes horizontal (known singular direction).
7. **Cut:** a quarter-size stump leaning out; after 30 s the arm is full width again.


## GL1: tips and suckers down
Spawn a kraken next to a ship (`/pirates mob spawn kraken`) and let it surface and attack.
1. **From above (on deck, looking down over the rail):** the raised arms (50° out at the attack rest) show their dark backs; the curled tips hook **down and outwards**, away from the sky. No pale sucker rows face up.
2. **From the side, level with the waterline:** the pale sucker rows of a raised arm are on its underside; an arm reaching onto the deck curls its tip down over the planks with the suckers inside the curl (towards the deck).
3. **Grab animation:** every grab curls the middle and the tip downwards over the target, not up and back towards the kraken's head.
4. **Hanging arms (lurking, retreating, cut stumps):** the sucker rows face the body's centre line, as before.
5. **Turning:** walk around the kraken while it turns to face the ship (try it with the ship north, east, south and west of it): the arms still end at their targets and the suckers stay down on every side.
6. **Sweeping arms:** an arm swinging from hanging to raised (a swimmer pulled up, a mast strike) rolls smoothly; say if any arm suddenly flips its suckers by half a turn (the old flip at horizontal is gone; a fast roll can remain only for an arm pointing steeply inwards, over the head or under the body).
