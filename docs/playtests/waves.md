# Playtest: waves (work package WV1)

The rules (the sea state from the weather and its easing, the wave field, the torque rule with its size scaling and
cap, the spill height, the sync payload) are covered by JUnit and GameTests: at storm the 7×17 test hull rolls
4.3° to each side, and settles within 10 s when the sea goes calm; a calm sea rolls it 0.36°; a 32×12 hull rolls
0.7° in a storm; a side hatch half a block above the waterline takes water in a storm and none in a calm sea; with
`waves.enabled = false` nothing acts. This checklist covers what only the game shows: how the rolling feels, the spray,
the camera sway and the state following real weather.

**How it works, in short.** Each dimension has a sea state: **calm** (waves up to 0.1 blocks), **moderate** (0.3),
**rough** (0.7) or **storm** (1.2), times `waves.amplitude`. Clear weather is calm or moderate (a slow noise switches
every few in-game hours), rain is rough, thunder is storm; the sea eases between states over `waves.state_change_seconds`
(60 s from calm to storm). The water surface does not move: the waves are two invisible sine trains (34 and 23 blocks
long, 9 and 6.5 s period) running with the wind. Every tick the wave height is sampled at the bow, stern and both sides
of each floating ship; the slope across the hull becomes a roll and pitch torque (Sable force group **"Waves"**), so the
ship rolls and pitches with the sea while the hull damping keeps it from building up. The highest crest around the hull
raises the sea at its openings, so a low open hatch or a low rim takes water over in rough seas. In rough and stormy
seas the bow throws spray with a splash when a crest rises over it.

Setup: `./gradlew :neoforge:runClient`, a creative world with cheats on, default config. Place a starter sloop on open
sea with `/pirates ship place starter_sloop assemble`. Turn on Sable's force display if you can (`/sable` debug, see
docs/sable-notes.md §9.2) to see the "Waves" force group. Please send screenshots or short clips of steps 2, 4 and 6,
and `latest.log` if anything goes wrong.

## Steps

1. **Read the sea.** Run `/pirates waves` (any player).
   - **Expected:** "Sea: calm (heading for calm), waves up to 0.10 blocks, coming from …°" (or moderate with 0.30). The
     direction is roughly where the wind comes from (`/pirates wind`), within about 25°.
2. **Storm on the sloop.** Stand on the deck amidships and run `/pirates waves set storm`. Watch for 30 seconds, then
   turn the sloop (or wait) so the waves come from the side, then from ahead.
   - **Expected:** the sloop starts rolling and pitching at once, smoothly, with a period of several seconds (not a
     jitter). Beam on, it rolls a few degrees to each side (the test hull rolls about 4.3° to each side; the sloop is
     bigger, so somewhat less); head on, it mostly pitches. It never capsizes and never flips from the waves alone.
     In the force display the "Waves" arrows swing back and forth.
   - Please tell us how it feels next to the heel of the sails (`sailing_runtime.sail_heel_factor`, default 0.25): set
     sail on a beam reach in the storm. Is the wave roll too weak, about right, or too strong? `waves.ship_torque`
     (default 5.5) scales it; `waves.max_torque_per_mass` (default 2) caps it for small boats.
3. **Calm again.** Run `/pirates waves set calm`.
   - **Expected:** within about 10 s the sloop lies still again (only the slow, tiny motion of the calm sea). Then run
     `/pirates waves clear`: "The sea follows the weather again".
4. **Spray at the bow.** `/pirates waves set storm`, then sail into the waves (or look at the bow from the deck while
   it pitches).
   - **Expected:** every few seconds, when the bow dips into a crest, white spray bursts up at the bow (water droplets
     and a few white puffs) with a splash sound, at most about once a second. At `/pirates waves set rough` it is
     rarer; at moderate and calm there is none. Client config `wave_effects.spray = false` turns spray and splash off.
5. **Camera sway.** Client config `wave_effects.camera_sway = true` (default off), `camera_sway_fraction` 0.5. Stand on
   the deck in the storm and look across the ship (to port or starboard), then along it.
   - **Expected:** looking across, the view tilts with the ship's heel, half as much as the deck (0.5); looking along
     the ship the view barely tilts (that motion is pitch). **Check the direction:** when the deck goes down on your
     right, the horizon should tilt as if your head went down to the right with it. If it tilts the other way, tell
     us (one sign constant, `WaveClient.ROLL_SIGN`). Off again: no tilt at all. Not aboard (swimming beside the ship):
     no tilt.
6. **A low hatch floods.** Build a small open boat or put an open trapdoor in the side of the sloop's hull about half a
   block above the waterline (one block above the water, on a hull that floats with the water just below that block).
   Keep it open, `/pirates waves set storm`, and wait a minute.
   - **Expected:** water comes in at the crests and stays in the compartment (the compartment's water rises in steps,
     and the ship sits lower). Close the trapdoor: no more water comes in. With `/pirates waves set calm` and the
     trapdoor open nothing comes in. `waves.spill = false` turns only the spilling off.
7. **The state follows the weather.** `/pirates waves clear`, then `/weather rain`, wait a minute, `/pirates waves`;
   then `/weather thunder`, wait a minute; then `/weather clear`.
   - **Expected:** rain → "heading for rough", and after about 30 s the sea is rough (the ship rocks more); thunder →
     storm after up to about a minute; clear → it eases back down to calm or moderate over about a minute. A friend on
     the server sees the same state (the sea is synced every 3 s).
8. **Waves off.** Server config `waves.enabled = false`.
   - **Expected:** `/pirates waves` says waves are off; ships lie still even in thunder, no spray, no spill.

## Known limits

- The water surface stays flat (design.md §1 non-goals): the ship rolls and pitches, but it does not heave up and down
  with the crests, and the water does not visibly move.
- The field is anchored at each ship (so a drifting wind direction cannot make the waves race far from the origin):
  the phase at a ship's middle comes from a fixed reference direction, the slope around it from the current
  direction. Two ships close together therefore see nearly, but not exactly, the same wave.
- The spill uses the highest crest around the whole hull at every opening, not the crest at that opening.
- Wave torque changes every tick, so a floating ship in waves never falls asleep in the physics engine (a cost for many
  anchored ships; see the performance check in milestone playtests).
