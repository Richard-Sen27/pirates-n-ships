package com.richardsenger.piratesnships.rpg.reputation;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * The factions a player has a reputation with (docs/design.md §15). Not the flag observers of
 * {@code law.flag.Faction}: merchants, ports and sailors count as villagers here.
 */
public enum Faction implements StringRepresentable {
    NAVY,
    PIRATES,
    VILLAGERS;

    public static final Codec<Faction> CODEC = StringRepresentable.fromEnum(Faction::values);

    /** Config key, command argument and lang suffix: {@code navy}, {@code pirates}, {@code villagers}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return Constants.MOD_ID + ".reputation.faction." + id();
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Optional<Faction> byId(String id) {
        for (Faction f : values()) if (f.id().equals(id)) return Optional.of(f);
        return Optional.empty();
    }
}
