package com.richardsenger.piratesnships.chart.client;

import com.mojang.math.Axis;
import com.richardsenger.piratesnships.chart.ChartConfig;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.tile.InkCost;
import com.richardsenger.piratesnships.chart.ChartText;
import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.net.ChartMarkerPayload;
import com.richardsenger.piratesnships.chart.net.ChartSettings;
import com.richardsenger.piratesnships.chart.net.ChartSimplePayloads;
import com.richardsenger.piratesnships.chart.net.ChartStatePayload;
import com.richardsenger.piratesnships.chart.net.ChartViewPayload;
import com.richardsenger.piratesnships.chart.net.ClearBoardPayload;
import com.richardsenger.piratesnships.chart.net.DrawTilePayload;
import com.richardsenger.piratesnships.chart.net.TileTarget;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import com.richardsenger.piratesnships.chart.render.ChartDoodles;
import com.richardsenger.piratesnships.chart.render.ChartProjection;
import com.richardsenger.piratesnships.chart.render.ChartSheet;
import com.richardsenger.piratesnships.core.client.gui.BrassButton;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import com.richardsenger.piratesnships.platform.Services;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The chart screen (client only, work package MAP1). A wooden frame with a header plaque, the chart on a parchment
 * sheet: the regions in {@link ClientChart} drawn by {@link ChartTextures} (the pirate style of
 * {@link com.richardsenger.piratesnships.chart.render.ChartRaster}), sea monsters and small roses in open water
 * ({@code chart_visuals.doodles}), the player's markers, other players (server option), the player as a small ship
 * pointing along their heading, a compass rose in the corner and a scale bar. A footer of brass buttons (zoom out, zoom
 * in, centre) with the position under the cursor and the hint or the last refusal.
 *
 * <p>Input: drag pans, the wheel zooms around the cursor (three steps), right-click places a new marker (an editor
 * with the five icons, a name field, save and cancel), left-click on a marker edits it (rename, other icon, delete).
 * The viewport is remembered per player; it is sent to the server whenever it moves, so the regions in view stream in.
 */
public final class ChartScreen extends Screen {

