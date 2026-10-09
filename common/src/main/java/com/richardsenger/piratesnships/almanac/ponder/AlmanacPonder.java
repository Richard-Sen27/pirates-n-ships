package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.client.AlmanacSceneOpener;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * The client side of our Ponder scenes (ALM4, physical client only): adds {@link AlmanacPonderPlugin} to Ponder and
 * opens a scene for the Almanac's "Show me" buttons (installed as the {@link AlmanacSceneOpener} handler).
 */
public final class AlmanacPonder {

    private AlmanacPonder() {
    }

    /**
     * Adds our plugin to Ponder's index. Called from {@link AlmanacPonderModule#initClient()} during mod construction
     * (NeoForge) or the client entry point (Fabric), before Ponder registers every plugin's scenes when loading
     * completes (NeoForge {@code FMLLoadCompleteEvent}, Fabric {@code CLIENT_STARTED}).
     */
    public static void register() {
        PonderIndex.addPlugin(new AlmanacPonderPlugin());
        AlmanacSceneOpener.install(AlmanacPonder::show);
        Constants.LOG.info("Ponder: registered {} scene(s) of {}", AlmanacScenes.ALL.size(), Constants.MOD_ID);
    }

    /**
     * Opens Ponder for a "Show me" id, on the client thread: one of our scene ids ({@code pirates_n_ships:cannon_loading})
     * opens exactly that scene; an item id opens every scene Ponder has for the item (ours and other mods'). An id
     * Ponder knows nothing about is logged and ignored.
     *
     * @return whether a Ponder screen was opened
     */
    public static boolean show(ResourceLocation id) {
        try {
            ResourceLocation component = AlmanacScenes.resolve(id).map(AlmanacScenes.Target::component).orElse(id);
            if (!PonderIndex.getSceneAccess().doScenesExistForId(component)) {
                Constants.LOG.warn("Ponder: no scene for {}, nothing to show", id);
                return false;
            }
            AlmanacScenes.Scene scene = AlmanacScenes.byId(id).orElse(null);
            List<StoryBoardEntry> entries = scene == null ? List.of() : entriesOf(scene);
            if (entries.isEmpty()) {
                Constants.LOG.info("Ponder: showing the scenes of {}", component);
                ScreenOpener.transitionTo(PonderUI.of(component));
            } else {
                Constants.LOG.info("Ponder: showing the scene {}", id);
                ScreenOpener.transitionTo(new SceneUI(PonderIndex.getSceneAccess().compile(entries)));
            }
            return true;
        } catch (RuntimeException e) {
            Constants.LOG.error("Ponder: could not show {}", id, e);
            return false;
        }
    }

    /**
     * The Almanac's "Show me" handler ({@link AlmanacSceneOpener}): {@link #show(ResourceLocation)} for a valid id, false
     * (logged) for a malformed one. Ponder's {@code ScreenOpener} keeps the book screen on its back stack: Ponder's back
     * button or Backspace returns to the same page; Escape closes Ponder and with it the book.
     */
    public static boolean show(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            Constants.LOG.warn("Ponder: \"{}\" is not a scene id, nothing to show", id);
            return false;
        }
        return show(rl);
    }

    /** Ponder's registered storyboard of one of our scenes: the entry of its item with its schematic. */
    private static List<StoryBoardEntry> entriesOf(AlmanacScenes.Scene scene) {
        return PonderIndex.getSceneAccess().getRegisteredEntries().stream()
                .filter(e -> e.getKey().equals(scene.component()))
                .map(Map.Entry::getValue)
                .filter(e -> e.getSchematicLocation().equals(scene.schematic()))
                .toList();
    }

    /** Ponder's screen with a chosen list of scenes (its constructor taking scenes is protected). */
    private static final class SceneUI extends PonderUI {
        SceneUI(List<PonderScene> scenes) {
            super(scenes);
        }
    }
}
