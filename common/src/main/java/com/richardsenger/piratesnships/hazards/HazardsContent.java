package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The hazard entity types. Tracked from 10 chunks (a hazard forms up to 96 blocks from a player) and synced every
 * 10 ticks (they hardly move); {@code noSummon} because a hazard needs its config set-up ({@code /pirates hazard spawn}).
 */
public final class HazardsContent {

    public static final RegistryEntry<EntityType<?>, EntityType<WaterspoutEntity>> WATERSPOUT = ModRegistry.entity("waterspout",
            () -> EntityType.Builder.<WaterspoutEntity>of(WaterspoutEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).noSummon().fireImmune().clientTrackingRange(10).updateInterval(10));

    public static final RegistryEntry<EntityType<?>, EntityType<WhirlpoolEntity>> WHIRLPOOL = ModRegistry.entity("whirlpool",
            () -> EntityType.Builder.<WhirlpoolEntity>of(WhirlpoolEntity::new, MobCategory.MISC)
                    .sized(1.0f, 0.5f).noSummon().fireImmune().clientTrackingRange(10).updateInterval(10));

    private HazardsContent() {
    }

    public static EntityType<? extends HazardEntity> type(HazardKind kind) {
        return switch (kind) {
            case WATERSPOUT -> WATERSPOUT.get();
            case WHIRLPOOL -> WHIRLPOOL.get();
        };
    }

    public static void init() {
        // class load registers the entries
    }
}
