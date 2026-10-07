package com.richardsenger.piratesnships.world.wreck;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Data keys of the wrecks (WK1): structure, structure set, biome tag and the piece templates. No registration here. */
public final class WreckKeys {

    /** The structure type's id ({@link WreckStructures#WRECK_TYPE}); also the piece type's id. */
    public static final ResourceLocation WRECK_TYPE = Constants.id("wreck");
    public static final ResourceKey<Structure> WRECK = ResourceKey.create(Registries.STRUCTURE, Constants.id("wreck"));
    public static final ResourceKey<StructureSet> WRECKS = ResourceKey.create(Registries.STRUCTURE_SET, Constants.id("wrecks"));
    public static final TagKey<Biome> HAS_WRECK = TagKey.create(Registries.BIOME, Constants.id("has_structure/wreck"));

    public static final ResourceLocation CARGO_FIELD = Constants.id("wreck/cargo_field");
    public static final ResourceLocation MAST_STUMP = Constants.id("wreck/mast_stump");
    public static final ResourceLocation STERN = Constants.id("wreck/stern");
    public static final ResourceLocation SUNKEN_SLOOP = Constants.id("wreck/sunken_sloop");

    private WreckKeys() {
    }
}
