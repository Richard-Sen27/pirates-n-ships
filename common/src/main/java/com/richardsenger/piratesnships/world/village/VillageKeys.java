package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Data keys of the seafarer village (WG1): structure, structure set, biome tag and pools. No registration here. */
public final class VillageKeys {

    /** The structure type's id ({@code world.structure.PortStructures#PORT_STRUCTURE}). */
    public static final net.minecraft.resources.ResourceLocation PORT_VILLAGE_TYPE = Constants.id("port_village");

    public static final ResourceKey<Structure> SEAFARER_VILLAGE = ResourceKey.create(Registries.STRUCTURE, Constants.id("seafarer_village"));
    public static final ResourceKey<StructureSet> SEAFARER_VILLAGES = ResourceKey.create(Registries.STRUCTURE_SET, Constants.id("seafarer_villages"));
    public static final TagKey<Biome> HAS_SEAFARER_VILLAGE = TagKey.create(Registries.BIOME, Constants.id("has_structure/seafarer_village"));

    public static final ResourceKey<StructureTemplatePool> START = pool("start");
    public static final ResourceKey<StructureTemplatePool> STREETS = pool("streets");
    public static final ResourceKey<StructureTemplatePool> BUILDINGS = pool("buildings");
    public static final ResourceKey<StructureTemplatePool> PIER = pool("pier");
    public static final ResourceKey<StructureTemplatePool> TERMINATORS = pool("terminators");

    private VillageKeys() {
    }

    private static ResourceKey<StructureTemplatePool> pool(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Constants.id("village/" + name));
    }
}
