package com.richardsenger.piratesnships.combat.melee.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimations;
import com.richardsenger.piratesnships.combat.melee.net.MeleeActions;
import com.richardsenger.piratesnships.combat.melee.net.MeleeNet;
import com.richardsenger.piratesnships.combat.melee.net.MeleeStatePayload;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Client side of the melee module (physical client only, called from {@code MeleeModule.initClient()}). */
public final class MeleeClient {

    private MeleeClient() {
    }

    public static void init() {
        ClientEvents.INTERACTION_KEY.register(MeleeInput::onInteraction);
        ClientEvents.CLIENT_TICK_END.register(MeleeInput::onClientTickEnd);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> {
            MeleeInput.reset();
            ClientMeleeState.reset();
        });
        ClientEvents.registerHudLayer(Constants.id("melee_stamina"), MeleeHud::render);
        MeleeNet.setClientReceiver(MeleeClient::onState);
    }

    /**
     * A {@link MeleeStatePayload} arrived (client main thread). About the local player: HUD state, refusal feedback and
     * correction of the predicted animation. About anyone else: their animation.
     */
    static void onState(MeleeStatePayload p, Player receiver) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(p.entityId());
        if (!(entity instanceof LivingEntity living)) return;
        MeleeAnimations anim = MeleeAnimations.get();
        if (mc.player != null && entity == mc.player) {
            ClientMeleeState.acceptOwn(p);
            String key = MeleeActions.refusalMessageKey(p.refusal());
            if (key != null) mc.gui.setOverlayMessage(Component.translatable(key), false);
            if (p.hasStamina()) {
                // the owner copy: animations follow the observer copy, except a refused prediction is undone here
                if (p.refusal() != Refusal.NONE && MeleeInput.predictionPending()) {
                    MeleeInput.clearPrediction();
                    play(anim, living, p);
                }
                return;
            }
            if (MeleeInput.confirmsPrediction(p.phase(), p.attack())) return;
        }
        play(anim, living, p);
    }

    private static void play(MeleeAnimations anim, LivingEntity entity, MeleeStatePayload p) {
        if (p.phase() == Phase.IDLE) anim.stop(entity);
        else anim.play(entity, p.phase(), p.attack(), p.riposteAttack(), p.elapsed(), p.duration());
    }
}
