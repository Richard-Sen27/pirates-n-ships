package com.richardsenger.piratesnships.ship.hull;

/**
 * Tiny DSL for hull shapes in tests.
 *
 * <p>Text form: {@link #parse} takes layers from bottom ({@code y = 0}) to top; each layer is rows along {@code z}
 * separated by {@code /}, each row characters along {@code x}: {@code #} solid, {@code .} air, {@code d} closed
 * opening, {@code D} open opening, {@code B} breach. Builder helpers make the usual boxes.
 */
public final class TestHulls {

    private TestHulls() {
    }

    public static HullGrid parse(String... layers) {
        String[] firstRows = layers[0].split("/");
        HullGrid.Builder b = HullGrid.builder(firstRows[0].length(), layers.length, firstRows.length);
        for (int y = 0; y < layers.length; y++) {
            String[] rows = layers[y].split("/");
            for (int z = 0; z < rows.length; z++) {
                for (int x = 0; x < rows[z].length(); x++) {
                    switch (rows[z].charAt(x)) {
                        case '#' -> b.set(x, y, z, CellKind.SOLID);
                        case 'd' -> b.set(x, y, z, CellKind.OPENING, false);
                        case 'D' -> b.set(x, y, z, CellKind.OPENING, true);
                        case 'B' -> b.breach(x, y, z);
                        case '.' -> b.set(x, y, z, CellKind.AIR);
                        default -> throw new IllegalArgumentException("Bad cell char " + rows[z].charAt(x));
                    }
                }
            }
        }
        return b.build();
    }

    /** Solid shell of the inclusive box, air inside. */
    public static HullGrid.Builder closedBox(HullGrid.Builder b, int x0, int y0, int z0, int x1, int y1, int z1) {
        b.fill(x0, y0, z0, x1, y1, z1, CellKind.SOLID);
        return b.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, CellKind.AIR);
    }

    /** Floor and walls of the inclusive box, no roof. */
    public static HullGrid.Builder openBox(HullGrid.Builder b, int x0, int y0, int z0, int x1, int y1, int z1) {
        b.fill(x0, y0, z0, x1, y1, z1, CellKind.SOLID);
        return b.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1, z1 - 1, CellKind.AIR);
    }

    /** 5×5×5 closed box (shell 0..4), interior 3×3×3 at heights 1.5, 2.5, 3.5. */
    public static HullGrid.Builder box5() {
        return closedBox(HullGrid.builder(5, 5, 5), 0, 0, 0, 4, 4, 4);
    }

    /** 5×5×5 open hull: floor y = 0, walls up to y = 4, interior x,z 1..3. Basin is y 1..3, rim layer y = 4. */
    public static HullGrid.Builder openHull5() {
        return openBox(HullGrid.builder(5, 5, 5), 0, 0, 0, 4, 4, 4);
    }
}
