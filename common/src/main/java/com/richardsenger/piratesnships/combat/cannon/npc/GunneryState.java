package com.richardsenger.piratesnships.combat.cannon.npc;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * What a ship's gun crews do on their own (WS4a): nothing ({@link #OFF}), fire at any hostile ship in reach
 * ({@link #AT_WILL}, the whistle's "Fire at will"), or fire at one chosen ship ({@link #target(UUID)}, set by AI such as
 * a navy patrol, WS4b). A chosen target is shot at whatever it flies, but never once it has struck its colours.
 *
 * @param mode   off, at will, or one target
 * @param target the chosen ship's id for {@link Mode#TARGET}, else null
 */
public record GunneryState(Mode mode, @Nullable UUID target) {

    public enum Mode { OFF, AT_WILL, TARGET }

    public static final GunneryState OFF = new GunneryState(Mode.OFF, null);
    public static final GunneryState AT_WILL = new GunneryState(Mode.AT_WILL, null);

    public GunneryState {
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.TARGET) Objects.requireNonNull(target, "a TARGET state needs a target");
        else target = null;
    }

    public static GunneryState target(UUID ship) {
        return new GunneryState(Mode.TARGET, ship);
    }

    public boolean isOff() {
        return mode == Mode.OFF;
    }
}
