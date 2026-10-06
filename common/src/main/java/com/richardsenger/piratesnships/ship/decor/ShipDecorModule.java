package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.models.model.TextureSlot;
import net.minecraft.data.models.model.TexturedModel;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * The {@code ship.decor} module: figureheads, nameplate, flagpole, cargo crate and cargo barrel. Models come from
 * vanilla templates: {@code orientable} for figureheads (placeholder until the art pass gives them real shapes),
 * the open orientable trapdoor plate for the nameplate, {@code fence_post} for the flagpole.
 */
public final class ShipDecorModule implements ModModule {

    @Override
    public String id() {
        return "ship.decor";
    }

    @Override
    public void registerContent() {
        ShipDecor.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(ShipDecor.FIGUREHEAD_MERMAID, "Mermaid Figurehead")
                .block(ShipDecor.FIGUREHEAD_LION, "Lion Figurehead")
                .block(ShipDecor.FIGUREHEAD_EAGLE, "Eagle Figurehead")
                .block(ShipDecor.FIGUREHEAD_SKULL, "Skull Figurehead")
                .block(ShipDecor.NAMEPLATE, "Nameplate")
                .block(ShipDecor.FLAGPOLE, "Flagpole")
                .block(ShipDecor.CARGO_CRATE, "Cargo Crate")
                .block(ShipDecor.CARGO_BARREL, "Cargo Barrel"));
        data.models(m -> {
            for (RegistryEntry<Block, FigureheadBlock> f : ShipDecor.figureheads()) figurehead(m, f.get());
            nameplate(m, ShipDecor.NAMEPLATE.get());
            Block pole = ShipDecor.FLAGPOLE.get();
            ResourceLocation poleModel = ModelTemplates.FENCE_POST.create(ModelLocationUtils.getModelLocation(pole), TextureMapping.defaultTexture(pole), m.models());
            m.blockStates().accept(MultiVariantGenerator.multiVariant(pole, Variant.variant().with(VariantProperties.MODEL, poleModel)));
            m.blocks().createTrivialCube(ShipDecor.CARGO_CRATE.get());
            m.blocks().createTrivialBlock(ShipDecor.CARGO_BARREL.get(), TexturedModel.COLUMN);
        });
        data.blockLoot(loot -> {
            for (RegistryEntry<Block, FigureheadBlock> f : ShipDecor.figureheads()) loot.dropSelf(f.get());
            loot.dropSelf(ShipDecor.NAMEPLATE.get());
            loot.dropSelf(ShipDecor.FLAGPOLE.get());
            loot.dropSelf(ShipDecor.CARGO_CRATE.get());
            loot.dropSelf(ShipDecor.CARGO_BARREL.get());
        });
        data.blockTags(tags -> {
            for (RegistryEntry<Block, FigureheadBlock> f : ShipDecor.figureheads()) {
                tags.tag(BlockTags.MINEABLE_WITH_AXE).add(f.get());
                tags.tag(SableWeightTags.LIGHT).add(f.get());
            }
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(ShipDecor.NAMEPLATE.get(), ShipDecor.FLAGPOLE.get(),
                    ShipDecor.CARGO_CRATE.get(), ShipDecor.CARGO_BARREL.get());
            // Thin blocks: like ladders and fences in Sable's own tags
            tags.tag(SableWeightTags.SUPER_LIGHT).add(ShipDecor.NAMEPLATE.get(), ShipDecor.FLAGPOLE.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(ShipDecor.NAMEPLATE.get(), ShipDecor.FLAGPOLE.get());
            // Wooden containers: like vanilla barrels and chests
            tags.tag(SableWeightTags.LIGHT).add(ShipDecor.CARGO_CRATE.get(), ShipDecor.CARGO_BARREL.get());
        });
        data.recipes(out -> {
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_MERMAID, Items.PRISMARINE_SHARD);
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_LION, Items.GOLD_INGOT);
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_EAGLE, Items.FEATHER);
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_SKULL, Items.BONE);
            ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ShipDecor.NAMEPLATE.get())
                    .requires(ItemTags.SIGNS).requires(Items.GOLD_NUGGET)
                    .unlockedBy("has_gold_nugget", InventoryChangeTrigger.TriggerInstance.hasItems(Items.GOLD_NUGGET))
                    .save(out, ShipDecor.NAMEPLATE.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.FLAGPOLE.get(), 2)
                    .pattern("S").pattern("S").pattern("S")
                    .define('S', Items.STICK)
                    .unlockedBy("has_stick", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STICK))
                    .save(out, ShipDecor.FLAGPOLE.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.CARGO_CRATE.get())
                    .pattern("SPS").pattern("P P").pattern("SPS")
                    .define('S', Items.STICK).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_stick", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STICK))
                    .save(out, ShipDecor.CARGO_CRATE.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.CARGO_BARREL.get())
                    .pattern("PIP").pattern("P P").pattern("PIP")
                    .define('P', ItemTags.PLANKS).define('I', Items.IRON_NUGGET)
                    .unlockedBy("has_iron_nugget", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_NUGGET))
                    .save(out, ShipDecor.CARGO_BARREL.id());
        });
    }

    /** Front {@code block/<name>}, shared sides {@code block/figurehead_side} and top {@code block/figurehead_top}. */
    private static void figurehead(ModelContext m, Block block) {
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(block))
                .put(TextureSlot.SIDE, Constants.id("block/figurehead_side"))
                .put(TextureSlot.TOP, Constants.id("block/figurehead_top"));
        ResourceLocation model = ModelTemplates.CUBE_ORIENTABLE.create(block, textures, m.models());
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block, Variant.variant().with(VariantProperties.MODEL, model))
                .with(horizontalFacing()));
    }

    /**
     * The plate of an open orientable trapdoor (3 px against the back of the block), rotated like a ladder; same
     * shape as {@code LadderBlock}. Flat item model with the block texture.
     */
    private static void nameplate(ModelContext m, Block block) {
        ResourceLocation model = ModelTemplates.ORIENTABLE_TRAPDOOR_OPEN.create(ModelLocationUtils.getModelLocation(block),
                TextureMapping.defaultTexture(block), m.models());
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block, Variant.variant().with(VariantProperties.MODEL, model))
                .with(horizontalFacing()));
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(block.asItem()),
                TextureMapping.layer0(TextureMapping.getBlockTexture(block)), m.models());
    }

    /** Model faces north; rotate for the other directions. */
    private static PropertyDispatch horizontalFacing() {
        return PropertyDispatch.property(BlockStateProperties.HORIZONTAL_FACING)
                .select(Direction.NORTH, Variant.variant())
                .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270));
    }

    private static void figureheadRecipe(RecipeOutput out, RegistryEntry<Block, FigureheadBlock> figurehead, Item key) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, figurehead.get())
                .pattern(" P ").pattern("PXP").pattern(" P ")
                .define('P', ItemTags.PLANKS).define('X', key)
                .unlockedBy("has_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key).getPath(),
                        InventoryChangeTrigger.TriggerInstance.hasItems(key))
                .save(out, figurehead.id());
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ShipDecorGameTests.class);
    }
}
