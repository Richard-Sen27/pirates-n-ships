package com.richardsenger.piratesnships.ship.screen;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import java.util.Locale;

/**
 * Text keys of the ship screen (HGUI1) and their English text. No client classes, so datagen writes the lang entries
 * ({@link ShipScreenModule}) and the server can name its messages.
 */
public final class ShipScreenText {

    static final String KEY = Constants.MOD_ID + ".ship_screen.";
    static final String MSG = "message." + Constants.MOD_ID + ".ship_screen.";

    // server messages
    public static final String NOT_YOURS = MSG + "not_yours";
    public static final String DISABLED = MSG + "disabled";
    public static final String GONE = MSG + "gone";
    public static final String TOO_FAR = MSG + "too_far";
    public static final String STALE = MSG + "stale";
    public static final String NO_CREW = MSG + "no_crew";
    public static final String NOT_AT_STATION = MSG + "not_at_station";
    public static final String NOT_YOUR_HAND = MSG + "not_your_hand";
    public static final String BAD_NAME = MSG + "bad_name";
    public static final String UNKNOWN_ORDER = MSG + "unknown_order";
    public static final String NOT_ON_THIS_SHIP = MSG + "not_on_this_ship";

    // the screen
    public static final String TITLE = KEY + "title";
    public static final String TAB_SHIP = KEY + "tab.ship";
    public static final String TAB_CREW = KEY + "tab.crew";
    public static final String TAB_STATIONS = KEY + "tab.stations";
    public static final String LOADING = KEY + "loading";
    public static final String UNNAMED = KEY + "unnamed";
    public static final String NAME_HINT = KEY + "name_hint";
    public static final String RENAME = KEY + "rename";
    public static final String RENAME_TIP = KEY + "rename_tip";
    public static final String LABEL_NAME = KEY + "label.name";
    public static final String LABEL_FLAG = KEY + "label.flag";
    public static final String LABEL_CAPTAIN = KEY + "label.captain";
    public static final String LABEL_HULL = KEY + "label.hull";
    public static final String LABEL_LOAD = KEY + "label.load";
    public static final String LABEL_ANCHOR = KEY + "label.anchor";
    public static final String LABEL_SAILS = KEY + "label.sails";
    public static final String LABEL_CREW = KEY + "label.crew";
    public static final String LABEL_SUPPLIES = KEY + "label.supplies";
    public static final String LABEL_PAY = KEY + "label.pay";
    /** "%s, %s" (allegiance, cover blown). */
    public static final String COVER_BLOWN = KEY + "cover_blown";
    public static final String CAPTAIN_NONE = KEY + "captain_none";
    public static final String CAPTAIN_UNKNOWN = KEY + "captain_unknown";
    /** "%s %s" (title, name). */
    public static final String CAPTAIN_TITLED = KEY + "captain_titled";
    /** "%s compartments, %s flooding, %s breaches, %s pumps working". */
    public static final String HULL = KEY + "hull";
    /** "%s compartments, dry". */
    public static final String HULL_DRY = KEY + "hull_dry";
    public static final String HULL_UNKNOWN = KEY + "hull_unknown";
    /** "%s, %s" (load level, speed). */
    public static final String LOAD_SPEED = KEY + "load_speed";
    public static final String LOAD_UNKNOWN = KEY + "load_unknown";
    /** "%s sails: %s full, %s reefed, %s furled". */
    public static final String SAILS = KEY + "sails";
    public static final String SAILS_NONE = KEY + "sails_none";
    /** "%s aboard, %s bunks". */
    public static final String CREW_COUNT = KEY + "crew_count";
    /** "food %s, water %s, rum %s days". */
    public static final String SUPPLIES = KEY + "supplies";
    public static final String PLENTY = KEY + "plenty";
    /** "%s paid, %s unpaid, %s doubloons". */
    public static final String PAY = KEY + "pay";
    public static final String PAY_NONE = KEY + "pay_none";
    public static final String PAY_OFF = KEY + "pay_off";
    public static final String DISASSEMBLE = KEY + "disassemble";
    public static final String DISASSEMBLE_CONFIRM = KEY + "disassemble_confirm";
    public static final String ORDERS = KEY + "orders";
    /** "Crew aboard (%s)". */
    public static final String CREW_HEADING = KEY + "crew_heading";
    public static final String NO_CREW_ABOARD = KEY + "no_crew_aboard";
    /** "morale %s". */
    public static final String MORALE = KEY + "morale";
    /** "%s: %s" (station, order). */
    public static final String AT_STATION_DOING = KEY + "at_station_doing";
    /** "hired by %s". */
    public static final String HIRED_BY = KEY + "hired_by";
    public static final String HIRED_BY_YOU = KEY + "hired_by_you";
    public static final String UNPAID = KEY + "unpaid";
    /** "low morale, %s dawns". */
    public static final String DESERTION_LOW = KEY + "desertion_low";
    public static final String DESERTION_LEAVING = KEY + "desertion_leaving";
    public static final String RELEASE = KEY + "release";
    public static final String DISMISS = KEY + "dismiss";
    public static final String MAN = KEY + "man";
    public static final String SEND = KEY + "send";
    public static final String CANCEL = KEY + "cancel";
    public static final String TIP_STATIONS_OFF = KEY + "tip.stations_off";
    public static final String TIP_HIRING_OFF = KEY + "tip.hiring_off";
    public static final String TIP_NOT_YOUR_HAND = KEY + "tip.not_your_hand";
    public static final String TIP_NO_FREE_HAND = KEY + "tip.no_free_hand";
    public static final String TIP_NOT_CAPTAIN = KEY + "tip.not_captain";
    public static final String TIP_PLAYER_AT_STATION = KEY + "tip.player_at_station";
    /** "Stations aboard (%s)". */
    public static final String STATIONS_HEADING = KEY + "stations_heading";
    public static final String NO_STATIONS = KEY + "no_stations";
    /** "manned by %s". */
    public static final String MANNED_BY = KEY + "manned_by";
    public static final String UNMANNED = KEY + "unmanned";
    /** "open job: %s". */
    public static final String JOB_OPEN = KEY + "job_open";
    /** "Send whom to the %s?". */
    public static final String PICK = KEY + "pick";

