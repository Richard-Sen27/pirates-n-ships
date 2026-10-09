package com.richardsenger.piratesnships.combat.boarding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure rules of a boarding plank run, the gangway (BRD1, BRD2, BRD4, docs/design.md §8.3, §8.4): where it starts,
 * where it lands, which cells the straight board takes, why it is refused, how its walking surface steps, and when it
 * breaks. No world access; {@link PlankPlanner} feeds it through a {@link Probe} from the blocks of the world and of the
 * ships.
 *
 * <p><b>Start.</b> Clicking a side face starts the run in the cell beside that face, at the clicked block's height,
 * running outward; clicking the top face starts it one block up and one block out in the player's facing, so the plank
 * starts level with the top of the clicked block (rail top to rail top, pier top outward). Cells are indexed from the
 * start, {@code (i, dy)}: column {@code i} out, {@code dy} blocks above the first cell. The board's underside starts at
 * the near edge of column 0 on the first cell's floor.
 *
 * <p><b>One straight board (BRD4).</b> The board runs straight from the start to its landing at a single pitch: over
 * {@code columns} columns it rises (or falls) {@code rise} whole blocks, its underside at
 * {@code rise * u / columns} at {@code u} blocks out. Each column's cells are the ones its underside passes through (one,
 * or two where it crosses a block boundary inside the column, {@link #cells}); each cell knows the underside's height
 * at its near and far edge ({@link Cell#near()}, {@link Cell#far()}).
 *
 * <p><b>Landings.</b> A surface is a landable block {@code (c, R - 1)} with nothing in {@code (c, R)} above it. A surface
 * at the start's height or below ({@code R <= 0}) is landed on: the board's last column lies over it and its far end
 * rests on its top ({@link Landing#LEVEL}, or {@link Landing#STEP_DOWN} when it falls), over {@code c + 1} columns. A
 * surface above ({@code R > 0}) is climbed to: the board ends at its top edge ({@link Landing#RAMP}) over {@code c}
 * columns. The board's walking surface ends 2 px above the landing top, a step of a sixth of what vanilla steps.
 *
 * <p><b>Choice.</b> Surfaces are tried nearest the start's height first (level, then one up, one down, two up, ...), and
 * at one height column by column from the start outward (the first sturdy top edge in the run's direction); the first
 * one whose board is no steeper than {@code max_pitch} and whose cells are free is laid. So a gangway bridges to the far
 * deck or pier rather than diving to the ground of a dry gap below it. If none is, the first surface's reason is given
 * (too steep, or blocked at a cell); a run whose first cell rests on its own pier is "no start" (nothing to bridge).
 *
 * <p><b>Home ground.</b> When the run starts on the world it may land on the world too ({@link Ground#HOME}), but only
 * across a gap: at least two columns, and no column before the last one resting on anything (a plank laid along a pier
 * is refused).
 */
public final class PlankRun {

    /** Columns a run can have ({@code boarding.plank.max_length} at most). */
    public static final int MAX_LENGTH = 6;
    /** Cells a run can have: two per column where the board crosses a block boundary. */
    public static final int MAX_CELLS = 2 * MAX_LENGTH;
    /** Steepest {@code max_pitch} the config allows, degrees. */
    public static final double MAX_PITCH = 45.0;
    /** The board's thickness, blocks (2 px): its walking surface lies this far above its underside. */
    public static final double BOARD = 2.0 / 16.0;
    /** Columns looked at beyond {@code max_length} to tell "too far" from "nothing to land on". */
    static final int FAR_LOOK = 4;
    /** Heights above and below the start looked at for surfaces (beyond 45° over the longest run, for "too steep"). */
    static final int HEIGHT_LOOK = MAX_LENGTH + 2;
    /** Collision steps per cell along the run (each 4 px long). */
    public static final int STEPS_PER_CELL = 4;
    private static final double EPS = 1.0e-6;

    /** How the far end meets what it lands on. */
    public enum Landing {
        /** Resting on a deck at the start's height. */
        LEVEL,
        /** Resting on a lower deck (the board falls onto it). */
        STEP_DOWN,
        /** BRD2: a flat run ending against a higher deck's side. Only in BRD2 saves; BRD4 lays none. */
        STEP_UP,
        /** Climbing to the top edge of a higher deck. */
        RAMP
    }

    /** Why no run was found. */
    public enum Failure {
        NONE,
        /** No run can start here (the bottom face, a plank, nothing solid, or nothing to bridge from a pier). */
        NO_START,
        /** A cell of the board is taken ({@link Result#blockedAt}, {@link Result#blockedDy}). */
        BLOCKED,
        /** Something to land on lies past {@code max_length}. */
        TOO_FAR,
        /** Something to land on is in reach but the board to it would be steeper than {@code max_pitch}. */
        TOO_STEEP,
        /** Nothing to land on within reach. */
        NOTHING_TO_LAND
    }

    /** What a cell holds, as something to land on. */
    public enum Ground {
        /** No collision. */
        NONE,
        /** Collision, but nothing the run may land on (an obstacle, the start's own ship, deep water). */
        SOLID,
        /** A sturdy top of a body other than the one the run starts on, which it may land on. */
        STURDY,
        /** A sturdy top of the world when the run starts on the world: a landing only across a gap. */
        HOME;

        public boolean landable() {
            return this == STURDY || this == HOME;
        }
    }

    /** The first cell of a run (in its frame) and the direction it runs in (horizontal). */
    public record Start(BlockPos first, Direction direction) {
        /** Position of cell {@code i} on the first cell's level. */
        public BlockPos cell(int i) {
            return first.relative(direction, i);
        }

        /** Position of cell {@code i}, {@code dy} blocks above the first cell's level. */
        public BlockPos cell(int i, int dy) {
            return first.relative(direction, i).above(dy);
        }
    }

    /**
     * The rule's settings: {@code maxLength} columns (clamped to 1..{@link #MAX_LENGTH}) and the steepest pitch in
     * degrees (0..{@link #MAX_PITCH}).
     */
    public record Settings(int maxLength, double maxPitch) {
        public Settings {
            maxLength = Math.max(1, Math.min(MAX_LENGTH, maxLength));
            maxPitch = Double.isNaN(maxPitch) ? 0.0 : Math.max(0.0, Math.min(MAX_PITCH, maxPitch));
        }

        /** Blocks of rise per block of run at {@link #maxPitch}. */
        public double maxSlope() {
            return Math.tan(Math.toRadians(maxPitch));
        }

        /** Whether a board rising {@code rise} over {@code columns} is within the pitch. */
        public boolean allows(int rise, int columns) {
            return Math.abs(rise) <= columns * maxSlope() + EPS;
        }
    }

    /**
     * One cell of a run: column {@code i}, floor {@code dy} blocks above the first cell's, and the board's underside
     * {@code near} and {@code far} blocks above this cell's floor at its near and far edge (outside 0..1 where the board
     * leaves the cell through its top or bottom: the cell above or below holds that part).
     */
    public record Cell(int i, int dy, double near, double far) {
        /** The same cell seen from the run's other end (laid the other way). */
        public Cell reversed() {
            return new Cell(i, dy, far, near);
        }
    }

    /**
     * A found run (its cells from the start outward, {@code columns} long, rising {@code rise} whole blocks, the landing
     * and the landing block's cell {@code (landI, landDy)}), or a failure; {@code blockedAt}/{@code blockedDy} is the
     * taken cell for {@link Failure#BLOCKED}, else -1/0.
     */
    public record Result(List<Cell> cells, int columns, int rise, @Nullable Landing landing, int landI, int landDy,
                         Failure failure, int blockedAt, int blockedDy) {
        static Result fail(Failure failure, int blockedAt, int blockedDy) {
            return new Result(List.of(), 0, 0, null, 0, 0, failure, blockedAt, blockedDy);
        }

        public boolean found() {
            return failure == Failure.NONE;
        }

        /** Cells of the run (one or two per column). */
        public int length() {
            return cells.size();
        }

        /** Blocks the far end's underside lies above the start's (negative when it falls). */
        public double riseBlocks() {
            return rise;
        }

        /** The board's pitch, degrees (negative when it falls). */
        public double pitchDegrees() {
            return columns == 0 ? 0.0 : Math.toDegrees(Math.atan2(rise, columns));
        }
    }

    /** What the run sees, per cell {@code (i, dy)}. */
    public interface Probe {
        /** A plank can go into cell {@code (i, dy)}: replaceable where it lies, nothing solid of anything else there. */
        boolean free(int i, int dy);

        /** Nothing with a collision shape is in cell {@code (i, dy)} (room for the board's top above its cells). */
        boolean open(int i, int dy);

        /** What cell {@code (i, dy)} holds as something to land on. */
        Ground ground(int i, int dy);
    }

    private PlankRun() {
    }

    // ------------------------------------------------------------------ start

    /**
     * Where a run laid by clicking {@code face} of the block {@code clicked} starts; {@code playerFacing} is the player's
     * horizontal facing (used for the top face). Null for the bottom face.
     */
    public static @Nullable Start start(BlockPos clicked, Direction face, Direction playerFacing) {
        if (face.getAxis().isHorizontal()) {
            return new Start(clicked.relative(face), face);
        }
        if (face == Direction.UP && playerFacing.getAxis().isHorizontal()) {
            return new Start(clicked.above().relative(playerFacing), playerFacing);
        }
        return null;
    }

    /**
     * RAIL1: the face a click on a gangway opening (a railing port, its outboard side {@code outboard}) counts as: a click
     * on its top by someone facing outboard is a click on its outboard side, so the run starts in the block beyond the
     * opening at the opening's own height (level with the deck it stands on) instead of a block above it. Any other
     * click, or no opening ({@code outboard} null), keeps its face.
     */
    public static Direction throughOpening(Direction face, Direction playerFacing, @Nullable Direction outboard) {
        return outboard != null && face == Direction.UP && playerFacing == outboard ? outboard : face;
    }

    /**
     * RAIL3: whether a block counts as a gangway opening for {@link #throughOpening}: it is in
     * {@code #pirates_n_ships:gangway_openings} ({@code tagged}) and, when it can be opened and shut (a railing gate,
     * {@code openable}), it is {@code open}; a closed gate blocks a plank like a railing.
     */
    public static boolean opening(boolean tagged, boolean openable, boolean open) {
        return tagged && (!openable || open);
    }

    // ------------------------------------------------------------------ the straight board

    /** The board's underside at the near and far edge of column {@code i}, blocks above the first cell's floor. */
    public static double[] column(int i, int columns, int rise) {
        return new double[]{rise * (double) i / columns, rise * (double) (i + 1) / columns};
    }

    /** Floor of the lowest cell of column {@code i}: the cell the underside's lowest point lies in. */
    static int lowestCell(double[] column) {
        return (int) Math.floor(Math.min(column[0], column[1]) + EPS);
    }

    /** Floor of the highest cell of column {@code i}: the underside only touching the next block up does not count. */
    static int highestCell(double[] column) {
        return Math.max(lowestCell(column), (int) Math.ceil(Math.max(column[0], column[1]) - EPS) - 1);
    }

    /**
     * The cells of a straight board rising {@code rise} blocks over {@code columns} columns (a 2D walk along the line,
     * column by column): in each column the cells its underside passes through, in the order the board reaches them
     * (bottom up when it climbs, top down when it falls).
     */
    public static List<Cell> cells(int columns, int rise) {
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            double[] c = column(i, columns, rise);
            int lo = lowestCell(c), hi = highestCell(c);
            if (rise >= 0) {
                for (int dy = lo; dy <= hi; dy++) out.add(new Cell(i, dy, c[0] - dy, c[1] - dy));
            } else {
                for (int dy = hi; dy >= lo; dy--) out.add(new Cell(i, dy, c[0] - dy, c[1] - dy));
            }
        }
        return out;
    }

    /** Whether the board's top in this column reaches into the cell above its highest cell. */
    static boolean topLeavesColumn(double[] column) {
        return Math.max(column[0], column[1]) + BOARD > highestCell(column) + 1 + EPS;
    }

    // ------------------------------------------------------------------ the finder

    /** Heights tried in a column: level, then one up, one down, two up, two down, ... */
    private static int[] heightOrder() {
        int[] out = new int[2 * HEIGHT_LOOK + 1];
        int k = 0;
        out[k++] = 0;
        for (int h = 1; h <= HEIGHT_LOOK; h++) {
            out[k++] = h;
            out[k++] = -h;
        }
        return out;
    }

    private static final int[] HEIGHTS = heightOrder();

    /** A deck surface at cell {@code (i, dy)}: something to land on under it, nothing in it. */
    static boolean surface(Probe probe, int i, int dy) {
        return probe.ground(i, dy - 1).landable() && probe.ground(i, dy) == Ground.NONE;
    }

    /** The run to lay: see the class comment for the order of choice. */
    public static Result find(Settings s, Probe probe) {
        Result firstFail = null;
        int max = s.maxLength();
        for (int rise : HEIGHTS) {
            for (int c = 0; c <= max; c++) {
                int columns = rise > 0 ? c : c + 1;
                if (columns < 1 || columns > max || !surface(probe, c, rise)) continue;
                Result r = evaluate(s, probe, c, rise, columns);
                if (r == null) continue;
                if (r.found()) return r;
                if (firstFail == null) firstFail = r;
            }
        }
        if (firstFail == null || probe.ground(0, -1) == Ground.HOME) {
            // nothing in reach, or a run along its own pier: say why without a surface to blame
            return diagnose(s, probe);
        }
        return firstFail;
    }

    /**
     * The straight board onto the surface at {@code (c, rise)} over {@code columns} columns: the run, a failure (too
     * steep, blocked), or null when it would land on home ground without crossing a gap.
     */
    private static @Nullable Result evaluate(Settings s, Probe probe, int c, int rise, int columns) {
        if (!s.allows(rise, columns)) {
            return Result.fail(Failure.TOO_STEEP, -1, 0);
        }
        List<Cell> cells = cells(columns, rise);
        for (Cell cell : cells) {
            if (!probe.free(cell.i(), cell.dy())) return Result.fail(Failure.BLOCKED, cell.i(), cell.dy());
        }
        for (int i = 0; i < columns; i++) {
            double[] col = column(i, columns, rise);
            if (topLeavesColumn(col) && !probe.open(i, highestCell(col) + 1)) {
                return Result.fail(Failure.BLOCKED, i, highestCell(col) + 1);
            }
        }
        if (probe.ground(c, rise - 1) == Ground.HOME && !acrossAGap(columns, rise, probe)) {
            return null;
        }
        Landing landing = rise > 0 ? Landing.RAMP : rise == 0 ? Landing.LEVEL : Landing.STEP_DOWN;
        return new Result(List.copyOf(cells), columns, rise, landing, c, rise - 1, Failure.NONE, -1, 0);
    }

    /** A run landing on its own home ground has two columns or more and none before the last rests on anything. */
    static boolean acrossAGap(int columns, int rise, Probe probe) {
        if (columns < 2) return false;
        for (int i = 0; i < columns - 1; i++) {
            double[] col = column(i, columns, rise);
            double low = Math.min(col[0], col[1]);
            long floor = Math.round(low);
            if (Math.abs(low - floor) < EPS && probe.ground(i, (int) floor - 1).landable()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ why not

    /**
     * Why nothing was found when no surface lay in reach (or the run lies along its own pier): no start (the first cell rests on the start's own ground:
     * nothing to bridge), blocked (a taken cell in the level line), too far (a surface just past {@code max_length}), or
     * nothing to land on.
     */
    static Result diagnose(Settings s, Probe probe) {
        int max = s.maxLength();
        if (probe.ground(0, -1) == Ground.HOME) {
            return Result.fail(Failure.NO_START, -1, 0);
        }
        for (int i = 0; i < max; i++) {
            if (!probe.free(i, 0)) return Result.fail(Failure.BLOCKED, i, 0);
        }
        for (int i = max; i < max + FAR_LOOK; i++) {
            for (int dy = -HEIGHT_LOOK; dy <= HEIGHT_LOOK; dy++) {
                if (surface(probe, i, dy)) return Result.fail(Failure.TOO_FAR, -1, 0);
            }
            if (!probe.free(i, 0)) break; // the level line runs into something: nothing past it counts
        }
        return Result.fail(Failure.NOTHING_TO_LAND, -1, 0);
    }

    // ------------------------------------------------------------------ the walking surface

    /**
     * The collision steps of one cell, in pixels: {@code {u0, u1, bottom, top}} per step, {@code u} along the run from
     * the near edge. The board's underside runs straight from {@code nearPx} to {@code farPx} above the cell's floor (it
     * may leave the cell); the cell is cut into {@link #STEPS_PER_CELL} steps of 4 px, each from the underside's lower end
     * over that step up to {@code thicknessPx} above its higher end, clipped to the cell (0..16). So the walking surface
     * is a staircase on the board, never more than a quarter block up from one step to the next at 45°, which vanilla's
     * step-up (0.6) and Sable's step logic climb without a jump.
     */
    public static List<double[]> steps(double nearPx, double farPx, double thicknessPx) {
        List<double[]> out = new ArrayList<>(STEPS_PER_CELL);
        for (int k = 0; k < STEPS_PER_CELL; k++) {
            double u0 = 16.0 * k / STEPS_PER_CELL, u1 = 16.0 * (k + 1) / STEPS_PER_CELL;
            double b0 = nearPx + (farPx - nearPx) * k / STEPS_PER_CELL;
            double b1 = nearPx + (farPx - nearPx) * (k + 1) / STEPS_PER_CELL;
            double top = Math.min(16.0, Math.max(b0, b1) + thicknessPx);
            double bottom = Math.max(0.0, Math.min(b0, b1));
            if (top - bottom > 1.0e-3) {
                out.add(new double[]{u0, u1, bottom, top});
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the break rule

    /**
     * Whether a laid run breaks: an end's ship is gone, a block an end rests on is gone, the far end has drifted farther
     * than {@code breakDistance} sideways from the spot it was laid onto, or more than {@code breakRise} up or down (the
     * larger vertical allowance rides out the heave of a moored ship). An unknown (NaN) drift breaks.
     */
    public static boolean breaks(boolean endsPresent, boolean supported, double horizontal, double vertical,
                                 double breakDistance, double breakRise) {
        return !endsPresent || !supported || !(horizontal <= breakDistance) || !(Math.abs(vertical) <= breakRise);
    }
}
