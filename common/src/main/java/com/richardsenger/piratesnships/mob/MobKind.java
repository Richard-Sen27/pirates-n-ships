package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistSkill;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * The humanoid mobs of docs/design.md §9, with their faction and default duelist skill. Pure: the entity types
 * live in {@code MobContent}, the per-type config in {@code MobConfig}. The pirate captain (BOS1, §15) is a named pirate
 * of the island's captain's hut ({@code mob.captain}); since ART6 he has his own look on the crew rig ({@link #artId()}).
 * The harbor master (PRT1a, §10.3) stands behind the harbor desks ({@code mob.harbor}): the sailor's geometry with his
 * own coat-and-hat texture ({@link #geoId()}).
 */
public enum MobKind {
    PIRATE(MobFaction.PIRATE, DuelistSkill.PIRATE),
    SAILOR(MobFaction.CIVILIAN, null),
    NAVY_SOLDIER(MobFaction.NAVY, null),
    NAVY_OFFICER(MobFaction.NAVY, DuelistSkill.NAVY_OFFICER),
    PIRATE_CAPTAIN(MobFaction.PIRATE, DuelistSkill.PIRATE_CAPTAIN),
    /** PRT1a: the harbor master behind every port's desk ({@code mob.harbor}); a sailor in a coat and hat. */
    HARBOR_MASTER(MobFaction.CIVILIAN, null);

    private final MobFaction faction;
    private final @Nullable DuelistSkill defaultSkill;

    MobKind(MobFaction faction, @Nullable DuelistSkill defaultSkill) {
        this.faction = faction;
        this.defaultSkill = defaultSkill;
    }

    /** Entity id path, texture name and command argument: {@code pirate}, {@code navy_soldier}, ... */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The name of the geometry and texture the kind is drawn with ({@code geo/<artId>.geo.json},
     * {@code textures/entity/<artId>.png}): its own id for every kind. BOS1 drew the pirate captain with the pirate's
     * art; ART6 gave him his own ({@code art/models/entity/pirate_captain.bbmodel}).
     */
    public String artId() {
        return id();
    }

    /**
     * The name of the geometry the kind is drawn with ({@code geo/<geoId>.geo.json}): its {@link #artId()}, except the
     * harbor master, who is a texture variant on the sailor's geometry.
     */
    public String geoId() {
        return this == HARBOR_MASTER ? SAILOR.id() : artId();
    }

    public MobFaction faction() {
        return faction;
    }

    /** The skill preset of a sword duelist ({@code null}: not a duelist, e.g. the musket-armed soldier). */
    public @Nullable DuelistSkill defaultSkill() {
        return defaultSkill;
    }

    public boolean duelist() {
        return defaultSkill != null;
    }

    public static Optional<MobKind> byId(String id) {
        for (MobKind k : values()) if (k.id().equals(id)) return Optional.of(k);
        return Optional.empty();
    }
}
