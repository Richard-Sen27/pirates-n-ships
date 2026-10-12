package com.richardsenger.piratesnships.trade.stall;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The stalls of a seafarer village (LIFE1, docs/design.md §10.3): each one a vendor keeps, with its own list of wares
 * ({@link StallWares}). The fish market's counter, the two stalls of the market square (provisions and the
 * chandler's) and the shipwright's bench.
 */
public enum StallKind implements StringRepresentable {
    /** The fish market: fresh and salted fish. */
    FISH,
    /** The market square's west stall: salt pork, hardtack and rum, the stores a ship takes on. */
    PROVISIONS,
    /** The market square's east stall, the ship's chandler: rope, cloth and nails. */
    CHANDLERY,
    /** The shipwright at his bench: hull patches and the shipwright's toolkit. */
    SHIPWRIGHT;

    public static final Codec<StallKind> CODEC = StringRepresentable.fromEnum(StallKind::values);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }
}
