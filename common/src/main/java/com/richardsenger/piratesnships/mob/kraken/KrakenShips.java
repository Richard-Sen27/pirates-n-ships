package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the kraken sees of ships, through our Sable wrappers ({@link SableShips}, {@link ShipBody}, {@link ShipEntities};
 * no Sable call of its own): the nearest ship, hull blocks to grip near the waterline, mast blocks and the people on
 * deck.
 */
final class KrakenShips {

    /** Least spacing in blocks between two grab spots. */
    static final double SPOT_SPACING = 1.5;
    /** Most grab spots offered at once. */
    static final int MAX_SPOTS = 6;

    private KrakenShips() {
    }

    /** Horizontal distance from {@code p} to the box (0 inside). */
    static double horizontalDistance(AABB box, Vec3 p) {
        double dx = Math.max(0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dz = Math.max(0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Distance from {@code p} to the box (0 inside). */
    static double distance(AABB box, Vec3 p) {
        double dx = Math.max(0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - p.y, p.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The ship whose bounds are horizontally nearest to {@code at} within {@code range}, or null. */
    static @Nullable ShipBody nearest(ServerLevel level, Vec3 at, double range) {
        ShipBody best = null;
        double bestD = range;
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved()) continue;
            double d = horizontalDistance(ship.worldBounds(), at);
            if (d <= bestD) {
                bestD = d;
                best = ship;
            }
        }
        return best;
    }

    /**
     * Up to {@link #MAX_SPOTS} hull blocks (plot positions) to grip: the blocks closest to the waterline
     * ({@code surfaceY}) and to the kraken ({@code from}), at least {@link #SPOT_SPACING} apart, within {@code reach}
     * of {@code from}.
     */
    static List<BlockPos> grabSpots(ServerLevel level, ShipBody ship, Vec3 from, double surfaceY, double reach) {
        record Scored(BlockPos plot, Vec3 world, double score) { }
        List<Scored> scored = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            Vec3 w = ship.toWorld(Vec3.atCenterOf(p));
            double d = w.distanceTo(from);
            if (d > reach) continue;
            scored.add(new Scored(p, w, Math.abs(w.y - surfaceY) * 2.0 + d * 0.3));
        }
        scored.sort(Comparator.comparingDouble(Scored::score));
        List<BlockPos> out = new ArrayList<>();
        List<Vec3> taken = new ArrayList<>();
        for (Scored s : scored) {
            if (out.size() >= MAX_SPOTS) break;
            boolean near = false;
            for (Vec3 t : taken) if (t.distanceTo(s.world()) < SPOT_SPACING) near = true;
            if (near) continue;
            out.add(s.plot());
            taken.add(s.world());
        }
        return out;
    }

    /** Mast blocks ({@code #pirates_n_ships:masts}, plot positions) of the ship within {@code reach} of {@code from}, nearest first. */
    static List<BlockPos> mastBlocks(ServerLevel level, ShipBody ship, Vec3 from, double reach) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            BlockState state = level.getBlockState(p);
            if (state.is(SailingBlocks.MASTS) && ship.toWorld(Vec3.atCenterOf(p)).distanceTo(from) <= reach) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(p -> ship.toWorld(Vec3.atCenterOf(p)).distanceTo(from)));
        return out;
    }

    /**
     * Living things standing on the ship's deck (not riding anything): Sable tracks them on the ship, or their feet are
     * inside the ship's bounds out of the water. Creative and spectator players and invulnerable entities are left out.
     */
    static List<LivingEntity> onDeck(ServerLevel level, ShipBody ship) {
        AABB bounds = ship.worldBounds();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bounds.inflate(0.5, 2.5, 0.5),
                e -> e.isAlive() && !(e instanceof Kraken) && !e.isPassenger())) {
            if (exempt(e)) continue;
            ShipBody standing = ShipEntities.standingOrRiding(e);
            boolean tracked = standing != null && standing.id().equals(ship.id());
            boolean inside = !e.isInWater() && bounds.inflate(0, 0.5, 0).contains(e.position());
            if (tracked || inside) out.add(e);
        }
        return out;
    }

    static boolean exempt(LivingEntity e) {
        return e.isInvulnerable() || e instanceof Player p && (p.isCreative() || p.isSpectator());
    }
}
