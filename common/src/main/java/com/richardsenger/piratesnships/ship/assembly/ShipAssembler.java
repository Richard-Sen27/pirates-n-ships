package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult.Outcome;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side helm logic: assemble, disassemble, name. Policy lives here, Sable calls go through {@link SableShips}. */
public final class ShipAssembler {

    /** Key of our sub-tree in the sub-level's user data. */
    public static final String USER_DATA_KEY = Constants.MOD_ID;

    private ShipAssembler() {
    }

    /** Gathers the ship blocks connected to {@code helm} with our rule and the configured limit. */
    public static SableShips.Gathered gather(ServerLevel level, BlockPos helm) {
        return SableShips.gather(level, helm, AssemblyConfig.MAX_BLOCKS.get(), (pos, state) -> ShipBlockRule.isShipBlock(state));
    }

    public static AssemblyResult assemble(ServerLevel level, BlockPos helm, @Nullable Player player) {
        if (!AssemblyConfig.ENABLED.get()) {
            return AssemblyResult.of(Outcome.DISABLED);
        }
        SableShips.Gathered gathered = gather(level, helm);
        switch (gathered.state()) {
            case TOO_MANY_BLOCKS -> {
                return new AssemblyResult(Outcome.TOO_MANY_BLOCKS, AssemblyConfig.MAX_BLOCKS.get(), null, null, 0);
            }
            case NO_BLOCKS -> {
                return AssemblyResult.of(Outcome.NOTHING_TO_ASSEMBLE);
            }
            default -> {
            }
        }
        Set<BlockPos> blocks = gathered.blocks();
        if (blocks.size() <= 1) {
            return AssemblyResult.of(Outcome.NOTHING_TO_ASSEMBLE);
        }

        // Water rule input, taken before the blocks leave: the hull plus the empty or flooded cells it encloses.
        Set<BlockPos> refillCandidates = new HashSet<>(blocks);
        for (BlockPos p : HullWater.interiorCells(blocks)) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || ShipBlockRule.isWaterBlock(s)) {
                refillCandidates.add(p);
            }
        }

        ShipBody ship = SableShips.assemble(level, helm, blocks, gathered.min(), gathered.max());
        if (ship == null) {
            Constants.LOG.error("Sable did not create a sub-level for the helm at {}", helm);
            return AssemblyResult.of(Outcome.FAILED);
        }

        if (AssemblyConfig.RESTORE_SEA.get()) {
            for (BlockPos p : HullWater.seaRefill(refillCandidates, p -> ShipBlockRule.isWaterSource(level.getBlockState(p)))) {
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        ShipData data = ShipData.create(ship.id(), Optional.ofNullable(player).map(Player::getUUID), level.dimension().location());
        ShipRegistry.get(level.getServer()).put(data);
        CompoundTag pointer = new CompoundTag();
        pointer.putUUID("ship", data.id());
        pointer.putInt("version", 1);
        ship.setUserData(USER_DATA_KEY, pointer);
        HullRuntimes.onAssembled(ship);
        return new AssemblyResult(Outcome.ASSEMBLED, blocks.size(), ship.id(), null, 0);
    }

    /** Disassembles {@code ship}; {@code helmPlotPos} is the helm's position in the plot and becomes the anchor. */
    public static AssemblyResult disassemble(ShipBody ship, BlockPos helmPlotPos, @Nullable Player player) {
        if (!AssemblyConfig.DISASSEMBLY_ENABLED.get()) {
            return AssemblyResult.of(Outcome.DISABLED);
        }
        ServerLevel level = ship.level();
        double tilt = DisassemblyMath.tiltDegrees(ship.orientation());
        if (tilt > AssemblyConfig.MAX_TILT_DEGREES.get()) {
            return new AssemblyResult(Outcome.NOT_LEVEL, 0, null, null, tilt);
        }
        double speed = ship.linearVelocity().length();
        double spin = ship.angularVelocity().length();
        if (speed > AssemblyConfig.MAX_LINEAR_SPEED.get() || spin > AssemblyConfig.MAX_ANGULAR_SPEED.get()) {
            return new AssemblyResult(Outcome.MOVING, 0, null, null, speed);
        }

        int turns = DisassemblyMath.quarterTurns(ship.orientation());
        BlockPos goal = BlockPos.containing(ship.toWorld(Vec3.atCenterOf(helmPlotPos)));
        List<BlockPos> blocks = ship.plotBlocks();
        for (BlockPos b : blocks) {
            BlockPos t = DisassemblyMath.target(b, helmPlotPos, goal, turns);
            if (level.isOutsideBuildHeight(t)) {
                return new AssemblyResult(Outcome.OUT_OF_WORLD, 0, null, t, 0);
            }
            if (!ShipBlockRule.isFreeForShip(level.getBlockState(t))) {
                return new AssemblyResult(Outcome.OBSTRUCTED, 0, null, t, 0);
            }
        }

        List<BlockPos> drain = new ArrayList<>();
        for (BlockPos p : HullWater.interiorCells(new HashSet<>(blocks))) {
            drain.add(DisassemblyMath.target(p, helmPlotPos, goal, turns));
        }
        List<Passenger> passengers = AssemblyConfig.MOVE_ENTITIES.get() ? passengers(ship, helmPlotPos, goal, turns) : List.of();

        ShipRegistry.get(level.getServer()).remove(ship.id());
        SableShips.disassemble(ship, helmPlotPos, goal, turns, blocks);

        if (AssemblyConfig.DRAIN_HULL.get()) {
            for (BlockPos p : drain) {
                if (ShipBlockRule.isWaterBlock(level.getBlockState(p))) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (Passenger p : passengers) {
            p.place(level);
        }
        return new AssemblyResult(Outcome.DISASSEMBLED, blocks.size(), null, null, 0);
    }

    public static AssemblyResult name(ShipBody ship, String name) {
        ShipRegistry registry = ShipRegistry.get(ship.level().getServer());
        Optional<ShipData> data = registry.find(ship.id());
        if (data.isEmpty()) {
            return AssemblyResult.of(Outcome.NO_SHIP);
        }
        registry.put(data.get().withName(name));
        ship.setName(name);
        return AssemblyResult.of(Outcome.NAMED);
    }

    /** An entity standing on (or in) the ship and the world position matching its deck position after disassembly. */
    private record Passenger(Entity entity, Vec3 target) {
        void place(ServerLevel level) {
            if (entity.isRemoved()) {
                return;
            }
            if (entity instanceof ServerPlayer player) {
                player.teleportTo(level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
            } else {
                entity.teleportTo(target.x, target.y, target.z);
            }
            // Pose rounding can leave a deck passenger a hair below the deck top (y = n − 1e-7). Snapping to the block
            // top fixes that; lifting by a whole block (below) would leave it floating one block high.
            double top = Math.ceil(entity.getY());
            if (!level.noCollision(entity) && top - entity.getY() < 0.05) {
                entity.teleportTo(entity.getX(), top, entity.getZ());
            }
            // If the deck mapping put it inside a block (rounding), lift it out, at most three blocks.
            for (int i = 0; i < 3 && !level.noCollision(entity); i++) {
                entity.teleportTo(entity.getX(), Math.floor(entity.getY()) + 1, entity.getZ());
            }
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
        }
    }

    private static List<Passenger> passengers(ShipBody ship, BlockPos helmPlotPos, BlockPos goal, int turns) {
        BlockPos[] plot = ship.plotBounds();
        AABB plotBox = new AABB(Vec3.atLowerCornerOf(plot[0]), Vec3.atLowerCornerOf(plot[1]).add(1, 3, 1)).inflate(0.5, 0, 0.5);
        List<Passenger> out = new ArrayList<>();
        for (Entity e : ship.level().getEntities((Entity) null, ship.worldBounds().inflate(1, 3, 1),
                e -> !e.isSpectator() && !e.isPassenger())) {
            Vec3 local = ship.toPlot(e.position());
            if (plotBox.contains(local)) {
                out.add(new Passenger(e, DisassemblyMath.target(local, helmPlotPos, goal, turns)));
            }
        }
        return out;
    }
}
