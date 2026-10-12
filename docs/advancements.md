# Advancements (ADV1)

The human (2026-10-12): "i would also like to have an advancement tree for this mod."

The mod has its own tab in the advancements screen (L), "Pirates 'n' Ships", with the helm as its icon and spruce
planks as its background. The root is granted on joining; eight branches hang from it: **Shipwright**, **Crew**,
**Trade**, **Combat**, **The world**, **The law**, **Career** (the navy and the infamy ladders side by side) and **The
Almanac**. Rewards are experience only, never items.

- **Source of truth:** `core/advancement/AdvancementTree` (pure data). Datagen (`AdvancementData`) writes one
  `data/pirates_n_ships/advancement/<id>.json` per node through vanilla's `Advancement.CODEC`, and the titles and
  descriptions into the lang file (`advancements.pirates_n_ships.<id with dots>.title` / `.description`).
  `AdvancementTreeTest` (JUnit) checks this table, the generated files and the lang against the tree; on a mismatch it
  writes the expected table to `common/build/advancements-table.md`.
- **Experience:** the generated files carry no reward; `AdvancementXp` pays a node's experience once when it is done,
  times `advancements.experience_scale` (task 5, goal 15, challenge 40 before scaling). It settles right after every
  criterion of the mod and once a second per player (`AdvancementWatch`) for the nodes on vanilla criteria. Paid
  advancements are remembered per player (`advancement_record` attachment), so a revoked and re-earned advancement pays
  nothing twice.
- **Hidden:** the kraken, the treasure and the capture stay hidden until earned (and so do their children).
- **Config:** `advancements.enabled` (true; off: none of the mod's criteria fires and no experience is paid; the
  vanilla criteria below still tick, so the helm, the rope railing, the wreck chest and the kills can still be earned)
  and `advancements.experience_scale` (1.0, 0 to 10).

## The tree

Columns: the id (`pirates_n_ships:<id>`), its parent, the English title and description, the icon item (our namespace
unless written with one), the frame (and whether it is hidden), the criteria (`trigger kind=… value>=…` for ours; the
vanilla trigger otherwise; several joined by "and" or "or") and the experience before scaling.

