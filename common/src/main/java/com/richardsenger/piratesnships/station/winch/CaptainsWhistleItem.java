package com.richardsenger.piratesnships.station.winch;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.order.WhistleMenu;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Captain's whistle (docs/design.md §7.2):
 * <ul>
 *   <li>use on an unassigned crew member: select it; then use on a station block: assign it there;</li>
 *   <li>use on an assigned crew member: release it;</li>
 *   <li>use anywhere else: opens the radial order menu on the client ({@link WhistleMenu}). Nothing happens on the
 *       server until the client sends the chosen order ({@code WhistleOrderPayload}, handled by
 *       {@link com.richardsenger.piratesnships.station.order.WhistleOrders}), which goes to all crew at stations of
 *       the ship the player stands on.</li>
 * </ul>
 */
public class CaptainsWhistleItem extends Item implements StationBlock.Tool {

    static final String KEY = "message." + Constants.MOD_ID + ".whistle.";
    public static final String KEY_SELECTED = KEY + "selected";
    public static final String KEY_NO_SELECTION = KEY + "no_selection";
    public static final String KEY_ORDER = KEY + "order";
    public static final String KEY_NOT_ON_SHIP = KEY + "not_on_ship";

    /** Selected crew member per player (server side, transient). */
    private static final Map<UUID, UUID> SELECTED = new ConcurrentHashMap<>();

    public CaptainsWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof CrewMember crew)) {
            return InteractionResult.PASS;
        }
        if (player.level() instanceof ServerLevel level) {
            if (crew.assignment() != null) {
                CrewStations.release(level, crew);
                SELECTED.remove(player.getUUID());
                player.displayClientMessage(Component.translatable(CrewStations.KEY_RELEASED, crew.getDisplayName()), true);
            } else {
                SELECTED.put(player.getUUID(), crew.getUUID());
                player.displayClientMessage(Component.translatable(KEY_SELECTED, crew.getDisplayName()), true);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof StationBlock)) {
            return InteractionResult.PASS;
        }
        if (ctx.getLevel() instanceof ServerLevel level) {
            CrewMember crew = selected(level, player);
            if (crew == null) {
                player.displayClientMessage(Component.translatable(KEY_NO_SELECTION), true);
            } else {
                CrewStations.AssignResult r = CrewStations.assign(level, crew, ctx.getClickedPos());
                player.displayClientMessage(assignMessage(r, crew), true);
                if (r == CrewStations.AssignResult.ASSIGNED) SELECTED.remove(player.getUUID());
            }
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            WhistleMenu.open(); // client only; the server waits for the chosen order
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static Component assignMessage(CrewStations.AssignResult r, CrewMember crew) {
        return switch (r) {
            case ASSIGNED -> Component.translatable(CrewStations.KEY_ASSIGNED, crew.getDisplayName());
            case TAKEN -> Component.translatable(CrewStations.KEY_TAKEN);
            case NOT_A_STATION -> Component.translatable(CrewStations.KEY_NOT_A_STATION);
            case DISABLED -> Component.translatable(CrewStations.KEY_DISABLED);
        };
    }

    /** The ship the player stands on or rides in; else the ship whose world bounds contain the player. */
    public static @Nullable ShipBody shipOf(ServerLevel level, Entity entity) {
        ShipBody s = ShipEntities.standingOrRiding(entity);
        if (s != null) {
            return s;
        }
        for (ShipBody b : SableShips.all(level)) {
            // near the ship and above a plot block once mapped into the ship's frame
            if (CrewStations.worldBox(b, 2).contains(entity.position()) && onDeck(level, b, entity)) return b;
        }
        return null;
    }

    private static boolean onDeck(ServerLevel level, ShipBody ship, Entity entity) {
        net.minecraft.core.BlockPos local = net.minecraft.core.BlockPos.containing(ship.toPlot(entity.position()));
        for (int dy = 0; dy <= 2; dy++) {
            if (!level.getBlockState(local.below(dy)).isAir()) return true;
        }
        return false;
    }

    private static @Nullable CrewMember selected(ServerLevel level, Player player) {
        UUID id = SELECTED.get(player.getUUID());
        return id != null && level.getEntity(id) instanceof CrewMember c && c.isAlive() ? c : null;
    }

    public static void onServerStopped() {
        SELECTED.clear();
    }
}
