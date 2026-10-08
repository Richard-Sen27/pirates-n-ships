package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/** Data attachments of the squads (MOB2), saved with the entity, server-only. */
public final class SquadAttachments {

    /** The post of a garrison mob (soldier or officer), set when the garrison is placed. */
    public static final AttachmentKey<GarrisonPost> POST =
            AttachmentKey.builder("garrison_post", () -> GarrisonPost.NONE).persistent(GarrisonPost.CODEC).build();

    /** An officer's squad: route, state, members. */
    public static final AttachmentKey<SquadData> SQUAD =
            AttachmentKey.builder("squad", () -> SquadData.EMPTY).persistent(SquadData.CODEC).build();

    private SquadAttachments() {
    }

    /** Called from {@code SquadModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(POST);
        Services.ATTACHMENTS.register(SQUAD);
    }
}
