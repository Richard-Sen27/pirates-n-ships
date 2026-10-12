package com.richardsenger.piratesnships.core.advancement;

import java.util.List;

/**
 * The ids ({@code pirates_n_ships:<id>}) of the mod's criterion triggers (ADV1), one per event family. Pure, so the
 * JUnit check of the generated advancements can read them; {@link ModTriggers} registers one {@link ModTrigger} per id.
 * The {@code kind} and {@code value} each family reports are listed in {@code docs/advancements.md}.
 */
public final class TriggerIds {

    // ships and sailing
    public static final String SHIP_ASSEMBLED = "ship_assembled";
    public static final String SAIL_SET = "sail_set";
    public static final String SAILED_WITH_WIND = "sailed_with_wind";
    public static final String ANCHOR_DROPPED = "anchor_dropped";
    public static final String LEAK_FIXED = "leak_fixed";
    public static final String SHIP_ORDERED = "ship_ordered";
    public static final String SHIP_LAUNCHED = "ship_launched";
    public static final String FLAG_HOISTED = "flag_hoisted";
    // crew
    public static final String CREW_HIRED = "crew_hired";
    public static final String ORDER_GIVEN = "order_given";
    public static final String HAMMOCK_NIGHT = "hammock_night";
    public static final String WAGES_PAID = "wages_paid";
    public static final String DESERTER_RETURNED = "deserter_returned";
    public static final String CREW_LOYAL = "crew_loyal";
    // trade
    public static final String DOUBLOONS_EARNED = "doubloons_earned";
    public static final String CONTRACT_DELIVERED = "contract_delivered";
    public static final String CONVOY_ESCORTED = "convoy_escorted";
    public static final String QUEST_COMPLETED = "quest_completed";
    // combat
    public static final String CANNON_FIRED = "cannon_fired";
    public static final String SHIP_HIT = "ship_hit";
    public static final String SHIP_BOARDED = "ship_boarded";
    public static final String SHIP_CAPTURED = "ship_captured";
    public static final String GRAPPLE_LATCHED = "grapple_latched";
    // the world
    public static final String PORT_VISITED = "port_visited";
    public static final String TREASURE_DUG = "treasure_dug";
    public static final String STORM_WEATHERED = "storm_weathered";
    public static final String MARKERS_PLACED = "markers_placed";
    // the law and careers
    public static final String BOUNTY_PAID = "bounty_paid";
    public static final String PRISONER_TAKEN = "prisoner_taken";
    public static final String RANK_REACHED = "rank_reached";
    // the Almanac
    public static final String ALMANAC_OPENED = "almanac_opened";
    public static final String SCENE_OPENED = "scene_opened";
    public static final String CHAPTERS_READ = "chapters_read";

    /** Every id, in registration order. */
    public static final List<String> ALL = List.of(
            SHIP_ASSEMBLED, SAIL_SET, SAILED_WITH_WIND, ANCHOR_DROPPED, LEAK_FIXED, SHIP_ORDERED, SHIP_LAUNCHED, FLAG_HOISTED,
            CREW_HIRED, ORDER_GIVEN, HAMMOCK_NIGHT, WAGES_PAID, DESERTER_RETURNED, CREW_LOYAL,
            DOUBLOONS_EARNED, CONTRACT_DELIVERED, CONVOY_ESCORTED, QUEST_COMPLETED,
            CANNON_FIRED, SHIP_HIT, SHIP_BOARDED, SHIP_CAPTURED, GRAPPLE_LATCHED,
            PORT_VISITED, TREASURE_DUG, STORM_WEATHERED, MARKERS_PLACED,
            BOUNTY_PAID, PRISONER_TAKEN, RANK_REACHED,
            ALMANAC_OPENED, SCENE_OPENED, CHAPTERS_READ);

    private TriggerIds() {
    }
}
