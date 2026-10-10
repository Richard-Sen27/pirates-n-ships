package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registry entries of the railing family (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)"): {@code <wood>_railing}
 * and {@code <wood>_railing_port} for every shipped wood ({@code ShipWood.shipped()}: the eleven vanilla woods and the loaded mods' woods, WOOD1), and (RAIL3)
 * {@code <wood>_railing_gate}, and (RAIL4) {@code <wood>_bulwark} and {@code <wood>_bulwark_port}, and (RAIL6)
 * {@code <wood>_rope_railing}.
 */
public final class RailingContent {

    public static final WoodFamily<RailingBlock> RAILINGS = WoodFamily.register("railing",
            wood -> new RailingBlock(wood, WoodFamily.properties(wood).noOcclusion().pushReaction(PushReaction.NORMAL)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    public static final WoodFamily<RailingPortBlock> PORTS = WoodFamily.register("railing_port",
            wood -> new RailingPortBlock(wood, WoodFamily.properties(wood).noOcclusion()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** RAIL3: the railing gate. */
    public static final WoodFamily<RailingGateBlock> GATES = WoodFamily.register("railing_gate",
            wood -> new RailingGateBlock(wood, WoodFamily.properties(wood).noOcclusion()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** RAIL4: the bulwark, the railing's solid variant. */
    public static final WoodFamily<BulwarkBlock> BULWARKS = WoodFamily.register("bulwark",
            wood -> new BulwarkBlock(wood, WoodFamily.properties(wood).noOcclusion().pushReaction(PushReaction.NORMAL)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** RAIL4: the bulwark gun port. */
    public static final WoodFamily<BulwarkPortBlock> BULWARK_PORTS = WoodFamily.register("bulwark_port",
            wood -> new BulwarkPortBlock(wood, WoodFamily.properties(wood).noOcclusion()
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** RAIL6: the rope railing, stanchions with two rope lines. */
    public static final WoodFamily<RopeRailingBlock> ROPE_RAILINGS = WoodFamily.register("rope_railing",
            wood -> new RopeRailingBlock(wood, WoodFamily.properties(wood).noOcclusion().pushReaction(PushReaction.NORMAL)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    private RailingContent() {
    }

    public static void init() {
    }
}
