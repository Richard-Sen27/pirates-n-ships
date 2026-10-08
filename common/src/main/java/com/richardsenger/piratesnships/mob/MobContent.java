package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.mob.captain.PirateCaptain;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.mob.entity.Shark;
import com.richardsenger.piratesnships.mob.kraken.KrakenContent;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.NaturalSpawn;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The humanoid mobs (docs/design.md §9) and their spawn eggs. Category {@code MISC} like the crew member: they are
 * placed by spawn eggs, the {@code /pirates mob spawn} command and later by structures, not by the natural spawner;
 * except the pirate, {@code MONSTER} since WG2, which the natural spawner places inside pirate island camps.
 * The shark (M4) is a {@code WATER_CREATURE}: it spawns naturally in ocean biomes and despawns like vanilla's fish.
 */
public final class MobContent {

    /**
     * {@code MONSTER}, not {@code MISC} (WG2): vanilla's natural spawner never spawns a {@code MISC} type, and pirates
     * spawn through the pirate island's {@code monster} spawn overrides (spawn rule: {@code world.island.PirateIslandSpawns}).
     */
    public static final RegistryEntry<EntityType<?>, EntityType<Pirate>> PIRATE = humanoid("pirate", Pirate::new, MobCategory.MONSTER);
    public static final RegistryEntry<EntityType<?>, EntityType<Sailor>> SAILOR = humanoid("sailor", Sailor::new, MobCategory.MISC);
    public static final RegistryEntry<EntityType<?>, EntityType<NavySoldier>> NAVY_SOLDIER = humanoid("navy_soldier", NavySoldier::new, MobCategory.MISC);
    public static final RegistryEntry<EntityType<?>, EntityType<NavyOfficer>> NAVY_OFFICER = humanoid("navy_officer", NavyOfficer::new, MobCategory.MISC);
    /** BOS1: the named captain of a pirate island, placed in the captain's hut ({@code mob.captain.IslandCaptains}); no spawn egg. */
    public static final RegistryEntry<EntityType<?>, EntityType<PirateCaptain>> PIRATE_CAPTAIN = humanoid("pirate_captain", PirateCaptain::new, MobCategory.MISC);

    /** 0.9 × 0.6 hitbox in the middle of a 2.4-block body (the model is longer than the box, like the dolphin's). */
    public static final RegistryEntry<EntityType<?>, EntityType<Shark>> SHARK = ModRegistry.entity("shark",
            () -> EntityType.Builder.of(Shark::new, MobCategory.WATER_CREATURE).sized(0.9f, 0.6f).eyeHeight(0.35f)
                    .clientTrackingRange(10));

    public static final RegistryEntry<Item, SpawnEggItem> PIRATE_SPAWN_EGG = egg("pirate", PIRATE, 0x3E2E26, 0x962228);
    public static final RegistryEntry<Item, SpawnEggItem> SAILOR_SPAWN_EGG = egg("sailor", SAILOR, 0xECE8DC, 0xB0342C);
    public static final RegistryEntry<Item, SpawnEggItem> NAVY_SOLDIER_SPAWN_EGG = egg("navy_soldier", NAVY_SOLDIER, 0x223468, 0xF0EEE6);
    public static final RegistryEntry<Item, SpawnEggItem> NAVY_OFFICER_SPAWN_EGG = egg("navy_officer", NAVY_OFFICER, 0x223468, 0xDEAA30);

    private MobContent() {
    }

    private static <E extends SeafarerMob> RegistryEntry<EntityType<?>, EntityType<E>> humanoid(String name, EntityType.EntityFactory<E> factory,
                                                                                                MobCategory category) {
        return ModRegistry.entity(name, () -> EntityType.Builder.of(factory, category).sized(0.6f, 1.95f)
                .eyeHeight(1.62f).clientTrackingRange(10));
    }

    public static final RegistryEntry<Item, SpawnEggItem> SHARK_SPAWN_EGG = egg("shark", SHARK, 0x5E6E7E, 0xE8ECEE);

    private static RegistryEntry<Item, SpawnEggItem> egg(String name, Supplier<? extends EntityType<? extends Mob>> type,
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
            case PIRATE_CAPTAIN -> PIRATE_CAPTAIN.get();
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
        Services.REGISTRY.registerEntityAttributes(PIRATE_CAPTAIN, PirateCaptain::createAttributes);
        Services.REGISTRY.registerEntityAttributes(SHARK, Shark::createAttributes);
        KrakenContent.init();
        Services.REGISTRY.registerSpawnPlacement(SHARK, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR, Shark::checkSpawnRules);
        Services.REGISTRY.registerNaturalSpawn(new NaturalSpawn(SHARK, MobCategory.WATER_CREATURE,
                List.of(BiomeTags.IS_OCEAN, BiomeTags.IS_DEEP_OCEAN), MobConfig.SHARK_SPAWN_WEIGHT::get,
                MobConfig.SHARK_MIN_GROUP::get, MobConfig.SHARK_MAX_GROUP::get));
    }
}
