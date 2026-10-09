package com.richardsenger.piratesnships.apparel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The coats' attribute rules, without world access (ART6, design.md §15 "officer gear"): worn in the chest slot, a flat
 * armour bonus there ({@code apparel.officers_coat_armor}, {@code apparel.captains_coat_armor}), no toughness. The chest
 * case of {@link ClothingArmor}; 0 means no modifier at all.
 */
public final class CoatArmor {

    /** The slot the coat goes into. */
    public static final EquipmentSlot SLOT = EquipmentSlot.CHEST;

    /** One id for every coat: only one can be worn at a time. */
    public static final ResourceLocation MODIFIER_ID = ClothingArmor.COAT_ID;

    private CoatArmor() {
    }

    /** The default modifiers of a coat for {@code armor} points: none for 0 or less. */
    public static ItemAttributeModifiers modifiers(int armor) {
        return ClothingArmor.modifiers(ArmorItem.Type.CHESTPLATE, armor);
    }
}
