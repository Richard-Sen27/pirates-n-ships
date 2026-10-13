package com.richardsenger.piratesnships.crew.walk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The pure rules of walking round people (WALK2): which cells of a walker's path are held by someone, the sidestep that
 * takes it past them, the yield rule for two walkers meeting head-on, and the wait accounting. No world access;
 * {@link WalkAvoidance} feeds it plot cells (the ship's grid, sable-notes §6) and a walkability test.
 * <p>
 * An <b>occupant</b> holds its cell (a standing crew member, villager or player by its feet, a seated hand by its seat's
 * cell). A path cell counts as held when an occupant stands in the same column one cell above, at or below it (a hand
 * on a step, a walker on a stair). Searches are flat (the walker's own deck height) and step to the four horizontal
 * neighbours only, so a detour never cuts a corner past a wall.
 */
public final class WalkAvoid {

    /** Path cells ahead of the walker that are looked at: the next cell and the one after (the one it is about to enter). */
    public static final int LOOKAHEAD = 2;
    /** Farthest a sidestep strays from the walker (blocks, along either axis). */
    public static final int DETOUR_RADIUS = 4;
    /** Farthest a yielding walker backs off to make room (blocks, along either axis). */
    public static final int REFUGE_RADIUS = 6;

    /** What a walker does when another walker's path runs into it head-on. */
    public enum Meeting {
        /** Not a head-on meeting: the other is no walker, or its way does not lead through this walker's cell. */
        NONE,
        /** This walker gives way: it steps off the other's way (aside, or back along its own) and waits there. */
        YIELD,
        /** This walker keeps its way and waits until the other has stepped off it. */
        HOLD
    }

    private WalkAvoid() {
    }

    // ------------------------------------------------------------------ obstacles

    /** Whether {@code cell} is held by one of {@code occupants}: the same column, at most one cell up or down. */
    public static boolean occupied(Collection<BlockPos> occupants, BlockPos cell) {
        for (BlockPos o : occupants) {
            if (o.getX() == cell.getX() && o.getZ() == cell.getZ() && Math.abs(o.getY() - cell.getY()) <= 1) {
                return true;
            }
        }
        return false;
    }

    /** The occupant that holds {@code cell}, or null. */
    public static @Nullable BlockPos holder(Collection<BlockPos> occupants, BlockPos cell) {
        for (BlockPos o : occupants) {
            if (o.getX() == cell.getX() && o.getZ() == cell.getZ() && Math.abs(o.getY() - cell.getY()) <= 1) {
                return o;
            }
        }
        return null;
    }

    /** Index of the first cell of {@code ahead} held by an occupant, or -1 when the way is clear. */
    public static int firstBlocked(List<BlockPos> ahead, Collection<BlockPos> occupants) {
        for (int i = 0; i < ahead.size(); i++) {
            if (occupied(occupants, ahead.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The cells a walker can see ahead from node {@code next} of a path of {@code nodes}: at most {@link #LOOKAHEAD}
     * cells, never the walker's own cell {@code self} (a node it still stands in).
     */
    public static List<BlockPos> ahead(List<BlockPos> nodes, int next, BlockPos self) {
        List<BlockPos> out = new ArrayList<>(LOOKAHEAD);
        for (int i = Math.max(0, next); i < nodes.size() && out.size() < LOOKAHEAD; i++) {
            BlockPos n = nodes.get(i);
            if (!sameColumn(n, self)) {
                out.add(n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the sidestep

    /**
     * The sidestep past a held cell: the shortest walk over free cells from {@code from} to any of {@code rejoin} (the
     * walker's path beyond the held cell, nearest first, and its goal), at {@code from}'s height, within
     * {@link #DETOUR_RADIUS}. The cells held by {@code occupants} are temporary obstacles; {@code walkable} says which
     * cells the walker may stand in. Among equally short ways the one that keeps the path's direction wins: neighbours
     * are tried forward (towards {@code blocked}), then to the right, then left, then back, so two walkers meeting in a
     * two-wide walkway both pass on their right. Returns the cells to walk (without {@code from}, ending on the rejoin
     * cell), or null when there is no way round.
     */
    public static @Nullable List<BlockPos> detour(BlockPos from, BlockPos blocked, List<BlockPos> rejoin,
            Predicate<BlockPos> walkable, Collection<BlockPos> occupants) {
        Set<BlockPos> goals = new HashSet<>();
        for (BlockPos r : rejoin) {
            if (r.getY() == from.getY() && !occupied(occupants, r) && !sameColumn(r, from)) {
                goals.add(r.immutable());
            }
        }
        if (goals.isEmpty()) {
            return null;
        }
        Direction forward = towards(from, blocked);
        return search(from, order(forward), DETOUR_RADIUS, c -> walkable.test(c) && !occupied(occupants, c), goals::contains);
    }

    // ------------------------------------------------------------------ head-on

    /**
     * The yield rule for two walkers that meet head-on (each in the other's way): the one with the lower entity id
     * yields, the other holds. {@code otherWalks}: the other is a walking crew member; {@code selfOnOtherWay}: this
     * walker's cell lies on the other's way ahead. Stable: both sides compute the same pair of answers from their ids.
     */
    public static Meeting meeting(int selfId, int otherId, boolean otherWalks, boolean selfOnOtherWay) {
        if (!otherWalks || !selfOnOtherWay || selfId == otherId) {
            return Meeting.NONE;
        }
        return selfId < otherId ? Meeting.YIELD : Meeting.HOLD;
    }

    /** Whether {@code cell} lies on {@code way} (same column, one up or down). */
    public static boolean onWay(BlockPos cell, Collection<BlockPos> way) {
        return occupied(way, cell);
    }

    /**
     * Where a yielding walker steps to: the nearest free cell off the other's {@code way} (and not the other's own cell
     * {@code other}), at {@code from}'s height within {@link #REFUGE_RADIUS}; neighbours tried back (away from
     * {@code other}), right, left, forward, so it steps aside where it can and otherwise backs along its own way. Returns
     * the cells to walk (empty when {@code from} is already off the way), or null when there is no room.
     */
    public static @Nullable List<BlockPos> refuge(BlockPos from, BlockPos other, Collection<BlockPos> way,
            Predicate<BlockPos> walkable, Collection<BlockPos> occupants) {
        Set<BlockPos> blocked = new HashSet<>(way);
        blocked.add(other);
        if (!occupied(blocked, from)) {
            return List.of();
        }
        Direction back = towards(from, other).getOpposite();
        List<Direction> order = List.of(back, back.getCounterClockWise(), back.getClockWise(), back.getOpposite());
        return search(from, order, REFUGE_RADIUS, c -> walkable.test(c) && !occupied(occupants, c) && !sameColumn(c, other),
                c -> !occupied(blocked, c));
    }

    /** Whether the other walker has gone past {@code self}: none of its {@code way} ahead lies within a cell of it. */
    public static boolean passed(BlockPos self, Collection<BlockPos> way) {
        for (BlockPos w : way) {
            if (Math.abs(w.getX() - self.getX()) <= 1 && Math.abs(w.getZ() - self.getZ()) <= 1 && Math.abs(w.getY() - self.getY()) <= 1) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ waiting and the timeout

    /**
     * Whether a walker that started waiting at {@code since} goes on at {@code now}: when its way ahead is clear again
     * ({@code clear}), or after {@code waitTicks}, when it looks for a way again.
     */
    public static boolean waitOver(long since, long now, int waitTicks, boolean clear) {
        return clear || now - since >= waitTicks;
    }

    /**
     * The ticks that count towards the walk's timeout after {@code elapsed} ticks of which {@code sidestepTicks} went
     * on sidesteps: walking and waiting count, the extra steps round people do not, up to {@code timeoutTicks} (so a
     * walker that sidesteps for ever still times out, at twice the timeout at the latest).
     */
    public static long timeoutElapsed(long elapsed, long sidestepTicks, int timeoutTicks) {
        return Math.max(0, elapsed - Math.min(Math.max(0, sidestepTicks), timeoutTicks));
    }

    // ------------------------------------------------------------------ grid search

    /** The horizontal direction from {@code from} towards {@code to} along the larger offset (north when equal columns). */
    static Direction towards(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) {
            return Direction.NORTH;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Forward, right, left, back: the right of a heading is its clockwise turn seen from above. */
    static List<Direction> order(Direction forward) {
        return List.of(forward, forward.getClockWise(), forward.getCounterClockWise(), forward.getOpposite());
    }

    private static boolean sameColumn(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ() && Math.abs(a.getY() - b.getY()) <= 1;
    }

    /** Breadth-first over the flat grid at {@code from}'s height; the first goal found, its way without {@code from}. */
    private static @Nullable List<BlockPos> search(BlockPos from, List<Direction> order, int radius, Predicate<BlockPos> free,
            Predicate<BlockPos> goal) {
        BlockPos start = from.immutable();
        Map<BlockPos, BlockPos> came = new HashMap<>();
        came.put(start, start);
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        while (!open.isEmpty()) {
            BlockPos c = open.poll();
            for (Direction d : order) {
                BlockPos n = c.relative(d);
                if (came.containsKey(n) || Math.abs(n.getX() - start.getX()) > radius || Math.abs(n.getZ() - start.getZ()) > radius
                        || !free.test(n)) {
                    continue;
                }
                came.put(n, c);
                if (goal.test(n)) {
                    List<BlockPos> way = new ArrayList<>();
                    for (BlockPos p = n; !p.equals(start); p = came.get(p)) {
                        way.add(0, p);
                    }
                    return way;
                }
                open.add(n);
            }
        }
        return null;
    }
}
