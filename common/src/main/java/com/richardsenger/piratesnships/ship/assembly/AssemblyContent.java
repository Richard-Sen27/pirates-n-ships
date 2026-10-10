package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Registry content of the {@code ship.assembly} module. */
public final class AssemblyContent {

    /**
     * WOOD4: the helm in every wood ({@code <wood>_helm}); dark oak keeps the legacy id {@code helm} (kept ids,
     * structures, ship templates and Ponder schematics name it). Strength as before WOOD4 (2.0).
     */
    public static final WoodFamily<HelmBlock> HELMS = WoodFamily.register("helm", ShipWood.DARK_OAK,
            wood -> new HelmBlock(wood, WoodFamily.properties(wood).strength(2.0f).noOcclusion()));
    /**
     * The legacy member ({@code helm}, dark oak), for placing a helm; never compare a block against it: every member is
     * a helm ({@code instanceof HelmBlock}).
     */
    public static final RegistryEntry<Block, HelmBlock> HELM = HELMS.lead();

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
