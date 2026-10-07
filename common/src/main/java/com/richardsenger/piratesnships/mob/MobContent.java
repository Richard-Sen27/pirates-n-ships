package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The humanoid mobs (docs/design.md §9) and their spawn eggs. Category {@code MISC} like the crew member: they are
 * placed by spawn eggs, the {@code /pirates mob spawn} command and later by structures, not by the natural spawner.
 */
public final class MobContent {

    public static final RegistryEntry<EntityType<?>, EntityType<Pirate>> PIRATE = humanoid("pirate", Pirate::new);
    public static final RegistryEntry<EntityType<?>, EntityType<Sailor>> SAILOR = humanoid("sailor", Sailor::new);
    public static final RegistryEntry<EntityType<?>, EntityType<NavySoldier>> NAVY_SOLDIER = humanoid("navy_soldier", NavySoldier::new);
    public static final RegistryEntry<EntityType<?>, EntityType<NavyOfficer>> NAVY_OFFICER = humanoid("navy_officer", NavyOfficer::new);

    public static final RegistryEntry<Item, SpawnEggItem> PIRATE_SPAWN_EGG = egg("pirate", PIRATE, 0x3E2E26, 0x962228);
    public static final RegistryEntry<Item, SpawnEggItem> SAILOR_SPAWN_EGG = egg("sailor", SAILOR, 0xECE8DC, 0xB0342C);
    public static final RegistryEntry<Item, SpawnEggItem> NAVY_SOLDIER_SPAWN_EGG = egg("navy_soldier", NAVY_SOLDIER, 0x223468, 0xF0EEE6);
    public static final RegistryEntry<Item, SpawnEggItem> NAVY_OFFICER_SPAWN_EGG = egg("navy_officer", NAVY_OFFICER, 0x223468, 0xDEAA30);

    private MobContent() {
    }

    private static <E extends SeafarerMob> RegistryEntry<EntityType<?>, EntityType<E>> humanoid(String name, EntityType.EntityFactory<E> factory) {
        return ModRegistry.entity(name, () -> EntityType.Builder.of(factory, MobCategory.MISC).sized(0.6f, 1.95f)
                .eyeHeight(1.62f).clientTrackingRange(10));
    }

    private static RegistryEntry<Item, SpawnEggItem> egg(String name, Supplier<? extends EntityType<? extends SeafarerMob>> type,
                                                         int base, int spots) {
        return ModRegistry.item(name + "_spawn_egg", () -> new SpawnEggItem(type.get(), base, spots, new Item.Properties()));
    }

    /** The entity type of a kind. */
    public static EntityType<? extends SeafarerMob> type(MobKind kind) {
        return switch (kind) {
            case PIRATE -> PIRATE.get();
            case SAILOR -> SAILOR.get();
            case NAVY_SOLDIER -> NAVY_SOLDIER.get();
            case NAVY_OFFICER -> NAVY_OFFICER.get();
        };
    }

    public static Map<MobKind, RegistryEntry<Item, SpawnEggItem>> eggs() {
        Map<MobKind, RegistryEntry<Item, SpawnEggItem>> m = new EnumMap<>(MobKind.class);
        m.put(MobKind.PIRATE, PIRATE_SPAWN_EGG);
        m.put(MobKind.SAILOR, SAILOR_SPAWN_EGG);
        m.put(MobKind.NAVY_SOLDIER, NAVY_SOLDIER_SPAWN_EGG);
        m.put(MobKind.NAVY_OFFICER, NAVY_OFFICER_SPAWN_EGG);
        return m;
    }

    public static void init() {
        Services.REGISTRY.registerEntityAttributes(PIRATE, Pirate::createAttributes);
        Services.REGISTRY.registerEntityAttributes(SAILOR, Sailor::createAttributes);
        Services.REGISTRY.registerEntityAttributes(NAVY_SOLDIER, NavySoldier::createAttributes);
        Services.REGISTRY.registerEntityAttributes(NAVY_OFFICER, NavyOfficer::createAttributes);
    }
}
