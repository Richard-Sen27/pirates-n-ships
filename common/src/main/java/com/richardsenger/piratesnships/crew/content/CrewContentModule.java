package com.richardsenger.piratesnships.crew.content;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.crew.provisions.ProvisionTags;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.crew.galley.GalleyGameTests;
import com.richardsenger.piratesnships.crew.galley.GalleyText;
import com.richardsenger.piratesnships.crew.galley.PantryBlock;
import com.richardsenger.piratesnships.crew.galley.ProvisionsCommands;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlock;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelRules;
import com.richardsenger.piratesnships.crew.hammock.CrewInfo;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import com.richardsenger.piratesnships.crew.hammock.HammockGameTests;
import com.richardsenger.piratesnships.crew.hammock.HammockTags;
import com.richardsenger.piratesnships.station.StationModule;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * The {@code crew.content} module: provision items and the pantry and water barrel blocks (design.md §7.4), plus
 * their {@code pirates_n_ships:provisions/*} tags (including rum from {@code trade.content}). The lime has no recipe:
 * it is a fruit that comes from loot and trade (apples and berries already prevent scurvy). The blocks' behavior, the
 * ship-level provisions entry point and the {@code /pirates provisions} debug commands live in {@code crew.galley}.
 * <p>
 * Also the hammock (HM1, design.md §7.1): block, item, seat entity, {@code #pirates_n_ships:hammock_supports}, the
 * placeholder models (until ART1d), and the nightfall tick of {@code crew.hammock.CrewRest}.
 */
public final class CrewContentModule implements ModModule {

    static final TagKey<Item> C_FOODS = cTag("foods");
    static final TagKey<Item> C_FRUITS = cTag("foods/fruit");
    static final TagKey<Item> C_BREADS = cTag("foods/bread");

    @Override
    public String id() {
        return "crew.content";
    }

    @Override
    public void registerContent() {
        CrewContent.init();
        // Pipes of other mods: same face rules as hoppers (insert provisions, extract the rest)
        Services.CAPABILITIES.registerBlockContainer(CrewContent.PANTRY_BLOCK_ENTITY);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .item(CrewContent.HARDTACK, "Hardtack")
                .item(CrewContent.SALTED_FISH, "Salted Fish")
                .item(CrewContent.SALT_PORK, "Salt Pork")
                .item(CrewContent.LIME, "Lime")
                .block(CrewContent.PANTRY, "Pantry")
                .block(CrewContent.WATER_BARREL, "Water Barrel")
                .block(CrewContent.HAMMOCK, "Hammock")
                .add(CrewContent.HAMMOCK_SEAT.get().getDescriptionId(), "Hammock Seat")
                .add(CrewContent.MEAL_SEAT.get().getDescriptionId(), "Meal Seat")
                .add("container." + Constants.MOD_ID + ".pantry", "Pantry"));
        data.lang(lang -> GalleyText.LANG.forEach(lang::add));
        data.lang(lang -> CrewInfo.LANG.forEach(lang::add));
        data.models(m -> {
            // The provisions' item models are hand-made (art/models/{hardtack,salted_fish,salt_pork,lime}.bbmodel),
            // so datagen writes none
            // hand-made Blockbench model (art/models/pantry.bbmodel, design.md §4.8), doors on the north side: only the
            // block state is generated, rotated so the doors face FACING
            m.blockStates().accept(MultiVariantGenerator.multiVariant(CrewContent.PANTRY.get(), Variant.variant()
                    .with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(CrewContent.PANTRY.get())))
                    .with(PropertyDispatch.property(PantryBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))));
            waterBarrelModels(m);
            hammockModels(m);
        });
        data.blockLoot(loot -> {
            loot.dropSelf(CrewContent.PANTRY.get());
            // keeps the rations on the item; the pantry drops its contents itself (onRemove)
            loot.add(CrewContent.WATER_BARREL.get(), LootTable.lootTable().withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(CrewContent.WATER_BARREL.get())
                            .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                    .include(CrewContent.WATER_RATIONS.get())))
                    .when(ExplosionCondition.survivesExplosion())));
            // like a bed: only the foot drops, so breaking either half (or a support) yields one hammock
            HammockBlock hammock = CrewContent.HAMMOCK.get();
            loot.add(hammock, LootTable.lootTable().withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(CrewContent.HAMMOCK_ITEM.get())
                            .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(hammock)
                                    .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(HammockBlock.PART, BedPart.FOOT))))
                    .when(ExplosionCondition.survivesExplosion())));
        });
        data.entityTypeTags(tags -> {
            // the hammock seat lives inside the ship's plot like the station seat
            tags.tag(StationModule.SABLE_RETAIN).add(CrewContent.HAMMOCK_SEAT.get());
            tags.tag(StationModule.SABLE_DESTROY_WITH_SUB_LEVEL).add(CrewContent.HAMMOCK_SEAT.get());
            // so does the meal seat beside the pantry (CRW2)
            tags.tag(StationModule.SABLE_RETAIN).add(CrewContent.MEAL_SEAT.get());
            tags.tag(StationModule.SABLE_DESTROY_WITH_SUB_LEVEL).add(CrewContent.MEAL_SEAT.get());
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(CrewContent.PANTRY.get(), CrewContent.WATER_BARREL.get());
            tags.tag(SableWeightTags.LIGHT).add(CrewContent.PANTRY.get(), CrewContent.HAMMOCK.get());
            tags.tag(HammockTags.SUPPORTS).addTag(BlockTags.FENCES).addTag(BlockTags.WALLS).addTag(BlockTags.LOGS);
            // Full of water: heavier than a wooden block until containers get load-dependent mass (§4.9)
            tags.tag(SableWeightTags.HEAVY).add(CrewContent.WATER_BARREL.get());
        });
        data.itemTags(tags -> {
            tags.tag(ProvisionTags.PRESERVED).add(CrewContent.HARDTACK.get(), CrewContent.SALTED_FISH.get(), CrewContent.SALT_PORK.get());
            tags.tag(ProvisionTags.ANTI_SCURVY).add(CrewContent.LIME.get());
            tags.tag(ProvisionTags.RUM).add(TradeContent.RUM.get());
            tags.tag(ProvisionTags.WATER_BARREL).add(CrewContent.WATER_BARREL.get().asItem());
            tags.tag(C_FOODS).add(CrewContent.HARDTACK.get(), CrewContent.SALTED_FISH.get(), CrewContent.SALT_PORK.get(), CrewContent.LIME.get());
            tags.tag(C_FRUITS).add(CrewContent.LIME.get());
            tags.tag(C_BREADS).add(CrewContent.HARDTACK.get());
        });
        data.recipes(out -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, CrewContent.HARDTACK.get(), 2)
                    .requires(Items.WHEAT, 3).requires(Items.WATER_BUCKET)
                    .unlockedBy("has_wheat", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHEAT))
                    .save(out, CrewContent.HARDTACK.id());
            // Dried kelp stands in for salt, which vanilla lacks
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, CrewContent.SALTED_FISH.get())
                    .requires(Ingredient.of(Items.COD, Items.SALMON)).requires(Items.DRIED_KELP)
                    .unlockedBy("has_dried_kelp", InventoryChangeTrigger.TriggerInstance.hasItems(Items.DRIED_KELP))
                    .save(out, CrewContent.SALTED_FISH.id());
            ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, CrewContent.SALT_PORK.get())
                    .requires(Items.PORKCHOP).requires(Items.DRIED_KELP)
                    .unlockedBy("has_dried_kelp", InventoryChangeTrigger.TriggerInstance.hasItems(Items.DRIED_KELP))
                    .save(out, CrewContent.SALT_PORK.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, CrewContent.PANTRY.get())
                    .pattern("PPP").pattern("PWP").pattern("PPP")
                    .define('P', ItemTags.PLANKS).define('W', Items.WHEAT)
                    .unlockedBy("has_wheat", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WHEAT))
                    .save(out, CrewContent.PANTRY.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, CrewContent.WATER_BARREL.get())
                    .pattern("PIP").pattern("PWP").pattern("PIP")
                    .define('P', ItemTags.PLANKS).define('I', Items.IRON_NUGGET).define('W', Items.WATER_BUCKET)
                    .unlockedBy("has_water_bucket", InventoryChangeTrigger.TriggerInstance.hasItems(Items.WATER_BUCKET))
                    .save(out, CrewContent.WATER_BARREL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, CrewContent.HAMMOCK_ITEM.get())
                    .pattern("S S").pattern("WWW")
                    .define('S', Items.STRING).define('W', ItemTags.WOOL)
                    .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                    .save(out, CrewContent.HAMMOCK_ITEM.id());
        });
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> ProvisionsCommands.register(dispatcher));
        CommonEvents.LEVEL_TICK_END.register(CrewRest::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> CrewRest.onServerStopped());
    }

    /**
     * Hand-made Blockbench models (art/models/water_barrel*.bbmodel, design.md §4.8): fill 0..3 use
     * {@code water_barrel_fill<n>}, fill 4 (full, also the item model) the plain {@code water_barrel}. Only the block
     * state is generated.
     */
    private static void waterBarrelModels(ModelContext m) {
        WaterBarrelBlock barrel = CrewContent.WATER_BARREL.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(barrel).with(PropertyDispatch.property(WaterBarrelBlock.FILL)
                .generate(fill -> Variant.variant().with(VariantProperties.MODEL, fill == WaterBarrelRules.MAX_FILL
                        ? ModelLocationUtils.getModelLocation(barrel)
                        : ModelLocationUtils.getModelLocation(barrel, "_fill" + fill)))));
        // The block's water surface is greyscale water_still tinted by the biome (CrewContentClient). Items have no
        // colour handler, so the item model keeps the old pre-coloured stand-in for that texture slot.
        m.models().accept(ModelLocationUtils.getModelLocation(barrel.asItem()), () -> {
            JsonObject textures = new JsonObject();
            textures.addProperty(WATER_TEXTURE_SLOT, "minecraft:block/blue_ice");
            JsonObject model = new JsonObject();
            model.addProperty("parent", ModelLocationUtils.getModelLocation(barrel).toString());
            model.add("textures", textures);
            return model;
        });
    }

    /**
     * The hammock's block state (design.md §4.8, ART1d): the hand-made Blockbench models {@code block/hammock_foot}
     * and {@code block/hammock_head} ({@code art/models/hammock.bbmodel}), drawn facing north (the head lies north of
     * the foot), picked by {@code PART} and turned by {@code FACING}. The item model {@code item/hammock} (the rolled-up
     * hammock, {@code art/models/hammock_item.bbmodel}) is hand-made too, so datagen writes no item model.
     */
    private static void hammockModels(ModelContext m) {
        HammockBlock hammock = CrewContent.HAMMOCK.get();
        ResourceLocation foot = ModelLocationUtils.getModelLocation(hammock, "_foot");
        ResourceLocation head = ModelLocationUtils.getModelLocation(hammock, "_head");
        m.blockStates().accept(MultiVariantGenerator.multiVariant(hammock)
                .with(PropertyDispatch.properties(HammockBlock.FACING, HammockBlock.PART).generate((facing, part) -> Variant.variant()
                        .with(VariantProperties.MODEL, part == BedPart.FOOT ? foot : head)
                        .with(VariantProperties.Y_ROT, switch (facing) {
                            case EAST -> VariantProperties.Rotation.R90;
                            case SOUTH -> VariantProperties.Rotation.R180;
                            case WEST -> VariantProperties.Rotation.R270;
                            default -> VariantProperties.Rotation.R0;
                        }))));
        m.handMadeItem(CrewContent.HAMMOCK_ITEM.get());
    }

    /** The texture slot of the water surface in the hand-made water barrel models. */
    static final String WATER_TEXTURE_SLOT = "7";

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.crew.content.client.CrewContentClient.init();
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CrewContentGameTests.class, GalleyGameTests.class, HammockGameTests.class);
    }

    private static TagKey<Item> cTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
