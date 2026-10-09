package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.combat.boarding.order.BoardingOrderGameTests;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingParties;
import com.richardsenger.piratesnships.combat.boarding.order.NpcBoarding;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
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
 * blocks laid from a ship, a pier or the shore onto a ship, a pier or the shore (BRD1, BRD2), one straight board at a
 * single pitch (BRD4), breaking when its ends part, taken up again by hand; and on the client the board's renderer and
 * the placement preview. BRD3: the "Board!" order ({@code order}): the crew cross a plank or a narrow gap onto the
 * other ship and fight, NPC captains board the player the same way.
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
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(BoardingParties::onLevelTick);
        CommonEvents.LEVEL_TICK_END.register(NpcBoarding::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> {
            BoardingParties.clear();
            NpcBoarding.clear();
        });
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.combat.boarding.client.BoardingClient.init();
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
                .add(BoardingPlankItem.KEY_TOO_STEEP, "Too steep: at most %s degrees")
                .add(BoardingPlankItem.KEY_NOTHING_TO_LAND, "Nothing to land on within reach")
                .add(BoardingPlankItem.KEY_DISABLED, "Boarding planks are off on this server")
                // BRD3: the boarding order's action-bar lines (MSG1: at most 40 characters)
                .add(BoardingParties.KEY_BOARD, "%s hands, board her!")
                .add(BoardingParties.KEY_BACK, "All hands back aboard")
                .add(BoardingParties.KEY_NO_WAY, "No way across to board")
                .add(BoardingParties.KEY_NO_HANDS, "No hands free to board")
                .add(BoardingParties.KEY_DISABLED, "Boarding is off on this server")
                .add(BoardingParties.KEY_CAPTURED, "Her deck is yours"));
        data.models(m -> {
            // BRD4: the straight board is drawn by BoardingPlankRenderer from segment 0; a cell's own model shows nothing
            // but the breaking particles (like the ratline net's cell). The item keeps its hand-made Blockbench model
            // (art/models/boarding_plank.bbmodel, design.md §4.8).
            BoardingPlankBlock plank = BoardingContent.PLANK.get();
            ResourceLocation cell = ModelTemplates.PARTICLE_ONLY.create(plank,
                    TextureMapping.particle(ResourceLocation.withDefaultNamespace("block/spruce_planks")), m.models());
            m.blockStates().accept(MultiVariantGenerator.multiVariant(plank, Variant.variant().with(VariantProperties.MODEL, cell)));
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
        return List.of(BoardingGameTests.class, BoardingOrderGameTests.class);
    }
}
