package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistSkill;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * The humanoid mobs of docs/design.md §9, with their faction and default duelist skill. Pure: the entity types
 * live in {@code MobContent}, the per-type config in {@code MobConfig}. The pirate captain (BOS1, §15) is a named pirate
 * of the island's captain's hut ({@code mob.captain}); it wears the pirate's model and texture ({@link #artId()}).
 */
public enum MobKind {
    PIRATE(MobFaction.PIRATE, DuelistSkill.PIRATE),
    SAILOR(MobFaction.CIVILIAN, null),
    NAVY_SOLDIER(MobFaction.NAVY, null),
    NAVY_OFFICER(MobFaction.NAVY, DuelistSkill.NAVY_OFFICER),
    PIRATE_CAPTAIN(MobFaction.PIRATE, DuelistSkill.PIRATE_CAPTAIN);

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
     * {@code textures/entity/<artId>.png}): its own id, except the pirate captain, who wears the pirate's.
     */
    public String artId() {
        return this == PIRATE_CAPTAIN ? PIRATE.id() : id();
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
