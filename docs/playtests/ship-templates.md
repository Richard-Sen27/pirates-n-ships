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
   moves and the helm steers; optional jib with a rope from the head cleat above the upper yard to the bowsprit cleat.
5. **Refusal:** `/pirates ship place starter_sloop` facing land: "Something is in the way at x y z (add force …)"; on
   open water without `assemble` the hull stands in the water with a dry hold and helm-use assembles it.

## Basic sloop (W0b)
`starter_sloop_basic` is the same 9×21×29 hull with the helm at 4 8 22, but less fitted out: no cleats (so no jib
rope), the two yards one block forward in front of an unbroken mast, and three extra planks closing the stern of the
hold (row 2). Covered by the converter check and 2 GameTests (palette and helm; place and assemble facing north).
1. `/pirates ship templates` now lists two lines; the new one is `pirates_n_ships:starter_sloop_basic (Starter Sloop
   (basic)): 9×21×29, helm 4 8 22, waterline row 2, price 300`.
2. On open water, `/pirates ship place starter_sloop_basic assemble`: "Placed Starter Sloop (basic) (682 blocks) …",
   then "Ship assembled: 682 blocks". On board: the helm, the two yards (with sails), the winch, capstan, pump,
   flagpole, figurehead and nameplate, and no cleats. It floats like the full sloop and the hold is dry.
3. It sails: winch the sails out, set `/pirates wind set <from> 8`, and the ship moves and steers with the helm. Check
   that the forward-shifted yards still carry and show their sails.

## SW1: shipwright orders
A seafarer village's harbor master's desk has an **Orders** tab: the ship templates with their price (template price ×
`ships.order_price_factor`), build days (`ships.build_time_days.default` per 500 blocks) and materials (a log per 20
blocks, a wool per sail yard, × `ships.order_materials_factor`). Ordering gives a **Ship Receipt**; using the desk
with it once the ship is ready puts the ship, assembled and yours, at a free berth of the village pier. Covered by
JUnit (maths, berth geometry, codecs, tooltip states) and 4 GameTests (order, early pickup, berth 1, berth 2, no free
berth, order limit, lost order, other port kinds, toggle off). What only a client shows: the tab, the tooltip, and
how the ship lies at a generated pier.

Prepare: a new world (or find a village with `/locate structure pirates_n_ships:seafarer_village`), creative or
survival with 1000 doubloons, 128 logs (any kind) and 64 wool (any colour) in the inventory.
1. **Tab:** use the village's harbor master's desk. The market screen shows a third tab, **Orders**. It lists
   "Starter Sloop" (400 doubloons, 1.4 days, 34 logs, 16 wool) and "Starter Sloop (basic)" (300 doubloons, 1.4 days,
   35 logs, 16 wool), the heading "The shipwright builds (0 of 3 slipways taken)", and "Your orders: No orders here".
   With too few logs the materials line turns red and the Order button is grey with a tooltip saying what you need.
   At a pirate island's or a navy outpost's desk there is no Orders tab.
2. **Order:** click Order on the basic sloop. Status line "Ordered: Starter Sloop (basic), ready in 1.4 days. Keep the
   receipt"; 300 doubloons, 35 logs and 16 wool are gone; a Ship Receipt (paper icon, placeholder) is in the
   inventory; "Your orders" lists it with "ready in 1.4 days"; the heading says 1 of 3.
3. **Tooltip:** hover the receipt: "Starter Sloop (basic) at Village <x> <z>", "Ready in 1.4 days" (yellow), "Hand it
   in at the village's harbor master's desk". `/time add 12000` and hover again: about 0.9 days.
4. **Too early:** hold the receipt and use the desk: chat "Not ready: 0.9 days to go"; the receipt stays.
5. **Finish:** `/pirates ship orders` lists "<id>: Starter Sloop (basic) for <you>, ready in 0.9 days".
   `/pirates ship orders finish <id>` (tab-completes): "Order <id> (Starter Sloop (basic) at Village …) is ready for
   pickup". (The receipt's tooltip updates the next time you use the desk with it.)
