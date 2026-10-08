package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.client.gui.BrassButton;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.core.client.gui.GuiSprites;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.career.CareerConfig;
import com.richardsenger.piratesnships.rpg.career.CareerPayloads;
import com.richardsenger.piratesnships.rpg.career.CareerRules;
import com.richardsenger.piratesnships.rpg.career.CareerText;
import com.richardsenger.piratesnships.rpg.career.ClientCareer;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * The navy officer's career screen (client only, docs/design.md §15, CAR1). Opened by a {@link CareerPayloads.View}
 * with {@code open}; shows the player's reputation, the navy rank and what the next rank (or enlisting) needs, the
 * infamy and what the next infamy rank needs, the letter of marque and the prize money waiting, with the buttons
 * Enlist or Resign, Letter (fee) and Collect prize. Buttons only send a request; the server answers with a new view
 * whose result shows on the status line. Disabled buttons explain why in a tooltip. Closes on Escape, the inventory
 * key, or when the officer is gone or out of reach.
 */
public final class CareerScreen extends Screen {

    private static final int FOOTER_H = 16;
    private static final int LINE_H = 10;

    private long seenVersion = -1;
    private Component status = Component.empty();
    private int statusColor = GuiKit.INK;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int bodyTop;
    private int bodyBottom;
    private int buttonsY;
    private int footerTop;

    CareerScreen() {
        super(Component.translatable(CareerText.TITLE));
    }

