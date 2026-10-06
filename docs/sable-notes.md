# Sable API notes (Phase B investigation)

Source: Sable 2.0.6 in `refs/sable` (read-only), plus Create Aeronautics in `refs/create-aeronautics`.
All paths below are relative to `refs/`. `SABLE` = `sable/common/src/main/java/dev/ryanhcode/sable`.
`SIM` = `create-aeronautics/simulated/common/src/main/java/dev/simulated_team/simulated`.
`AERO` = `create-aeronautics/aeronautics/common/src/main/java/dev/eriksonn/aeronautics`.

Labels used throughout:
- **[V] verified in source**: I read the code at the given location.
- **[I] inferred**: a conclusion drawn from the code named next to it; not directly stated.
- **[?] unknown / not found**: I looked and did not find it, or could not check.

Never paste Sable code into the mod (PolyForm Shield). Signatures below are paraphrased.

Math types: Sable uses JOML (`org.joml.Vector3d`, `Vector3dc`, `Quaterniond`, `Matrix3dc`) and its own
`Pose3d`, `BoundingBox3i`, `BoundingBox3ic`, `BoundingBox3d(c)` from the package
`dev.ryanhcode.sable.companion.math` (these live in the **Sable Companion** artifact, see §7). [V: imports in 32 Sable files]

---

## 1. Mental model

### 1.1 What a sub-level is
- A `SubLevel` (`SABLE/sublevel/SubLevel.java`, abstract, `implements SubLevelAccess`) is a block structure that
  lives in a reserved region ("plot") of the **same** `Level` and is rendered / simulated at a pose in the world. [V]
  - Fields: parent `Level`, current `Pose3d pose` (`logicalPose()`), `lastPose()` (previous tick), global
    `boundingBox()` (world AABB, `BoundingBox3dc`), `getPlot()`, `isRemoved()` / `markRemoved()`,
    `getUniqueId()` / `setUniqueId(UUID)`, `getName()` / `setName(String)`. [V: SubLevel.java lines 22-232]
  - Javadoc of the UUID field: it "will persist throughout lifetime, stay consistent between client and server,
    and persist across serialization". [V: SubLevel.java ~line 55]
- Server side: `ServerSubLevel` (`SABLE/sublevel/ServerSubLevel.java`). Client side: `ClientSubLevel`. [V]
  Physics, forces, mass, user data and tickets only exist on the server object. [V: methods listed in §3, §8]

### 1.2 Plots and chunks
- `LevelPlot` (`SABLE/sublevel/plot/LevelPlot.java`): "An allocated & reserved space in a level belonging to a SubLevel,
  holding its own chunk grid." Server subclass `ServerLevelPlot`, client `ClientLevelPlot`. [V: class javadoc]
- Plots live in a plot grid owned by a `SubLevelContainer` (`SABLE/api/sublevel/SubLevelContainer.java`).
  The grid origin is `DEFAULT_ORIGIN = 10000` in plot units (line 44), i.e. the plot chunks are far out in the
  same dimension's chunk coordinate space. [V] Blocks of a ship are real blocks in real `LevelChunk`s at those far
  coordinates ("plot coordinates"); `level.getBlockState(plotPos)` works on them (Aeronautics does exactly this in
  `SIM/util/SimAssemblyHelper.java` `disassembleSubLevel`, line 73). [V]
- Useful `LevelPlot` methods [V, LevelPlot.java]: `getCenterBlock()` (l.133; center chunk + 8, Y = middle of build
  height), `getCenterChunk()`, `newEmptyChunk(ChunkPos global)` (l.167), `getChunkHolder(ChunkPos local)`,
  `toLocal/toGlobal(ChunkPos)`, `getLoadedChunks()`, `getBoundingBox()` (plot-space block bounds, `BoundingBox3ic`,
  l.342), `contains(Vec3|Vector3dc|ChunkPos|x,z)`, `getEmbeddedLevelAccessor()` (l.126), `getBlockEntityActors()`.
- `EmbeddedPlotLevelAccessor` (`SABLE/sublevel/plot/EmbeddedPlotLevelAccessor.java`) is a
  `ServerLevelAccessor` whose coordinates are **relative to `plot.getCenterBlock()`** (every call does
  `blockPos.offset(center)`, lines 100-110). [V] Used to place blocks / templates into a fresh plot (§2.5).
- Plot chunks only exist where blocks are; assembly creates them with `newEmptyChunk` and the plot expands
  automatically when blocks are placed at the edge (`expandIfNecessary`, l.382). [V/I]

### 1.3 Pose and coordinates
- `Pose3d` has `position()` (world, `Vector3d`), `orientation()` (`Quaterniond`), `rotationPoint()` (plot-space pivot,
  normally the center of mass) and `transformPosition`, `transformPositionInverse`, `transformNormal`,
  `transformNormalInverse` (overloads for `Vec3` and JOML). [V: usage counts across Sable + Aeronautics; the class
  itself is in Sable Companion, source not in `refs/`, so exact overload list is **[?]**]
- The formula, confirmed by Aeronautics: `world = orientation · (plot − rotationPoint) + position`
  (`AERO/content/blocks/hot_air/balloon/ServerBalloon.java` `applyForces`, lines 118-120). [V]
  World directions into body frame: `pose.orientation().transformInverse(vec)` (same file, l.123, l.135). [V]

### 1.4 The containers
- `SubLevelContainer.getContainer(Level)` / `(ServerLevel)` → `ServerSubLevelContainer` / `(ClientLevel)` →
  `ClientSubLevelContainer` (SubLevelContainer.java lines 92-116). May return null if Sable has not initialised the
  level. [V]
- Server container: `physicsSystem()` → `SubLevelPhysicsSystem`, `trackingSystem()`, `getAllSubLevels()`,
  `getSubLevel(UUID)` (l.528), `queryIntersecting(BoundingBox3dc)` (l.507), `allocateNewSubLevel(Pose3d)` (l.232),
  `removeSubLevel(SubLevel, SubLevelRemovalReason)` (l.517), `addObserver(SubLevelObserver)` (l.173),
  force-load tickets (§8). [V: ServerSubLevelContainer.java, SubLevelContainer.java]
- `SubLevelPhysicsSystem` (`SABLE/sublevel/system/SubLevelPhysicsSystem.java`): `get(Level)`, `require(Level)`,
  `getPipeline()` → `PhysicsPipeline`, `getPhysicsHandle(ServerSubLevel)` → `RigidBodyHandle`, `getLevel()`,
  `getPaused()/setPaused`, `queryIntersecting(BoundingBox3dc)`, `getConfig()`, `getPartialPhysicsTick()`. [V: lines 124-628]

### 1.5 Tick order (server, per game tick)
From `SubLevelPhysicsSystem.tick(SubLevelContainer)` (lines 218-248) and `tickPipelinePhysics` (250-298): [V]
1. Loading ticket manager update.
2. For every sub-level: `updateLastPose()`, then `BlockEntitySubLevelActor.sable$tick(subLevel)` for every actor
   block entity in its plot.
3. `pipeline.tick()`.
4. If not paused, for each **substep** (`config.substepsPerTick`, default 2, `SABLE/physics/config/PhysicsConfigData.java`
   l.33; time step `1/20/substeps` s):
   1. `prePhysicsTickBegin()` on each sub-level → resets all `QueuedForceGroup`s.
   2. `updateMergedMassData`.
   3. `ServerSubLevel.prePhysicsTick(...)` (ServerSubLevel.java 297-387): calls
      `BlockEntitySubLevelActor.sable$physicsTick(subLevel, handle, timeStep)` for every actor, then lift providers
      (`BlockSubLevelLiftProvider`), floating block materials, reaction wheels, then applies their impulses.
   4. **`SablePrePhysicsTickEvent`** fired (`SableEventPublishPlatform.INSTANCE.prePhysicsTick`, l.271).
   5. `applyQueuedForces` → every `QueuedForceGroup`'s `ForceTotal` is pushed into the body (ServerSubLevel l.279-287).
   6. Native `pipeline.physicsTick(timeStep)`. Inside the native tick Rapier also runs ropes, joints and
      **buoyancy** (`sable_rapier/src/main/rust/rapier/src/lib.rs` l.498-502). [V]
   7. `processSubLevelRemovals()` (removes sub-levels whose mass became invalid, i.e. empty), pose update.
   8. **`SablePostPhysicsTickEvent`** fired (l.293).