    private ShipScreenText() {
    }

    public static String anchor(ShipScreenRules.Anchor a) {
        return KEY + "anchor." + a.name().toLowerCase(Locale.ROOT);
    }

    public static String allegiance(ShipScreenRules.Allegiance a) {
        return KEY + "allegiance." + a.name().toLowerCase(Locale.ROOT);
    }

    public static void lang(LangBuilder lang) {
        lang.add(NOT_YOURS, "This is not your ship: only her captain manages her at the helm")
                .add(DISABLED, "The ship screen is turned off on this server")
                .add(GONE, "The ship is gone")
                .add(TOO_FAR, "You stepped away from the helm")
                .add(STALE, "This screen shows another ship")
                .add(NO_CREW, "That hand is no longer aboard")
                .add(NOT_AT_STATION, "%s mans no station")
                .add(NOT_YOUR_HAND, "Only the captain or whoever hired %s can order them about")
                .add(BAD_NAME, "Type a name first")
                .add(UNKNOWN_ORDER, "No such order")
                .add(NOT_ON_THIS_SHIP, "That station is not on this ship")
                .add(TITLE, "Ship")
                .add(TAB_SHIP, "Ship")
                .add(TAB_CREW, "Crew")
                .add(TAB_STATIONS, "Stations")
                .add(LOADING, "Reading the ship's log...")
                .add(UNNAMED, "Unnamed ship")
                .add(NAME_HINT, "Ship name")
                .add(RENAME, "Rename")
                .add(RENAME_TIP, "Your title goes in front of the name, as with a name tag")
                .add(LABEL_NAME, "Name")
                .add(LABEL_FLAG, "Flag")
                .add(LABEL_CAPTAIN, "Captain")
                .add(LABEL_HULL, "Hull")
                .add(LABEL_LOAD, "Load")
                .add(LABEL_ANCHOR, "Anchor")
                .add(LABEL_SAILS, "Sails")
                .add(LABEL_CREW, "Crew")
                .add(LABEL_SUPPLIES, "Supplies")
                .add(LABEL_PAY, "Last pay")
                .add(COVER_BLOWN, "%s, the navy has seen through it")
                .add(CAPTAIN_NONE, "none: anyone may command her")
                .add(CAPTAIN_UNKNOWN, "unknown")
                .add(CAPTAIN_TITLED, "%s %s")
                .add(HULL, "%s compartments, %s flooding, %s breaches, %s pumps working")
                .add(HULL_DRY, "%s compartments, dry")
                .add(HULL_UNKNOWN, "not surveyed yet")
                .add(LOAD_SPEED, "%s, %s")
                .add(LOAD_UNKNOWN, "not weighed yet")
                .add(SAILS, "%s: %s full, %s reefed, %s furled")
                .add(SAILS_NONE, "none")
                .add(CREW_COUNT, "%s aboard, %s bunks")
                .add(SUPPLIES, "food %s, water %s, rum %s days")
                .add(PLENTY, "plenty")
                .add(PAY, "%s paid, %s unpaid, %s doubloons")
                .add(PAY_NONE, "no payday yet")
                .add(PAY_OFF, "wages are off")
                .add(DISASSEMBLE, "Disassemble")
                .add(DISASSEMBLE_CONFIRM, "Sure? Click again")
                .add(ORDERS, "Orders to the whole crew")
                .add(CREW_HEADING, "Crew aboard (%s)")
                .add(NO_CREW_ABOARD, "No crew aboard. Hire hands at a harbor desk")
                .add(MORALE, "morale %s")
                .add(AT_STATION_DOING, "%s: %s")
                .add(HIRED_BY, "hired by %s")
                .add(HIRED_BY_YOU, "hired by you")
                .add(UNPAID, "unpaid")
                .add(DESERTION_LOW, "low morale, %s dawns")
                .add(DESERTION_LEAVING, "will desert at dawn")
                .add(RELEASE, "Release")
                .add(DISMISS, "Dismiss")
                .add(MAN, "Man")
                .add(SEND, "Send")
                .add(CANCEL, "Cancel")
                .add(TIP_STATIONS_OFF, "Crew stations are turned off on this server")
                .add(TIP_HIRING_OFF, "Dismissal is turned off together with hiring")
                .add(TIP_NOT_YOUR_HAND, "Only the captain or whoever hired them")
                .add(TIP_NO_FREE_HAND, "No free hand to send")
                .add(TIP_NOT_CAPTAIN, "Only the captain")
                .add(TIP_PLAYER_AT_STATION, "A player mans it")
                .add(STATIONS_HEADING, "Stations aboard (%s)")
                .add(NO_STATIONS, "No stations aboard")
                .add(MANNED_BY, "manned by %s")
                .add(UNMANNED, "unmanned")
                .add(JOB_OPEN, "open job: %s")
                .add(PICK, "Send whom to the %s?");
        lang.add(anchor(ShipScreenRules.Anchor.UNKNOWN), "unknown")
                .add(anchor(ShipScreenRules.Anchor.STOWED), "stowed")
                .add(anchor(ShipScreenRules.Anchor.DROPPING), "running out")
                .add(anchor(ShipScreenRules.Anchor.DOWN), "down, the ship still moves")
                .add(anchor(ShipScreenRules.Anchor.ANCHORED), "anchored")
                .add(anchor(ShipScreenRules.Anchor.RAISING), "weighing");
        lang.add(allegiance(ShipScreenRules.Allegiance.NONE), "no flag")
                .add(allegiance(ShipScreenRules.Allegiance.MERCHANT), "merchant flag: neutral")
                .add(allegiance(ShipScreenRules.Allegiance.NAVY), "navy flag: friend of the navy")
                .add(allegiance(ShipScreenRules.Allegiance.PIRATE), "Jolly Roger: friend of the pirates")
                .add(allegiance(ShipScreenRules.Allegiance.CUSTOM), "banner flag: neutral")
                .add(allegiance(ShipScreenRules.Allegiance.STRUCK), "colours struck: surrendered");
    }
}
