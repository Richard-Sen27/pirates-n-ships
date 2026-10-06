package com.richardsenger.piratesnships.station.order;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the whistle's radial menu: registers {@link WhistleOrderPayload} and carries out a chosen order. The
 * client only asks; this class checks that the sender holds a whistle, that the order exists and that the sender
 * stands on a ship, then issues the order to that ship's crew through {@link CrewStations}.
 */
public final class WhistleOrders {

    static final String KEY = "message." + Constants.MOD_ID + ".whistle.";
    public static final String KEY_RELEASED_ALL = KEY + "released_all";

    public enum Outcome {
        /** Issued to the ship's crew; {@link Result#crew()} tells how many act on it. */
        ISSUED,
        /** The sender holds no whistle: refused without a word. */
        NO_WHISTLE,
        /** No such order: ignored (logged). */
        UNKNOWN_ORDER,
        NOT_ON_SHIP,
        DISABLED
    }

    public record Result(Outcome outcome, int crew) {
        static Result of(Outcome o) {
            return new Result(o, 0);
        }
    }

    private WhistleOrders() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(WhistleOrderPayload.TYPE, WhistleOrderPayload.CODEC,
                (payload, player) -> handle(player, payload.order()));
    }

    /** Carries out the order {@code id} for {@code player} (server side) and tells the player what happened. */
    public static Result handle(Player player, String id) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Result.of(Outcome.NO_WHISTLE);
        }
        ItemStack whistle = heldWhistle(player);
        if (whistle == null) {
            return Result.of(Outcome.NO_WHISTLE);
        }
        Optional<WhistleOrder> order = WhistleOrder.byId(id);
        if (order.isEmpty()) {
            Constants.LOG.debug("Ignoring unknown whistle order '{}' from {}", id, player.getName().getString());
            return Result.of(Outcome.UNKNOWN_ORDER);
        }
        if (!StationConfig.ENABLED.get()) {
            player.displayClientMessage(Component.translatable(CrewStations.KEY_DISABLED), true);
            return Result.of(Outcome.DISABLED);
        }
        ShipBody ship = CaptainsWhistleItem.shipOf(level, player);
        if (ship == null) {
            player.displayClientMessage(Component.translatable(CaptainsWhistleItem.KEY_NOT_ON_SHIP), true);
            return Result.of(Outcome.NOT_ON_SHIP);
        }
        SailOrder sail = order.get().sail();
        int n;
        if (sail != null) {
            n = CrewStations.orderShip(level, ship.id(), sail);
            whistle.set(StationContent.WHISTLE_ORDER.get(), sail);
            player.displayClientMessage(Component.translatable(CaptainsWhistleItem.KEY_ORDER, Component.translatable(sail.nameKey()), n), true);
        } else {
            // RELEASE, the only order that is not a sail order so far
            n = CrewStations.releaseShip(level, ship.id());
            player.displayClientMessage(Component.translatable(KEY_RELEASED_ALL, n), true);
        }
        return new Result(Outcome.ISSUED, n);
    }

    /** The whistle in the main hand, else in the off hand, else null. */
    public static @Nullable ItemStack heldWhistle(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof CaptainsWhistleItem) return s;
        }
        return null;
    }
}
