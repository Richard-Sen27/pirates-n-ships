package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartSightings;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;

/**
 * The player's chart (work package MAP1): persistent, kept on death, never synced as a whole (the server streams
 * regions to the open chart screen instead).
 */
public final class ChartAttachments {

    public static final AttachmentKey<ChartData> CHART = AttachmentKey.builder("chart", () -> ChartData.EMPTY)
            .persistent(ChartData.CODEC).copyOnDeath().build();
    /** CHART2: the ports and structures the player has come near ({@link ChartStructures}); never synced as a whole. */
    public static final AttachmentKey<ChartSightings> SIGHTINGS = AttachmentKey.builder("chart_sightings", () -> ChartSightings.EMPTY)
            .persistent(ChartSightings.CODEC).copyOnDeath().build();

    private ChartAttachments() {
    }

    public static void register() {
        Services.ATTACHMENTS.register(CHART);
        Services.ATTACHMENTS.register(SIGHTINGS);
        Services.ATTACHMENTS.register(com.richardsenger.piratesnships.chart.held.ChartHeld.READING);
    }
}
