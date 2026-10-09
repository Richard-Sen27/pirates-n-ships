package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.core.config.ConfigValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * A wearable coat (ART6, ART9): the officer's coat and the captain's coat, chest-slot {@link ClothingItem}s. The client
 * draws them with the coat model that adds the tails ({@code apparel.client.CoatArmorModel}); a loader without that hook
 * would draw the coat to the waist from the same texture with vanilla's armour model.
 */
public class CoatItem extends ClothingItem {

    public CoatItem(Holder<ArmorMaterial> material, ConfigValue<Integer> armor, Properties properties) {
        super(material, Type.CHESTPLATE, armor, properties);
    }
}
