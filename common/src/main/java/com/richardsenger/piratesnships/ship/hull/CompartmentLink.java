package com.richardsenger.piratesnships.ship.hull;

/**
 * An opening cell (door, hatch, trapdoor) between two compartments. Its open state is not part of the analysis: the
 * flooding simulation reads it live, which is why toggling it never needs a re-analysis.
 *
 * @param a           lower compartment id
 * @param b           higher compartment id
 * @param sill        lowest height of the opening cell along the up vector
 * @param openingCell grid index of the opening
 */
public record CompartmentLink(int a, int b, double sill, int openingCell) {
}
