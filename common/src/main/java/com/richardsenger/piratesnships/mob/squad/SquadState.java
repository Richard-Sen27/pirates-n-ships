package com.richardsenger.piratesnships.mob.squad;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a squad does (MOB2). */
public enum SquadState implements StringRepresentable {
    /** Walking the patrol route in file behind the officer. */
    PATROLLING,
    /** Someone of the squad has a target; the squad fights as one, then re-forms. */
    FIGHTING,
    /** Walking back to the garrison posts. */
    RETURNING,
    /** Everyone at his garrison post, stationary; the next patrol is due later. */
    AT_POST;

    public static final Codec<SquadState> CODEC = StringRepresentable.fromEnum(SquadState::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
