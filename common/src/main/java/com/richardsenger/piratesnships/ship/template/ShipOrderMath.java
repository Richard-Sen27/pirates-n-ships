package com.richardsenger.piratesnships.ship.template;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * The numbers of a shipwright order (design.md §4.1, SW1), without world access:
 * <ul>
 *     <li>price = the template's {@code price} × {@code ships.order_price_factor}, rounded;</li>
 *     <li>build days = {@code ships.build_time_days.<template>} (else {@code .default}) × blocks / {@link #BLOCKS_PER_DAY_UNIT};</li>
 *     <li>logs = ⌈blocks / {@link #BLOCKS_PER_LOG} × {@code ships.order_materials_factor}⌉;</li>
 *     <li>wool = ⌈sail yard blocks × {@code ships.order_materials_factor}⌉;</li>
 *     <li>finish day = order day + build days; ready once the world day reaches it.</li>
 * </ul>
 * A berth is free when no ship's bounds contain the berth position or overlap the footprint a template would take there.
 */
public final class ShipOrderMath {

    /** The template block count the {@code build_time_days} values are given for. */
    public static final double BLOCKS_PER_DAY_UNIT = 500.0;
    /** Template blocks per log of the material list. */
    public static final double BLOCKS_PER_LOG = 20.0;
    public static final double TICKS_PER_DAY = 24000.0;

    // ------------------------------------------------------------------ template prices (SHIP1, SHIP2)

    /** The starter sloop's price and block count: what a shipwright asks per block of hull (SW1, SHIP1). */
    public static final int STARTER_SLOOP_PRICE = 400, STARTER_SLOOP_BLOCKS = 685;
    /** An iron ingot's base price in doubloons (trade/good/TradeGoods, IRON). */
    public static final double IRON_INGOT_PRICE = 6.0;
    /** The cannon's recipe: an iron block and two ingots (eleven ingots) on two stripped logs and a plank. */
    public static final int CANNON_INGOTS = 11, CANNON_WOOD_BLOCKS = 3;
    /** Prices are rounded to this many doubloons. */
    public static final int PRICE_STEP = 25;
    /**
     * The brig's blocks (art/schematics/brig.py; {@code BrigLayoutTest} checks them against the committed structure) and
     * her carriage guns, two blocks each.
     */
    public static final int BRIG_BLOCKS = 1233, BRIG_GUNS = 8;

    private ShipOrderMath() {
    }

    /** One cannon at its recipe's cost: the iron at its trade price, the wood at the hull's price per block. */
    public static double cannonPrice() {
        return CANNON_INGOTS * IRON_INGOT_PRICE + CANNON_WOOD_BLOCKS * (double) STARTER_SLOOP_PRICE / STARTER_SLOOP_BLOCKS;
    }

    /**
     * A template's price: its hull blocks (every block but the guns') at the starter sloop's price per block, plus each
     * carriage gun at {@link #cannonPrice()}, rounded to {@link #PRICE_STEP}.
     */
    public static int templatePrice(int blocks, int guns) {
        double hull = (double) STARTER_SLOOP_PRICE * Math.max(0, blocks - 2 * guns) / STARTER_SLOOP_BLOCKS;
        double total = hull + Math.max(0, guns) * cannonPrice();
        return (int) (Math.round(total / PRICE_STEP) * PRICE_STEP);
    }

    /** The brig's price (SHIP2: she is sold with her eight guns). */
    public static int brigPrice() {
        return templatePrice(BRIG_BLOCKS, BRIG_GUNS);
    }

    /** What a template costs at a shipwright, never negative. */
    public static long price(int templatePrice, double factor) {
        return Math.max(0L, Math.round(templatePrice * Math.max(0.0, factor)));
    }

    /** Build time in days of a template with {@code blocks} blocks, given the configured days per 500 blocks. */
    public static double buildDays(double daysPer500, int blocks) {
        return Math.max(0.0, daysPer500) * Math.max(0, blocks) / BLOCKS_PER_DAY_UNIT;
    }

    public static int logs(int blocks, double factor) {
        return ceilCount(Math.max(0, blocks) / BLOCKS_PER_LOG * Math.max(0.0, factor));
    }

    public static int wool(int yardBlocks, double factor) {
        return ceilCount(Math.max(0, yardBlocks) * Math.max(0.0, factor));
    }

    private static int ceilCount(double v) {
        // a tiny epsilon keeps 34.0000000001 (floating point noise) at 34
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(v - 1e-9));
    }

    /** The world day (fractional) of a day time in ticks. */
    public static double day(long dayTime) {
        return dayTime / TICKS_PER_DAY;
    }

    public static double finishDay(double orderDay, double buildDays) {
        return orderDay + Math.max(0.0, buildDays);
    }

    /** Whether the ship is built ({@code today} has reached the finish day, within floating point noise). */
    public static boolean ready(double finishDay, double today) {
        return today + 1e-9 >= finishDay;
    }

    /** Days left until {@code finishDay}, never negative. */
    public static double remaining(double finishDay, double today) {
        return Math.max(0.0, finishDay - today);
    }

    /**
     * Remaining days for display: rounded up to a tenth, so a ship that is not ready never shows "0.0" (at least
     * "0.1"). Locale independent ({@code 1.5}).
     */
    public static String formatDays(double days) {
        double tenths = Math.max(0.0, Math.ceil(days * 10.0 - 1e-9) / 10.0);
        if (days > 0 && tenths < 0.1) tenths = 0.1;
        return String.format(Locale.ROOT, "%.1f", tenths);
    }

    /** Whether a port with {@code open} orders takes another one. */
    public static boolean takesOrder(int open, int max) {
        return open < max;
    }

    /**
     * Whether a berth is taken: a ship's bounds (world space) contain the berth's centre block or overlap the block
     * box a template would occupy there.
     */
    public static boolean occupied(BlockPos berth, BoundingBox footprint, List<AABB> ships) {
        AABB foot = new AABB(footprint.minX(), footprint.minY(), footprint.minZ(), footprint.maxX() + 1, footprint.maxY() + 1,
                footprint.maxZ() + 1);
        AABB centre = new AABB(berth);
        for (AABB ship : ships) {
            if (ship.intersects(centre) || ship.intersects(foot)) return true;
        }
        return false;
    }
}
