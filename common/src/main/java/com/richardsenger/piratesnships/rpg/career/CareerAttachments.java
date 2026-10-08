package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/**
 * Data attachment of the careers module (the {@code ReputationAttachments} pattern): saved with the player, copied on
 * death, server-only. The owner gets the ranks through {@link CareerSyncPayload}.
 */
public final class CareerAttachments {

    public static final AttachmentKey<CareerRecord> CAREER =
            AttachmentKey.builder("career", () -> CareerRecord.EMPTY)
                    .persistent(CareerRecord.CODEC)
                    .copyOnDeath()
                    .build();

    private CareerAttachments() {
    }

    /** Called from {@code CareerModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(CAREER);
    }
}
