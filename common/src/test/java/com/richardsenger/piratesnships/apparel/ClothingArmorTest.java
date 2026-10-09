package com.richardsenger.piratesnships.apparel;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ART9: the armour rule of the captain's coat, breeches and boots (and the coats, {@link CoatArmor}). */
class ClothingArmorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void eachPieceArmoursItsOwnSlot() {
        for (ArmorItem.Type type : List.of(ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
            ItemAttributeModifiers mods = ClothingArmor.modifiers(type, 2);
            assertEquals(1, mods.modifiers().size(), type + ": one modifier");
            ItemAttributeModifiers.Entry entry = mods.modifiers().getFirst();
            assertTrue(entry.attribute().is(Attributes.ARMOR), type + ": armour");
            assertEquals(EquipmentSlotGroup.bySlot(type.getSlot()), entry.slot(), type + ": slot group");
            assertEquals(2.0, entry.modifier().amount(), type + ": amount");
            assertEquals(ClothingArmor.modifierId(type), entry.modifier().id(), type + ": id");
        }
    }

    @Test
    void piecesInDifferentSlotsStack() {
        Set<Object> ids = new HashSet<>();
        for (ArmorItem.Type type : List.of(ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
            assertTrue(ids.add(ClothingArmor.modifierId(type)), "a modifier id of its own for " + type);
        }
        assertTrue(!ids.contains(HatArmor.MODIFIER_ID), "the hat's bonus stacks with the clothing");
        assertEquals(CoatArmor.MODIFIER_ID, ClothingArmor.modifierId(ArmorItem.Type.CHESTPLATE), "both coats share the chest id");
        assertEquals(CoatArmor.modifiers(3), ClothingArmor.modifiers(ArmorItem.Type.CHESTPLATE, 3));
    }

    @Test
    void zeroOrLessGivesNoModifier() {
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            assertTrue(ClothingArmor.modifiers(type, 0).modifiers().isEmpty(), type + " at 0");
            assertTrue(ClothingArmor.modifiers(type, -1).modifiers().isEmpty(), type + " at -1");
        }
    }

    @Test
    void configDefaultsLikeLeatherArmour() {
        assertEquals(3, ApparelConfig.CAPTAINS_COAT_ARMOR.get(), "coat like a leather tunic");
        assertEquals(2, ApparelConfig.CAPTAINS_BREECHES_ARMOR.get(), "breeches like leather pants");
        assertEquals(1, ApparelConfig.CAPTAINS_BOOTS_ARMOR.get(), "boots like leather boots");
        assertEquals(3, ApparelConfig.OFFICERS_COAT_ARMOR.get(), "the officer's coat is unchanged");
    }
}
