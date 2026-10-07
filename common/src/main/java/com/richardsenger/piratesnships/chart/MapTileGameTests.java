package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
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
import com.richardsenger.piratesnships.chart.tile.MapTileBlockEntity;
import com.richardsenger.piratesnships.chart.tile.MapTileRules;
import com.richardsenger.piratesnships.chart.tile.MapTileService;
import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The map tile (work package MAP2) in a real world: a server player whose chart is fed directly (a strip of land
 * beside the sea, two markers) draws onto a placed tile with and without markers; the tile holds exactly
 * {@link MapTileRaster#draw} of the player's own chart (land, water and unknown in the right palette kinds) and the
 * markers inside the area; a redraw replaces everything; the item keeps the drawing through breaking and placing;
 * using a tile with a chart opens the chart in draw mode; the config switches refuse what they should.
 */
public final class MapTileGameTests {

    private static final BlockPos FLOOR = new BlockPos(1, 1, 1);
    private static final BlockPos TILE = new BlockPos(1, 2, 1);

    private MapTileGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MapTileGameTests.class);
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    /** The fed chart: cells around the tile, land west of {@link #coastCx}, deep water from it on, in a 40-cell square. */
    private record Fixture(ServerPlayer player, BlockPos tile, int cb, int coastCx, int midCz, int minCx, int minCz, int size,
                           ChartData data, int insideMarker, int outsideMarker) {
        MapTileBlockEntity be(GameTestHelper helper) {
            if (!(helper.getLevel().getBlockEntity(tile) instanceof MapTileBlockEntity be)) throw new AssertionError("no map tile at " + tile);
            return be;
        }

        DrawTilePayload request(boolean markers) {
            return new DrawTilePayload(tile, minCx, minCz, markers);
        }
    }

    private static Fixture fixture(GameTestHelper helper, String name, boolean chartInHand) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(TILE, ChartContent.MAP_TILE.get());
        BlockPos tile = helper.absolutePos(TILE);
        ServerPlayer p = ChartGameTests.player(helper, name);
        p.setPos(Vec3.atCenterOf(tile).add(1, 0, 1));
        if (chartInHand) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChartContent.CHART.get()));
        // MAP3: drawing costs ink
        p.getInventory().setItem(9, new ItemStack(Items.INK_SAC, 64));

        int cb = ChartConfig.CELL_BLOCKS.get();
        int tileCx = Math.floorDiv(tile.getX(), cb);
        int tileCz = Math.floorDiv(tile.getZ(), cb);
        SampleGrid grid = SampleGrid.empty(tileCx - 20, tileCz - 20, 40, 40);
        int coastCx = tileCx;
        for (int cz = tileCz - 20; cz < tileCz + 20; cz++) {
            for (int cx = tileCx - 20; cx < tileCx + 20; cx++) {
                grid.set(cx, cz, cx < coastCx ? CellClass.LAND : CellClass.DEEP_WATER);
            }
        }
        ChartData data = ChartMerge.merge(ChartData.EMPTY.resetCells(cb), grid, 0, ChartConfig.MAX_CELLS.get()).data();
        // one marker on the charted land (inside the area), one far away (outside)
        MarkerRules.Outcome in = MarkerRules.add(data, (coastCx - 5) * cb + 1, tileCz * cb + 2, MarkerIcon.SKULL, "Skull Rock", 64);
        MarkerRules.Outcome out = MarkerRules.add(in.data(), tile.getX() + 50_000, tile.getZ(), MarkerIcon.X, "Far away", 64);
        data = out.data();
        ChartService.setData(p, data);
        int size = ChartConfig.TILE_CELLS.get();
        // the area: the charted square sits in the middle, with unknown parchment around it
        int minCx = MapTileRaster.centredOn(tile.getX(), cb, size);
        int minCz = MapTileRaster.centredOn(tile.getZ(), cb, size);
        return new Fixture(p, tile, cb, coastCx, tileCz, minCx, minCz, size, data, in.markerId(), out.markerId());
    }

    private static int pixelOf(GameTestHelper helper, MapTileDrawing d, int cx, int cz) {
        int x = cx - d.minCx();
        int y = cz - d.minCz();
        helper.assertTrue(x >= 0 && y >= 0 && x < d.size() && y < d.size(), "cell " + cx + "," + cz + " is on the tile");
        return d.pixel(x, y);
    }

    private static void assertDrawingOf(GameTestHelper helper, Fixture f, MapTileDrawing d, int minCx, int minCz) {
        helper.assertTrue(d != null, "the tile holds a drawing");
        helper.assertValueEqual(d.size(), f.size(), "tile size");
        helper.assertValueEqual(d.minCx(), minCx, "area x");
        helper.assertValueEqual(d.minCz(), minCz, "area z");
        helper.assertValueEqual(d.cellBlocks(), f.cb(), "cell size");
        helper.assertValueEqual(d.drawer(), f.player().getGameProfile().getName(), "drawer");
        byte[] expected = MapTileRaster.draw(CellLookup.of(f.data()), minCx, minCz, f.size());
        helper.assertTrue(Arrays.equals(expected, d.pixels()), "the raster is the drawer's own chart");
        // land, coast, water, unknown in the right palette kinds
        int land = pixelOf(helper, d, f.coastCx() - 3, f.midCz());
        helper.assertValueEqual(MapTileRaster.kind(land), MapTileRaster.Kind.LAND, "a land cell is drawn as land");
        int coast = pixelOf(helper, d, f.coastCx() - 1, f.midCz());
        helper.assertTrue(ChartCells.coast(f.data().cell(f.coastCx() - 1, f.midCz())), "the fed chart has a coast");
        helper.assertValueEqual(MapTileRaster.kind(coast), MapTileRaster.Kind.INK, "the coast is inked");
        int water = pixelOf(helper, d, f.coastCx() + 3, f.midCz());
        helper.assertValueEqual(MapTileRaster.kind(water), MapTileRaster.Kind.WATER, "a sea cell is drawn as water");
        int unknown = pixelOf(helper, d, f.coastCx() + 30, f.midCz());
        helper.assertValueEqual(unknown, 0, "an unknown cell stays parchment");
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) ChartBackend.stopRecording(p.getUUID());
        helper.succeed();
    }

    // --- drawing --------------------------------------------------------------------------------------------------

    /** A blank tile drawn without markers, then with them (a redraw), then elsewhere: every draw replaces everything. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void drawsTheOwnChartWithAndWithoutMarkers(GameTestHelper helper) {
        Fixture f = fixture(helper, "tile_drawer", true);
        helper.assertTrue(f.be(helper).drawing() == null, "a placed tile is blank");

        MapTileService.Outcome plain = MapTileService.draw(f.player(), f.request(false));
        helper.assertTrue(plain.ok(), "the first draw succeeds, got " + plain.refusal());
        MapTileDrawing first = f.be(helper).drawing();
        assertDrawingOf(helper, f, first, f.minCx(), f.minCz());
        helper.assertTrue(first.markers().isEmpty(), "no markers unless asked");
        helper.assertValueEqual(first.day(), helper.getLevel().getServer().overworld().getDayTime() / MapTileService.TICKS_PER_DAY, "the day");

        MapTileService.Outcome marked = MapTileService.draw(f.player(), f.request(true));
        helper.assertTrue(marked.ok(), "a redraw is allowed by default");
        MapTileDrawing second = f.be(helper).drawing();
        assertDrawingOf(helper, f, second, f.minCx(), f.minCz());
        helper.assertValueEqual(second.markers().size(), 1, "only the marker inside the area is stamped");
        TileMarker m = second.markers().get(0);
        ChartMarkerCheck.assertStamped(helper, f, m);

        int shifted = f.minCx() + 7;
        helper.assertTrue(MapTileService.draw(f.player(), new DrawTilePayload(f.tile(), shifted, f.minCz(), false)).ok(), "redraw elsewhere");
        MapTileDrawing third = f.be(helper).drawing();
        assertDrawingOf(helper, f, third, shifted, f.minCz());
        helper.assertFalse(Arrays.equals(third.pixels(), second.pixels()), "the raster was replaced");
        helper.assertTrue(third.markers().isEmpty(), "and the markers with it");
        finish(helper, f.player());
    }

    private static final class ChartMarkerCheck {
        static void assertStamped(GameTestHelper helper, Fixture f, TileMarker m) {
            var source = f.data().marker(f.insideMarker()).orElseThrow();
            helper.assertValueEqual(m.icon(), MarkerIcon.SKULL, "icon");
            helper.assertValueEqual(m.name(), "Skull Rock", "name");
            helper.assertValueEqual(m.px(), Math.floorDiv(source.x(), f.cb()) - f.minCx(), "marker pixel x");
            helper.assertValueEqual(m.py(), Math.floorDiv(source.z(), f.cb()) - f.minCz(), "marker pixel y");
        }
    }

    /**
     * Own batch: changes config. With redraw_allowed off the first drawing stays: a redraw elsewhere and a clear are
     * refused, but the draw mode opens for an update (MAP3: updates only add what was charted since).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_chart_tile_redraw")
    public static void permanentTilesRefuseARedraw(GameTestHelper helper) {
        ConfigOverrides.during(helper, ChartConfig.REDRAW_ALLOWED, false);
        Fixture f = fixture(helper, "tile_permanent", true);
        helper.assertTrue(MapTileService.draw(f.player(), f.request(true)).ok(), "a blank tile can be drawn");
        MapTileDrawing first = f.be(helper).drawing();
        MapTileService.Outcome again = MapTileService.draw(f.player(), new DrawTilePayload(f.tile(), f.minCx() + 3, f.minCz(), false));
        helper.assertValueEqual(again.refusal(), MapTileRules.Refusal.PERMANENT, "the second draw is refused");
        helper.assertTrue(f.be(helper).drawing() == first, "the drawing is untouched");
        helper.assertValueEqual(MapTileService.clear(f.player(), f.tile()).refusal(), MapTileRules.Refusal.PERMANENT, "no clearing either");
        helper.assertTrue(f.be(helper).drawing() == first, "still untouched");
        List<CustomPacketPayload> sent = ChartBackend.record(f.player().getUUID());
        helper.assertTrue(MapTileService.openDrawMode(f.player(), f.tile()), "the draw mode opens for an update");
        TileTarget target = opened(helper, sent, 1);
        helper.assertTrue(target.updatable(), "an intact drawing to update");
        helper.assertFalse(target.redrawAllowed(), "the screen offers no redraw and no clear");
        helper.assertValueEqual(MapTileService.draw(f.player(), DrawTilePayload.update(f.tile(), true)).refusal(), MapTileRules.Refusal.NOTHING_NEW,
                "an update with the same chart adds nothing (and is not refused as a redraw)");
        finish(helper, f.player());
    }

    /** Own batch: changes config. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_chart_tiles")
    public static void disabledTilesRefuse(GameTestHelper helper) {
        ConfigOverrides.during(helper, ChartConfig.TILES_ENABLED, false);
        Fixture f = fixture(helper, "tile_disabled", true);
        helper.assertValueEqual(MapTileService.draw(f.player(), f.request(false)).refusal(), MapTileRules.Refusal.TILES_DISABLED, "refused");
        helper.assertTrue(f.be(helper).drawing() == null, "still blank");
        helper.assertFalse(MapTileService.openDrawMode(f.player(), f.tile()), "no draw mode");
        finish(helper, f.player());
    }

    /** By default the chart must be in hand (either hand); too far away or no tile is refused too. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void drawingNeedsAChartInHandAndReach(GameTestHelper helper) {
        Fixture f = fixture(helper, "tile_handless", false);
        helper.assertValueEqual(MapTileService.draw(f.player(), f.request(false)).refusal(), MapTileRules.Refusal.NEEDS_CHART, "no chart in hand");
        helper.assertTrue(f.be(helper).drawing() == null, "still blank");
        f.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ChartContent.CHART.get()));
        Vec3 near = f.player().position();
        f.player().setPos(near.add(ChartConfig.TILE_REACH.get() + 2, 0, 0));
        helper.assertValueEqual(MapTileService.draw(f.player(), f.request(false)).refusal(), MapTileRules.Refusal.TOO_FAR, "out of reach");
        f.player().setPos(near);
        helper.assertValueEqual(MapTileService.draw(f.player(), new DrawTilePayload(f.tile().above(), f.minCx(), f.minCz(), false)).refusal(),
                MapTileRules.Refusal.NO_TILE, "no tile there");
        helper.assertValueEqual(MapTileService.draw(f.player(), new DrawTilePayload(f.tile(), 40_000_000, f.minCz(), false)).refusal(),
                MapTileRules.Refusal.OUT_OF_WORLD, "beyond the world's edge");
        helper.assertValueEqual(MapTileService.draw(f.player(), new DrawTilePayload(f.tile(), f.minCx() + 1000, f.minCz(), false)).refusal(),
                MapTileRules.Refusal.UNCHARTED, "an area outside the player's chart");
        helper.assertTrue(f.be(helper).drawing() == null, "still blank after the refusals");
        helper.assertTrue(MapTileService.draw(f.player(), f.request(false)).ok(), "a chart in the off hand will do");
        finish(helper, f.player());
    }

    /** Own batch: changes config. Without require_chart_item empty hands may draw. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_chart_tile_item")
    public static void chartItemNotRequiredWhenOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, ChartConfig.REQUIRE_CHART_ITEM, false);
        Fixture f = fixture(helper, "tile_free", false);
        helper.assertTrue(MapTileService.draw(f.player(), f.request(false)).ok(), "empty hands draw");
        finish(helper, f.player());
    }

    /** The drawing reaches clients: the update tag and packet carry it, and a client-side copy loads the same drawing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void drawingSyncsThroughTheUpdateTag(GameTestHelper helper) {
        Fixture f = fixture(helper, "tile_sync", true);
        MapTileBlockEntity be = f.be(helper);
        var registries = helper.getLevel().registryAccess();
        MapTileBlockEntity blankCopy = new MapTileBlockEntity(f.tile(), be.getBlockState());
        blankCopy.loadWithComponents(be.getUpdateTag(registries), registries);
        helper.assertTrue(blankCopy.drawing() == null, "a blank tile syncs as blank");

        helper.assertTrue(MapTileService.draw(f.player(), f.request(true)).ok(), "drawn");
        helper.assertTrue(be.getUpdatePacket() != null, "the tile sends a block entity data packet");
        var tag = be.getUpdateTag(registries);
        helper.assertTrue(tag.contains(MapTileBlockEntity.TAG_DRAWING), "the update tag carries the drawing");
        helper.assertTrue(tag.getCompound(MapTileBlockEntity.TAG_DRAWING).getByteArray("pixels").length < f.size() * f.size() / 4,
                "packed well below the raw raster");
        MapTileBlockEntity clientCopy = new MapTileBlockEntity(f.tile(), be.getBlockState());
        clientCopy.loadWithComponents(tag, registries);
        helper.assertValueEqual(clientCopy.drawing(), be.drawing(), "the client sees the same drawing");

        // the redraw replaces the client's drawing as a whole
        helper.assertTrue(MapTileService.draw(f.player(), new DrawTilePayload(f.tile(), f.minCx() + 5, f.minCz(), false)).ok(), "redrawn");
        clientCopy.loadWithComponents(be.getUpdateTag(registries), registries);
        helper.assertValueEqual(clientCopy.drawing(), be.drawing(), "the client follows the redraw");
        helper.assertTrue(clientCopy.drawing().markers().isEmpty(), "with the markers gone");
        finish(helper, f.player());
    }

    /** A tile hangs on a wall (facing out of it), keeps standing there, and takes a drawing like a floor tile. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void tileHangsOnAWallAndTakesADrawing(GameTestHelper helper) {
        BlockPos wallRel = new BlockPos(3, 2, 4);
        BlockPos tileRel = wallRel.north();
        helper.setBlock(wallRel, Blocks.STONE);
        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        placer.setXRot(0f);
        placer.setYRot(0f); // looking south, at the wall's north face
        ItemStack item = new ItemStack(ChartContent.MAP_TILE_ITEM.get());
        placer.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockPos wall = helper.absolutePos(wallRel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, -0.5), Direction.NORTH, wall, false);
        InteractionResult placed = item.useOn(new UseOnContext(placer, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(placed.consumesAction(), "placed on the wall, got " + placed);
        helper.assertBlockPresent(ChartContent.MAP_TILE.get(), tileRel);
        BlockState state = helper.getBlockState(tileRel);
        helper.assertValueEqual(state.getValue(com.richardsenger.piratesnships.chart.tile.MapTileBlock.FACE),
                net.minecraft.world.level.block.state.properties.AttachFace.WALL, "a wall tile");
        helper.assertValueEqual(state.getValue(com.richardsenger.piratesnships.chart.tile.MapTileBlock.FACING), Direction.NORTH, "facing out of the wall");
        helper.assertTrue(state.canSurvive(helper.getLevel(), helper.absolutePos(tileRel)), "it holds on the wall");

        // draw onto it with the fixture's chart (the fixture also puts a floor tile at TILE; this one is on the wall)
        Fixture f = fixture(helper, "tile_wall", true);
        BlockPos onWall = helper.absolutePos(tileRel);
        f.player().setPos(Vec3.atCenterOf(onWall).add(0, 0, -1.5));
        MapTileService.Outcome outcome = MapTileService.draw(f.player(), new DrawTilePayload(onWall, f.minCx(), f.minCz(), true));
        helper.assertTrue(outcome.ok(), "the wall tile is drawn, got " + outcome.refusal());
        if (!(helper.getLevel().getBlockEntity(onWall) instanceof MapTileBlockEntity be)) throw new AssertionError("no block entity on the wall tile");
        helper.assertValueEqual(be.drawing(), outcome.drawing(), "the wall tile holds the drawing");
        helper.assertTrue(f.be(helper).drawing() == null, "the floor tile stays blank");

        // without the wall it cannot stay
        helper.setBlock(wallRel, Blocks.AIR);
        helper.assertFalse(helper.getBlockState(tileRel).is(ChartContent.MAP_TILE.get())
                && helper.getBlockState(tileRel).canSurvive(helper.getLevel(), onWall), "a wall tile needs its wall");
        finish(helper, f.player());
    }

    // --- the item -------------------------------------------------------------------------------------------------

    /** Breaking a drawn tile drops one tile carrying the drawing; placing it back restores it. A blank tile stacks. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void drawingSurvivesBreakingAndPlacing(GameTestHelper helper) {
        Fixture f = fixture(helper, "tile_mover", true);
        helper.assertTrue(MapTileService.draw(f.player(), f.request(true)).ok(), "drawn");
        MapTileDrawing drawing = f.be(helper).drawing();
        BlockState state = helper.getLevel().getBlockState(f.tile());
        Player survivor = helper.makeMockPlayer(GameType.SURVIVAL);
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), f.tile(), f.be(helper), survivor, ItemStack.EMPTY);
        helper.assertValueEqual(drops.size(), 1, "one item drops");
        ItemStack item = drops.get(0);
        helper.assertTrue(item.is(ChartContent.MAP_TILE_ITEM.get()), "a map tile");
        helper.assertValueEqual(item.get(ChartContent.MAP_TILE_DRAWING.get()), drawing, "the item carries the drawing");

        helper.setBlock(TILE, Blocks.AIR);
        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        placer.setXRot(90f);
        placer.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockPos floor = helper.absolutePos(FLOOR);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        InteractionResult placed = item.useOn(new UseOnContext(placer, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(placed.consumesAction(), "the tile is placed, got " + placed);
        helper.assertBlockPresent(ChartContent.MAP_TILE.get(), TILE);
        helper.assertValueEqual(f.be(helper).drawing(), drawing, "the placed tile shows the drawing again");

        // a blank tile drops a plain, stackable item
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 3), ChartContent.MAP_TILE.get());
        BlockPos blankPos = helper.absolutePos(new BlockPos(3, 2, 3));
        List<ItemStack> blank = Block.getDrops(helper.getLevel().getBlockState(blankPos), helper.getLevel(), blankPos,
                helper.getLevel().getBlockEntity(blankPos), survivor, ItemStack.EMPTY);
        helper.assertValueEqual(blank.size(), 1, "a blank tile drops one item");
        helper.assertFalse(blank.get(0).has(ChartContent.MAP_TILE_DRAWING.get()), "without a drawing");
        helper.assertValueEqual(blank.get(0).getMaxStackSize(), 64, "blank tiles stack");
        helper.assertTrue(ItemStack.isSameItemSameComponents(blank.get(0), new ItemStack(ChartContent.MAP_TILE_ITEM.get())), "like a new one");
        finish(helper, f.player());
    }

    // --- opening the draw mode ------------------------------------------------------------------------------------

    /** Sneak-using a chart on a blank tile opens the draw mode around the player; on a drawn tile at the old area. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void usingATileWithAChartOpensTheDrawMode(GameTestHelper helper) {
        Fixture f = fixture(helper, "tile_opener", true);
        List<CustomPacketPayload> sent = ChartBackend.record(f.player().getUUID());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(f.tile()), Direction.UP, f.tile(), false);
        // the item path (vanilla skips the block while sneaking with an item)
        InteractionResult used = f.player().getMainHandItem().useOn(new UseOnContext(f.player(), InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(used.consumesAction(), "the chart is used on the tile");
        TileTarget blank = opened(helper, sent, 1);
        helper.assertValueEqual(blank.pos(), f.tile(), "the tile");
        helper.assertFalse(blank.drawn(), "blank");
        helper.assertValueEqual(blank.tileCells(), ChartConfig.TILE_CELLS.get(), "the selection size");
        helper.assertValueEqual(blank.minCx(), MapTileRaster.centredOn(f.player().getX(), f.cb(), f.size()), "centred on the player");

        helper.assertTrue(MapTileService.draw(f.player(), new DrawTilePayload(f.tile(), f.minCx() - 9, f.minCz() + 4, false)).ok(), "drawn");
        // the block path (no sneaking)
        BlockState state = helper.getLevel().getBlockState(f.tile());
        var result = state.useItemOn(f.player().getMainHandItem(), helper.getLevel(), f.player(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result.consumesAction(), "using the tile with a chart");
        TileTarget drawn = opened(helper, sent, 2);
        helper.assertTrue(drawn.drawn(), "now drawn: the screen asks before a redraw");
        helper.assertValueEqual(drawn.minCx(), f.minCx() - 9, "the old area x");
        helper.assertValueEqual(drawn.minCz(), f.minCz() + 4, "the old area z");

        // a plain chart still opens without a tile
        helper.assertTrue(ChartBackend.open(f.player()), "plain open");
        List<ChartOpenPayload> opens = of(sent);
        helper.assertTrue(opens.get(opens.size() - 1).tile().isEmpty(), "a plain chart has no tile");
        finish(helper, f.player());
    }

    private static List<ChartOpenPayload> of(List<CustomPacketPayload> sent) {
        synchronized (sent) {
            return sent.stream().filter(ChartOpenPayload.class::isInstance).map(ChartOpenPayload.class::cast).toList();
        }
    }

    private static TileTarget opened(GameTestHelper helper, List<CustomPacketPayload> sent, int count) {
        List<ChartOpenPayload> opens = of(sent);
        helper.assertValueEqual(opens.size(), count, "chart openings");
        return opens.get(count - 1).tile().orElseThrow(() -> new AssertionError("opened without a tile"));
    }

    // --- content --------------------------------------------------------------------------------------------------

    @ModGameTest
    public static void mapTileBlockItemAndRecipe(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, List.of("map_tile"), List.of("map_tile"));
        ContentTestSupport.assertRecipe(helper, "map_tile", ChartContent.MAP_TILE_ITEM.get(), 1);
        helper.succeed();
    }
}
