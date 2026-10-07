package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.client.ChartClient;
import com.richardsenger.piratesnships.chart.net.ChartBackend;
import com.richardsenger.piratesnships.chart.tile.MapTileModels;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The {@code chart} module (work package MAP1): every player's own pirate chart. The world around each player is
 * charted into a persistent player attachment ({@link ChartService}); the chart item (or the key, if the server
 * allows it) opens the chart screen, which the server fills region by region ({@link ChartBackend}); players put
 * their own markers on it. MAP2: a chosen part of the chart is drawn onto a map tile block for everyone to see
 * ({@link com.richardsenger.piratesnships.chart.tile.MapTileService}, config {@code chart.tiles}).
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
            lang.block(ChartContent.MAP_TILE, "Map Tile");
            ChartText.ENGLISH.forEach(lang::add);
        });
        data.models(m -> {
            m.flatItem(ChartContent.CHART.get());
            MapTileModels.generate(m);
        });
        data.recipes(out -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, ChartContent.CHART.get())
                    .requires(Items.PAPER).requires(Items.LEATHER).requires(Items.FEATHER)
                    .unlockedBy("has_paper", InventoryChangeTrigger.TriggerInstance.hasItems(Items.PAPER))
                    .save(out, ChartContent.CHART.id());
            // paper on a wooden frame
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ChartContent.MAP_TILE_ITEM.get())
                    .pattern("SSS").pattern("SPS").pattern("SSS")
                    .define('S', Items.STICK).define('P', Items.PAPER)
                    .unlockedBy("has_paper", InventoryChangeTrigger.TriggerInstance.hasItems(Items.PAPER))
                    .save(out, ChartContent.MAP_TILE_ITEM.id());
        });
        // A drawn tile keeps its drawing when broken, also in explosions (like the sea chest's contents)
        data.blockLoot(loot -> loot.add(ChartContent.MAP_TILE.get(), LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(ChartContent.MAP_TILE_ITEM.get())
                        .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                .include(ChartContent.MAP_TILE_DRAWING.get()))))));
        data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_AXE).add(ChartContent.MAP_TILE.get()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ChartGameTests.class, MapTileGameTests.class);
    }
}
