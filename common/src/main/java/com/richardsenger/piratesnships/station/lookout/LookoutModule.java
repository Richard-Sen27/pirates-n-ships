package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import java.util.List;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * The crow's nest and its lookout (CN1, docs/design.md §4.8 "Visual backlog 2" item 2, §6, §7): the block
 * ({@link CrowsNestBlock}, hand-made model {@code block/crows_nest}), the lookout station ({@link LookoutStation}) and
 * the watch ({@link Lookouts}), config {@code lookout.*}.
 */
public final class LookoutModule implements ModModule {

    @Override
    public String id() {
        return "station.lookout";
    }

    @Override
    public void registerConfig() {
        LookoutConfig.init();
    }

    @Override
    public void registerContent() {
        LookoutContent.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(Lookouts::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> Lookouts.onServerStopped());
        SableShips.onShipRemoved(Lookouts::onShipRemoved);
    }

    @Override
    public void gatherData(DataContributions data) {
        CrowsNestBlock nest = LookoutContent.CROWS_NEST.get();
        data.lang(lang -> lang.block(LookoutContent.CROWS_NEST, "Crow's Nest"));
        data.lang(LookoutLang::lang);
        // the model is hand-made (art/models/crows_nest.bbmodel) and carries the item's display entries; the block item
        // gets datagen's delegating item model
        data.models(m -> m.blockStates().accept(MultiVariantGenerator.multiVariant(nest,
                Variant.variant().with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(nest)))));
        data.blockLoot(loot -> loot.dropSelf(nest));
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(nest);
            tags.tag(SableWeightTags.LIGHT).add(nest); // wooden, like planks: light on the masthead
        });
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, nest)
                .pattern("PRP").pattern("N N").pattern("PPP")
                .define('P', ItemTags.PLANKS).define('R', TriangularSailContent.ROPE.get()).define('N', Items.IRON_NUGGET)
                .unlockedBy("has_rope", InventoryChangeTrigger.TriggerInstance.hasItems(TriangularSailContent.ROPE.get()))
                .save(out, LookoutContent.CROWS_NEST.id()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(LookoutGameTests.class);
    }
}
