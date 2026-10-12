package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

/** Data keys of the seafarer village (WG1, STR3): structure, structure set, biome tag, pools and the streets' processor list. No registration here. */
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
    /** STR7: the pier's extension sections (hung by {@code PierExtensions}, not by the jigsaw). */
    public static final ResourceKey<StructureTemplatePool> PIER_EXTENSION = pool("pier_extension");
    public static final ResourceKey<StructureTemplatePool> TERMINATORS = pool("terminators");
    /** STR3: what the dock head's inland street draws (straights, forks, a crossroads, the well square). */
    public static final ResourceKey<StructureTemplatePool> STREETHEADS = pool("streetheads");
    /** STR3: the small props on the streets' and squares' {@code deco_out} connectors. */
    public static final ResourceKey<StructureTemplatePool> DECORATIONS = pool("decorations");
    /** STR3: the streets' processor list, the connections and the boardwalk (a street over an inlet). */
    public static final ResourceKey<StructureProcessorList> STREET_PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, Constants.id("village_street"));
    /**
     * STR8: the streets' processors since STR8 (the connections, {@code TrailBedProcessor}: level on a cut and filled bed,
     * the boardwalk). {@link #STREET_PROCESSORS} stays for the streets of villages planned before.
     */
    public static final ResourceKey<StructureProcessorList> STREET_LEVEL_PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, Constants.id("village_street_level"));
    /** STR8: what a bend's, a fork's and the crossroads' ways on draw (straights only). */
    public static final ResourceKey<StructureTemplatePool> STREETS_STRAIGHT = pool("streets_straight");
    /** STR8: the village on a snowy or frozen shore (its own structure set, {@code cold_frequency}). */
    public static final ResourceKey<Structure> SEAFARER_VILLAGE_COLD = ResourceKey.create(Registries.STRUCTURE,
            Constants.id("seafarer_village_cold"));
    public static final ResourceKey<StructureSet> SEAFARER_VILLAGES_COLD = ResourceKey.create(Registries.STRUCTURE_SET,
            Constants.id("seafarer_villages_cold"));

    private VillageKeys() {
    }

    private static ResourceKey<StructureTemplatePool> pool(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Constants.id("village/" + name));
    }
}
