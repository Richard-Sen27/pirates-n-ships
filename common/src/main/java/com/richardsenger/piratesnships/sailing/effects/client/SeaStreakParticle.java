package com.richardsenger.piratesnships.sailing.effects.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.sailing.effects.SpawnRules;
import com.richardsenger.piratesnships.sailing.effects.WindStreakRules;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A long thin translucent streak (WD1): a wind streak in the air or a foam streak lying flat on the water. Spawned
 * directly into the particle engine by {@link SeaEffectsClient} (no particle type, so nothing to register on either
 * loader); its sprite is a texture of our own in the vanilla particle atlas, which stitches every
 * {@code textures/particle/*.png} of every namespace ({@code atlases/particles.json}, a directory source).
 *
 * <p>The quad runs along {@code axis} with the given length. {@link Shape#AIR}: it turns about its axis to face the
 * camera (a beam billboard), so a streak seen from the side is a line and one seen end-on shrinks to a dot, like a real
 * wisp. {@link Shape#FLAT}: it lies in the horizontal plane. Both faces are emitted, so neither depends on culling. The
 * streak flies in a straight line at its spawn velocity (no gravity, no friction, no collision) and fades in and out
 * over its life ({@link SpawnRules#fade}). It is a world particle: a ship sails through it. The wind streaks are the
 * subclass {@link WindStreakParticle} (WD2: wobble and their own fade).
 */
public class SeaStreakParticle extends TextureSheetParticle {

    public enum Shape { AIR, FLAT }

    private final Shape shape;
    protected final float axisX, axisZ;
    private final float halfLength, halfWidth, peakAlpha;

    /**
     * @param axisX     x of the unit horizontal direction of the streak's long side
     * @param axisZ     z of that direction
     * @param velocity  speed along the axis [blocks/tick]
     * @param rise      vertical speed [blocks/tick]
     * @param peakAlpha opacity once faded in
     */
    public SeaStreakParticle(ClientLevel level, double x, double y, double z, Shape shape, double axisX, double axisZ,
                             double velocity, double rise, double length, double width, double peakAlpha, int lifetime,
                             TextureAtlasSprite sprite) {
        super(level, x, y, z);
        this.shape = shape;
        this.axisX = (float) axisX;
        this.axisZ = (float) axisZ;
        this.halfLength = (float) (length * 0.5);
        this.halfWidth = (float) (width * 0.5);
        this.peakAlpha = (float) peakAlpha;
        this.xd = axisX * velocity;
        this.yd = rise;
        this.zd = axisZ * velocity;
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 1.0f;
        this.alpha = 0.0f;
        this.quadSize = this.halfLength; // NeoForge's frustum box for single-quad particles is ± quadSize
        setLifetime(lifetime);
        setSprite(sprite);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float a = (float) (peakAlpha * fade(age + partialTicks));
        if (a <= 0.004f) {
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        float lx = axisX * halfLength, lz = axisZ * halfLength;
        float sx, sy, sz;
        if (shape == Shape.FLAT) {
            sx = -axisZ * halfWidth;
            sy = 0.0f;
            sz = axisX * halfWidth;
        } else {
            double[] side = WindStreakRules.uprightSide(axisX, axisZ, px, py, pz);
            sx = (float) side[0] * halfWidth;
            sy = (float) side[1] * halfWidth;
            sz = (float) side[2] * halfWidth;
        }
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        // front
        vertex(buffer, px - lx - sx, py - sy, pz - lz - sz, u0, v1, a, light);
        vertex(buffer, px - lx + sx, py + sy, pz - lz + sz, u0, v0, a, light);
        vertex(buffer, px + lx + sx, py + sy, pz + lz + sz, u1, v0, a, light);
        vertex(buffer, px + lx - sx, py - sy, pz + lz - sz, u1, v1, a, light);
        // back
        vertex(buffer, px + lx - sx, py - sy, pz + lz - sz, u1, v1, a, light);
        vertex(buffer, px + lx + sx, py + sy, pz + lz + sz, u1, v0, a, light);
        vertex(buffer, px - lx + sx, py + sy, pz - lz + sz, u0, v0, a, light);
        vertex(buffer, px - lx - sx, py - sy, pz - lz - sz, u0, v1, a, light);
    }

    /** Opacity factor in [0, 1] at {@code age} ticks: {@link SpawnRules#fade} (the foam's and WD1's look). */
    protected double fade(double age) {
        return SpawnRules.fade(age, lifetime);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, float a, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, a).setLight(light);
    }
}
