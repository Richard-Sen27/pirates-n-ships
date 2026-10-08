package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.core.client.gui.BrassButton;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.core.client.gui.GuiSprites;
import com.richardsenger.piratesnships.core.client.gui.ScrollBar;
import com.richardsenger.piratesnships.core.client.gui.ScrollMath;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.quest.Quest;
import com.richardsenger.piratesnships.rpg.quest.QuestPayloads;
import com.richardsenger.piratesnships.rpg.quest.QuestTarget;
import com.richardsenger.piratesnships.rpg.quest.QuestText;
import com.richardsenger.piratesnships.world.treasure.TreasureMapContent;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.MarketView;
import com.richardsenger.piratesnships.trade.net.OrderPayloads;
import com.richardsenger.piratesnships.ship.template.ShipOrderContent;
import com.richardsenger.piratesnships.ship.template.ShipOrderMath;
import com.richardsenger.piratesnships.ship.template.ShipReceiptText;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
 * deliver) and, at a seafarer village's desk, an "Orders" tab (SW1: the shipwright's ships with price, build time
 * and materials and an order button, and the player's orders there with their progress). Every button only sends a request; the server decides and answers with a new state, whose result shows
 * on the status line. The server pushes a new state when what the screen shows changed (another player's trade,
 * prices, doubloons). Closes on Escape, the inventory key, when the player is farther than the desk's reach, or when
 * the server refuses the session; closing sends {@link MarketPayloads.CloseMarket}.
 *
 * <p>Look (U1, GUI kit in {@code core/client/gui}): a wooden frame with a header plaque (port, kind, doubloons),
 * brass tab and quantity buttons, a styled quantity field, the goods table on parchment (item icons, buy and sell
 * columns between brass dividers, brass row buttons with hover, pressed and disabled looks) and the status line on a
 * parchment footer.
 */
public final class MarketScreen extends Screen {

    private enum Tab { GOODS, CONTRACTS, ORDERS, QUESTS }

    private enum Action { NONE, BUY, SELL, CONTRACT, ORDER, QUEST }

