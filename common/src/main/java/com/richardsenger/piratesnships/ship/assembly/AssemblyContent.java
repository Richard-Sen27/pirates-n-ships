package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registry content of the {@code ship.assembly} module. */
public final class AssemblyContent {

    public static final RegistryEntry<Block, HelmBlock> HELM = ModRegistry.blockWithItem("helm",
            () -> new HelmBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion()));

    // ---------------------------------------------------------------- the Shipwright's Toolkit (RS2)

    /** A crafting part of the toolkit. */
    public static final RegistryEntry<Item, Item> CARPENTERS_HAMMER = ModRegistry.item("carpenters_hammer",
            () -> new Item(new Item.Properties().stacksTo(1)));
    /** A crafting part of the toolkit. */
    public static final RegistryEntry<Item, Item> SAW = ModRegistry.item("saw", () -> new Item(new Item.Properties().stacksTo(1)));
    /** Used up by a rejoin ({@code assembly.rejoin.nails_per_rejoin}). */
    public static final RegistryEntry<Item, Item> NAILS = ModRegistry.item("nails", () -> new Item(new Item.Properties()));
    /**
     * Rejoins split pieces of a ship ({@link ShipRejoin}). The registered durability is the default of
     * {@code assembly.rejoin.toolkit_durability}; each stack follows the config value ({@link ShipwrightToolkitItem}).
     */
    public static final RegistryEntry<Item, ShipwrightToolkitItem> SHIPWRIGHT_TOOLKIT = ModRegistry.item("shipwright_toolkit",
            () -> new ShipwrightToolkitItem(new Item.Properties().durability(64)));
    /** The piece a toolkit is marked on. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<RejoinMark>> REJOIN_MARK = ModRegistry.dataComponent("rejoin_mark",
            b -> b.persistent(RejoinMark.CODEC).networkSynchronized(RejoinMark.STREAM_CODEC));

    private AssemblyContent() {
    }

    public static void init() {
    }
}
