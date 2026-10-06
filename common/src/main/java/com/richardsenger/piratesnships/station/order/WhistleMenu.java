package com.richardsenger.piratesnships.station.order;

/**
 * Indirection from the whistle item (loaded on both sides) to the radial menu screen (client only). The physical
 * client installs the opener in {@code StationModule#initClient()}; on a dedicated server it stays a no-op, so the
 * item never touches a client class.
 */
public final class WhistleMenu {

    public static final String KEY_TITLE = "screen." + com.richardsenger.piratesnships.Constants.MOD_ID + ".whistle_menu";
    public static final String KEY_HINT = KEY_TITLE + ".hint";
    public static final String KEY_LAST = KEY_TITLE + ".last";

    private static volatile Runnable opener = () -> { };

    private WhistleMenu() {
    }

    /** Client init only: what {@link #open()} does. */
    public static void setOpener(Runnable r) {
        opener = r;
    }

    /** Opens the radial menu on the physical client; does nothing on a server. Call from client-side item use only. */
    public static void open() {
        opener.run();
    }
}
