# Pirates 'n' Ships — Design Spec

> Status: brainstorm → spec, v0.3 (2026-10-06, adds flags, ship customization, cargo, trade, brig and provisions). Living document: update it whenever a decision changes.
> Mod name "Pirates 'n' Ships". Mod ID: `pirates_n_ships`.

---

## 1. Vision

A Minecraft mod about sailing, piracy and life at sea. Players build **real block ships** that float, sail with the wind, take damage and sink. They crew them with NPCs, fight the Royal Navy or join it, and hunt treasure across pirate islands.

### Unique selling points
1. **Dry hulls.** Ship interiors below the waterline stay dry: no water inside, no swimming below deck.
2. **Flooding and sinking.** Hull breaches let water into compartments. Ships list, lose buoyancy and sink. The crew can bail water and patch holes.
3. **Wind-driven sailing.** A global, weather-dependent wind drives sails. Course and sail trim matter.
4. **Commandable crew.** NPCs man stations such as sails, cannons, the crow's nest and the anchor, following the captain's orders.
5. **Skill-based swordplay.** Slash, thrust, guard, parry and riposte with stamina. Deck duels are about timing, not click spam, against players and NPCs alike.
6. **A living sea world.** Pirate islands, seafarer villages, the navy, bounties, sharks and a rare kraken.

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
| Animated entities | GeckoLib (required dependency). It is available for both loaders. |
| Config | A cross-loader config solution (e.g. Forge Config API Port), accessed only through our own `config` wrapper. See §21. |
| Mappings | Official Mojang + Parchment |
| Build | Based on the **MultiLoader-Template** (jaredlll08): `common` (vanilla only, via NeoForm), `neoforge` (ModDevGradle), `fabric` (Loom). The template's legacy Forge module was removed. |
| Optional compat | Create (contraptions on ships), shader packs (Iris), JEI/EMI, Jade |
| License | **PolyForm Noncommercial 1.0.0**: anyone may use, modify and redistribute the mod for any noncommercial purpose. |

### Dependency rule
Every new dependency must exist for **both** NeoForge and Fabric, or be optional and isolated behind a compat module.

### Vanilla vs. loader APIs
Prefer vanilla APIs when vanilla offers an equivalent (data components, codecs, `CustomPacketPayload`, GameTest). Use a loader API, through a platform service, when it offers better compatibility, interop or performance. **Never write a mixin just to avoid a loader API.** Typical cases where the loader API wins:
- **Loader events instead of our own mixins:** maintained hooks, shared with other mods, fewer conflicts.
- **Capabilities / transfer APIs** (NeoForge capabilities, Fabric Transfer API): let other mods' pipes and hoppers work with cargo crates and pantries. NeoForge also caches capability lookups.
- **Data attachments** on entities, levels and chunks (criminal score, stamina, ship data). Vanilla has no general equivalent.
- **Render stage events** for the water mask and HUD, instead of level-renderer mixins where possible.
- **Chunk loading** for ships far from players.
- **Convention tags** (`c:` namespace) for recipes and trade goods.

