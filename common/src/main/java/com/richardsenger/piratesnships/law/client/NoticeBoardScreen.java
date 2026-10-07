package com.richardsenger.piratesnships.law.client;

import com.richardsenger.piratesnships.core.client.gui.BrassButton;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.core.client.gui.GuiSprites;
import com.richardsenger.piratesnships.core.client.gui.ScrollBar;
import com.richardsenger.piratesnships.core.client.gui.ScrollMath;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;
import com.richardsenger.piratesnships.law.net.NoticeBoardPayloads;
import com.richardsenger.piratesnships.law.net.NoticeBoardText;
import com.richardsenger.piratesnships.law.net.NoticeBoardView;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The notice board screen (client only, docs/design.md §13.2). Opened by {@link NoticeBoardPayloads.Open}; shows
 * {@link ClientNoticeBoard}'s view: the bounty on the viewer's own head, every active bounty (target, amount, who placed
 * it, how long ago) and a form to place a bounty (a target name, with the online players and the names on the board
 * offered below the field, and an amount). The form only sends a request; the server decides and answers with a new
 * view, whose result shows on the status line. Closes on Escape, beyond the board's reach, or when the server ends
 * the session.
 *
 * <p>Look (U1, GUI kit in {@code core/client/gui}): a wooden board frame with a header plaque (title, the viewer's
 * doubloons), the bounties as parchment notice cards pinned to the board in a scrollable list (a red wax seal for a
 * player's notice, a navy anchor for the navy's; the viewer's own bounty on a red card), and the place form on a
 * parchment strip with styled fields, a brass button and the names as tags.
 */
public final class NoticeBoardScreen extends Screen {

    private record Chip(int x, int y, int w, String name) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + GuiKit.TAG_H;
        }
    }

    private static final int CARD_H = 24;
    private static final int CARD_PITCH = CARD_H + 3;
    private static final int FORM_H = 60;

    private EditBox targetField;
    private EditBox amountField;
    private BrassButton placeButton;
    private final List<Chip> chips = new ArrayList<>();
    private final ScrollBar scrollBar = new ScrollBar();
    private Component status = Component.empty();
    private int statusColor = GuiKit.INK;
    private long seenVersion = -1;
    private int scroll;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;
    private int cardLeft;
    private int cardRight;
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
        listTop = top + 41;
        formY = top + panelH - GuiKit.FRAME_BORDER - FORM_H;
        listBottom = formY - 4;
        int barX = left + panelW - GuiKit.FRAME_BORDER - 3 - GuiKit.SCROLLBAR_W;
        scrollBar.place(barX, listTop, listBottom - listTop);
        cardLeft = left + GuiKit.FRAME_BORDER + 3;
        cardRight = barX - 4;

        String oldTarget = targetField == null ? "" : targetField.getValue();
        String oldAmount = amountField == null ? "" : amountField.getValue();
        int x = left + GuiKit.FRAME_BORDER + 6;
        int fieldY = formY + 17;
        targetField = new EditBox(font, GuiKit.fieldTextX(x), GuiKit.fieldTextY(fieldY), GuiKit.fieldTextW(120), 10,
                Component.translatable(NoticeBoardText.FORM_TARGET));
        targetField.setMaxLength(32);
        GuiKit.styleField(targetField, Component.translatable(NoticeBoardText.FORM_TARGET));
        targetField.setValue(oldTarget);
        addRenderableWidget(targetField);
        amountField = new EditBox(font, GuiKit.fieldTextX(x + 124), GuiKit.fieldTextY(fieldY), GuiKit.fieldTextW(58), 10,
                Component.translatable(NoticeBoardText.FORM_AMOUNT));
        amountField.setMaxLength(7);
        amountField.setFilter(s -> s.chars().allMatch(Character::isDigit));
        GuiKit.styleField(amountField, Component.translatable(NoticeBoardText.FORM_AMOUNT));
        amountField.setValue(oldAmount);
        addRenderableWidget(amountField);
        int buttonX = x + 186;
        int buttonW = Math.min(76, left + panelW - GuiKit.FRAME_BORDER - 6 - buttonX);
        placeButton = addRenderableWidget(new BrassButton(buttonX, fieldY, buttonW, GuiKit.FIELD_H,
                Component.translatable(NoticeBoardText.FORM_PLACE), this::place));
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
            statusColor = r.ok() ? GuiKit.INK_GREEN : GuiKit.INK_RED;
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

    private int rows() {
        return ClientNoticeBoard.view().map(v -> v.lines().size()).orElse(0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int s = scrollBar.click(mouseX, mouseY, rows(), visibleRows(), scroll);
            if (s >= 0) {
                scroll = s;
                return true;
            }
        }
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int s = scrollBar.drag(mouseY, rows(), visibleRows());
        if (s >= 0) {
            scroll = s;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = ScrollMath.clamp(scroll - (int) Math.signum(scrollY), rows(), visibleRows());
        return true;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop + 3) / CARD_PITCH);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiKit.frame(g, left, top, panelW, panelH);
        int inner = left + GuiKit.FRAME_BORDER;
        GuiKit.header(g, font, title, inner, top + GuiKit.FRAME_BORDER, panelW - 2 * GuiKit.FRAME_BORDER);
        GuiKit.parchment(g, inner + 1, formY, panelW - 2 * GuiKit.FRAME_BORDER - 2, FORM_H);
        GuiKit.field(g, targetField);
        GuiKit.field(g, amountField);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        chips.clear();
        int inner = left + GuiKit.FRAME_BORDER;
        int innerW = panelW - 2 * GuiKit.FRAME_BORDER;
        Optional<NoticeBoardView> view = ClientNoticeBoard.view();
        if (view.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(NoticeBoardText.LOADING), left + panelW / 2, listTop + 20, GuiKit.ON_WOOD_DIM);
            return;
        }
        NoticeBoardView v = view.get();
        // doubloons on the header plaque's right end
        GuiKit.coins(g, font, Component.literal(Long.toString(v.coins())), inner + innerW - 6, top + GuiKit.FRAME_BORDER + 4,
                GuiKit.BRASS_TEXT, true);
        boolean wanted = v.ownTotal() > 0;
        Component own = wanted
                ? Component.translatable(NoticeBoardText.OWN_BOUNTY, v.ownTotal())
                : Component.translatable(NoticeBoardText.OWN_NONE);
        int ownX = inner + 4;
        if (wanted) {
            GuiKit.icon(g, GuiSprites.WAX_SEAL, ownX, top + 28);
            ownX += GuiSprites.ICON + 3;
        }
        GuiKit.text(g, font, own, ownX, top + 29, inner + innerW - 4 - ownX, wanted ? GuiKit.ON_WOOD_RED : GuiKit.ON_WOOD_DIM, true);
        renderList(g, v, mouseX, mouseY);
        renderForm(g, v, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, NoticeBoardView v, int mouseX, int mouseY) {
        int rows = visibleRows();
        scroll = ScrollMath.clamp(scroll, v.lines().size(), rows);
        scrollBar.render(g, v.lines().size(), rows, scroll, mouseX, mouseY);
        if (v.lines().isEmpty()) {
            g.drawCenteredString(font, Component.translatable(NoticeBoardText.EMPTY), (cardLeft + cardRight) / 2, listTop + 20, GuiKit.ON_WOOD_DIM);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        UUID me = mc.player == null ? null : mc.player.getUUID();
        int w = cardRight - cardLeft;
        int y = listTop;
        for (int i = scroll; i < Math.min(v.lines().size(), scroll + rows); i++, y += CARD_PITCH) {
            NoticeBoardListing.Line line = v.lines().get(i);
            boolean mine = line.target().equals(me);
            boolean hover = mouseX >= cardLeft && mouseX < cardRight && mouseY >= y && mouseY < y + CARD_H;
            GuiKit.sprite(g, mine ? GuiSprites.CARD_OWN : hover ? GuiSprites.CARD_HOVER : GuiSprites.CARD, cardLeft, y, w, CARD_H);
            GuiKit.sprite(g, GuiSprites.PIN, cardLeft + w / 2 - 2, y - 2, 5, 5);
            GuiKit.icon(g, line.navy() ? GuiSprites.NAVY_ANCHOR : GuiSprites.WAX_SEAL, cardLeft + 5, y + 7);

            // amount, right-aligned with the coin
            int amountLeft = GuiKit.coins(g, font, Component.literal(Integer.toString(line.amount())), cardRight - 7, y + 4,
                    GuiKit.INK_AMBER, false);
            // headline: the target (plus the total when several bounties are on the same head)
            int textX = cardLeft + 18;
            Component name = Component.literal(line.targetName());
            if (line.targetTotal() != line.amount()) {
                name = name.copy().append(Component.translatable(NoticeBoardText.TOTAL, line.targetTotal()).withColor(GuiKit.INK_DIM));
            }
            int nameColor = mine ? GuiKit.INK_RED : line.targetIsPlayer() ? GuiKit.INK : 0xFF4A3A2A;
            GuiKit.ink(g, font, name, textX, y + 4, amountLeft - 4 - textX, nameColor);
            // second line: who placed it, how long ago
            Component by = line.navy()
                    ? Component.translatable(NoticeBoardText.NAVY).withColor(GuiKit.INK_NAVY)
                    : Component.literal(line.placedBy());
            NoticeBoardListing.Age age = NoticeBoardListing.age(v.now() - line.createdAt());
            Component second = Component.translatable(NoticeBoardText.COL_PLACED_BY).append(": ").append(by)
                    .append(Component.literal("  ·  ")).append(Component.translatable(NoticeBoardText.age(age.unit()), age.value()));
            GuiKit.ink(g, font, second, textX, y + 14, cardRight - 7 - textX, GuiKit.INK_DIM);
        }
    }

    private void renderForm(GuiGraphics g, NoticeBoardView v, int mouseX, int mouseY) {
        int x = left + GuiKit.FRAME_BORDER + 6;
        int right = left + panelW - GuiKit.FRAME_BORDER - 6;
        Component label = v.placing()
                ? Component.translatable(NoticeBoardText.FORM).append(" ")
                        .append(Component.translatable(NoticeBoardText.FORM_MINIMUM, v.minimum()).withColor(GuiKit.INK_DIM))
                : Component.translatable(NoticeBoardText.FORM_DISABLED);
        GuiKit.ink(g, font, label, x, formY + 6, right - x, v.placing() ? GuiKit.INK : GuiKit.INK_DIM);
        // Name suggestions: those starting with what was typed (all when empty)
        String typed = targetField.getValue().trim().toLowerCase(Locale.ROOT);
        int cx = x;
        int cy = formY + 34;
        for (String name : v.names()) {
            if (!typed.isEmpty() && (!name.toLowerCase(Locale.ROOT).startsWith(typed) || name.equalsIgnoreCase(typed))) continue;
            int w = GuiKit.tagWidth(font, name);
            if (cx + w > right) break;
            Chip chip = new Chip(cx, cy, w, name);
            chips.add(chip);
            GuiKit.tag(g, font, cx, cy, w, name, chip.contains(mouseX, mouseY));
            cx += w + 3;
        }
        if (!status.getString().isEmpty()) GuiKit.ink(g, font, status, x, formY + 48, right - x, statusColor);
    }
}
