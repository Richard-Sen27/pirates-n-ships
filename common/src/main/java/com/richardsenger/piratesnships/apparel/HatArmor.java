package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The hats' attribute rules, without world access: worn in the head slot, a flat armour bonus there (design.md §9,
 * config {@code apparel.hat_armor}). The modifiers are computed when asked, so a config change applies to every hat
 * at once (also to stacks that already exist); 0 means no modifier at all, so the tooltip shows none.
 */
public final class HatArmor {

    /** The slot every hat goes into. */
    public static final EquipmentSlot SLOT = EquipmentSlot.HEAD;

    /** One id for all hats: only one hat can be worn at a time. */
    public static final ResourceLocation MODIFIER_ID = Constants.id("hat_armor");

    private HatArmor() {
    }

    /** The default modifiers of a hat for {@code armor} points: none for 0 or less. */
    public static ItemAttributeModifiers modifiers(int armor) {
        if (armor <= 0) {
            return ItemAttributeModifiers.EMPTY;
        }
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(MODIFIER_ID, armor, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.HEAD)
                .build();
    }
}
