package com.richardsenger.piratesnships.combat.firearms.client.anim;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.anim.FirstPersonRule;
import com.richardsenger.piratesnships.platform.Services;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Firearm animations through the Player Animation Library (PAL, docs/animation-libraries.md). The only class of the
 * firearms that imports {@code com.zigythebird.playeranim*}; load it only when PAL is installed
 * ({@link FirearmAnimationsSetup}).
 *
 * <p>Every player gets one layer {@code pirates_n_ships:firearms} (a {@link FirearmLayer}), registered below the
 * melee layer so a sword animation wins. Each tick {@link #update} keeps the pose {@link FirearmAnimationMapping}
 * derived from the use state playing: an aim holds its last frame while the session lasts, a reload plays once with
 * a {@link SpeedModifier} that stretches it to the gun's reload time, and the layer stops when the player lets go.
 * While aiming, an {@link AdjustmentModifier} adds the look pitch and the head's turn against the body to both arms
 * (as vanilla's bow and crossbow poses do), so the barrel follows the crosshair. A {@link MirrorModifier} flips the
 * animation for a gun in the left hand. In first person the layer asks for PAL's {@code THIRD_PERSON_MODEL} mode with
 * both arms and the gun shown, unless {@link FirstPersonRule} ({@code melee_animations.first_person}) says no.
 *
 * <p>With {@code firearm_animations.enabled} off the layer stays stopped (vanilla's held pose).
 */
public final class PalFirearmAnimations implements FirearmAnimations {

    private static final ResourceLocation LAYER = Constants.id(FirearmAnimationMapping.LAYER);
    /** Ticks the vanilla first-person hand needs to slide out before the animated arms show (PAL transition). */
    private static final int FIRST_PERSON_TRANSITION_TICKS = 2;

    private final Set<ResourceLocation> missingReported = new HashSet<>();

    private PalFirearmAnimations() {
    }

    /** Registers the layer factory (client setup, NeoForge: inside {@code enqueueWork}) and returns the implementation. */
    static PalFirearmAnimations register(int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, priority, FirearmLayer::new);
        return new PalFirearmAnimations();
    }

    @Override
    public void update(Player player, FirearmAnimationMapping.@Nullable Pose pose, boolean leftArm, int reloadTicks) {
        FirearmLayer layer = layer(player);
        if (layer == null) return;
        if (pose == null || !MeleeClientConfig.FIREARM_ANIMATIONS_ENABLED.get()) {
            if (layer.current != null) layer.halt();
            return;
        }
        if (FirearmAnimationMapping.restarts(layer.current, pose) && !trigger(layer, pose, leftArm, reloadTicks)) return;
        layer.current = pose;
    }

    private boolean trigger(FirearmLayer layer, FirearmAnimationMapping.Pose pose, boolean leftArm, int reloadTicks) {
        ResourceLocation id = Constants.id(pose.animation());
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) {
            if (missingReported.add(id)) Constants.LOG.warn("Firearm animation {} is missing (resource pack?)", id);
            layer.halt();
            return false;
        }
        float speed = FirearmAnimationMapping.speed(pose.aim(), animation.length(), reloadTicks);
        layer.speed.speed = speed;
        layer.mirror.enabled = leftArm;
        layer.adjustment.enabled = pose.aim();
        layer.firstPerson = FirstPersonRule.animateFirstPerson(MeleeClientConfig.ANIMATIONS_FIRST_PERSON.get(), Services.PLATFORM::isModLoaded);
        layer.triggerAnimation(animation, FirearmAnimationMapping.startTick(pose.heldTicks(), speed, animation.length()));
        return true;
    }

    private static @Nullable FirearmLayer layer(Player player) {
        if (!(player instanceof AbstractClientPlayer clientPlayer)) return null;
        try {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(clientPlayer, LAYER);
            return layer instanceof FirearmLayer firearm ? firearm : null;
        } catch (IllegalArgumentException e) {
            return null; // PAL's player mixin did not apply (another mod's player class)
        }
    }

    /**
     * The firearm layer of one player: a PAL controller that only plays what {@link PalFirearmAnimations} triggers,
     * with the aim adjustment, a speed and a mirror modifier, and the first-person settings.
     */
    static final class FirearmLayer extends PlayerAnimationController {

        /** Both arms (the musket needs the left one) and the gun's item bone; the off-hand item stays hidden. */
        private static final FirstPersonConfiguration RIGHT_HANDED = new FirstPersonConfiguration(true, true, true, false);
        private static final FirstPersonConfiguration LEFT_HANDED = new FirstPersonConfiguration(true, true, false, true);

        final AdjustmentModifier adjustment;
        final SpeedModifier speed = new SpeedModifier(1f);
        final MirrorModifier mirror = new MirrorModifier();
        /** The pose that plays (updated every tick while it lasts), {@code null} when the layer is stopped. */
        FirearmAnimationMapping.@Nullable Pose current;
        boolean firstPerson = true;

        FirearmLayer(AbstractClientPlayer player) {
            // the state handler never picks animations by itself; everything is triggered
            super(player, (controller, state, setter) -> PlayState.STOP);
            adjustment = new AdjustmentModifier(bone -> aimAdjustment(player, bone));
            adjustment.fadeIn = false;
            adjustment.fadeOut = false;
            adjustment.enabled = false;
            mirror.enabled = false;
            // the adjustment goes first (PAL: outermost), so it acts on the mirrored pose like vanilla's arm aim
            addModifierLast(adjustment);
            addModifierLast(speed);
            addModifierLast(mirror);
            setFirstPersonModeHandler(c -> firstPerson ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
            setFirstPersonConfigurationHandler(c -> mirror.enabled ? LEFT_HANDED : RIGHT_HANDED);
            setFirstPersonTransitionLength(FIRST_PERSON_TRANSITION_TICKS);
        }

        /** Ends any firearm animation at once. */
        void halt() {
            stopTriggeredAnimation();
            stop();
            adjustment.enabled = false;
            current = null;
        }

        /** Look pitch and head turn added to both arms while aiming (radians, vanilla model angles). */
        private static Optional<AdjustmentModifier.PartModifier> aimAdjustment(AbstractClientPlayer player, String bone) {
            if (!bone.equals("right_arm") && !bone.equals("left_arm")) return Optional.empty();
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            float pitch = player.getViewXRot(partialTick);
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot)
                    - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
            return Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(pitch * Mth.DEG_TO_RAD, headYaw * Mth.DEG_TO_RAD, 0f), Vec3f.ZERO));
        }
    }
}
