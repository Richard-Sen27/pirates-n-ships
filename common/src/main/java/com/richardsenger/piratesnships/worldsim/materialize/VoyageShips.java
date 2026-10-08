package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoodIndex;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which ship belongs to which voyage (WS3b), in memory, plus the ship-side state the endings track. The saved half of
 * the link is the voyage record ({@code Voyage.shipId}, state MATERIALISED) and the sub-level user data
 * ({@link VoyageLink}); after a reload {@link Materializer} rebuilds this map from both. Also the cargo containers'
 * load and read-back. Server thread only.
 */
public final class VoyageShips {

    /** One materialised voyage. */
    static final class Active {
        final UUID voyage;
        UUID ship;
        ResourceKey<Level> dimension;
        long lastNear;
        long lastUpdate;
        int submerged;
        int capture;
        int lastUnits = -1;
        boolean plundered;
        Map<ResourceLocation, Integer> overflow = Map.of();

        Active(UUID voyage, UUID ship, ResourceKey<Level> dimension, long now) {
            this.voyage = voyage;
            this.ship = ship;
            this.dimension = dimension;
            this.lastNear = now;
            this.lastUpdate = now;
        }

        VoyageLink link() {
            return new VoyageLink(voyage, plundered, overflow);
        }
    }

    private static final Map<UUID, Active> BY_VOYAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BY_SHIP = new ConcurrentHashMap<>();

    private VoyageShips() {
    }

    /** The voyage the loaded or unloaded ship {@code shipId} belongs to, if it is a materialised voyage's ship. */
    public static Optional<UUID> voyageOf(UUID shipId) {
        return Optional.ofNullable(BY_SHIP.get(shipId));
    }

    /** The ship of a materialised voyage, if known. */
    public static Optional<UUID> shipOf(UUID voyage) {
        Active a = BY_VOYAGE.get(voyage);
        return a == null ? Optional.empty() : Optional.of(a.ship);
    }

    /** How many voyages are real ships now. */
    public static int count() {
        return BY_VOYAGE.size();
    }

    static Active get(UUID voyage) {
        return BY_VOYAGE.get(voyage);
    }

    static List<Active> all() {
        return new ArrayList<>(BY_VOYAGE.values());
    }

    static void put(Active a) {
        Active old = BY_VOYAGE.put(a.voyage, a);
        if (old != null) BY_SHIP.remove(old.ship, old.voyage);
        BY_SHIP.put(a.ship, a.voyage);
    }

    static void relink(Active a, UUID newShip) {
        BY_SHIP.remove(a.ship, a.voyage);
        a.ship = newShip;
        BY_SHIP.put(newShip, a.voyage);
    }

    static void remove(UUID voyage) {
        Active a = BY_VOYAGE.remove(voyage);
        if (a != null) BY_SHIP.remove(a.ship, voyage);
    }

    static void clear() {
        BY_VOYAGE.clear();
        BY_SHIP.clear();
    }

    // ------------------------------------------------------------------ user data

    static Optional<VoyageLink> readLink(ShipBody ship) {
        return VoyageLink.fromTag(ship.userData(VoyageLink.USER_DATA_KEY));
    }

    static void writeLink(ShipBody ship, VoyageLink link) {
        ship.setUserData(VoyageLink.USER_DATA_KEY, link.toTag());
    }

    /** Clears the link: the ship is not a voyage's any more (captured, sunk). */
    static void clearLink(ShipBody ship) {
        ship.setUserData(VoyageLink.USER_DATA_KEY, new CompoundTag());
    }

    // ------------------------------------------------------------------ cargo

    static List<CargoContainerBlockEntity> containers(ShipBody ship) {
        List<CargoContainerBlockEntity> out = new ArrayList<>();
        for (BlockEntity be : ship.plotBlockEntities()) {
            if (be instanceof CargoContainerBlockEntity c) out.add(c);
        }
        out.sort(java.util.Comparator.comparingLong(c -> c.getBlockPos().asLong()));
        return out;
    }

    /**
     * Fills the ship's cargo containers with {@code cargo} (units per good, each container one good), marked as plunder
     * when {@code markPlunder}. Returns what did not fit.
     */
    static Map<ResourceLocation, Integer> load(ServerLevel level, ShipBody ship, Map<ResourceLocation, Integer> cargo, boolean markPlunder) {
        Map<ResourceLocation, Integer> overflow = new LinkedHashMap<>();
        TradeGoodIndex goods = TradeService.goods(level);
        List<CargoContainerBlockEntity> containers = containers(ship);
        cargo.forEach((good, units) -> {
            if (units == null || units <= 0) return;
            Optional<TradeGood> def = goods.tradeable().get(good);
            Item item = def.map(d -> BuiltInRegistries.ITEM.get(d.item())).orElse(Items.AIR);
            if (item == Items.AIR) {
                overflow.merge(good, units, Integer::sum);
                return;
            }
            ItemStack kind = new ItemStack(item);
            if (markPlunder) PlunderMark.mark(kind);
            int left = units;
            for (CargoContainerBlockEntity c : containers) {
                if (left <= 0) break;
                int room = c.room(kind);
                if (room <= 0) continue;
                int n = Math.min(room, left);
                if (c.insertAll(kind, n)) left -= n;
            }
            if (left > 0) overflow.merge(good, left, Integer::sum);
        });
        return overflow;
    }

    /** Units of each trade good in the ship's cargo containers. */
    static Map<ResourceLocation, Integer> read(ServerLevel level, ShipBody ship) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        TradeGoodIndex goods = TradeService.goods(level);
        for (CargoContainerBlockEntity c : containers(ship)) {
            int n = c.count();
            if (n <= 0) continue;
            goods.of(c.heldKind()).ifPresent(e -> out.merge(e.id(), n, Integer::sum));
        }
        return out;
    }

    static int units(Map<ResourceLocation, Integer> cargo) {
        int n = 0;
        for (int v : cargo.values()) n += v;
        return n;
    }

    static Map<ResourceLocation, Integer> plus(Map<ResourceLocation, Integer> a, Map<ResourceLocation, Integer> b) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>(a);
        b.forEach((k, v) -> out.merge(k, v, Integer::sum));
        out.values().removeIf(v -> v <= 0);
        return out;
    }
}
