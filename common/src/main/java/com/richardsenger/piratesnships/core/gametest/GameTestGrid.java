package com.richardsenger.piratesnships.core.gametest;

import java.util.List;

/**
 * Where the headless GameTest server lays out its tests (pure logic, used by {@code mixin/MixinGameTestServer}).
 *
 * <p>Why: Sable's physics is 32-bit ({@code refs/sable/sable_rapier/src/main/rust/marten/src/lib.rs}: {@code Real =
 * f32}), so how a slow ship moves depends on how far from the origin it sails. Vanilla's grid spawner
 * ({@code StructureGridSpawner}, 8 tests per row, never reusing a cell) starts at one random corner and grows only
 * towards +z: with about 1,300 tests the suite ran over 4,900 blocks of z, and with the corner drawn within ±4,096 the
 * last rows reached z 9,000. Past z 8,192 an f32 step is 1/1024 block, and the helm tests measured it (TST1): the
 * two-waypoint course arrived after 400 ticks instead of 660 and a ship pressed against the basin wall kept a
 * velocity of 0.3 m/s without moving, so it was never reported stuck. Which test landed out there depended on the
 * random corner and on how many tests ran before it, so any package that added tests could make an unrelated
 * physics test fail at random.
 *
 * <p>The fix lays the same grid out as a square centred on the origin: the number of tests per row is chosen so that
 * the simulated grid is as wide as it is deep, and its north-west corner is put at minus half its size. The whole
 * suite then stays within about ±1,000 blocks, the same precision band for every run and every order.
 * {@link #inSafeRange} is the guard {@code ModGameTests} checks before each test on the GameTest server.
 */
public final class GameTestGrid {

    /** Blocks between two tests of a row ({@code StructureGridSpawner.SPACE_BETWEEN_COLUMNS}). */
    static final int COLUMN_GAP = 5;
    /** Blocks between two rows ({@code StructureGridSpawner.SPACE_BETWEEN_ROWS}). */
    static final int ROW_GAP = 6;
    /** Vanilla's tests per row; the least we use. */
    static final int MIN_PER_ROW = 8;
    /**
     * A test's north-west corner must lie within this many blocks of the origin on x and z. 4,096 is where the
     * suite's physics numbers were calibrated (an f32 step of 1/2048 block); 256 blocks are left for the test's own
     * size and the courses its ships sail beyond it.
     */
    public static final int SAFE_RANGE = 4_096 - 256;

    /** A grid layout: tests per row, the north-west corner and the grid's size on x and z. */
    public record Plan(int perRow, int startX, int startZ, int width, int depth) {
    }

    private GameTestGrid() {
    }

    /**
     * The layout for tests of the given footprints ({@code sizes[i] = {x, z}}, in the order the runner spawns them):
     * the tests per row giving the most square grid, centred on the origin.
     */
    public static Plan plan(List<int[]> sizes) {
        int n = sizes.size();
        int bestPerRow = MIN_PER_ROW;
        int[] best = extent(sizes, MIN_PER_ROW);
        for (int perRow = MIN_PER_ROW + 1; perRow <= Math.max(MIN_PER_ROW, n); perRow++) {
            int[] e = extent(sizes, perRow);
            if (Math.max(e[0], e[1]) < Math.max(best[0], best[1])) {
                best = e;
                bestPerRow = perRow;
            }
        }
        return new Plan(bestPerRow, -best[0] / 2, -best[1] / 2, best[0], best[1]);
    }

    /**
     * The size {@code {x, z}} of the grid {@code StructureGridSpawner} builds with {@code perRow} tests per row: each
     * test moves the next one {@code x + 5} east; a full row moves the next row its deepest test plus 6 south.
     */
    static int[] extent(List<int[]> sizes, int perRow) {
        int x = 0, z = 0, rowDepth = 1, inRow = 0, width = 0, depth = 0;
        for (int[] s : sizes) {
            width = Math.max(width, x + s[0]);
            depth = Math.max(depth, z + s[1]);
            rowDepth = Math.max(rowDepth, s[1]);
            x += s[0] + COLUMN_GAP;
            if (++inRow >= perRow) {
                inRow = 0;
                x = 0;
                z += rowDepth + ROW_GAP;
                rowDepth = 1;
            }
        }
        return new int[]{width, depth};
    }

    /** Whether a test whose north-west corner is at ({@code x}, {@code z}) runs at full physics precision. */
    public static boolean inSafeRange(int x, int z) {
        return Math.abs(x) <= SAFE_RANGE && Math.abs(z) <= SAFE_RANGE;
    }
}
