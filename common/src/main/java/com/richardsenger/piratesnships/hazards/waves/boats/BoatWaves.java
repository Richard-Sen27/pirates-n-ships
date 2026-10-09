package com.richardsenger.piratesnships.hazards.waves.boats;

import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.waves.ClientWaves;
import com.richardsenger.piratesnships.hazards.waves.SeaStates;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.hazards.waves.client.surface.SurfaceRules;
import com.richardsenger.piratesnships.hazards.waves.client.surface.SurfaceSupport;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The world side of {@link BoatWaveRule} (WAV6): finds the moving sea under a vanilla boat or a floating item. Called by
 * {@code mixin.MixinBoat} and {@code mixin.MixinItemEntity} on the server and on the client alike: a boat is simulated
 * by the client of the player who drives it and by the server otherwise, and a client predicts item motion between the
 * server's updates, so both sides read the same sea ({@link SeaStates} on the server, the synced {@link ClientWaves} on
 * the client, both unpinned and on the game time) and apply the same offset.
 */
public final class BoatWaves {

    /**
     * The moving sea at one column this tick.
     *
     * @param stillTop vanilla's still water level there [world y]
     * @param offset   the faded wave height [blocks]
     * @param speed    the faded surface's rise over the coming tick [blocks per tick]
     */
    public record Surface(double stillTop, double offset, double speed) {
        /** The moving water level [world y]. */
        public double y() {
            return stillTop + offset;
        }
    }

    /** GameTests only: a sea level for a test level whose world sea level lies below its structures. */
    private static final Map<Level, long[]> TEST_SEA_LEVELS = new WeakHashMap<>();
    private static volatile boolean anyTestSeaLevel;

    private BoatWaves() {
    }

    /** The sea under a boat whose bottom is at {@code minY}, or null where vanilla's flat water applies. */
    public static @Nullable Surface forBoat(Level level, double x, double minY, double z, boolean onGround) {
        return at(level, x, minY, z, onGround, HazardConfig.BOATS.get());
    }

    /** The sea under a floating item whose bottom is at {@code minY}, or null where vanilla applies. */
    public static @Nullable Surface forItem(Level level, double x, double minY, double z, boolean onGround) {
        return at(level, x, minY, z, onGround, HazardConfig.FLOATING_ITEMS.get());
    }

    /**
     * The slope {@code {∂/∂x, ∂/∂z}} of the moving sea under a boat at {@code (x, z)} at time {@code t} (client time with
     * the partial tick, for the renderer), or null where the boat lies on flat water.
     */
    public static double @Nullable [] slope(Level level, double x, double minY, double z, boolean onGround, double t) {
        if (!HazardConfig.BOATS.get() || !HazardConfig.WAVES_ENABLED.get()) {
            return null;
        }
        Column c = column(level, x, minY, z, onGround, field(level));
        if (c == null) {
            return null;
        }
        BoatWaveRule.Slopes sea = level instanceof ServerLevel server ? (px, pz, pt) -> SeaStates.slopeAt(server, px, pz, pt)
                : ClientWaves::slopeAt;
        return BoatWaveRule.slope(sea, x, z, t, c.fade);
    }

    private record Column(double stillTop, double fade) {
    }

    private static @Nullable Surface at(Level level, double x, double minY, double z, boolean onGround, boolean enabled) {
        if (!enabled || !HazardConfig.WAVES_ENABLED.get()) {
            return null;
        }
        WaveField field = field(level);
        Column c = column(level, x, minY, z, onGround, field);
        if (c == null) {
            return null;
        }
        double t = level.getGameTime();
        BoatWaveRule.Heights sea = level instanceof ServerLevel server ? (px, pz, pt) -> SeaStates.heightAt(server, px, pz, pt)
                : ClientWaves::heightAt;
        return new Surface(c.stillTop, BoatWaveRule.offset(sea, x, z, t, c.fade), BoatWaveRule.speed(sea, x, z, t, c.fade));
    }

    private static @Nullable Column column(Level level, double x, double minY, double z, boolean onGround, WaveField field) {
        if (field.isFlat() || !SurfaceSupport.dimensionHasSea(level.dimensionType().hasSkyLight(), level.dimensionType().hasCeiling())) {
            return null;
        }
        int seaLevel = seaLevel(level);
        int y = SurfaceRules.surfaceBlockY(seaLevel);
        double stillTop = y + SurfaceRules.STILL_HEIGHT;
        if (!BoatWaveRule.inBand(minY, stillTop, field)) {
            return null;
        }
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int depth = depth(level, bx, y, bz, seaLevel);
        boolean resting = onGround && minY >= stillTop - 0.05;
        if (!BoatWaveRule.applies(true, field, depth, false, resting)) {
            return null;
        }
        // last, as the dearest test: a column inside a dry hull's water occlusion region (Sable) has no sea
        if (WaterRegions.isOccluded(level, new Vec3(x, stillTop - 0.1, z))) {
            return null;
        }
        return new Column(stillTop, BoatWaveRule.fade(depth));
    }

    /** Water blocks of the column at {@code (x, z)} counted down from {@code y} = sea level − 1; 0 unless it is a sea cell. */
    private static int depth(Level level, int x, int y, int z, int seaLevel) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        if (!level.isLoaded(p)) {
            return 0;
        }
        boolean still = level.getFluidState(p).getType() == Fluids.WATER;
        boolean air = level.getBlockState(p.setY(y + 1)).isAir();
        if (!SurfaceRules.isSeaCell(y, seaLevel, still, air)) {
            return 0;
        }
        int depth = 1;
        int min = level.getMinBuildHeight();
        while (depth < BoatWaveRule.FULL_DEPTH && y - depth >= min && level.getFluidState(p.setY(y - depth)).is(FluidTags.WATER)) {
            depth++;
        }
        return depth;
    }

    /** The level's sea this tick, for its flatness and largest crest: the server's own or the synced one. */
    private static WaveField field(Level level) {
        return level instanceof ServerLevel server ? SeaStates.field(server) : ClientWaves.fieldCached(level.getGameTime());
    }

    /** The sea level the rule uses: the level's own, or a GameTest's ({@link #setTestSeaLevel}). */
    public static int seaLevel(Level level) {
        if (anyTestSeaLevel && level instanceof ServerLevel server) {
            synchronized (TEST_SEA_LEVELS) {
                long[] t = TEST_SEA_LEVELS.get(level);
                if (t != null && SeaStates.stillByDefault(server) && level.getGameTime() < t[1]) {
                    return (int) t[0];
                }
            }
        }
        return level.getSeaLevel();
    }

    /**
     * GameTests only: the rule takes {@code seaLevel} as the sea level of {@code level} until game time
     * {@code untilGameTime}, or the level's own again with null. The GameTest world is a superflat whose sea level lies
     * in its bedrock, below every test structure, so a test builds its sea higher up and names its level here. Acts
     * only on the GameTest server ({@link SeaStates#stillByDefault}); level-wide like a held sea state, so each test
     * using it runs in its own batch.
     */
    public static void setTestSeaLevel(ServerLevel level, @Nullable Integer seaLevel, long untilGameTime) {
        synchronized (TEST_SEA_LEVELS) {
            if (seaLevel == null) {
                TEST_SEA_LEVELS.remove(level);
            } else {
                TEST_SEA_LEVELS.put(level, new long[] {seaLevel, untilGameTime});
            }
            anyTestSeaLevel = !TEST_SEA_LEVELS.isEmpty();
        }
    }
}
