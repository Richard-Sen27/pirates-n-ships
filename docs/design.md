# Pirates 'n' Ships — Design Spec

> Status: brainstorm → spec, v0.1 (2026-10-06). Living document: update it whenever a decision changes.
> Mod name "Pirates 'n' Ships". Mod ID: `pirates_n_ships`.

---

## 1. Vision

A Minecraft mod about sailing, piracy and life at sea. Players build **real block ships** that float, sail with the wind, take damage and sink. They crew them with NPCs, fight the Royal Navy or join it, and hunt treasure across pirate islands.

### Unique selling points
1. **Dry hulls.** Ship interiors below the waterline stay dry: no water inside, no swimming below deck.
2. **Flooding and sinking.** Hull breaches let water into compartments. Ships list, lose buoyancy and sink. The crew can bail water and patch holes.
3. **Wind-driven sailing.** A global, weather-dependent wind drives sails. Course and sail trim matter.
4. **Commandable crew.** NPCs man stations such as sails, cannons, the crow's nest and the anchor, following the captain's orders.
5. **A living sea world.** Pirate islands, seafarer villages, the navy, bounties, sharks and a rare kraken.

### Non-goals (for now)
- No visibly deforming water surface. Waves are simulated through ship motion, sound and particles.
- No dependency on Create. Optional compatibility can come later.
- No full fluid simulation. Water inside ships is a gameplay model, not a CFD model.

---

## 2. Technical foundation

| Topic | Decision |
|---|---|
| Minecraft | 1.21.1 |
| Loader | **Multiloader project.** NeoForge is the first shipped target. Fabric is planned (module exists in the build, but it is disabled until the NeoForge version is stable). |
| Java | 21 |
| Physics | **Sable** (`dev.ryanhcode.sable`) is the only hard dependency. It provides sub-levels (moving, interactive block structures), Rapier-based physics and basic buoyancy. License: PolyForm Shield 1.0.0. Depend on it, never copy or bundle its code. `common` compiles against `sable-common`, and each loader module uses its loader artifact. |
| Animated entities | GeckoLib (required dependency, to be confirmed). It is available for both loaders. |
| Config | A cross-loader config solution (e.g. Forge Config API Port), accessed only through our own `config` wrapper. See §21. |
| Mappings | Official Mojang + Parchment |
| Build | Based on the **MultiLoader-Template** (jaredlll08): `common` (vanilla only, via NeoForm), `neoforge` (ModDevGradle), `fabric` (Loom). The template's legacy Forge module was removed. |
| License | **PolyForm Noncommercial 1.0.0**: anyone may use, modify and redistribute the mod for any noncommercial purpose. |

### Dependency rule
Every new dependency must exist for **both** NeoForge and Fabric, or be optional and isolated behind a compat module. Prefer vanilla APIs (data components, codecs, `CustomPacketPayload`, GameTest) over loader APIs wherever vanilla offers something.
| Optional compat | Create (contraptions on ships), shader packs (Iris), JEI/EMI, Jade |

### Reference sources (read-only, in `refs/`, excluded from build)
- `refs/sable`: primary API reference
- `refs/create-aeronautics`: example of a real mod using Sable (assembly, forces)
- Minecraft sources generated via Gradle / IDE

---

## 3. Architecture

Package root: `com.richardsenger.piratesnships`.

### 3.1 Multiloader layout

```
common/      ~80–90% of the code. Vanilla Minecraft + sable-common only.
             All gameplay logic, block/item/entity classes, payloads, renderers,
             most mixins, datagen providers, GameTest definitions.
  └─ platform/   Service interfaces (Services.PLATFORM, .REGISTRY, .NETWORK, .EVENTS, …)
neoforge/    Thin layer: @Mod entrypoint, platform service implementations,
             registration timing, event wiring, NeoForge-only datagen extras.
fabric/      Same thin layer for Fabric (ModInitializer / ClientModInitializer).
             Disabled in settings.gradle until the Fabric port starts.
```

