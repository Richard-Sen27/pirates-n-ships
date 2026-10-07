package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.client.ChartClient;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The {@code chart} module (work package MAP1): every player's own pirate chart. The world around each player is
 * charted into a persistent player attachment ({@link ChartService}); the chart item (or the key, if the server
 * allows it) opens the chart screen, which the server fills region by region ({@link ChartBackend}); players put
 * their own markers on it. MAP2 draws a chosen part onto a map tile block with
 * {@link com.richardsenger.piratesnships.chart.render.ChartRaster}.
 */
public final class ChartModule implements ModModule {

    @Override
    public String id() {
        return "chart";
    }

    @Override
    public void registerConfig() {
        ChartConfig.init();
    }

    @Override
    public void registerContent() {
        ChartContent.init();
        ChartAttachments.register();
    }

    @Override
    public void registerPayloads() {
        ChartBackend.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.PLAYER_TICK_END.register(ChartService::onPlayerTick);
        CommonEvents.SERVER_TICK_END.register(ChartBackend::onServerTick);
        CommonEvents.PLAYER_LOGOUT.register(ChartBackend::onLogout);
        CommonEvents.SERVER_STOPPED.register(ChartBackend::onServerStopped);
        CommonEvents.DATAPACK_SYNC.register(ChartBackend::onDatapackSync);
    }

    @Override
    public void initClient() {
        ChartClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> {
            lang.item(ChartContent.CHART, "Chart");
            ChartText.ENGLISH.forEach(lang::add);
        });
        data.models(m -> m.flatItem(ChartContent.CHART.get()));
        data.recipes(out -> ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, ChartContent.CHART.get())
                .requires(Items.PAPER).requires(Items.LEATHER).requires(Items.FEATHER)
                .unlockedBy("has_paper", InventoryChangeTrigger.TriggerInstance.hasItems(Items.PAPER))
                .save(out, ChartContent.CHART.id()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ChartGameTests.class);
    }
}
