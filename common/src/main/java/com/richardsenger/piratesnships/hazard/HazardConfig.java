package com.richardsenger.piratesnships.hazard;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.sailing.effects.FoamRules;
import com.richardsenger.piratesnships.sailing.effects.WakeRules;

/**
 * Server config section {@code waves} and client section {@code wave_effects} (docs/design.md §17, group "Waves";
 * §5.4; read by {@code hazards.waves} and {@code sailing.waves}, WV1). The client section can't be called {@code waves}
 * because config translation keys don't include the config type, so it would clash with the server section. Loaded by
 * {@code core.settings.SettingsModule}; it stays in this package so the settings module and its tests keep their
 * imports. The {@code hazards} section belongs to {@code hazards.HazardsConfig} (H1).
 */
public final class HazardConfig {

    private static final ConfigSection WAVES = ModConfigs.server("waves", "Simulated sea state: wave roll and pitch on ships");

    public static final ConfigValue<Boolean> WAVES_ENABLED = WAVES.bool("enabled", true,
            "The sea state follows the weather, rocks ships with roll and pitch and spills water into low open hulls. Off = always a flat sea");
    public static final ConfigValue<Double> WAVE_AMPLITUDE = WAVES.doubleRange("amplitude", 1.0, 0.0, 5.0,
            "Multiplier on the wave height of every sea state (calm 0.15, moderate 0.6, rough 1.2, storm 2.0 blocks); 0 = flat sea");
    public static final ConfigValue<Double> SHIP_TORQUE = WAVES.doubleRange("ship_torque", 18.0, 0.0, 200.0,
            "Roll and pitch torque per unit of wave slope and per kpg of ship mass, scaled by (blocks / 200)^-size_exponent");
    public static final ConfigValue<Double> SIZE_EXPONENT = WAVES.doubleRange("size_exponent", 1.0, 0.0, 2.0,
            "How much steadier big ships lie: the wave torque per kpg falls as (blocks / 200) to the power of minus this");
    public static final ConfigValue<Double> MAX_TORQUE_PER_MASS = WAVES.doubleRange("max_torque_per_mass", 2.5, 0.0, 20.0,
            "Upper limit of the wave torque per kpg of ship mass, so that small boats are not flipped");
    public static final ConfigValue<Double> STATE_CHANGE_SECONDS = WAVES.doubleRange("state_change_seconds", 60.0, 1.0, 1200.0,
            "Seconds the sea takes to go from calm to storm (or back) when the weather changes");
    public static final ConfigValue<Boolean> SPILL = WAVES.bool("spill", true,
            "Wave crests spill water over low rims and through low open hatches into compartments");
    public static final ConfigValue<Integer> SYNC_INTERVAL_TICKS = WAVES.intRange("sync_interval_ticks", 60, 10, 1200,
            "Ticks between sea state updates sent to players (clients blend between them)");
    // WAV2: spectrum, wave groups and heave
    public static final ConfigValue<Integer> COMPONENTS = WAVES.intRange("components",
            com.richardsenger.piratesnships.hazards.waves.WaveSpectrum.DEFAULT_COMPONENTS,
            com.richardsenger.piratesnships.hazards.waves.WaveSpectrum.MIN_COMPONENTS,
            com.richardsenger.piratesnships.hazards.waves.WaveSpectrum.MAX_COMPONENTS,
            "Wave trains the sea is made of (16 to 64 blocks long, weighted around the sea state's peak); more gives a less regular rhythm");
    public static final ConfigValue<Double> GROUP_DEPTH = WAVES.doubleRange("group_depth", 0.35, 0.0, 0.9,
            "How much the wave height swells and fades in sets (wave groups): 0.35 = between 65 % and 135 % of the sea state's height");
    public static final ConfigValue<Double> GROUP_PERIOD_SECONDS = WAVES.doubleRange("group_period_seconds", 60.0, 10.0, 600.0,
            "Time scale of the wave groups in seconds: sets of bigger waves come and go over about this long");
    public static final ConfigValue<Boolean> HEAVE = WAVES.bool("heave", true,
            "Ships rise on wave crests and sink into troughs (a vertical force from the mean wave height under the hull)");
    public static final ConfigValue<Double> HEAVE_STRENGTH = WAVES.doubleRange("heave_strength", 0.95, 0.0, 5.0,
            "Heave force per block of mean wave height under the hull, as a share of the ship's weight");
    public static final ConfigValue<Double> HEAVE_PER_MASS = WAVES.doubleRange("heave_per_mass", 25.0, 0.0, 50.0,
            "Upper limit of the heave force per kpg of ship mass");
    // WAV6: vanilla boats and floating items ride the waves (hazards.waves.boats)
    public static final ConfigValue<Boolean> BOATS = WAVES.bool("boats", true,
            "Vanilla boats on the open sea rise and fall with the waves (not in lakes, rivers, shallow water or inside a hull)");
    public static final ConfigValue<Boolean> FLOATING_ITEMS = WAVES.bool("floating_items", true,
            "Items floating on the open sea bob with the waves");

