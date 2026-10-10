package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.railing.BulwarkPortBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingGateBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingLatch;
import com.richardsenger.piratesnships.ship.decor.railing.RailingPortBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Railing latches (RL5, docs/design.md §4.8 "Net sheets and railing latches"): the ratline net item used on a railing's
 * or bulwark's top rail ties the net to an iron latch set on that edge ({@link RailingBlock#LATCH}, drawn on the cap)
 * whose net end is a {@link RailingLatchBlock} in the block above. Here: which railing-family blocks take one
 * ({@link #refusal}), which edge a click means ({@link #edgeAt}), and setting and taking away a latch.
 */
public final class RailingLatches {

    /** Why a railing-family block takes no latch on an edge. */
    public enum Refusal {
        NONE,
        /** {@code rigging.railing_latches} is off. */
        DISABLED,
        /** A railing gate, railing port or bulwark gun port. */
        GATE_OR_PORT,
        /** The edge has no rail. */
        NO_RAIL,
        /** The block carries a latch already (one per block). */
        TAKEN,
        /** The block above the rail is not free for the net's end. */
        BLOCKED
    }

    private RailingLatches() {
    }

    /** Whether {@code state} belongs to the railing family (railing, bulwark, their ports, the gate). */
    public static boolean railingFamily(BlockState state) {
        return state.getBlock() instanceof RailingBlock || state.getBlock() instanceof RailingGateBlock
                || state.getBlock() instanceof RailingPortBlock || state.getBlock() instanceof BulwarkPortBlock;
    }

    /**
     * The edge a click at {@code hit} (world or plot position, as the block) on the railing at {@code pos} means: the
     * edge nearest the click point; with no click point (a test), the railing's only railed edge, else null.
     */
    public static @Nullable Direction edgeAt(BlockGetter level, BlockPos pos, @Nullable Vec3 hit) {
        if (hit == null) {
            var edges = RailingBlock.edges(level.getBlockState(pos));
            return edges.size() == 1 ? edges.iterator().next() : null;
        }
        double fx = Math.max(0, Math.min(1, hit.x - pos.getX()));
        double fz = Math.max(0, Math.min(1, hit.z - pos.getZ()));
        return RailingRules.edgeForClick(fx, fz, false, Direction.NORTH);
    }

    /** Why the railing-family block at {@code pos} takes no latch on {@code edge}, or {@link Refusal#NONE}. */
    public static Refusal refusal(BlockGetter level, BlockPos pos, @Nullable Direction edge) {
        if (!RiggingConfig.RAILING_LATCHES.get()) {
            return Refusal.DISABLED;
        }
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof RailingBlock)) {
            return Refusal.GATE_OR_PORT;
        }
        if (RailingBlock.latch(s) != null) {
            return Refusal.TAKEN;
        }
        if (edge == null || !edge.getAxis().isHorizontal() || !s.getValue(RailingBlock.edge(edge))) {
            return Refusal.NO_RAIL;
        }
        return RatlineNets.free(level.getBlockState(pos.above())) ? Refusal.NONE : Refusal.BLOCKED;
    }

    /**
     * Server: sets the latch on {@code edge} of the railing at {@code rail} (checked by {@link #refusal}): the net end
     * above it, then the railing's eye bolt. Returns the net end's position (the fitting).
     */
    public static BlockPos place(ServerLevel level, BlockPos rail, Direction edge) {
        BlockPos at = rail.above().immutable();
        BlockState end = Waterlogging.at(RiggingContent.RAILING_LATCH.get().defaultBlockState()
                .setValue(RailingLatchBlock.FACING, edge), level, at);
        level.setBlock(at, end, Block.UPDATE_ALL);
        BlockState r = level.getBlockState(rail);
        if (r.getBlock() instanceof RailingBlock) {
            level.setBlock(rail, r.setValue(RailingBlock.LATCH, RailingLatch.of(edge)), Block.UPDATE_ALL);
        }
        return at;
    }

    /** Server: takes the latch whose net end is at {@code end} away again (a refused net); the railing loses its eye bolt. */
    public static void remove(ServerLevel level, BlockPos end) {
        BlockState s = level.getBlockState(end);
        if (s.getBlock() instanceof RailingLatchBlock) {
            level.setBlock(end, Waterlogging.leftBehind(s), Block.UPDATE_ALL);
        }
    }
}
