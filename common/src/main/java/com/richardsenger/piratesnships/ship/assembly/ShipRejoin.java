package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rejoining a split-off piece to its ship with the Shipwright's Toolkit (RS2). Two clicks, never automatic:
 * <ol>
 *   <li>{@link #mark}: sneak-use on a block of the piece to keep (normally the helm piece). The toolkit remembers it
 *       ({@link RejoinMark}); while a player holds it, the marked piece's seam (its blocks near other pieces of the same
 *       ship) shows particles.</li>
 *   <li>{@link #use}: use on a block of the other piece. The checks of {@link RejoinRules} run (same origin, size,
 *       alignment, contact, no overlap, nails); on success a second of hammering starts, then the checks run again and
 *       the absorbed piece's blocks move into the keeper's plot at the snapped positions ({@link RejoinTransform},
 *       {@link SableShips#moveBlocksBetween}). The absorbed body and its record go; entities standing on it, riding in
 *       it or living in its plot move along; crew stations and grappling hooks follow their blocks ({@link #relocate},
 *       {@link #onRejoin}). The keeper keeps its identity.</li>
 * </ol>
 * The hull runtime and the rigging of the keeper update from the block changes (ship block change listeners and the
 * new block entities' first tick), as after any block placed on a ship.
 */
public final class ShipRejoin {

    /** What a click or a finished rejoin did. */
    public enum Outcome {
        MARKED, STARTED, REJOINED,
        DISABLED, NOT_A_SHIP, NO_MARK, SAME_PIECE, MARK_GONE, BUSY, NO_TOOLKIT,
        DIFFERENT_SHIP, TOO_BIG, NOT_ALIGNED, ADD_PLANKS, TOO_FAR, OVERLAP, OUT_OF_PLOT, NO_NAILS, FAILED;

        public String key() {
            return "message." + Constants.MOD_ID + ".rejoin." + name().toLowerCase(Locale.ROOT);
        }

        public boolean refused() {
            return this != MARKED && this != STARTED && this != REJOINED;
        }

        static Outcome of(RejoinRules.Check check) {
            return switch (check) {
                case DIFFERENT_SHIP -> DIFFERENT_SHIP;
                case TOO_BIG -> TOO_BIG;
                case NOT_ALIGNED -> NOT_ALIGNED;
                case ADD_PLANKS -> ADD_PLANKS;
                case TOO_FAR -> TOO_FAR;
                case OVERLAP -> OVERLAP;
                case NO_NAILS -> NO_NAILS;
                case OK -> STARTED;
            };
        }
    }

    /** A click's or a rejoin's outcome with the arguments of its message. */
    public record Result(Outcome outcome, Object... args) {
        public Component message() {
            return Component.translatable(outcome.key(), args);
        }

        @Override
        public String toString() {
            return outcome + java.util.Arrays.toString(args);
        }
    }

    /** A rejoin that happened: the keeper's plot now holds the absorbed piece's blocks, moved by {@code transform}. */
    public record Rejoined(ServerLevel level, UUID keeper, UUID absorbed, RejoinTransform transform, int blocks) { }

    @FunctionalInterface
    public interface Listener {
        void onRejoin(Rejoined event);
    }

    /** A checked rejoin, ready to run. */
    private record Plan(ShipBody keeper, ShipBody absorbed, List<BlockPos> blocks, RejoinTransform transform) { }

    /** Either a refusal or a plan. */
    private record Evaluation(@Nullable Result refusal, @Nullable Plan plan) { }

    /** A second click that is being hammered in. */
    private record Job(Player player, InteractionHand hand, UUID keeper, UUID absorbed, Vec3 soundAt, long started, long due) { }

    private record Remembered(Rejoined event, long until) { }

    private record Seam(List<BlockPos> plotBlocks, long until) { }

    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Map<ServerLevel, List<Job>> JOBS = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Remembered>> RECENT = new IdentityHashMap<>();
    private static final Map<UUID, Seam> SEAMS = new HashMap<>();
    private static final Map<UUID, Result> LAST = new HashMap<>();
    private static final int SEAM_MAX = 24;
    private static final int SEAM_PERIOD = 10;

    private ShipRejoin() {
    }

    /** Listens to every finished rejoin (server thread). */
    public static synchronized void onRejoin(Listener listener) {
        LISTENERS.add(listener);
    }

    // ---------------------------------------------------------------- clicks

    /** First click: marks the piece holding {@code clickedPlot} as the keeper on {@code toolkit}. */
    public static Result mark(ServerLevel level, Player player, ItemStack toolkit, BlockPos clickedPlot) {
        if (!AssemblyConfig.REJOIN_ENABLED.get()) {
            return new Result(Outcome.DISABLED);
        }
        ShipBody ship = SableShips.containing(level, clickedPlot);
        Optional<ShipData> data = ship == null ? Optional.empty() : ShipRegistry.get(level.getServer()).find(ship.id());
        if (data.isEmpty()) {
            return new Result(Outcome.NOT_A_SHIP);
        }
        String label = label(ship, data.get());
        toolkit.set(AssemblyContent.REJOIN_MARK.get(), new RejoinMark(ship.id(), label));
        SEAMS.remove(ship.id());
        return new Result(Outcome.MARKED, label.isEmpty() ? "-" : label);
    }

    /**
     * Second click: checks that the piece holding {@code clickedPlot} can join the marked piece and starts the
     * hammering. The rejoin itself happens {@code assembly.rejoin.work_ticks} later ({@link #onLevelTick}), after the
     * checks ran again; its outcome is reported to the player then (and kept for {@link #lastResult}).
     */
    public static Result use(ServerLevel level, Player player, InteractionHand hand, BlockPos clickedPlot) {
        if (!AssemblyConfig.REJOIN_ENABLED.get()) {
            return new Result(Outcome.DISABLED);
        }
        ItemStack toolkit = player.getItemInHand(hand);
        ShipBody absorbed = SableShips.containing(level, clickedPlot);
        if (absorbed == null || ShipRegistry.get(level.getServer()).find(absorbed.id()).isEmpty()) {
            return new Result(Outcome.NOT_A_SHIP);
        }
        RejoinMark mark = toolkit.get(AssemblyContent.REJOIN_MARK.get());
        if (mark == null) {
            return new Result(Outcome.NO_MARK);
        }
        if (mark.ship().equals(absorbed.id())) {
            return new Result(Outcome.SAME_PIECE);
        }
        ShipBody keeper = SableShips.byId(level, mark.ship());
        if (keeper == null || ShipRegistry.get(level.getServer()).find(keeper.id()).isEmpty()) {
            toolkit.remove(AssemblyContent.REJOIN_MARK.get());
            return new Result(Outcome.MARK_GONE);
        }
        List<Job> jobs = JOBS.computeIfAbsent(level, l -> new ArrayList<>());
        for (Job j : jobs) {
            if (j.player() == player || j.absorbed().equals(absorbed.id()) || j.keeper().equals(absorbed.id())) {
                return new Result(Outcome.BUSY);
            }
        }
        Evaluation e = evaluate(level, keeper, absorbed, player);
        if (e.refusal() != null) {
            return e.refusal();
        }
        long now = level.getGameTime();
        Vec3 at = absorbed.toWorld(Vec3.atCenterOf(clickedPlot));
        jobs.add(new Job(player, hand, keeper.id(), absorbed.id(), at, now, now + AssemblyConfig.REJOIN_WORK_TICKS.get()));
        LAST.remove(player.getUUID());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 1.0f, 0.9f);
        return new Result(Outcome.STARTED, e.plan().blocks().size());
    }

    /** The outcome of the player's last finished (or failed) hammering, null while it runs or if there was none. */
    public static @Nullable Result lastResult(UUID player) {
        return LAST.get(player);
    }

    /** True while a rejoin of {@code player} is being hammered in. */
    public static boolean working(ServerLevel level, Player player) {
        List<Job> jobs = JOBS.get(level);
        return jobs != null && jobs.stream().anyMatch(j -> j.player() == player);
    }

    // ---------------------------------------------------------------- checks

    private static Evaluation evaluate(ServerLevel level, ShipBody keeper, ShipBody absorbed, Player player) {
        UUID keeperOrigin = ShipSplits.lineage(keeper).origin();
        UUID absorbedOrigin = ShipSplits.lineage(absorbed).origin();
        List<BlockPos> blocks = absorbed.plotBlocks();
        if (blocks.isEmpty()) {
            return new Evaluation(new Result(Outcome.NOT_A_SHIP), null);
        }
        int maxBlocks = AssemblyConfig.REJOIN_MAX_PIECE_BLOCKS.get();
        double maxAngle = AssemblyConfig.REJOIN_MAX_ANGLE.get();
        double maxGap = AssemblyConfig.REJOIN_MAX_GAP.get();
        int cost = AssemblyConfig.NAILS_PER_REJOIN.get();
        boolean creative = player.hasInfiniteMaterials();
        RejoinTransform.Fit[] fit = new RejoinTransform.Fit[1];
        List<BlockPos> snapped = new ArrayList<>(blocks.size());
        RejoinRules.Check check = RejoinRules.check(keeperOrigin, absorbedOrigin, blocks.size(), maxBlocks,
                () -> fit[0] = fit(keeper, absorbed, blocks), maxAngle, maxGap,
                () -> {
                    for (BlockPos p : blocks) {
                        snapped.add(fit[0].transform().apply(p));
                    }
                    return RejoinRules.contact(new HashSet<>(keeper.plotBlocks()), snapped);
                },
                creative ? Integer.MAX_VALUE : countNails(player), cost);
        Result refusal = switch (check) {
            case OK -> null;
            case TOO_BIG -> new Result(Outcome.TOO_BIG, blocks.size(), maxBlocks);
            case NOT_ALIGNED -> new Result(Outcome.NOT_ALIGNED, String.format(Locale.ROOT, "%.0f", maxAngle),
                    String.format(Locale.ROOT, "%.1f", maxGap));
            case NO_NAILS -> new Result(Outcome.NO_NAILS, cost);
            default -> new Result(Outcome.of(check));
        };
        if (refusal != null) {
            return new Evaluation(refusal, null);
        }
        for (BlockPos s : snapped) {
            if (!keeper.plotContains(s) || level.isOutsideBuildHeight(s)) {
                return new Evaluation(new Result(Outcome.OUT_OF_PLOT), null);
            }
        }
        return new Evaluation(null, new Plan(keeper, absorbed, blocks, fit[0].transform()));
    }

    /** Where the absorbed piece's blocks are now in the keeper's plot, snapped onto its grid. */
    static RejoinTransform.Fit fit(ShipBody keeper, ShipBody absorbed, List<BlockPos> blocks) {
        List<Vec3> exact = new ArrayList<>(blocks.size());
        for (BlockPos p : blocks) {
            exact.add(keeper.toPlot(absorbed.toWorld(Vec3.atCenterOf(p))));
        }
        return RejoinTransform.fit(RejoinTransform.relative(keeper.orientation(), absorbed.orientation()), blocks, exact);
    }

    private static int countNails(Player player) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(AssemblyContent.NAILS.get())) {
                n += s.getCount();
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(AssemblyContent.NAILS.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void takeNails(Player player, int count) {
        int left = count;
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        for (ItemStack s : all) {
            if (left <= 0) {
                return;
            }
            if (s.is(AssemblyContent.NAILS.get())) {
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
    }

    private static String label(ShipBody ship, ShipData data) {
        if (!data.name().isEmpty()) {
            return data.name();
        }
        ShipSplits.Lineage l = ShipSplits.lineage(ship);
        return l.wreck() && !l.wreckOf().isEmpty() ? "Wreck of " + l.wreckOf() : "";
    }

    // ---------------------------------------------------------------- the rejoin

    private static Result execute(ServerLevel level, Job job) {
        Player player = job.player();
        if (!AssemblyConfig.REJOIN_ENABLED.get()) {
            return new Result(Outcome.DISABLED);
        }
        ItemStack toolkit = player.getItemInHand(job.hand());
        if (!(toolkit.getItem() instanceof ShipwrightToolkitItem)) {
            return new Result(Outcome.NO_TOOLKIT);
        }
        ShipBody keeper = SableShips.byId(level, job.keeper());
        ShipBody absorbed = SableShips.byId(level, job.absorbed());
        if (keeper == null) {
            return new Result(Outcome.MARK_GONE);
        }
        if (absorbed == null) {
            return new Result(Outcome.NOT_A_SHIP);
        }
        Evaluation e = evaluate(level, keeper, absorbed, player);
        if (e.refusal() != null) {
            return e.refusal();
        }
        Plan plan = e.plan();
        perform(level, plan);
        takeNails(player, RejoinRules.nailsToTake(AssemblyConfig.NAILS_PER_REJOIN.get(), player.hasInfiniteMaterials()));
        ShipwrightToolkitItem.syncDurability(toolkit);
        toolkit.hurtAndBreak(1, player, LivingEntity.getSlotForHand(job.hand()));
        return new Result(Outcome.REJOINED, plan.blocks().size());
    }

    private static void perform(ServerLevel level, Plan plan) {
        ShipBody keeper = plan.keeper();
        ShipBody absorbed = plan.absorbed();
        RejoinTransform t = plan.transform();
        ChunkCacheGuard.flush(level);
        List<Mover> movers = movers(level, keeper, absorbed, t);
        for (Mover m : movers) {
            if (m.entity().isPassenger()) {
                m.entity().stopRiding(); // the station seat goes with its block; the crew re-seats at the new place
            }
        }
        SableShips.moveBlocksBetween(absorbed, keeper, t.anchor(), t.target(), t.turns(), plan.blocks());
        for (Mover m : movers) {
            m.place(level, keeper);
        }
        UUID absorbedId = absorbed.id();
        ShipRegistry.get(level.getServer()).remove(absorbedId);
        if (!absorbed.isRemoved()) {
            SableShips.remove(absorbed);
        }
        ChunkCacheGuard.flush(level);
        SEAMS.remove(keeper.id());
        Rejoined event = new Rejoined(level, keeper.id(), absorbedId, t, plan.blocks().size());
        RECENT.computeIfAbsent(level, l -> new HashMap<>())
                .put(absorbedId, new Remembered(event, level.getGameTime() + ShipSplits.RELOCATION_MEMORY_TICKS));
        Constants.LOG.debug("Ship {} rejoined into {}: {} blocks, {} quarter turns", absorbedId, keeper.id(), plan.blocks().size(), t.turns());
        for (Listener l : List.copyOf(LISTENERS)) {
            try {
                l.onRejoin(event);
            } catch (RuntimeException ex) {
                Constants.LOG.error("Rejoin listener failed", ex);
            }
        }
    }

    /**
     * An entity that moves with the absorbed piece: {@code plotTarget} is its new position in the keeper's plot;
     * {@code inPlot} entities (retained in the plot: seats, item frames) stay in plot space, the others are placed at the
     * matching world position.
     */
    private record Mover(Entity entity, Vec3 plotTarget, boolean inPlot) {
        void place(ServerLevel level, ShipBody keeper) {
            if (entity.isRemoved()) {
                return;
            }
            if (inPlot) {
                entity.setPos(plotTarget);
                return;
            }
            Vec3 w = keeper.toWorld(plotTarget);
            if (entity instanceof ServerPlayer player) {
                player.teleportTo(level, w.x, w.y, w.z, player.getYRot(), player.getXRot());
            } else {
                entity.teleportTo(w.x, w.y, w.z);
            }
            // lift out of the deck if the snap put it a hair (or a block) into it, at most three blocks
            for (int i = 0; i < 3 && !level.noCollision(entity); i++) {
                entity.teleportTo(entity.getX(), Math.floor(entity.getY()) + 1, entity.getZ());
            }
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
        }
    }

    private static List<Mover> movers(ServerLevel level, ShipBody keeper, ShipBody absorbed, RejoinTransform t) {
        BlockPos[] plot = absorbed.plotBounds();
        AABB plotBox = new AABB(Vec3.atLowerCornerOf(plot[0]), Vec3.atLowerCornerOf(plot[1]).add(1, 3, 1)).inflate(0.5, 0, 0.5);
        List<Mover> out = new ArrayList<>();
        Set<Entity> seen = new HashSet<>();
        // standing on deck (world space): tracked by the piece or inside its plot box seen from its frame
        for (Entity e : level.getEntities((Entity) null, absorbed.worldBounds().inflate(1, 3, 1), e -> !e.isSpectator())) {
            if (e.isPassenger()) {
                continue;
            }
            ShipBody on = ShipEntities.standingOrRiding(e);
            if (on != null && on.id().equals(keeper.id())) {
                continue; // on the keeper next to the seam
            }
            Vec3 local = absorbed.toPlot(e.position());
            if (plotBox.contains(local) && seen.add(e)) {
                out.add(new Mover(e, t.apply(local), false));
            }
        }
        // living in the plot (seats, frames, retained entities); their riders get off and stand where the vehicle was
        for (Entity e : level.getEntities((Entity) null, plotBox.inflate(1), e -> !e.isSpectator())) {
            if (!seen.add(e)) {
                continue;
            }
            if (e.isPassenger()) {
                Entity vehicle = e.getVehicle();
                if (vehicle != null && plotBox.contains(vehicle.position())) {
                    out.add(new Mover(e, t.apply(vehicle.position()), false));
                }
            } else {
                out.add(new Mover(e, t.apply(e.position()), true));
            }
        }
        // riders of plot vehicles seen in world space
        for (Entity e : level.getEntities((Entity) null, absorbed.worldBounds().inflate(1, 3, 1), Entity::isPassenger)) {
            Entity vehicle = e.getVehicle();
            if (vehicle != null && plotBox.contains(vehicle.position()) && seen.add(e)) {
                out.add(new Mover(e, t.apply(vehicle.position()), false));
            }
        }
        return out;
    }

    /**
     * Where {@code plotPos} of {@code ship} is now if {@code ship} was absorbed by a rejoin during the last
     * {@link ShipSplits#RELOCATION_MEMORY_TICKS} ticks, else null. Read through {@link ShipSplits#relocate}.
     */
    static @Nullable ShipSplits.Relocation relocate(ServerLevel level, UUID ship, BlockPos plotPos) {
        Map<UUID, Remembered> m = RECENT.get(level);
        Remembered r = m == null ? null : m.get(ship);
        if (r == null) {
            return null;
        }
        return new ShipSplits.Relocation(r.event().keeper(), r.event().transform().apply(plotPos), true, false);
    }

    // ---------------------------------------------------------------- ticking

    /** Level tick end: hammering, finished rejoins, seam particles, old relocations. */
    static void onLevelTick(ServerLevel level) {
        long now = level.getGameTime();
        List<Job> jobs = JOBS.get(level);
        if (jobs != null && !jobs.isEmpty()) {
            List<Job> due = new ArrayList<>();
            jobs.removeIf(j -> (j.due() <= now || j.player().isRemoved()) && due.add(j));
            for (Job j : jobs) {
                long age = now - j.started();
                if (age > 0 && age % 4 == 0) {
                    Vec3 at = j.soundAt();
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 1.0f,
                            0.9f + 0.1f * (float) ((age / 4) % 3));
                }
            }
            for (Job j : due) {
                if (j.player().isRemoved()) {
                    continue;
                }
                Result r;
                try {
                    r = execute(level, j);
                } catch (RuntimeException ex) {
                    Constants.LOG.error("Rejoining ship {} into {} failed", j.absorbed(), j.keeper(), ex);
                    r = new Result(Outcome.FAILED);
                }
                LAST.put(j.player().getUUID(), r);
                j.player().displayClientMessage(r.message(), true);
                if (r.outcome() == Outcome.REJOINED) {
                    Vec3 at = j.soundAt();
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
                }
            }
        }
        Map<UUID, Remembered> recent = RECENT.get(level);
        if (recent != null && now % 100 == 0) {
            recent.values().removeIf(r -> r.until() <= now);
        }
        if (now % SEAM_PERIOD == 0 && AssemblyConfig.REJOIN_ENABLED.get()) {
            showSeams(level, now);
        }
    }

    static void onServerStopped() {
        JOBS.clear();
        RECENT.clear();
        SEAMS.clear();
        LAST.clear();
    }

    /** Particles on the marked piece's seam, sent only to the players holding a marked toolkit. */
    private static void showSeams(ServerLevel level, long now) {
        SEAMS.values().removeIf(s -> s.until() <= now);
        for (ServerPlayer player : level.players()) {
            RejoinMark mark = heldMark(player);
            if (mark == null) {
                continue;
            }
            ShipBody keeper = SableShips.byId(level, mark.ship());
            if (keeper == null) {
                continue;
            }
            Seam seam = SEAMS.get(keeper.id());
            if (seam == null) {
                seam = new Seam(seam(level, keeper), now + 40);
                SEAMS.put(keeper.id(), seam);
            }
            for (BlockPos p : seam.plotBlocks()) {
                Vec3 w = keeper.toWorld(Vec3.atCenterOf(p));
                level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, w.x, w.y + 0.6, w.z, 1, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    private static @Nullable RejoinMark heldMark(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof ShipwrightToolkitItem) {
                RejoinMark m = s.get(AssemblyContent.REJOIN_MARK.get());
                if (m != null) {
                    return m;
                }
            }
        }
        return null;
    }

    /** The keeper's blocks near other pieces of the same ship ({@link RejoinRules#seam}), plot coordinates. */
    static List<BlockPos> seam(ServerLevel level, ShipBody keeper) {
        double radius = AssemblyConfig.SEAM_RADIUS.get();
        UUID origin = ShipSplits.lineage(keeper).origin();
        AABB reach = keeper.worldBounds().inflate(radius);
        List<ShipBody> others = new ArrayList<>();
        AABB othersBox = null;
        for (ShipBody s : SableShips.all(level)) {
            if (!s.isRemoved() && !s.id().equals(keeper.id()) && s.worldBounds().intersects(reach)
                    && ShipSplits.lineage(s).origin().equals(origin)) {
                others.add(s);
                othersBox = othersBox == null ? s.worldBounds() : othersBox.minmax(s.worldBounds());
            }
        }
        if (others.isEmpty()) {
            return List.of();
        }
        AABB near = othersBox.inflate(radius);
        List<BlockPos> keeperBlocks = new ArrayList<>();
        List<Vec3> keeperWorld = new ArrayList<>();
        for (BlockPos p : keeper.plotBlocks()) {
            Vec3 w = keeper.toWorld(Vec3.atCenterOf(p));
            if (near.contains(w)) {
                keeperBlocks.add(p);
                keeperWorld.add(w);
            }
        }
        List<Vec3> otherWorld = new ArrayList<>();
        for (ShipBody s : others) {
            for (BlockPos p : s.plotBlocks()) {
                Vec3 w = s.toWorld(Vec3.atCenterOf(p));
                if (reach.contains(w)) {
                    otherWorld.add(w);
                }
            }
        }
        // keep the pair count bounded on big ships: thin out the other pieces' blocks
        int stride = Math.max(1, (int) ((long) keeperWorld.size() * otherWorld.size() / 400_000L) + 1);
        if (stride > 1) {
            List<Vec3> thin = new ArrayList<>();
            for (int i = 0; i < otherWorld.size(); i += stride) {
                thin.add(otherWorld.get(i));
            }
            otherWorld = thin;
        }
        List<BlockPos> out = new ArrayList<>();
        for (int i : RejoinRules.seam(keeperWorld, otherWorld, radius, SEAM_MAX)) {
            out.add(keeperBlocks.get(i));
        }
        return out;
    }
}
