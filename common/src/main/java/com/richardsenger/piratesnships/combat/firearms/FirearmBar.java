package com.richardsenger.piratesnships.combat.firearms;

/**
 * Pure rules of the gun's item bar (vanilla's durability bar under the slot, docs/design.md §8.1): a white bar that
 * fills up while the local player loads the gun, a full gold bar on a loaded gun, no bar on an empty one.
 */
public final class FirearmBar {

    /** Width of vanilla's item bar in GUI pixels. */
    public static final int MAX_WIDTH = 13;
    /** Colour of the bar while loading (white). */
    public static final int LOADING_COLOR = 0xFFFFFF;
    /** Colour of the bar on a loaded gun ({@code ChatFormatting.GOLD}). */
    public static final int LOADED_COLOR = 0xFFAA00;

    /** What the bar shows. */
    public enum State { NONE, LOADING, LOADED }

    private FirearmBar() {
    }

    /**
     * The bar's state: loaded wins (also while the player keeps holding after loading finished), then a running
     * loading session, otherwise no bar.
     */
    public static State state(boolean loaded, boolean loadingSession) {
        if (loaded) return State.LOADED;
        return loadingSession ? State.LOADING : State.NONE;
    }

    public static boolean visible(State state) {
        return state != State.NONE;
    }

    /** Width of a loading bar after {@code heldTicks} of a {@code reloadTicks} load: 0 to {@link #MAX_WIDTH}, clamped. */
    public static int loadingWidth(int heldTicks, int reloadTicks) {
        return Math.round(MAX_WIDTH * FirearmRules.reloadProgress(heldTicks, reloadTicks));
    }

    /** Width of the bar: full when loaded, the loading progress while loading, 0 without a bar. */
    public static int width(State state, int heldTicks, int reloadTicks) {
        return switch (state) {
            case LOADED -> MAX_WIDTH;
            case LOADING -> loadingWidth(heldTicks, reloadTicks);
            case NONE -> 0;
        };
    }

    public static int color(State state) {
        return state == State.LOADED ? LOADED_COLOR : LOADING_COLOR;
    }
}
