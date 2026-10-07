package com.richardsenger.piratesnships.world.port;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * A buried treasure of a port (design.md §10.1, WG2): {@code pos} is the buried chest, two blocks under the surface of
 * a pirate island's treasure spot; {@code looted} is for the later treasure-map feature (false when generated).
 */
public record TreasureSite(BlockPos pos, boolean looted) {

    public static final Codec<TreasureSite> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(TreasureSite::pos),
            Codec.BOOL.optionalFieldOf("looted", false).forGetter(TreasureSite::looted)
    ).apply(i, TreasureSite::new));

    public TreasureSite {
        pos = pos.immutable();
    }
}
