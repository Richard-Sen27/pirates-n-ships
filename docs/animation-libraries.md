# Player animation libraries for the melee milestone

Research package G5, written on 2026-10-07. It answers the open question in `docs/design.md` §21: which
player-animation library drives the first- and third-person sword animations of milestone 8 (§8.5, §20), on
Minecraft 1.21.1, for both NeoForge and Fabric, from our multiloader layout. No mod code changes in this package.

What the library has to drive (`common/.../combat/melee/rules/Phase.java`, `CombatState.java`): per combatant a
phase `IDLE`, `WINDUP`, `ACTIVE`, `RECOVERY` (each with `elapsed` and `duration` ticks from the weapon definition),
`GUARDING`, `PARRYING`, `STAGGERED`, an `AttackKind` (`SLASH`, `THRUST`), a riposte flag and a parry lockout.
Phase timings are data-driven per weapon, so the animation layer must be able to play an animation at a chosen
speed and start it part-way through (to catch up on a phase that started before the packet arrived).

## Summary table

| Candidate | 1.21.1 NeoForge | 1.21.1 Fabric | Animates the player model | First person | Common artifact | Syncs over network | Licence | Maintenance | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| **Player Animation Library (PAL)**, ZigyTheBird | `com.zigythebird.playeranim:PlayerAnimationLibNeo:1.1.6+mc.1.21.1` | `com.zigythebird.playeranim:PlayerAnimationLibFabric:1.1.6+mc.1.21.1` | Yes (layers, priorities, bends, item bones) | Yes: renders third-person model parts in first person, arms and held item configurable | Yes: `PlayerAnimationLibCommon` + `PlayerAnimationLibCore` | No, left to the mod | MIT | Active: 1.21.1 branch commits 2026-07-29, 1.21.1 release 2026-08-18, newest release on any version 2026-10-06 | **Recommended** |
| **PlayerAnimator**, KosmX | `dev.kosmx.player-anim:player-animation-lib-forge:2.0.4+1.21.1` | `dev.kosmx.player-anim:player-animation-lib-fabric:2.0.4+1.21.1` | Yes | Yes (`FirstPersonMode.THIRD_PERSON_MODEL`) | Yes: `player-animation-lib:2.0.4+1.21.1` | No | MIT | Maintenance only: README says "NO-LONGER-UPDATED", last release 2025-12-28 | Fallback |
| Better Combat, ZsoltMolnarrr | (mod, not a library) | | Uses PlayerAnimator on 1.21.1, PAL on 1.21.11+ | Via the library | n/a | Own payloads | All Rights Reserved | Active | Reference only |
| GeckoLib | 4.9.3 | 4.9.3 | No (entities, items, armour, block entities) | Item models only | Yes | Own trigger sync for its own animatables | MIT | Active | For NPCs only (already planned) |
| AzureLib | 3.1.16 | 3.1.16 | No | Arm bones inside an animated item model | Yes | Own | MIT | Active (release 2026-10-06) | Not suitable for melee |
| Not Enough Animations, tr7zw | 1.12.6 | 1.12.6 | Cosmetic tweaks, no API | n/a | n/a | n/a | tr7zw Protective License | Active | Not a library; compat target |
| Animated Java (Blockbench plugin) | n/a (data pack) | n/a | No: display-entity rigs | No | n/a | Vanilla entities | NOASSERTION on GitHub | Active | Not suitable |
| Epic Fight | NeoForge only for 1.21.1 | No 1.21.1 Fabric build found | Yes, full overhaul | Yes | n/a | Own | GPL-3.0-or-later | Active | Excluded (§8.5: no overhaul dependency; not on both loaders) |

## Candidates

### Player Animation Library (PAL)

- **What it is.** "A library that allows mods to animate the player, in a way that doesn't conflict with other
  mods." The documentation calls it "the official successor to PlayerAnimator". It is based on GeckoLib's animation
  code (up to GeckoLib 4.8.4, MIT, noted in `GeckoLib.txt` in the repo) with full Bedrock-format support and Molang
  (via `mochafloats`, a fork of `unnamed/mocha`, which is MIT).
  - Repo: https://github.com/PlayerAnimationLibrary/PlayerAnimationLibrary (moved from `ZigyTheBird/PlayerAnimationLibrary`)
  - Docs: https://docs.zigythebird.com/pal/intro
  - Modrinth: https://modrinth.com/mod/player-animation-library, CurseForge: https://www.curseforge.com/minecraft/mc-mods/player-animation-library
