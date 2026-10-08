package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;

import java.util.Locale;

/** Translation keys of the careers module and their English text (datagen, from {@code CareerModule.gatherData}). */
public final class CareerText {

    public static final String MSG = "message." + Constants.MOD_ID + ".career.";
    public static final String CMD = "commands." + Constants.MOD_ID + ".career.";
    public static final String GUI = "gui." + Constants.MOD_ID + ".career.";

    public static final String PROMOTED_NAVY = MSG + "promoted.navy";
    public static final String PROMOTED_INFAMY = MSG + "promoted.infamy";
    public static final String ENLISTED = MSG + "enlisted";
    public static final String RESIGNED = MSG + "resigned";
    public static final String NOT_ENLISTED = MSG + "not_enlisted";
    public static final String DESERTED = MSG + "deserted";
    public static final String DESERTED_CRIME = MSG + "deserted.crime";
    public static final String LETTER_GRANTED = MSG + "letter.granted";
    public static final String LETTER_VOIDED = MSG + "letter.voided";
    public static final String PRIZE_EARNED = MSG + "prize.earned";
    public static final String PRIZE_COLLECTED = MSG + "prize.collected";
    public static final String PRIZE_NONE = MSG + "prize.none";
    public static final String HOSTILE = MSG + "hostile";
    public static final String OFFICER_GONE = MSG + "officer_gone";
    public static final String DISABLED = MSG + "disabled";

    public static final String TITLE = GUI + "title";
    public static final String NAVY_RANK = GUI + "navy_rank";
    public static final String NAVY_NONE = GUI + "navy_none";
    public static final String INFAMY_RANK = GUI + "infamy_rank";
    public static final String NEXT = GUI + "next";
    public static final String TOP = GUI + "top";
    public static final String NO_INFAMY_IN_SERVICE = GUI + "no_infamy_in_service";
    public static final String LETTER = GUI + "letter";
    public static final String LETTER_BLOCKED = GUI + "letter_blocked";
    public static final String PRIZE = GUI + "prize";
    public static final String REPUTATION = GUI + "reputation";
    public static final String ENLIST = GUI + "enlist";
    public static final String RESIGN = GUI + "resign";
    public static final String REQUEST_LETTER = GUI + "request_letter";
    public static final String COLLECT_PRIZE = GUI + "collect_prize";

    public static final String DESCRIBE = CMD + "describe";
    public static final String SHOW = CMD + "show";
    public static final String SHOW_OFF = CMD + "show.off";
    public static final String COUNTERS = CMD + "counters";
    public static final String SET_NAVY = CMD + "set.navy";
    public static final String SET_INFAMY = CMD + "set.infamy";
    public static final String LETTER_SET = CMD + "letter.set";
    public static final String UNKNOWN_RANK = CMD + "unknown_rank";

    private CareerText() {
    }

    public static String requirement(String key) {
        return GUI + "req." + key;
    }

    public static String enlistVerdict(CareerRules.EnlistVerdict v) {
        return MSG + "enlist." + v.name().toLowerCase(Locale.ROOT);
    }

    public static String letterVerdict(CareerRules.LetterVerdict v) {
        return MSG + "letter." + v.name().toLowerCase(Locale.ROOT);
    }

