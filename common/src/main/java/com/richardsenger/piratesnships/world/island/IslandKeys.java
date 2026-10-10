package com.richardsenger.piratesnships.world.island;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

/** Data keys of the pirate island (WG2, STR2): structure, structure set, biome tag, pools and the trails' processor list. No registration here. */
public final class IslandKeys {

    public static final ResourceKey<Structure> PIRATE_ISLAND = ResourceKey.create(Registries.STRUCTURE, Constants.id("pirate_island"));
    public static final ResourceKey<StructureSet> PIRATE_ISLANDS = ResourceKey.create(Registries.STRUCTURE_SET, Constants.id("pirate_islands"));
    public static final TagKey<Biome> HAS_PIRATE_ISLAND = TagKey.create(Registries.BIOME, Constants.id("has_structure/pirate_island"));

    public static final ResourceKey<StructureTemplatePool> START = pool("start");
    public static final ResourceKey<StructureTemplatePool> PATHS = pool("paths");
    /** STR2: the camp's inland trailhead draws from this pool (straights and forks: the camp always grows inland). */
    public static final ResourceKey<StructureTemplatePool> TRAILHEADS = pool("trailheads");
    public static final ResourceKey<StructureTemplatePool> HUTS = pool("huts");
    public static final ResourceKey<StructureTemplatePool> JETTY = pool("jetty");
    public static final ResourceKey<StructureTemplatePool> TERMINATORS = pool("terminators");
    /** STR2: the small props hung on the trails' and the clearing's {@code deco_out} connectors. */
    public static final ResourceKey<StructureTemplatePool> DECORATIONS = pool("decorations");
    /** STR2: the trails' processors (the connections plus {@code BoardwalkProcessor}: a trail over water becomes boards). */
    public static final ResourceKey<StructureProcessorList> TRAIL_PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, Constants.id("pirate_island_trail"));

    private IslandKeys() {
    }

    private static ResourceKey<StructureTemplatePool> pool(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Constants.id("pirate_island/" + name));
    }
}