<!-- tree:start -->
| id | parent | title | description | icon | frame | criteria | reward |
|---|---|---|---|---|---|---|---|
| `root` | - | Pirates 'n' Ships | Sailing, piracy and life at sea. | `helm` | task | `minecraft:tick` | - |
| `shipwright/craft_helm` | `root` | Hand on the Wheel | Craft a ship's helm, in any wood. | `helm` | task | `inventory_changed helm (any wood)` | 5 XP |
| `shipwright/launch_ship` | `shipwright/craft_helm` | Launched! | Assemble a ship at her helm. | `nameplate` | task | `ship_assembled` | 5 XP |
| `shipwright/set_sail` | `shipwright/launch_ship` | Canvas Aloft | Set a sail at a winch, a yard or a cleat. | `sail_winch` | task | `sail_set` | 5 XP |
| `shipwright/sail_with_wind` | `shipwright/set_sail` | Following Wind | Make way under sail with the wind in your canvas. | `yard` | goal | `sailed_with_wind value>=3` | 15 XP |
| `shipwright/drop_anchor` | `shipwright/set_sail` | Let Go the Anchor | Drop the anchor at a capstan. | `capstan` | task | `anchor_dropped` | 5 XP |
| `shipwright/fix_leak` | `shipwright/launch_ship` | Stop the Leak | Patch a breach in your hull. | `hull_patch` | task | `leak_fixed` | 5 XP |
| `shipwright/order_ship` | `shipwright/launch_ship` | Built to Order | Order a ship from a shipwright at a harbor master's desk. | `ship_receipt` | task | `ship_ordered` | 5 XP |
| `shipwright/launch_brig` | `shipwright/order_ship` | Two Masts | Take delivery of a brig. | `shipwright_toolkit` | goal | `ship_launched kind=brig` | 15 XP |
| `shipwright/big_ship` | `shipwright/launch_ship` | Ship of the Line | Assemble a ship of 500 blocks or more. | `figurehead_lion` | challenge | `ship_assembled value>=500` | 40 XP |
| `shipwright/rope_railing` | `shipwright/craft_helm` | Mind the Rail | Rig a rope railing. | `oak_rope_railing` | task | `placed_block rope_railing (any wood)` | 5 XP |
| `shipwright/helm_wood` | `shipwright/craft_helm` | Fine Joinery | Craft a helm in another wood than dark oak. | `spruce_helm` | task | `inventory_changed helm (not dark oak)` | 5 XP |
| `crew/hire_hand` | `root` | Signed On | Hire a hand at a harbor master's desk. | `bandana` | task | `crew_hired` | 5 XP |
| `crew/give_order` | `crew/hire_hand` | Whistle Up | Give your crew an order. | `captains_whistle` | task | `order_given` | 5 XP |
| `crew/hammock_night` | `crew/hire_hand` | Swinging Sleep | Sleep through the night in a hammock. | `hammock` | task | `hammock_night` | 5 XP |
| `crew/full_crew` | `crew/hire_hand` | A Full Complement | Have eight hands aboard one ship. | `ships_bell` | goal | `crew_hired value>=8` | 15 XP |
| `crew/wages_paid` | `crew/hire_hand` | Payday | Pay your crew's wages at dawn. | `sea_chest` | task | `wages_paid` | 5 XP |
| `crew/deserter_returned` | `crew/wages_paid` | Second Thoughts | Win back a hand who meant to desert. | `rum` | task | `deserter_returned` | 5 XP |
| `crew/loyal_crew` | `crew/wages_paid` | Loyal to a Man | Keep one ship's crew ten days without a deserter. | `figurehead_mermaid` | goal | `crew_loyal value>=10` | 15 XP |
| `trade/first_doubloon` | `root` | Pieces of Eight | Earn your first doubloon. | `doubloon` | task | `doubloons_earned` | 5 XP |
| `trade/contract` | `trade/first_doubloon` | Signed and Delivered | Deliver a trade contract. | `cargo_crate` | task | `contract_delivered` | 5 XP |
| `trade/quest` | `trade/first_doubloon` | Harbour Business | Complete a quest from a harbor master. | `notice_board` | task | `quest_completed` | 5 XP |
| `trade/convoy` | `trade/contract` | Safe Passage | See a merchant convoy safely into port. | `merchant_flag` | goal | `convoy_escorted` | 15 XP |
| `trade/thousand` | `trade/first_doubloon` | A Fat Purse | Earn 1,000 doubloons in all. | `cargo_barrel` | goal | `doubloons_earned value>=1000` | 15 XP |
| `trade/ten_thousand` | `trade/thousand` | A King's Ransom | Earn 10,000 doubloons in all. | `spices` | challenge | `doubloons_earned value>=10000` | 40 XP |
| `combat/fire_cannon` | `root` | Fire in the Hole | Fire a cannon or a swivel gun. | `cannon` | task | `cannon_fired` | 5 XP |
| `combat/hit_ship` | `combat/fire_cannon` | A Palpable Hit | Hit another ship with a cannonball. | `cannonball` | task | `ship_hit` | 5 XP |
| `combat/grapple` | `combat/fire_cannon` | Grappled | Latch a grappling hook onto another ship. | `grappling_hook` | task | `grapple_latched` | 5 XP |
| `combat/board` | `combat/grapple` | Away Boarders | Lay a plank to another ship or send a boarding party. | `boarding_plank` | task | `ship_boarded` | 5 XP |
| `combat/capture` | `combat/board` | Prize Taken | Hold the deck of a ship you boarded. | `cutlass` | goal, hidden | `ship_captured` | 15 XP |
| `combat/shark` | `combat/fire_cannon` | Shark Bait | Kill a shark. | `shark_spawn_egg` | task | `player_killed_entity shark` | 5 XP |
| `combat/kraken` | `combat/shark` | Release the Kraken | Slay the kraken. | `kraken_beak` | challenge, hidden | `player_killed_entity kraken` | 40 XP |
| `world/landfall` | `root` | Landfall | Visit a port: a village, a navy outpost or a pirate island. | `mooring_ring` | task | `port_visited` | 5 XP |
| `world/all_ports` | `world/landfall` | Ports of Call | Visit a seafarer village, a navy outpost and a pirate island. | `chart` | challenge | `port_visited kind=seafarer_village and port_visited kind=navy_outpost and port_visited kind=pirate_island` | 40 XP |
| `world/treasure` | `world/landfall` | X Marks the Spot | Dig up a buried treasure. | `treasure_map` | goal, hidden | `treasure_dug` | 15 XP |
| `world/wreck` | `world/landfall` | Davy Jones' Locker | Open the chest of a sunken wreck. | `ship_lantern` | task | `player_generates_container_loot chests/wreck` | 5 XP |
| `world/fly_flag` | `world/landfall` | Show Your Colours | Hoist a flag on a flagpole. | `flagpole` | task | `flag_hoisted` | 5 XP |
| `world/chart_markers` | `world/landfall` | Cartographer | Have ten markers on your chart. | `chart_table` | goal | `markers_placed value>=10` | 15 XP |
| `world/storm` | `world/landfall` | Weathered the Storm | Stay aboard your ship through a minute of storm. | `oar` | goal | `storm_weathered value>=60` | 15 XP |
| `law/bounty_paid` | `root` | Square with the Crown | Pay a fine to a navy officer. | `bounty_proof` | task | `bounty_paid` | 5 XP |
| `law/shackled` | `law/bounty_paid` | In Irons | Put someone in shackles. | `shackles` | task | `prisoner_taken kind=shackled` | 5 XP |
| `law/brigged` | `law/shackled` | Under Lock and Key | Lead a prisoner into a brig cell. | `brig_key` | task | `prisoner_taken kind=brigged` | 5 XP |
| `law/letter` | `law/shackled` | Letter of Marque | Buy a letter of marque from a navy officer. | `navy_flag` | goal | `rank_reached kind=letter` | 15 XP |
| `law/black_flag` | `law/shackled` | Under the Black Flag | Hoist the Jolly Roger. | `jolly_roger_flag` | task | `flag_hoisted kind=jolly_roger` | 5 XP |
| `career/midshipman` | `root` | Midshipman | Enlist in the navy. | `navy_hat` | task | `rank_reached kind=navy/midshipman` | 5 XP |
| `career/lieutenant` | `career/midshipman` | Lieutenant | Earn a lieutenant's commission. | `officer_hat` | task | `rank_reached kind=navy/lieutenant` | 5 XP |
| `career/captain` | `career/lieutenant` | Captain | Reach the rank of captain. | `officers_coat` | goal | `rank_reached kind=navy/captain` | 15 XP |
| `career/commodore` | `career/captain` | Commodore | Reach the rank of commodore. | `saber` | goal | `rank_reached kind=navy/commodore` | 15 XP |
| `career/admiral` | `career/commodore` | Admiral | Reach the navy's highest rank. | `ship_commission` | challenge | `rank_reached kind=navy/admiral` | 40 XP |
| `career/buccaneer` | `root` | Buccaneer | Grow infamous enough to be called a buccaneer. | `pirate_hat` | task | `rank_reached kind=infamy/buccaneer` | 5 XP |
| `career/dread_captain` | `career/buccaneer` | Dread Captain | Become a dread captain of the brethren. | `pistol` | goal | `rank_reached kind=infamy/dread_captain` | 15 XP |
| `career/pirate_lord` | `career/dread_captain` | Pirate Lord | Become a lord of the pirates. | `captains_hat` | challenge | `rank_reached kind=infamy/pirate_lord` | 40 XP |
| `almanac/open` | `root` | Required Reading | Open the Captain's Almanac. | `captains_almanac` | task | `almanac_opened` | 5 XP |
| `almanac/scene` | `almanac/open` | Show Me | Watch a Show me scene in the Almanac. | `map_tile` | task | `scene_opened` | 5 XP |
| `almanac/all_chapters` | `almanac/open` | Cover to Cover | Read every chapter of the Almanac. | `captains_almanac` | goal | `chapters_read kind=all` | 15 XP |
<!-- tree:end -->

