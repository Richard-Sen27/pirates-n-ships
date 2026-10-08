package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a crew member back into an ordinary seafarer mob (docs/design.md §7.3): desertion and dismissal give a neutral
 * sailor, mutiny a hostile pirate. Shared by the dawn upkeep ({@link ShipDayTick}) and the crew packages that let crew
 * go (hiring, desertion at a port).
 */
public final class CrewReplacement {

    private CrewReplacement() {
    }

    /**
     * Replaces {@code crew} by a {@code type} mob at its place: released from its station and hammock, dismounted,
     * position, rotation, custom name and AI flag carried over, the mob made persistent, then the crew member removed.
     * The mob's own look (GL1's seafarer rig) replaces the crew look.
     *
     * @return the new mob, or null when {@code type} could not be created (the crew member is removed anyway)
     */
    public static @Nullable SeafarerMob replace(ServerLevel level, CrewMember crew, EntityType<? extends SeafarerMob> type) {
        if (crew.assignment() != null) CrewStations.release(level, crew);
        CrewRest.getUp(crew, false);
        crew.stopRiding();
        SeafarerMob mob = type.create(level);
        if (mob != null) {
            mob.moveTo(crew.getX(), crew.getY(), crew.getZ(), crew.getYRot(), crew.getXRot());
            mob.setYHeadRot(crew.getYHeadRot());
            if (crew.hasCustomName()) mob.setCustomName(crew.getCustomName());
            mob.setNoAi(crew.isNoAi());
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
        crew.discard();
        return mob;
    }
}
