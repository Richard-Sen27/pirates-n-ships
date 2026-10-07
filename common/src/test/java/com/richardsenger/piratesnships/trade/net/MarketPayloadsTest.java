package com.richardsenger.piratesnships.trade.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every market payload survives an encode and decode. */
class MarketPayloadsTest {

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
    void clientRequestsRoundTrip() {
        var port = Constants.id("debug/a");
        var refresh = new MarketPayloads.Refresh(port, 64);
        assertEquals(refresh, roundTrip(MarketPayloads.Refresh.CODEC, refresh));
        var trade = new MarketPayloads.Trade(port, false, Constants.id("sugar"), 128, true, Optional.of(new BlockPos(1, -2, 3)));
        assertEquals(trade, roundTrip(MarketPayloads.Trade.CODEC, trade));
        var noContainer = new MarketPayloads.Trade(port, true, Constants.id("rum"), 1, false, Optional.empty());
        assertEquals(noContainer, roundTrip(MarketPayloads.Trade.CODEC, noContainer));
        var contract = new MarketPayloads.ContractAction(port, true, UUID.randomUUID(), Optional.empty());
        assertEquals(contract, roundTrip(MarketPayloads.ContractAction.CODEC, contract));
    }

    @Test
    void openMarketRoundTrips() {
        var open = new MarketPayloads.OpenMarket(Constants.id("debug/a"), new BlockPos(-12, 64, 300), 8.0);
        assertEquals(open, roundTrip(MarketPayloads.OpenMarket.CODEC, open));
    }

    @Test
    void stateRoundTrips() {
        var port = Constants.id("debug/a");
        var c = new DeliveryContract(UUID.randomUUID(), Constants.id("sugar"), 20, port, Constants.id("debug/b"), 1, 3, 9, 100, 30,
                DeliveryContract.State.ACCEPTED, Optional.of(UUID.randomUUID()));
        var line = new MarketView.GoodLine(Constants.id("sugar"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("sugar"),
                GoodRole.PRODUCES, new MarketView.Price(120, 900, Market.Outcome.OK), new MarketView.Price(0, 0, Market.Outcome.LIMIT));
        var view = new MarketView(port, PortKind.PIRATE_ISLAND, 64, 12345L, List.of(line), List.of(), List.of(c));
        var result = new TransactionResult(TransactionResult.Status.CONFISCATED, Constants.id("sugar"), 64, 0,
                PlunderRules.Outcome.CONFISCATED, true, Optional.empty());
        var state = new MarketPayloads.State(Optional.of(view), Optional.of(result));
        assertEquals(state, roundTrip(MarketPayloads.State.CODEC, state));
        var empty = new MarketPayloads.State(Optional.empty(), Optional.of(TransactionResult.contract(TransactionResult.Status.CONTRACT_REFUSED,
                Constants.id("sugar"), 0, 0, DeliveryContract.Outcome.WRONG_PORT)));
        assertEquals(empty, roundTrip(MarketPayloads.State.CODEC, empty));
    }
}
