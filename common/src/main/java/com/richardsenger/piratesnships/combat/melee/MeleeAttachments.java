package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/**
 * The combat state attachment on living entities. Neither persistent nor synced: a fight doesn't survive a restart
 * (everyone starts idle with full stamina), and the state changes every tick, which attachment sync would send to
 * every tracking client each time. {@code net.MeleeStateSync} sends the changes that matter instead.
 */
public final class MeleeAttachments {

    public static final AttachmentKey<CombatState> COMBAT_STATE =
            AttachmentKey.builder("combat_state", () -> CombatState.fresh(MeleeConfig.STAMINA_MAX.get().floatValue())).build();

    private MeleeAttachments() {
    }

    /** Called from {@code MeleeModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(COMBAT_STATE);
    }
}
