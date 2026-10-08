package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;

import java.util.Locale;

/** Translation keys of the market screen (no client classes, so datagen can write the lang entries). */
public final class MarketText {

    public static final String KEY = "screen." + Constants.MOD_ID + ".market.";
    public static final String TITLE = KEY + "title";
    public static final String TAB_GOODS = KEY + "tab_goods";
    public static final String TAB_CONTRACTS = KEY + "tab_contracts";
    public static final String COINS = KEY + "coins";
    public static final String QUANTITY = KEY + "quantity";
    public static final String BUY = KEY + "buy";
    public static final String SELL = KEY + "sell";
    public static final String HAVE = KEY + "have";
    public static final String HAVE_PLUNDERED = KEY + "have_plundered";
    public static final String SELLING_CLEAN = KEY + "selling_clean";
    public static final String SELLING_PLUNDERED = KEY + "selling_plundered";
    public static final String AFFORD = KEY + "afford";
    public static final String LOADING = KEY + "loading";
    public static final String EMPTY = KEY + "empty";
    public static final String OFFERS = KEY + "offers";
    public static final String MY_CONTRACTS = KEY + "my_contracts";
    public static final String NO_OFFERS = KEY + "no_offers";
    public static final String NO_CONTRACTS = KEY + "no_contracts";
    public static final String CONTRACT_LINE = KEY + "contract_line";
    public static final String CONTRACT_DETAIL = KEY + "contract_detail";
    public static final String ACCEPT = KEY + "accept";
    public static final String DELIVER = KEY + "deliver";
    public static final String BOUGHT = KEY + "result.bought";
    public static final String ACCEPTED = KEY + "result.accepted";
    public static final String DELIVERED = KEY + "result.delivered";
    public static final String REFUSED = KEY + "result.refused";
    public static final String CLOSED = KEY + "closed";
    // SW1: the shipwright's Orders tab
    public static final String TAB_ORDERS = KEY + "tab_orders";
    public static final String SHIPS = KEY + "ships";
    public static final String NO_SHIPS = KEY + "no_ships";
    public static final String MY_ORDERS = KEY + "my_orders";
    public static final String NO_MY_ORDERS = KEY + "no_my_orders";
    public static final String ORDER = KEY + "order";
    public static final String ORDER_DETAIL = KEY + "order_detail";
    public static final String ORDER_MATERIALS = KEY + "order_materials";
    public static final String ORDERS_DISABLED = KEY + "orders_disabled";
    public static final String ORDERS_FULL = KEY + "orders_full";
    public static final String ORDER_NEEDS = KEY + "order_needs";
    public static final String MY_ORDER_WAITING = KEY + "my_order_waiting";
    public static final String MY_ORDER_READY = KEY + "my_order_ready";
    // QST1: the Quests tab (its rows' texts are in rpg.quest.QuestText)
    public static final String TAB_QUESTS = KEY + "tab_quests";

    private MarketText() {
    }

    public static String kind(PortKind kind) {
        return KEY + "kind." + kind.getSerializedName();
    }

    public static String role(GoodRole role) {
        return KEY + "role." + role.getSerializedName();
    }

    /** A sale's message by plunder outcome: count, good, doubloons. */
    public static String sold(PlunderRules.Outcome outcome) {
        return KEY + "result.sold." + outcome.name().toLowerCase(Locale.ROOT);
    }

    public static String contractOutcome(DeliveryContract.Outcome outcome) {
        return KEY + "contract_outcome." + outcome.name().toLowerCase(Locale.ROOT);
    }

