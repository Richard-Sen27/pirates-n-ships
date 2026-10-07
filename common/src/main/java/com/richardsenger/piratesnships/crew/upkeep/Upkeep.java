package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.crew.CrewConfig;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Config and the per-ship work speed of crew upkeep (CR2); the day tick itself is {@link ShipDayTick}. */
public final class Upkeep {

    private Upkeep() {
    }

    /** The current {@code crew.wages / desertion / mutiny} config. */
    public static UpkeepSettings settings() {
        return new UpkeepSettings(
                CrewConfig.WAGES_ENABLED.get(), CrewConfig.WAGE_PER_DAY.get(), CrewConfig.UNPAID_PER_DAY.get(), CrewConfig.PAID_PER_DAY.get(),
                CrewConfig.DESERTION_ENABLED.get(), CrewConfig.DESERT_BELOW.get(), CrewConfig.DESERT_DAYS.get(),
                CrewConfig.MUTINY_ENABLED.get(), CrewConfig.MUTINY_BELOW.get(), CrewConfig.MUTINY_DAYS.get());
    }

    /**
     * The work speed of the ship's crew, from its last day tick (hungry, thirsty, drunk); 1 for a ship that never had
     * one. Registered as {@code station.Stations.WorkSpeed} by {@code ProvisionsModule}.
     */
    public static double workSpeed(ServerLevel level, UUID ship) {
        return ShipUpkeepData.get(level.getServer()).get(ship).workSpeed();
    }
}
