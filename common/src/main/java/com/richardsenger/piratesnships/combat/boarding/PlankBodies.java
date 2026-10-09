package com.richardsenger.piratesnships.combat.boarding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a plank run can start on, lie in and land on (BRD2): the world or a ship, on the server and on the client alike
 * (the placement preview runs the same rule from the client's view). A ship is a Sable sub-level read through calls that
 * exist on both sides:
 * <ul>
 *   <li>{@code ActiveSableCompanion#getContaining(Level, Vec3i)} (l.92) for the ship whose plot holds a position;</li>
 *   <li>{@code api/sublevel/SubLevelContainer#getContainer(Level)} (l.92) and {@code #getAllSubLevels} (l.445);</li>
 *   <li>{@code sublevel/SubLevel#logicalPose} (l.151; the client's {@code ClientSubLevel#tick} keeps it current),
 *       {@code #boundingBox} (l.167), {@code #getPlot} (l.174) with {@code LevelPlot#contains(double, double)},
 *       {@code #isRemoved} (l.192), {@code #getUniqueId} (l.210);</li>
 *   <li>companion {@code Pose3dc#transformPosition}, {@code #transformPositionInverse} and {@code #orientation}, as
 *       {@code ship.sable.ShipBody} uses them.</li>
 * </ul>
 */
public final class PlankBodies {

    private PlankBodies() {
    }

    /** The world (identity frame) or one ship (its plot frame). Read right away; do not keep across ticks. */
    public static final class Body {
        public static final Body WORLD = new Body(null);

        private final @Nullable SubLevel sub;

        private Body(@Nullable SubLevel sub) {
            this.sub = sub;
        }

        public boolean isWorld() {
            return sub == null;
        }

        /** The ship's id, null for the world. */
        public @Nullable UUID id() {
            return sub == null ? null : sub.getUniqueId();
        }

        public boolean same(Body other) {
            return sub == null ? other.sub == null : other.sub != null && sub.getUniqueId().equals(other.sub.getUniqueId());
        }

        /** Frame position to world position. */
        public Vec3 toWorld(Vec3 local) {
            return sub == null ? local : sub.logicalPose().transformPosition(local);
        }

        /** World position to frame position. */
        public Vec3 toLocal(Vec3 world) {
            return sub == null ? world : sub.logicalPose().transformPositionInverse(world);
        }

        /** The frame's horizontal axis nearest to the world direction {@code dir}. */
        public Direction toLocal(Direction dir) {
            if (sub == null) {
                return dir;
            }
            Vector3d v = sub.logicalPose().orientation().transformInverse(new Vector3d(dir.getStepX(), 0, dir.getStepZ()));
            Direction d = Direction.getNearest((float) v.x, 0.0f, (float) v.z);
            return d.getAxis().isHorizontal() ? d : dir;
        }

        /** The frame position is inside this ship's plot (always true for the world). */
        public boolean contains(BlockPos local) {
            return sub == null || sub.getPlot().contains(local.getX() + 0.5, local.getZ() + 0.5);
        }

        /** World bounds of the ship; an infinite box for the world. */
        public AABB worldBounds() {
            if (sub == null) {
                return new AABB(-3.0e7, -1.0e4, -3.0e7, 3.0e7, 1.0e4, 3.0e7);
            }
            BoundingBox3dc b = sub.boundingBox();
            return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        }

        @Override
        public String toString() {
            return sub == null ? "world" : "ship " + sub.getUniqueId();
        }
    }

    /** The ship whose plot holds {@code pos}, else the world. */
    public static Body at(Level level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        return sub != null && !sub.isRemoved() ? new Body(sub) : Body.WORLD;
    }

    /** Every loaded ship of the level whose world bounds, grown by {@code margin}, touch {@code area}. */
    public static List<Body> shipsNear(Level level, AABB area, double margin) {
        List<Body> out = new ArrayList<>();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return out;
        }
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub.isRemoved()) continue;
            Body b = new Body(sub);
            if (b.worldBounds().inflate(margin).intersects(area)) {
                out.add(b);
            }
        }
        return out;
    }
}