Performance in this mod comes mainly from our own hot paths: hull flood fill, flooding, per-ship forces, mask meshes, network sync and crew AI. Use efficient data structures (e.g. bitsets for hull volume), incremental updates and throttled sync, and **profile with spark** before optimizing.

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
- Mixins: shared mixins go in `common` (`pirates_n_ships.mixins.json`). Loader-specific mixins are allowed only when unavoidable, in that loader's own mixin config.
- Loader-specific *features* (e.g. NeoForge's generated config screen) are allowed, but must sit behind a platform method with a fallback on the other loader.

### 3.2 Modules
The feature modules below are packages inside `common` (and, where needed, a small matching package in each loader module for wiring).

| Module | Responsibility |
|---|---|
| `core` | Registries, config, networking, data attachments, common utilities |
| `ship` | Assembly/disassembly, ship registry, hull analysis (dry volume), flooding, buoyancy hook, damage, cargo weight, flags, customization blocks |
| `sailing` | Wind field, weather coupling, sails, rudder/helm, anchor, oars |
| `station` | Station blocks (sail winch, cannon station, crow's nest, capstan, pump) and the shared "operate" interface used by players and crew |
| `crew` | Crew NPC base, hiring, command system, station assignment, morale/pay, provisions consumption |
| `combat` | Weapons, ammo, cannons, projectiles, grappling hook, boarding |
| `law` | Criminal score, bounties, navy turn-in, brig and prisoners, flag allegiance detection |
| `trade` | Doubloon economy, trade goods, port markets and dynamic prices, contracts, cargo containers |
| `world` | Structures (pirate islands, seafarer villages, navy outposts), loot tables, treasure maps, ship blueprints |
| `worldsim` | World simulation: port registry, faction state, NPC voyages, raids (§10.4) |
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

### 4.1 Building and assembly
- **Ships are built freely** from any blocks, like any other build. There is no fixed ship type and no blueprint requirement.
- **Mod components** placed on the build give the ship its abilities and change how it behaves: helm (steering), sails (§5.2), anchor and capstan, cannons and gun ports (Kanonenluke, §8.2), crow's nest (lookout), flags (§4.7), pumps, galley/pantry, brig, cargo containers. A ship without sails doesn't sail, a ship without cannons can't fire, and so on. See §6 for the full station list.
- **Blueprints for beginners:** a few prebuilt ships (e.g. sloop, brigantine, merchant cog) are available as blueprint items, bought from the shipwright in seafarer villages. Using one places the prebuilt ship as normal blocks at the dock. From then on it is a regular ship: it is assembled at the helm and can be modified freely. Blueprint ships are stored as structure NBT built by hand.
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

### 4.7 Flags
- A **flagpole / mast-top flag** block. The flag a ship flies sets its **displayed allegiance**, which NPC ships, ports and forts react to.

| Flag | Effect |
|---|---|
| None / merchant flag | Default. Ports and navy treat the ship normally. |
| Navy (national) flag | Navy ships and outposts are friendly, pirates are hostile. Legitimate only with enough navy reputation, otherwise it counts as a false flag. |
| Jolly Roger | Pirates are friendly or neutral. Navy attacks on sight. Merchant NPC ships may surrender without a fight (morale check). Being seen under it raises the criminal score. |
| Custom flag | Made from banner patterns. Treated as neutral. |

- **False colors:** flying a flag that doesn't match the ship's real allegiance (e.g. a navy flag on a ship with a known bounty) lets you get close unnoticed. Being caught raises the criminal score heavily. Detection chance depends on distance, whether the observer has a manned crow's nest, and your criminal score.
- **Striking colors:** lowering the flag mid-fight signals surrender. NPC ships stop firing, and the attacker can board without resistance. Attacking a ship that has struck its colors is a crime.
- Changing the flag takes a few seconds at the flagpole (player, or the crew order "hoist colors").
- Flags flutter in the wind direction, doubling as a visual wind indicator (§5.1).

### 4.8 Ship customization
- **Ship name:** set at the helm. Shown on a nameplate block on the hull and in the HUD, logbook and bounty notices ("the *Black Gull*").
- **Figureheads:** decorative bow blocks in several designs (mermaid, lion, eagle, skull, …).
- **Sails:** dyeable, and large sails can carry banner patterns.
- **Hull paint and trim:** dyeable planks and trim blocks (optional, since vanilla wood types already give a lot of variety).
- **Decor:** lanterns, stern windows, ship's bell, rope coils, captain's cabin furniture.
- Customization is purely cosmetic and never changes performance stats.

### 4.9 Cargo and weight
- Every ship has a **cargo weight** = the contents of containers on board (crates, barrels, chests) plus heavy items such as cannons and cannonballs.
- Weight lowers the ship in the water (less freeboard, so it floods more easily) and reduces speed and turning (applied to Sable mass or as a drag factor).
- The HUD and the helm GUI show the load level (light / laden / heavily laden / overloaded).

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
| Flagpole | Hoist, change or strike colors (§4.7) |
| Galley / pantry | Stores provisions; a cook crew member boosts morale (§7.4) |

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

### 7.4 Provisions
- A **galley / provisions store** on the ship (a pantry container block) holds food, fresh water and rum.
- The crew consumes provisions per in-game day, scaled by crew size. The HUD shows how many days of supplies are left.
- **Food:** any vanilla food counts, weighted by nutrition. Ship-specific foods are optional extras (hardtack, salted fish, salt pork) that keep longer.
- **Fresh water:** water barrels, refilled in ports or from rain catchers.
- **Rum:** a morale booster. Too much reduces the crew's work speed for a while.
- **Running out:** hungry crew work slower and lose morale. Thirsty crew lose morale fast and eventually desert or mutiny.
- **Scurvy (optional, config toggle):** after a long time at sea without citrus or fresh food, crew (and optionally players) get weakness and slower healing. Eating citrus cures it.
- **Spoilage (optional):** fresh food in the pantry slowly spoils on long voyages. Preserved foods don't.
- Provisions add weight to the cargo (§4.9).

---

## 8. Combat

### 8.1 Weapons (Waffen)
| Item | Notes |
|---|---|
| Rapier (Degen) | Fast, long reach, strong thrust, weak slash. Uses the melee combat system (§8.5). |
| Cutlass (Entermesser) | Shorter, heavier. Strong slash, weaker thrust. Uses §8.5. |
| Saber (Säbel, officers) | Balanced between rapier and cutlass. Uses §8.5. |
| Pistol (Pistole) | Single shot, long reload, high damage, short range |
| Musket (Flinte) | Single shot, longer range, slower reload |
| Ammunition (Munition) | Lead shot, crafted |
| Gunpowder (Schießpulver) | Vanilla gunpowder. No custom variant. |
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
- Fights on deck use the melee combat system (§8.5). Boarding is where skill-based duels matter most.

### 8.5 Melee combat (skill-based swordplay)

Goal: sword fights are about timing and reading the opponent, not click spam. The system only applies to the mod's swords. Vanilla weapons keep vanilla behavior.

**Actions**

| Action | Default input | Effect |
|---|---|---|
| Slash | Left click | Wide, short arc. Medium damage, fast recovery. |
| Thrust | Hold left click, release | Narrow ray, long reach. High damage, slower, long recovery if it misses. |
| Guard | Hold right click | Reduces frontal damage (per weapon), drains stamina while held and per blocked hit. |
| Parry | Tap right click shortly before a hit lands | Deflects the hit completely, staggers the attacker, opens a **riposte** window. |
| Riposte | Attack during the riposte window | Bonus damage, can't be parried. |
| Feint (later) | Cancel an attack during wind-up | Baits a mistimed parry. |
| Directional attacks/parries (later, optional) | Mouse movement picks the direction | A parry only works in the matching direction (Mount & Blade style). Off by default, config toggle. |

**Rules**
- **Stamina:** attacks, guarding and failed parries cost stamina. At zero stamina the player can't guard or parry and gets staggered more easily. Stamina regenerates when not attacking. Shown in a small HUD bar while holding a sword.
- **Attack phases:** wind-up (telegraph, readable animation) → active (hit frames) → recovery (vulnerable). Each weapon defines the timings for each phase.
- **Parry window:** about 6–8 ticks (configurable), deliberately generous for multiplayer latency. Each failed parry briefly blocks the next one, which prevents parry spam.
- **Stagger/poise:** being parried or hit by a thrust while in recovery causes a short stagger (no actions, slowed movement).
- **Mixed fights:** a parry also deflects vanilla melee hits from mobs. Projectiles (pistols, muskets) can't be parried.

**Technical design**
- `common`: combat state machine per entity (an attachment), weapon definitions (data-driven via datapack JSON: timings, damage, reach, arc, stamina costs), hit resolution (ray for thrust, arc sweep for slash), GameTests for resolution logic.
- **Client:** intercepts attack/use input while holding a mod sword. It plays the animation immediately (prediction) and sends an action payload with a client timestamp.
- **Server:** authoritative. It validates range, cone, cooldowns and stamina, then resolves parries using the server tick plus a latency allowance. It broadcasts the resulting state so other clients animate correctly.
- **Animations:** a player animation library for first- and third-person player animations, which must be available on both loaders (§2 dependency rule). Choose it during this milestone, see §21. NPC animations use GeckoLib.
- Works on moving ships: hit checks use positions relative to the sub-level where needed.

**NPC duelists**
- Pirates, navy soldiers and officers use the same state machine and the same rules as players: they telegraph attacks, guard, parry and riposte.
- Skill tiers per NPC type set the parry chance, reaction time and how often they use feints. For example, a sailor is clumsy and a pirate captain is dangerous.
- Duel bosses: named pirate captains with unique movesets, as quest and boarding targets.

**Compatibility:** no hard dependency on combat overhaul mods (e.g. Epic Fight, Better Combat). Optional compatibility can come later.

---

## 9. Mobs and NPCs

| Mob | Behavior |
|---|---|
| Pirate | Hostile to most players by default (depends on reputation). Cutlass duelist (§8.5) and pistols. Spawns on pirate islands and pirate ships. Hireable when reputation is high enough. |
| Sailor (Matrose) | Neutral villager-like NPC in seafarer villages. Hireable. |
| Navy soldier | Patrols outposts and ships. Hostile to players with a bounty. Muskets. |
| Navy officer | Leads soldiers and accepts pirate turn-ins and bounty claims. Gives quests. Skilled saber duelist (§8.5). |
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

### 10.3 Trade and cargo
- **Trade goods:** a set of cargo items, each with a base price. Vanilla items are reused where they exist (sugar, fish, timber/logs, iron). New items only for typical colonial goods vanilla lacks (tobacco, spices, cloth, rum). Stored in cargo crates and barrels (cargo containers hold one good type in bulk).
- **Markets:** every port (seafarer village, navy outpost, pirate island) has a harbor master or trader with a market screen. Each port has goods it **produces** (cheap) and goods it **demands** (expensive), derived from its biome and type.
- **Dynamic prices:** buying raises a good's price and selling lowers it, recovering slowly over time. This prevents infinite money loops.
- **Trade runs:** buy cheap in one port and sell where demand is high. Longer and riskier routes (through pirate waters) pay more.
- **Contracts:** harbor masters offer delivery contracts (bring X to port Y by day Z) as a simpler entry point to trading. They link to the quest system (§15).
- **Plunder:** cargo on captured or sunk ships can be taken. Pirate fences buy plundered goods at a discount, no questions asked. Selling plundered goods in navy ports is risky and can raise the criminal score.
- **Port fees (optional):** small docking fees in navy ports, waived for high navy reputation.
- Cargo weight affects the ship (§4.9), so a fully laden merchant ship is slow and an easy target.

### 10.4 World simulation (later stage)
A server-wide simulation that makes the sea feel alive between the ports, without simulating every ship physically.

- **Port registry:** a saved-data record of every generated port (seafarer village, navy outpost, pirate island) with its position, faction and market. Ports are added when their structure generates.
- **Faction state:** a small state machine per faction (Navy, Pirates, Merchants) with values such as aggression, wealth and tension between factions. Player actions and world events shift them (e.g. many pirate kills by the navy raise pirate tension).
- **Abstract voyages:** NPC ships exist mostly as abstract records (route, cargo, faction, progress) that move along routes between ports on the server tick. Only when a player comes within range is a voyage **materialized** as a real Sable ship with crew. When no player is near any more, it is turned back into a record. This keeps the cost low no matter how many ships are travelling.
- **Trade convoys:** merchants travel between ports that produce and demand goods (§10.3) and actually move goods, nudging market prices. Pirate players can intercept and plunder them, which feeds the criminal score (§13.1) and Merchant/Navy reputation.
- **Navy patrols:** sail between navy outposts and hunt ships flying the Jolly Roger or with high bounties.
- **Pirate raids on navy settlements:** while a player stays at a navy outpost or a navy-aligned village, the chance of a pirate raid slowly rises with the time spent there. It is capped and has a long cooldown, so raids stay rare events rather than a routine.
- **Retaliation:** when the navy has been very aggressive against pirates (high pirate tension), pirates are more likely to raid navy settlements or attack navy convoys, and the reverse.
- Events are announced in advance where it makes sense (sails on the horizon, a warning bell in the village), so the player has time to react.
- This also answers how trade stays interesting: routes have real traffic, risk and opportunities.

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

### 13.3 Brig (prisoners)
- **Capture:** a defeated (not killed) NPC with low health can be put in **shackles**. Players with a bounty can be captured the same way (PvP, config toggle).
- **Brig:** a cell area on a ship, made of brig bars and a lockable brig door. A shackled prisoner led into the cell (on a lead, like a mob) stays there and can't escape while the door is locked.
- **Prisoners on board:** take a small share of provisions. They may try to escape when the door is open or the crew's morale is low, and they free themselves if the ship is captured by their faction.
- **What to do with them:**
  - Deliver pirates and bounty targets to a navy officer for the bounty (§13.2).
  - Ransom captured navy officers or merchants at their faction's port.
  - Press-gang captured sailors into your crew (low morale at first). Pirates only, raises the criminal score.
  - Release them (small reputation gain with their faction).
- Captured enemy captains are worth extra and are needed for some quests.

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
| Provisions | consumption on/off, consumption rate, scurvy on/off + onset time, spoilage on/off |
| Cargo & trade | cargo weight affects ships on/off + weight factor, price volatility, price recovery rate, port fees on/off |
| Flags & brig | false-flag detection strength, NPC surrender on/off, player capture (PvP) on/off, prisoner escapes on/off |
| Combat | firearm misfire in rain, cannon block damage on/off, damage multipliers |
| Melee | skill-based combat on/off (off = vanilla-style melee for mod swords), parry window (ticks), stamina costs and regen, directional mode on/off, NPC skill multiplier |
| Law | criminal score enabled, decay rate, bounty threshold, player bounties on/off |
| Survival | cold water on/off + time to freeze, swimming hunger multiplier |
| World | structure spacing / frequency per structure type, mob spawn weights |
| World simulation | enabled, max simultaneous voyages, materialize radius, convoy frequency, patrol frequency, raid chance growth + cap + cooldown, retaliation on/off |
| Audio (client) | music on/off, music volume, ambience volume |

---

## 18. Multiplayer, performance and compatibility
- All gameplay state is server-side, with ship and crew state synced through custom payloads. Hull analysis runs on the server. Mask meshes are built on the client from synced compartment data.
- **Budgets:** hull analysis is incremental and off the hot path. No per-tick full ship scans. Crew AI is throttled when no player is nearby.
- **Compatibility targets:** Create (optional), Iris/Oculus shaders (water mask), Jade/WTHIT, JEI/EMI.

---

## 19. Testing strategy
- **GameTests** for all logic: assembly/disassembly, hull analysis (known hull shapes → expected compartments), flooding rates, criminal score/bounty thresholds, station operate logic, market price changes, provision consumption, prisoner/brig rules, flag detection, world simulation (voyage progress, raid chance and cooldown, faction state changes), melee resolution (parry windows, stamina, hit arcs/rays), config toggles actually disabling features.
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
| 5 | Config framework + weapons | All §8.1 items exist (swords still with vanilla-style melee), firearms work, the config screen works |
| 6 | Flooding + damage | Cannon damage → breach → flooding → sinking, plus pump and patch |
| 7 | **Melee combat core** | Slash, thrust, guard, parry, riposte and stamina work player vs. player and player vs. a test dummy. Server-side resolution covered by GameTests. Animation library chosen. |
| 8 | Melee animations + NPC duelists | First- and third-person sword animations with readable telegraphs. A test NPC uses the same system (telegraph, guard, parry) with skill tiers. |
| 9 | Cannons + grappling hook v1 + boarding | Full ship-to-ship combat loop, ending in a deck duel |
| 10 | Ship identity + blueprints | Flags (incl. striking colors), ship name, figureheads, dyeable sails, decor blocks, beginner blueprint ships at the shipwright |
| 11 | World | Islands, villages, outposts, wrecks, treasure maps |
| 12 | Mobs | Pirates, sailors, navy, sharks (human mobs use the §8.5 duel AI) |
| 13 | Law + brig | Criminal score, bounties, turn-ins, shackles, brig, ransom, false-flag detection |
| 14 | Trade + cargo | Trade goods, port markets with dynamic prices, contracts, cargo weight affecting ships, plunder |
| 15 | Crew command system + provisions | Hiring, all orders, wages and morale, galley, provisions consumption, scurvy |
| 16 | Sea chest + survival | §11, §14 |
| 17 | Weather and hazards | Waves, waterspouts, whirlpools |
| 18 | Audio | Sounds, music manager |
| 19 | RPG + kraken + duel bosses | Reputation, quests, named pirate captains, the kraken |
| 20 | World simulation | Port registry, faction state, abstract voyages that materialize near players, trade convoys, navy patrols, raids and retaliation (§10.4) |
| 21 | Melee extras (optional) | Feints, directional attacks/parries mode |
| 22 | Fabric port | Enable `fabric/`, implement platform services, all GameTests pass on both loaders, CI builds both |

Spikes 1–4 are throwaway-quality prototypes that prove feasibility. They may live in `neoforge/` while exploring Sable, but must be moved into `common` (behind platform services) before milestone 5.

---

## 21. Open questions
- What exactly does Sable's API offer for assembly, applying forces and custom buoyancy? (Read `refs/sable` + wiki: "Block Physics Properties", "Dimension Physics Data", "Working with Entities".)
- Can buoyancy be overridden per ship (needed for dry volume and flooding), or does it have to be applied as an external force?
- How should cargo weight interact with Sable's mass: change block/ship mass directly, or apply drag and a buoyancy offset?
- Should there be a Navy career path for players (join the navy instead of pirating)?
- Player animation library for melee combat: which options are maintained for 1.21.1 on both NeoForge and Fabric, and do they support first-person animations? Decide in milestone 7.
- Melee input defaults: do hold-to-thrust and tap-to-parry feel good with mouse buttons, or are dedicated keybinds better? Decide by playtesting.
- Config library: Forge Config API Port (NeoForge's config API on Fabric) vs. another cross-loader config library. Decide before milestone 5. Either way, access it only through our wrapper.
- Does Sable's API differ between `sable-common` and the loader artifacts (e.g. events or registration only on the loader side)? Check in `refs/sable` during milestone 0.
- Do the dry-hull rendering mixins target the same classes on both loaders? Fabric has no NeoForge render patches, so some hooks may need loader-specific variants.
