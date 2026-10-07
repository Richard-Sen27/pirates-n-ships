package com.richardsenger.piratesnships.chart.render;

/**
 * Maps between world blocks, chart cells and screen pixels for the chart screen (work package MAP1), pure. The
 * viewport is centred on block {@code (centerX, centerZ)}; {@code zoom} indexes {@link #PIXELS_PER_CELL} (1, 2 or 4 GUI
 * pixels per cell); north is up. The view rectangle is given per call ({@code left, top, width, height}).
 */
public record ChartProjection(double centerX, double centerZ, int zoom, int cellBlocks) {

    /** GUI pixels per cell for each zoom step, widest first. */
    public static final int[] PIXELS_PER_CELL = {1, 2, 4};
    public static final int DEFAULT_ZOOM = 1;

    public ChartProjection {
        zoom = clampZoom(zoom);
        cellBlocks = Math.max(1, cellBlocks);
    }

    public static int clampZoom(int zoom) {
        return Math.max(0, Math.min(PIXELS_PER_CELL.length - 1, zoom));
    }

    public int pixelsPerCell() {
        return PIXELS_PER_CELL[zoom];
    }

    /** World blocks per GUI pixel. */
    public double blocksPerPixel() {
        return cellBlocks / (double) pixelsPerCell();
    }

    /** Screen x of block x (a fraction: the left edge of the block). */
    public double screenX(double blockX, int left, int width) {
        return left + width / 2.0 + (blockX - centerX) / blocksPerPixel();
    }

    public double screenY(double blockZ, int top, int height) {
        return top + height / 2.0 + (blockZ - centerZ) / blocksPerPixel();
    }

    public double blockX(double screenX, int left, int width) {
        return centerX + (screenX - left - width / 2.0) * blocksPerPixel();
    }

    public double blockZ(double screenY, int top, int height) {
        return centerZ + (screenY - top - height / 2.0) * blocksPerPixel();
    }

    /** Screen x of the left edge of cell column {@code cx}. */
    public double cellScreenX(int cx, int left, int width) {
        return screenX((double) cx * cellBlocks, left, width);
    }

    public double cellScreenY(int cz, int top, int height) {
        return screenY((double) cz * cellBlocks, top, height);
    }

    /** The cell under a screen point. */
    public int cellX(double screenX, int left, int width) {
        return (int) Math.floor(blockX(screenX, left, width) / cellBlocks);
    }

    public int cellZ(double screenY, int top, int height) {
        return (int) Math.floor(blockZ(screenY, top, height) / cellBlocks);
    }

    /** The view dragged by {@code (dx, dy)} GUI pixels: the map follows the mouse. */
    public ChartProjection pan(double dx, double dy) {
        return new ChartProjection(centerX - dx * blocksPerPixel(), centerZ - dy * blocksPerPixel(), zoom, cellBlocks);
    }

    public ChartProjection centeredOn(double x, double z) {
        return new ChartProjection(x, z, zoom, cellBlocks);
    }

    /**
     * Another zoom step, keeping the block under screen point {@code (sx, sy)} where it is (the mouse wheel's
     * anchor).
     */
    public ChartProjection zoomed(int newZoom, double sx, double sy, int left, int top, int width, int height) {
        double bx = blockX(sx, left, width);
        double bz = blockZ(sy, top, height);
        ChartProjection z = new ChartProjection(centerX, centerZ, newZoom, cellBlocks);
        // shift so that (bx, bz) lands under (sx, sy) again
        double nx = bx - (sx - left - width / 2.0) * z.blocksPerPixel();
        double nz = bz - (sy - top - height / 2.0) * z.blocksPerPixel();
        return new ChartProjection(nx, nz, z.zoom, cellBlocks);
    }

    /**
     * The centre clamped so the view cannot be dragged away from what is charted: it stays within the bounding box
     * of the known blocks ({@code minX..maxX, minZ..maxZ}), grown by {@code margin} blocks. Without a box (nothing
     * charted) the projection is unchanged.
     */
    public ChartProjection clamped(Bounds known, double margin) {
        if (known == null) return this;
        double x = Math.max(known.minX() - margin, Math.min(known.maxX() + margin, centerX));
        double z = Math.max(known.minZ() - margin, Math.min(known.maxZ() + margin, centerZ));
        return x == centerX && z == centerZ ? this : new ChartProjection(x, z, zoom, cellBlocks);
    }

    /** A box of world blocks. */
    public record Bounds(double minX, double minZ, double maxX, double maxZ) {
        public Bounds include(double x, double z) {
            return new Bounds(Math.min(minX, x), Math.min(minZ, z), Math.max(maxX, x), Math.max(maxZ, z));
        }
    }
}
