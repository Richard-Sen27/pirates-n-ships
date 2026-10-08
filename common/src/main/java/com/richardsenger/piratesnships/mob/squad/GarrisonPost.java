package com.richardsenger.piratesnships.mob.squad;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A garrison mob's post (MOB2): the block it stands in, the way it faces there, and its outpost (the world position of
 * the fort gate's court centre, which tells the garrisons of two outposts apart). Saved on the mob.
 */
public record GarrisonPost(BlockPos pos, Direction facing, BlockPos outpost) {

    public static final GarrisonPost NONE = new GarrisonPost(BlockPos.ZERO, Direction.NORTH, BlockPos.ZERO);

    public static final Codec<GarrisonPost> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(GarrisonPost::pos),
            Direction.CODEC.fieldOf("facing").forGetter(GarrisonPost::facing),
            BlockPos.CODEC.fieldOf("outpost").forGetter(GarrisonPost::outpost)
    ).apply(i, GarrisonPost::new));
}
