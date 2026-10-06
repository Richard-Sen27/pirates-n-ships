package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registry content of the {@code core} module. */
public final class CoreContent {

    /** Dev/test block: a plain cube used by foundation GameTests and as a known-good registration example. */
    public static final RegistryEntry<Block, Block> TEST_BLOCK = ModRegistry.blockWithItem("test_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD)));

    /** The mod's creative tab. Lists every item registered through {@link ModRegistry}. */
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> TAB = ModRegistry.creativeTab("main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + Constants.MOD_ID))
                    .icon(() -> new ItemStack(TEST_BLOCK.get()))
                    .displayItems((params, output) -> ModRegistry.items().forEach(item -> output.accept(item.get())))
                    .build());

    private CoreContent() {
    }

    public static void init() {
    }
}
