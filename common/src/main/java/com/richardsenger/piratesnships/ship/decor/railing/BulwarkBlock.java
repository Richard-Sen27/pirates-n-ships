package com.richardsenger.piratesnships.ship.decor.railing;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The bulwark (RAIL4, docs/design.md §4.8 "Railing extras (RAIL3–RAIL5)"): a solid plank wall along one or more edges
 * of its block, the railing's solid variant. It is a {@link RailingBlock} in everything but its looks and shapes: the
 * same {@code north/east/south/west} edges, placement, second-edge use, waterlogging, drops (one per edge), toggle
 * ({@code ship_decor.railings_enabled}) and turning, and the same derived posts, drawn as stanchions; a run continues
 * only into another bulwark or a bulwark gun port ({@link BulwarkRules#continues}).
 *
 * <p><b>Shapes</b> ({@link BulwarkRules#boxes}): outline, each edge's wall 2 px deep to 16 px with a 4 px deep cap rail
 * on top (y 14..16) and each stanchion 4 x 4 px to 18 px; collision, the walls and stanchions raised to
 * {@code ship_decor.railing_collision_blocks} like the railing. Crew do not path through it. The swivel gun mounts on
 * it, ratlines and the grappling hook hold on it ({@code swivel_mounts}, {@code ratlines_anchors},
 * {@code grapple_latches}), and a carriage cannon behind it fires into it ({@code barrel_blocking}): the bulwark is
 * the wall that needs a {@link BulwarkPortBlock} to shoot through. Not a hull block: the hull analysis takes it as air
 * like a railing (no full face, no hull tag).
 */
public class BulwarkBlock extends RailingBlock {

    public BulwarkBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected List<double[]> outlineBoxes(BlockState s) {
        return BulwarkRules.boxes(edges(s), posts(s), BulwarkRules.WALL_TOP, BulwarkRules.STANCHION_TOP, true);
    }

    @Override
    protected List<double[]> collisionBoxes(BlockState s, double height) {
        return BulwarkRules.boxes(edges(s), posts(s), height, height, false);
    }
}
