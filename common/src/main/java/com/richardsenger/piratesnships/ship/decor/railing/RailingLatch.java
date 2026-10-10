package com.richardsenger.piratesnships.ship.decor.railing;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * The edge of a railing or bulwark block that carries an iron latch for a ratline net (RL5, docs/design.md §4.8 "Net
 * sheets and railing latches"), or none. One latch per block; the block state's multipart draws the eye bolt
 * ({@code block/railing_latch}) on the cap of that edge.
 */
public enum RailingLatch implements StringRepresentable {
    NONE(null),
    NORTH(Direction.NORTH),
    EAST(Direction.EAST),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST);

    private final @Nullable Direction edge;

    RailingLatch(@Nullable Direction edge) {
        this.edge = edge;
    }

    /** The edge, or null for {@link #NONE}. */
    public @Nullable Direction edge() {
        return edge;
    }

    /** The latch on {@code edge} (a horizontal direction; anything else is {@link #NONE}). */
    public static RailingLatch of(@Nullable Direction edge) {
        if (edge == null) return NONE;
        return switch (edge) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NONE;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
