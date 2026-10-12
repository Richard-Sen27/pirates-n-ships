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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Datagen of the boom (SAIL5) in every wood: lang, the wood's children of the hand-made {@code block/boom} (the
 * gooseneck), {@code block/boom_arm} (one block of the arm, the renderer's part) and {@code block/boom_item}, block
 * states (the facing only: the sheet is drawn by the renderer), loot, tags, the recipe, the action-bar lines and the
 * Sable physics properties by the family tag (WOOD4's rule, as the yard's).
 */
public final class BoomData {

    /** The hand-made gooseneck (art/models/boom.bbmodel, group {@code gooseneck}), built for {@code facing=north}. */
    public static final ResourceLocation MODEL = Constants.id("block/boom");
    /** One block of the arm along −z from the pivot (group {@code arm}); the renderer draws its child per wood. */
    public static final ResourceLocation ARM_MODEL = Constants.id("block/boom_arm");
    /** The item: the gooseneck with a short arm (group {@code item}). */
    static final ResourceLocation ITEM_MODEL = Constants.id("block/boom_item");
    /** Every boom of every wood (a mod's wood's as an optional entry): the selector of the booms' physics properties. */
    public static final TagKey<Block> BOOMS = TagKey.create(Registries.BLOCK, Constants.id("booms"));

    private BoomData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<BoomBlock> booms = SailingBlocks.BOOMS;
        data.lang(lang -> booms.lang(lang, "Boom"));
        data.lang(lang -> lang.add(BoomBlock.KEY_SHEETED, "Boom sheeted to %s°")
                .add(BoomBlock.KEY_OFF, "Booms are off on this server"));
        data.models(BoomData::models);
        booms.dropSelf(data);
        data.blockTags(tags -> booms.tag(tags, BlockTags.MINEABLE_WITH_AXE, BOOMS));
        // A boom is a spar like the yard (refs/sable/wiki/Block Physics Properties.md; priority above Sable's tag
        // definitions): the same small mass and volume, selected by the family tag (WOOD4's rule: a selector naming a
        // block of a mod that is not loaded would log an error, a tag's optional entry does not)
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", SailingBlocks.BOOM.id(), () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", 0.1);
            properties.addProperty("sable:volume", 0.15);
            JsonObject json = new JsonObject();
            json.addProperty("selector", "#" + BOOMS.location());
            json.addProperty("priority", 1001);
            json.add("properties", properties);
            return json;
        });
        // two of the wood's stripped logs and an iron ingot for the gooseneck give two booms (the arm is drawn to the sail)
        booms.recipe(data, WoodRecipe.shaped(RecipeCategory.TRANSPORTATION, 2, "LLI")
                .define('L', WoodRecipe.strippedLog()).define('I', WoodRecipe.item(Items.IRON_INGOT))
                .group(Constants.MOD_ID + ":boom").unlockedBy("has_log", WoodRecipe.strippedLog()));
    }

    /**
     * The gooseneck is built for {@code facing=north} (the mast to the south); the block state turns it by the facing.
     * The sheet and the water do not change the model.
     */
    private static void models(ModelContext m) {
        WoodFamily<BoomBlock> booms = SailingBlocks.BOOMS;
        booms.childModels(m, MODEL, WoodFamily.Particle.LOG);
        booms.childModels(m, ARM_MODEL, WoodFamily.Particle.LOG);
        booms.itemModels(m, ITEM_MODEL, WoodFamily.Particle.LOG);
        booms.forEach((wood, block) -> {
            ResourceLocation model = WoodFamily.child(wood, MODEL);
            PropertyDispatch.C1<Direction> dispatch = PropertyDispatch.property(BoomBlock.FACING);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                dispatch.select(d, Variant.variant().with(VariantProperties.MODEL, model).with(VariantProperties.Y_ROT, rotation(d)));
            }
            m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
        });
    }

    /** Y rotation of the north-facing model to {@code d}. */
    private static VariantProperties.Rotation rotation(Direction d) {
        return switch (d) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }
}
