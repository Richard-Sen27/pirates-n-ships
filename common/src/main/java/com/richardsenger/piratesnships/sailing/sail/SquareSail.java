package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.force.SailTrim;

/**
 * A square sail: two yards on one mast column (docs/design.md §5.2, rule F5a). The cloth is a trapezoid between the
 * two yards. Its <b>head</b> is the upper yard's middle block, which carries the trim.
 *
 * <p><b>Cloth coordinates</b> (used for the centroid and by the renderer): {@code u} runs along the yard axis from
 * the center of the head block (positive toward +x or +z), {@code v} runs down from the head block's center. The
 * upper edge spans {@code u ∈ [-upper.negativeExtent, upper.positiveExtent]} at {@code v = 0}, the lower edge
 * {@code u ∈ [-lower.negativeExtent, lower.positiveExtent]} at {@code v = drop}; both yards share the mast column,
 * so their middles are at {@code u = 0}.
 *
 * <p><b>Drawn cloth per trim:</b> furled = none (bundled at the upper yard), half = the upper half of the drop, full =
 * the whole trapezoid. Area = mean of the two yard lengths × drop.
 *
 * @param upper the upper yard (head)
 * @param lower the lower yard
 */
public record SquareSail(YardRow upper, YardRow lower) {

    public SquareSail {
        if (lower.y() >= upper.y()) {
            throw new IllegalArgumentException("the lower yard must be below the upper one");
        }
    }

    /** Vertical distance between the two yards [blocks]. */
    public int drop() {
        return upper.y() - lower.y();
    }

    /** Full cloth area [blocks²]: mean yard length × drop. */
    public double area() {
        return (upper.length() + lower.length()) * 0.5 * drop();
    }

    /** Fraction of the drop the cloth reaches at {@code trim}: 0, 0.5, 1. */
    public static double drawnFraction(SailTrim trim) {
        return switch (trim) {
            case FURLED -> 0.0;
            case HALF -> 0.5;
            case FULL -> 1.0;
        };
    }

    /** Area of the cloth drawn at {@code trim} [blocks²] (the upper part of the trapezoid). */
    public double drawnArea(SailTrim trim) {
        double h = drawnFraction(trim) * drop();
        double w0 = upperWidth();
        double w1 = (lowerWidth() - w0) / drop();
        return w0 * h + w1 * h * h / 2.0;
    }

    /**
     * Centroid of the cloth drawn at {@code trim}, in cloth coordinates {@code {u, v}}. Furled: the middle of the
     * upper yard ({@code v = 0}).
     */
    public double[] centroid(SailTrim trim) {
        double d = drop();
        double h = drawnFraction(trim) * d;
        double w0 = upperWidth();
        double c0 = upperCenter();
        if (h <= 0.0) {
            return new double[] {c0, 0.0};
        }
        double w1 = (lowerWidth() - w0) / d;
        double c1 = (lowerCenter() - c0) / d;
        // width w(v) = w0 + w1 v, center c(v) = c0 + c1 v, integrated over v in [0, h]
        double a = w0 * h + w1 * h * h / 2.0;
        double sv = w0 * h * h / 2.0 + w1 * h * h * h / 3.0;
        double su = c0 * w0 * h + (c0 * w1 + c1 * w0) * h * h / 2.0 + c1 * w1 * h * h * h / 3.0;
        return new double[] {su / a, sv / a};
    }

    /** The cloth shape relative to the head block, for the block entity and the renderer. */
    public ClothGeometry geometry() {
        return new ClothGeometry(upper.alongX(), (float) upper.negativeExtent(), (float) upper.positiveExtent(),
                (float) lower.negativeExtent(), (float) lower.positiveExtent(), drop());
    }

    private double upperWidth() {
        return upper.length();
    }

    private double lowerWidth() {
        return lower.length();
    }

    private double upperCenter() {
        return (upper.positiveExtent() - upper.negativeExtent()) / 2.0;
    }

    private double lowerCenter() {
        return (lower.positiveExtent() - lower.negativeExtent()) / 2.0;
    }
}
