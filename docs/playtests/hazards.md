# Playtest: waterspouts and whirlpools (work package H1)

The force fields, spawn rules, sail tearing, ship pull with the mass cap, the commands and toggles are covered by
19 JUnit tests and 10 GameTests with measured drift. What only a client shows: the look, the sounds, the pull on a
player (client push payload) and how it feels.

Setup: `./gradlew :neoforge:runClient`, an ocean, a boat, a small assembled ship with sails and a large one.

## Steps
1. **Natural waterspout.** At sea: `/weather thunder`, `/gamerule doWeatherCycle false`, set
   `hazards.spawn_check_interval_ticks` 20 and `waterspouts.chance_per_check` 1.0: within seconds a spout forms 48–96
   blocks away over water and disappears after its lifetime; in clear weather or on land none form.
2. **Look.** A white funnel turning counter-clockwise, widening toward 24 blocks, spray at its foot, a low roar every
   second; `hazard_visuals.particle_density` 0.25 thins it, 0 removes the particles.
3. **Pull on you.** `/pirates hazard spawn waterspout ~5 ~ ~`: you are drawn in and lifted, hover a few blocks up and
   drop into the water when it ends; fall damage counts only the fall after leaving the funnel; creative flight is
   unaffected.
4. **Boat.** Row into it: pushed toward the axis and lifted, smoothly, without rubber-banding (this is the client
   push payload, check it closely).
5. **Small ship and sails.** Sail a small ship with full sails into a spout: it is pulled to the axis and rises
   (at the axis a small ship lifts out of the water); set sails drop a step every few seconds with a cloth-tear sound
   until furled; the winch sets them again afterwards. A large ship barely moves. Sable's force display shows
   "Sea Hazards".
6. **Whirlpool.** `/pirates hazard spawn whirlpool ~8 ~ ~` in deep water: a flat foam ring turning counter-clockwise
   around an inky centre, a gurgle every 2 s, drifting about 2 blocks per 5 s and turning away from shores.
7. **Boat dragged under.** An empty boat near the centre is pulled in and sinks; in a ridden boat you are thrown out
   after about 3 s under water.
8. **Ship pulled and turned.** A small ship near a whirlpool drifts in and orbits counter-clockwise; at the centre it
   turns slowly on the spot.
9. **Commands and toggles.** `/pirates hazard clear` removes all, `clear 10` only nearby; with `waterspouts.enabled =
   false` the spawn command refuses and existing spouts vanish; non-operators cannot use `/pirates hazard`.
10. **Persistence.** Spawn a whirlpool, save and quit, reload: still there with its remaining lifetime.
