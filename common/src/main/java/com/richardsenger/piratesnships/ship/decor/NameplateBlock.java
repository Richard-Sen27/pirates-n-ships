package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.world.level.block.LadderBlock;

/**
 * The nameplate (design.md §4.8): vanilla {@link LadderBlock} placement and shape (a 3-pixel plate against a wall,
 * facing away from it, waterloggable). Not climbable, because climbing comes from {@code #minecraft:climbable}.
 * A subclass only because the vanilla constructor is protected (it keeps LadderBlock's codec, whose type is fixed). The name text comes later.
 */
public class NameplateBlock extends LadderBlock {

    public NameplateBlock(Properties properties) {
        super(properties);
    }
}
