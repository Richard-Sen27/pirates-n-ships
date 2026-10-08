package com.richardsenger.piratesnships.ship.hull.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.StaminaHudLayout;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * The ship HUD (docs/design.md §4.6, HUD1, HUD2), a HUD layer drawn while the player is aboard and a fresh
 * {@link ShipStatusPayload} is in {@link ClientShipStatus}, in two panels. The compass panel (bottom left by default,
 * above the chat): a north-up compass rose with a ship-shaped needle for the heading and the wind as an arrow outside
 * the rose (on the side it blows from, pointing where it blows, longer for stronger wind, amber in a gust); under it
 * speed and rudder; then the ship's name, followed by the cargo load level (CW1) when known. The hull panel (bottom
 * right by default, clear of the hotbar and the stamina bar): the hull strip, bow on the left, one cell per compartment
 * filling blue with water, a red tick on a cell with an open breach and a pump glyph while a pump drains it. Layout:
 * {@link ShipHudLayout}; texture: {@link ShipHudSheet}; config {@code ship_hud}.
 */
public final class ShipHud {

    private static final int BACKING = 0x70101418;
    private static final int TEXT = 0xFFEEE0BC;
    private static final int TEXT_DIM = 0xFFB8A27C;
    private static final int CELL_FRAME = 0xFF8A6A2E;
    private static final int CELL_EMPTY = 0xC0202830;
    private static final int WATER = 0xFF2F64B4;
    private static final int WATER_TOP = 0xFF78AAE8;
    private static final int WIND = 0xFFF2F2EA;
    private static final int WIND_GUST = 0xFFF0B040;
    private static final int WIND_SHADOW = 0x90000000;

    private ShipHud() {
    }

