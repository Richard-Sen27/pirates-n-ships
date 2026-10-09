package com.richardsenger.piratesnships.almanac.sync;

import com.richardsenger.piratesnships.platform.Services;

/** Registers the Almanac's payloads (ALM2). Called from {@code AlmanacData.registerPayloads()}. */
public final class AlmanacPayloads {

    private AlmanacPayloads() {
    }

    public static void register() {
        Services.NETWORK.registerToClient(AlmanacSyncPayload.TYPE, AlmanacSyncPayload.CODEC, (payload, player) -> ClientAlmanac.onSync(payload));
        Services.NETWORK.registerToClient(AlmanacPagePayload.Restore.TYPE, AlmanacPagePayload.Restore.CODEC,
                (payload, player) -> ClientAlmanac.onRestore(payload));
        Services.NETWORK.registerToServer(AlmanacPagePayload.Report.TYPE, AlmanacPagePayload.Report.CODEC, AlmanacSync::onReport);
    }
}
