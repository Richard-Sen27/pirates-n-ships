package com.richardsenger.piratesnships.ship.screen.client;

import com.richardsenger.piratesnships.core.client.gui.BrassButton;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.core.client.gui.GuiSprites;
import com.richardsenger.piratesnships.core.client.gui.ScrollBar;
import com.richardsenger.piratesnships.core.client.gui.ScrollMath;
import com.richardsenger.piratesnships.crew.galley.GalleyText;
import com.richardsenger.piratesnships.crew.hammock.CrewInfo;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudConfig;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudText;
import com.richardsenger.piratesnships.ship.screen.ShipScreenPayloads;
import com.richardsenger.piratesnships.ship.screen.ShipScreenRules;
import com.richardsenger.piratesnships.ship.screen.ShipScreenText;
import com.richardsenger.piratesnships.ship.screen.ShipScreenView;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * The ship screen at the helm (HGUI1, client only, docs/design.md §7.2). Opened by the server
 * ({@link ShipScreenPayloads.State} with {@code open}); draws {@link ClientShipScreenState}'s view in three tabs:
 * <b>Ship</b> (name field with rename, flag and allegiance, captain and title, hull, load and speed, anchor, sails,
 * crew and bunks, supplies, last pay, disassemble), <b>Crew</b> (the whistle's ship-wide orders as buttons, every hand
 * with morale, station or status, order, hirer, desertion, and release and dismiss buttons) and <b>Stations</b>
 * (every station with who mans it, its order or open job, release, and "Man" to send a free hand). Every button only
 * sends a {@link ShipScreenPayloads.Action}; the server decides and answers with a new view and a status line. The
 * server closes the screen when the player walks away from the helm or the ship goes; Escape and the inventory key close
 * it too, which ends the session on the server.
 *
 * <p>Look: the U1 kit as the market screen ({@code trade/client/MarketScreen}): a wooden frame, the header plaque,
 * brass tab and order buttons, the list on parchment with row buttons and a scrollbar, the status on a parchment
 * footer.
 */
public final class ShipScreen extends Screen {

    private enum Tab { SHIP, CREW, STATIONS }

    /** A button drawn in a list row; {@code tooltip} (may be null) explains a disabled button. */
    private record RowButton(int x, int y, int w, int h, Component label, boolean active, Component tooltip, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final int TAB_W = 70;
    private static final int TAB_GAP = 2;
    private static final int ROW_H = 22;
    private static final int LINE_H = 13;
    private static final int FOOTER_H = 16;
    private static final int BUTTON_W = 46;
    private static final int LABEL_W = 62;

    private static Tab lastTab = Tab.SHIP;

    private Tab tab = lastTab;
    private int scroll;
    private long seenVersion = -1;
    private long seenMessageVersion = ClientShipScreenState.messageVersion();
    private Component status = Component.empty();
    private int statusColor = GuiKit.INK;
    private boolean pending;
    private boolean confirmDisassemble;
    /** Stations tab: the station a free hand is being picked for, or null. */
    private BlockPos picking;
    private EditBox nameField;
    private BrassButton renameButton;
    private final List<RowButton> rowButtons = new ArrayList<>();
    private final ScrollBar scrollBar = new ScrollBar();

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int listPanelTop;
    private int listTop;
    private int listBottom;
    private int footerTop;
    private int tableRight;

    ShipScreen() {
        super(Component.translatable(ShipScreenText.TITLE));
    }

    /** Opens the screen (installed as {@link ClientShipScreenState}'s opener). */
    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof ShipScreen s) {
            s.seenVersion = -1; // reopened at the helm: show the new view
            return;
        }
        mc.setScreen(new ShipScreen());
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

    private static Optional<ShipScreenView> view() {
        return ClientShipScreenState.view();
    }

