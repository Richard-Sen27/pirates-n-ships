package com.richardsenger.piratesnships.sailing.rope;

import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Ropes between anchors applied to a level (RP1, docs/design.md §5.2): the ropes stored in the anchors'
 * {@link RopeAnchorBlockEntity}, rigging one, and what happens when an anchor goes. A rope between two cleats that
 * passes the stay rule with a clew is a sail's stay ({@link TriangularSails}); every other rope is a decorative line.
 * Works the same on land and in a ship's plot (server side).
 */
public final class RopeLines {

    private RopeLines() {
    }

    /**
     * The other ends of the ropes of the anchor at {@code pos} whose anchor stores the rope back (a rope whose other end
     * was broken, moved without it or replaced does not count), in stored order.
     */
    public static List<BlockPos> partners(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RopeAnchorBlockEntity be) || !be.hasRopes()) {
            return Collections.emptyList();
        }
        List<BlockPos> out = new ArrayList<>(be.ropeCount());
        for (BlockPos t : be.ropeTargets()) {
            if (!t.equals(pos) && level.getBlockEntity(t) instanceof RopeAnchorBlockEntity other && other.hasRopeTo(pos)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Whether the anchors at {@code a} and {@code b} are roped together (both store the rope). */
    public static boolean joined(BlockGetter level, BlockPos a, BlockPos b) {
        return level.getBlockEntity(a) instanceof RopeAnchorBlockEntity ea && ea.hasRopeTo(b)
                && level.getBlockEntity(b) instanceof RopeAnchorBlockEntity eb && eb.hasRopeTo(a);
    }

    /**
     * Whether a rope has room at {@code pos}: the anchor holds fewer than {@link RopeAnchorBlockEntity#MAX_ROPES}
     * ropes once those whose other end no longer agrees are forgotten (done here, so a broken rope frees its slot).
     */
    public static boolean hasRoom(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RopeAnchorBlockEntity be)) {
            return false;
        }
        if (be.ropeCount() < RopeAnchorBlockEntity.MAX_ROPES) {
            return true;
        }
        List<BlockPos> live = partners(level, pos);
        for (BlockPos t : be.ropeTargets()) {
            if (!live.contains(t)) {
                be.removeRope(t);
            }
        }
        return be.ropeCount() < RopeAnchorBlockEntity.MAX_ROPES;
    }

    /**
     * Rigs a rope between the anchors at {@code a} and {@code b} (the caller checked the rule and the room) and brings
     * the sails at both ends up to date. False when an anchor has no block entity or no room.
     */
    public static boolean rig(ServerLevel level, BlockPos a, BlockPos b) {
        if (!(level.getBlockEntity(a) instanceof RopeAnchorBlockEntity ea) || !(level.getBlockEntity(b) instanceof RopeAnchorBlockEntity eb)) {
            return false;
        }
        boolean addedA = ea.hasRopeTo(b) || ea.addRope(b);
        boolean addedB = addedA && (eb.hasRopeTo(a) || eb.addRope(a));
        if (!addedB) {
            ea.removeRope(b);
            return false;
        }
        TriangularSails.refresh(level, a);
        TriangularSails.refresh(level, b);
        SailingRuntimes.onRigChanged(level, a); // no block changed: tell the ship's runtime
        return true;
    }

    /** A player breaking the anchor at {@code pos} gets one rope back per rope on it (not in creative mode). */
    public static void dropRopes(Level level, BlockPos pos, ItemStack rope) {
        int n = partners(level, pos).size();
        for (int i = 0; i < n; i++) {
            Block.popResource(level, pos, rope.copy());
        }
    }

    /**
     * After the anchor at {@code pos} with the rope partners {@code partners} (read before it went) was removed: the
     * other ends forget their ropes to it, and the sails around it and at the other ends are recomputed.
     */
    public static void onAnchorRemoved(ServerLevel level, BlockPos pos, List<BlockPos> partners) {
        for (BlockPos p : partners) {
            if (level.getBlockEntity(p) instanceof RopeAnchorBlockEntity other) {
                other.removeRope(pos);
            }
        }
        TriangularSails.refreshAround(level, pos);
        for (BlockPos p : partners) {
            TriangularSails.refresh(level, p);
        }
        if (!partners.isEmpty()) {
            SailingRuntimes.onRigChanged(level, partners.get(0));
        }
    }

    /**
     * Which end draws a rope (both store it, one draws it): the higher one, on a tie the one with the larger x, then
     * the larger z. A stay is drawn by its head, as before RP1.
     */
    public static boolean drawsFrom(BlockPos self, BlockPos other) {
        if (self.getY() != other.getY()) {
            return self.getY() > other.getY();
        }
        if (self.getX() != other.getX()) {
            return self.getX() > other.getX();
        }
        return self.getZ() > other.getZ();
    }

    /** Whether the anchor at {@code pos} is a cleat (stays run between cleats only). */
    public static boolean isCleat(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CleatBlock;
    }
}