    /** A button drawn in a list row; {@code tooltip} (may be null) explains a disabled button. */
    private record RowButton(int x, int y, int w, int h, Component label, boolean active, Component tooltip, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** A line of the contracts tab: a section header, an offer, an accepted contract, or an "empty" note. */
    private record ContractRow(Component header, boolean heading, DeliveryContract contract, boolean mine) {
    }

    /** A line of the orders tab (SW1): a section header, a ship on offer, one of the player's orders, or a note. */
    private record OrderRow(Component header, boolean heading, OrderPayloads.OrderLine ship, OrderPayloads.MyOrder mine) {
    }

    /** A line of the quests tab (QST1): a section header, an offer, one of the player's active quests, or a note. */
    private record QuestRow(Component header, boolean heading, Quest quest, boolean mine) {
    }

    private static final int ROW_H = 22;
    private static final int FOOTER_H = 16;
    private static final int PRICE_W = 46;
    private static final int TRADE_BUTTON_W = 34;
    private static final int CONTRACT_BUTTON_W = 50;

    private Tab tab = Tab.GOODS;
    private int quantity = 1;
    private boolean sellPlundered;
    private int scroll;
    private long seenVersion = -1;
    private long seenResultVersion = ClientMarketState.resultVersion();
    private long seenOrderResultVersion = ClientMarketState.orderResultVersion();
    private boolean ordersShown;
    private boolean questsShown;
    private long seenQuestResultVersion = ClientMarketState.questResultVersion();
    private Action pending = Action.NONE;
    private Component status = Component.empty();
    private int statusColor = GuiKit.INK;
    private EditBox quantityField;
    private BrassButton plunderToggle;
    private final List<RowButton> rowButtons = new ArrayList<>();
    private final ScrollBar scrollBar = new ScrollBar();

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int tablePanelTop;
    private int listTop;
    private int listBottom;
    private int footerTop;
    private int tableRight;

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

    private int inner() {
        return left + GuiKit.FRAME_BORDER;
    }

    private int innerW() {
        return panelW - 2 * GuiKit.FRAME_BORDER;
    }

    @Override
    protected void init() {
        panelW = Math.min(380, width - 16);
        panelH = Math.min(250, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        int x0 = inner() + 2;
        int y = top + 28;
        addRenderableWidget(new BrassButton(x0, y, 70, 14, Component.translatable(MarketText.TAB_GOODS), () -> switchTab(Tab.GOODS))
                .selected(tab == Tab.GOODS)).active = tab != Tab.GOODS;
        addRenderableWidget(new BrassButton(x0 + 72, y, 70, 14, Component.translatable(MarketText.TAB_CONTRACTS), () -> switchTab(Tab.CONTRACTS))
                .selected(tab == Tab.CONTRACTS)).active = tab != Tab.CONTRACTS;
        // SW1: a seafarer village's desk also takes ship orders
        ordersShown = ClientMarketState.orders().isPresent();
        if (!ordersShown && tab == Tab.ORDERS) tab = Tab.GOODS;
        if (ordersShown) {
            addRenderableWidget(new BrassButton(x0 + 144, y, 70, 14, Component.translatable(MarketText.TAB_ORDERS), () -> switchTab(Tab.ORDERS))
                    .selected(tab == Tab.ORDERS)).active = tab != Tab.ORDERS;
        }
        // QST1: every desk has a Quests tab while quests are on
        questsShown = ClientMarketState.quests().isPresent();
        if (!questsShown && tab == Tab.QUESTS) tab = Tab.GOODS;
        if (questsShown) {
            int qx = x0 + (ordersShown ? 216 : 144);
            addRenderableWidget(new BrassButton(qx, y, 70, 14, Component.translatable(MarketText.TAB_QUESTS), () -> switchTab(Tab.QUESTS))
                    .selected(tab == Tab.QUESTS)).active = tab != Tab.QUESTS;
        }
        if (tab == Tab.GOODS) {
            int qy = top + 46;
            int x = x0 + font.width(Component.translatable(MarketText.QUANTITY)) + 6;
            for (int q : MarketLines.QUICK_QUANTITIES) {
                addRenderableWidget(new BrassButton(x, qy, 22, 14, Component.literal(Integer.toString(q)), () -> setQuantity(q, true)));
                x += 24;
            }
            int fieldX = x + 2;
            quantityField = new EditBox(font, GuiKit.fieldTextX(fieldX), GuiKit.fieldTextY(qy), GuiKit.fieldTextW(44), 10,
                    Component.translatable(MarketText.QUANTITY));
            quantityField.setMaxLength(6);
            quantityField.setFilter(s -> s.chars().allMatch(Character::isDigit));
            GuiKit.styleField(quantityField, Component.translatable(MarketText.QUANTITY));
            quantityField.setValue(Integer.toString(quantity));
            quantityField.setResponder(s -> MarketLines.parseQuantity(s, maxQuantity()).ifPresent(q -> setQuantity(q, false)));
            addRenderableWidget(quantityField);
            int right = inner() + innerW() - 2;
            int tw = Math.min(130, right - (fieldX + 50));
            plunderToggle = addRenderableWidget(new BrassButton(right - tw, qy, tw, 14, plunderLabel(), () -> {
                sellPlundered = !sellPlundered;
                plunderToggle.setMessage(plunderLabel());
            }));
            tablePanelTop = top + 64;
            listTop = tablePanelTop + 19;
        } else {
            quantityField = null;
            plunderToggle = null;
            tablePanelTop = top + 46;
            listTop = tablePanelTop + 4;
        }
        footerTop = top + panelH - GuiKit.FRAME_BORDER - FOOTER_H;
        listBottom = footerTop - 6;
        int barX = inner() + innerW() - 4 - GuiKit.SCROLLBAR_W;
        scrollBar.place(barX, tablePanelTop + 4, footerTop - 3 - 4 - (tablePanelTop + 4));
        tableRight = barX - 3;
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
            if (ClientMarketState.orders().isPresent() != ordersShown || ClientMarketState.quests().isPresent() != questsShown) rebuildWidgets();
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
            statusColor = result.get().done() ? GuiKit.INK_GREEN : GuiKit.INK_RED;
            pending = Action.NONE;
        }
        Optional<OrderPayloads.OrderResult> order = ClientMarketState.lastOrderResult();
        if (order.isPresent() && ClientMarketState.orderResultVersion() != seenOrderResultVersion) {
            seenOrderResultVersion = ClientMarketState.orderResultVersion();
            OrderPayloads.OrderResult r = order.get();
            // template names travel as translation keys
            Object[] args = r.args().stream().map(a -> a.startsWith("ship_template.") ? (Object) Component.translatable(a) : a).toArray();
            status = Component.translatable(r.key(), args);
            statusColor = r.done() ? GuiKit.INK_GREEN : GuiKit.INK_RED;
            if (pending == Action.ORDER) pending = Action.NONE;
        }
        Optional<QuestPayloads.QuestResult> quest = ClientMarketState.lastQuestResult();
        if (quest.isPresent() && ClientMarketState.questResultVersion() != seenQuestResultVersion) {
            seenQuestResultVersion = ClientMarketState.questResultVersion();
            QuestPayloads.QuestResult r = quest.get();
            Component name = r.quest().map(q -> QuestText.title(q, true)).orElse(Component.empty());
            status = Component.translatable(QuestText.result(r.key()), name);
            statusColor = r.done() ? GuiKit.INK_GREEN : GuiKit.INK_RED;
            if (pending == Action.QUEST) pending = Action.NONE;
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
        // a special offer (TM1) is no trade good: its id is its item's id
        Optional<ResourceLocation> item = TradeService.goods(true).tradeable().get(good).map(TradeGood::item).or(() -> Optional.of(good));
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
            case PRODUCES -> GuiKit.INK_GREEN;
            case DEMANDS -> GuiKit.INK_AMBER;
            case NEUTRAL, NOT_TRADED -> GuiKit.INK_DIM;
        };
    }

    private void send(Action action, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        pending = action;
        Services.NETWORK.sendToServer(payload);
    }

    /** Rows of the open tab (for scrolling). */
    private int rowCount() {
        if (tab == Tab.ORDERS) return ClientMarketState.orders().map(o -> orderRows(o).size()).orElse(0);
        if (tab == Tab.QUESTS) return ClientMarketState.quests().map(q -> questRows(q).size()).orElse(0);
        return ClientMarketState.view().map(v -> tab == Tab.GOODS ? MarketLines.order(v.goods()).size() : contractRows(v).size()).orElse(0);
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiKit.frame(g, left, top, panelW, panelH);
        GuiKit.sprite(g, GuiSprites.HEADER, inner(), top + GuiKit.FRAME_BORDER, innerW(), GuiKit.HEADER_H);
        GuiKit.parchment(g, inner() + 1, tablePanelTop, innerW() - 2, footerTop - 3 - tablePanelTop);
        GuiKit.parchment(g, inner() + 1, footerTop, innerW() - 2, FOOTER_H);
        if (quantityField != null) GuiKit.field(g, quantityField);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        rowButtons.clear();
        Optional<MarketView> view = ClientMarketState.view();

        // header plaque: port name and kind, doubloons
        int hx = inner() + 6;
        int hy = top + GuiKit.FRAME_BORDER + 4;
        int hRight = inner() + innerW() - 6;
        if (view.isPresent()) {
            MarketView v = view.get();
            int coinsLeft = GuiKit.coins(g, font, Component.literal(MarketLines.formatCoins(v.coins())), hRight, hy, GuiKit.BRASS_TEXT, true);
            Component name = Component.literal(MarketLines.portName(v.port())).withColor(GuiKit.BRASS_TEXT)
                    .append(Component.literal("  ")).append(Component.translatable(MarketText.kind(v.kind())).withColor(GuiKit.ON_WOOD_DIM));
            GuiKit.text(g, font, name, hx, hy, coinsLeft - 6 - hx, GuiKit.BRASS_TEXT, true);
        } else {
            g.drawString(font, title, hx, hy, GuiKit.BRASS_TEXT, true);
        }
        if (tab == Tab.GOODS) {
            g.drawString(font, Component.translatable(MarketText.QUANTITY), inner() + 2, top + 49, GuiKit.ON_WOOD, true);
        }

        int total = 0;
        if (view.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(MarketText.LOADING), (inner() + tableRight) / 2, listTop + 20, GuiKit.INK_DIM);
        } else if (tab == Tab.GOODS) {
            total = renderGoods(g, view.get(), mouseX, mouseY);
        } else if (tab == Tab.ORDERS) {
            MarketView v = view.get();
            total = ClientMarketState.orders().map(o -> renderOrders(g, v, o, mouseX, mouseY)).orElse(0);
        } else if (tab == Tab.QUESTS) {
            total = ClientMarketState.quests().map(q -> renderQuests(g, q, mouseX, mouseY)).orElse(0);
        } else {
            total = renderContracts(g, view.get(), mouseX, mouseY);
        }
        scrollBar.render(g, total, visibleRows(), scroll, mouseX, mouseY);

        for (RowButton b : rowButtons) {
            GuiKit.button(g, font, b.x(), b.y(), b.w(), b.h(), b.label(), GuiKit.buttonState(b.active(), b.contains(mouseX, mouseY)));
        }
        if (!status.getString().isEmpty()) {
            GuiKit.ink(g, font, status, inner() + 6, footerTop + 4, innerW() - 12, statusColor);
        }

        for (RowButton b : rowButtons) {
            if (!b.active() && b.tooltip() != null && b.contains(mouseX, mouseY)) g.renderTooltip(font, b.tooltip(), mouseX, mouseY);
        }
    }

