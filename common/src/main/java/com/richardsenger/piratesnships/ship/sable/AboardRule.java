package com.richardsenger.piratesnships.ship.sable;

import org.jetbrains.annotations.Nullable;

/**
 * Which ship the local player is on, as the client sees it (HUD2, CAM1). Pure; one instance lives in
 * {@link ClientAboard}, counted in client ticks.
 *
 * <p>Sable's client tracks the player on a ship only on the ticks they touch it from above
 * ({@code mixin/entity/entity_sublevel_collision/EntityMixin}): mid-jump, on a ladder or falling it reports nothing.
 * So the rule keeps the last tracked ship:
 * <ul>
 *   <li><b>Tracked:</b> Sable reports a ship (standing on it or riding a seat on it): that ship, at once.</li>
 *   <li><b>Holding:</b> climbing, or in the air (not in water, not flying or gliding, not riding something else):
 *   the aboard time is carried on, up to {@link #MAX_HOLD_TICKS} after the ship was last touched.</li>
 *   <li><b>Grace:</b> after the aboard time stops being carried on, the player still counts as aboard for
 *   {@link #ABOARD_GRACE_TICKS}.</li>
 *   <li><b>Left:</b> landing on something that is not the ship, entering water, riding something else or changing
 *   dimension. The ship ({@link #ship()}, the camera) is dropped at once; the HUD's {@link #aboard()} keeps its grace
 *   (HUD2's behaviour: the panel lingers 1.5 s after swimming away).</li>
 * </ul>
 *
 * @param <S> the ship handle (the client sub-level; anything in tests)
 */
public final class AboardRule<S> {

    /** After the player was last counted aboard (1.5 s): the HUD stays this long, then hides (HUD2). */
    public static final int ABOARD_GRACE_TICKS = 30;
    /** Longest climb or flight (20 s) that carries the aboard time on without touching the ship (HUD2). */
    public static final int MAX_HOLD_TICKS = 400;

    private static final long NEVER = Long.MIN_VALUE / 2;

    private long ticks;
    private long aboardAt = NEVER;
    private long touchedAt = NEVER;
    private @Nullable S ship;
    private boolean left;

    /**
     * One client tick.
     *
     * @param tracked the ship Sable tracks the player on this tick, or null
     * @param holding climbing, or in the air and not in water, not flying or gliding, not riding something else
     * @param leaving on ground that is not a ship, in water, riding something else, or in another dimension
     */
    public void tick(@Nullable S tracked, boolean holding, boolean leaving) {
        ticks++;
        if (tracked != null) {
            aboardAt = touchedAt = ticks;
            ship = tracked;
            left = false;
            return;
        }
        if (leaving) {
            left = true;
        }
        if (holding && ticks - aboardAt <= ABOARD_GRACE_TICKS && ticks - touchedAt <= MAX_HOLD_TICKS) {
            aboardAt = ticks;
        }
        if (!aboard()) {
            ship = null;
        }
    }

    /** True while the player counts as aboard for the HUD: tracked, held, or within the grace after either. */
    public boolean aboard() {
        return ticks - aboardAt <= ABOARD_GRACE_TICKS;
    }

    /** The ship the player is on for the camera: the kept ship while aboard and not left since it was last tracked. */
    public @Nullable S ship() {
        return left || !aboard() ? null : ship;
    }

    public void reset() {
        ticks = 0;
        aboardAt = touchedAt = NEVER;
        ship = null;
        left = false;
    }
}
