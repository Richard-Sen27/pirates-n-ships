package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registered content of the visible anchor: the anchor entity and its three sound events. */
public final class AnchorContent {

    public static final RegistryEntry<EntityType<?>, EntityType<AnchorEntity>> ANCHOR = ModRegistry.entity("anchor",
            () -> EntityType.Builder.<AnchorEntity>of(AnchorEntity::new, MobCategory.MISC)
                    .sized(0.5f, (float) AnchorTravel.HEIGHT).noSummon().fireImmune().clientTrackingRange(10).updateInterval(1));

    /** The running chain (placeholder: vanilla chain steps). */
    public static final RegistryEntry<SoundEvent, SoundEvent> CHAIN = ModRegistry.sound("anchor.chain");
    /** The anchor entering the water (placeholder: vanilla heavy splash). */
    public static final RegistryEntry<SoundEvent, SoundEvent> SPLASH = ModRegistry.sound("anchor.splash");
    /** The anchor landing on the ground (placeholder: vanilla stone digging sounds). */
    public static final RegistryEntry<SoundEvent, SoundEvent> THUD = ModRegistry.sound("anchor.thud");

    /** The sound to play for the running chain: ours once {@code sounds.json} is generated, else the vanilla placeholder. */
    static SoundEvent chainSound() {
        return AnchorData.SOUNDS_JSON ? CHAIN.get() : SoundEvents.CHAIN_STEP;
    }

    static SoundEvent splashSound() {
        return AnchorData.SOUNDS_JSON ? SPLASH.get() : SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    static SoundEvent thudSound() {
        return AnchorData.SOUNDS_JSON ? THUD.get() : SoundEvents.STONE_BREAK;
    }

    private AnchorContent() {
    }

    public static void init() {
        // class load registers the entries
    }
}
