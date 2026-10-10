package com.richardsenger.piratesnships.world.outpost;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Data keys of the navy outpost (WG3, STR4): structure, structure set, biome tag and pools. No registration here. */
public final class OutpostKeys {

    public static final ResourceKey<Structure> NAVY_OUTPOST = ResourceKey.create(Registries.STRUCTURE, Constants.id("navy_outpost"));
    public static final ResourceKey<StructureSet> NAVY_OUTPOSTS = ResourceKey.create(Registries.STRUCTURE_SET, Constants.id("navy_outposts"));
    public static final TagKey<Biome> HAS_NAVY_OUTPOST = TagKey.create(Registries.BIOME, Constants.id("has_structure/navy_outpost"));

    public static final ResourceKey<StructureTemplatePool> START = pool("start");
    public static final ResourceKey<StructureTemplatePool> WALLS = pool("walls");
    public static final ResourceKey<StructureTemplatePool> BUILDINGS = pool("buildings");
    public static final ResourceKey<StructureTemplatePool> QUAY = pool("quay");
    public static final ResourceKey<StructureTemplatePool> TERMINATORS = pool("terminators");
    /** STR4: the wall trace's flank (after a salient angle). */
    public static final ResourceKey<StructureTemplatePool> WALLS_FLANK = pool("walls_flank");
    /** STR4: the flank's first piece (straights only), after the shore's angle. */
    public static final ResourceKey<StructureTemplatePool> WALLS_FLANK_HEAD = pool("walls_flank_head");
    /** STR4: what the gate's own wall connectors draw (the shore state without the angles). */
    public static final ResourceKey<StructureTemplatePool> WALLS_GATE = pool("walls_gate");
    /** STR4: the court's lanes: what the land gate draws, the lanes, their dead ends; the decorations. */
    public static final ResourceKey<StructureTemplatePool> LANEHEADS = pool("laneheads");
    public static final ResourceKey<StructureTemplatePool> LANES = pool("lanes");
    public static final ResourceKey<StructureTemplatePool> LANE_ENDS = pool("lane_ends");
    public static final ResourceKey<StructureTemplatePool> DECORATIONS = pool("decorations");

    private OutpostKeys() {
    }

    private static ResourceKey<StructureTemplatePool> pool(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Constants.id("navy_outpost/" + name));
    }
}
