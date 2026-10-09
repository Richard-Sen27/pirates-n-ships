# Sound audit (AUD1)

Every action and event a player notices, with the sound it plays today and the recording that would fit. Checked
against the code and `common/src/generated/resources/assets/pirates_n_ships/sounds.json` on main on 2026-10-09
(commit `40a00db4`). "Sound today" comes from the code, never from the design: where the design promises a sound the
code does not play, the cell says so.

**Priorities**

- **A**: silent today. The A list below is ordered by how often and how loudly a player meets the moment.
- **B**: plays a vanilla sound, either straight (`SoundEvents.…`) or through one of our own event ids whose
  `sounds.json` entry still points at vanilla files (the anchor and the lookout). Rows marked *vanilla fits* are fine
  as they are and come last.
- **C**: our own recording exists. Some C rows still want more variants or a dedicated file; that is said in the row.

**How to read the "fitting recording" column:** what a player expects to hear, its length, loop or one-shot, and how
much pitch variation the code should add. The `→ event` hint is the sound event id an agent would wire it to (new
ids are suggestions; name the manifest `event` after them so the wiring is mechanical).

## Summary

| Priority | Rows | Meaning |
|---|---|---|
| A | 59 | silent |
| B | 91 | vanilla sound (78 worth replacing, 13 where vanilla fits) |
| C | 15 | own recording (3 of them registered with files but never played) |
| | 165 | total |

### A: silent, most prominent first

| Rank | Row | Action |
|---|---|---|
| 1 | SA4 | Wind in the rigging and the canvas while sailing |
| 2 | SA1 | Setting, reefing and furling a sail by hand |
| 3 | SA22 | The hull running through the water (wake) |
| 4 | S9 | Flooding: water rising inside the hull |
| 5 | S10 | Working the bilge pump |
| 6 | SA10 | Turning the helm's wheel |
| 7 | ST1 | Blowing the captain's whistle (opening the order wheel) |
| 8 | S8 | A breach opening below the waterline |
| 9 | S12 | A ship sinking |
| 10 | S1 | Assembling a ship at the helm |
| 11 | SA3 | A luffing sail flapping head to wind |
| 12 | H4 | Storm wind and heavy seas |
| 13 | SA19 | Open-sea wind ambience |
| 14 | SA21 | Calm sea lapping at the hull |
| 15 | C6 | A cannonball whistling through the air |
| 16 | W8 | Treasure found |
| 17 | S13 | A ship breaking into pieces |
| 18 | S2 | Disassembling a ship |
| 19 | ST3 | An order reaching the crew (heard aboard) |
| 20 | SA2 | Crew setting or furling sail on an order |
| 21 | AL1 | Opening the Captain's Almanac |
| 22 | R1 | Promotion or rank change |
| 23 | C20 | Hauling two ships together on grapple ropes |
| 24 | C43 | A pistol or musket ball striking wood |
| 25 | S16 | Hoisting or striking the flag (surrender) |
| 26 | L1 | A crime noticed, wanted level rising |
| 27 | ST18 | Wages paid at dawn |
| 28 | ST16 | Hiring a recruit |
| 29 | W3 | A market buy or sell going through |
| 30 | ST10 | Crew meal at the pantry |
| 31 | ST20 | Mutiny |
| 32 | R5 | Quest completed, reward given |
| 33 | C32 | A fighter staggering |
| 34 | SA9 | Taking the helm |
| 35 | ST8 | Lying down in or getting out of a hammock |
| 36 | W7 | Reading a treasure map |
| 37 | W1 | Opening the harbor desk |
| 38 | AL4 | Closing the Almanac |
| 39 | R2 | Letter of marque granted or voided |
| 40 | R4 | Quest accepted |
| 41 | W4 | Delivery contract accepted or delivered |
| 42 | SA11 | The rudder groaning under load |
| 43 | C5 | A cannon recoiling on its carriage |
| 44 | ST4 | Crew acknowledgements ("Aye!") |
| 45 | SA7 | Mending a torn sail |
| 46 | ST15 | Rum ration at dawn |
| 47 | ST19 | A deserter leaving at port |
| 48 | C11 | Chain shot whirring in flight |
| 49 | UI1 | Opening the ship screen |
| 50 | ST11 | Opening the pantry |
| 51 | SA24 | Tying a rope line |
| 52 | ST5 | Assigning a crew member with the whistle |
| 53 | ST17 | Dismissing a crew member |
| 54 | R3 | Enlisting, resigning or deserting a career |
| 55 | L2 | False colours detected |
| 56 | C36 | Raising the guard |
| 57 | C33 | A feint |
| 58 | SV1 | Freezing in cold water |
| 59 | M13 | A shark approaching (optional) |

### B: vanilla placeholders worth replacing, most prominent first

Combat and the sea first, then the kraken, sharks and voices, then the rest.

