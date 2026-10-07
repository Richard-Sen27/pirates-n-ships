package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;

/**
 * The sea chest (work package S1, docs/design.md §11): a 54-slot chest block, an item that keeps its contents and is
 * worn on the back (no jumping, sprinting or swimming, slower walking, drags its wearer under), and a floating entity
 * that drifts with currents and wind. Config sections {@code sea_chest} (server) and {@code sea_chest_visuals}
 * (client).
 */
public final class SeaChestModule implements ModModule {

    static final TagKey<Block> C_CHESTS_BLOCK = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "chests"));
    static final TagKey<Item> C_CHESTS_ITEM = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "chests"));

    @Override
    public String id() {
        return "seachest";
    }

    @Override
    public void registerConfig() {
        SeaChestConfig.init();
    }

    @Override
    public void registerContent() {
        SeaChestContent.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.PLAYER_TICK_END.register(SeaChestWearing::onPlayerTick);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.seachest.client.SeaChestClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        SeaChestBlock block = SeaChestContent.BLOCK.get();
        data.lang(lang -> lang
                .block(SeaChestContent.BLOCK, "Sea Chest")
                .add(SeaChestContent.ENTITY.get().getDescriptionId(), "Sea Chest")
                .add(SeaChestBlockEntity.TITLE_KEY, "Sea Chest")
                .add(SeaChestItem.WORN_HINT_KEY, "Use in the air to carry it on your back: no jumping, sprinting or swimming"));
        data.models(m -> {
            // Hand-made Blockbench model (art/models/sea_chest.bbmodel, design.md §4.8): only the block state is
            // generated. The model's north side (hasp and lock plate) is the front, unrotated for FACING north; the
            // block item delegates to the block model.
            ResourceLocation model = ModelLocationUtils.getModelLocation(block);
            m.blockStates().accept(MultiVariantGenerator.multiVariant(block, Variant.variant().with(VariantProperties.MODEL, model))
                    .with(PropertyDispatch.property(SeaChestBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))));
        });
        // Like the shulker box: the contents and the name go with the item, also in explosions
        data.blockLoot(loot -> loot.add(block, LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(block).apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                        .include(DataComponents.CONTAINER).include(DataComponents.CUSTOM_NAME))))));
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(block);
            tags.tag(C_CHESTS_BLOCK).add(block);
        });
        data.itemTags(tags -> tags.tag(C_CHESTS_ITEM).add(SeaChestContent.ITEM.get()));
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, SeaChestContent.ITEM.get())
                .pattern("LLL").pattern("ICI")
                .define('L', Items.LEATHER).define('I', Items.IRON_INGOT).define('C', Items.CHEST)
                .unlockedBy("has_chest", InventoryChangeTrigger.TriggerInstance.hasItems(Items.CHEST))
                .save(out, SeaChestContent.ITEM.id()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SeaChestGameTests.class);
    }
}