## The criteria

One trigger per mod event family, all of the same shape (`ModTrigger`): an optional `kind` that must match exactly and
a `value` bound (`MinMaxBounds.Ints`). Registered in `minecraft:trigger_type` as `pirates_n_ships:<id>`; fired only
through `ModAdvancements` (one line at each site; anything but a server player is ignored, a failure is logged and never
reaches the caller).

- `ship_assembled` (value: blocks): `ShipAssembler.assemble` with a player (the helm, and ships delivered to a player).
- `sail_set` (value: sails set): `SailWinchBlock.use` (to half or full), `YardBlock` and `CleatBlock` use.
- `sailed_with_wind` (value: tenths of a block a second): `AdvancementWatch`, aboard a ship with a sail set.
- `anchor_dropped`: `CapstanBlock.useWithoutItem` when the anchor drops.
- `leak_fixed`: `HullPatchItem.place` into a breach.
- `ship_ordered` (kind: template path): `ShipOrders.place`.
- `ship_launched` (kind: template path, value: blocks): `ShipOrders.pickup` once the delivered ship is assembled.
- `flag_hoisted` (kind: `merchant`, `navy`, `jolly_roger`, `custom`): `FlagpoleBlockEntity.apply` when a hoist completes.
- `crew_hired` (value: hands aboard that ship): `Hiring.place`.
- `order_given` (kind: whistle order id): `WhistleOrders.handle` (the whistle) and the ship screen's order action.
- `hammock_night`: `PlayerSleep.onWakeUp` after a full sleep in a hammock.
- `wages_paid` (value: hands paid), `deserter_returned` (value: how many), `crew_loyal` (value: dawns in a row without
  a deserter on that ship): `ShipDayTick.run` at dawn, for the owner (or the paying player).
