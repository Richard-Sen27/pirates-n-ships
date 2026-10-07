package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.sail.StayRules;
import com.richardsenger.piratesnships.sailing.rope.RopeLines;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.sail.YardLinker;
import com.richardsenger.piratesnships.sailing.sail.YardLookup;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.HullDampingModel;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.sailing.helm.HelmBlockEntity;
import com.richardsenger.piratesnships.sailing.helm.HelmConfig;
import com.richardsenger.piratesnships.sailing.helm.WheelMath;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
        BlockPos helm = null;
        int rudder = 0;
        double wheel = 0.0;
        List<BlockPos> capstans = new ArrayList<>();
        for (BlockPos p : blocks) {
            b[0] = Math.min(b[0], p.getX()); b[1] = Math.min(b[1], p.getY()); b[2] = Math.min(b[2], p.getZ());
            b[3] = Math.max(b[3], p.getX()); b[4] = Math.max(b[4], p.getY()); b[5] = Math.max(b[5], p.getZ());
            BlockState s = level.getBlockState(p);
            if (helmFacing == null && s.is(AssemblyContent.HELM.get())) {
                helmFacing = s.getValue(HorizontalDirectionalBlock.FACING);
                helm = p.immutable();
                rudder = RudderSteps.fromProperty(s.getValue(HelmBlock.RUDDER));
                wheel = wheelOf(level, p);
            } else if (s.getBlock() instanceof CapstanBlock) {
                capstans.add(p.immutable());
            }
        }
        if (blocks.isEmpty()) {
            b = new int[6];
        }
        // HL1b: the ship record's steering helm drives the rudder; a ship that has recorded one but lost it is helmless
        // (a second helm waits until it is used); a ship from before HL1 (nothing recorded) keeps the first helm found
        BlockPos steering = ShipHelm.steering(ship);
        if (steering != null) {
            BlockState s = level.getBlockState(steering);
            helm = steering;
            rudder = RudderSteps.fromProperty(s.getValue(HelmBlock.RUDDER));
            wheel = wheelOf(level, steering);
        } else if (ship.userData(ShipAssembler.USER_DATA_KEY).contains(ShipHelm.TAG_HELM)) {
            helm = null;
            rudder = 0;
            wheel = 0.0;
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
            if (s.getBlock() instanceof CleatBlock) {
                rt.addCleat(p);
            } else if (s.getBlock() instanceof YardBlock) {
                rt.addYardBlock(p);
            }
        }
        relink(level, rt);
        rt.setHelm(helm, rudder);
        rt.setWheelAngle(wheel);
        ShipAnchor anchor = ShipControls.readAnchor(data);
        if (anchor != null && !capstans.contains(anchor.capstan())) {
            anchor = null; // the capstan is gone: the anchor went with it
            data.remove(ShipControls.ANCHOR_TAG);
            ship.setUserData(USER_DATA_KEY, data);
        }
        rt.setAnchor(anchor);
        // capstans that came back from land (disassembled with the anchor out) or lost their anchor show it stowed
        for (BlockPos c : capstans) {
            if (anchor == null || !anchor.capstan().equals(c)) {
                ShipControls.showPhase(level, c, AnchorState.Phase.RAISED);
            }
        }
        return rt;
    }

    private static double wheelOf(ServerLevel level, BlockPos helm) {
        return level.getBlockEntity(helm) instanceof HelmBlockEntity be ? be.wheel() : 0.0;
    }

    /**
     * Points the runtime at the helm at {@code helm} (plot) with its rudder step and wheel, or at no helm (rudder and
     * wheel midships) when {@code helm} is null or holds no helm.
     */
    private static void useHelm(ServerLevel level, SailingRuntime rt, @Nullable BlockPos helm) {
        BlockState s = helm == null ? null : level.getBlockState(helm);
        if (s == null || !(s.getBlock() instanceof HelmBlock)) {
            rt.setHelm(null, 0); // no helm, no rudder
            rt.setWheelAngle(0.0);
        } else {
            rt.setHelm(helm, RudderSteps.fromProperty(s.getValue(HelmBlock.RUDDER)));
            rt.setWheelAngle(wheelOf(level, helm));
        }
    }

    /**
     * HL1b: a helmless runtime takes the ship's steering helm once the ship has one again (a second helm claimed by its
     * first use, which with wheel steering changes no block). Cheap: only helmless runtimes look, and only at the ship
     * pointer and one block.
     */
    private static void followSteeringHelm(ServerLevel level, ShipBody ship, SailingRuntime rt) {
        if (rt.helm() == null) {
            BlockPos steering = ShipHelm.steering(ship);
            if (steering != null) {
                useHelm(level, rt, steering);
            }
        }
    }

    /**
     * Brings the sails of {@code ship}'s runtime up to date with its plot (Q5): reads every yard and cleat position
     * again, adds or drops those the runtime missed, and relinks both kinds of sails. The runtime is otherwise kept
     * current by block changes only, so a change it never heard of would leave it without sails that the cloth (which
     * re-checks the blocks by itself) still shows. Creates the runtime when there is none. Logs a warning when the
     * runtime was stale. Returns the runtime, or null when the ship is not one of ours.
     */
    public static @Nullable SailingRuntime refreshSails(ShipBody ship) {
        SailingRuntime rt = get(ship.level(), ship.id());
        if (rt == null) {
            return getOrCreate(ship); // a fresh scan
        }
        ServerLevel level = ship.level();
        java.util.Set<BlockPos> yards = new java.util.HashSet<>();
        java.util.Set<BlockPos> cleats = new java.util.HashSet<>();
        for (BlockPos p : ship.plotBlocks()) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof YardBlock) {
                yards.add(p);
            } else if (s.getBlock() instanceof CleatBlock) {
                cleats.add(p);
            }
        }
        int missed = 0;
        for (BlockPos p : rt.yardBlocks()) {
            if (!yards.contains(p) && rt.removeYardBlock(p)) missed++;
        }
        for (BlockPos p : yards) {
            if (rt.addYardBlock(p)) missed++;
        }
        for (BlockPos p : rt.cleatBlocks()) {
            if (!cleats.contains(p) && rt.removeCleat(p)) missed++;
        }
        for (BlockPos p : cleats) {
            if (rt.addCleat(p)) missed++;
        }
        List<BlockPos> before = rt.sailPositions();
        relink(level, rt);
        List<BlockPos> after = rt.sailPositions();
        if (missed > 0 || !before.equals(after)) {
            Constants.LOG.warn("Sailing state of ship {} was stale: {} yard or cleat positions missed, sails {} -> {}",
                    ship.id(), missed, before, after);
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
        boolean steering = SailingConfig.STEERING_ENABLED.get();
        int steps = SailingConfig.RUDDER_STEPS.get();
        boolean wheel = HelmConfig.DRAG_STEERING.get();
        double lock = HelmConfig.lockAngle();
        boolean anchors = SailingConfig.ANCHOR_ENABLED.get();
        for (SailingRuntime rt : List.copyOf(m.values())) {
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship != null) {
                followSteeringHelm(level, ship, rt);
                WindSample w = WindService.sample(level, ship.worldBounds().getCenter());
                rt.setTickInputs(now, w, params);
                // HELM1: with wheel steering the rudder follows the helm wheel, else the click steps of the block state
                double rudder = !steering ? 0.0 : wheel ? WheelMath.rudderAngle(rt.wheelAngle(), lock, params.maxRudderAngleDeg())
                        : RudderSteps.angle(rt.rudderStep(), steps, params.maxRudderAngleDeg());
                rt.setControlInputs(rudder, anchors);
                ShipControls.tickAnchor(ship, rt, params.anchor());
            }
        }
    }

    /** Physics substep: applies sail and keel forces and the hull damping to every ship with a runtime. */
    public static void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, SailingRuntime> m = SERVER.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        boolean forces = SailingConfig.FORCES_ENABLED.get();
        HullDampingModel.Params damping = SailingConfig.hullDampingParams();
        if (!forces && !damping.enabled()) {
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
                // a wreck (a loose piece of a split ship, RS1) gets no sail, keel or rudder force, only the damping
                boolean shipForces = forces && !com.richardsenger.piratesnships.ship.assembly.ShipSplits.isWreck(ship);
                rt.physicsTick(ship, sea, timeStep, now, fullDraft, needWater, heel, shipForces, damping);
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
        boolean oldCleat = oldState.getBlock() instanceof CleatBlock;
        boolean newCleat = newState.getBlock() instanceof CleatBlock;
        boolean oldYard = oldState.getBlock() instanceof YardBlock;
        boolean newYard = newState.getBlock() instanceof YardBlock;
        boolean helm = oldState.getBlock() instanceof HelmBlock || newState.getBlock() instanceof HelmBlock;
        boolean capstanGone = oldState.getBlock() instanceof CapstanBlock && !(newState.getBlock() instanceof CapstanBlock);
        boolean mastChanged = oldState.is(SailingBlocks.MASTS) != newState.is(SailingBlocks.MASTS);
        if (!oldCleat && !newCleat && !oldYard && !newYard && !helm && !capstanGone && !mastChanged
                && oldState.isAir() == newState.isAir()) {
            // only cleats, yards, the helm, a removed capstan, masts, and cells that were filled (the plot box may grow)
            // or cleared (a square sail's gap may open) matter
            return;
        }
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : m.get(ship.id());
        if (rt == null) {
            return;
        }
        if (newState.getBlock() instanceof HelmBlock) {
            // HL1b: the runtime's own helm turned its rudder, or a helm came to a runtime without a (standing) helm; a
            // second helm placed beside the steering one changes nothing
            BlockPos current = rt.helm();
            if (pos.equals(current)) {
                rt.setHelm(pos, RudderSteps.fromProperty(newState.getValue(HelmBlock.RUDDER)));
            } else if (current == null || !(level.getBlockState(current).getBlock() instanceof HelmBlock)) {
                useHelm(level, rt, pos);
            }
        } else if (oldState.getBlock() instanceof HelmBlock && pos.equals(rt.helm())) {
            // HL1b: the ship record's steering helm takes over if another one stands, else the ship is helmless
            BlockPos steering = ShipHelm.steering(ship);
            useHelm(level, rt, pos.equals(steering) ? null : steering);
        }
        ShipAnchor anchor = rt.anchor();
        if (capstanGone && anchor != null && anchor.capstan().equals(pos)) {
            ShipControls.setAnchor(ship, rt, null); // the anchor is lost with its capstan
        }
        if (oldYard && newYard && oldState.getValue(YardBlock.AXIS) == newState.getValue(YardBlock.AXIS)) {
            rt.setTrim(pos, newState.getValue(YardBlock.TRIM)); // a trim change; only a sail's head counts
            return;
        }
        if (oldCleat && newCleat) {
            rt.setTrim(pos, newState.getValue(CleatBlock.TRIM)); // a trim change; only a sail's head counts
            return;
        }
        if (!newState.isAir()) {
            rt.include(pos);
        }
        boolean yardsChanged = false;
        if (oldYard) {
            yardsChanged = rt.removeYardBlock(pos);
        }
        if (newYard) {
            yardsChanged |= rt.addYardBlock(pos);
        }
        if (oldCleat) {
            yardsChanged |= rt.removeCleat(pos);
        }
        if (newCleat) {
            yardsChanged |= rt.addCleat(pos);
        }
        if (yardsChanged || rt.watchesGap(pos)) {
            relink(level, rt);
        }
    }

    /**
     * A stay was rigged or dropped at {@code pos} without a block change (the rope only writes block entities):
     * relinks the sails of the ship containing {@code pos}, if it has a runtime.
     */
    public static void onRigChanged(ServerLevel level, BlockPos pos) {
        Map<UUID, SailingRuntime> m = SERVER.get(level);
        ShipBody ship = m == null || m.isEmpty() ? null : SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : m.get(ship.id());
        if (rt != null) {
            relink(level, rt);
        }
    }

    /**
     * Pairs the ship's yards again (rule F5a) and replaces its square sails, keeping each head's trim from its block
     * state, and brings the cloth of the yard block entities up to date.
     */
    static void relink(ServerLevel level, SailingRuntime rt) {
        YardRules rules = SailingConfig.yardRules();
        YardLookup lookup = YardSails.lookup(level);
        List<YardRow> before = rt.yards();
        List<int[]> blocks = new ArrayList<>();
        for (BlockPos p : rt.yardBlocks()) {
            blocks.add(new int[] {p.getX(), p.getY(), p.getZ()});
        }
        YardLinker.Linked linked = YardLinker.link(lookup, blocks, rules);
        rt.replaceSquareSails(linked, head -> {
            BlockState s = level.getBlockState(head);
            return s.getBlock() instanceof YardBlock ? s.getValue(YardBlock.TRIM) : SailTrim.FURLED;
        }, rules.maxGap());
        // cloth display: clear the yards as they were, then write the current ones
        for (YardRow r : before) {
            if (!linked.rows().contains(r)) {
                for (int a = r.min(); a <= r.max(); a++) {
                    if (level.getBlockEntity(new BlockPos(r.xAt(a), r.y(), r.zAt(a))) instanceof YardBlockEntity be) {
                        be.setGeometry(null);
                    }
                }
            }
        }
        for (YardRow r : linked.rows()) {
            YardSails.refreshRow(level, lookup, r, rules);
        }
        relinkTriangles(level, rt);
    }

    /**
     * Finds the ship's triangular sails again (rule F5b): every cleat that is the higher end of a valid stay is a head;
     * with a clew below it, it heads a sail. Brings the cloth of the cleats' block entities up to date.
     */
    private static void relinkTriangles(ServerLevel level, SailingRuntime rt) {
        StayRules rules = SailingConfig.stayRules();
        List<TriangularSail> sails = new ArrayList<>();
        List<int[]> columns = new ArrayList<>();
        for (BlockPos p : rt.cleatBlocks()) {
            boolean head = false;
            for (BlockPos partner : RopeLines.partners(level, p)) { // RP1: a cleat may hold several ropes
                if (partner.getY() < p.getY() && RopeLines.isCleat(level, partner)) {
                    columns.add(new int[] {p.getX(), p.getZ(), partner.getY(), p.getY() - 1});
                    head = true;
                }
            }
            if (head) {
                TriangularSail t = TriangularSails.sailHeadedAt(level, p, rules);
                if (t != null) sails.add(t);
            }
            TriangularSails.refresh(level, p);
        }
        rt.replaceTriangularSails(sails, head -> {
            BlockState s = level.getBlockState(head);
            return s.getBlock() instanceof CleatBlock ? s.getValue(CleatBlock.TRIM) : SailTrim.FURLED;
        }, columns);
    }

    /**
     * Sets the trim of the sail at {@code sailPos} (a position from {@link SailingRuntime#sailPositions()}): on every
     * block of a square sail's upper yard, or on a triangular sail's head cleat. The block changes update the runtime.
     * False when there is no yard or cleat at {@code sailPos}.
     */
    public static boolean setTrim(ServerLevel level, BlockPos sailPos, SailTrim trim) {
        BlockState s = level.getBlockState(sailPos);
        if (s.getBlock() instanceof YardBlock) {
            return YardSails.setTrim(level, sailPos, trim);
        }
        if (s.getBlock() instanceof CleatBlock) {
            return TriangularSails.setTrim(level, sailPos, trim);
        }
        return false;
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
        if (sails.isEmpty()) {
            rt = refreshSails(ship); // Q5: read the rigging again before answering "no sails"
            sails = rt == null ? List.of() : rt.sailPositions();
        }
        SailTrim highest = SailTrim.FURLED;
        for (BlockPos p : sails) {
            SailTrim t = rt.trimAt(p);
            if (t != null && t.ordinal() > highest.ordinal()) highest = t;
        }
        SailTrim next = highest.next();
        for (BlockPos p : sails) {
            setTrim(level, p, next); // the block changes update rt
        }
        return new CycleResult(next, sails.size());
    }
}
