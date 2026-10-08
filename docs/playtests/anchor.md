# Playtest: anchor physics (work package AN2a)

The anchor is now a body of its own (docs/design.md §5.2, "Anchor physics"). When you drop it, it leaves the hawse
(the ring of the stowed anchor at the hull side) with the ship's speed there. It falls through the air, sinks at
4 blocks/s, and lands where it falls, so a moving ship leaves it astern. The chain pays out with the distance as long as
the anchor falls. Once the anchor rests, the capstan's brake holds the chain's length. When the ship pulls the chain
taut, the chain pulls the ship back at the hawse, softly (a spring with damping), up to the anchor's holding power. A
pull above the holding power drags the anchor over the seabed. Because the hawse sits at the hull side, a ship with way
on swings round its anchor toward that side.

JUnit covers the fall, the sinking, the landing offset, the chain's end, dragging, heaving in, the chain force, the cap
and the taut test. GameTests (`sailing/anchor/AnchorPhysicsGameTests`) use a 9 × 17 hull with its sails still drawing.
They check the turn at speed, the landing offset, a weak anchor that drags, zero holding power, holding under sail for
20 s and raising. What only a real world shows: how the turn looks and feels on the starter sloop, whether the
deceleration feels right, and what the crew, the HUD and mooring make of "anchored".

Setup: `./gradlew :neoforge:runClient`, a creative world with cheats on and open sea at least 8 blocks deep. Place a
starter sloop (`/pirates ship place pirates_n_ships:starter_sloop assemble`, then add a capstan if it has none, or build one with a helm, sails, a winch and a **capstan near the bow
on one side**: the anchor hangs on the side nearer the capstan). Use `/pirates ship forces` on deck. Its second line now
reads `anchor holding (anchored), chain 6.2 of 6.2 blocks out, taut, on the ground, pull 0.0`. Please send
`latest.log` and screenshots (a second player or a boat beside the ship is the best view).

Config values (server, `anchor_chain`): `sink_speed` 4, `water_drag` 2, `chain_stiffness` 5, `chain_damping` 2,
`holding_force` 10, `drag_scrape_rate` 1, `settle_drag` 0.2, `heel_factor` 0.25, `at_rest_speed` 0.3, `raise_speed`
2.5. The chain's length is `sailing_runtime.anchor_chain_length` (32).

## Steps

1. **The offset after release.** Sail with full sails and a fair wind (`/pirates wind set <bearing astern> 12`) until
   `/pirates ship forces` shows at least 1 block/s. Use the capstan and watch the anchor from the side.
   - **Expected:** the anchor leaves the hull and keeps moving forward with the ship for a moment. It falls slower once
     it is in the water (splash and bubbles) and lands on the floor a few blocks astern of where the hawse is by then
     (a thud and a puff of floor particles). The chain runs from the hull side down to the anchor at an angle, not
     straight down. The action bar says "Anchor dropping to the ground N blocks below, lands in about X s".
2. **The turn at speed (the Pirates of the Caribbean turn).** Same as 1, sails left set, ship as fast as you can get it.
   - **Expected:** no instant stop. Once the anchor lands and the chain comes taut, the ship loses its way over one to
     three seconds. The bow swings toward the capstan's side (a starboard capstan swings it to starboard, clockwise seen
     from above), and the ship keeps turning until it lies head to the chain, bow toward the anchor. The GameTest hull
     turned 36° in the first 5 s and 59° in 10 s. Tell us: is the turn hard enough, too slow, too violent? Does the ship
     heel or pitch badly while it swings (`heel_factor`)?
3. **Dragging a heavy ship.** In the server config file (`serverconfig/pirates_n_ships-server.toml`, or the in-game
   mod config screen) set `anchor_chain.holding_force` to 1, then repeat 2.
   - **Expected:** the chain comes taut, but the ship keeps going, slower, and drags the anchor behind it. The anchor
     ploughs the seabed (small puffs of floor particles, the chain rattles now and then). `/pirates ship forces` shows
     `dragging` and never `(anchored)` while it moves. Furl the sails: the ship slows and the anchor bites (the
     `dragging` goes away). Set `holding_force` back to 10.
