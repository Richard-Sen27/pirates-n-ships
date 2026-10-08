package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A navy officer's squad (MOB2, docs/design.md §9): the officer as leader, the soldiers drawn from his outpost's
 * garrison, the patrol route ({@link SquadRoutes}), the current waypoint and the {@link SquadState}. This is the
 * squad's brain, ticked by the officer every server tick ({@link SquadService#tickLeader}); the goals only walk
 * ({@link SquadLeaderGoal}, {@link SquadMemberGoal}).
 *
 * <ul>
 *     <li><b>At the posts</b> the garrison stands as WG3 placed it. A patrol starts {@code patrol_interval_minutes}
 *     after the last one ended (not at night with {@code night_at_posts}), or by command.</li>
 *     <li><b>Patrolling</b> the officer walks the route, the members in file behind him. At each waypoint he waits
 *     until the file has closed up (at most {@link #GATHER_TICKS}), then pauses {@code post_pause_seconds}. A member
 *     who died is replaced from the garrison when the squad reaches the next waypoint. A waypoint that is blocked
 *     (terrain) or that the officer can't get closer to for {@link #STUCK_TICKS} is skipped. After the last waypoint,
 *     at nightfall (unless ordered by command) or by command the squad returns.</li>
 *     <li><b>Fighting</b> ({@link SquadCombat}): the squad fights as one; when it has had no target for
 *     {@link #QUIET_TICKS} it re-forms on the officer and goes on, or walks back to the posts.</li>
 *     <li><b>Returning</b> everyone walks to his own post and stands there, stationary again.</li>
 * </ul>
 * Saved on the officer ({@link SquadAttachments#SQUAD}) whenever the state, waypoint or members change.
 */
public final class Squad {

    /** What the officer does on patrol. */
    public enum Phase { WALK, GATHER, PAUSE, REFORM }

    /** The squad reached waypoint {@code waypoint} with its file closed up to {@code maxGap} blocks (tests, info). */
    public record Arrival(int waypoint, double maxGap, int members, long time) {
    }

    /** Longest wait at a waypoint (or after a fight) for the file to close up. */
    static final int GATHER_TICKS = 300;
    /** A waypoint the officer got no closer to for this long is skipped. */
    static final int STUCK_TICKS = 200;
    /** A return that takes longer ends anyway; stragglers walk home on their own. */
    static final int RETURN_TIMEOUT_TICKS = 1200;
    /** Ticks without a target after which a fight is over. */
    static final int QUIET_TICKS = 40;
    /** After loading, members not loaded yet are kept this long before they count as lost. */
    static final int LOAD_GRACE_TICKS = 40;
    /** Horizontal blocks within which the officer has reached a waypoint. */
    static final double ARRIVE = 1.25;
    /** Horizontal blocks within which an officer whose path has ended counts as there. */
    static final double NEAR = 2.5;
    /** Blocks around the officer within which garrison soldiers are drawn. */
    static final double DRAFT_RANGE = 64.0;
    /** Path search budget (times vanilla's) for whether a soldier can walk to the officer at all. */
    static final float DRAFT_SEARCH_MULTIPLIER = 4f;

    private final NavyOfficer leader;
    private final List<BlockPos> route;
    private final BlockPos outpost;
    private SquadState state;
    private int waypoint;
    private final List<UUID> members;
    private long nextPatrolAt;
    private boolean forced;

    // --- memory only ---
    private Phase phase = Phase.WALK;
    private long phaseUntil;
    private SquadState resume = SquadState.AT_POST;
    private int quiet;
    private long returnSince;
    private double bestDistance = Double.MAX_VALUE;
    private long bestAt;
    private final long loadedAt;
    final Map<UUID, Integer> hurtSeen = new HashMap<>();
    private @Nullable Arrival lastArrival;
    private int arrivals;
    private int replacements;

    private Squad(NavyOfficer leader, SquadData data) {
        this.leader = leader;
        this.route = data.route();
        this.outpost = data.outpost();
        // a squad saved mid-fight walks home
        this.state = data.state() == SquadState.FIGHTING ? SquadState.RETURNING : data.state();
        this.waypoint = data.waypoint();
        this.members = new ArrayList<>(data.members());
        this.nextPatrolAt = data.nextPatrolAt();
        this.forced = data.forced();
        this.loadedAt = leader.level().getGameTime();
        this.returnSince = loadedAt;
        this.bestAt = loadedAt;
    }

    static Squad load(NavyOfficer leader, SquadData data) {
        return new Squad(leader, data);
    }

    // --- accessors ----------------------------------------------------------------------------------------------

    public NavyOfficer leader() {
        return leader;
    }

    public List<BlockPos> route() {
        return route;
    }

    public BlockPos outpost() {
        return outpost;
    }

    public SquadState state() {
        return state;
    }

    public Phase phase() {
        return phase;
    }

    public int waypoint() {
        return waypoint;
    }

    /** The current waypoint, or {@code null} past the route's end. */
    public @Nullable BlockPos currentWaypoint() {
        return waypoint >= 0 && waypoint < route.size() ? route.get(waypoint) : null;
    }

    /** The members' ids in file order (the first walks right behind the officer). */
    public List<UUID> members() {
        return List.copyOf(members);
    }

    public boolean hasMember(UUID id) {
        return members.contains(id);
    }

    public long nextPatrolAt() {
        return nextPatrolAt;
    }

    public boolean forced() {
        return forced;
    }

    public @Nullable Arrival lastArrival() {
        return lastArrival;
    }

    /** Waypoints reached since loaded (tests). */
    public int arrivals() {
        return arrivals;
    }

    /** Members drawn to replace lost ones since loaded (tests). */
    public int replacements() {
        return replacements;
    }

    boolean leaderAlive() {
        return leader.isAlive() && !leader.isRemoved();
    }

    // --- the brain ----------------------------------------------------------------------------------------------

    void tick() {
        if (!(leader.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        boolean enabled = SquadConfig.ENABLED.get();
        if (state != SquadState.AT_POST) pruneMembers(level, now);
        if (enabled) SquadCombat.tick(this, level, now);
        switch (state) {
            case AT_POST -> {
                if (!enabled) return;
                if (nextPatrolAt <= 0) {
                    nextPatrolAt = now + SquadConfig.patrolIntervalTicks();
                    save();
                } else if (now >= nextPatrolAt && !nightRest(level)) {
                    startPatrol(false);
                }
            }
            case PATROLLING -> {
                if (!enabled || !forced && nightRest(level)) beginReturn(now);
                else patrolStep(level, now);
            }
            case FIGHTING -> {
                if (!enabled) beginReturn(now);
            }
            case RETURNING -> returnStep(level, now);
        }
    }

    private static boolean nightRest(ServerLevel level) {
        return SquadConfig.NIGHT_AT_POSTS.get() && level.isNight();
    }

    /**
     * Starts a patrol: draws {@code mobs.squad.size} soldiers from the garrison and sets off for the first waypoint.
     * Refused while squads are disabled or without a route.
     *
     * @param ordered by command: ignores the night until it is over
     */
    public boolean startPatrol(boolean ordered) {
        if (!(leader.level() instanceof ServerLevel level) || route.isEmpty() || !SquadConfig.ENABLED.get()) return false;
        long now = level.getGameTime();
        for (UUID id : members) SquadService.unindex(id, this);
        members.clear();
        state = SquadState.PATROLLING;
        waypoint = 0;
        forced = ordered;
        startPhase(Phase.WALK, now);
        leader.setStationary(false);
        draft(level, SquadConfig.SIZE.get());
        save();
        return true;
    }

    /** Sends the squad back to the garrison posts (by command; no-op while at the posts). */
    public boolean orderReturn() {
        if (state == SquadState.AT_POST || !(leader.level() instanceof ServerLevel level)) return false;
        beginReturn(level.getGameTime());
        return true;
    }

    private void patrolStep(ServerLevel level, long now) {
        BlockPos wp = currentWaypoint();
        if (wp == null) {
            beginReturn(now);
            return;
        }
        switch (phase) {
            case WALK -> {
                if (!SquadService.standable(level, wp)) {
                    advance(now);
                    return;
                }
                // on the spot, or as near as the path got him (someone may stand on it)
                boolean near = horizontalDistance(leader.position(), wp) <= NEAR && Math.abs(leader.getY() - wp.getY()) < 1.5
                        && leader.getNavigation().isDone() && now - bestAt > 10;
                if (reached(leader, wp) || near) {
                    refill(level);
                    startPhase(Phase.GATHER, now);
                    phaseUntil = now + GATHER_TICKS;
                    return;
                }
                double d = horizontalDistance(leader.position(), wp);
                if (d < bestDistance - 0.5) {
                    bestDistance = d;
                    bestAt = now;
                } else if (now - bestAt > STUCK_TICKS) {
                    advance(now);
                }
            }
            case GATHER -> {
                List<Vec3> file = file(level);
                if (FileFollowing.closedUp(file) || now >= phaseUntil) {
                    lastArrival = new Arrival(waypoint, FileFollowing.maxGap(file), members.size(), now);
                    arrivals++;
                    startPhase(Phase.PAUSE, now);
                    phaseUntil = now + SquadConfig.pauseTicks();
                }
            }
            case PAUSE -> {
                if (now >= phaseUntil) advance(now);
            }
            case REFORM -> {
                if (FileFollowing.closedUp(file(level)) || now >= phaseUntil) startPhase(Phase.WALK, now);
            }
        }
    }

    private void advance(long now) {
        waypoint++;
        if (waypoint >= route.size()) {
            beginReturn(now);
            return;
        }
        startPhase(Phase.WALK, now);
        save();
    }

    private void startPhase(Phase next, long now) {
        phase = next;
        bestDistance = Double.MAX_VALUE;
        bestAt = now;
    }

    private void beginReturn(long now) {
        if (state == SquadState.PATROLLING || state == SquadState.FIGHTING && resume == SquadState.PATROLLING) {
            nextPatrolAt = now + SquadConfig.patrolIntervalTicks(); // the next patrol counts from the end of this one
        }
        state = SquadState.RETURNING;
        returnSince = now;
        forced = false;
        startPhase(Phase.WALK, now);
        save();
    }

    private void returnStep(ServerLevel level, long now) {
        boolean home = SquadService.atPost(leader);
        for (SeafarerMob m : loadedMembers(level)) home &= SquadService.atPost(m);
        if (home || now - returnSince > RETURN_TIMEOUT_TICKS) finishReturn(level);
    }

    private void finishReturn(ServerLevel level) {
        if (SquadService.atPost(leader)) SquadService.settle(leader);
        for (SeafarerMob m : loadedMembers(level)) {
            if (SquadService.atPost(m)) SquadService.settle(m);
        }
        for (UUID id : members) SquadService.unindex(id, this);
        members.clear();
        state = SquadState.AT_POST;
        waypoint = 0;
        forced = false;
        save();
    }

    // --- combat (SquadCombat) -----------------------------------------------------------------------------------

    /** Called every tick by {@link SquadCombat} with whether anyone of the squad has a target. */
    void combat(boolean fighting, long now) {
        if (fighting) {
            quiet = 0;
            if (state != SquadState.FIGHTING) {
                resume = state;
                state = SquadState.FIGHTING;
                save();
            }
            return;
        }
        if (state != SquadState.FIGHTING || ++quiet < QUIET_TICKS) return;
        if (resume == SquadState.PATROLLING) {
            // re-form on the officer where the fight ended, then go on to the waypoint
            state = SquadState.PATROLLING;
            startPhase(Phase.REFORM, now);
            phaseUntil = now + GATHER_TICKS;
            save();
        } else {
            beginReturn(now);
        }
    }

    // --- members ------------------------------------------------------------------------------------------------

    /** The officer and the loaded, living members, in file order. */
    public List<SeafarerMob> mobs(ServerLevel level) {
        List<SeafarerMob> out = new ArrayList<>();
        out.add(leader);
        out.addAll(loadedMembers(level));
        return out;
    }

    List<NavySoldier> loadedMembers(ServerLevel level) {
        List<NavySoldier> out = new ArrayList<>();
        for (UUID id : members) {
            if (level.getEntity(id) instanceof NavySoldier s && s.isAlive()) out.add(s);
        }
        return out;
    }

    /** Whom {@code soldier} follows: the living member in front of him, or the officer. */
    public SeafarerMob predecessor(ServerLevel level, NavySoldier soldier) {
        int i = members.indexOf(soldier.getUUID());
        for (int j = i - 1; j >= 0; j--) {
            if (level.getEntity(members.get(j)) instanceof NavySoldier s && s.isAlive()) return s;
        }
        return leader;
    }

    private List<Vec3> file(ServerLevel level) {
        return mobs(level).stream().map(Entity::position).toList();
    }

    /** Drops members that died or are gone (members not loaded yet get {@link #LOAD_GRACE_TICKS} after loading). */
    private void pruneMembers(ServerLevel level, long now) {
        boolean changed = members.removeIf(id -> {
            Entity e = level.getEntity(id);
            boolean lost = e == null ? now - loadedAt > LOAD_GRACE_TICKS : !e.isAlive() || e.isRemoved();
            if (lost) SquadService.unindex(id, this);
            return lost;
        });
        if (changed) save();
    }

    /** Fills the squad up to {@code mobs.squad.size} from the garrison (at a waypoint, after losses). */
    private void refill(ServerLevel level) {
        int missing = SquadConfig.SIZE.get() - members.size();
        if (missing > 0) replacements += draft(level, SquadConfig.SIZE.get());
    }

    /**
     * Draws soldiers of this outpost's garrison until the squad has {@code size} members: alive, not fighting, not in
     * another squad, nearest to the officer first, and only those who can walk to him (no guard on a tower roof that
     * only a ladder reaches). Returns the number drawn.
     */
    private int draft(ServerLevel level, int size) {
        if (members.size() >= size) return 0;
        AABB box = leader.getBoundingBox().inflate(DRAFT_RANGE, DRAFT_RANGE / 2, DRAFT_RANGE);
        List<NavySoldier> candidates = new ArrayList<>(level.getEntitiesOfClass(NavySoldier.class, box, s -> s.isAlive()
                && s.getTarget() == null && !members.contains(s.getUUID()) && SquadService.memberSquad(s) == null
                && Services.ATTACHMENTS.has(s, SquadAttachments.POST)
                && Services.ATTACHMENTS.get(s, SquadAttachments.POST).outpost().equals(outpost)));
        candidates.sort(Comparator.comparingDouble(s -> s.distanceToSqr(leader)));
        int drawn = 0;
        for (NavySoldier s : candidates) {
            if (members.size() >= size) break;
            // the walkway's one stair makes long detours (a wall guard on the far run): search wider than a chase does
            s.getNavigation().setMaxVisitedNodesMultiplier(DRAFT_SEARCH_MULTIPLIER);
            Path path = SquadReach.wide(s, () -> s.getNavigation().createPath(leader, 1));
            s.getNavigation().resetMaxVisitedNodesMultiplier();
            if (path == null || !path.canReach()) continue;
            members.add(s.getUUID());
            SquadService.index(s.getUUID(), this);
            hurtSeen.put(s.getUUID(), s.getLastHurtByMobTimestamp());
            s.setStationary(false);
            drawn++;
        }
        if (drawn > 0) save();
        return drawn;
    }

    // --- geometry -----------------------------------------------------------------------------------------------

    static boolean reached(Entity e, BlockPos target) {
        return horizontalDistance(e.position(), target) <= ARRIVE && Math.abs(e.getY() - target.getY()) < 1.5;
    }

    static double horizontalDistance(Vec3 pos, BlockPos target) {
        double dx = pos.x - (target.getX() + 0.5);
        double dz = pos.z - (target.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** State, waypoint and where everyone stands (logs, debugging). */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Squad[").append(state).append('/').append(phase)
                .append(" wp ").append(waypoint).append('/').append(route.size());
        BlockPos wp = currentWaypoint();
        if (wp != null) b.append(' ').append(wp.toShortString());
        b.append(" arrivals ").append(arrivals).append(" leader ").append(describe(leader));
        if (leader.level() instanceof ServerLevel level) {
            for (NavySoldier m : loadedMembers(level)) b.append(" | ").append(describe(m));
        }
        return b.append(']').toString();
    }

    private static String describe(SeafarerMob m) {
        return String.format("%.1f %.1f %.1f%s%s", m.getX(), m.getY(), m.getZ(), m.getTarget() != null ? " target" : "",
                m.getNavigation().isDone() ? "" : " moving");
    }

    // --- persistence --------------------------------------------------------------------------------------------

    SquadData data() {
        return new SquadData(route, outpost, state, waypoint, members, nextPatrolAt, forced);
    }

    void save() {
        Services.ATTACHMENTS.set(leader, SquadAttachments.SQUAD, data());
    }
}
