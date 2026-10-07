package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.MarketView;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The market screen of a harbor master's desk (client only, design.md §10.3). Opened by
 * {@link MarketPayloads.OpenMarket}; shows {@link ClientMarketState}'s view: the port's name and kind, the player's
 * doubloons, a "Goods" tab (each good with its role, buy and sell totals for the chosen quantity, what the player
 * carries, buy and sell buttons) and a "Contracts" tab (today's offers with accept, the player's contracts with
 * deliver). Every button only sends a request; the server decides and answers with a new state, whose result shows
 * on the status line. The server pushes a new state when what the screen shows changed (another player's trade,
 * prices, doubloons). Closes on Escape, the inventory key, when the player is farther than the desk's reach, or when
 * the server refuses the session; closing sends {@link MarketPayloads.CloseMarket}.
 */
public final class MarketScreen extends Screen {

    private enum Tab { GOODS, CONTRACTS }

    private enum Action { NONE, BUY, SELL, CONTRACT }

    /** A button drawn in a list row; {@code tooltip} (may be null) explains a disabled button. */
    private record RowButton(int x, int y, int w, int h, Component label, boolean active, Component tooltip, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** A line of the contracts tab: a section header, an offer, an accepted contract, or an "empty" note. */
    private record ContractRow(Component header, boolean heading, DeliveryContract contract, boolean mine) {
    }

    private static final int ROW_H = 22;
    private static final int PANEL = 0xF0201812;
    private static final int BORDER = 0xFF8B6B3A;
    private static final int ROW_ALT = 0x18FFFFFF;
    private static final int GOLD = 0xFFE8C060;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFA0A0A0;
    private static final int GOOD = 0xFF8FE08F;
    private static final int BAD = 0xFFFF8070;

    private Tab tab = Tab.GOODS;
    private int quantity = 1;
    private boolean sellPlundered;
    private int scroll;
    private long seenVersion = -1;
    private long seenResultVersion = ClientMarketState.resultVersion();
    private Action pending = Action.NONE;
    private Component status = Component.empty();
    private int statusColor = TEXT;
    private EditBox quantityField;
    private Button plunderToggle;
    private final List<RowButton> rowButtons = new ArrayList<>();

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listBottom;

    MarketScreen() {
        super(Component.translatable(MarketText.TITLE));
    }

    /** Opens the screen for the desk in {@link ClientMarketState} (installed as its opener). */
    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof MarketScreen) return;
        mc.setScreen(new MarketScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout and widgets

    @Override
    protected void init() {
        panelW = Math.min(380, width - 16);
        panelH = Math.min(250, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        int y = top + 20;
        Button goods = addRenderableWidget(Button.builder(Component.translatable(MarketText.TAB_GOODS), b -> switchTab(Tab.GOODS))
                .bounds(left + 6, y, 70, 16).build());
        Button contracts = addRenderableWidget(Button.builder(Component.translatable(MarketText.TAB_CONTRACTS), b -> switchTab(Tab.CONTRACTS))
                .bounds(left + 78, y, 70, 16).build());
        goods.active = tab != Tab.GOODS;
        contracts.active = tab != Tab.CONTRACTS;
        if (tab == Tab.GOODS) {
            int qy = top + 40;
            int x = left + 6 + font.width(Component.translatable(MarketText.QUANTITY)) + 6;
            for (int q : MarketLines.QUICK_QUANTITIES) {
                addRenderableWidget(Button.builder(Component.literal(Integer.toString(q)), b -> setQuantity(q, true))
                        .bounds(x, qy, 22, 16).build());
                x += 24;
            }
            quantityField = new EditBox(font, x + 2, qy + 1, 40, 14, Component.translatable(MarketText.QUANTITY));
            quantityField.setMaxLength(6);
            quantityField.setFilter(s -> s.chars().allMatch(Character::isDigit));
            quantityField.setValue(Integer.toString(quantity));
            quantityField.setResponder(s -> MarketLines.parseQuantity(s, maxQuantity()).ifPresent(q -> setQuantity(q, false)));
            addRenderableWidget(quantityField);
            int tw = Math.min(130, left + panelW - 6 - (x + 48));
            plunderToggle = addRenderableWidget(Button.builder(plunderLabel(), b -> {
                sellPlundered = !sellPlundered;
                b.setMessage(plunderLabel());
            }).bounds(left + panelW - 6 - tw, qy, tw, 16).build());
            listTop = top + 60;
        } else {
            quantityField = null;
            plunderToggle = null;
            listTop = top + 40;
        }
        listBottom = top + panelH - 16;
    }

    private Component plunderLabel() {
        return Component.translatable(sellPlundered ? MarketText.SELLING_PLUNDERED : MarketText.SELLING_CLEAN);
    }

    private void switchTab(Tab t) {
        tab = t;
        scroll = 0;
        rebuildWidgets();
    }

    private static int maxQuantity() {
        return TradeConfig.MAX_TRADE_QUANTITY.get();
    }

    private void setQuantity(int q, boolean updateField) {
        q = MarketLines.clampQuantity(q, maxQuantity());
        if (updateField && quantityField != null) {
            quantityField.setResponder(s -> { });
            quantityField.setValue(Integer.toString(q));
            quantityField.setResponder(s -> MarketLines.parseQuantity(s, maxQuantity()).ifPresent(v -> setQuantity(v, false)));
        }
        if (q == quantity) return;
        quantity = q;
        port().ifPresent(p -> Services.NETWORK.sendToServer(new MarketPayloads.Refresh(p, quantity)));
    }

    private static Optional<ResourceLocation> port() {
        return ClientMarketState.desk().map(MarketPayloads.OpenMarket::port);
    }

    // ------------------------------------------------------------------ state

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        Optional<MarketPayloads.OpenMarket> desk = ClientMarketState.desk();
        if (mc.player == null || desk.isEmpty()) {
            onClose();
            return;
        }
        double reach = desk.get().reach();
        if (mc.player.position().distanceToSqr(Vec3.atCenterOf(desk.get().desk())) > reach * reach) {
            onClose();
            return;
        }
        if (ClientMarketState.version() != seenVersion) {
            seenVersion = ClientMarketState.version();
            onState(mc);
        }
    }

    private void onState(Minecraft mc) {
        Optional<MarketView> view = ClientMarketState.view();
        Optional<TransactionResult> result = ClientMarketState.lastResult();
        boolean newResult = ClientMarketState.resultVersion() != seenResultVersion;
        seenResultVersion = ClientMarketState.resultVersion();
        if (view.isEmpty() && result.isPresent() && newResult) {
            // The server refused the session (desk gone, unbound, too far, desks disabled)
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable(MarketText.CLOSED), true);
            onClose();
            return;
        }
        if (result.isPresent() && newResult) {
            status = message(result.get(), view);
            statusColor = result.get().done() ? GOOD : BAD;
            pending = Action.NONE;
        }
    }

