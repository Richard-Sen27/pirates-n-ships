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
- `GatherResult(Set<BlockPos> blocks, int checkedBlocks, BoundingBox3i boundingBox, State assemblyState)` with
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

### Corrections from spike 1 (verified by compiling and running against Sable 2.0.6)
- The `FrontierPredicate` is **not applied to the gather origin**: the origin is always taken when it is not air. [V]
- `Pose3d` API, checked with `javap` on the Sable Companion 1.6.0 jar: `Pose3dc.transformPosition(Vec3)`,
  `transformPositionInverse(Vec3)`; `BoundingBox3i(BlockPos, BlockPos)`, `BoundingBox3i(BoundingBox3ic)`,
  `BoundingBox3i.EMPTY`; `BoundingBox3dc.minX()…` and `toMojang()` (returns an `AABB`). [V]
- Our own wrapper for all of this is `ship/sable/SableShips` and `ship/sable/ShipBody` in `common`. New Sable calls go there.

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

### 9.0 Findings from spike 1
- Removal reasons, verified: world close and holding-chunk unload use `UNLOADED` (`ServerSubLevelContainer` l.328,
  `SubLevelHoldingChunkMap` l.192/624). `REMOVED` is used for an empty or invalid mass tracker (`SubLevelContainer` l.165),
  explicit removal, a sub-level that fails to load with an empty plot (`SubLevelSerializer` l.168),
  `SubLevelHeatMapManager` l.249, and physics recovery with no center of mass (`SubLevelPhysicsSystem` l.379). So a ship
  that fails to load is reported as `REMOVED`. [V]
- Sable's GameTest mixin removes sub-levels that intersect the test area when a test succeeds, so cleanup is automatic.
  Our tests still remove their ships explicitly. [V]
- Physics in our GameTests settled within about 10 ticks for a small hull resting on stone. [V]
- Caution from spike 1's tests: `new Quaterniond().rotationX(angle)` gave an unexpected orientation on the test
  classpath. The tests use `rotateAxis(angle, 1, 0, 0)` instead. Not investigated. [?]

### 9.0b Findings from spike 2 (verified by running)
- **Physics is 32-bit, and precision fails far from the origin.** Sable's native physics uses `f32`
  (`sable/sable_rapier/src/main/rust/marten/src/lib.rs`: `Real = f32`). One `f32` step is a whole block beyond
  8,388,608. Ships on identical stone pads did not move at 1,000, 3,000,000 or 6,000,000 blocks from the origin, and
  some sank into the stone at 12,000,000 and 13,600,000. [V]
- **The vanilla GameTest server places its test grid at a random X/Z of up to ±14,999,992** (`GameTestServer#startTests`).
  That made physics tests fail at random: a ship "moving at 72 m/s" was in free fall through the world. Our
  `mixin/MixinGameTestServer` keeps the grid within ±250,000 blocks. [V]
- Sable's GameTest cleanup removes only sub-levels that intersect the test box, on success and when the space is
  cleared (`GameTestInfoMixin`, `StructureUtilsMixin`). Ships of failed or timed-out tests, and ships that drifted out,
  stay alive. Tests that create ships must use `ship/ShipTestCleanup`. [V]
- The GameTest world in `neoforge/build/gametest/world` is **not** deleted between runs, and batches do not reuse grid
  positions (`clearOnBatch=false`). [V]
- A real `ServerPlayer` from `makeMockServerPlayerInLevel` can't be used in GameTests: on login Sable sends
  `sable:dimension_physics` to the fake connection, which throws "may not be sent to the client". Tests use a plain
  mock `Player`, or a `ServerPlayer` that is not placed in the level. [V, found by three packages independently]
- `ForceTotal.applyForces` wakes a sleeping body only when the force or torque changed (`ForceTotal` l.29), so a
  constant force does not wake a sleeping ship. [V]
