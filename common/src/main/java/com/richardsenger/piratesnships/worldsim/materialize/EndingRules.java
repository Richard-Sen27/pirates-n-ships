package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The pure rules of how a materialised voyage ends or changes (WS3b, design.md §10.4); {@link VoyageEndings} feeds them
 * from the world. No world access.
 *
 * <ul>
 *   <li><b>Sunk:</b> the flooded fraction of the hull reaches {@code sunk_flood_fraction}, or the ship became a wreck
 *       (a piece that is not the ship any more), or its whole bounding box stayed under the sea surface for
 *       {@code submerged_ticks}.</li>
 *   <li><b>Captured:</b> no fighter of the voyage is alive and a player stands aboard, for {@code capture_hold_ticks}
 *       without a break.</li>
 *   <li><b>Plundered:</b> the cargo aboard went down while a player stood aboard; the deed counts once per voyage.</li>
 *   <li><b>Blame:</b> the last player whose cannonball hit the ship within {@code shooter_memory_ticks}.</li>
 * </ul>
 */
public final class EndingRules {

    public record Params(double sunkFloodFraction, int submergedTicks, int captureHoldTicks, int shooterMemoryTicks) {
        public static final Params DEFAULTS = new Params(0.8, 100, 100, 1200);
    }

    /** The last player hit on a ship: who and when (game time). */
    public record Hit(UUID player, long tick) {
    }

    /** What a change of the cargo aboard means. */
    public enum CargoChange {
        /** Nothing changed. */
        NONE,
        /** The count changed (goods added, a container broken, goods taken without a player aboard, or a second plunder). */
        CHANGED,
        /** Goods were taken while a player was aboard, the first time on this voyage: the plunder deed. */
        PLUNDERED
    }

    private EndingRules() {
    }

    /** Water inside / inside volume, in [0, 1]; 0 without a hull volume. */
    public static double floodFraction(double floodVolume, double insideVolume) {
        if (insideVolume <= 0 || floodVolume <= 0) return 0.0;
        return Math.min(1.0, floodVolume / insideVolume);
    }

    /** Ticks the ship has been fully under the sea surface after {@code dt} more ticks. */
    public static int submergedTicks(int previous, boolean topUnderSea, int dt) {
        return topUnderSea ? previous + Math.max(0, dt) : 0;
    }

    public static boolean sunk(double floodFraction, boolean wreck, int submergedTicks, Params p) {
        return wreck || floodFraction >= p.sunkFloodFraction() || submergedTicks >= p.submergedTicks();
    }

    /** Ticks of an uninterrupted capture hold after {@code dt} more ticks. */
    public static int captureTicks(int previous, int fightersAlive, boolean playerAboard, int dt) {
        return fightersAlive <= 0 && playerAboard ? previous + Math.max(0, dt) : 0;
    }

    public static boolean captured(int captureTicks, Params p) {
        return captureTicks >= p.captureHoldTicks();
    }

    /** How a change of the cargo count from {@code before} to {@code after} counts. */
    public static CargoChange cargoChange(int before, int after, boolean playerAboard, boolean alreadyPlundered) {
        if (after == before) return CargoChange.NONE;
        if (after < before && playerAboard && !alreadyPlundered) return CargoChange.PLUNDERED;
        return CargoChange.CHANGED;
    }

    /** The player to blame for a sinking at {@code now}: the last hit, if it is recent enough. */
    public static Optional<UUID> blame(@Nullable Hit last, long now, Params p) {
        if (last == null || now - last.tick() > p.shooterMemoryTicks() || now < last.tick()) return Optional.empty();
        return Optional.of(last.player());
    }

    /** The world event of a ship of {@code faction} lost (sunk or captured). */
    public static FactionEvent lostEvent(Faction faction) {
        return switch (faction) {
            case MERCHANTS -> FactionEvent.CONVOY_SUNK;
            case NAVY -> FactionEvent.PATROL_LOST;
            case PIRATES -> FactionEvent.PIRATE_SHIP_LOST;
        };
    }
}
