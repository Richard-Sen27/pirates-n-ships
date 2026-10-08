package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;

/**
 * The {@code combat.boarding} module (docs/design.md §8.3 "Boarding"): BRD1's boarding plank, a walkway of plank blocks
 * laid from one ship's gunwale onto a ship lying alongside, breaking when the hulls part. The boarding AI that uses
 * {@link BoardingPlanks#between} comes later (BRD2).
 */
public final class BoardingModule implements ModModule {

    @Override
    public String id() {
        return "combat.boarding";
    }

    @Override
    public void registerConfig() {
        BoardingConfig.init();
    }

    @Override
    public void registerContent() {
        BoardingContent.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(BoardingContent.PLANK, "Boarding Plank")
                .add(BoardingPlankItem.KEY_TOOLTIP, "Use on a gunwale beside a ship lying alongside")
                .add(BoardingPlankItem.KEY_NO_DECK, "No deck within reach")
                .add(BoardingPlankItem.KEY_BLOCKED, "Something is in the way of the plank")
                .add(BoardingPlankItem.KEY_NOT_ON_SHIP, "Lay the plank from a ship's gunwale")
                .add(BoardingPlankItem.KEY_DISABLED, "Boarding planks are disabled on this server"));
        data.models(m -> {
            // hand-made Blockbench models (art/models/boarding_plank.bbmodel, design.md §4.8), running north: only the
            // block state is generated. The gunwale end, the middle and the far end with its hooks are separate models.
            BoardingPlankBlock plank = BoardingContent.PLANK.get();
            ResourceLocation base = ModelLocationUtils.getModelLocation(plank, "_base");
            ResourceLocation middle = ModelLocationUtils.getModelLocation(plank);
            ResourceLocation tip = ModelLocationUtils.getModelLocation(plank, "_tip");
            m.blockStates().accept(MultiVariantGenerator.multiVariant(plank)
                    .with(PropertyDispatch.properties(BoardingPlankBlock.FACING, BoardingPlankBlock.SEGMENT, BoardingPlankBlock.TIP)
                            .generate((facing, segment, isTip) -> Variant.variant()
                                    .with(VariantProperties.MODEL, isTip ? tip : segment == 0 ? base : middle)
                                    .with(VariantProperties.Y_ROT, switch (facing) {
                                        case EAST -> VariantProperties.Rotation.R90;
                                        case SOUTH -> VariantProperties.Rotation.R180;
                                        case WEST -> VariantProperties.Rotation.R270;
                                        default -> VariantProperties.Rotation.R0;
                                    }))));
            m.handMadeItem(BoardingContent.PLANK_ITEM.get());
        });
        data.blockLoot(loot -> {
            // only segment 0 drops, so a whole run yields one plank however it breaks
            BoardingPlankBlock plank = BoardingContent.PLANK.get();
            loot.add(plank, LootTable.lootTable().withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(BoardingContent.PLANK_ITEM.get())
                            .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(plank)
                                    .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(BoardingPlankBlock.SEGMENT, 0))))
                    .when(ExplosionCondition.survivesExplosion())));
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(BoardingContent.PLANK.get());
            // a thin board: light like a ladder, a quarter volume for buoyancy
            tags.tag(SableWeightTags.SUPER_LIGHT).add(BoardingContent.PLANK.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(BoardingContent.PLANK.get());
        });
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, BoardingContent.PLANK_ITEM.get())
                .pattern("SSS").pattern("N N")
                .define('S', ItemTags.WOODEN_SLABS).define('N', Items.IRON_NUGGET)
                .unlockedBy("has_wooden_slab", InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(ItemTags.WOODEN_SLABS)))
                .save(out, BoardingContent.PLANK_ITEM.id()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(BoardingGameTests.class);
    }
}
