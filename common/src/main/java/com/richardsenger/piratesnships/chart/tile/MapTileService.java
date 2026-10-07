package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.chart.ChartConfig;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.ChartService;
import com.richardsenger.piratesnships.chart.data.BoardMarker;
import com.richardsenger.piratesnships.chart.data.BoardSlice;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.net.ClearBoardPayload;
import com.richardsenger.piratesnships.chart.net.DrawTilePayload;
import com.richardsenger.piratesnships.chart.net.TileTarget;
import com.richardsenger.piratesnships.chart.render.CellLookup;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server side of map tiles and boards (work packages MAP2, MAP3). Using a tile with a chart opens the chart in draw
 * mode for the whole board the tile belongs to ({@link #openDrawMode}; tiles side by side in a full rectangle,
 * {@link BoardRules}); the screen answers with a {@link DrawTilePayload} or a {@link ClearBoardPayload}.
 *
 * <p>A <b>draw</b> ({@link #draw}) rasters the chosen area at the chosen zoom from the player's <b>own</b> chart into
 * every tile's slice ({@link MapTileRaster#draw(CellLookup, int, int, int, int)}: unknown stays parchment), stamps the
 * player's markers if asked, gives the board a new id and replaces what the tiles showed. An <b>update</b> of an
 * intact board re-rasters the board's own area and zoom and merges per pixel and marker ({@link BoardMerge}). A
 * <b>clear</b> ({@link #clear}) blanks every tile. Draws and updates cost ink ({@link InkCost}); every rule is checked
 * again here ({@link MapTileRules}), and a refusal is told on the action bar. Every changed tile then reaches the
 * clients in range through its own update tag (each tile syncs only its slice).
 */
public final class MapTileService {

    public static final String MSG = ChartBackend.MSG + "tile.";
    public static final String DRAWN = MSG + "drawn";
    public static final String UPDATED = MSG + "updated";
    public static final String CLEARED = MSG + "cleared";
    /** NO_INK with the amounts: "You need N ink (you have M)". */
    public static final String NO_INK_AMOUNT = MSG + "no_ink_amount";
    /** Game ticks per day: drawings record the overworld's day time / 24000, the day number players see. */
    public static final long TICKS_PER_DAY = 24000L;

    private MapTileService() {
    }

    /** What happened to a draw, update or clear request; {@code inkDue} is what was (or would have been) paid. */
    public record Outcome(MapTileRules.Refusal refusal, @Nullable MapTileDrawing drawing, List<BlockPos> tiles, boolean update,
                          int inkDue, InkCost.Payment paid) {
        public boolean ok() {
            return refusal == MapTileRules.Refusal.NONE;
        }

        static Outcome refused(MapTileRules.Refusal refusal) {
            return new Outcome(refusal, null, List.of(), false, 0, InkCost.Payment.NONE);
        }
    }

    /** A board in the world: its tiles in {@link BoardRules.Board#members()} order, and what they show. */
    private record WorldBoard(BoardRules.Board board, List<MapTileBlockEntity> tiles, BoardRules.Existing existing) {
    }

    // --- opening ---------------------------------------------------------------------------------------------------

    /** Opens the chart of {@code player} in draw mode for the board of the tile at {@code pos}; false (and a message) when refused. */
    public static boolean openDrawMode(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        MapTileBlockEntity tile = tileAt(level, pos);
        MapTileRules.Refusal refusal = MapTileRules.check(request(player, pos, tile, false));
        if (refusal == MapTileRules.Refusal.NONE) {
            WorldBoard wb = board(level, pos);
            if (!wb.board().ok()) {
                refusal = boardRefusal(wb.board());
            } else {
                // with redraw off, a drawn board can still be updated; leftovers of a broken board cannot be replaced
                boolean permanent = wb.existing().state() == BoardRules.State.BROKEN && !ChartConfig.REDRAW_ALLOWED.get();
                if (permanent) refusal = MapTileRules.Refusal.PERMANENT;
                else return ChartBackend.open(player, Optional.of(target(player, pos, wb)));
            }
        }
        tell(player, refusal);
        return false;
    }

    private static TileTarget target(ServerPlayer player, BlockPos pos, WorldBoard wb) {
        BoardRules.Board b = wb.board();
        BoardRules.Area area = wb.existing().area();
        int cb = cellBlocks(ChartService.data(player));
        int size = area != null ? area.size() : ChartConfig.TILE_CELLS.get();
        int zoom = area != null ? area.zoom() : 1;
        int minCx = area != null ? area.minCx() : MapTileRaster.centredOn(player.getX(), cb, size * b.columns());
        int minCz = area != null ? area.minCz() : MapTileRaster.centredOn(player.getZ(), cb, size * b.rows());
        int inkPerTile = InkCost.due(ChartConfig.INK_COST_ENABLED.get(), player.isCreative(), 1, ChartConfig.INK_PER_TILE.get());
        int kraken = InkCost.krakenWorth(ChartConfig.KRAKEN_INK_TILE_VALUE.get(), ChartConfig.INK_PER_TILE.get());
        return new TileTarget(pos.immutable(), size, wb.existing().state(), minCx, minCz, b.columns(), b.rows(), zoom,
                ChartConfig.MAX_ZOOM.get(), ChartConfig.REDRAW_ALLOWED.get(), inkPerTile, kraken);
    }

    // --- drawing ---------------------------------------------------------------------------------------------------

    /** The {@link DrawTilePayload} handler: draws or updates, or tells the player why not. */
    public static Outcome handleDraw(ServerPlayer player, DrawTilePayload request) {
        Outcome outcome = draw(player, request);
        if (outcome.ok()) {
            player.displayClientMessage(Component.translatable(outcome.update() ? UPDATED : DRAWN), true);
            player.serverLevel().playSound(null, request.tilePos(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.8f, 1.0f);
        } else if (outcome.refusal() == MapTileRules.Refusal.NO_INK) {
            player.displayClientMessage(Component.translatable(NO_INK_AMOUNT, outcome.inkDue(), inkHeld(player)), true);
        } else {
            tell(player, outcome.refusal());
        }
        return outcome;
    }

    /** Checks the request and, if allowed, draws or updates the whole board and takes the ink. */
    public static Outcome draw(ServerPlayer player, DrawTilePayload request) {
        BlockPos pos = request.tilePos();
        ServerLevel level = player.serverLevel();
        MapTileBlockEntity tile = level.isLoaded(pos) ? tileAt(level, pos) : null;
        MapTileRules.Refusal refusal = MapTileRules.check(request(player, pos, tile, false));
        if (refusal != MapTileRules.Refusal.NONE) return Outcome.refused(refusal);
        WorldBoard wb = board(level, pos);
        if (!wb.board().ok()) return Outcome.refused(boardRefusal(wb.board()));
        BoardRules.Board b = wb.board();
        BoardRules.Existing existing = wb.existing();
        boolean update = request.update() && existing.intact();
        // a draw over anything already drawn is a redraw
        refusal = MapTileRules.check(request(player, pos, tile, !update && existing.anyDrawing()));
        if (refusal != MapTileRules.Refusal.NONE) return Outcome.refused(refusal);

        ChartData data = ChartService.data(player);
        int cb = cellBlocks(data);
        int zoom = Math.max(1, Math.min(ChartConfig.MAX_ZOOM.get(), request.zoom()));
        BoardRules.Area area = update ? existing.area()
                : new BoardRules.Area(request.minCx(), request.minCz(), ChartConfig.TILE_CELLS.get(), zoom, cb);
        int size = area.size();
        boolean inWorld = MapTileRaster.inWorld(area.minCx(), area.minCz(), BoardRules.cells(b.columns(), size, area.zoom()),
                BoardRules.cells(b.rows(), size, area.zoom()), area.cellBlocks(), MarkerRules.MAX_COORDINATE);
        refusal = MapTileRules.check(request(player, pos, tile, false).withArea(inWorld, true));
        if (refusal != MapTileRules.Refusal.NONE) return Outcome.refused(refusal);
        // a chart of another cell size (the server changed cell_blocks) cannot add to this board
        if (area.cellBlocks() != cb) return Outcome.refused(MapTileRules.Refusal.UNCHARTED);

        CellLookup cells = CellLookup.cached(data);
        List<BoardRules.Member> members = b.members();
        List<byte[]> fresh = new ArrayList<>(members.size());
        boolean anyKnown = false;
        for (BoardRules.Member m : members) {
            byte[] px = MapTileRaster.draw(cells, area.sliceMinCx(m.column()), area.sliceMinCz(m.row()), size, area.zoom());
            fresh.add(px);
            if (!anyKnown) anyKnown = anyNonZero(px);
        }
        List<BoardMarker> added = request.includeMarkers()
                ? MapTileRaster.boardMarkers(data.markers(), area.minCx(), area.minCz(), size * b.columns(), size * b.rows(), area.zoom(), cb,
                MapTileDrawing.MAX_MARKERS)
                : List.of();

        List<byte[]> pixels;
        List<BoardMarker> markers;
        int tilesToPay;
        if (update) {
            List<BoardMarker> old = new ArrayList<>();
            pixels = new ArrayList<>(members.size());
            int changed = 0;
            for (int i = 0; i < members.size(); i++) {
                MapTileDrawing d = wb.tiles().get(i).drawing();
                BoardRules.Member m = members.get(i);
                old.addAll(BoardMerge.owned(d.markers(), m.column(), m.row(), size));
                byte[] before = d.pixels();
                byte[] merged = BoardMerge.mergePixels(before, fresh.get(i));
                if (!Arrays.equals(before, merged)) changed++;
                pixels.add(merged);
            }
            markers = BoardMerge.union(old, added, MapTileDrawing.MAX_MARKERS);
            boolean anything = changed > 0 || !BoardMerge.sameMarkers(old, markers);
            if (!anything) return Outcome.refused(MapTileRules.Refusal.NOTHING_NEW);
            tilesToPay = InkCost.updateTiles(changed, true);
        } else {
            if (!anyKnown) return Outcome.refused(MapTileRules.Refusal.UNCHARTED);
            pixels = fresh;
            markers = added;
            tilesToPay = InkCost.drawTiles(members.size());
        }

        int due = InkCost.due(ChartConfig.INK_COST_ENABLED.get(), player.isCreative(), tilesToPay, ChartConfig.INK_PER_TILE.get());
        int krakenWorth = InkCost.krakenWorth(ChartConfig.KRAKEN_INK_TILE_VALUE.get(), ChartConfig.INK_PER_TILE.get());
        int[] held = countInk(player.getInventory());
        InkCost.Payment payment = InkCost.pay(due, held[0], held[1], krakenWorth);
        if (payment == null) return new Outcome(MapTileRules.Refusal.NO_INK, null, List.of(), update, due, InkCost.Payment.NONE);

        UUID id = update && existing.id() != null ? existing.id() : UUID.randomUUID();
        long day = player.server.overworld().getDayTime() / TICKS_PER_DAY;
        String name = player.getGameProfile().getName();
        int margin = MapTileRaster.markerMargin(size);
        MapTileDrawing used = null;
        List<BlockPos> tiles = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            BoardRules.Member m = members.get(i);
            MapTileDrawing d = new MapTileDrawing(size, pixels.get(i), area.sliceMinCx(m.column()), area.sliceMinCz(m.row()), area.cellBlocks(),
                    name, day, BoardMerge.slice(markers, m.column(), m.row(), size, margin, MapTileDrawing.MAX_MARKERS), area.zoom(),
                    Optional.of(new BoardSlice(id, m.column(), m.row(), b.columns(), b.rows())), update);
            MapTileBlockEntity be = wb.tiles().get(i);
            be.setDrawing(d);
            tiles.add(be.getBlockPos());
            if (be.getBlockPos().equals(pos)) used = d;
        }
        take(player.getInventory(), payment);
        return new Outcome(MapTileRules.Refusal.NONE, used, List.copyOf(tiles), update, due, payment);
    }

    // --- clearing --------------------------------------------------------------------------------------------------

    /** The {@link ClearBoardPayload} handler. */
    public static Outcome handleClear(ServerPlayer player, ClearBoardPayload request) {
        Outcome outcome = clear(player, request.tilePos());
        if (outcome.ok()) {
            player.displayClientMessage(Component.translatable(CLEARED), true);
            player.serverLevel().playSound(null, request.tilePos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 0.8f);
        } else {
            tell(player, outcome.refusal());
        }
        return outcome;
    }

    /** Blanks every tile of the board at {@code pos}: the rules of a redraw (reach, chart, {@code redraw_allowed}), no ink. */
    public static Outcome clear(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        MapTileBlockEntity tile = level.isLoaded(pos) ? tileAt(level, pos) : null;
        MapTileRules.Refusal refusal = MapTileRules.check(request(player, pos, tile, false));
        if (refusal != MapTileRules.Refusal.NONE) return Outcome.refused(refusal);
        WorldBoard wb = board(level, pos);
        if (!wb.board().ok()) return Outcome.refused(boardRefusal(wb.board()));
        if (!wb.existing().anyDrawing()) return Outcome.refused(MapTileRules.Refusal.BLANK);
        refusal = MapTileRules.check(request(player, pos, tile, true));
        if (refusal != MapTileRules.Refusal.NONE) return Outcome.refused(refusal);
        List<BlockPos> tiles = new ArrayList<>();
        for (MapTileBlockEntity be : wb.tiles()) {
            if (be.drawing() != null) be.setDrawing(null);
            tiles.add(be.getBlockPos());
        }
        return new Outcome(MapTileRules.Refusal.NONE, null, List.copyOf(tiles), false, 0, InkCost.Payment.NONE);
    }

    // --- the board in the world ------------------------------------------------------------------------------------

    /** The board the tile at {@code pos} belongs to (a map tile must be there), with its block entities and what they show. */
    private static WorldBoard board(Level level, BlockPos pos) {
        BoardRules.Board b = BoardRules.find((x, y, z) -> orientation(level, x, y, z), pos.getX(), pos.getY(), pos.getZ(), ChartConfig.MAX_BOARD_SIDE.get());
        if (!b.ok()) return new WorldBoard(b, List.of(), new BoardRules.Existing(BoardRules.State.BLANK, null, null));
        List<MapTileBlockEntity> tiles = new ArrayList<>(b.members().size());
        List<BoardRules.Slot> slots = new ArrayList<>(b.members().size());
        for (BoardRules.Member m : b.members()) {
            MapTileBlockEntity be = tileAt(level, new BlockPos(m.x(), m.y(), m.z()));
            if (be == null) {
                // a tile block without its block entity: not usable as a board
                return new WorldBoard(new BoardRules.Board(BoardRules.Refusal.NOT_RECTANGLE, List.of(), 0, 0), List.of(),
                        new BoardRules.Existing(BoardRules.State.BLANK, null, null));
            }
            tiles.add(be);
            slots.add(new BoardRules.Slot(m.column(), m.row(), be.drawing()));
        }
        return new WorldBoard(b, List.copyOf(tiles), BoardRules.existing(slots, b.columns(), b.rows()));
    }

    /** The board of the tile at {@code pos}, as {@link BoardRules} finds it in this level (for tests and the screen). */
    public static BoardRules.Board findBoard(Level level, BlockPos pos) {
        return BoardRules.find((x, y, z) -> orientation(level, x, y, z), pos.getX(), pos.getY(), pos.getZ(), ChartConfig.MAX_BOARD_SIDE.get());
    }

    private static @Nullable BoardRules.Orientation orientation(Level level, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (!level.isLoaded(p)) return null;
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof MapTileBlock)) return null;
        AttachFace face = s.getValue(MapTileBlock.FACE);
        if (face == AttachFace.CEILING) return null;
        return new BoardRules.Orientation(face == AttachFace.WALL, s.getValue(MapTileBlock.FACING));
    }

    private static MapTileRules.Refusal boardRefusal(BoardRules.Board b) {
        return switch (b.refusal()) {
            case TOO_BIG -> MapTileRules.Refusal.TOO_BIG;
            case NOT_RECTANGLE -> MapTileRules.Refusal.NOT_RECTANGLE;
            case NONE -> MapTileRules.Refusal.NONE;
        };
    }

    private static boolean anyNonZero(byte[] px) {
        for (byte b : px) {
            if (b != 0) return true;
        }
        return false;
    }

    // --- ink -------------------------------------------------------------------------------------------------------

    private static boolean isKraken(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ChartContent.KRAKEN_INK);
    }

    /** {plain ink items, kraken inks} in {@code inv} (items of {@code #pirates_n_ships:chart_ink}). */
    public static int[] countInk(Inventory inv) {
        int ink = 0;
        int kraken = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(ChartContent.CHART_INK)) continue;
            if (isKraken(s)) kraken += s.getCount();
            else ink += s.getCount();
        }
        return new int[]{ink, kraken};
    }

    /** All the ink the player holds, in ink. */
    public static long inkHeld(ServerPlayer player) {
        int[] held = countInk(player.getInventory());
        return InkCost.held(held[0], held[1], InkCost.krakenWorth(ChartConfig.KRAKEN_INK_TILE_VALUE.get(), ChartConfig.INK_PER_TILE.get()));
    }

    private static void take(Inventory inv, InkCost.Payment payment) {
        int ink = payment.ink();
        int kraken = payment.kraken();
        for (int i = 0; i < inv.getContainerSize() && (ink > 0 || kraken > 0); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(ChartContent.CHART_INK)) continue;
            if (isKraken(s)) {
                int n = Math.min(kraken, s.getCount());
                s.shrink(n);
                kraken -= n;
            } else {
                int n = Math.min(ink, s.getCount());
                s.shrink(n);
                ink -= n;
            }
        }
        inv.setChanged();
    }

    // --- rules -----------------------------------------------------------------------------------------------------

    private static MapTileRules.Request request(ServerPlayer player, BlockPos pos, @Nullable MapTileBlockEntity tile, boolean redraw) {
        boolean holds = player.getMainHandItem().is(ChartContent.CHART.get()) || player.getOffhandItem().is(ChartContent.CHART.get());
        return new MapTileRules.Request(ChartConfig.ENABLED.get(), ChartConfig.TILES_ENABLED.get(), tile != null,
                tile != null && inReach(player, pos), ChartConfig.REQUIRE_CHART_ITEM.get(), holds,
                redraw, ChartConfig.REDRAW_ALLOWED.get(), true, true);
    }

    /** Whether the player stands within {@code chart.tiles.reach} of the tile (in the world, also for a tile on a ship). */
    public static boolean inReach(ServerPlayer player, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        ShipBody ship = SableShips.containing(player.level(), pos);
        if (ship != null) centre = ship.toWorld(centre);
        double reach = ChartConfig.TILE_REACH.get();
        return player.position().distanceToSqr(centre) <= reach * reach;
    }

    private static @Nullable MapTileBlockEntity tileAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MapTileBlockEntity tile ? tile : null;
    }

    /** The cell size of the player's chart, or the server's while nothing is charted. */
    private static int cellBlocks(ChartData data) {
        return data.cellBlocks() > 0 ? data.cellBlocks() : ChartConfig.CELL_BLOCKS.get();
    }

    private static void tell(ServerPlayer player, MapTileRules.Refusal refusal) {
        player.displayClientMessage(Component.translatable(MSG + refusal.key()), true);
    }
}
