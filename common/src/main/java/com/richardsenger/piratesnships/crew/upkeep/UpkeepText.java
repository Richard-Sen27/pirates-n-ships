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
    /** Owner, action bar: "Could not pay %s crew". */
    public static final String UNPAID = KEY + "unpaid";
    /** Joins two owner lines into one action-bar line. */
    public static final String JOIN = KEY + "join";
    /** Owner, chat: "%s has deserted". */
    public static final String DESERTED = KEY + "deserted";
    /** Owner, chat: "Mutiny aboard %s!". */
    public static final String MUTINY = KEY + "mutiny";
    /** The ship's name when it has none. */
    public static final String UNNAMED_SHIP = KEY + "unnamed_ship";
    /** Said by an unpaid crew member. */
    public static final String SAY_UNPAID = KEY + "say.unpaid";
    /** Said by a deserter. */
    public static final String SAY_DESERT = KEY + "say.desert";
    /** Said by the mutineers. */
    public static final String SAY_MUTINY = KEY + "say.mutiny";

    /** {@code /pirates crew info}: "Supplies: food %s days, water %s days, rum %s days". */
    public static final String INFO_SUPPLIES = KEY + "info.supplies";
    /** {@code /pirates crew info}: "Last pay: %s paid, %s unpaid, %s doubloons". */
    public static final String INFO_PAY = KEY + "info.pay";
    public static final String INFO_PAY_NONE = KEY + "info.pay_none";
    public static final String INFO_PAY_OFF = KEY + "info.pay_off";
    /** {@code /pirates crew info}: "Work speed %s%%" with the reasons. */
    public static final String INFO_WORK = KEY + "info.work";
    /** Infinite supplies (nobody eats, or consumption is off). */
    public static final String INFO_PLENTY = KEY + "info.plenty";
    /** Appended to the whistle's crew line: ", unpaid". */
    public static final String STATUS_UNPAID = KEY + "status.unpaid";

    public static final Map<String, String> LANG = lang();

    private UpkeepText() {
    }

    private static Map<String, String> lang() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(NO_FOOD, "The crew has no food");
        m.put(NO_WATER, "The crew has no water");
        m.put(SCURVY, "Scurvy aboard: the crew needs citrus or fresh food");
        m.put(PAID, "Paid %s crew, %s doubloons");
        m.put(UNPAID, "Could not pay %s crew");
        m.put(JOIN, "%s · %s");
        m.put(DESERTED, "%s has deserted");
        m.put(MUTINY, "Mutiny aboard %s!");
        m.put(UNNAMED_SHIP, "your ship");
        m.put(SAY_UNPAID, "No pay again? A sailor can't live on promises.");
        m.put(SAY_DESERT, "I've had enough of this ship. I'm off.");
        m.put(SAY_MUTINY, "The ship is ours now!");
        m.put(INFO_SUPPLIES, "Supplies: food %s days, water %s days, rum %s days");
        m.put(INFO_PAY, "Last pay: %s paid, %s unpaid, %s doubloons");
        m.put(INFO_PAY_NONE, "Last pay: no payday yet");
        m.put(INFO_PAY_OFF, "Last pay: wages are off");
        m.put(INFO_WORK, "Work speed %s%%");
        m.put(INFO_PLENTY, "plenty");
        m.put(STATUS_UNPAID, "%s, unpaid");
        return Map.copyOf(m);
    }
}
