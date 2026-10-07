package com.richardsenger.piratesnships.combat.firearms.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationsSetup;
import com.richardsenger.piratesnships.platform.Services;

/**
 * Picks the {@link FirearmAnimations} implementation at client setup ({@code ClientEvents.CLIENT_SETUP}, registered by
 * {@code FirearmsClient.init()}). Must not reference PAL types, so it loads without PAL; only
 * {@link PalFirearmAnimations} does.
 */
public final class FirearmAnimationsSetup {

    /** How far below the melee layer the firearm layer sits, so a sword animation (a stagger) wins. */
    public static final int PRIORITY_BELOW_MELEE = 100;

    private FirearmAnimationsSetup() {
    }

    /** The firearm layer's priority for a melee layer priority (never negative). */
    public static int priority(int meleePriority) {
        return Math.max(0, meleePriority - PRIORITY_BELOW_MELEE);
    }

    /**
     * Installs the PAL animations when PAL is loaded (the layer is registered even when
     * {@code firearm_animations.enabled} is off, so the option can be turned on in game), else keeps
     * {@link NoopFirearmAnimations}. The melee setup already warns when PAL is missing.
     */
    public static void onClientSetup() {
        if (!Services.PLATFORM.isModLoaded(MeleeAnimationsSetup.PAL_MOD_ID)) {
            Constants.LOG.debug("Player Animation Library is not loaded: no firearm animations");
            return;
        }
        FirearmAnimations.install(PalFirearmAnimations.register(priority(MeleeClientConfig.ANIMATIONS_LAYER_PRIORITY.get())));
    }
}