| Row | Action | Vanilla today |
|---|---|---|
| C7 | Cannonball splashing into the sea (miss) | `entity.generic.splash` |
| S5 | Cannonball smashing hull planks | `block.wood.break` + `entity.zombie.break_wooden_door` |
| SA20 | Bow spray and waves slapping the hull in rough seas | `entity.generic.splash` |
| SA12 | Anchor chain running out / hauled in | `anchor.chain` → `block/chain/step1-6` |
| SA17 | Capstan pawl while winding | `anchor.capstan` → `block/iron_trapdoor/close1-4` |
| SA14 | Anchor splashing in or out | `anchor.splash` → `liquid/heavy_splash` |
| SA16 | Anchor chain snapping taut | `anchor.jolt` → `random/anvil_land` |
| SA15 | Anchor landing on the seabed | `anchor.thud` → `dig/stone1-4` |
| SA13 | Anchor dragging on the seabed | `anchor.chain`, low pitch |
| ST21 | Lookout's sighting hail | `lookout.hail` → `block/bell/bell_use01/02` at pitch 1.5 |
| S14 | Ship's bell (rung or raid alarm) | `block.bell.use` |
| C1 | Loading a cannon | `block.sand.place` + `block.iron_door.close` |
| C2 | Aiming a cannon | `block.wood.hit` |
| S6 | Cannonball stopped by a block | `block.stone.hit` |
| S7 | Cannonball glancing off | `block.anvil.land` |
| C9 | Chain shot tearing into rigging | `block.chain.break` |
| SA5 | Sail torn (chain shot, storm) | `block.wool.break` |
| SA6 | Ropes cut by chain shot | `entity.leash_knot.break` |
| C10 | Grapeshot pellets hitting wood | `block.chain.hit` |
| C8 | Cannonball hitting a mob or player | target's vanilla hurt sound only |
| C15 | Throwing the grappling hook | `entity.fishing_bobber.throw` |
| C16 | Grapple latching | `block.chain.place` + `entity.leash_knot.place` |
| C19 | Grapple rope snapping | `entity.leash_knot.break` |
| C18 | Grapple reeled in | `entity.fishing_bobber.retrieve` |
| C17 | Grapple missing into water | `entity.fishing_bobber.splash` |
| C22 | Sliding along a grapple rope, landing | `entity.leash_knot.place`, `item.armor.equip_leather` |
| C23 | Laying a boarding plank | the plank's `block.wood.place`, pitch ×0.8 |
| C25 | Boarding plank snapping when ships part | `block.wood.break` |
| C24 | Picking a boarding plank up | `block.wood.break`, pitch ×0.8 |
| C12 | Loading a swivel gun | `block.sand.place` + `block.iron_door.close` |
| C37 | Starting to load a firearm | `item.crossbow.loading_start` |
| C38 | Ramrod, half way | `item.crossbow.loading_middle` |
| C39 | Firearm loaded (cocked) | `item.crossbow.loading_end` |
| M15 | Kraken surfacing | `entity.elder_guardian.ambient`, pitch 0.5 |
| M17 | Kraken tentacle grabbing a hull or swimmer | `entity.slime.squish` |
| M18 | Kraken smashing a mast | `entity.zombie.break_wooden_door` |
| M19 | Kraken tentacle swipe | `entity.player.attack.sweep` |
| M20 | Kraken tentacle cut off | `entity.slime.death` |
| M16 | Kraken retreating | `entity.elder_guardian.death`, pitch 0.5 |
| M21 | Kraken hurt / death | `entity.elder_guardian.hurt` / `.death` |
| M14 | Kraken ambient | `entity.elder_guardian.ambient_land` |
| M10 | Shark bite | `entity.evoker_fangs.attack` |
| M11 | Shark hurt / death | `entity.cod.hurt` / `.death` |
| M9 | Shark swimming | `entity.dolphin.swim` |
| M1 | Pirate voice (ambient, hurt, death) | pillager |
| M2 | Pirate captain voice | pillager |
| M3 | Captain's duel challenge and start | `entity.pillager.ambient` / `.celebrate` |
| M6 | Navy soldier voice | vindicator |
| M7 | Navy officer voice | vindicator |
| M4 | Sailor voice | villager |
| ST7 | Crew member voice | none ambient; `entity.generic.hurt` / `.death` |
| H1 | Waterspout | `weather.rain.above`, pitch 0.45–0.55 |
| H2 | Whirlpool | `block.bubble_column.whirlpool_ambient` |
| S4 | Rejoining a split ship (toolkit hammering) | `block.wood.hit`, `block.wood.place` |
| S11 | Nailing a hull patch | `block.wood.place` |
| L8 | Shackling a mob | `block.chain.place` |
| L11 | Locking / unlocking the brig door | iron trapdoor + `block.tripwire.click_on` |
| L13 | Brig door opening / closing | `block.iron_door.open` / `.close` |

The remaining 20 B rows (M5, M8, R6, L3, L4, L5, L6, L7, L9, L10, L12, ST2, ST14, SV3, SV4, SV6, W2, W10, AL2, AL3) are officer and
harbor-master grunts, coins, chains and paper: worth a pass once the list above is done. The 13 *vanilla fits* rows
(S15, S17, SA8, ST6, ST12, ST13, SV5, SV7, C21, M12, H5, W6, UI2) need nothing.

### C: own recordings that are never played

These three events have converted files and a `sounds.json` entry but no code plays them; they need code, not
recordings: `combat.cannon_volley` (C4), `combat.melee.disarm` (C34), `combat.melee.weapon_break` (C35). The ship
creak's two variants (S3) are cut from one recording whose author and URL are still `TBD` in the manifest. C41
(musket) reuses the pistol shot at pitch 0.7 and its subtitle says "Pistol fires".

### Adding a sound you found (the pipeline)

Full instructions: [`tools/sounds/README.md`](../../tools/sounds/README.md); the design is §16 "Sound pipeline".

1. **Licence first.** Only Pixabay Content License, CC0, CC-BY (credited) or your own recordings. Note the page URL,
   the title and the author while you download.
2. **Drop the file** (mp3, wav, …) into `raw_sound/` at the repository root, in a subfolder per area if you like
   (`raw_sound/sails/…`). The folder is git-ignored; files there do nothing until the manifest names them.
3. **Add a manifest entry** to `tools/sounds/manifest.json`:

   ```json
   {
     "source": "sails/canvas-flap-123456.mp3",
     "target": "sailing/sail_set_1.ogg",
     "kind": "effect",
     "event": "sailing.sail_set",
     "title": "Canvas Flap",
     "author": "someone",
     "url": "https://pixabay.com/sound-effects/canvas-flap-123456/",
     "license": "Pixabay Content License"
   }
   ```

   `kind` is `effect` (converted to mono so Minecraft plays it positionally, about -16 LUFS) or `music` (stays
   stereo, about -18 LUFS). Several entries may share one `event` as variants (`sail_set_1.ogg`, `sail_set_2.ogg`).
   `start` and `duration` (seconds, both together) cut a stretch out of a longer source, so one long recording can
   give several variants. `author` and `url` may be `TBD` for now; the credits flag it.
4. **Convert:** `python3 tools/convert_sounds.py` (needs `ffmpeg`; with Homebrew's ffmpeg 8 also `oggenc` from
   `brew install vorbis-tools`). It writes the `.ogg` under
   `common/src/main/resources/assets/pirates_n_ships/sounds/<target>` and regenerates `docs/credits.md` from the
   manifest. Targets newer than their source are skipped; `--force` converts everything again (needed after
   changing a cut).
5. **Wiring is code.** The JUnit test `SoundResourcesTest` fails the build when a manifest file is not listed under
   its `event` in the generated `sounds.json`, so a manifest entry and its wiring land together:
   - *A new variant of an event that already plays our files* (`ship.creak`, `combat.*`): add the file name to the
     event's list (`AudioModule.CREAK_SOUNDS`, `CombatSounds.ALL`) and run `./gradlew :neoforge:runData`.
   - *Replacing a vanilla placeholder behind our own id* (`anchor.*` in `sailing/anchor/AnchorData.SOUNDS`,
     `lookout.hail` in `station/lookout/LookoutModule.gatherData`): swap the `minecraft:…` names for
     `pirates_n_ships:<target without .ogg>` and run `./gradlew :neoforge:runData`.
   - *Everything else* (every A row, every B row that plays `SoundEvents.…` directly) needs a new `ModRegistry.sound`
     event, a `data.sounds(...)` entry with a subtitle, and the `playSound` call: an agent package. Until then keep
     the found files in `raw_sound/` and list them (file, licence, URL, the row id here) so the package can take
     them as they are.
6. **Commit** the `.ogg`, `manifest.json`, `docs/credits.md` and the regenerated `sounds.json` together.

