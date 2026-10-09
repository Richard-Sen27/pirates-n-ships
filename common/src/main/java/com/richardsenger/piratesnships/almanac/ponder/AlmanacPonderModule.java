package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;

/**
 * The {@code almanac.ponder} module (ALM4, docs/design.md §3.4, docs/almanac.md "Scenes"): Ponder scenes for the
 * Captain's Almanac's "Show me" buttons and the Ponder key on items.
 *
 * <ul>
 *   <li>Client: {@link AlmanacPonder#register()} adds {@link AlmanacPonderPlugin} to Ponder; {@link AlmanacPonder#show}
 *       is the hook the Almanac screen calls. Nothing runs on a dedicated server (Ponder is client-side).</li>
 *   <li>Datagen: the scenes' English lang ({@link CannonLoadingText#lang()}).</li>
 *   <li>The scene schematics are resources from the structure pipeline ({@code art/structures/ponder/}).</li>
 * </ul>
 * No config toggle: the scenes change no gameplay.
 */
public final class AlmanacPonderModule implements ModModule {

    @Override
    public String id() {
        return "almanac.ponder";
    }

    @Override
    public void initClient() {
        AlmanacPonder.register();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> CannonLoadingText.lang().forEach(lang::add));
    }
}
