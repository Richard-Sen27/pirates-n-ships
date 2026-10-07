package com.richardsenger.piratesnships.law.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.IronBarsBlock;

/**
 * Vanilla iron bars, unchanged. A subclass only because the vanilla constructor is protected.
 *
 * <p>Model: hand-made (art/models/brig_bars*.bbmodel, design.md §4.8): a capped iron post, and per connected side an
 * arm of two round bars between a top and a bottom rail with a middle brace. Opaque, so no render type is needed.
 */
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
