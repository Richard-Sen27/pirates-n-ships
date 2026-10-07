package com.richardsenger.piratesnships.chart.data;

/**
 * One sampling pass (work package MAP1): a rectangle of cells starting at cell {@code (minCx, minCz)}, {@code w} by
 * {@code h}, each holding a {@link CellClass} ordinal ({@code UNKNOWN} = not sampled: outside the circle or in an
 * unloaded chunk). Row-major: index {@code z * w + x}.
 */
public record SampleGrid(int minCx, int minCz, int w, int h, byte[] classes) {

    public SampleGrid {
        if (w < 0 || h < 0 || classes.length != w * h) throw new IllegalArgumentException("sample grid " + w + "x" + h + " with " + classes.length + " cells");
    }

    public static SampleGrid empty(int minCx, int minCz, int w, int h) {
        return new SampleGrid(minCx, minCz, w, h, new byte[w * h]);
    }

    public CellClass get(int cx, int cz) {
        int x = cx - minCx;
        int z = cz - minCz;
        if (x < 0 || z < 0 || x >= w || z >= h) return CellClass.UNKNOWN;
        return CellClass.of(classes[z * w + x]);
    }

    public void set(int cx, int cz, CellClass cls) {
        classes[(cz - minCz) * w + (cx - minCx)] = (byte) cls.ordinal();
    }

    public int sampledCount() {
        int n = 0;
        for (byte b : classes) if (b != 0) n++;
        return n;
    }
}