- Sable's own `RegistryObject` handles (e.g. `ForceGroups.LEVITATION.get()`) can't be used from our `common`: the
  class is not on our compile classpath. Our own force group is registered through `Services.REGISTRY` against
  `ForceGroups.REGISTRY_KEY` (`ship/sable/ShipForces`). [V]
- `WaterOcclusionContainer.isOccluded` finds the ship through the min corner of the region's **bounding box**. [V]
- Heights along the ship's up vector must be compared using one and the same up vector (the one of the hull
  analysis). Plot coordinates are in the millions, so two slightly different up vectors shift a height by hundreds
  of blocks. [V, cost spike 2 a bug]
- There is no listener for "a block state changed inside a plot". Sable uses its own `LevelChunk#setBlockState` mixin
  and publishes nothing, so we have our own (`mixin/MixinLevelChunk`). [V]
- Not covered by Sable's water occlusion: boats (`Boat#checkInWater` reads the fluid directly), fishing bobbers and
  mob pathfinding water nodes. [V by reading; not fixed]

### 9.0c A Sable bug: stale chunk lookups on a reused plot (found by D2c, verified by a deterministic test)
- `ServerChunkCache#getChunk` (behind `Level#getChunkAt`, `getBlockState`, `getBlockEntity`) answers from a private
  4-entry memo before it asks the chunk map. Vanilla clears that memo once per tick. Sable's
  `mixin/plot/ServerChunkCacheMixin` redirects the chunk map lookups for plot chunks, but not the memo, and neither
  removing nor allocating a plot clears it. `SubLevelContainer#getFirstEmptyPlot` hands a freed plot to the next new
  sub-level at once. [V]
- So when one sub-level is removed and another is created **in the same server tick**, `Level#getBlockEntity` on the
  new plot can return a block entity of the removed sub-level's dead chunk. `SubLevelAssemblyHelper#moveBlocks` writes
  block states through the chunk map (correct) but loads block entity data through `Level#getBlockEntity` (stale):
  a chest arrived in the new ship **empty**, and its items were written into the dead chunk. Physics and anything
  else that reads the new plot in that tick can be wrong too. [V]
- Our workaround: `ship/assembly/ChunkCacheGuard.flush(level)` pushes the memo out before a ship is assembled, before
  a plot is read for disassembly, and after disassembly. Regression test:
  `AssemblyGameTests.plotFreedThisTickKeepsChestContent`. Any new code that removes a ship or reads a freshly created
  plot must flush too. `getChunkNow` is redirected by Sable and always returns the live plot chunk. [V]
- This is worth reporting to the Sable project. It can affect any mod that assembles a sub-level right after another
  one was removed.

### 9.0d Findings from spike 3 part 1 (sails, verified by running)
- **Hollow block hulls float but have almost no righting moment.** A 5×4×5 plank hull capsized within 2 seconds on a
  beam reach when the full physical heel and pitch moments of sail and keel were applied. Sable's own water drag below
  the waterline adds a heeling couple that we can't scale. Our workaround is `sailing_runtime.sail_heel_factor`
  (default 0.25), which scales the roll and pitch torque of our forces. Yaw is kept. [V]
- `ServerSubLevel.latestLinearVelocity` is a per-tick difference of the pose position, not the velocity of the center
  of mass. Use the `RigidBodyHandle` velocities (`ShipBody.velocities`). [V]
- The GameTest world keeps its server config file between runs (`neoforge/build/gametest/world/serverconfig`), so a
  changed config default does not reach tests on a machine that ran them before. Tests that depend on a value pin it
  with `ConfigOverrides`. [V]
- Measured with a 42.5 kpg hull and a small square sail in 6 blocks/s of wind from astern: about 0.4 m/s mean forward
  speed over 100 ticks (peak about 1 m/s). The same hull drifts at about 0.09 m/s with no force at all. [V]

