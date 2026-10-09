package com.richardsenger.piratesnships.apparel.client;

import com.richardsenger.piratesnships.apparel.ApparelContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the {@code apparel} module (physical client only, from {@code ApparelModule.initClient()}): the coats
 * are drawn with {@link CoatArmorModel}, which adds the tails (ART9). The hats need nothing: vanilla's
 * {@code CustomHeadLayer} draws their item models; the captain's breeches and boots use vanilla's armour models.
 */
public final class ApparelClient {

    private static @Nullable CoatArmorModel coatModel;

    private ApparelClient() {
    }

    public static void init() {
        ClientEvents.registerModelLayer(CoatArmorModel.LAYER, CoatArmorModel::createLayer);
        ClientEvents.registerArmorModel(ApparelClient::coatModel, ApparelContent.OFFICERS_COAT, ApparelContent.CAPTAINS_COAT);
    }

    /** The coat model for the chest slot (baked once, on first use); vanilla's model for any other slot. */
    static HumanoidModel<?> coatModel(LivingEntity wearer, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
        if (slot != EquipmentSlot.CHEST) return original;
        CoatArmorModel model = coatModel;
        if (model == null) {
            model = new CoatArmorModel(Minecraft.getInstance().getEntityModels().bakeLayer(CoatArmorModel.LAYER));
            coatModel = model;
        }
        return model;
    }
}
