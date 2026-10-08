package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.worldsim.lane.Lane;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure rules of pirate raids (WS5, design.md §10.4 "Pirate raids on navy settlements", "Retaliation"): no world
 * access, tested by {@code RaidRulesTest}.
 *
 * <ul>
 *   <li><b>Presence:</b> every minute with a player inside the settlement's box counts one minute; a minute without
 *       anyone there starts the count over.</li>
 *   <li><b>Chance per minute:</b> {@code min(cap, growth × multiplier × minutes)}; zero during the cooldown of
 *       {@code cooldown_days} after the settlement's last raid.</li>
 *   <li><b>Retaliation:</b> the multiplier is {@code 1 + factor × tension(Navy, Pirates)} when retaliation is on, else 1.</li>
 *   <li><b>Approach:</b> the raiders appear the last {@code approach_distance} blocks out on their lane (or on a straight
 *       line from the sea when there is no lane).</li>
 * </ul>
 */
public final class RaidRules {

    /** No raid recorded yet for a settlement. */
    public static final long NO_DAY = Long.MIN_VALUE;

    /** The chance parameters: growth per minute, cap, cooldown in days, and the retaliation multiplier on the growth. */
    public record Params(double growth, double cap, double cooldownDays, double multiplier) {
    }

    /** How a raid ended for the faction state: nothing to report, succeeded or repelled. */
    public enum Outcome { NONE, SUCCEEDED, REPELLED }

    private RaidRules() {
    }

    /** The presence count after one more minute. */
    public static int nextMinutes(int minutes, boolean present) {
        return present ? Math.max(0, minutes) + 1 : 0;
    }

    /** The growth multiplier from the Navy-Pirates tension (clamped to 0..1). */
    public static double multiplier(boolean retaliation, double factor, double tension) {
        if (!retaliation) return 1.0;
        return 1.0 + Math.max(0.0, factor) * Math.max(0.0, Math.min(1.0, tension));
    }

    /** Whether the settlement is still on cooldown on {@code today} after a raid on {@code lastRaidDay}. */
    public static boolean onCooldown(long lastRaidDay, long today, double cooldownDays) {
        if (lastRaidDay == NO_DAY) return false;
        return today - lastRaidDay < cooldownDays;
    }

    /** Days left on the cooldown (0 when none). */
    public static double cooldownLeft(long lastRaidDay, long today, double cooldownDays) {
        if (lastRaidDay == NO_DAY) return 0.0;
        return Math.max(0.0, cooldownDays - (today - lastRaidDay));
    }

    /** The raid chance per minute after {@code minutes} of presence, outside a cooldown. */
    public static double chance(int minutes, Params p) {
        if (minutes <= 0 || p.cap() <= 0.0 || p.growth() <= 0.0) return 0.0;
        return Math.min(p.cap(), p.growth() * p.multiplier() * minutes);
    }

    /** The raid chance per minute now: zero during the cooldown. */
    public static double chance(int minutes, Params p, long lastRaidDay, long today) {
        return onCooldown(lastRaidDay, today, p.cooldownDays()) ? 0.0 : chance(minutes, p);
    }

    /** Whether a roll in [0, 1) starts a raid at {@code chance}. */
    public static boolean rolls(double chance, double roll) {
        return roll < chance;
    }

    /** What a raid reports once every ship of it is done. */
    public static Outcome outcome(boolean fought, boolean succeeded) {
        if (!fought) return Outcome.NONE;
        return succeeded ? Outcome.SUCCEEDED : Outcome.REPELLED;
    }

    /** Whether a ship centre at {@code (x, z)} is close enough to land at {@code target}. */
    public static boolean withinLanding(double x, double z, double targetX, double targetZ, double landingDistance) {
        return Math.hypot(x - targetX, z - targetZ) <= landingDistance;
    }

    /**
     * The last {@code distance} blocks of {@code lane} (all of it when shorter): its first point is the cut point on
     * the lane, the rest are the lane's later points.
     */
    public static List<Lane.Point> approach(List<Lane.Point> lane, double distance) {
        if (lane.size() < 2) return List.copyOf(lane);
        double length = Lane.length(lane);
        if (distance >= length) return List.copyOf(lane);
        double cut = length - Math.max(0.0, distance);
        Lane.Position p = Lane.positionAlong(lane, cut);
        List<Lane.Point> out = new ArrayList<>();
        out.add(new Lane.Point((int) Math.round(p.x()), (int) Math.round(p.z())));
        for (int i = p.leg() + 1; i < lane.size(); i++) {
            Lane.Point next = lane.get(i);
            if (!next.equals(out.get(out.size() - 1))) out.add(next);
        }
        if (out.size() < 2) out.add(lane.get(lane.size() - 1));
        return out;
    }

    /**
     * A straight approach to {@code target} from {@code distance} blocks out along the direction {@code (dirX, dirZ)}
     * (toward the sea). A zero direction counts as north.
     */
    public static List<Lane.Point> straight(Lane.Point target, double dirX, double dirZ, double distance) {
        double len = Math.hypot(dirX, dirZ);
        double ux = len < 1e-9 ? 0.0 : dirX / len;
        double uz = len < 1e-9 ? -1.0 : dirZ / len;
        Lane.Point start = new Lane.Point((int) Math.round(target.x() + ux * distance), (int) Math.round(target.z() + uz * distance));
        return List.of(start, target);
    }

    /**
     * The way home from where a ship lies ({@code x, z}) back along its approach: the approach reversed, without its
     * end (the berth the ship lies off), starting at the ship.
     */
    public static List<Lane.Point> home(List<Lane.Point> approach, double x, double z) {
        List<Lane.Point> out = new ArrayList<>();
        out.add(new Lane.Point((int) Math.floor(x), (int) Math.floor(z)));
        for (int i = approach.size() - 2; i >= 0; i--) out.add(approach.get(i));
        if (out.size() < 2) out.add(out.get(0));
        return out;
    }
}
