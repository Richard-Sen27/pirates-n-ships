package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Brig content (design.md §13.3): shackles, brig bars and the lockable brig door. Prisoners: {@code law.brig}. */
public final class LawContent {

    /**
     * Iron door sounds. {@code canOpenByHand = false} like vanilla's iron set, so mobs (villagers, door-breaking
     * zombies, pathfinding) treat the door like an iron door; {@link BrigDoorBlock} opens it by hand itself and adds
     * the lock. Not added to vanilla's block set registry (its {@code register} is private); nothing in 1.21.1 looks
     * block sets up by name for our blocks.
     */
    public static final BlockSetType BRIG_SET = new BlockSetType(Constants.MOD_ID + ":brig",
            false, false, false, BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

    public static final RegistryEntry<Item, Item> SHACKLES = ModRegistry.item("shackles", () -> new ShacklesItem(new Item.Properties().stacksTo(16)));

    /** Vanilla iron bars behavior (connects to neighbors and sturdy faces). */
    public static final RegistryEntry<Block, BrigBarsBlock> BRIG_BARS = ModRegistry.blockWithItem("brig_bars",
            () -> new BrigBarsBlock(BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(5.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));

    /** Door behavior plus a lock (two halves, hinge, opens by hand and redstone unless locked). */
    public static final RegistryEntry<Block, BrigDoorBlock> BRIG_DOOR = ModRegistry.blockWithItem("brig_door",
            () -> new BrigDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                    .strength(5.0f).noOcclusion().pushReaction(PushReaction.DESTROY)));

    /** Owner of a brig door (lower half). */
    @SuppressWarnings("unchecked")
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<BrigDoorBlockEntity>> BRIG_DOOR_ENTITY =
            ModRegistry.blockEntity("brig_door", BrigDoorBlockEntity::new, BRIG_DOOR);

    private LawContent() {
    }

    public static void init() {
    }
}
