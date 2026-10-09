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
import java.util.List;

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

    /**
     * The armour layer texture is vanilla's 64x32 layout, with the body and both arm areas painted opaque, and (ART9)
     * every face of the tails of {@code CoatArmorModel} too; the captain's coat likewise.
     */
    @Test
    void theWornTexturePaintsBodyArmsAndTails() throws IOException {
        for (Path file : List.of(ARMOR_TEXTURE, ARMOR_DIR.resolve("captains_coat_layer_1.png"))) {
            BufferedImage img = layer(file);
            // body front (20..28 x 20..32) and the right arm's front (44..48 x 20..32), box UV 16,16 and 40,16
            assertOpaque(img, file, new int[][]{{20, 20, 8, 12}, {44, 20, 4, 12}, {16, 20, 4, 12}, {32, 20, 8, 12}});
            // the tails: back plates 5x7x1 at (0,0) and (0,8), side plates 1x7x6 at (12,0) and (26,0); each face
            assertOpaque(img, file, boxFaces(0, 0, 5, 7, 1));
            assertOpaque(img, file, boxFaces(0, 8, 5, 7, 1));
            assertOpaque(img, file, boxFaces(12, 0, 1, 7, 6));
            assertOpaque(img, file, boxFaces(26, 0, 1, 7, 6));
        }
    }

    /** ART9: boots on layer 1 (legs from row 4 down), breeches on layer 2 (legs and the waist band on the body). */
    @Test
    void theCaptainsBootsAndBreechesPaintTheirParts() throws IOException {
        Path boots = ARMOR_DIR.resolve("captains_clothing_layer_1.png"), breeches = ARMOR_DIR.resolve("captains_clothing_layer_2.png");
        BufferedImage b = layer(boots);
        assertOpaque(b, boots, new int[][]{{0, 24, 16, 8}});
        assertEquals(0, b.getRGB(4, 20) >>> 24, "nothing above the boot top");
        assertEquals(0, b.getRGB(20, 24) >>> 24, "nothing on the body");
        BufferedImage l = layer(breeches);
        assertOpaque(l, breeches, new int[][]{{0, 20, 16, 12}, {16, 28, 24, 4}});
        assertEquals(0, l.getRGB(20, 21) >>> 24, "the breeches leave the upper body free");
    }

    private static final Path ARMOR_DIR = ARMOR_TEXTURE.getParent();

    private static BufferedImage layer(Path file) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        assertEquals(64, img.getWidth(), file + " width");
        assertEquals(32, img.getHeight(), file + " height");
        return img;
    }

    /** The six faces of a w x h x d box at (u, v) in vanilla's box UV layout. */
    private static int[][] boxFaces(int u, int v, int w, int h, int d) {
        return new int[][]{{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h},
                {u + 2 * d + w, v + d, w, h}};
    }

    private static void assertOpaque(BufferedImage img, Path file, int[][] areas) {
        for (int[] area : areas) {
            for (int y = area[1]; y < area[1] + area[3]; y++) {
                for (int x = area[0]; x < area[0] + area[2]; x++) {
                    assertEquals(255, img.getRGB(x, y) >>> 24, file.getFileName() + ": opaque pixel at " + x + "," + y);
                }
            }
        }
    }
}
