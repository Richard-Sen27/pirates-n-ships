package com.richardsenger.piratesnships.sailing.effects.client;

import com.richardsenger.piratesnships.hazards.waves.client.surface.SeaSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A foam streak that rides on the visible sea surface (WAV3, WD1's foam): every tick its height moves to the surface
 * under the point it drifts to ({@link SeaSurface#surfaceY}) plus a small lift, so it rises and falls with the waves
 * instead of lying on the still level. Used only while the surface is on; otherwise {@link SeaStreakParticle} as before.
 */
public class SurfaceFoamParticle extends SeaStreakParticle {

    /** Height above the surface [blocks]: above the near ring's triangles, which may sag a little below the bilinear height. */
    public static final double LIFT = 0.06;

    public SurfaceFoamParticle(ClientLevel level, double x, double y, double z, double axisX, double axisZ, double velocity,
                               double length, double width, double peakAlpha, int lifetime, TextureAtlasSprite sprite) {
        super(level, x, y, z, Shape.FLAT, axisX, axisZ, velocity, 0.0, length, width, peakAlpha, lifetime, sprite);
    }

    @Override
    public void tick() {
        double target = SeaSurface.surfaceY(x + xd, z + zd, level.getGameTime() + 1.0);
        yd = Double.isNaN(target) ? 0.0 : target + LIFT - y;
        super.tick();
    }
}
