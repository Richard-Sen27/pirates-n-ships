package com.richardsenger.piratesnships.mob;

/**
 * What a navy soldier is doing with its musket, for the client's animations (M6): {@code musket_aim} (held),
 * {@code musket_reload} (once, stretched to the reload time) and {@code musket_shove} (once) on the shared crew rig.
 * The server picks it every tick ({@link #choose}) and syncs it packed with the reload length ({@link #pack}), so the
 * client stretches the reload to the server's {@code mobs.musket_reload_ticks} without reading the server config. Pure.
 */
public enum MusketAction {
    NONE, AIM, RELOAD, SHOVE;

    /** Length of {@code musket_reload} in the animation file (5 s). */
    public static final int RELOAD_ANIMATION_TICKS = 100;
    /** Length of {@code musket_shove} in the animation file (0.5 s); the server reports SHOVE this long. */
    public static final int SHOVE_TICKS = 10;

    private static final MusketAction[] VALUES = values();

    /**
     * The action for this tick. A shove (it happened within the last {@link #SHOVE_TICKS}) shows over everything, then
     * the reload (it runs on its own timer, whatever the goal decides), then the aim.
     *
     * @param shoving   a shove started less than {@link #SHOVE_TICKS} ago
     * @param reloading the reload timer is running
     * @param aiming    the musketeer goal keeps the musket on a target this tick
     */
    public static MusketAction choose(boolean shoving, boolean reloading, boolean aiming) {
        if (shoving) return SHOVE;
        if (reloading) return RELOAD;
        if (aiming) return AIM;
        return NONE;
    }

    /**
     * What the arm controller under the shove plays: the shove is a one-shot on its own controller layered on top, so
     * during it the arm controller keeps what it played before (a reload interrupted by a shove carries on underneath).
     *
     * @param previous what the arm controller played last ({@link #held} of the previous action), never SHOVE
     */
    public static MusketAction held(MusketAction current, MusketAction previous) {
        return current == SHOVE ? (previous == SHOVE ? NONE : previous) : current;
    }

    /**
     * Speed of {@code musket_reload} so it lasts {@code reloadTicks}: 1 at the default 100 ticks, 2 at 50, 0.5 at 200.
     * A non-positive length plays at normal speed.
     */
    public static double reloadSpeed(int reloadTicks) {
        return reloadTicks <= 0 ? 1.0 : (double) RELOAD_ANIMATION_TICKS / reloadTicks;
    }

    /** Action in bits 0-1, the reload length (ticks, clamped to 0..65535) in bits 8-23. */
    public static int pack(MusketAction action, int reloadTicks) {
        return action.ordinal() | Math.max(0, Math.min(reloadTicks, 0xFFFF)) << 8;
    }

    public static MusketAction unpackAction(int packed) {
        int a = packed & 0x3;
        return a < VALUES.length ? VALUES[a] : NONE;
    }

    public static int unpackReloadTicks(int packed) {
        return packed >>> 8 & 0xFFFF;
    }
}