4. **Anchored for the crew and the HUD.** Furl the sails with the anchor holding and wait until the ship is still.
   - **Expected:** `/pirates ship forces` shows `(anchored)` once the ship is slower than 0.3 blocks/s. Before that it
     shows `(anchor down, way on)`. With wind on the sails the ship lies back on its chain and holds: the hawse stays
     within about a block of the chain's reach (`chain N of N blocks out, taut`).
5. **Raising from an offset position.** With the anchor lying several blocks astern (after 1 or 2), use the capstan.
   - **Expected:** "Raising the anchor, stowed in X s", with X about the chain's length divided by 2.5. The chain
     rattles. The anchor slides over the seabed toward the ship, then breaks out and rises along the chain when it is
     nearly below the hawse. It splashes out of the water and settles back into its place at the hull side. The capstan
     shows `anchor=raised`. With sails set, the ship then sails off freely. Using the capstan mid-way lets the anchor
     go again from where it is (it falls and lands again).
6. **Deep water and relog.** Over water deeper than 32 blocks below the hull side: "No ground within 32 blocks below",
   nothing runs out. Drop the anchor in about 20 blocks of water and save and quit while it is still sinking. Rejoin.
   - **Expected:** the anchor is back where it was (or a little lower) and finishes its fall. With the anchor holding,
     a relog keeps it on the floor with the same chain length, and the ship still holds.

# Playtest: the chain's look and sounds (work package AN2b)

The chain is no longer a straight bar. While the anchor rests, it hangs as a catenary of the paid-out length between
the hawse and the anchor's ring, and the part that would dip below the anchor lies on the seabed. When the chain is
taut it runs straight, with a bow of at most 0.1 blocks. While the anchor falls (or hangs, or is heaved up) the chain
runs straight from the hawse, because it pays out or is wound in as the anchor moves. The curve is computed every frame
from the ship's render pose, so it follows the hull without lag. The chain is lit per link from the world, so its
upper part is no longer as dark as the seabed.

Sounds (all played by the server, so everyone nearby hears the same thing; all are vanilla placeholders):

| Moment | Sound | Where |
|---|---|---|
| Chain running out (only while the paid-out length grows) and being wound in | `anchor.chain` (vanilla chain steps), every 0.2 s | hawse |
| Anchor dragging over the seabed | `anchor.chain` at about half pitch | anchor |
| The chain snapping taut: at the chain's end in a fall, or when the ship pulls a resting chain taut (once per landing; again only after the chain was more than a block slack) | `anchor.jolt` (vanilla anvil landing, pitch 0.55 to 0.65) | hawse |
| Raising | `anchor.capstan` (vanilla iron trapdoor closing, pitch about 0.8), every 0.5 s, on top of the running chain | capstan |

New config values (server, `anchor_chain`): `jolt_volume` 0.7, `capstan_volume` 0.6. `sounds = false` silences all.
Subtitles on (Options, Accessibility) help to tell the sounds apart: "Anchor chain rattles", "Anchor chain jolts
taut", "Capstan clanks".

Setup as for AN2a. Look at the chain from a boat or from a second player beside the ship, ideally under water too.

## Steps

1. **Sag at rest.** Anchor in about 8 blocks of water with a still ship (furled sails). Then sail or push the ship a few
   blocks toward the anchor, so the chain goes slack.
   - **Expected:** right after the landing, with the ship above the anchor, the chain runs almost straight down. Once
     the ship moves toward the anchor, the chain hangs in a smooth curve: down from the hawse, along the seabed for a
     stretch, and up the last two blocks to the ring on top of the anchor. No kinks, no links sticking out of the
     curve, nothing below the seabed (on flat ground; a slope can cut through the lying part, see the open problems).
2. **Swinging.** With the anchor holding and a breeze, let the ship swing round its anchor (or turn it with the helm).
   - **Expected:** the curve follows the hawse smoothly every frame, without lagging behind the hull or jumping.
     When the ship lies back on its chain, the chain straightens.
3. **Paying out during the fall.** Drop the anchor from a ship at speed (AN2a step 1), watch from the side.
   - **Expected:** the chain runs straight from the hull side to the falling anchor and grows with it; nothing is drawn
     below the anchor. The running chain rattles throughout the fall and stops when the anchor lands (the thud). Over
     water deeper than the chain (with `sailing_runtime.anchor_chain_length` lowered to e.g. 6 and the ship over deep
     water, if the drop is allowed there), the anchor stops at the chain's end with one clank and the rattle stops.
