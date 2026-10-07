package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * The start piece's sea-edge anchor (WG2, generalised from WG1's dock head): local ({@code x}, {@code z}) of the
 * column on the start piece's sea edge that goes onto the last land column of the shore. Start pieces face −z
 * (art/README.md "Structures"), so {@code z} is 0 for the pieces we ship: the dock head's pier connector (5, 0), the
 * camp's jetty connector in the middle of its north beach edge (6, 0). Pure arithmetic, shared by world generation
 * and the tests.
 */
public record ShoreAnchor(int x, int z) {

    public static final Codec<ShoreAnchor> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 47).fieldOf("x").forGetter(ShoreAnchor::x),
            Codec.intRange(0, 47).fieldOf("z").forGetter(ShoreAnchor::z)
    ).apply(i, ShoreAnchor::new));

    /** The seafarer village's dock head (11×8×11): its pier connector column on the sea edge row. */
    public static final ShoreAnchor VILLAGE = new ShoreAnchor(5, 0);
    /** The pirate camp (13×8×13): the centre of its north beach edge, where the jetty connector sits. */
    public static final ShoreAnchor PIRATE_CAMP = new ShoreAnchor(6, 0);

    /**
     * The template origin (y unchanged) for the start piece rotated by {@code rotation} so that its local
     * ({@code x}, y, {@code z}) lands on {@code seaEdge}. Templates rotate about their origin (pivot 0).
     */
    public BlockPos startOrigin(BlockPos seaEdge, Rotation rotation) {
        return seaEdge.subtract(new BlockPos(x, 0, z).rotate(rotation));
    }

    /**
     * The column whose ground decides the height check: {@code x} blocks inland from the sea edge, the start piece's
     * centre for the square start pieces whose anchor is the centre of their sea edge (both of ours).
     */
    public BlockPos groundProbe(BlockPos seaEdge, Direction sea) {
        return seaEdge.relative(sea.getOpposite(), x);
    }
}
