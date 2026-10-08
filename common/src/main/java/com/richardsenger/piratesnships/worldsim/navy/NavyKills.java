package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * A patrol's fighters killing a pirate report the world event {@link FactionEvent#PIRATE_KILLED_BY_NAVY} (WS4b, plan:
 * "each patrol kill of a pirate mob"). A player's own kills go through their deeds ({@code FactionDeeds}); a lost
 * patrol ({@code PATROL_LOST}) is reported by the materialiser's endings (WS3b).
 */
public final class NavyKills {

    /** The prefix of {@link VoyageCrew#voyageTag}, before the voyage id. */
    private static final String VOYAGE_PREFIX;

    static {
        String sample = VoyageCrew.voyageTag(new UUID(0, 0));
        VOYAGE_PREFIX = sample.substring(0, sample.length() - new UUID(0, 0).toString().length());
    }

    private NavyKills() {
    }

    /** {@code LIVING_DEATH}: never cancels. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide() || !(victim instanceof Pirate)) return false;
        Entity killer = source.getEntity();
        if (killer == null || victim.getServer() == null) return false;
        MinecraftServer server = victim.getServer();
        Optional<Voyage> patrol = voyageOf(server, killer).filter(v -> v.kind() == VoyageKind.PATROL);
        if (patrol.isPresent()) Factions.report(server, FactionEvent.PIRATE_KILLED_BY_NAVY);
        return false;
    }

    /** The active voyage whose people {@code e} belongs to (by its voyage tag), if any. */
    static Optional<Voyage> voyageOf(MinecraftServer server, Entity e) {
        for (String tag : e.getTags()) {
            if (!tag.startsWith(VOYAGE_PREFIX)) continue;
            try {
                return Voyages.get(server, UUID.fromString(tag.substring(VOYAGE_PREFIX.length())));
            } catch (IllegalArgumentException ignored) {
                // not a voyage id
            }
        }
        return Optional.empty();
    }
}
