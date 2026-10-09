package com.richardsenger.piratesnships.combat.boarding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure rules of a boarding plank run, the gangway (BRD1, BRD2, docs/design.md §8.3, §8.4): where it starts, which
 * cells it takes, how it climbs or falls, what it lands on, why it is refused, and when it breaks. No world access;
 * {@link PlankPlanner} feeds it through a {@link Probe} from the blocks of the world and of the ships.
 *
 * <p><b>Start.</b> Clicking a side face starts the run in the cell beside that face, at the clicked block's height,
 * running outward; clicking the top face starts it one block up and one block out in the player's facing, so the plank
 * lies level with the top of the clicked block (rail top to rail top, pier top outward). Cells are indexed from the
 * start, {@code (i, dy)}: {@code i} cells out, {@code dy} blocks above the first cell.
 *
 * <p><b>Pitch.</b> Each cell has a {@link Pitch}: flat, the two halves of a 1-in-2 ramp, or a 45° cell. A run is a flat
 * stretch, one ramp of {@code |rise| × cells-per-block} cells climbing or falling {@code rise} blocks
 * ({@code 1 ≤ |rise| ≤ max_rise}), and another flat stretch; the walking surface is continuous from the start to the far
 * end.
 *
 * <p><b>Landings.</b> A smooth run ends on {@link Landing#LEVEL} (its last cell rests on a sturdy block under it) or
 * {@link Landing#RAMP} (its last cell climbs to the top edge of a sturdy block ahead with room above). A stepped run is
 * flat and ends {@link Landing#STEP_DOWN} (a deck one block lower under a free cell) or {@link Landing#STEP_UP} (a deck
 * one block higher ahead, the plank ending against its edge).
 *
 * <p><b>Choice.</b> First the shortest smooth run that is level or uses the configured pitch ({@code rise_run} cells per
 * block); if none, the shortest smooth run with 45° cells ({@code steep_fallback}); if none, the shortest stepped run.
 * Within one length: level before sloped, a smaller rise first, climbing before falling, the ramp as far out as it goes.
 *
 * <p><b>Home ground.</b> When the run starts on the world it may land on the world too ({@link Ground#HOME}), but only
 * across a gap: at least two cells, and no cell before the last one resting on anything (a plank laid along a pier is
 * refused).
 */
public final class PlankRun {

    /** Cells a run can have; the block state's {@code segment} property counts 0..MAX_SEGMENTS-1. */
    public static final int MAX_SEGMENTS = 6;
    /** Highest {@code max_rise} the config allows. */
    public static final int MAX_RISE = 3;
    /** Columns looked at beyond {@code max_length} to tell "too far" from "nothing to land on". */
    static final int FAR_LOOK = 4;
    /** Heights looked at beyond {@code max_rise} to tell "too steep" from "nothing to land on". */
    static final int STEEP_LOOK = 2;

    /**
     * The slope of one cell, as the height of its walking surface at the near edge (toward the start) and the far edge,
     * in half blocks above the cell's floor.
     */
    public enum Pitch implements StringRepresentable {
        FLAT("flat", 0, 0),
        /** The lower half of a 1-in-2 climb. */
        UP_LOW("up_low", 0, 1),
        /** The upper half of a 1-in-2 climb. */
        UP_HIGH("up_high", 1, 2),
        /** A 45° climb of a whole block. */
        UP("up", 0, 2),
        /** A 45° fall of a whole block. */
        DOWN("down", 2, 0),
        /** The upper half of a 1-in-2 fall. */
        DOWN_HIGH("down_high", 2, 1),
        /** The lower half of a 1-in-2 fall. */
        DOWN_LOW("down_low", 1, 0);

        private final String name;
        /** Surface height at the near edge, half blocks above the floor. */
        public final int near;
        /** Surface height at the far edge, half blocks above the floor. */
        public final int far;

        Pitch(String name, int near, int far) {
            this.name = name;
            this.near = near;
            this.far = far;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The same cell walked the other way (laying a run from its far end). */
        public Pitch reversed() {
            return switch (this) {
                case FLAT -> FLAT;
                case UP_LOW -> DOWN_LOW;
                case DOWN_LOW -> UP_LOW;
                case UP_HIGH -> DOWN_HIGH;
                case DOWN_HIGH -> UP_HIGH;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }

        /** The surface reaches the top of the cell somewhere, so whoever walks it needs the cell above free. */
        public boolean tall() {
            return near == 2 || far == 2;
        }
    }

    /** How the far end meets what it lands on. */
    public enum Landing { LEVEL, STEP_DOWN, STEP_UP, RAMP }

    /** Why no run was found. */
    public enum Failure {
        NONE,
        /** No run can start here (the bottom face, a plank, nothing solid, or nothing to bridge from a pier). */
        NO_START,
        /** A cell in the level line is taken ({@link Result#blockedAt}). */
        BLOCKED,
        /** Something to land on lies past {@code max_length}. */
        TOO_FAR,
        /** Something to land on is in reach but higher or lower than a run can climb or fall. */
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
     * The rule's settings: {@code maxLength} cells (clamped to 1..{@link #MAX_SEGMENTS}), {@code maxRise} blocks of
     * climb or fall (0..{@link #MAX_RISE}), {@code riseRun} cells per block of the preferred pitch (1 or 2), whether
     * 45° cells may be used when the preferred pitch finds nothing.
     */
    public record Settings(int maxLength, int maxRise, int riseRun, boolean steepFallback) {
        public Settings {
            maxLength = Math.max(1, Math.min(MAX_SEGMENTS, maxLength));
            maxRise = Math.max(0, Math.min(MAX_RISE, maxRise));
            riseRun = Math.max(1, Math.min(2, riseRun));
        }
    }

    /** One cell of a run: its floor {@code dy} blocks above the first cell's, and its pitch. */
    public record Cell(int dy, Pitch pitch) {
    }

    /**
     * A found run (its cells from the start outward, the landing and the landing block's cell {@code (landI, landDy)}),
     * or a failure; {@code blockedAt} is the index of the taken cell for {@link Failure#BLOCKED}, else -1.
     */
    public record Result(List<Cell> cells, @Nullable Landing landing, int landI, int landDy, Failure failure,
                         int blockedAt) {
        static Result fail(Failure failure, int blockedAt) {
            return new Result(List.of(), null, 0, 0, failure, blockedAt);
        }

        public boolean found() {
            return failure == Failure.NONE;
        }

        public int length() {
            return cells.size();
        }

        /** Index of the far cell. */
        public int tip() {
            return cells.size() - 1;
        }

        /** Blocks the far end's surface lies above the start's (negative when it falls). */
        public double rise() {
            if (cells.isEmpty()) {
                return 0;
            }
            Cell last = cells.get(cells.size() - 1);
            return last.dy() + last.pitch().far / 2.0;
        }
    }

    /** What the run sees, per cell {@code (i, dy)}. */
    public interface Probe {
        /** A plank can go into cell {@code (i, dy)}: replaceable where it lies, nothing solid of anything else there. */
        boolean free(int i, int dy);

        /** Nothing with a collision shape is in cell {@code (i, dy)} (headroom above a tall ramp cell). */
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

    // ------------------------------------------------------------------ profiles

    /**
     * The cells of a run with these pitches whose walking surface starts level with the first cell's floor, or null when
     * the pitches do not join up.
     */
    public static @Nullable List<Cell> profile(List<Pitch> pitches) {
        int surface = 0; // half blocks above the first cell's floor
        List<Cell> out = new ArrayList<>(pitches.size());
        for (Pitch p : pitches) {
            int twice = surface - p.near;
            if (Math.floorMod(twice, 2) != 0) {
                return null;
            }
            int dy = Math.floorDiv(twice, 2);
            out.add(new Cell(dy, p));
            surface = 2 * dy + p.far;
        }
        return out;
    }

    /** The ramp cells for {@code rise} blocks (sign = up or down) at {@code perBlock} cells per block. */
    static List<Pitch> ramp(int rise, int perBlock) {
        List<Pitch> out = new ArrayList<>();
        for (int b = 0; b < Math.abs(rise); b++) {
            if (perBlock == 1) {
                out.add(rise > 0 ? Pitch.UP : Pitch.DOWN);
            } else {
                out.add(rise > 0 ? Pitch.UP_LOW : Pitch.DOWN_HIGH);
                out.add(rise > 0 ? Pitch.UP_HIGH : Pitch.DOWN_LOW);
            }
        }
        return out;
    }

    /**
     * Every run shape of {@code n} cells in the order they are tried: level (when {@code withLevel}), then for each rise
     * 1..{@code maxRise}, climbing then falling, the ramp of {@code perBlock} cells per block at every offset from the far
     * end inward.
     */
    static List<List<Cell>> shapes(int n, int maxRise, int perBlock, boolean withLevel) {
        List<List<Cell>> out = new ArrayList<>();
        if (withLevel) {
            List<Pitch> flat = new ArrayList<>();
            for (int i = 0; i < n; i++) flat.add(Pitch.FLAT);
            out.add(profile(flat));
        }
        for (int r = 1; r <= maxRise; r++) {
            for (int sign : new int[]{1, -1}) {
                List<Pitch> ramp = ramp(sign * r, perBlock);
                int m = ramp.size();
                for (int o = n - m; o >= 0; o--) {
                    List<Pitch> p = new ArrayList<>(n);
                    for (int i = 0; i < o; i++) p.add(Pitch.FLAT);
                    p.addAll(ramp);
                    while (p.size() < n) p.add(Pitch.FLAT);
                    List<Cell> cells = profile(p);
                    if (cells != null) out.add(cells);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the finder

    private enum Phase { SMOOTH, STEP }

    /** The run to lay: see the class comment for the order of choice. */
    public static Result find(Settings s, Probe probe) {
        Result r = search(s, probe, s.riseRun(), true, Phase.SMOOTH);
        if (r == null && s.riseRun() > 1 && s.steepFallback()) {
            r = search(s, probe, 1, false, Phase.SMOOTH);
        }
        if (r == null) {
            r = search(s, probe, 1, true, Phase.STEP);
        }
        return r != null ? r : diagnose(s, probe);
    }

    private static @Nullable Result search(Settings s, Probe probe, int perBlock, boolean withLevel, Phase phase) {
        for (int n = 1; n <= s.maxLength(); n++) {
            List<List<Cell>> shapes = phase == Phase.STEP
                    ? shapes(n, 0, 1, true)
                    : shapes(n, s.maxRise(), perBlock, withLevel);
            for (List<Cell> cells : shapes) {
                Result r = evaluate(cells, probe, phase);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** The run of these cells if every cell fits and the last one lands as the phase allows, else null. */
    private static @Nullable Result evaluate(List<Cell> cells, Probe probe, Phase phase) {
        int n = cells.size();
        for (int i = 0; i < n; i++) {
            Cell c = cells.get(i);
            if (!probe.free(i, c.dy())) return null;
            if (c.pitch().tall() && !probe.open(i, c.dy() + 1)) return null;
        }
        int t = n - 1;
        Cell tip = cells.get(t);
        Landing landing = null;
        int li = t, ld = 0;
        if (phase == Phase.SMOOTH) {
            if (tip.pitch().far == 0 && probe.ground(t, tip.dy() - 1).landable()) {
                landing = Landing.LEVEL;
                ld = tip.dy() - 1;
            } else if (tip.pitch().far == 2 && probe.ground(t + 1, tip.dy()).landable()
                    && probe.ground(t + 1, tip.dy() + 1) == Ground.NONE) {
                landing = Landing.RAMP;
                li = t + 1;
                ld = tip.dy();
            }
        } else if (tip.pitch() == Pitch.FLAT) {
            if (probe.ground(t, tip.dy() - 1) == Ground.NONE && probe.ground(t, tip.dy() - 2).landable()) {
                landing = Landing.STEP_DOWN;
                ld = tip.dy() - 2;
            } else if (probe.ground(t + 1, tip.dy()).landable() && probe.ground(t + 1, tip.dy() + 1) == Ground.NONE) {
                landing = Landing.STEP_UP;
                li = t + 1;
                ld = tip.dy();
            }
        }
        if (landing == null) {
            return null;
        }
        if (probe.ground(li, ld) == Ground.HOME && !acrossAGap(cells, probe)) {
            return null;
        }
        return new Result(List.copyOf(cells), landing, li, ld, Failure.NONE, -1);
    }

    /** A run landing on its own home ground has two cells or more and none before the last rests on anything. */
    static boolean acrossAGap(List<Cell> cells, Probe probe) {
        if (cells.size() < 2) return false;
        for (int i = 0; i < cells.size() - 1; i++) {
            if (probe.ground(i, cells.get(i).dy() - 1).landable()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ why not

    /** A deck surface at cell {@code (i, dy)}: something to land on under it, nothing in it. */
    private static boolean surface(Probe probe, int i, int dy) {
        return probe.ground(i, dy - 1).landable() && probe.ground(i, dy) == Ground.NONE;
    }

    /**
     * Why nothing was found: no start (the first cell rests on the start's own ground: nothing to bridge), too
     * steep (a deck surface in reach, up to the first taken cell of the level line, above or below the start), too far (a
     * deck surface within {@code max_rise} just past {@code max_length}), blocked (a taken cell in the level line), or
     * nothing to land on.
     */
    static Result diagnose(Settings s, Probe probe) {
        int max = s.maxLength();
        if (probe.ground(0, -1) == Ground.HOME) {
            return Result.fail(Failure.NO_START, -1);
        }
        int b = max;
        for (int i = 0; i < max; i++) {
            if (!probe.free(i, 0)) {
                b = i;
                break;
            }
        }
        int h = s.maxRise() + STEEP_LOOK;
        for (int i = 0; i <= Math.min(b, max - 1); i++) {
            for (int dy = -h; dy <= h; dy++) {
                if (dy != 0 && surface(probe, i, dy)) {
                    return Result.fail(Failure.TOO_STEEP, -1);
                }
            }
        }
        if (b >= max) {
            for (int i = max; i < max + FAR_LOOK; i++) {
                for (int dy = -s.maxRise(); dy <= s.maxRise(); dy++) {
                    if (surface(probe, i, dy)) return Result.fail(Failure.TOO_FAR, -1);
                }
                if (!probe.free(i, 0)) break; // the level line runs into something: nothing past it counts
            }
            return Result.fail(Failure.NOTHING_TO_LAND, -1);
        }
        return Result.fail(Failure.BLOCKED, b);
    }

    // ------------------------------------------------------------------ the walking surface

    /**
     * The collision steps of a cell of this pitch, in pixels: {@code {u0, u1, bottom, top}} per step, {@code u} along the
     * run from the near edge. A flat cell is one board {@code thickness} thick; a ramp cell is a staircase under its slope
     * (two steps for a half of a 1-in-2 ramp, four for a 45° cell), each step's top {@code thickness} above the slope's
     * higher end over that step (at most the cell's top) and {@code depth} deep at most.
     */
    public static List<double[]> steps(Pitch pitch, double thickness, double depth) {
        int n = pitch == Pitch.FLAT ? 1 : pitch.near + pitch.far == 2 ? 4 : 2;
        List<double[]> out = new ArrayList<>(n);
        double near = pitch.near * 8.0, far = pitch.far * 8.0;
        for (int k = 0; k < n; k++) {
            double u0 = 16.0 * k / n, u1 = 16.0 * (k + 1) / n;
            double h0 = near + (far - near) * k / n, h1 = near + (far - near) * (k + 1) / n;
            double top = Math.min(16.0, Math.max(h0, h1) + thickness);
            double bottom = pitch == Pitch.FLAT ? 0.0 : Math.max(0.0, Math.min(top - depth, Math.min(h0, h1)));
            out.add(new double[]{u0, u1, bottom, top});
        }
        return out;
    }

    // ------------------------------------------------------------------ the break rule

    /** Whether segment {@code segment} of a run of {@code length} cells is its far end. */
    public static boolean isTip(int segment, int length) {
        return segment == length - 1;
    }

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
