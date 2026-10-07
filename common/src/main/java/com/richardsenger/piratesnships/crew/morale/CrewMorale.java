package com.richardsenger.piratesnships.crew.morale;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.npc.CrewMember;

/**
 * Per-crew morale (HM1, docs/design.md §7.1): a value from 0 to 100 saved on the crew member, {@code crew.morale.start}
 * until something changes it. {@link #adjust} is <b>the one entry point</b> for every system that changes it:
 * <ul>
 *   <li>the hammock rule at dawn ({@code crew.hammock.CrewRest#dawn});</li>
 *   <li>provisions (§7.4): the morale deltas that {@code crew.provisions.ProvisionRules} computes into
 *       {@code ProvisionEffects}, to be applied to every crew member of the ship (not wired yet);</li>
 *   <li>wages (§7.3, CR2): unpaid wages; desertion and mutiny will read {@link #get}.</li>
 * </ul>
 * While {@code crew.morale.enabled} is off, morale reads as {@code start} and {@link #adjust} changes nothing.
 */
public final class CrewMorale {

    private CrewMorale() {
    }

    public static MoraleRules.Settings settings() {
        return new MoraleRules.Settings(CrewConfig.MORALE_ENABLED.get(), CrewConfig.MORALE_START.get(),
                CrewConfig.HAMMOCK_REST_PER_NIGHT.get(), CrewConfig.NO_HAMMOCK_PER_NIGHT.get());
    }

    public static boolean enabled() {
        return CrewConfig.MORALE_ENABLED.get();
    }

    /** The crew member's morale, 0 to 100. */
    public static int get(CrewMember crew) {
        return MoraleRules.effective(settings(), crew.storedMorale());
    }

    /**
     * Changes the crew member's morale by {@code delta} (capped at 100, floored at 0); {@code reason} goes to the debug
     * log. Returns the new morale. Does nothing while morale is disabled.
     */
    public static int adjust(CrewMember crew, int delta, String reason) {
        MoraleRules.Settings s = settings();
        int before = MoraleRules.effective(s, crew.storedMorale());
        if (!s.enabled()) {
            return before;
        }
        int after = MoraleRules.adjust(s, crew.storedMorale(), delta);
        crew.setStoredMorale(after);
        if (after != before) {
            Constants.LOG.debug("Morale of {} {} -> {} ({})", crew.getUUID(), before, after, reason);
        }
        return after;
    }
}
