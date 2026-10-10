package com.richardsenger.piratesnships.trade.desk;

import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Registration of the harbor master's desk in every wood (WOOD2, docs/woods.md; {@code harbor_desk} is dark oak's legacy
 * id, {@code <wood>_harbor_desk} every other wood's) and its block entity, valid for every member.
 */
public final class HarborDesks {

    public static final WoodFamily<HarborDeskBlock> HARBOR_DESKS = WoodFamily.register("harbor_desk", ShipWood.DARK_OAK,
            wood -> new HarborDeskBlock(wood, WoodFamily.properties(wood).strength(2.5f).noOcclusion()));

    /** The legacy dark oak desk (the family's lead). */
    public static final RegistryEntry<Block, HarborDeskBlock> HARBOR_DESK = HARBOR_DESKS.lead();

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<HarborDeskBlockEntity>> BLOCK_ENTITY =
            HARBOR_DESKS.blockEntity("harbor_desk", HarborDeskBlockEntity::new);

    private HarborDesks() {
    }

    /** Loads the class so the entries are registered. Called from {@code TradeModule.registerContent()}. */
    public static void init() {
    }
}
