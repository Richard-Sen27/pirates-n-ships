---
navigation:
  title: "Configuration"
  parent: index.md
  position: 130
  icon: minecraft:comparator
---

# Configuration

Open it in game under Mods → Pirates 'n' Ships → Config. Gameplay settings are in the server config, which is synced to
clients. Every feature has a switch and every strength or rate has a value.

| Section | What it controls |
|---|---|
| `assembly` | Assembly on/off, block limit, how still and level a ship must be to disassemble, water handling. |
| `dry_hull` | Dry hull on/off, buoyancy of the dry volume, weight of flood water. |
| `flooding` | Flooding on/off, inflow rate; bilge pump on/off, rate, reach, use time and exhaustion; hull patch on/off. |
| `wind` | Wind strength range, how fast it changes, weather multipliers, gusts, regional variation. |
| `sailing` | Sail force, rudder strength and turning authority (`rudder_force_factor`), keel drag, anchor strength, roll and pitch damping. |
| `anchor_chain` | Chain speeds, travel time limits, anchor sounds and volumes. |
| `hull_creaking` | Creaking on/off, how often, volume and pitch ranges, the rolling rate that counts. |
| `audio` (client) | Music on/off and volume, the gap between tracks, shanties aboard. |
| `grapple.launch` | Musket launch on/off, speed, gravity factor and rope length, `offhand_required`. |
| `grapple` | Grappling hook on/off, throw speed, rope length, haul force and damping, hold distance and slack, shore pull, entity damage, lost-hook rule, `latch_world_blocks`, `latch_own_ship`, `slide.grab_cooldown_ticks`. |
| `cannons` | Cannons on/off, damage, muzzle speed, gravity, reload, elevation range and steps, blocks per hit, recoil and impact impulses, ball lifetime and water behaviour, `mobGriefing` and spawn protection, drops from destroyed blocks, glancing hits and the bounce angle. |
| `cannons.swivel` | Swivel gun on/off, ammo item and count, damage, muzzle speed, reload, blocks per hit, recoil and impact impulses, ball lifetime, elevation limits, aim reach. |
| `cannons.chain_shot` / `cannons.grapeshot` | Chain shot and grapeshot on/off, range factor, spread, damage; chain shot's cloth radius, rigging damage and mend time; the pellet count. |
| `mobs` | Mob types on/off and peaceful, hostility toggles, detection and fight ranges, skill tiers, musket timings and ammo, shove, drops. |
| `hazards` / `hazard_visuals` (client) | Waterspouts and whirlpools on/off, spawn chances and interval, distance band, lifetimes, radii, pull, lift, spin, drag-down, drift, sail tearing, ship force scale and mass cap; particle density and sounds. |
| `mobs.kraken` / `hazards.kraken` | Kraken on/off and chance per day; detection, grips, tentacle health and regrow, weak spots, strike and swipe intervals, damage, retreat. |
| `chart` / `chart_visuals` (client) | Charts on/off, cell size, sampling radius and interval, shallow depth, the cell cap, opening without the item, other players visible, marker cap; doodles. |
| `lookout` | Crow's nest lookout on/off, calls on/off, range, scan interval, how long a sighting is remembered, the land rays. |
| `mobs.shark` | Shark on/off and peaceful, spawn weight and group (server restart), detection, circle and give-up times, bite cooldown, damage and knockback, frenzy threshold. |
| `melee_hud` (client) | Stamina bar on/off, position (tight above the hotbar or left of it), scale, offsets, opacity, fade when full and its timing. |
| `melee_animations` (client) | Sword animations on/off, first-person mode, layer priority. |
| `melee_input` / `melee_hud` (client) | Hold-to-thrust and parry-tap thresholds; stamina bar on/off, scale and offsets. |
| `firearms.aim` / `firearm_view` (client) | Minimum hold, steady time and aimed spread factor, sneak lowers the gun; musket zoom. |
| `firearms` | Firearms on/off, `fire_on_attack` (left-click fires; off = release to fire), per gun: damage, muzzle velocity, spread, reload time, recoil; ball lifetime and gravity, cooldown, gunpowder use. |
| `sailing_runtime` | Sailing forces on/off, heel scaling, steering and anchor on/off, rudder steps, chain length. |
| `crew_stations` | Crew stations on/off, time per trim step. |
| `ship_screen` | The ship screen at the helm on/off (off: sneak-use disassembles at once), its reach, how often it refreshes. |
| `flags` | Hoisting delay, flags following the wind at its exact angle (land and ship check intervals), banners as flags. |
| `dry_hull` | Also: whether slabs, stairs and hatches are drawn dry in their empty half. |
| `sea_chest` | Sea chest on/off, worn speed, sink pull, wind drift and its cap, draft; paddling on/off, speed, backing speed, turn rate, hunger. |
| `survival` | Cold water on/off and freeze rate, warm effect length, swimming hunger multiplier. |
| `provisions` | Consumption, rations, spoilage, scurvy, rum, water barrel capacity, rain refill. |
| `cargo_trade.market_backend` | Desk reach, maximum trade quantity, refresh interval of open market screens. |
| `cargo_trade` | Container sizes, prices, price recovery, contracts, plunder on/off and the fence's discount, port fees, cargo weight. |
| `law` | Criminal score, severity of each crime, decay, fines, bounties, crime detection, theft; `law.bounty`: officer turn-ins on/off and delivery range, notice boards on/off and reach. |
| `flags_brig` | False-colors detection, NPC surrender, capturing players, prisoner escapes. |
| `brig` | Capture threshold, leading distances, cell size, escape chance, ransom. |
| `melee` | Skill-based sword fighting: parry window, stamina, stagger, feint recovery, NPC feints on/off, sword sounds on/off and volume. |
| `core` | Debug logging. |
| `ships`, `waves`, `hazards`, `crew`, `combat`, `survival`, `world`, `world_simulation`, and the client sections `audio` and `wave_effects` | Settings for features that are not built yet. They do nothing so far. |

Many defaults are first guesses that need playtesting. `progress.md` lists the ones to review.
