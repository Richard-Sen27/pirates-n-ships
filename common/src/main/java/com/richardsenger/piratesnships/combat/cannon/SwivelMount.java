package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Where a swivel gun sits on its block (RAIL1, docs/design.md §8.3): {@link #NONE} in the middle (on a fence, a wall,
 * bars or a full block, as before), or on the railed edge of a railing below it ({@link #NORTH}..{@link #WEST}): the
 * gun, its shape and its barrel's pivot shift {@link SwivelRules#MOUNT_SHIFT} toward that edge.
 */
public enum SwivelMount implements StringRepresentable {
    NONE("none", null),
    NORTH("north", Direction.NORTH),
    EAST("east", Direction.EAST),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST);

    private final String name;
    private final @Nullable Direction edge;

    SwivelMount(String name, @Nullable Direction edge) {
        this.name = name;
        this.edge = edge;
    }

    /** The edge the gun sits on, or null in the middle. */
    public @Nullable Direction edge() {
        return edge;
    }

    /** The mount on {@code edge} (a horizontal direction), {@link #NONE} for null. */
    public static SwivelMount of(@Nullable Direction edge) {
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
        return name;
    }
}
