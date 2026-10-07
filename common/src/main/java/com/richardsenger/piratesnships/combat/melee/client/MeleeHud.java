package com.richardsenger.piratesnships.combat.melee.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.core.client.gui.GuiSprites;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The stamina bar (docs/design.md §8.5, "Rules"), shown while a skill-based sword is held: a brass-framed trough
 * with a fill from deep red to amber and a tick mark every 25 %. Where it sits: {@link StaminaHudLayout} (client
 * config {@code melee_hud.position}, offsets, scale). It fades out while stamina is full outside a fight and comes back
 * at once ({@link StaminaHudFade}). A refused action flashes the frame red; during the riposte window white-gold
 * sparks blink at both ends; a stagger tints the fill violet with a shrinking violet line beside it; during a parry
 * lockout a grey padlock shows next to the bar.
 */
public final class MeleeHud {

    private static final int TICK_MARK = 0xB0281A0E;
    private static final int STAGGER_TINT = 0x80A040D0;
    private static final int STAGGER_LINE = 0xFFC060F0;

    private static final StaminaHudFade FADE = new StaminaHudFade();
    private static boolean shownLastTick;

    private MeleeHud() {
    }

    /** The bar could show: a skill-based sword in hand, HUD on. */
    private static boolean active(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || !MeleeClientConfig.HUD_ENABLED.get()) return false;
        return MeleeService.skillBasedCombat() && MeleeService.weaponInHand(player).isPresent();
    }

    private static float fraction() {
        float fallback = MeleeConfig.STAMINA_MAX.get().floatValue();
        float max = ClientMeleeState.maxStamina(fallback);
        return Mth.clamp(ClientMeleeState.stamina(max) / max, 0f, 1f);
    }

    private static boolean full(float frac) {
        return frac >= 0.999f;
    }

    /** Guard, attack, parry, stagger, riposte, lockout, a refusal or a hit taken. */
    private static boolean fighting(LocalPlayer player) {
        return ClientMeleeState.phase() != Phase.IDLE || ClientMeleeState.riposteTicks() > 0 || ClientMeleeState.lockoutTicks() > 0
                || ClientMeleeState.flashTicks() > 0 || ClientMeleeState.staggerTicks() > 0 || player.hurtTime > 0;
    }

    /** Client tick: advances the fade. */
    static void tick(Minecraft mc) {
        boolean shown = active(mc);
        if (!shown) {
            if (shownLastTick) FADE.reset();
        } else {
            FADE.tick(full(fraction()), fighting(mc.player));
        }
        shownLastTick = shown;
    }

    /** Left the world. */
    static void reset() {
        FADE.reset();
        shownLastTick = false;
    }

    private static StaminaHudLayout.Hud hud(Minecraft mc, GuiGraphics g) {
        LocalPlayer player = mc.player;
        boolean statusBars = mc.gameMode != null && mc.gameMode.canHurtPlayer();
        int vehicleRows = 0;
        if (player.getVehicle() instanceof LivingEntity vehicle && vehicle.showVehicleHealth()) {
            int hearts = Math.min(30, (int) (vehicle.getMaxHealth() + 0.5f) / 2);
            vehicleRows = (hearts + 9) / 10;
        }
        boolean air = player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply();
        int above = Math.max(0, vehicleRows - 1) + (air ? 1 : 0);
        return new StaminaHudLayout.Hud(g.guiWidth(), g.guiHeight(), statusBars, above);
    }

    /** HUD layer. */
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !active(mc)) return;
        LocalPlayer player = mc.player;

        float frac = fraction();
        float alpha = MeleeClientConfig.HUD_OPACITY.get().floatValue();
        if (MeleeClientConfig.HUD_FADE.get()) {
            alpha *= FADE.visibility(full(frac), fighting(player), delta.getGameTimeDeltaPartialTick(false),
                    MeleeClientConfig.HUD_FADE_DELAY.get(), MeleeClientConfig.HUD_FADE_TICKS.get());
        }
        if (alpha <= 0.01f) return;

        double scale = MeleeClientConfig.HUD_SCALE.get();
        StaminaHudLayout.Rect r = StaminaHudLayout.place(MeleeClientConfig.HUD_POSITION.get(), hud(mc, g), scale,
                MeleeClientConfig.HUD_X_OFFSET.get(), MeleeClientConfig.HUD_Y_OFFSET.get());
        boolean vertical = r.vertical();
        int w = vertical ? StaminaHudLayout.VERTICAL_W : StaminaHudLayout.HORIZONTAL_W;
        int h = vertical ? StaminaHudLayout.VERTICAL_H : StaminaHudLayout.HORIZONTAL_H;
        int length = vertical ? StaminaHudLayout.FILL_LENGTH_V : StaminaHudLayout.FILL_LENGTH_H;
        long now = ClientMeleeState.now();

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(r.x(), r.y(), 0);
        pose.scale((float) r.w() / w, (float) r.h() / h, 1f);
        g.setColor(1f, 1f, 1f, alpha);

        GuiKit.sprite(g, GuiSprites.STAMINA_TROUGH, 0, 0, w, h);
        int filled = Math.round(length * frac);
        RenderSystem.enableBlend();
        if (filled > 0) {
            if (vertical) {
                g.blitSprite(GuiSprites.STAMINA_FILL_VERTICAL, 5, length, 0, length - filled, 1, 1 + length - filled, 5, filled);
            } else {
                g.blitSprite(GuiSprites.STAMINA_FILL, length, 5, 0, 0, 1, 1, filled, 5);
            }
        }
        // tick marks every 25 %
        for (int k = 1; k < 4; k++) {
            int at = 1 + length * k / 4;
            if (vertical) g.fill(1, at, 6, at + 1, TICK_MARK);
            else g.fill(at, 1, at + 1, 6, TICK_MARK);
        }

        int stagger = ClientMeleeState.staggerTicks();
        int staggerDur = ClientMeleeState.staggerDuration();
        if (stagger > 0 && staggerDur > 0) {
            if (vertical) g.fill(1, 1, 6, 1 + length, STAGGER_TINT);
            else g.fill(1, 1, 1 + length, 6, STAGGER_TINT);
            int len = Math.max(1, Math.round(length * Math.min(1f, stagger / (float) staggerDur)));
            if (vertical) g.fill(w + 1, h - len, w + 2, h, STAGGER_LINE);
            else g.fill(w / 2 - len / 2, -3, w / 2 + len - len / 2, -2, STAGGER_LINE);
        }
        if (ClientMeleeState.lockoutTicks() > 0) {
            if (vertical) GuiKit.sprite(g, GuiSprites.LOCKOUT, 1, -9, 5, 7);
            else GuiKit.sprite(g, GuiSprites.LOCKOUT, -11, 0, 5, 7);
        }
        if (ClientMeleeState.riposteTicks() > 0 && (now / 2) % 2 == 0) {
            int ry = vertical ? (h - 9) / 2 : -1;
            GuiKit.sprite(g, GuiSprites.RIPOSTE, -4, ry, 3, 9);
            GuiKit.sprite(g, GuiSprites.RIPOSTE, w + 1, ry, 3, 9);
        }
        int flash = ClientMeleeState.flashTicks();
        if (flash > 0) {
            g.setColor(1f, 1f, 1f, alpha * flash / (float) ClientMeleeState.FLASH_TICKS);
            GuiKit.sprite(g, GuiSprites.STAMINA_TROUGH_ALERT, 0, 0, w, h);
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
