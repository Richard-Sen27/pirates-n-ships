package com.richardsenger.piratesnships.crew.galley;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * The crew's meal schedule (CRW2, docs/design.md §7.4). Pure.
 * <ul>
 *   <li>A meal starts when the level's clock runs past one of the meal times ({@code crew.meals.meal_times}, ticks of
 *       the day). Only a running clock serves meals: a jump of more than {@link #MAX_STEP} ticks (a {@code /time set},
 *       sleeping through the night) or backwards skips them.</li>
 *   <li>A meal lasts {@code meal_ticks}; then the crew gets up.</li>
 *   <li>The diner sits on one of the eight cells around the pantry or water barrel ({@link #spotsAround}, the four
 *       sides first), facing it ({@link #facingYaw}).</li>
 * </ul>
 * Nothing is eaten at a meal: the provisions are booked once a day at dawn (CR2, {@code ShipProvisions}), so the days
 * of supplies left move once a day.
 */
public final class MealRules {

    /** Ticks per day. */
    public static final long DAY = 24000L;
    /** Largest clock step that still serves the meals it passes; anything larger is a jump. */
    public static final long MAX_STEP = 600L;
    /** Default {@code meal_times}: noon and sunset. */
    public static final List<String> DEFAULT_TIMES = List.of("6000", "12000");

    private MealRules() {
    }

    /** The meal times in {@code entries}: whole numbers, taken modulo a day, without repeats, in day order. */
    public static List<Long> parseTimes(List<String> entries) {
        TreeSet<Long> times = new TreeSet<>();
        for (String e : entries) {
            if (e == null) continue;
            try {
                times.add(Math.floorMod(Long.parseLong(e.trim()), DAY));
            } catch (NumberFormatException ignored) {
                // not a number: ignored, as the config comment says
            }
        }
        return List.copyOf(times);
    }

    /**
     * Whether a meal starts as the clock moves from day time {@code before} to {@code now} (both absolute day times):
     * a meal time lies in {@code (before, now]} and the clock moved forward by at most {@link #MAX_STEP}.
     */
    public static boolean mealStarts(long before, long now, List<Long> times) {
        long step = now - before;
        if (step <= 0 || step > MAX_STEP) return false;
        for (long t : times) {
            long next = before + Math.floorMod(t - before - 1, DAY) + 1; // first time of day t after `before`
            if (next <= now) return true;
        }
        return false;
    }

    /** The game time a meal that starts at {@code gameTime} ends. */
    public static long mealEnd(long gameTime, int mealTicks) {
        return gameTime + Math.max(1, mealTicks);
    }

    /** The eight cells around {@code block} at its height: the four sides (north, east, south, west), then the corners. */
    public static List<BlockPos> spotsAround(BlockPos block) {
        List<BlockPos> out = new ArrayList<>(8);
        for (Direction d : Direction.Plane.HORIZONTAL) out.add(block.relative(d));
        for (Direction d : Direction.Plane.HORIZONTAL) out.add(block.relative(d).relative(d.getClockWise()));
        return out;
    }

    /**
     * The yaw (Minecraft degrees: 0 = south, 90 = west, wrapped to [-180, 180)) of a diner at plot cell {@code spot}
     * facing plot cell {@code block}. {@code shipOrientation}: the body to world rotation of the ship whose plot holds
     * both, or null in the world; the plot direction is turned into the world and projected onto the horizontal plane.
     */
    public static float facingYaw(BlockPos spot, BlockPos block, @Nullable Quaterniondc shipOrientation) {
        Vector3d d = new Vector3d(block.getX() - spot.getX(), 0, block.getZ() - spot.getZ());
        if (shipOrientation != null) shipOrientation.transform(d);
        if (d.x * d.x + d.z * d.z < 1e-8) return 0;
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
    }
}
