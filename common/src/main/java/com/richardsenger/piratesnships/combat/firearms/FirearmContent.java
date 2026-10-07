package com.richardsenger.piratesnships.combat.firearms;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

/**
 * Registered content of the firearms: the {@code firearm_loaded} data component and the lead ball entity. The gun
 * items themselves stay in {@code combat.content.CombatContent} (as {@link FirearmItem}s).
 */
public final class FirearmContent {

    /** Present and {@code true} on a loaded gun; removed when it fires. Saved and synced to the client. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<Boolean>> LOADED = ModRegistry.dataComponent(
            "firearm_loaded", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final RegistryEntry<EntityType<?>, EntityType<LeadBallEntity>> LEAD_BALL = ModRegistry.entity("lead_ball",
            () -> EntityType.Builder.<LeadBallEntity>of(LeadBallEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f).noSummon().clientTrackingRange(4).updateInterval(10));

    private FirearmContent() {
    }

    public static void init() {
        // class load registers the entries
    }

    public static boolean isLoaded(ItemStack stack) {
        return stack.getOrDefault(LOADED.get(), false);
    }

    public static void setLoaded(ItemStack stack, boolean loaded) {
        if (loaded) {
            stack.set(LOADED.get(), true);
        } else {
            stack.remove(LOADED.get());
        }
    }
}
