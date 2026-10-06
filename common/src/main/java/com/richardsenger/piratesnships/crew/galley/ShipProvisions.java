package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.ProvisionRules;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import com.richardsenger.piratesnships.crew.provisions.ProvisionUpdate;
import com.richardsenger.piratesnships.crew.provisions.ProvisioningState;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.crew.provisions.SuppliesLeft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A ship's provisions as one unit: all pantries and water barrels of a ship (design.md §7.4, §4.9). The entry point
 * the crew system calls once per interval. It does not find ships: the caller passes the container positions (the
 * ship integration supplies them) and keeps the crew's {@link ProvisioningState} in its own ship data.
 *
 * <pre>{@code
 * // crew system, every N ticks for each crewed ship:
 * ShipProvisions.Result r = ShipProvisions.advance(level, ship.provisionContainers(), ship.provisioning(),
 *         new CrewHeadcount(ship.crewCount(), ship.prisonerCount(), ship.rumRations()), ticksSinceLastCall);
 * ship.setProvisioning(r.state());                   // persist
 * crew.applyEffects(r.update().outcome().effects()); // morale, work speed, desertion, scurvy
 * // HUD: ShipProvisions.suppliesLeft(level, positions, ship.provisioning(), headcount).overallDays()
 * // §4.9: ShipProvisions.weight(level, positions)
 * }</pre>
 *
 * Positions that hold no pantry or barrel (destroyed, unloaded) are skipped. Duplicates count once.
 */
public final class ShipProvisions {

    private ShipProvisions() {
    }

    /** The update of the combined store and how many containers took part. */
    public record Result(ProvisionUpdate update, int pantries, int barrels) {
        public ProvisioningState state() {
            return update.state();
        }
    }

    /**
     * Feeds the crew for the {@code elapsedTicks} that ended now (real time). Each container's spoilage is lined up
     * with the start of the period first, so nothing ages twice when pantries also tick on their own.
     */
    public static Result advance(ServerLevel level, Collection<BlockPos> containers, ProvisioningState state,
                                 CrewHeadcount crew, long elapsedTicks) {
        long now = level.getGameTime();
        return run(level, containers, state, crew, Math.max(0, elapsedTicks), now - Math.max(0, elapsedTicks), now);
    }

    /**
     * Simulates {@code ticks} on top of now without real time passing (debug command): the provisions end up as if
     * that time had passed, and the containers' clocks stay at now.
     */
    public static Result simulate(ServerLevel level, Collection<BlockPos> containers, ProvisioningState state,
                                  CrewHeadcount crew, long ticks) {
        long now = level.getGameTime();
        return run(level, containers, state, crew, Math.max(0, ticks), now, now);
    }

    private static Result run(ServerLevel level, Collection<BlockPos> positions, ProvisioningState state,
                              CrewHeadcount crew, long ticks, long anchor, long now) {
        ProvisionSettings s = ProvisionsConfig.settings();
        List<ProvisionContainer> containers = containers(level, positions);
        List<ProvisionStore> stores = new ArrayList<>();
        for (ProvisionContainer c : containers) {
            stores.add(c.storeAt(anchor, s));
        }
        ProvisionUpdate u = ProvisionRules.advance(ProvisionPool.combine(stores), state, crew, s, ticks);
        List<ProvisionPool.Share> shares = ProvisionPool.split(stores, u.outcome());
        long aged = s.consumptionEnabled() && s.spoilageEnabled() ? ticks : 0;
        for (int i = 0; i < containers.size(); i++) {
            ProvisionStore remaining = ProvisionPool.remaining(stores.get(i), shares.get(i), aged);
            containers.get(i).applyShare(shares.get(i), remaining, now, s);
        }
        return new Result(u, count(containers, PantryBlockEntity.class), count(containers, WaterBarrelBlockEntity.class));
    }

    /** The combined store now (spoilage caught up). */
    public static ProvisionStore store(ServerLevel level, Collection<BlockPos> positions) {
        ProvisionSettings s = ProvisionsConfig.settings();
        List<ProvisionStore> stores = new ArrayList<>();
        for (ProvisionContainer c : containers(level, positions)) {
            stores.add(c.catchUp(level.getGameTime(), s));
        }
        return ProvisionPool.combine(stores);
    }

    /** Days of food, water and rum left for this headcount (for the HUD). */
    public static SuppliesLeft suppliesLeft(ServerLevel level, Collection<BlockPos> positions, ProvisioningState state, CrewHeadcount crew) {
        return ProvisionRules.suppliesLeft(store(level, positions), state, crew, ProvisionsConfig.settings());
    }

    /** Total cargo weight of the provisions (design.md §4.9). */
    public static double weight(ServerLevel level, Collection<BlockPos> positions) {
        return store(level, positions).totalWeight();
    }

    /** The provision containers at these positions, in the given order, each once. */
    public static List<ProvisionContainer> containers(ServerLevel level, Collection<BlockPos> positions) {
        List<ProvisionContainer> out = new ArrayList<>();
        for (BlockPos pos : new LinkedHashSet<>(positions)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ProvisionContainer c) {
                out.add(c);
            }
        }
        return out;
    }

    private static int count(List<ProvisionContainer> containers, Class<?> type) {
        return (int) containers.stream().filter(type::isInstance).count();
    }
}
