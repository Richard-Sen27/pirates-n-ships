package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.net.ChartMarkerPayload;
import com.richardsenger.piratesnships.chart.net.ChartOpenPayload;
import com.richardsenger.piratesnships.chart.net.ChartRegionPayload;
import com.richardsenger.piratesnships.chart.net.ChartSettingsPayload;
import com.richardsenger.piratesnships.chart.net.ChartStatePayload;
import com.richardsenger.piratesnships.chart.net.ChartViewPayload;
import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The chart (work package MAP1) in a real world: a coast built in the test area (grass, beach, shallow and deep water,
 * one cell column each) is charted by a server player's periodic sample with the right classes and the coast flag on
 * the beach; later samples add and never forget; the open chart streams the regions in view once per version; marker
 * requests are validated and capped; the config switches.
 */
public final class ChartGameTests {

    private static final int SIZE = 24;

    private ChartGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ChartGameTests.class);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, String name) {
        // Not added to the level: a mock connection would receive (and reject) other mods' login payloads
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.getInventory().clearContent();
        return p;
    }

    /** The built coast: whole cell columns from west to east inside the test area (two of grass, so the asserted land cell has land on both sides). */
    private record Coast(int cb, int cx0, int czMid) {
        int land() {
            return cx0 + 1;
        }

        int beach() {
            return cx0 + 2;
        }

        int shallow() {
            return cx0 + 3;
        }

        int deep() {
            return cx0 + 4;
        }

        /** Block x at the sampled (centre) column of cell column {@code cx}. */
        int centreX(int cx) {
            return cx * cb + cb / 2;
        }
    }

    /**
     * Builds, from the west edge to the east edge: grass (up to the second whole cell column), sand, shallow water (2 deep), deep water (6 deep)
     * up to the east edge, on a stone floor at y 0. Glass at the cells' west boundary columns (never a sampled
     * column) holds the water in. The barrier ceiling is removed so the heightmaps see the open sky.
     */
    private static Coast buildCoast(GameTestHelper helper) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                BlockPos p = new BlockPos(x, 13, z);
                if (helper.getBlockState(p).is(Blocks.BARRIER)) helper.setBlock(p, Blocks.AIR);
            }
        }
        int cb = ChartConfig.CELL_BLOCKS.get();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int cx0 = Math.floorDiv(origin.getX() + cb - 1, cb);
        int cz0 = Math.floorDiv(origin.getZ() + cb - 1, cb);
        Coast coast = new Coast(cb, cx0, cz0 + 2);
        for (int x = 0; x < SIZE; x++) {
            int cx = Math.floorDiv(origin.getX() + x, cb);
            boolean boundary = Math.floorMod(origin.getX() + x, cb) == 0;
            for (int z = 0; z < SIZE; z++) {
                boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
                if (cx <= coast.land()) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                } else if (cx == coast.beach()) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.SAND);
                } else {
                    int depth = cx == coast.shallow() ? 2 : 6;
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    BlockState fill = (boundary || edge) ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
                    for (int y = 1; y <= depth; y++) helper.setBlock(new BlockPos(x, y, z), fill);
                    if (boundary && cx == coast.deep()) {
                        // the wall between shallows and deep water reaches the deep water's surface
                        for (int y = 1; y <= 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                    }
                }
            }
        }
        return coast;
    }

    private static void standOnBeach(GameTestHelper helper, ServerPlayer p, Coast coast) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        p.setPos(new Vec3(coast.centreX(coast.beach()) + 0.5, origin.getY() + 1, coast.czMid() * coast.cb() + coast.cb() / 2.0));
    }

    private static <T> List<T> of(List<CustomPacketPayload> sent, Class<T> type) {
        synchronized (sent) {
            return sent.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    private static void assertCell(GameTestHelper helper, ChartData data, int cx, int cz, CellClass cls, boolean coast) {
        int cell = data.cell(cx, cz);
        helper.assertValueEqual(ChartCells.cellClass(cell), cls, "class of cell " + cx + "," + cz);
        helper.assertValueEqual(ChartCells.coast(cell), coast, "coast flag of cell " + cx + "," + cz + " (" + cls + ")");
    }

    private static void assertCoastCharted(GameTestHelper helper, ChartData data, Coast c) {
        for (int dz = -1; dz <= 1; dz++) {
            int cz = c.czMid() + dz;
            assertCell(helper, data, c.land(), cz, CellClass.LAND, false);
            assertCell(helper, data, c.beach(), cz, CellClass.BEACH, true);
            assertCell(helper, data, c.shallow(), cz, CellClass.SHALLOW_WATER, false);
            assertCell(helper, data, c.deep(), cz, CellClass.DEEP_WATER, false);
        }
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) ChartBackend.stopRecording(p.getUUID());
        helper.succeed();
    }

    // --- sampling -----------------------------------------------------------------------------------------------

    /** One sample interval of player ticks charts the built coast: land, beach (coast), shallow, deep. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void aSampleIntervalChartsTheCoast(GameTestHelper helper) {
        Coast coast = buildCoast(helper);
        ServerPlayer p = player(helper, "chart_sailor");
        standOnBeach(helper, p, coast);
        helper.assertTrue(ChartService.data(p).regions().isEmpty(), "a new player's chart is blank");
        int interval = ChartConfig.SAMPLE_INTERVAL_TICKS.get();
        // what the PLAYER_TICK_END listener does for a player in the level, every tick
        helper.onEachTick(() -> ChartService.onPlayerTick(p));
        helper.runAfterDelay(interval + 4, () -> {
            ChartData data = ChartService.data(p);
            helper.assertFalse(data.regions().isEmpty(), "something was charted within one interval");
            helper.assertValueEqual(data.cellBlocks(), coast.cb(), "the cell size is recorded");
            assertCoastCharted(helper, data, coast);
            finish(helper, p);
        });
    }

    /** A second sample adds what is new and forgets nothing, even far away where nothing is sampled. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void laterSamplesAddAndNeverRemove(GameTestHelper helper) {
        Coast coast = buildCoast(helper);
        ServerPlayer p = player(helper, "chart_keeper");
        // a cell charted long ago, far from here
        int farX = coast.land() + 20_000;
        int farZ = coast.czMid() - 20_000;
        SampleGrid old = SampleGrid.empty(farX, farZ, 1, 1);
        old.set(farX, farZ, CellClass.SNOW_ICE);
        ChartData seeded = ChartMerge.merge(ChartData.EMPTY.resetCells(coast.cb()), old, 0, ChartConfig.MAX_CELLS.get()).data();
        ChartService.setData(p, seeded);
        standOnBeach(helper, p, coast);
        helper.runAfterDelay(2, () -> {
            ChartData first = ChartService.sampleNow(p).orElseThrow().data();
            assertCoastCharted(helper, first, coast);
            assertCell(helper, first, farX, farZ, CellClass.SNOW_ICE, false);
            helper.assertTrue(first.version() > seeded.version(), "the version grew");

            // far out at sea, in unloaded chunks: nothing is sampled, nothing is forgotten
            p.setPos(p.getX() + 50_000, p.getY(), p.getZ() + 50_000);
            ChartMerge.Result second = ChartService.sampleNow(p).orElseThrow();
            helper.assertValueEqual(second.changedCells(), 0, "nothing sampled in unloaded chunks");
            ChartData after = ChartService.data(p);
            helper.assertValueEqual(after.regions().size(), first.regions().size(), "no region was dropped");
            for (ChartRegion r : first.regions().values()) {
                helper.assertTrue(after.regions().get(r.key()).sameCells(r), "region " + r + " is unchanged");
            }
            assertCoastCharted(helper, after, coast);
            assertCell(helper, after, farX, farZ, CellClass.SNOW_ICE, false);
            finish(helper, p);
        });
    }

    /** With the chart open, the regions in view are sent once per version, nearest first. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void openChartStreamsChangedRegionsOnce(GameTestHelper helper) {
        Coast coast = buildCoast(helper);
        ServerPlayer p = player(helper, "chart_reader");
        standOnBeach(helper, p, coast);
        List<CustomPacketPayload> sent = ChartBackend.record(p.getUUID());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChartContent.CHART.get()));
        helper.runAfterDelay(2, () -> {
            ChartData data = ChartService.sampleNow(p).orElseThrow().data();
            var use = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
            helper.assertTrue(use.getResult().consumesAction(), "using the chart succeeds");
            helper.assertValueEqual(of(sent, ChartOpenPayload.class).size(), 1, "the chart opens");
            helper.assertTrue(ChartBackend.isOpen(p.getUUID()), "a chart session is open");
            ChartBackend.handleView(p, new ChartViewPayload(coast.beach(), coast.czMid(), 64));
            for (int i = 0; i < 10; i++) ChartBackend.sendPending(p.getUUID(), ChartBackend.REGIONS_PER_TICK);
            List<ChartRegionPayload> regions = of(sent, ChartRegionPayload.class);
            helper.assertFalse(regions.isEmpty(), "regions were sent");
            long distinct = regions.stream().map(r -> ChartRegion.key(r.rx(), r.rz())).distinct().count();
            helper.assertValueEqual((long) regions.size(), distinct, "each region is sent once");
            long home = ChartRegion.keyOfCell(coast.beach(), coast.czMid());
            ChartRegionPayload own = regions.stream().filter(r -> ChartRegion.key(r.rx(), r.rz()) == home).findFirst().orElseThrow();
            helper.assertTrue(own.region().sameCells(data.regions().get(home)), "the sent region matches the chart");
            helper.assertValueEqual(ChartRegion.key(regions.get(0).rx(), regions.get(0).rz()), home, "the region in the middle goes first");
            // a new sample changes nothing: nothing new is sent
            ChartService.sampleNow(p);
            int before = of(sent, ChartRegionPayload.class).size();
            ChartBackend.sendPending(p.getUUID(), 100);
            helper.assertValueEqual(of(sent, ChartRegionPayload.class).size(), before, "unchanged regions are not resent");
            finish(helper, p);
        });
    }

    // --- markers ------------------------------------------------------------------------------------------------

    @ModGameTest
    public static void markerRequestsAreValidatedAndCapped(GameTestHelper helper) {
        ServerPlayer p = player(helper, "chart_marker");
        List<CustomPacketPayload> sent = ChartBackend.record(p.getUUID());
        MarkerRules.Outcome first = ChartBackend.handleMarker(p, ChartMarkerPayload.add(100, -200, MarkerIcon.SKULL, "  Dead Man's Chest "));
        helper.assertTrue(first.ok(), "the first marker is placed");
        ChartData data = ChartService.data(p);
        helper.assertValueEqual(data.markers().size(), 1, "one marker");
        helper.assertValueEqual(data.markers().get(0).name(), "Dead Man's Chest", "the name is trimmed");
        ChartStatePayload state = of(sent, ChartStatePayload.class).get(0);
        helper.assertValueEqual(state.markers(), data.markers(), "the client gets the new list");
        helper.assertTrue(state.refusal().isEmpty(), "no refusal");

        // rename it, then a name that is too long
        int id = first.markerId();
        helper.assertTrue(ChartBackend.handleMarker(p, ChartMarkerPayload.edit(id, 100, -200, MarkerIcon.X, "Treasure")).ok(), "rename");
        helper.assertValueEqual(ChartService.data(p).marker(id).orElseThrow().name(), "Treasure", "renamed");
        MarkerRules.Outcome tooLong = ChartBackend.handleMarker(p, ChartMarkerPayload.add(0, 0, MarkerIcon.X, "x".repeat(MarkerRules.MAX_NAME_LENGTH + 1)));
        helper.assertValueEqual(tooLong.refusal(), MarkerRules.Refusal.NAME_TOO_LONG, "a long name is refused");

        // fill the chart up to max_markers, then one more is refused
        int max = ChartConfig.MAX_MARKERS.get();
        for (int i = 1; i < max; i++) {
            helper.assertTrue(ChartBackend.handleMarker(p, ChartMarkerPayload.add(i, i, MarkerIcon.ANCHOR, "m" + i)).ok(), "marker " + i);
        }
        helper.assertValueEqual(ChartService.data(p).markers().size(), max, "the chart is full");
        MarkerRules.Outcome over = ChartBackend.handleMarker(p, ChartMarkerPayload.add(5, 5, MarkerIcon.PORT, "one too many"));
        helper.assertValueEqual(over.refusal(), MarkerRules.Refusal.TOO_MANY, "beyond max_markers is refused");
        helper.assertValueEqual(ChartService.data(p).markers().size(), max, "still full, nothing added");
        List<ChartStatePayload> states = of(sent, ChartStatePayload.class);
        helper.assertValueEqual(states.get(states.size() - 1).refusal().orElse(""), ChartBackend.MSG + "refused.too_many", "the client hears why");

        // removing one makes room again
        helper.assertTrue(ChartBackend.handleMarker(p, ChartMarkerPayload.remove(id)).ok(), "remove");
        helper.assertTrue(ChartService.data(p).marker(id).isEmpty(), "removed");
        helper.assertValueEqual(ChartBackend.handleMarker(p, ChartMarkerPayload.remove(id)).refusal(), MarkerRules.Refusal.UNKNOWN_MARKER, "already gone");
        finish(helper, p);
    }

    // --- the key and the settings -------------------------------------------------------------------------------

    /** Defaults: the settings say the key needs the item; the key opens nothing with empty hands, but does with a chart. */
    @ModGameTest
    public static void keyNeedsTheItemByDefault(GameTestHelper helper) {
        ServerPlayer p = player(helper, "chart_keyless");
        List<CustomPacketPayload> sent = ChartBackend.record(p.getUUID());
        ChartBackend.onDatapackSync(p, true);
        ChartSettingsPayload settings = of(sent, ChartSettingsPayload.class).get(0);
        helper.assertFalse(settings.settings().openWithoutItem(), "open_without_item is off by default");
        helper.assertFalse(settings.settings().showOtherPlayers(), "show_other_players is off by default");
        helper.assertFalse(ChartBackend.handleRequestOpen(p), "the key does not open the chart without one in hand");
        helper.assertTrue(of(sent, ChartOpenPayload.class).isEmpty(), "nothing opened");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ChartContent.CHART.get()));
        helper.assertTrue(ChartBackend.handleRequestOpen(p), "with a chart in hand the key opens it");
        helper.assertValueEqual(of(sent, ChartOpenPayload.class).size(), 1, "opened");
        helper.assertTrue(ChartBackend.others(p).isEmpty(), "no other players without the option");
        finish(helper, p);
    }

    /** Own batch: changes config. */
    @ModGameTest(batch = "pirates_n_ships_config_chart_open_without_item")
    public static void openWithoutItemReachesTheClient(GameTestHelper helper) {
        ConfigOverrides.during(helper, ChartConfig.OPEN_WITHOUT_ITEM, true);
        ServerPlayer p = player(helper, "chart_free");
        List<CustomPacketPayload> sent = ChartBackend.record(p.getUUID());
        ChartBackend.onDatapackSync(p, true);
        helper.assertTrue(of(sent, ChartSettingsPayload.class).get(0).settings().openWithoutItem(), "the synced settings carry the option");
        helper.assertTrue(ChartBackend.handleRequestOpen(p), "the key opens the chart with empty hands");
        ChartOpenPayload open = of(sent, ChartOpenPayload.class).get(0);
        helper.assertTrue(open.settings().openWithoutItem(), "and the open payload says so too");
        finish(helper, p);
    }

    /** Own batch: changes config. Disabled: no sampling over a whole interval, no chart, no markers. */
    @ModGameTest(batch = "pirates_n_ships_config_chart_disabled", template = GameTestTemplates.EMPTY_24)
    public static void disabledChartsNothing(GameTestHelper helper) {
        ConfigOverrides.during(helper, ChartConfig.ENABLED, false);
        Coast coast = buildCoast(helper);
        ServerPlayer p = player(helper, "chart_none");
        standOnBeach(helper, p, coast);
        List<CustomPacketPayload> sent = ChartBackend.record(p.getUUID());
        helper.assertTrue(ChartService.sampleNow(p).isEmpty(), "no sample while disabled");
        helper.onEachTick(() -> ChartService.onPlayerTick(p));
        helper.runAfterDelay(ChartConfig.SAMPLE_INTERVAL_TICKS.get() + 2, () -> {
            helper.assertTrue(ChartService.data(p).regions().isEmpty(), "nothing charted over a whole interval");
            helper.assertFalse(ChartBackend.open(p), "the chart does not open");
            helper.assertTrue(of(sent, ChartOpenPayload.class).isEmpty(), "no open payload");
            helper.assertValueEqual(ChartBackend.handleMarker(p, ChartMarkerPayload.add(0, 0, MarkerIcon.X, "")).refusal(),
                    MarkerRules.Refusal.DISABLED, "markers are refused");
            finish(helper, p);
        });
    }

    // --- content ------------------------------------------------------------------------------------------------

    @ModGameTest
    public static void chartItemAndRecipe(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, List.of("chart"), List.of());
        helper.assertValueEqual(new ItemStack(ChartContent.CHART.get()).getMaxStackSize(), 1, "a chart does not stack");
        ContentTestSupport.assertRecipe(helper, "chart", ChartContent.CHART.get(), 1);
        helper.succeed();
    }
}
