package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The {@code ship.rigging} module (docs/design.md §4.8 "Visual backlog 2", item 1): RL1's ratlines, a climbable rope
 * net hung on a mast or laid sloped from the gunwale to the masthead ({@link RatlinesBlock}).
 */
public final class RiggingModule implements ModModule {

    @Override
    public String id() {
        return "ship.rigging";
    }

    @Override
    public void registerConfig() {
        RiggingConfig.init();
    }

    @Override
    public void registerContent() {
        RiggingContent.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(RiggingContent.RATLINES, "Ratlines")
                .add(RatlinesItem.KEY_TOOLTIP, "Hang on a mast, or lay from the deck to climb up sloped")
                .add(RatlinesItem.KEY_DISABLED, "Ratlines are disabled on this server"));
        data.models(m -> {
            // hand-made Blockbench models (art/models/ratlines.bbmodel, design.md §4.8), drawn facing north: the hung net
            // against the south side, the sloped net rising to the north. Only the block state is generated; the item
            // has its own hand-made model (item/ratlines).
            RatlinesBlock block = RiggingContent.RATLINES.get();
            ResourceLocation wall = ModelLocationUtils.getModelLocation(block);
            ResourceLocation slope = ModelLocationUtils.getModelLocation(block, "_slope");
            m.blockStates().accept(MultiVariantGenerator.multiVariant(block)
                    .with(PropertyDispatch.properties(RatlinesBlock.KIND, RatlinesBlock.FACING)
                            .generate((kind, facing) -> Variant.variant()
                                    .with(VariantProperties.MODEL, kind == RatlinesRules.Kind.SLOPE ? slope : wall)
                                    .with(VariantProperties.Y_ROT, switch (facing) {
                                        case EAST -> VariantProperties.Rotation.R90;
                                        case SOUTH -> VariantProperties.Rotation.R180;
                                        case WEST -> VariantProperties.Rotation.R270;
                                        default -> VariantProperties.Rotation.R0;
                                    }))));
            m.handMadeItem(RiggingContent.RATLINES_ITEM.get());
        });
        data.blockLoot(loot -> loot.dropSelf(RiggingContent.RATLINES.get()));
        data.blockTags(tags -> {
            tags.tag(BlockTags.CLIMBABLE).add(RiggingContent.RATLINES.get());
            tags.tag(RiggingContent.RATLINES_ANCHORS).addTag(BlockTags.FENCES).addTag(BlockTags.WALLS);
            // a rope net: light like a ladder, a quarter volume for buoyancy (Sable lists minecraft:ladder in both,
            // refs/sable common/src/main/resources/data/sable/tags/block/super_light.json and quarter_volume.json)
            tags.tag(SableWeightTags.SUPER_LIGHT).add(RiggingContent.RATLINES.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(RiggingContent.RATLINES.get());
        });
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RiggingContent.RATLINES_ITEM.get(), 4)
                .pattern("T T").pattern("SSS").pattern("T T")
                .define('T', Items.STICK).define('S', Items.STRING)
                .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                .save(out, RiggingContent.RATLINES_ITEM.id()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(RatlinesGameTests.class);
    }
}
