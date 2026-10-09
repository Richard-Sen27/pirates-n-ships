package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.core.config.ConfigValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * A piece of wearable clothing (ART6, ART9): an {@link ArmorItem} on one of our armour materials, so vanilla's
 * {@code HumanoidArmorLayer} draws it on players, armour stands and vanilla humanoids from the material's layer texture
 * ({@code textures/models/armor/<material>_layer_1.png}, {@code _layer_2} for the legs slot), or with a custom armour
 * model where the client registers one (the coats' tails, {@code apparel.client.ApparelClient}). No durability, like
 * the hats; the armour bonus follows its config value live ({@link ClothingArmor}) instead of the material's fixed
 * defence. Right-click swaps it with the worn item of its slot ({@code ArmorItem#use}).
 */
public class ClothingItem extends ArmorItem {

    private final ConfigValue<Integer> armor;

    public ClothingItem(Holder<ArmorMaterial> material, Type type, ConfigValue<Integer> armor, Properties properties) {
        super(material, type, properties);
        this.armor = armor;
    }

    /** The config value of this piece's armour points. */
    public ConfigValue<Integer> armorConfig() {
        return armor;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ClothingArmor.modifiers(getType(), armor.get());
    }
}
