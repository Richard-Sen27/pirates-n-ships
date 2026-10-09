package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
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
 * The {@code combat.boarding} module (docs/design.md §8.3, §8.4 "Boarding"): the boarding plank, a gangway of plank
 * blocks laid from a ship, a pier or the shore onto a ship, a pier or the shore (BRD1, BRD2), climbing or falling on
 * ramps, breaking when its ends part, taken up again by hand; and the client's placement preview. A boarding order that
 * sends crew across the planks {@link BoardingPlanks#between} two ships does not exist yet.
 */
public final class BoardingModule implements ModModule {

    @Override
    public String id() {
        return "combat.boarding";
    }

    @Override
    public void registerConfig() {
        BoardingConfig.init();
        BoardingClientConfig.init();
    }

    @Override
    public void registerContent() {
        BoardingContent.init();
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.boarding.client.PlankPreview.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(BoardingContent.PLANK, "Boarding Plank")
                .add(BoardingPlankItem.KEY_TOOLTIP, "Use on the edge of a deck, rail or pier toward a ship, a pier or the shore")
                .add(BoardingPlankItem.KEY_TOOLTIP_PICK_UP, "Sneak and use a laid plank with an empty hand to take it up")
                .add(BoardingPlankItem.KEY_NO_START, "No plank from here")
                .add(BoardingPlankItem.KEY_BLOCKED, "Something is in the way %s blocks out")
                .add(BoardingPlankItem.KEY_TOO_FAR, "Too far: a plank reaches %s blocks")
                .add(BoardingPlankItem.KEY_TOO_STEEP, "Too steep: a plank climbs or falls %s blocks at most")
                .add(BoardingPlankItem.KEY_NOTHING_TO_LAND, "Nothing to land on within reach")
                .add(BoardingPlankItem.KEY_DISABLED, "Boarding planks are disabled on this server"));
        data.models(m -> {
            // hand-made Blockbench models (art/models/boarding_plank.bbmodel, design.md §4.8), laid north: only the block
            // state is generated. Flat cells: the end at segment 0, the middle and the far end with its hooks. Ramp cells
            // climb north (the 1-in-2 ramp's lower and upper cell, the 45 degree cell); a falling cell is the climbing
            // model turned round.
            BoardingPlankBlock plank = BoardingContent.PLANK.get();
            ResourceLocation base = ModelLocationUtils.getModelLocation(plank, "_base");
            ResourceLocation middle = ModelLocationUtils.getModelLocation(plank);
            ResourceLocation tip = ModelLocationUtils.getModelLocation(plank, "_tip");
            ResourceLocation rampLow = ModelLocationUtils.getModelLocation(plank, "_ramp_low");
            ResourceLocation rampHigh = ModelLocationUtils.getModelLocation(plank, "_ramp_high");
            ResourceLocation steep = ModelLocationUtils.getModelLocation(plank, "_steep");
            m.blockStates().accept(MultiVariantGenerator.multiVariant(plank)
                    .with(PropertyDispatch.properties(BoardingPlankBlock.FACING, BoardingPlankBlock.SEGMENT, BoardingPlankBlock.TIP,
                                    BoardingPlankBlock.PITCH)
                            .generate((facing, segment, isTip, pitch) -> {
                                ResourceLocation model = switch (pitch) {
                                    case FLAT -> isTip ? tip : segment == 0 ? base : middle;
                                    case UP_LOW, DOWN_LOW -> rampLow;
                                    case UP_HIGH, DOWN_HIGH -> rampHigh;
                                    case UP, DOWN -> steep;
                                };
                                return Variant.variant()
                                        .with(VariantProperties.MODEL, model)
                                        .with(VariantProperties.Y_ROT, rotation(falling(pitch) ? facing.getOpposite() : facing));
                            })));
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

    private static boolean falling(PlankRun.Pitch pitch) {
        return pitch == PlankRun.Pitch.DOWN || pitch == PlankRun.Pitch.DOWN_LOW || pitch == PlankRun.Pitch.DOWN_HIGH;
    }

    /** The block state rotation of a model laid north for a run going {@code facing}. */
    private static VariantProperties.Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(BoardingGameTests.class);
    }
}
