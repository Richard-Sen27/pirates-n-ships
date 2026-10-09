package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The attribute rules of worn clothing (ART6, ART9: the coats, the captain's breeches and boots), without world access:
 * a flat armour bonus in the piece's own slot from the config, no toughness, computed when asked like {@link HatArmor}
 * so a config change applies to every stack at once; 0 means no modifier at all. One modifier id per slot: pieces in
 * different slots stack, and only one piece per slot can be worn.
 */
public final class ClothingArmor {

    /** The chest piece's modifier id (the coats); the same as {@link CoatArmor#MODIFIER_ID}. */
    public static final ResourceLocation COAT_ID = Constants.id("coat_armor");
    public static final ResourceLocation LEGS_ID = Constants.id("breeches_armor");
    public static final ResourceLocation FEET_ID = Constants.id("boots_armor");
    public static final ResourceLocation HEAD_ID = Constants.id("clothing_head_armor");

    private ClothingArmor() {
    }

    /** The modifier id of a piece of {@code type}. */
    public static ResourceLocation modifierId(ArmorItem.Type type) {
        return switch (type) {
            case CHESTPLATE, BODY -> COAT_ID;
            case LEGGINGS -> LEGS_ID;
            case BOOTS -> FEET_ID;
            case HELMET -> HEAD_ID;
        };
    }

    /** The default modifiers of a piece of {@code type} for {@code armor} points: none for 0 or less. */
    public static ItemAttributeModifiers modifiers(ArmorItem.Type type, int armor) {
        if (armor <= 0) {
            return ItemAttributeModifiers.EMPTY;
        }
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(modifierId(type), armor, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.bySlot(type.getSlot()))
                .build();
    }
}