    @Override
    protected void init() {
        panelW = Math.min(380, width - 16);
        panelH = Math.min(250, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        int x0 = inner() + 2;
        int tabW = Math.max(30, Math.min(TAB_W, (innerW() - 4 - TAB_GAP * 2) / 3));
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(new BrassButton(x0 + i * (tabW + TAB_GAP), top + 28, tabW, 14, Component.translatable(tabLabel(t)),
                    () -> switchTab(t)).selected(tab == t)).active = tab != t;
        }
        Optional<ShipScreenView> v = view();
        String old = nameField == null ? null : nameField.getValue(); // typed text survives a rebuild
        nameField = null;
        renameButton = null;
        if (tab == Tab.SHIP) {
            int y = top + 46;
            int fx = x0 + LABEL_W - 8;
            int rw = 56;
            int fw = Math.max(60, inner() + innerW() - 2 - rw - 4 - fx);
            nameField = new EditBox(font, GuiKit.fieldTextX(fx), GuiKit.fieldTextY(y), GuiKit.fieldTextW(fw), 10,
                    Component.translatable(ShipScreenText.NAME_HINT));
            nameField.setMaxLength(ShipScreenView.MAX_NAME);
            GuiKit.styleField(nameField, Component.translatable(ShipScreenText.NAME_HINT));
            nameField.setValue(old != null ? old : v.map(x -> x.header().bareName()).orElse(""));
            nameField.setResponder(s -> updateRename());
            addRenderableWidget(nameField);
            renameButton = addRenderableWidget(new BrassButton(fx + fw + 4, y, rw, 14, Component.translatable(ShipScreenText.RENAME), this::rename));
            renameButton.setTooltip(Tooltip.create(Component.translatable(ShipScreenText.RENAME_TIP)));
            updateRename();
            listPanelTop = top + 64;
        } else if (tab == Tab.CREW) {
            List<WhistleOrder> orders = WhistleOrder.entries();
            int perRow = (orders.size() + 1) / 2;
            int bw = (innerW() - 4 - TAB_GAP * (perRow - 1)) / perRow;
            boolean active = v.isPresent() && ShipScreenRules.canOrder(v.get());
            for (int i = 0; i < orders.size(); i++) {
                WhistleOrder o = orders.get(i);
                int bx = x0 + (i % perRow) * (bw + TAB_GAP);
                int by = top + 46 + (i / perRow) * 16;
                BrassButton b = addRenderableWidget(new BrassButton(bx, by, bw, 14, Component.translatable(o.nameKey()), () -> order(o)));
                b.active = active && !pending;
                b.setTooltip(Tooltip.create(Component.translatable(o.descriptionKey())));
            }
            listPanelTop = top + 80;
        } else {
            listPanelTop = top + 46;
        }
        listTop = listPanelTop + 4;
        footerTop = top + panelH - GuiKit.FRAME_BORDER - FOOTER_H;
        listBottom = footerTop - 6;
        int barX = inner() + innerW() - 4 - GuiKit.SCROLLBAR_W;
        scrollBar.place(barX, listPanelTop + 4, footerTop - 3 - 4 - (listPanelTop + 4));
        tableRight = barX - 3;
    }

    private static String tabLabel(Tab t) {
        return switch (t) {
            case SHIP -> ShipScreenText.TAB_SHIP;
            case CREW -> ShipScreenText.TAB_CREW;
            case STATIONS -> ShipScreenText.TAB_STATIONS;
        };
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        scroll = 0;
        picking = null;
        confirmDisassemble = false;
        rebuildWidgets();
    }

    private void updateRename() {
        if (renameButton == null || nameField == null) return;
        renameButton.active = !pending && view().map(v -> ShipScreenRules.canRename(v, nameField.getValue())).orElse(false);
    }

    // ------------------------------------------------------------------ requests

    private void send(ShipScreenPayloads.Action action) {
        pending = true;
        Services.NETWORK.sendToServer(action);
        if (tab == Tab.CREW) rebuildWidgets(); // grey the order buttons until the answer
        updateRename();
    }

    private void rename() {
        view().ifPresent(v -> send(ShipScreenPayloads.Action.text(v.ship(), ShipScreenPayloads.Kind.RENAME, nameField.getValue())));
    }

    private void order(WhistleOrder o) {
        view().ifPresent(v -> send(ShipScreenPayloads.Action.text(v.ship(), ShipScreenPayloads.Kind.ORDER, o.id())));
    }