- Normal block entity ticking and entity ticking happen in the vanilla level tick, separately. [I]

### 1.6 Where our code can hook in
| Hook | Kind | Where | Notes |
|---|---|---|---|
| `BlockEntitySubLevelActor` | interface on a `BlockEntity` | `SABLE/api/block/BlockEntitySubLevelActor.java` | `sable$tick(ServerSubLevel)` once per game tick; `sable$physicsTick(ServerSubLevel, RigidBodyHandle, double timeStep)` once per substep; loading/connection dependencies. Auto-registered when the BE is in a plot (`LevelPlot.java` l.410-413). [V] |
| `BlockSubLevelAssemblyListener` | interface on a `Block` | `SABLE/api/block/BlockSubLevelAssemblyListener.java` | `beforeMove(...)` (default) and `afterMove(originLevel, resultingLevel, newState, oldPos, newPos)`; called for each moved block in `moveBlocks` (assembly **and** disassembly). [V] |
| `BlockSubLevelLiftProvider` | interface on a `Block` | `SABLE/api/block/BlockSubLevelLiftProvider.java` (243 lines) | flat-plate aerodynamic lift/drag per block, air-pressure scaled, body-velocity based (see §3.4). [V] |
| `BlockSubLevelCustomCenterOfMass` | `Block` | `SABLE/api/block/...` | `Vector3dc getCenterOfMass(BlockGetter, BlockState)` relative to the block's lower corner. [V] |
| `BlockEntityPropeller`, `BlockEntitySubLevelPropellerActor` | BE | `SABLE/api/block/propeller/` | ready-made thrust actor (§3.4). [V] |
| `BlockEntitySubLevelReactionWheel`, `BlockSubLevelCollisionShape`, `BlockSubLevelDynamicCollider`, `BlockWithSubLevelCollisionCallback` | various | `SABLE/api/block/` | exist [V]; not studied. |
| `SubLevelObserver` | object registered on a container | `SABLE/api/sublevel/SubLevelObserver.java` | `onSubLevelAdded(SubLevel)`, `onSubLevelRemoved(SubLevel, SubLevelRemovalReason)`, `tick(SubLevelContainer)`. Register with `container.addObserver(...)`, typically from the container-ready event (Aeronautics: `AERO/events/AeronauticsCommonEvents.java` l.36-42). [V] |
| `SableEventPlatform` events | callbacks | `SABLE/platform/SableEventPlatform.java` | `onSubLevelContainerReady`, `onPhysicsTick` (pre), `onPostPhysicsTick`. See §7. [V] |
| `SubLevelHelper.registerWindProvider` | static | `SABLE/api/SubLevelHelper.java` l.138 | registers `BiFunction<Vector3dc, Level, Vector3dc>` giving air velocity at a point; consumed by `getVelocityRelativeToAir` (l.118). Relevant for wind (§11.3). [V] |
| Datapack JSON | data | wiki "Block Physics Properties", "Dimension Physics Data" | per-block-state mass/volume/friction, per-dimension gravity/drag. [V wiki] |
| Entity tags | data | wiki "Working with Entities" | `#sable:retain_in_sub_level`, `#sable:destroy_when_leaving_plot`, `#sable:destroy_with_sub_level`. [V wiki] |

---

## 2. Assembly and disassembly

### 2.1 Gathering blocks
`SubLevelAssemblyHelper.gatherConnectedBlocks(BlockPos gatherOrigin, ServerLevel level, int maximumBlocksToAssemble, @Nullable FrontierPredicate frontierPredicate)` → `GatherResult`
(`SABLE/api/SubLevelAssemblyHelper.java` l.191-277). [V]
- Flood fill from the origin over **non-air** blocks, checking the 3x3x3 neighbourhood minus the 8 corners
  (faces + edges, l.236-240). It does **not** exclude terrain: the only filter is your `FrontierPredicate`. [V]
- `FrontierPredicate.isValidConnection(BlockPos originPos, BlockState originState, BlockPos pos, BlockState state, @Nullable Direction directionFrom)`
  (`directionFrom` is null for edge links), l.496-508. [V]
- `GatherResult(Set<BlockPos> blocks, int checkedBlocks, BoundingBox3i boundingBox, State state)` with
  `State` ∈ `SUCCESS`, `NO_BLOCKS`, `TOO_MANY_BLOCKS` (each has `errorKey`). On `TOO_MANY_BLOCKS` it aborts
  immediately and `blocks` is null. [V l.579-590, l.215-217]
- Fluids: water source blocks are not air, so **water is gathered** unless the predicate rejects it. [I from the
  `isAir()` checks at l.198/251] Our predicate must reject fluids, terrain (via a block tag), and the helm logic must
  pass the config block limit as `maximumBlocksToAssemble`.

