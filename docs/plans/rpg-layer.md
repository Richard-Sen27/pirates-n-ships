# RPG layer: package plan

Written by a planning agent on 2026-10-08 from design.md §15 and the code of that day (REP1 on its branch); the orchestrator turns each package into an implementer prompt. Decisions that change land in design.md; this file is the detailed plan.

# Milestone 19 (RPG layer) — package plan

Root `…/` = `/Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/`. REP1 (branch `worktree-agent-a5133ef2e79ae91a3`) gives: `rpg/reputation/Reputation.get/adjust/set/navyStanding/piratesFriendly/navyHostile`, attachment `ReputationAttachments.REPUTATION`, `ReputationSync`/`ReputationSyncPayload`/`ClientReputation`, `rpg/deeds/Deeds.record/listen`, `Deed` (13 values), `DeedContext(victimKind, victim, port, amount)`, `DeedListener(player, deed, context, applied)`, `/pirates rep`. The kraken (K1a–c) already exists, so milestone 19's "kraken" is done; it only needs a quest target.

**What exists to build on:** `LawService` (bounties, `claimBounty`, `turnInPirate(PirateTier)`, `setScore`, `recordRelease`), `BountyBoard` (navy bounty only via `syncNavy` from the criminal score; no fixed-amount navy bounty), `law/net/NoticeBoardBackend` (lists every active bounty automatically), `law/turnin/OfficerTurnIns` (officer gestures: proof, empty hand + prisoners, coins; empty hand without prisoners returns PASS), `CommonEvents.ENTITY_INTERACT` (fires before `mobInteract`, first non-PASS wins), `trade/net/MarketBackend.openDesk` + `OrderPayloads`/`ShipOrders` (the SW1 Orders-tab pattern; `MarketScreen` has `Tab {GOODS, CONTRACTS, ORDERS}`), `DeliveryContract` + `TradeService.accept/deliver/contractsOf`, `world/port/Port` (`treasures`, `orders`), `TreasureMapService.boundMap(level, port, site)` + `TreasureBinding.found`, `world/outpost/Garrison`/`GarrisonPosts` (mobs placed from `afterPlace` at fixed piece posts), `DuelistSkill.PIRATE_CAPTAIN` (unused), `PirateTier.CAPTAIN` (150; `NavyOfficer.pirateTier` returns DECKHAND for every pirate), `ShipData.name/withName`, `apparel` hats (bicorne, pirate hat), `CombatContent.SABER`, `ShipTemplate.price`, `ShipOrders` (villages only, `KEY_NOT_VILLAGE`). Missing on main: crew hiring (milestone 15), NPC ships (WS3b), patrols (WS4b), `Voyages.onEnd` (WS2 branch `worktree-agent-a43854b075a1c4dbc`: `VoyageEnd {ARRIVED, SUNK, CAPTURED, LOST, CANCELLED}`, `Voyage.pursuit/shipId`).

**Rule for all packages:** own `ModModule` per package (`rpg/career/CareerModule` id `rpg.career`, `rpg/quest/QuestModule`, `mob/captain/CaptainModule`), one line each in `core/ModModules` (orchestrator). Nobody edits `RpgModule`. Only QST1 edits `Deed.java`. Each package adds `docs/playtests/<name>.md` and a guide page.

---

## CAR1 — Careers: navy ranks, pirate infamy, letter of marque

**Goal.** A per-player `CareerRecord` with the navy rank (NONE, MIDSHIPMAN, LIEUTENANT, CAPTAIN, COMMODORE, ADMIRAL), the infamy rank (DECKHAND, BUCCANEER, DREAD_CAPTAIN, PIRATE_LORD, or similar four), the letter of marque state, and deed counters (pirates killed, captains killed, navy killed, merchants plundered, plunder coins fenced, bounties claimed, pirates turned in, quests per faction, ships captured — the last fed later by WS3b). Promotions are checked after every deed and quest; the two ladders exclude each other; the letter is granted and voided by rules; an officer screen shows it all; `/pirates career`.

