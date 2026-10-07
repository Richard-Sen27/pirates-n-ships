package com.richardsenger.piratesnships.world.wreck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * One piece a wreck can be (design.md §10.1, WK1): the structure template, its weight among the pieces that fit, and
 * the water it needs above its seabed row (art/README.md "Wrecks (ST5)": the piece's height, so its top row stays
 * under the sea surface and its waterlogged blocks never reach the air).
 */
public record WreckPieceEntry(ResourceLocation template, int weight, int waterAbove) {

    public static final Codec<WreckPieceEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(WreckPieceEntry::template),
            Codec.intRange(1, 1000).fieldOf("weight").forGetter(WreckPieceEntry::weight),
            Codec.intRange(0, 256).fieldOf("water_above").forGetter(WreckPieceEntry::waterAbove)
    ).apply(i, WreckPieceEntry::new));
}
