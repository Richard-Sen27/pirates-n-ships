package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

import java.util.List;

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

    /**
     * The ranks whose promotion gifts the player already received (CAR2, {@link CareerRewards}): keys of
     * {@link CareerRewardRules#giftKey}. Kept through death and resignation so a rank is gifted once.
     */
    public static final AttachmentKey<List<String>> GIFTS =
            AttachmentKey.<List<String>>builder("career_gifts", List::of)
                    .persistent(Codec.STRING.listOf())
                    .copyOnDeath()
                    .build();

    private CareerAttachments() {
    }

    /** Called from {@code CareerModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(CAREER);
        Services.ATTACHMENTS.register(GIFTS);
    }
}
