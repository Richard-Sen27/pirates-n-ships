package com.richardsenger.piratesnships.sailing.block;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Datagen of the yard (docs/design.md §5.2 rule F5a) in every wood (WOOD3): lang, the wood's child of the hand-made
 * {@code block/yard} (a log-only spar: {@code #log}, {@code #log_top}, the birch rope bands and iron fixed; the log as
 * particle), block states, item models, loot, tags, recipes and the Sable physics properties.
 */
public final class YardData {

    /** The hand-made parent (art/models/yard.bbmodel), along x. */
    static final ResourceLocation MODEL = Constants.id("block/yard");
    /** Every yard of every wood (a mod's wood's as an optional entry): the selector of the yards' physics properties. */
    public static final TagKey<Block> YARDS = TagKey.create(Registries.BLOCK, Constants.id("yards"));

    private YardData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<YardBlock> yards = SailingBlocks.YARDS;
        data.lang(lang -> yards.lang(lang, "Yard"));
        data.models(YardData::models);
        yards.dropSelf(data);
        data.blockTags(tags -> yards.tag(tags, BlockTags.MINEABLE_WITH_AXE, YARDS));
        // A yard is a 6 px spar (about 0.14 of a block of wood) high up on the mast: Sable's lightest tag (0.25 kpg, the
        // fence's) still made the small test hull top-heavy with six of them, so it gets its own, physical mass and volume
        // (refs/sable/wiki/Block Physics Properties.md; priority above Sable's tag definitions). WOOD3: one file for every
        // wood, selected by the yards' tag (a selector naming a block of a mod that is not loaded would log an error:
        // refs/sable common/.../PhysicsBlockPropertiesDefinitionLoader#applyToBlocks; a tag selector is resolved there
        // too). The file keeps the legacy path.
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", SailingBlocks.YARD.id(), () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", 0.1);
            properties.addProperty("sable:volume", 0.15);
            JsonObject json = new JsonObject();
            json.addProperty("selector", "#" + YARDS.location());
            json.addProperty("priority", 1001);
            json.add("properties", properties);
            return json;
        });
        // three of the wood's stripped logs in a row give three yards (the cloth needs no item: it is drawn between the yards)
        yards.recipe(data, WoodRecipe.shaped(RecipeCategory.TRANSPORTATION, 3, "LLL")
                .define('L', WoodRecipe.strippedLog())
                .group(Constants.MOD_ID + ":yard").unlockedBy("has_log", WoodRecipe.strippedLog()));
    }

    /**
     * The yard: a hand-made Blockbench model along x (art/models/yard.bbmodel, design.md §4.8); {@code axis=z} turns it
     * by 90°. The cloth is drawn by {@code YardClothRenderer}, not by the model.
     */
    private static void models(ModelContext m) {
        WoodFamily<YardBlock> yards = SailingBlocks.YARDS;
        yards.childModels(m, MODEL, WoodFamily.Particle.LOG);
        yards.itemModels(m, MODEL, WoodFamily.Particle.LOG);
        yards.forEach((wood, block) -> {
            ResourceLocation model = WoodFamily.child(wood, MODEL);
            m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(PropertyDispatch.property(YardBlock.AXIS)
                    .select(Direction.Axis.X, Variant.variant().with(VariantProperties.MODEL, model))
                    .select(Direction.Axis.Z, Variant.variant().with(VariantProperties.MODEL, model)
                            .with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))));
        });
    }
}