### 2.2 Assembling
`SubLevelAssemblyHelper.assembleBlocks(ServerLevel level, BlockPos anchor, Iterable<BlockPos> blocks, BoundingBox3ic bounds)` → `ServerSubLevel`
(l.69-140). [V] What it does, in order:
1. Gets the server container and the sub-level containing `anchor` (if the blocks are themselves on a ship, the new
   sub-level inherits that ship's pose and velocity and is marked "split from" it). [V l.74-99, 127-129]
2. `container.allocateNewSubLevel(pose)` with pose position = anchor block center and **identity orientation**
   (unless nested). [V l.75-77, 101]
3. Creates the plot's center chunk and builds `AssemblyTransform(anchor, plot.getCenterBlock(), 0, Rotation.NONE, level)`:
   **a pure translation, no rotation**. [V l.103-107]
4. `moveOtherStuff`: only **`HangingEntity`s** (paintings, item frames) whose support box overlaps the moved blocks are
   moved into the plot. [V l.297-318] Other entities (players, mobs, items) are **not** moved.
5. `moveBlocks` (l.327-463), per block: calls `BlockSubLevelAssemblyListener.beforeMove`, saves the block entity with
   `saveWithFullMetadata`, rewrites its x/y/z, clears the old BE (`Clearable.tryClear`, loot table set to null, or plain
   removal for blocks tagged `#sable:silent_assembly_removal`), writes the state into the plot chunk, loads the data
   into the new BE with `loadWithComponents`, calls `afterMove`. Then it runs neighbour updates (`markAndNotifyBlock`),
   sets the old positions to air and sends block updates. Placement side effects are suppressed via
   `SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(level, true/false)`. [V]
   → Block entities and their NBT (inventories, our station data) survive. A BE that caches its `BlockPos` or other
   positions in fields must refresh them in `afterMove` or on load. [I]
6. Sets the pose position to the world-space center of mass (`getMassTracker().getCenterOfMass()`), teleports the
   physics body there and calls `updateLastPose()`. [V l.111-135] Result: the ship appears exactly where the blocks were.
7. `moveTrackingPoints`: player log-out points inside `bounds` are re-pointed at the new sub-level. [V l.279-295]
- `bounds` is only used for entities and tracking points (javadoc l.67). Pass the gather bounding box. [V]
- Return value: the new `ServerSubLevel`. Aeronautics null-checks it (`SIM/util/SimAssemblyHelper.java` l.157). [V]
- Players standing on the deck are not moved. Because the blocks reappear at the same world position, they should
  stay on the deck and get picked up by tracking (§6). [I, not tested]
- **Water risk**: the old positions become **air** (l.446-456). A hull assembled while floating in the sea leaves an
  air pocket in the world water until vanilla water flows back in. [I]

### 2.3 How Aeronautics triggers it
- `SIM/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity.java` `assembleOrDisassemble()` l.287-317:
  if `Sable.HELPER.getContaining(this)` is null, it assembles from the block the assembler sticks to. Otherwise it
  starts disassembly. [V]
- `SIM/util/SimAssemblyHelper.java` `assembleFromSingleBlock(...)` l.136-184 gathers blocks with **Create's**
  contraption search (`SimAssemblyContraption.searchMovedStructure`), not with Sable's `gatherConnectedBlocks`. Then it
  calls `SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, BoundingBox3i.from(blocks))` and re-creates glue
  entities offset by `plot.getCenterBlock() - anchor`. [V] We have no Create dependency, so we use
  `gatherConnectedBlocks`, as Sable's own `/sable assemble connected` does (`SABLE/command/SableAssembleCommands.java` l.332-338). [V]

### 2.4 Disassembly
**Sable's API has no `disassemble` method.** [V: grep for "disassembl" in `sable/common` finds nothing]
The reusable pieces are public: `SubLevelAssemblyHelper.AssemblyTransform`, `moveBlocks` and `moveTrackingPoints`.
Aeronautics implements it itself in `SIM/util/SimAssemblyHelper.java`
`disassembleSubLevel(Level, SubLevel, BlockPos subLevelAnchor, BlockPos disassemblyGoal, Rotation, boolean playSound)` l.43-134 [V]:
1. `new AssemblyTransform(subLevelAnchor /*plot pos*/, disassemblyGoal /*world pos*/, quarterTurns, rotation, serverLevel)`.
   `AssemblyTransform` supports **only yaw in 90° steps** (`angle` = counter-clockwise quarter turns, plus a `Rotation`
   for block states, l.513-560). [V]
2. Collects every non-air block in each loaded plot chunk (`plot.getLoadedChunks()`, `chunk.getBoundingBox()`). [V]
3. Moves entities living in the plot (glue, retained entities) with the transform and re-adds them. [V l.84-125]
4. `((ServerLevelPlot) plot).kickAllEntities()`, then `SubLevelAssemblyHelper.moveBlocks(level, transform, blocks)`
   (the same function as assembly, now plot → world), then `moveTrackingPoints(level, plotBounds, null, transform)`. [V]
5. The emptied sub-level is removed automatically: `SubLevelContainer.processSubLevelRemovals()` removes any server
   sub-level whose mass tracker `isInvalid()` (mass ≤ 0 or no COM) (`SubLevelContainer.java` l.153-168). This is also
   checked on every block change (`SubLevelPhysicsSystem.updateMassDataFromBlockChange` l.533-537). [V] Explicit removal
   also works: `container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED)`. [V]

Alignment and stationary checks (all **Aeronautics policy, not Sable rules**), `PhysicsAssemblerBlockEntity` [V]:
- `throwDisassemblyExceptions` (l.198-249): bounds inside build height; linear and angular speed below config limits
  (`RigidBodyHandle.of(subLevel).getLinearVelocity/getAngularVelocity`); optionally "near ground".
- `startDisassembling` (l.325-355): picks the nearest 90° yaw, adds a world constraint
  (`pipeline.addConstraint(null, subLevel, new FreeConstraintConfiguration(...))`) and drives it with
  `setMotor(ConstraintJointAxis.*, target, stiffness, damping, false, 0)` to pull the body onto the grid.
- `tickDisassembling` (l.145-178): waits until the orientation error is within tolerance and the pivot block center is
  < 0.2 m from a block center for > 5 ticks. Then `placeIntoWorld()` calls
  `disassembleSubLevel(level, subLevel, assemblerPlotPos, goalWorldPos, rotation, true)` with
  `goal = BlockPos.containing(pose.transformPosition(Vec3.atCenterOf(assemblerPlotPos)))` and removes the constraint.
- `moveBlocks` **overwrites** whatever is at the target positions (`chunk.setBlockState` unconditionally): water,
  terrain, other builds. [V l.407-409] We must check the target volume ourselves (allow only air, water, replaceables).
  Air cells inside the hull are not in the block list, so **sea water stays inside the hull** after disassembly in
  water. [I]

### 2.5 Creating a sub-level directly from a structure template (shipwright pickup)
Possible. Sable's own command does it: `SABLE/command/SableSpawnCommands.java` `executeSpawnSchematicCommand` l.267-311 [V]:
`container.allocateNewSubLevel(pose)` → for every chunk the template's bounding box covers,
`plot.newEmptyChunk(new ChunkPos(center.x + x, center.z + z))` →
`template.placeInWorld(plot.getEmbeddedLevelAccessor(), BlockPos.ZERO, BlockPos.ZERO, new StructurePlaceSettings(), random, 3)` →
`subLevel.updateLastPose()` → set `logicalPose().position()`. The same pattern (without a template) is in the NeoForge
GameTest helper `sable/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/gametest/SableTestHelper.java` `spawnSubLevel` l.23-36. [V]
- The template origin maps to `plot.getCenterBlock()` (accessor offset, §1.2). Rotation can be passed through
  `StructurePlaceSettings` (vanilla). [I]
- Caveat: the command sets the pose position after placement but never calls `pipeline.teleport`. For an exact berth
  position, do what `assembleBlocks` does: after placement compute the world COM and call
  `physicsSystem.getPipeline().teleport(subLevel, pos, orientation)` (Sable's GameTest does the same,
  `AssemblyTest.java` l.83-88). [I, check in the spike]
- Chunks are only created for `bounds.minX()>>4 .. maxX()>>4` relative to the center chunk, so the code assumes the
  template starts at ZERO. [V]

---

## 3. Forces and state

### 3.1 Getting a handle
- `RigidBodyHandle.of(ServerSubLevel)` (nullable) or `RigidBodyHandle.of(ServerLevel, PhysicsPipelineBody)`. In bulk or
  inside a physics tick prefer `physicsSystem.getPhysicsHandle(subLevel)`. Check `isValid()`.
  (`SABLE/api/physics/handle/RigidBodyHandle.java` l.33-67, l.205) [V]
- `BlockEntitySubLevelActor.sable$physicsTick(subLevel, handle, timeStep)` receives the handle. [V]

### 3.2 Applying forces
Frames and units (javadoc of `RigidBodyHandle`, `ForceTotal`, `PhysicsPipeline`) [V]:
- **Positions are plot coordinates** ("the position inside the plot", e.g. `Vec3.atCenterOf(blockPosInPlot)`).
- **Forces, impulses and torques are in the body's local frame** ("local impulse"), not the world frame. Convert a
  world vector with `pose.orientation().transformInverse(v)` (or `pose.transformNormalInverse`). [V doc + ServerBalloon l.123]
- Returned velocities are **global** (world frame), in m/s and rad/s.
- Although the docs say "[N]", every call adds an **impulse**: callers multiply force by `timeStep`
  (`SABLE/api/block/propeller/BlockEntitySubLevelPropellerActor.java` `applyForces`; `ServerBalloon.applyForces`
  l.144-146: "torque and force are both in momentum units"). Mass unit is "kpg" (≈ kg); default gravity is 11 m/s². [V]

Methods:
- `RigidBodyHandle`: `applyImpulseAtPoint(Vector3dc plotPos, Vector3dc localImpulse)` (+ `Vec3` overload),
  `applyLinearAndAngularImpulse(Vector3dc impulse, Vector3dc torque[, boolean wakeUp])`, `applyLinearImpulse`,
  `applyAngularImpulse`, `applyTorqueImpulse`, `addLinearAndAngularVelocity(Vector3dc, Vector3dc)`,
  `teleport(Vector3dc, Quaterniondc)`, `applyForcesAndReset(ForceTotal)`, `getLinearVelocity(Vector3d dest)`,
  `getAngularVelocity(Vector3d dest)`. [V l.78-205]
- **Preferred: queued force groups.** `ServerSubLevel.getOrCreateQueuedForceGroup(ForceGroup)` → `QueuedForceGroup`
  (`ServerSubLevel.java` l.395). `QueuedForceGroup.applyAndRecordPointForce(Vector3dc plotPoint, Vector3dc localImpulse)`;
  `getForceTotal()` → `ForceTotal` with `applyImpulseAtPoint(MassData|ServerSubLevel, Vector3dc pos, Vector3dc impulse)`,
  `applyLinearAndAngularImpulse`, `applyLinearImpulse`, `applyTorqueImpulse`, `getLocalForce/Torque`
  (`SABLE/api/physics/force/QueuedForceGroup.java`, `ForceTotal.java` l.58-148). [V]
  `ForceTotal.applyImpulseAtPoint` turns a point force into force plus `(pos − COM) × force` torque (l.101-105). [V]
  Groups are reset at the start of each substep and applied right after the pre-physics event (§1.5), so forces must be
  recorded **every substep**, in `sable$physicsTick` or in the pre-physics event. They also appear in Sable's force
  display (`recordPointForce` only records while `isTrackingIndividualQueuedForces()`). [V]
- `ForceGroup(Component name, Component description, int color, boolean defaultDisplayed)` is a record in a vanilla
  registry with key `ForceGroups.REGISTRY_KEY` (`sable:force_groups`). Built-ins: `GRAVITY, DRAG, LEVITATION,
  BALLOON_LIFT, PROPULSION, LIFT, MAGNETIC_FORCE` (`SABLE/api/physics/force/ForceGroups.java`). [V] We can register our
  own groups (e.g. `pirates_n_ships:wind`, `:buoyancy`) through our registry service, or reuse `PROPULSION`/`LIFT`. [I]

When it is legal: during the physics substep, i.e. in `sable$physicsTick` or a `SablePrePhysicsTickEvent` listener
(`SableEventPlatform` javadoc: "Logic that needs to influence the physics world should occur on the physics tick, and
not the game tick"). Queued groups written during the game tick are reset by `prePhysicsTickBegin()` before they are
applied. [V/I] Sable calls `teleport` and `addLinearAndAngularVelocity` from game-tick code itself (assembly, commands),
so those are fine outside the substep. [V]

### 3.3 Reading state
- Pose: `subLevel.logicalPose()` (current) and `lastPose()` (previous tick), each with `position()`, `orientation()`,
  `rotationPoint()`. The client sub-level has `renderPose(partialTick)` for rendering. [V usage] `boundingBox()` is the world AABB.
- `pipeline.readPose(ServerSubLevel, Pose3d dest)` (`PhysicsPipeline.java`). [V]
- Velocity: `handle.getLinearVelocity(dest)`, `getAngularVelocity(dest)` (world frame), and the public fields
  `ServerSubLevel.latestLinearVelocity` / `latestAngularVelocity` (l.54-60). [V] Velocity of any world point, including
  rotation: `Sable.HELPER.getVelocity(Level, Vector3dc pos, Vector3d dest)` and `getVelocityRelativeToAir(...)`
  (`SABLE/ActiveSableCompanion.java` l.360-423). [V]
- Mass: `ServerSubLevel.getMassTracker()` → `MassData` with `getMass()` [kpg], `getInverseMass()`,
  `getInertiaTensor()` (`Matrix3dc`, local), `getInverseInertiaTensor()`, `getCenterOfMass()` (nullable, **plot coords**),
  `isInvalid()`, `getInverseNormalMass(pos, dir)` (`SABLE/api/physics/mass/MassData.java`). [V]
  `getSelfMassTracker()` → `MassTracker` without merged (connected) bodies. [V]

### 3.4 How Aeronautics and Sable apply continuous forces
- **Propeller (block-entity actor)**: `BlockEntitySubLevelPropellerActor.sable$physicsTick` takes the block facing as the
  direction (plot frame = body frame), impulse = `thrust * timeStep`, position = `atCenterOf(blockPos)`, and calls
  `subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get()).applyAndRecordPointForce(pos, impulse)`. [V]
- **Balloons (global system, no BE)**: `AeronauticsCommonEvents.physicsTick`, registered with
  `SableEventPlatform.INSTANCE.onPhysicsTick(...)` (`AERO/Aeronautics.java` l.65), calls `BalloonMap.physicsTick(level, timeStep)`,
  which calls `ServerBalloon.applyForces(timeStep)`. That computes force and torque in the local frame and calls
  `getOrCreateQueuedForceGroup(ForceGroups.BALLOON_LIFT.get()).getForceTotal().applyLinearAndAngularImpulse(force, torque)`
  (`ServerBalloon.java` l.98-154). [V] This is the template for **ship-wide** forces (buoyancy correction, waves, rudder).
- **Lift/drag blocks** (`BlockSubLevelLiftProvider`) are summed in `ServerSubLevel.prePhysicsTick` (l.317-340) into
  the `LIFT` and `DRAG` groups. [V] Contract: `Direction sable$getNormal(BlockState)` plus optional
  `sable$getParallelDragScalar()`, `sable$getDirectionlessDragScalar()`, `sable$getLiftScalar()`. The default
  `sable$contributeLiftAndDrag(...)` is a flat-plate model: drag along the normal, isotropic drag and lift, all
  proportional to the **body's** velocity at the block (not air- or wind-relative) and scaled by **air pressure** at the
  block (`BlockSubLevelLiftProvider.java` l.131-205). [V] So it works as aerodynamic damping, not as a wind-driven sail, and
  it does not distinguish water from air, so it is no good as a keel either. Blocks are registered automatically when their `Block`
  implements the interface (`ServerLevelPlot.java` l.668-676). [V]

---

## 4. Buoyancy, fluids and water occlusion

### 4.1 How Sable's buoyancy works
Buoyancy is computed in **native Rust**, not Java: `sable_rapier/src/main/rust/rapier/src/buoyancy.rs`
`compute_buoyancy(scene)` (l.14-141), called from the JNI tick `Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_tick`
together with ropes and joints (`rapier/src/lib.rs` l.491-502), once per physics substep. [V]
- For **every** rigid body it finds pairs (ship voxel, **world** liquid voxel) via `find_collision_pairs(..., liquid = true)`,
  which reads a separate `liquid_octree` per chunk (`rapier/src/algo.rs` l.23, l.236-237; `marten/src/level.rs` l.124). [V]
  The second argument is `None`, so only liquid in the static world counts, not liquid in other sub-levels. [I]
- What counts as liquid: `VoxelNeighborhoodState.isLiquid(state)` = `state.liquid()` or kelp / kelp plant
  (`SABLE/physics/chunk/VoxelNeighborhoodState.java` l.87-88). So **water and lava**, as full cubes; partial fluid
  heights are ignored. [V for Java predicate; I that it feeds the liquid octree]
- **Float force**, per ship block overlapping a liquid block: upward world-Y force `10.5 × overlapVolume × sable:volume`
  at the block's position (`do_float`, l.166-189). It uses the constant 10.5 and does not scale with dimension gravity. [V]
- **Drag**, per ship block overlapping liquid: `−velocityAtPoint × 1.7 × overlapVolume` (`do_drag`, l.143-163),
  isotropic (the same in every direction). [V] Bodies whose bounds sum to < 10 blocks are sampled at 8 sub-points
  per block. [V l.54-73]
- Only **solid ship blocks** displace water: air cells inside the hull contribute **nothing**. [V: pairs come from ship
  voxels with `block_id != 0`, l.89-92] A hollow hull floats only as well as its wall blocks do.
- Mass: wooden blocks are `#sable:light` (mass 0.5): planks, logs, slabs, stairs, chests, barrels
  (`sable/common/src/main/resources/data/sable/tags/block/light.json`). Default volume 1.0, default gravity 11. So a
  solid plank block floats about half submerged (10.5·v vs 0.5·11). [V data; I arithmetic]
- `sable:volume` is a **per-block-state** property from datapack JSON (`physics_block_properties/`, wiki "Block Physics
  Properties"; `PhysicsBlockPropertyTypes.VOLUME`, `PhysicsBlockPropertyHelper.getVolume(BlockState)` l.70). [V]
- Floating block materials (`sable:floating_material`, `SABLE/physics/floating_block/*`, data in
  `data/sable/floating_materials/end_stone.json`) are **not** water buoyancy. They are an "anti-gravity" lift material
  (`FloatingBlockMaterial(liftStrength, scaleWithPressure, …frictions)`), ticked by `FloatingBlockController` in
  `ServerSubLevel.prePhysicsTick`. [V] Not useful for us.

### 4.2 Can buoyancy be overridden per sub-level?
**No, as far as I can see. Treat it as a hard limitation.** [V negative: no flag in `PhysicsConfigData`
(solver/substep settings only), no per-body switch in `compute_buoyancy`, no Java hook; the constants 10.5 and 1.7 are
hard-coded] What can be changed:
- `sable:volume` per block state, globally for all ships (datapack, including our own blocks). [V]
- Per-dimension `base_gravity`, `universal_drag` (wiki "Dimension Physics Data"). [V]
- Anything else must be an **external force** through a `QueuedForceGroup` (§3.2). [I]

Cleanest supported approach (recommendation): keep Sable's native buoyancy for the hull blocks, and add our own
**dry-volume buoyancy** as an extra force. For each dry compartment cell, transform its center to world space, check
whether the world fluid there is water, and add `ρ·g·cellVolume` upward at that point, in the local frame, × timeStep.
Use 10.5 per m³ to match Sable's float constant. Flood water is a downward force at the water's centroid (or less dry
volume). This matches design.md §4.5 ("hull blocks + dry volume − flood water"). [I] Aggregate per compartment
(one force at the center of buoyancy) to keep it cheap.

### 4.3 Drag
- Native water drag is isotropic and linear (above). Dimension `universal_drag` default 0.09. [V]
- **There is no keel / lateral resistance.** A sail force sideways to the hull just pushes the ship sideways, so
  sailing across or against the wind will not work without our own anisotropic drag (strong lateral, weak
  longitudinal, applied below the waterline). [I from `do_drag`; confirm in the wind spike]

### 4.4 What Sable already does about water inside sub-levels ("water occlusion")
Sable has a ready-made **water occlusion region** system that covers most of design.md §4.3 and §4.4. [V]
- `WaterOcclusionContainer<T>` (`SABLE/sublevel/water_occlusion/WaterOcclusionContainer.java`), one per level, obtained
  with `WaterOcclusionContainer.getContainer(Level)` (nullable). Server: `ServerWaterOcclusionContainer`, client:
  `ClientWaterOcclusionContainer`. Javadoc: "Water occlusion regions are **not networked**." [V]
- API: `addRegion(BoundedBitVolume3i)` → `WaterOcclusionRegion`, `removeRegion(WaterOcclusionRegion)`,
  `isOccluded(Vec3 worldPos)`, `getOccludingRegion(Vec3)`, `markDirty(BlockPos)`, `getRegions()`.
  `WaterOcclusionRegion`: `getVolume()`, `markDirty()`, `isDirty()`. `BoundedBitVolume3i` is
  `dev.ryanhcode.sable.util.BoundedBitVolume3i` (constructor `(minX, minY, minZ, maxX, maxY, maxZ)`, static
  `fromBlocks(Iterable<BlockPos>)`, `getOccupied(x,y,z)`, `setOccupied(x,y,z,boolean)`, `getMinBlockPos()`,
  `getMaxBlockPos()`, `volume()`; `SABLE/util/BoundedBitVolume3i.java` l.17-125). [V] `fromBlocks` appears to return
  null for an empty input (Sable asserts non-null after calling it, `SubLevelAssemblyHelper` l.303-304), so never pass an
  empty set. [I] Its package is `util`, not `api`, so it is not formally API and could change between Sable versions. [I]
- **Regions are in plot coordinates and follow the ship**: `isOccluded` finds the sub-level containing the region's min
  block and transforms the query point with `transformPositionInverse` (l.36-50). [V]
- Auto-dirty: when a block in a plot changes solidity, `SableCommonEvents` calls `markDirty(blockPos)`, which marks
  regions adjacent to that block dirty (`SableCommonEvents.java` l.56-61, `WaterOcclusionContainer.markDirty`). Sable does
  **not** rebuild or remove the region itself. The owner polls `isDirty()` and rebuilds. [V]
- Gameplay effects (all in common mixins, `SABLE/mixin/water_occlusion/`): [V]
  - `EntityMixin`: cancels `updateFluidHeightAndDoFluidPushing` (no in-water state, no pushing, so no swimming or
    drowning), blocks `setSwimming(true)`, and makes `updateFluidOnEyes` see no fluid when the bounding-box center,
    feet or eyes are occluded.
  - `CameraMixin`: `getFluidInCamera` returns none when occluded (no underwater fog or overlay). `FogRendererMixin` /
    `GameRendererMixin` also exist (not read).
  - `WaterFluidMixin`: no underwater ambient particles in occluded regions. `particle/SuspendedParticleMixin` too.
  - `ServerLevelMixin` / `ClientLevelMixin` attach the containers to levels.
- Rendering: `SABLE/render/water_occlusion/WaterOcclusionRenderer.java` (`preRenderTranslucent`, `setupTranslucentShader`),
  hooked by a **common vanilla mixin** on `LevelRenderer.renderLevel` / `renderSectionLayer`
  (`SABLE/mixin/sublevel_render/impl/vanilla/water_occlusion/LevelRendererMixin.java`). It renders region faces into
  depth before translucent water (the boat-patch technique) and clears depth when the camera is inside a region. [V]
  `WaterOcclusionRenderer.isEnabled()/setIsEnabled` exist. [V]
- Usage example: Aeronautics' **Absorber** block (`SIM/content/blocks/absorber/AbsorberBlockEntity.java` l.128-158):
  flood-fills the enclosed air above the block, `container.addRegion(BoundedBitVolume3i.fromBlocks(enclosed))`,
  removes the region when dirty or unpowered. The call I saw runs under `!level.isClientSide` (l.89). How the client
  gets its region in Aeronautics is **[?]**. Since regions are not networked, we must **sync our compartments to clients
  ourselves** and call `addRegion` on the `ClientWaterOcclusionContainer` (that is what creates the render region,
  `ClientWaterOcclusionContainer.addRegion`). [V/I]
- `SABLE/mixin/fluids_on_sub_levels/FlowingFluidMixin.java`: stops fluids flowing off the edge of sub-levels
  (`canSpreadTo` HEAD cancel). Relevant if we place real water blocks in a flooded hull. [V]

What Sable leaves to us: computing compartments (flood fill), creating, rebuilding and removing regions, client sync,
partial flooding (a region is binary: a cell is dry or not; for a water level we would shrink the region to the cells
above the water line, an inferred approach), item flotation, block placement checks and bubble columns (not covered by
the mixins I saw, [?]), and buoyancy of the dry volume (§4.2). Performance: `isOccluded` loops over **all** regions
in the level, each with a `getContaining` lookup and a transform, and it runs per entity per tick. Many ships means
many regions. [V code; I cost]

---

## 5. Coordinate transforms

- Plot → world position: `subLevel.logicalPose().transformPosition(Vec3|Vector3d)`. World → plot:
  `transformPositionInverse`. Directions: `transformNormal` / `transformNormalInverse`, or
  `pose.orientation().transform/transformInverse(Vector3d)`. [V usage, §1.3]
- Rotations: `pose.orientation()` is a JOML `Quaterniond` (body → world). Yaw for alignment: Aeronautics uses its own
  `SimMathUtils.getClosestYaw(orientation)`, so there is no Sable helper for it. [V]
- Plot position of a block → world: `Sable.HELPER.projectOutOfSubLevel(Level, Vec3|Position|Vector3dc)` returns the
  world position if the point is inside a plot, otherwise the point unchanged (`ActiveSableCompanion.java` l.171-192). [V signature; I semantics from the name]
- Which sub-level **owns** a plot position / block entity / entity: `Sable.HELPER.getContaining(Level, Vec3i|Position|Vector3dc|ChunkPos|SectionPos|chunkX,chunkZ)`,
  `getContaining(BlockEntity)`, `getContaining(Entity)`; client-only variants `getContainingClient(...)` → `ClientSubLevel`
  (l.67-165). These only match **plot** coordinates, not a world position near a ship. [V signatures; I semantics: lookup by chunk]
- Which sub-levels are **at / under** a world position: `Sable.HELPER.getAllIntersecting(Level, BoundingBox3dc)` (l.50),
  `container.queryIntersecting(BoundingBox3dc)`, `physicsSystem.queryIntersecting(...)`. Then transform the point into
  each candidate's plot and check the block there. [V signatures; I procedure]
- Raycasts and "run including sub-levels": `runIncludingSubLevels(...)`, `findIncludingSubLevels(...)` (l.208-263), and
  distance helpers `distanceSquaredWithSubLevels(...)` (l.273-352). [V signatures; behaviour not studied]
- Entities: `SubLevelHelper.pushEntityLocal(SubLevel, Entity)` / `popEntityLocal(...)` temporarily move an entity
  into plot space and back (`SABLE/api/SubLevelHelper.java` l.42-108). [V]
- Velocity at a world point: `Sable.HELPER.getVelocity(level, pos, dest)` (§3.3). [V]
- Sable's GameTest helpers convert test-relative ↔ absolute vectors (`SableTestHelper.absolutePosition/localPosition/absoluteDirection`),
  but they live in the NeoForge module, so we write our own. [V]

---

## 6. Entities and sub-levels

- **Kicking** (wiki): entities spawned inside a plot are teleported to world space with the sub-level's velocity, unless
  tagged `#sable:retain_in_sub_level`. Other tags: `#sable:destroy_when_leaving_plot`, `#sable:destroy_with_sub_level`. [V wiki]
  API: `EntitySubLevelUtil.kickEntity(SubLevel, Entity)`, `shouldKick(Entity)`, `setOldPosNoMovement(Entity)`,
  `hasCustomEntityOrientation/getCustomEntityOrientation` (`SABLE/api/entity/EntitySubLevelUtil.java` l.29-96). [V]
  Sable's default retain list includes `minecraft:armor_stand`, minecarts, `minecraft:snow_golem`, `create:seat` and other
  mods' seat entities (`data/sable/tags/entity_type/retain_in_sub_level.json`). [V]
- **Tracking**: entities standing on a sub-level stay in world space but are marked as tracking it. They are networked
  and interpolated relative to it and move with it. Players also log out and back in relative to it (tracking points). [V wiki]
  `Sable.HELPER.getTrackingSubLevel(Entity)`, `getLastTrackingSubLevel`, `getVehicleSubLevel(Entity)` (= the sub-level
  containing the entity's vehicle), `getTrackingOrVehicleSubLevel` (`ActiveSableCompanion.java` l.434-466). [V]
- **Seating crew at a station** (design.md §6): Sable has no explicit "attach entity at local position" API. [V negative]
  The supported pattern is a **vehicle entity living in the plot**: an entity type of ours (an invisible seat), tagged
  `#sable:retain_in_sub_level`, spawned at the station's plot position, with the crew mob as its passenger.
  `getVehicleSubLevel` exists exactly for this, and Sable retains Create's and other mods' seats this way. [V for the
  pieces; I that passengers render and move correctly; verify in the crew spike] Alternative: keep the mob in world space
  and teleport it each tick to `pose.transformPosition(stationPos)`. That is fragile and jittery. [I]
- **Pathfinding**: Sable patches vanilla navigation (`SABLE/mixin/entity/entity_pathfinding/`: `PathNavigationMixin`,
  `GroundPathNavigationMixin`, `WalkNodeEvaluatorMixin`, `FlyNodeEvaluatorMixin`, `PathMixin`, `PathfindingContextMixin`,
  `RandomPosMixin`). If the mob tracks a sub-level (or the target is in a plot), `createPath` converts the target set into
  plot-local positions and pathfinds in plot space (`PathNavigationMixin.sable$createPath`, l.58+). [V] So walking crew on
  deck is plausible later. Quality on a moving ship is unknown. [?]
- Other relevant mixin groups exist (names only, not read): `climbing_sub_levels`, `player_standup`, `player_freezing`,
  `interaction_distance`, `block_placement`, `entity`. [V names]

---

## 7. `sable-common` vs. loader-only

- **Everything above is in `sable/common`** (`SubLevelAssemblyHelper`, `SubLevelHelper`, `Sable.HELPER`, containers, physics
  handles, forces, mass, block/BE interfaces, water occlusion, events interfaces, commands). So our `common` module can use
  all of it. [V: file paths]
- Loader modules contain only: platform service implementations (registered in `META-INF/services` for
  `SableAssemblyPlatform, SableChunkEventPlatform, SableEventPlatform, SableEventPublishPlatform, SableLoaderPlatform,
  SablePlatform, SablePlotPlatform, SableSubLevelRenderPlatform`), loader event classes (`neoforge/event/ForgeSable*Event`,
  `fabric/event/FabricSable*Event`), loader mixins (camera rotation, block outline, sounds, Flywheel compat, …) and the
  **GameTests** (`neoforge/gametest`). [V directory listing]
- **Subscribing from common code**: `SableEventPlatform.INSTANCE.onSubLevelContainerReady(listener)`,
  `.onPhysicsTick(SablePrePhysicsTickEvent)`, `.onPostPhysicsTick(SablePostPhysicsTickEvent)`. `INSTANCE` is loaded with
  `SablePlatformUtil.load` (ServiceLoader). NeoForge forwards to `NeoForge.EVENT_BUS.addListener`, Fabric to
  `Fabric*Event.EVENT.register` (`neoforge/.../platform/SableEventPlatformImpl.java`,
  `fabric/.../platform/SableEventPlatformImpl.java`). Aeronautics and Simulated call it from their common init
  (`AERO/Aeronautics.java` l.65-66, `SIM/Simulated.java` l.58-59). [V] So we need **no platform service of our own** for
  Sable events. Call it once during common init.
- `SablePlatform`, `SableAssemblyPlatform`, `SableLoaderPlatform`, `SableEventPublishPlatform` are `@ApiStatus.Internal`;
  `SableEventPlatform` is not. [V]
- API stability: only `dev.ryanhcode.sable.api.*` is clearly meant as API. Much of what we need lives outside it:
  `sublevel.ServerSubLevel`, `sublevel.plot.*`, `sublevel.water_occlusion.*`, `sublevel.system.SubLevelPhysicsSystem`,
  `util.BoundedBitVolume3i`. Several members are `@ApiStatus.Internal` (e.g. `ServerSubLevel.prePhysicsTick`,
  `ForceTotal.applyForces`, `SubLevelAssemblyHelper.kickFromContainingSubLevel`). Wrap all Sable calls in one `ship/sable`
  adapter package in `common`, so a Sable update touches only that package. [V annotations; I recommendation]
- Event signatures: `SablePrePhysicsTickEvent.prePhysicsTick(SubLevelPhysicsSystem physicsSystem, double timeStep)`,
  `SablePostPhysicsTickEvent.postPhysicsTick(SubLevelPhysicsSystem, double)`,
  `SableSubLevelContainerReadyEvent.onSubLevelContainerReady(Level level, SubLevelContainer container)` (fires for client
  and server levels; filter with `instanceof ServerSubLevelContainer`, as Aeronautics does). [V]
- Registries: `ForceGroups` and `PhysicsBlockPropertyTypes` use Veil's `RegistrationProvider` (`foundry.veil.platform.registry`),
  so **Veil is a transitive dependency** of Sable. [V import in `ForceGroups.java`] Registering our own `ForceGroup`
  goes through our own `Services.REGISTRY` against `ForceGroups.REGISTRY_KEY`. [I]
- **Sable Companion** (`dev.ryanhcode.sable-companion:sable-companion-common-<mc>:1.6.0`, an `api` dependency in
  `sable/common/build.gradle` l.57, version in `sable/gradle.properties` l.33): a small API jar with
  `SableCompanion.INSTANCE`, `SubLevelAccess`, `ClientSubLevelAccess` and the math types (`Pose3d`, `BoundingBox3i/d`).
  `Sable.HELPER` is `(ActiveSableCompanion) SableCompanion.INSTANCE` (`SABLE/Sable.java` l.35). It lets mods *soft*-depend
  on Sable. Its source is **not in `refs/`**, so `Pose3d`'s exact API is [?]. We hard-depend, so we use `Sable.HELPER`
  directly. We get the companion transitively and do not need to declare it, but the Maven filter must include the
  `dev.ryanhcode.sable-companion` group (wiki "Home"). [V/I]

---

## 8. Persistence and loading

- **Stable id**: `SubLevel.getUniqueId()` (UUID), documented as stable across save/load and identical on client and
  server (§1.1). Lookup: `container.getSubLevel(UUID)`. **Key `ShipData` on this UUID.** [V] Also `getName()/setName`
  (shown by Sable commands, `/sable … name set`). [V]
- **Custom data on the sub-level**: `ServerSubLevel.getUserDataTag()` / `setUserDataTag(CompoundTag)`, "saved and serialized
  with this sub-level" (`ServerSubLevel.java` l.548-560; written as `"user_data"` in `SubLevelSerializer.java` l.82-84). [V]
  Good for a small back-pointer (our ship UUID, schema version). Keep the authoritative `ShipData` in our own SavedData so
  it survives while the sub-level is unloaded. [I]
- Saving: sub-levels are stored in "holding chunks" (`SABLE/sublevel/storage/holding/SubLevelHoldingChunkMap.java`), keyed
  by world chunk. `updateChunkStatus(ChunkPos, boolean loaded)` loads and unloads them with the world chunks.
  `SubLevelRemovalReason.UNLOADED` vs `REMOVED` (unloaded keeps occupancy data). [V class/javadoc; I that a ship unloads
  when the world chunk at its position unloads]
- Ship blocks are real plot chunks. Block entities in them save with the sub-level. [I]
- **Force loading**: `ServerSubLevelContainer.addForceLoadTicket(ServerSubLevel, SubLevelLoadingTicketType<T>, T key)` /
  `removeForceLoadTicket(...)`, `collectForceLoadedSubLevels()`, `getAllTickets()` (l.199-300). Ticket types:
  `SubLevelLoadingTicketType.create(ResourceLocation, Codec<T>)`, built-in `COMMAND_FORCED`
  (`SABLE/api/sublevel/ticket/SubLevelLoadingTicketType.java`). Tickets are persisted (`SubLevelTicketsSavedData`) and
  re-activated when the sub-level loads (`SubLevelTicketLoadingSystem.onSubLevelAdded`), and dropped on `REMOVED`. [V]
  Whether a ticket also keeps the surrounding *world* chunks (sea floor, water) loaded for collision is handled by
  `PhysicsChunkTicketManager` (`physicsSystem.getTicketManager()`), not studied. [?]
- Observers: `SubLevelObserver.onSubLevelAdded/Removed(reason)` let us attach and detach runtime state (`ShipRuntime`)
  when a ship loads or unloads. [V]
- Connected sub-levels load together via `BlockEntitySubLevelActor.sable$getLoadingDependencies()`. [V] Not needed for single-body ships.

---

## 9. Testing and debugging

### 9.1 How Sable tests sub-levels
- Tests live in **`sable/neoforge/src/main/java/dev/ryanhcode/sable/neoforge/gametest/`** (`AssemblyTest`, `PhysicsTest`,
  `SableTestHelper`), registered with NeoForge's `@GameTestHolder(Sable.MOD_ID)` and vanilla `@GameTest(template = …)`. [V]
  Our tests go in `common` using vanilla GameTest. Only the holder/registration is loader-specific (our own concern).
- `AssemblyTest.testBrittleBreaking` (l.61-118): `SubLevelContainer.getContainer(helper.getLevel())`, then
  `SubLevelAssemblyHelper.assembleBlocks(level, min, BlockPos.betweenClosed(min, max), bounds)`, then
  `physicsSystem.getPipeline().teleport(subLevel, center, rotation)`, then `helper.runAtTickTime(10, …)`, comparing
  `subLevel.getPlot().getBoundingBox()` size and the block states in the plot (`subLevel.getLevel().getBlockState(plotPos)`). [V]
- `PhysicsTest.testGravity` (l.54-98): spawns a single-block sub-level with `SableTestHelper.spawnSingleBlockSubLevel`
  (allocate, center chunk, set block through the embedded accessor), then checks `RigidBodyHandle.getLinearVelocity`
  against `DimensionPhysicsData.getGravity` and the displacement of `logicalPose().position()`. **So physics runs in the
  GameTest server.** [V] Note the FIXME in that test: "the sublevels don't have a consistent distance travelled. It seems
  like they aren't spawned on the first tick?" (l.~91), so use tolerances and wait a few ticks. [V]
- Cleanup: `SableTestHelper.removeSubLevel(container, subLevel)` → `container.removeSubLevel(plotX − origin.x, plotZ − origin.y, REMOVED)`
  (l.83-87). Sable also mixes into the GameTest framework (`SABLE/mixin/game_test/`: `GameTestInfoMixin.succeed`,
  `StructureUtilsMixin.clearSpaceForStructure`, `TestCommandMixin.resetGameTestInfo`), presumably to clear sub-levels in a
  test area. [V targets; I purpose]
- Native physics: the Rapier library must load in the headless test server. Sable's own tests run there, so it does. [I]

### 9.2 Commands for playtests (all `/sable …`, permission level 2, `SABLE/command/SableCommand.java` l.38-80) [V]
- `/sable assemble connected|area|sphere|cube …`, `/sable assemble shatter …` (`SableAssembleCommands`)
- `/sable spawn schematic <name>` (templates under `data/sable/schematics`), `spawn sphere|clone|jenga|…` (`SableSpawnCommands`)
- `/sable name set|clear|get`, `/sable teleport <targets> <destination>`, `/sable remove <targets>` (`SableSubLevelCommands`)
- `/sable physics impulse linear|angular … global|local`, `/sable physics rotation …`, `/sable physics translation add|set …`
  (`SablePhysicsCommands`). Useful to shove a ship and check righting moment.
- `/sable storage prune_holding_chunks|find_all_sub_levels|find` (`SableStorageCommands`)
- `/sable debug paused`, `debug forceload add|query|remove <sub_level>`, `debug info <sub_level>`, `debug engage_gizmo`,
  `debug joint add …`, `debug config …` (`SableCommand`, `SableJointCommands`, `SableConfigCommands`)
- Sub-level selector argument: `SABLE/api/command/SubLevelArgumentType.java` with filters (name, distance, limit, sort, UUID). [V]

---

## 10. Answers to design.md §21

1. **What does the API offer for assembly, forces and custom buoyancy?**
   Assembly: `SubLevelAssemblyHelper.gatherConnectedBlocks` + `assembleBlocks` (§2.1-2.2). Disassembly: no API; build it
   from `AssemblyTransform` + `moveBlocks` + `moveTrackingPoints`, as Aeronautics does (§2.4). Forces: `QueuedForceGroup`
   / `ForceTotal` / `RigidBodyHandle` impulses in the local frame at plot positions, every physics substep (§3).
   Custom buoyancy: **none**. Buoyancy is native, per solid block, controlled only by the global per-block-state
   `sable:volume` (§4.1-4.2). [V]
2. **Can buoyancy be overridden per ship?** No. Add dry-volume buoyancy and flood weight as **external forces** (one force
   group, e.g. `pirates_n_ships:buoyancy`) on top of Sable's hull-block buoyancy (§4.2). [V negative + I design]
3. **Cargo weight vs. Sable mass?** Mass comes only from block-state properties (`PhysicsBlockPropertyHelper.getMass(level, pos, state)`
   reads `sable:mass`), and Sable updates the mass, COM and inertia automatically on every block-state change
   (`SubLevelPhysicsSystem.updateMassDataFromBlockChange`, l.517-546, which calls `pipeline.onStatsChanged`). [V] Options:
   (a) **recommended for containers we own**: give crates and barrels a `load` block-state property (e.g. 0-3) and
   datagen a `physics_block_properties` JSON with `overrides` per load level (the wiki's piston example). That gives real
   mass, inertia and COM, so lower freeboard and slower acceleration come for free. [I, supported by data format]
   (b) for vanilla chests (no state to change): a downward force at the container position, in a `cargo` force group,
   adds weight and heel but no inertia. [I] Changing `MassTracker` directly (`addBlockMass` is public) is fragile:
   it is rebuilt from blocks and is not documented as API. Avoid it. [I] Speed loss then comes mostly from deeper draft
   (more submerged blocks, so more native drag) plus our own drag factor if needed.
4. **Sub-level directly from a structure template?** Yes: `allocateNewSubLevel` → `plot.newEmptyChunk` per chunk →
   `template.placeInWorld(plot.getEmbeddedLevelAccessor(), BlockPos.ZERO, …)`, as in `/sable spawn schematic` (§2.5). No
   need to place the blocks at the berth first. Teleport after placement to set the exact pose. [V + I]
5. **Does the API differ between `sable-common` and the loaders?** No, for everything we need. All APIs, including event
   subscription (`SableEventPlatform`), are in common. Loader modules only hold service implementations, loader event
   classes, loader mixins and Sable's own GameTests (§7). [V]
6. **Are the dry-hull rendering hooks loader-specific?** Sable's water-occlusion renderer is hooked by a **common** vanilla
   mixin on `LevelRenderer` (§4.4), so it works on both loaders. If we use Sable's regions, we need **no rendering mixin
   of our own** for the basic dry hull. Partial flooding (a moving water surface) would need our own renderer, ideally
   on loader render-stage events behind a platform service. Shader-pack (Iris) compatibility of Sable's depth trick is
   untested. [V hook location; I rest]

---

## 11. Recommended approach per spike

### 11.1 Assembly at the helm
Entry points: `SubLevelAssemblyHelper.gatherConnectedBlocks`, `assembleBlocks`; for disassembly `AssemblyTransform`,
`moveBlocks`, `moveTrackingPoints`, `ServerLevelPlot.kickAllEntities()`, constraint motors.
1. Server, on helm use: if `Sable.HELPER.getContaining(helmBE) != null`, go to disassembly. Otherwise
   `gatherConnectedBlocks(helmPos, level, config.maxBlocks, predicate)`. The predicate rejects fluids, terrain tag, air, and
   blocks in a `pirates_n_ships:never_assemble` tag. Report `GatherResult.State` errors to the player.
2. `ServerSubLevel ship = assembleBlocks(level, helmPos, result.blocks(), result.boundingBox())`.
3. Create `ShipData` keyed on `ship.getUniqueId()`. Write a back-pointer into `ship.setUserDataTag(...)`. `ship.setName(...)`.
4. Disassembly: check speed (`RigidBodyHandle.getLinearVelocity/getAngularVelocity`), align (FreeConstraint + motors,
   like `PhysicsAssemblerBlockEntity.startDisassembling`, or a simpler `teleport` to the snapped pose when nearly still),
   check the target volume for obstructions (`moveBlocks` overwrites), then run the Aeronautics-style sequence (§2.4).
Risks: water pockets after assembling in water (§2.2), water left inside the hull after disassembly (§2.4), terrain
gathered without a good predicate, yaw-only 90° disassembly (ships tilted by waves must be levelled first). Large ships:
`gatherConnectedBlocks` and `moveBlocks` run synchronously on the server thread. [I]

### 11.2 Dry hull below the waterline
Entry points: `WaterOcclusionContainer.getContainer(level).addRegion(BoundedBitVolume3i.fromBlocks(cells))`,
`removeRegion`, `WaterOcclusionRegion.isDirty()`, `QueuedForceGroup` for buoyancy.
1. Flood fill in plot coordinates (our hull analysis). Compartments become `Set<BlockPos>` in **plot** coordinates.
2. Server: `addRegion` per dry compartment. This gives the entity in-water, swim, eye, camera and particle effects (§4.4).
3. Sync compartments (bitsets) to clients with our own payload and `addRegion` on the client container for rendering.
   Remove and re-add on rebuild and when the sub-level unloads (`SubLevelObserver.onSubLevelRemoved`).
4. Buoyancy: in `SablePrePhysicsTickEvent`, per ship, per compartment, sum the cells whose world position is in water.
   Apply `10.5 × n` upward (world Y, converted to the local frame, × timeStep) at their centroid.
Risks / gaps: regions are binary (no partial water level) and not networked; `isOccluded` cost grows with regions × entities;
items, bubble columns and block placement are not covered [?]; Sable does not rebuild dirty regions, we must.
**Possible hard limitation:** native buoyancy and drag cannot be turned off per ship, so our force is always additive.
A badly tuned dry-volume force means over-buoyant ships. Tune the constants together.

### 11.3 Wind and sails
Entry points: `BlockEntitySubLevelActor.sable$physicsTick` on the sail block entity (or one per-ship system in
`SablePrePhysicsTickEvent`), `QueuedForceGroup.applyAndRecordPointForce`, `SubLevelHelper.registerWindProvider`.
1. Optionally register our wind field with `SubLevelHelper.registerWindProvider((pos, level) -> windVelocity)` so that
   `Sable.HELPER.getVelocityRelativeToAir` includes it. [V API] Note: nothing in Sable or Aeronautics calls
   `getVelocityRelativeToAir` or `registerWindProvider` (grep), and `BlockSubLevelLiftProvider` uses the body velocity
   only (l.152-154), so registering wind changes nothing by itself. It is just a shared place for other mods to read
   our wind. [V]
2. Per substep: apparent wind = `Sable.HELPER.getVelocityRelativeToAir(level, worldSailPos, dest)` negated. Convert it to the
   local frame with `pose.orientation().transformInverse`. Compute the sail force from the angle against the sail normal
   (plot frame = block facing). Apply `force × timeStep` at the sail's plot position in a `pirates_n_ships:wind` group.
   The heel torque follows automatically from the point of application.
3. Add **keel / lateral drag** below the waterline (strong sideways, weak forward, local frame) and rudder torque
   proportional to forward speed. Without lateral drag, sailing across the wind will not work (§4.3).
Risks: forces are reset every substep, so sail state must be cheap to evaluate. Units are impulses. Sable's force
display shows our groups only if registered in `sable:force_groups`.

### 11.4 Crew station
Entry points: retained entity in the plot (`#sable:retain_in_sub_level`), `Sable.HELPER.getVehicleSubLevel`,
`getContaining(Entity)`.
1. Register an invisible `StationSeat` entity type, add it to `#sable:retain_in_sub_level` (datagen tag), and spawn it at the
   station block's **plot** position (the station BE knows its plot pos).
2. Make the crew mob `startRiding(seat)`. The mob is then attached to the ship through its vehicle.
3. Player stations work the same way (players already ride retained seats from other mods).
Risks: rendering, interpolation and the interaction ray for passengers of plot entities are not verified [?]. Station
blocks broken or disassembled must dismount and discard the seat (`#sable:destroy_with_sub_level` is an option for the
seat type). Walking crew later can rely on Sable's pathfinding mixins (§6).

### Least certain
- `Pose3d` and `BoundingBox3*` exact method lists (Sable Companion source not available).
- Client-side creation of water-occlusion regions in Aeronautics, and whether region rendering behaves with many ships.
- Pose and COM handling after `template.placeInWorld` into a fresh plot.
- How world-chunk loading around active ships works (`PhysicsChunkTicketManager`).
- Whether entities riding a plot-retained seat render, interpolate and interact correctly (crew spike must check).
- Buoyancy details derived from Rust source (which voxels are in the liquid octree, overlap computation). They were read,
  not run.

