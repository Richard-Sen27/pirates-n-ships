# Playtest: milestone 3, sailing (spike 3)

Part 1 (work package D3a) covers sails, the winch, wind and keel. Part 2 (helm steering and the anchor) follows below.
Commands need operator rights (cheats on). Report screenshots, the chat output of `/pirates ship forces` and `logs/latest.log`.

## 0. Setup
Same world and boat as `milestone-1.md` §0 and §1 (open boat 7 long along X, helm `facing=west`). **Bow convention:**
the bow is where the helmsman looks, i.e. the opposite of the helm's `facing`. A `facing=west` helm means the
**bow points east (+X)**. Before assembling, add a mast, a sail and a winch (coordinates relative to the same spot as
in milestone 1):
```
/fill ~6 ~-2 ~ ~6 ~1 ~ oak_fence
/setblock ~6 ~2 ~ pirates_n_ships:small_square_sail[facing=east,trim=furled]
/setblock ~4 ~-2 ~-1 pirates_n_ships:sail_winch
```
A square sail hangs across the ship, so it faces the bow or the stern; a fore-and-aft sail stands along the hull, so
it faces north or south here. The facing is visual only (the crew is assumed to trim the sail optimally).
Then use the helm to assemble (milestone-1 §4).
- Expected: the sail shows the "furled" texture (a rolled bundle under the yard).

## 1. Wind command
1. `/pirates wind set 270 6` (wind **from** the west, 6 blocks/s; 0 = from the north, 90 = from the east).
2. `/pirates wind get`. Expected: "Wind from 270.0° at 6.0 blocks/s (fixed by command)".
3. `/pirates wind clear`, `/pirates wind get`. Expected: "(natural)". Set it back to `270 6`.

## 2. Hoist the sail, sail downwind
1. Use the winch once. Expected: action bar "Sails set: half sail (1 sails)", the sail texture shows the upper half set.
   Again: "full sail", full canvas. Again: "furled". Set it to full.
2. Expected: the boat accelerates east (bow first) over a few seconds and settles at a steady speed. In the GameTest
   (5x4x5 hull, 42.5 kpg, small square sail, 6 blocks/s astern) it peaked at ~1 m/s after 2 s and averaged 0.42 m/s
   over the next 5 s. Note the speed you see (F3 coordinates over 10 s, or `/pirates ship forces`).
3. Using the sail block itself also cycles its trim (config `sail_block_trim`).

## 3. Wind on the beam and from ahead
1. `/pirates wind set 0 6` (from the north, on the port beam). A square sail: slow forward drive and some drift south.
2. Replace the sail with `pirates_n_ships:fore_and_aft_sail[facing=north,trim=full]` (break and place, the ship picks
   it up). Expected: clearly faster than the square sail on this course, mostly forward, little sideways drift
   (GameTest at 3 blocks/s: 0.38 m/s forward, 0.11 m/s sideways; with `keel_enabled=false`: 0.33 forward, 0.26 sideways).
3. `/pirates wind set 90 6` (from ahead). Expected: no forward motion; the boat is pushed slowly backward
   (GameTest: −0.16 m/s).
4. Furl (winch until "furled"). Expected: the boat coasts to a stop; no force from the sail.

## 4. Debug output
Stand on the deck: `/pirates ship forces`. Expected: one line with speed (fwd/port), heading (compass, 90 = east),
bow, wind, apparent wind angle off the bow (0 = from ahead), submerged fraction (1.0 afloat), sail count; then one line
per contributor (`sail[0]:small_square`, `keel`) and a `total`, forces in N along fwd/port/up, torques roll/pitch/yaw.
The printed torques are the physical ones; roll and pitch are applied scaled by `sail_heel_factor`.

## 5. Heel, bobbing, land
1. Beam reach at 6 and 10 blocks/s: watch the heel. Report whether the boat heels visibly, bobs, or capsizes.
2. Run the boat aground (or assemble one on land) with a full sail. Expected: the wind does not push a ship that is
   not afloat (`sails_need_water=true`), and there is no keel force; the ship does not slide.

## 5b. Stability (the biggest open question, added after the test investigation)
Headless measurements say small hollow hulls barely right themselves. Please check how that looks, because it decides
whether ships need ballast, a keel block or a correction in the mod.
1. Use the small boat from the setup (about 5×5, 4 high, all planks, deck, helm at the stern, mast amidships) and
   assemble it in calm water with the sail furled.
   - **Measured headlessly:** it lies about 20° bow up at rest.
   - Report what you see: level, bow up, or listing to a side. A screenshot from the side helps.
