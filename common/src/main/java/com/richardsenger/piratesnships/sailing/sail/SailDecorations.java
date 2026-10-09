package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Dyeing sails and hanging banners on them (SAIL2, docs/design.md §4.8 "Sails: dyeable, and large sails can carry
 * banner patterns"), server side; the blocks call it from their use handlers ({@code YardBlock}, {@code CleatBlock}).
 * Works the same on land and in a ship's plot.
 *
 * <ul>
 *   <li><b>Dye on a yard:</b> any block of a yard that heads a square sail dyes the whole yard (every block keeps the
 *       colour, so it survives a lengthened or shortened yard, {@link #carryAlongRow}); the head's tint colours the
 *       cloth. Refused while a banner hangs on the sail (its base colour is the cloth's colour).</li>
 *   <li><b>Dye on a cleat:</b> the head cleat of a triangular sail keeps its dye.</li>
 *   <li><b>Banner:</b> on a yard heading a square sail that {@link SailBanner#eligible qualifies}; kept on the head.
 *       Stay sails take dye only. Taken back from any block of the yard ({@link #takeBanner}).</li>
 * </ul>
 * Washing a sail out is not offered: a white dye brings back the natural canvas.
 */
public final class SailDecorations {

    /** What a use did; {@link #key()} is its action bar message. */
    public enum Outcome {
        DYED, SAME_DYE, NO_SAIL, HAS_BANNER, HUNG, TOO_SMALL, ALREADY_BANNER, TAKEN, STAY_DYE_ONLY;

        public String key() {
            return "message." + Constants.MOD_ID + ".sail." + name().toLowerCase(Locale.ROOT);
        }

        /** Whether the item used is spent. */
        public boolean consumes() {
            return this == DYED || this == HUNG;
        }
    }

    private SailDecorations() {
    }

    /** The action bar message of {@code o}; {@code color} names the dye of {@link Outcome#DYED} and {@link Outcome#SAME_DYE}. */
    public static Component message(Outcome o, @Nullable DyeColor color) {
        return switch (o) {
            case DYED, SAME_DYE -> Component.translatable(o.key(),
                    Component.translatable("color.minecraft." + (color == null ? DyeColor.WHITE : color).getName()));
            case TOO_SMALL -> Component.translatable(o.key(), SailingConfig.SAIL_BANNER_MIN_WIDTH.get(), SailingConfig.SAIL_BANNER_MIN_DROP.get());
            default -> Component.translatable(o.key());
        };
    }

    /** Dyes the square sail headed by the yard containing {@code pos}. */
    public static Outcome dyeYard(Level level, BlockPos pos, DyeColor color) {
        SquareSail sail = YardSails.sailHeadedByYardAt(level, pos, SailingConfig.yardRules());
        if (sail == null) {
            return Outcome.NO_SAIL;
        }
        YardBlockEntity head = head(level, sail.upper());
        if (head == null) {
            return Outcome.NO_SAIL;
        }
        if (!head.banner().isEmpty()) {
            return Outcome.HAS_BANNER;
        }
        List<YardBlockEntity> row = entities(level, sail.upper());
        if (row.stream().allMatch(be -> be.dye() == color)) {
            return Outcome.SAME_DYE;
        }
        row.forEach(be -> be.setDye(color));
        return Outcome.DYED;
    }

    /** Hangs one of {@code banner} on the square sail headed by the yard containing {@code pos} (the caller spends it). */
    public static Outcome hangBanner(Level level, BlockPos pos, ItemStack banner) {
        SquareSail sail = YardSails.sailHeadedByYardAt(level, pos, SailingConfig.yardRules());
        YardBlockEntity head = sail == null ? null : head(level, sail.upper());
        if (head == null) {
            return Outcome.NO_SAIL;
        }
        if (!head.banner().isEmpty()) {
            return Outcome.ALREADY_BANNER;
        }
        if (!SailBanner.eligible(sail.geometry(), SailingConfig.SAIL_BANNER_MIN_WIDTH.get(), SailingConfig.SAIL_BANNER_MIN_DROP.get())) {
            return Outcome.TOO_SMALL;
        }
        head.setBanner(banner);
        return Outcome.HUNG;
    }

    /** Whether the yard containing {@code pos} keeps a banner (works on a client too: the banner is synced). */
    public static boolean hasBanner(BlockGetter level, BlockPos pos) {
        YardRow row = YardSails.row(level, pos, SailingConfig.yardRules());
        return row != null && entities(level, row).stream().anyMatch(be -> !be.banner().isEmpty());
    }

    /** Takes the banner off the yard containing {@code pos} (the head's first); empty when it keeps none. */
    public static ItemStack takeBanner(Level level, BlockPos pos) {
        YardRow row = YardSails.row(level, pos, SailingConfig.yardRules());
        if (row == null) {
            return ItemStack.EMPTY;
        }
        YardBlockEntity head = head(level, row);
        if (head != null && !head.banner().isEmpty()) {
            return head.takeBanner();
        }
        for (YardBlockEntity be : entities(level, row)) {
            if (!be.banner().isEmpty()) {
                return be.takeBanner();
            }
        }
        return ItemStack.EMPTY;
    }

    /** Dyes the triangular sail headed by the cleat at {@code pos}. */
    public static Outcome dyeStay(Level level, BlockPos pos, DyeColor color) {
        if (!(level.getBlockEntity(pos) instanceof CleatBlockEntity be) || be.cloth() == null) {
            return Outcome.NO_SAIL;
        }
        if (be.dye() == color) {
            return Outcome.SAME_DYE;
        }
        be.setDye(color);
        return Outcome.DYED;
    }

    /**
     * After a yard changed ({@link YardSails#refreshRow}): blocks without a dye take the yard's (the middle block's,
     * else the first dyed block's), and a banner left on a block that is no longer the middle moves to the middle, so a
     * yard that was lengthened or shortened keeps its colours.
     */
    public static void carryAlongRow(Level level, YardRow row) {
        List<YardBlockEntity> all = entities(level, row);
        YardBlockEntity middle = head(level, row);
        DyeColor dye = middle != null && middle.dye() != null ? middle.dye() : null;
        for (YardBlockEntity be : all) {
            if (dye == null && be.dye() != null) {
                dye = be.dye();
            }
        }
        if (dye != null) {
            for (YardBlockEntity be : all) {
                if (be.dye() == null) {
                    be.setDye(dye);
                }
            }
        }
        if (middle != null && middle.banner().isEmpty()) {
            for (YardBlockEntity be : all) {
                if (be != middle && !be.banner().isEmpty()) {
                    middle.setBanner(be.takeBanner());
                    break;
                }
            }
        }
    }

    private static @Nullable YardBlockEntity head(BlockGetter level, YardRow row) {
        return level.getBlockEntity(YardSails.headOf(row)) instanceof YardBlockEntity be ? be : null;
    }

    private static List<YardBlockEntity> entities(BlockGetter level, YardRow row) {
        List<YardBlockEntity> out = new ArrayList<>(row.length());
        for (int a = row.min(); a <= row.max(); a++) {
            if (level.getBlockEntity(new BlockPos(row.xAt(a), row.y(), row.zAt(a))) instanceof YardBlockEntity be) {
                out.add(be);
            }
        }
        return out;
    }
}
