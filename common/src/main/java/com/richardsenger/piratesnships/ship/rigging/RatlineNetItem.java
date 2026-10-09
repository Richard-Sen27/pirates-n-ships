package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.text.Num;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Check;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ratline net (RL2): a rolled net of string and sticks. Used on a fitting (a chainplate or a mast hoop) it ties
 * that end as A ({@code net_start}, with the dimension; the item glints); used on a second fitting it strings the net
 * from A to B ({@link RatlineNets#check}, then {@link RatlineNets#string}) and is used up, unless the player is in
 * creative mode. A mast hoop takes several nets from different chainplates (RL4, its fan); two chainplates on top of
 * railings along one rail edge take a boarding net (RAIL5, {@link BoardingNets}). Every answer goes to the action bar, short: a refusal keeps A. With {@code rigging.nets_enabled} off
 * it strings nothing. All on the server.
 */
public class RatlineNetItem extends Item {

    private static final String P = "message." + Constants.MOD_ID + ".ratline_net.";
    public static final String KEY_MARKED = P + "marked";
    public static final String KEY_STRUNG = P + "strung";
    public static final String KEY_SAME = P + "same";
    public static final String KEY_TOO_LONG = P + "too_long";
    public static final String KEY_TOO_FLAT = P + "too_flat";
    public static final String KEY_OTHER_BODY = P + "other_body";
    public static final String KEY_BUSY = P + "busy";
    public static final String KEY_BLOCKED = P + "blocked";
    /** RL4: a hoop holds {@code rigging.net_max_strands} nets. */
    public static final String KEY_FULL = P + "full";
    /** RL4: strung, but too far from its neighbours on the hoop to be netted in between. */
    public static final String KEY_APART = P + "apart";
    public static final String KEY_DISABLED = P + "disabled";
    /** RAIL5: a boarding net strung along the rail, and its refusals. */
    public static final String KEY_BOARDING_STRUNG = P + "boarding_strung";
    public static final String KEY_NOT_ALONG = P + "not_along";
    public static final String KEY_TOO_SHORT = P + "too_short";
    public static final String KEY_TOOLTIP = "item." + Constants.MOD_ID + ".ratline_net.tooltip";

    public RatlineNetItem(Properties properties) {
        super(properties);
    }

    private static boolean isFitting(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof NetFitting;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!isFitting(context.getLevel(), context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel level) {
            Component message = use(level, context.getItemInHand(), context.getClickedPos(), context.getPlayer());
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(message, true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** A net with one end tied glints. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(RiggingContent.NET_START.get()) || super.isFoil(stack);
    }

    /** Uses {@code stack} on the fitting at {@code pos}; returns the action bar message. */
    public static Component use(ServerLevel level, ItemStack stack, BlockPos pos, @Nullable Player player) {
        if (!RiggingConfig.NETS_ENABLED.get()) {
            return Component.translatable(KEY_DISABLED);
        }
        GlobalPos start = stack.get(RiggingContent.NET_START.get());
        if (start == null || !start.dimension().equals(level.dimension()) || !isFitting(level, start.pos())) {
            switch (RatlineNets.takesAnother(level, pos, null)) {
                case BUSY -> {
                    return Component.translatable(KEY_BUSY);
                }
                case FULL -> {
                    return Component.translatable(KEY_FULL, RiggingConfig.NET_MAX_STRANDS.get());
                }
                default -> {
                }
            }
            stack.set(RiggingContent.NET_START.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            return Component.translatable(KEY_MARKED);
        }
        double maxLength = RiggingConfig.NET_MAX_LENGTH.get();
        double minPitch = RiggingConfig.NET_MIN_PITCH.get();
        Check check = RatlineNets.check(level, start.pos(), pos, maxLength, minPitch);
        switch (check.refusal()) {
            case SAME -> {
                return Component.translatable(KEY_SAME);
            }
            case OTHER_BODY -> {
                return Component.translatable(KEY_OTHER_BODY);
            }
            case BUSY -> {
                return Component.translatable(KEY_BUSY);
            }
            case FULL -> {
                return Component.translatable(KEY_FULL, RiggingConfig.NET_MAX_STRANDS.get());
            }
            case NOT_ALONG -> {
                return Component.translatable(KEY_NOT_ALONG);
            }
            case TOO_SHORT -> {
                return Component.translatable(KEY_TOO_SHORT);
            }
            case TOO_LONG -> {
                if (check.boarding()) {
                    double run = Math.abs(check.net().a().getX() - check.net().b().getX())
                            + Math.abs(check.net().a().getZ() - check.net().b().getZ());
                    return tooLong(run, RiggingConfig.BOARDING_NET_LENGTH.get());
                }
                double length = Math.sqrt(sq(check.net().ringA().x() - check.net().ringB().x())
                        + sq(check.net().ringA().y() - check.net().ringB().y()) + sq(check.net().ringA().z() - check.net().ringB().z()));
                return tooLong(length, maxLength);
            }
            case TOO_FLAT -> {
                return tooFlat(RatlineNetRules.pitch(check.net().ringA(), check.net().ringB()), minPitch);
            }
            case BLOCKED -> {
                BlockPos at = RatlineNets.worldPos(level, check.blocked());
                return Component.translatable(KEY_BLOCKED, at.getX(), at.getY(), at.getZ());
            }
            default -> {
            }
        }
        RatlineNets.string(level, check.net());
        stack.remove(RiggingContent.NET_START.get());
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (check.boarding()) {
            return Component.translatable(KEY_BOARDING_STRUNG);
        }
        if (!Double.isNaN(check.apart())) {
            return Component.translatable(KEY_APART, String.format(java.util.Locale.ROOT, "%.1f", check.apart()));
        }
        return Component.translatable(KEY_STRUNG, check.net().cells().size());
    }

    private static double sq(double v) {
        return v * v;
    }

    /** "Too long: 6.3 of 6 blocks", the numbers with a dot whatever the system locale (FIX2). */
    public static Component tooLong(double length, double maxLength) {
        return Component.translatable(KEY_TOO_LONG, Num.one(length), Num.trim(maxLength));
    }

    /** "Too flat: 12°, at least 15°". */
    public static Component tooFlat(double pitch, double minPitch) {
        return Component.translatable(KEY_TOO_FLAT, Num.whole(pitch), Num.trim(minPitch));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(KEY_TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