    /** Draws a row's background: a faint stripe on odd rows, a stronger wash under the mouse. */
    private void rowWash(GuiGraphics g, int i, int y, int mouseX, int mouseY) {
        int x0 = inner() + 4;
        boolean hover = mouseX >= x0 && mouseX < tableRight && mouseY >= y && mouseY < y + ROW_H;
        if (hover) GuiKit.wash(g, x0, y, tableRight, y + ROW_H, 0x30);
        else if ((i & 1) == 1) GuiKit.wash(g, x0, y, tableRight, y + ROW_H, 0x14);
    }

    private int renderGoods(GuiGraphics g, MarketView v, int mouseX, int mouseY) {
        List<MarketView.GoodLine> lines = MarketLines.order(v.goods());
        int right = tableRight - 2;
        int sellBtnX = right - TRADE_BUTTON_W;
        int sellPriceX = sellBtnX - 2 - PRICE_W;
        int buyBtnX = sellPriceX - 8 - TRADE_BUTTON_W;
        int buyPriceX = buyBtnX - 2 - PRICE_W;
        int nameX = inner() + 24;
        int textW = buyPriceX - nameX - 8;
        int divBuy = buyPriceX - 5;
        int divSell = sellPriceX - 5;

        // column headings and brass rules
        int hy = tablePanelTop + 5;
        GuiKit.ink(g, font, Component.translatable(MarketText.TAB_GOODS), nameX, hy, textW, GuiKit.INK_DIM);
        Component buy = Component.translatable(MarketText.BUY);
        Component sell = Component.translatable(MarketText.SELL);
        g.drawString(font, buy, (divBuy + 3 + divSell) / 2 - font.width(buy) / 2, hy, GuiKit.INK_DIM, false);
        g.drawString(font, sell, (divSell + 3 + right) / 2 - font.width(sell) / 2, hy, GuiKit.INK_DIM, false);
        GuiKit.divider(g, inner() + 4, tablePanelTop + 15, tableRight - inner() - 4);
        int ruleBottom = Math.min(listBottom, listTop + Math.max(1, Math.min(lines.size(), visibleRows())) * ROW_H);
        GuiKit.dividerVertical(g, divBuy, tablePanelTop + 3, ruleBottom - tablePanelTop - 3);
        GuiKit.dividerVertical(g, divSell, tablePanelTop + 3, ruleBottom - tablePanelTop - 3);

        if (lines.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(MarketText.EMPTY), (inner() + tableRight) / 2, listTop + 8, GuiKit.INK_DIM);
            return 0;
        }
        scroll = ScrollMath.clamp(scroll, lines.size(), visibleRows());
        boolean fresh = v.quantity() == quantity && pending == Action.NONE;
        for (int i = scroll; i < lines.size() && i < scroll + visibleRows(); i++) {
            MarketView.GoodLine l = lines.get(i);
            int y = listTop + (i - scroll) * ROW_H;
            rowWash(g, i, y, mouseX, mouseY);
            ItemStack stack = itemStack(l.item());
            g.renderItem(stack, inner() + 5, y + 3);
            GuiKit.ink(g, font, stack.getHoverName(), nameX, y + 2, textW, GuiKit.INK);
            int clean = carried(stack.getItem(), false);
            int plundered = carried(stack.getItem(), true);
            Component have = plundered > 0 ? Component.translatable(MarketText.HAVE_PLUNDERED, clean, plundered)
                    : Component.translatable(MarketText.HAVE, clean);
            Component second = Component.translatable(MarketText.role(l.role())).withColor(roleColor(l.role()))
                    .append(Component.literal(", ").withColor(GuiKit.INK_DIM)).append(have.copy().withColor(GuiKit.INK_DIM));
            GuiKit.ink(g, font, second, nameX, y + 12, textW, GuiKit.INK);

            boolean canBuy = fresh && MarketLines.canBuy(l.buy(), v.coins());
            int carriedForSale = sellPlundered ? plundered : clean;
            boolean canSell = fresh && MarketLines.canSell(l.sell(), carriedForSale, quantity);
            String buyText = MarketLines.price(l.buy());
            String sellText = MarketLines.price(l.sell());
            g.drawString(font, buyText, buyPriceX + PRICE_W - font.width(buyText), y + 7, canBuy ? GuiKit.INK : GuiKit.INK_DIM, false);
            g.drawString(font, sellText, sellPriceX + PRICE_W - font.width(sellText), y + 7, canSell ? GuiKit.INK : GuiKit.INK_DIM, false);
            ResourceLocation port = v.port();
            ResourceLocation good = l.good();
            int q = quantity;
            boolean plunderedSale = sellPlundered;
            Component buyTip = null;
            if (fresh && l.buy().outcome() == com.richardsenger.piratesnships.trade.market.Market.Outcome.OK && l.buy().total() > v.coins()) {
                long n = Math.min(MarketLines.affordable(v.coins(), l.buy().total(), v.quantity()), l.buy().available());
                buyTip = Component.translatable(MarketText.AFFORD, MarketLines.formatCoins(n));
            }
            rowButtons.add(new RowButton(buyBtnX, y + 4, TRADE_BUTTON_W, 14, Component.translatable(MarketText.BUY), canBuy, buyTip,
                    () -> send(Action.BUY, new MarketPayloads.Trade(port, true, good, q, false, Optional.empty()))));
            rowButtons.add(new RowButton(sellBtnX, y + 4, TRADE_BUTTON_W, 14, Component.translatable(MarketText.SELL), canSell, null,
                    () -> send(Action.SELL, new MarketPayloads.Trade(port, false, good, q, plunderedSale, Optional.empty()))));
        }
        return lines.size();
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