- **1.21.1 artifacts.** Maven repository `https://repo.redlance.org/public`
  (listing: https://repo.redlance.org/#/public/com/zigythebird/playeranim). Group `com.zigythebird.playeranim`,
  version `1.1.6+mc.1.21.1` for all four artifacts:
  `PlayerAnimationLibCore` (loader- and Minecraft-independent animation core, package `com.zigythebird.playeranimcore`),
  `PlayerAnimationLibCommon` (Minecraft code, mixins, API, package `com.zigythebird.playeranim`),
  `PlayerAnimationLibNeo`, `PlayerAnimationLibFabric`. Setup per the docs
  (https://docs.zigythebird.com/pal/gettingstarted/how_to_add_lib_to_mod): NeoForge `implementation`, Fabric on
  1.21.1 `modImplementation`. The 1.21.1 branch builds against NeoForge 21.1.230 and Fabric API 0.110.0+1.21.1
  (`gradle.properties` on branch `1.21.1`), mod id `player_animation_library`.
  Modrinth 1.21.1 releases: 1.1.3 (2025-12-15), 1.1.4 (2026-02-11), 1.1.5 (2026-07-17), 1.1.6 (2026-08-18), each for
  NeoForge and Fabric. Modrinth marks it client required, server optional.
- **Common artifact.** Yes. `PlayerAnimationLibCommon` is an Architectury common jar in Mojang names (its build uses
  `officialMojangMappings()` + Parchment). Its POM lists `PlayerAnimationLibCore` only at runtime scope, so a
  consumer that compiles against the API needs both Common and Core on its compile classpath. Its only
  loader-specific hook is an internal `@ExpectPlatform isModLoaded`, which API users never call. Whether the jar
  compiles cleanly against our NeoForm-based `common` (MultiLoader-Template) is **not verified**; it should, since
  the names are Mojang names, and it is the first thing the milestone 8 spike checks.
- **API (1.21.1 branch, read from source).** Register a layer at client setup with
  `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(id, priority, player -> new PlayerAnimationController(player, handler))`
  (on NeoForge inside `FMLClientSetupEvent.enqueueWork`, docs). Get it with
  `PlayerAnimationAccess.getPlayerAnimationLayer(player, id)`. `PlayerAnimationController` offers
  `triggerAnimation(ResourceLocation)`, `triggerAnimation(ResourceLocation, float startAnimFrom)`,
  `replaceAnimationWithFade(...)`, `stopTriggeredAnimation()`, `isActive()`, first-person settings and bone queries
  (`getBonePosition`, `getBoneWorldPositionPoseStack`). Modifiers: fade, `SpeedModifier`, `AdjustmentModifier`,
  `MirrorModifier`, `MirrorIfLeftHandModifier`, `FirstPersonOffsetModifier`
  (https://docs.zigythebird.com/pal/features/modifiers). `startAnimFrom` and `SpeedModifier` cover our needs: start
  a remote player's phase part-way, and stretch an animation to a weapon's phase duration.
- **First person.** Mode `THIRD_PERSON_MODEL` renders parts of the third-person model in first person instead of
  the vanilla hand. `FirstPersonConfiguration` toggles right arm, left arm, right item, left item and armour
  (defaults: arms off, items on, armour off). Optional "follows camera" mode with `FirstPersonOffsetModifier`, and a
  transition length so the vanilla arm moves out before the animated one appears
  (https://docs.zigythebird.com/pal/features/first_person). Implemented by mixins in
  `mixin/firstPerson/` (`ItemInHandRendererMixin`, `LevelRendererMixin`, `EntityRenderDispatcherMixin`,
  `LivingEntityRendererMixin`, `HumanoidArmorLayerMixin`). The "First Person API improvements" commit on the 1.21.1
  branch is from 2026-07-17.
- **Authoring.** Blockbench with the GeckoLib Animation Utils plugin and the player template `.bbmodel` from
  https://github.com/KosmX/emotes/tree/dev/blender, exported as GeckoLib animation JSON (`format_version` 1.8.0,
  `geckolib_format_version` 2), optional metadata under a `player_animation_library` key; Blender is also supported
  (https://docs.zigythebird.com/emotecraft/creatingemotes/blockbench). Keyframes may be Molang expressions, Bezier
  easing was backported to 1.21.1 in 1.1.6. Files go in `assets/<namespace>/player_animations/`; the animation id is
  `<namespace>:<animation name inside the file>`. Bones: `head`, `body`, `torso`, `right_arm`, `left_arm`,
  `right_leg`, `left_leg`, `right_item`, `left_item`, `cape`, `elytra`, with bends (elbow, knee, waist) and custom
  pivot bones (https://docs.zigythebird.com/pal/features/bones).
- **Networking.** None for gameplay. The core has `network/AnimationBinary` helpers to serialise animations (used by
  Emotecraft), but nothing that syncs playback. The docs do not mention sync. Every mod sends its own payload, which
  fits §8.5 (server-authoritative state, client renders).
- **NPCs.** Players only on 1.21.1. The generic `AnimationController` in the core exists, and newer versions add a
  `HumanoidAnimationController` and mannequin support (https://docs.zigythebird.com/pal/features/mannequins), but the
  1.21.1 branch has no `HumanoidAnimationController`. NPC duelists stay on GeckoLib as planned. Because PAL files are
  GeckoLib-format JSON, a GeckoLib NPC model that uses the same bone names could probably reuse the same animation
  files (**not verified**).
- **Licence.** MIT (repo `LICENSE`, Modrinth). Compatible with depending on it from a PolyForm Noncommercial mod, and
  with bundling it (keep the notice). Its shaded/runtime deps: mochafloats (Mocha is MIT), javassist (MPL 1.1 / LGPL 2.1
  / Apache 2.0 tri-licence).
- **Maintenance.** Repo pushed 2026-10-06, 12 open issues/PRs. Branches `1.21.1`, `1.21.8`, `1.21.9`, `1.21.11`,
  `26.1`, `26.2`, so 1.21.1 is a maintained backport branch beside the newer versions. Modrinth downloads ~2.6 M.
- **Known issues relevant to us** (https://github.com/PlayerAnimationLibrary/PlayerAnimationLibrary/issues):
  - #163 (1.21.1): `runClient` in a fresh NeoForge MDK fails to resolve because Core pulls newer gson, slf4j,
    fastutil, netty. The maintainer's fix is a `resolutionStrategy { force ... }` block in the build. Expect it.
  - #96, #92: conflicts with the First Person Model mod; the maintainer says 1.21.1 and 1.21.11 are not affected.
  - #91 EMF/ETF features vanish during animations, #171 CustomPlayerModels compat (open), #66 Essential emotes (open),
    #134 shader shadows (closed).
  - Coexistence with PlayerAnimator: PlayerAnimator 2.0.3 merged PR #171 by the PAL author, "Fix crashes and log spam
    with PAL present" (https://github.com/KosmX/minecraftPlayerAnimator/pull/171), so packs with Better Combat
    (PlayerAnimator on 1.21.1) and our mod (PAL) should load both. Simultaneous animations from both are **not verified**.
  - Sable: no reported issues found. I compared mixin targets in `refs/sable`: Sable injects into
    `EntityRenderDispatcher.render` (`entity_rotations_and_riding`), wraps the dispatcher call in
    `LevelRenderer.renderEntity` (`entity_rendering`), and rotates the camera with sub-levels. PAL injects at
    `LevelRenderer.renderLevel` around `Camera.isDetached()`, at the tail of `renderEntity`, and cancels
    `EntityRenderDispatcher.renderShadow`. All are `@Inject`s on different points, no overwrites or redirects on the
    same call, so no load-time conflict is expected. Whether the first-person body lines up with a camera that rolls
    with the ship is **not verified** and needs a playtest.
- **Who uses it.** Better Combat 3.x on 1.21.11 and 26.x (Modrinth dependency `ha1mEyJS`), Emotecraft 3.x on newer
  versions. Which 1.21.1 combat mods use it was **not verified** (Better Combat for 1.21.1 still uses PlayerAnimator).

### PlayerAnimator (KosmX)

- Repo https://github.com/KosmX/minecraftPlayerAnimator, Modrinth https://modrinth.com/mod/playeranimator, Maven
  `https://maven.kosmx.dev/` (https://maven.kosmx.dev/dev/kosmx/player-anim/).
- 1.21.1: `dev.kosmx.player-anim:player-animation-lib:2.0.4+1.21.1` (common), `player-animation-lib-forge` (the
  NeoForge build, Modrinth loader `neoforge`) and `player-animation-lib-fabric`, same version. Modrinth 1.21.1 releases
  2.0.0 (2024-12-20), 2.0.1 (2024-12-26), 2.0.4 (2025-12-28).
- Maintenance: README starts with "NO-LONGER-UPDATED ... Please use PAL instead ... Major bugfixes on existing
  releases will be done if needed, nothing else." Last commit 2025-12-28, 25 open issues. Not archived. ~27 M downloads.
- API: layered `AnimationStack`, `ModifierLayer`, `KeyframeAnimationPlayer`, modifiers (`AdjustmentModifier`,
  `MirrorModifier`, `SpeedModifier`, fades), `FirstPersonMode` / `FirstPersonConfiguration` in
  `coreLib/.../api/firstPerson`. Bends via the optional bendy-lib.
- Authoring: Emotecraft JSON and GeckoLib-format JSON from Blockbench (same template family as PAL). Fewer features
  than PAL (no Molang, no custom pivots, no effect keyframes per PAL's feature list).
- Networking: none; the README points at Emotecraft's server API for server-triggered animations.
- Licence: MIT.
- Porting path to PAL is documented: https://docs.zigythebird.com/pal/gettingstarted/how_to_port_from_player_animator

### Better Combat (how a real combat mod does it)

- https://github.com/ZsoltMolnarrr/BetterCombat, https://modrinth.com/mod/better-combat. Licence: All Rights Reserved
  (repo `LICENSE`), so it is a reference for the approach only.
- 1.21.1 builds (2.4.0+1.21.1, 2026-07-14) require PlayerAnimator (`gedNE4y2`); 1.21.11 (3.1.0) and 26.x (3.2.2)
  require PAL (`ha1mEyJS`). So the most-used combat mod moved to PAL on every version newer than 1.21.1.
- Architecture (branch `1.21.1`): an Architectury `common` module imports the library API directly
  (`dev.kosmx.playerAnim.api.layered.ModifierLayer`, `AdjustmentModifier`, `MirrorModifier` in
  `client/animation/AttackAnimationSubStack.java`). Networking is its own: `C2S_AttackRequest` from the attacker and
  an `AttackAnimation(playerId, animatedHand, animationName, length, upswing, ...)` payload that the server sends to
  other clients (`network/Packets.java`). This is the same split §8.5 asks for.
- First person: `client/compat/FirstPersonAnimationCompatibility.java` returns `FirstPersonMode.THIRD_PERSON_MODEL`,
  or `NONE` when a config tri-state says no or (on auto) when the camera mods `firstperson` or `realcamera` are
  loaded. We should copy this behaviour (not the code).

### GeckoLib

- https://github.com/bernie-g/geckolib (MIT), Modrinth 4.9.3 for 1.21.1 on NeoForge, Forge and Fabric (2026-09-16).
- Its animatables are `GeoEntity`, `GeoItem`, `GeoArmor`, `GeoBlockEntity` (wiki:
  https://github.com/bernie-g/geckolib/wiki). It has no API for the vanilla player model; I found no explicit FAQ
  statement saying so (**not verified** beyond the wiki page list and API). Third-party "GeckoLib Player Compat"
  exists on CurseForge (https://www.curseforge.com/minecraft/mc-mods/geckolib-player); its versions and licence were
  **not verified**.
- Role for us: NPC duelists (already in §2 and §8.5). PAL reuses GeckoLib's animation format, so the same Blockbench
  workflow serves both.

### AzureLib

- https://github.com/AzureDoom/AzureLib (MIT), Modrinth 3.1.16 for 1.21.1 on NeoForge and Fabric/Quilt (2026-10-06),
  group `mod.azure.azurelib`. A GeckoLib fork for entities, items, armour and block entities.
- Player support: only "items with arms": arm bones imported from `player_arms_import.bbmodel` into an item model are
  rendered in first person while an item animation moves them
  (https://moddedmc.wiki/en/project/azurelib/latest/docs/items/items_with_arms). No third-person player-model
  animation (docs index https://moddedmc.wiki/en/project/azurelib/latest/llms.txt lists none). Useful at most for a
  first-person firearm reload, not for readable third-person telegraphs.

### Not Enough Animations

- https://modrinth.com/mod/not-enough-animations, 1.12.6 for 1.21.1 (NeoForge, Forge, Fabric, 2026-09-19), client
  only, licence "tr7zw Protective License". A cosmetic mod that shows first-person item poses in third person, with no
  public animation API. Not a candidate; it is a compatibility target for the playtest (it also poses arms holding
  items, so its interaction with PAL while our swords animate is **not verified**). Same for tr7zw's First Person
  Model (https://modrinth.com/mod/first-person-model, MIT), see PAL #96.

### Animated Java and armour-stand / display-entity rigs

- https://github.com/Animated-Java/animated-java, Blockbench plugin, v1.10.2 (2026-07-07), licence NOASSERTION on
  GitHub. It exports a data pack and resource pack that animate `item_display` entity rigs. It cannot pose the real
  player model or the first-person view, so a "rig" would have to hide the player and replace it with entities: no
  first-person arms, interpolation lag in multiplayer, and fights with every other player-rendering mod. Not suitable.

### Epic Fight

- https://modrinth.com/mod/epic-fight, GPL-3.0-or-later, 1.21.1 builds for NeoForge only (21.17.3.1, 2026-05-31). A
  complete combat overhaul with its own animation system, not a library. Excluded by §8.5 (no hard dependency on
  overhaul mods) and by the dependency rule (no 1.21.1 Fabric build found).

## Recommendation

Use **Player Animation Library (PAL) 1.1.6+mc.1.21.1** as a required client-side dependency on both loaders.

1. It is the only maintained player-animation library with 1.21.1 builds for both NeoForge and Fabric: 1.21.1
   releases in July and August 2026, and a dedicated `1.21.1` branch.
2. It has first-person support that shows the animated arms and the held sword, configurable per arm and per item,
   which §8.5 needs for readable telegraphs from the attacker's own view.
3. It ships a Mojang-named common artifact, so all animation calls can live in `common` like Better Combat's do.
4. Its API matches our phase model: per-phase animations, `startAnimFrom` to catch up remote players, `SpeedModifier`
   to fit data-driven phase durations, `MirrorIfLeftHandModifier` for left-handed players, fades between phases.
5. MIT licence: no conflict with PolyForm Noncommercial, whether we depend on it or bundle it.
6. It is where the ecosystem is going: PlayerAnimator's author points to it, Better Combat uses it on every version
   after 1.21.1, and it already coexists with PlayerAnimator in the same pack.
7. Authoring uses Blockbench and GeckoLib-format JSON, the same toolchain as our GeckoLib NPCs.

Depend on it, do not jar-in-jar it: players with Better Combat or Emotecraft-style mods then share one copy, and
version conflicts stay visible. It can be bundled later if pack makers ask for it.

## Integration sketch

### Artifacts per module

| Module | Dependency | Notes |
|---|---|---|
| `common` | `compileOnly "com.zigythebird.playeranim:PlayerAnimationLibCommon:${pal_version}"` and `compileOnly "...:PlayerAnimationLibCore:${pal_version}"` | Both are needed: the Common POM lists Core only at runtime scope, and `FirstPersonMode`/`FirstPersonConfiguration` live in Core. Mojang names, no remapping. |
| `neoforge` | `implementation "com.zigythebird.playeranim:PlayerAnimationLibNeo:${pal_version}"` | Plus the `resolutionStrategy { force ... }` block from PAL issue #163 if `runClient` fails to resolve. `neoforge.mods.toml`: dependency `player_animation_library`, `type="required"`, `side="CLIENT"`, version range `[1.1.6,)`. |
| `fabric` | `modImplementation "com.zigythebird.playeranim:PlayerAnimationLibFabric:${pal_version}"` | `fabric.mod.json` `depends` (Fabric has no per-side depends, so required on both sides; PAL loads on servers, Modrinth server side "optional"). |
| root | `maven { url = "https://repo.redlance.org/public" }`, `pal_version=1.1.6+mc.1.21.1` in `gradle.properties` | Add the property to the `expandProps` map. |

The dependency rule (§2) is met: PAL exists for both loaders. GameTests and the dedicated server never load PAL
classes because all PAL code sits in a client-only package.

### Code layout

- `combat/melee/` (common, server-safe, unchanged): the state machine stays the single source of truth.
- `combat/melee/network/MeleeStatePayload` (common): server to tracking clients and self, sent on every phase change:
  `entityId`, `phase`, `attack`, `riposte`, `elapsed`, `duration`, server tick. Registration and sending go through
  `Services.NETWORK` as today. The attacker's own client predicts and only corrects when the payload disagrees.
- `combat/melee/client/anim/MeleeAnimations` (common, client): our interface, the only thing the rest of the mod sees.
  `play(AbstractClientPlayer, Phase, AttackKind, boolean riposte, int elapsedTicks, int durationTicks)`,
  `stop(AbstractClientPlayer)`. Two implementations:
  - `PalMeleeAnimations` (common, client): the only class that imports `com.zigythebird.playeranim*`. Registers one
    layer `pirates_n_ships:melee` at client setup, maps each phase to an animation id, triggers it with
    `startAnimFrom = elapsed` and a `SpeedModifier` of `animationLength / duration`, sets the first-person mode and
    configuration, mirrors for left-handed players.
  - `NoopMeleeAnimations`: vanilla swing only. Used when `Services.PLATFORM.isModLoaded("player_animation_library")`
    is false (defensive, e.g. a broken install) or the client config turns animations off.
- Loader modules: the only loader work is calling our client-setup hook, on NeoForge inside
  `FMLClientSetupEvent.enqueueWork` (PAL docs require it), on Fabric in the client entrypoint. No new platform service
  method is needed beyond `isModLoaded`, which we already need for compat checks.
- Assets: `common/src/main/resources/assets/pirates_n_ships/player_animations/*.json`, hand-authored in Blockbench
  like the item models in `art/`; source `.bbmodel` files under `art/animations/`. Animation JSON is an exported art
  asset, not datagen output, the same exception as the Blockbench item models.
- Animation set (per attack kind, one file each): `slash_windup`, `slash_active`, `slash_recovery`, `thrust_windup`,
  `thrust_active`, `thrust_recovery`, `guard_loop`, `parry`, `stagger`, plus `riposte_*` if the riposte should look
  different. One animation per phase keeps the animation in step with the server's phase timings, whatever the
  weapon's data says.
- Layer priority: above emotes (the docs suggest 1000 for emotes, 0–10 for idle/walk poses), e.g. 1500, so a
  telegraph is never hidden by an emote. A convention for combat layers was **not verified**.

### First person

- Mode `THIRD_PERSON_MODEL` while a combat animation plays, `FirstPersonConfiguration` with right arm and right item
  shown (left arm too for two-handed guards), armour hidden, a short transition length, and
  `FirstPersonOffsetModifier` if the arms leave the frame.
- Client config `melee.firstPersonAnimations = auto | on | off`; auto turns it off when a camera mod that already
  draws the body is loaded (`firstperson`, `realcamera`), the same rule Better Combat uses. With it off the vanilla
  first-person hand plays the vanilla swing while third person still animates.
- No server config is needed: animations do not change gameplay. The melee system's existing server toggle covers it.

## Risks

1. **1.21.1 is a backport branch.** PAL's main development targets newer versions. If 1.21.1 releases stop, we stay
   on the last good version; the `MeleeAnimations` interface keeps a swap to PlayerAnimator to one class.
2. **Build friction.** Core pulls newer gson, slf4j, fastutil and netty (issue #163); our NeoForm `common` consuming an
   Architectury common jar is **not verified**. The first M8 task is a build spike before any animation work.
3. **Rendering conflicts.** First Person Model, Real Camera, EMF/ETF, CustomPlayerModels, Not Enough Animations and
   shader packs touch the same renderers (see the issues above). Mitigation: the auto/on/off client toggle and a
   playtest with the common ones.
4. **Sable.** No overlapping redirects found, but the first-person body on a rolling ship and third-person animation
   of players standing on a moving sub-level are **not verified**. Needs a playtest at sea.
5. **Better Combat in the same pack.** On 1.21.1 it uses PlayerAnimator; PlayerAnimator ≥ 2.0.3 tolerates PAL. Better
   Combat only animates items that have its weapon attributes, and we ship none, so it should leave our swords alone
   (**not verified**).
6. **Not testable headlessly.** GameTests cover the state machine and payloads; animation timing and readability need
   human playtests with screenshots or recordings.
7. **Maven host.** `repo.redlance.org` is a third-party host. Modrinth's Maven is a backup source (coordinates use the
   Modrinth version id, **not verified**).

## Fallback

1. **PlayerAnimator 2.0.4+1.21.1** (KosmX): same idea, same first-person mode, MIT, both loaders, a common artifact,
   and the most-installed player-animation library on 1.21.1 (Better Combat depends on it). It is in maintenance mode
   but feature-complete for 1.21.1. Swapping means rewriting `PalMeleeAnimations` against `ModifierLayer` /
   `KeyframeAnimationPlayer` and re-exporting the animations in its format (both read GeckoLib-format JSON from the
   same Blockbench template; Molang keyframes would have to go).
2. If both stall: a small in-house animator. Third person through a `HumanoidModel.setupAnim` hook in common (a small
   mixin, because as far as checked neither loader offers a player-model pose event; **not verified**), first person through the loaders' hand-render
   events behind a platform service (NeoForge `RenderHandEvent`, Fabric equivalent **not verified**). Procedural
   keyframes from our phase timings, no Blockbench import. Much less expressive; last resort.

## Proposed text for `docs/design.md`

Replace the §21 bullet "Player animation library for melee combat: ..." with:

> - ~~Player animation library for melee combat~~ **Resolved (G5, `docs/animation-libraries.md`):** Player Animation
>   Library (PAL, `com.zigythebird.playeranim`, MIT) 1.1.6+mc.1.21.1, the maintained successor of PlayerAnimator, with
>   builds for NeoForge and Fabric and a Mojang-named common artifact. It animates the third-person player model and,
>   in its `THIRD_PERSON_MODEL` first-person mode, the first-person arms and held sword. It does no networking: our
>   server broadcasts the melee phase and clients play one animation per phase. All PAL calls sit in one client
>   package in `common` behind our `MeleeAnimations` interface. Fallback: PlayerAnimator 2.0.4+1.21.1. Still to
>   check in milestone 8: the build with our NeoForm `common`, and first-person rendering on a rolling ship.

Add this row to the §2 table, after "Animated entities":

> | Player animations | **Player Animation Library (PAL)** (`com.zigythebird.playeranim`, MIT), required on the client for both loaders. `common` compiles against `PlayerAnimationLibCommon` + `PlayerAnimationLibCore`; `neoforge` uses `PlayerAnimationLibNeo`, `fabric` uses `PlayerAnimationLibFabric`. Used only for the player's melee animations (§8.5); NPCs use GeckoLib. See `docs/animation-libraries.md`. |


## Correction after integration (G12, 2026-10-07)

`PlayerAnimationLibCommon` 1.1.6+mc.1.21.1 is published remapped to Fabric intermediary names (`net.minecraft.class_742`, `class_2960`), not Mojang names as stated above, so our NeoForm `common` cannot compile against it. `PlayerAnimationLibNeo` bundles the Common and Core classes in Mojang names (with mochafloats and javassist nested), so `common` uses it as a non-transitive `compileOnly` dependency and `neoforge` as `implementation` with the separate Core artifact excluded; that exclusion also avoids the dependency clash of PAL issue #163, so no `resolutionStrategy force` block was needed. The Maven repository is declared in `buildSrc/src/main/groovy/multiloader-common.gradle` as exclusive content for `com.zigythebird`. PAL loads on the dedicated server without errors. `PalMeleeAnimations` registers one `PlayerAnimationController` layer per player and triggers animations with `triggerAnimation(animation, startTick)` plus a `SpeedModifier` and a per-trigger `MirrorModifier` (PAL's `MirrorIfLeftHandModifier` only checks the local player's option).
