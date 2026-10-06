package com.richardsenger.piratesnships.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * Where a station is: the ship (Sable sub-level UUID, stable across save and load) and the station block's plot
 * position on that ship. Also the persisted crew assignment ({@code CrewMember}).
 */
public record StationRef(UUID ship, BlockPos pos) {

    public static final Codec<StationRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("ship").forGetter(StationRef::ship),
            BlockPos.CODEC.fieldOf("pos").forGetter(StationRef::pos)
    ).apply(i, StationRef::new));

    public StationRef {
        pos = pos.immutable();
    }
}
