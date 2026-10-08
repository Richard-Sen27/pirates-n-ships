package com.richardsenger.piratesnships.rpg.reputation;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/**
 * Data attachment of the rpg module (the {@code law.LawAttachments} pattern). The reputation is saved with the player
 * and survives death; it is server-only, because attachment sync would go to every tracking client. The owner gets
 * the shown scores through {@link ReputationSyncPayload} instead.
 */
public final class ReputationAttachments {

    public static final AttachmentKey<ReputationRecord> REPUTATION =
            AttachmentKey.builder("reputation", () -> ReputationRecord.EMPTY)
                    .persistent(ReputationRecord.CODEC)
                    .copyOnDeath()
                    .build();

    private ReputationAttachments() {
    }

    /** Called from {@code RpgModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(REPUTATION);
    }
}
