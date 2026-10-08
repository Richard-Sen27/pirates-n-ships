package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;

/**
 * Text keys of hiring (CRW1) and their English text: the results of a hire or dismissal, the Crew tab of the market
 * screen and the kinds. No client classes, so datagen can write the lang entries ({@code HiringModule}).
 */
public final class HiringText {

    static final String KEY = "message." + Constants.MOD_ID + ".hiring.";
    static final String RESULT = KEY + "result.";

    // results (Hiring.Result#key); every text gets the candidate's or crew member's name as %1$s
    public static final String HIRED = "hired";
    public static final String DISABLED = "disabled";
    public static final String NO_SESSION = "no_session";
    public static final String NO_PORT = "no_port";
    public static final String UNKNOWN = "unknown";
    public static final String WRONG_PORT = "wrong_port";
    public static final String VILLAGERS_REFUSE = "villagers_refuse";
    public static final String PIRATES_DISTRUST = "pirates_distrust";
    public static final String NOT_ENLISTED = "not_enlisted";
    public static final String NO_SHIP = "no_ship";
    public static final String NO_BUNK = "no_bunk";
    public static final String NO_COINS = "no_coins";
    public static final String NO_ROOM = "no_room";
    public static final String DISMISSED = "dismissed";
    public static final String NOT_YOURS = "not_yours";
    public static final String NOT_CREW = "not_crew";

    // the Crew tab
    public static final String CANDIDATES = KEY + "tab.candidates";
    public static final String NO_CANDIDATES = KEY + "tab.no_candidates";
    /** "%1$s, fee %2$s, wage %3$s a day". */
    public static final String DETAIL = KEY + "tab.detail";
    public static final String HIRE = KEY + "tab.hire";
    /** "Your ship: %1$s, crew %2$s / %3$s". */
    public static final String SHIP = KEY + "tab.ship";
    public static final String SHIP_UNNAMED = KEY + "tab.ship_unnamed";
    public static final String NO_SHIP_HERE = KEY + "tab.no_ship_here";
    public static final String SHIP_FULL = KEY + "tab.ship_full";
    public static final String TOO_POOR = KEY + "tab.too_poor";

    private HiringText() {
    }

    /** The translation key of a result id. */
    public static String result(String id) {
        return RESULT + id;
    }

    /** The translation key of a candidate kind's name. */
    public static String kind(CandidateKind kind) {
        return KEY + "kind." + kind.id();
    }

    public static void lang(LangBuilder lang) {
        lang.add(result(HIRED), "%s signed on and is aboard your ship")
                .add(result(DISABLED), "Hiring is turned off")
                .add(result(NO_SESSION), "Step up to the harbor desk first")
                .add(result(NO_PORT), "This desk belongs to no known port")
                .add(result(UNKNOWN), "That candidate has found another berth")
                .add(result(WRONG_PORT), "%s does not sign on at this port")
                .add(result(VILLAGERS_REFUSE), "The villagers won't sign on with you")
                .add(result(PIRATES_DISTRUST), "Pirates sign on only with a friend of the pirates or a captain of some infamy")
                .add(result(NOT_ENLISTED), "Navy ratings sign on only with an enlisted officer")
                .add(result(NO_SHIP), "Moor your ship at this port first")
                .add(result(NO_BUNK), "No free hammock aboard")
                .add(result(NO_COINS), "%s asks more doubloons than you carry")
                .add(result(NO_ROOM), "No room on deck for %s")
                .add(result(DISMISSED), "%s is dismissed")
                .add(result(NOT_YOURS), "Only the captain or the one who hired %s can dismiss them")
                .add(result(NOT_CREW), "%s is no crew member")
                .add(CANDIDATES, "Looking for a berth")
                .add(NO_CANDIDATES, "Nobody is looking for a berth today")
                .add(DETAIL, "%s, fee %s, wage %s a day")
                .add(HIRE, "Hire")
                .add(SHIP, "Your ship: %s, crew %s / %s")
                .add(SHIP_UNNAMED, "unnamed")
                .add(NO_SHIP_HERE, "No ship of yours is moored here")
                .add(SHIP_FULL, "No free hammock aboard")
                .add(TOO_POOR, "Not enough doubloons")
                .add(kind(CandidateKind.SAILOR), "Sailor")
                .add(kind(CandidateKind.PIRATE), "Pirate")
                .add(kind(CandidateKind.NAVY), "Navy rating");
    }
}
