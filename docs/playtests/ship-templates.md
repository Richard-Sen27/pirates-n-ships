# Playtest: ship templates (work package W0)

The converter, the placement maths, the refusals and the assembly of the starter sloop are covered by 15 JUnit tests
and 5 GameTests. What only a client shows: how the sloop floats, how the turned blocks face, and how it sails.

## Steps
1. **List:** `/pirates ship templates`: one line, `pirates_n_ships:starter_sloop (Starter Sloop): 9×21×29, helm 4 8 22,
   waterline row 2, price 400`.
2. **Place and assemble:** on a shore or a boat at sea level facing open ocean, `/pirates ship place starter_sloop
   assemble`: "Placed Starter Sloop (680 blocks) … bow to the <facing>, waterline at y 62", then "Ship assembled: 680
   blocks"; the sloop 3 blocks ahead, bow away, centred on you, floating roughly at the wale (say how far it settles up
   or down), the hold dry.
3. **Rig:** figurehead, nameplate, helm, winch, capstan and pump face sensibly after rotation; try all four facings
   without `assemble` and note any block that faces wrong (the cleat and the capstan are unchecked).
4. **Sailing:** board, use the sail winch (furled, half, full), set a wind with `/pirates wind set <from> 8`: the ship
   moves and the helm steers; optional jib with a rope from the mast-head cleat to the bowsprit cleat.
5. **Refusal:** `/pirates ship place starter_sloop` facing land: "Something is in the way at x y z (add force …)"; on
   open water without `assemble` the hull stands in the water with a dry hold and helm-use assembles it.
