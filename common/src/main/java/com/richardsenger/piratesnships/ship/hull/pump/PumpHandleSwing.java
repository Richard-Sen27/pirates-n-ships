package com.richardsenger.piratesnships.ship.hull.pump;

/**
 * The drawn swing of one bilge pump's handle (PMP1), client state: while the pump is worked the handle rocks from the
 * top of its stroke (rest) down by the stroke angle and back, {@code (1 - cos) / 2} over one stroke period, so it
 * starts and turns smoothly; when the pumping stops, the swing's size eases to zero over half a stroke (smoothstep)
 * while the rocking runs on, so the handle settles at rest from wherever it was. Pumping again before it has settled
 * grows the size back from where it is without restarting the stroke. Pure state on game ticks (with the partial
 * tick), no world access; one per client {@link BilgePumpBlockEntity}, unit tested.
 */
public final class PumpHandleSwing {

    private boolean pumping;
    /** Game time the running stroke began (the handle at rest). */
    private double phaseStart;
    private double sizeFrom;
    private double sizeTo;
    private double sizeStart;

    /**
     * The swing in degrees (0 = rest, {@code strokeDegrees} = the bottom of the stroke) to draw at {@code nowTicks}.
     *
     * @param pumping       whether the pump is being worked (the synced flag)
     * @param strokeTicks   ticks of one full stroke, down and up (at least 1)
     * @param strokeDegrees how far the handle goes down
     */
    public double swing(boolean pumping, double nowTicks, double strokeTicks, double strokeDegrees) {
        double period = Math.max(1.0, strokeTicks);
        double ease = period / 2;
        if (pumping != this.pumping) {
            double size = size(nowTicks, ease);
            if (pumping && size <= 0) {
                phaseStart = nowTicks;
                sizeFrom = 1;
            } else {
                sizeFrom = size;
            }
            sizeTo = pumping ? 1 : 0;
            sizeStart = nowTicks;
            this.pumping = pumping;
        }
        double size = size(nowTicks, ease);
        if (size <= 0) {
            return 0;
        }
        double phase = (nowTicks - phaseStart) / period;
        return strokeDegrees * size * (1 - Math.cos(2 * Math.PI * phase)) / 2;
    }

    private double size(double now, double ease) {
        double t = Math.max(0.0, Math.min(1.0, (now - sizeStart) / ease));
        return sizeFrom + (sizeTo - sizeFrom) * t * t * (3 - 2 * t);
    }
}