    private static final ConfigSection WAVE_EFFECTS = ModConfigs.client("wave_effects", "Client-side wave visuals");

    public static final ConfigValue<Boolean> CAMERA_SWAY = WAVE_EFFECTS.bool("camera_sway", false,
            "The camera rolls with the ship while you are aboard");
    public static final ConfigValue<Double> CAMERA_SWAY_FRACTION = WAVE_EFFECTS.doubleRange("camera_sway_fraction", 0.5, 0.0, 1.0,
            "Fraction of the ship's roll the camera follows (1 = the full roll)");
    public static final ConfigValue<Integer> CAMERA_SWAY_EASE_TICKS = WAVE_EFFECTS.intRange("camera_sway_ease_ticks", 5, 0, 100,
            "Ticks the camera sway takes to fade in when you board or land and out when you leave the ship (0 = instant)");
    public static final ConfigValue<Boolean> SPRAY = WAVE_EFFECTS.bool("spray", true,
            "Spray and a splash at the bow when it digs into a wave in rough or stormy seas");
    // Foam streaks on the water (WD1, read through sailing.effects.SeaEffectsConfig#foam)
    public static final ConfigValue<Boolean> FOAM = WAVE_EFFECTS.bool("foam", true,
            "Faint foam streaks on the water, stretched along the direction the waves run, more in a higher sea; none in a calm sea");
    public static final ConfigValue<Double> FOAM_DENSITY = WAVE_EFFECTS.doubleRange("foam_density", FoamRules.DEFAULTS.density(), 0.0, 20.0,
            "Foam streaks tried per tick at one block of wave height above foam_min_amplitude, growing with the square root of that height (about a third of them land, on the crests)");
    public static final ConfigValue<Double> FOAM_MIN_AMPLITUDE = WAVE_EFFECTS.doubleRange("foam_min_amplitude", FoamRules.DEFAULTS.minAmplitude(), 0.0, 5.0,
            "No foam below this wave height in blocks (calm sea 0.15, moderate 0.6, rough 1.2, storm 2.0)");
    public static final ConfigValue<Integer> FOAM_RADIUS = WAVE_EFFECTS.intRange("foam_radius", (int) FoamRules.DEFAULTS.radius(), 4, 64,
            "Horizontal distance from the camera in blocks within which foam appears");
    public static final ConfigValue<Integer> FOAM_LIFE_TICKS = WAVE_EFFECTS.intRange("foam_life_ticks", FoamRules.DEFAULTS.lifeTicks(), 10, 400,
            "Life of one foam streak in ticks, including fading in and out");
    // Wake behind ships (WAKE1, read through sailing.effects.SeaEffectsConfig#wake)
    public static final ConfigValue<Boolean> WAKE = WAVE_EFFECTS.bool("wake", true,
            "Moving ships leave a wake: a bow wave of foam at the stem, a foam trail behind the stern and foam along the waterline when fast");
    public static final ConfigValue<Integer> WAKE_RADIUS = WAVE_EFFECTS.intRange("wake_radius", (int) WakeRules.DEFAULTS.radius(), 8, 128,
            "Ships within this horizontal distance of the camera in blocks leave a wake");
    public static final ConfigValue<Double> WAKE_MIN_SPEED = WAVE_EFFECTS.doubleRange("wake_min_speed", WakeRules.DEFAULTS.minSpeed(), 0.0, 20.0,
            "No wake below this speed through the water in blocks per second");
    public static final ConfigValue<Double> WAKE_FULL_SPEED = WAVE_EFFECTS.doubleRange("wake_full_speed", WakeRules.DEFAULTS.fullSpeed(), 0.5, 40.0,
            "The wake is at its densest and most opaque from this speed in blocks per second on");
    public static final ConfigValue<Double> WAKE_DENSITY = WAVE_EFFECTS.doubleRange("wake_density", WakeRules.DEFAULTS.density(), 0.0, 5.0,
            "Foam quads per tick for every block of stern width at full speed; the bow wave and the waterline foam scale with it");
    public static final ConfigValue<Integer> WAKE_LIFE_TICKS = WAVE_EFFECTS.intRange("wake_life_ticks", WakeRules.DEFAULTS.lifeTicks(), 20, 600,
            "Life of the stern trail's foam in ticks (the bow wave lives three quarters of it): the longer, the longer the trail");
    public static final ConfigValue<Double> WAKE_SPREAD_DEGREES = WAVE_EFFECTS.doubleRange("wake_spread_degrees", WakeRules.DEFAULTS.spreadDegrees(), 0.0, 45.0,
            "Angle in degrees between the course and each arm of the bow wave's V (19.5 is the Kelvin angle of real ships)");
    // The visible sea surface (WAV3, read by hazards.waves.client.surface.SeaSurface)
    public static final ConfigValue<Boolean> SURFACE = WAVE_EFFECTS.bool("surface", true,
            "The sea surface rises and falls with the waves (a moving water surface around you; ships, swimming and collisions still use the flat sea level). Off, and always with Sodium, Embeddium, Iris, Oculus or VulkanMod: flat water");
    public static final ConfigValue<Integer> SURFACE_NEAR_RADIUS = WAVE_EFFECTS.intRange("surface_near_radius", 32, 8, 96,
            "Distance from the camera in blocks within which the moving surface has one point per block (rounded up to 4)");
    public static final ConfigValue<Integer> SURFACE_MID_RADIUS = WAVE_EFFECTS.intRange("surface_mid_radius", 128, 32, 512,
            "Distance from the camera in blocks within which the moving surface has one point every 4 blocks; beyond it the far cells reach to the render distance");
    public static final ConfigValue<Integer> SURFACE_FAR_CELL = WAVE_EFFECTS.intRange("surface_far_cell", 16, 4, 64,
            "Size in blocks of the moving surface's cells beyond surface_mid_radius (4, 8, 16, 32 or 64; other values round down)");
    public static final ConfigValue<Integer> SURFACE_FULL_DEPTH = WAVE_EFFECTS.intRange("surface_full_depth", 6, 2, 16,
            "Water depth in blocks from which the surface moves with the full wave height; shallower water moves less, one block deep not at all, so waves never cut into a beach");
    public static final ConfigValue<Boolean> SURFACE_UNDERSIDE = WAVE_EFFECTS.bool("surface_underside", true,
            "Also draw the moving surface from below, so it shows when you look up from under water");
    public static final ConfigValue<Double> BOAT_TILT_DEGREES = WAVE_EFFECTS.doubleRange("boat_tilt_degrees", 8.0, 0.0, 20.0,
            "Vanilla boats on the waves tilt along the slope of the sea by up to this many degrees (0 = no tilt; the rider stays upright)");

    private HazardConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
