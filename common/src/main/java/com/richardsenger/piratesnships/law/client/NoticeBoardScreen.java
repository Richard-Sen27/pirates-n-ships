package com.richardsenger.piratesnships.law.client;

import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;
import com.richardsenger.piratesnships.law.net.NoticeBoardPayloads;
import com.richardsenger.piratesnships.law.net.NoticeBoardText;
import com.richardsenger.piratesnships.law.net.NoticeBoardView;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The notice board screen (client only, docs/design.md §13.2). Opened by {@link NoticeBoardPayloads.Open}; shows
 * {@link ClientNoticeBoard}'s view: the bounty on the viewer's own head, every active bounty (target, amount, who placed
 * it, how long ago) and a form to place a bounty (a target name, with the online players and the names on the board
 * offered below the field, and an amount). The form only sends a request; the server decides and answers with a new
 * view, whose result shows on the status line. Closes on Escape, beyond the board's reach, or when the server ends
 * the session.
 */
public final class NoticeBoardScreen extends Screen {

    private record Chip(int x, int y, int w, String name) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + 11;
        }
    }

    private static final int ROW_H = 11;
    private static final int PANEL = 0xF0241A10;
    private static final int BORDER = 0xFF8B6B3A;
    private static final int ROW_ALT = 0x14FFFFFF;
    private static final int GOLD = 0xFFE8C060;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFA0A0A0;
    private static final int GOOD = 0xFF8FE08F;
    private static final int BAD = 0xFFFF8070;

    private EditBox targetField;
    private EditBox amountField;
    private Button placeButton;
    private final List<Chip> chips = new ArrayList<>();
    private Component status = Component.empty();
    private int statusColor = TEXT;
    private long seenVersion = -1;
    private int scroll;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;
    private int formY;

    NoticeBoardScreen() {
        super(Component.translatable(NoticeBoardText.TITLE));
    }

    /** Opens the screen for the board in {@link ClientNoticeBoard} (installed as its opener). */
    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof NoticeBoardScreen) return;
        mc.setScreen(new NoticeBoardScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, width - 16);
        panelH = Math.min(240, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        listTop = top + 44;
        formY = top + panelH - 54;
        listBottom = formY - 6;
        String oldTarget = targetField == null ? "" : targetField.getValue();
        String oldAmount = amountField == null ? "" : amountField.getValue();
        int x = left + 6;
        int fieldY = formY + 12;
        targetField = new EditBox(font, x, fieldY, 120, 14, Component.translatable(NoticeBoardText.FORM_TARGET));
        targetField.setMaxLength(32);
        targetField.setHint(Component.translatable(NoticeBoardText.FORM_TARGET));
        targetField.setValue(oldTarget);
        addRenderableWidget(targetField);
        amountField = new EditBox(font, x + 126, fieldY, 56, 14, Component.translatable(NoticeBoardText.FORM_AMOUNT));
        amountField.setMaxLength(7);
        amountField.setFilter(s -> s.chars().allMatch(Character::isDigit));
        amountField.setHint(Component.translatable(NoticeBoardText.FORM_AMOUNT));
        amountField.setValue(oldAmount);
        addRenderableWidget(amountField);
        placeButton = addRenderableWidget(Button.builder(Component.translatable(NoticeBoardText.FORM_PLACE), b -> place())
                .bounds(x + 188, fieldY - 1, 70, 16).build());
        updateButton();
    }

    // ------------------------------------------------------------------ state

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        Optional<NoticeBoardPayloads.Open> board = ClientNoticeBoard.board();
        if (mc.player == null || board.isEmpty()) {
            if (mc.player != null && ClientNoticeBoard.lastResult().isPresent()) {
                mc.player.displayClientMessage(Component.translatable(NoticeBoardText.CLOSED), true);
            }
            onClose();
            return;
        }
        double reach = board.get().reach();
        if (mc.player.position().distanceToSqr(Vec3.atCenterOf(board.get().board())) > reach * reach) {
            onClose();
            return;
        }
        if (ClientNoticeBoard.version() != seenVersion) {
            seenVersion = ClientNoticeBoard.version();
            onState(mc);
        }
        updateButton();
    }

    private void onState(Minecraft mc) {
        Optional<NoticeBoardView> view = ClientNoticeBoard.view();
        if (view.isPresent() && amountField != null && amountField.getValue().isEmpty()) {
            amountField.setValue(Integer.toString(view.get().minimum()));
        }
        Optional<NoticeBoardPayloads.Result> result = ClientNoticeBoard.lastResult();
        if (result.isPresent()) {
            NoticeBoardPayloads.Result r = result.get();
            status = Component.translatable(r.key(), r.args().toArray());
            statusColor = r.ok() ? GOOD : BAD;
            if (r.ok()) {
                targetField.setValue("");
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
            }
            ClientNoticeBoard.clearResult();
        }
    }

    private void updateButton() {
        if (placeButton == null) return;
        Optional<NoticeBoardView> view = ClientNoticeBoard.view();
        placeButton.active = view.isPresent() && view.get().placing() && !targetField.getValue().isBlank()
                && parseAmount().orElse(0) > 0;
    }

    private Optional<Integer> parseAmount() {
        try {
            return amountField.getValue().isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(amountField.getValue()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void place() {
        Optional<NoticeBoardPayloads.Open> board = ClientNoticeBoard.board();
        Optional<Integer> amount = parseAmount();
        if (board.isEmpty() || amount.isEmpty() || targetField.getValue().isBlank()) return;
        Services.NETWORK.sendToServer(new NoticeBoardPayloads.Place(board.get().board(), targetField.getValue().trim(), amount.get()));
    }

    @Override
    public void removed() {
        super.removed();
        if (ClientNoticeBoard.board().isPresent() && Minecraft.getInstance().getConnection() != null) {
            Services.NETWORK.sendToServer(NoticeBoardPayloads.Close.INSTANCE);
        }
        ClientNoticeBoard.reset();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Chip c : chips) {
            if (c.contains(mouseX, mouseY)) {
                targetField.setValue(c.name());
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = ClientNoticeBoard.view().map(v -> v.lines().size()).orElse(0);
        int max = Math.max(0, rows - visibleRows());
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop - ROW_H) / ROW_H);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + panelW, top + panelH, PANEL);
        g.renderOutline(left, top, panelW, panelH, BORDER);
        g.fill(left + 4, formY - 3, left + panelW - 4, formY - 2, BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        chips.clear();
        g.drawString(font, title, left + 6, top + 6, GOLD);
        Optional<NoticeBoardView> view = ClientNoticeBoard.view();
        if (view.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(NoticeBoardText.LOADING), left + panelW / 2, listTop + 20, DIM);
            return;
        }
        NoticeBoardView v = view.get();
        Component coins = Component.translatable(NoticeBoardText.COINS, v.coins());
        g.drawString(font, coins, left + panelW - 6 - font.width(coins), top + 6, GOLD);
        Component own = v.ownTotal() > 0
                ? Component.translatable(NoticeBoardText.OWN_BOUNTY, v.ownTotal())
                : Component.translatable(NoticeBoardText.OWN_NONE);
        drawClipped(g, own, left + 6, top + 20, panelW - 12, v.ownTotal() > 0 ? BAD : DIM);
        renderList(g, v);
        renderForm(g, v, mouseX, mouseY);
        if (!status.getString().isEmpty()) drawClipped(g, status, left + 6, top + panelH - 12, panelW - 12, statusColor);
    }

    private void renderList(GuiGraphics g, NoticeBoardView v) {
        int xTarget = left + 6;
        int xAmount = left + 6 + (panelW - 12) * 40 / 100;
        int xBy = left + 6 + (panelW - 12) * 58 / 100;
        int xSince = left + 6 + (panelW - 12) * 82 / 100;
        int y = listTop;
        g.drawString(font, Component.translatable(NoticeBoardText.COL_TARGET), xTarget, y, DIM);
        g.drawString(font, Component.translatable(NoticeBoardText.COL_AMOUNT), xAmount, y, DIM);
        g.drawString(font, Component.translatable(NoticeBoardText.COL_PLACED_BY), xBy, y, DIM);
        g.drawString(font, Component.translatable(NoticeBoardText.COL_SINCE), xSince, y, DIM);
        y += ROW_H;
        if (v.lines().isEmpty()) {
            g.drawCenteredString(font, Component.translatable(NoticeBoardText.EMPTY), left + panelW / 2, y + 12, DIM);
            return;
        }
        int rows = visibleRows();
        scroll = Math.clamp(scroll, 0, Math.max(0, v.lines().size() - rows));
        for (int i = scroll; i < Math.min(v.lines().size(), scroll + rows); i++) {
            NoticeBoardListing.Line line = v.lines().get(i);
            boolean firstOfTarget = i == 0 || !v.lines().get(i - 1).target().equals(line.target());
            if ((i & 1) == 1) g.fill(left + 4, y - 1, left + panelW - 4, y + ROW_H - 2, ROW_ALT);
            if (firstOfTarget || i == scroll) {
                Component name = Component.literal(line.targetName());
                if (line.targetTotal() != line.amount()) {
                    name = name.copy().append(Component.translatable(NoticeBoardText.TOTAL, line.targetTotal()).withColor(DIM));
                }
                drawClipped(g, name, xTarget, y, xAmount - xTarget - 4, line.targetIsPlayer() ? TEXT : 0xFFD0C8B0);
            }
            g.drawString(font, Integer.toString(line.amount()), xAmount, y, GOLD);
            Component by = line.navy() ? Component.translatable(NoticeBoardText.NAVY) : Component.literal(line.placedBy());
            drawClipped(g, by, xBy, y, xSince - xBy - 4, line.navy() ? 0xFF8FB4FF : TEXT);
            NoticeBoardListing.Age age = NoticeBoardListing.age(v.now() - line.createdAt());
            drawClipped(g, Component.translatable(NoticeBoardText.age(age.unit()), age.value()), xSince, y, left + panelW - 6 - xSince, DIM);
            y += ROW_H;
        }
    }

    private void renderForm(GuiGraphics g, NoticeBoardView v, int mouseX, int mouseY) {
        Component label = v.placing()
                ? Component.translatable(NoticeBoardText.FORM).append(" ").append(Component.translatable(NoticeBoardText.FORM_MINIMUM, v.minimum()).withColor(DIM))
                : Component.translatable(NoticeBoardText.FORM_DISABLED);
        drawClipped(g, label, left + 6, formY, panelW - 12, v.placing() ? GOLD : DIM);
        // Name suggestions: those starting with what was typed (all when empty)
        String typed = targetField.getValue().trim().toLowerCase(Locale.ROOT);
        int x = left + 6;
        int y = formY + 30;
        for (String name : v.names()) {
            if (!typed.isEmpty() && (!name.toLowerCase(Locale.ROOT).startsWith(typed) || name.equalsIgnoreCase(typed))) continue;
            int w = font.width(name) + 6;
            if (x + w > left + panelW - 6) break;
            Chip chip = new Chip(x, y, w, name);
            chips.add(chip);
            boolean hover = chip.contains(mouseX, mouseY);
            g.fill(x, y, x + w, y + 11, hover ? 0x40FFFFFF : 0x20FFFFFF);
            g.drawString(font, name, x + 3, y + 2, hover ? GOLD : TEXT);
            x += w + 3;
        }
    }

    private void drawClipped(GuiGraphics g, Component text, int x, int y, int maxW, int color) {
        FormattedText cut = font.width(text) <= maxW ? text : font.substrByWidth(text, Math.max(0, maxW - font.width("..")));
        g.drawString(font, Language.getInstance().getVisualOrder(cut), x, y, color);
        if (cut != text) g.drawString(font, "..", x + font.width(cut), y, color);
    }
}
