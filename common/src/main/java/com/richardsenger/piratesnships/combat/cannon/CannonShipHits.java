package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side notification when a cannonball (cannon or swivel gun) hits a ship that is not the one it was fired
 * from (FL2). Listeners decide what the hit means; the law module records crimes for hitting a ship that struck its
 * colours or flies a neutral flag ({@code law.world.FlagCrimes}). The cannon module never imports the law module.
 */
public final class CannonShipHits {

    /**
     * One ball hitting a ship.
     *
     * @param hitShip    the sub-level id of the ship that was hit
     * @param shooter    the ball's owner: the player who fired, or null for a crew shot
     * @param firingShip the ship the ball was fired from, or null (fired from land, or spawned without a cannon)
     * @param at         world position of the hit
     */
    public record ShipHit(ServerLevel level, UUID hitShip, @Nullable Entity shooter, @Nullable UUID firingShip, Vec3 at) {
    }

    @FunctionalInterface
    public interface Listener {
        void onShipHit(ShipHit hit);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private CannonShipHits() {
    }

    public static void register(Listener listener) {
        LISTENERS.add(listener);
    }

    static void fire(ShipHit hit) {
        for (Listener l : LISTENERS) l.onShipHit(hit);
    }
}
