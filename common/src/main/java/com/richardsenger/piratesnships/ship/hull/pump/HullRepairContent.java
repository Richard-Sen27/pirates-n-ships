package com.richardsenger.piratesnships.ship.hull.pump;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Registered content of the flooding counter-measures (docs/design.md §4.5, milestone 6): the bilge pump and the hull
 * patch (block plus its own block item, {@link HullPatchItem}). Loaded from {@code HullModule.registerContent()}.
 */
public final class HullRepairContent {

    public static final RegistryEntry<Block, BilgePumpBlock> BILGE_PUMP = ModRegistry.blockWithItem("bilge_pump",
            () -> new BilgePumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().ignitedByLava()));

    /** PMP1: carries the synced pumping flag the handle renderer reads. */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<BilgePumpBlockEntity>> BILGE_PUMP_ENTITY =
            ModRegistry.blockEntity("bilge_pump", BilgePumpBlockEntity::new, BILGE_PUMP);

    /** A plank-like, watertight hull block; as strong as planks. */
    public static final RegistryEntry<Block, Block> HULL_PATCH_BLOCK = ModRegistry.block("hull_patch",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(2.0f, 3.0f).sound(SoundType.WOOD).ignitedByLava()));

    public static final RegistryEntry<Item, HullPatchItem> HULL_PATCH = ModRegistry.item("hull_patch",
            () -> new HullPatchItem(HULL_PATCH_BLOCK.get(), new Item.Properties()));

    private HullRepairContent() {
    }

    public static void init() {
    }
}
