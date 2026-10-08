---
navigation:
  title: "Ships"
  parent: index.md
  position: 20
  icon: pirates_n_ships:helm
item_ids:
  - pirates_n_ships:helm
---

# Ships

## Assembly at the helm
Using a **helm** that stands in the world assembles a ship:
- It collects every block connected to the helm, through faces and edges.
- It never takes air, fluids, or **terrain** (dirt, sand, stone, ores, gravel, ice, snow, leaves, corals, kelp and
  similar: the block tag `pirates_n_ships:terrain`). Logs are not terrain, so a wooden build counts. The tag
  `pirates_n_ships:never_assemble` excludes more blocks (bedrock, barriers, portals, command blocks).
- The limit is 2048 blocks. Above it the helm refuses and tells you. A dock that touches the hull counts towards it.
- Chests and other block entities keep their contents.
- The sea is put back where the hull stood, so no hole is left in the water.
- The ship gets a record (its id, a name, the owner). Use a **name tag** on the helm of an assembled ship to name it.

## Disassembly
Sneak-use the helm with an empty hand. The ship is put back into the world as blocks, snapped to the block grid with
its heading rounded to the nearest 90°. It is refused when:
- the ship moves faster than 0.3 m/s or turns too fast,
- it is tilted more than 6°,
- something solid is in the way (the message names the position).

**If your helm breaks,** the ship stays a ship: its stations keep working, but nothing steers or disassembles it until
you place a helm anywhere on its deck. The first helm placed steers; a second helm on the same ship does nothing while
the first stands. Using a helm on a floating hull the mod has lost track of makes it your ship again.

Players and mobs on deck are set down on the deck blocks. Water inside the hull is removed.

## The dry hull
An assembled ship analyses its own hull:
- An air space counts as part of the hull when it lies **below its own pour point**, the lowest way water could get in.
  So a closed cabin is dry, and an open boat is dry up to its rim.
- Watertight: full blocks, slabs, stairs, glass. Not watertight: fences, walls, bars, panes, ladders, chains.
  The block tags `pirates_n_ships:watertight` and `pirates_n_ships:not_watertight` override this.
- Doors, trapdoors and fence gates are **openings**: watertight when closed, a way for water when open.
- Inside the dry part there is no water: you don't swim, you can breathe, and no water is drawn (through Sable's water
  occlusion). Slabs, stairs and closed hatches inside the hull are drawn dry in their empty half too, as long as that
  half faces the dry room and not the sea or the sky. Known gaps: boats, fishing bobbers and mob pathfinding still see
  the water.

## Flooding and sinking
- **A breach:** a hull block that is destroyed below the waterline lets water into the room behind it. So does an
  opening that is open below the waterline. Water comes in faster the bigger the hole and the deeper it lies.
  A 1×1 hole one block under water lets in about one block of water per second.
- Rooms joined by an open door or hatch level out. A closed door holds the water back.
- Placing a block into the breach stops the inflow.
- **Buoyancy:** the dry volume under water lifts the ship, and flood water weighs it down. A fully flooded ship sinks.
- The flood state is saved with the ship.
- Flood water shows as a level water surface inside the room, rising and falling with the flood (client config
  `dry_hull_view.flood_surface`); below it you get the underwater view.
- Below that surface you hold your breath as in the sea: with your head under the flood water your air runs out and
  you start to drown, even where the flood stands higher than the sea outside. Respiration, Water Breathing, Conduit
  Power and a turtle helmet help as usual; lift your head above the water and you breathe again (server config
  `dry_hull.flood_breath`).

## Fighting a leak
A hull block destroyed below the waterline leaves a breach, and water runs into that room at a rate; deeper holes leak
faster.
- **Hull Patch** (2 planks and 1 coal or charcoal give 2): stand inside, look at the edge of the hole (the face of a hull
  block next to it) and use the patch. It fills the hole with a tarred plank block and the water stops at once. A patch
  only goes into a real hole in an assembled ship; elsewhere it tells you so and is not used up. You can break the
  patch later and put in a normal plank, but the hole is open while you swap.
- **Bilge Pump** (a stick on top, plank, bucket, plank in the middle, a plank below): place it on the hold floor, or on
  the deck right above the hold, since its pipe reaches 4 blocks down through the planks. Hold right-click on it to
  pump: it removes 1 block of water per second and stops when you let go. The action bar shows how much water is left,
  or "The bilge is dry". It drains the highest flooded room below it. Pumping makes you hungry slowly. Several pumps in
  one room add up.
- A crew member can man a pump like a sail winch: assign it with the whistle, then pick **Man the pumps** in the
  whistle wheel (or `/pirates crew order pump`). It pumps until the room is dry and answers "The bilge is dry" when
  there is nothing to do. Ship-wide orders reach only the crew whose station takes them; an order to the wrong station
  is refused.
- Server config `flooding`: `pump_enabled`, `pump_rate`, `pump_reach`, `pump_use_ticks`, `pump_exhaustion`,
  `patch_enabled`.

## Limits to know
- **Stay near the world origin.** Sable's physics uses 32-bit positions. Beyond about 100,000 blocks from the origin a
  slow ship reports a speed but stops moving. Beyond several million blocks ships can sink into solid blocks.
- **Small hollow hulls tip easily.** They float but barely right themselves. In tests a 5×5 plank boat, 4 blocks high,
  with a deck, helm and mast lay about 20° bow up at rest, ran 35 to 46° bow down under a small sail, and wandered
  a few degrees off course without rudder input. **Give a small ship a heavy bottom:** with a bottom layer of stone
  the same boat ran about 16° bow down and held its course. Longer and wider hulls are much steadier. The heeling
  force of sails is also scaled down to 25% (config `sailing_runtime.sail_heel_factor`).
- A ship only floats on water that is under its own hull. A ship in a dry dock next to the sea stays put.
