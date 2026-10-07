package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Which piece of the swivel gun a block state's model shows (P2). The placed block is always {@link #YOKE}; the
 * {@link #BARREL} states exist only so that the barrel's model is baked through the block state like any block model
 * and the block entity renderer can draw it turned and raised ({@code client/SwivelGunRenderer}). Nothing places a
 * barrel state; one set by command just behaves like the gun.
 */
public enum SwivelPiece implements StringRepresentable {
    YOKE, BARREL;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
