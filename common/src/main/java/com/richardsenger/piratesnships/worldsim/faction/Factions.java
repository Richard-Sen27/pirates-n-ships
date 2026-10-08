package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.law.flag.Faction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The faction state service (design.md §10.4, WS1): read the state, report world events and deeds, run the daily
 * decay. Server thread only. While {@link FactionConfig#active()} is false, reports and decay change nothing (the
 * state is still readable).
 * <p>
 * The day edge: the overworld's day number ({@code dayTime / 24000}, which turns at sunrise) is compared with the
 * last day the data saw, once per server tick; each day passed decays the state by {@code decay_per_day}. The day is
 * saved with the state, so a restart neither loses nor repeats a day; a clock set back only remembers the new day.
 */
public final class Factions {

    /** Called after a report changed the state. */
    @FunctionalInterface
    public interface Listener {
        void onChanged(MinecraftServer server, FactionEvent event, FactionState before, FactionState after);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private Factions() {
    }

    public static FactionState state(MinecraftServer server) {
        return FactionData.get(server).state();
    }

    public static double aggression(MinecraftServer server, Faction f) {
        return state(server).aggression(f);
    }

    /** Tension between {@code a} and {@code b} in any order (0 for a faction with itself). */
    public static double tension(MinecraftServer server, Faction a, Faction b) {
        return state(server).tension(a, b);
    }

    public static long wealth(MinecraftServer server, Faction f) {
        return state(server).wealth(f);
    }

    /** Reports a world event (an NPC-side outcome) at {@code event_scale}; true if the state changed. */
    public static boolean report(MinecraftServer server, FactionEvent event) {
        return apply(server, event, FactionConfig.EVENT_SCALE.get());
    }

    /** Reports the faction side of a player's deed at {@code deed_scale}; true if the state changed. */
    public static boolean reportDeed(MinecraftServer server, FactionEvent event) {
        return apply(server, event, FactionConfig.DEED_SCALE.get());
    }

    private static boolean apply(MinecraftServer server, FactionEvent event, double strength) {
        if (!FactionConfig.active()) return false;
        FactionData data = FactionData.get(server);
        FactionState before = data.state();
        FactionState after = FactionRules.apply(before, event, strength, FactionConfig.MAX_WEALTH.get());
        if (after.equals(before)) return false;
        data.setState(after);
        for (Listener l : LISTENERS) l.onChanged(server, event, before, after);
        return true;
    }

    /** Replaces the whole state (commands, tests). Ignores the toggles. */
    public static void set(MinecraftServer server, FactionState state) {
        FactionData.get(server).setState(state);
    }

    /** Listens to every change a report makes. */
    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    /** The overworld's current day number. */
    public static long currentDay(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime(), (long) Level.TICKS_PER_DAY);
    }

    /**
     * Looks at the overworld's day; decays once per day passed since the last look. Returns the days decayed. Runs
     * every server tick; public for the GameTests.
     */
    public static long observeDay(MinecraftServer server) {
        return observeDay(server, currentDay(server));
    }

    /** {@link #observeDay(MinecraftServer)} with the given day number instead of the overworld's (GameTests). */
    public static long observeDay(MinecraftServer server, long day) {
        FactionData data = FactionData.get(server);
        long last = data.lastDay();
        data.setLastDay(day);
        if (last == FactionData.NO_DAY || day <= last || !FactionConfig.active()) return 0;
        long days = day - last;
        data.setState(FactionRules.decay(data.state(), FactionConfig.DECAY_PER_DAY.get(), days));
        return days;
    }
}