    public static void lang(LangBuilder lang) {
        for (NavyRank r : NavyRank.values()) {
            lang.add(r.nameKey(), switch (r) {
                case NONE -> "None";
                case MIDSHIPMAN -> "Midshipman";
                case LIEUTENANT -> "Lieutenant";
                case CAPTAIN -> "Captain";
                case COMMODORE -> "Commodore";
                case ADMIRAL -> "Admiral";
            });
        }
        for (InfamyRank r : InfamyRank.values()) {
            lang.add(r.nameKey(), switch (r) {
                case DECKHAND -> "Deckhand";
                case BUCCANEER -> "Buccaneer";
                case DREAD_CAPTAIN -> "Dread Captain";
                case PIRATE_LORD -> "Pirate Lord";
            });
        }
        for (LetterState s : LetterState.values()) {
            lang.add(s.nameKey(), switch (s) {
                case NONE -> "none";
                case ACTIVE -> "held";
                case VOIDED -> "void";
            });
        }
        for (CareerCounter c : CareerCounter.values()) {
            lang.add(c.nameKey(), switch (c) {
                case PIRATES_KILLED -> "Pirates killed";
                case CAPTAINS_KILLED -> "Pirate captains killed";
                case PIRATES_TURNED_IN -> "Pirates turned in";
                case NAVY_KILLED -> "Navy killed";
                case OFFICERS_KILLED -> "Navy officers killed";
                case MERCHANTS_PLUNDERED -> "Merchants plundered";
                case PLUNDER_COINS -> "Plunder fenced (doubloons)";
                case BOUNTIES_CLAIMED -> "Bounties claimed";
                case NAVY_QUESTS -> "Navy quests";
                case PIRATE_QUESTS -> "Pirate quests";
                case VILLAGE_QUESTS -> "Village quests";
                case SHIPS_CAPTURED -> "Ships captured";
            });
        }
        for (CareerRules.EnlistVerdict v : CareerRules.EnlistVerdict.values()) {
            lang.add(enlistVerdict(v), switch (v) {
                case OK -> "You may enlist";
                case DISABLED -> "The navy is not recruiting (careers are off on this server)";
                case ALREADY_ENLISTED -> "You already serve in the navy";
                case INFAMOUS -> "The navy does not take a known pirate";
                case PIRATE_FRIEND -> "The navy does not trust a friend of the pirates";
                case WANTED -> "There is a bounty on your head";
                case HOSTILE -> "The navy wants nothing to do with you";
                case LOW_STANDING -> "Your navy reputation is too low (need %s)";
            });
        }
        for (CareerRules.LetterVerdict v : CareerRules.LetterVerdict.values()) {
            lang.add(letterVerdict(v), switch (v) {
                case OK -> "You may request a letter of marque";
                case DISABLED -> "The navy grants no letters of marque on this server";
                case ENLISTED -> "Navy officers need no letter of marque";
                case ALREADY_HELD -> "You already hold a letter of marque";
                case BLOCKED -> "Your last letter was voided; ask again in %s days";
                case INFAMOUS -> "The navy grants no letter to a pirate of your infamy";
                case LOW_STANDING -> "Your navy reputation is too low (need %s)";
                case TOO_POOR -> "A letter of marque costs %s doubloons";
            });
        }
        lang.add(PROMOTED_NAVY, "You have been promoted to %s!")
                .add(PROMOTED_INFAMY, "Your infamy grows: they call you %s now.")
                .add(ENLISTED, "You have enlisted in the navy as %s.")
                .add(RESIGNED, "You have left the navy's service.")
                .add(NOT_ENLISTED, "You do not serve in the navy.")
                .add(DESERTED, "Desertion! You have lost your navy rank.")
                .add(DESERTED_CRIME, "Desertion! You have lost your navy rank, and the navy wants you for it.")
                .add(LETTER_GRANTED, "You now hold a letter of marque (paid %s doubloons). Every pirate you kill earns prize money.")
                .add(LETTER_VOIDED, "Your letter of marque is void.")
                .add(PRIZE_EARNED, "Prize money: +%s doubloons (%s waiting at any navy officer)")
                .add(PRIZE_COLLECTED, "Prize money paid: %s doubloons.")
                .add(PRIZE_NONE, "No prize money is owed to you.")
                .add(HOSTILE, "The officer will not speak with you.")
                .add(OFFICER_GONE, "The officer is gone or too far away.")
                .add(DISABLED, "Careers are off on this server.");
        lang.add(TITLE, "Navy Officer")
                .add(NAVY_RANK, "Navy rank: %s")
                .add(NAVY_NONE, "Navy rank: not in service")
                .add(INFAMY_RANK, "Infamy: %s")
                .add(NEXT, "Next: %s")
                .add(TOP, "The highest rank")
                .add(NO_INFAMY_IN_SERVICE, "No infamy while in navy service")
                .add(LETTER, "Letter of marque: %s")
                .add(LETTER_BLOCKED, "Letter of marque: void (new one in %s days)")
                .add(PRIZE, "Prize money waiting: %s")
                .add(REPUTATION, "Reputation: navy %s, pirates %s")
                .add(ENLIST, "Enlist")
                .add(RESIGN, "Resign")
                .add(REQUEST_LETTER, "Letter (%s)")
                .add(COLLECT_PRIZE, "Collect prize")
                .add(requirement("navy_rep"), "navy reputation %s/%s")
                .add(requirement("pirates_defeated"), "pirates defeated %s/%s")
                .add(requirement("navy_quests"), "navy quests %s/%s")
                .add(requirement("pirate_rep"), "pirate reputation %s/%s")
                .add(requirement("plunder_coins"), "plunder fenced %s/%s")
                .add(requirement("captures"), "captures %s/%s");
        lang.add(DESCRIBE, "Career: navy %s, infamy %s, letter of marque %s")
                .add(SHOW, "Career of %s: navy %s, infamy %s, letter of marque %s, prize money %s")
                .add(SHOW_OFF, "Career of %s: navy %s, infamy %s, letter of marque %s, prize money %s (careers are off on this server)")
                .add(COUNTERS, "  %s")
                .add(SET_NAVY, "Set the navy rank of %s to %s")
                .add(SET_INFAMY, "Set the infamy of %s to %s")
                .add(LETTER_SET, "Letter of marque of %s: %s")
                .add(UNKNOWN_RANK, "Unknown rank");
    }
}