**Decisions.**
1. *Stored rank, pure eligibility.* Ranks are events (lost on desertion), so `CareerRecord` stores them; `CareerRules.nextNavyRank(record, navyRep, pirateRep, thresholds)` / `nextInfamyRank(...)` are pure and decide promotions. Recommended over "rank = f(rep)" because §15 wants deeds and quests to count and demotion to be sticky.
2. *Counters from `Deeds.listen`.* `CareerDeeds` maps deeds to counters using `DeedContext.victimKind` (so BOS1's captain counts ×`captain_weight` under `KILL_PIRATE` without a new deed) and `amount` (FENCE_PLUNDER coins = plunder value). No new `Deed` values.
3. *Auto promotion with a title message* (no ceremony): simplest, fully testable; the officer screen lists the next rank's requirements. Pirate infamy also auto.
4. *Mutual exclusion.* Enlisting (officer screen) needs infamy == DECKHAND, pirate rep ≤ `max_pirate_rep_to_enlist` (0), no bounty, not navy-hostile. While `enlisted`, a desertion deed (configurable list; default ATTACK_NAVY, KILL_NAVY, ATTACK_MERCHANT_SHIP, PLUNDER_MERCHANT) or reaching BUCCANEER sets the navy rank to NONE and reports `CrimeType.DESERTION` (exists, 60 points → the law places the bounty itself). Infamy promotion is refused while enlisted.
5. *Letter of marque.* Granted at the officer screen for `letter_fee` doubloons when not enlisted, navy rep ≥ `letter_min_navy_rep` (20), infamy ≤ BUCCANEER; voided by the same desertion deeds (no crime, cooldown `letter_void_days`). Effect now: each pirate kill while ACTIVE adds `letter_prize.<tier>` to a `prizeMoney` counter paid out at the officer screen ("Collect prize"); ship prizes come with WS3b (`Voyages.onEnd` SUNK/CAPTURED pirate voyage → CAR follow-up).
6. *Officer interaction* via `CommonEvents.ENTITY_INTERACT` on a `NavyOfficer`: main hand empty, not sneaking, no proof, `OfficerTurnIns.heldPrisonersNear(...)` empty, officer not hostile → open `CareerScreen` (payloads `CareerPayloads.Open/State/Action`, actions ENLIST, RESIGN, REQUEST_LETTER, COLLECT_PRIZE; server re-checks reach 4 blocks and the officer). Does not touch `NavyOfficer.java` or `OfficerTurnIns`.
7. *Rank sync* through its own `CareerSyncPayload` (navy rank, infamy rank, letter state, prize), `CareerSync` copied from `ReputationSync`; `ClientCareer` store.

**Files (common).** `rpg/career/`: `NavyRank`, `InfamyRank` (enums with id, nameKey, `flagRight`, `standingBonus`), `LetterState`, `CareerRecord` (record + codec: ranks, letter, enlisted, counters map `EnumMap<CareerCounter,Long>`, prizeMoney), `CareerCounter` (enum), `CareerRules` (pure), `CareerThresholds` (pure snapshot of config), `CareerAttachments`, `Careers` (service: `record`, `promoteIfEligible`, `enlist`, `resign`, `grantLetter`, `voidLetter`, `onDeed`, `recordQuest(player, Faction)`, `desert`), `CareerDeeds`, `CareerInteractions` (ENTITY_INTERACT), `CareerPayloads`, `CareerBackend`, `CareerSync`, `CareerSyncPayload`, `ClientCareer`, `CareerCommands` (`/pirates career [player]`, ops `set navy|infamy <rank>`, `letter grant|void`), `CareerConfig`, `CareerModule`, `CareerGameTests`; `rpg/career/client/CareerScreen`, `CareerClient`; JUnit `CareerRulesTest`, `CareerRecordTest`. One-line edit after REP1 merges: `ReputationCommands.show` appends `Careers.describe(player)`.

**Config `careers.*`:** `enabled`; `navy.<rank>.{min_navy_rep, pirates_killed, quests}` (Midshipman 10/0/0, Lieutenant 25/5/1, Captain 45/15/3, Commodore 65/30/6, Admiral 85/60/10); `infamy.<rank>.{min_pirate_rep, plunder_coins, captures_or_captains}`; `max_pirate_rep_to_enlist` 0; `desertion_deeds` (string list); `desertion_is_crime` true; `letter.{enabled, min_navy_rep 20, fee 200, void_days 3, prize_deckhand 5, prize_captain 50}`; `captain_weight` 5.

**GameTests.** Enlist a clean mock player → MIDSHIPMAN; five `Deeds.record(KILL_PIRATE)` + rep set 25 → LIEUTENANT; enlisted + ATTACK_NAVY → rank NONE, DESERTION on the criminal record, `enlisted=false`; infamy: FENCE_PLUNDER amounts over `plunder_coins` + pirate rep → BUCCANEER, refused while enlisted; enlist refused at BUCCANEER; letter: grant takes the fee, a KILL_PIRATE adds prize, PLUNDER_MERCHANT voids it; record survives death (copyOnDeath) and codec round-trip (JUnit); `enabled=false` promotes nothing (own config batch); the interaction opens nothing for a hostile officer; `/pirates career` output.

**Risks / open.** Desertion by an accidental cannon hit on a merchant (same window as `ATTACK_MERCHANT_SHIP`'s 30 s repeat): accept or require KILL/PLUNDER only? Thresholds are guesses for the playtest. Should resigning cost anything?

---

## CAR2 — Rank rewards (after CAR1)

**Goal.** Make ranks matter with what exists: the navy flag right, docking fees, officer gear, navy-outpost shipwright with a rank discount, fence prices with infamy, infamy hostility.

**Decisions.** (1) Flag right: `FlagCrimes` (one line, REP1 edited it) passes `Careers.effectiveNavyStanding(player)` = `max(rep, law.flags.navy_flag_min_standing)` when `NavyRank.flagRight` (Lieutenant+); same in `TradeService.dockingFee`. (2) Promotion gift: Lieutenant gets a bicorne + saber, Captain a `ShipReceipt`-free coin purse? Recommend items only (`careers.rewards.<rank>` item-id list, default bicorne, saber). (3) Navy shipyard: `ShipOrders.place/pickup` accept `PortKind.NAVY_OUTPOST` when `Careers.navyRank ≥ orders_min_rank` with price × `careers.navy_ship_discount` (0.7 at Captain, 0.5 at Commodore); `MarketBackend.openDesk` sends the Orders view at outposts too. (4) Fence: `MarketReputation.priceScore` adds `InfamyRank.priceBonus` at pirate islands; `Reputation.piratesFriendly` OR infamy ≥ DREAD_CAPTAIN in `SeafarerMob.describe` (one line). (5) Navy hunts high infamy harder: `law.world` threshold stays; WS4b's `HuntRules` reads `Careers.infamy` later (note only). Crew hiring and "more patrols" need other packages.

**Files.** `rpg/career/CareerRewards` (pure + apply), small edits in `law/world/FlagCrimes`, `trade/TradeService`, `ship/template/ShipOrders` (+`KEY_NOT_VILLAGE` → kind check), `trade/net/MarketBackend.openDesk`, `rpg/market/MarketReputation`, `mob/entity/SeafarerMob.describe`; `CareerRewardsGameTests`.

**Config.** `careers.rewards.*`: `flag_right_rank` LIEUTENANT, `navy_orders_min_rank` CAPTAIN, `navy_ship_discount`, `fee_waiver_rank`, `<rank>_items`, `infamy_price_bonus` 20, `infamy_pirates_friendly_rank`.

**GameTests.** Lieutenant under a navy flag with rep 0 is no false flag (`LawService.isFalseFlag` with the effective standing); docking fee 0 for the waiver rank; an order at an outpost desk refused at Midshipman, accepted at Captain with the discounted price; fence quote cheaper for DREAD_CAPTAIN; promotion puts the bicorne in the inventory.

**Open.** Should the navy ship discount also apply to the navy's own templates (none exist beyond two sloops)?

---

## HON1 — Honor and status (after CAR1)

**Goal.** The captain's title visible: a name prefix ("Lt.", "Capt.", "Dread Pirate"), a rank line in the HUD, and the title on the ship.

**Decisions.** (1) Prefix via a vanilla scoreboard team per rank (`PlayerTeam.setPlayerPrefix`, server, no mixin; toggle `careers.name_prefix`, only when the player is on no other team). (2) HUD: a small layer `rpg/career/client/RankHud` (`ClientEvents.registerHudLayer`, `ShipHudClient` pattern) with rank, navy/pirate rep from `ClientReputation`, letter state; client config section `career_hud.{enabled, x, y}`. (3) Ship: the nameplate shows `ShipData.name`; recommend the title as a prefix drawn by the nameplate renderer from the owner's synced rank (needs the owner's rank on the client: add `ownerTitle` to the ship-name sync, or simpler: `ShipAssembler.name` auto-prefixes when naming while the toggle is on). Recommend the renderer option as a follow-up and ship only the prefix-on-naming now. Flag textures are fixed per kind; a title on the flag is not recommended.

**Files.** `rpg/career/CareerTitles` (pure), `CareerTeams`, `client/RankHud`, `RankHudLayout` (pure, JUnit); `HonorGameTests` (team prefix set/cleared on promotion and demotion; naming with the title).

---

## QST1 — Quests (core, buildable now)

**Goal.** Quest offers per port, a per-player quest log, accept/abandon/complete, rewards (doubloons, reputation deed, career progress), a Quests tab at every desk, a command. Types now: HUNT_PIRATES (kill N), KILL_MONSTER (sharks N, kraken 1), TURN_IN (deliver N shackled pirates), DELIVER (a quest-made `DeliveryContract` with a raised reward), FIND_TREASURE (hands out a bound map; done when the site is looted), HUNT_NAVY (pirate islands: kill N navy). HUNT_CAPTAIN waits for BOS1 (QST1b), ESCORT / PLUNDER_CONVOY / HUNT_PATROL / HUNT_SHIP wait for WS3b/WS4b (QST2).

**Decisions.** (1) Storage: `QuestLog` player attachment (active quests ≤ `max_active` 3, completed counts) + `QuestData` SavedData `pirates_n_ships_quests` with offers per port (regenerated at the day edge, `offers_per_port` 3, `offer_days` 2) — no change to `Port`. (2) `Quest(id, port, giver PortKind, type, target (good/entity type/port/site/UUID as `QuestTarget` sum codec), needed, progress, rewardCoins, rewardDeed, deadlineDay, state OFFERED|ACTIVE|DONE|FAILED)`; pure `QuestRules.advance(quest, QuestEvent)` and `QuestGenerator.offers(kind, climate, rng, params)`. (3) Progress from `Deeds.listen` (KILL_PIRATE, KILL_NAVY, TURN_IN_PIRATE), `LIVING_DEATH` with a player killer (monsters), a 20-tick poll for DELIVER (contract state) and FIND_TREASURE (`TreasureBinding.found`). (4) Rewards: coins via `Wallet.give`, reputation via three new deeds `COMPLETE_NAVY_QUEST (+6 navy, −2 pirates)`, `COMPLETE_PIRATE_QUEST (+6 pirates, −2 navy)`, `COMPLETE_VILLAGE_QUEST (+4 villagers)` (edits `Deed.java` + `RpgModule` lang; CAR1 counts them, so no direct CAR1 call), optional `ShipReceipt` reward for rare quests later. (5) UI: a Quests tab in `MarketScreen` modelled on Orders (`QuestPayloads.Quests/QuestAction`, `MarketBackend.openDesk` + handler lines, `ClientMarketState.quests()`); the officer/captain mobs do not give quests in this package (CAR1 owns the officer gesture). (6) `/pirates quest list|abandon <id>`, ops `offer <port> <type>`, `complete <id>`.

**Files.** `rpg/quest/`: `Quest`, `QuestType`, `QuestTarget`, `QuestState`, `QuestLog`, `QuestData`, `QuestRules`, `QuestGenerator`, `QuestAttachments`, `Quests` (service), `QuestTracker` (listeners + poll), `QuestRewards`, `QuestPayloads`, `QuestBackend`, `QuestCommands`, `QuestText`, `QuestConfig`, `QuestModule`, `QuestGameTests`; `trade/client/MarketScreen` (+tab), `ClientMarketState`, `MarketText`; `rpg/deeds/Deed`, `rpg/RpgModule` (lang only); JUnit `QuestRulesTest`, `QuestGeneratorTest`.

**Config `quests.*`:** `enabled`, `offers_per_port`, `offer_days`, `max_active`, `reward_scale`, `deadline_days`, `hunt_count_min/max`, `treasure_quest_enabled`, per-kind type toggles.

**GameTests.** Fake port (`PortRegistry.add` gametest id, removed at the end): offers generated for each kind; accept → in the log; HUNT_PIRATES progresses on `Deeds.record(KILL_PIRATE)` and completes with coins and the deed delta; KILL_MONSTER on a shark death by the mock player; TURN_IN on `TURN_IN_PIRATE`; FIND_TREASURE hands a bound map and completes when the site is marked looted; DELIVER completes when its contract is delivered; deadline fails it (day set); `max_active`; `enabled=false` offers nothing; log survives codec round-trip (JUnit); `/pirates quest list`.

**Open.** Should DELIVER show in both tabs? Passenger quests need a seat entity — skip. Reward for HUNT_NAVY is a crime for the player (intended).

---

## BOS1 — Named pirate captains and the duel

**Goal.** Every pirate island gets a named captain in the captain's hut with a fixed navy bounty on the notice boards; a player can challenge him to a duel (his crew holds back); he drops a hat, coins and a map; he counts as `PirateTier.CAPTAIN` when captured; a successor appears after a cooldown.

**Decisions.** (1) `PirateCaptain extends Pirate`, `MobKind.PIRATE_CAPTAIN` (faction PIRATE, skill PIRATE_CAPTAIN, 40 health, pirate hat equipped), persistent, `stationary`; registered in `MobContent`, per-kind toggles in `MobConfig`, texture reuse of the pirate with a hat; `MobLoot.pirateCaptain()`. (2) Names from `CaptainNames` (pure, seeded by port id: "Black-Tooth Bartholomew Kidd"), `setCustomName`. (3) Placement at generation like `Garrison`: `IslandCaptains.place(level, chunkBox, pieces)` at a fixed post of the `captains_hut` piece; a `CaptainRegistry` SavedData `pirates_n_ships_captains` (port → id, name, alive, diedDay). (4) Bounty: new `BountyBoard.placeStandingBounty(id, target, amount, now)` + `LawService.placeStandingBounty(...)` (NAVY source, random id so `syncNavy` never withdraws it); amount `captain_bounty` 300; the existing `ProofDrops` then gives the proof. (5) Duel: sneak-use the captain holding a sword → `DuelChallenge`: pirates within `duel_truce_range` get a truce for the challenger (`SeafarerMob.truce(player, until)` consulted in `attacksOnSight`), the captain targets only the challenger; truce ends on his death or the player's. (6) Death: deed `KILL_PIRATE` (automatic; CAR1 weights it), `dropCustomDeathLoot` adds a map bound to that island, registry marks dead; respawn after `respawn_days` at the next day tick when the hut chunk is loaded. (7) `NavyOfficer.pirateTier` returns CAPTAIN for a `PirateCaptain`. A hunted voyage with his ship is BOS2 after WS3b (`Voyages.spawn` kind HUNT with the captain as a fighter, QST2's HUNT_SHIP targets it).

