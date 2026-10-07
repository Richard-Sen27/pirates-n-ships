package com.richardsenger.piratesnships.sailing.item;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchor;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchorBlockEntity;
import com.richardsenger.piratesnships.sailing.rope.RopeLines;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Rope (docs/design.md §5.2, rules F5b and RP1): used on one rope anchor (a cleat, or a mooring ring while
 * {@code sailing.sails.rope_lines} is on) it remembers that anchor ({@code rope_start}, with the dimension); used on a
 * second anchor it runs a rope between the two when {@link StayLinker#decide} allows it and uses up one rope: a stay
 * between two cleats that pass the stay rule (a sail once a clew is below the head), else a decorative rope line.
 * Both anchors must be on one body (both on land, or both on the same ship) and at most {@code stay_max_length}
 * blocks apart, and each holds at most {@link RopeAnchorBlockEntity#MAX_ROPES} ropes. Otherwise it says why not and
 * keeps the first anchor. All on the server.
 */
public class RopeItem extends Item {

    private static final String P = "message." + Constants.MOD_ID + ".rope.";
    public static final String KEY_TIED = P + "tied";
    public static final String KEY_RIGGED = P + "rigged";
    public static final String KEY_SAME = P + "same";
    public static final String KEY_TOO_LONG = P + "too_long";
    public static final String KEY_TOO_FLAT = P + "too_flat";
    public static final String KEY_ELSEWHERE = P + "elsewhere";
    public static final String KEY_LINE = P + "line";
    public static final String KEY_OTHER_BODY = P + "other_body";
    public static final String KEY_NO_LINES = P + "no_lines";
    public static final String KEY_ALREADY = P + "already";
    public static final String KEY_FULL = P + "full";

    public RopeItem(Properties properties) {
        super(properties);
    }

    /** Whether the rope ties onto the block at {@code pos}: a cleat always, another anchor only while rope lines are on. */
    public static boolean takesRope(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        RopeAnchor a = RopeAnchor.of(state);
        return a != null && (a.anchorKind() == RopeAnchor.Kind.CLEAT || SailingConfig.ROPE_LINES.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!takesRope(context.getLevel(), context.getClickedPos())) {
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

    /** A rope that remembers a first anchor glints. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(TriangularSailContent.ROPE_START.get()) || super.isFoil(stack);
    }

    /**
     * Uses {@code stack} on the anchor at {@code pos} and returns the message for the player. Rigging a rope uses up
     * one rope unless {@code player} is in creative mode.
     */
    public static Component use(ServerLevel level, ItemStack stack, BlockPos pos, @Nullable Player player) {
        StayRules rules = SailingConfig.stayRules();
        GlobalPos start = stack.get(TriangularSailContent.ROPE_START.get());
        if (start == null || !start.dimension().equals(level.dimension()) || !takesRope(level, start.pos())) {
            boolean elsewhere = start != null;
            stack.set(TriangularSailContent.ROPE_START.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            return elsewhere ? Component.translatable(KEY_ELSEWHERE, rules.maxLength())
                    : Component.translatable(KEY_TIED, rules.maxLength());
        }
        BlockPos a = start.pos();
        boolean aCleat = level.getBlockState(a).getBlock() instanceof CleatBlock;
        boolean bCleat = level.getBlockState(pos).getBlock() instanceof CleatBlock;
        StayLinker.Rig rig = StayLinker.decide(TriangularSails.lookup(level), TriangularSails.point(a), aCleat,
                TriangularSails.point(pos), bCleat, Objects.equals(shipOf(level, a), shipOf(level, pos)),
                SailingConfig.ROPE_LINES.get(), rules);
        switch (rig) {
            case SAME -> {
                return Component.translatable(KEY_SAME, rules.maxLength());
            }
            case OTHER_BODY -> {
                return Component.translatable(KEY_OTHER_BODY);
            }
            case TOO_LONG -> {
                return Component.translatable(KEY_TOO_LONG,
                        String.format("%.1f", StayLinker.length(TriangularSails.point(a), TriangularSails.point(pos))), rules.maxLength());
            }
            case TOO_FLAT -> {
                return Component.translatable(KEY_TOO_FLAT, rules.minDrop());
            }
            case NO_LINES -> {
                return Component.translatable(KEY_NO_LINES);
            }
            default -> {
            }
        }
        if (RopeLines.joined(level, a, pos)) {
            return Component.translatable(KEY_ALREADY);
        }
        if (!RopeLines.hasRoom(level, a) || !RopeLines.hasRoom(level, pos) || !RopeLines.rig(level, a, pos)) {
            return Component.translatable(KEY_FULL, RopeAnchorBlockEntity.MAX_ROPES);
        }
        stack.remove(TriangularSailContent.ROPE_START.get());
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return Component.translatable(rig == StayLinker.Rig.LINE ? KEY_LINE : KEY_RIGGED);
    }

    private static @Nullable UUID shipOf(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        return ship == null ? null : ship.id();
    }
}
