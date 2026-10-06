package com.richardsenger.piratesnships.crew.provisions;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Item tags under {@code pirates_n_ships:provisions/…} that decide how items count as provisions. Filled by datagen. */
public final class ProvisionTags {

    /** Food that never spoils (hardtack, salted fish, salt pork; vanilla: bread, cookies, dried kelp, ...). */
    public static final TagKey<Item> PRESERVED = tag("preserved");
    /** "Citrus": food that prevents and cures scurvy even when preserved or when fresh food doesn't count. */
    public static final TagKey<Item> ANTI_SCURVY = tag("anti_scurvy");
    /** Rum, one ration per item. */
    public static final TagKey<Item> RUM = tag("rum");
    /** Fresh water, one ration per item (water buckets count {@code water_bucket_rations}). */
    public static final TagKey<Item> FRESH_WATER = tag("fresh_water");
    /** Water barrels, {@code water_barrel_rations} per item. */
    public static final TagKey<Item> WATER_BARREL = tag("water_barrel");
    /** Food the crew refuses to eat (poisonous or rotten). */
    public static final TagKey<Item> EXCLUDED = tag("excluded");

    private ProvisionTags() {
    }

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, Constants.id("provisions/" + name));
    }
}
