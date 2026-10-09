package com.richardsenger.piratesnships.almanac.data;

import com.richardsenger.piratesnships.almanac.client.AlmanacScreenConfig;
import com.richardsenger.piratesnships.almanac.client.AlmanacScreenText;
import com.richardsenger.piratesnships.almanac.sync.AlmanacPayloads;
import com.richardsenger.piratesnships.almanac.sync.AlmanacSync;
import com.richardsenger.piratesnships.almanac.sync.ClientAlmanac;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/**
 * The single entry point of the Almanac's data side (ALM2: chapters, loader, sync, last page). The almanac module calls
 * each hook from the {@code ModModule} method of the same name ({@code almanac.AlmanacModule}).
 */
public final class AlmanacData {

    private AlmanacData() {
    }

    public static void registerConfig() {
        AlmanacDataConfig.init();
        AlmanacScreenConfig.init();
    }

    public static void registerContent() {
        AlmanacAttachments.register();
    }

    public static void registerPayloads() {
        AlmanacPayloads.register();
    }

    public static void registerEvents() {
        CommonEvents.ADD_RELOAD_LISTENERS.register((sink, registries) -> sink.accept(new AlmanacReloadListener()));
        CommonEvents.DATAPACK_SYNC.register(AlmanacSync::onDatapackSync);
        CommonEvents.SERVER_STOPPED.register(server -> AlmanacBook.setServer(AlmanacBook.EMPTY));
    }

    /** Physical client only. */
    public static void initClient() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientAlmanac.reset());
    }

    public static void gatherData(DataContributions data) {
        AlmanacChapterProvider.gather(data);
        data.lang(AlmanacScreenText::lang);
    }

    public static List<Class<?>> gameTestClasses() {
        return List.of(AlmanacDataGameTests.class);
    }
}
