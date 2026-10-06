package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.MarketView;

import java.util.Optional;

/**
 * Client copy of the last market state the server sent, for the later market screen. Uses no client-only classes, so
 * the payload handler may reference it on both sides; {@link TradeClient#init} (from {@code initClient()}) clears it when the
 * client leaves a world. {@link #version()} grows with every update so a screen can tell when to redraw.
 */
public final class ClientMarketState {

    private static volatile Optional<MarketView> view = Optional.empty();
    private static volatile Optional<TransactionResult> lastResult = Optional.empty();
    private static volatile long version;

    private ClientMarketState() {
    }


    public static void accept(MarketPayloads.State state) {
        view = state.view();
        lastResult = state.result();
        version++;
    }

    public static Optional<MarketView> view() {
        return view;
    }

    public static Optional<TransactionResult> lastResult() {
        return lastResult;
    }

    public static long version() {
        return version;
    }

    public static void reset() {
        view = Optional.empty();
        lastResult = Optional.empty();
        version++;
    }
}