- `doubloons_earned` (value: doubloons earned in all, kept per player): `Wallet.give` (sales, contracts, quests,
  bounties, prize money).
- `contract_delivered`: `MarketTransactions.deliverContract`.
- `quest_completed` (kind: quest type), `convoy_escorted` (an escort quest): `QuestRewards.grant`.
- `cannon_fired` (kind: `cannon`, `swivel_gun`): `CannonService.fire`, `SwivelService.fire` with a player as the owner.
- `ship_hit`: a `CannonShipHits` listener; the shooter, else the online owner of the firing ship.
- `ship_boarded` (kind: `plank`, `party`): a boarding plank laid from one ship to another (`BoardingPlankItem.useOn`);
  "Board!" by whistle (`WhistleOrders.issue`).
- `ship_captured` (kind: the beaten side): `VoyageEndings.capture`.
- `grapple_latched`: `GrapplingHookEntity.latch` on a ship other than the thrower's.
- `port_visited` (kind: `seafarer_village`, `navy_outpost`, `pirate_island`): `AdvancementWatch`, standing in a port.
- `treasure_dug`: `TreasureMapService.onContainerOpen` when a treasure site is looted.
- `storm_weathered` (value: seconds aboard in the current storm): `AdvancementWatch`.
- `markers_placed` (value: markers on the chart): `ChartBackend.handleMarker`.
- `bounty_paid` (value: doubloons): `LawService.payFine` when it lowers the score.
- `prisoner_taken` (kind: `shackled`, `brigged`): `BrigService.capture`; `BrigService.tick` when a led prisoner is in a
  cell.
- `rank_reached` (kind: `navy/<rank>`, `infamy/<rank>`, `letter`): `Careers.store`, every rank up to the current one.
- `almanac_opened`: `AlmanacReading.readInHand` / `readTome`.
- `scene_opened` (kind: scene id): `AlmanacSceneOpener.open` sends `SceneOpenedPayload` (client to server).
- `chapters_read` (kind: `all` once every loaded chapter was read, value: chapters read): `AlmanacSync.onReport`.

Vanilla criteria: `minecraft:tick` (the root), `inventory_changed` (a helm in any vanilla wood; one in a wood other
than dark oak), `placed_block` (a rope railing in any vanilla wood; the mods' woods are left out because their items
exist only with their mod), `player_generates_container_loot` with `pirates_n_ships:chests/wreck` (the wreck chest),
`player_killed_entity` with `pirates_n_ships:shark` and `pirates_n_ships:kraken`.

## Datapacks

A datapack may replace any of these files or add its own advancements on our triggers, e.g.
`{"trigger": "pirates_n_ships:ship_assembled", "conditions": {"value": {"min": 1000}}}` or
`{"trigger": "pirates_n_ships:rank_reached", "conditions": {"kind": "navy/captain"}}`. Experience from
`advancements.experience_scale` is paid only for the ids of this table; a datapack's own advancements use vanilla's
`rewards`.

## Limits

- **Two masts** (`shipwright/launch_brig`) waits for SHIP1's orderable `pirates_n_ships:brig` template: until it exists
  nobody can earn it.
- **Show me** counts the Almanac's "Show me" buttons only; a scene opened with the Ponder key from the inventory does
  not reach the server.
- **Following Wind** and **Weathered the Storm** are checked once a second while Sable tracks the player aboard;
  **Landfall** while the player stands inside a port's bounds.
- **A full complement** counts the hands aboard the ship a hand was just hired onto, so it is earned at a hiring.
- **Loyal to a Man** counts dawns per ship of the owner, so a ship that changes hands starts over.
