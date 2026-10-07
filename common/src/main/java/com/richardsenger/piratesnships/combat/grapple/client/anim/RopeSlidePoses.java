package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.grapple.RopeRiderEntity;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationsSetup;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The pose of a player sliding along a rope (GR2): both arms raised to the rope, legs hanging, played through the
 * Player Animation Library while the player rides a {@link RopeRiderEntity}. Must not reference PAL types, so it loads
 * without PAL; only {@link PalRopeSlidePoses} does. Without PAL players slide in vanilla's riding pose.
 */
public final class RopeSlidePoses {

    /** Name of the animation, also its file {@code assets/pirates_n_ships/rope_animations/rope_slide.json}. */
    public static final String ANIMATION = "rope_slide";
    /** How far above the melee layer the rope layer sits (nobody fights while hanging on a rope). */
    public static final int PRIORITY_ABOVE_MELEE = 50;

    /** Shows or stops the pose of one player. */
    public interface Driver {
        void update(Player player, boolean sliding);
    }

    private static Driver driver = (player, sliding) -> { };

    private RopeSlidePoses() {
    }

    /** {@code CLIENT_SETUP}: installs the PAL driver when PAL is loaded. */
    public static void onClientSetup() {
        if (!Services.PLATFORM.isModLoaded(MeleeAnimationsSetup.PAL_MOD_ID)) {
            Constants.LOG.debug("Player Animation Library is not loaded: no rope slide pose");
            return;
        }
        driver = PalRopeSlidePoses.register(MeleeClientConfig.ANIMATIONS_LAYER_PRIORITY.get() + PRIORITY_ABOVE_MELEE);
    }

    /** {@code CLIENT_TICK_END}: every player in the level riding a rope holds the pose, everyone else drops it. */
    public static void onClientTickEnd(Minecraft mc) {
        if (mc.level == null) return;
        for (AbstractClientPlayer player : mc.level.players()) {
            driver.update(player, player.getVehicle() instanceof RopeRiderEntity);
        }
    }
}
