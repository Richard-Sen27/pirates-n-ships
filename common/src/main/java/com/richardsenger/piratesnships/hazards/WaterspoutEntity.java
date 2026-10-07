package com.richardsenger.piratesnships.hazards;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A waterspout (docs/design.md §12): forms over the ocean in thunderstorms, pulls entities and ships toward its axis,
 * lifts them, and tears at the sails of ships inside it once per second. Stationary.
 */
public class WaterspoutEntity extends HazardEntity {

    public WaterspoutEntity(EntityType<? extends WaterspoutEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public HazardKind kind() {
        return HazardKind.WATERSPOUT;
    }

    @Override
    protected boolean enabled() {
        return HazardsConfig.WATERSPOUTS_ENABLED.get();
    }

    @Override
    protected void syncSize() {
        setSize(HazardsConfig.WATERSPOUT_RADIUS.get().floatValue(), HazardsConfig.WATERSPOUT_FUNNEL_HEIGHT.get().floatValue());
    }

    @Override
    public void configure(RandomSource random) {
        setLifetime(HazardsConfig.WATERSPOUT_DURATION.get());
        syncSize();
    }

    /** The field parameters from the config. */
    public static HazardField.Spout params() {
        return new HazardField.Spout(HazardsConfig.WATERSPOUT_RADIUS.get(), HazardsConfig.WATERSPOUT_FUNNEL_HEIGHT.get(),
                HazardsConfig.WATERSPOUT_PULL.get(), HazardsConfig.WATERSPOUT_LIFT.get());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        HazardForces.waterspout(level, this, params(), HazardsConfig.WATERSPOUT_MAX_LIFT_SPEED.get());
        if (age() % 20 == 0) {
            HazardForces.tearSails(level, this, params(), HazardsConfig.SAIL_DAMAGE_CHANCE.get());
        }
    }
}
