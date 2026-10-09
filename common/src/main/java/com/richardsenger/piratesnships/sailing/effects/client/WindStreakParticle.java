package com.richardsenger.piratesnships.sailing.effects.client;

import com.richardsenger.piratesnships.sailing.effects.WindStreakStyle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A wind streak in the air (WD2): a {@link SeaStreakParticle.Shape#AIR} streak that flies along its own heading at its
 * own speed and wobbles gently over its flight ({@link WindStreakStyle#wobbleOffset}: mostly up and down, a little
 * sideways), fading in fast and out slowly ({@link WindStreakStyle#fade}). The wobble is added to the straight flight
 * as a per-tick velocity, so the particle still moves through vanilla's {@code move} and interpolates smoothly.
 */
public final class WindStreakParticle extends SeaStreakParticle {

    private final double baseXd, baseZd, wobbleAmplitude, wobblePhase;

    /**
     * @param axisX    x of the unit horizontal heading of the streak (its long side and its flight)
     * @param axisZ    z of that heading
     * @param velocity speed along the heading [blocks/tick]
     */
    public WindStreakParticle(ClientLevel level, double x, double y, double z, double axisX, double axisZ,
                              double velocity, double length, double width, double peakAlpha, int lifetime,
                              double wobbleAmplitude, double wobblePhase, TextureAtlasSprite sprite) {
        super(level, x, y, z, Shape.AIR, axisX, axisZ, velocity, 0.0, length, width, peakAlpha, lifetime, sprite);
        this.baseXd = axisX * velocity;
        this.baseZd = axisZ * velocity;
        this.wobbleAmplitude = wobbleAmplitude;
        this.wobblePhase = wobblePhase;
    }

    @Override
    public void tick() {
        // super.tick moves by (xd, yd, zd) from age to age + 1: the straight flight plus this step of the wobble
        double[] from = WindStreakStyle.wobbleOffset(wobbleAmplitude, wobblePhase, (double) age / lifetime);
        double[] to = WindStreakStyle.wobbleOffset(wobbleAmplitude, wobblePhase, (double) (age + 1) / lifetime);
        double side = to[0] - from[0];
        xd = baseXd - axisZ * side;
        yd = to[1] - from[1];
        zd = baseZd + axisX * side;
        super.tick();
    }

    @Override
    protected double fade(double age) {
        return WindStreakStyle.fade(age, lifetime);
    }
}
