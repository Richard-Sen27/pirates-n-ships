package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.RopeRiderEntity;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationsSetup;
import com.richardsenger.piratesnships.platform.Services;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The rope poses of a player, played through the Player Animation Library: sliding along a rope (GR2: both arms raised
 * to the rope, legs hanging, while the player rides a {@link RopeRiderEntity}) and hauling a hooked ship by hand (ART7
 * for GR5: leaning back, hand over hand, while the player's own rope is frozen and taut). Must not reference PAL types,
 * so it loads without PAL; only {@link PalRopeSlidePoses} does. Without PAL players slide in vanilla's riding pose and
 * haul in vanilla's sneaking pose.
 */
public final class RopeSlidePoses {

    /** Name of the slide animation, also its file {@code assets/pirates_n_ships/rope_animations/rope_slide.json}. */
    public static final String ANIMATION = "rope_slide";
    /** Name of the haul animation (ART7), also its file {@code assets/pirates_n_ships/rope_animations/haul.json}. */
    public static final String HAUL = "haul";
    /** How far above the melee layer the rope layer sits (nobody fights while hanging on a rope). */
    public static final int PRIORITY_ABOVE_MELEE = 50;

    /** Shows or stops the rope pose of one player. */
    public interface Driver {
        /** @param animation {@link #ANIMATION}, {@link #HAUL}, or null for none */
        void update(Player player, @Nullable String animation);
    }

    private static Driver driver = (player, animation) -> { };

    private RopeSlidePoses() {
    }

    /** {@code CLIENT_SETUP}: installs the PAL driver when PAL is loaded. */
    public static void onClientSetup() {
        if (!Services.PLATFORM.isModLoaded(MeleeAnimationsSetup.PAL_MOD_ID)) {
            Constants.LOG.debug("Player Animation Library is not loaded: no rope slide or haul pose");
            return;
        }
        driver = PalRopeSlidePoses.register(MeleeClientConfig.ANIMATIONS_LAYER_PRIORITY.get() + PRIORITY_ABOVE_MELEE);
    }

    /**
     * {@code CLIENT_TICK_END}: every player in the level riding a rope holds the slide pose, every player hauling on
     * their own rope shows the haul pose, everyone else drops the rope pose.
     */
    public static void onClientTickEnd(Minecraft mc) {
        if (mc.level == null) return;
        Set<UUID> hauling = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GrapplingHookEntity hook && hook.getOwner() instanceof Player owner
                    && hauls(hook.state() == GrapplingHookEntity.State.LATCHED, hook.taut(), hook.syncedTiedRing().isPresent(),
                    owner.isShiftKeyDown(), owner.isPassenger())) {
                hauling.add(owner.getUUID());
            }
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            driver.update(player, pose(player.getVehicle() instanceof RopeRiderEntity, hauling.contains(player.getUUID())));
        }
    }

    /**
     * Whether the client shows a player hauling on a rope (GR5's freeze as far as the client can see it): their hook
     * is latched, the rope is taut (synced), its near end is in their hand (not tied to a ring or cleat), they sneak
     * and do not ride anything.
     */
    public static boolean hauls(boolean latched, boolean taut, boolean tied, boolean sneaking, boolean riding) {
        return latched && taut && !tied && sneaking && !riding;
    }

    /** The rope animation of a player: sliding wins over hauling; null for none. */
    public static @Nullable String pose(boolean sliding, boolean hauling) {
        return sliding ? ANIMATION : hauling ? HAUL : null;
    }
}
