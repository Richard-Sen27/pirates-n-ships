package com.richardsenger.piratesnships.ship.cargo;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlockEntity;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipForces;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.cargo.CargoLoad;
import com.richardsenger.piratesnships.trade.cargo.CargoMass;
import com.richardsenger.piratesnships.trade.cargo.CargoWeighing;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import com.richardsenger.piratesnships.trade.good.TradeGoodIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Cargo weight on ships (CW1, design.md §4.9), the runtime part:
 * <ul>
 *   <li>every {@code cargo_trade.weigh_interval_ticks} each of our ships is weighed from its plot's block entities. Our
 *       containers (every block with {@link CargoLoad#LOAD}: cargo crate and barrel, pantry, water barrel) carry their
 *       weight as Sable mass already and are only counted; every other {@link Container} (chests, barrels, shulker
 *       boxes, hoppers, the sea chest) becomes a downward force at its position ({@link CargoForceRule}), recorded every
 *       physics substep in the {@code pirates_n_ships:cargo} force group;</li>
 *   <li>the total gives the ship's {@link CargoWeight.LoadLevel} (weight over {@code blocks × capacity_per_block}),
 *       shown by {@code /pirates ship info} and the ship screen (HUD4 took it off the helm overlay).</li>
 * </ul>
 * A sleeping body is not woken by a steady queued force (docs/sable-notes.md §9.0i), so a changed force wakes the ship
 * once through {@link ShipBody#addVelocity} with zero velocity.
 */
public final class ShipCargo {

    /** {@code /pirates ship info} line: "Load: %s (cargo %s of %s capacity)". */
    public static final String KEY_INFO = "commands." + Constants.MOD_ID + ".ship.info.load";

    /**
     * One weighing of a ship.
     *
     * @param ours       whether the ship is one of ours (assembled at a helm); others are never weighed
     * @param blocks     the ship's block count
     * @param ownWeight  weight in our containers [units] (already Sable mass)
     * @param vanilla    the vanilla containers with weight (force points)
     * @param params     the cargo config at weighing time
     * @param gravity    the level's gravity, world frame
     */
    public record Snapshot(long gameTime, boolean ours, int blocks, double ownWeight, List<CargoForceRule.Container> vanilla,
                           CargoWeight.Params params, Vector3dc gravity, @Nullable CargoWeight.LoadLevel level) {

        public double vanillaWeight() {
            return CargoForceRule.totalWeight(vanilla);
        }

        public double totalWeight() {
            return ownWeight + vanillaWeight();
        }

        public double capacity() {
            return TradeService.shipCapacity(blocks);
        }

        /** Whether this snapshot pulls on the ship. */
        boolean pulls() {
            return params.affectsShips() && params.weightFactor() > 0 && !vanilla.isEmpty();
        }
    }

    private static final Map<ServerLevel, Map<UUID, Snapshot>> SHIPS = new IdentityHashMap<>();

    private ShipCargo() {
    }

    // ------------------------------------------------------------------ setup (called by the trade module)

    public static void registerContent() {
        ShipForces.registerCargo();
    }

    public static void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(ShipCargo::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(s -> clear());
        SableShips.onPhysicsTick(ShipCargo::onPhysicsTick);
        SableShips.onShipRemoved((level, id, destroyed) -> {
            Map<UUID, Snapshot> m = SHIPS.get(level);
            if (m != null) {
                m.remove(id);
            }
        });
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY_INFO, "Load: %s (cargo %s of %s capacity)");
        lang.add(ShipForces.CARGO_KEY, "Cargo");
    }

    // ------------------------------------------------------------------ queries

    /** Weighs {@code ship} now and keeps the result (null-free; a ship that is not ours gives an empty snapshot). */
    public static Snapshot weighNow(ShipBody ship) {
        Snapshot s = weigh(ship);
        SHIPS.computeIfAbsent(ship.level(), l -> new HashMap<>()).put(ship.id(), s);
        return s;
    }

    /** The last weighing of {@code ship}, or null. */
    public static @Nullable Snapshot last(ServerLevel level, UUID ship) {
        Map<UUID, Snapshot> m = SHIPS.get(level);
        return m == null ? null : m.get(ship);
    }

    /** The {@code /pirates ship info} line for {@code ship} (weighed now). */
    public static Component infoLine(ShipBody ship) {
        Snapshot s = weighNow(ship);
        CargoWeight.LoadLevel level = s.level() == null ? CargoWeight.LoadLevel.LIGHT : s.level();
        return Component.translatable(KEY_INFO, Component.translatable(level.translationKey()),
                String.format(Locale.ROOT, "%.1f", s.totalWeight()), String.format(Locale.ROOT, "%.0f", s.capacity()));
    }

    // ------------------------------------------------------------------ weighing

    static boolean isOurs(ShipBody ship) {
        return ship.userData(ShipAssembler.USER_DATA_KEY).hasUUID("ship");
    }

    static Snapshot weigh(ShipBody ship) {
        ServerLevel level = ship.level();
        CargoWeight.Params params = TradeConfig.cargoParams();
        Vector3d gravity = ship.gravity();
        if (!isOurs(ship)) {
            return new Snapshot(level.getGameTime(), false, 0, 0, List.of(), params, gravity, null);
        }
        TradeGoodIndex goods = TradeService.goods(level);
        double own = 0;
        List<CargoForceRule.Container> vanilla = new ArrayList<>();
        for (BlockEntity be : ship.plotBlockEntities()) {
            if (be.isRemoved()) {
                continue;
            }
            if (be.getBlockState().hasProperty(CargoLoad.LOAD)) {
                own += ownWeight(be, goods);
            } else if (be instanceof Container) {
                double w = CargoWeighing.weigh(be, goods);
                if (w > 0) {
                    BlockPos p = be.getBlockPos();
                    vanilla.add(new CargoForceRule.Container(new Vector3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), w));
                }
            }
        }
        int blocks = ship.plotBlocks().size();
        double total = own + CargoForceRule.totalWeight(vanilla);
        return new Snapshot(level.getGameTime(), true, blocks, own, List.copyOf(vanilla), params, gravity,
                TradeService.shipLoadLevel(total, blocks));
    }

    /** Content weight of one of our containers [units]. */
    static double ownWeight(BlockEntity be, TradeGoodIndex goods) {
        if (be instanceof WaterBarrelBlockEntity barrel) {
            return barrel.rations() * ProvisionsConfig.settings().waterWeightPerRation();
        }
        return CargoWeighing.weigh(be, goods);
    }

    // ------------------------------------------------------------------ ticks

    static void onLevelTick(ServerLevel level) {
        long now = level.getGameTime();
        int interval = Math.max(1, TradeConfig.WEIGH_INTERVAL_TICKS.get());
        Map<UUID, Snapshot> m = SHIPS.computeIfAbsent(level, l -> new HashMap<>());
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved()) {
                continue;
            }
            Snapshot old = m.get(ship.id());
            if (old != null && now - old.gameTime() < interval) {
                continue;
            }
            Snapshot fresh = weigh(ship);
            m.put(ship.id(), fresh);
            boolean changed = old == null
                    ? fresh.pulls()
                    : old.pulls() != fresh.pulls() || Math.abs(old.vanillaWeight() - fresh.vanillaWeight()) > 1e-6
                            || !old.params().equals(fresh.params());
            if (changed) {
                // a sleeping body ignores a new steady queued force (docs/sable-notes.md §9.0i): wake it once
                ship.addVelocity(new Vector3d(), new Vector3d());
            }
        }
    }

    /** Physics substep: records the vanilla containers' weight as impulses in the cargo group. */
    static void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, Snapshot> m = SHIPS.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        Quaterniond orientation = new Quaterniond();
        Vector3d impulse = new Vector3d();
        for (Map.Entry<UUID, Snapshot> e : m.entrySet()) {
            Snapshot s = e.getValue();
            if (!s.pulls()) {
                continue;
            }
            ShipBody ship = SableShips.byId(level, e.getKey());
            if (ship == null || ship.isRemoved()) {
                continue;
            }
            ship.orientation(orientation);
            forEachForce(s, orientation, f -> ship.applyCargoImpulse(f.plotPoint(), impulse.set(f.localForce()).mul(timeStep)));
        }
    }

    static void forEachForce(Snapshot s, Quaterniond orientation, Consumer<CargoForceRule.PointForce> sink) {
        for (CargoForceRule.PointForce f : CargoForceRule.forces(s.vanilla(), s.params(), s.gravity(), orientation)) {
            sink.accept(f);
        }
    }

    static void clear() {
        SHIPS.clear();
    }

    /** Mass [kpg] that the vanilla containers of {@code s} amount to (for tests and the playtest notes). */
    public static double vanillaMass(Snapshot s) {
        return CargoMass.massOf(s.vanillaWeight(), s.params());
    }
}