    @Override
    public void removed() {
        super.removed();
        // Escape, the inventory key, out of reach or refused: end the session on the server
        if (ClientMarketState.desk().isPresent() && Minecraft.getInstance().getConnection() != null) {
            Services.NETWORK.sendToServer(MarketPayloads.CloseMarket.INSTANCE);
        }
    }

    private Component message(TransactionResult r, Optional<MarketView> view) {
        Component good = goodStack(r.good()).getHoverName();
        if (r.contractOutcome().isPresent()) {
            DeliveryContract.Outcome o = r.contractOutcome().get();
            if (r.done() && o == DeliveryContract.Outcome.ACCEPTED) return Component.translatable(MarketText.ACCEPTED, MarketLines.formatCoins(r.coins()));
            if (r.done() && o == DeliveryContract.Outcome.DELIVERED) {
                return Component.translatable(MarketText.DELIVERED, r.units(), MarketLines.formatCoins(r.coins()));
            }
            return Component.translatable(MarketText.REFUSED, Component.translatable(r.status().translationKey()),
                    Component.translatable(MarketText.contractOutcome(o)));
        }
        if (!r.done()) return Component.translatable(r.status().translationKey());
        if (pending == Action.BUY) return Component.translatable(MarketText.BOUGHT, r.units(), good, MarketLines.formatCoins(r.coins()));
        return Component.translatable(MarketText.sold(r.plunder()), r.units(), good, MarketLines.formatCoins(r.coins()));
    }