**Files.** `mob/captain/`: `PirateCaptain`, `CaptainNames`, `CaptainRegistry`, `IslandCaptains`, `DuelChallenge`, `DuelRules` (pure), `CaptainCommands` (`/pirates mob captain spawn|list`), `CaptainConfig` (`mobs.captain.*`), `CaptainModule`, `CaptainGameTests`; edits: `mob/MobKind`, `MobContent`, `MobConfig`, `MobLoot`, `MobModule` (lang), `mob/client/MobClient` (renderer), `mob/entity/SeafarerMob` + `HostilityRules` (truce), `mob/entity/NavyOfficer.pirateTier`, `law/bounty/BountyBoard`, `law/LawService`, `world/island` (afterPlace hook); JUnit `CaptainNamesTest`, `DuelRulesTest`.

**Config `mobs.captain.*`:** `enabled`, `health` 40, `bounty` 300, `respawn_days` 5, `duel_truce_range` 16, `duel_enabled`, `drop_map`.

**GameTests.** `IslandCaptains.spawn` → named, registry entry, `LawService.hasBounty` true, `NoticeBoardListing.lines` contains the name; challenge → nearby pirate ignores the challenger, captain targets him; kill → proof in inventory, hat/doubloons/map dropped, registry dead; capture + officer turn-in pays the CAPTAIN tier; respawn rule (JUnit day math); `enabled=false` spawns nothing.

