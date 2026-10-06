package com.richardsenger.piratesnships.ship.hull;

/**
 * A place where a compartment meets outside air, so water can flow in or out.
 *
 * @param compartment the compartment id
 * @param sill        lowest height of the passage along the up vector; water passes only above it
 * @param area        passage area in block faces. Pour points with the same sill are merged into one port
 * @param openingCell grid index of the opening (door, hatch, breach) whose open state gates this port, or {@code -1}
 *                    for a pour point (rim or hole through plain air), which is always open
 */
public record OutsidePort(int compartment, double sill, int area, int openingCell) {

    public boolean isPourPoint() {
        return openingCell < 0;
    }
}
