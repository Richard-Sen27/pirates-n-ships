package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/**
 * The quest log attachment (the {@code ReputationAttachments} pattern): saved with the player, kept through death,
 * server-only (the Quests tab and the commands show it).
 */
public final class QuestAttachments {

    public static final AttachmentKey<QuestLog> LOG =
            AttachmentKey.builder("quest_log", () -> QuestLog.EMPTY)
                    .persistent(QuestLog.CODEC)
                    .copyOnDeath()
                    .build();

    private QuestAttachments() {
    }

    /** Called from {@code QuestModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(LOG);
    }
}
