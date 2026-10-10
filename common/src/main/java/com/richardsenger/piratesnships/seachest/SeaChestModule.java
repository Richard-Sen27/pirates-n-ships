package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
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
import net.minecraft.tags.ItemTags;
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
        WoodFamily<SeaChestBlock> chests = SeaChestContent.CHESTS;
        data.lang(lang -> chests.lang(lang, "Sea Chest"));
        data.lang(lang -> lang
                .add(SeaChestContent.ENTITY.get().getDescriptionId(), "Sea Chest")
                .add(SeaChestBlockEntity.TITLE_KEY, "Sea Chest")
                .add(SeaChestItem.WORN_HINT_KEY, "Use in the air to carry it on your back: no jumping, sprinting or swimming"));
        data.models(m -> {
            // Hand-made Blockbench model (art/models/sea_chest.bbmodel, design.md §4.8) with the wood family's texture
            // variables (WOOD2: the dark oak planks are #planks, the stripped dark oak lid and feet #log; the iron and
            // the rope handles stay): a child per wood and its block state. The model's north side (hasp and lock
            // plate) is the front, unrotated for FACING north; the item uses the wood's child.
            chests.childModels(m, MODEL);
            chests.itemModels(m, MODEL);
            chests.forEach((wood, block) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(block,
                            Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, MODEL)))
                    .with(PropertyDispatch.property(SeaChestBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270)))));
        });
        // Like the shulker box: the contents and the name go with the item, also in explosions
        chests.loot(data, block -> LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(block).apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                        .include(DataComponents.CONTAINER).include(DataComponents.CUSTOM_NAME)))));
        data.blockTags(tags -> chests.tag(tags, BlockTags.MINEABLE_WITH_AXE, C_CHESTS_BLOCK));
        data.itemTags(tags -> chests.itemTag(tags, C_CHESTS_ITEM));
        // WOOD2: the chest's wooden input is that wood's planks, a bottom row of three under the chest (before: leather,
        // iron and a chest only, "LLL / ICI")
        chests.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "LLL", "ICI", "PPP")
                .define('L', WoodRecipe.item(Items.LEATHER)).define('I', WoodRecipe.item(Items.IRON_INGOT))
                .define('C', WoodRecipe.item(Items.CHEST)).define('P', WoodRecipe.planks())
                .group(Constants.MOD_ID + ":sea_chest").unlockedBy("has_chest", WoodRecipe.item(Items.CHEST)));
        gatherPaddleData(data);
    }

    /** The sea chest's hand-made parent model. */
    static final ResourceLocation MODEL = Constants.id("block/sea_chest");

    /** The paddle (SC2): name, hints, refusals and the recipe; its model is hand-made (ART8, {@code item/paddle}). */
    private static void gatherPaddleData(DataContributions data) {
        PaddleItem paddle = SeaChestContent.PADDLE.get();
        data.lang(lang -> lang
                .item(SeaChestContent.PADDLE, "Paddle")
                .add(PaddleItem.HINT_KEY, "Use on a floating sea chest to sit on it and paddle; sneak to get off")
                .add(SeaChestEntity.MountRefusal.DISABLED.key(), "Paddling is disabled on this server")
                .add(SeaChestEntity.MountRefusal.NOT_FLOATING.key(), "The sea chest must float to be paddled")
                .add(SeaChestEntity.MountRefusal.OCCUPIED.key(), "Someone is sitting on this sea chest")
                .add(SeaChestEntity.MountRefusal.CANNOT_RIDE.key(), "You can't sit on the sea chest now")
                .add(SeaChestEntity.DISMOUNT_FIRST_KEY, "Get off first (sneak) to open the sea chest"));
        // Diagonal like a tool, two planks and a stick (a straight column would be the wooden sword)
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, paddle)
                .pattern(" P").pattern(" P").pattern("S ")
                .define('P', ItemTags.PLANKS).define('S', Items.STICK)
                .unlockedBy("has_sea_chest", InventoryChangeTrigger.TriggerInstance.hasItems(SeaChestContent.ITEM.get()))
                .save(out, SeaChestContent.PADDLE.id()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SeaChestGameTests.class, PaddleGameTests.class);
    }
}
