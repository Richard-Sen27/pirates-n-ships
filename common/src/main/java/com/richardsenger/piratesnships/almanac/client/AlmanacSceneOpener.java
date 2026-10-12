package com.richardsenger.piratesnships.almanac.client;

import java.util.Objects;

/**
 * The client hook behind the Almanac's "Show me" buttons (ALM3). The screen calls {@link #open(String)} with the
 * element's scene id; until a handler is installed nothing happens. ALM4 installs Ponder's handler with
 * {@link #install(Handler)} from its client init ({@code almanac.ponder.AlmanacPonder#register}). A handler that opens a
 * scene replaces the book screen; one that returns false leaves the reader on the page.
 */
public final class AlmanacSceneOpener {

    /** Plays a scene. Called on the client main thread while the Almanac screen is open. */
    @FunctionalInterface
    public interface Handler {

        /**
         * @param sceneId the {@code show_me} element's {@code scene}, e.g. {@code pirates_n_ships:cannon_loading}
         * @return whether a scene was opened (false: unknown id, the handler logs why)
         */
        boolean openScene(String sceneId);
    }

    /** The handler before ALM4: opens nothing. */
    public static final Handler NO_OP = sceneId -> false;

    private static volatile Handler handler = NO_OP;

    private AlmanacSceneOpener() {
    }

    /** Replaces the handler. */
    public static void install(Handler h) {
        handler = Objects.requireNonNull(h, "handler");
    }

    /** The installed handler ({@link #NO_OP} by default). */
    public static Handler handler() {
        return handler;
    }

    /** Opens {@code sceneId} through the installed handler; whether a scene opened. */
    public static boolean open(String sceneId) {
        boolean opened = handler.openScene(sceneId);
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(sceneId);
        if (opened && id != null) { // ADV1: Ponder runs on the client only, so the server learns of it here
            com.richardsenger.piratesnships.platform.Services.NETWORK.sendToServer(
                    new com.richardsenger.piratesnships.core.advancement.SceneOpenedPayload(id));
        }
        return opened;
    }
}
