package com.richardsenger.piratesnships.station.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.order.RadialLayout;
import com.richardsenger.piratesnships.station.order.WhistleMenu;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrderPayload;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * The captain's whistle radial menu (client only, docs/design.md §7.2). The entries of {@link WhistleOrder} sit on a
 * ring around the screen center, entry 0 at the top; the sector under the mouse lights up, its name shows in the
 * center and its description under the ring. Confirm with a left click, a click or a press of the use key, or by
 * releasing the use key over a sector (hold, aim, release); a click in the center or Escape closes without an order.
 * The menu only sends a {@link WhistleOrderPayload}; the server decides. Sizes follow the window
 * ({@link RadialLayout#forWindow}), and the game keeps running underneath.
 */
public final class WhistleMenuScreen extends Screen {

    private static final int SECTOR = 0xA0181C20;
    private static final int SECTOR_HOVER = 0xC0A07A30;
    private static final int RIM = 0x80000000;
    private static final int RIM_HOVER = 0xFFE8C060;
    private static final int LAST_MARK = 0xC0E8E8E8;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFC8C8C8;
    private static final double SEGMENT_STEP = Math.toRadians(3);
    private static final double GAP_PX = 1.0;

    private final List<WhistleOrder> entries = WhistleOrder.entries();
    private final ItemStack[] icons;
    private RadialLayout layout;
    private int hovered = -1;

    WhistleMenuScreen() {
        super(Component.translatable(WhistleMenu.KEY_TITLE));
        icons = new ItemStack[entries.size()];
        for (int i = 0; i < icons.length; i++) {
            icons[i] = new ItemStack(BuiltInRegistries.ITEM.get(entries.get(i).icon()));
        }
        layout = RadialLayout.forWindow(entries.size(), 320, 240, 1.0);
    }

    /** Opens the menu unless another screen is open. Installed as {@link WhistleMenu}'s opener. */
    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) {
            mc.setScreen(new WhistleMenuScreen());
        }
    }

    @Override
    protected void init() {
        layout = RadialLayout.forWindow(entries.size(), width, height, 1.0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // dropped or switched away from the whistle: nothing left to order with
        if (minecraft != null && (minecraft.player == null || WhistleOrders.heldWhistle(minecraft.player) == null)) {
            onClose();
        }
    }

    private int sectorAt(double mouseX, double mouseY) {
        return layout.sectorAt(mouseX - width / 2.0, mouseY - height / 2.0);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || minecraft.options.keyUse.matchesMouse(button)) {
            int i = sectorAt(mouseX, mouseY);
            if (i < 0) onClose(); else confirm(i);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // hold-aim-release; a release in the center (the tap that opened the menu) keeps it open
        if (minecraft.options.keyUse.matchesMouse(button)) {
            int i = sectorAt(mouseX, mouseY);
            if (i >= 0) confirm(i);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyUse.matches(keyCode, scanCode)) {
            if (hovered >= 0) confirm(hovered);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyUse.matches(keyCode, scanCode)) {
            if (hovered >= 0) confirm(hovered);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void confirm(int i) {
        Services.NETWORK.sendToServer(new WhistleOrderPayload(entries.get(i)));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f));
        onClose();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // a light dim instead of the menu blur: the sea stays visible
        g.fillGradient(0, 0, width, height, 0x30000000, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hovered = sectorAt(mouseX, mouseY);
        float cx = width / 2f;
        float cy = height / 2f;
        double inner = layout.inner();
        double outer = layout.outer();
        int last = lastOrder();

        Matrix4f pose = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < entries.size(); i++) {
            double a0 = layout.startAngle(i);
            double a1 = a0 + layout.span();
            boolean hot = i == hovered;
            arc(vc, pose, cx, cy, inner, outer, a0, a1, hot ? SECTOR_HOVER : SECTOR);
            arc(vc, pose, cx, cy, outer, outer + (hot ? 3 : 1), a0, a1, hot ? RIM_HOVER : RIM);
            if (i == last) {
                arc(vc, pose, cx, cy, inner, inner + 2, a0, a1, LAST_MARK);
            }
        }
        g.flush(); // the ring below the icons and the text

        double thickness = outer - inner;
        float iconScale = (float) Mth.clamp(thickness * 0.5 / 16.0, 0.75, 2.0);
        for (int i = 0; i < entries.size(); i++) {
            double a = layout.centerAngle(i);
            float s = i == hovered ? iconScale * 1.2f : iconScale;
            g.pose().pushPose();
            g.pose().translate(cx + RadialLayout.x(a, layout.iconRadius()), cy + RadialLayout.y(a, layout.iconRadius()), 0);
            g.pose().scale(s, s, 1);
            g.renderItem(icons[i], -8, -8);
            g.pose().popPose();
        }

        // center: hovered order name (or the title), wrapped to the hole
        Component center = hovered >= 0 ? Component.translatable(entries.get(hovered).nameKey()) : title;
        List<FormattedCharSequence> lines = font.split(center, Math.max(24, (int) (inner * 1.7)));
        int y = (int) cy - lines.size() * font.lineHeight / 2;
        for (FormattedCharSequence line : lines) {
            g.drawCenteredString(font, line, (int) cx, y, hovered >= 0 ? TEXT : TEXT_DIM);
            y += font.lineHeight;
        }

        // under the ring: the description of the hovered order, or how to use the menu
        Component below = hovered >= 0 ? Component.translatable(entries.get(hovered).descriptionKey()) : Component.translatable(WhistleMenu.KEY_HINT);
        int wrap = Math.min(width - 16, Math.max(160, (int) (outer * 2.2)));
        y = (int) (cy + outer + 8);
        for (FormattedCharSequence line : font.split(below, wrap)) {
            if (y + font.lineHeight > height) break;
            g.drawCenteredString(font, line, (int) cx, y, TEXT_DIM);
            y += font.lineHeight;
        }
        if (last >= 0 && hovered < 0 && y + font.lineHeight <= height) {
            g.drawCenteredString(font, Component.translatable(WhistleMenu.KEY_LAST, Component.translatable(entries.get(last).nameKey())),
                    (int) cx, y + 2, TEXT_DIM);
        }
    }

    /** Index of the entry of the last sail order given with the held whistle, or -1. */
    private int lastOrder() {
        if (minecraft == null || minecraft.player == null) return -1;
        ItemStack whistle = WhistleOrders.heldWhistle(minecraft.player);
        SailOrder sail = whistle == null ? null : whistle.get(StationContent.WHISTLE_ORDER.get());
        if (sail == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).sail() == sail) return i;
        }
        return -1;
    }

    /**
     * A filled ring segment from {@code r0} to {@code r1} between the angles, as thin quads, with a constant
     * {@link #GAP_PX} gap to the neighbours. Vertices go counter-clockwise on screen, like {@code GuiGraphics#fill}
     * (the gui render type culls back faces).
     */
    private static void arc(VertexConsumer vc, Matrix4f pose, float cx, float cy, double r0, double r1, double a0, double a1, int color) {
        double g0 = GAP_PX / Math.max(r0, 1);
        double g1 = GAP_PX / Math.max(r1, 1);
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / SEGMENT_STEP));
        for (int k = 0; k < steps; k++) {
            double t0 = (double) k / steps;
            double t1 = (double) (k + 1) / steps;
            double in0 = Mth.lerp(t0, a0 + g0, a1 - g0);
            double in1 = Mth.lerp(t1, a0 + g0, a1 - g0);
            double out0 = Mth.lerp(t0, a0 + g1, a1 - g1);
            double out1 = Mth.lerp(t1, a0 + g1, a1 - g1);
            vertex(vc, pose, cx, cy, out0, r1, color);
            vertex(vc, pose, cx, cy, in0, r0, color);
            vertex(vc, pose, cx, cy, in1, r0, color);
            vertex(vc, pose, cx, cy, out1, r1, color);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float cx, float cy, double angle, double r, int color) {
        vc.addVertex(pose, cx + (float) RadialLayout.x(angle, r), cy + (float) RadialLayout.y(angle, r), 0).setColor(color);
    }
}
