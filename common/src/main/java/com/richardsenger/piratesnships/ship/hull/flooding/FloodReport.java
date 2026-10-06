package com.richardsenger.piratesnships.ship.hull.flooding;

import com.richardsenger.piratesnships.ship.hull.HullVec;
import org.jetbrains.annotations.Nullable;

/**
 * Outputs of one flooding tick: the buoyancy inputs of docs/design.md §4.5 (flood water pulls down at its centroid,
 * submerged dry volume pushes up at its centroid) and per-compartment state. Flooded and dry cell sets are queried on
 * {@link FloodSimulation} (lazily, they are only needed when the client sync or the occlusion regions change).
 *
 * @param volumes            water volume per compartment (blocks)
 * @param levels             water surface height per compartment (along the up vector)
 * @param floodVolume        total water inside the ship
 * @param floodCentroid      ship-local centroid of that water, {@code null} when there is none
 * @param submergedDryVolume dry compartment volume below the sea level
 * @param dryCentroid        ship-local centroid of that volume, {@code null} when there is none
 * @param inflow             water that entered from outside this tick
 * @param outflow            water that drained to outside this tick
 * @param pumped             water removed by pumps this tick
 */
public record FloodReport(double[] volumes, double[] levels, double floodVolume, @Nullable HullVec floodCentroid,
                          double submergedDryVolume, @Nullable HullVec dryCentroid,
                          double inflow, double outflow, double pumped) {
}
