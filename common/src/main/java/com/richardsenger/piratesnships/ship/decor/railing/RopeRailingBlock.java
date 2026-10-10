package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;

/**
 * The rope railing (RAIL6, docs/design.md §4.8 "Railing rework (RAIL6)"): a light railing for working decks, a 2 px
 * stanchion per block with a top and a middle rope line along the edge, turned posts where a run ends or turns. It is a
 * {@link RailingBlock} in everything but its looks: the same edges, placement, second-edge use, waterlogging, drops (one
 * per edge), toggle ({@code ship_decor.railings_enabled}), turning, collision and outline, the grappling hook's hold
 * ({@code grapple_latches}), RAIL5's leaners and boarding nets, and RL5's latch on its top line (whose top is the cap
 * rail's y 16). A run continues only into another rope railing ({@link BulwarkRules.Kind#ROPE}), so against a railing,
 * a port, a gate or a bulwark it ends in a post; it has no ports or gates of its own, and no swivel gun mounts on it.
 */
public class RopeRailingBlock extends RailingBlock {

    public RopeRailingBlock(ShipWood wood, Properties properties) {
        super(wood, properties);
    }
}
