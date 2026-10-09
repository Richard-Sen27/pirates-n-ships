package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.almanac.sync.ClientAlmanac;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Client entry of the Almanac's screen (ALM3). Physical client only: callers on common code reach it through their
 * client dispatch (ALM1's block and item).
 */
public final class AlmanacClient {

    private AlmanacClient() {
    }

    /**
     * Opens the book at the page the player last read ({@link ClientAlmanac#progress()}).
     *
     * @param tome the placed tome being read, or null when the book is read in hand
     */
    public static void open(@Nullable BlockPos tome) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new AlmanacScreen(ClientAlmanac.book(), ClientAlmanac.progress(), tome));
    }

    /**
     * The screen closed. ALM1 sends its close payload here (the placed tome closes its book); nothing yet.
     *
     * @param tome the tome the screen was opened from, or null
     */
    static void closed(@Nullable BlockPos tome) {
    }
}
