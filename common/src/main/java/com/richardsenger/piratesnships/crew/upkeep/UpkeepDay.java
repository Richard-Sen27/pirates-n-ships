package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.crew.morale.MoraleRules;
import com.richardsenger.piratesnships.crew.provisions.ProvisionEffects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One ship's day of crew upkeep at dawn (CR2, docs/design.md §7.3, §7.4), as pure arithmetic over the crew's morale.
 * The steps run in this order, each one on the morale the step before left:
 * <ol>
 *   <li><b>Provisions</b>: every member gets the provisions' morale change ({@link #provisionsDelta}).</li>
 *   <li><b>Wages</b>: {@code payable} members are paid ({@link WageRules}), lowest morale first (ties in crew order);
 *       paid members gain {@code paid_per_day}, the others lose {@code unpaid_per_day}. Nothing with wages off.</li>
 *   <li><b>Mutiny</b> (when enabled): the ship's average morale below {@code mutiny_below} adds a low day to the
 *       ship's counter, anything else resets it; at {@code mutiny_days} the whole crew mutinies.</li>
 *   <li><b>Desertion</b> (when enabled): a member below {@code desert_below} adds a low day to its own counter,
 *       anything else resets it; at {@code desert_days} it deserts. While mutiny is enabled and the average is below
 *       {@code mutiny_below}, nobody deserts (they stay aboard and plot; the counters still run), and on a mutiny day
 *       nobody deserts either.</li>
 * </ol>
 * The hammock rule of HM1 settles the night after all of this (in {@code crew.hammock.CrewRest}).
 */
public final class UpkeepDay {

    private UpkeepDay() {
    }

    /** Whether a member was paid this day. */
    public enum Pay { NONE, PAID, UNPAID }

    /** A crew member before the day: stored morale ({@link MoraleRules#UNSET} reads as start) and its low-morale days. */
    public record Member(UUID id, int storedMorale, int lowDays) {
        public Member {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * A crew member after the day: the morale changes to apply in order (provisions, then wages), the morale after
     * them, its pay, its new low-morale counter and whether it deserts.
     */
    public record MemberResult(UUID id, int provisionsDelta, int wageDelta, Pay pay, int moraleAfter, int lowDays, boolean deserts) {
    }

    /** The whole crew after the day. */
    public record Result(List<MemberResult> members, int paid, int unpaid, double averageMorale, int shipLowDays, boolean mutiny) {
        public Result {
            members = List.copyOf(members);
        }

        public long deserters() {
            return members.stream().filter(MemberResult::deserts).count();
        }
    }

    /** The provisions' morale change of one day as whole morale points. */
    public static int provisionsDelta(ProvisionEffects effects) {
        return (int) Math.round(effects.moraleChange());
    }

    /**
     * Settles one day. {@code payable}: how many members the coins aboard pay ({@link WageRules.Payment#paid()});
     * ignored with wages off. {@code shipLowDays}: the ship's mutiny counter before the day.
     */
    public static Result settle(MoraleRules.Settings morale, UpkeepSettings s, List<Member> crew, int provisionsDelta,
                                int payable, int shipLowDays) {
        int n = crew.size();
        int[] after = new int[n];
        for (int i = 0; i < n; i++) {
            after[i] = MoraleRules.adjust(morale, crew.get(i).storedMorale(), provisionsDelta);
        }

        // wages: lowest morale first, ties in crew order
        Pay[] pay = new Pay[n];
        int[] wageDelta = new int[n];
        java.util.Arrays.fill(pay, Pay.NONE);
        int paid = 0, unpaid = 0;
        if (s.wagesEnabled()) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) order.add(i);
            order.sort(Comparator.comparingInt(i -> after[i]));
            int left = Math.max(0, payable);
            for (int i : order) {
                if (left > 0) {
                    left--;
                    paid++;
                    pay[i] = Pay.PAID;
                    wageDelta[i] = s.paidPerDay();
                } else {
                    unpaid++;
                    pay[i] = Pay.UNPAID;
                    wageDelta[i] = -s.unpaidPerDay();
                }
                after[i] = MoraleRules.adjust(morale, after[i], wageDelta[i]);
            }
        }

        double avg = 0;
        for (int m : after) avg += m;
        avg = n == 0 ? 0 : avg / n;

        boolean shipLow = s.mutinyEnabled() && n > 0 && avg < s.mutinyBelow();
        int newShipDays = shipLow ? shipLowDays + 1 : 0;
        boolean mutiny = shipLow && newShipDays >= s.mutinyDays();

        List<MemberResult> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Member m = crew.get(i);
            int low = s.desertionEnabled() && after[i] < s.desertBelow() ? m.lowDays() + 1 : 0;
            boolean deserts = s.desertionEnabled() && !shipLow && !mutiny && low >= s.desertDays();
            out.add(new MemberResult(m.id(), provisionsDelta, wageDelta[i], pay[i], after[i], low, deserts));
        }
        return new Result(out, paid, unpaid, avg, mutiny ? 0 : newShipDays, mutiny);
    }
}
