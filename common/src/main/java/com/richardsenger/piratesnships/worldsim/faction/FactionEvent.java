package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.law.flag.Faction;

import java.util.List;
import java.util.Locale;

import static com.richardsenger.piratesnships.law.flag.Faction.MERCHANTS;
import static com.richardsenger.piratesnships.law.flag.Faction.NAVY;
import static com.richardsenger.piratesnships.law.flag.Faction.PIRATES;
import static com.richardsenger.piratesnships.worldsim.faction.FactionPair.NAVY_MERCHANTS;
import static com.richardsenger.piratesnships.worldsim.faction.FactionPair.NAVY_PIRATES;
import static com.richardsenger.piratesnships.worldsim.faction.FactionPair.PIRATES_MERCHANTS;

/**
 * Something that shifts the faction state (design.md §10.4, WS1), with its fixed shifts at strength 1. The strength
 * (config {@code event_scale} for world events, {@code deed_scale} for a player's deeds) multiplies every shift.
 * <p>
 * Rule against double counting: NPC-side outcomes (a patrol kills a pirate, a convoy arrives) are reported as world
 * events by the package that sees them; a player's own act is reported once, through its deed ({@code FactionDeeds}).
 */
public enum FactionEvent {
    /** A navy mob or ship killed a pirate. */
    PIRATE_KILLED_BY_NAVY(te(NAVY_PIRATES, 0.05), ag(PIRATES, 0.02), ag(NAVY, 0.01)),
    /** A pirate killed a navy soldier or officer. */
    NAVY_KILLED_BY_PIRATE(te(NAVY_PIRATES, 0.05), ag(NAVY, 0.03), ag(PIRATES, 0.01)),
    /** A merchant ship or convoy was plundered. */
    MERCHANT_PLUNDERED(te(PIRATES_MERCHANTS, 0.08), te(NAVY_PIRATES, 0.03), ag(NAVY, 0.02), ag(PIRATES, 0.02),
            we(MERCHANTS, -200), we(PIRATES, 200)),
    /** A merchant or villager was attacked or killed. */
    MERCHANT_ATTACKED(te(PIRATES_MERCHANTS, 0.04), ag(NAVY, 0.01)),
    /** A convoy reached its destination and sold its cargo. */
    CONVOY_DELIVERED(we(MERCHANTS, 100), we(NAVY, 20)),
    /** A convoy was sunk. */
    CONVOY_SUNK(te(PIRATES_MERCHANTS, 0.05), ag(NAVY, 0.02), we(MERCHANTS, -300)),
    /** A pirate raid on a settlement succeeded. */
    RAID_SUCCEEDED(te(NAVY_PIRATES, 0.10), te(PIRATES_MERCHANTS, 0.05), ag(PIRATES, 0.05), ag(NAVY, 0.03),
            we(PIRATES, 300), we(NAVY, -200), we(MERCHANTS, -100)),
    /** A pirate raid was beaten off. */
    RAID_REPELLED(te(NAVY_PIRATES, 0.05), ag(PIRATES, -0.05), ag(NAVY, 0.03), we(PIRATES, -100)),
    /** A navy patrol ship was sunk or captured. */
    PATROL_LOST(te(NAVY_PIRATES, 0.10), ag(NAVY, 0.05), we(NAVY, -500)),
    /** A pirate ship was sunk or captured. */
    PIRATE_SHIP_LOST(te(NAVY_PIRATES, 0.05), ag(PIRATES, -0.03), we(PIRATES, -300)),
    /** A navy ship was attacked (fired on, boarded) without being lost. */
    NAVY_ATTACKED(te(NAVY_PIRATES, 0.03), ag(NAVY, 0.02)),
    /** A pirate was handed over to the navy. */
    PIRATE_TURNED_IN(te(NAVY_PIRATES, 0.02), ag(PIRATES, 0.01), we(NAVY, -50)),
    /** Plunder was sold at a pirate fence. */
    PLUNDER_FENCED(we(PIRATES, 50), te(PIRATES_MERCHANTS, 0.01)),
    /** Honest trade at a port. */
    PORT_TRADE(we(MERCHANTS, 20), te(NAVY_MERCHANTS, -0.01));

    private final List<Shift> shifts;

    FactionEvent(Shift... shifts) {
        this.shifts = List.of(shifts);
    }

    /** The shifts at strength 1. */
    public List<Shift> shifts() {
        return shifts;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** One shift of an event: exactly one of faction (aggression or wealth) and pair (tension) is set. */
    public record Shift(Kind kind, Faction faction, FactionPair pair, double amount) {
        public enum Kind { AGGRESSION, WEALTH, TENSION }
    }

    private static Shift ag(Faction f, double amount) {
        return new Shift(Shift.Kind.AGGRESSION, f, null, amount);
    }

    private static Shift we(Faction f, long amount) {
        return new Shift(Shift.Kind.WEALTH, f, null, amount);
    }

    private static Shift te(FactionPair p, double amount) {
        return new Shift(Shift.Kind.TENSION, null, p, amount);
    }
}
