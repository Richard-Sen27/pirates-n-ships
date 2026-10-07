package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Registration of the ship receipt ({@code ship_receipt}) and its data component (SW1). */
public final class ShipOrderContent {

    public static final RegistryEntry<DataComponentType<?>, DataComponentType<ShipReceipt>> RECEIPT_DATA = ModRegistry.dataComponent(
            "ship_receipt", b -> b.persistent(ShipReceipt.CODEC).networkSynchronized(ShipReceipt.STREAM_CODEC));

    public static final RegistryEntry<Item, ShipReceiptItem> SHIP_RECEIPT = ModRegistry.item("ship_receipt",
            () -> new ShipReceiptItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    private ShipOrderContent() {
    }

    /** Loads the class so the entries are registered. Called from {@link ShipTemplates#init()}. */
    public static void init() {
    }
}
