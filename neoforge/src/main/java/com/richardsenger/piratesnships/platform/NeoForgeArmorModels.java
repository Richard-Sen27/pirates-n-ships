package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Forwards {@link ClientEvents#armorModels()} to NeoForge (ART9): one {@link IClientItemExtensions} per registration
 * whose {@code getHumanoidArmorModel} asks the common provider. NeoForge's default {@code getGenericArmorModel} then
 * copies the pose and part visibility of vanilla's model onto ours before {@code HumanoidArmorLayer} draws it with the
 * material's layer texture. Called from {@link NeoForgeClientSetup} with {@code RegisterClientExtensionsEvent::registerItem}.
 */
public final class NeoForgeArmorModels {

    private NeoForgeArmorModels() {
    }

    /** Registers every armour model through {@code registrar} (NeoForge: {@code RegisterClientExtensionsEvent#registerItem}). */
    public static void register(List<ClientEvents.ArmorModel> models, BiConsumer<IClientItemExtensions, Item[]> registrar) {
        for (ClientEvents.ArmorModel model : models) {
            registrar.accept(new Extension(model.provider()), model.items().stream().map(Supplier::get).toArray(Item[]::new));
        }
    }

    /** The client extension of an item drawn with a custom armour model. */
    public static final class Extension implements IClientItemExtensions {

        private final ClientEvents.ArmorModelProvider provider;

        public Extension(ClientEvents.ArmorModelProvider provider) {
            this.provider = provider;
        }

        public ClientEvents.ArmorModelProvider provider() {
            return provider;
        }

        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
            return provider.model(livingEntity, itemStack, equipmentSlot, original);
        }
    }
}
