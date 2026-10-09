package com.richardsenger.piratesnships.ship.hull;

import java.util.List;

/**
 * The tilt-independent part of a {@link HullAnalysis} (CMP1): the spill heights, the basin cells and the compartments,
 * computed once per grid in the plot frame (up = +Y) by {@link HullAnalyzer#partition}. Shared by every analysis of
 * the same grid at any tilt ({@link HullAnalyzer#retilt}). Immutable by convention: nothing writes the arrays.
 *
 * @param grid          the snapshot
 * @param spill         spill height of each air cell in the plot frame ({@code +∞} enclosed, {@code NaN} non-air)
 * @param air           whether a cell is air
 * @param basin         whether a cell is a basin cell (in a compartment)
 * @param compartmentOf compartment id per cell, {@code -1} outside any
 * @param members       cells of each compartment, in breadth-first order from its lowest-index cell
 */
record HullPartition(HullGrid grid, double[] spill, boolean[] air, boolean[] basin, int[] compartmentOf,
                     List<int[]> members) {
}
