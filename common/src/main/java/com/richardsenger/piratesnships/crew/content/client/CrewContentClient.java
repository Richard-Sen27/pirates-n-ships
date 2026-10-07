package com.richardsenger.piratesnships.crew.content.client;

import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.BiomeColors;

/** Client setup of the {@code crew.content} module (physical client only, from {@code CrewContentModule.initClient()}). */
public final class CrewContentClient {

    /** The {@code tintindex} of the water surface faces in the hand-made water barrel models. */
    public static final int WATER_TINT_INDEX = 0;
    /** Vanilla's default water colour, used without a level (e.g. particles). */
    public static final int DEFAULT_WATER_COLOR = 0x3F76E4;

    private CrewContentClient() {
    }

    public static void init() {
        // The water barrel's surface is greyscale water_still, tinted like water in the biome it stands in (on a Sable
        // ship: the biome of the ship's plot, like vanilla water blocks there)
        ClientEvents.registerBlockColor((state, level, pos, tintIndex) -> {
            if (tintIndex != WATER_TINT_INDEX) return -1;
            return level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : DEFAULT_WATER_COLOR;
        }, CrewContent.WATER_BARREL);
    }
}
