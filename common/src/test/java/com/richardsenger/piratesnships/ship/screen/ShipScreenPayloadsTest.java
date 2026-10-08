package com.richardsenger.piratesnships.ship.screen;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** HGUI1: what the ship screen payloads carry survives an encode and decode, plenty of supplies (infinity) included. */
class ShipScreenPayloadsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "all bytes read");
        return back;
    }

    @Test
    void stateRoundTrips() {
        BlockPos winch = new BlockPos(5, 9, 5);
        ShipScreenView.CrewLine anne = new ShipScreenView.CrewLine(UUID.randomUUID(), "Anne", 18, ShipScreenRules.CrewState.STATION,
                Optional.of(winch), "block.pirates_n_ships.sail_winch", "sail_order.pirates_n_ships.hoist", Optional.of("Steve"), true,
                true, ShipScreenRules.Desertion.LEAVING, 1, true);
        ShipScreenView.CrewLine bart = ShipScreenRulesTest.crew("Bart", Optional.empty(), false);
        ShipScreenView.StationLine manned = new ShipScreenView.StationLine(winch, "block.pirates_n_ships.sail_winch",
                Optional.of(anne.id()), "Anne", false, "sail_order.pirates_n_ships.hoist", "");
        ShipScreenView.StationLine free = ShipScreenRulesTest.station(new BlockPos(6, 9, 5), Optional.empty(), false);
        ShipScreenView view = ShipScreenRulesTest.view(List.of(anne, bart), List.of(manned, free), new ShipScreenView.Toggles(true, false, true));
        var open = new ShipScreenPayloads.State(true, Optional.of(view), Optional.empty(), true);
        assertEquals(open, roundTrip(ShipScreenPayloads.State.CODEC, open));
        assertEquals(Double.POSITIVE_INFINITY, roundTrip(ShipScreenPayloads.State.CODEC, open).view().orElseThrow().upkeep().waterDays());
        var answer = new ShipScreenPayloads.State(false, Optional.of(view),
                Optional.of(Component.translatable("message.pirates_n_ships.crew.released", "Anne")), false);
        assertEquals(answer, roundTrip(ShipScreenPayloads.State.CODEC, answer));
        var closed = new ShipScreenPayloads.State(false, Optional.empty(), Optional.of(Component.literal("gone")), false);
        assertEquals(closed, roundTrip(ShipScreenPayloads.State.CODEC, closed));
    }

    @Test
    void actionsRoundTrip() {
        UUID ship = UUID.randomUUID();
        for (var a : List.of(
                ShipScreenPayloads.Action.text(ship, ShipScreenPayloads.Kind.RENAME, "Sea Wolf"),
                ShipScreenPayloads.Action.text(ship, ShipScreenPayloads.Kind.ORDER, "hoist"),
                ShipScreenPayloads.Action.crew(ship, ShipScreenPayloads.Kind.DISMISS, UUID.randomUUID()),
                ShipScreenPayloads.Action.assign(ship, UUID.randomUUID(), new BlockPos(-3, 70, 12)),
                ShipScreenPayloads.Action.of(ship, ShipScreenPayloads.Kind.CLOSE))) {
            assertEquals(a, roundTrip(ShipScreenPayloads.Action.CODEC, a));
        }
    }
}
