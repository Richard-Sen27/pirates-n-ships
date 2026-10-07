package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.platform.Services;

/**
 * Picks the {@link MeleeAnimations} implementation at client setup ({@code ClientEvents.CLIENT_SETUP}, registered by
 * {@code MeleeClient.init()}). This class must not reference PAL types, so it loads without PAL; only
 * {@link PalMeleeAnimations} does, and that class is loaded only when PAL is present.
 */
public final class MeleeAnimationsSetup {

    /** PAL's mod id. */
    public static final String PAL_MOD_ID = "player_animation_library";

    private MeleeAnimationsSetup() {
    }

    /**
     * Installs the PAL animations when PAL is loaded (its layer is registered even when {@code melee_animations.enabled}
     * is off, so the option can be turned on in game), else keeps {@link NoopMeleeAnimations}.
     */
    public static void onClientSetup() {
        if (!Services.PLATFORM.isModLoaded(PAL_MOD_ID)) {
            Constants.LOG.warn("Player Animation Library is not loaded: sword animations fall back to vanilla's hand swing");
            return;
        }
        MeleeAnimations.install(PalMeleeAnimations.register(MeleeClientConfig.ANIMATIONS_LAYER_PRIORITY.get()));
    }
}