**Hard rules:**
- `common/` never imports `net.neoforged.*` or `net.fabricmc.*`. Since `common` compiles against vanilla only, violations fail the build.
- Loader-specific needs go through a **platform service interface** in `common/.../platform/`. Implementations live in `neoforge/` and `fabric/` and are loaded via `ServiceLoader` (`META-INF/services`).
- Event handlers are plain methods in `common` (e.g. `ShipEvents.onServerTick(server)`). Loader modules only *subscribe* and forward to them.
- Registration: `common` declares what exists (a registry helper with suppliers). The loader layer performs the actual registration at the right time.
- Networking: payload records + codecs + handlers live in `common` (vanilla `CustomPacketPayload`). Only registration and sending go through `Services.NETWORK`.
- Mixins: shared mixins go in `common` (`piratesnships.mixins.json`). Loader-specific mixins are allowed only when unavoidable, in that loader's own mixin config.
- Loader-specific *features* (e.g. NeoForge's generated config screen) are allowed, but must sit behind a platform method with a fallback on the other loader.

### 3.2 Modules
The feature modules below are packages inside `common` (and, where needed, a small matching package in each loader module for wiring).

| Module | Responsibility |
|---|---|
| `core` | Registries, config, networking, data attachments, common utilities |
| `ship` | Assembly/disassembly, ship registry, hull analysis (dry volume), flooding, buoyancy hook, damage |
| `sailing` | Wind field, weather coupling, sails, rudder/helm, anchor, oars |
| `station` | Station blocks (sail winch, cannon station, crow's nest, capstan, pump) and the shared "operate" interface used by players and crew |
| `crew` | Crew NPC base, hiring, command system, station assignment, morale/pay |
| `combat` | Weapons, ammo, cannons, projectiles, grappling hook, boarding |
| `law` | Criminal score, bounties, navy turn-in, doubloon economy |
| `world` | Structures (pirate islands, seafarer villages, navy outposts), loot tables, treasure maps |
| `entity` | Mobs (pirates, sailors, navy soldiers/officers, sharks, kraken), sea chest entity |
| `survival` | Cold water, swimming hunger, sea chest carry rules |
| `hazard` | Waves, waterspouts, whirlpools |
| `rpg` | Reputation/honor, quests, story hooks |
| `audio` | Sound events, sea ambience, shanty music manager |
| `client` | Renderers, water-mask rendering, HUD (wind indicator, ship status), config screen |

**Rule:** server-authoritative gameplay. The client only renders and predicts.
**Rule:** every gameplay-changing feature has a config toggle, and every frequency or strength has a config value (see §17).

---

## 4. Ships (core system)

### 4.1 Assembly
- A **Helm block** (Steuerrad) is the ship's anchor point. Using it while docked triggers assembly.
- Assembly collects connected blocks, excluding world terrain, with a configurable block limit. They become a Sable sub-level.
- **Disassembly** happens at the helm when the ship is stationary and aligned. Blocks are placed back into the world, snapped to the grid.
- Each ship gets a persistent **ShipData** record: UUID, name, owner, crew, flag/faction, hull analysis cache.
- Ship naming happens via the helm GUI or a name tag on the helm.

### 4.2 Hull analysis: dry volume
- Run a flood fill in ship-local coordinates from the "outside" (the bounding box shell and openings in the deck).
- Air cells the outside can't reach are **enclosed volume**, split into **compartments** (connected components).
- Openings such as hatches, doors and trapdoors count as open or closed depending on block state.
- Results are cached and recomputed only when ship blocks change (debounced). The same goes for opening and closing hatches.

### 4.3 Dry hull: rendering
- For enclosed compartments below the waterline, build a **water-mask mesh** (the inner faces of the dry volume).
- Render it with a depth-only render type before the translucent (water) layer, the same technique as vanilla's boat water patch, generalized.
- The mesh moves with the ship transform, so no chunk rebuilds are needed.
- Compatibility risk: shader packs. Plan a separate compatibility pass (Iris) later.

### 4.4 Dry hull: gameplay
- Mixins on fluid queries (entity in-water checks, fluid pushing, drowning, bubbles, item flotation, block placement checks).
- If a world position transformed into ship-local space lies in a **dry** compartment cell, it reports no fluid.
- Applies to players, mobs, items and particles.

### 4.5 Flooding and sinking
- Each compartment stores `waterLevel` (0..volume).
- **Breach:** when a hull block below the waterline is destroyed, or an opening is left open below the waterline, the compartment connects to the sea. Inflow rate scales with the opening size and its depth below the waterline.
- Water renders inside flooded compartments, either as a mask cut at the current water level or as a flat rendered surface.
- Compartments connected through open doors or hatches equalize.
- **Pump / bailing station:** removes water at a set rate. A player or crew member operates it.
- **Patching:** a repair item (planks + tar/pitch) placed in a breach closes it.
- **Buoyancy:** effective displacement = hull blocks + dry volume − flood water. Hook into or extend Sable's buoyancy, then compute list/heel from where the flooded water sits.
- A ship that loses buoyancy sinks. A sunk ship stays a sub-level resting on the seabed (with a config option to turn it back into world blocks after some time), so wrecks can be looted.

### 4.6 Damage
- Cannonballs and explosions destroy ship blocks, which may create a breach.
- There is no separate HP bar. Ship health = structural integrity + buoyancy. A HUD shows hull status and flooding per compartment.

---

## 5. Sailing

### 5.1 Wind
- A **global wind field** per dimension: a direction and strength that drift slowly over time (noise-based).
- Weather multiplier: clear ×1.0, rain ×1.5, thunder ×2.2 (configurable). Gusts appear during storms.
- Optional regional variation (noise by position), behind a config toggle.
- Synced to clients for the HUD (wind indicator) and visuals (flags and sails flutter in the wind direction).

### 5.2 Sails
- Sail blocks come in sizes (small and large square sails, plus a fore-and-aft/lateen sail). Each has an area and an efficiency curve over the angle to the wind.
- **Trim states:** furled / half / full, set via the sail winch station.
- Force = wind strength × area × trim × efficiency(angle), applied at the sail position, which also produces heel torque.
- Square sails work best downwind. Fore-and-aft sails allow sailing closer to the wind.
- No-go zone: sailing directly into the wind produces no forward force.

### 5.3 Steering and other propulsion
- **Helm:** sets the rudder angle. Rudder torque scales with the ship's speed through the water.
- **Oars:** an optional small, slow propulsion source for windless conditions or small boats, operated by crew.
- **Anchor (capstan):** a dropped anchor applies strong drag and holds position. Raising it takes time.

### 5.4 Waves (simulated)
- Sea state comes from weather: calm / moderate / rough / storm.
- Applied as roll and pitch forces on ships (configurable amplitude), plus spray particles, sounds and an optional client-side camera sway.
- Rough seas spill water over low decks into open compartments.

---

## 6. Stations

Stations are blocks on a ship that a **player or a crew member** can operate. They all share one interface: `occupy → operate → release`.

| Station | Function |
|---|---|
| Sail winch | Hoist, reef or furl the assigned sails |
| Helm | Steer (players; crew only in "hold course" mode) |
| Cannon | Load (powder + ball), aim, fire |
| Crow's nest | Extended view range; spots ships, land and monsters and reports them |
| Capstan | Raise or drop the anchor |
| Pump | Remove flood water |
| Repair kit / carpenter | Patch breaches |

The crew operates a station by being attached to it, much like being seated. This avoids complex pathfinding on moving ships. Walking between stations on deck is a later improvement.

---

## 7. Crew

### 7.1 Hiring
- Hire crew in seafarer villages (sailors), in taverns, or on pirate islands (pirates).
- Each crew member has a role skill (sailing, gunnery, carpentry, lookout), a wage in doubloons and morale.
- The crew limit per ship depends on the ship's size (number of bunks or hammocks).

### 7.2 Commands
- Issued with a **captain's whistle** (radial menu) or a **command GUI** at the helm.
- Orders:
  - hoist / reef / furl sails
  - load cannons / fire (broadside port or starboard / at will)
  - man the crow's nest
  - raise / drop the anchor
  - man the pumps
  - repair
  - prepare to board
  - all hands to stations
- Assignment: automatic by skill, or manually per station.
- Feedback: crew members respond with voice lines or text, and the HUD shows which stations are manned.

### 7.3 Crew upkeep
- Wages are paid periodically from the ship's chest. Unpaid or starving crew lose morale, and low morale leads to desertion or mutiny (mutiny behind a config toggle).

---

## 8. Combat

### 8.1 Weapons (Waffen)
| Item | Notes |
|---|---|
| Rapier (Degen) | Fast melee, good against unarmored targets |
| Pistol (Pistole) | Single shot, long reload, high damage, short range |
| Musket (Flinte) | Single shot, longer range, slower reload |
| Ammunition (Munition) | Lead shot, crafted |
| Gunpowder (Schießpulver) | Uses vanilla gunpowder or a refined variant (to be decided) |
| Grappling hook (Enterhaken) | See §8.3 |
| Cannon (Kanone) | Block, placed on ships or land, see §8.2 |
| Cannonball (Kanonenkugel) | Plus later chain shot (damages sails/rigging) and grapeshot (hits crew) |

Firearms get a reload animation, smoke and recoil. Rain reduces reliability (a misfire chance, configurable).

### 8.2 Cannons
- Operated by a player or by crew at the cannon station.
- Loading sequence: powder → ball → (ram) → ready. Then fire.
- The projectile is a physics-aware entity that damages ship blocks (§4.6) and applies an impulse to the hit ship.
- Recoil applies an impulse to the firing ship.

### 8.3 Grappling hook
- **Version 1:** throw or shoot the hook at a block (including blocks on a moving ship) and pull yourself to the hook point.
- **Version 2:** rope physics: swing, climb up and down, balance on the rope between two ships.
- The hook attaches in ship-local coordinates, so it follows the moving ship.

### 8.4 Boarding
- Grappled ships can be pulled closer (with multiple hooks or crew assistance).
- Crew with the "prepare to board" order jump over and fight.
- A ship is **captured** when its captain is defeated or all hostile crew are gone. The player then becomes the owner.

---

## 9. Mobs and NPCs

| Mob | Behavior |
|---|---|
| Pirate | Hostile to most players by default (depends on reputation). Melee and pistols. Spawns on pirate islands and pirate ships. Hireable when reputation is high enough. |
| Sailor (Matrose) | Neutral villager-like NPC in seafarer villages. Hireable. |
| Navy soldier | Patrols outposts and ships. Hostile to players with a bounty. Muskets. |
| Navy officer | Leads soldiers and accepts pirate turn-ins and bounty claims. Gives quests. |
| Shark (Hai) | Hostile in deep water, attracted to blood (injured entities in water). |
| Kraken | Rare boss (§12). |
| Sea chest | Entity, see §11. |

Models and animations use GeckoLib. Textures are 16×16-scale pixel art.

---

## 10. World and economy

### 10.1 Structures
- **Pirate islands** (Piraten-Inseln): a jigsaw structure with a camp, tavern, docks, buried treasure and a pirate captain. Pirates spawn there, and loot can be traded or fenced there.
- **Seafarer villages** (Seemannsdörfer): coastal villages with docks, a shipwright, a tavern and a harbor master. Villagers and sailors spawn there.
- **Navy outposts / forts**: a turn-in point for pirates and bounties, with patrols.
- **Wrecks**: sunken ship structures with loot.
- **Treasure maps**: lead to buried treasure chests (Schatzkisten) with special loot tables.

### 10.2 Currency and loot
- **Gold doubloons** (Golddublonen) are the main currency, used for wages, bounties, trading and hiring.
- Loot (Beute) from treasure chests, captured ships and wrecks can be sold to fences on pirate islands or traders in villages.

---

## 11. Sea chest (Seemannstruhe)
- An item and block with **double-chest capacity**.
- **Carried on the back:** while worn, the player can't jump, sprint or swim. Walking is slowed (configurable). Drowning risk applies, since the chest drags the player down.
- **Placed in water:** becomes a floating entity that drifts with currents and wind.
- **With a paddle:** a player sitting on it can paddle it like a small boat.
- Contents are preserved in every state (item, worn, block, entity).

---

## 12. Hazards and weather
- **Waves:** see §5.4.
- **Waterspouts** (Wasserhosen): spawn during thunderstorms over the ocean. They pull in and lift entities and small ships, and damage sails.
- **Whirlpools** (Wasserstrudel): rare, stationary or slowly drifting. They pull ships toward their center, apply rotational force, and can drag small boats under.
- **Kraken** (rare): spawns in the deep ocean, more likely at night and during storms.
  - Multi-part entity with tentacles that grab ship blocks, pull the ship down, destroy masts and swipe crew off the deck.
  - Weak spots on the eyes and tentacles.
  - Drops unique loot.

All hazards can be turned off individually and have frequency settings.

---

## 13. Law system

### 13.1 Criminal score
- Tracked per player and per NPC.
- Increases through crimes: attacking or killing navy or villagers, attacking neutral ships, theft from village chests, piracy (capturing non-pirate ships).
- Decays slowly over time (configurable) and can be reduced by paying fines at navy outposts.

### 13.2 Bounties (Kopfgeld)
- When the criminal score passes a threshold, the navy automatically places a bounty that scales with the score.
- Players can place bounties on other players or NPCs by paying doubloons at a notice board.
- **Claiming a bounty:** defeat the target and bring a proof item, or capture the target alive (shackles) and deliver them to a navy officer.
- **Pirate turn-in:** captured pirate NPCs can be delivered to the navy for doubloons.
- Bounty notices are posted on notice boards in villages and outposts.

---

## 14. Survival
- **Cold water:** in cold and frozen ocean biomes, being in water builds up a freezing meter (similar to powder snow). It ends in freezing damage. Boats, dry hulls and warming items prevent it.
- **Swimming hunger:** swimming increases exhaustion (configurable multiplier).

---

## 15. RPG layer
- **Reputation:** separate scores with Pirates, Navy and Villagers. Actions shift them. Reputation affects prices, hiring, hostility and available quests.
- **Honor/status:** a captain's rank based on deeds (ships captured, bounties claimed), shown in the ship's flag or title.
- **Quests** (Aufträge) from navy officers, harbor masters and pirate captains: escort, deliver cargo, hunt a ship, find treasure, kill a monster.
- **Story (optional, later):** a light questline, e.g. a legendary pirate, a cursed treasure and the kraken.

---

## 16. Audio and ambience
- Sounds for creaking hulls, sail flapping, rope, cannon fire, pistols, splashes, flooding water, wind, the crew and the kraken.
- **Music:** a sea music manager plays ambient and shanty tracks while at sea, combat music during ship battles, and calm tracks in harbors. Music discs.
- **Licensing:** traditional shanties are public-domain songs, but only use our own recordings or recordings with a compatible license.

---

## 17. Configuration (Einstellungen)

All gameplay settings live in the **server config** (synced to clients). Audio and visuals live in the **client config**. Config values are defined once in `common` and read through our own `config` wrapper, so the backing library can be swapped. The in-game config screen uses NeoForge's generated config UI on NeoForge. On Fabric, use a Mod Menu integration or a simple fallback screen (behind a platform method).

| Group | Toggles and values |
|---|---|
| Ships | max block count, assembly enabled, sinking enabled, wreck persistence time |
| Flooding | enabled, inflow rate, pump rate |
| Wind | variability, weather multipliers, regional variation on/off |
| Waves | enabled, amplitude, camera sway (client) |
| Hazards | waterspouts / whirlpools / kraken: enabled + frequency each |
| Crew | wages on/off, mutiny on/off, max crew multiplier |
| Combat | firearm misfire in rain, cannon block damage on/off, damage multipliers |
| Law | criminal score enabled, decay rate, bounty threshold, player bounties on/off |
| Survival | cold water on/off + time to freeze, swimming hunger multiplier |
| World | structure spacing / frequency per structure type, mob spawn weights |
| Audio (client) | music on/off, music volume, ambience volume |

---

## 18. Multiplayer, performance and compatibility
- All gameplay state is server-side, with ship and crew state synced through custom payloads. Hull analysis runs on the server. Mask meshes are built on the client from synced compartment data.
- **Budgets:** hull analysis is incremental and off the hot path. No per-tick full ship scans. Crew AI is throttled when no player is nearby.
- **Compatibility targets:** Create (optional), Iris/Oculus shaders (water mask), Jade/WTHIT, JEI/EMI.

---

## 19. Testing strategy
- **GameTests** for all logic: assembly/disassembly, hull analysis (known hull shapes → expected compartments), flooding rates, criminal score/bounty thresholds, station operate logic, config toggles actually disabling features.
- GameTests are defined in `common` (vanilla GameTest framework). They run via the NeoForge game test server, and later also via Fabric's runner.
- **Datagen** for all JSON: models, blockstates, recipes, loot tables, tags, lang, worldgen. Providers live in `common` where possible, and generated resources are output into `common/src/generated/resources` so both loaders ship them.
- **CI** (GitHub Actions): build every enabled loader module on each push. Once Fabric is enabled, a Fabric build failure blocks merges just like a NeoForge one.
- **Manual playtest checklist** per milestone, with screenshots and logs fed back into Claude Code.

---

## 20. Roadmap

| # | Milestone | Done when |
|---|---|---|
| 0 | Project setup | MultiLoader-Template builds. `common` + `neoforge` enabled. Sable loads in the NeoForge dev client. A test block registered from `common` and a GameTest from `common` both run. Platform service skeleton exists. |
| 1 | **Spike: assembly** | A helm assembles a small hull into a floating sub-level, which disassembles back to blocks |
| 2 | **Spike: dry hull** | No water renders inside the hull, and the player doesn't swim below deck |
| 3 | **Spike: wind + sails** | A sail moves the ship relative to the wind, the helm steers, the anchor holds |
| 4 | **Spike: crew station** | An NPC attached to the sail winch hoists the sails on command while the ship moves |
| 5 | Config framework + weapons | All §8.1 items, the config screen works |
| 6 | Flooding + damage | Cannon damage → breach → flooding → sinking, plus pump and patch |
| 7 | Cannons + grappling hook v1 + boarding | Full ship-to-ship combat loop |
| 8 | World | Islands, villages, outposts, wrecks, treasure maps |
| 9 | Mobs | Pirates, sailors, navy, sharks |
| 10 | Law + economy | Criminal score, bounties, doubloons, turn-ins |
| 11 | Crew command system | Hiring, all orders, wages and morale |
| 12 | Sea chest + survival | §11, §14 |
| 13 | Weather and hazards | Waves, waterspouts, whirlpools |
| 14 | Audio | Sounds, music manager |
| 15 | RPG + kraken | Reputation, quests, the boss |

| 16 | Fabric port | Enable `fabric/`, implement platform services, all GameTests pass on both loaders, CI builds both |

Spikes 1–4 are throwaway-quality prototypes that prove feasibility. They may live in `neoforge/` while exploring Sable, but must be moved into `common` (behind platform services) before milestone 5.

---

## 21. Open questions
- What exactly does Sable's API offer for assembly, applying forces and custom buoyancy? (Read `refs/sable` + wiki: "Block Physics Properties", "Dimension Physics Data", "Working with Entities".)
- Can buoyancy be overridden per ship (needed for dry volume and flooding), or does it have to be applied as an external force?
- Should gunpowder be vanilla, or a custom refined variant?
- Should there be a Navy career path for players (join the navy instead of pirating)?
- Should ships be buildable freely, or use blueprints / shipwright NPCs?
- Config library: Forge Config API Port (NeoForge's config API on Fabric) vs. another cross-loader config library. Decide before milestone 5. Either way, access it only through our wrapper.
- Does Sable's API differ between `sable-common` and the loader artifacts (e.g. events or registration only on the loader side)? Check in `refs/sable` during milestone 0.
- Do the dry-hull rendering mixins target the same classes on both loaders? Fabric has no NeoForge render patches, so some hooks may need loader-specific variants.
