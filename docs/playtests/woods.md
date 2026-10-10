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
