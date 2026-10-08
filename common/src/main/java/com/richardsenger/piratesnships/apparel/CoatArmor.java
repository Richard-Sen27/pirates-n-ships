package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The officer's coat's attribute rules, without world access (ART6, design.md §15 "officer gear"): worn in the chest
 * slot, a flat armour bonus there ({@code apparel.officers_coat_armor}), no toughness. Computed when asked, like
 * {@link HatArmor}, so a config change applies to every coat at once; 0 means no modifier at all.
 */
public final class CoatArmor {

    /** The slot the coat goes into. */
    public static final EquipmentSlot SLOT = EquipmentSlot.CHEST;

    /** One id for every coat: only one can be worn at a time. */
    public static final ResourceLocation MODIFIER_ID = Constants.id("coat_armor");

    private CoatArmor() {
    }

    /** The default modifiers of a coat for {@code armor} points: none for 0 or less. */
    public static ItemAttributeModifiers modifiers(int armor) {
        if (armor <= 0) {
            return ItemAttributeModifiers.EMPTY;
        }
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(MODIFIER_ID, armor, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.CHEST)
                .build();
    }
}
