package com.richardsenger.piratesnships.sailing.wind;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side access to the wind (docs/design.md §5.1). A thin adapter around the pure {@link WindField}: it reads the
 * level seed, dimension, game time and the vanilla rain and thunder levels, and the tuning from the server config.
 */
public final class WindService {

    private WindService() {
    }

    /** The wind at a world position in this level. */
    public static WindSample sample(ServerLevel level, Vec3 position) {
        return sample(level, position, SailingConfig.windParams());
    }

    /** The wind at a world position in this level, with explicit tuning. */
    public static WindSample sample(ServerLevel level, Vec3 position, WindParams params) {
        return WindField.sample(params, level.getSeed(), level.dimension().location().toString(), level.getGameTime(),
                level.getRainLevel(1.0f), level.getThunderLevel(1.0f), position.x, position.z);
    }
}
