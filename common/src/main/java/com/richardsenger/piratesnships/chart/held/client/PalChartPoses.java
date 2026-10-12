package com.richardsenger.piratesnships.chart.held.client;

import com.richardsenger.piratesnships.Constants;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The chart's arm poses through PAL (CHART3). The only class of the chart that imports {@code com.zigythebird.playeranim*};
 * load it only when PAL is installed ({@link ChartHeldClient}).
 *
 * <p>Every player gets a layer {@code pirates_n_ships:chart} above the melee layer that plays only what
 * {@link ChartHeldClient} asks for: {@code chart_read} (held on its last frame) or {@code chart_stow} (once), each from
 * the tick that matches the chart's unroll progress. The files live in {@code assets/pirates_n_ships/chart_animations/}
 * (not PAL's {@code player_animations/}, whose contents {@code PalAnimationFilesTest} pins) and are read once each
 * with PAL's own loader, so a resource pack can replace them. First person stays vanilla's (the item's first-person
 * display shows the open chart low in the view), so the layer asks for {@link FirstPersonMode#NONE}.
 */
final class PalChartPoses implements ChartHeldClient.Driver {

    private static final ResourceLocation LAYER = Constants.id("chart");

    private final Map<String, Animation> animations = new HashMap<>();
    private final Set<String> missingReported = new HashSet<>();

    private PalChartPoses() {
    }

    /** Registers the layer factory (client setup) and returns the driver. */
    static PalChartPoses register(int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, priority, ChartLayer::new);
        return new PalChartPoses();
    }

    @Override
    public void update(Player player, @Nullable String name, float startTick) {
        ChartLayer layer = layer(player);
        if (layer == null || Objects.equals(layer.playing, name)) return;
        if (name == null) {
            layer.stopTriggeredAnimation();
            layer.stop();
            layer.playing = null;
            return;
        }
        Animation a = animation(name);
        if (a == null) return;
        layer.triggerAnimation(a, Math.min(startTick, a.length()));
        layer.playing = name;
    }

    static ResourceLocation file(String animation) {
        return Constants.id("chart_animations/" + animation + ".json");
    }

    private @Nullable Animation animation(String name) {
        Animation cached = animations.get(name);
        if (cached != null || missingReported.contains(name)) return cached;
        ResourceLocation file = file(name);
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
        Animation loaded = null;
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                loaded = UniversalAnimLoader.loadAnimations(in).get(name);
            } catch (IOException | RuntimeException e) {
                Constants.LOG.warn("Could not read the chart animation {}", file, e);
            }
        }
        if (loaded == null) {
            missingReported.add(name);
            Constants.LOG.warn("Chart animation {} is missing (resource pack?)", file);
            return null;
        }
        animations.put(name, loaded);
        return loaded;
    }

    private static @Nullable ChartLayer layer(Player player) {
        if (!(player instanceof AbstractClientPlayer clientPlayer)) return null;
        try {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(clientPlayer, LAYER);
            return layer instanceof ChartLayer chart ? chart : null;
        } catch (IllegalArgumentException e) {
            return null; // PAL's player mixin did not apply (another mod's player class)
        }
    }

    /** One player's chart layer: plays only what {@link PalChartPoses} triggers; vanilla's first person. */
    static final class ChartLayer extends PlayerAnimationController {

        @Nullable String playing;

        ChartLayer(AbstractClientPlayer player) {
            super(player, (controller, state, setter) -> PlayState.STOP);
            setFirstPersonModeHandler(c -> FirstPersonMode.NONE);
        }
    }
}
