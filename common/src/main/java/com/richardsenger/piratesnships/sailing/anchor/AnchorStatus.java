package com.richardsenger.piratesnships.sailing.anchor;

import net.minecraft.world.phys.Vec3;

/**
 * What the anchor of one ship does this game tick (docs/design.md §5.2, AN2a), derived by {@link AnchorPhysics} and
 * not saved. Synced to clients on the anchor entity for the chain's look (AN2b).
 *
 * @param hawse    the hawse, world [blocks]
 * @param ring     the anchor's ring, world [blocks]
 * @param paidOut  chain paid out [blocks]
 * @param distance distance from the hawse to the ring [blocks]
 * @param resting  the anchor lies on the ground
 * @param taut     the chain is taut ({@link AnchorChain#taut})
 * @param dragging the anchor scraped over the seabed within the last {@link AnchorPhysics#DRAG_MEMORY_TICKS} ticks
 * @param holding  the anchor rests, holds (holding power above 0) and does not drag
 * @param anchored holding, and the ship slower than {@code anchor_chain.at_rest_speed}: the ship counts as anchored
 */
public record AnchorStatus(Vec3 hawse, Vec3 ring, double paidOut, double distance, boolean resting, boolean taut,
                           boolean dragging, boolean holding, boolean anchored) {

    /** Flag bits of {@link #flags()}, as synced on the anchor entity. */
    public static final int RESTING = 1, TAUT = 2, DRAGGING = 4, HOLDING = 8, ANCHORED = 16;

    public int flags() {
        return (resting ? RESTING : 0) | (taut ? TAUT : 0) | (dragging ? DRAGGING : 0) | (holding ? HOLDING : 0)
                | (anchored ? ANCHORED : 0);
    }
}
