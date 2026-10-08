package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.SpecialOffer;
import com.richardsenger.piratesnships.trade.market.SpecialOffers;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.world.item.ItemStack;

/**
 * The treasure maps (TM1, design.md §10.1) as part of the {@code world} module, which calls these hooks: the item and
 * its component, the fence's market line on pirate islands, the chest listener, datagen and the client overlay.
 */
public final class TreasureMaps {

    private TreasureMaps() {
    }

    public static void registerContent() {
        TreasureMapContent.init();
        SpecialOffers.register(new SpecialOffer(TreasureMapContent.TREASURE_MAP.id(), TreasureMapContent.TREASURE_MAP.id(),
                PortKind.PIRATE_ISLAND, () -> TradeConfig.TREASURE_MAP_PRICE.get(), WorldConfig.TREASURE_MAPS_ENABLED::get,
                () -> new ItemStack(TreasureMapContent.TREASURE_MAP.get())));
    }

    public static void registerEvents() {
        CommonEvents.CONTAINER_OPEN.register(TreasureMapService::onContainerOpen);
    }

    public static void initClient() {
        com.richardsenger.piratesnships.world.treasure.client.TreasureMapClient.init();
    }

    public static void gatherData(DataContributions data) {
        data.lang(lang -> {
            lang.item(TreasureMapContent.TREASURE_MAP, "Treasure Map");
            TreasureMapText.lang(lang);
        });
        // The item model is hand-made (art/models/treasure_map.bbmodel, design.md §4.8, ART4)
    }
}