### 9.0e Findings from spike 3 part 2 and spike 4 (helm, anchor, crew seat; verified by running)
- **32-bit positions also stall slow ships, long before ships fall through blocks.** At 131,000 to 262,000 blocks from
  the origin one `f32` step is 1/64 block. A ship at 0.3 m/s moves less than half of that per physics substep, so it
  reported a velocity but did not move: 0.2 blocks in 9 s at x = -191,000, against 6.3 blocks at x = 55,000. As a rule
  of thumb a ship slower than about 20 × the step size per second stalls: about 0.3 m/s beyond 131,000 blocks, 0.15 m/s
  beyond 65,000, 0.08 m/s beyond 32,000. GameTests now run within ±4,096 blocks. [V]
- Clicking a block on a ship gives a hit result in **plot space** (`mixin/clip_overwrite/BlockGetterMixin#clip` returns
  the sub-level clip result as is), so "which part of the block was clicked" works unchanged on ships. [V]
- **A seat entity inside the plot works on the server.** It needs only two entity-type tags: `sable:retain_in_sub_level`
  (otherwise `mixin/entity/entity_kicking/ServerLevelMixin#sable$kickEntity` kicks it out when it is added) and
  `sable:destroy_with_sub_level` (`ServerLevelPlot#kickAllEntities` l.265-289 then kills it on disassembly and on
  removal for good). It lives in a plot chunk, ticks there, and is saved with its passenger. [V]
- **The rider of a plot vehicle lives in world space.** Sable moves it every tick
  (`mixin/entity/entity_rotations_and_riding/EntityMixin` l.108-130 → `EntityRidingSubLevelVehicleHelper.kickRidingEntity`
  l.14-27): it transforms the rider's **eye** point and hangs the body straight down in world space. So the rider stays
  upright, its eyes stay fixed in the ship's frame, and on a tilted ship its feet swing off the seat by about
  2 × eye height × sin(tilt / 2): 1.2 blocks at 43°. `getCustomEntityOrientation` exists but always returns null. [V]
- After `startRiding`, the rider keeps its old position until its next ride tick: call `seat.positionRider(rider)` at
  once. A released rider must be given a dismount position in world space, or it drops to the plot coordinates. [V]
- `ShipBody.worldBounds()` is an empty box right after assembly, until the next tick. [V]
- Not verified (needs the client): how a rider of a plot seat renders and interpolates, and whether it is visible from
  far away (the seat travels with plot tracking, the rider with world tracking).

