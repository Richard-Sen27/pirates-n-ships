package com.richardsenger.piratesnships.apparel;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * A wearable coat (ART6): the officer's coat. An {@link ArmorItem} for the chest slot, so vanilla's
 * {@code HumanoidArmorLayer} draws it on players, armour stands and vanilla humanoids from the material's layer texture
 * ({@code textures/models/armor/<material>_layer_1.png}): the chest counterpart of the hats, which vanilla's
 * {@code CustomHeadLayer} draws. No durability, like the hats; the armour bonus follows the config live
 * ({@link CoatArmor}) instead of the material's fixed defence. Right-click swaps it with the worn chest item
 * ({@code ArmorItem#use}).
 */
public class CoatItem extends ArmorItem {

    public CoatItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.CHESTPLATE, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return CoatArmor.modifiers(ApparelConfig.OFFICERS_COAT_ARMOR.get());
    }
}
