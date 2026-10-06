package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.world.level.block.LadderBlock;

/**
 * The nameplate (design.md §4.8): vanilla {@link LadderBlock} placement and shape (a 3-pixel slab of space against a
 * wall, facing away from it, waterloggable). Not climbable, because climbing comes from {@code #minecraft:climbable}.
 * Its look is the hand-made Blockbench model {@code block/nameplate} ({@code art/models/nameplate.bbmodel}): a dark oak
 * board with a raised spruce panel and bevelled edges, hung 1 px off the wall on two iron brackets.
 * A subclass only because the vanilla constructor is protected (it keeps LadderBlock's codec, whose type is fixed). The name text comes later.
 */
public class NameplateBlock extends LadderBlock {

    public NameplateBlock(Properties properties) {
        super(properties);
    }
}
