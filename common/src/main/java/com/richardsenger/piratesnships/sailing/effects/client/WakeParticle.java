package com.richardsenger.piratesnships.sailing.effects.client;

import com.richardsenger.piratesnships.hazards.waves.client.surface.SeaSurface;
import com.richardsenger.piratesnships.sailing.effects.WakeRules;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A foam quad of a ship's wake (WAKE1): a {@link SeaStreakParticle} whose long side and velocity point different ways
 * (a stern quad lies along the course and drifts sideways; a bow wave quad lies along its arm and moves straight out),
 * which keeps {@code friction} of its velocity each tick, and which fades in at once and out slowly
 * ({@link WakeRules#fade}). World-fixed: once born it no longer follows the ship, so a turning ship leaves a curved
 * wake. Spawned directly into the particle engine by {@link WakeClient}; no particle type, nothing to register.
 *
 * <p>While WAV3's surface is on ({@code riding}), a flat quad rises and falls with it like the foam
 * ({@link SurfaceFoamParticle}): every tick its height moves to the drawn surface under the point it drifts to plus
 * {@link WakeSurface#RIDING_LIFT}; if the surface goes off mid-life it stays where it is.
 *
 * <p>A bow wave quad is born at the stem, and a hull blunter than the Kelvin angle sails over it for a moment: under
 * the planks it is hidden by the hull's own blocks, but in a hollow hull it would show in the hold. So for the first
 * {@code occlusionTicks} (about the time the hull takes to pass) it checks every other tick whether it is inside a
 * dry-hull region ({@link WaterRegions#isOccluded}, the rule the spawner uses) and disappears if so.
 */
public final class WakeParticle extends SeaStreakParticle {

    private final int occlusionTicks;
    private final boolean riding;

    /**
     * @param axisX          x of the unit horizontal direction of the quad's long side
     * @param axisZ          z of that direction
     * @param vx             x velocity [blocks/tick]
     * @param vy             vertical velocity [blocks/tick] (a splash rises; foam on the water has 0)
     * @param vz             z velocity [blocks/tick]
     * @param friction       share of the velocity kept per tick
     * @param occlusionTicks re-check the dry-hull regions for this many ticks (0: never)
     * @param riding         follow WAV3's moving surface (flat quads while the surface is on)
     */
    public WakeParticle(ClientLevel level, double x, double y, double z, Shape shape, double axisX, double axisZ,
                        double vx, double vy, double vz, double length, double width, double peakAlpha, int lifetime,
                        double friction, int occlusionTicks, boolean riding, TextureAtlasSprite sprite) {
        super(level, x, y, z, shape, axisX, axisZ, 0.0, 0.0, length, width, peakAlpha, lifetime, sprite);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = (float) friction;
        this.occlusionTicks = occlusionTicks;
        this.riding = riding;
    }

    /**
     * The point inside the sea-level water block at {@code (x, z)} that tells whether it lies in a dry hull (the foam's
     * probe: 0.3 below the still top), independent of how high the moving surface stands there.
     */
    static Vec3 hullProbe(Level level, double x, double z) {
        return new Vec3(x, level.getSeaLevel() - 1.0 + 8.0 / 9.0 - 0.3, z);
    }

    @Override
    public void tick() {
        if (riding) {
            double target = SeaSurface.surfaceY(x + xd, z + zd, level.getGameTime() + 1.0);
            yd = Double.isNaN(target) ? 0.0 : target + WakeSurface.RIDING_LIFT - y;
        }
        super.tick();
        if (!removed && age <= occlusionTicks && (age & 1) == 0
                && WaterRegions.isOccluded(level, hullProbe(level, x, z))) {
            remove();
        }
    }

    @Override
    protected double fade(double age) {
        return WakeRules.fade(age, lifetime);
    }
}
