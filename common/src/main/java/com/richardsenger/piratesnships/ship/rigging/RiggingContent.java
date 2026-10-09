package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registry entries of the {@code ship.rigging} module: RL1's ratlines block and item and their anchor tag; RL2's
 * ratline nets (the chainplate and the mast hoop with their block entity, the invisible net cell with its block entity,
 * the net item and the component that remembers its first end).
 */
public final class RiggingContent {

    /**
     * Blocks that carry a ratlines net on any side although their side faces are not sturdy: fences and walls (a mast of
     * fence posts), and (RL1b) the yard and the crow's nest, so a run passes the yard rows and leans on the nest; a net
     * that only a yard holds is still refused in a sail's cloth ({@link RatlinesBlock#refusedByCloth}). Every block with
     * a sturdy face carries one on that face anyway.
     */
    public static final TagKey<Block> RATLINES_ANCHORS = TagKey.create(Registries.BLOCK, Constants.id("ratlines_anchors"));

    public static final RegistryEntry<Block, RatlinesBlock> RATLINES = ModRegistry.block("ratlines",
            () -> new RatlinesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.4f).sound(SoundType.WOOL)
                    .noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava()));
    public static final RegistryEntry<Item, RatlinesItem> RATLINES_ITEM = ModRegistry.item("ratlines",
            () -> new RatlinesItem(RATLINES.get(), new Item.Properties()));

    // ------------------------------------------------------------------ RL2: ratline nets between fittings

    /**
     * Sable's {@code weightless} tag (mass 0, refs/sable common/src/main/resources/data/sable/physics_block_properties/
     * weightless.json): the net cells, whose weight the fittings and the drawn net stand for.
     */
    public static final TagKey<Block> SABLE_WEIGHTLESS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("sable", "weightless"));

    public static final RegistryEntry<Block, ChainplateBlock> CHAINPLATE = ModRegistry.blockWithItem("chainplate",
            () -> new ChainplateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f)
                    .sound(SoundType.CHAIN).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final RegistryEntry<Block, MastHoopBlock> MAST_HOOP = ModRegistry.blockWithItem("mast_hoop",
            () -> new MastHoopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f)
                    .sound(SoundType.CHAIN).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final RegistryEntry<Block, RatlineNetCellBlock> NET_CELL = ModRegistry.block("ratline_net_cell",
            () -> new RatlineNetCellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.2f)
                    .sound(SoundType.WOOL).noCollission().noOcclusion().noLootTable().pushReaction(PushReaction.DESTROY)
                    .isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryEntry<Item, RatlineNetItem> NET_ITEM = ModRegistry.item("ratline_net",
            () -> new RatlineNetItem(new Item.Properties().stacksTo(16)));
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<NetFittingBlockEntity>> NET_FITTING_BLOCK_ENTITY =
            ModRegistry.blockEntity("net_fitting", NetFittingBlockEntity::new, CHAINPLATE, MAST_HOOP);
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<RatlineNetCellBlockEntity>> NET_CELL_BLOCK_ENTITY =
            ModRegistry.blockEntity("ratline_net_cell", RatlineNetCellBlockEntity::new, NET_CELL);
    /** The fitting a ratline net item was first used on (its end A), with the dimension. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<GlobalPos>> NET_START =
            ModRegistry.dataComponent("net_start", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private RiggingContent() {
    }

    public static void init() {
    }
}
