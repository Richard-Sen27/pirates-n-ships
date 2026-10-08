package com.richardsenger.piratesnships.rpg.career;

import java.util.Optional;

/**
 * Client copy of the local player's career ({@link CareerSyncPayload}) and of the officer's career screen
 * ({@link CareerPayloads.View}). Plain Java without client classes (the {@code ClientReputation} pattern), so the
 * payload handlers that write it can be registered on both sides. {@link #version()} grows with every screen update.
 */
public final class ClientCareer {

    private static volatile CareerSyncPayload current = CareerSyncPayload.NONE;
    private static volatile boolean known;
    private static volatile Optional<CareerPayloads.View> view = Optional.empty();
    private static volatile long version;
    private static volatile Runnable opener = () -> { };

    private ClientCareer() {
    }

    public static CareerSyncPayload get() {
        return current;
    }

    public static boolean known() {
        return known;
    }

    public static void accept(CareerSyncPayload payload) {
        current = payload;
        known = true;
    }

    /** Client init only: what opens the career screen. */
    public static void setOpener(Runnable r) {
        opener = r;
    }

    /** A new screen state; {@code open} states open the screen. */
    public static void accept(CareerPayloads.View v) {
        view = Optional.of(v);
        version++;
        if (v.open()) opener.run();
    }

    public static Optional<CareerPayloads.View> view() {
        return view;
    }

    public static long version() {
        return version;
    }

    public static void reset() {
        current = CareerSyncPayload.NONE;
        known = false;
        view = Optional.empty();
        version++;
    }
}
