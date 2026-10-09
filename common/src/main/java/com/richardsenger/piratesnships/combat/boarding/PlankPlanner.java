package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.combat.boarding.PlankBodies.Body;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans a gangway from a click (BRD2, docs/design.md §8.4; RAIL1: through a railing port, {@link PlankRun#throughOpening}): reads the world and the ships for {@link PlankRun} and says
 * where the cells go, in which frame, and what each end rests on. Used by the server to lay a run
 * ({@link BoardingPlanks#lay}) and by the client for the placement preview, so both see the same rule.
 *
 * <p><b>Frames.</b> A run touching a ship lives in that ship's plot and follows it:
 * <ul>
 *   <li>started on ship A: in A's plot, from the clicked block outward, landing on the world or another ship;</li>
 *   <li>started on the world and landing on ship B: planned again in B's frame from the same start, then laid from B's
 *       end outward (cell order and pitches reversed), so the cells follow B and Sable grows B's plot from its edge;</li>
 *   <li>started on the world and landing on the world: plain world blocks.</li>
 * </ul>
 */
public final class PlankPlanner {

    /** Margin in blocks around a ship's world bounds when looking for it near the run. */
    private static final double SHIP_MARGIN = 2.0;

    /**
     * A planned run. On success: the frame it lies in ({@code frame}: the world or a ship), its cells in laying order
     * (segment 0 first) with their pitches, the direction it is laid in ({@code facing}), the block segment 0's end
     * rests on ({@code baseSupport}, frame position), what the far end rests on ({@code far}, {@code farBlock} in the far
     * body's frame), the far end's spot in the far body's frame ({@code farPoint}), and the landing.
     */
    public record Plan(PlankRun.Failure failure, int blockedAt, @Nullable Body frame, List<BlockPos> cells,
                       List<PlankRun.Pitch> pitches, @Nullable Direction facing, @Nullable PlankRun.Landing landing,
                       @Nullable BlockPos baseSupport, @Nullable Body far, @Nullable BlockPos farBlock,
                       @Nullable Vec3 farPoint) {
        static Plan fail(PlankRun.Failure failure, int blockedAt) {
            return new Plan(failure, blockedAt, null, List.of(), List.of(), null, null, null, null, null, null);
        }

        static Plan fail(PlankRun.Result r) {
            return fail(r.failure(), r.blockedAt());
        }

        public boolean found() {
            return failure == PlankRun.Failure.NONE;
        }

        public int length() {
            return cells.size();
        }
    }

    private PlankPlanner() {
    }

    /** The rule's settings from the server config. */
    public static PlankRun.Settings settings() {
        return new PlankRun.Settings(BoardingConfig.MAX_LENGTH.get(), BoardingConfig.MAX_RISE.get(),
                BoardingConfig.RISE_RUN.get(), BoardingConfig.STEEP_FALLBACK.get());
    }

    /**
     * Plans a run from the block {@code clicked} (a world position, or a plot position of a ship) clicked on its face
     * {@code face} by someone facing {@code playerFacing}.
     */
    public static Plan plan(Level level, BlockPos clicked, Direction face, Direction playerFacing, PlankRun.Settings s) {
        BlockState clickedState = level.getBlockState(clicked);
        if (clickedState.is(BoardingContent.PLANK.get()) || clickedState.getCollisionShape(level, clicked).isEmpty()) {
            return Plan.fail(PlankRun.Failure.NO_START, -1);
        }
        // RAIL1: the top of a railing port's sill, facing outboard, starts the run beyond the port at its height;
        // RAIL3: a railing gate only while it is open
        boolean openable = clickedState.hasProperty(BlockStateProperties.OPEN);
        Direction outboard = PlankRun.opening(clickedState.is(BoardingContent.GANGWAY_OPENINGS), openable,
                openable && clickedState.getValue(BlockStateProperties.OPEN))
                && clickedState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? clickedState.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
        PlankRun.Start start = PlankRun.start(clicked, PlankRun.throughOpening(face, playerFacing, outboard), playerFacing);
        if (start == null) {
            return Plan.fail(PlankRun.Failure.NO_START, -1);
        }
        Body home = PlankBodies.at(level, clicked);
        if (!home.isWorld()) {
            // from a ship: in its plot, landing on the world or any other ship
            LevelProbe probe = new LevelProbe(level, home, start, home, false, null, s);
            PlankRun.Result r = PlankRun.find(s, probe);
            if (!r.found()) return Plan.fail(r);
            Hit hit = probe.hit(r.landI(), r.landDy());
            List<BlockPos> cells = cells(start, r);
            Vec3 tipWorld = home.toWorld(Vec3.atCenterOf(cells.get(cells.size() - 1)));
            return new Plan(PlankRun.Failure.NONE, -1, home, cells, pitches(r), start.direction(), r.landing(), clicked,
                    hit.body(), hit.pos(), hit.body().toLocal(tipWorld));
        }
        // from the world: anything to land on, the world only across a gap
        LevelProbe probe = new LevelProbe(level, Body.WORLD, start, Body.WORLD, true, null, s);
        PlankRun.Result r = PlankRun.find(s, probe);
        if (!r.found()) return Plan.fail(r);
        Hit hit = probe.hit(r.landI(), r.landDy());
        if (hit.body().isWorld()) {
            List<BlockPos> cells = cells(start, r);
            return new Plan(PlankRun.Failure.NONE, -1, Body.WORLD, cells, pitches(r), start.direction(), r.landing(),
                    clicked, Body.WORLD, hit.pos(), Vec3.atCenterOf(cells.get(cells.size() - 1)));
        }
        // onto ship B: plan again in B's frame from the same start, landing on B only, then lay it from B's end
        Body ship = hit.body();
        PlankRun.Start onShip = new PlankRun.Start(BlockPos.containing(ship.toLocal(Vec3.atCenterOf(start.first()))),
                ship.toLocal(start.direction()));
        LevelProbe shipProbe = new LevelProbe(level, ship, onShip, Body.WORLD, false, ship, s);
        PlankRun.Result rs = PlankRun.find(s, shipProbe);
        if (!rs.found()) return Plan.fail(rs);
        Hit landing = shipProbe.hit(rs.landI(), rs.landDy());
        List<BlockPos> fromShore = cells(onShip, rs);
        List<BlockPos> cells = new ArrayList<>(fromShore.size());
        List<PlankRun.Pitch> pitches = new ArrayList<>(fromShore.size());
        for (int k = fromShore.size() - 1; k >= 0; k--) {
            cells.add(fromShore.get(k));
            pitches.add(rs.cells().get(k).pitch().reversed());
        }
        Vec3 shoreEnd = ship.toWorld(Vec3.atCenterOf(fromShore.get(0)));
        return new Plan(PlankRun.Failure.NONE, -1, ship, cells, pitches, onShip.direction().getOpposite(), rs.landing(),
                landing.pos(), Body.WORLD, clicked, shoreEnd);
    }

    private static List<BlockPos> cells(PlankRun.Start start, PlankRun.Result r) {
        List<BlockPos> out = new ArrayList<>(r.length());
        for (int i = 0; i < r.length(); i++) {
            out.add(start.cell(i, r.cells().get(i).dy()).immutable());
        }
        return out;
    }

    private static List<PlankRun.Pitch> pitches(PlankRun.Result r) {
        List<PlankRun.Pitch> out = new ArrayList<>(r.length());
        for (PlankRun.Cell c : r.cells()) out.add(c.pitch());
        return out;
    }

    /** A sturdy top the probe saw: the body and the block's position in that body's frame. */
    record Hit(Body body, BlockPos pos) {
    }

    /**
     * Reads one frame's cells for {@link PlankRun}. {@code frame} is where the cells lie, {@code home} the body the run
     * starts on (its sturdy tops are {@link PlankRun.Ground#HOME} when {@code homeLandable}, else obstacles), and
     * {@code only} (when set) the one body the run may land on; otherwise any body but home.
     */
    private static final class LevelProbe implements PlankRun.Probe {
        private final Level level;
        private final Body frame;
        private final PlankRun.Start start;
        private final Body home;
        private final boolean homeLandable;
        private final @Nullable Body only;
        private final List<Body> others = new ArrayList<>();
        private final Map<Long, Boolean> free = new HashMap<>();
        private final Map<Long, PlankRun.Ground> ground = new HashMap<>();
        private final Map<Long, Hit> hits = new HashMap<>();

        LevelProbe(Level level, Body frame, PlankRun.Start start, Body home, boolean homeLandable, @Nullable Body only,
                   PlankRun.Settings s) {
            this.level = level;
            this.frame = frame;
            this.start = start;
            this.home = home;
            this.homeLandable = homeLandable;
            this.only = only;
            int reach = s.maxLength() + PlankRun.FAR_LOOK;
            int h = s.maxRise() + PlankRun.STEEP_LOOK + 2;
            AABB area = new AABB(frame.toWorld(Vec3.atCenterOf(start.cell(0))), frame.toWorld(Vec3.atCenterOf(start.cell(reach))))
                    .inflate(1.0, h, 1.0);
            for (Body b : PlankBodies.shipsNear(level, area, SHIP_MARGIN)) {
                if (!b.same(frame)) others.add(b);
            }
        }

        private static long key(int i, int dy) {
            return ((long) i << 32) | (dy & 0xffffffffL);
        }

        private BlockPos local(int i, int dy) {
            return start.cell(i, dy);
        }

        private Vec3 world(int i, int dy) {
            return frame.toWorld(Vec3.atCenterOf(local(i, dy)));
        }

        private boolean collides(BlockPos pos) {
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        /** Water in this cell and the one above: no plank goes under the surface, nor lands there. */
        private boolean deepWater(BlockPos pos) {
            return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER);
        }

        @Override
        public boolean free(int i, int dy) {
            return free.computeIfAbsent(key(i, dy), k -> computeFree(i, dy));
        }

        private boolean computeFree(int i, int dy) {
            BlockPos local = local(i, dy);
            if (!frame.contains(local)) {
                return false;
            }
            BlockState here = level.getBlockState(local);
            if (!here.canBeReplaced() || here.is(BoardingContent.PLANK.get()) || collides(local)) {
                return false;
            }
            Vec3 w = world(i, dy);
            if (frame.isWorld()) {
                if (deepWater(local)) return false;
            } else {
                BlockPos wp = BlockPos.containing(w);
                if (collides(wp) || deepWater(wp)) return false; // land, a quay, anything of the world
            }
            for (Body s : others) {
                if (collides(BlockPos.containing(s.toLocal(w)))) return false;
            }
            return true;
        }

        @Override
        public boolean open(int i, int dy) {
            BlockPos local = local(i, dy);
            if (collides(local)) return false;
            Vec3 w = world(i, dy);
            if (!frame.isWorld() && collides(BlockPos.containing(w))) return false;
            for (Body s : others) {
                if (collides(BlockPos.containing(s.toLocal(w)))) return false;
            }
            return true;
        }

        @Override
        public PlankRun.Ground ground(int i, int dy) {
            return ground.computeIfAbsent(key(i, dy), k -> computeGround(i, dy));
        }

        private PlankRun.Ground computeGround(int i, int dy) {
            Vec3 w = world(i, dy);
            PlankRun.Ground best = look(frame, local(i, dy), i, dy, PlankRun.Ground.NONE);
            if (!frame.isWorld()) {
                best = look(Body.WORLD, BlockPos.containing(w), i, dy, best);
            }
            for (Body s : others) {
                best = look(s, BlockPos.containing(s.toLocal(w)), i, dy, best);
            }
            return best;
        }

        /** What body {@code b}'s block at {@code pos} (its frame) is to this run, kept if it beats {@code best}. */
        private PlankRun.Ground look(Body b, BlockPos pos, int i, int dy, PlankRun.Ground best) {
            BlockState st = level.getBlockState(pos);
            PlankRun.Ground g;
            if (b.isWorld() && deepWater(pos)) {
                g = PlankRun.Ground.SOLID;
            } else if (st.getCollisionShape(level, pos).isEmpty()) {
                g = PlankRun.Ground.NONE;
            } else if (!st.isFaceSturdy(level, pos, Direction.UP) || st.is(BoardingContent.PLANK.get())) {
                g = PlankRun.Ground.SOLID;
            } else if (b.same(home)) {
                g = homeLandable ? PlankRun.Ground.HOME : PlankRun.Ground.SOLID;
            } else if (only != null) {
                g = b.same(only) ? PlankRun.Ground.STURDY : PlankRun.Ground.SOLID;
            } else {
                g = PlankRun.Ground.STURDY;
            }
            if (g.landable() && !best.landable()) {
                hits.put(key(i, dy), new Hit(b, pos.immutable()));
                return g;
            }
            return g.ordinal() > best.ordinal() && !best.landable() ? g : best;
        }

        /** The body and block behind a landable answer of {@link #ground} at {@code (i, dy)}. */
        Hit hit(int i, int dy) {
            ground(i, dy);
            Hit h = hits.get(key(i, dy));
            if (h == null) throw new IllegalStateException("no landing block at " + i + "," + dy);
            return h;
        }
    }
}
