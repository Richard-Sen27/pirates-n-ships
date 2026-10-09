package com.richardsenger.piratesnships.sailing.sail;

import java.util.function.IntSupplier;

/**
 * How fast torn sail cloth ({@link ClothTears}) mends: one cell every {@link #interval()} ticks, 0 = never. The value is
 * the cannon module's ({@code cannons.chain_shot.mend_ticks}, CAN3), which tears the cloth; it hands its config over
 * through {@link #setInterval}, so the sailing module needs no import of the combat module. Never set: cloth does not
 * mend.
 */
public final class SailMending {

    private static volatile IntSupplier interval = () -> 0;

    private SailMending() {
    }

    /** Called once by the module that tears cloth (the cannon module's {@code registerContent}). */
    public static void setInterval(IntSupplier ticks) {
        interval = ticks;
    }

    /** Ticks between two mended cells of one sail; 0 or less = never. */
    public static int interval() {
        return interval.getAsInt();
    }
}
