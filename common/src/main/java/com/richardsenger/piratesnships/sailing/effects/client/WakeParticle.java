package com.richardsenger.piratesnships.sailing.effects.client;

import com.richardsenger.piratesnships.sailing.effects.WakeRules;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;

/**
 * A foam quad of a ship's wake (WAKE1): a {@link SeaStreakParticle} whose long side and velocity point different ways
 * (a stern quad lies along the course and drifts sideways; a bow wave quad lies along its arm and moves straight out),
 * which keeps {@code friction} of its velocity each tick, and which fades in at once and out slowly
 * ({@link WakeRules#fade}). World-fixed: once born it no longer follows the ship, so a turning ship leaves a curved
 * wake. Spawned directly into the particle engine by {@link WakeClient}; no particle type, nothing to register.
 *
 * <p>A bow wave quad is born at the stem, and a hull blunter than the Kelvin angle sails over it for a moment: under
 * the planks it is hidden by the hull's own blocks, but in a hollow hull it would show in the hold. So for the first
 * {@code occlusionTicks} (about the time the hull takes to pass) it checks every other tick whether it is inside a
 * dry-hull region ({@link WaterRegions#isOccluded}, the rule the spawner uses) and disappears if so.
 */
public final class WakeParticle extends SeaStreakParticle {

    private final int occlusionTicks;

    /**
     * @param axisX          x of the unit horizontal direction of the quad's long side
     * @param axisZ          z of that direction
     * @param vx             x velocity [blocks/tick]
     * @param vy             vertical velocity [blocks/tick] (a splash rises; foam on the water has 0)
     * @param vz             z velocity [blocks/tick]
     * @param friction       share of the velocity kept per tick
     * @param occlusionTicks re-check the dry-hull regions for this many ticks (0: never)
     */
    public WakeParticle(ClientLevel level, double x, double y, double z, Shape shape, double axisX, double axisZ,
                        double vx, double vy, double vz, double length, double width, double peakAlpha, int lifetime,
                        double friction, int occlusionTicks, TextureAtlasSprite sprite) {
        super(level, x, y, z, shape, axisX, axisZ, 0.0, 0.0, length, width, peakAlpha, lifetime, sprite);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = (float) friction;
        this.occlusionTicks = occlusionTicks;
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed && age <= occlusionTicks && (age & 1) == 0
                && WaterRegions.isOccluded(level, new Vec3(x, y - 0.3, z))) {
            remove();
        }
    }

    @Override
    protected double fade(double age) {
        return WakeRules.fade(age, lifetime);
    }
}
