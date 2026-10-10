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
