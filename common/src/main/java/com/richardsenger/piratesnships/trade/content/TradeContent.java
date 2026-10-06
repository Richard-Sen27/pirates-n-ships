package com.richardsenger.piratesnships.trade.content;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Currency (design.md §10.2) and the colonial trade goods vanilla lacks (§10.3). No behavior yet. */
public final class TradeContent {

    /** Gold doubloon, the main currency. Loot and trade only, no recipe. */
    public static final RegistryEntry<Item, Item> DOUBLOON = ModRegistry.item("doubloon", () -> new Item(new Item.Properties()));
    public static final RegistryEntry<Item, Item> TOBACCO = ModRegistry.item("tobacco", () -> new Item(new Item.Properties()));
    public static final RegistryEntry<Item, Item> SPICES = ModRegistry.item("spices", () -> new Item(new Item.Properties()));
    public static final RegistryEntry<Item, Item> CLOTH = ModRegistry.item("cloth", () -> new Item(new Item.Properties()));

    /** Rum: a trade good and a provision (§7.4). Drinkable; the bottle is returned. Morale effects come later. */
    public static final FoodProperties RUM_FOOD = new FoodProperties.Builder()
            .nutrition(1).saturationModifier(0.1f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE).build();
    public static final RegistryEntry<Item, DrinkItem> RUM = ModRegistry.item("rum",
            () -> new DrinkItem(new Item.Properties().stacksTo(16).food(RUM_FOOD)));

    private TradeContent() {
    }

    public static void init() {
    }
}
