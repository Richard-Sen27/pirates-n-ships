package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.lookout.CrowsNestBlock;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The people of a materialised voyage (WS3b, design.md §7, §9): crew members (one pinned at the steering helm, one
 * pinned in the crow's nest as the lookout when the ship has one (TPL2, {@code materialize.man_lookout}), the other
 * deckhands seated by the job board's {@code SailOrder.HOIST}) and the faction's fighters ({@code Sailor},
 * {@code NavyOfficer} + {@code NavySoldier}, {@code Pirate}) standing on {@link DeckSpots}, stationary and persistent.
 * The crew members are stationary too while the ship is a voyage's (WS3c): idle deckhands stand instead of strolling
 * off the deck; {@link #letGo} makes them ordinary crew again.
 * Everyone carries the entity tags {@link #voyageTag} and {@link #CREW_TAG} or {@link #FIGHTER_TAG} (saved with the
 * entity), so a dematerialised ship takes them along and a reload finds them again.
 */
public final class VoyageCrew {

    public static final String CREW_TAG = "pirates_n_ships.voyage_crew";
    public static final String FIGHTER_TAG = "pirates_n_ships.voyage_fighter";
    private static final String VOYAGE_TAG = "pirates_n_ships.voyage.";
    /** How far around the ship's box people still count as aboard (fighters knocked off the deck, crew in the water). */
    static final double REACH = 16;

    public record Manned(int crew, int fighters, @Nullable UUID helmsman, @Nullable UUID lookout) { }

    private VoyageCrew() {
    }

    public static String voyageTag(UUID voyage) {
        return VOYAGE_TAG + voyage;
    }

    /**
     * Spawns {@code crew} crew members and {@code fighters} fighters of {@code faction} on the deck of {@code ship},
     * seats the first crew member at the steering helm, the next one in the ship's crow's nest (TPL2: the lookout, one
     * of the {@code crew}, while {@code man_lookout} is on and the ship has a nest) and posts {@code HOIST} on the job
     * board for the others.
     * {@code waterlinePlotY} is the plot y of the waterline row (decks are searched from there up).
     */
    public static Manned man(ServerLevel level, ShipBody ship, UUID voyage, Faction faction, int crew, int fighters, int waterlinePlotY) {
        List<BlockPos> candidates = DeckSpots.candidates(ship.plotBlocks(),
                p -> level.getBlockState(p).isFaceSturdy(level, p, Direction.UP),
                waterlinePlotY, waterlinePlotY + DeckSpots.MAX_DECK_HEIGHT);
        List<BlockPos> spots = DeckSpots.pick(candidates, crew + fighters);
        if (spots.isEmpty() && crew + fighters > 0) {
            return new Manned(0, 0, null, null);
        }
        int crewMade = 0;
        int fightersMade = 0;
        UUID helmsman = null;
        UUID lookout = null;
        BlockPos helm = ShipHelm.steering(ship);
        BlockPos nest = MaterializeConfig.MAN_LOOKOUT.get() ? nest(level, ship) : null;
        for (int i = 0; i < crew; i++) {
            CrewMember c = StationContent.CREW_MEMBER.get().create(level);
            if (c == null) continue;
            // WS3c: idle deckhands stand where they are like the fighters instead of strolling off the deck
            c.setStationary(true);
            if (!place(level, ship, c, spots.get(i), voyage, CREW_TAG)) continue;
            crewMade++;
            if (helmsman == null && helm != null
                    && CrewStations.assign(level, c, helm, true) == CrewStations.AssignResult.ASSIGNED) {
                helmsman = c.getUUID();
            } else if (lookout == null && nest != null
                    && CrewStations.assign(level, c, nest, true) == CrewStations.AssignResult.ASSIGNED) {
                lookout = c.getUUID(); // pinned: the job board never takes the lookout down to the sails
            }
        }
        if (crewMade > (helmsman == null ? 0 : 1) + (lookout == null ? 0 : 1)) {
            JobBoard.post(level, ship, SailOrder.HOIST);
            JobBoard.pass(level, ship.id());
        }
        for (int i = 0; i < fighters; i++) {
            EntityType<? extends SeafarerMob> type = fighterType(faction, i);
            SeafarerMob m = type.create(level);
            if (m == null) continue;
            m.setStationary(true);
            m.setPersistenceRequired();
            if (place(level, ship, m, spots.get(crew + i), voyage, FIGHTER_TAG)) fightersMade++;
        }
        return new Manned(crewMade, fightersMade, helmsman, lookout);
    }

    /** The plot position of {@code ship}'s crow's nest (the lowest, then the first by position), or null. */
    static @Nullable BlockPos nest(ServerLevel level, ShipBody ship) {
        BlockPos best = null;
        for (BlockPos p : ship.plotBlocks()) {
            if (!(level.getBlockState(p).getBlock() instanceof CrowsNestBlock)) continue;
            if (best == null || p.getY() < best.getY() || p.getY() == best.getY() && p.compareTo(best) < 0) best = p.immutable();
        }
        return best;
    }

    /** The fighter type of {@code faction}; a navy ship's first fighter is its officer. */
    public static EntityType<? extends SeafarerMob> fighterType(Faction faction, int index) {
        return switch (faction) {
            case MERCHANTS -> MobContent.SAILOR.get();
            case NAVY -> index == 0 ? MobContent.NAVY_OFFICER.get() : MobContent.NAVY_SOLDIER.get();
            case PIRATES -> MobContent.PIRATE.get();
        };
    }

    private static boolean place(ServerLevel level, ShipBody ship, Mob mob, BlockPos floor, UUID voyage, String role) {
        Vec3 at = ship.toWorld(Vec3.atBottomCenterOf(floor.above()));
        mob.moveTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        mob.addTag(voyageTag(voyage));
        mob.addTag(role);
        return level.addFreshEntity(mob);
    }

    /** Living people of {@code voyage} with {@code role} around {@code ship}. */
    public static List<LivingEntity> alive(ServerLevel level, ShipBody ship, UUID voyage, String role) {
        String tag = voyageTag(voyage);
        AABB box = CrewStations.worldBox(ship, REACH);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.getTags().contains(tag) && e.getTags().contains(role));
    }

    /** Makes every crew member of {@code voyage} in the level stationary (an adopted ship's crew saved before WS3c). */
    public static void settle(ServerLevel level, UUID voyage) {
        String tag = voyageTag(voyage);
        for (Entity e : level.getAllEntities()) {
            if (e instanceof CrewMember c && c.getTags().contains(tag) && c.getTags().contains(CREW_TAG)) c.setStationary(true);
        }
    }

    /** Removes everyone of {@code voyage} in the level (dematerialisation, an orphaned ship). */
    public static int discard(ServerLevel level, UUID voyage) {
        String tag = voyageTag(voyage);
        List<Entity> found = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e != null && e.getTags().contains(tag)) found.add(e);
        }
        for (Entity e : found) {
            if (e instanceof CrewMember c && c.assignment() != null) CrewStations.release(level, c);
            e.discard();
        }
        return found.size();
    }

    /**
     * Lets everyone of {@code voyage} go (a capture or a sinking): crew released from their stations, every voyage tag
     * removed, so they are ordinary crew and mobs from now on.
     */
    public static void letGo(ServerLevel level, UUID ship, UUID voyage) {
        CrewStations.releaseShip(level, ship);
        String tag = voyageTag(voyage);
        for (Entity e : level.getAllEntities()) {
            if (e == null || !e.getTags().contains(tag)) continue;
            e.removeTag(tag);
            if (e instanceof CrewMember c && e.getTags().contains(CREW_TAG)) c.setStationary(false); // ordinary crew now
            e.removeTag(CREW_TAG);
            e.removeTag(FIGHTER_TAG);
        }
    }
}
