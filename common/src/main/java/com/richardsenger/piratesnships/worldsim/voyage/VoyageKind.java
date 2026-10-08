package com.richardsenger.piratesnships.worldsim.voyage;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.law.flag.Faction;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a voyage is for (WS2): a merchant convoy, a navy patrol (WS4b) or a pirate raid (WS5). */
public enum VoyageKind implements StringRepresentable {
    CONVOY(Faction.MERCHANTS),
    PATROL(Faction.NAVY),
    RAID(Faction.PIRATES);

    public static final Codec<VoyageKind> CODEC = StringRepresentable.fromEnum(VoyageKind::values);

    private final Faction defaultFaction;

    VoyageKind(Faction defaultFaction) {
        this.defaultFaction = defaultFaction;
    }

    /** The faction that usually sails this kind. */
    public Faction defaultFaction() {
        return defaultFaction;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