2. Fix the wind from astern at 6 (`/pirates wind set <bearing> 6`) and hoist the small square sail fully.
   - **Measured headlessly:** it runs 35 to 46° bow down, and with the rudder midships it wanders a few degrees off
     course in 10 seconds, to either side.
   - Report whether the bow digs in, whether water comes over the deck, and whether it holds a course.
3. Disassemble, replace the bottom layer of the hull with stone, assemble again and repeat steps 1 and 2.
   - **Measured headlessly:** about 16° bow down under sail, and it holds its course (under 0.25° in 10 seconds).
     Full rudder then turns it about 9° in 10 seconds at about 0.5 m/s.
   - Report whether this boat feels right.
4. Build a longer hull (for example 5×12) without ballast and repeat. Longer hulls should be much steadier.
   - Report at what size an unballasted wooden hull starts to feel acceptable.

## 5c. Rolling and creaking (F1, added after the first playtest)
The endless rocking of a floating ship is now damped, and a rolling ship's planks creak now and then. The creak uses
vanilla placeholder sounds (the creaky wooden door opening and the chest lid, pitched down), so judge the timing and
loudness, not the recording.
1. Assemble a ship in calm open sea with the sails furled and stand on the deck or next to it. Give it a roll kick:
   `/sable physics impulse @n angular 30 0 0 global` (the same command as milestone 1; use a larger number for a bigger
   ship, and `0 0 30` if the ship's bow points east or west).
   - **Expected:** it rolls over and back once or twice and then lies still within about 3 seconds. It should not look
     glued to the water (it does swing over), and it should not keep rocking.
   - **Measured headlessly** (a 7×17 plank hull of 180 kpg in a basin): with damping it swings 14° and settles within
     about 3 s; with `hull_damping_enabled=false` it swings 18°, −7°, +3° and rocks for about 8 s.
   - Report: how long it rocks, and whether it feels too stiff, about right or still too lively.
2. `/pirates ship forces` while it rolls lists a `hull_damping` line (a pure torque in roll and pitch, no force).
3. Listen while it rolls: now and then a quiet wooden creak from somewhere on the hull, more often while it rolls
   hard, never more than one every 2 seconds. Subtitles (Options → Accessibility → Show Subtitles) show "Ship creaks".
   - **Expected:** no creak at all while the ship lies still. Sailing on a beam reach with a heel that changes
     (gusts, `/weather thunder`) also creaks now and then.
   - Report: too frequent, too rare or about right; too loud or too quiet; whether the sound seems to come from the
     ship (it should come from random points of the hull, not from one spot).
4. Optional: set `hull_damping_enabled=false` in `[sailing]` and repeat step 1 to compare.

Tuning values (server config, `serverconfig/pirates_n_ships-server.toml`):

| What | Values [section] |
|------|------------------|
| Rocking dies down too slowly / too fast | `roll_damping`, `pitch_damping` (default 1.5 each, about the decay rate per second), `hull_damping_enabled` [sailing] |
| Creaks too often / too rarely | `creaks_per_second` (0.2), `min_interval_ticks` (40), `roll_rate_threshold` (0.05 rad/s, about 3°/s) [hull_creaking] |
| Creaks too loud / too quiet / too high | `min_volume` (0.25), `max_volume` (0.6), `min_pitch` (0.5), `max_pitch` (0.8) [hull_creaking] |
| No creaking at all | `enabled` [hull_creaking] |

The creak volume follows the game's "Blocks" volume slider (it is played by the server like any block sound); the
client setting `audio.ambience_volume` does not affect it yet.

## 6. A second, bigger ship
Build a longer hull (e.g. 13 x 7) with two masts and a `large_square_sail` plus a `fore_and_aft_sail`, one winch.
Expected: the winch sets both sails; the ship is slower to accelerate (more mass) but reaches a similar top speed.

## Tuning questions (server config, section in brackets)
| Question | Config value |
|---|---|
| Is the speed right? Too slow or too fast in a steady wind | `sail_force_scale` [sailing], wind `min_strength`/`max_strength` [wind] |
| Does it heel too much or capsize on a beam reach? | `sail_heel_factor` [sailing_runtime] (0.25; 1 = physical) |
| Does it slide sideways on a beam reach? | `keel_lateral_drag` [sailing] (8.0) |
| Does it coast too long or stop too fast after furling? | `keel_longitudinal_drag` [sailing] |
| Does it turn by itself / spin too freely? | `keel_yaw_drag` [sailing] |
| Is half sail useful? | `half_trim_factor` [sailing] |
| Storm: is it fun or uncontrollable? (`/weather thunder`, `/pirates wind clear`) | `thunder_multiplier`, `gust_strength` [wind] |
| Does a barely floating ship get too little keel? | `full_draft` [sailing_runtime] |

## Part 2: helm and anchor

Work package D3b. Same ship as part 1 (helm at the stern, a square sail), plus a **capstan** on deck, ideally near
the stern (`/give @s pirates_n_ships:capstan`). Assemble at the helm. Wind fixed from astern:
`/pirates wind set <bearing your bow points away from> 6`. Use `/pirates ship forces` while standing on deck: its
second line shows `rudder ... (step n, x°), anchor ...` and the force lines include `rudder` and `anchor`.

**How the helm works now:** on an assembled ship, plain use (right-click, any hand) steers: click the **right third**
of the wheel as seen by the helmsman (standing on the side the helm faces, looking towards the bow) = one step to
starboard, the **left third** = one step to port, the **middle** = midships. Three steps per side up to 35°. The
action bar shows "Rudder 2 of 3 to starboard (23°)" or "Rudder midships". **Sneak-use with an empty hand
disassembles** (spike-1 refusals unchanged). On land, plain use still assembles. F3 on the helm shows `rudder=0..10`
(5 = midships).

### 1. Steer both ways under sail
1. Set full sail (winch), let the ship gather way (~10 s, `/pirates ship forces` fwd > 0.3 m/s). Click the right third
   of the wheel three times.
- Expected: action bar "Rudder 3 of 3 to starboard (35°)". The bow swings to starboard (clockwise seen from above),
  slowly: in the GameTests the 5x4x5 hull at 0.3 m/s turned 6 to 8° in 10 s. Bigger and faster ships turn faster.
2. Click the middle, then the left third three times.
- Expected: "Rudder midships", the turn stops (heading holds within a few degrees); then the bow swings to port.
- Screenshot of `/pirates ship forces` with the rudder hard over (the `rudder:` line has a non-zero yaw torque).

### 2. Sail a circle
1. Hard to starboard and keep the sail set for 1-2 minutes. Note: as the bow comes into the wind, a square sail stops
   drawing, so the ship may stall head to wind. Tell us whether it gets around.
- Expected: the ship moves on a curve and the heading keeps changing in one direction while it has way on.

### 3. Steer at rest
1. Furl the sails, wait until the ship lies still, put the rudder hard over.
- Expected: the ship does not turn (the rudder needs speed). It may creep by a fraction of a degree while it settles.

### 4. Drop the anchor under sail
1. Full sail, ship moving. Use the capstan.
- Expected: action bar "Anchor dropping to the ground N blocks below, holds in 2.0 s". F3 on the capstan:
  `anchor=dropping`, then `anchor=holding`. The ship slows within ~2 s and stops within about 2-3 blocks (rode slack 2
  blocks) of the point below the capstan, then swings round so the capstan points into the wind (a bow capstan turns
  the bow to the wind; a stern capstan keeps the stern to the wind). `/pirates ship forces`: "anchor holding, hold
  1.00, x blocks from the anchor point" with x below ~3.
2. Use the capstan again.
- Expected: "Raising the anchor, stowed in 5.0 s", `anchor=raising`, the ship starts moving while the hold ramps out,
  `anchor=raised` after 5 s. Use it mid-way to drop again: it reverses from where it was.

### 5. Deep water
1. Sail (or `/tp`) over water deeper than 32 blocks below the capstan and use it.
- Expected: "No ground within 32 blocks below: the anchor would not hold". Nothing changes.

### 6. Disassemble with sneak-use
1. Furl, wait until still, plain-use the helm: it only steers. Sneak-use with a stick in hand: it steers too. Sneak-use
   with an empty hand: it disassembles (or refuses while moving or tilted, as in milestone 1).

### 7. Relog
1. Rudder at "2 of 3 to port", anchor holding. Save and Quit, reopen.
- Expected: F3 on the helm still `rudder=3`, the anchor still holds (ship stays put, capstan `anchor=holding`,
  `/pirates ship forces` "anchor holding").

### Tuning questions (server config, `serverconfig/pirates_n_ships-server.toml`)
| Question | Value |
|---|---|
| Does the ship turn too slowly or too fast? | `rudder_strength` [sailing] (also `keel_yaw_drag`) |
| Are three rudder steps per side right? Is 35° enough? | `rudder_steps` [sailing_runtime], `max_rudder_angle` [sailing] |
| Does the anchored ship drift too far, or snap back too hard? | `anchor_slack`, `anchor_stiffness`, `anchor_damping` [sailing] |
| Does the anchor stop a ship under full sail? | `anchor_max_acceleration` [sailing] |
| Do dropping (2 s) and raising (5 s) feel right? | `anchor_drop_ticks`, `anchor_raise_ticks` [sailing] |
| Is a 32-block chain right? | `anchor_chain_length` [sailing_runtime] |
| Switch the features off | `steering_enabled`, `anchor_enabled` [sailing_runtime] |
