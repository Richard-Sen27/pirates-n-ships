package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Content of the village life module (LIFE1). The villager's entity type lives with the other humanoid mobs
 * ({@code MobContent.VILLAGER}); here only the invisible seat it sits on at dusk.
 */
public final class VillageLifeContent {

    public static final RegistryEntry<EntityType<?>, EntityType<BenchSeat>> BENCH_SEAT = ModRegistry.entity("bench_seat",
            () -> EntityType.Builder.<BenchSeat>of(BenchSeat::new, MobCategory.MISC)
                    .sized(0.25f, 0.01f).noSummon().fireImmune().clientTrackingRange(10).updateInterval(1));

    private VillageLifeContent() {
    }

    public static void init() {
    }
}