    // ------------------------------------------------------------------ state

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            onClose();
            return;
        }
        if (ClientShipScreenState.version() == seenVersion) return;
        boolean first = seenVersion == -1;
        seenVersion = ClientShipScreenState.version();
        boolean newMessage = ClientShipScreenState.messageVersion() != seenMessageVersion;
        seenMessageVersion = ClientShipScreenState.messageVersion();
        Optional<ShipScreenView> v = view();
        if (v.isEmpty()) {
            // the server ended the session: walked away, ship gone, disassembled, no longer the captain
            ClientShipScreenState.message().filter(m -> newMessage).ifPresent(m -> mc.player.displayClientMessage(m, true));
            onClose();
            return;
        }
        if (newMessage) {
            ClientShipScreenState.message().ifPresent(m -> {
                status = m;
                statusColor = ClientShipScreenState.ok() ? GuiKit.INK_GREEN : GuiKit.INK_RED;
            });
            pending = false;
            if (nameField != null && ClientShipScreenState.ok()) nameField.setValue(v.get().header().bareName());
        }
        if (picking != null && v.get().stations().stream().noneMatch(s -> s.pos().equals(picking) && s.occupant().isEmpty())) {
            picking = null;
        }
        if (first || newMessage || tab == Tab.CREW) rebuildWidgets();
        updateRename();
    }

    @Override
    public void removed() {
        super.removed();
        // Escape, the inventory key, or the server closed it: end the session (harmless when already ended)
        Optional<ShipScreenView> v = view();
        if (v.isPresent() && Minecraft.getInstance().getConnection() != null) {
            Services.NETWORK.sendToServer(ShipScreenPayloads.Action.of(v.get().ship(), ShipScreenPayloads.Kind.CLOSE));
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiKit.frame(g, left, top, panelW, panelH);
        GuiKit.sprite(g, GuiSprites.HEADER, inner(), top + GuiKit.FRAME_BORDER, innerW(), GuiKit.HEADER_H);
        GuiKit.parchment(g, inner() + 1, listPanelTop, innerW() - 2, footerTop - 3 - listPanelTop);
        GuiKit.parchment(g, inner() + 1, footerTop, innerW() - 2, FOOTER_H);
        if (nameField != null) GuiKit.field(g, nameField);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        rowButtons.clear();
        Optional<ShipScreenView> view = view();
        int hx = inner() + 6;
        int hy = top + GuiKit.FRAME_BORDER + 4;
        int hRight = inner() + innerW() - 6;
        if (view.isPresent()) {
            ShipScreenView v = view.get();
            Component flag = Component.translatable(ShipScreenText.allegiance(v.header().allegiance()));
            int fw = Math.min(font.width(flag), innerW() / 2);
            GuiKit.text(g, font, flag, hRight - fw, hy, fw, GuiKit.ON_WOOD_DIM, true);
            Component name = v.header().name().isEmpty() ? Component.translatable(ShipScreenText.UNNAMED) : Component.literal(v.header().name());
            GuiKit.text(g, font, name, hx, hy, hRight - fw - 8 - hx, GuiKit.BRASS_TEXT, true);
        } else {
            g.drawString(font, title, hx, hy, GuiKit.BRASS_TEXT, true);
        }
        if (tab == Tab.SHIP) {
            g.drawString(font, Component.translatable(ShipScreenText.LABEL_NAME), inner() + 2, top + 49, GuiKit.ON_WOOD, true);
        }
        int total = 0;
        if (view.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(ShipScreenText.LOADING), (inner() + tableRight) / 2, listTop + 20, GuiKit.INK_DIM);
        } else {
            total = switch (tab) {
                case SHIP -> renderShip(g, view.get(), mouseX, mouseY);
                case CREW -> renderCrew(g, view.get(), mouseX, mouseY);
                case STATIONS -> renderStations(g, view.get(), mouseX, mouseY);
            };
        }
        if (tab != Tab.SHIP) scrollBar.render(g, total, visibleRows(), scroll, mouseX, mouseY);
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

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private void rowWash(GuiGraphics g, int i, int y, int mouseX, int mouseY) {
        int x0 = inner() + 4;
        boolean hover = mouseX >= x0 && mouseX < tableRight && mouseY >= y && mouseY < y + ROW_H;
        if (hover) GuiKit.wash(g, x0, y, tableRight, y + ROW_H, 0x30);
        else if ((i & 1) == 1) GuiKit.wash(g, x0, y, tableRight, y + ROW_H, 0x14);
    }

    // ------------------------------------------------------------------ Ship tab

    private int renderShip(GuiGraphics g, ShipScreenView v, int mouseX, int mouseY) {
        int x = inner() + 6;
        int valueX = x + LABEL_W;
        int right = inner() + innerW() - 6;
        int y = listTop + 2;
        y = line(g, ShipScreenText.LABEL_FLAG, flagLine(v), x, valueX, right, y);
        y = line(g, ShipScreenText.LABEL_CAPTAIN, captainLine(v), x, valueX, right, y);
        y = line(g, ShipScreenText.LABEL_HULL, hullLine(v.status().hull()), x, valueX, right, y, v.status().hull().flooded() > 0
                || v.status().hull().breaches() > 0 ? GuiKit.INK_RED : GuiKit.INK);
        y = line(g, ShipScreenText.LABEL_LOAD, loadLine(v), x, valueX, right, y);
        y = line(g, ShipScreenText.LABEL_ANCHOR, Component.translatable(ShipScreenText.anchor(v.status().anchor())), x, valueX, right, y);
        y = line(g, ShipScreenText.LABEL_SAILS, sailsLine(v.status()), x, valueX, right, y);
        y = line(g, ShipScreenText.LABEL_CREW, Component.translatable(ShipScreenText.CREW_COUNT, v.upkeep().crew(), v.upkeep().bunks()),
                x, valueX, right, y, v.upkeep().crew() > v.upkeep().bunks() ? GuiKit.INK_AMBER : GuiKit.INK);
        ShipScreenView.Upkeep u = v.upkeep();
        y = line(g, ShipScreenText.LABEL_SUPPLIES, Component.translatable(ShipScreenText.SUPPLIES, days(u.foodDays()), days(u.waterDays()),
                days(u.rumDays())), x, valueX, right, y, Math.min(u.foodDays(), u.waterDays()) < 1 ? GuiKit.INK_RED : GuiKit.INK);
        line(g, ShipScreenText.LABEL_PAY, payLine(u), x, valueX, right, y, u.paidOnce() && u.unpaid() > 0 ? GuiKit.INK_RED : GuiKit.INK);
        int bw = 90;
        int by = listBottom - 16;
        Component label = Component.translatable(confirmDisassemble ? ShipScreenText.DISASSEMBLE_CONFIRM : ShipScreenText.DISASSEMBLE);
        boolean can = ShipScreenRules.canDisassemble(v) && !pending;
        rowButtons.add(new RowButton(right - bw, by, bw, 14, label, can, Component.translatable(ShipScreenText.TIP_NOT_CAPTAIN), () -> {
            if (!confirmDisassemble) {
                confirmDisassemble = true;
                return;
            }
            confirmDisassemble = false;
            send(ShipScreenPayloads.Action.of(v.ship(), ShipScreenPayloads.Kind.DISASSEMBLE));
        }));
        return 0;
    }

    private int line(GuiGraphics g, String label, Component value, int x, int valueX, int right, int y) {
        return line(g, label, value, x, valueX, right, y, GuiKit.INK);
    }

    private int line(GuiGraphics g, String label, Component value, int x, int valueX, int right, int y, int color) {
        GuiKit.ink(g, font, Component.translatable(label), x, y, valueX - x - 4, GuiKit.INK_DIM);
        GuiKit.ink(g, font, value, valueX, y, right - valueX, color);
        return y + LINE_H;
    }

    private static Component flagLine(ShipScreenView v) {
        Component a = Component.translatable(ShipScreenText.allegiance(v.header().allegiance()));
        return v.header().coverBlown() ? Component.translatable(ShipScreenText.COVER_BLOWN, a) : a;
    }

    private static Component captainLine(ShipScreenView v) {
        if (v.header().ownerless()) return Component.translatable(ShipScreenText.CAPTAIN_NONE);
        Component name = v.header().owner().<Component>map(Component::literal).orElse(Component.translatable(ShipScreenText.CAPTAIN_UNKNOWN));
        return v.header().title().<Component>map(t -> Component.translatable(ShipScreenText.CAPTAIN_TITLED, Component.translatable(t), name))
                .orElse(name);
    }

    private static Component hullLine(ShipScreenRules.Hull h) {
        if (h.compartments() == 0) return Component.translatable(ShipScreenText.HULL_UNKNOWN);
        if (h.flooded() == 0 && h.breaches() == 0 && h.pumping() == 0) return Component.translatable(ShipScreenText.HULL_DRY, h.compartments());
        return Component.translatable(ShipScreenText.HULL, h.compartments(), h.flooded(), h.breaches(), h.pumping());
    }

    private static Component loadLine(ShipScreenView v) {
        CargoWeight.LoadLevel[] all = CargoWeight.LoadLevel.values();
        int load = v.status().load();
        Component level = load >= 0 && load < all.length ? Component.translatable(all[load].translationKey())
                : Component.translatable(ShipScreenText.LOAD_UNKNOWN);
        return Component.translatable(ShipScreenText.LOAD_SPEED, level, ShipHudText.speed(ShipHudConfig.SPEED_UNIT.get(), v.status().speed()));
    }

    private static Component sailsLine(ShipScreenView.Status s) {
        if (s.sails() == 0) return Component.translatable(ShipScreenText.SAILS_NONE);
        return Component.translatable(ShipScreenText.SAILS, s.sails(), s.full(), s.half(), s.furled());
    }

    private static Object days(double d) {
        return Double.isInfinite(d) ? Component.translatable(ShipScreenText.PLENTY) : GalleyText.number(d);
    }

    private static Component payLine(ShipScreenView.Upkeep u) {
        if (!u.wagesEnabled()) return Component.translatable(ShipScreenText.PAY_OFF);
        if (!u.paidOnce()) return Component.translatable(ShipScreenText.PAY_NONE);
        return Component.translatable(ShipScreenText.PAY, u.paid(), u.unpaid(), u.coins());
    }

    // ------------------------------------------------------------------ Crew tab

    private int renderCrew(GuiGraphics g, ShipScreenView v, int mouseX, int mouseY) {
        List<ShipScreenView.CrewLine> crew = v.crew();
        int rows = crew.size() + 1;
        scroll = ScrollMath.clamp(scroll, rows, visibleRows());
        int right = tableRight - 2;
        int textX = inner() + 8;
        for (int i = scroll; i < rows && i < scroll + visibleRows(); i++) {
            int y = listTop + (i - scroll) * ROW_H;
            if (i == 0) {
                Component head = crew.isEmpty() ? Component.translatable(ShipScreenText.NO_CREW_ABOARD)
                        : Component.translatable(ShipScreenText.CREW_HEADING, crew.size());
                g.drawString(font, head, inner() + 6, y + 8, crew.isEmpty() ? GuiKit.INK_DIM : GuiKit.INK, false);
                GuiKit.divider(g, inner() + 4, y + 18, tableRight - inner() - 4);
                continue;
            }
            ShipScreenView.CrewLine c = crew.get(i - 1);
            rowWash(g, i, y, mouseX, mouseY);
            int buttonsW = 2 * BUTTON_W + 4;
            int textW = right - buttonsW - 4 - textX;
            Component morale = Component.translatable(ShipScreenText.MORALE, c.morale());
            int mw = font.width(morale);
            GuiKit.ink(g, font, Component.literal(c.name()), textX, y + 2, textW - mw - 6, GuiKit.INK);
            g.drawString(font, morale, textX + textW - mw, y + 2, moraleColor(c), false);
            GuiKit.ink(g, font, crewDetail(c), textX, y + 12, textW, c.desertion() == ShipScreenRules.Desertion.LEAVING ? GuiKit.INK_RED : GuiKit.INK_DIM);
            UUID id = c.id();
            Component releaseTip = !v.toggles().stations() ? Component.translatable(ShipScreenText.TIP_STATIONS_OFF)
                    : !c.mayCommand() ? Component.translatable(ShipScreenText.TIP_NOT_YOUR_HAND) : null;
            rowButtons.add(new RowButton(right - buttonsW, y + 4, BUTTON_W, 14, Component.translatable(ShipScreenText.RELEASE),
                    !pending && ShipScreenRules.canRelease(v, c), releaseTip,
                    () -> send(ShipScreenPayloads.Action.crew(v.ship(), ShipScreenPayloads.Kind.RELEASE, id))));
            Component dismissTip = !v.toggles().hiring() ? Component.translatable(ShipScreenText.TIP_HIRING_OFF)
                    : Component.translatable(ShipScreenText.TIP_NOT_YOUR_HAND);
            rowButtons.add(new RowButton(right - BUTTON_W, y + 4, BUTTON_W, 14, Component.translatable(ShipScreenText.DISMISS),
                    !pending && ShipScreenRules.canDismiss(v, c), dismissTip,
                    () -> send(ShipScreenPayloads.Action.crew(v.ship(), ShipScreenPayloads.Kind.DISMISS, id))));
        }
        return rows;
    }

    private static int moraleColor(ShipScreenView.CrewLine c) {
        return switch (c.desertion()) {
            case NONE -> c.morale() >= 50 ? GuiKit.INK_GREEN : GuiKit.INK_AMBER;
            case LOW -> GuiKit.INK_AMBER;
            case LEAVING -> GuiKit.INK_RED;
        };
    }

    /** "Sail Winch: hoist the sails · hired by you · unpaid · low morale, 1 dawns". */
    private static Component crewDetail(ShipScreenView.CrewLine c) {
        MutableComponent out;
        if (c.state() == ShipScreenRules.CrewState.STATION) {
            Component station = Component.translatable(c.stationKey());
            out = c.orderKey().isEmpty() ? station.copy()
                    : Component.translatable(ShipScreenText.AT_STATION_DOING, station, Component.translatable(c.orderKey()));
        } else {
            out = Component.translatable(c.state() == ShipScreenRules.CrewState.RESTING ? CrewInfo.KEY_STATUS_HAMMOCK : CrewInfo.KEY_STATUS_FREE);
        }
        if (c.hiredByYou()) out.append(" · ").append(Component.translatable(ShipScreenText.HIRED_BY_YOU));
        else c.hiredBy().ifPresent(h -> out.append(" · ").append(Component.translatable(ShipScreenText.HIRED_BY, h)));
        if (c.unpaid()) out.append(" · ").append(Component.translatable(ShipScreenText.UNPAID));
        if (c.desertion() == ShipScreenRules.Desertion.LEAVING) {
            out.append(" · ").append(Component.translatable(ShipScreenText.DESERTION_LEAVING));
        } else if (c.desertion() == ShipScreenRules.Desertion.LOW) {
            out.append(" · ").append(Component.translatable(ShipScreenText.DESERTION_LOW, c.lowDays()));
        }
        return out;
    }

    // ------------------------------------------------------------------ Stations tab

    private int renderStations(GuiGraphics g, ShipScreenView v, int mouseX, int mouseY) {
        if (picking != null) return renderPicker(g, v, mouseX, mouseY);
        List<ShipScreenView.StationLine> stations = v.stations();
        int rows = stations.size() + 1;
        scroll = ScrollMath.clamp(scroll, rows, visibleRows());
        int right = tableRight - 2;
        int textX = inner() + 8;
        for (int i = scroll; i < rows && i < scroll + visibleRows(); i++) {
            int y = listTop + (i - scroll) * ROW_H;
            if (i == 0) {
                Component head = stations.isEmpty() ? Component.translatable(ShipScreenText.NO_STATIONS)
                        : Component.translatable(ShipScreenText.STATIONS_HEADING, stations.size());
                g.drawString(font, head, inner() + 6, y + 8, stations.isEmpty() ? GuiKit.INK_DIM : GuiKit.INK, false);
                GuiKit.divider(g, inner() + 4, y + 18, tableRight - inner() - 4);
                continue;
            }
            ShipScreenView.StationLine s = stations.get(i - 1);
            rowWash(g, i, y, mouseX, mouseY);
            int textW = right - BUTTON_W - 4 - textX;
            GuiKit.ink(g, font, Component.translatable(s.blockKey()), textX, y + 2, textW, GuiKit.INK);
            GuiKit.ink(g, font, stationDetail(s), textX, y + 12, textW, s.occupant().isEmpty() ? GuiKit.INK_DIM : GuiKit.INK_NAVY);
            BlockPos pos = s.pos();
            if (s.occupant().isPresent()) {
                UUID who = s.occupant().get();
                Component tip = s.playerOccupant() ? Component.translatable(ShipScreenText.TIP_PLAYER_AT_STATION)
                        : !v.toggles().stations() ? Component.translatable(ShipScreenText.TIP_STATIONS_OFF)
                        : Component.translatable(ShipScreenText.TIP_NOT_YOUR_HAND);
                rowButtons.add(new RowButton(right - BUTTON_W, y + 4, BUTTON_W, 14, Component.translatable(ShipScreenText.RELEASE),
                        !pending && ShipScreenRules.canReleaseAt(v, s), tip,
                        () -> send(ShipScreenPayloads.Action.crew(v.ship(), ShipScreenPayloads.Kind.RELEASE, who))));
            } else {
                Component tip = !v.toggles().stations() ? Component.translatable(ShipScreenText.TIP_STATIONS_OFF)
                        : Component.translatable(ShipScreenText.TIP_NO_FREE_HAND);
                rowButtons.add(new RowButton(right - BUTTON_W, y + 4, BUTTON_W, 14, Component.translatable(ShipScreenText.MAN),
                        !pending && ShipScreenRules.canMan(v, s), tip, () -> {
                            picking = pos;
                            scroll = 0;
                        }));
            }
        }
        return rows;
    }

    /** "manned by Jack · hoist the sails", "unmanned · open job: hoist the sails". */
    private static Component stationDetail(ShipScreenView.StationLine s) {
        MutableComponent out = s.occupant().isPresent()
                ? Component.translatable(ShipScreenText.MANNED_BY, s.occupantName().isEmpty() ? "?" : s.occupantName())
                : Component.translatable(ShipScreenText.UNMANNED);
        if (!s.orderKey().isEmpty()) out.append(" · ").append(Component.translatable(s.orderKey()));
        if (!s.jobKey().isEmpty()) out.append(" · ").append(Component.translatable(ShipScreenText.JOB_OPEN, Component.translatable(s.jobKey())));
        return out;
    }

    /** The free hands to send to the station {@link #picking}. */
    private int renderPicker(GuiGraphics g, ShipScreenView v, int mouseX, int mouseY) {
        List<ShipScreenView.CrewLine> free = ShipScreenRules.freeCrew(v);
        String stationKey = v.stations().stream().filter(s -> s.pos().equals(picking)).map(ShipScreenView.StationLine::blockKey)
                .findFirst().orElse("");
        int rows = free.size() + 1;
        scroll = ScrollMath.clamp(scroll, rows, visibleRows());
        int right = tableRight - 2;
        int textX = inner() + 8;
        BlockPos station = picking;
        for (int i = scroll; i < rows && i < scroll + visibleRows(); i++) {
            int y = listTop + (i - scroll) * ROW_H;
            if (i == 0) {
                GuiKit.ink(g, font, Component.translatable(ShipScreenText.PICK, Component.translatable(stationKey)), inner() + 6, y + 8,
                        right - BUTTON_W - 8 - inner(), GuiKit.INK);
                rowButtons.add(new RowButton(right - BUTTON_W, y + 3, BUTTON_W, 14, Component.translatable(ShipScreenText.CANCEL), true, null,
                        () -> {
                            picking = null;
                            scroll = 0;
                        }));
                GuiKit.divider(g, inner() + 4, y + 18, tableRight - inner() - 4);
                continue;
            }
            ShipScreenView.CrewLine c = free.get(i - 1);
            rowWash(g, i, y, mouseX, mouseY);
            int textW = right - BUTTON_W - 4 - textX;
            GuiKit.ink(g, font, Component.literal(c.name()), textX, y + 2, textW, GuiKit.INK);
            GuiKit.ink(g, font, crewDetail(c), textX, y + 12, textW, GuiKit.INK_DIM);
            UUID id = c.id();
            rowButtons.add(new RowButton(right - BUTTON_W, y + 4, BUTTON_W, 14, Component.translatable(ShipScreenText.SEND), !pending, null,
                    () -> {
                        picking = null;
                        send(ShipScreenPayloads.Action.assign(v.ship(), id, station));
                    }));
        }
        return rows;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null && nameField.isFocused() && (keyCode == 257 || keyCode == 335)) { // Enter renames
            if (renameButton != null && renameButton.active) rename();
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        boolean typing = nameField != null && nameField.isFocused();
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
        if (tab != Tab.SHIP) {
            int s = scrollBar.click(mouseX, mouseY, rowCount(), visibleRows(), scroll);
            if (s >= 0) {
                scroll = s;
                return true;
            }
        }
        for (RowButton b : new ArrayList<>(rowButtons)) {
            if (b.active() && b.contains(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                b.action().run();
                return true;
            }
        }
        confirmDisassemble = false;
        return false;
    }

    private int rowCount() {
        Optional<ShipScreenView> v = view();
        if (v.isEmpty()) return 0;
        if (tab == Tab.CREW) return v.get().crew().size() + 1;
        if (picking != null) return ShipScreenRules.freeCrew(v.get()).size() + 1;
        return v.get().stations().size() + 1;
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
        if (tab != Tab.SHIP && mouseY >= listPanelTop && mouseY < listBottom && scrollY != 0) {
            scroll -= (int) Math.signum(scrollY);
            if (scroll < 0) scroll = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
