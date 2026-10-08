package com.richardsenger.piratesnships.apparel;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The officer's coat (ART6): chest-slot armour rules and the worn texture vanilla's armour layer reads (run from {@code common/}). */
class CoatArmorTest {

    private static final Path ARMOR_TEXTURE = Path.of("src/main/resources/assets/pirates_n_ships/textures/models/armor/officers_coat_layer_1.png");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theCoatGoesOnTheChestWithTheConfiguredArmour() {
        assertEquals(EquipmentSlot.CHEST, CoatArmor.SLOT);
        assertEquals(EquipmentSlot.CHEST, ArmorItem.Type.CHESTPLATE.getSlot(), "CoatItem is a chestplate-type ArmorItem");
        assertTrue(ArmorItem.class.isAssignableFrom(CoatItem.class), "vanilla's HumanoidArmorLayer draws only ArmorItems");
        ItemAttributeModifiers three = CoatArmor.modifiers(3);
        assertEquals(1, three.modifiers().size());
        ItemAttributeModifiers.Entry entry = three.modifiers().getFirst();
        assertTrue(entry.attribute().is(Attributes.ARMOR), "armour attribute");
        assertEquals(EquipmentSlotGroup.CHEST, entry.slot());
        assertEquals(3.0, entry.modifier().amount());
        assertNotEquals(HatArmor.MODIFIER_ID, CoatArmor.MODIFIER_ID, "hat and coat bonuses stack");
        assertTrue(CoatArmor.modifiers(0).modifiers().isEmpty(), "0 gives no modifier");
    }

    /** The armour layer texture is vanilla's 64x32 layout, with the body and both arm areas painted opaque. */
    @Test
    void theWornTexturePaintsBodyAndArms() throws IOException {
        BufferedImage img = ImageIO.read(ARMOR_TEXTURE.toFile());
        assertEquals(64, img.getWidth());
        assertEquals(32, img.getHeight());
        // body front (20..28 x 20..32) and the right arm's front (44..48 x 20..32), box UV 16,16 and 40,16
        for (int[] area : new int[][]{{20, 20, 8, 12}, {44, 20, 4, 12}, {16, 20, 4, 12}, {32, 20, 8, 12}}) {
            for (int y = area[1]; y < area[1] + area[3]; y++) {
                for (int x = area[0]; x < area[0] + area[2]; x++) {
                    assertEquals(255, img.getRGB(x, y) >>> 24, "opaque pixel at " + x + "," + y);
                }
            }
        }
    }
}