### 9.0f Findings from the flaky test investigation (D5, measured over 26 full runs)
- **Small hollow hulls have almost no metacentric height.** A 5×4×5 plank box with a deck, helm and mast floats with
  an estimated GM of 0.1 to 0.2 blocks. It lies about 22° bow up at rest (the helm's weight at the stern is enough), and
  under a small sail in 6 blocks/s of wind from astern it runs 35 to 46° bow down, because Sable's drag on the submerged
  blocks works against a drive force applied at the center of mass (with our heel factor at 0 it is still 34°). In that
  attitude a few degrees of heel make it sheer off course by up to 4° in 10 s, with a random sign. A stone bottom layer
  cuts the pitch to about 16° and the drift to under 0.25°. Tests that measure course or heading use a ballasted hull
  (`SailingGameTestsShips.ballast`). [V]
- **Tests in one GameTest batch do not all start in the same tick** (a spread of up to about 30 ticks was seen), and
  Sable reuses a freed plot at once. After a test removes its ship, a later test can put an identical hull, with its
  seats and block entities, at the same plot coordinates. Never search an old plot position after a ship is gone
  without checking which ship owns what you find. [V]
- `Level#noCollision` does not see sub-level blocks. To check that an entity stands on a ship, compare heights relative
  to the deck. [V]

### 9.0g Findings from the yard sails (F5a, verified by running)
- **GameTest barrier ceiling.** `GameTestInfo.prepareTestStructure` → `StructureUtils.encaseStructure` puts barrier blocks one above every template (relative y = 13 for our templates). A floating test ship rises about 1.4 blocks after assembly, so anything reaching y ≥ 12 drags on the barriers: ships stopped after 5 blocks, a kicked hull could not roll, a hull was held down. `SailingGameTestsShips.openSky(helper, size)` removes the ceiling; the sailing and station basins call it.
- **Test ballast was no ballast.** `SailingGameTestsShips.ballast()` used stone, which is in `pirates_n_ships:terrain` and is never gathered, so the hull floated without its floor (30.6 kpg instead of about 43) and the D5 thresholds (§9.0f) were measured on that hull. Fixed in F5b: cobblestone (not terrain, in `#sable:heavy` through `#c:cobblestones`, 2 kpg per block against 1 for planks) gives the test ship 81.6 kpg; full rudder now turns it ±6.8° in 200 ticks at 0.38 m/s with 3.4° bow-down trim and no midships drift (thresholds: turn ≥ 4°, drift ≤ 0.5°), identical over five runs. The beam-reach tests moved to the triangular rig (0.371 m/s forward, leeway ratio 0.30 with the keel, 0.45 without; bound 0.37).
- **Block entity renderers run in client sub-levels**: `sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher.java` l.219-252 renders each section's block entities through the vanilla `BlockEntityRenderDispatcher` with the ship's pose; `mixinhelpers/sublevel_render/vanilla/VanillaSubLevelBlockEntityRenderer.java` does the call; NeoForge's culling box is transformed in `neoforge/mixin/block_entity_visible/LevelRendererMixin.java`. Do **not** set `shouldRenderOffScreen`: Sable's sub-level path only renders the per-section block entities.

### 9.0h Reported velocities under contact (G11, measured)
- With two bodies pressed together by a continuous queued force (the grapple rope), `RigidBodyHandle`'s linear velocity, and therefore `ShipBody.velocityAt`, keeps reporting about the velocity the force would produce each substep (about 0.1 m/s here), while the contact solver cancels nearly all of the motion: the actual distance changed by about 0.005 m/s. Contact and stall logic must measure positions, not velocities; velocity-based damping stays usable only as a brake.
- `RigidBodyHandle.applyImpulseAtPoint` goes straight to the physics pipeline (Sable uses it for dispenser recoil and arrows hitting ships), while `QueuedForceGroup.applyAndRecordPointForce` is a per-substep force shown in Sable's force display; the cannon uses the first, the grapple the second.

### 9.0i Waking sleeping bodies under a steady queued force (H1)
A rigid body that has fallen asleep at rest is not woken by a queued force group whose total does not change
(`ForceTotal#applyForces` only wakes when the total changes). A steady hazard force therefore never moved a ship
lying still. Workaround in `hazards/HazardShipForces`: while a ship is inside a field, add a zero velocity through
`ShipBody.addVelocity` once per game tick (`RigidBodyHandle#addLinearAndAngularVelocity` wakes the body), then
record the point forces in the `pirates_n_ships:sea_hazards` group every physics substep (the grapple's path:
`ServerSubLevel` l.395, `QueuedForceGroup` l.25, `ForceTotal` l.101-105). Impulses once per game tick through
`applyImpulseNow` were far too weak against the water drag; the ship rate ended at a quarter of the entity rate
(`HazardField.SHIP_ACCELERATION`), measured in the H1 GameTests.

### 9.0j Splitting and entity-loaded chunks (K1a)
- Sable splits a sub-level by itself when a part is cut off (`SableConfig` `sub_level_splitting`, the flood fill in
  `sublevel/plot/heat/SubLevelHeatMapManager`): a cannonball or a kraken strike through the only mast block turns the
  rig above it into its own body, marked "split from" the ship (`ServerSubLevel.setSplitFrom`). Tests that break a
  connecting block must expect a second ship id; the kraken tests switch `mobGriefing` off instead. Sable has no
  merge: rejoining pieces is ours to build.
- `getEntitiesOfClass` and the other entity lookups do not see entities in chunks that are loaded but not
  entity-ticking. Spawners that need to see their neighbours (the kraken's 200-block separation) must place and look
  in entity-ticking chunks only.

- **Hooking the split (RS1):** `SubLevelHeatMapManager.split` (l.200-251) runs inside `ServerSubLevel#tick` while `SableConfig.SUB_LEVEL_SPLITTING` holds; for each cut-off group it calls every `SplitListener.addBlocks(level, bounds, blocks)` (registered with the static `addSplitListener`, l.382) while the blocks are still in the parent's plot, then `SubLevelAssemblyHelper.assembleBlocks` (a pure translation of `blocks.get(0)` to the new plot's centre; `SubLevelContainer#allocateSubLevel` fires `SubLevelObserver#onSubLevelAdded`); the observers' `tick` runs after all sub-levels ticked at the head of `ServerLevel.tick`. The part that stays in the original body is the one holding the heat-map root, the first block ever added (`onSolidAdded`), or the largest if every block is cut off; assembly adds blocks in the given order (`moveBlocks`), so our assembler passes the helm first. After a reload the root is wherever Sable's chunk scan starts. Sable exposes splitting only as a global `BooleanValue` (`SableConfig` l.27-29), no per-sub-level flag.

- **Block count and body moves (HL1):** after a split that cuts every block off the heat-map root, `rebuildHeatmapFrom` resets `solidCount` to the largest group (l.224-227, l.254-266) and each later removal of the other groups' blocks lowers it again (`onSolidRemoved` l.328-331 from `SableCommonEvents` l.78-79), so the count is too low. At the next root loss the remaining blocks match neither the "whole" nor the "all pieces" branch (l.204, l.224) and Sable moves every block into a new sub-level. `SubLevelContainer#tick` (l.141-147) ticks the sub-levels, then `processSubLevelRemovals` (l.153-168) removes the emptied parent as REMOVED, and only then do the observers deliver the split: a removal listener must not drop per-ship state while a split of that ship is pending.

### 9.0k Physics timing under load, and the calm-basin flake (Q6, measured)
- **Sable's physics is a fixed step per server tick, not wall-clock time.** `SubLevelPhysicsSystem#tickPipelinePhysics`
  (l.250-298) runs `config.substepsPerTick` substeps (default 2, `SableServerConfig` l.15-17, `PhysicsConfigData` l.33-36)
  of exactly `1/20/substeps` s each, on the server thread, from `ServerLevelMixin#sable$tickPlotContainer` (l.80-90,
  skipped only when the vanilla `TickRateManager` is frozen). A slow server runs fewer ticks per second but the same
  physics per tick. The GameTest server does not even wait between ticks (`GameTestServer#waitUntilNextTick` only runs
  tasks), so "Can't keep up" never appears and test time is pure tick count. [V]
- The native solver is built with Rapier's `parallel` feature (`sable_rapier/src/main/rust/rapier/Cargo.toml` l.15-18)
  and `algo.rs` collects collision pairs with rayon: results are not bit-exact between runs, but the spread is tiny
  (the basin hull's speed at tick 40 differed in the 14th digit, its heel by 1e-5 degrees). [V]
- `hullAfloatInBasinStaysCalm` (5x4x5 plank hull in a still basin) gave the same trajectory in about 30 full-suite runs:
  alone, with three to six GameTest servers at once (load averages 45 to 265 on 8 cores, one run took 13.6 minutes),
  replayed at the exact positions of the three failing merge-stage runs, and with its plot freed by another ship in the
  same tick: bob peak 1.8 m/s at tick 10, 0.657 m/s at tick 40 (the first tick the limit checks), at rest from tick 140,
  heel below 1e-4 degrees. No force of ours breaks the symmetry: no container (CW1's cargo force needs one), sailing
  damping and keel act on angular and lateral motion only, the dry-volume lift is at the symmetric dry centroid. The
  hull's chunks were all loaded and entity-ticking from tick 0, and the assembler refills the footprint with water in
  the assembly tick, so fluid ticks play no part. [V]
- The three failures (9.17 degrees; 1.063 and 1.050 m/s) all came from merge-stage runs with six to nine GameTest
  servers plus Gradle builds, with 49 other tests in the same batch. The cause was not reproduced. What the hull is
  sensitive to was measured: water missing under two of its five columns for 30 ticks heels it 53 degrees, the
  footprint dry for 20 ticks makes it bob at 3.6 m/s after tick 40; a delay of the whole bob by about 20 ticks would
  give the observed 1.05 m/s at tick 40. The test now runs in its own batch and names every other ship and entity near
  its area, plus a trace, in its failure message, and it gained a rest check (from tick 140: below 0.05 m/s and
  0.5 degrees). [V for the measurements; the cause is open]

### 9.0l Slow drifts stall or overshoot well inside ±4,096 blocks (HZ1, measured)
- **The stall threshold is about 360 × one f32 step per second, not 20 × (§9.0e).** Rapier's world positions are
  `f32` (`marten/src/lib.rs` l.4, `rapier/src/lib.rs` l.657-668: the pose is cast to `Real`), and Sable runs it with
  `solverIterations = 18` per substep (`PhysicsConfigData` l.10, passed on by `RapierPhysicsPipeline` l.564) and 2
  substeps per tick. The measurements fit a position update 720 times a second that rounds to the nearest f32: a
  per-axis speed below about 360 × ulp(coordinate) per second is lost (0.088 m/s between 2,048 and 4,096 blocks, 0.18
  m/s up to 8,192, 0.35 m/s up to 16,384), and just above it the motion is rounded up to whole steps. [V for the
  measurements, I for the mechanism]
- Measured in `HazardGameTests.heavyShipMovesLessThanALightOne` (two hulls drifting at 0.07-0.15 m/s): the heavy hull's
  x stayed at exactly 2737.5 for 110 ticks while it reported -0.069 m/s; at x=1,616 it drifted 0.47 blocks and at x=202
  0.37 (ratio 1.27 = one step of 1.22e-4 over the 9.6e-5 the speed asks for); the light hull's x froze at 4,258.5 and the
  orbit then carried it 0.72 blocks *out* of the whirlpool. The reported velocity is unaffected (the same -0.069 m/s
  either way), and the velocity integrated over the window agreed across twelve runs at x/z from -3,517 to 4,258
  (light 3.08-3.13 blocks travelled; the pose gave 2.99-3.26).
- **Rule for tests:** a GameTest that measures a slow drift (below about 0.2 m/s on an axis) must not compare poses;
  integrate `ShipBody.linearVelocity()` over the window (pattern: `HazardGameTests.Track`) or assert on velocity
  (`SailingGameTestsControls`, anchor release). Tests that depend on the slow motion feeding back into the forces still
  see a small spread from where the grid put them.
- **Shared helper (PHY1):** `core/gametest/ShipTrack` integrates `ShipBody.linearVelocity()` per game tick (`follow(h,
  ship, centre, fromTick)`, `travel()`, `radialMove()`, the pose alongside for the log). Converted: the whirlpool tests
  (`smallShipIsPulledTowardTheCentre`, `heavyShipMovesLessThanALightOne`), the grapple rest checks
  (`withoutSneakTheRopePaysOutAndTheShipStays`, `hookOnTheThrowersOwnShipLatchesWithoutHauling`) and the one-block
  shore haul (`sneakingFreezesTheRopeAndBackingAwayHaulsTheShip`, about 0.2 m/s: at x of about -2,000 to -2,900 its pose
  read 6 % long, 1.003-1.007 blocks against 0.944-0.964 integrated). Heading, heel, trim and height measurements are not
  affected (orientation and y are not large coordinates), nor are hauls of five blocks and more at 0.3 m/s and faster.
  Not for bodies pressed together by a steady force (§9.0h).
- **Basin depth rule (PHY1):** a test ship's lowest block must stay more than a block above its basin's floor, or the
  test measures the seabed. Measure it with `SailingGameTestsShips.hullBottomY` against the floor's top face. The
  starter sloop's stern keel draws 4.27 blocks at 4.2 degrees bow up and clears the heel tests' 6-deep basin by 1.73
  (heel only lifts it; `SailingGameTestsHeel` asserts the margin); the AN2a test hull clears its basin by 3.66, the 7x17
  test hull its 40x40 basin by 4.13. [V]
- **For gameplay** the same holds in a real world: at 10,000 blocks from the origin a ship drifting slower than about
  0.35 m/s on an axis does not move along it (whirlpool pull at the rim, a hull settling, a kedge creeping), at 100,000
  blocks below about 2.8 m/s. Not ours to fix (it is Sable's native precision); worth a report upstream.
- The async hull re-analysis (`dry_hull.async_analysis`) is not a source of spread here: delaying every analysis by
  up to 0.4 s (results landing 9 to 53 ticks late instead of 1) left the measurements within the normal range. [V]

### 9.0m A Sable bug: a removed ship's blocks haunt the next ship in its plot (CW1b, verified by a deterministic test)
- `SubLevelContainer#removeSubLevel` (l.481-497) frees the plot at once and the next new sub-level gets it, but
  `RapierPhysicsPipeline#remove` (l.216) removes only the body: the plot's native chunk sections (`main_level_chunks`)
  and their physics tickets stay. Plot tickets expire only at a ticket update that finds the plot empty and the ticket
  older than 20 ticks (`PhysicsChunkTicketManager#expirePhysicsChunkTickets` l.258-276; plot chunks always count as
  loaded, `isChunkLoadedEnough` l.428-433). [V]
- A sub-level assembled into the plot before that sees the old tickets: `addSectionIfNotTracked` (l.346-353, from
  `SubLevelPhysicsSystem#handleBlockChange` l.459-475) skips the fresh upload and `Rapier3D.changeBlock` writes only the
  new blocks into the old sections. The removed ship's blocks stay wherever the new ship has air, as phantom voxels that
  float, drag and collide. [V]
- Effect: the first ship a GameTest assembled in its first tick often got a plot that another test of the batch freed
  in that tick. CW1's empty 5x4x5 hull trimmed 0.21 to 0.43 blocks instead of 0.05 and once stopped rising mid-bob,
  so `chestOfIronSinksAndTrimsTheShip` measured a sink anywhere from 0.05 to 0.99 instead of 0.66 (bound 0.4) in runs of the
  ship-heavy classes together; scoped runs of one class never showed it. Same-tick reuse after a removal in the previous
  tick is clean once the ship lived 20 ticks. Rule of thumb for flakes: if a ship misbehaves only in big batches,
  suspect what the batch did in the same tick, not load (physics is a fixed step per tick, §9.0k). [V]
- Workaround (`ship/sable/SableShips`): a leaving sub-level's plot sections are dropped from the pipeline in our removal
  observer (`PhysicsPipeline#handleChunkSectionRemoval`, observers run before `SubLevel#onRemove`, l.487-488), and every
  sub-level we assemble gets all its plot sections uploaded again with `handleChunkSectionAddition` plus
  `PhysicsChunkTicketManager#addTicketForSection` (l.355); sub-levels Sable creates by itself (splits, loads) within 40
  ticks of a removal get the same refresh at the container tick. Regression test:
  `AssemblyGameTests.plotFreedThisTickHasNoPhantomBlocks` (without the fix the hull's ends differ by 0.24 blocks, with
  it 0.0000). Worth reporting to the Sable project together with §9.0c. [V]

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

