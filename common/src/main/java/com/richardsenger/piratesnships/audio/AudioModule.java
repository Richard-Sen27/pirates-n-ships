package com.richardsenger.piratesnships.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.audio.creak.ShipCreaks;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import java.util.List;
import net.minecraft.data.PackOutput;

/**
 * The {@code audio} module (docs/design.md §16): the mod's sound events and the hull creaking of rolling ships. Owns
 * the client section {@code audio} ({@link AudioConfig}, taken over from {@code core.settings}) and the server section
 * {@code hull_creaking} ({@link CreakConfig}).
 *
 * <h2>Placeholder sounds</h2>
 * We have no recordings yet. {@code pirates_n_ships:ship.creak} plays vanilla's creaky wooden door opening
 * ({@code minecraft:block/wooden_door/open1}, {@code open2}) and chest lid ({@code minecraft:block/chest/open}),
 * pitched down by the trigger (0.5 to 0.8). To use real recordings, either ship a resource pack with its own
 * {@code assets/pirates_n_ships/sounds.json} entry {@code "ship.creak"} ({@code "replace": true}) and the files, or
 * put {@code .ogg} files under {@code common/src/main/resources/assets/pirates_n_ships/sounds/ship/} and change
 * {@link #CREAK_SOUNDS} to {@code pirates_n_ships:ship/creak1}, … (then regenerate data).
 */
public final class AudioModule implements ModModule {

    /** Sound files of {@code ship.creak} (vanilla placeholders) and their volume in the sound definition. */
    static final List<String> CREAK_SOUNDS = List.of(
            "minecraft:block/wooden_door/open1", "minecraft:block/wooden_door/open2", "minecraft:block/chest/open");
    static final String CREAK_SUBTITLE = "subtitles." + Constants.MOD_ID + ".ship.creak";

    @Override
    public String id() {
        return "audio";
    }

    @Override
    public void registerConfig() {
        AudioConfig.init();
        CreakConfig.init();
    }

    @Override
    public void registerContent() {
        AudioSounds.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(ShipCreaks::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> ShipCreaks.onServerStopped());
        SableShips.onShipRemoved((level, ship, destroyed) -> ShipCreaks.onShipRemoved(level, ship));
    }

    @Override
    public void gatherData(DataContributions data) {
        // an empty directory puts the file at the namespace root: assets/pirates_n_ships/sounds.json
        data.json(PackOutput.Target.RESOURCE_PACK, "", Constants.id("sounds"), AudioModule::soundsJson);
        data.lang(lang -> lang.add(CREAK_SUBTITLE, "Ship creaks"));
    }

    static JsonElement soundsJson() {
        JsonArray sounds = new JsonArray();
        for (String name : CREAK_SOUNDS) {
            JsonObject s = new JsonObject();
            s.addProperty("name", name);
            s.addProperty("volume", 0.8);
            sounds.add(s);
        }
        JsonObject creak = new JsonObject();
        creak.add("sounds", sounds);
        creak.addProperty("subtitle", CREAK_SUBTITLE);
        JsonObject root = new JsonObject();
        root.add(AudioSounds.SHIP_CREAK.id().getPath(), creak);
        return root;
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(AudioGameTests.class);
    }
}