    // ------------------------------------------------------------------ helpers

    private static ItemStack goodStack(ResourceLocation good) {
        Optional<ResourceLocation> item = TradeService.goods(true).tradeable().get(good).map(TradeGood::item);
        Item i = item.map(BuiltInRegistries.ITEM::get).orElse(Items.AIR);
        return i == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(i);
    }

    private static ItemStack itemStack(ResourceLocation item) {
        Item i = BuiltInRegistries.ITEM.get(item);
        return i == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(i);
    }

    /** Units of {@code item} with the plunder state in the main inventory (what the server counts). */
    private static int carried(Item item, boolean plundered) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (!s.isEmpty() && s.is(item) && PlunderMark.isPlundered(s) == plundered) n += s.getCount();
        }
        return n;
    }

    private static int roleColor(GoodRole role) {
        return switch (role) {
            case PRODUCES -> GOOD;
            case DEMANDS -> 0xFFFFB050;
            case NEUTRAL, NOT_TRADED -> DIM;
        };
    }

    private void drawClipped(GuiGraphics g, Component text, int x, int y, int maxW, int color) {
        FormattedText cut = font.width(text) <= maxW ? text : font.substrByWidth(text, Math.max(0, maxW - font.width("..")));
        g.drawString(font, Language.getInstance().getVisualOrder(cut), x, y, color);
        if (cut != text) g.drawString(font, "..", x + font.width(cut), y, color);
    }

    private void send(Action action, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        pending = action;
        Services.NETWORK.sendToServer(payload);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + panelW, top + panelH, PANEL);
        g.renderOutline(left, top, panelW, panelH, BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        rowButtons.clear();
        Optional<MarketView> view = ClientMarketState.view();

        // header: port name and kind, doubloons
        ItemStack coin = new ItemStack(TradeContent.DOUBLOON.get());
        if (view.isPresent()) {
            MarketView v = view.get();
            Component coins = Component.translatable(MarketText.COINS, MarketLines.formatCoins(v.coins()));
            int cw = font.width(coins);
            g.drawString(font, coins, left + panelW - 6 - cw, top + 6, GOLD);
            g.renderItem(coin, left + panelW - 6 - cw - 18, top + 2);
            Component name = Component.literal(MarketLines.portName(v.port())).withColor(GOLD)
                    .append(Component.literal("  ")).append(Component.translatable(MarketText.kind(v.kind())).withColor(DIM));
            drawClipped(g, name, left + 6, top + 6, panelW - cw - 36, TEXT);
        } else {
            g.drawString(font, title, left + 6, top + 6, GOLD);
        }
        if (tab == Tab.GOODS) {
            g.drawString(font, Component.translatable(MarketText.QUANTITY), left + 6, top + 44, DIM);
        }

        if (view.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(MarketText.LOADING), left + panelW / 2, listTop + 20, DIM);
        } else if (tab == Tab.GOODS) {
            renderGoods(g, view.get(), mouseX, mouseY);
        } else {
            renderContracts(g, view.get());
        }

        for (RowButton b : rowButtons) drawButton(g, b, mouseX, mouseY);
        if (!status.getString().isEmpty()) drawClipped(g, status, left + 6, top + panelH - 12, panelW - 12, statusColor);

        for (RowButton b : rowButtons) {
            if (!b.active() && b.tooltip() != null && b.contains(mouseX, mouseY)) g.renderTooltip(font, b.tooltip(), mouseX, mouseY);
        }
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private void renderGoods(GuiGraphics g, MarketView v, int mouseX, int mouseY) {
        List<MarketView.GoodLine> lines = MarketLines.order(v.goods());
        if (lines.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(MarketText.EMPTY), left + panelW / 2, listTop + 20, DIM);
            return;
        }
        scroll = MarketLines.clampScroll(scroll, lines.size(), visibleRows());
        boolean fresh = v.quantity() == quantity && pending == Action.NONE;
        int right = left + panelW - 6;
        int priceW = 46;
        int btnW = 34;
        int sellBtnX = right - btnW;
        int sellPriceX = sellBtnX - 2 - priceW;
        int buyBtnX = sellPriceX - 6 - btnW;
        int buyPriceX = buyBtnX - 2 - priceW;
        int textW = buyPriceX - (left + 26) - 4;
        for (int i = scroll; i < lines.size() && i < scroll + visibleRows(); i++) {
            MarketView.GoodLine l = lines.get(i);
            int y = listTop + (i - scroll) * ROW_H;
            if ((i & 1) == 1) g.fill(left + 2, y, left + panelW - 2, y + ROW_H, ROW_ALT);
            ItemStack stack = itemStack(l.item());
            g.renderItem(stack, left + 6, y + 3);
            drawClipped(g, stack.getHoverName(), left + 26, y + 2, textW, TEXT);
            int clean = carried(stack.getItem(), false);
            int plundered = carried(stack.getItem(), true);
            Component have = plundered > 0 ? Component.translatable(MarketText.HAVE_PLUNDERED, clean, plundered)
                    : Component.translatable(MarketText.HAVE, clean);
            Component second = Component.translatable(MarketText.role(l.role())).withColor(roleColor(l.role()))
                    .append(Component.literal(", ").withColor(DIM)).append(have.copy().withColor(DIM));
            drawClipped(g, second, left + 26, y + 12, textW, TEXT);

            boolean canBuy = fresh && MarketLines.canBuy(l.buy(), v.coins());
            int carriedForSale = sellPlundered ? plundered : clean;
            boolean canSell = fresh && MarketLines.canSell(l.sell(), carriedForSale, quantity);
            String buyText = MarketLines.price(l.buy());
            String sellText = MarketLines.price(l.sell());
            g.drawString(font, buyText, buyPriceX + priceW - font.width(buyText), y + 7, canBuy ? TEXT : DIM);
            g.drawString(font, sellText, sellPriceX + priceW - font.width(sellText), y + 7, canSell ? TEXT : DIM);
            ResourceLocation port = v.port();
            ResourceLocation good = l.good();
            int q = quantity;
            boolean plunderedSale = sellPlundered;
            Component buyTip = null;
            if (fresh && l.buy().outcome() == com.richardsenger.piratesnships.trade.market.Market.Outcome.OK && l.buy().total() > v.coins()) {
                long n = Math.min(MarketLines.affordable(v.coins(), l.buy().total(), v.quantity()), l.buy().available());
                buyTip = Component.translatable(MarketText.AFFORD, MarketLines.formatCoins(n));
            }
            rowButtons.add(new RowButton(buyBtnX, y + 4, btnW, 14, Component.translatable(MarketText.BUY), canBuy, buyTip,
                    () -> send(Action.BUY, new MarketPayloads.Trade(port, true, good, q, false, Optional.empty()))));
            rowButtons.add(new RowButton(sellBtnX, y + 4, btnW, 14, Component.translatable(MarketText.SELL), canSell, null,
                    () -> send(Action.SELL, new MarketPayloads.Trade(port, false, good, q, plunderedSale, Optional.empty()))));
        }
    }

    private List<ContractRow> contractRows(MarketView v) {
        List<ContractRow> rows = new ArrayList<>();
        rows.add(new ContractRow(Component.translatable(MarketText.OFFERS), true, null, false));
        if (v.offers().isEmpty()) rows.add(new ContractRow(Component.translatable(MarketText.NO_OFFERS), false, null, false));
        for (DeliveryContract c : v.offers()) rows.add(new ContractRow(null, false, c, false));
        rows.add(new ContractRow(Component.translatable(MarketText.MY_CONTRACTS), true, null, true));
        if (v.contracts().isEmpty()) rows.add(new ContractRow(Component.translatable(MarketText.NO_CONTRACTS), false, null, true));
        for (DeliveryContract c : v.contracts()) rows.add(new ContractRow(null, false, c, true));
        return rows;
    }

    private void renderContracts(GuiGraphics g, MarketView v) {
        List<ContractRow> rows = contractRows(v);
        scroll = MarketLines.clampScroll(scroll, rows.size(), visibleRows());
        int right = left + panelW - 6;
        int btnW = 50;
        for (int i = scroll; i < rows.size() && i < scroll + visibleRows(); i++) {
            ContractRow row = rows.get(i);
            int y = listTop + (i - scroll) * ROW_H;
            if (row.contract() == null) {
                boolean heading = row.heading();
                g.drawString(font, row.header(), left + (heading ? 6 : 26), y + 8, heading ? GOLD : DIM);
                continue;
            }
            DeliveryContract c = row.contract();
            if ((i & 1) == 1) g.fill(left + 2, y, left + panelW - 2, y + ROW_H, ROW_ALT);
            ItemStack stack = goodStack(c.good());
            g.renderItem(stack, left + 6, y + 3);
            int textW = right - btnW - 4 - (left + 26);
            drawClipped(g, Component.translatable(MarketText.CONTRACT_LINE, c.quantity(), stack.getHoverName(),
                    MarketLines.portName(c.destination())), left + 26, y + 2, textW, TEXT);
            drawClipped(g, Component.translatable(MarketText.CONTRACT_DETAIL, c.deadlineDay(), MarketLines.formatCoins(c.reward()),
                    MarketLines.formatCoins(c.deposit())), left + 26, y + 12, textW, DIM);
            ResourceLocation port = v.port();
            boolean idle = pending == Action.NONE;
            if (row.mine()) {
                boolean here = c.destination().equals(port);
                boolean enough = carried(stack.getItem(), false) >= c.quantity();
                rowButtons.add(new RowButton(right - btnW, y + 4, btnW, 14, Component.translatable(MarketText.DELIVER), idle && here && enough, null,
                        () -> send(Action.CONTRACT, new MarketPayloads.ContractAction(port, true, c.id(), Optional.empty()))));
            } else {
                rowButtons.add(new RowButton(right - btnW, y + 4, btnW, 14, Component.translatable(MarketText.ACCEPT),
                        idle && v.coins() >= c.deposit(), null,
                        () -> send(Action.CONTRACT, new MarketPayloads.ContractAction(port, false, c.id(), Optional.empty()))));
            }
        }
    }

    private void drawButton(GuiGraphics g, RowButton b, int mouseX, int mouseY) {
        boolean hover = b.active() && b.contains(mouseX, mouseY);
        int bg = !b.active() ? 0xFF303030 : hover ? 0xFF8B6B3A : 0xFF5A4325;
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), bg);
        g.renderOutline(b.x(), b.y(), b.w(), b.h(), b.active() ? BORDER : 0xFF505050);
        g.drawCenteredString(font, b.label(), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, b.active() ? TEXT : 0xFF808080);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        // The inventory key closes the screen like a container screen, unless the quantity field takes the key
        boolean typing = quantityField != null && quantityField.isFocused();
        if (!typing && Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (RowButton b : rowButtons) {
            if (b.active() && b.contains(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                b.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= listTop && mouseY < listBottom && scrollY != 0) {
            scroll -= (int) Math.signum(scrollY);
            if (scroll < 0) scroll = 0; // the upper bound is clamped when drawing
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
