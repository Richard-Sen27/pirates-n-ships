package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.client.ClientMeleeState;
import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import com.richardsenger.piratesnships.platform.Services;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.AnimationSnapshot;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.AdvancedBoneSnapshot;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.FadeType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Melee animations through the Player Animation Library (PAL, docs/animation-libraries.md). The only class of the mod
 * that imports {@code com.zigythebird.playeranim*}; load it only when PAL is installed ({@link MeleeAnimationsSetup}).
 *
 * <p>Every player gets one animation layer {@code pirates_n_ships:melee} (a {@link MeleeLayer}). A phase change
 * triggers the phase's animation ({@link MeleeAnimationMapping}) with a {@link SpeedModifier} that stretches it to the
 * phase's server duration and a start offset for the time already spent in the phase. A {@link MirrorModifier} flips
 * it for left-handed players. In first person the layer asks for PAL's {@code THIRD_PERSON_MODEL} mode (the animated
 * arm and sword replace the vanilla hand) unless {@link FirstPersonRule} says no. Only players are animated; other
 * entities (NPC duelists) use their own GeckoLib animations.
 *
 * <p>MEL1: a new animation that does not start where the pose on screen is (an attack cut short by a parry or a
 * stagger, a parry ending its window early, a riposte wind-up going into a slash, a parry from rest) cross-fades over
 * {@code melee_animations.fade_ticks} ({@link MeleeFades}); going back to idle mid-pose fades out the same way. A
 * crouching player keeps vanilla's crouch under the sword pose ({@link CrouchPose}).
 *
 * <p>With {@code melee_animations.enabled} off, every call goes to {@link NoopMeleeAnimations} (vanilla swing).
 */
public final class PalMeleeAnimations implements MeleeAnimations {

    private static final ResourceLocation LAYER = Constants.id(MeleeAnimationMapping.LAYER);
    /** Ticks the vanilla first-person hand needs to slide out before the animated arm shows (PAL transition). */
    private static final int FIRST_PERSON_TRANSITION_TICKS = 2;

    private final NoopMeleeAnimations vanilla = new NoopMeleeAnimations();
    private final Set<ResourceLocation> missingReported = new HashSet<>();

    private PalMeleeAnimations() {
    }

