package com.richardsenger.piratesnships.almanac.data;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/**
 * The player's last Almanac page (ALM2): persistent and kept on death. Not synced as an attachment: the server sends it
 * on login with the book ({@code almanac.sync.AlmanacSync}), and the client reports page changes itself.
 */
public final class AlmanacAttachments {

    public static final AttachmentKey<AlmanacProgress> PROGRESS = AttachmentKey.builder("almanac_progress", () -> AlmanacProgress.NONE)
            .persistent(AlmanacProgress.CODEC).copyOnDeath().build();

    private AlmanacAttachments() {
    }

    public static void register() {
        Services.ATTACHMENTS.register(PROGRESS);
    }
}
