package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.MarketView;

import java.util.Optional;

/**
 * Client copy of the last market state the server sent, for the market screen. Uses no client-only classes, so
 * the payload handler may reference it on both sides; {@link TradeClient#init} (from {@code initClient()}) clears it when the
 * client leaves a world. {@link #version()} grows with every update so a screen can tell when to redraw.
 */
public final class ClientMarketState {

    private static volatile Optional<MarketView> view = Optional.empty();
    private static volatile Optional<TransactionResult> lastResult = Optional.empty();
    private static volatile long version;
    private static volatile long resultVersion;
    private static volatile Optional<MarketPayloads.OpenMarket> desk = Optional.empty();
    private static volatile Runnable opener = () -> { };

    private ClientMarketState() {
    }


    /** Client init only: what happens when a desk opens a market (opens the market screen). */
    public static void setOpener(Runnable r) {
        opener = r;
    }

    /** A harbor master's desk opened a market: remember the desk and open the screen (the state follows). */
    public static void open(MarketPayloads.OpenMarket open) {
        desk = Optional.of(open);
        view = Optional.empty();
        lastResult = Optional.empty();
        version++;
        opener.run();
    }

    /** The desk the open market screen belongs to. */
    public static Optional<MarketPayloads.OpenMarket> desk() {
        return desk;
    }

    /**
     * A new state. A state without a result (a live refresh) keeps the last result, so a refresh that arrives in the
     * same client tick as a request's answer does not hide it; {@link #resultVersion()} tells new results apart.
     */
    public static void accept(MarketPayloads.State state) {
        view = state.view();
        if (state.result().isPresent()) {
            lastResult = state.result();
            resultVersion++;
        }
        version++;
    }

    /** Grows with every state that carried a result. */
    public static long resultVersion() {
        return resultVersion;
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
        desk = Optional.empty();
        view = Optional.empty();
        lastResult = Optional.empty();
        version++;
    }
}
