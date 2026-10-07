package com.richardsenger.piratesnships.crew.hammock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * A crew member's hammock for the night (HM1): the ship (Sable sub-level UUID) and the plot position of the hammock's
 * foot. Saved on the crew member like its station ({@code StationRef}).
 */
public record HammockRef(UUID ship, BlockPos foot) {

    public static final Codec<HammockRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("ship").forGetter(HammockRef::ship),
            BlockPos.CODEC.fieldOf("foot").forGetter(HammockRef::foot)
    ).apply(i, HammockRef::new));

    public HammockRef {
        foot = foot.immutable();
    }
}
