package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.item.Item;

import java.util.List;

/** The wearable hats (design.md §9): each one copies the hat of a seafarer mob. */
public final class ApparelContent {

    /** A black tricorn with a skull on the front: the soldier's hat in pirate colours. */
    public static final RegistryEntry<Item, HatItem> PIRATE_HAT = hat("pirate_hat");
    /** The pirate's red bandana with its knot and tails. */
    public static final RegistryEntry<Item, HatItem> BANDANA = hat("bandana");
    /** The navy soldier's tricorn, edged in white, with a black cockade. */
    public static final RegistryEntry<Item, HatItem> NAVY_HAT = hat("navy_hat");
    /** The navy officer's bicorne, worn athwart, edged in gold, with a gold loop and cockade. */
    public static final RegistryEntry<Item, HatItem> OFFICER_HAT = hat("officer_hat");

    public static final List<RegistryEntry<Item, HatItem>> HATS = List.of(PIRATE_HAT, BANDANA, NAVY_HAT, OFFICER_HAT);

    private ApparelContent() {
    }

    private static RegistryEntry<Item, HatItem> hat(String name) {
        return ModRegistry.item(name, () -> new HatItem(new Item.Properties().stacksTo(1)));
    }

    public static void init() {
    }
}
