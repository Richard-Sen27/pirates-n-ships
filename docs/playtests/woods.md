# Playtest: wood families

## WOOD4: the machines in every wood

The helm, capstan, sail winch, bilge pump and cannon come in every wood (11 vanilla woods, plus every loaded wood
mod's). The old ids stay the members of the wood their model always had (`helm`, `capstan`, `bilge_pump`, `cannon`:
dark oak; `sail_winch`: spruce), so saved worlds, ship templates and structures keep their blocks. Covered headlessly:
JUnit (`WoodDataTest`: every member's lang, block state, item model, loot, recipe and the children of every wooden
model and renderer part; `HandMadeModelsTest`) and five GameTests that place a non-legacy member (a spruce helm
assembles and steers a ship, a birch capstan drops and weighs the anchor, a jungle winch's crew sets sail, an acacia
pump drains the hold, a cherry cannon loads, fires and drops itself). What only a client shows: the textures.

1. **Creative tab:** each machine is listed in every wood in a row ("Oak Helm", "Spruce Helm", ... "Dark Oak Helm",
   ...), like vanilla's fences. The old blocks are now named with their wood: "Dark Oak Helm", "Spruce Sail Winch".
2. **Looks:** place each machine in oak, birch, cherry, crimson and bamboo. Expected: the main wood follows the wood
   (helm: the pedestal planks and the wheel's spokes; capstan: base plate, drumhead, drum and cap; winch: base,
   uprights, braces and crank; pump: base and casing; cannon: the cheeks, transoms and axletrees), the accents stay
   (the helm's spruce rim and gold, the capstan's spruce whelps, the winch's dark oak caps and rope, the pump's oak
   handle and iron, the cannon's spruce bed and wheels and its iron barrel). No missing-texture (magenta) faces, no
   face that changes wood when you look from another side.
3. **Renderer parts:** turn a spruce helm's wheel, let a crew member work a birch capstan, and aim a cherry cannon up
   and down. Expected: the wheel, the capstan bars and the cannon's quoin (the wedge under the breech) are in the
   member's wood, not dark oak, and move as before.
4. **The legacy members changed slightly:** the dark oak bilge pump's casing is now stripped dark oak (it was spruce),
   and the breaking particles of the helm, pump and cannon carriage are the wood's planks. Say whether that reads well.
5. **Recipes:** in the recipe book or JEI, a spruce capstan needs stripped spruce logs and spruce planks, a cherry cannon
   stripped cherry logs and cherry planks, a jungle winch jungle planks. The Almanac's helm, winch, capstan, pump and
   cannon panels cycle through the woods (every 2 s).
6. **Old world:** open a world saved before WOOD4 with a ship that has a helm, capstan, winch, pump and cannon. Expected:
   all are still there, as "Dark Oak ..." (the winch "Spruce Sail Winch"), and work.
7. **Weight:** a ship with four acacia cannons sits as low as one with four dark oak cannons (the weight goes through the
   new `#pirates_n_ships:cannons` tag).

## WOOD3: the fittings

The name board, chart table, sea cot, stern window, notice board, flagpole, flag pedestal, crow's nest, yard and cleat
come in every wood (docs/woods.md). Registration, recipes, tags, legacy ids and each family's main behaviour in one
non-legacy wood are covered by JUnit tests and GameTests; a client is needed for the looks.

1. **Creative tab:** each fitting runs through the eleven vanilla woods in a row (oak, spruce, birch, jungle, acacia,
   dark oak, mangrove, cherry, bamboo, crimson, warped), family after family, like the railings. Names read "Spruce
   Yard", "Oak Name Board", "Dark Oak Stern Window"; no missing textures (purple-black) in the tab or in hand.
2. **One of each in a non-legacy wood:** place a birch name board row (mix in an oak and a spruce board: one frame
   round the row, each board in its own wood, the ship's name across it), a cherry chart table, a bamboo sea cot (sleep
   in it aboard), a warped stern window (shutters open and close; the trapdoor shutters stay dark oak), a mangrove
   notice board (posts in stripped mangrove, the spruce board behind the notices stays), an acacia flagpole on a jungle
   pedestal (the pedestal's pole stub is jungle, the pole acacia), a crimson crow's nest with a pole on it, birch yards
   carrying a sail, jungle cleats holding a rope and a stay sail. Check each from all sides: the wood's planks on the
   planks, its stripped log on the posts, rails and spars, end grain on cut ends, the accents (iron, gold, birch rope,
   the cot's wool, the chart table's spruce drawer, the nest's dark underside) unchanged. Breaking particles show the
   wood (planks, or the log on the flagpole, pedestal, yard and cleat).
3. **Old worlds:** open a world saved before WOOD3 with name boards, chart tables, cots, stern windows, notice boards,
   flagpoles with flags, pedestals, crow's nests, yards with sails and cleats with stays: every block is still there as
   the legacy member (oak name board, dark oak window/table/cot/notice board/cleat, spruce flagpole/pedestal/nest/yard),
   flags still fly, sails still set, names still show. The notice board's posts are now stripped dark oak (were bark).
4. **Recipes in a recipe viewer (JEI):** each member from its own wood's planks or stripped log; the stern window from
   that wood's planks (no longer only dark oak), the yard and the pedestal from that wood's stripped log.

## WOOD2: storage blocks in every wood

Setup: a creative world (`./gradlew :neoforge:runClient`, then the same on Fabric), the mod's creative tab.

1. **Creative tab and names.** The tab lists eleven cargo crates (oak first: "Oak Cargo Crate"), then the barrels,
   pantries, water barrels, harbor master's desks and sea chests, each in eleven woods. Expected: every name carries
   the wood ("Bamboo Sea Chest"); no item shows a missing texture (magenta-black) in hand, in the GUI or placed.
2. **Looks.** Place every wood's crate, barrel, pantry, water barrel, desk and sea chest in a row (facing blocks
   towards you). Expected: the crate's slats and lid and its corner posts take the wood (posts in its stripped log, the
   log end on top), its rails stay spruce and its inside dark; the barrel's staves take the wood, heads and bung stay;
   the pantry's spruce parts take the wood, the dark oak carcass and oak panels stay; the desk's body takes the wood,
   its spruce drawers stay; the sea chest's planks and lid take the wood, the iron bands and rope handles stay. The
   legacy oak crate's posts are now stripped oak (they were spruce). Breaking shows particles of the wood's planks.
3. **Water barrel colour.** Fill a cherry and a warped water barrel with water buckets in a swamp and in an ocean
   biome. Expected: the water surface is tinted like water in that biome at every fill level (four heights), in every
   wood; the item in the inventory shows blue water (vanilla's default water colour) for every wood.
4. **Recipes.** In JEI (or the recipe book), look up a birch cargo crate, a jungle cargo barrel, an acacia pantry, a
   cherry water barrel, a mangrove desk and a bamboo sea chest. Expected: each needs that wood's planks (the sea
   chest: `LLL / ICI / PPP`, three planks under the chest); crafting one gives that wood's member. JEI's market and
   provisions categories list every desk, pantry and water barrel as catalysts.
5. **Behaviour.** On an assembled ship: fill a birch crate and a jungle barrel with iron (the ship sits lower, `/pirates
   ship info` shows the load); put food in an acacia pantry and a cherry water barrel and wait for a meal and the next
   dawn (the crew eat beside the acacia pantry, food and water are taken); place a mangrove desk in a village's port
   area (it joins the port: use opens the market). Launch a bamboo sea chest with items into the sea, paddle it,
   save and quit, reload. Expected: it still floats as a bamboo chest; sneak-use gives back the bamboo chest with the
   items; a hit drops it as the bamboo chest.
6. **Old worlds.** Load a world saved before WOOD2 with crates, barrels, a pantry, a water barrel, a desk and a sea chest
   (also one floating). Expected: all are still there (the legacy ids are kept) as oak crates, spruce barrels,
   pantries and water barrels, dark oak desk and sea chests, with their contents; the floating chest is dark oak.
7. **Wood mods (optional).** With Biomes O' Plenty loaded: the tab shows the redwood members, they look right and
   craft from redwood planks; the log has no "Failed to apply tag physics properties" line for our blocks. Without it:
   no error or warning about our `*_cargo_crate`, `*_pantry` and the other ids in the log.
