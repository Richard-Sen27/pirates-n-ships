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
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
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
        String next = enabled() ? MeleeAnimationMapping.onStop(layer.lastPhase) : null;
        layer.lastPhase = Phase.IDLE;
        if (next == null || !trigger(layer, next, 0, 0)) layer.halt();
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
        layer.speed.speed = speed;
        layer.mirror.enabled = layer.getPlayer().getMainArm() == HumanoidArm.LEFT;
        layer.firstPerson = firstPersonEnabled();
        layer.triggerAnimation(animation, MeleeAnimationMapping.startTick(elapsedTicks, speed, animation.length()));
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

    private static boolean firstPersonEnabled() {
        return FirstPersonRule.animateFirstPerson(MeleeClientConfig.ANIMATIONS_FIRST_PERSON.get(), Services.PLATFORM::isModLoaded);
    }

    /**
     * The melee layer of one player: a PAL controller that only plays what {@link PalMeleeAnimations} triggers,
     * with a speed and a mirror modifier, and the first-person settings.
     */
    static final class MeleeLayer extends PlayerAnimationController {

        /** Shows the main-hand arm and sword in first person (the mirror modifier swaps them for left-handers). */
        private static final FirstPersonConfiguration RIGHT_HANDED = new FirstPersonConfiguration(true, false, true, false);
        private static final FirstPersonConfiguration LEFT_HANDED = new FirstPersonConfiguration(false, true, false, true);

        final SpeedModifier speed = new SpeedModifier(1f);
        final MirrorModifier mirror = new MirrorModifier();
        /** The phase whose animation was triggered last (for the guard lowering on stop). */
        Phase lastPhase = Phase.IDLE;
        boolean firstPerson = true;

        MeleeLayer(AbstractClientPlayer player) {
            // the state handler never picks animations by itself; everything is triggered
            super(player, (controller, state, setter) -> PlayState.STOP);
            mirror.enabled = false;
            addModifierLast(speed);
            addModifierLast(mirror);
            setFirstPersonModeHandler(c -> firstPerson ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
            // the layer is asked directly (not through the mirror modifier), so pick the side here
            setFirstPersonConfigurationHandler(c -> mirror.enabled ? LEFT_HANDED : RIGHT_HANDED);
            setFirstPersonTransitionLength(FIRST_PERSON_TRANSITION_TICKS);
        }

        /** Ends any melee animation at once. */
        void halt() {
            stopTriggeredAnimation();
            stop();
            lastPhase = Phase.IDLE;
        }
    }
}
