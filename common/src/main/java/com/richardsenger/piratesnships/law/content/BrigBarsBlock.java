package com.richardsenger.piratesnships.law.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.IronBarsBlock;

/** Vanilla iron bars, unchanged. A subclass only because the vanilla constructor is protected. */
public class BrigBarsBlock extends IronBarsBlock {

    public static final MapCodec<BrigBarsBlock> CODEC = simpleCodec(BrigBarsBlock::new);

    public BrigBarsBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return CODEC;
    }
}