    public static void lang(LangBuilder lang) {
        lang.add(TITLE, "Harbor Master")
                .add(TAB_GOODS, "Goods")
                .add(TAB_CONTRACTS, "Contracts")
                .add(COINS, "%s doubloons")
                .add(QUANTITY, "Quantity")
                .add(BUY, "Buy")
                .add(SELL, "Sell")
                .add(HAVE, "you have %s")
                .add(HAVE_PLUNDERED, "you have %s (+%s plundered)")
                .add(SELLING_CLEAN, "Selling clean goods")
                .add(SELLING_PLUNDERED, "Selling plundered goods")
                .add(AFFORD, "You can afford about %s")
                .add(LOADING, "Asking the harbor master...")
                .add(EMPTY, "This port trades nothing")
                .add(OFFERS, "Offers")
                .add(MY_CONTRACTS, "Your contracts")
                .add(NO_OFFERS, "No offers today")
                .add(NO_CONTRACTS, "No accepted contracts")
                .add(CONTRACT_LINE, "%s × %s to %s")
                .add(CONTRACT_DETAIL, "by day %s, reward %s, deposit %s")
                .add(ACCEPT, "Accept")
                .add(DELIVER, "Deliver")
                .add(BOUGHT, "Bought %s %s for %s doubloons")
                .add(ACCEPTED, "Contract accepted, %s doubloons deposit paid")
                .add(DELIVERED, "Contract delivered: %s units, %s doubloons paid out")
                .add(REFUSED, "%s: %s")
                .add(CLOSED, "The harbor master has closed the books");
        lang.add(TAB_QUESTS, "Quests");
        lang.add(TAB_ORDERS, "Orders")
                .add(SHIPS, "The shipwright builds (%s of %s slipways taken)")
                .add(NO_SHIPS, "The shipwright has no ship plans")
                .add(MY_ORDERS, "Your orders")
                .add(NO_MY_ORDERS, "No orders here")
                .add(ORDER, "Order")
                .add(ORDER_DETAIL, "%s doubloons, %s days")
                .add(ORDER_MATERIALS, "%s logs, %s wool")
                .add(ORDERS_DISABLED, "The shipwright takes no orders")
                .add(ORDERS_FULL, "All slipways are taken; come back later")
                .add(ORDER_NEEDS, "You need %s doubloons, %s logs and %s wool")
                .add(MY_ORDER_WAITING, "ready in %s days")
                .add(MY_ORDER_READY, "ready: bring the receipt to this desk");
        lang.add(kind(PortKind.SEAFARER_VILLAGE), "Seafarer village")
                .add(kind(PortKind.NAVY_OUTPOST), "Navy outpost")
                .add(kind(PortKind.PIRATE_ISLAND), "Pirate island");
        lang.add(role(GoodRole.PRODUCES), "produced here")
                .add(role(GoodRole.NEUTRAL), "traded")
                .add(role(GoodRole.DEMANDS), "wanted here")
                .add(role(GoodRole.NOT_TRADED), "not traded");
        for (PlunderRules.Outcome o : PlunderRules.Outcome.values()) {
            lang.add(sold(o), switch (o) {
                case NORMAL -> "Sold %s %s for %s doubloons";
                case FENCED -> "The fence took %s %s at a discount for %s doubloons";
                case UNNOTICED -> "Sold %s plundered %s for %s doubloons, nobody noticed";
                case NOTICED_SOLD -> "Sold %s %s for %s doubloons, but the port noticed the plunder";
                case CONFISCATED -> "The port noticed the plunder and confiscated %s %s (%s doubloons)";
            });
        }
        for (DeliveryContract.Outcome o : DeliveryContract.Outcome.values()) {
            lang.add(contractOutcome(o), switch (o) {
                case ACCEPTED -> "accepted";
                case DELIVERED -> "delivered";
                case FAILED -> "the deadline has passed";
                case EXPIRED -> "the offer has expired";
                case ABANDONED -> "abandoned";
                case UNCHANGED -> "nothing changed";
                case NOT_OFFERED -> "no longer offered";
                case NOT_ACCEPTED -> "not accepted";
                case WRONG_HOLDER -> "held by someone else";
                case WRONG_PORT -> "deliver it at its destination";
                case NOT_ENOUGH -> "not enough goods";
                case TOO_MANY -> "you hold too many contracts";
            });
        }
    }
}
