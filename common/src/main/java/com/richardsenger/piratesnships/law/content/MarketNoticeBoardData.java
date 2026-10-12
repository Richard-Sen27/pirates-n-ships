package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.Condition;
import net.minecraft.data.models.blockstates.MultiPartGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Datagen of the market notice board (NB2) in every wood: a multipart block state per wood (each part's frame, the
 * wood's child of {@code block/market_notice_board_<part>}, plus the notices for its load,
 * {@code block/market_notice_board_<part>_<load>}, shared by every wood; turned by facing, the notices on the north side
 * unrotated), the item models, loot (only the anchor drops the board), tags, recipe and lang. The models come from
 * {@code tools/gen_market_notice_board_models.py}.
 */
public final class MarketNoticeBoardData {

    static final ResourceLocation ITEM = Constants.id("block/market_notice_board_item");

    private MarketNoticeBoardData() {
    }

    static ResourceLocation frame(MarketBoardPart part) {
        return Constants.id("block/market_notice_board_" + part.getSerializedName());
    }

    static ResourceLocation notices(MarketBoardPart part, NoticeBoardLoad load) {
        return Constants.id("block/market_notice_board_" + part.getSerializedName() + "_" + load.getSerializedName());
    }

    private static VariantProperties.Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }

    public static void gather(DataContributions data) {
        WoodFamily<MarketNoticeBoardBlock> boards = LawContent.MARKET_NOTICE_BOARDS;
        data.lang(lang -> boards.lang(lang, "Market Notice Board"));
        data.models(m -> {
            for (MarketBoardPart part : MarketBoardPart.values()) boards.childModels(m, frame(part));
            boards.itemModels(m, ITEM);
            boards.forEach((wood, board) -> {
                MultiPartGenerator gen = MultiPartGenerator.multiPart(board);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (MarketBoardPart part : MarketBoardPart.values()) {
                        gen.with(Condition.condition().term(MarketNoticeBoardBlock.FACING, facing).term(MarketNoticeBoardBlock.PART, part),
                                Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, frame(part)))
                                        .with(VariantProperties.Y_ROT, rotation(facing)));
                        for (NoticeBoardLoad load : NoticeBoardLoad.values()) {
                            gen.with(Condition.condition().term(MarketNoticeBoardBlock.FACING, facing)
                                            .term(MarketNoticeBoardBlock.PART, part).term(MarketNoticeBoardBlock.LOAD, load),
                                    Variant.variant().with(VariantProperties.MODEL, notices(part, load))
                                            .with(VariantProperties.Y_ROT, rotation(facing)));
                        }
                    }
                }
                m.blockStates().accept(gen);
            });
        });
        boards.loot(data, MarketNoticeBoardData::anchorOnly);
        data.blockTags(tags -> boards.tag(tags, BlockTags.MINEABLE_WITH_AXE));
        // Two small notice boards of the wood side by side, paper between them, on two of the wood's logs
        boards.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "BPB", "L L")
                .define('B', WoodRecipe.member(LawContent.NOTICE_BOARDS)).define('P', WoodRecipe.item(Items.PAPER))
                .define('L', WoodRecipe.log())
                .group(Constants.MOD_ID + ":market_notice_board").unlockedBy("has_paper", WoodRecipe.item(Items.PAPER)));
    }

    /** The board drops once, from its anchor (the other parts drop nothing; an explosion may destroy it). */
    private static LootTable.Builder anchorOnly(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(block).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties()
                                .hasProperty(MarketNoticeBoardBlock.PART, MarketBoardPart.BOTTOM_LEFT))))
                .when(ExplosionCondition.survivesExplosion()));
    }
}
