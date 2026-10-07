package com.richardsenger.piratesnships.combat.melee.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The stamina bar (docs/design.md §8.5, "Rules"): a thin bar centred above the health and food rows while a
 * skill-based sword is held. Gold = stamina left, dark = drained. A refused action flashes the frame red; during the
 * riposte window a white marker blinks on both ends; a stagger tints the bar violet with a shrinking violet line above
 * it; during a parry lockout the left end shows a small grey block. Client config {@code melee_hud}.
 */
public final class MeleeHud {

    static final int WIDTH = 80;
    static final int HEIGHT = 4;
    /** Bar top, measured up from the bottom of the screen: just above the armor row and the item name. */
    static final int BASE_Y = 64;

    private static final int FRAME = 0xC0000000;
    private static final int FILLED = 0xFFE0B040;
    private static final int FILLED_LOW = 0xFFE07030;
    private static final int DRAINED = 0xFF3A2E14;
    private static final int STAGGER_TINT = 0x80A040D0;
    private static final int STAGGER_LINE = 0xFFC060F0;
    private static final int RIPOSTE = 0xFFFFFFFF;
    private static final int LOCKOUT = 0xFF808080;
    private static final int FLASH_RGB = 0xE02020;

    private MeleeHud() {
    }

    /** HUD layer. */
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator() || !MeleeClientConfig.HUD_ENABLED.get()) return;
        if (!MeleeService.skillBasedCombat() || MeleeService.weaponInHand(player).isEmpty()) return;

        float fallback = MeleeConfig.STAMINA_MAX.get().floatValue();
        float max = ClientMeleeState.maxStamina(fallback);
        float frac = Mth.clamp(ClientMeleeState.stamina(max) / max, 0f, 1f);
        float scale = MeleeClientConfig.HUD_SCALE.get().floatValue();
        int cx = g.guiWidth() / 2 + MeleeClientConfig.HUD_X_OFFSET.get();
        int top = g.guiHeight() - BASE_Y + MeleeClientConfig.HUD_Y_OFFSET.get();
        long now = ClientMeleeState.now();

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, top, 0);
        pose.scale(scale, scale, 1f);
        int l = -WIDTH / 2;
        int r = WIDTH / 2;

        g.fill(l - 1, -1, r + 1, HEIGHT + 1, FRAME);
        int split = l + Math.round(WIDTH * frac);
        g.fill(l, 0, split, HEIGHT, frac < 0.25f ? FILLED_LOW : FILLED);
        g.fill(split, 0, r, HEIGHT, DRAINED);

        int stagger = ClientMeleeState.staggerTicks();
        int staggerDur = ClientMeleeState.staggerDuration();
        if (stagger > 0 && staggerDur > 0) {
            g.fill(l, 0, r, HEIGHT, STAGGER_TINT);
            int len = Math.max(1, Math.round(WIDTH * Math.min(1f, stagger / (float) staggerDur)));
            g.fill(-len / 2, -3, len - len / 2, -2, STAGGER_LINE);
        }
        if (ClientMeleeState.lockoutTicks() > 0) {
            g.fill(l - 4, 0, l - 2, HEIGHT, LOCKOUT);
        }
        if (ClientMeleeState.riposteTicks() > 0 && (now / 2) % 2 == 0) {
            g.fill(l - 4, -1, l - 2, HEIGHT + 1, RIPOSTE);
            g.fill(r + 2, -1, r + 4, HEIGHT + 1, RIPOSTE);
        }
        int flash = ClientMeleeState.flashTicks();
        if (flash > 0) {
            int alpha = Math.round(0xD0 * flash / (float) ClientMeleeState.FLASH_TICKS);
            g.fill(l - 1, -1, r + 1, HEIGHT + 1, (alpha << 24) | FLASH_RGB);
        }
        pose.popPose();
    }
}
