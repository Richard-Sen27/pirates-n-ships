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
(to be written by the next work package)