    /** Client tick end. */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            ClientShipStatus.tick(false, false);
            return;
        }
        // Sable drops the client's tracking whenever the player does not touch the ship from above (jump, ladder)
        boolean holding = p.onClimbable() || (!p.onGround() && !p.isInWater() && !p.getAbilities().flying);
        ClientShipStatus.tick(ClientShipPoses.onShip(p), holding);
    }

    /** HUD layer. */
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null || !ShipHudConfig.ENABLED.get()) return;
        ShipStatusPayload s = ClientShipStatus.shown();
        if (s == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);

        ChatComponent chat = mc.gui.getChat();
        boolean chatOpen = chat.isChatFocused();
        ShipHudLayout.Placement placed = ShipHudLayout.place(ShipHudConfig.COMPASS_CORNER.get(),
                ShipHudConfig.HULL_CORNER.get(), screen(mc, g, chat, chatOpen), ShipHudConfig.SCALE.get());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // a panel that cannot get clear of the open chat on this screen waits until the chat closes
        if (placed.compass().clear() || !chatOpen) {
            begin(g, placed.compass().rect());
            compass(g, mc, ClientShipStatus.heading(partial), partial);
            text(g, mc.font, s);
            g.pose().popPose();
        }
        if (placed.hull().clear() || !chatOpen) {
            begin(g, placed.hull().rect());
            strip(g, s.cells());
            g.pose().popPose();
        }
        RenderSystem.disableBlend();
    }

    private static void begin(GuiGraphics g, ShipHudLayout.Rect r) {
        float f = ShipHudLayout.factor(r);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(r.x(), r.y(), 0);
        pose.scale(f, f, 1f);
    }

    /** What vanilla and the stamina bar draw where the panels might go (HUD2). */
    private static ShipHudLayout.Screen screen(Minecraft mc, GuiGraphics g, ChatComponent chat, boolean chatOpen) {
        int gw = g.guiWidth(), gh = g.guiHeight();
        LocalPlayer player = mc.player;
        List<ShipHudLayout.Rect> obstacles = new ArrayList<>(ShipHudLayout.effects(gw, effectsInset(player)));

        boolean hidden = mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN;
        double cs = chat.getScale();
        int chatW = hidden ? 0 : (int) Math.ceil(chat.getWidth() + 12 * cs);
        int chatH = hidden ? 0 : (int) Math.ceil(chat.getHeight() * cs);
        obstacles.addAll(ShipHudLayout.chat(gw, gh, chatW, chatH, chatOpen));

        boolean statusBars = mc.gameMode != null && mc.gameMode.canHurtPlayer();
        int right = rightRowsAbove(player);
        obstacles.addAll(ShipHudLayout.hotbar(gw, gh, statusBars, Math.max(leftRowsAbove(player), right)));
        if (MeleeClientConfig.HUD_ENABLED.get()) {
            // reserved whether or not a sword is in hand, so the panels do not jump when one is drawn
            StaminaHudLayout.Rect bar = StaminaHudLayout.place(MeleeClientConfig.HUD_POSITION.get(),
                    new StaminaHudLayout.Hud(gw, gh, statusBars, right), MeleeClientConfig.HUD_SCALE.get(),
                    MeleeClientConfig.HUD_X_OFFSET.get(), MeleeClientConfig.HUD_Y_OFFSET.get());
            obstacles.add(new ShipHudLayout.Rect(bar.x(), bar.y(), bar.w(), bar.h()));
        }
        return new ShipHudLayout.Screen(gw, gh, obstacles);
    }

    /** Rows above the hearts: armour and further heart rows (max health and absorption). */
    private static int leftRowsAbove(LocalPlayer player) {
        int hearts = Mth.ceil((player.getMaxHealth() + player.getAbsorptionAmount()) / 2f);
        int rows = Math.max(1, (hearts + 9) / 10);
        return rows - 1 + (player.getArmorValue() > 0 ? 1 : 0);
    }

    /** Rows above the food row: air bubbles, and mount hearts beyond the first row (the same as the stamina bar's). */
    private static int rightRowsAbove(LocalPlayer player) {
        int vehicleRows = 0;
        if (player.getVehicle() instanceof LivingEntity vehicle && vehicle.showVehicleHealth()) {
            int hearts = Math.min(30, (int) (vehicle.getMaxHealth() + 0.5f) / 2);
            vehicleRows = (hearts + 9) / 10;
        }
        boolean air = player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply();
        return Math.max(0, vehicleRows - 1) + (air ? 1 : 0);
    }

    /** Vanilla's status effect icons at the top right: one row for good effects, a second one below for bad ones. */
    private static int effectsInset(LocalPlayer player) {
        boolean good = false, bad = false;
        for (MobEffectInstance e : player.getActiveEffects()) {
            if (!e.showIcon()) continue;
            if (e.getEffect().value().isBeneficial()) good = true;
            else bad = true;
        }
        return bad ? 52 : good ? 26 : 0;
    }

    private static void blit(GuiGraphics g, ShipHudSheet.Part p, int x, int y) {
        g.blit(ShipHudSheet.TEXTURE, x, y, p.u(), p.v(), p.w(), p.h(), ShipHudSheet.WIDTH, ShipHudSheet.HEIGHT);
    }

    private static void compass(GuiGraphics g, Minecraft mc, double heading, float partial) {
        PoseStack pose = g.pose();
        int cx = ShipHudLayout.ROSE_CX, cy = ShipHudLayout.ROSE_CY;
        ShipHudSheet.Part rose = ShipHudSheet.ROSE;
        blit(g, rose, cx - rose.w() / 2, cy - rose.h() / 2);

        if (ClientWind.hasData()) {
            WindSample w = ClientWind.sample(mc.level.getGameTime() + partial);
            int len = ShipHudLayout.arrowLength(w.strength());
            if (len > 0) {
                pose.pushPose();
                pose.translate(cx, cy, 0);
                pose.mulPose(Axis.ZP.rotationDegrees((float) w.fromDegrees()));
                // drawn on the north side, pointing at the centre, then turned to the bearing the wind comes from
                int tip = -ShipHudLayout.ARROW_TIP_R, tail = tip - len;
                int colour = w.gusting() ? WIND_GUST : WIND;
                g.fill(0, tail + 1, 2, tip - 2, WIND_SHADOW);
                g.fill(-1, tail, 1, tip - 2, colour);
                g.fill(-3, tip - 3, 3, tip - 2, colour);
                g.fill(-2, tip - 2, 2, tip - 1, colour);
                g.fill(-1, tip - 1, 1, tip, colour);
                pose.popPose();
            }
        }

        ShipHudSheet.Part needle = ShipHudSheet.NEEDLE;
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) heading));
        pose.translate(-needle.w() / 2f, -needle.h() / 2f, 0);
        blit(g, needle, 0, 0);
        pose.popPose();
    }

    private static void text(GuiGraphics g, Font font, ShipStatusPayload s) {
        int w = ShipHudLayout.W;
        Component line = ShipHudText.line(ShipHudConfig.SPEED_UNIT.get(), s.speed(), s.rudder());
        int lw = font.width(line);
        g.fill(w / 2 - lw / 2 - 2, ShipHudLayout.SPEED_Y - 1, w / 2 - lw / 2 + lw + 2, ShipHudLayout.SPEED_Y + 9, BACKING);
        g.drawString(font, line, w / 2 - lw / 2, ShipHudLayout.SPEED_Y, TEXT, true);

        boolean unnamed = s.name().isEmpty();
        String name = unnamed ? Component.translatable(ShipHudText.KEY_UNNAMED).getString() : s.name();
        // CW1's load level after the name ("Black Pearl · Laden"); the name gives way when space is short
        String load = s.loadLevel() == null ? "" : " · " + Component.translatable(s.loadLevel().translationKey()).getString();
        int max = ShipHudLayout.W - 4 - font.width(load);
        if (font.width(name) > max) name = font.plainSubstrByWidth(name, Math.max(0, max - font.width("…"))) + "…";
        int nw = font.width(name), tw = nw + font.width(load);
        int x = w / 2 - tw / 2;
        g.drawString(font, name, x, ShipHudLayout.NAME_Y, unnamed ? TEXT_DIM : TEXT, true);
        if (!load.isEmpty()) g.drawString(font, load, x + nw, ShipHudLayout.NAME_Y, TEXT_DIM, true);
    }

    private static void strip(GuiGraphics g, List<ShipStatusPayload.Cell> cells) {
        int x0 = ShipHudLayout.STRIP_X, y0 = ShipHudLayout.STRIP_Y, h = ShipHudLayout.STRIP_H;
        blit(g, ShipHudSheet.BOW, x0 - ShipHudSheet.BOW.w(), y0);
        if (cells.isEmpty()) {
            g.fill(x0, y0, x0 + ShipHudLayout.STRIP_W, y0 + h, CELL_FRAME);
            g.fill(x0 + 1, y0 + 1, x0 + ShipHudLayout.STRIP_W - 1, y0 + h - 1, CELL_EMPTY);
            return;
        }
        int[] volumes = cells.stream().mapToInt(ShipStatusPayload.Cell::volume).toArray();
        ShipHudLayout.Span[] spans = ShipHudLayout.cells(volumes, ShipHudLayout.STRIP_W);
        int inner = h - 2;
        for (int i = 0; i < cells.size(); i++) {
            ShipStatusPayload.Cell c = cells.get(i);
            int x = x0 + spans[i].x(), cw = spans[i].w();
            g.fill(x, y0, x + cw, y0 + h, CELL_FRAME);
            g.fill(x + 1, y0 + 1, x + cw - 1, y0 + h - 1, CELL_EMPTY);
            int water = ShipHudLayout.waterPixels(c.fraction(), inner);
            if (water > 0 && cw > 2) {
                int top = y0 + 1 + inner - water;
                g.fill(x + 1, top, x + cw - 1, y0 + h - 1, WATER);
                g.fill(x + 1, top, x + cw - 1, top + 1, WATER_TOP);
            }
            if (c.pumping() && cw >= ShipHudSheet.PUMP.w()) {
                blit(g, ShipHudSheet.PUMP, x + (cw - ShipHudSheet.PUMP.w()) / 2, y0 + (h - ShipHudSheet.PUMP.h()) / 2);
            }
            if (c.breaches() > 0) {
                ShipHudSheet.Part b = ShipHudSheet.BREACH;
                blit(g, b, x + cw - b.w(), y0 - 2);
            }
        }
    }
}