    private int renderContracts(GuiGraphics g, MarketView v, int mouseX, int mouseY) {
        List<ContractRow> rows = contractRows(v);
        scroll = ScrollMath.clamp(scroll, rows.size(), visibleRows());
        int right = tableRight - 2;
        int textX = inner() + 24;
        for (int i = scroll; i < rows.size() && i < scroll + visibleRows(); i++) {
            ContractRow row = rows.get(i);
            int y = listTop + (i - scroll) * ROW_H;
            if (row.contract() == null) {
                if (row.heading()) {
                    g.drawString(font, row.header(), inner() + 6, y + 8, GuiKit.INK, false);
                    GuiKit.divider(g, inner() + 4, y + 18, tableRight - inner() - 4);
                } else {
                    g.drawString(font, row.header(), textX, y + 8, GuiKit.INK_DIM, false);
                }
                continue;
            }
            DeliveryContract c = row.contract();
            rowWash(g, i, y, mouseX, mouseY);
            ItemStack stack = goodStack(c.good());
            g.renderItem(stack, inner() + 5, y + 3);
            int textW = right - CONTRACT_BUTTON_W - 4 - textX;
            GuiKit.ink(g, font, Component.translatable(MarketText.CONTRACT_LINE, c.quantity(), stack.getHoverName(),
                    MarketLines.portName(c.destination())), textX, y + 2, textW, GuiKit.INK);
            GuiKit.ink(g, font, Component.translatable(MarketText.CONTRACT_DETAIL, c.deadlineDay(), MarketLines.formatCoins(c.reward()),
                    MarketLines.formatCoins(c.deposit())), textX, y + 12, textW, GuiKit.INK_DIM);
            ResourceLocation port = v.port();
            boolean idle = pending == Action.NONE;
            if (row.mine()) {
                boolean here = c.destination().equals(port);
                boolean enough = carried(stack.getItem(), false) >= c.quantity();
                rowButtons.add(new RowButton(right - CONTRACT_BUTTON_W, y + 4, CONTRACT_BUTTON_W, 14, Component.translatable(MarketText.DELIVER),
                        idle && here && enough, null,
                        () -> send(Action.CONTRACT, new MarketPayloads.ContractAction(port, true, c.id(), Optional.empty()))));
            } else {
                rowButtons.add(new RowButton(right - CONTRACT_BUTTON_W, y + 4, CONTRACT_BUTTON_W, 14, Component.translatable(MarketText.ACCEPT),
                        idle && v.coins() >= c.deposit(), null,
                        () -> send(Action.CONTRACT, new MarketPayloads.ContractAction(port, false, c.id(), Optional.empty()))));
            }
        }
        return rows.size();
    }

