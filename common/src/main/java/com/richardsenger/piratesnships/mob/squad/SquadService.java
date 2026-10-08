package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.world.outpost.Garrison;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The squads of the loaded officers (MOB2) and the hooks the rest of the mod calls: {@link #onGarrisonPlaced} from
 * {@code Garrison.place}, {@link #tickLeader} from the officer's server tick. Squads live in memory while their officer
 * is loaded and are saved on him ({@link SquadAttachments#SQUAD}); a member finds his squad through the index here.
 *
 * <p>Officers of outposts generated before MOB2 have no squad saved: the first time such an officer ticks, his squad is
 * built from the outpost structure he stands in (its pieces give the route), and the garrison soldiers standing in it
 * keep the block they stand on as their post.
 */
public final class SquadService {

    private static final Map<UUID, Squad> BY_LEADER = new HashMap<>();
    private static final Map<UUID, Squad> BY_MEMBER = new HashMap<>();
    /** Officers looked at this session that have no outpost (spawned by hand, outside any outpost). */
    private static final Set<UUID> NO_SQUAD = new HashSet<>();

    private SquadService() {
    }

    // --- hooks --------------------------------------------------------------------------------------------------

    /**
     * {@code Garrison.place}: a garrison mob placed at {@code post} facing {@code facing} in an outpost of
     * {@code pieces}. Every mob keeps its post; an officer gets his squad's route.
     */
    public static void onGarrisonPlaced(SeafarerMob mob, BlockPos post, Direction facing, List<GarrisonPosts.PlacedPiece> pieces) {
        BlockPos anchor = SquadRoutes.anchor(pieces);
        if (anchor == null) return;
        Services.ATTACHMENTS.set(mob, SquadAttachments.POST, new GarrisonPost(post.immutable(), facing, anchor));
        if (mob instanceof NavyOfficer) {
            Services.ATTACHMENTS.set(mob, SquadAttachments.SQUAD, SquadData.atPost(SquadRoutes.route(pieces), anchor));
        }
    }

    /** The officer's server tick: runs his squad's brain. */
    public static void tickLeader(NavyOfficer officer) {
        if (!(officer.level() instanceof ServerLevel)) return;
        Squad squad = of(officer);
        if (squad != null) squad.tick();
    }

    /** Server stopped: forget every squad (they are saved on their officers). */
    public static void clear() {
        BY_LEADER.clear();
        BY_MEMBER.clear();
        NO_SQUAD.clear();
    }

    // --- lookup -------------------------------------------------------------------------------------------------

    /** The squad of {@code officer}, loaded from his save (or built from his outpost) on first use; null if none. */
    public static @Nullable Squad of(NavyOfficer officer) {
        if (!(officer.level() instanceof ServerLevel level)) return null;
        UUID id = officer.getUUID();
        Squad squad = BY_LEADER.get(id);
        if (squad != null && squad.leader() == officer) return squad;
        if (squad != null) forget(squad); // the officer was reloaded: a new entity object
        if (NO_SQUAD.contains(id)) return null;
        SquadData data = Services.ATTACHMENTS.has(officer, SquadAttachments.SQUAD)
                ? Services.ATTACHMENTS.get(officer, SquadAttachments.SQUAD)
                : fromStructure(level, officer);
        if (data == null || data.route().isEmpty()) {
            NO_SQUAD.add(id);
            return null;
        }
        squad = Squad.load(officer, data);
        BY_LEADER.put(id, squad);
        for (UUID member : squad.members()) BY_MEMBER.put(member, squad);
        return squad;
    }

    /** The squad {@code soldier} belongs to while its officer is alive and loaded, else null. */
    public static @Nullable Squad memberSquad(NavySoldier soldier) {
        Squad squad = BY_MEMBER.get(soldier.getUUID());
        if (squad == null) return null;
        if (!squad.leaderAlive()) {
            forget(squad);
            return null;
        }
        if (!squad.hasMember(soldier.getUUID())) {
            BY_MEMBER.remove(soldier.getUUID(), squad);
            return null;
        }
        return squad;
    }

    /** The officer nearest to {@code pos} within {@code range} that leads a squad. */
    public static @Nullable Squad nearest(ServerLevel level, BlockPos pos, double range) {
        Squad best = null;
        double bestSq = Double.MAX_VALUE;
        for (NavyOfficer o : level.getEntitiesOfClass(NavyOfficer.class, new AABB(pos).inflate(range), NavyOfficer::isAlive)) {
            Squad s = of(o);
            double d = o.distanceToSqr(pos.getCenter());
            if (s != null && d < bestSq) {
                best = s;
                bestSq = d;
            }
        }
        return best;
    }

    static void index(UUID member, Squad squad) {
        BY_MEMBER.put(member, squad);
    }

    static void unindex(UUID member, Squad squad) {
        BY_MEMBER.remove(member, squad);
    }

    private static void forget(Squad squad) {
        BY_LEADER.remove(squad.leader().getUUID(), squad);
        BY_MEMBER.values().removeIf(s -> s == squad);
    }

    // --- posts --------------------------------------------------------------------------------------------------

    public static @Nullable GarrisonPost post(Mob mob) {
        return Services.ATTACHMENTS.has(mob, SquadAttachments.POST) ? Services.ATTACHMENTS.get(mob, SquadAttachments.POST) : null;
    }

    /** {@code mob} stands at its post (within a block); true for a mob without a post. */
    public static boolean atPost(Mob mob) {
        GarrisonPost post = post(mob);
        return post == null || Squad.horizontalDistance(mob.position(), post.pos()) <= 1.0
                && Math.abs(mob.getY() - post.pos().getY()) < 1.0;
    }

    /** Back at its post: stops, stands still again ({@code stationary}) and faces the post's way. */
    public static void settle(SeafarerMob mob) {
        mob.getNavigation().stop();
        mob.setStationary(true);
        GarrisonPost post = post(mob);
        if (post == null) return;
        float yaw = post.facing().toYRot();
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
    }

    /** A mob can stand at {@code pos}: no collision there and above, something to stand on below. */
    static boolean standable(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    // --- outposts from before MOB2 ------------------------------------------------------------------------------

    /**
     * The squad of an officer standing in a navy outpost that was generated before squads existed: the route from the
     * outpost's pieces; the officer and every stationary soldier in it without a post keep where they stand as post.
     * Null outside an outpost.
     */
    private static @Nullable SquadData fromStructure(ServerLevel level, NavyOfficer officer) {
        Structure outpost = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OutpostKeys.NAVY_OUTPOST);
        if (outpost == null) return null;
        StructureStart start = level.structureManager().getStructureWithPieceAt(officer.blockPosition(), outpost);
        if (!start.isValid()) return null;
        List<GarrisonPosts.PlacedPiece> pieces = Garrison.placedPieces(new PiecesContainer(start.getPieces()));
        BlockPos anchor = SquadRoutes.anchor(pieces);
        if (anchor == null) return null;
        keepPostWhereStanding(officer, anchor);
        for (NavySoldier s : level.getEntitiesOfClass(NavySoldier.class, AABB.of(start.getBoundingBox()).inflate(1),
                s -> s.isAlive() && s.isStationary())) {
            keepPostWhereStanding(s, anchor);
        }
        SquadData data = SquadData.atPost(SquadRoutes.route(pieces), anchor);
        Services.ATTACHMENTS.set(officer, SquadAttachments.SQUAD, data);
        return data;
    }

    private static void keepPostWhereStanding(SeafarerMob mob, BlockPos anchor) {
        if (Services.ATTACHMENTS.has(mob, SquadAttachments.POST)) return;
        Services.ATTACHMENTS.set(mob, SquadAttachments.POST,
                new GarrisonPost(mob.blockPosition(), Direction.fromYRot(mob.getYRot()), anchor));
    }
}
