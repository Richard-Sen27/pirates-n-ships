package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.function.Supplier;

/**
 * Longer walks for the squad's path searches (ST3b). Vanilla's path finder stops expanding a path once its walked
 * length reaches the mob's follow range ({@code PathFinder.findPath}: {@code walkedDistance < maxRange}), 32 blocks for
 * our mobs. The outpost's walkway is reached only by the court's stairs, so a wall guard on the west run walks some 35
 * blocks to an officer in the court, east along the walls, down the stairs and back west: the search gave up, he was
 * never drafted, and a partial path led him onto the office roof (the nearest point in a straight line). Squad
 * navigation therefore plans with the follow range raised by {@link #EXTRA_RANGE} for that one call only, so targeting
 * (which also reads the follow range) is unchanged.
 */
final class SquadReach {

    /** Blocks added to the follow range while a squad path is planned. */
    static final double EXTRA_RANGE = 32.0;
    private static final ResourceLocation ID = Constants.id("squad_reach");

    private SquadReach() {
    }

    /** Runs {@code planning} (a {@code createPath} or {@code moveTo} of {@code mob}'s navigation) with the wider range. */
    static <T> T wide(Mob mob, Supplier<T> planning) {
        AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (range == null || range.hasModifier(ID)) return planning.get();
        range.addTransientModifier(new AttributeModifier(ID, EXTRA_RANGE, AttributeModifier.Operation.ADD_VALUE));
        try {
            return planning.get();
        } finally {
            range.removeModifier(ID);
        }
    }
}
