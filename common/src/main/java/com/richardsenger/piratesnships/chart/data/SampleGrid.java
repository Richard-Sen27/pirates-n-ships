package com.richardsenger.piratesnships.chart.data;

/**
 * One sampling pass (work package MAP1): a rectangle of cells starting at cell {@code (minCx, minCz)}, {@code w} by
 * {@code h}, each holding a {@link CellClass} ordinal ({@code UNKNOWN} = not sampled: outside the circle or in an
 * unloaded chunk). Row-major: index {@code z * w + x}. CHART4: beside each class the cell's packed vanilla map colour
 * ({@code colours}, 0 = none), merged with the class. CHART5: a class byte may carry {@link ChartCells#BUILT}.
 */
public record SampleGrid(int minCx, int minCz, int w, int h, byte[] classes, byte[] colours) {

    public SampleGrid {
        if (w < 0 || h < 0 || classes.length != w * h || colours.length != w * h) {
            throw new IllegalArgumentException("sample grid " + w + "x" + h + " with " + classes.length + " cells and " + colours.length + " colours");
        }
    }

    /** A grid of classes without colours (what a chart sampled before CHART4 looked like). */
    public SampleGrid(int minCx, int minCz, int w, int h, byte[] classes) {
        this(minCx, minCz, w, h, classes, new byte[classes.length]);
    }

    public static SampleGrid empty(int minCx, int minCz, int w, int h) {
        return new SampleGrid(minCx, minCz, w, h, new byte[w * h], new byte[w * h]);
    }

    public CellClass get(int cx, int cz) {
        int x = cx - minCx;
        int z = cz - minCz;
        if (x < 0 || z < 0 || x >= w || z >= h) return CellClass.UNKNOWN;
        return CellClass.of(classes[z * w + x] & ChartCells.CLASS_MASK);
    }

    /** CHART4: the packed map colour sampled for cell {@code (cx, cz)}, 0 when none (or outside the grid). */
    public int colour(int cx, int cz) {
        int x = cx - minCx;
        int z = cz - minCz;
        if (x < 0 || z < 0 || x >= w || z >= h) return 0;
        return colours[z * w + x] & 0xFF;
    }

    public void set(int cx, int cz, CellClass cls) {
        classes[(cz - minCz) * w + (cx - minCx)] = (byte) cls.ordinal();
    }

    /** CHART4: sets the class and the packed map colour (0 = none) of cell {@code (cx, cz)}. */
    public void set(int cx, int cz, CellClass cls, int colour) {
        int i = (cz - minCz) * w + (cx - minCx);
        classes[i] = (byte) cls.ordinal();
        colours[i] = (byte) colour;
    }

    /** CHART5: {@link #set(int, int, CellClass, int)} with the built flag. */
    public void set(int cx, int cz, CellClass cls, int colour, boolean built) {
        set(cx, cz, cls, colour);
        if (built) classes[(cz - minCz) * w + (cx - minCx)] |= (byte) ChartCells.BUILT;
    }

    /** CHART5: whether cell {@code (cx, cz)} was sampled as built. */
    public boolean built(int cx, int cz) {
        int x = cx - minCx;
        int z = cz - minCz;
        return x >= 0 && z >= 0 && x < w && z < h && (classes[z * w + x] & ChartCells.BUILT) != 0;
    }

    public int sampledCount() {
        int n = 0;
        for (byte b : classes) if (b != 0) n++;
        return n;
    }
}
