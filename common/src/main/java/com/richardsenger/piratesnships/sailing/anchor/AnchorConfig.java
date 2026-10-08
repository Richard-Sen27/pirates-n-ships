package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config of the anchor and its chain ({@code anchor_chain}, docs/design.md §5.2 AN2a). The sounds are played by
 * the server to every nearby player, so their switch and volumes are server values too. The chain's length and the
 * anchor's on/off switch are {@code sailing_runtime.anchor_chain_length} and {@code anchor_enabled}.
 *
 * <p><b>Units.</b> Sable's mass unit, the kpg, is taken as one ton of displacement (a floating ship displaces its own
 * mass). The chain values are per ton, so a ship of any size feels the same accelerations: a force is
 * {@code mass × value}, in kpg·blocks/s². {@link #HOLDING_FORCE} is therefore the largest deceleration the anchor can
 * give the ship, in blocks/s².
 */
public final class AnchorConfig {

    private static final ConfigSection S = ModConfigs.server("anchor_chain", "The anchor: its fall, its chain, its holding power, sounds and particles");

    public static final ConfigValue<Double> SINK_SPEED = S.doubleRange("sink_speed", 4.0, 0.5, 32.0,
            "Speed at which the dropped anchor sinks through water, in blocks per second (in air it falls freely)");
    public static final ConfigValue<Double> WATER_DRAG = S.doubleRange("water_drag", 2.0, 0.0, 20.0,
            "How fast the water takes away the sideways speed the anchor kept from the moving ship, per second. Lower: it lands further ahead");
    public static final ConfigValue<Double> CHAIN_STIFFNESS = S.doubleRange("chain_stiffness", AnchorChain.Params.DEFAULTS.stiffness(), 0.1, 100.0,
            "Pull of a taut chain per block it is stretched beyond the paid-out length, per ton of the ship, in blocks per second squared");
    public static final ConfigValue<Double> CHAIN_DAMPING = S.doubleRange("chain_damping", AnchorChain.Params.DEFAULTS.damping(), 0.0, 100.0,
            "Extra pull of a taut chain per block per second the ship moves away from the anchor, per ton of the ship");
    public static final ConfigValue<Double> HOLDING_FORCE = S.doubleRange("holding_force", AnchorChain.Params.DEFAULTS.holding(), 0.0, 50.0,
            "Holding power of the anchor per ton of the ship: the largest deceleration it gives, in blocks per second squared. "
                    + "A stronger pull drags the anchor over the seabed. 0 = the anchor never holds");
    public static final ConfigValue<Double> DRAG_SCRAPE_RATE = S.doubleRange("drag_scrape_rate", 1.0, 0.0, 16.0,
            "Fastest the anchor scrapes over the seabed when the ship pulls harder than its holding power, in blocks per second");
    public static final ConfigValue<Double> SETTLE_DRAG = S.doubleRange("settle_drag", AnchorChain.Params.DEFAULTS.settleDrag(), 0.0, 5.0,
            "Mild drag of the chain lying on the seabed on a ship whose anchor rests, per second, so it settles. Counts toward the holding power");
    public static final ConfigValue<Double> HEEL_FACTOR = S.doubleRange("heel_factor", 0.25, 0.0, 1.0,
            "Fraction of the heeling and pitching moment of the chain's pull that is applied (yaw is always full; see sailing_runtime.sail_heel_factor)");
    public static final ConfigValue<Double> AT_REST_SPEED = S.doubleRange("at_rest_speed", 0.3, 0.0, 4.0,
            "A ship whose anchor holds counts as anchored (for mooring, crew and the HUD) below this speed, in blocks per second");
    public static final ConfigValue<Double> RAISE_SPEED = S.doubleRange("raise_speed", 2.5, 0.1, 64.0,
            "Speed at which the capstan winds the chain in, in blocks per second");
    public static final ConfigValue<Boolean> SOUNDS = S.bool("sounds", true,
            "Chain, splash, landing, jolt and capstan sounds and the splash particles of the anchor");
    public static final ConfigValue<Double> CHAIN_VOLUME = S.doubleRange("chain_volume", 0.8, 0.0, 4.0,
            "Volume of the running chain");
    public static final ConfigValue<Double> SPLASH_VOLUME = S.doubleRange("splash_volume", 1.0, 0.0, 4.0,
            "Volume of the splash when the anchor enters the water");
    public static final ConfigValue<Double> THUD_VOLUME = S.doubleRange("thud_volume", 1.0, 0.0, 4.0,
            "Volume of the anchor landing on the ground");
    public static final ConfigValue<Double> JOLT_VOLUME = S.doubleRange("jolt_volume", 0.7, 0.0, 4.0,
            "Volume of the clank when the chain snaps taut");
    public static final ConfigValue<Double> CAPSTAN_VOLUME = S.doubleRange("capstan_volume", 0.6, 0.0, 4.0,
            "Volume of the capstan's clank while it winds the chain in");

    private AnchorConfig() {
    }

    public static void init() {
        // class load registers the values
    }

    /** The chain tuning from the server config. */
    public static AnchorChain.Params chainParams() {
        return new AnchorChain.Params(CHAIN_STIFFNESS.get(), CHAIN_DAMPING.get(), HOLDING_FORCE.get(), SETTLE_DRAG.get());
    }
}
