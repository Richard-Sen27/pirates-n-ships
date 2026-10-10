package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.combat.boarding.order.BoardingParties;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Cuts;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Hold;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Layout;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Step;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Net;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Boarding nets in a level (RAIL5, docs/design.md §4.8 "Railing extras"). A ratline net between two railing latches
 * (RL5, {@link RailingLatchBlock}, on the same edge) or two chainplates on top of railing blocks (RAIL5's way, still
 * accepted) of one body, at one height and along one railed edge both railings share, is a boarding net: its
 * cells are the block row above the rail between the chainplates (the line's own cells, {@link RatlineNetRules#netCells}),
 * placed with {@link RatlineNetCellBlock#BOARDING} and their {@link RatlineNetCellBlock#FACING} on the outboard side
 * (the shared edge), so the side turns with the ship.
 *
 * <p><b>Holding a boarder.</b> Sable asks a plot block for its collision without the entity
 * ({@code SubLevelEntityCollision#getSubLevelEntityCollisionShape}, l.506: {@code state.getCollisionShape(level, pos)},
 * only scaffolding gets an entity context), so a wall that lets the ship's own crew through cannot be a collision shape
 * on a ship. The net holds in {@code entityInside} instead, which Sable calls for plot blocks too
 * ({@code mixin/entity/entities_in_blocks/EntityMixin#checkInsideBlocks}, the entity's box turned into the plot): a
 * living entity that comes into a net cell from outboard and does not pass ({@link BoardingNetRules#passes}) is held
 * behind a thin wall {@link BoardingNetRules#WALL} inside the outboard face for {@code rigging.boarding_net_hold} ticks,
 * its inboard motion taken away and the rest slowed like a cobweb at half strength; then it may climb over (the cells
 * are climbable). This runs on the side that moves the entity (the client for the local player, the server for mobs and
 * mock players), with one hold table per side.
 *
 * <p><b>Cutting.</b> A boarding net cannot be mined; a player's cutlass hits on any of its cells
 * ({@link RatlineNetCellBlock#attack}) cut it after {@code rigging.boarding_net_cut_hits}, dropping one net item at its
 * first chainplate (A).
 */
public final class BoardingNets {

    /** Each side's holds: the client's for its local player, the server's for the entities it moves. */
    private static final Map<Entity, Hold> CLIENT_HOLDS = new WeakHashMap<>();
    private static final Map<Entity, Hold> SERVER_HOLDS = new WeakHashMap<>();
    /** Cutlass hits per net (its hub), server only. */
    private static final Map<GlobalPos, Cuts> CUTS = new HashMap<>();

    private BoardingNets() {
    }

    // ------------------------------------------------------------------ what is a boarding net

    /** The railing a floor chainplate or a railing latch (RL5) at {@code pos} stands on, or null. */
    static @Nullable BlockPos railBelow(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        boolean plate = s.getBlock() instanceof ChainplateBlock && s.getValue(ChainplateBlock.FACE) == AttachFace.FLOOR;
        if (!plate && !(s.getBlock() instanceof RailingLatchBlock)) {
            return null;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).getBlock() instanceof RailingBlock ? below : null;
    }

    /** The edges a boarding net end at {@code end} on the railing {@code rail} may hang along: a latch's own edge, else every railed one. */
    private static Set<Direction> edgesFor(BlockGetter level, BlockPos end, BlockPos rail) {
        BlockState s = level.getBlockState(end);
        if (s.getBlock() instanceof RailingLatchBlock) {
            Direction edge = s.getValue(RailingLatchBlock.FACING);
            return RailingBlock.edges(level.getBlockState(rail)).contains(edge) ? EnumSet.of(edge) : EnumSet.noneOf(Direction.class);
        }
        return RailingBlock.edges(level.getBlockState(rail));
    }

    /**
     * The boarding net layout of fittings {@code a} and {@code b} when both are latches or floor chainplates on top of
     * railings at one height ({@link BoardingNetRules#layout}; a latch only along its own edge), else null (an ordinary
     * ratline net).
     */
    public static @Nullable Layout layout(BlockGetter level, BlockPos a, BlockPos b, int maxLength) {
        if (a.getY() != b.getY()) {
            return null;
        }
        BlockPos ra = railBelow(level, a), rb = railBelow(level, b);
        if (ra == null || rb == null) {
            return null;
        }
        return BoardingNetRules.layout(ra, edgesFor(level, a, ra), rb, edgesFor(level, b, rb), maxLength);
    }

    /** The outboard side of {@code net} when it is a boarding net, else null. */
    public static @Nullable Direction outboard(BlockGetter level, Net net) {
        Layout l = layout(level, net.a(), net.b(), Integer.MAX_VALUE);
        return l != null && l.verdict() == BoardingNetRules.Verdict.OK ? l.outboard() : null;
    }

    /** Whether {@code net} is a boarding net (hung along the rail). */
    public static boolean isBoarding(BlockGetter level, Net net) {
        return outboard(level, net) != null;
    }

    // ------------------------------------------------------------------ holding

    /** {@link RatlineNetCellBlock#entityInside} of a boarding net cell, both sides. */
    static void onInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!RiggingConfig.BOARDING_NETS.get() || !(entity instanceof LivingEntity) || entity.isSpectator()) {
            return;
        }
        if (entity instanceof Player p && (p.getAbilities().flying || p.isCreative())) {
            return;
        }
        boolean movesHere = level.isClientSide ? entity instanceof Player p && p.isLocalPlayer() : !(entity instanceof ServerPlayer);
        if (!movesHere) {
            return;
        }
        Map<Entity, Hold> holds = level.isClientSide ? CLIENT_HOLDS : SERVER_HOLDS;
        if (level instanceof ServerLevel server && member(server, pos, entity)) {
            holds.remove(entity);
            return;
        }
        Direction out = state.getValue(RatlineNetCellBlock.FACING);
        RatlineNetAssist.Frame frame = RatlineNetAssist.frameAt(level, pos);
        Vec3 local = frame == null ? entity.position() : frame.toLocal(entity.position());
        double s = (local.x - (pos.getX() + 0.5)) * out.getStepX() + (local.z - (pos.getZ() + 0.5)) * out.getStepZ();
        Step step = BoardingNetRules.contact(holds.get(entity), level.getGameTime(), s > 0, RiggingConfig.BOARDING_NET_HOLD.get());
        holds.put(entity, step.next());
        if (!step.holds()) {
            return;
        }
        Vec3 o = new Vec3(out.getStepX(), 0, out.getStepZ());
        Vec3 v = frame == null ? entity.getDeltaMovement() : frame.dirToLocal(entity.getDeltaMovement());
        double[] across = BoardingNetRules.withoutInboard(v.x, v.z, o.x, o.z);
        Vec3 kept = new Vec3(across[0], v.y, across[1]);
        entity.setDeltaMovement(frame == null ? kept : frame.dirToWorld(kept));
        double push = BoardingNetRules.pushOut(s, entity.getBbWidth() / 2.0);
        if (push > 0) {
            Vec3 d = o.scale(push);
            entity.setPos(entity.position().add(frame == null ? d : frame.dirToWorld(d)));
        }
        entity.makeStuckInBlock(state, new Vec3(BoardingNetRules.HELD_ACROSS, BoardingNetRules.HELD_VERTICAL, BoardingNetRules.HELD_ACROSS));
    }

    /** Whether a held side's hold is running for {@code entity} right now (for tests). */
    public static boolean holds(Level level, Entity entity) {
        Hold h = (level.isClientSide ? CLIENT_HOLDS : SERVER_HOLDS).get(entity);
        long now = level.getGameTime();
        return h != null && now - h.lastContact() <= 1 && h.held() > 0 && now > h.freeUntil();
    }

    /**
     * Whether {@code entity} crosses the boarding net at {@code pos} freely: a crew member of the net's ship (now on
     * it, at its station or hammock) or hired by its owner ({@link BoardingNetRules#passes}); a boarder of a party from the
     * net's ship (BRD3, {@code BoardingParties#homeOf}, also an NPC captain's fighter) and no other boarder.
     */
    static boolean member(ServerLevel level, BlockPos pos, Entity entity) {
        ShipBody ship = SableShips.containing(level, pos);
        if (ship == null) {
            return false;
        }
        // BRD3: a boarder (crew, or an NPC captain's fighter) belongs to the ship it boards from: its own net lets it
        // back in, any other ship's net holds it like any attacker
        UUID home = BoardingParties.homeOf(entity);
        if (home != null) {
            return home.equals(ship.id());
        }
        if (!(entity instanceof CrewMember crew)) {
            return false;
        }
        Optional<UUID> owner = ShipRegistry.get(level.getServer()).find(ship.id()).flatMap(ShipData::owner);
        return BoardingNetRules.passes(true, ship.id(), ShipBunks.shipIdOf(level, crew), crew.hiredBy(), owner);
    }

    // ------------------------------------------------------------------ cutting

    /** {@link RatlineNetCellBlock#attack} of a boarding net cell: a cutlass hit (server). */
    static void hit(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server) || !player.getMainHandItem().is(CombatContent.CUTLASS.get())) {
            return;
        }
        RatlineNets.Strand strand = RatlineNets.ownerStrand(level, pos);
        if (strand == null) {
            return;
        }
        GlobalPos key = GlobalPos.of(level.dimension(), strand.hub().immutable());
        Cuts cuts = BoardingNetRules.hit(CUTS.get(key), level.getGameTime());
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.1f);
        if (cuts.hits() >= RiggingConfig.BOARDING_NET_CUT_HITS.get()) {
            CUTS.remove(key);
            RatlineNets.breakNet(server, strand.net(), true);
        } else {
            CUTS.put(key, cuts);
        }
    }
}
