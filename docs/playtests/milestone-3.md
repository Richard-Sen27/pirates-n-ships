# Playtest: milestone 3, sailing (spike 3)

Part 1 (work package D3a) covers sails, the winch, wind and keel. Part 2 (helm steering and the anchor) follows below.
Commands need operator rights (cheats on). Report screenshots, the chat output of `/pirates ship forces` and `logs/latest.log`.

## 0. Setup
Same world and boat as `milestone-1.md` §0 and §1 (open boat 7 long along X, helm `facing=west`). **Bow convention:**
the bow is where the helmsman looks, i.e. the opposite of the helm's `facing`. A `facing=west` helm means the
**bow points east (+X)**. Before assembling, add a mast, a sail and a winch (coordinates relative to the same spot as
in milestone 1):
```
/fill ~6 ~-2 ~ ~6 ~4 ~ oak_fence
/fill ~6 ~5 ~-1 ~6 ~5 ~1 pirates_n_ships:yard[axis=z]
/fill ~6 ~2 ~-1 ~6 ~2 ~1 pirates_n_ships:yard[axis=z]
/setblock ~4 ~-2 ~-1 pirates_n_ships:sail_winch
```
The two yards run across the ship (axis z, the bow points +X); the upper one at y+5 is the sail's head, the lower one
at y+2 its foot, 3 blocks apart with fence (mast) between their middle blocks, area 3 × 3 = 9. A fore-and-aft sail
(still one block) stands along the hull, so it faces north or south here. Directions are visual only (the crew is
assumed to trim the sail optimally). Then use the helm to assemble (milestone-1 §4).
- Expected: a rolled cloth bundle hangs under the upper yard; the lower yard shows nothing. Clicking the lower yard
  says it heads no sail.

## 1. Wind command
1. `/pirates wind set 270 6` (wind **from** the west, 6 blocks/s; 0 = from the north, 90 = from the east).
2. `/pirates wind get`. Expected: "Wind from 270.0° at 6.0 blocks/s (fixed by command)".
3. `/pirates wind clear`, `/pirates wind get`. Expected: "(natural)". Set it back to `270 6`.

## 2. Hoist the sail, sail downwind
1. Use the winch once. Expected: action bar "Sails set: half sail (1 sails)", the cloth lowers smoothly (about one
   second) to half way between the yards. Again: "full sail", the cloth reaches the lower yard. Again: "furled", it
   rolls back up under the upper yard. Set it to full.
2. Expected: the boat accelerates east (bow first) over a few seconds and settles at a steady speed. In the GameTest
   (5x4x5 hull, 42.5 kpg, small square sail, 6 blocks/s astern) it peaked at ~1 m/s after 2 s and averaged 0.42 m/s
   over the next 5 s. Note the speed you see (F3 coordinates over 10 s, or `/pirates ship forces`).
3. Clicking the upper yard with the empty hand also cycles its trim (config `sail_block_trim`). Both faces of the
   cloth are visible and it bellies to the downwind side (east); change the wind and watch it flip after a moment.

## 3. Wind on the beam and from ahead
1. `/pirates wind set 0 6` (from the north, on the port beam). A square sail: slow forward drive and some drift south.
2. Remove the two yards and put `pirates_n_ships:fore_and_aft_sail[facing=north,trim=full]` on top of the mast
   (break and place, the ship picks it up). Expected: clearly faster than the square sail on this course, mostly forward, little sideways drift
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
Build a longer hull (e.g. 13 x 7) with two masts: a square sail from two 5-wide yards 5 blocks apart (area 25) on one,
a `fore_and_aft_sail` on the other, one winch. Expected: the winch sets both sails; the ship is slower to accelerate
(more mass) but reaches a similar top speed; `/pirates ship forces` lists `sail[0]:square` with area 25.

## 6b. Yard sails up close (F5a)
On land is fine for all of this except step 5.
1. **Gap rules.** Put a stone block into the mast between the two yards. Expected: the cloth disappears within about a
   second and comes back when the stone is removed. Move the lower yard one block sideways: no sail. Turn it 90°
   (axis x): no sail. Put it only one block below the upper yard: no sail (minimum gap 2); nine below: no sail (maximum 8).
