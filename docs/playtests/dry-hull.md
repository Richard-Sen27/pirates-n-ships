# Playtest: the dry hull view (work package HV1)

Two bugs the human saw in game (design.md §4.4, "Dry hull view (HV1)"): the underwater overlay while climbing through
a deck trapdoor, and seagrass and kelp of the world showing inside a dry hold. The region rule (the deck hatch is in
the dry region while the air above it is above the sea) is covered by GameTests; the plant culling is client rendering
and has only JUnit tests for its math, so it has never been seen in game.

Setup: `./gradlew :neoforge:runClient`, a creative world, the starter sloop (`/pirates ship place` on deep water near
a seagrass and kelp bottom, or any ship with a hold below the waterline and a trapdoor in the deck over the hold).
Please send `latest.log` if anything differs. For the cost numbers, run the client with debug logging for our mod
(the log line starts with "Hidden water plants").

## Steps
1. **Deck trapdoor, every camera height.** Moor the ship, open the deck trapdoor and climb down the ladder slowly,
   stopping every few pixels (sneak). Then climb up again. Expected: no blue underwater tint, no fog and no water
   overlay at any height, also with the head exactly in the trapdoor cell. Repeat in third person (F5).
2. **A trapdoor hung under the deck.** If you have one (a trapdoor at the top of the hold under a deck opening), do the
   same. Expected: the same, no overlay in the opening either.
3. **The sea beside the hull.** Swim beside the hull below the waterline. Expected: you are in water (swimming, fog,
   overlay), right up to the planks; no dry pocket next to slabs or stairs in the outer hull.
4. **Moored over seagrass and kelp.** Moor the ship over a seagrass meadow or a kelp forest so plants reach up into the
   hold's volume in the world. Go below deck. Expected: no seagrass, kelp, sea pickles or bubble columns inside the
   hold; outside the hull (look through an open door or porthole, or swim around) the plants still stand.
5. **Sail at speed over a kelp forest.** Sail at full speed over kelp. Expected: plants that the hold passes over
   vanish inside within a few ticks (at the leading edge of the hold you may see a plant for a moment), and the plants
   behind the ship come back. No stutter compared with sailing over plain sea. With debug logging, note the
   "sections re-marked" number in the log line (every 10 s).
6. **Toggle off.** Set `dry_hull_view.hide_water_plants = false` in the client config (`pirates_n_ships-client.toml`,
   or the config screen). Expected: within a fraction of a second the plants show inside the hold again; set it back
   to true and they vanish again.
7. **Flooding.** Breach the hold below the waterline and let it fill. Expected: as the dry region shrinks, the
   plants in the flooded part come back (they stand in real water now).

## FLD1: the water surface inside flooded rooms

A flooding compartment now shows a translucent water surface at its level (design.md §4.4, "Decided (FLD1)"). The
server sync (cells and level) is covered by a GameTest and the geometry by JUnit tests; the drawing itself, the
underwater fog and the overlay below the surface are client-only and have never been seen in game. Two new client
mixins (`MixinCamera`, `MixinScreenEffectRenderer`) load only in a real client: if the client crashes on world load,
send `latest.log` (look for "Mixin" and "pirates_n_ships").
Breath below the surface (FLD1b) is server logic covered by GameTests with a crouching mock player; what they cannot
show is whether the bubbles on screen match the overlay (step 3).

