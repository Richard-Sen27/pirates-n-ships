package com.richardsenger.piratesnships.mob.captain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Which pieces of his clothing a slain pirate captain drops (ART9), without world access: each piece on its own roll
 * with {@code mobs.captain.clothing_drop_chance} (0 = never, 1 = always), beside his hat, which always drops.
 */
public final class ClothingDrops {

    private ClothingDrops() {
    }

    /**
     * The pieces that drop, in their order: one roll of {@code random} (uniform in [0, 1)) per piece, a piece drops when
     * the roll is below {@code chance}. A chance of 0 or less drops nothing and 1 or more drops everything, without
     * rolling.
     */
    public static <T> List<T> roll(List<T> pieces, double chance, DoubleSupplier random) {
        if (chance <= 0) return List.of();
        if (chance >= 1) return List.copyOf(pieces);
        List<T> out = new ArrayList<>();
        for (T piece : pieces) {
            if (random.getAsDouble() < chance) out.add(piece);
        }
        return out;
    }
}
