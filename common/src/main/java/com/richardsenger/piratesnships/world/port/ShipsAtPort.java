package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The ships moored at a port (CRW1, docs/design.md §7.5): loaded ships of an owner whose world bounds
 * ({@code ShipBody#worldBounds}) touch the port's box inflated by a radius. Used by hiring (the recruit goes aboard the
 * player's ship at the port); later by desertion at a port (CRW2).
 */
public final class ShipsAtPort {

    private ShipsAtPort() {
    }

    /**
     * The loaded ships owned by {@code owner} ({@link ShipRegistry}) in the port's dimension that touch the port's box
     * inflated by {@code radius}, nearest {@code near} first (by the distance to their bounds' centre). Empty when
     * {@code level} is not the port's dimension.
     */
    public static List<ShipBody> of(ServerLevel level, UUID owner, Port port, double radius, Vec3 near) {
        if (!level.dimension().equals(port.dimension())) return List.of();
        AABB area = AABB.of(port.box()).inflate(Math.max(0.0, radius));
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        List<ShipBody> out = new ArrayList<>();
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved()) continue;
            Optional<UUID> shipOwner = registry.find(ship.id()).flatMap(ShipData::owner);
            if (shipOwner.isEmpty() || !shipOwner.get().equals(owner)) continue;
            if (ship.worldBounds().intersects(area)) out.add(ship);
        }
        out.sort(Comparator.comparingDouble(s -> s.worldBounds().getCenter().distanceToSqr(near)));
        return out;
    }

    /** The nearest of {@link #of}, if any. */
    public static Optional<ShipBody> nearest(ServerLevel level, UUID owner, Port port, double radius, Vec3 near) {
        List<ShipBody> all = of(level, owner, port, radius, near);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }
}
