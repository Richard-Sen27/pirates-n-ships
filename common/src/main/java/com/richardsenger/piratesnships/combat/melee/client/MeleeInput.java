package com.richardsenger.piratesnships.combat.melee.client;

import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimations;
import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;
import com.richardsenger.piratesnships.combat.melee.net.MeleeActionPayload;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Melee input of the local player (docs/design.md §8.5, "Client"). While a skill-based sword is in the main hand and
 * {@code melee.skill_based_combat} is on, vanilla's attack and use are cancelled ({@link ClientEvents#INTERACTION_KEY})
 * and both keys are sampled once per client tick into {@link MeleeInputClassifier}; each resulting action is sent as a
 * {@link MeleeActionPayload} and predicted through {@link MeleeAnimations}. With any other item, or the system off,
 * nothing is intercepted. Client thread only.
 */
public final class MeleeInput {

    private static final MeleeInputClassifier CLASSIFIER = new MeleeInputClassifier();
    /** A click seen by the interaction hook this tick (a press and release inside one tick is not visible by polling). */
    private static boolean attackClicked;
    private static boolean useClicked;
    private static boolean wasActive;
    /** Keys already held when the sword came out count only after they were released once. */
    private static boolean attackBlocked;
    private static boolean useBlocked;
    /** The phase the last sent action should lead to, until the server confirms or contradicts it. */
    private static @Nullable Phase predictedPhase;
    private static @Nullable AttackKind predictedAttack;

    private MeleeInput() {
    }

    /** Whether melee input replaces vanilla's attack and use right now. */
    static boolean active(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isSpectator() || !p.isAlive()) return false;
        return MeleeService.skillBasedCombat() && MeleeService.weaponInHand(p).isPresent();
    }

    /** {@link ClientEvents#INTERACTION_KEY} listener. */
    static ClientEvents.InteractionKeyResult onInteraction(Minecraft mc, ClientEvents.InteractionInput input, InteractionHand hand) {
        if (input == ClientEvents.InteractionInput.PICK_BLOCK || !active(mc)) return ClientEvents.InteractionKeyResult.PASS;
        if (input == ClientEvents.InteractionInput.ATTACK) attackClicked = true;
        else useClicked = true;
        return ClientEvents.InteractionKeyResult.CANCEL;
    }

    /** {@link ClientEvents#CLIENT_TICK_END} listener. */
    static void onClientTickEnd(Minecraft mc) {
        ClientMeleeState.tick();
        if (mc.player == null || mc.getConnection() == null) {
            reset();
            return;
        }
        boolean active = active(mc) && mc.screen == null;
        boolean attackDown = attackClicked || mc.options.keyAttack.isDown();
        boolean useDown = useClicked || mc.options.keyUse.isDown();
        attackClicked = false;
        useClicked = false;
        if (active && !wasActive) {
            attackBlocked = attackDown;
            useBlocked = useDown;
        }
        wasActive = active;
        if (!attackDown) attackBlocked = false;
        if (!useDown) useBlocked = false;
        List<MeleeAction> actions = active
                ? CLASSIFIER.tick(attackDown && !attackBlocked, useDown && !useBlocked, MeleeClientConfig.thresholds())
                : CLASSIFIER.reset();
        for (MeleeAction a : actions) send(mc, mc.player, a);
    }

    private static void send(Minecraft mc, LocalPlayer player, MeleeAction action) {
        int clientTick = mc.level == null ? 0 : (int) mc.level.getGameTime();
        Services.NETWORK.sendToServer(new MeleeActionPayload(action, clientTick));
        switch (action) {
            case SLASH -> predict(Phase.WINDUP, AttackKind.SLASH);
            case THRUST -> predict(Phase.WINDUP, AttackKind.THRUST);
            case GUARD_DOWN -> predict(Phase.GUARDING, null);
            case GUARD_UP -> predict(Phase.IDLE, null);
            case PARRY -> predict(Phase.PARRYING, null);
        }
        MeleeAnimations.get().predict(player, action);
    }

    private static void predict(Phase phase, @Nullable AttackKind attack) {
        predictedPhase = phase;
        predictedAttack = attack;
    }

    /**
     * Whether a server-confirmed phase of the local player was already predicted (then the animation is already
     * playing). Consumes the prediction either way: a contradiction means the server's state wins from now on.
     */
    static boolean confirmsPrediction(Phase phase, @Nullable AttackKind attack) {
        boolean match = predictedPhase != null && predictedPhase == phase && predictedAttack == attack;
        predictedPhase = null;
        predictedAttack = null;
        return match;
    }

    static boolean predictionPending() {
        return predictedPhase != null;
    }

    static void clearPrediction() {
        predictedPhase = null;
        predictedAttack = null;
    }

    static void reset() {
        CLASSIFIER.reset();
        attackClicked = false;
        useClicked = false;
        wasActive = false;
        attackBlocked = false;
        useBlocked = false;
        predictedPhase = null;
        predictedAttack = null;
    }
}
