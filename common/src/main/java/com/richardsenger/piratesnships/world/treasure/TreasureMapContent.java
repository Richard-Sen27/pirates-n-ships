package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The treasure map item and its component (TM1). Blank maps stack (16); a bound map carries
 * {@code pirates_n_ships:treasure_map} and stands alone.
 */
public final class TreasureMapContent {

    public static final RegistryEntry<Item, TreasureMapItem> TREASURE_MAP = ModRegistry.item("treasure_map",
            () -> new TreasureMapItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final RegistryEntry<DataComponentType<?>, DataComponentType<TreasureMapData>> TREASURE_MAP_DATA =
            ModRegistry.dataComponent("treasure_map", b -> b.persistent(TreasureMapData.CODEC).networkSynchronized(TreasureMapData.STREAM_CODEC));

    private TreasureMapContent() {
    }

    public static void init() {
    }
}
