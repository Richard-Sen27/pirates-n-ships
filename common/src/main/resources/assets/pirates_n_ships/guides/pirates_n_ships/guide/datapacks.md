---
navigation:
  title: "Datapacks"
  parent: index.md
  position: 140
  icon: minecraft:knowledge_book
---

# Datapacks

**Definitions** are JSON files under `data/<namespace>/pirates_n_ships/<type>/<name>.json`. A datapack can add entries
in its own namespace, or replace one of ours by shipping a file at the same path. A broken file is logged and skipped.

| Type | Folder | Contents |
|---|---|---|
| Trade goods | `trade_good` | Item, base price, weight, category, where it is produced. |
| Weapons | `weapon` | Timings, damage, reach, arc, stamina costs and guard values for the fighting system. A weapon whose name matches an item id applies to that item. |

**Tags** you can extend:

| Tag | Effect |
|---|---|
| block `pirates_n_ships:terrain` | Never part of a ship. |
| block `pirates_n_ships:never_assemble` | Never part of a ship. |
| block `pirates_n_ships:watertight`, `not_watertight` | Overrides whether a block keeps water out. |
| item `pirates_n_ships:provisions/preserved` | Food that never spoils. |
| item `pirates_n_ships:provisions/anti_scurvy` | Food that prevents scurvy. |
| item `pirates_n_ships:provisions/rum`, `fresh_water`, `water_barrel` | What counts as rum and as water. |
| item `pirates_n_ships:provisions/excluded` | Food the crew won't eat (rotten flesh and similar). |
| item `pirates_n_ships:flags` | Items that can be hoisted. |
| entity `pirates_n_ships:law_protected` | Hurting these is a crime (villagers, wandering traders). |
| entity `pirates_n_ships:navy` | Navy members. Empty until navy mobs exist. |
| entity `pirates_n_ships:law_enforcers` | Never get a criminal record (iron golems, the navy). |
| entity `pirates_n_ships:not_capturable` | Can't be shackled (bosses). |

Sable reads block weight from its own tags. The mod's blocks are already sorted into `sable:light`, `sable:heavy` and
the like.
