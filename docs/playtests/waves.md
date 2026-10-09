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

## WD1: seeing the wind and the waves

The rules (spawn rate = `wind_effects.density` × wind speed, none below `wind_effects.min_strength`, more in gusts;
foam rate = `wave_effects.foam_density` × wave height, none below `foam_min_amplitude`, kept mostly on the crests;
positions spread evenly over the ring around the camera, streaks started upwind by half their drift so they are centred
on you mid-life; the streak quad turning about its axis to face you; fade in and out) are covered by JUnit
(`SeaEffectRulesTest`). Nothing here touches the server or gameplay. Only the look needs the game.

**How it works, in short.** Every client tick, white streaks are spawned within `wind_effects.radius` (24) blocks of
the camera, 1 to `wind_effects.height` (12) blocks above sea level, only in air (not in terrain, not inside a dry hull).
Each flies in a straight line along the synced wind at the wind's speed for `life_ticks` (40, 2 s), fading in and out;
its length grows with the wind (1.2 to 4.5 blocks). Foam streaks are flat, lie on top of the water within
`wave_effects.foam_radius` (24), point along the direction the waves run (±10°), drift slowly with them and last
`foam_life_ticks` (80). Both are world particles: a sailing ship moves through them. Video setting "Particles":
Decreased halves them, Minimal hides them.

Setup: `./gradlew :neoforge:runClient`, a creative world with cheats on, default config, open sea. Place a starter
sloop with `/pirates ship place starter_sloop assemble` and stand on its deck. `/pirates wind get` shows the wind,
`/pirates wind set <fromDegrees> <strength>` holds one and `/pirates wind clear` releases it; `/pirates waves set
<state>` holds a sea. Please send
screenshots or short clips of steps 2, 3, 5 and 7, and `latest.log` if anything goes wrong.

1. **Calm.** `/weather clear`, `/pirates waves set calm`, `/pirates wind set 0 2` (a 2 blocks/s wind from the north,
   below `wind_effects.min_strength` 4).
   - **Expected:** no wind streaks in the air, no foam on the water. Spray (WV1) also none.
2. **Breeze.** `/pirates wind set 0 10` (10 blocks/s from the north), `/pirates waves set moderate`. Look around from
   the deck.
   - **Expected:** a few thin white streaks at a time drift through the air from deck height up to the masthead, all
     flying the same way, from where `/pirates wind get` says the wind comes from, about twice as fast as a sprinting
     player. They fade in and out softly (no popping), are thin from the side and short when you look along the wind.
     A few faint, ragged foam patches lie flat on the water, stretched along the waves, in loose bands.
   - Compare with the **HUD wind arrow** (bottom left while aboard): the streaks fly the way the arrow points
     (downwind). Tell us if they ever disagree.
3. **Gale and storm.** `/pirates wind clear`, `/weather thunder`, `/pirates waves set storm`. Wait 30 s (the weather
   now drives the wind, with gusts).
   - **Expected:** many more streaks, longer and faster (the wind is 2.2 times stronger); during gusts (the HUD shows
     them) a flurry of extra streaks for a few seconds. The foam is denser, longer, brighter and gathers in bands that
     move slowly with the waves. Is it too much, too little, too opaque? Tell us; `wind_effects.density`,
     `wave_effects.foam_density` tune the numbers.
4. **Direction of the foam.** Still in the storm, run `/pirates waves`: it says which way the waves come from.
   - **Expected:** the long side of the foam patches points along that direction (within about 10°), and the bands
     of foam run across it.
5. **On a sailing ship.** Set sail on a beam reach (wind from the side) in the gale, then run downwind.
   - **Expected:** the streaks keep flying with the true wind, not with the ship: on a beam reach they cross the deck
     sideways while the ship moves through them; running downwind at nearly the wind's speed they seem to hang almost
     still around you. The foam stays where it lies on the water and slides past the hull.
6. **Inside the hull.** Go below deck into a dry hold (below the waterline) in the storm.
   - **Expected:** no foam on the water inside the hold (the dry region hides the water there, and no foam spawns in
     it); no streaks born inside the hold. Looking out through a hatch, streaks and foam outside are still there.
7. **Toggles and settings.** Client config `wind_effects.streaks = false`: no streaks; `wave_effects.foam = false`:
   no foam; video setting Particles: Decreased about half, Minimal none. Turn both back on.
8. **Away from the sea.** Fly 40 blocks up, then walk inland over hills, then dive under water.
   - **Expected:** high above the sea the streaks thin out and stop (they are only spawned near the sea's band); over
     land they appear only in open air near sea level (not inside hills); under water none.
9. **Performance.** In the storm with `wind_effects.density = 0.5`, `wave_effects.foam_density = 10` (both higher than
   default), watch the F3 particle count ("P:") and the frame rate for a minute, then reset to defaults.
   - **Expected:** at defaults the count rises by a few hundred at most in a storm; the frame rate does not drop
     noticeably. At the raised values it stays playable (each kind is capped at 48 new particles per tick).

### Known limits

- The streaks use the one wind the client knows (the wind at the player); with `wind.regional_variation` on, far
  streaks still fly with the player's wind.
- The water surface stays flat, so the foam lies flat on the still water level; its bands follow the invisible crests.
- A streak keeps the wind of the moment it was born for its 2 s of life, so after a sudden shift the old streaks fly
  on briefly before the new ones take over.
- Streaks do not collide: one born over the sea may drift into a hill or through a ship's hull and sails.
- Shader packs (Iris) were not checked.
