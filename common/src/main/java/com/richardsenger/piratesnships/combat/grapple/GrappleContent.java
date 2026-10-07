package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registered content of the grappling hook (docs/design.md §8.3): the thrown hook entity. The item itself is
 * {@code combat.content.CombatContent#GRAPPLING_HOOK}, a {@link GrapplingHookItem}.
 */
public final class GrappleContent {

    /** Tracked from 10 chunks and updated every other tick, so the rope to a moving ship stays in view and close. */
    public static final RegistryEntry<EntityType<?>, EntityType<GrapplingHookEntity>> HOOK = ModRegistry.entity("grappling_hook",
            () -> EntityType.Builder.<GrapplingHookEntity>of(GrapplingHookEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).noSummon().clientTrackingRange(10).updateInterval(2));

    private GrappleContent() {
    }

    public static void init() {
        // class load registers the entries
    }
}
