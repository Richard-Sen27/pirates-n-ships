package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.RopeLean;
import com.richardsenger.piratesnships.combat.grapple.RopePose;
import com.richardsenger.piratesnships.combat.grapple.RopeRiderEntity;
import com.richardsenger.piratesnships.combat.grapple.RopeSwingService;
import com.richardsenger.piratesnships.combat.grapple.client.ClientRopeSwing;
import com.richardsenger.piratesnships.combat.grapple.client.ClientRopes;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationsSetup;
import com.richardsenger.piratesnships.platform.Services;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The rope poses of a player, played through the Player Animation Library: sliding along a rope (GR2: both arms raised
 * to the rope, legs hanging, while the player rides a {@link RopeRiderEntity}), hauling a hooked ship by hand (ART7
 * for GR5: leaning back, hand over hand, while the player's own rope is frozen and taut), and (GR8) hanging on, climbing,
 * swinging on and pulling one's own hand-held rope ({@link RopePose}). Every player in the level gets their pose from the
 * hook's synced state, so other players see it too. Must not reference PAL types, so it loads without PAL; only
 * {@link PalRopeSlidePoses} does. Without PAL players slide in vanilla's riding pose, haul in vanilla's sneaking pose
 * and hang in vanilla's falling pose.
 */
public final class RopeSlidePoses {

    /** Name of the slide animation, also its file {@code assets/pirates_n_ships/rope_animations/rope_slide.json}. */
    public static final String ANIMATION = "rope_slide";
    /** Name of the haul animation (ART7), also its file {@code assets/pirates_n_ships/rope_animations/haul.json}. */
    public static final String HAUL = "haul";
    /** How far above the melee layer the rope layer sits (nobody fights while hanging on a rope). */
    public static final int PRIORITY_ABOVE_MELEE = 50;
    /** Share of the way to the rope's lean the body moves each tick (smooths the swing's turning points). */
    static final float LEAN_EASE = 0.5f;

    /** Shows or stops the rope pose of one player. */
    public interface Driver {
        /**
         * @param animation a {@link RopePose#animation()}, or null for none
         * @param serial    a count that, when it changes while the same animation plays, starts it again (one
         *                  hand-over-hand per pull)
         * @param lean      the body's lean along the rope ({@link RopeLean}), {@link RopeLean.Lean#NONE} when not hanging
         */
        void update(Player player, @Nullable String animation, int serial, RopeLean.Lean lean);
    }

    private static Driver driver = (player, animation, serial, lean) -> { };
    /** Each player's pose of the last client tick (for the swing's hysteresis and the rope's grip). */
    private static final Map<UUID, RopePose> POSES = new HashMap<>();

    private RopeSlidePoses() {
    }

    /** {@code CLIENT_SETUP}: installs the PAL driver when PAL is loaded. */
    public static void onClientSetup() {
        if (!Services.PLATFORM.isModLoaded(MeleeAnimationsSetup.PAL_MOD_ID)) {
            Constants.LOG.debug("Player Animation Library is not loaded: no rope poses");
            return;
        }
        driver = PalRopeSlidePoses.register(MeleeClientConfig.ANIMATIONS_LAYER_PRIORITY.get() + PRIORITY_ABOVE_MELEE);
    }

    /** The rope pose {@code player} showed on the last client tick ({@link RopePose#NONE} for none). */
    public static RopePose poseOf(Player player) {
        return POSES.getOrDefault(player.getUUID(), RopePose.NONE);
    }

    /**
     * {@code CLIENT_TICK_END}: every player in the level gets the pose their rope gives them ({@link RopePose#select}):
     * riding a rope the slide, hauling the haul, on their own hand-held rope the hang, climb, swing or pull; everyone
     * else drops the rope pose.
     */
    public static void onClientTickEnd(Minecraft mc) {
        if (mc.level == null) {
            POSES.clear();
            return;
        }
        Map<UUID, GrapplingHookEntity> hooks = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GrapplingHookEntity hook && !hook.isRemoved() && hook.getOwner() instanceof Player owner) {
                hooks.put(owner.getUUID(), hook);
            }
        }
        Map<UUID, RopePose> next = new HashMap<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            GrapplingHookEntity hook = hooks.get(player.getUUID());
            RopePose previous = POSES.getOrDefault(player.getUUID(), RopePose.NONE);
            RopePose pose = RopePose.select(facts(player, hook), previous);
            next.put(player.getUUID(), pose);
            RopeLean.Lean lean = pose.hangs() && hook != null ? lean(player, hook) : RopeLean.Lean.NONE;
            int serial = hook != null && (pose == RopePose.CLIMB || pose == RopePose.PULL) ? hook.pulls() : 0;
            driver.update(player, pose.animation(), serial, lean);
        }
        POSES.clear();
        POSES.putAll(next);
    }

    /** What the rope pose of {@code player} depends on, as this client sees it ({@code hook}: their own hook, or null). */
    static RopePose.Facts facts(Player player, @Nullable GrapplingHookEntity hook) {
        boolean sliding = player.getVehicle() instanceof RopeRiderEntity;
        if (hook == null) {
            return RopePose.facts(sliding, null, player, Double.NaN, 0.0);
        }
        double length = ClientRopeSwing.length(hook); // the local player's prediction, the synced length for others
        Vec3 pivot = ClientRopes.hookPos(hook, 1.0f);
        Vec3 pivotMotion = pivot.subtract(ClientRopes.hookPos(hook, 0.0f));
        Vec3 motion = player.position().subtract(player.xo, player.yo, player.zo).subtract(pivotMotion);
        double speed = RopePose.swingSpeed(RopeSwingService.hand(player).subtract(pivot), motion);
        return RopePose.facts(sliding, hook, player, length, speed);
    }

    /** The body's lean along the rope from the grip to the hook's eye ({@link RopeLean}). */
    private static RopeLean.Lean lean(Player player, GrapplingHookEntity hook) {
        Vec3 grip = player.position().add(0, RopeLean.GRIP_HEIGHT * player.getScale(), 0);
        return RopeLean.of(ClientRopes.hookEye(hook, 1.0f).subtract(grip), player.yBodyRot);
    }

    /** Whether the client shows a player hauling on a rope ({@link RopePose#hauls}). */
    public static boolean hauls(boolean latched, boolean taut, boolean tied, boolean sneaking, boolean riding) {
        return RopePose.hauls(latched, taut, tied, sneaking, riding);
    }

    /** The rope animation of a player who slides or hauls: sliding wins over hauling; null for none. */
    public static @Nullable String pose(boolean sliding, boolean hauling) {
        return RopePose.select(new RopePose.Facts(sliding, hauling, false, false, -1, 0.0), RopePose.NONE).animation();
    }
}
