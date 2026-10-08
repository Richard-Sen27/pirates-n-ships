package com.richardsenger.piratesnships.worldsim.voyage;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Why a voyage ended (WS2; SUNK, CAPTURED come from WS3b). */
public enum VoyageEnd implements StringRepresentable {
    /** Reached its destination (a convoy has sold its cargo). */
    ARRIVED,
    SUNK,
    CAPTURED,
    /** Its lane or a port disappeared. */
    LOST,
    /** Removed by a command or a test. */
    CANCELLED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
