package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.item.RopeItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registered content of the triangular (fore-and-aft) sail (docs/design.md §5.2, rule F5b): the cleat, its block
 * entity, the rope item and the rope's "first cleat" component. Loaded from {@code SailingBlocks.init()}.
 */
public final class TriangularSailContent {

    /**
     * The cleat in every wood (WOOD3; iron horns on a pad of the wood, which does not burn in lava like before); the
     * dark oak member keeps the id {@code cleat}.
     */
    public static final WoodFamily<CleatBlock> CLEATS = WoodFamily.register("cleat", ShipWood.DARK_OAK,
            wood -> new CleatBlock(wood, BlockBehaviour.Properties.of().mapColor(wood.mapColor()).strength(1.0f, 3.0f)
                    .sound(wood.sound()).noOcclusion().noCollission().pushReaction(PushReaction.DESTROY)));
    /** The dark oak cleat, legacy id {@code cleat}. */
    public static final RegistryEntry<Block, CleatBlock> CLEAT = CLEATS.entry(ShipWood.DARK_OAK);
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<CleatBlockEntity>> CLEAT_BLOCK_ENTITY =
            CLEATS.blockEntity("cleat", CleatBlockEntity::new);
    /** The cleat a rope was first used on (with its dimension), until it is used on a second one. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<GlobalPos>> ROPE_START =
            ModRegistry.dataComponent("rope_start", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    public static final RegistryEntry<Item, RopeItem> ROPE = ModRegistry.item("rope", () -> new RopeItem(new Item.Properties()));

    private TriangularSailContent() {
    }

    public static void init() {
        // class load registers the entries
    }
}
