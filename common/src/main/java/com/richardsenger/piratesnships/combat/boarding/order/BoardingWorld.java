package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.combat.boarding.BoardingConfig;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlankBlockEntity;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlanks;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the two decks of a boarding from the level for {@link BoardingWays} (BRD3): every stand cell of a ship's plot
 * (a cell above one of its blocks whose collision top lies between half and one block, so a deck, a slab or a stair but
 * not a fence or rail top, with no collision in the cell and the one above), the gangways between the two ships
 * ({@link BoardingPlanks#between}) and the map from one plot to the other through the world ({@link ShipBody#toWorld},
 * {@link ShipBody#toPlot}, the mod's Sable wrapper).
 */
public final class BoardingWorld {

    /** Cells searched around a plank's end for a stand cell beside it. */
    private static final int SEARCH = 1;

    private BoardingWorld() {
    }

    /** One ship's deck read from the level, in its plot frame. */
    public static BoardingWays.Deck deck(ServerLevel level, ShipBody ship) {
        return new BoardingWays.Deck() {
            @Override
            public boolean stand(BlockPos cell) {
                return BoardingWorld.stand(level, ship, cell);
            }

            @Override
            public boolean blocked(BlockPos cell) {
                return BoardingWorld.blocked(level, ship, cell);
            }
        };
    }

    /** A block of {@code ship} with collision fills {@code cell} (plot frame). */
    static boolean blocked(ServerLevel level, ShipBody ship, BlockPos cell) {
        if (!ship.plotContains(cell)) return false;
        BlockState st = level.getBlockState(cell);
        return !st.isAir() && !st.getCollisionShape(level, cell).isEmpty();
    }

    /** A boarder can stand in {@code cell} of {@code ship}'s plot: free feet and head, a deck-like floor below. */
    static boolean stand(ServerLevel level, ShipBody ship, BlockPos cell) {
        if (!ship.plotContains(cell) || blocked(level, ship, cell) || blocked(level, ship, cell.above())) return false;
        BlockPos below = cell.below();
        if (!ship.plotContains(below)) return false;
        BlockState st = level.getBlockState(below);
        if (st.isAir()) return false;
        VoxelShape shape = st.getCollisionShape(level, below);
        if (shape.isEmpty()) return false;
        double top = shape.max(Axis.Y);
        return top >= 0.5 && top <= 1.0;
    }

    /** Every stand cell of {@code ship}: the cells above its blocks that {@link #stand} allows. */
    public static Set<BlockPos> standCells(ServerLevel level, ShipBody ship) {
        Set<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos p : ship.plotBlocks()) {
            BlockPos above = p.above();
            if (stand(level, ship, above)) out.add(above.immutable());
        }
        return out;
    }

    /**
     * Every way from {@code from} onto {@code to}: the gangways between them first, then the gap crossings of at most
     * {@code boarding.board_gap} blocks. Empty when the ships lie too far apart.
     */
    public static List<BoardingWays.Crossing> crossings(ServerLevel level, ShipBody from, ShipBody to) {
        List<BoardingWays.Crossing> out = new ArrayList<>();
        if (from.isRemoved() || to.isRemoved() || from.id().equals(to.id())) return out;
        int gap = BoardingConfig.BOARD_GAP.get();
        if (!near(from, to, gap + 2.0)) return out;
        BoardingWays.Deck fromDeck = deck(level, from);
        BoardingWays.Deck toDeck = deck(level, to);
        for (BoardingPlanks.Plank p : BoardingPlanks.between(from, to)) {
            BoardingWays.Crossing c = plank(level, from, to, p, fromDeck, toDeck);
            if (c != null) out.add(c);
        }
        out.addAll(BoardingWays.gaps(standCells(level, from), fromDeck, toDeck, v -> to.toPlot(from.toWorld(v)), gap));
        return out;
    }

    /** Whether the world boxes of the two ships come within {@code margin} blocks of each other. */
    static boolean near(ShipBody a, ShipBody b, double margin) {
        AABB ba = box(a);
        AABB bb = box(b);
        return ba.inflate(margin).intersects(bb);
    }

    private static AABB box(ShipBody ship) {
        AABB w = ship.worldBounds();
        // right after assembly Sable has not filled the world bounds yet: the plot bounds through the pose then
        return w.getSize() > 0 ? w : com.richardsenger.piratesnships.crew.npc.CrewStations.worldBox(ship, 0);
    }

    /** The crossing over plank {@code p} (laid from {@code from} onto {@code to}, or the other way), or null. */
    static @Nullable BoardingWays.Crossing plank(ServerLevel level, ShipBody from, ShipBody to, BoardingPlanks.Plank p,
                                                 BoardingWays.Deck fromDeck, BoardingWays.Deck toDeck) {
        boolean laidFromLeaving = p.fromShip().equals(from.id());
        ShipBody frame = laidFromLeaving ? from : to;
        ShipBody far = laidFromLeaving ? to : from;
        BlockPos base = p.base();
        Direction facing = p.facing();
        BlockPos tip = base.relative(facing, Math.max(0, p.length() - 1));
        double baseY = boardY(level, base, true);
        double tipY = boardY(level, tip, false);
        // the stand cell behind segment 0 (in the plank's own frame) and the one past the tip (in the far ship's frame)
        BlockPos nearStand = nearestStand(laidFromLeaving ? fromDeck : toDeck, base.relative(facing.getOpposite()), base);
        Vec3 past = far.toPlot(frame.toWorld(Vec3.atBottomCenterOf(tip).add(facing.getStepX(), tipY + 0.25, facing.getStepZ())));
        BlockPos farStand = nearestStand(laidFromLeaving ? toDeck : fromDeck, BlockPos.containing(past), null);
        if (nearStand == null || farStand == null) return null;
        BlockPos start = laidFromLeaving ? nearStand : farStand;
        BlockPos landing = laidFromLeaving ? farStand : nearStand;
        return BoardingWays.plank(laidFromLeaving, base, facing, p.length(), baseY, tipY, start, landing);
    }

    /** The walking surface over the floor of plank cell {@code cell}: the board's top at the cell's middle. */
    private static double boardY(ServerLevel level, BlockPos cell, boolean nearEnd) {
        if (level.getBlockEntity(cell) instanceof BoardingPlankBlockEntity be) {
            return (be.near() + be.far()) / 2.0 + 0.125;
        }
        return 0.125;
    }

    /** {@code want} if it is a stand cell of {@code deck}, else the nearest one within one block of it (not {@code not}). */
    private static @Nullable BlockPos nearestStand(BoardingWays.Deck deck, BlockPos want, @Nullable BlockPos not) {
        if (deck.stand(want) && !want.equals(not)) return want;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos c : BlockPos.betweenClosed(want.offset(-SEARCH, -SEARCH, -SEARCH), want.offset(SEARCH, SEARCH, SEARCH))) {
            if (c.equals(not) || !deck.stand(c)) continue;
            double d = c.distSqr(want);
            if (d < bestD) {
                best = c.immutable();
                bestD = d;
            }
        }
        return best;
    }
}
