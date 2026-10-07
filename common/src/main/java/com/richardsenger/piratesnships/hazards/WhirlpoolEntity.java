package com.richardsenger.piratesnships.hazards;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A whirlpool (docs/design.md §12): appears in the deep ocean, pulls entities and ships toward its centre and spins
 * them, drags boats and swimmers in its inner third under, and turns a ship lying at its centre. Drifts slowly on a
 * random heading and turns away from anything that is not open water.
 */
public class WhirlpoolEntity extends HazardEntity {

    private double heading;
    private double driftSpeed;

    public WhirlpoolEntity(EntityType<? extends WhirlpoolEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public HazardKind kind() {
        return HazardKind.WHIRLPOOL;
    }

    @Override
    protected boolean enabled() {
        return HazardsConfig.WHIRLPOOLS_ENABLED.get();
    }

    @Override
    protected void syncSize() {
        setSize(HazardsConfig.WHIRLPOOL_RADIUS.get().floatValue(), 0.0f);
    }

    @Override
    public void configure(RandomSource random) {
        setLifetime(HazardsConfig.WHIRLPOOL_DURATION.get());
        heading = random.nextDouble() * Math.PI * 2;
        driftSpeed = HazardsConfig.WHIRLPOOL_DRIFT_SPEED.get();
        syncSize();
    }

    /** Drift speed in blocks/tick (0 = stationary; tests stop the drift so their geometry holds). */
    public void setDriftSpeed(double speed) {
        this.driftSpeed = Math.max(0, speed);
    }

    public double driftSpeed() {
        return driftSpeed;
    }

    /** The field parameters from the config. */
    public static HazardField.Pool params() {
        return new HazardField.Pool(HazardsConfig.WHIRLPOOL_RADIUS.get(), HazardsConfig.WHIRLPOOL_PULL.get(),
                HazardsConfig.WHIRLPOOL_SPIN.get(), HazardsConfig.WHIRLPOOL_DRAG_DOWN.get());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        drift(level);
        HazardForces.whirlpool(level, this, params());
    }

    private void drift(ServerLevel level) {
        if (driftSpeed <= 0) {
            return;
        }
        double[] next = Drift.step(getX(), getZ(), heading, driftSpeed);
        BlockPos below = BlockPos.containing(next[0], getY() - 0.5, next[1]);
        if (!level.isLoaded(below)) {
            return;
        }
        if (level.getFluidState(below).is(FluidTags.WATER) && level.getBlockState(below.above()).isAir()) {
            setPos(next[0], getY(), next[1]);
        } else {
            heading = Drift.turnAway(heading, random.nextDouble());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        heading = tag.getDouble("Heading");
        driftSpeed = tag.getDouble("DriftSpeed");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Heading", heading);
        tag.putDouble("DriftSpeed", driftSpeed);
    }
}
