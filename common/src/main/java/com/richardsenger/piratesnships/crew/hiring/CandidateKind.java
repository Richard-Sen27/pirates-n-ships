package com.richardsenger.piratesnships.crew.hiring;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * What a candidate at a harbor desk is (CRW1, docs/design.md §7.1, §15): a sailor at a seafarer village, a pirate at a
 * pirate island, a navy rating at a navy outpost. Kinds only; role skills stay open (§7.1). Hired, every kind becomes
 * the same crew member.
 */
public enum CandidateKind implements StringRepresentable {
    SAILOR,
    PIRATE,
    NAVY;

    public static final Codec<CandidateKind> CODEC = StringRepresentable.fromEnum(CandidateKind::values);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    /** The kind a port of {@code kind} offers. */
    public static CandidateKind at(PortKind kind) {
        return switch (kind) {
            case SEAFARER_VILLAGE -> SAILOR;
            case PIRATE_ISLAND -> PIRATE;
            case NAVY_OUTPOST -> NAVY;
        };
    }

    public static Optional<CandidateKind> byId(String id) {
        for (CandidateKind k : values()) if (k.id().equals(id)) return Optional.of(k);
        return Optional.empty();
    }
}
