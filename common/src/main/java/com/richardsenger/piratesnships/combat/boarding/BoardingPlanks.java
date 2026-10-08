package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Laying boarding planks and finding them again (BRD1, docs/design.md §8.3). A run lives in the plot of the ship it
 * was laid from (ship A; Sable grows the plot as each cell is placed, docs/sable-notes.md §1.2, so the cells are
 * placed from the gunwale outward) and lands on another ship B lying alongside, found by its world bounds and
 * {@link ShipBody#toPlot}. The rules are in {@link PlankRun}; this class reads both ships for it.
 */
public final class BoardingPlanks {

    /** Margin in blocks around another ship's world bounds when looking for it under a plank cell. */
    private static final double SHIP_MARGIN = 1.5;

    /** Outcome of {@link #lay}. */
    public enum Outcome { OK, DISABLED, NOT_ON_SHIP, NO_DECK, BLOCKED }

    /** The result of {@link #lay}: on {@link Outcome#OK}, the far ship and the plot position of segment 0. */
    public record Laid(Outcome outcome, @Nullable UUID farShip, @Nullable BlockPos base, int length,
                       @Nullable PlankRun.Landing landing) {
        static Laid fail(Outcome outcome) {
            return new Laid(outcome, null, null, 0, null);
        }
    }

    /**
     * A live plank run: laid from ship {@code fromShip} (segment 0 at its plot position {@code base}, running
     * {@code facing} for {@code length} cells) onto ship {@code toShip}.
     */
    public record Plank(UUID fromShip, BlockPos base, Direction facing, int length, UUID toShip, Vec3 toPlot,
                        BlockPos toBlock, PlankRun.Landing landing) {
        /** Plot position (of the ship it lies in) of the far cell. */
        public BlockPos tip() {
            return base.relative(facing, length - 1);
        }
    }

    private BoardingPlanks() {
    }

    // ------------------------------------------------------------------ laying

    /**
     * Lays a run from the block {@code clicked} (plot position, on a ship) clicked on its face {@code face} by someone
     * facing {@code playerFacing}. Places nothing unless a whole run fits ({@link PlankRun#compute}).
     */
    public static Laid lay(ServerLevel level, BlockPos clicked, Direction face, Direction playerFacing) {
        if (!BoardingConfig.ENABLED.get()) {
            return Laid.fail(Outcome.DISABLED);
        }
        ShipBody from = SableShips.containing(level, clicked);
        if (from == null || from.isRemoved()) {
            return Laid.fail(Outcome.NOT_ON_SHIP);
        }
        PlankRun.Start start = PlankRun.start(clicked, face, playerFacing);
        if (start == null) {
            return Laid.fail(Outcome.NO_DECK);
        }
        WorldProbe probe = new WorldProbe(level, from, start);
        PlankRun.Result run = PlankRun.compute(BoardingConfig.MAX_LENGTH.get(), probe);
        if (!run.found()) {
            return Laid.fail(run.failure() == PlankRun.Failure.BLOCKED ? Outcome.BLOCKED : Outcome.NO_DECK);
        }
        ShipBody to = probe.landingShip[run.tip()];
        BlockPos toBlock = probe.landingBlock[run.tip()];
        Vec3 tipWorld = from.toWorld(Vec3.atCenterOf(start.cell(run.tip())));

        BoardingPlankBlock block = BoardingContent.PLANK.get();
        for (int i = 0; i < run.length(); i++) {
            BlockPos cell = start.cell(i);
            level.setBlock(cell, block.segment(start.direction(), i, run.length()), Block.UPDATE_ALL);
            if (!level.getBlockState(cell).is(block)) {
                // the plot did not take the block (no chunk there): take back what was placed, without drops
                for (int j = i - 1; j >= 0; j--) {
                    level.setBlock(start.cell(j), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
                return Laid.fail(Outcome.BLOCKED);
            }
        }
        BlockPos base = start.cell(0);
        if (level.getBlockEntity(base) instanceof BoardingPlankBlockEntity be) {
            be.link(to.id(), to.toPlot(tipWorld), toBlock, run.length(), run.landing());
        }
        SoundType sound = block.defaultBlockState().getSoundType();
        Vec3 at = from.toWorld(Vec3.atCenterOf(base));
        level.playSound(null, at.x, at.y, at.z, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        return new Laid(Outcome.OK, to.id(), base, run.length(), run.landing());
    }

    /** Reads ship A's plot, the world and the other ships for {@link PlankRun#compute}. */
    private static final class WorldProbe implements PlankRun.Probe {
        private final ServerLevel level;
        private final ShipBody from;
        private final PlankRun.Start start;
        private final List<ShipBody> others = new ArrayList<>();
        final ShipBody[] landingShip = new ShipBody[PlankRun.MAX_SEGMENTS];
        final BlockPos[] landingBlock = new BlockPos[PlankRun.MAX_SEGMENTS];

        WorldProbe(ServerLevel level, ShipBody from, PlankRun.Start start) {
            this.level = level;
            this.from = from;
            this.start = start;
            for (ShipBody s : SableShips.all(level)) {
                if (!s.isRemoved() && !s.id().equals(from.id())) {
                    others.add(s);
                }
            }
        }

        private Vec3 world(int i) {
            return from.toWorld(Vec3.atCenterOf(start.cell(i)));
        }

        private List<ShipBody> near(Vec3 world) {
            List<ShipBody> out = new ArrayList<>();
            for (ShipBody s : others) {
                AABB b = s.worldBounds();
                if (b.inflate(SHIP_MARGIN).contains(world)) {
                    out.add(s);
                }
            }
            return out;
        }

        private boolean solid(BlockPos pos) {
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        private boolean sturdyTop(BlockPos pos) {
            return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
        }

        @Override
        public boolean free(int i) {
            BlockPos cell = start.cell(i);
            if (!from.plotContains(cell)) {
                return false;
            }
            BlockState here = level.getBlockState(cell);
            if (!here.canBeReplaced()) {
                return false;
            }
            Vec3 w = world(i);
            if (solid(BlockPos.containing(w))) {
                return false; // land, a quay, anything of the world
            }
            for (ShipBody s : near(w)) {
                if (solid(BlockPos.containing(s.toPlot(w)))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public @Nullable PlankRun.Landing landing(int i) {
            Vec3 w = world(i);
            Vec3 ahead = from.toWorld(Vec3.atCenterOf(start.cell(i + 1)));
            for (ShipBody s : near(w)) {
                BlockPos p = BlockPos.containing(s.toPlot(w));
                BlockPos q = BlockPos.containing(s.toPlot(ahead));
                PlankRun.Landing l = PlankRun.classify(sturdyTop(p.below()), solid(p.below()), sturdyTop(p.below(2)),
                        sturdyTop(q), solid(q.above()));
                if (l != null) {
                    landingShip[i] = s;
                    landingBlock[i] = switch (l) {
                        case LEVEL -> p.below();
                        case STEP_DOWN -> p.below(2);
                        case STEP_UP -> q;
                    };
                    return l;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ finding

    /** Every live plank run laid from {@code ship}, onto any ship. */
    public static List<Plank> from(ShipBody ship) {
        List<Plank> out = new ArrayList<>();
        for (BlockEntity be : ship.plotBlockEntities()) {
            if (be instanceof BoardingPlankBlockEntity p && !p.isRemoved() && p.farShip() != null
                    && p.getBlockState().is(BoardingContent.PLANK.get())) {
                out.add(new Plank(ship.id(), p.getBlockPos(), p.facing(), p.length(), p.farShip(), p.farPlot(),
                        p.farBlock(), p.landing()));
            }
        }
        return out;
    }

    /**
     * Every live plank run between ships {@code a} and {@code b}, in either direction (for the boarding AI, BRD2):
     * first the runs laid from {@code a} onto {@code b}, then those laid from {@code b} onto {@code a}.
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
