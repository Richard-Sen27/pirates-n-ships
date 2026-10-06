package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/**
 * Data attachments of the law module. The criminal record is saved with the entity, survives a player's death and is
 * server-only: attachment sync goes to every tracking client on each change, which the frequent decay updates would
 * turn into spam. A HUD will get the wanted level through its own payload later.
 */
public final class LawAttachments {

    /** Criminal record of a player or other living entity. */
    public static final AttachmentKey<CriminalRecord> CRIMINAL_RECORD =
            AttachmentKey.builder("criminal_record", () -> CriminalRecord.EMPTY)
                    .persistent(CriminalRecord.CODEC)
                    .copyOnDeath()
                    .build();

    private LawAttachments() {
    }

    /** Called from {@code LawModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(CRIMINAL_RECORD);
    }
}
