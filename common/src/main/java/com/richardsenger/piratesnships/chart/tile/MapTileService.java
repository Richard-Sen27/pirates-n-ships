package com.richardsenger.piratesnships.chart.tile;

import com.richardsenger.piratesnships.chart.ChartConfig;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.ChartService;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.net.DrawTilePayload;
import com.richardsenger.piratesnships.chart.net.TileTarget;
import com.richardsenger.piratesnships.chart.render.CellLookup;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Server side of the map tile (work package MAP2). Using a tile with a chart opens the chart in "draw on tile" mode
 * ({@link #openDrawMode}); the screen answers with a {@link DrawTilePayload}, and {@link #draw} renders the area from
 * the player's <b>own</b> chart ({@link MapTileRaster#draw}: one pixel per cell, unknown cells stay parchment),
 * stamps the player's markers inside it if asked, and replaces the tile's drawing as a whole (there is no partial
 * edit). The block entity then reaches every client in range through its update tag. Every rule is checked again on
 * the draw ({@link MapTileRules}); a refusal is told on the action bar.
 */
public final class MapTileService {

    public static final String MSG = ChartBackend.MSG + "tile.";
    public static final String DRAWN = MSG + "drawn";
    /** Game ticks per day: drawings record the overworld's day time / 24000, the day number players see. */
    public static final long TICKS_PER_DAY = 24000L;

    private MapTileService() {
    }

    /** What happened to a draw request. */
    public record Outcome(MapTileRules.Refusal refusal, @Nullable MapTileDrawing drawing) {
        public boolean ok() {
            return refusal == MapTileRules.Refusal.NONE;
        }
    }

    // --- opening ---------------------------------------------------------------------------------------------------

    /** Opens the chart of {@code player} in draw mode for the tile at {@code pos}; false (and a message) when refused. */
    public static boolean openDrawMode(ServerPlayer player, BlockPos pos) {
        MapTileBlockEntity tile = tileAt(player.serverLevel(), pos);
        MapTileRules.Refusal refusal = MapTileRules.check(request(player, pos, tile));
        if (refusal != MapTileRules.Refusal.NONE) {
            tell(player, refusal);
            return false;
        }
        int size = ChartConfig.TILE_CELLS.get();
        MapTileDrawing old = tile.drawing();
        int cb = cellBlocks(ChartService.data(player));
        int minCx = old != null ? old.minCx() : MapTileRaster.centredOn(player.getX(), cb, size);
        int minCz = old != null ? old.minCz() : MapTileRaster.centredOn(player.getZ(), cb, size);
        return ChartBackend.open(player, Optional.of(new TileTarget(pos.immutable(), size, old != null, minCx, minCz)));
    }

    // --- drawing ---------------------------------------------------------------------------------------------------

    /** The {@link DrawTilePayload} handler: draws, or tells the player why not. */
    public static Outcome handleDraw(ServerPlayer player, DrawTilePayload request) {
        Outcome outcome = draw(player, request);
        if (outcome.ok()) {
            player.displayClientMessage(Component.translatable(DRAWN), true);
            player.serverLevel().playSound(null, request.tilePos(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.8f, 1.0f);
        } else {
            tell(player, outcome.refusal());
        }
        return outcome;
    }

    /** Checks the request and, if allowed, replaces the tile's drawing. */
    public static Outcome draw(ServerPlayer player, DrawTilePayload request) {
        BlockPos pos = request.tilePos();
        ServerLevel level = player.serverLevel();
        MapTileBlockEntity tile = level.isLoaded(pos) ? tileAt(level, pos) : null;
        int size = ChartConfig.TILE_CELLS.get();
        ChartData data = ChartService.data(player);
        int cb = cellBlocks(data);
        boolean inWorld = MapTileRaster.inWorld(request.minCx(), request.minCz(), size, cb, MarkerRules.MAX_COORDINATE);
        MapTileRules.Request rules = request(player, pos, tile).withArea(inWorld, true);
        MapTileRules.Refusal refusal = MapTileRules.check(rules);
        if (refusal != MapTileRules.Refusal.NONE) return new Outcome(refusal, null);
        // only now is the area known to be sane: an area without a single charted cell would draw blank parchment
        refusal = MapTileRules.check(rules.withArea(true, MapTileRaster.anyKnown(CellLookup.of(data), request.minCx(), request.minCz(), size)));
        if (refusal != MapTileRules.Refusal.NONE) return new Outcome(refusal, null);
        byte[] pixels = MapTileRaster.draw(CellLookup.of(data), request.minCx(), request.minCz(), size);
        List<TileMarker> markers = request.includeMarkers()
                ? MapTileRaster.stamp(data.markers(), request.minCx(), request.minCz(), size, cb, MapTileDrawing.MAX_MARKERS)
                : List.of();
        long day = player.server.overworld().getDayTime() / TICKS_PER_DAY;
        MapTileDrawing drawing = new MapTileDrawing(size, pixels, request.minCx(), request.minCz(), cb,
                player.getGameProfile().getName(), day, markers);
        tile.setDrawing(drawing);
        return new Outcome(MapTileRules.Refusal.NONE, drawing);
    }

    // --- rules -----------------------------------------------------------------------------------------------------

    private static MapTileRules.Request request(ServerPlayer player, BlockPos pos, @Nullable MapTileBlockEntity tile) {
        boolean holds = player.getMainHandItem().is(ChartContent.CHART.get()) || player.getOffhandItem().is(ChartContent.CHART.get());
        return new MapTileRules.Request(ChartConfig.ENABLED.get(), ChartConfig.TILES_ENABLED.get(), tile != null,
                tile != null && inReach(player, pos), ChartConfig.REQUIRE_CHART_ITEM.get(), holds,
                tile != null && tile.drawing() != null, ChartConfig.REDRAW_ALLOWED.get(), true, true);
    }

    /** Whether the player stands within {@code chart.tiles.reach} of the tile (in the world, also for a tile on a ship). */
    public static boolean inReach(ServerPlayer player, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        ShipBody ship = SableShips.containing(player.level(), pos);
        if (ship != null) centre = ship.toWorld(centre);
        double reach = ChartConfig.TILE_REACH.get();
        return player.position().distanceToSqr(centre) <= reach * reach;
    }

    private static @Nullable MapTileBlockEntity tileAt(ServerLevel level, BlockPos pos) {
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
