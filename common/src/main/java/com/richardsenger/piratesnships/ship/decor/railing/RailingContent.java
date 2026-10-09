package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registry entries of the railing family (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)"): {@code <wood>_railing}
 * and {@code <wood>_railing_port} for every shipped wood ({@code ShipWood.SHIPPED}: oak in RAIL1).
 */
public final class RailingContent {

    public static final WoodFamily<RailingBlock> RAILINGS = WoodFamily.register("railing",
            wood -> new RailingBlock(WoodFamily.properties(wood).noOcclusion().pushReaction(PushReaction.NORMAL)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    public static final WoodFamily<RailingPortBlock> PORTS = WoodFamily.register("railing_port",
            wood -> new RailingPortBlock(WoodFamily.properties(wood).noOcclusion()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    private RailingContent() {
    }

    public static void init() {
    }
}