4. **Going taut and the jolt.** Same drop at speed, sails set.
   - **Expected:** shortly after the landing, when the ship pulls the chain taut, one heavy metallic clank at the hull
     side (subtitle "Anchor chain jolts taut"), and the chain is a straight line from the hawse to the anchor (a barely
     visible bow). A still ship dropping its anchor makes no jolt. Sail toward the anchor so the chain goes slack by
     more than a block, then away again: a second jolt when it comes taut.
5. **Dragging.** Set `anchor_chain.holding_force` to 1 and repeat 4.
   - **Expected:** the chain stays straight (taut) while the ship drags the anchor; a lower, grinding chain sound comes
     from the anchor about five times a second, with the puffs of seabed particles. Set `holding_force` back to 10.
6. **Raising.** Use the capstan with the anchor several blocks astern.
   - **Expected:** the chain rattles at the hull side and the capstan clanks twice a second while the chain winds in.
     The chain straightens as the anchor is pulled toward the ship. Both sounds stop when the anchor is stowed.
7. **Long chain.** Set `sailing_runtime.anchor_chain_length` to 128 and anchor in deep water (or over a deep trench).
   - **Expected:** no stutter when looking at the chain; the curve still looks smooth (it is capped at 64 segments, so
     each one is two blocks long here).

Tell us: is the sag convincing (too deep, too shallow)? Are the jolt and capstan placeholders acceptable until real
recordings exist, and are their volumes right?

# Playtest: the capstan crew (work package CRW3)

A crew member at the capstan drops and raises the anchor on order (design.md §6, §7.5). Setup: an assembled ship
afloat over ground within the chain's reach (water less than 32 blocks deep), a capstan on the deck with free deck on
at least one side, a captain's whistle, and one crew member (`/pirates crew spawn` on the deck). Config defaults:
`crew_stations.capstan.enabled = true`, `drop_ticks = 40`, `min_raise_ticks = 20`.

## Steps
1. **Manning the capstan.** Use the whistle on the crew member, then on the capstan.
   - **Expected:** "… mans the station"; the crew member stands beside the capstan (the first free side of north,
     east, south, west) and idles there. Using the capstan with the whistle in hand does not drop the anchor.
2. **Drop anchor.** Open the whistle menu (use in the air): two new entries, "Drop anchor" (capstan icon) and "Weigh
   anchor" (chain icon), between "Fire at will" and "Release crew". Choose "Drop anchor".
   - **Expected:** "Aye, letting go the anchor!"; for 2 s the crew member faces the capstan and pushes the bars
     (`capstan_push`: chest to the bars, arms forward, walking on the spot), the anchor still stowed; then the anchor
     falls and lands as for a player's drop, and the crew member goes back to idle. Choose "Drop anchor" again:
     "The anchor is out already, captain".
3. **Weigh anchor.** Choose "Weigh anchor".
   - **Expected:** "Aye, heave away!"; the capstan starts winding at once (chain rattle, capstan clank), the crew
     member pushes the bars the whole time, and stops when the anchor is stowed (at most a second after it). Choose
     "Weigh anchor" again: "The anchor is stowed already, captain".
4. **Leaving mid-raise.** Drop the anchor, weigh it, and choose "Release crew" while it is coming up.
   - **Expected:** the crew member leaves the capstan; the anchor still comes up and is stowed.
5. **The job board.** With the crew member free on deck (released) and nobody at the capstan, choose "Drop anchor".
   - **Expected:** within a second the crew member walks to the capstan by itself, pushes the bars for 2 s, and the
     anchor goes.
6. **No ground.** Over water deeper than the chain (or with `sailing_runtime.anchor_chain_length` set to 3), order
   "Drop anchor" to a manned capstan.
   - **Expected:** "No ground for the anchor within the chain's reach, captain!"; nothing moves.
7. **The command.** `/pirates crew order drop_anchor` and `/pirates crew order raise_anchor` near the ship.
   - **Expected:** as steps 2 and 3, with "Order …: 1 of 1 crew carry it out".
8. **Off switch.** Set `crew_stations.capstan.enabled = false`, give "Drop anchor".
   - **Expected:** the crew member says "No ground …" (the station's "can't" line) and nothing moves; using the capstan
     by hand still works.

Tell us: does the push read as working the capstan from beside it, or does it need the crew member to walk round the
drum? Which side does he stand on, and do his hands reach the bars?
