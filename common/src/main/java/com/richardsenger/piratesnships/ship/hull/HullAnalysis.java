package com.richardsenger.piratesnships.ship.hull;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable result of {@link HullAnalyzer#analyze}: compartments, their outside ports and the links between them.
 * Does not depend on the open state of openings (the flooding simulation reads that live).
 */
public final class HullAnalysis {

    private final HullGrid grid;
    private final HullVec up;
    private final double[] spill;
    private final int[] compartmentOf;
    private final List<Compartment> compartments;
    private final List<CompartmentLink> links;

    HullAnalysis(HullGrid grid, HullVec up, double[] spill, int[] compartmentOf, List<Compartment> compartments,
                 List<CompartmentLink> links) {
        this.grid = grid;
        this.up = up;
        this.spill = spill;
        this.compartmentOf = compartmentOf;
        this.compartments = List.copyOf(compartments);
        this.links = List.copyOf(links);
    }

    /** The snapshot this analysis was computed from. */
    public HullGrid grid() {
        return grid;
    }

    /** The (normalized) up vector the heights were computed with. Compute sea level along this vector. */
    public HullVec up() {
        return up;
    }

    public List<Compartment> compartments() {
        return compartments;
    }

    public List<CompartmentLink> links() {
        return links;
    }

    /** Compartment id of a grid cell, or {@code -1} for solid, openings and outside air. */
    public int compartmentOf(int index) {
        return compartmentOf[index];
    }

    public int compartmentAt(int x, int y, int z) {
        return grid.inBounds(x, y, z) ? compartmentOf[grid.index(x, y, z)] : -1;
    }

    public @Nullable Compartment compartmentAtOrNull(int x, int y, int z) {
        int c = compartmentAt(x, y, z);
        return c < 0 ? null : compartments.get(c);
    }

    /** Spill height of an air cell ({@code +∞} when no air path to the outside exists; {@code NaN} for non-air). */
    public double spillHeight(int index) {
        return spill[index];
    }

    /** Height of a cell's center along {@link #up()}. */
    public double height(int index) {
        return grid.height(index, up);
    }

    /** Whether the analysis should be redone because the ship's up vector moved more than {@code maxDegrees}. */
    public boolean tiltExceeds(HullVec currentUp, double maxDegrees) {
        return up.angleDegrees(currentUp) > maxDegrees;
    }

    /** Structural equality (same grid, up vector, compartments, ports and links), for tests. */
    public boolean sameStructure(HullAnalysis o) {
        if (!grid.equals(o.grid) || !up.equals(o.up) || compartments.size() != o.compartments.size()
                || !links.equals(o.links) || !java.util.Arrays.equals(compartmentOf, o.compartmentOf)) {
            return false;
        }
        for (int i = 0; i < compartments.size(); i++) {
            Compartment a = compartments.get(i), b = o.compartments.get(i);
            if (!a.cells().equals(b.cells()) || !a.ports().equals(b.ports()) || !a.links().equals(b.links())
                    || !java.util.Arrays.equals(a.cellsByHeight(), b.cellsByHeight())) {
                return false;
            }
        }
        return true;
    }
}