2. **Two sails on one mast.** Yards at y, y−3 and y−6. Expected: two cloths; the middle yard is the foot of the top sail
   and the head of the lower one. Clicking the middle yard cycles the lower sail.
3. **Uneven yards.** A 5-wide upper yard over a 3-wide lower yard. Expected: a trapezoid cloth; `/pirates ship forces`
   on a ship shows area 4 × gap.
4. **Look.** The yard is a thin stripped-spruce spar centred on the mast; the cloth clears a full-block log mast, the
   texture is not badly stretched, the furled roll shrinks as the sail lowers. Note anything that looks wrong.
5. **On a ship.** With the rig of §0 assembled and moving: the cloth follows the ship without jitter and is not culled
   when the upper yard's middle block is off screen (look away from the mast while the sail is still in view).
6. **Whistle menu icons:** hoist shows a yard, reef shows white wool (`milestone-4.md` §9).

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

### 4. The anchor: stowed, dropped, raised (visible anchor, F4)
The anchor is now an object of its own. Placeholder look: a dark iron anchor about two blocks tall (ring, stock,
shank, two arms with flukes) and a chain drawn with the vanilla chain texture. Placeholder sounds: vanilla chain
steps (running chain), the heavy splash (entering the water) and stone breaking (landing). Keep subtitles on
(Options > Accessibility) to tell them apart.

1. Look at the ship from the deck and from a boat beside it, before using the capstan.
- Expected: the anchor hangs **outside the hull** next to the capstan, on the nearer side across the ship (starboard
  on a tie), its ring just below deck level, arms along the ship's length, flat against the planks. It moves, heels
  and turns with the ship without lagging or jittering. Breaking the capstan makes it disappear; placing a capstan
  again brings it back within a moment.
2. Full sail, ship moving. Use the capstan.
- Expected: action bar "Anchor dropping to the ground N blocks below, holds in X s", with X = N / 6 blocks per
  second (at least 1 s). The anchor leaves the hull and runs straight down with a chain from the hull side to its
  ring, the chain rattling all the way. When it enters the water: a splash sound and a ring of splash particles
  (bubbles below). When it hits the sea floor: a dull thud, a puff of floor particles, and the rattling stops. The
  ship slows while the chain runs out and holds fully on the tick the anchor lands (F3 on the capstan:
  `anchor=dropping`, then `anchor=holding` at the moment of the thud). It stops within about 2-3 blocks of the
  anchor point and swings round. `/pirates ship forces`: "anchor holding, hold 1.00".
- Check from a boat or swimming next to the ship: the chain stays attached to the hull side while the ship swings,
  and runs diagonally down to the anchor on the floor.
3. Use the capstan again.
- Expected: "Raising the anchor, stowed in X s" (N / 2.5 blocks per second). The chain rattles, the anchor rises
  along the chain, splashes out of the water, and settles back into its place at the hull side; `anchor=raised` at
  that moment. Use it mid-way to drop again: the anchor turns round where it is and runs down again.
4. Tell us: does the anchor look like an anchor from the deck, is two blocks the right size, does it clip into the
  hull (a hull that is wider below the deck than at the deck edge will clip), are the sounds too loud or too
  frequent (`anchor_chain.chain_volume`, `splash_volume`, `thud_volume`), are the speeds right
  (`anchor_chain.drop_speed` 6, `raise_speed` 2.5 blocks per second)?

### 5. Deep water and relog
1. Sail (or `/tp`) over water deeper than 32 blocks below the hull side and use the capstan.
- Expected: "No ground within 32 blocks below: the anchor would not hold". The anchor stays hanging at the hull side,
  nothing runs out, no sound.
2. Drop the anchor in water about 20 blocks deep and, while it is still running out, save and quit, then rejoin.
- Expected: the anchor is back on its chain where it was (or a little further) and finishes the drop; the ship holds
  when it lands. Repeat with the anchor holding: after the relog it lies on the floor, chain to the hull, the ship
  still held. Raise it: it comes back and is stowed at the hull side.
3. Drop the anchor, then sail away from the area (far enough for the ship to unload) and come back, or relog far
  away and fly back. Expected: no anchor left behind in the water without its ship; with the ship back, the anchor
  is on its chain again.

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
