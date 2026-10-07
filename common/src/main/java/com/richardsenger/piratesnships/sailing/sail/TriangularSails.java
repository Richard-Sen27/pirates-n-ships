package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The triangular sail rule ({@link StayLinker}) applied to a level: the block lookup, the stays stored in the cleats'
 * block entities, the cloth of the head cleats, and setting a sail's trim. Works the same on land and in a ship's plot
 * (server side); on a ship, the sailing runtime is told when a stay is rigged.
 */
public final class TriangularSails {

    private TriangularSails() {
    }

    /** The rule's view of {@code level}: cleats, air, mast blocks ({@link SailingBlocks#MASTS}), anything else. */
    public static CleatLookup lookup(BlockGetter level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return (x, y, z) -> cell(level.getBlockState(m.set(x, y, z)));
    }

    public static CleatLookup.Cell cell(BlockState s) {
        if (s.getBlock() instanceof CleatBlock) {
            return CleatLookup.Cell.CLEAT;
        }
        if (s.isAir()) {
            return CleatLookup.Cell.AIR;
        }
        return s.is(SailingBlocks.MASTS) ? CleatLookup.Cell.MAST : CleatLookup.Cell.OTHER;
    }

    public static BlockPoint point(BlockPos p) {
        return new BlockPoint(p.getX(), p.getY(), p.getZ());
    }

    public static BlockPos pos(BlockPoint p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }

    /**
     * The other end of the stay of the cleat at {@code pos}, or null: only when that cleat's stay points back here (a
     * stay whose other end was broken, moved without it or replaced does not count).
     */
    public static @Nullable BlockPos partner(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CleatBlockEntity be)) {
            return null;
        }
        BlockPos target = be.stayTarget();
        if (target == null || target.equals(pos) || !(level.getBlockEntity(target) instanceof CleatBlockEntity other)) {
            return null;
        }
        return pos.equals(other.stayTarget()) ? target : null;
    }

    /** The sail headed by the cleat at {@code pos} (it must be the higher end of a valid stay), or null. */
    public static @Nullable TriangularSail sailHeadedAt(BlockGetter level, BlockPos pos, StayRules rules) {
        BlockPos partner = partner(level, pos);
        if (partner == null || partner.getY() >= pos.getY()) {
            return null;
        }
        return StayLinker.sail(lookup(level), point(pos), point(partner), rules);
    }

    /** Whether the cleat at {@code pos} is the higher end of a valid stay (it draws the rope). */
    public static boolean isStayHead(BlockGetter level, BlockPos pos) {
        BlockPos partner = partner(level, pos);
        return partner != null && partner.getY() < pos.getY();
    }

    /** Writes the cloth of the cleat at {@code pos}: its sail's if it heads one, else none. */
    public static void refresh(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CleatBlockEntity be) {
            TriangularSail s = sailHeadedAt(level, pos, SailingConfig.stayRules());
            be.setCloth(s == null ? null : s.geometry());
        }
    }

    /**
     * After a cleat at {@code pos} was placed or removed: recomputes the cloth of the cleat itself, of the other end of
     * its stay, and of every cleat above it in its column within the longest stay (whose clew it may be).
     */
    public static void refreshAround(ServerLevel level, BlockPos pos) {
        refresh(level, pos);
        BlockPos partner = partner(level, pos);
        if (partner != null) {
            refresh(level, partner);
        }
        int reach = SailingConfig.stayRules().maxLength();
        for (int dy = 1; dy <= reach; dy++) {
            BlockPos p = pos.above(dy);
            if (level.getBlockState(p).getBlock() instanceof CleatBlock) {
                refresh(level, p);
            }
        }
    }

    /** Rigs a stay between the cleats at {@code a} and {@code b} (the caller checked the rule), dropping their old stays. */
    public static void rig(ServerLevel level, BlockPos a, BlockPos b) {
        for (BlockPos p : new BlockPos[] {a, b}) {
            BlockPos old = partner(level, p);
            if (old != null && level.getBlockEntity(old) instanceof CleatBlockEntity o) {
                o.setStay(null);
                refresh(level, old);
            }
        }
        if (level.getBlockEntity(a) instanceof CleatBlockEntity ea && level.getBlockEntity(b) instanceof CleatBlockEntity eb) {
            ea.setStay(b);
            eb.setStay(a);
        }
        refresh(level, a);
        refresh(level, b);
        SailingRuntimes.onRigChanged(level, a); // no block changed: tell the ship's runtime
    }

    /**
     * Sets {@code trim} on the cleat at {@code pos} (the head's block state is the sail's trim). Returns false when
     * there is no cleat at {@code pos}.
     */
    public static boolean setTrim(Level level, BlockPos pos, SailTrim trim) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CleatBlock)) {
            return false;
        }
        if (s.getValue(CleatBlock.TRIM) != trim) {
            level.setBlock(pos, s.setValue(CleatBlock.TRIM, trim), Block.UPDATE_ALL);
        }
        return true;
    }

    /** Cycles the trim of the sail headed by the cleat at {@code pos}; returns the new trim, or null when it heads none. */
    public static @Nullable SailTrim cycle(ServerLevel level, BlockPos pos) {
        if (sailHeadedAt(level, pos, SailingConfig.stayRules()) == null) {
            return null;
        }
        SailTrim next = level.getBlockState(pos).getValue(CleatBlock.TRIM).next();
        setTrim(level, pos, next);
        return next;
    }
}