    private List<OrderRow> orderRows(OrderPayloads.OrdersView o) {
        List<OrderRow> rows = new ArrayList<>();
        rows.add(new OrderRow(Component.translatable(MarketText.SHIPS, o.open(), o.max()), true, null, null));
        if (!o.enabled()) rows.add(new OrderRow(Component.translatable(MarketText.ORDERS_DISABLED), false, null, null));
        else if (o.lines().isEmpty()) rows.add(new OrderRow(Component.translatable(MarketText.NO_SHIPS), false, null, null));
        for (OrderPayloads.OrderLine l : o.lines()) rows.add(new OrderRow(null, false, l, null));
        rows.add(new OrderRow(Component.translatable(MarketText.MY_ORDERS), true, null, null));
        if (o.mine().isEmpty()) rows.add(new OrderRow(Component.translatable(MarketText.NO_MY_ORDERS), false, null, null));
        for (OrderPayloads.MyOrder m : o.mine()) rows.add(new OrderRow(null, false, null, m));
        return rows;
    }

    /** Items of {@code tag} anywhere in the inventory (what the server takes). */
    private static int carried(TagKey<Item> tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(tag)) n += s.getCount();
        }
        return n;
    }

    private int renderOrders(GuiGraphics g, MarketView v, OrderPayloads.OrdersView o, int mouseX, int mouseY) {
        List<OrderRow> rows = orderRows(o);
        scroll = ScrollMath.clamp(scroll, rows.size(), visibleRows());
        int right = tableRight - 2;
        int textX = inner() + 24;
        int logs = carried(ItemTags.LOGS);
        int wool = carried(ItemTags.WOOL);
        Minecraft mc = Minecraft.getInstance();
        double today = mc.level == null ? 0.0 : ShipOrderMath.day(mc.level.getDayTime());
        for (int i = scroll; i < rows.size() && i < scroll + visibleRows(); i++) {
            OrderRow row = rows.get(i);
            int y = listTop + (i - scroll) * ROW_H;
            if (row.ship() == null && row.mine() == null) {
                if (row.heading()) {
                    g.drawString(font, row.header(), inner() + 6, y + 8, GuiKit.INK, false);
                    GuiKit.divider(g, inner() + 4, y + 18, tableRight - inner() - 4);
                } else {
                    g.drawString(font, row.header(), textX, y + 8, GuiKit.INK_DIM, false);
                }
                continue;
            }
            rowWash(g, i, y, mouseX, mouseY);
            if (row.mine() != null) {
                OrderPayloads.MyOrder m = row.mine();
                ShipReceiptText.State state = ShipReceiptText.state(m.finishDay(), today);
                g.renderItem(new ItemStack(ShipOrderContent.SHIP_RECEIPT.get()), inner() + 5, y + 3);
                GuiKit.ink(g, font, Component.translatable(m.name()), textX, y + 2, right - textX, GuiKit.INK);
                Component when = state.ready() ? Component.translatable(MarketText.MY_ORDER_READY)
                        : Component.translatable(MarketText.MY_ORDER_WAITING, state.daysLeft());
                GuiKit.ink(g, font, when, textX, y + 12, right - textX, state.ready() ? GuiKit.INK_GREEN : GuiKit.INK_DIM);
                continue;
            }
            OrderPayloads.OrderLine l = row.ship();
            g.renderItem(new ItemStack(Items.OAK_BOAT), inner() + 5, y + 3);
            int textW = right - CONTRACT_BUTTON_W - 4 - textX;
            GuiKit.ink(g, font, Component.translatable(l.name()), textX, y + 2, textW, GuiKit.INK);
            boolean enough = v.coins() >= l.price() && logs >= l.logs() && wool >= l.wool();
            Component detail = Component.translatable(MarketText.ORDER_DETAIL, MarketLines.formatCoins(l.price()),
                    ShipOrderMath.formatDays(l.buildDays())).append(Component.literal(", "))
                    .append(Component.translatable(MarketText.ORDER_MATERIALS, l.logs(), l.wool()));
            GuiKit.ink(g, font, detail, textX, y + 12, textW, enough ? GuiKit.INK_DIM : GuiKit.INK_RED);
            boolean free = o.enabled() && o.open() < o.max();
            Component tip = !o.enabled() ? Component.translatable(MarketText.ORDERS_DISABLED)
                    : !free ? Component.translatable(MarketText.ORDERS_FULL)
                    : !enough ? Component.translatable(MarketText.ORDER_NEEDS, MarketLines.formatCoins(l.price()), l.logs(), l.wool()) : null;
            ResourceLocation port = o.port();
            ResourceLocation template = l.template();
            rowButtons.add(new RowButton(right - CONTRACT_BUTTON_W, y + 4, CONTRACT_BUTTON_W, 14, Component.translatable(MarketText.ORDER),
                    pending == Action.NONE && free && enough, tip,
                    () -> send(Action.ORDER, new OrderPayloads.PlaceOrder(port, template))));
        }
        return rows.size();
    }

    private List<QuestRow> questRows(QuestPayloads.QuestsView q) {
        List<QuestRow> rows = new ArrayList<>();
        rows.add(new QuestRow(Component.translatable(QuestText.OFFERS, q.mine().size(), q.maxActive()), true, null, false));
        if (q.offers().isEmpty()) rows.add(new QuestRow(Component.translatable(QuestText.NO_OFFERS), false, null, false));
        for (Quest o : q.offers()) rows.add(new QuestRow(null, false, o, false));
        rows.add(new QuestRow(Component.translatable(QuestText.MINE), true, null, true));
        if (q.mine().isEmpty()) rows.add(new QuestRow(Component.translatable(QuestText.NO_MINE), false, null, true));
        for (Quest m : q.mine()) rows.add(new QuestRow(null, false, m, true));
        return rows;
    }

    /** The icon of a quest row: the cargo for a delivery, a map, a sword, ... */
    private static ItemStack questIcon(Quest q) {
        if (q.target() instanceof QuestTarget.Cargo c) return goodStack(c.good());
        return switch (q.type()) {
            case FIND_TREASURE -> new ItemStack(TreasureMapContent.TREASURE_MAP.get());
            case KILL_MONSTER -> new ItemStack(Items.TRIDENT);
            case TURN_IN -> new ItemStack(Items.CHAIN);
            case HUNT_NAVY -> new ItemStack(Items.GOLDEN_SWORD);
            default -> new ItemStack(Items.IRON_SWORD);
        };
    }

    private int renderQuests(GuiGraphics g, QuestPayloads.QuestsView q, int mouseX, int mouseY) {
        List<QuestRow> rows = questRows(q);
        scroll = ScrollMath.clamp(scroll, rows.size(), visibleRows());
        int right = tableRight - 2;
        int textX = inner() + 24;
        boolean full = q.mine().size() >= q.maxActive();
        for (int i = scroll; i < rows.size() && i < scroll + visibleRows(); i++) {
            QuestRow row = rows.get(i);
            int y = listTop + (i - scroll) * ROW_H;
            if (row.quest() == null) {
                if (row.heading()) {
                    g.drawString(font, row.header(), inner() + 6, y + 8, GuiKit.INK, false);
                    GuiKit.divider(g, inner() + 4, y + 18, tableRight - inner() - 4);
                } else {
                    g.drawString(font, row.header(), textX, y + 8, GuiKit.INK_DIM, false);
                }
                continue;
            }
            Quest quest = row.quest();
            rowWash(g, i, y, mouseX, mouseY);
            g.renderItem(questIcon(quest), inner() + 5, y + 3);
            int textW = right - CONTRACT_BUTTON_W - 4 - textX;
            GuiKit.ink(g, font, QuestText.title(quest, true), textX, y + 2, textW, GuiKit.INK);
            Component detail = row.mine() ? QuestText.activeDetail(quest) : QuestText.offerDetail(quest, q.deadlineDays());
            GuiKit.ink(g, font, detail, textX, y + 12, textW, GuiKit.INK_DIM);
            ResourceLocation port = q.port();
            java.util.UUID id = quest.id();
            boolean idle = pending == Action.NONE;
            if (row.mine()) {
                rowButtons.add(new RowButton(right - CONTRACT_BUTTON_W, y + 4, CONTRACT_BUTTON_W, 14, Component.translatable(QuestText.ABANDON),
                        idle, null, () -> send(Action.QUEST, new QuestPayloads.QuestAction(port, false, id))));
            } else {
                rowButtons.add(new RowButton(right - CONTRACT_BUTTON_W, y + 4, CONTRACT_BUTTON_W, 14, Component.translatable(QuestText.ACCEPT),
                        idle && !full, full ? Component.translatable(QuestText.FULL) : null,
                        () -> send(Action.QUEST, new QuestPayloads.QuestAction(port, true, id))));
            }
        }
        return rows.size();
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
        int s = scrollBar.click(mouseX, mouseY, rowCount(), visibleRows(), scroll);
        if (s >= 0) {
            scroll = s;
            return true;
        }
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int s = scrollBar.drag(mouseY, rowCount(), visibleRows());
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
        if (mouseY >= tablePanelTop && mouseY < listBottom && scrollY != 0) {
            scroll -= (int) Math.signum(scrollY);
            if (scroll < 0) scroll = 0; // the upper bound is clamped when drawing
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
