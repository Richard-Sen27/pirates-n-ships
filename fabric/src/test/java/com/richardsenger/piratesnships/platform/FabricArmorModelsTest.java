package com.richardsenger.piratesnships.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ART13 (the FAB2 port of ART9's armour-model hook): {@code ClientEvents.registerArmorModel} reaches Fabric API's
 * armour renderer registry, which Fabric's {@code HumanoidArmorLayer} mixin consults for every worn piece, and the two
 * steps {@link FabricArmorModels} copies from vanilla's layer and NeoForge's hook behave like them. A vanilla chestplate
 * stands in for the coats (our items exist only under a loader).
 */
class FabricArmorModelsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registeredProvidersReachFabricsArmorRendererRegistry() {
        ClientEvents.ArmorModelProvider provider = (wearer, stack, slot, original) -> original;
        Supplier<Item> chest = () -> Items.CHAINMAIL_CHESTPLATE;
        assertNull(ArmorRendererRegistryImpl.get(Items.CHAINMAIL_CHESTPLATE), "nothing registered before");
        FabricArmorModels.register(List.of(new ClientEvents.ArmorModel(provider, List.of(chest))));
        assertInstanceOf(FabricArmorModels.Renderer.class, ArmorRendererRegistryImpl.get(Items.CHAINMAIL_CHESTPLATE));
        assertNull(ArmorRendererRegistryImpl.get(Items.CHAINMAIL_LEGGINGS), "only the registered items");
    }

    /** Vanilla's {@code HumanoidArmorLayer#setPartVisibility}: a slot shows only the parts it covers. */
    @Test
    void slotPartsMatchVanillasArmorLayer() {
        HumanoidModel<LivingEntity> m = model();
        FabricArmorModels.showSlotParts(m, EquipmentSlot.CHEST);
        assertVisible(m, false, false, true, true, true, false, false);
        FabricArmorModels.showSlotParts(m, EquipmentSlot.LEGS);
        assertVisible(m, false, false, true, false, false, true, true);
        FabricArmorModels.showSlotParts(m, EquipmentSlot.FEET);
        assertVisible(m, false, false, false, false, false, true, true);
        FabricArmorModels.showSlotParts(m, EquipmentSlot.HEAD);
        assertVisible(m, true, true, false, false, false, false, false);
    }

    /** NeoForge's {@code ClientHooks.copyModelProperties}: the replacement takes the pose and the visibility. */
    @Test
    void replacementTakesThePoseAndVisibilityOfTheOriginal() {
        HumanoidModel<LivingEntity> original = model(), replacement = model();
        FabricArmorModels.showSlotParts(original, EquipmentSlot.CHEST);
        original.rightLeg.xRot = 0.7f;
        original.body.xRot = 0.5f;
        original.crouching = true;
        FabricArmorModels.copyModelProperties(original, replacement);
        assertEquals(0.7f, replacement.rightLeg.xRot, "leg swing copied (the tails follow it)");
        assertEquals(0.5f, replacement.body.xRot, "body lean copied");
        assertTrue(replacement.crouching, "crouch copied");
        assertVisible(replacement, false, false, true, true, true, false, false);
        assertFalse(replacement.rightLeg.visible, "a chest model hides the legs");
    }

    private static HumanoidModel<LivingEntity> model() {
        return new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.0f), 0f), 64, 32).bakeRoot());
    }

    private static void assertVisible(HumanoidModel<?> m, boolean head, boolean hat, boolean body, boolean rightArm, boolean leftArm,
                                      boolean rightLeg, boolean leftLeg) {
        assertEquals(List.of(head, hat, body, rightArm, leftArm, rightLeg, leftLeg),
                List.of(m.head.visible, m.hat.visible, m.body.visible, m.rightArm.visible, m.leftArm.visible, m.rightLeg.visible, m.leftLeg.visible));
    }
}
