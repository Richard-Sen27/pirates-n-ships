package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registered content of the visible anchor: the anchor entity and its sound events. */
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
    /** The chain snapping taut (AN2b; placeholder: vanilla anvil landing, played low). */
    public static final RegistryEntry<SoundEvent, SoundEvent> JOLT = ModRegistry.sound("anchor.jolt");
    /** The capstan's pawl while it winds the chain in (AN2b; placeholder: vanilla iron trapdoor closing). */
    public static final RegistryEntry<SoundEvent, SoundEvent> CAPSTAN = ModRegistry.sound("anchor.capstan");

    /** The sound of the running chain ({@code anchor.chain}; its {@code sounds.json} entry comes from {@link AnchorData}). */
    static SoundEvent chainSound() {
        return CHAIN.get();
    }

    static SoundEvent splashSound() {
        return SPLASH.get();
    }

    static SoundEvent thudSound() {
        return THUD.get();
    }

    static SoundEvent joltSound() {
        return JOLT.get();
    }

    static SoundEvent capstanSound() {
        return CAPSTAN.get();
    }

    private AnchorContent() {
    }

    public static void init() {
        // class load registers the entries
    }
}
