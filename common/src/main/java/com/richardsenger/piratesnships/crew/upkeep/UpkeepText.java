package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.Constants;
import java.util.LinkedHashMap;
import java.util.Map;

/** Text keys of crew upkeep (CR2) and their English text, written to the lang file by {@code ProvisionsModule}. */
public final class UpkeepText {

    static final String KEY = "message." + Constants.MOD_ID + ".upkeep.";
    /** Owner, action bar: the crew is out of food. */
    public static final String NO_FOOD = KEY + "no_food";
    /** Owner, action bar: the crew is out of water. */
    public static final String NO_WATER = KEY + "no_water";
    /** Owner, action bar: scurvy aboard. */
    public static final String SCURVY = KEY + "scurvy";
    /** Owner, action bar: "Paid %s crew, %s doubloons". */
    public static final String PAID = KEY + "paid";
    /** Owner, action bar: "Paid %s crew: %s doubloons, %s yours" (CRW2). */
    public static final String PAID_WALLET = KEY + "paid_wallet";
    /** Owner, chat when not aboard: "Wages from your purse: %s doubloons" (CRW2). */
    public static final String WALLET_PAID = KEY + "wallet_paid";
    /** Owner, action bar: "Could not pay %s crew". */
    public static final String UNPAID = KEY + "unpaid";
    /** Joins two owner lines into one action-bar line. */
    public static final String JOIN = KEY + "join";
    /** Owner, chat: "%s has deserted". */
    public static final String DESERTED = KEY + "deserted";
    /** Owner, chat: "%s means to desert: off at the next port" (CRW2). */
    public static final String DESERTING = KEY + "deserting";
    /** Owner, chat: "%s has deserted: too long without %s" (FIX1: name, {@link #WITHOUT_FOOD} or {@link #WITHOUT_WATER}). */
    public static final String DESERTED_STARVED = KEY + "deserted_starved";
    /** Owner, chat: "%s means to desert after too long without %s: off at the next port" (FIX1). */
    public static final String DESERTING_STARVED = KEY + "deserting_starved";
    /** "food", in {@link #DESERTED_STARVED} and {@link #DESERTING_STARVED}. */
    public static final String WITHOUT_FOOD = KEY + "without.food";
    /** "water", in {@link #DESERTED_STARVED} and {@link #DESERTING_STARVED}. */
    public static final String WITHOUT_WATER = KEY + "without.water";
    /** Owner, chat: "%s walked off at %s" (CRW2: name, port). */
    public static final String WALKED_OFF = KEY + "walked_off";
    /** Owner, chat: "%s changed their mind and stays aboard" (CRW2). */
    public static final String STAYS = KEY + "stays";
    /** Owner, chat: "Mutiny aboard %s!". */
    public static final String MUTINY = KEY + "mutiny";
    /** The ship's name when it has none. */
    public static final String UNNAMED_SHIP = KEY + "unnamed_ship";

    /** {@code /pirates crew info}: "Supplies: food %s days, water %s days, rum %s days". */
    public static final String INFO_SUPPLIES = KEY + "info.supplies";
    /** {@code /pirates crew info}: "Last pay: %s paid, %s unpaid, %s doubloons". */
    public static final String INFO_PAY = KEY + "info.pay";
    /** {@code /pirates crew info}: "Last pay: %s paid, %s unpaid, %s doubloons, %s from the owner's purse" (CRW2). */
    public static final String INFO_PAY_WALLET = KEY + "info.pay_wallet";
    public static final String INFO_PAY_NONE = KEY + "info.pay_none";
    public static final String INFO_PAY_OFF = KEY + "info.pay_off";
    /** {@code /pirates crew info}: "Work speed %s%%" with the reasons. */
    public static final String INFO_WORK = KEY + "info.work";
    /** Infinite supplies (nobody eats, or consumption is off). */
    public static final String INFO_PLENTY = KEY + "info.plenty";
    /** Appended to the whistle's crew line: ", unpaid". */
    public static final String STATUS_UNPAID = KEY + "status.unpaid";
    /** Appended to the whistle's crew line: ", deserting" (CRW2). */
    public static final String STATUS_DESERTING = KEY + "status.deserting";
    /** The whistle's crew line: "%s · hired by %s" (CRW2). */
    public static final String STATUS_HIRED_BY = KEY + "status.hired_by";
    /** The whistle's ship line: "%s · food %s days, water %s days" (CRW2). */
    public static final String STATUS_SUPPLIES = KEY + "status.supplies";

    public static final Map<String, String> LANG = lang();

    private UpkeepText() {
    }

    private static Map<String, String> lang() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(NO_FOOD, "No food aboard");
        m.put(NO_WATER, "No water aboard");
        m.put(SCURVY, "Scurvy! The crew needs fresh food");
        m.put(PAID, "Paid %s crew, %s doubloons");
        m.put(PAID_WALLET, "Paid %s crew: %s doubloons, %s yours");
        m.put(WALLET_PAID, "Wages from your purse: %s doubloons");
        m.put(UNPAID, "Could not pay %s crew");
        m.put(JOIN, "%s · %s");
        m.put(DESERTED, "%s has deserted");
        m.put(DESERTING, "%s means to desert and will walk off at the next port");
        m.put(DESERTED_STARVED, "%s has deserted: too long without %s");
        m.put(DESERTING_STARVED, "%s means to desert after too long without %s and will walk off at the next port");
        m.put(WITHOUT_FOOD, "food");
        m.put(WITHOUT_WATER, "water");
        m.put(WALKED_OFF, "%s walked off at %s");
        m.put(STAYS, "%s has changed their mind and stays aboard");
        m.put(MUTINY, "Mutiny aboard %s!");
        m.put(UNNAMED_SHIP, "your ship");
        m.put(INFO_SUPPLIES, "Supplies: food %s days, water %s days, rum %s days");
        m.put(INFO_PAY, "Last pay: %s paid, %s unpaid, %s doubloons");
        m.put(INFO_PAY_WALLET, "Last pay: %s paid, %s unpaid, %s doubloons, %s of them from the owner's purse");
        m.put(INFO_PAY_NONE, "Last pay: no payday yet");
        m.put(INFO_PAY_OFF, "Last pay: wages are off");
        m.put(INFO_WORK, "Work speed %s%%");
        m.put(INFO_PLENTY, "plenty");
        m.put(STATUS_UNPAID, "%s, unpaid");
        m.put(STATUS_DESERTING, "%s, deserting");
        m.put(STATUS_HIRED_BY, "%s · hired by %s");
        m.put(STATUS_SUPPLIES, "%s · food %s days, water %s days");
        return Map.copyOf(m);
    }
}
