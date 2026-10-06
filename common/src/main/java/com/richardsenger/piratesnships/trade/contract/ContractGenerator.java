package com.richardsenger.piratesnships.trade.contract;

import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.trade.TradeRandom;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic contract offers for one port and day. Candidates are (good, destination) pairs where the origin
 * trades the good without demanding it and the destination demands it or trades it neutrally; produced-here and
 * demanded-there pairs are more likely. The reward is
 * <pre>
 *   ceil( quantity × basePrice × (rewardBase + priceDifferenceWeight × max(0, f_dest − f_origin))
 *         × (1 + distanceBonusPer1000 × distance / 1000) × (1 + riskBonus × risk) )
 * </pre>
 * with {@code f} the role price factor of {@link MarketParams}, so it grows with quantity, distance, price difference
 * and risk. Deadline = day + ceil(distance / blocksPerDay) + slackDays.
 */
public final class ContractGenerator {

    /** Another port as seen from the origin: plain data until the port registry exists. */
    public record Destination(ResourceLocation port, PortProfile profile, double distance, double risk) {
    }

    private record Candidate(ResourceLocation good, TradeGood def, GoodRole originRole, Destination dest, double weight) {
    }

    private ContractGenerator() {
    }

    public static List<DeliveryContract> generate(ResourceLocation origin, PortProfile originProfile, List<Destination> destinations,
                                                  Definitions<TradeGood> goods, long day, long seed,
                                                  ContractParams cp, MarketParams mp) {
        if (!cp.enabled() || cp.offersPerDay() <= 0) return List.of();
        List<ResourceLocation> ids = new ArrayList<>(goods.ids());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        List<Destination> dests = new ArrayList<>(destinations);
        dests.sort(Comparator.comparing(d -> d.port().toString()));

        List<Candidate> candidates = new ArrayList<>();
        for (ResourceLocation id : ids) {
            GoodRole from = originProfile.role(id);
            if (!from.traded() || from == GoodRole.DEMANDS) continue;
            for (Destination d : dests) {
                if (d.port().equals(origin) || !(d.distance() > 0)) continue;
                GoodRole to = d.profile().role(id);
                if (to != GoodRole.DEMANDS && to != GoodRole.NEUTRAL) continue;
                double w = (from == GoodRole.PRODUCES ? 2.0 : 1.0) * (to == GoodRole.DEMANDS ? 3.0 : 1.0);
                candidates.add(new Candidate(id, goods.require(id), from, d, w));
            }
        }

        TradeRandom.Stream rng = new TradeRandom.Stream(TradeRandom.mix(TradeRandom.mix(seed, origin.toString()), day));
        List<DeliveryContract> out = new ArrayList<>();
        while (out.size() < cp.offersPerDay() && !candidates.isEmpty()) {
            double total = 0;
            for (Candidate c : candidates) total += c.weight();
            double r = rng.nextDouble() * total;
            int pick = candidates.size() - 1;
            for (int i = 0; i < candidates.size(); i++) {
                r -= candidates.get(i).weight();
                if (r < 0) {
                    pick = i;
                    break;
                }
            }
            Candidate c = candidates.remove(pick);
            out.add(make(origin, originProfile, c, day, rng, cp, mp));
        }
        return List.copyOf(out);
    }

    private static DeliveryContract make(ResourceLocation origin, PortProfile originProfile, Candidate c, long day,
                                         TradeRandom.Stream rng, ContractParams cp, MarketParams mp) {
        int lo = Math.max(1, Math.min(cp.minQuantity(), cp.maxQuantity()));
        int hi = Math.max(lo, cp.maxQuantity());
        double raw = (lo + rng.nextDouble() * (hi - lo)) * c.def().stockFactor();
        int quantity = Math.max(8, (int) Math.round(raw / 8.0) * 8);
        GoodRole to = c.dest().profile().role(c.good());
        int reward = reward(quantity, c.def(), c.originRole(), to, c.dest().distance(), c.dest().risk(), cp, mp);
        long travel = (long) Math.ceil(c.dest().distance() / Math.max(1.0, cp.blocksPerDay()));
        long deadline = day + travel + Math.max(0, cp.slackDays());
        int deposit = (int) Math.ceil(reward * Math.max(0.0, cp.depositFraction()));
        UUID id = new UUID(rng.nextLong(), rng.nextLong());
        return new DeliveryContract(id, c.good(), quantity, origin, c.dest().port(), day, day + Math.max(0, cp.offerLifetimeDays()),
                deadline, reward, deposit, DeliveryContract.State.OFFERED, Optional.empty());
    }

    /** The reward formula from the class comment. */
    public static int reward(int quantity, TradeGood good, GoodRole from, GoodRole to, double distance, double risk,
                             ContractParams cp, MarketParams mp) {
        double priceDiff = Math.max(0.0, mp.roleFactor(to) - mp.roleFactor(from));
        double perUnit = cp.rewardBase() + cp.priceDifferenceWeight() * priceDiff;
        double distanceMult = 1.0 + cp.distanceBonusPer1000() * Math.max(0.0, distance) / 1000.0;
        double riskMult = 1.0 + cp.riskBonus() * Math.min(1.0, Math.max(0.0, risk));
        double value = quantity * good.basePrice() * perUnit * distanceMult * riskMult;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(value));
    }
}
