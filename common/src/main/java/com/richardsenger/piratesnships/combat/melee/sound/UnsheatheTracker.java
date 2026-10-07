package com.richardsenger.piratesnships.combat.melee.sound;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Pure per-player state deciding when drawing a sword sounds: when the main hand changes to a different melee weapon
 * and the last draw sound is at least the cooldown old. The first observation only sets the baseline (a player who
 * logs in or respawns with a sword in hand draws nothing), and scrolling the hotbar across two swords within the
 * cooldown sounds once.
 */
public final class UnsheatheTracker {

    private boolean seen;
    private @Nullable Object held;
    private long lastPlayed = Long.MIN_VALUE / 2;

    /**
     * @param weapon   identity of the melee weapon in the main hand (e.g. its item), or {@code null} for anything else
     * @param tick     current game time
     * @param cooldown minimum ticks between two draw sounds
     * @return true when the draw sound should play now
     */
    public boolean update(@Nullable Object weapon, long tick, int cooldown) {
        boolean first = !seen;
        seen = true;
        Object before = held;
        held = weapon;
        if (first || weapon == null || Objects.equals(before, weapon)) return false;
        if (tick - lastPlayed < cooldown) return false;
        lastPlayed = tick;
        return true;
    }
}
