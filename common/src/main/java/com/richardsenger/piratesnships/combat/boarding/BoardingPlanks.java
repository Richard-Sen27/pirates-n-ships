package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Laying gangways, taking them up and finding them again (BRD1, BRD2, BRD4, docs/design.md §8.3, §8.4). The rules are
 * in {@link PlankRun}, the reading of the world and the ships in {@link PlankPlanner}; this class places the cells (from
 * segment 0 outward, so Sable grows a ship's plot from its edge, docs/sable-notes.md §1.2), gives every cell its board
 * heights and records the run and its ends on segment 0's {@link BoardingPlankBlockEntity}.
 */
public final class BoardingPlanks {

    /** Outcome of {@link #lay}. */
    public enum Outcome { OK, DISABLED, NO_START, BLOCKED, TOO_FAR, TOO_STEEP, NOTHING_TO_LAND }

    /**
     * The result of {@link #lay}. On {@link Outcome#OK}: the ship the run lies in ({@code ship}, null for the world), the
     * cells (frame positions, segment 0 first) with the board's heights in each, its length in columns and rise in whole
     * blocks (in the laying direction), the landing and the far end's ship (null for the world). On
     * {@link Outcome#BLOCKED}: {@code blockedAt}, the column of the taken cell.
     */
    public record Laid(Outcome outcome, @Nullable UUID ship, List<BlockPos> cells, List<PlankRun.Cell> heights,
                       int columns, int rise, @Nullable UUID farShip, @Nullable PlankRun.Landing landing, int blockedAt) {
        static Laid fail(Outcome outcome, int blockedAt) {
            return new Laid(outcome, null, List.of(), List.of(), 0, 0, null, null, blockedAt);
        }

        /** Frame position of segment 0, or null. */
        public @Nullable BlockPos base() {
            return cells.isEmpty() ? null : cells.get(0);
        }

        /** Cells of the run. */
        public int length() {
            return cells.size();
        }

        /** The board's pitch in the laying direction, degrees (negative when it falls). */
        public double pitchDegrees() {
            return columns == 0 ? 0.0 : Math.toDegrees(Math.atan2(rise, columns));
        }
    }

    /**
     * A live run laid from ship {@code fromShip} (segment 0 at its plot position {@code base}, laid {@code facing} for
     * {@code length} cells) onto ship {@code toShip}, landing at {@code toPlot} on {@code toBlock} of its plot.
     */
    public record Plank(UUID fromShip, BlockPos base, Direction facing, int length, UUID toShip, Vec3 toPlot,
                        BlockPos toBlock, PlankRun.Landing landing) {
    }

    private BoardingPlanks() {
    }

    // ------------------------------------------------------------------ laying

    /**
     * Lays a run from the block {@code clicked} (a world position, or a plot position on a ship) clicked on its face
     * {@code face} by someone facing {@code playerFacing}. Places nothing unless the whole run fits.
     */
    public static Laid lay(ServerLevel level, BlockPos clicked, Direction face, Direction playerFacing) {
        if (!BoardingConfig.ENABLED.get()) {
            return Laid.fail(Outcome.DISABLED, -1);
        }
        PlankPlanner.Plan plan = PlankPlanner.plan(level, clicked, face, playerFacing, PlankPlanner.settings());
        if (!plan.found()) {
            return Laid.fail(outcome(plan.failure()), plan.blockedAt());
        }
        return place(level, plan);
    }

    /**
     * Places a found plan's cells from segment 0 outward, gives each its board heights and segment 0 the run and its
     * ends. Places nothing (and reports the cell as blocked) when a ship's plot does not take a cell.
     */
    static Laid place(ServerLevel level, PlankPlanner.Plan plan) {
        BoardingPlankBlock block = BoardingContent.PLANK.get();
        int n = plan.length();
        for (int i = 0; i < n; i++) {
            BlockPos cell = plan.cells().get(i);
            BlockState state = block.segment(plan.facing(), i);
            level.setBlock(cell, Waterlogging.at(state, level, cell), Block.UPDATE_ALL);
            if (!level.getBlockState(cell).is(block)) {
                // the plot did not take the block (no chunk there): take back what was placed, without drops
                BoardingPlankBlock.quietly(() -> {
                    for (int j = 0; j < n; j++) {
                        BlockPos back = plan.cells().get(j);
                        if (level.getBlockState(back).is(block)) {
                            level.setBlock(back, Waterlogging.leftBehind(level.getBlockState(back)), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                        }
                    }
                });
                return Laid.fail(Outcome.BLOCKED, plan.heights().get(i).i());
            }
        }
        BlockPos base = plan.cells().get(0);
        for (int i = 0; i < n; i++) {
            if (level.getBlockEntity(plan.cells().get(i)) instanceof BoardingPlankBlockEntity be) {
                PlankRun.Cell h = plan.heights().get(i);
                be.cell(base, h.near(), h.far());
            }
        }
        PlankBodies.Body frame = plan.frame();
        PlankBodies.Body far = plan.far();
        if (level.getBlockEntity(base) instanceof BoardingPlankBlockEntity be) {
            be.link(plan.cells(), plan.columns(), plan.rise(), plan.lipNear(), plan.lipFar(), frame != null && !frame.isWorld(),
                    plan.baseSupport(), far == null ? null : far.id(), plan.farPoint(), plan.farBlock(), plan.landing());
        }
        for (BlockPos cell : plan.cells()) {
            if (level.getBlockEntity(cell) instanceof BoardingPlankBlockEntity be) be.sync();
        }
        SoundType sound = block.defaultBlockState().getSoundType();
        play(level, frame, base, sound.getPlaceSound(), sound);
        return new Laid(Outcome.OK, frame == null ? null : frame.id(), List.copyOf(plan.cells()),
                List.copyOf(plan.heights()), plan.columns(), plan.rise(), far == null ? null : far.id(), plan.landing(), -1);
    }

    static Outcome outcome(PlankRun.Failure failure) {
        return switch (failure) {
            case NONE -> Outcome.OK;
            case NO_START -> Outcome.NO_START;
            case BLOCKED -> Outcome.BLOCKED;
            case TOO_FAR -> Outcome.TOO_FAR;
            case TOO_STEEP -> Outcome.TOO_STEEP;
            case NOTHING_TO_LAND -> Outcome.NOTHING_TO_LAND;
        };
    }

    private static void play(ServerLevel level, @Nullable PlankBodies.Body frame, BlockPos at, SoundEvent event, SoundType sound) {
        Vec3 w = frame == null ? Vec3.atCenterOf(at) : frame.toWorld(Vec3.atCenterOf(at));
        level.playSound(null, w.x, w.y, w.z, event, SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
    }

    // ------------------------------------------------------------------ taking it up

    /**
     * Takes up the whole run {@code pos} belongs to (from either end or the middle) and gives {@code player} one plank
     * (none in creative), or drops it when the inventory is full. False when picking up is off or there is no run.
     */
    public static boolean pickUp(ServerLevel level, BlockPos pos, Player player) {
        if (!BoardingConfig.PICK_UP.get()) {
            return false;
        }
        List<BlockPos> run = BoardingPlankBlock.run(level, pos);
        if (run.isEmpty()) {
            return false;
        }
        BoardingPlankBlock block = BoardingContent.PLANK.get();
        // every cell without drops (segment 0 holds the only loot), and no cascade of the run breaking
        BoardingPlankBlock.quietly(() -> {
            for (BlockPos p : run) {
                BlockState st = level.getBlockState(p);
                if (st.is(block)) {
                    level.setBlock(p, Waterlogging.leftBehind(st), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        });
        if (!player.getAbilities().instabuild) {
            ItemStack stack = new ItemStack(BoardingContent.PLANK_ITEM.get());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        SoundType sound = block.defaultBlockState().getSoundType();
        play(level, PlankBodies.at(level, pos), pos, sound.getBreakSound(), sound);
        return true;
    }

    // ------------------------------------------------------------------ finding

    /** Every live run laid from {@code ship} onto another ship. */
    public static List<Plank> from(ShipBody ship) {
        List<Plank> out = new ArrayList<>();
        for (BlockEntity be : ship.plotBlockEntities()) {
            if (be instanceof BoardingPlankBlockEntity p && !p.isRemoved() && p.farShip() != null
                    && p.getBlockState().is(BoardingContent.PLANK.get()) && p.isBase()) {
                out.add(new Plank(ship.id(), p.getBlockPos(), p.facing(), p.length(), p.farShip(), p.farPoint(),
                        p.farBlock(), p.landing()));
            }
        }
        return out;
    }

    /**
     * Every live run between ships {@code a} and {@code b}, in either direction (for a boarding order): first the runs
     * laid from {@code a} onto {@code b}, then those laid from {@code b} onto {@code a}.
     */
    public static List<Plank> between(ShipBody a, ShipBody b) {
        List<Plank> out = new ArrayList<>();
        for (Plank p : from(a)) {
            if (p.toShip().equals(b.id())) out.add(p);
        }
        for (Plank p : from(b)) {
            if (p.toShip().equals(a.id())) out.add(p);
        }
        return out;
    }
}
