package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.net.ChartOpenPayload;
import com.richardsenger.piratesnships.chart.net.DrawTilePayload;
import com.richardsenger.piratesnships.chart.net.TileTarget;
import com.richardsenger.piratesnships.chart.render.CellLookup;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import com.richardsenger.piratesnships.chart.tile.BoardRules;
import com.richardsenger.piratesnships.chart.tile.MapTileBlock;
import com.richardsenger.piratesnships.chart.tile.MapTileBlockEntity;
import com.richardsenger.piratesnships.chart.tile.MapTileRules;
import com.richardsenger.piratesnships.chart.tile.MapTileService;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Map boards (work package MAP3) in a real world: tiles side by side form one board that a draw fills slice by slice
 * (a 2x2 floor board, a 1x3 wall board); a set that is no rectangle is refused; a second player with a chart that
 * knows more updates the board, which fills only what was unknown and keeps the rest, markers of both included; a
 * clear blanks every tile; ink is taken per tile, refused without, free in creative and with the cost switched off;
 * a broken tile carries its slice and restores it when placed back; zoom 4 draws a four times larger area.
 */
public final class MapBoardGameTests {

    private MapBoardGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MapBoardGameTests.class);
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    /** Floor tiles facing north on stone: column = x - x0, row = z - z0 (relative positions). */
    private static List<BlockPos> floorBoard(GameTestHelper helper, int x0, int z0, int columns, int rows) {
        List<BlockPos> tiles = new ArrayList<>();
        BlockState tile = ChartContent.MAP_TILE.get().defaultBlockState().setValue(MapTileBlock.FACE, AttachFace.FLOOR)
                .setValue(MapTileBlock.FACING, Direction.NORTH);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                helper.setBlock(new BlockPos(x0 + c, 1, z0 + r), Blocks.STONE);
                helper.setBlock(new BlockPos(x0 + c, 2, z0 + r), tile);
                tiles.add(helper.absolutePos(new BlockPos(x0 + c, 2, z0 + r)));
            }
        }
        return tiles;
    }

    private static MapTileBlockEntity be(GameTestHelper helper, BlockPos abs) {
        if (!(helper.getLevel().getBlockEntity(abs) instanceof MapTileBlockEntity be)) throw new AssertionError("no map tile at " + abs);
        return be;
    }

    /** A chart that knows the cells {@code [minCx, minCx + w) x [minCz, minCz + h)}: land west of {@code coastCx}, deep water east of it. */
    private static ChartData chart(int cb, int minCx, int minCz, int w, int h, int coastCx) {
        SampleGrid grid = SampleGrid.empty(minCx, minCz, w, h);
        for (int cz = minCz; cz < minCz + h; cz++) {
            for (int cx = minCx; cx < minCx + w; cx++) grid.set(cx, cz, cx < coastCx ? CellClass.LAND : CellClass.DEEP_WATER);
        }
        return ChartMerge.merge(ChartData.EMPTY.resetCells(cb), grid, 0, ChartConfig.MAX_CELLS.get()).data();
    }

    private static ChartData marked(ChartData data, int cb, int cx, int cz, MarkerIcon icon, String name) {
        return MarkerRules.add(data, cx * cb + 1, cz * cb + 1, icon, name, 64).data();
    }

    private static ServerPlayer drawer(GameTestHelper helper, String name, BlockPos near, ChartData data, int ink) {
        ServerPlayer p = ChartGameTests.player(helper, name);
        p.setPos(Vec3.atCenterOf(near).add(0.5, 0, 0.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChartContent.CHART.get()));
        if (ink > 0) p.getInventory().setItem(9, new ItemStack(Items.INK_SAC, ink));
        ChartService.setData(p, data);
        return p;
    }

    private static int inkSacs(ServerPlayer p) {
        return MapTileService.countInk(p.getInventory())[0];
    }

    private static int cb() {
        return ChartConfig.CELL_BLOCKS.get();
    }

    private static int size() {
        return ChartConfig.TILE_CELLS.get();
    }

    /** The board's first cell: one board side west and north of the first tile, so the tiles sit inside the area. */
    private static int base(int block) {
        return Math.floorDiv(block, cb()) - size();
    }

    /** Every tile holds its slice of one board: same id and size, its own column and row, the area offset by its place. */
    private static UUID assertBoard(GameTestHelper helper, List<BlockPos> tiles, int columns, int rows, int minCx, int minCz, int zoom,
                                    CellLookup chart) {
        UUID id = null;
        Set<String> places = new HashSet<>();
        int i = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                MapTileDrawing d = be(helper, tiles.get(i++)).drawing();
                helper.assertTrue(d != null, "tile " + c + "," + r + " is drawn");
                var slice = d.board().orElseThrow(() -> new AssertionError("tile " + " has no board record"));
                if (id == null) id = slice.board();
                helper.assertValueEqual(slice.board(), id, "one board id");
                helper.assertValueEqual(slice.columns(), columns, "columns");
                helper.assertValueEqual(slice.rows(), rows, "rows");
                helper.assertTrue(places.add(slice.column() + "," + slice.row()), "each place once");
                helper.assertValueEqual(slice.column(), c, "the column of tile " + c + "," + r);
                helper.assertValueEqual(slice.row(), r, "the row of tile " + c + "," + r);
                helper.assertValueEqual(d.zoom(), zoom, "zoom");
                helper.assertValueEqual(d.minCx(), BoardRules.sliceMin(minCx, c, size(), zoom), "slice x of " + c + "," + r);
                helper.assertValueEqual(d.minCz(), BoardRules.sliceMin(minCz, r, size(), zoom), "slice z of " + c + "," + r);
                if (chart != null) {
                    helper.assertTrue(Arrays.equals(d.pixels(), MapTileRaster.draw(chart, d.minCx(), d.minCz(), size(), zoom)),
                            "slice " + c + "," + r + " is the chart's raster");
                }
            }
        }
        return id;
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) ChartBackend.stopRecording(p.getUUID());
        helper.succeed();
    }

    // --- drawing --------------------------------------------------------------------------------------------------

    /** A 2x2 floor board: one draw fills all four tiles with consistent slices and one id, and takes four ink. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aFloorBoardDrawsAcrossAllTiles(GameTestHelper helper) {
        List<BlockPos> tiles = floorBoard(helper, 2, 2, 2, 2);
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        ChartData data = chart(cb(), bx + 20, bz + 20, 2 * size() - 40, 2 * size() - 40, bx + size());
        ServerPlayer p = drawer(helper, "board_floor", tiles.get(0), data, 10);
        // used from the bottom-right tile: the whole board is drawn
        MapTileService.Outcome out = MapTileService.draw(p, new DrawTilePayload(tiles.get(3), bx, bz, 1, false, false));
        helper.assertTrue(out.ok(), "drawn, got " + out.refusal());
        helper.assertValueEqual(out.tiles().size(), 4, "four tiles");
        assertBoard(helper, tiles, 2, 2, bx, bz, 1, CellLookup.of(data));
        helper.assertValueEqual(inkSacs(p), 6, "one ink per tile");
        helper.assertFalse(be(helper, tiles.get(0)).drawing().updated(), "a first draw");
        finish(helper, p);
    }

    /** A wall board of one column and three rows: the top tile is row 0, slices go south down the wall. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aWallBoardDrawsTopDown(GameTestHelper helper) {
        BlockState tile = ChartContent.MAP_TILE.get().defaultBlockState().setValue(MapTileBlock.FACE, AttachFace.WALL)
                .setValue(MapTileBlock.FACING, Direction.NORTH);
        List<BlockPos> tiles = new ArrayList<>();
        for (int y = 4; y >= 2; y--) {
            helper.setBlock(new BlockPos(4, y, 5), Blocks.STONE);
            helper.setBlock(new BlockPos(4, y, 4), tile);
            tiles.add(helper.absolutePos(new BlockPos(4, y, 4)));
        }
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        ChartData data = chart(cb(), bx, bz, size(), 3 * size(), bx + 50);
        ServerPlayer p = drawer(helper, "board_wall", tiles.get(2).north(), data, 10);
        BoardRules.Board board = MapTileService.findBoard(helper.getLevel(), tiles.get(1));
        helper.assertTrue(board.ok(), "a 1x3 board");
        helper.assertValueEqual(board.columns(), 1, "one column");
        helper.assertValueEqual(board.rows(), 3, "three rows");
        MapTileService.Outcome out = MapTileService.draw(p, new DrawTilePayload(tiles.get(2), bx, bz, 1, false, false));
        helper.assertTrue(out.ok(), "drawn, got " + out.refusal());
        assertBoard(helper, tiles, 1, 3, bx, bz, 1, CellLookup.of(data));
        helper.assertValueEqual(inkSacs(p), 7, "three ink");
        finish(helper, p);
    }

    /** Three tiles in an L are no board: drawing and opening are refused, nothing is drawn, no ink is taken. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void anLShapedSetIsRefused(GameTestHelper helper) {
        List<BlockPos> square = floorBoard(helper, 2, 2, 2, 2);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        int bx = base(square.get(0).getX());
        int bz = base(square.get(0).getZ());
        ServerPlayer p = drawer(helper, "board_l", square.get(0), chart(cb(), bx, bz, size(), size(), bx + 10), 10);
        MapTileService.Outcome out = MapTileService.draw(p, new DrawTilePayload(square.get(0), bx, bz, 1, true, false));
        helper.assertValueEqual(out.refusal(), MapTileRules.Refusal.NOT_RECTANGLE, "refused");
        for (int i = 0; i < 3; i++) helper.assertTrue(be(helper, square.get(i)).drawing() == null, "tile " + i + " stays blank");
        helper.assertValueEqual(inkSacs(p), 10, "no ink taken");
        List<CustomPacketPayload> sent = ChartBackend.record(p.getUUID());
        helper.assertFalse(MapTileService.openDrawMode(p, square.get(1)), "the draw mode does not open");
        helper.assertTrue(sent.isEmpty(), "no chart opened");
        finish(helper, p);
    }

    // --- updating -------------------------------------------------------------------------------------------------

    /**
     * Anne draws what she knows (the west part, a marker); Mary, who has charted further east, updates the board: the
     * pixels only Mary knows appear, those only Anne knew stay as Anne drew them, unknown stays parchment; both
     * markers end up stamped (Mary's at the seam on both tiles); the board keeps its id and names Mary.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void anUpdateByAnotherPlayerAddsWhatTheyKnow(GameTestHelper helper) {
        List<BlockPos> tiles = floorBoard(helper, 2, 2, 2, 2);
        int s = size();
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        int cb = cb();
        ChartData anneChart = marked(chart(cb, bx + 20, bz + 20, 100, 2 * s - 20, bx + 70), cb, bx + 40, bz + 60, MarkerIcon.SKULL, "Anne's rock");
        ChartData maryChart = marked(chart(cb, bx + 100, bz + 20, 100, 2 * s - 20, bx + 70), cb, bx + s - 1, bz + 60, MarkerIcon.ANCHOR, "Seam bay");
        ServerPlayer anne = drawer(helper, "board_anne", tiles.get(0), anneChart, 10);
        ServerPlayer mary = drawer(helper, "board_mary", tiles.get(1), maryChart, 10);

        helper.assertTrue(MapTileService.draw(anne, new DrawTilePayload(tiles.get(0), bx, bz, 1, true, false)).ok(), "Anne draws");
        UUID id = assertBoard(helper, tiles, 2, 2, bx, bz, 1, CellLookup.of(anneChart));
        MapTileDrawing anneNw = be(helper, tiles.get(0)).drawing();
        MapTileDrawing anneNe = be(helper, tiles.get(1)).drawing();
        helper.assertValueEqual(anneNe.pixel(150 - s, 50), 0, "east of Anne's chart: parchment");

        // the frame of the update is the board's own: the area in the payload is ignored
        MapTileService.Outcome out = MapTileService.draw(mary, new DrawTilePayload(tiles.get(1), bx + 999, bz - 999, 3, true, true));
        helper.assertTrue(out.ok(), "Mary updates, got " + out.refusal());
        helper.assertTrue(out.update(), "an update");
        helper.assertValueEqual(assertBoard(helper, tiles, 2, 2, bx, bz, 1, null), id, "the same board");
        MapTileDrawing nw = be(helper, tiles.get(0)).drawing();
        MapTileDrawing ne = be(helper, tiles.get(1)).drawing();
        MapTileDrawing sw = be(helper, tiles.get(2)).drawing();
        MapTileDrawing se = be(helper, tiles.get(3)).drawing();
        helper.assertValueEqual(nw.pixel(50, 50), anneNw.pixel(50, 50), "only Anne knew it: her pixel stays");
        helper.assertTrue(MapTileRaster.known(nw.pixel(50, 50)), "and it is known");
        helper.assertTrue(MapTileRaster.known(ne.pixel(150 - s, 50)), "only Mary knew it: now drawn");
        helper.assertValueEqual(se.pixel(240 - s, 240 - s), 0, "nobody knew it: parchment");
        helper.assertTrue(MapTileRaster.known(sw.pixel(110, 200 - s)), "Mary's knowledge reaches the south-west tile");
        // every pixel Anne had drawn is still drawn
        for (int i = 0; i < 4; i++) {
            MapTileDrawing after = be(helper, tiles.get(i)).drawing();
            helper.assertValueEqual(after.drawer(), "board_mary", "the last updater");
            helper.assertTrue(after.updated(), "marked as an update");
        }
        byte[] before = anneNw.pixels();
        byte[] after = nw.pixels();
        for (int i = 0; i < before.length; i++) {
            if (before[i] != 0 && after[i] == 0) throw new AssertionError("pixel " + i + " was lost");
        }
        // markers: Anne's on the north-west tile, Mary's at the seam on both northern tiles
        helper.assertTrue(nw.markers().stream().anyMatch(m -> m.name().equals("Anne's rock") && m.px() == 40 && m.py() == 60), "Anne's marker stays");
        helper.assertTrue(nw.markers().stream().anyMatch(m -> m.name().equals("Seam bay") && m.px() == s - 1), "Mary's marker on its tile");
        TileMarker overSeam = ne.markers().stream().filter(m -> m.name().equals("Seam bay")).findFirst()
                .orElseThrow(() -> new AssertionError("Mary's seam marker is stamped on the eastern tile too"));
        helper.assertValueEqual(overSeam.px(), -1, "just beyond the eastern tile's west edge");
        helper.assertValueEqual(inkSacs(mary), 6, "Mary pays for the four tiles that changed");
        helper.assertValueEqual(inkSacs(anne), 6, "Anne paid for her draw only");

        // the same update again adds nothing and costs nothing
        helper.assertValueEqual(MapTileService.draw(mary, DrawTilePayload.update(tiles.get(1), true)).refusal(), MapTileRules.Refusal.NOTHING_NEW,
                "nothing new");
        helper.assertValueEqual(inkSacs(mary), 6, "no ink for nothing");
        finish(helper, anne, mary);
    }

    // --- clearing -------------------------------------------------------------------------------------------------

    /** Clearing blanks all four tiles, costs no ink, and a blank board cannot be cleared again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void clearingBlanksEveryTile(GameTestHelper helper) {
        List<BlockPos> tiles = floorBoard(helper, 2, 2, 2, 2);
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        ServerPlayer p = drawer(helper, "board_clear", tiles.get(0), chart(cb(), bx, bz, 2 * size(), 2 * size(), bx + 100), 10);
        helper.assertTrue(MapTileService.draw(p, new DrawTilePayload(tiles.get(0), bx, bz, 1, false, false)).ok(), "drawn");
        MapTileService.Outcome cleared = MapTileService.clear(p, tiles.get(3));
        helper.assertTrue(cleared.ok(), "cleared, got " + cleared.refusal());
        for (BlockPos t : tiles) helper.assertTrue(be(helper, t).drawing() == null, "blank again");
        helper.assertValueEqual(inkSacs(p), 6, "clearing is free");
        helper.assertValueEqual(MapTileService.clear(p, tiles.get(0)).refusal(), MapTileRules.Refusal.BLANK, "nothing left to clear");
        finish(helper, p);
    }

    // --- ink ------------------------------------------------------------------------------------------------------

    /** Three sacs do not pay for four tiles; a kraken ink does (worth eight); a creative player draws for free. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void inkIsTakenPerTileAndRefusedWithout(GameTestHelper helper) {
        List<BlockPos> tiles = floorBoard(helper, 2, 2, 2, 2);
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        ChartData data = chart(cb(), bx, bz, 2 * size(), 2 * size(), bx + 100);
        ServerPlayer poor = drawer(helper, "board_poor", tiles.get(0), data, 3);
        MapTileService.Outcome refused = MapTileService.draw(poor, new DrawTilePayload(tiles.get(0), bx, bz, 1, false, false));
        helper.assertValueEqual(refused.refusal(), MapTileRules.Refusal.NO_INK, "not enough ink");
        helper.assertValueEqual(refused.inkDue(), 4, "four ink needed");
        helper.assertValueEqual(inkSacs(poor), 3, "nothing taken");
        for (BlockPos t : tiles) helper.assertTrue(be(helper, t).drawing() == null, "nothing drawn");

        Item krakenInk = BuiltInRegistries.ITEM.get(ChartContent.KRAKEN_INK);
        helper.assertTrue(new ItemStack(krakenInk).is(ChartContent.CHART_INK), "kraken ink is chart ink");
        helper.assertTrue(new ItemStack(Items.GLOW_INK_SAC).is(ChartContent.CHART_INK), "so is a glow ink sac");
        poor.getInventory().setItem(10, new ItemStack(krakenInk, 1));
        MapTileService.Outcome paid = MapTileService.draw(poor, new DrawTilePayload(tiles.get(0), bx, bz, 1, false, false));
        helper.assertTrue(paid.ok(), "a kraken ink pays for the board, got " + paid.refusal());
        helper.assertValueEqual(paid.paid().kraken(), 1, "one kraken ink");
        helper.assertValueEqual(paid.paid().ink(), 0, "and no sacs");
        helper.assertValueEqual(MapTileService.countInk(poor.getInventory())[1], 0, "the kraken ink is used up");
        helper.assertValueEqual(inkSacs(poor), 3, "the sacs stay");

        ServerPlayer creative = drawer(helper, "board_creative", tiles.get(0), data, 0);
        creative.setGameMode(GameType.CREATIVE);
        MapTileService.Outcome free = MapTileService.draw(creative, new DrawTilePayload(tiles.get(0), bx + 5, bz, 1, false, false));
        helper.assertTrue(free.ok(), "creative draws without ink, got " + free.refusal());
        helper.assertValueEqual(free.inkDue(), 0, "free");
        finish(helper, poor, creative);
    }

    /** Own batch: changes config. With the ink cost off nobody pays. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_chart_boards_ink")
    public static void noInkNeededWhenTheCostIsOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, ChartConfig.INK_COST_ENABLED, false);
        List<BlockPos> tiles = floorBoard(helper, 2, 2, 2, 2);
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        ServerPlayer p = drawer(helper, "board_free", tiles.get(0), chart(cb(), bx, bz, 2 * size(), 2 * size(), bx + 100), 0);
        MapTileService.Outcome out = MapTileService.draw(p, new DrawTilePayload(tiles.get(0), bx, bz, 1, false, false));
        helper.assertTrue(out.ok(), "drawn without ink, got " + out.refusal());
        helper.assertValueEqual(out.inkDue(), 0, "nothing due");
        finish(helper, p);
    }

    // --- breaking a tile --------------------------------------------------------------------------------------------

    /**
     * Breaking one tile of a 2x2 board drops it with its slice; the other three keep theirs (now no rectangle); placing
     * the tile back restores the slice and the board is intact again, ready for an update.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aBrokenTileCarriesItsSliceAndRestoresIt(GameTestHelper helper) {
        List<BlockPos> tiles = floorBoard(helper, 2, 2, 2, 2);
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        ServerPlayer p = drawer(helper, "board_mover", tiles.get(0), chart(cb(), bx, bz, 2 * size(), 2 * size(), bx + 100), 10);
        helper.assertTrue(MapTileService.draw(p, new DrawTilePayload(tiles.get(0), bx, bz, 1, false, false)).ok(), "drawn");
        List<MapTileDrawing> before = tiles.stream().map(t -> be(helper, t).drawing()).toList();

        BlockPos ne = tiles.get(1);
        Player survivor = helper.makeMockPlayer(GameType.SURVIVAL);
        List<ItemStack> drops = Block.getDrops(helper.getLevel().getBlockState(ne), helper.getLevel(), ne, be(helper, ne), survivor, ItemStack.EMPTY);
        helper.assertValueEqual(drops.size(), 1, "one item");
        ItemStack item = drops.get(0);
        helper.assertValueEqual(item.get(ChartContent.MAP_TILE_DRAWING.get()), before.get(1), "the item carries the slice");
        helper.getLevel().setBlock(ne, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int i : new int[]{0, 2, 3}) helper.assertValueEqual(be(helper, tiles.get(i)).drawing(), before.get(i), "tile " + i + " keeps its slice");
        helper.assertValueEqual(MapTileService.findBoard(helper.getLevel(), tiles.get(0)).refusal(), BoardRules.Refusal.NOT_RECTANGLE,
                "three tiles are no rectangle");

        // place it back where it was, facing north like the others
        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        placer.setYRot(180f);
        placer.setXRot(90f);
        placer.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockPos floor = ne.below();
        InteractionResult placed = item.useOn(new UseOnContext(placer, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        helper.assertTrue(placed.consumesAction(), "placed back, got " + placed);
        helper.assertValueEqual(helper.getLevel().getBlockState(ne).getValue(MapTileBlock.FACING), Direction.NORTH, "facing north again");
        helper.assertValueEqual(be(helper, ne).drawing(), before.get(1), "the slice is back");
        List<CustomPacketPayload> sent = ChartBackend.record(p.getUUID());
        helper.assertTrue(MapTileService.openDrawMode(p, tiles.get(3)), "the draw mode opens");
        TileTarget target = sent.stream().filter(ChartOpenPayload.class::isInstance).map(ChartOpenPayload.class::cast).findFirst()
                .flatMap(ChartOpenPayload::tile).orElseThrow(() -> new AssertionError("no draw mode"));
        helper.assertValueEqual(target.state(), BoardRules.State.INTACT, "the board is whole again");
        helper.assertValueEqual(target.columns(), 2, "2 columns");
        helper.assertValueEqual(target.minCx(), bx, "the board's area");
        finish(helper, p);
    }

    // --- zoom -----------------------------------------------------------------------------------------------------

    /** Zoom 4 on a single tile covers four times the cells per side, rastered from the downsampled chart. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void zoomFourDrawsAFourTimesLargerArea(GameTestHelper helper) {
        List<BlockPos> tiles = floorBoard(helper, 4, 4, 1, 1);
        int s = size();
        int bx = base(tiles.get(0).getX());
        int bz = base(tiles.get(0).getZ());
        // land on the western quarter of a 4s-wide area, sea elsewhere
        ChartData data = chart(cb(), bx, bz, 4 * s, 4 * s, bx + s);
        ServerPlayer p = drawer(helper, "board_zoom", tiles.get(0), data, 10);
        MapTileService.Outcome out = MapTileService.draw(p, new DrawTilePayload(tiles.get(0), bx, bz, 4, false, false));
        helper.assertTrue(out.ok(), "drawn at zoom 4, got " + out.refusal());
        MapTileDrawing d = be(helper, tiles.get(0)).drawing();
        helper.assertValueEqual(d.zoom(), 4, "zoom 4");
        helper.assertValueEqual(d.cells(), 4 * s, "four times the cells");
        helper.assertValueEqual(d.maxX() - d.minX(), (long) 4 * s * cb(), "four times the blocks");
        helper.assertTrue(Arrays.equals(d.pixels(), MapTileRaster.draw(CellLookup.of(data), bx, bz, s, 4)), "the downsampled raster");
        helper.assertValueEqual(MapTileRaster.kind(d.pixel(s / 8, s / 2)), MapTileRaster.Kind.LAND, "the land quarter");
        helper.assertValueEqual(MapTileRaster.kind(d.pixel(s / 2, s / 2)), MapTileRaster.Kind.WATER, "the sea at the middle of the area");
        helper.assertTrue(MapTileRaster.known(d.pixel(s - 1, s - 1)), "the far corner, 4s cells away, is charted too");
        finish(helper, p);
    }
}
