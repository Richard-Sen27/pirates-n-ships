package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Entities and ships (sable-notes §6). Read-only lookups; the station seat itself needs no Sable call beyond its
 * entity-type tags. Backed by {@code ActiveSableCompanion.java} and {@code api/entity/EntitySubLevelUtil.java}.
 */
public final class ShipEntities {

    private ShipEntities() {
    }

    /** The ship whose plot contains the entity (a retained plot entity, e.g. a station seat). {@code getContaining(Entity)}, l.536. */
    public static @Nullable ShipBody containing(Entity entity) {
        return wrap(Sable.HELPER.getContaining(entity));
    }

    /** The ship whose plot contains the entity's vehicle. {@code getVehicleSubLevel}, l.460. */
    public static @Nullable ShipBody vehicleShip(Entity entity) {
        return wrap(Sable.HELPER.getVehicleSubLevel(entity));
    }

    /** The ship the entity stands on (tracking) or rides in. {@code getTrackingOrVehicleSubLevel}, l.449. */
    public static @Nullable ShipBody standingOrRiding(Entity entity) {
        return wrap(Sable.HELPER.getTrackingOrVehicleSubLevel(entity));
    }

    /** True when Sable keeps this entity type inside plots ({@code #sable:retain_in_sub_level}, {@code shouldKick} l.87). */
    public static boolean retainedInPlot(Entity entity) {
        return !EntitySubLevelUtil.shouldKick(entity);
    }

    private static @Nullable ShipBody wrap(@Nullable SubLevel sub) {
        return sub instanceof ServerSubLevel s && !s.isRemoved() ? new ShipBody(s) : null;
    }
}
