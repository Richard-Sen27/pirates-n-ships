package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code firearms} (docs/design.md §8.1, §17 "Combat"). The rain misfire toggle and chance and
 * the firearm damage multiplier already exist in the {@code combat} section ({@link CombatConfig}) and are used from
 * there, so there is one value per setting. {@link #type(FirearmKind)} is the only adapter from config to the pure
 * {@link FirearmType}.
 */
public final class FirearmsConfig {

    private static final ConfigSection S = ModConfigs.server("firearms", "Pistols and muskets: loading, firing, recoil and the lead ball");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Pistols and muskets can be loaded and fired. Off = they are inert items");
    public static final ConfigValue<Boolean> CONSUME_GUNPOWDER = S.bool("consume_gunpowder", true,
            "Loading takes one gunpowder besides the lead shot. Off = lead shot only");
    public static final ConfigValue<Integer> COOLDOWN_TICKS = S.intRange("cooldown_ticks", 10, 0, 200,
            "Ticks after a shot or a misfire before the gun can be used again");
    public static final ConfigValue<Integer> BALL_LIFETIME_TICKS = S.intRange("ball_lifetime_ticks", 100, 1, 1200,
            "Ticks a lead ball flies before it vanishes");
    public static final ConfigValue<Double> BALL_GRAVITY = S.doubleRange("ball_gravity", 0.02, 0.0, 1.0,
            "Downward acceleration of a lead ball in blocks per tick squared (arrows: 0.05, snowballs: 0.03)");

    private static final ConfigSection AIM = S.section("aim", "Holding a loaded gun to aim; the shot leaves when the use key is let go");
    public static final ConfigValue<Integer> AIM_MIN_TICKS = AIM.intRange("aim_min_ticks", 0, 0, 100,
            "Ticks a loaded gun must be held before letting go fires it; a shorter hold puts it down still loaded. 0 = a click fires at once");
    public static final ConfigValue<Integer> AIM_STEADY_TICKS = AIM.intRange("aim_steady_ticks", 20, 0, 1200,
            "Ticks of aiming after which the shot is steadied (aimed_spread_factor applies)");
    public static final ConfigValue<Double> AIMED_SPREAD_FACTOR = AIM.doubleRange("aimed_spread_factor", 0.5, 0.0, 1.0,
            "Spread of a steadied shot as a fraction of the gun's spread (1 = no benefit from aiming)");

    private static final ConfigSection VIEW = ModConfigs.client("firearm_view", "How aiming a gun looks on this client");
    public static final ConfigValue<Double> MUSKET_ZOOM = VIEW.doubleRange("musket_zoom", 1.25, 1.0, 4.0,
            "Zoom while aiming a loaded musket (field of view divided by this). 1 = no zoom");

    private static final ConfigSection PISTOL = S.section("pistol", "The pistol: short range, high damage");
    public static final ConfigValue<Double> PISTOL_DAMAGE = PISTOL.doubleRange("damage", 10.0, 0.0, 100.0,
            "Damage of a pistol ball hit");
    public static final ConfigValue<Double> PISTOL_VELOCITY = PISTOL.doubleRange("muzzle_velocity", 2.5, 0.1, 10.0,
            "Start speed of a pistol ball in blocks per tick");
    public static final ConfigValue<Double> PISTOL_SPREAD = PISTOL.doubleRange("spread_degrees", 4.0, 0.0, 45.0,
            "Half angle of the cone a pistol ball leaves in, in degrees");
    public static final ConfigValue<Integer> PISTOL_RELOAD = PISTOL.intRange("reload_ticks", 60, 1, 1200,
            "Ticks the pistol has to be held to load it");
    public static final ConfigValue<Double> PISTOL_PITCH = PISTOL.doubleRange("sound_pitch", 1.0, 0.5, 2.0,
            "Pitch of the pistol shot sound");
    public static final ConfigValue<Double> PISTOL_RECOIL_PITCH = PISTOL.doubleRange("recoil_pitch_degrees", 3.0, 0.0, 45.0,
            "How far a pistol shot kicks the shooter's view up, in degrees");
    public static final ConfigValue<Double> PISTOL_RECOIL_PUSH = PISTOL.doubleRange("recoil_push", 0.08, 0.0, 2.0,
            "Backward push on the shooter per pistol shot, in blocks per tick");

    private static final ConfigSection MUSKET = S.section("musket", "The musket: longer range, slower reload");
    public static final ConfigValue<Double> MUSKET_DAMAGE = MUSKET.doubleRange("damage", 14.0, 0.0, 100.0,
            "Damage of a musket ball hit");
    public static final ConfigValue<Double> MUSKET_VELOCITY = MUSKET.doubleRange("muzzle_velocity", 4.0, 0.1, 10.0,
            "Start speed of a musket ball in blocks per tick");
    public static final ConfigValue<Double> MUSKET_SPREAD = MUSKET.doubleRange("spread_degrees", 1.0, 0.0, 45.0,
            "Half angle of the cone a musket ball leaves in, in degrees");
    public static final ConfigValue<Integer> MUSKET_RELOAD = MUSKET.intRange("reload_ticks", 100, 1, 1200,
            "Ticks the musket has to be held to load it");
    public static final ConfigValue<Double> MUSKET_PITCH = MUSKET.doubleRange("sound_pitch", 0.7, 0.5, 2.0,
            "Pitch of the musket shot sound (the pistol sound, lower)");
    public static final ConfigValue<Double> MUSKET_RECOIL_PITCH = MUSKET.doubleRange("recoil_pitch_degrees", 5.0, 0.0, 45.0,
            "How far a musket shot kicks the shooter's view up, in degrees");
    public static final ConfigValue<Double> MUSKET_RECOIL_PUSH = MUSKET.doubleRange("recoil_push", 0.15, 0.0, 2.0,
            "Backward push on the shooter per musket shot, in blocks per tick");

    private FirearmsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** The current numbers of a gun, including the rain misfire chance and damage multiplier from {@code combat}. */
    public static FirearmType type(FirearmKind kind) {
        double misfire = CombatConfig.FIREARM_MISFIRE_IN_RAIN.get() ? CombatConfig.RAIN_MISFIRE_CHANCE.get() : 0.0;
        FirearmType base = switch (kind) {
            case PISTOL -> new FirearmType(PISTOL_DAMAGE.get().floatValue(), PISTOL_VELOCITY.get().floatValue(),
                    PISTOL_SPREAD.get().floatValue(), PISTOL_RELOAD.get(), misfire, PISTOL_PITCH.get().floatValue());
            case MUSKET -> new FirearmType(MUSKET_DAMAGE.get().floatValue(), MUSKET_VELOCITY.get().floatValue(),
                    MUSKET_SPREAD.get().floatValue(), MUSKET_RELOAD.get(), misfire, MUSKET_PITCH.get().floatValue());
        };
        return base.withDamageMultiplier(CombatConfig.FIREARM_DAMAGE.get());
    }

    /** View kick of a shot in degrees. */
    static float recoilPitch(FirearmKind kind) {
        return (kind == FirearmKind.PISTOL ? PISTOL_RECOIL_PITCH : MUSKET_RECOIL_PITCH).get().floatValue();
    }

    /** Backward push of a shot in blocks per tick. */
    static double recoilPush(FirearmKind kind) {
        return (kind == FirearmKind.PISTOL ? PISTOL_RECOIL_PUSH : MUSKET_RECOIL_PUSH).get();
    }
}
