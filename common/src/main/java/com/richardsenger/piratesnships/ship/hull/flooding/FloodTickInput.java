package com.richardsenger.piratesnships.ship.hull.flooding;

import com.richardsenger.piratesnships.ship.hull.OutsidePort;
import org.jetbrains.annotations.Nullable;

/**
 * Per-tick inputs of the flooding simulation.
 *
 * @param seaLevel     sea surface height {@code W} along the analysis' up vector, in ship-local coordinates
 *                     ({@code analysis.up() · localPointOnSeaSurface})
 * @param waveHeight   extra temporary height at every outside port (§5.4: rough seas spill water over low rims)
 * @param waves        optional per-port extra height, added to {@code waveHeight} (e.g. only the windward side)
 * @param pumps        active pumps per compartment id ({@code null} = none; shorter arrays are fine)
 */
public record FloodTickInput(double seaLevel, double waveHeight, @Nullable WaveField waves, int @Nullable [] pumps) {

    /** Extra wave height at one port. */
    @FunctionalInterface
    public interface WaveField {
        double extraHeight(OutsidePort port);
    }

    public static FloodTickInput calm(double seaLevel) {
        return new FloodTickInput(seaLevel, 0, null, null);
    }

    public FloodTickInput withPumps(int... pumpsPerCompartment) {
        return new FloodTickInput(seaLevel, waveHeight, waves, pumpsPerCompartment);
    }

    public FloodTickInput withWaves(double height) {
        return new FloodTickInput(seaLevel, height, waves, pumps);
    }

    /** Outside water surface at a port this tick. */
    public double outsideLevel(OutsidePort port) {
        return seaLevel + waveHeight + (waves == null ? 0 : waves.extraHeight(port));
    }

    public int pumpsAt(int compartment) {
        return pumps == null || compartment >= pumps.length ? 0 : pumps[compartment];
    }
}
