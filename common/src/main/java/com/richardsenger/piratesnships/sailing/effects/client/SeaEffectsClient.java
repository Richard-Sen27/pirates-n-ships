package com.richardsenger.piratesnships.sailing.effects.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.waves.ClientWaves;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.effects.FoamRules;
import com.richardsenger.piratesnships.sailing.effects.SeaEffectsConfig;
import com.richardsenger.piratesnships.sailing.effects.SpawnRules;
import com.richardsenger.piratesnships.sailing.effects.WindStreakRules;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Seeing the wind and the waves (WD1, docs/design.md §5.1, §5.4; physical client only): every client tick, white wind
 * streaks ({@code wind_effects.*}) drift with the synced wind ({@link ClientWind}) through the air over the sea around
 * the camera, and faint foam streaks ({@code wave_effects.foam*}) lie on the water along the direction of the synced
 * waves ({@link ClientWaves}), most on the crests. The rates and positions come from the pure {@link WindStreakRules}
 * and {@link FoamRules}. Both respect the video setting "Particles" and stop in calm air and a calm sea. Only renders:
 * nothing here reaches the server or changes gameplay.
 */
public final class SeaEffectsClient {

    /** Sprites of our own in the vanilla particle atlas (textures/particle/*.png, made by tools/gen_sea_effects_textures.py). */
    static final ResourceLocation WIND_STREAK = Constants.id("wind_streak");
    static final ResourceLocation FOAM_STREAK = Constants.id("foam_streak");

    /** Upper limit of streaks or foam spawned in one tick, whatever the config says. */
    static final int MAX_PER_TICK = 48;
    /** Thickness of a wind streak and of a foam streak [blocks]. */
    static final double STREAK_WIDTH = 0.09, FOAM_WIDTH = 0.45;
    /** Opacity of a fully faded-in wind streak. */
    static final double STREAK_ALPHA = 0.32;

    private SeaEffectsClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(SeaEffectsClient::tick);
    }

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || mc.player == null) {
            return;
        }
        boolean streaks = SeaEffectsConfig.STREAKS.get() && ClientWind.hasData();
        boolean foam = HazardConfig.FOAM.get() && ClientWaves.hasData();
        if (!streaks && !foam) {
            return;
        }
        double setting = SpawnRules.particleSetting(mc.options.particles().get().getId());
        if (setting <= 0.0 || !level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        Vec3 cam = camera.getPosition();
        double time = level.getGameTime();
        double seaY = level.getSeaLevel();
        RandomSource r = level.random;
        if (streaks) {
            spawnStreaks(mc, level, cam, seaY, time, setting, r);
        }
        if (foam) {
            spawnFoam(mc, level, cam, seaY, time, setting, r);
        }
    }

    private static void spawnStreaks(Minecraft mc, ClientLevel level, Vec3 cam, double seaY, double time, double setting,
                                     RandomSource r) {
        WindStreakRules rules = SeaEffectsConfig.windStreaks();
        if (!rules.inRange(cam.y, seaY)) {
            return;
        }
        WindSample wind = ClientWind.sample(time);
        int n = Math.min(MAX_PER_TICK, SpawnRules.count(rules.rate(wind.strength(), wind.gust()) * setting, r.nextDouble()));
        if (n == 0) {
            return;
        }
        TextureAtlasSprite sprite = sprite(mc, WIND_STREAK);
        if (sprite == null) {
            return;
        }
        double speed = WindStreakRules.speedPerTick(wind.strength());
        double length = WindStreakRules.length(wind.strength());
        for (int i = 0; i < n; i++) {
            double[] p = rules.start(cam.x, cam.z, wind.dirX(), wind.dirZ(), wind.strength(), r.nextDouble(), r.nextDouble());
            double y = rules.y(seaY, r.nextDouble());
            BlockPos pos = BlockPos.containing(p[0], y, p[1]);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()
                    || WaterRegions.isOccluded(level, new Vec3(p[0], y, p[1]))) {
                continue;
            }
            mc.particleEngine.add(new SeaStreakParticle(level, p[0], y, p[1], SeaStreakParticle.Shape.AIR,
                    wind.dirX(), wind.dirZ(), speed * (0.9 + 0.2 * r.nextDouble()), (r.nextDouble() - 0.5) * 0.01,
                    length * (0.75 + 0.5 * r.nextDouble()), STREAK_WIDTH * (0.8 + 0.4 * r.nextDouble()), STREAK_ALPHA,
                    rules.lifeTicks(), sprite));
        }
    }

    private static void spawnFoam(Minecraft mc, ClientLevel level, Vec3 cam, double seaY, double time, double setting,
                                  RandomSource r) {
        FoamRules rules = SeaEffectsConfig.foam();
        if (!rules.inRange(cam.y, seaY)) {
            return;
        }
        WaveField field = ClientWaves.field(time);
        double amplitude = field.amplitude();
        int n = Math.min(MAX_PER_TICK, SpawnRules.count(rules.rate(amplitude) * setting, r.nextDouble()));
        if (n == 0) {
            return;
        }
        TextureAtlasSprite sprite = sprite(mc, FOAM_STREAK);
        if (sprite == null) {
            return;
        }
        double drift = FoamRules.driftPerTick(amplitude);
        double length = FoamRules.length(amplitude);
        double alpha = FoamRules.opacity(amplitude);
        for (int i = 0; i < n; i++) {
            double[] p = SpawnRules.ringPoint(cam.x, cam.z, FoamRules.INNER_RADIUS, rules.radius(), r.nextDouble(), r.nextDouble());
            // the crests near the camera: the field anchored at the camera (as the server anchors it at a ship)
            if (r.nextDouble() >= FoamRules.crestKeep(field.heightAround(cam.x, cam.z, p[0], p[1], time), amplitude)) {
                continue;
            }
            BlockPos water = BlockPos.containing(p[0], seaY - 1.0, p[1]);
            if (!level.isLoaded(water)) {
                continue;
            }
            FluidState fluid = level.getFluidState(water);
            if (!fluid.is(FluidTags.WATER) || !level.getBlockState(water.above()).isAir()) {
                continue;
            }
            double y = water.getY() + fluid.getHeight(level, water) + FoamRules.LIFT;
            if (WaterRegions.isOccluded(level, new Vec3(p[0], y - 0.3, p[1]))) {
                continue; // inside a dry hull
            }
            double[] dir = SpawnRules.bearing(field.directionDegrees() + (r.nextDouble() - 0.5) * 20.0);
            mc.particleEngine.add(new SeaStreakParticle(level, p[0], y, p[1], SeaStreakParticle.Shape.FLAT,
                    dir[0], dir[1], drift, 0.0, length * (0.7 + 0.6 * r.nextDouble()), FOAM_WIDTH * (0.7 + 0.6 * r.nextDouble()),
                    alpha, rules.lifeTicks(), sprite));
        }
    }

    private static @Nullable TextureAtlasSprite sprite(Minecraft mc, ResourceLocation id) {
        AbstractTexture texture = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        return texture instanceof TextureAtlas atlas ? atlas.getSprite(id) : null;
    }
}
