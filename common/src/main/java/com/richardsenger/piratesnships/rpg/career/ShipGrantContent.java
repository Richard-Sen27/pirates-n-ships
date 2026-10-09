package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Registration of the ship commission ({@code ship_commission}) and its data component (SHP1). */
public final class ShipGrantContent {

    public static final RegistryEntry<DataComponentType<?>, DataComponentType<ShipCommission>> COMMISSION_DATA = ModRegistry.dataComponent(
            "ship_commission", b -> b.persistent(ShipCommission.CODEC).networkSynchronized(ShipCommission.STREAM_CODEC));

    public static final RegistryEntry<Item, ShipCommissionItem> SHIP_COMMISSION = ModRegistry.item("ship_commission",
            () -> new ShipCommissionItem(new Item.Properties().rarity(Rarity.RARE)));

    private ShipGrantContent() {
    }

    /** Loads the class so the entries are registered. Called from {@code CareerModule.registerContent()}. */
    public static void init() {
    }
}