    /** Registers the layer factory (client setup, NeoForge: inside {@code enqueueWork}) and returns the implementation. */
    static PalMeleeAnimations register(int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, priority, MeleeLayer::new);
        return new PalMeleeAnimations();
    }

    // --- MeleeAnimations ---------------------------------------------------------------------------------------

    @Override
    public void predict(LocalPlayer player, MeleeAction action) {
        if (!enabled()) {
            vanilla.predict(player, action);
            return;
        }
        boolean firstPerson = firstPersonEnabled();
        switch (action) {
            case SLASH, THRUST -> {
                AttackKind kind = action == MeleeAction.SLASH ? AttackKind.SLASH : AttackKind.THRUST;
                WeaponDefinition weapon = MeleeService.weaponInHand(player).orElse(null);
                if (weapon == null) break;
                boolean riposte = ClientMeleeState.riposteTicks() > 0;
                int windup = weapon.windupTicks(kind);
                if (windup > 0) play(player, Phase.WINDUP, kind, riposte, 0, windup);
                else play(player, Phase.ACTIVE, kind, riposte, 0, weapon.activeTicks(kind));
                // without first-person animations the vanilla hand would not move at all (the melee input cancels vanilla's swing)
                if (!firstPerson) vanilla.predict(player, action);
            }
            case GUARD_DOWN -> play(player, Phase.GUARDING, null, false, 0, 0);
            case GUARD_UP -> stop(player);
            case PARRY -> play(player, Phase.PARRYING, null, false, 0, MeleeConfig.PARRY_WINDOW.get());
        }
    }

    @Override
    public void play(LivingEntity entity, Phase phase, @Nullable AttackKind attack, boolean riposte, int elapsedTicks, int durationTicks) {
        MeleeLayer layer = layer(entity);
        if (layer == null) return;
        if (!enabled()) {
            layer.halt();
            return;
        }
        String name = MeleeAnimationMapping.forPhase(phase, attack, riposte);
        if (name == null) {
            stop(entity);
            return;
        }
        if (trigger(layer, name, elapsedTicks, durationTicks)) layer.lastPhase = phase;
    }

    @Override
    public void stop(LivingEntity entity) {
        MeleeLayer layer = layer(entity);
        if (layer == null) return;
        if (!enabled()) {
            layer.halt();
            return;
        }
        String next = MeleeAnimationMapping.onStop(layer.lastPhase);
        layer.lastPhase = Phase.IDLE;
        if (next != null && trigger(layer, next, 0, 0)) return;
        int fade = layer.track.stop(layer.now(), fadeTicks());
        if (fade > 0) layer.fadeToRest(fade);
        else layer.halt();
    }

    // --- internals ---------------------------------------------------------------------------------------------

    private boolean trigger(MeleeLayer layer, String name, int elapsedTicks, int durationTicks) {
        ResourceLocation id = Constants.id(name);
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) {
            if (missingReported.add(id)) Constants.LOG.warn("Melee animation {} is missing (resource pack?)", id);
            layer.halt();
            return false;
        }
        float speed = MeleeAnimationMapping.speed(animation.length(), durationTicks);
        float start = MeleeAnimationMapping.startTick(elapsedTicks, speed, animation.length());
        // the fade only blends the pose on screen into the new animation; speed and start stay as without it
        int fade = layer.track.play(name, animation.length(), start, speed, layer.now(), fadeTicks(), durationTicks);
        layer.speed.speed = speed;
        layer.mirror.enabled = layer.getPlayer().getMainArm() == HumanoidArm.LEFT;
        layer.firstPerson = firstPersonEnabled();
        layer.fadeInto(fade);
        layer.triggerAnimation(animation, start);
        return true;
    }

    private static @Nullable MeleeLayer layer(LivingEntity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) return null;
        try {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER);
            return layer instanceof MeleeLayer melee ? melee : null;
        } catch (IllegalArgumentException e) {
            return null; // PAL's player mixin did not apply (another mod's player class)
        }
    }

    private static boolean enabled() {
        return MeleeClientConfig.ANIMATIONS_ENABLED.get();
    }

    private static int fadeTicks() {
        return MeleeClientConfig.ANIMATIONS_FADE_TICKS.get();
    }

    private static boolean firstPersonEnabled() {
        return FirstPersonRule.animateFirstPerson(MeleeClientConfig.ANIMATIONS_FIRST_PERSON.get(), Services.PLATFORM::isModLoaded);
    }

    /**
     * The melee layer of one player: a PAL controller that only plays what {@link PalMeleeAnimations} triggers.
     * Modifiers, outermost first: the mirror (left-handers), the {@link PoseModifier} (vanilla's crouch kept, the shown
     * pose recorded), a {@link FadeIn} while a cross-fade runs, and the speed that stretches the animation to its
     * phase. The fade sits outside the speed so it runs in game ticks whatever the phase's speed, and inside the
     * mirror and the crouch so the recorded pose it starts from and the animation it blends into are in the same
     * (raw, standing, right-handed) space.
     */
    static final class MeleeLayer extends PlayerAnimationController {

        /** Shows the main-hand arm and sword in first person (the mirror modifier swaps them for left-handers). */
        private static final FirstPersonConfiguration RIGHT_HANDED = new FirstPersonConfiguration(true, false, true, false);
        private static final FirstPersonConfiguration LEFT_HANDED = new FirstPersonConfiguration(false, true, false, true);

        final SpeedModifier speed = new SpeedModifier(1f);
        final MirrorModifier mirror = new MirrorModifier();
        final PoseModifier pose = new PoseModifier();
        final MeleeFades.Track track = new MeleeFades.Track();
        /** The phase whose animation was triggered last (for the guard lowering on stop). */
        Phase lastPhase = Phase.IDLE;
        boolean firstPerson = true;

        MeleeLayer(AbstractClientPlayer player) {
            // the state handler never picks animations by itself; everything is triggered
            super(player, (controller, state, setter) -> PlayState.STOP);
            mirror.enabled = false;
            addModifierLast(mirror);
            addModifierLast(pose);
            addModifierLast(speed);
            setFirstPersonModeHandler(c -> firstPerson ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
            // the layer is asked directly (not through the mirror modifier), so pick the side here
            setFirstPersonConfigurationHandler(c -> mirror.enabled ? LEFT_HANDED : RIGHT_HANDED);
            setFirstPersonTransitionLength(FIRST_PERSON_TRANSITION_TICKS);
        }

        long now() {
            return getPlayer().level().getGameTime();
        }

        /** Ends any melee animation at once. */
        void halt() {
            removeModifierIf(m -> m instanceof FadeIn);
            stopTriggeredAnimation();
            stop();
            lastPhase = Phase.IDLE;
            track.halt(now());
        }

        /**
         * Starts a cross-fade of {@code ticks} from the pose on screen into whatever is triggered next (0 = none, the
         * new animation snaps in as before). PAL's own {@code replaceAnimationWithFade} always starts the new animation
         * at its first frame and snapshots the raw bones of the old one; this keeps the start offset and fades from
         * the pose actually shown, including a fade that was still running.
         */
        void fadeInto(int ticks) {
            AnimationSnapshot from = ticks > 0 ? shownPose() : null;
            removeModifierIf(m -> m instanceof FadeIn);
            if (ticks > 0) addModifier(new FadeIn(ticks, from), getModifierCount() - 1); // just outside the speed
        }

        /** Fades from the pose on screen back to vanilla's over {@code ticks}, then the layer ends by itself. */
        void fadeToRest(int ticks) {
            fadeInto(ticks);
            speed.speed = 1f;
            // an empty animation: nothing keyed, so the blend target is the vanilla pose coming in
            triggerAnimation(RawAnimation.begin().thenWait(ticks), 0);
        }

        /**
         * The pose on screen as a snapshot of the channels the melee animations key: what the {@link PoseModifier}
         * recorded at the last frame, or (not drawn lately, e.g. a remote player out of view) the controller's own
         * bones. {@code null} when there is nothing to fade from (the fade then starts at the vanilla pose).
         */
        private @Nullable AnimationSnapshot shownPose() {
            boolean fresh = now() - pose.recordedAt <= 1;
            Map<String, AdvancedBoneSnapshot> snapshots = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : MeleeFades.FADED_CHANNELS.entrySet()) {
                PlayerAnimBone source = fresh ? pose.recorded.get(e.getKey()) : null;
                if (source == null && isActive()) source = activeBones.get(e.getKey());
                if (source == null) continue;
                AdvancedBoneSnapshot s = new AdvancedBoneSnapshot(source);
                boolean rot = e.getValue().contains("rotation");
                boolean pos = e.getValue().contains("position");
                s.rotXEnabled = s.rotYEnabled = s.rotZEnabled = rot;
                s.positionXEnabled = s.positionYEnabled = s.positionZEnabled = pos;
                s.scaleXEnabled = s.scaleYEnabled = s.scaleZEnabled = false;
                s.bendEnabled = false;
                snapshots.put(e.getKey(), s);
            }
            return snapshots.isEmpty() ? null : new AnimationSnapshot(snapshots);
        }

        /**
         * Keeps vanilla's crouch under the sword animation ({@link CrouchPose}) and records the pose it passes on, for
         * the next cross-fade to start from.
         */
        final class PoseModifier extends AbstractModifier {

            final Map<String, PlayerAnimBone> recorded = new HashMap<>();
            long recordedAt = Long.MIN_VALUE;

            @Override
            public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
                CrouchPose.Offset crouch = CrouchPose.applies(MeleeClientConfig.ANIMATIONS_KEEP_CROUCH.get(),
                        getPlayer().isCrouching(), FirstPersonMode.isFirstPersonPass()) ? CrouchPose.forBone(bone.getName()) : null;
                if (crouch != null) {
                    bone.rotX -= crouch.rotX();
                    bone.positionY -= crouch.posY();
                }
                bone = super.get3DTransform(bone);
                if (MeleeFades.FADED_CHANNELS.containsKey(bone.getName())) {
                    recorded.computeIfAbsent(bone.getName(), PlayerAnimBone::new).copyOtherBone(bone);
                    recordedAt = now();
                }
                if (crouch != null) {
                    bone.rotX += crouch.rotX();
                    bone.positionY += crouch.posY();
                }
                return bone;
            }
        }
    }

    /**
     * A cross-fade into the layer's animation from a snapshot of the previous pose ({@code null}: from the vanilla
     * pose), smoothstep-weighted ({@link MeleeFades#alpha}). Removed by the controller once complete.
     */
    static final class FadeIn extends AbstractFadeModifier {

        FadeIn(int ticks, @Nullable AnimationSnapshot from) {
            super(ticks);
            setTransitionAnimation(from);
        }

        @Override
        protected float getAlpha(String boneName, float progress) {
            return MeleeFades.alpha(progress);
        }

        @Override
        protected FadeType getFadeType() {
            return FadeType.FADE_IN;
        }

        @Override
        public void setupAnim(AnimationData state) {
            // the speed modifier inside rewrites the partial tick to its own animation time; the fade runs in game time
            float partialTick = state.getPartialTick();
            super.setupAnim(state);
            tickDelta = partialTick;
        }
    }
}
