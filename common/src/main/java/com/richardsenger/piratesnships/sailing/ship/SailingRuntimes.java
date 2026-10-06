package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailBlock;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * All {@link SailingRuntime}s of the server, per level. A runtime is created by one plot scan when a ship is found
 * (every {@code scan_interval_ticks}, or at once when its winch is used), which covers freshly assembled and freshly
 * loaded ships alike; block changes keep it current; Sable's removal callback drops it.
 *
 * <p>The bow is stored in the ship's Sable user data ({@code pirates_n_ships_sailing.bow}) the first time it is derived
 * from the helm, so it survives save and reload and even a broken helm.
 */
public final class SailingRuntimes {

    public static final String USER_DATA_KEY = Constants.MOD_ID + "_sailing";

    private static final Map<ServerLevel, Map<UUID, SailingRuntime>> SERVER = new IdentityHashMap<>();

    private SailingRuntimes() {
    }

    public static @Nullable SailingRuntime get(ServerLevel level, UUID ship) {
        Map<UUID, SailingRuntime> m = SERVER.get(level);
        return m == null ? null : m.get(ship);
    }

    /** The runtime of {@code ship}, scanning it now if there is none yet. Null if the ship is not one of ours. */
    public static @Nullable SailingRuntime getOrCreate(ShipBody ship) {
        SailingRuntime rt = get(ship.level(), ship.id());
        if (rt != null) {
            return rt;
        }
        if (!ship.userData(ShipAssembler.USER_DATA_KEY).hasUUID("ship")) {
            return null;
        }
        rt = scan(ship);
        SERVER.computeIfAbsent(ship.level(), l -> new HashMap<>()).put(ship.id(), rt);
        return rt;
    }

    /** One full plot scan: sails, helm (for the bow) and extent. */
    static SailingRuntime scan(ShipBody ship) {
        ServerLevel level = ship.level();
        List<BlockPos> blocks = ship.plotBlocks();
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        Direction helmFacing = null;
        for (BlockPos p : blocks) {
            b[0] = Math.min(b[0], p.getX()); b[1] = Math.min(b[1], p.getY()); b[2] = Math.min(b[2], p.getZ());
            b[3] = Math.max(b[3], p.getX()); b[4] = Math.max(b[4], p.getY()); b[5] = Math.max(b[5], p.getZ());
            if (helmFacing == null) {
                BlockState s = level.getBlockState(p);
                if (s.is(AssemblyContent.HELM.get())) {
                    helmFacing = s.getValue(HorizontalDirectionalBlock.FACING);
                }
            }
        }
        if (blocks.isEmpty()) {
            b = new int[6];
        }
        CompoundTag data = ship.userData(USER_DATA_KEY);
        BowFrame bow;
        if (data.contains("bow")) {
            bow = BowFrame.byName(data.getString("bow"));
        } else {
            bow = helmFacing == null ? BowFrame.SOUTH : BowFrame.fromHelmFacing(helmFacing.getStepX(), helmFacing.getStepZ());
            data.putString("bow", bow.name());
            ship.setUserData(USER_DATA_KEY, data);
        }
        SailingRuntime rt = new SailingRuntime(ship.id(), bow, b);
        for (BlockPos p : blocks) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof SailBlock sail) {
                rt.putSail(p, sail.type(), s.getValue(SailBlock.TRIM));
            }
        }
        return rt;
    }

    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        Map<UUID, SailingRuntime> m = SERVER.get(level);
        if (m != null) {
            m.remove(ship);
        }
    }

    public static void onServerStopped() {
        SERVER.clear();
        WindOverride.clearAll();
    }

    /** Level tick: discovery of ships without a runtime, and the once-per-tick wind sample and config read. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % SailingConfig.SCAN_INTERVAL.get() == 0) {
            ShipRegistry registry = ShipRegistry.get(level.getServer());
            for (ShipBody ship : SableShips.all(level)) {
                if (!ship.isRemoved() && get(level, ship.id()) == null && registry.find(ship.id()).isPresent()) {
                    getOrCreate(ship);
                }
            }
        }
        Map<UUID, SailingRuntime> m = SERVER.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        SailingParams params = SailingConfig.sailingParams();
        long now = level.getGameTime();
        for (SailingRuntime rt : m.values()) {
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship != null) {
                WindSample w = WindService.sample(level, ship.worldBounds().getCenter());
                rt.setTickInputs(now, w, params);
            }
        }
    }

    /** Physics substep: applies sail and keel forces to every ship with a runtime. */
    public static void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, SailingRuntime> m = SERVER.get(level);
        if (m == null || m.isEmpty() || !SailingConfig.FORCES_ENABLED.get()) {
            return;
        }
        double fullDraft = SailingConfig.FULL_DRAFT.get();
        boolean needWater = SailingConfig.SAILS_NEED_WATER.get();
        double heel = SailingConfig.SAIL_HEEL_FACTOR.get();
        long now = level.getGameTime();
        for (SailingRuntime rt : m.values()) {
            if (rt.windTime() == Long.MIN_VALUE) {
                continue; // no wind sampled yet
            }
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship == null) {
                continue;
            }
            HullRuntime hull = HullRuntimes.get(level, rt.id());
            double sea = hull == null || !hull.seesSea() ? Double.NaN : hull.seaWorldY();
            try {
                rt.physicsTick(ship, sea, timeStep, now, fullDraft, needWater, heel);
            } catch (RuntimeException e) {
                Constants.LOG.error("Sailing forces of ship {} failed", rt.id(), e);
            }
        }
    }

    /** From {@link com.richardsenger.piratesnships.ship.ShipBlockChanges}: keeps sail lists current. */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        Map<UUID, SailingRuntime> m = SERVER.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        boolean oldSail = oldState.getBlock() instanceof SailBlock;
        boolean newSail = newState.getBlock() instanceof SailBlock;
        if (!oldSail && !newSail && (newState.isAir() || !oldState.isAir())) {
            return; // only sails and newly filled cells (the plot box may grow) matter
        }
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : m.get(ship.id());
        if (rt == null) {
            return;
        }
        if (newState.getBlock() instanceof SailBlock sail) {
            rt.putSail(pos, sail.type(), newState.getValue(SailBlock.TRIM));
        } else {
            if (oldSail) {
                rt.removeSail(pos);
            }
            if (!newState.isAir()) {
                rt.include(pos);
            }
        }
    }

    /** Result of a winch use: the trim set and how many sails got it. */
    public record CycleResult(SailTrim trim, int sails) { }

    /**
     * Cycles the trim of all sails of the ship containing {@code plotPos} (see {@code SailWinchBlock}). Null when the
     * position is not on one of our ships.
     */
    public static @Nullable CycleResult cycleTrim(ServerLevel level, BlockPos plotPos) {
        ShipBody ship = SableShips.containing(level, plotPos);
        SailingRuntime rt = ship == null ? null : getOrCreate(ship);
        if (rt == null) {
            return null;
        }
        List<BlockPos> sails = rt.sailPositions();
        SailTrim highest = SailTrim.FURLED;
        for (BlockPos p : sails) {
            SailTrim t = rt.trimAt(p);
            if (t != null && t.ordinal() > highest.ordinal()) highest = t;
        }
        SailTrim next = highest.next();
        for (BlockPos p : sails) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof SailBlock) {
                level.setBlock(p, s.setValue(SailBlock.TRIM, next), Block.UPDATE_ALL); // the block change updates rt
            }
        }
        return new CycleResult(next, sails.size());
    }
}
