package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** Entity-type tags of the law system (generated in {@code LawModule.gatherData}). */
public final class LawTags {

    /** Navy soldiers and officers. Empty until the navy mobs exist (docs/design.md §9); they add themselves here. */
    public static final TagKey<EntityType<?>> NAVY = TagKey.create(Registries.ENTITY_TYPE, Constants.id("navy"));

    /**
     * Civilians under the protection of the law ("villagers" in §13.1): villagers and wandering traders. Iron and
     * snow golems are not: they are guards, not civilians (see {@link #LAW_ENFORCERS}). Zombie villagers are
     * monsters, not villagers.
     */
    public static final TagKey<EntityType<?>> LAW_PROTECTED = TagKey.create(Registries.ENTITY_TYPE, Constants.id("law_protected"));

    /** Entities that never commit combat crimes: {@code #navy} plus iron golems (village defenders). */
    public static final TagKey<EntityType<?>> LAW_ENFORCERS = TagKey.create(Registries.ENTITY_TYPE, Constants.id("law_enforcers"));

    private LawTags() {
    }
}