    /** Opens the screen for the view in {@link ClientCareer} (installed as its opener). */
    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof CareerScreen) return;
        mc.setScreen(new CareerScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int inner() {
        return left + GuiKit.FRAME_BORDER;
    }

    private int innerW() {
        return panelW - 2 * GuiKit.FRAME_BORDER;
    }

    @Override
    protected void init() {
        panelW = Math.min(300, width - 16);
        panelH = Math.min(236, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        bodyTop = top + GuiKit.FRAME_BORDER + GuiKit.HEADER_H + 4;
        footerTop = top + panelH - GuiKit.FRAME_BORDER - FOOTER_H;
        buttonsY = footerTop - 18;
        bodyBottom = buttonsY - 4;
        Optional<CareerPayloads.View> view = ClientCareer.view();
        if (view.isEmpty()) return;
        CareerPayloads.View v = view.get();
        int gap = 4;
        int bw = (innerW() - 4 - 2 * gap) / 3;
        int x = inner() + 2;

        boolean enlisted = v.status().enlisted();
        BrassButton service = enlisted
                ? new BrassButton(x, buttonsY, bw, 14, Component.translatable(CareerText.RESIGN), () -> send(v, CareerPayloads.Kind.RESIGN))
                : new BrassButton(x, buttonsY, bw, 14, Component.translatable(CareerText.ENLIST), () -> send(v, CareerPayloads.Kind.ENLIST));
        if (!enlisted && v.enlist() != CareerRules.EnlistVerdict.OK) {
            service.active = false;
            service.setTooltip(Tooltip.create(Component.translatable(CareerText.enlistVerdict(v.enlist()),
                    v.navyLadder().requirements().isEmpty() ? "" : v.navyLadder().requirements().getFirst().need())));
        }
        addRenderableWidget(service);

        BrassButton letter = new BrassButton(x + bw + gap, buttonsY, bw, 14,
                Component.translatable(CareerText.REQUEST_LETTER, v.letterFee()), () -> send(v, CareerPayloads.Kind.REQUEST_LETTER));
        if (v.letterVerdict() != CareerRules.LetterVerdict.OK) {
            letter.active = false;
            Object arg = switch (v.letterVerdict()) {
                case BLOCKED -> v.status().letterBlockedDays();
                case LOW_STANDING -> CareerConfig.LETTER_MIN_NAVY_REP.get();
                case TOO_POOR -> v.letterFee();
                default -> "";
            };
            letter.setTooltip(Tooltip.create(Component.translatable(CareerText.letterVerdict(v.letterVerdict()), arg)));
        }
        addRenderableWidget(letter);

        BrassButton prize = new BrassButton(x + 2 * (bw + gap), buttonsY, bw, 14, Component.translatable(CareerText.COLLECT_PRIZE),
                () -> send(v, CareerPayloads.Kind.COLLECT_PRIZE));
        if (v.status().prize() <= 0) {
            prize.active = false;
            prize.setTooltip(Tooltip.create(Component.translatable(CareerText.PRIZE_NONE)));
        }
        addRenderableWidget(prize);
    }

    private void send(CareerPayloads.View v, CareerPayloads.Kind kind) {
        Services.NETWORK.sendToServer(new CareerPayloads.Action(v.officer(), kind));
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        Optional<CareerPayloads.View> view = ClientCareer.view();
        if (mc.player == null || mc.level == null || view.isEmpty()) {
            onClose();
            return;
        }
        Entity officer = mc.level.getEntity(view.get().officer());
        double reach = CareerConfig.OFFICER_REACH.get() + 1.0;
        if (officer == null || !officer.isAlive() || officer.distanceToSqr(mc.player) > reach * reach) {
            onClose();
            return;
        }
        if (ClientCareer.version() != seenVersion) {
            seenVersion = ClientCareer.version();
            view.get().result().ifPresent(r -> {
                Object[] args = r.args().stream()
                        .map(a -> a.startsWith(Constants.MOD_ID + ".") ? (Object) Component.translatable(a) : a).toArray();
                status = Component.translatable(r.key(), args);
                statusColor = r.done() ? GuiKit.INK_GREEN : GuiKit.INK_RED;
            });
            rebuildWidgets();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiKit.frame(g, left, top, panelW, panelH);
        GuiKit.sprite(g, GuiSprites.HEADER, inner(), top + GuiKit.FRAME_BORDER, innerW(), GuiKit.HEADER_H);
        GuiKit.parchment(g, inner() + 1, bodyTop, innerW() - 2, bodyBottom - bodyTop);
        GuiKit.parchment(g, inner() + 1, footerTop, innerW() - 2, FOOTER_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hy = top + GuiKit.FRAME_BORDER + 4;
        g.drawString(font, title, inner() + (innerW() - font.width(title)) / 2, hy, GuiKit.BRASS_TEXT, true);
        Optional<CareerPayloads.View> view = ClientCareer.view();
        int x = inner() + 6;
        int w = innerW() - 12;
        if (view.isPresent()) drawBody(g, view.get(), x, bodyTop + 5, w);
        GuiKit.ink(g, font, status, x, footerTop + 4, w, statusColor);
    }

    private void drawBody(GuiGraphics g, CareerPayloads.View v, int x, int y, int w) {
        if (!v.enabled()) {
            GuiKit.ink(g, font, Component.translatable(CareerText.DISABLED), x, y, w, GuiKit.INK_RED);
            return;
        }
        CareerPayloads.Status s = v.status();
        GuiKit.ink(g, font, Component.translatable(CareerText.REPUTATION, v.navyRep(), v.pirateRep()), x, y, w, GuiKit.INK_DIM);
        y += LINE_H + 4;

        Component navy = s.enlisted() ? Component.translatable(CareerText.NAVY_RANK, Component.translatable(s.navy().nameKey()))
                : Component.translatable(CareerText.NAVY_NONE);
        GuiKit.ink(g, font, navy, x, y, w, GuiKit.INK_NAVY);
        y += LINE_H;
        y = drawLadder(g, v.navyLadder(), s.enlisted(), x + 8, y, w - 8, null);
        y += 4;

        GuiKit.ink(g, font, Component.translatable(CareerText.INFAMY_RANK, Component.translatable(s.infamy().nameKey())), x, y, w, GuiKit.INK_RED);
        y += LINE_H;
        y = drawLadder(g, v.infamyLadder(), true, x + 8, y, w - 8,
                s.enlisted() ? Component.translatable(CareerText.NO_INFAMY_IN_SERVICE) : null);
        y += 4;

        GuiKit.divider(g, x - 2, y, w + 4);
        y += 6;
        Component letter = s.letter() == LetterState.VOIDED && s.letterBlockedDays() > 0
                ? Component.translatable(CareerText.LETTER_BLOCKED, s.letterBlockedDays())
                : Component.translatable(CareerText.LETTER, Component.translatable(s.letter().nameKey()));
        GuiKit.ink(g, font, letter, x, y, w, s.letter() == LetterState.ACTIVE ? GuiKit.INK_GREEN : GuiKit.INK);
        y += LINE_H;
        GuiKit.ink(g, font, Component.translatable(CareerText.PRIZE, s.prize()), x, y, w, s.prize() > 0 ? GuiKit.INK_AMBER : GuiKit.INK_DIM);
    }

    /**
     * The next rank and its requirements (met in green, missing in red). {@code closedNote} replaces it all when the
     * ladder is closed; an empty ladder with {@code atTop} reads "the highest rank", otherwise nothing.
     */
    private int drawLadder(GuiGraphics g, CareerPayloads.Ladder ladder, boolean atTop, int x, int y, int w, Component closedNote) {
        if (closedNote != null) {
            GuiKit.ink(g, font, closedNote, x, y, w, GuiKit.INK_DIM);
            return y + LINE_H;
        }
        if (ladder.next().isEmpty()) {
            if (!atTop) return y;
            GuiKit.ink(g, font, Component.translatable(CareerText.TOP), x, y, w, GuiKit.INK_DIM);
            return y + LINE_H;
        }
        GuiKit.ink(g, font, Component.translatable(CareerText.NEXT, Component.translatable(ladder.next().get())), x, y, w, GuiKit.INK);
        y += LINE_H;
        for (CareerPayloads.Req r : ladder.requirements()) {
            if (r.need() <= 0 && !r.key().endsWith("_rep")) continue;
            Component line = Component.literal(r.met() ? "+ " : "- ")
                    .append(Component.translatable(CareerText.requirement(r.key()), r.have(), r.need()));
            GuiKit.ink(g, font, line, x + 6, y, w - 6, r.met() ? GuiKit.INK_GREEN : GuiKit.INK_RED);
            y += LINE_H;
        }
        return y;
    }
}
