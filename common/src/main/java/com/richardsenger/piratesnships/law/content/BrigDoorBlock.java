package com.richardsenger.piratesnships.law.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DoorBlock;

/**
 * A vanilla door with the brig block set ({@link LawContent#BRIG_SET}), unchanged. A subclass only because the
 * vanilla constructor is protected. Locking comes in a later package.
 */
public class BrigDoorBlock extends DoorBlock {

    public static final MapCodec<BrigDoorBlock> CODEC = simpleCodec(BrigDoorBlock::new);

    public BrigDoorBlock(Properties properties) {
        super(LawContent.BRIG_SET, properties);
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }
}
