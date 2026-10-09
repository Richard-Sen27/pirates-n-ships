package com.richardsenger.piratesnships.almanac.client;

import java.util.Objects;

/**
 * The client hook behind the Almanac's "Show me" buttons (ALM3). The screen calls {@link #open(String)} with the
 * element's scene id; until a handler is installed nothing happens. ALM4 installs Ponder's handler with
 * {@link #install(Handler)} from its client init.
 */
public final class AlmanacSceneOpener {

    /** Plays a scene. Called on the client main thread while the Almanac screen is open. */
    @FunctionalInterface
    public interface Handler {

        /** @param sceneId the {@code show_me} element's {@code scene}, e.g. {@code pirates_n_ships:cannon_loading} */
        void openScene(String sceneId);
    }

    /** The handler before ALM4: does nothing. */
    public static final Handler NO_OP = sceneId -> {
    };

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

    /** Opens {@code sceneId} through the installed handler. */
    public static void open(String sceneId) {
        handler.openScene(sceneId);
    }
}
