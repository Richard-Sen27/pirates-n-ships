package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import com.richardsenger.piratesnships.chart.sample.ChartSampler;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Server side of the chart's exploration (work package MAP1): every {@code chart.sample_interval_ticks} each player
 * in the overworld samples the loaded world within {@code chart.sample_radius} ({@link ChartSampler}) and merges it
 * into their chart ({@link ChartMerge}). Players are spread over the interval by their UUID so they do not all
 * sample in the same tick. Spectators chart nothing.
 */
public final class ChartService {

    private ChartService() {
    }

    public static ChartData data(Player player) {
        return Services.ATTACHMENTS.get(player, ChartAttachments.CHART);
    }

    public static void setData(Player player, ChartData data) {
        Services.ATTACHMENTS.set(player, ChartAttachments.CHART, data);
    }

    /** Whether {@code level} is charted (the overworld only). */
    public static boolean charted(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }

    /** {@code PLAYER_TICK_END}: sample when this player's turn in the interval comes. */
    public static void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer sp) || !ChartConfig.ENABLED.get()) return;
        int interval = ChartConfig.SAMPLE_INTERVAL_TICKS.get();
        long phase = sp.getUUID().getLeastSignificantBits() & 0xFFFF;
        if ((sp.serverLevel().getGameTime() + phase) % interval != 0) return;
        sampleNow(sp);
    }

    /**
     * Samples around {@code player} now and stores the result. Empty when nothing was sampled (disabled, another
     * dimension, a spectator).
     */
    public static Optional<ChartMerge.Result> sampleNow(ServerPlayer player) {
        if (!ChartConfig.ENABLED.get() || player.isSpectator()) return Optional.empty();
        ServerLevel level = player.serverLevel();
        if (!charted(level)) return Optional.empty();
        int cellBlocks = ChartConfig.CELL_BLOCKS.get();
        ChartData data = data(player);
        if (data.cellBlocks() != cellBlocks) {
            if (!data.regions().isEmpty()) {
                Constants.LOG.info("Chart of {}: cell size changed from {} to {} blocks, explored cells cleared",
                        player.getGameProfile().getName(), data.cellBlocks(), cellBlocks);
            }
            data = data.resetCells(cellBlocks);
        }
        SampleGrid grid = ChartSampler.sample(level, player.getX(), player.getZ(), ChartConfig.SAMPLE_RADIUS.get(),
                cellBlocks, ChartConfig.SHALLOW_DEPTH.get());
        ChartMerge.Result result = ChartMerge.merge(data, grid, level.getGameTime(), ChartConfig.MAX_CELLS.get());
        if (!result.droppedRegions().isEmpty()) {
            Constants.LOG.warn("Chart of {} reached chart.max_cells ({}): forgot {} least recently visited region(s)",
                    player.getGameProfile().getName(), ChartConfig.MAX_CELLS.get(), result.droppedRegions().size());
        }
        setData(player, result.data());
        return Optional.of(result);
    }
}
