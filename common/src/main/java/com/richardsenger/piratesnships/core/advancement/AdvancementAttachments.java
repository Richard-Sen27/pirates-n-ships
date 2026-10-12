package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/** The advancement tab's player attachment (ADV1): saved, kept through death, server only. */
public final class AdvancementAttachments {

    public static final AttachmentKey<AdvancementRecord> RECORD =
            AttachmentKey.builder("advancement_record", () -> AdvancementRecord.EMPTY)
                    .persistent(AdvancementRecord.CODEC)
                    .copyOnDeath()
                    .build();

    private AdvancementAttachments() {
    }

    /** Called from {@code AdvancementModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(RECORD);
    }
}
