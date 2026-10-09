package com.richardsenger.piratesnships.station.order;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.OrderHints;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
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
 * stands on a ship, then issues the order to that ship's crew through {@link CrewStations}: a {@link CrewOrder} goes to
 * the crew at the stations whose kind takes it (sail orders to the winches, "pump" to the bilge pumps, "fire" and "load" to the cannons and swivel guns), and the
 * unmanned stations that take it become open jobs on the ship's {@link JobBoard} for free crew to claim (CR1). Only
 * sail orders are remembered on the whistle as its last order. "Release crew" frees everyone, clears the board and ends
 * "Fire at will" (WS4a, {@link Gunnery#clear}).
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

    /**
     * @param crew how many manned stations carry the order out at once (or, for "release crew", how many leave)
     * @param jobs how many unmanned stations became open jobs on the ship's board
     */
    public record Result(Outcome outcome, int crew, int jobs) {
        static Result of(Outcome o) {
            return new Result(o, 0, 0);
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
        ShipBody ship = StationConfig.ENABLED.get() ? CaptainsWhistleItem.shipOf(level, player) : null;
        Issued issued = issue(level, ship, order.get(), whistle);
        player.displayClientMessage(issued.message(), true);
        return issued.result();
    }

    /** What {@link #issue} did and the line that tells the captain about it (the whistle shows it in the action bar). */
    public record Issued(Result result, Component message) {
    }

    /**
     * Issues {@code order} to the crew of {@code ship}: the whistle's radial menu and the helm's ship screen (HGUI1) both
     * come here, so the rules live in one place. {@code whistle} (may be null) remembers the last sail order. The
     * caller checks who may give the order and tells the player {@link Issued#message()}.
     */
    public static Issued issue(ServerLevel level, @Nullable ShipBody ship, WhistleOrder order, @Nullable ItemStack whistle) {
        if (!StationConfig.ENABLED.get()) {
            return new Issued(Result.of(Outcome.DISABLED), Component.translatable(CrewStations.KEY_DISABLED));
        }
        if (ship == null) {
            return new Issued(Result.of(Outcome.NOT_ON_SHIP), Component.translatable(CaptainsWhistleItem.KEY_NOT_ON_SHIP));
        }
        CrewOrder crewOrder = order.order();
        int n;
        int jobs = 0;
        Component message;
        if (crewOrder != null) {
            // only the crew at stations that take this order hear it (CrewStations#orderShip)
            n = CrewStations.orderShip(level, ship.id(), crewOrder);
            // the unmanned stations that take it become open jobs for the free crew (CR1)
            JobBoard.Posted posted = JobBoard.post(level, ship, crewOrder);
            jobs = posted.jobs();
            if (whistle != null && crewOrder instanceof SailOrder sail) {
                whistle.set(StationContent.WHISTLE_ORDER.get(), sail); // the menu marks the last sail order
            }
            Component name = Component.translatable(crewOrder.nameKey());
            if (posted.noFreeHands()) {
                message = Component.translatable(JobBoard.KEY_NO_FREE_HANDS, name);
            } else if (jobs > 0) {
                message = Component.translatable(JobBoard.KEY_ORDER_POSTED, name, n, jobs);
            } else if (n == 0 && CrewStations.crewOf(level, ship.id()).stream().noneMatch(c -> CrewStations.takes(level, c, crewOrder))) {
                // Q5: nobody heard it and no job opened: say what is missing instead of "0 crew carry it out"
                message = OrderHints.why(level, ship, crewOrder);
            } else {
                message = Component.translatable(CaptainsWhistleItem.KEY_ORDER, name, n);
            }
        } else {
            // RELEASE, the only entry that is not a crew order so far: everyone leaves, the open jobs go too
            n = CrewStations.releaseShip(level, ship.id());
            JobBoard.clear(ship.id());
            Gunnery.clear(ship); // WS4a: "Fire at will" ends with the release
            message = Component.translatable(KEY_RELEASED_ALL, n);
        }
        return new Issued(new Result(Outcome.ISSUED, n, jobs), message);
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
