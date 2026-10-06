package com.richardsenger.piratesnships.sailing.sail;

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

    public static final RegistryEntry<Block, CleatBlock> CLEAT = ModRegistry.blockWithItem("cleat",
            () -> new CleatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().noCollission().pushReaction(PushReaction.DESTROY)));
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<CleatBlockEntity>> CLEAT_BLOCK_ENTITY =
            ModRegistry.blockEntity("cleat", CleatBlockEntity::new, CLEAT);
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