**Risks / open.** `MobKind` enum growth ripples through MobConfig lang; the hut post coordinates must be read from `captains_hut.py`; crew truce vs. `mobs.pirates_hostile` off. Should the captain refuse a duel to a player he likes (high pirate rep) and offer quests instead?

---

## Order and parallelism

- **Wave 1 (parallel, all after REP1 merges):** CAR1, QST1, BOS1. Disjoint files except: QST1 alone edits `Deed`/`RpgModule`/`MarketScreen`/`MarketBackend`; BOS1 alone edits `mob/*` and `law/bounty`; CAR1 edits only `ReputationCommands` (one line). All three add one `ModModules` line (orchestrator).
- **Wave 2:** CAR2 and HON1 (after CAR1, parallel; CAR2 touches `FlagCrimes`, `TradeService`, `ShipOrders`, `MarketBackend.openDesk`, `MarketReputation`, `SeafarerMob`); QST1b HUNT_CAPTAIN (after QST1 + BOS1).
- **Wave 3 (after WS3b/WS4b land):** QST2 (ESCORT, PLUNDER_CONVOY, HUNT_PATROL, HUNT_SHIP via `Voyages.onEnd` and the last shooter from `CannonShipHits`), BOS2 (captain's hunted voyage), CAR letter prizes for ships and `CareerCounter.SHIPS_CAPTURED` from `VoyageEnd.CAPTURED`.

**Missing helpers:** `BountyBoard.placeStandingBounty` + `LawService` wrapper (BOS1); `SeafarerMob` truce (BOS1); `Careers.effectiveNavyStanding` hook points in `FlagCrimes`/`TradeService` (CAR2); `MarketBackend.onTrade` listener does not exist (QST1 avoids it by wrapping contracts); `PLUNDER_MERCHANT` carries no amount (CAR1 uses FENCE_PLUNDER coins); crew hiring (milestone 15) blocks the "recruit at outposts/islands" reward.

### Critical Files for Implementation
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/rpg/deeds/Deeds.java (on branch worktree-agent-a5133ef2e79ae91a3)
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/law/LawService.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/law/turnin/OfficerTurnIns.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/trade/net/MarketBackend.java
- /Users/richard/Projects/modding/mc/pirates-n-ships/common/src/main/java/com/richardsenger/piratesnships/world/outpost/Garrison.java