6. **Pickup:** use the desk with the receipt: chat "Your Starter Sloop (basic) lies at berth 1"; the receipt is gone;
   the sloop floats beside the pier with one water column between hull and pier, bow pointing seaward along the pier,
   assembled (the helm takes the wheel). `/pirates ship info` on board names it; the owner is you (crew commands work).
   Report: does it lie at the pier or was it slid seaward (up to 16 blocks when the quay or beach is in the way)? Is it
   on the side away from the pier? Does it bump the pier when it settles?
7. **Second ship:** order again, finish, pick up: "… lies at berth 2", on the other side of the pier.
8. **Both berths full:** a third order, finish, pickup: "No berth is free; come back later"; the receipt stays and
   `/pirates ship orders` still lists the order. Sail one ship away (or disassemble it) and pick up again: it works.
9. **Limit:** with three open orders the Order button is grey ("All slipways are taken; come back later").
10. **Config off:** set `ships.shipwright_orders = false` (server config): the Orders tab says "The shipwright takes no
    orders" and lists nothing; an open order can still be picked up. Turn it back on.
11. **Lost order:** not needed in game (covered by a GameTest): a receipt whose port record is gone says "This order
    was lost" and stays.

## TPL3: the sloops follow the yard rule, the jib kept

`starter_sloop`, `navy_sloop_armed` and `pirate_sloop_armed` now carry both yards one block **forward** of the mast
(z 12, the mast at z 13), the mast one unbroken column of logs, and the jib's cleats in the yards' column: the head
cleat right above the upper yard [4, 17, 12], the clew cleat right below the lower yard [4, 9, 12], the tack on the
bowsprit one block in from its tip [4, 7, 1]. The rule change behind it: a triangular sail's clew search (from the head
down to the clew) passes yard blocks as well as air and mast; a square sail still needs only air or mast between its
yards' middles.

Prepare: creative, a flat stretch of open sea, `/pirates wind set 180 8` (wind from the south, dead astern of a ship
heading north).
1. **Starter sloop:** stand at the shore facing the sea, `/pirates ship place starter_sloop assemble`.
   - **Expected:** both yards stand in front of the mast (seen from the side, the mast runs straight from the deck to
     the masthead behind them, no yard cuts through it); the head cleat sits on the mast's fore face just above the
     upper yard, the clew cleat just below the lower yard; one cleat on the bowsprit.
2. **Square sail:** use the sail winch to half, then full.
   - **Expected:** the square cloth hangs from the upper yard to the lower one in front of the mast, bellies downwind,
     and does not flicker through the mast more than a real sail would; the ship gathers way as before.
3. **Jib:** take a rope, use it on the head cleat above the upper yard, then on the bowsprit cleat.
   - **Expected:** the rope is used up, a stay runs from the head to the bowsprit, and with the winch at half or full
     the jib's cloth fills the triangle head, bowsprit, clew: its after edge runs straight down past both yards to the
     clew cleat below the lower yard. Both sails draw at once; the square sail does not vanish when the jib is rigged.
     Report how the jib's edge looks where it passes the yards (cloth through the yard beam, a gap, z-fighting).
4. **Navy armed sloop:** `/pirates ship place navy_sloop_armed assemble` beside it, repeat steps 2 and 3.
   - **Expected:** as on the starter sloop; the crow's nest on the mast top, the ratlines untouched by the cloth.
5. **Lookout's climb:** on the navy sloop walk up the port ratlines from the quarterdeck toward the bow without
   jumping, into the top sloped net beside the mast (right above and behind the upper yard's end), face the mast, push
   forward up the hung net and step sideways into the crow's nest.
   - **Expected:** a continuous climb without a jump; the top sloped net stays put (it stands on the net below it, the
     yard is now in front of it); nothing blocks the way into the nest.
6. **Pirate armed sloop:** `/pirates ship place pirate_sloop_armed assemble`: the same rig as the navy sloop.
7. **NPC ships:** `/pirates world voyages` and spawn or meet a navy patrol (`/pirates world materialize` near one):
   its yards stand forward of the mast, its lookout climbs to the nest.

Report: a screenshot of each sloop from the side with both sails set, one of the jib's edge passing the yards, and
`latest.log`.
