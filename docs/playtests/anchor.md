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
