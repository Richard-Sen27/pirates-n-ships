package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Text of the ship HUD (HUD1): the speed and rudder line, plus the unit conversion. No client
 * classes, so JUnit tests the numbers and datagen writes the lang keys.
 */
public final class ShipHudText {

    static final String KEY = "hud." + Constants.MOD_ID + ".ship_status.";
    public static final String KEY_LINE = KEY + "line";
    public static final String KEY_KNOTS = KEY + "knots";
    public static final String KEY_BLOCKS = KEY + "blocks";
    public static final String KEY_RUDDER_STARBOARD = KEY + "rudder_starboard";
    public static final String KEY_RUDDER_PORT = KEY + "rudder_port";
    public static final String KEY_RUDDER_MIDSHIPS = KEY + "rudder_midships";

    /** One knot in metres (= blocks) per second. */
    public static final double KNOT = 1852.0 / 3600.0;

    private ShipHudText() {
    }

    /** Blocks per second to knots (1 block = 1 m). */
    public static double knots(double blocksPerSecond) {
        return blocksPerSecond / KNOT;
    }

    /** The speed number in the chosen unit, one decimal. */
    public static String speedNumber(ShipHudConfig.SpeedUnit unit, double blocksPerSecond) {
        double v = unit == ShipHudConfig.SpeedUnit.KNOTS ? knots(blocksPerSecond) : blocksPerSecond;
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0, v));
    }

    public static Component speed(ShipHudConfig.SpeedUnit unit, double blocksPerSecond) {
        return Component.translatable(unit == ShipHudConfig.SpeedUnit.KNOTS ? KEY_KNOTS : KEY_BLOCKS, speedNumber(unit, blocksPerSecond));
    }

    /** Whole degrees of a rudder angle; below half a degree it is midships. */
    public static int rudderDegrees(double angle) {
        return (int) Math.round(Math.abs(angle));
    }

    public static Component rudder(double angle) {
        int deg = rudderDegrees(angle);
        if (deg == 0) {
            return Component.translatable(KEY_RUDDER_MIDSHIPS);
        }
        return Component.translatable(angle > 0 ? KEY_RUDDER_STARBOARD : KEY_RUDDER_PORT, deg);
    }

    /** "4.2 kn · rudder 12° stb", or only the speed for a ship without a helm. */
    public static Component line(ShipHudConfig.SpeedUnit unit, double speed, double rudder) {
        Component s = speed(unit, speed);
        return Double.isNaN(rudder) ? s : Component.translatable(KEY_LINE, s, rudder(rudder));
    }

    public static void gather(DataContributions data) {
        data.lang(lang -> {
            lang.add(KEY_LINE, "%s · %s");
            lang.add(KEY_KNOTS, "%s kn");
            lang.add(KEY_BLOCKS, "%s b/s");
            lang.add(KEY_RUDDER_STARBOARD, "rudder %s° stb");
            lang.add(KEY_RUDDER_PORT, "rudder %s° port");
            lang.add(KEY_RUDDER_MIDSHIPS, "rudder midships");
        });
    }
}
