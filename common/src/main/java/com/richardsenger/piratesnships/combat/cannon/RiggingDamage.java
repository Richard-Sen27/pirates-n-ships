package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchor;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchorBlockEntity;
import com.richardsenger.piratesnships.sailing.rope.RopeLines;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.ClothTears;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.rigging.RatlinesBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * What chain shot does to rigging (CAN3, docs/design.md §5.2, §8.2). Two entry points, both server side:
 * <ul>
 *   <li>{@link #sweep}: each tick of its flight, the step of a chain shot is checked against the square sails (the drawn
 *       cloth of every yard head, {@link ClothTears#crossing}) and the ropes (stays and rope lines,
 *       {@link ShotRules#segmentDistance}) of the land around it and of every ship it passes, except the ship it was
 *       fired from. The cloth does not stop the shot; it tears the cloth cells within {@code cannons.chain_shot.cloth_radius}
 *       of where it passed through, cuts the ropes within that distance of its path and breaks the ratlines around the
 *       hole.</li>
 *   <li>{@link #strike}: where it strikes a block, it tears the cloth, cuts the ropes and breaks the ratlines within the
 *       radius of the hit point. It breaks no other block, so a hull is never breached.</li>
 * </ul>
 * A cut rope is gone from both its ends (a stay's triangular sail goes with it, through the existing stay rules); torn
 * cloth is kept on the sail's head ({@code YardBlockEntity#setTears}), so the sail draws less through the sailing runtime
 * and shows its holes. Positions are in one frame per call: world coordinates on land, plot coordinates on a ship.
 */
final class RiggingDamage {

    /** Half thickness of the slab a square sail's cloth counts as around its yard's axis (it bellies up to ~1.1 off it). */
    static final double CLOTH_SLAB = 1.2;
    /** How far a yard head may lie from the shot's path and still have cloth on it: half the longest yard, plus the drop. */
    private static final double SAIL_REACH = 12.0;

    private RiggingDamage() {
    }

    /** What one call did, for sounds and tests. */
    record Result(int cells, int ropes, int ratlines) {
        static final Result NONE = new Result(0, 0, 0);

        boolean any() {
            return cells + ropes + ratlines > 0;
        }

        Result plus(Result o) {
            return new Result(cells + o.cells, ropes + o.ropes, ratlines + o.ratlines);
        }
    }

    // ---- entry points ------------------------------------------------------------------------------------------------

    /**
     * The step of a chain shot from {@code a} to {@code b} (world) through the rigging of the land and of every ship but
     * {@code firingShip}. {@code mayBreak} decides whether a ratlines block (frame position, its ship or null) may go.
     */
    static Result sweep(ServerLevel level, @Nullable java.util.UUID firingShip, Vec3 a, Vec3 b, double radius,
                        BiPredicate<BlockPos, ShipBody> mayBreak) {
        Result r = sweepFrame(level, null, a, b, radius, mayBreak, worldBlockEntities(level, a, b));
        AABB step = new AABB(a, b);
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved() || ship.id().equals(firingShip)) continue;
            if (!CrewStations.worldBox(ship, radius + 1.0).intersects(step)) continue;
            r = r.plus(sweepFrame(level, ship, ship.toPlot(a), ship.toPlot(b), radius, mayBreak, ship.plotBlockEntities()));
        }
        return r;
    }

    /** A chain shot striking a block at {@code at} (frame coordinates; {@code ship} the struck ship or null on land). */
    static Result strike(ServerLevel level, @Nullable ShipBody ship, Vec3 at, double radius,
                         BiPredicate<BlockPos, ShipBody> mayBreak) {
        List<BlockEntity> near = ship != null ? ship.plotBlockEntities() : worldBlockEntities(level, at, at);
        Result r = Result.NONE;
        for (BlockEntity be : near) {
            if (be instanceof YardBlockEntity yard && yard.geometry() != null) {
                r = r.plus(tearAround(level, ship, yard, at, radius));
            }
        }
        r = r.plus(cutRopes(level, ship, near, at, at, radius));
        return r.plus(breakRatlines(level, ship, at, radius, mayBreak));
    }

    // ---- one frame ---------------------------------------------------------------------------------------------------

    private static Result sweepFrame(ServerLevel level, @Nullable ShipBody ship, Vec3 a, Vec3 b, double radius,
                                     BiPredicate<BlockPos, ShipBody> mayBreak, List<BlockEntity> near) {
        Result r = Result.NONE;
        AABB reach = new AABB(a, b).inflate(SAIL_REACH);
        for (BlockEntity be : near) {
            if (!(be instanceof YardBlockEntity yard) || yard.geometry() == null) continue;
            BlockPos p = yard.getBlockPos();
            if (!reach.contains(Vec3.atCenterOf(p))) continue;
            ClothGeometry g = yard.geometry();
            double bottom = bottom(level, p, g);
            double[] hit = ClothTears.crossing(g, bottom, cloth(g, p, a), cloth(g, p, b), CLOTH_SLAB);
            if (hit == null) continue;
            Result torn = tear(level, ship, yard, g, hit[0], hit[1], radius);
            Vec3 through = point(g, p, hit[0], hit[1]);
            r = r.plus(torn).plus(breakRatlines(level, ship, through, radius, mayBreak));
        }
        return r.plus(cutRopes(level, ship, near, a, b, radius));
    }

    /** How far down the cloth of the sail headed at {@code head} is drawn now [blocks]: its trim times the drop. */
    private static double bottom(ServerLevel level, BlockPos head, ClothGeometry g) {
        BlockState s = level.getBlockState(head);
        return s.getBlock() instanceof YardBlock ? SquareSail.drawnFraction(s.getValue(YardBlock.TRIM)) * g.drop() : 0.0;
    }

    /** A frame point in the cloth coordinates of the sail headed at {@code head}: {u, across, v}. */
    private static double[] cloth(ClothGeometry g, BlockPos head, Vec3 p) {
        double dx = p.x - (head.getX() + 0.5), dy = p.y - (head.getY() + 0.5), dz = p.z - (head.getZ() + 0.5);
        return g.alongX() ? new double[]{dx, dz, -dy} : new double[]{dz, dx, -dy};
    }

    /** The frame point of the cloth point (u, v) of the sail headed at {@code head}. */
    private static Vec3 point(ClothGeometry g, BlockPos head, double u, double v) {
        Vec3 c = Vec3.atCenterOf(head);
        return g.alongX() ? c.add(u, -v, 0) : c.add(0, -v, u);
    }

    /** The cloth within {@code radius} of the frame point {@code at}, if the drawn cloth comes that close. */
    private static Result tearAround(ServerLevel level, @Nullable ShipBody ship, YardBlockEntity yard, Vec3 at, double radius) {
        ClothGeometry g = yard.geometry();
        if (g == null) return Result.NONE;
        double[] c = cloth(g, yard.getBlockPos(), at);
        double across = Math.abs(c[1]);
        if (across >= radius || c[2] < -radius || c[2] > bottom(level, yard.getBlockPos(), g) + radius) return Result.NONE;
        double inPlane = Math.sqrt(radius * radius - across * across);
        return tear(level, ship, yard, g, c[0], Math.min(c[2], bottom(level, yard.getBlockPos(), g)), inPlane);
    }

    private static Result tear(ServerLevel level, @Nullable ShipBody ship, YardBlockEntity yard, ClothGeometry g,
                               double u, double v, double radius) {
        if (bottom(level, yard.getBlockPos(), g) <= 0.0) return Result.NONE; // furled: the bundle is not hit
        int[] cells = ClothTears.cellsWithin(g, u, v, radius);
        ClothTears before = yard.tears();
        ClothTears after = before.with(cells);
        int added = after.size() - before.size();
        if (added <= 0) return Result.NONE;
        yard.setTears(after);
        Vec3 world = world(ship, point(g, yard.getBlockPos(), u, v));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_WOOL.defaultBlockState()),
                world.x, world.y, world.z, 30, 0.6, 0.6, 0.6, 0.1);
        level.playSound(null, world.x, world.y, world.z, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 2.0f, 0.7f);
        return new Result(added, 0, 0);
    }

    /** Cuts every rope (stay or rope line) whose straight line passes within {@code radius} of the segment a–b. */
    private static Result cutRopes(ServerLevel level, @Nullable ShipBody ship, List<BlockEntity> near, Vec3 a, Vec3 b,
                                   double radius) {
        AABB reach = new AABB(a, b).inflate(radius + 17.0); // a rope is at most 16 blocks long (stay_max_length)
        List<BlockPos[]> cut = new ArrayList<>();
        for (BlockEntity be : near) {
            if (!(be instanceof RopeAnchorBlockEntity anchor) || !anchor.hasRopes()) continue;
            BlockPos self = anchor.getBlockPos();
            if (!reach.contains(Vec3.atCenterOf(self))) continue;
            Vec3 p = RopeAnchor.point(level, self);
            for (BlockPos other : RopeLines.partners(level, self)) {
                if (!RopeLines.drawsFrom(self, other)) continue; // each rope once, from the end that draws it
                if (ShotRules.segmentDistance(a, b, p, RopeAnchor.point(level, other)) <= radius) cut.add(new BlockPos[]{self, other});
            }
        }
        for (BlockPos[] rope : cut) {
            cut(level, rope[0], rope[1]);
            Vec3 mid = world(ship, RopeAnchor.point(level, rope[0]).add(RopeAnchor.point(level, rope[1])).scale(0.5));
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1.5f, 0.8f);
        }
        return new Result(0, cut.size(), 0);
    }

    /** The rope between the anchors at {@code a} and {@code b} is gone from both; the sails at both ends are relinked. */
    static void cut(ServerLevel level, BlockPos a, BlockPos b) {
        if (level.getBlockEntity(a) instanceof RopeAnchorBlockEntity ea) ea.removeRope(b);
        if (level.getBlockEntity(b) instanceof RopeAnchorBlockEntity eb) eb.removeRope(a);
        TriangularSails.refresh(level, a);
        TriangularSails.refresh(level, b);
        SailingRuntimes.onRigChanged(level, a); // no block changed: tell the ship's runtime
    }

    /** Breaks the ratlines blocks whose centres lie within {@code radius} of the frame point {@code at}. */
    private static Result breakRatlines(ServerLevel level, @Nullable ShipBody ship, Vec3 at, double radius,
                                        BiPredicate<BlockPos, ShipBody> mayBreak) {
        int n = 0;
        int reach = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-reach, -reach, -reach), c.offset(reach, reach, reach))) {
            if (Vec3.atCenterOf(p).distanceToSqr(at) > radius * radius) continue;
            if (!(level.getBlockState(p).getBlock() instanceof RatlinesBlock)) continue;
            if (!mayBreak.test(p, ship)) continue;
            level.destroyBlock(p.immutable(), CannonConfig.DESTROYED_BLOCKS_DROP.get());
            n++;
        }
        return new Result(0, 0, n);
    }

    private static Vec3 world(@Nullable ShipBody ship, Vec3 framePoint) {
        return ship == null ? framePoint : ship.toWorld(framePoint);
    }

    /** Block entities of the loaded world chunks a step from {@code a} to {@code b} (world) may have rigging in. */
    private static List<BlockEntity> worldBlockEntities(ServerLevel level, Vec3 a, Vec3 b) {
        List<BlockEntity> out = new ArrayList<>();
        AABB box = new AABB(a, b).inflate(SAIL_REACH + 5.0);
        int x0 = ((int) Math.floor(box.minX)) >> 4, x1 = ((int) Math.floor(box.maxX)) >> 4;
        int z0 = ((int) Math.floor(box.minZ)) >> 4, z1 = ((int) Math.floor(box.maxZ)) >> 4;
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 64) return out; // a step this long is no shot
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) out.addAll(chunk.getBlockEntities().values());
            }
        }
        return out;
    }
}
