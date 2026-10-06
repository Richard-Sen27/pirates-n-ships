package com.richardsenger.piratesnships.ship.hull;

/** What one ship-local voxel is, as far as water is concerned (docs/design.md §4.2). */
public enum CellKind {
    /** Watertight: hull blocks, slabs, stairs, glass. */
    SOLID,
    /** Passable for water: air, fluids, replaceables and gappy blocks (fences, bars, ladders, torches). */
    AIR,
    /**
     * A door, trapdoor or fence gate. Separates compartments in the analysis whatever its state, and becomes a link
     * whose open/closed state the flooding simulation reads live. A permanent breach is an opening that stays open.
     */
    OPENING
}
