package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * The rope slide pose through PAL (GR2). The only class of the grapple that imports {@code com.zigythebird.playeranim*};
 * load it only when PAL is installed ({@link RopeSlidePoses#onClientSetup}).
 *
 * <p>Every player gets a layer {@code pirates_n_ships:rope_slide} above the melee layer. While the player rides a rope
 * it plays {@code rope_slide} (held on its last frame); otherwise it is stopped. The animation file lives in
 * {@code assets/pirates_n_ships/rope_animations/}, not in PAL's {@code player_animations/} folder (whose contents
 * {@code PalAnimationFilesTest} pins to the melee and firearm animations), and is read once through the resource
 * manager with PAL's own loader, so a resource pack can still replace it.
 */
final class PalRopeSlidePoses implements RopeSlidePoses.Driver {

    private static final ResourceLocation LAYER = Constants.id(RopeSlidePoses.ANIMATION);
    static final ResourceLocation FILE = Constants.id("rope_animations/" + RopeSlidePoses.ANIMATION + ".json");

    private @Nullable Animation animation;
    private boolean missingReported;

    private PalRopeSlidePoses() {
    }

    /** Registers the layer factory (client setup) and returns the driver. */
    static PalRopeSlidePoses register(int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, priority, RopeLayer::new);
        return new PalRopeSlidePoses();
    }

    @Override
    public void update(Player player, boolean sliding) {
        RopeLayer layer = layer(player);
        if (layer == null || layer.playing == sliding) return;
        if (!sliding) {
            layer.stopTriggeredAnimation();
            layer.stop();
            layer.playing = false;
            return;
        }
        Animation a = animation();
        if (a == null) return;
        layer.triggerAnimation(a);
        layer.playing = true;
    }

    private @Nullable Animation animation() {
        if (animation != null || missingReported) return animation;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(FILE);
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                animation = UniversalAnimLoader.loadAnimations(in).get(RopeSlidePoses.ANIMATION);
            } catch (IOException | RuntimeException e) {
                Constants.LOG.warn("Could not read the rope slide animation {}", FILE, e);
            }
        }
        if (animation == null && !missingReported) {
            missingReported = true;
            Constants.LOG.warn("Rope slide animation {} is missing (resource pack?)", FILE);
        }
        return animation;
    }

    private static @Nullable RopeLayer layer(Player player) {
        if (!(player instanceof AbstractClientPlayer clientPlayer)) return null;
        try {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(clientPlayer, LAYER);
            return layer instanceof RopeLayer rope ? rope : null;
        } catch (IllegalArgumentException e) {
            return null; // PAL's player mixin did not apply (another mod's player class)
        }
    }

    /** One player's rope layer: plays only what {@link PalRopeSlidePoses} triggers. */
    static final class RopeLayer extends PlayerAnimationController {

        boolean playing;

        RopeLayer(AbstractClientPlayer player) {
            super(player, (controller, state, setter) -> PlayState.STOP);
        }
    }
}
