# Playtest: wood families (WOOD1 to WOOD4)

See docs/woods.md for the families, legacy ids and the compat woods.

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
