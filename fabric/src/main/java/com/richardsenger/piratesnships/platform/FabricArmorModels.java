package com.richardsenger.piratesnships.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Forwards {@link ClientEvents#armorModels()} to Fabric API's {@link ArmorRenderer} (ART9 coats, FAB2), the twin of
 * {@code NeoForgeArmorModels}. Fabric replaces vanilla's whole {@code HumanoidArmorLayer#renderArmorPiece} for a
 * registered item, so {@link Renderer} redoes what NeoForge's patched layer does around its armour-model hook:
 * <ol>
 *   <li>vanilla's armour model for the slot (inner for legs, outer otherwise) gets the wearer's pose
 *       ({@code copyPropertiesTo} from the context model) and the slot's part visibility ({@link #showSlotParts});</li>
 *   <li>the common provider picks the model ({@code original} is that vanilla model);</li>
 *   <li>a different model gets the pose and the visibility of {@code original} ({@link #copyModelProperties}, the copy of
 *       NeoForge's {@code ClientHooks.copyModelProperties});</li>
 *   <li>it is drawn like vanilla: each material layer with its texture and dye colour, the trim, the glint.</li>
 * </ol>
 * One difference: vanilla's armour model depends on the wearer's renderer (players, zombies, armour stands); Fabric does
 * not pass it, so {@code original} is always the player armour model. Only matters for a provider that returns it
 * (the coats do for every slot but the chest, where they never sit).
 */
public final class FabricArmorModels {

    private FabricArmorModels() {
    }

    /** Registers every armour model with Fabric API (client init; Fabric's registries are filled by then). */
    public static void register(List<ClientEvents.ArmorModel> models) {
        for (ClientEvents.ArmorModel model : models) {
            ArmorRenderer.register(new Renderer(model.provider()), model.items().stream().map(Supplier::get).toArray(ItemLike[]::new));
        }
    }

    /** Vanilla's {@code HumanoidArmorLayer#setPartVisibility}: only the parts the slot covers are drawn. */
    static void showSlotParts(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> { }
        }
    }

    /** Pose and part visibility of {@code original} onto {@code replacement} (NeoForge's {@code ClientHooks.copyModelProperties}). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void copyModelProperties(HumanoidModel<?> original, HumanoidModel<?> replacement) {
        ((HumanoidModel) original).copyPropertiesTo(replacement);
        replacement.head.visible = original.head.visible;
        replacement.hat.visible = original.hat.visible;
        replacement.body.visible = original.body.visible;
        replacement.rightArm.visible = original.rightArm.visible;
        replacement.leftArm.visible = original.leftArm.visible;
        replacement.rightLeg.visible = original.rightLeg.visible;
        replacement.leftLeg.visible = original.leftLeg.visible;
    }

    /** Draws one registration's items in their slot, see the class comment. Render thread only. */
    static final class Renderer implements ArmorRenderer {

        private final ClientEvents.ArmorModelProvider provider;
        private @Nullable HumanoidModel<LivingEntity> innerModel;
        private @Nullable HumanoidModel<LivingEntity> outerModel;

        Renderer(ClientEvents.ArmorModelProvider provider) {
            this.provider = provider;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, ItemStack stack, LivingEntity entity, EquipmentSlot slot,
                           int light, HumanoidModel<LivingEntity> contextModel) {
            // vanilla draws an armour item only in its own slot; Fabric calls us for any slot holding the item
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) return;
            boolean inner = slot == EquipmentSlot.LEGS;
            HumanoidModel<LivingEntity> original = vanillaModel(inner);
            contextModel.copyPropertiesTo(original);
            showSlotParts(original, slot);
            HumanoidModel<?> model = provider.model(entity, stack, slot, original);
            if (model != original) copyModelProperties(original, model);

            Holder<ArmorMaterial> material = armor.getMaterial();
            int dye = stack.is(ItemTags.DYEABLE)
                    ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR)) : -1;
            for (ArmorMaterial.Layer layer : material.value().layers()) {
                VertexConsumer buffer = buffers.getBuffer(RenderType.armorCutoutNoCull(layer.texture(inner)));
                model.renderToBuffer(poseStack, buffer, light, OverlayTexture.NO_OVERLAY, layer.dyeable() ? dye : -1);
            }
            ArmorTrim trim = stack.get(DataComponents.TRIM);
            if (trim != null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                        .getSprite(inner ? trim.innerTexture(material) : trim.outerTexture(material));
                VertexConsumer buffer = sprite.wrap(buffers.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
                model.renderToBuffer(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
            }
            if (stack.hasFoil()) {
                model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY);
            }
        }

        /** The player's armour model (baked once): what {@code HumanoidArmorLayer} would hand NeoForge's hook. */
        private HumanoidModel<LivingEntity> vanillaModel(boolean inner) {
            if (inner) {
                if (innerModel == null) {
                    innerModel = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
                }
                return innerModel;
            }
            if (outerModel == null) {
                outerModel = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
            }
            return outerModel;
        }
    }
}
