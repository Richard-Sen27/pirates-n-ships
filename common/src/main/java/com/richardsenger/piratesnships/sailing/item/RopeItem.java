package com.richardsenger.piratesnships.sailing.item;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.sail.StayLinker;
import com.richardsenger.piratesnships.sailing.sail.StayRules;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;

/**
 * Rope (docs/design.md §5.2, rule F5b): used on one cleat it remembers that cleat ({@code rope_start}, with the
 * dimension), used on a second cleat it rigs a stay between the two when the rule allows it
 * ({@link StayLinker#check}: at most {@code stay_max_length} blocks apart, at least {@code stay_min_drop} blocks
 * apart in height, both in the same level and on the same ship or both on land) and uses up one rope; otherwise it
 * says why not. All on the server.
 */
public class RopeItem extends Item {

    private static final String P = "message." + Constants.MOD_ID + ".rope.";
    public static final String KEY_TIED = P + "tied";
    public static final String KEY_RIGGED = P + "rigged";
    public static final String KEY_SAME = P + "same";
    public static final String KEY_TOO_LONG = P + "too_long";
    public static final String KEY_TOO_FLAT = P + "too_flat";
    public static final String KEY_ELSEWHERE = P + "elsewhere";

    public RopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof CleatBlock)) {
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

    /** A rope that remembers a first cleat glints. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(TriangularSailContent.ROPE_START.get()) || super.isFoil(stack);
    }

    /**
     * Uses {@code stack} on the cleat at {@code pos} and returns the message for the player. Rigging a stay uses up one
     * rope unless {@code player} is in creative mode.
     */
    public static Component use(ServerLevel level, ItemStack stack, BlockPos pos, @Nullable Player player) {
        StayRules rules = SailingConfig.stayRules();
        GlobalPos start = stack.get(TriangularSailContent.ROPE_START.get());
        if (start == null || !start.dimension().equals(level.dimension())
                || !(level.getBlockState(start.pos()).getBlock() instanceof CleatBlock)
                || !Objects.equals(shipOf(level, start.pos()), shipOf(level, pos))) {
            boolean elsewhere = start != null;
            stack.set(TriangularSailContent.ROPE_START.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            return elsewhere ? Component.translatable(KEY_ELSEWHERE, rules.maxLength())
                    : Component.translatable(KEY_TIED, rules.maxLength());
        }
        BlockPos a = start.pos();
        StayLinker.Check check = StayLinker.check(TriangularSails.point(a), TriangularSails.point(pos), rules);
        switch (check) {
            case SAME -> {
                return Component.translatable(KEY_SAME, rules.maxLength());
            }
            case TOO_LONG -> {
                return Component.translatable(KEY_TOO_LONG,
                        String.format("%.1f", StayLinker.length(TriangularSails.point(a), TriangularSails.point(pos))), rules.maxLength());
            }
            case TOO_FLAT -> {
                return Component.translatable(KEY_TOO_FLAT, rules.minDrop());
            }
            default -> {
            }
        }
        TriangularSails.rig(level, a, pos);
        stack.remove(TriangularSailContent.ROPE_START.get());
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return Component.translatable(KEY_RIGGED);
    }

    private static @Nullable UUID shipOf(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        return ship == null ? null : ship.id();
    }
}