Setup: the same as above (the starter sloop moored on calm deep water, a hold below the waterline with a ladder and a
deck hatch). A pickaxe, a few hull patches, and a bilge pump in the hold (or crew to man it). Fancy graphics first, then
repeat step 3 with Fabulous. For the cost numbers, run with debug logging for our mod (the log line starts with "Flood
surfaces").

1. **Hole the hull, watch the water rise.** Stand in the hold and break one hull block a block or two below the
   waterline. Expected: within a second or two a flat, blue, translucent water surface appears on the hold floor and
   rises smoothly (no steps every half second) while the hold fills. It covers exactly the hold's floor area: no water
   in the walls, none outside the hull, none sticking out of the ship's side. It has vanilla's still-water texture,
   animated, and roughly the colour of the sea outside (biome tint).
2. **From above.** Climb the ladder to the deck hatch and look down into the hold. Expected: the surface looks like a
   pond: you see the hold floor and any blocks below it through the water, dimmed like vanilla water. It is darker than
   the deck when the hold is dark (lit by the hold's own light; place a torch in the hold to see it brighten).
3. **From below and diving in.** Jump into the water and dive below the surface. Expected: the underwater overlay and the
   blue underwater fog appear as soon as your eyes go below the surface, and vanish when they come up; looking up from
   below you see the underside of the surface. Watch especially the moment your eyes cross the surface: the overlay
   should switch at the drawn surface, not half a block above or below it. In third person (F5) the fog follows the
   camera, the overlay your eyes. Breath (FLD1b): with your eyes under the surface the air bubbles appear and run
   down at the sea's pace (about 15 seconds from full), then you take drowning damage every second; with your head
   above the surface they refill. Check this in two places: just under the surface while the hold is still filling
   (the cell your head is in still counts as dry for the server), and in a hold whose water stands higher than the
   sea outside (let the hold fill above head height, patch the breach, then break a few deck blocks well away from
   the hold so the lighter ship rises and carries its water above the sea). Expected in both: the bubbles drain
   only while the overlay shows, and never faster than in the open sea (no double drain where the sea itself fills
   the hold). Repeat once in creative (no drain) and once after drinking a Water Breathing potion (no drain).
4. **Pump it down.** Patch the breach with a hull patch, then work the bilge pump. Expected: the surface sinks smoothly
   and disappears when the hold is dry (the last film of water vanishes at once).
5. **A rolling ship.** With some water in the hold, sail into waves or turn hard so the ship heels. Expected: the
   surface stays level with the world (horizon-flat) while the hull tilts around it, and never shows outside the hull
   on the low side.
6. **Several rooms.** On a ship with two holds, flood one. Expected: only the flooded one shows a surface. Open a
   door between them: both surfaces settle to the same height as the water equalises.
7. **Toggle.** Set `dry_hull_view.flood_surface = false` in the client config (`pirates_n_ships-client.toml` or the
   config screen). Expected: the surface disappears at once (the hold looks dry again, as before FLD1), and diving
   below the old level no longer shows the overlay or fog unless the world's sea is there. Set it back to true.
   Then set the server config `dry_hull.flood_breath = false` (`pirates_n_ships-server.toml`): diving under the
   surface where the world's sea is not shows the overlay but no longer drains the air bubbles. Set it back to true.
8. **Shaders and other renderers.** If you use Iris or Sodium, note whether the surface draws at all.

## HV1c: plants in partly covered cells

A water plant is now hidden as soon as any part of its cell (shifted by its random model offset, for tall seagrass) is
inside the dry region, not only when its block position is; a tall seagrass goes as a whole when either half is cut
(design.md §4.4, "Partly covered cells (HV1c)"). The rule is covered by JUnit tests and a GameTest; what it looks like
has not been seen. Setup as above: the sloop moored over a seagrass meadow with some tall seagrass and kelp, so plants
stand right under and beside the hold. Fancy graphics, no Sodium or Embeddium.

1. **Hold floor and walls.** Go below deck and look at the floor edges, the corners and the lower walls, also crouched
   with the camera close to the planks, and while the ship bobs. Expected: no blade of seagrass, tall seagrass, kelp or
   sea pickle pokes through the floor, a wall, a slab or a stair into the hold, at any moment of the bobbing (before
   HV1c, plants whose cell the hold only partly covered stuck out half a block).
2. **The waterline and the deck.** If the hold reaches above the waterline, or a plant reaches up to the deck, look at
   the deck underside and the hatch. Expected: nothing green inside the hold there either.
3. **Outside stays.** Swim around the hull below the waterline. Expected: plants a block or more away from the hull
   all stand, and so do most plants right against the planks. A plant whose cell reaches through the plank wall into
   the hold (the ship sits between block positions) is hidden as a whole, also its part outside the hull; that is the
   price of not cutting plants in half. Tell us if a plant visibly away from the hull (a block or more) is missing.
4. **Tall seagrass.** Find a tall seagrass under the hull whose top half reaches into the hold. Expected: the whole
   plant is gone, also its lower half outside the hull; no half plant.
5. **Sailing.** Sail slowly (a block per second) over the meadow and watch the hold floor from inside. Expected: no
   plant flickers into the hold at the floor edges; outside the stern the plants come back behind the ship. With debug
   logging, note the "Hidden water plants" log line (µs per refresh, sections re-marked) and compare it with HV1.