    private static final int FOOTER_H = 20;
    private static final int EDITOR_W = 156;
    private static final int EDITOR_H = 62;
    private static final int MARKER_HIT = 6;
    private static final int[] SCALE_STEPS = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};

    private ChartProjection view;
    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int mapL;
    private int mapT;
    private int mapW;
    private int mapH;

    private boolean dragging;
    private double dragDistance;
    private ChartProjection lastSentView;
    private int viewCooldown;

    // marker editor
    private boolean editing;
    private int editId = -1;
    private int editX;
    private int editZ;
    private MarkerIcon editIcon = MarkerIcon.X;
    private EditBox nameField;
    private BrassButton saveButton;
    private BrassButton deleteButton;
    private BrassButton cancelButton;
    private int editorX;
    private int editorY;

    private Component status = Component.empty();
    private int statusColor = GuiKit.INK_DIM;
    private int statusTicks;

    private final Map<Long, CachedDoodle> doodles = new HashMap<>();

    // "draw on tile" mode (work package MAP2): null for the plain chart
    private final TileTarget target;
    private int selCx;
    private int selCz;
    private double selFracX;
    private double selFracZ;
    private boolean draggingSelection;
    private boolean includeMarkers = true;
    private boolean confirmRedraw;
    private BrassButton markersButton;
    private BrassButton drawButton;
    // map boards (work package MAP3): the zoom, update or new drawing, clearing
    private int boardZoom = 1;
    private boolean updateMode;
    private boolean confirmClear;
    private BrassButton zoomDownButton;
    private BrassButton zoomUpButton;
    private BrassButton modeButton;
    private BrassButton clearButton;

    private record CachedDoodle(long version, Optional<ChartDoodles.Doodle> doodle) {
    }

    ChartScreen(TileTarget target) {
        super(target == null ? Component.translatable(ChartText.TITLE, playerName()) : Component.translatable(ChartText.DRAW_TITLE));
        this.target = target;
        if (target != null) {
            selCx = target.minCx();
            selCz = target.minCz();
            boardZoom = Math.max(1, target.zoom());
            updateMode = target.updatable();
        }
    }

    private static String playerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "" : mc.player.getGameProfile().getName();
    }

    /** Opens the chart (installed as {@link ClientChart}'s opener). */
    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TileTarget target = ClientChart.drawTarget().orElse(null);
        if (mc.screen instanceof ChartScreen open && target == null && open.target == null) {
            open.lastSentView = null;
            return;
        }
        mc.setScreen(new ChartScreen(target));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cellBlocks() {
        return Math.max(1, ClientChart.settings().cellBlocks());
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        panelW = Math.min(420, width - 16);
        panelH = Math.min(300, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        int inner = GuiKit.FRAME_BORDER;
        mapL = left + inner + 2;
        mapT = top + inner + GuiKit.HEADER_H + 3;
        mapW = panelW - 2 * inner - 4;
        mapH = panelH - 2 * inner - GuiKit.HEADER_H - 3 - FOOTER_H - 2;
        if (view == null && target != null) {
            // the whole selection in view at the widest zoom
            double cx = (selCx + selW() / 2.0) * cellBlocks();
            double cz = (selCz + selH() / 2.0) * cellBlocks();
            view = new ChartProjection(cx, cz, 0, cellBlocks());
            clampSelection();
        }
        if (view == null) {
            view = mc.player == null ? new ChartProjection(0, 0, ChartProjection.DEFAULT_ZOOM, cellBlocks())
                    : ClientChart.view(mc.player.getUUID())
                            .map(v -> new ChartProjection(v.centerX(), v.centerZ(), v.zoom(), cellBlocks()))
                            .orElseGet(() -> new ChartProjection(mc.player.getX(), mc.player.getZ(), ChartProjection.DEFAULT_ZOOM, cellBlocks()));
        }

        int by = mapT + mapH + 4;
        int bx = mapL;
        addRenderableWidget(new BrassButton(bx, by, 16, GuiKit.FIELD_H, Component.translatable(ChartText.ZOOM_OUT), () -> zoomBy(-1)));
        addRenderableWidget(new BrassButton(bx + 18, by, 16, GuiKit.FIELD_H, Component.translatable(ChartText.ZOOM_IN), () -> zoomBy(1)));
        addRenderableWidget(new BrassButton(bx + 36, by, 50, GuiKit.FIELD_H, Component.translatable(ChartText.CENTRE), this::centreOnPlayer));

        // the marker editor (hidden until a marker is placed or picked)
        editorX = mapL + 4;
        editorY = mapT + 4;
        String oldName = nameField == null ? "" : nameField.getValue();
        int fieldY = editorY + 28;
        nameField = new EditBox(font, GuiKit.fieldTextX(editorX + 6), GuiKit.fieldTextY(fieldY), GuiKit.fieldTextW(EDITOR_W - 12), 10,
                Component.translatable(ChartText.MARKER_NAME));
        nameField.setMaxLength(MarkerRules.MAX_NAME_LENGTH);
        GuiKit.styleField(nameField, Component.translatable(ChartText.MARKER_NAME));
        nameField.setValue(oldName);
        addRenderableWidget(nameField);
        int buttonsY = editorY + EDITOR_H - GuiKit.FIELD_H - 4;
        saveButton = addRenderableWidget(new BrassButton(editorX + 6, buttonsY, 44, GuiKit.FIELD_H, Component.translatable(ChartText.SAVE), this::saveMarker));
        deleteButton = addRenderableWidget(new BrassButton(editorX + 54, buttonsY, 46, GuiKit.FIELD_H, Component.translatable(ChartText.DELETE), this::deleteMarker));
        cancelButton = addRenderableWidget(new BrassButton(editorX + 104, buttonsY, 46, GuiKit.FIELD_H, Component.translatable(ChartText.CANCEL), this::closeEditor));
        updateEditorWidgets();
        if (target != null) {
            int right = mapL + mapW;
            drawButton = addRenderableWidget(new BrassButton(right - 56, by, 56, GuiKit.FIELD_H, drawLabel(), this::drawPressed));
            markersButton = addRenderableWidget(new BrassButton(right - 56 - 4 - 62, by, 62, GuiKit.FIELD_H,
                    Component.translatable(ChartText.INCLUDE_MARKERS), this::toggleMarkers).selected(includeMarkers));
            // the board panel in the map's top-left corner
            int px = panelX();
            int py = panelY() + PANEL_H - GuiKit.FIELD_H - 4;
            zoomDownButton = addRenderableWidget(new BrassButton(px + 6, py, 16, GuiKit.FIELD_H, Component.translatable(ChartText.ZOOM_OUT), () -> zoomBoard(-1)));
            zoomUpButton = addRenderableWidget(new BrassButton(px + 24, py, 16, GuiKit.FIELD_H, Component.translatable(ChartText.ZOOM_IN), () -> zoomBoard(1)));
            modeButton = addRenderableWidget(new BrassButton(px + 46, py, 50, GuiKit.FIELD_H, modeLabel(), this::toggleMode));
            clearButton = addRenderableWidget(new BrassButton(px + PANEL_W - 6 - 48, py, 48, GuiKit.FIELD_H, clearLabel(), this::clearPressed));
            updateBoardWidgets();
        }
        lastSentView = null;
    }

    // ------------------------------------------------------------------ draw on a tile or board (MAP2, MAP3)

    private static final int PANEL_W = 170;
    private static final int PANEL_H = 44;

    private int panelX() {
        return mapL + 4;
    }

    private int panelY() {
        return mapT + 4;
    }

    private boolean inPanel(double mx, double my) {
        return target != null && mx >= panelX() && mx < panelX() + PANEL_W && my >= panelY() && my < panelY() + PANEL_H;
    }

    /** Chart cells the frame covers across: tile pixels x columns x zoom. */
    private int selW() {
        return target.tileCells() * target.columns() * boardZoom;
    }

    private int selH() {
        return target.tileCells() * target.rows() * boardZoom;
    }

    private Component drawLabel() {
        if (target == null) return Component.translatable(ChartText.DRAW);
        if (updateMode) return Component.translatable(ChartText.UPDATE);
        if (!target.drawn()) return Component.translatable(ChartText.DRAW);
        return Component.translatable(confirmRedraw ? ChartText.REDRAW_CONFIRM : ChartText.REDRAW);
    }

    private Component modeLabel() {
        return Component.translatable(updateMode ? ChartText.NEW_DRAWING : ChartText.UPDATE);
    }

    private Component clearLabel() {
        return Component.translatable(confirmClear ? ChartText.CLEAR_CONFIRM : ChartText.CLEAR);
    }

    private void updateBoardWidgets() {
        if (target == null || drawButton == null) return;
        drawButton.setMessage(drawLabel());
        drawButton.active = updateMode || !target.drawn() || target.redrawAllowed();
        zoomDownButton.active = !updateMode && boardZoom > 1;
        zoomUpButton.active = !updateMode && boardZoom < target.maxZoom();
        // switching between an update and a new drawing: only on an intact board, and a new drawing needs redraw_allowed
        modeButton.visible = target.updatable() && target.redrawAllowed();
        modeButton.setMessage(modeLabel());
        clearButton.visible = target.drawn() && target.redrawAllowed();
        clearButton.setMessage(clearLabel());
    }

    private void toggleMarkers() {
        includeMarkers = !includeMarkers;
        markersButton.selected(includeMarkers);
    }

    private void toggleMode() {
        if (target == null || !target.updatable()) return;
        updateMode = !updateMode;
        if (updateMode) {
            // back to the board's own area and zoom
            selCx = target.minCx();
            selCz = target.minCz();
            boardZoom = Math.max(1, target.zoom());
        }
        confirmRedraw = false;
        confirmClear = false;
        updateBoardWidgets();
    }

    private void zoomBoard(int step) {
        if (target == null || updateMode) return;
        int next = Math.max(1, Math.min(target.maxZoom(), boardZoom + step));
        if (next == boardZoom) return;
        // keep the frame's centre where it is
        double cx = selCx + selW() / 2.0;
        double cz = selCz + selH() / 2.0;
        boardZoom = next;
        selCx = (int) Math.floor(cx - selW() / 2.0);
        selCz = (int) Math.floor(cz - selH() / 2.0);
        clampSelection();
        selectionMoved();
        updateBoardWidgets();
    }

    private void drawPressed() {
        if (target == null) return;
        if (updateMode) {
            Services.NETWORK.sendToServer(DrawTilePayload.update(target.pos(), includeMarkers));
        } else {
            if (target.drawn() && !confirmRedraw) {
                // a drawn board is only ever redrawn as a whole: ask once more
                confirmRedraw = true;
                updateBoardWidgets();
                showStatus(Component.translatable(ChartText.REDRAW_CONFIRM), GuiKit.INK_RED);
                return;
            }
            Services.NETWORK.sendToServer(new DrawTilePayload(target.pos(), selCx, selCz, boardZoom, includeMarkers, false));
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        onClose();
    }

    private void clearPressed() {
        if (target == null || !target.drawn()) return;
        if (!confirmClear) {
            confirmClear = true;
            updateBoardWidgets();
            showStatus(Component.translatable(ChartText.CLEAR_CONFIRM), GuiKit.INK_RED);
            return;
        }
        Services.NETWORK.sendToServer(new ClearBoardPayload(target.pos()));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        onClose();
    }

    private void selectionMoved() {
        if (confirmRedraw || confirmClear) {
            confirmRedraw = false;
            confirmClear = false;
            updateBoardWidgets();
        }
    }

    /** Keeps the selection on the charted area ({@link MapTileRaster#clampAxis}); nothing charted: no limit. */
    private void clampSelection() {
        if (target == null) return;
        ChartProjection.Bounds known = ClientChart.bounds();
        if (known == null) return;
        int cb = cellBlocks();
        selCx = MapTileRaster.clampAxis(selCx, selW(), (int) Math.floor(known.minX() / cb), (int) Math.floor(known.maxX() / cb) - 1);
        selCz = MapTileRaster.clampAxis(selCz, selH(), (int) Math.floor(known.minZ() / cb), (int) Math.floor(known.maxZ() / cb) - 1);
    }

    private long originX() {
        return (long) Math.floor(view.screenX(0, mapL, mapW));
    }

    private long originY() {
        return (long) Math.floor(view.screenY(0, mapT, mapH));
    }

    private boolean inSelection(double mx, double my) {
        if (target == null) return false;
        int px = view.pixelsPerCell();
        double x0 = originX() + (double) selCx * px;
        double y0 = originY() + (double) selCz * px;
        return mx >= x0 && mx < x0 + (double) selW() * px && my >= y0 && my < y0 + (double) selH() * px;
    }

    /** The selection as a parchment frame over the chart, the rest washed darker, thin lines where the tiles meet. */
    private void renderSelection(GuiGraphics g) {
        int px = view.pixelsPerCell();
        int l = clampScreen(originX() + (long) selCx * px, mapL, mapW);
        int t = clampScreen(originY() + (long) selCz * px, mapT, mapH);
        int r = clampScreen(originX() + ((long) selCx + selW()) * px, mapL, mapW);
        int b = clampScreen(originY() + ((long) selCz + selH()) * px, mapT, mapH);
        int wash = 0x60201810;
        g.fill(mapL, mapT, mapL + mapW, Math.max(mapT, t), wash);
        g.fill(mapL, Math.min(mapT + mapH, b), mapL + mapW, mapT + mapH, wash);
        g.fill(mapL, Math.max(t, mapT), Math.max(mapL, l), Math.min(b, mapT + mapH), wash);
        g.fill(Math.min(mapL + mapW, r), Math.max(t, mapT), mapL + mapW, Math.min(b, mapT + mapH), wash);
        // the seams between the tiles of a board
        long tilePx = (long) target.tileCells() * boardZoom * px;
        for (int c = 1; c < target.columns(); c++) {
            int x = clampScreen(originX() + (long) selCx * px + c * tilePx, mapL, mapW);
            g.fill(x, Math.max(t, mapT), x + 1, Math.min(b, mapT + mapH), 0x80201810);
        }
        for (int row = 1; row < target.rows(); row++) {
            int y = clampScreen(originY() + (long) selCz * px + row * tilePx, mapT, mapH);
            g.fill(Math.max(l, mapL), y, Math.min(r, mapL + mapW), y + 1, 0x80201810);
        }
        // ink, two pixels of parchment, ink
        frameRect(g, l - 3, t - 3, r + 3, b + 3, GuiKit.INK);
        frameRect(g, l - 2, t - 2, r + 2, b + 2, MapTileRaster.PARCHMENT);
        frameRect(g, l - 1, t - 1, r + 1, b + 1, MapTileRaster.PARCHMENT);
        frameRect(g, l, t, r, b, GuiKit.INK);
    }

    /** The board panel: size and zoom, and the ink a draw or update costs against what the player carries. */
    private void renderBoardPanel(GuiGraphics g) {
        int x = panelX();
        int y = panelY();
        GuiKit.parchment(g, x, y, PANEL_W, PANEL_H);
        Component size = Component.translatable(ChartText.BOARD_SIZE, target.columns(), target.rows()).append("  ")
                .append(Component.translatable(updateMode ? ChartText.BOARD_ZOOM_LOCKED : ChartText.BOARD_ZOOM, boardZoom));
        GuiKit.ink(g, font, size, x + 6, y + 4, PANEL_W - 12, GuiKit.INK);
        Component ink;
        int color = GuiKit.INK_DIM;
        if (target.inkPerTile() <= 0) {
            ink = Component.translatable(ChartText.INK_FREE);
        } else {
            long held = inkHeld();
            int cost = target.tiles() * target.inkPerTile();
            if (updateMode) {
                ink = Component.translatable(ChartText.INK_COST_UPDATE, cost, held);
                if (held < target.inkPerTile()) color = GuiKit.INK_RED;
            } else if (held < cost) {
                ink = Component.translatable(ChartText.INK_SHORT, cost, held);
                color = GuiKit.INK_RED;
            } else {
                ink = Component.translatable(ChartText.INK_COST, cost, held);
            }
        }
        GuiKit.ink(g, font, ink, x + 6, y + 15, PANEL_W - 12, color);
    }

    /** The ink in the player's inventory ({@code #pirates_n_ships:chart_ink}; kraken ink counts as several tiles). */
    private long inkHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int ink = 0;
        int kraken = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ChartContent.CHART_INK)) continue;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ChartContent.KRAKEN_INK)) kraken += stack.getCount();
            else ink += stack.getCount();
        }
        return InkCost.held(ink, kraken, target.krakenWorth());
    }

    private static int clampScreen(long v, int min, int len) {
        return (int) Math.max(min - 8, Math.min(min + len + 8, v));
    }

    private static void frameRect(GuiGraphics g, int l, int t, int r, int b, int color) {
        g.fill(l, t, r, t + 1, color);
        g.fill(l, b - 1, r, b, color);
        g.fill(l, t, l + 1, b, color);
        g.fill(r - 1, t, r, b, color);
    }

    // ------------------------------------------------------------------ state

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientChart.settings().enabled()) {
            onClose();
            return;
        }
        if (view.cellBlocks() != cellBlocks()) {
            view = new ChartProjection(view.centerX(), view.centerZ(), view.zoom(), cellBlocks());
        }
        Optional<String> refusal = ClientChart.takeRefusal();
        if (refusal.isPresent()) {
            showStatus(Component.translatable(refusal.get()), GuiKit.INK_RED);
        }
        if (statusTicks > 0 && --statusTicks == 0) status = Component.empty();
        if (viewCooldown > 0) viewCooldown--;
        if (!view.equals(lastSentView) && viewCooldown == 0) {
            sendView();
        }
        if (saveButton != null) saveButton.active = editing && MarkerRules.checkName(nameField.getValue().strip()) == null;
    }

    private void sendView() {
        int px = view.pixelsPerCell();
        int radius = (int) Math.ceil(Math.max(mapW, mapH) / 2.0 / px) + ChartRegion.SIZE / 2;
        int cx = (int) Math.floor(view.centerX() / cellBlocks());
        int cz = (int) Math.floor(view.centerZ() / cellBlocks());
        Services.NETWORK.sendToServer(new ChartViewPayload(cx, cz, radius));
        lastSentView = view;
        viewCooldown = 4;
        rememberView();
    }

    private void rememberView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) ClientChart.rememberView(mc.player.getUUID(), view);
    }

    private void showStatus(Component text, int color) {
        status = text;
        statusColor = color;
        statusTicks = 100;
    }

    @Override
    public void removed() {
        super.removed();
        rememberView();
        if (Minecraft.getInstance().getConnection() != null) {
            Services.NETWORK.sendToServer(ChartSimplePayloads.Close.INSTANCE);
        }
    }

    // ------------------------------------------------------------------ view changes

    private void setView(ChartProjection p) {
        view = p.clamped(ClientChart.bounds(), Math.max(mapW, mapH) * p.blocksPerPixel() / 2);
    }

    private void zoomBy(int steps) {
        zoomAt(steps, mapL + mapW / 2.0, mapT + mapH / 2.0);
    }

    private void zoomAt(int steps, double sx, double sy) {
        int z = ChartProjection.clampZoom(view.zoom() + steps);
        if (z == view.zoom()) return;
        setView(view.zoomed(z, sx, sy, mapL, mapT, mapW, mapH));
    }

    private void centreOnPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        view = view.centeredOn(mc.player.getX(), mc.player.getZ());
    }

    // ------------------------------------------------------------------ marker editor

    private void openEditor(int id, int x, int z, MarkerIcon icon, String name) {
        editing = true;
        editId = id;
        editX = x;
        editZ = z;
        editIcon = icon;
        nameField.setValue(name);
        setFocused(nameField);
        nameField.setFocused(true);
        updateEditorWidgets();
    }

    private void closeEditor() {
        editing = false;
        editId = -1;
        nameField.setFocused(false);
        updateEditorWidgets();
    }

    private void updateEditorWidgets() {
        if (nameField == null) return;
        nameField.visible = editing;
        saveButton.visible = editing;
        cancelButton.visible = editing;
        deleteButton.visible = editing && editId >= 0;
    }

    private void saveMarker() {
        if (!editing) return;
        String name = nameField.getValue().strip();
        if (editId >= 0) {
            Services.NETWORK.sendToServer(ChartMarkerPayload.edit(editId, editX, editZ, editIcon, name));
        } else {
            ChartSettings s = ClientChart.settings();
            if (ClientChart.markers().size() >= s.maxMarkers()) {
                showStatus(Component.translatable(com.richardsenger.piratesnships.chart.net.ChartBackend.MSG + "refused.too_many"), GuiKit.INK_RED);
                return;
            }
            Services.NETWORK.sendToServer(ChartMarkerPayload.add(editX, editZ, editIcon, name));
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        closeEditor();
    }

    private void deleteMarker() {
        if (!editing || editId < 0) return;
        Services.NETWORK.sendToServer(ChartMarkerPayload.remove(editId));
        closeEditor();
    }

    private boolean inEditor(double mx, double my) {
        return editing && mx >= editorX && mx < editorX + EDITOR_W && my >= editorY && my < editorY + EDITOR_H;
    }

    private int iconX(int i) {
        return editorX + 6 + i * 14;
    }

    private int iconY() {
        return editorY + 14;
    }

    // ------------------------------------------------------------------ input

    private boolean inMap(double mx, double my) {
        return mx >= mapL && mx < mapL + mapW && my >= mapT && my < mapT + mapH;
    }

    private Optional<ChartMarker> markerAt(double mx, double my) {
        ChartMarker best = null;
        double bestD = MARKER_HIT * MARKER_HIT;
        for (ChartMarker m : ClientChart.markers()) {
            double sx = view.screenX(m.x() + 0.5, mapL, mapW);
            double sy = view.screenY(m.z() + 0.5, mapT, mapH);
            double d = (sx - mx) * (sx - mx) + (sy - my) * (sy - my);
            if (d <= bestD) {
                bestD = d;
                best = m;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inEditor(mx, my)) {
            for (int i = 0; i < MarkerIcon.values().length; i++) {
                if (mx >= iconX(i) - 1 && mx < iconX(i) + 12 && my >= iconY() - 1 && my < iconY() + 12) {
                    editIcon = MarkerIcon.values()[i];
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    return true;
                }
            }
            super.mouseClicked(mx, my, button);
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (!inMap(mx, my)) return false;
        if (target != null) {
            // draw mode: no marker editing; drag the selection (not while updating: the board's area is fixed) or pan the chart
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || inPanel(mx, my)) return true;
            if (!updateMode && inSelection(mx, my)) {
                draggingSelection = true;
                selFracX = 0;
                selFracZ = 0;
            } else {
                dragging = true;
                dragDistance = 0;
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int x = (int) Math.floor(view.blockX(mx, mapL, mapW));
            int z = (int) Math.floor(view.blockZ(my, mapT, mapH));
            if (editing && editId < 0) {
                editX = x;
                editZ = z;
            } else {
                openEditor(-1, x, z, editIcon, "");
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Optional<ChartMarker> m = markerAt(mx, my);
            if (m.isPresent()) {
                ChartMarker mk = m.get();
                openEditor(mk.id(), mk.x(), mk.z(), mk.icon(), mk.name());
                return true;
            }
            if (editing) closeEditor();
            dragging = true;
            dragDistance = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSelection && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int px = view.pixelsPerCell();
            selFracX += dx / px;
            selFracZ += dy / px;
            int stepX = (int) selFracX;
            int stepZ = (int) selFracZ;
            if (stepX != 0 || stepZ != 0) {
                selFracX -= stepX;
                selFracZ -= stepZ;
                selCx += stepX;
                selCz += stepZ;
                clampSelection();
                selectionMoved();
            }
            return true;
        }
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragDistance += Math.abs(dx) + Math.abs(dy);
            setView(view.pan(dx, dy));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        draggingSelection = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0 && inMap(mx, my)) {
            zoomAt(scrollY > 0 ? 1 : -1, mx, my);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (editing) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeEditor();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (saveButton.active) saveMarker();
                return true;
            }
            if (nameField.isFocused()) return nameField.keyPressed(key, scan, modifiers) || nameField.canConsumeInput() || super.keyPressed(key, scan, modifiers);
        }
        if (ChartClient.OPEN_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiKit.frame(g, left, top, panelW, panelH);
        int inner = left + GuiKit.FRAME_BORDER;
        GuiKit.header(g, font, title, inner, top + GuiKit.FRAME_BORDER, panelW - 2 * GuiKit.FRAME_BORDER);
        GuiKit.parchment(g, mapL - 2, mapT - 2, mapW + 4, mapH + 4);
        // everything below the widgets: the chart, the footer text, the editor's panel
        renderMap(g, mouseX, mouseY);
        renderFooter(g, mouseX, mouseY);
        if (editing) renderEditor(g, mouseX, mouseY);
        if (target != null) renderBoardPanel(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltips(g, mouseX, mouseY);
    }

    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        ChartTextures.beginFrame();
        int px = view.pixelsPerCell();
        int regionPx = ChartRegion.SIZE * px;
        // one integer origin for everything, so regions, markers and ships line up to the pixel
        long originX = (long) Math.floor(view.screenX(0, mapL, mapW));
        long originY = (long) Math.floor(view.screenY(0, mapT, mapH));
        int minRx = (int) Math.floorDiv(Math.floorDiv(mapL - originX, px), ChartRegion.SIZE);
        int maxRx = (int) Math.floorDiv(Math.floorDiv(mapL + mapW - originX, px), ChartRegion.SIZE);
        int minRz = (int) Math.floorDiv(Math.floorDiv(mapT - originY, px), ChartRegion.SIZE);
        int maxRz = (int) Math.floorDiv(Math.floorDiv(mapT + mapH - originY, px), ChartRegion.SIZE);

        g.enableScissor(mapL, mapT, mapL + mapW, mapT + mapH);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int rz = minRz; rz <= maxRz; rz++) {
            for (int rx = minRx; rx <= maxRx; rx++) {
                if (ClientChart.region(ChartRegion.key(rx, rz)).isEmpty()) continue;
                var tex = ChartTextures.texture(rx, rz, px);
                if (tex == null) continue;
                int x = (int) (originX + (long) rx * regionPx);
                int y = (int) (originY + (long) rz * regionPx);
                g.blit(tex, x, y, 0, 0, regionPx, regionPx, regionPx, regionPx);
            }
        }
        if (ChartConfig.DOODLES.get()) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                for (int rx = minRx; rx <= maxRx; rx++) {
                    Optional<ChartDoodles.Doodle> d = doodle(rx, rz);
                    if (d.isEmpty()) continue;
                    ChartSheet.Part part = ChartSheet.doodle(d.get().kind());
                    // the doodle sits in the middle of its widest-zoom footprint
                    long fx = originX + (long) d.get().cx() * px + (long) d.get().kind().w * px / 2 - part.w() / 2;
                    long fy = originY + (long) d.get().cz() * px + (long) d.get().kind().h * px / 2 - part.h() / 2;
                    blitPart(g, part, (int) fx, (int) fy);
                }
            }
        }

        // markers
        Optional<ChartMarker> hover = inMap(mouseX, mouseY) && !inEditor(mouseX, mouseY) ? markerAt(mouseX, mouseY) : Optional.empty();
        for (ChartMarker m : ClientChart.markers()) {
            int sx = (int) Math.floor(originX + (m.x() + 0.5) / view.blocksPerPixel());
            int sy = (int) Math.floor(originY + (m.z() + 0.5) / view.blocksPerPixel());
            boolean selected = editing && editId == m.id();
            if (selected) blitPart(g, ChartSheet.RING, sx - 5, sy - 5);
            blitPart(g, ChartSheet.marker(selected ? editIcon : m.icon()), sx - 4, sy - 4);
            boolean showName = !m.name().isEmpty() && (view.zoom() >= ChartProjection.DEFAULT_ZOOM || hover.map(h -> h.id() == m.id()).orElse(false));
            if (showName) {
                int w = font.width(m.name());
                g.drawString(font, m.name(), sx - w / 2, sy + 6, GuiKit.INK, false);
            }
        }
        if (editing && editId < 0) {
            int sx = (int) Math.floor(originX + (editX + 0.5) / view.blocksPerPixel());
            int sy = (int) Math.floor(originY + (editZ + 0.5) / view.blocksPerPixel());
            blitPart(g, ChartSheet.RING, sx - 5, sy - 5);
            blitPart(g, ChartSheet.marker(editIcon), sx - 4, sy - 4);
        }

        if (target != null) renderSelection(g);

        boolean charted = mc.level != null && mc.level.dimension() == Level.OVERWORLD;
        if (charted) {
            for (ChartStatePayload.OtherPlayer o : ClientChart.others()) {
                int sx = (int) Math.floor(originX + (o.x() + 0.5) / view.blocksPerPixel());
                int sy = (int) Math.floor(originY + (o.z() + 0.5) / view.blocksPerPixel());
                ship(g, ChartSheet.OTHER_SHIP, sx, sy, o.yaw());
                if (Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) {
                    int w = font.width(o.name());
                    g.drawString(font, o.name(), sx - w / 2, sy + 6, GuiKit.INK_NAVY, false);
                }
            }
            if (mc.player != null) {
                int sx = (int) Math.floor(originX + mc.player.getX() / view.blocksPerPixel());
                int sy = (int) Math.floor(originY + mc.player.getZ() / view.blocksPerPixel());
                ship(g, ChartSheet.OWN_SHIP, sx, sy, mc.player.getYRot());
            }
        }
        if (ClientChart.regions().isEmpty()) {
            Component text = Component.translatable(charted ? ChartText.LOADING : ChartText.NOT_CHARTED);
            int w = font.width(text);
            g.drawString(font, text, mapL + (mapW - w) / 2, mapT + mapH / 2 + 12, GuiKit.INK_DIM, false);
        }
        g.disableScissor();

        // the compass rose in the top-right corner, the scale bar in the bottom-left
        blitPart(g, ChartSheet.COMPASS, mapL + mapW - ChartSheet.COMPASS.w() - 4, mapT + 4);
        renderScale(g);
        if (!charted && !ClientChart.regions().isEmpty()) {
            Component text = Component.translatable(ChartText.NOT_CHARTED);
            g.drawString(font, text, mapL + 4, mapT + mapH - 24, GuiKit.INK_DIM, false);
        }
    }

    private Optional<ChartDoodles.Doodle> doodle(int rx, int rz) {
        long key = ChartRegion.key(rx, rz);
        Optional<ChartRegion> region = ClientChart.region(key);
        if (region.isEmpty()) return Optional.empty();
        CachedDoodle c = doodles.get(key);
        if (c == null || c.version() != region.get().version()) {
            c = new CachedDoodle(region.get().version(), ChartDoodles.place(ClientChart.lookup(), rx, rz));
            doodles.put(key, c);
        }
        return c.doodle();
    }

    private void blitPart(GuiGraphics g, ChartSheet.Part p, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(ChartSheet.TEXTURE, x, y, p.u(), p.v(), p.w(), p.h(), ChartSheet.WIDTH, ChartSheet.HEIGHT);
    }

    /** A ship icon centred on {@code (x, y)}, bow along the Minecraft yaw (0 = south, 180 = north). */
    private void ship(GuiGraphics g, ChartSheet.Part part, int x, int y, float yaw) {
        g.pose().pushPose();
        g.pose().translate(x + 0.5f, y + 0.5f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yaw + 180f));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(ChartSheet.TEXTURE, -part.w() / 2, -part.h() / 2, 0, part.u(), part.v(), part.w(), part.h(), ChartSheet.WIDTH, ChartSheet.HEIGHT);
        g.pose().popPose();
    }

    /** A bar of a round number of blocks, 30 to 90 pixels long, in four alternating segments. */
    private void renderScale(GuiGraphics g) {
        double bpp = view.blocksPerPixel();
        int blocks = SCALE_STEPS[SCALE_STEPS.length - 1];
        for (int s : SCALE_STEPS) {
            if (s / bpp >= 30) {
                blocks = s;
                break;
            }
        }
        int len = (int) Math.round(blocks / bpp);
        int x = mapL + 6;
        int y = mapT + mapH - 8;
        int seg = Math.max(1, len / 4);
        g.fill(x - 1, y - 1, x + seg * 4 + 1, y + 3, GuiKit.INK);
        for (int i = 0; i < 4; i++) {
            g.fill(x + i * seg, y, x + (i + 1) * seg, y + 2, i % 2 == 0 ? GuiKit.INK : 0xFFEEE0BC);
        }
        g.drawString(font, Component.translatable(ChartText.SCALE, blocks), x, y - 10, GuiKit.INK, false);
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        int x = mapL + 92;
        int y = mapT + mapH + 7;
        int right = mapL + mapW;
        Component text;
        int color;
        if (!status.getString().isEmpty()) {
            text = status;
            color = statusColor == GuiKit.INK_RED ? GuiKit.ON_WOOD_RED : GuiKit.ON_WOOD;
        } else if (target != null && !inMap(mouseX, mouseY)) {
            int cb = cellBlocks();
            text = updateMode
                    ? Component.translatable(ChartText.UPDATE_HINT)
                    : Component.translatable(ChartText.DRAW_BOARD_AREA, (long) selCx * cb, (long) selCz * cb, (long) selW() * cb, (long) selH() * cb);
            color = GuiKit.ON_WOOD;
        } else if (inMap(mouseX, mouseY)) {
            text = Component.translatable(ChartText.POSITION, (int) Math.floor(view.blockX(mouseX, mapL, mapW)),
                    (int) Math.floor(view.blockZ(mouseY, mapT, mapH)));
            color = GuiKit.ON_WOOD;
        } else {
            text = Component.translatable(target != null ? ChartText.DRAW_HINT : ChartText.HINT);
            color = GuiKit.ON_WOOD_DIM;
        }
        if (target != null) {
            GuiKit.text(g, font, text, x, y, markersButton.getX() - 6 - x, color, true);
            return;
        }
        Component count = Component.translatable(ChartText.MARKER_COUNT, ClientChart.markers().size(), ClientChart.settings().maxMarkers());
        int cw = font.width(count);
        g.drawString(font, count, right - cw, y, GuiKit.ON_WOOD_DIM, true);
        GuiKit.text(g, font, text, x, y, right - cw - 8 - x, color, true);
    }

    private void renderEditor(GuiGraphics g, int mouseX, int mouseY) {
        GuiKit.parchment(g, editorX, editorY, EDITOR_W, EDITOR_H);
        GuiKit.ink(g, font, Component.translatable(editId >= 0 ? ChartText.EDIT_MARKER : ChartText.NEW_MARKER)
                .append(Component.literal("  " + editX + ", " + editZ).withColor(GuiKit.INK_DIM)), editorX + 6, editorY + 4, EDITOR_W - 12, GuiKit.INK);
        MarkerIcon[] icons = MarkerIcon.values();
        for (int i = 0; i < icons.length; i++) {
            int ix = iconX(i);
            if (icons[i] == editIcon) blitPart(g, ChartSheet.RING, ix - 1, iconY() - 1);
            blitPart(g, ChartSheet.marker(icons[i]), ix, iconY());
        }
        GuiKit.field(g, nameField);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (!editing) return;
        MarkerIcon[] icons = MarkerIcon.values();
        for (int i = 0; i < icons.length; i++) {
            if (mouseX >= iconX(i) - 1 && mouseX < iconX(i) + 12 && mouseY >= iconY() - 1 && mouseY < iconY() + 12) {
                g.renderTooltip(font, Component.translatable(ChartText.icon(icons[i])), mouseX, mouseY);
            }
        }
    }
}
