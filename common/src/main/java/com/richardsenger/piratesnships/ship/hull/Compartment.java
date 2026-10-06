package com.richardsenger.piratesnships.ship.hull;

import java.util.BitSet;
import java.util.List;

/**
 * One compartment: a 6-connected component of basin cells (see {@link HullAnalyzer}).
 *
 * @param id             index in {@link HullAnalysis#compartments()}
 * @param cells          member cells as grid indices
 * @param cellsByHeight  member cells sorted by height (ascending), the order {@link #profile()} uses
 * @param minX           inclusive grid-coordinate bounds
 * @param profile        volume ↔ surface height conversion
 * @param ports          links to the outside (pour points, openings and breaches to outside air)
 * @param links          ids into {@link HullAnalysis#links()} of the openings to other compartments
 */
public record Compartment(int id, BitSet cells, int[] cellsByHeight,
                          int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          HeightProfile profile, List<OutsidePort> ports, List<Integer> links) {

    /** Volume in blocks (= cell count). */
    public int volume() {
        return cellsByHeight.length;
    }
}
