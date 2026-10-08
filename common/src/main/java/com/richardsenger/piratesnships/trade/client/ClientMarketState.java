package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.crew.hiring.CrewPayloads;
import com.richardsenger.piratesnships.rpg.quest.QuestPayloads;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.OrderPayloads;
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
    private static volatile Optional<OrderPayloads.OrdersView> orders = Optional.empty();
    private static volatile Optional<OrderPayloads.OrderResult> lastOrderResult = Optional.empty();
    private static volatile long orderResultVersion;
    private static volatile Optional<QuestPayloads.QuestsView> quests = Optional.empty();
    private static volatile Optional<QuestPayloads.QuestResult> lastQuestResult = Optional.empty();
    private static volatile long questResultVersion;
    private static volatile Optional<CrewPayloads.CrewView> crew = Optional.empty();
    private static volatile Optional<CrewPayloads.CrewResult> lastCrewResult = Optional.empty();
    private static volatile long crewResultVersion;

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
        orders = Optional.empty();
        lastOrderResult = Optional.empty();
        quests = Optional.empty();
        lastQuestResult = Optional.empty();
        crew = Optional.empty();
        lastCrewResult = Optional.empty();
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

    /**
     * The shipwright's Orders tab (SW1). A payload without a view (a refused order) keeps the last tab content;
     * {@link #orderResultVersion()} grows with every result.
     */
    public static void acceptOrders(OrderPayloads.Orders payload) {
        if (payload.view().isPresent()) orders = payload.view();
        if (payload.result().isPresent()) {
            lastOrderResult = payload.result();
            orderResultVersion++;
        }
        version++;
    }

    /** The Orders tab of the open desk (present only at a seafarer village's desk). */
    public static Optional<OrderPayloads.OrdersView> orders() {
        return orders;
    }

    public static Optional<OrderPayloads.OrderResult> lastOrderResult() {
        return lastOrderResult;
    }

    public static long orderResultVersion() {
        return orderResultVersion;
    }

    /**
     * The Quests tab (QST1). A payload without a view (a refused action) keeps the last tab content;
     * {@link #questResultVersion()} grows with every result.
     */
    public static void acceptQuests(QuestPayloads.QuestsPayload payload) {
        if (payload.view().isPresent()) quests = payload.view();
        if (payload.result().isPresent()) {
            lastQuestResult = payload.result();
            questResultVersion++;
        }
        version++;
    }

    /** The Quests tab of the open desk (present while quests are on). */
    public static Optional<QuestPayloads.QuestsView> quests() {
        return quests;
    }

    public static Optional<QuestPayloads.QuestResult> lastQuestResult() {
        return lastQuestResult;
    }

    public static long questResultVersion() {
        return questResultVersion;
    }

    /**
     * The Crew tab (CRW1). A payload without a view (a refused action) keeps the last tab content;
     * {@link #crewResultVersion()} grows with every result.
     */
    public static void acceptCrew(CrewPayloads.CrewPayload payload) {
        if (payload.view().isPresent()) crew = payload.view();
        if (payload.result().isPresent()) {
            lastCrewResult = payload.result();
            crewResultVersion++;
        }
        version++;
    }

    /** The Crew tab of the open desk (present while hiring is on). */
    public static Optional<CrewPayloads.CrewView> crew() {
        return crew;
    }

    public static Optional<CrewPayloads.CrewResult> lastCrewResult() {
        return lastCrewResult;
    }

    public static long crewResultVersion() {
        return crewResultVersion;
    }

    public static void reset() {
        desk = Optional.empty();
        view = Optional.empty();
        lastResult = Optional.empty();
        orders = Optional.empty();
        lastOrderResult = Optional.empty();
        quests = Optional.empty();
        lastQuestResult = Optional.empty();
        crew = Optional.empty();
        lastCrewResult = Optional.empty();
        version++;
    }
}