## Ships (design §4)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| S1 | Assembling a ship at the helm | `ship/assembly/HelmBlock.useWithoutItem` → `ShipAssembler.assemble`; also `ship/template/ShipTemplatePlacer` | none | none | Deep timber groan and a settling thump as the hull comes free, 1.5–2.5 s, one-shot, ±5 % pitch → `ship.assemble` | A |
| S2 | Disassembling a ship | Ship screen's Disassemble button (`ship/screen/ShipScreens`) or sneak-use on the helm with the screen off (`HelmBlock.useWithoutItem`) → `ShipAssembler.disassemble` | none | none | Timber creak, a couple of knocks and a settle, 1–2 s, one-shot → `ship.disassemble` | A |
| S3 | Hull creaks while the ship rolls | `audio/creak/ShipCreaks.play` (trigger `CreakTrigger`) | own `pirates_n_ships:ship.creak` (2 variants cut from one recording) | "Ship creaks" | Have it. 3–4 more variants from other recordings would cut the repetition; the source's author and URL are still `TBD` in the manifest | C |
| S4 | Rejoining a split-off piece (Shipwright's Toolkit) | `ship/assembly/ShipRejoin.use` (start), `ShipRejoin.onLevelTick` (hammer blow every 4 ticks, then done) | vanilla `block.wood.hit` (3 pitches), `block.wood.place` when done | vanilla | Carpenter's mallet on timber and nails, 0.3–0.5 s, 4–6 variants played in sequence, a closing thunk → `ship.hammer` | B |
| S5 | Cannonball smashing hull planks | `combat/cannon/CannonballEntity.onHitBlock` (blocks destroyed) | vanilla `block.wood.break` (vol 2, pitch 0.7) + `entity.zombie.break_wooden_door` | vanilla | Splintering timber crash, 0.8–1.5 s, one-shot, 3–4 variants, ±8 % pitch → `combat.cannonball_hit_wood` | B |
| S6 | Cannonball stopped by a block it cannot break | same, nothing destroyed | vanilla `block.stone.hit` (pitch 0.6) | vanilla | Heavy dull thud of iron on oak or stone, 0.4 s, 3 variants | B |
| S7 | Cannonball glancing off a hull | same, bounce branch | vanilla `block.anvil.land` (vol 0.4, pitch 1.6) | vanilla | Iron ball skipping off planks: a sharp knock and a whirr away, 0.5 s, 2–3 variants | B |
| S8 | A breach opening below the waterline | `ship/hull/runtime/HullRuntime.onBlockChanged` / `finishAnalysis` (new breach in `BreachSet`) | none | none | Water bursting through a hole, gush onset, 1–2 s one-shot (the flooding loop takes over) → `ship.breach` | A |
| S9 | Flooding: water rising in a compartment | `HullRuntime.physicsTick` (`FloodSimulation`) | none (design §16 says "flooding water") | none | Rushing, gurgling water inside a wooden hull, seamless loop 5–10 s, volume by inflow → `ship.flooding` | A |
| S10 | Working the bilge pump (player or crew) | `ship/hull/pump/BilgePumpBlock.useWithoutItem` → `BilgePumps.operate`; crew `station/pump/PumpStation` | none | none | Wooden pump handle clank and a slosh of water over the side, 0.6–1 s per stroke, 3–4 variants, ±5 % → `ship.pump` | A |
| S11 | Nailing a hull patch over a hole | `ship/hull/pump/HullPatchItem` (a `BlockItem`, vanilla placement) | vanilla `block.wood.place` (block sound type WOOD) | vanilla | Three hammer blows on a plank, 1–1.5 s, one-shot, 2–3 variants → `ship.patch` | B |
| S12 | A ship sinking | no event of its own; wrecks in `ship/assembly/ShipSplits`, NPC ships in `worldsim/materialize/VoyageEndings` | none | none | Long timber groan, air bubbling out, the gurgle of going under, 4–8 s one-shot → `ship.sink` | A |
| S13 | A ship breaking into pieces | `ShipSplits.process` / `breakUp` | none | none | Loud cracking keel and splintering, 2–3 s one-shot → `ship.split` | A |
| S14 | Ship's bell rung (by hand or as a raid alarm) | `ship/decor/ShipsBellBlock.ring` | vanilla `block.bell.use` (vol 2) | vanilla | Brass ship's bell, a double strike "ding-ding" with a 1.5–3 s ring-out, 2 variants → `ship.bell` | B |
| S15 | Stern window opening / closing | `ship/decor/SternWindowBlock.toggle` | vanilla `block.wooden_trapdoor.open` / `.close` | vanilla | *vanilla fits* | B |
| S16 | Hoisting, lowering or striking the flag | `ship/decor/flag/FlagpoleMachine.use` / `tick` / `setStruck` | none | none | Halyard running through a block (squeaky sheave), 1–2 s, and a cloth snap at the top → `ship.flag_hoist` | A |
| S17 | Placing, breaking and walking on ship blocks | block sound types: WOOD (planks, helm, decor, yards, pump), WOOL (ratlines, rope coil, hammock, rigging lines), CHAIN (chainplates, stays, mooring ring), LANTERN, ANVIL (ship's bell), METAL (cannon, swivel, brig door), CHISELED_BOOKSHELF (Almanac); multi-block breaks through `levelEvent 2001` | vanilla | vanilla | *vanilla fits* | B |

## Sailing (design §5)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| SA1 | Setting, reefing or furling a sail by hand (cycling the trim) | `sailing/block/YardBlock.useWithoutItem`, `CleatBlock.useWithoutItem`, `SailWinchBlock.useWithoutItem` | none | none | Canvas dropping or gathered with a heavy flap, rope running through blocks, 1–2 s, one-shot; separate set and furl variants (2 each) → `sailing.sail_set`, `sailing.sail_furl` | A |
| SA2 | Crew setting or furling sail on an order | `station/winch/WinchStation` (order from `WhistleOrders.issue`) | none | none | Same files as SA1 | A |
| SA3 | A sail luffing (head to wind, the cloth flapping) | `sailing/sail/SailAir` (drive near 0); shown only by the cloth renderer | none | none | Canvas flogging and thundering in the wind, loop 3–6 s, volume by wind strength → `sailing.luff` | A |
| SA4 | Wind in the rigging and the drawing canvas while under way | `sailing/wind`, `sailing/ship/SailingRuntimes` | none (design §16: "sail flapping, rope, wind"; client `audio.ambience_volume` exists but is read by nothing) | none | Wind whistling through shrouds plus taut canvas and rope creak, seamless loop 10–20 s, mono, volume by apparent wind → `sailing.rigging_wind` | A |
| SA5 | A sail torn (chain shot, storm) | `combat/cannon/RiggingDamage.tear`; `hazards/HazardForces.tearSails` | vanilla `block.wool.break` (vol 1.5–2, pitch 0.6–0.7) | vanilla | Canvas ripping, 0.8–1.5 s, one-shot, 3 variants, ±8 % → `sailing.sail_tear` | B |
| SA6 | Ropes cut by chain shot | `RiggingDamage.cutRopes` | vanilla `entity.leash_knot.break` | vanilla | A taut rope parting with a whip crack, 0.4–0.8 s, 3 variants (shared with C19) → `sailing.rope_snap` | B |
| SA7 | Mending a torn sail | `sailing/block/YardBlockEntity.mend` | none | none | Needle through canvas, short rustle, 0.5–1 s | A |
| SA8 | Dyeing a sail or hanging a banner | `YardBlock.useItemOn`, `CleatBlock.useItemOn` | vanilla `item.dye.use` / `block.wool.place` | vanilla | *vanilla fits* | B |
| SA9 | Taking the helm | `sailing/helm/HelmService.start` | none | none | Hands closing on wooden spokes, a soft knock, 0.3 s | A |
| SA10 | Turning the wheel | `HelmService.turn` / `handleWheel` | none | none | Wheel spokes and tiller ropes creaking, 0.2–0.4 s grains played every few degrees, 4 variants, ±5 % → `sailing.helm_turn` | A |
| SA11 | The rudder groaning under load | rudder force in `sailing/ship/SailingRuntimes` | none | none | Low wooden groan of pintles and gudgeons, loop 3–5 s, volume by rudder angle × speed | A |
| SA12 | Anchor chain running out or hauled in | `sailing/anchor/AnchorEntities.effects` (RUNNING cue) | own id `pirates_n_ships:anchor.chain` → vanilla `block/chain/step1-6` | "Anchor chain rattles" | Heavy iron chain paying out through a hawse, 2–4 s grains that can repeat, ±15 % (the code already varies pitch 0.75–1.05) | B |
| SA13 | Anchor dragging along the seabed | same, SCRAPE cue | `anchor.chain` at pitch 0.45–0.6 | "Anchor chain rattles" | Chain grinding over rock and sand, 1–2 s, 3 variants → `anchor.scrape` | B |
| SA14 | Anchor splashing into or out of the water | same, water change | own id `anchor.splash` → vanilla `liquid/heavy_splash` | "Anchor splashes" | A heavy iron weight plunging into the sea, 1–1.5 s, 2–3 variants | B |
| SA15 | Anchor landing on the seabed | same, resting | own id `anchor.thud` → vanilla `dig/stone1-4` | "Anchor lands" | Muffled underwater thud, 0.5–1 s, 2 variants | B |
| SA16 | Anchor chain jolting taut | same, JOLT cue | own id `anchor.jolt` → vanilla `random/anvil_land` (pitch 0.55–0.65) | "Anchor chain jolts taut" | Chain jerking taut: a metallic clank and a hull groan, 0.6–1 s, 2 variants | B |
| SA17 | Capstan pawl while winding (player or crew) | same, CAPSTAN cue; capstan use `sailing/block/CapstanBlock.useWithoutItem`, crew `station/capstan/CapstanStation` | own id `anchor.capstan` → vanilla `block/iron_trapdoor/close1-4` | "Capstan clanks" | Capstan pawl dropping over the ratchet, "clack", 0.2–0.4 s, 4 variants, plus the creak of the drum | B |
| SA19 | Open-sea wind ambience (aboard or swimming at sea) | nowhere (design §16 "wind"; `audio.ambience_volume` unused) | none | none | Open-sea wind bed, seamless loop 30–60 s, light / strong / gale layers → `ambient.sea_wind` | A |
| SA20 | Bow spray and waves slapping the hull in rough seas | `hazards/waves/client/WaveClient.onSpray` | vanilla `entity.generic.splash` (vol 0.35–0.65, pitch 0.8–1.1) | vanilla | Bow wave slapping against a wooden hull, 1–2 s, one-shot, 4–6 variants, ±10 % → `ambient.hull_slap` | B |
| SA21 | Calm sea lapping at the hull | nowhere (design §5.4: waves "through ship motion, sound and particles") | none | none | Water lapping against planks, seamless loop 20–40 s → `ambient.hull_lap` | A |
| SA22 | The hull running through the water (wake) | `sailing/effects/client/WakeClient` (particles only) | none | none | Water rushing along the hull, seamless loop 5–10 s, volume and pitch by speed → `ambient.wake` | A |
| SA24 | Tying a rope line between two points | `sailing/rope/RopeAnchorUse` | none | none | A rope knot cinched tight, 0.3–0.5 s | A |

(SA18 and SA23 were folded into SA17 and SA4.)

## Stations and crew (design §6, §7)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| ST1 | Blowing the captain's whistle (opens the order wheel) | `station/winch/CaptainsWhistleItem.use` → client `station/client/WhistleMenuScreen` | none | none | Bosun's call (boatswain's pipe): a rising-falling whistle, 0.8–1.5 s, 2–3 variants → `crew.whistle` | A |
| ST2 | Confirming an order in the wheel | `WhistleMenuScreen.confirm` (local UI sound) | vanilla `ui.button.click` (pitch 0.6) | vanilla | A short pipe trill, 0.5 s → `crew.whistle_confirm` | B |
| ST3 | An order reaching the crew (heard by everyone aboard) | `station/order/WhistleOrders.issue` | none | none | Bosun's call, positional at the captain; distinct calls per order family (sails, anchor, guns) would be a bonus | A |
| ST4 | Crew acknowledgements | gone since MSG1: the "Aye!" lines were action-bar text and never had a sound | none | none | Optional, needs a design decision: short voiced "Aye!", "Aye, captain", 0.5 s, 6+ variants over several voices | A |
| ST5 | Assigning a crew member with the whistle | `CaptainsWhistleItem.interactLivingEntity` | none | none | A short pipe chirp, 0.3 s | A |
| ST6 | Crew walking about the deck | vanilla step sounds of the block underfoot (`crew/walk/CrewWalk` moves the mob) | vanilla block steps | vanilla | *vanilla fits* | B |
| ST7 | Crew member voice | `crew/npc/CrewMember` (no overrides) | ambient none; hurt `entity.generic.hurt`; death `entity.generic.death` | vanilla | Sailor grunts and mutters: 3 ambient hums or mutters, 3 hurt, 2 death, 0.3–1 s, ±8 % → `crew.ambient/hurt/death` | B |
| ST8 | Lying down in or getting out of a hammock (crew or player) | `crew/hammock/CrewRest.lieDown` / `getUp`; `PlayerSleep.lieDown` / `onWakeUp` | none | none | Hammock canvas creak and rope strain, 0.8 s, 2 variants | A |
| ST10 | Meal time: crew sitting down at the pantry (twice a day) | `crew/galley/MealVisits.serve` / `sitDown` | none | none | Wooden bowls and spoons, chewing, low chatter, 2–4 s → `crew.meal` | A |
| ST11 | Opening the pantry | `crew/galley/PantryBlock.useWithoutItem` (opens the menu) | none | none | Wooden cupboard door, 0.4 s (vanilla `block.barrel.open` would also do) | A |
| ST12 | Filling or draining the water barrel | `crew/galley/WaterBarrelBlock.useItemOn` | vanilla bucket and bottle fill / empty | vanilla | *vanilla fits* | B |
| ST13 | Eating hardtack, salt pork, salted fish, a lime | vanilla food items (`crew/content/CrewContent.food`) | vanilla `entity.generic.eat` | vanilla | *vanilla fits* | B |
| ST14 | Drinking rum | `trade/content/DrinkItem.getEatingSound` | vanilla `entity.generic.drink` | vanilla | Bottle glug and a satisfied "aah", 1 s | B |
| ST15 | Rum ration issued at dawn | `crew/upkeep/ShipDayTick.rumRations` (`crew/provisions/RumIssue`) | none | none | Bottles clinking, pouring, a short cheer, 1–2 s | A |
| ST16 | Hiring a recruit at a port | `crew/hiring/Hiring.hire` / `place` | none | none | Coins on wood and a gruff "Aye", 0.8 s → `crew.hire` | A |
| ST17 | Dismissing a crew member | `Hiring.dismiss` | none | none | A grumble, 0.5 s | A |
| ST18 | Wages paid at dawn | `crew/upkeep/ShipDayTick.run` (`WageRules.pay`) | none | none | Coin purse jingle, 0.6–1 s, 3 variants → `crew.wages` (shared with W3) | A |
| ST19 | A deserter walking off at port | `crew/upkeep/Desertions.walkOff` | none | none | A sour mutter, 1 s | A |
| ST20 | Mutiny (off by default) | `crew/upkeep/ShipDayTick.run` (mutiny branch) | none | none | Angry crowd shout and blades drawn, 2–3 s sting → `crew.mutiny` | A |
| ST21 | Lookout's sighting hail (the HUD cue) | `station/lookout/client/LookoutClient.tick` (cue sent by `Lookouts.deliver`) | own id `pirates_n_ships:lookout.hail` → vanilla `block/bell/bell_use01/02` at pitch 1.5, vol 0.6 | "Lookout rings the bell" | A voiced "Sail ho!" / "Land ho!" from aloft, or a small handbell, 1–1.5 s, 3 variants (the subtitle would change with a voice) | B |

## Combat (design §8)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| C1 | Loading a cannon (player or crew) | `combat/cannon/CannonService.load` | vanilla `block.sand.place` + `block.iron_door.close` (pitch 0.6) | vanilla | Powder charge rammed home, the ball rolling down the barrel, a ramrod thump, 1–1.5 s, 2 variants → `combat.cannon_load` | B |
| C2 | Aiming a cannon (elevation step) | `CannonService.aim` | vanilla `block.wood.hit` | vanilla | Wooden quoin knocked in or out, carriage creak, 0.3 s, 3 variants | B |
| C3 | A cannon firing (round, chain or grape) | `CannonService.fire` | own `pirates_n_ships:combat.cannon_shot` (vol 4, pitch 0.9–1.1) | "Cannon fires" | Have it. 2–3 more variants and a distant layer would help a broadside | C |
| C4 | Cannons firing far away | registered `combat.cannon_volley` with a file; no code plays it | own (unused) | "Cannons fire in the distance" | Have it; needs code (e.g. for NPC battles beyond a range) | C |
| C5 | A cannon recoiling on its carriage | no event (the recoil is an impulse only) | none | none | Wooden truck wheels rumbling back on deck, 0.8 s | A |
| C6 | A cannonball in flight | `combat/cannon/CannonballEntity` tick | none | none | A ball whistling past, doppler sweep, 1 s, played near the listener → `combat.cannonball_whistle` | A |
| C7 | A cannonball splashing into the sea (miss) | `CannonballEntity.splash` | vanilla `entity.generic.splash` (vol 1.5, pitch 0.7); grapeshot 9 small splashes (vol 0.3, pitch 1.6) | vanilla | Heavy plunge with a water column, 1–1.5 s, 3 variants; small pellet plinks for grape → `combat.shot_splash` | B |
| C8 | A cannonball hitting a mob or player | `CannonballEntity.onHitEntity` | the target's vanilla hurt sound only | vanilla | Heavy body impact thud, 0.4 s | B |
| C9 | Chain shot striking rigging | `CannonballEntity.chainHit` (plus SA5, SA6) | vanilla `block.chain.break` (vol 1.5, pitch 0.7) | vanilla | Whirling chain crashing into canvas and ropes, 1 s, 2 variants | B |
| C10 | Grapeshot pellets hitting a block | `CannonballEntity.pelletHit` | vanilla `block.chain.hit` (vol 0.4, pitch 1.8) | vanilla | Lead pellets peppering wood, "thk-thk", 0.3 s, 4 variants | B |
| C11 | Chain shot whirring in flight | none | none | none | Spinning chain whirr, 1 s loop near the listener | A |
| C12 | Loading a swivel gun | `combat/cannon/SwivelService.load` | vanilla `block.sand.place` + `block.iron_door.close` | vanilla | Small ramrod and wad, 0.8 s | B |
| C13 | A swivel gun firing | `SwivelService.fire` | own `combat.cannon_shot` at pitch 1.4–1.6, vol 2 | "Cannon fires" | Own file, pitched up; a dedicated sharper small-bore crack, 0.8–1.5 s, would fit better | C |
| C15 | Throwing the grappling hook | `combat/grapple/GrappleService.launch` | vanilla `entity.fishing_bobber.throw` | vanilla | Rope whirled overhead and an iron hook swooshing, 0.6 s → `combat.grapple_throw` | B |
| C16 | The grapple latching | `GrapplingHookEntity.latch` | vanilla `block.chain.place` + `entity.leash_knot.place` | vanilla | An iron hook biting into wood and the rope snapping taut, 0.5 s, 3 variants → `combat.grapple_latch` | B |
| C17 | The grapple missing into water | `GrapplingHookEntity.miss` | vanilla `entity.fishing_bobber.splash` | vanilla | A small hook splash, 0.5 s | B |
| C18 | Reeling the grapple in | `GrapplingHookEntity.tickRetracting`, `release` (normal) | vanilla `entity.fishing_bobber.retrieve` | vanilla | Rope hauled in hand over hand, 0.8 s | B |
| C19 | The grapple rope snapping (too far) | `GrapplingHookEntity.release(TOO_FAR)` | vanilla `entity.leash_knot.break` | vanilla | Rope parting with a whip crack, 0.5 s (shared with SA6) | B |
| C20 | Hauling two ships together | `GrappleService.haul` (physics tick) | none | none | Rope and timber creaking under heavy strain, loop 2–4 s → `combat.grapple_strain` | A |
| C21 | Tying the rope off at a ring or cleat | `GrappleService.tieOff` | vanilla `entity.leash_knot.place` | vanilla | *vanilla fits* | B |
| C22 | Sliding along a rope; landing | `combat/grapple/RopeSlideService.tryBoard` (`entity.leash_knot.place`), `tickRider` / `tickPull` landing (`item.armor.equip_leather`) | vanilla | vanilla | Hands or a hook zipping along a rope, loop 1–2 s, and boots landing on deck, 0.4 s | B |
| C23 | Laying a boarding plank | `combat/boarding/BoardingPlanks.lay` | the plank's `block.wood.place`, pitch ×0.8 | vanilla | A heavy plank dropped onto a gunwale, wooden clunk, 0.5 s, 2 variants → `combat.plank_lay` | B |
| C24 | Picking a boarding plank up | `BoardingPlanks.pickUp` | `block.wood.break`, pitch ×0.8 | vanilla | The plank dragged off the rail, 0.6 s | B |
| C25 | A boarding plank snapping when the ships part | `BoardingPlankBlockEntity.serverTick` → `destroyBlock` | vanilla `block.wood.break` | vanilla | A plank cracking and falling into the water, 1 s | B |
| C26 | A sword swing missing (slash; thrust lower) | `combat/melee/sound/MeleeSoundPlayer.onStateChange` | own `combat.melee.miss` (6 variants) | "Sword misses" | Have it | C |
| C27 | A sword hitting flesh | `MeleeSoundPlayer.onSwordHit` | own `combat.melee.hit` (2 variants) | "Sword hits" | Have it; 2–3 more variants | C |
| C28 | A thrust, riposte or staggering hit landing | `onSwordHit` | own `combat.melee.hit_heavy` (2 variants) | "Sword hits" | Have it | C |
| C29 | Parry, blade on blade, guarded hit | `onSwordHit`, `onVanillaHit` | own `combat.melee.clash` (6 variants) | "Blades clash" | Have it | C |
| C30 | A sword hitting a chestplate | `onSwordHit` | own `combat.melee.hit_armor` (1 file) | "Armour rings" | Have it; 2 more variants | C |
| C31 | Drawing a sword | `MeleeSoundPlayer.onPlayerTick` | own `combat.melee.unsheathe` | "Sword drawn" | Have it | C |
| C32 | A fighter staggering | none (silent by design since P8) | none | none | A body grunt and a stumbling step, 0.4 s, 3 variants | A |
| C33 | A feint | none (silent by design) | none | none | A light cloth swish, 0.3 s | A |
| C34 | Being disarmed | registered `combat.melee.disarm` with a file; no code plays it | own (unused) | "Sword clatters to the ground" | Have it; needs code | C |
| C35 | A sword breaking | registered `combat.melee.weapon_break` with a file; no code plays it (vanilla's item-break sound plays when durability runs out) | own (unused) | "Sword breaks" | Have it; needs code | C |
| C36 | Raising the guard | none | none | none | Leather and steel shifting, 0.3 s | A |
| C37 | Starting to load a pistol or musket | `combat/firearms/FirearmItem.use` | vanilla `item.crossbow.loading_start` | vanilla | Powder poured from a horn, 0.6 s → `combat.firearm_load` | B |
| C38 | The ramrod, half way through loading | `FirearmItem.onUseTick` | vanilla `item.crossbow.loading_middle` | vanilla | Ramrod rattling down the barrel, 0.6 s, 2 variants | B |
| C39 | Loading finished (cocking the lock) | `combat/firearms/FirearmService.completeLoading` | vanilla `item.crossbow.loading_end` | vanilla | Flintlock hammer cocked, "click-clack", 0.4 s | B |
| C40 | A pistol firing (player or NPC) | `FirearmService.fireWithSpread` | own `combat.pistol_shot` (pitch `firearms.pistol.sound_pitch` 1.0 ±5 %) | "Pistol fires" | Have it | C |
| C41 | A musket firing | same | own `combat.pistol_shot` at pitch 0.7 (`firearms.musket.sound_pitch`) | "Pistol fires" | Own file pitched down; a dedicated musket shot (deeper, longer tail, 1.5–2 s) and a "Musket fires" subtitle would fit | C |
| C42 | An empty gun or a misfire in the rain | `FirearmService.playEmpty` (from `fireWithSpread`, `FirearmItem.use`, `FirearmTrigger`) | own `combat.pistol_empty` | "Pistol clicks" | Have it; a flash-in-the-pan fizz would set the rain misfire apart | C |
| C43 | A pistol or musket ball striking a block | `combat/firearms/LeadBallEntity` (no hit sound; a hit on a mob plays only its hurt sound) | none | none | A ball thudding into wood, or a ricochet whine off stone, 0.3 s, 3 variants → `combat.ball_hit` | A |

(C14 was folded into S17.)

## Mobs (design §9)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| M1 | Pirate ambient, hurt, death | `mob/entity/Pirate.ambientSound` / `hurtSound` / `deathSound` | vanilla pillager | vanilla | Gruff pirate voice: "Arr", laughs, threats; 4 ambient, 3 hurt, 2 death, ±8 % → `mob.pirate.*` | B |
| M2 | Pirate captain voice | `mob/captain/PirateCaptain` (inherits Pirate) | vanilla pillager | vanilla | A deeper, slower variant of M1 | B |
| M3 | A captain's duel challenge; the duel starting | `mob/captain/DuelChallenge.interact` / `start` | vanilla `entity.pillager.ambient` (pitch 0.8) / `entity.pillager.celebrate` | vanilla | A captain's taunt ("Draw yer steel!"), 1–2 s, 2–3 variants | B |
| M4 | Sailor ambient, hurt, death | `mob/entity/Sailor` | vanilla villager | vanilla | Merchant sailor hums and grunts, as M1 | B |
| M5 | Harbor master answering (refusal, offer) | `mob/harbor/HarborMasterInteractions.talk` (+ the Sailor voice) | vanilla `entity.villager.no` / `entity.villager.trade` | vanilla | An official's "Hm?" and a quill on paper, 0.5–1 s | B |
| M6 | Navy soldier ambient, hurt, death | `mob/entity/NavySoldier` | vanilla vindicator | vanilla | Drilled soldier barks ("Halt!", "Make ready!"), hurt grunts | B |
| M7 | Navy officer ambient, hurt, death | `mob/entity/NavyOfficer` | vanilla vindicator | vanilla | An officer's clipped voice | B |
| M8 | Navy officer answering (career, ship grants) | `rpg/career/CareerInteractions.onEntityInteract`, `ShipGrants.onEntityInteract` / `redeemAtOfficer` | vanilla `entity.villager.no` / `.ambient` / `.yes` | vanilla | Approving "Very good" / refusing "Out of the question", 0.5–1 s | B |
| M9 | Shark swimming | `mob/entity/Shark.getSwimSound` | vanilla `entity.dolphin.swim` | vanilla | A fin cutting the water, 1 s | B |
| M10 | Shark bite | `Shark.bite` | vanilla `entity.evoker_fangs.attack` | vanilla | Jaws snapping shut with a thrash and splash, 0.5 s, 3 variants → `mob.shark.bite` | B |
| M11 | Shark hurt, death | `Shark.getHurtSound` / `getDeathSound` | vanilla `entity.cod.hurt` / `entity.cod.death` | vanilla | Wet thrashing, 0.5 s | B |
| M12 | Shark flopping on land | `Shark.flop` | vanilla `entity.cod.flop` (pitch ×0.6) | vanilla | *vanilla fits* | B |
| M13 | A shark approaching | none (no ambient sound) | none | none | Optional: sharks are silent animals; a low "danger" sting when one circles would be a design choice | A |
| M14 | Kraken ambient (not while lurking) | `mob/kraken/Kraken.getAmbientSound` | vanilla `entity.elder_guardian.ambient_land` | vanilla | Deep wet rumble from below, 2–4 s, 3 variants → `mob.kraken.ambient` | B |
| M15 | The kraken surfacing | `Kraken.onStateChange` (SURFACE) | vanilla `entity.elder_guardian.ambient` (vol 4, pitch 0.5) | vanilla | A huge bellow from the deep with water pouring off, 3–5 s, one-shot → `mob.kraken.surface` | B |
| M16 | The kraken retreating | `onStateChange` (RETREAT) | vanilla `entity.elder_guardian.death` (vol 4, pitch 0.5) | vanilla | A wounded shriek sinking away underwater, 3 s | B |
| M17 | A tentacle grabbing a hull or a swimmer | `Kraken.touch` (first contact) | vanilla `entity.slime.squish` (pitch 0.5) | vanilla | A wet suction slap on wood, 0.6 s, 3 variants | B |
| M18 | A tentacle smashing a mast | `Kraken.strikeMast` | vanilla `entity.zombie.break_wooden_door` | vanilla | A mast cracking and timber splintering, 1–2 s | B |
| M19 | A tentacle swipe | `Kraken.swipe` | vanilla `entity.player.attack.sweep` (pitch 0.5) | vanilla | A heavy wet whoosh, 1 s | B |
| M20 | A tentacle cut off | `Kraken.onCut` | vanilla `entity.slime.death` (pitch 0.5) | vanilla | A squelching sever and a screech, 1 s | B |
| M21 | Kraken hurt, death | `Kraken.getHurtSound` / `getDeathSound` | vanilla `entity.elder_guardian.hurt` / `.death` | vanilla | A pain bellow, 1–2 s; a long death groan, 4–6 s | B |

## World and economy (design §10)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| W1 | Opening the harbor desk | `trade/desk/HarborDeskBlock.useWithoutItem` → `HarborDeskService.use` | none | none | A ledger opened on a desk, 0.5 s | A |
| W2 | Buy and sell buttons in the market | `trade/client/MarketScreen.mouseClicked` (local) | vanilla `ui.button.click` | vanilla | Coins on a counter, 0.4 s, 3 variants | B |
| W3 | A buy or sell going through | `trade/exchange/MarketTransactions.buy` / `sell` | none | none | Coin purse clink (shared with ST18) | A |
| W4 | A delivery contract accepted or delivered | `MarketTransactions.acceptContract` / `deliverContract` | none | none | A paper stamp and coins, 0.8 s | A |
| W6 | The notice board (open, pages, buttons) | `law/client/NoticeBoardScreen.onState` / `mouseClicked` | vanilla `item.book.page_turn`, `ui.button.click` | vanilla | *vanilla fits* | B |
| W7 | Reading a treasure map | `world/treasure/TreasureMapItem.use` → `TreasureMapService.use` | none | none | Parchment unrolled, 0.6 s | A |
| W8 | Treasure found (the site's chest opened) | `TreasureMapService.onContainerOpen` | none (only vanilla's chest open) | none | A short triumphant sting or coins pouring, 1.5–3 s → `world.treasure_found` | A |
| W10 | The chart table (open, draw, clear, markers) | `chart/net/ChartBackend.open`, `chart/tile/MapTileService.handleDraw` / `handleClear`, `chart/client/ChartScreen` | vanilla `item.book.page_turn`, `entity.villager.work_cartographer`, `ui.button.click` | vanilla | A quill scratching on parchment, 1 s, 2 variants | B |

(W5 port dues are paid through the desk and covered by W1 and W3; wrecks and ports have no events of their own.)

## Law (design §13)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| L1 | A crime noticed, the wanted level rising | `law/crime/CriminalRecord.addCrime` (from the `law` services) | none | none | A short ominous drum hit or a distant navy whistle, 1 s → `law.crime` | A |
| L2 | False colours detected | `law/flag/FalseColorsDetection` | none | none | Same sting as L1 | A |
| L3 | Placing a bounty at the notice board | `law/net/NoticeBoardBackend.place` | vanilla `item.book.page_turn` | vanilla | A poster nailed up, two knocks, 0.6 s | B |
| L4 | Turning in bounty proof | `law/turnin/OfficerTurnIns.turnInProof` | vanilla `entity.villager.yes` + `entity.experience_orb.pickup` | vanilla | A coin purse handed over, 0.6 s | B |
| L5 | Delivering or ransoming prisoners | `OfficerTurnIns.deliver` | vanilla `entity.villager.yes` + `block.chain.break` | vanilla | Shackles unlocked and coins, 1 s | B |
| L6 | Paying a fine | `OfficerTurnIns.payFine` | vanilla `entity.villager.ambient` / `.yes` + `entity.experience_orb.pickup` | vanilla | Coins counted onto a table, 0.8 s | B |
| L7 | An officer refusing | `OfficerTurnIns.refuse` | vanilla `entity.villager.no` | vanilla | A stern "No." (with M8) | B |
| L8 | Shackling a mob | `law/brig/BrigService.capture` | vanilla `block.chain.place` | vanilla | Iron shackles snapping shut, 0.5 s, 2 variants → `law.shackle` | B |
| L9 | A prisoner escaping | `BrigService.escape` | vanilla `block.chain.break` | vanilla | Chains breaking and running feet, 1 s | B |
| L10 | Releasing or press-ganging a prisoner | `law/brig/PrisonerInteractions.release` / `pressGang` | vanilla `block.chain.break` (+ `entity.villager.no` for press-gang) | vanilla | Shackles unlocked with a key, 0.6 s | B |
| L11 | Locking or unlocking the brig door with the key | `law/content/BrigDoorBlock.useKey` | vanilla `block.iron_trapdoor.close` / `.open` + `block.tripwire.click_on` | vanilla | A heavy key turning in an iron lock, 0.6 s, lock and unlock variants → `law.brig_lock` | B |
| L12 | Rattling the locked brig door | `BrigDoorBlock.useWithoutItem` | vanilla `block.chain.hit` | vanilla | Iron bars rattling, 0.5 s | B |
| L13 | The brig door opening and closing | `BrigDoorBlock.playDoorSound` (block set type in `law/content/LawContent`) | vanilla `block.iron_door.open` / `.close` | vanilla | An iron-barred door creaking and clanging, 1 s | B |

## Survival and sea chest (design §11, §14)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| SV1 | Freezing in cold water | `survival/cold/ColdWater.onLevelTick` | none | none | Shivering breath, 1 s, 2 variants | A |
| SV3 | Putting the sea chest on or picking it up from the water | `seachest/SeaChestItem.getEquipSound`; `SeaChestEntity.interact` | vanilla `item.armor.equip_leather` | vanilla | A heavy wooden chest on leather straps, 0.6 s | B |
| SV4 | Launching the sea chest into the water | `SeaChestItem.launch` | vanilla `entity.generic.splash` | vanilla | A wooden crate splashing down, 0.8 s | B |
| SV5 | Paddling the floating chest | `SeaChestEntity.paddle` | vanilla `entity.boat.paddle_water` | vanilla | *vanilla fits* | B |
| SV6 | Opening and closing the sea chest (placed or floating) | `SeaChestBlockEntity.startOpen` / `stopOpen`, `SeaChestEntity.startOpen` / `stopOpen` | vanilla `block.chest.open` / `.close` (pitch 0.9) | vanilla | A heavy lid on iron hinges, 0.6 s | B |
| SV7 | Putting on a hat or apparel | `apparel/HatItem.getEquipSound`, `ApparelContent.material` | vanilla `item.armor.equip_leather` | vanilla | *vanilla fits* | B |

(SV2, swim hunger, has nothing to hear.)

## Hazards and weather (design §12)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| H1 | A waterspout | `hazards/HazardVisuals.tick` (client, every second) | vanilla `weather.rain.above` (vol 3, pitch 0.45–0.55) | vanilla | A roaring, spinning column of water, seamless loop 5–10 s → `hazard.waterspout` | B |
| H2 | A whirlpool | `HazardVisuals.tick` (every 2 s) | vanilla `block.bubble_column.whirlpool_ambient` (vol 3, pitch 0.6–0.7) | vanilla | A deep swirling roar, seamless loop 8–15 s → `hazard.whirlpool` | B |
| H4 | A storm at sea (gale, heavy seas) | sea state STORM in `hazards/waves`; the mod adds only the bow spray of SA20 | none of its own (vanilla rain and thunder play) | none | Storm wind howling in the rigging and seas crashing, seamless loop 30–60 s → `ambient.storm` | A |
| H5 | Lightning | vanilla weather; the mod adds nothing | vanilla thunder | vanilla | *vanilla fits* | B |

(H3, sails torn by a hazard, is SA5.)

## RPG and quests (design §15)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| R1 | Promotion or rank change (navy rank, infamy) | `rpg/career/Careers.promoteIfEligible` (also `setNavy`, `setInfamy`) | none | none | A short fanfare: fife and drum for the navy, a dark sting for infamy, 2–3 s → `rpg.promotion` | A |
| R2 | A letter of marque granted or voided | `Careers.grantLetter` / `voidLetter` | none | none | A wax seal pressed and paper folded, 1 s | A |
| R3 | Enlisting, resigning or deserting | `Careers.enlist` / `resign` / `desert` | none | none | Short sting (shared with R1 or L1) | A |
| R4 | A quest accepted | `rpg/quest/Quests.accept` | none | none | Paper and quill, 0.6 s | A |
| R5 | A quest completed, the reward given | `rpg/quest/QuestRewards.grant` | none | none | A reward sting with coins, 1.5 s → `rpg.quest_complete` | A |
| R6 | A ship grant redeemed | `rpg/career/ShipGrants.redeemAtOfficer` | vanilla `entity.villager.yes` / `.no` | vanilla | Covered by M8 | B |

## The Almanac (design §3.4)

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| AL1 | Opening the Almanac (in hand or on its stand) | `almanac/AlmanacItem.use`, `AlmanacBlock.useWithoutItem` → client `almanac/client/AlmanacScreen` | none (design §3.4 promises a page-turn sound only) | none | A heavy leather-bound book opened, 0.6–1 s → `almanac.open` | A |
| AL2 | Turning a page | `AlmanacScreen.turnTo` | vanilla `item.book.page_turn` | vanilla | A thick parchment page turning, 0.4–0.6 s, 3–4 variants | B |
| AL3 | Clicking a bookmark, link or button | `AlmanacScreen.clickPage` | vanilla `ui.button.click` | vanilla | A soft paper tap, 0.2 s | B |
| AL4 | Closing the Almanac | none | none | none | A book shut with a thump, 0.4 s → `almanac.close` | A |

## HUD and screens

| ID | Action / event | Where it fires | Sound today | Subtitle today | Fitting recording | Pri |
|---|---|---|---|---|---|---|
| UI1 | Opening the ship screen (sneak-use on the helm) | `ship/screen/ShipScreens` (open) | none | none | A logbook opened, 0.5 s (could share AL1) | A |
| UI2 | Buttons in the ship screen | `ship/screen/client/ShipScreen.mouseClicked` | vanilla `ui.button.click` | vanilla | *vanilla fits* | B |

The whistle wheel is ST1/ST2, the market screen W2, the chart W10, the lookout's HUD cue ST21.

## Music (design §16, "Pools")

The music manager is `audio/music/SeaMusic`: it answers the client `SELECT_MUSIC` event with the pool
`MusicSelector.select` picks for the `MusicSituation`, and scales our tracks by `audio.music_volume` when their stream
starts. A running track is never cut off; the gap between tracks is `min_gap_seconds` to `max_gap_seconds` (120–300 s).

| Pool (event) | Trigger | Tracks today | Source |
|---|---|---|---|
| `music.shanty` | Aboard a ship (on deck, riding, or seated at a station), with `shanties_aboard` on | "Leave Her Johnny" (Alexander Nakarada), "The Ghost Of Gallows Reef" (kaazoom) | `AudioSounds.SHANTY_TRACKS` |
| `music.sea` | At sea but not aboard (ocean, deep ocean or beach biome), or aboard with `shanties_aboard` off | "There Be Pirates – The Quest", "There Be Pirates – Lost in the Deep" (Pixabay user 44363205) | `AudioSounds.SEA_TRACKS` |
| vanilla | Anywhere else, or `music_enabled` off | vanilla | — |

**What is missing.** The design (§16) and the `music_enabled` config comment name battle and harbor music; neither
exists in code, and there are no music discs.

| Wanted pool | Trigger it would need | What to look for |
|---|---|---|
| `music.battle` | A ship battle nearby: cannons fired or a hostile ship within range (the gunnery and voyage code know both) | Driving drums and brass or fiddle, 1.5–3 min, 2–3 tracks, a clear start so a cut-in feels right |
| `music.harbor` | Inside a port's bounds (`world/port/PortIndex`) | Calm accordion, fiddle or guitar tavern tunes, 2–4 min, 2–3 tracks |
| `music.storm` | Sea state STORM while aboard | Dark, rolling strings and low drums, 2–3 min, 1–2 tracks |
| `music.sinking` | Your own ship sinking (no event exists yet, see S12) | A short mournful sting, 20–40 s, one-shot |
| `music.kraken` | The kraken surfacing (M15) | A tense boss theme, 1.5–2 min |
| `music.almanac` | While the Almanac is open (optional; a book screen usually stays quiet) | A quiet music-box or harpsichord loop, 1–2 min |
| more `music.shanty` / `music.sea` | the existing triggers | 2–4 more tracks each, so a long voyage repeats less (two tracks per pool is thin with 2–5 min gaps) |
| music discs | jukebox | A shanty or two as discs, if wanted |

Music files use `"kind": "music"` in the manifest (kept stereo, about -18 LUFS) and `SoundEntries.file(name).stream()`
in datagen. A new track for an existing pool goes into `AudioSounds.SEA_TRACKS` or `SHANTY_TRACKS`; a new pool needs
a `MusicSituation`, a `MusicSelector` rule and its trigger: an agent package.
