package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.item.Item;

/** The chart item (work package MAP1): a rolled parchment with a wax seal that opens the player's own chart. */
public final class ChartContent {

    public static final RegistryEntry<Item, ChartItem> CHART = ModRegistry.item("chart", () -> new ChartItem(new Item.Properties().stacksTo(1)));

    private ChartContent() {
    }

    public static void init() {
    }
}
