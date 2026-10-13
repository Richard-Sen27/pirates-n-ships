package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.anim.FirstPersonRule;
import com.richardsenger.piratesnships.platform.Services;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.richardsenger.piratesnships.combat.grapple.RopeLean;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
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
 * The rope poses through PAL: the slide (GR2), the haul (ART7) and GR8's hang, climb, swing and pull
 * ({@link com.richardsenger.piratesnships.combat.grapple.RopePose}). The only class of the grapple that imports
 * {@code com.zigythebird.playeranim*}; load it only when PAL is installed ({@link RopeSlidePoses#onClientSetup}).
 *
 * <p>Every player gets a layer {@code pirates_n_ships:rope_slide} above the melee layer. It plays the pose's animation
 * (the slide, hang and swing hold their last frame, the haul loops, the climb and the pull play once per pull, started
 * again when the pull counter moves); a change of pose fades over {@link #FADE_TICKS} from the pose on screen; with no
 * pose it is stopped. While the player hangs by the hands, a {@link LeanModifier} tips the whole body (PAL's
 * {@code body} bone) along the rope toward the hook ({@link RopeLean}), eased per tick and interpolated per frame, in
 * third person only. The animation files live in {@code assets/pirates_n_ships/rope_animations/}, not in PAL's
 * {@code player_animations/} folder (whose contents {@code PalAnimationFilesTest} pins to the melee and firearm
 * animations), and are read once each through the resource manager with PAL's own loader, so a resource pack can still
 * replace them.
 */
final class PalRopeSlidePoses implements RopeSlidePoses.Driver {

    private static final ResourceLocation LAYER = Constants.id(RopeSlidePoses.ANIMATION);
    /** Ticks a change from one rope pose to another fades over. */
    static final int FADE_TICKS = 3;

    private final Map<String, Animation> animations = new HashMap<>();
    private final Set<String> missingReported = new HashSet<>();

    private PalRopeSlidePoses() {
    }

    /** Registers the layer factory (client setup) and returns the driver. */
    static PalRopeSlidePoses register(int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, priority, RopeLayer::new);
        return new PalRopeSlidePoses();
    }

    @Override
    public void update(Player player, @Nullable String name, int serial, RopeLean.Lean lean) {
        RopeLayer layer = layer(player);
        if (layer == null) return;
        layer.lean.ease(lean);
        if (Objects.equals(layer.playing, name) && (name == null || layer.serial == serial)) return;
        if (name == null) {
            layer.stopTriggeredAnimation();
            layer.stop();
            layer.playing = null;
            layer.lean.reset();
            return;
        }
        Animation a = animation(name);
        if (a == null) return;
        layer.firstPerson = FirstPersonRule.animateFirstPerson(MeleeClientConfig.ANIMATIONS_FIRST_PERSON.get(), Services.PLATFORM::isModLoaded);
        if (layer.playing != null && layer.isActive()) {
            // from one rope pose to the next (or the next pull): fade from what is on screen, no snap through the rest pose
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE), a, false);
        } else {
            layer.triggerAnimation(a);
        }
        layer.playing = name;
        layer.serial = serial;
    }

    static ResourceLocation file(String animation) {
        return Constants.id("rope_animations/" + animation + ".json");
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
                Constants.LOG.warn("Could not read the rope animation {}", file, e);
            }
        }
        if (loaded == null) {
            missingReported.add(name);
            Constants.LOG.warn("Rope animation {} is missing (resource pack?)", file);
            return null;
        }
        animations.put(name, loaded);
        return loaded;
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

    /**
     * One player's rope layer: plays only what {@link PalRopeSlidePoses} triggers. In first person it shows both raised
     * arms (no held items) under the same rule as the melee animations ({@code melee_animations.first_person}).
     */
    static final class RopeLayer extends PlayerAnimationController {

        private static final FirstPersonConfiguration BOTH_ARMS = new FirstPersonConfiguration(true, true, false, false);

        @Nullable String playing;
        int serial;
        boolean firstPerson = true;
        final LeanModifier lean = new LeanModifier();

        RopeLayer(AbstractClientPlayer player) {
            super(player, (controller, state, setter) -> PlayState.STOP);
            setFirstPersonModeHandler(c -> firstPerson ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
            setFirstPersonConfigurationHandler(c -> BOTH_ARMS);
            addModifierBefore(lean);
        }
    }

    /**
     * GR8: tips the whole body along the rope ({@link RopeLean}) by adding to PAL's {@code body} bone, which PAL applies
     * right after vanilla's body turn. Eased toward the target once per client tick ({@link #ease}), interpolated with
     * the frame's partial tick; skipped in the first-person pass (the camera would tip with it).
     */
    static final class LeanModifier extends AbstractModifier {
        private RopeLean.Lean previous = RopeLean.Lean.NONE;
        private RopeLean.Lean current = RopeLean.Lean.NONE;
        private float partialTick;

        /** One client tick: a step of {@link RopeSlidePoses#LEAN_EASE} toward {@code target}. */
        void ease(RopeLean.Lean target) {
            previous = current;
            current = current.toward(target, RopeSlidePoses.LEAN_EASE);
        }

        void reset() {
            previous = RopeLean.Lean.NONE;
            current = RopeLean.Lean.NONE;
        }

        @Override
        public void setupAnim(AnimationData state) {
            partialTick = state.getPartialTick();
            super.setupAnim(state);
        }

        @Override
        public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
            PlayerAnimBone b = super.get3DTransform(bone);
            if (!"body".equals(bone.getName()) || FirstPersonMode.isFirstPersonPass()) {
                return b;
            }
            RopeLean.Lean l = previous.toward(current, partialTick);
            b.updateRotation(b.getRotX() + l.rx(), b.getRotY(), b.getRotZ() + l.rz());
            b.updatePosition(b.getPosX() + l.px(), b.getPosY() + l.py(), b.getPosZ() + l.pz());
            return b;
        }
    }
}
