package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * The letter of marque (docs/design.md §15, CAR1): never held, held ({@link #ACTIVE}: pirate kills earn prize money),
 * or voided by a desertion deed ({@link #VOIDED}: a new one only after {@code careers.letter.void_days}).
 */
public enum LetterState implements StringRepresentable {
    NONE,
    ACTIVE,
    VOIDED;

    public static final Codec<LetterState> CODEC = StringRepresentable.fromEnum(LetterState::values);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return Constants.MOD_ID + ".career.letter." + id();
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Optional<LetterState> byId(String id) {
        for (LetterState s : values()) if (s.id().equals(id)) return Optional.of(s);
        return Optional.empty();
    }
}
