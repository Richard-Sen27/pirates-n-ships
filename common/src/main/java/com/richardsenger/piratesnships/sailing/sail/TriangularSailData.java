package com.richardsenger.piratesnships.sailing.sail;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.item.RopeItem;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
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
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Datagen of the triangular sail (docs/design.md §5.2, rule F5b): the cleat's block states, loot, tags,
 * physical weight and recipe, the rope's item model and recipe, and the lang entries of both. Called from
 * {@code SailingModule.gatherData}.
 */
public final class TriangularSailData {

    /** The cleat's hand-made parent (WOOD3: the pad {@code #log}/{@code #log_top}, the iron horns fixed). */
    static final ResourceLocation CLEAT_MODEL = Constants.id("block/cleat");
    /** Every cleat of every wood (a mod's wood's as an optional entry): the selector of the cleats' physics properties. */
    public static final TagKey<Block> CLEATS = TagKey.create(Registries.BLOCK, Constants.id("cleats"));

    private TriangularSailData() {
    }

    public static void gather(DataContributions data) {
        data.lang(lang -> lang
                .item(TriangularSailContent.ROPE, "Rope")
                .add(CleatBlock.KEY_NO_SAIL, "No sail: rig a stay, and a cleat below")
                .add(RopeItem.KEY_TIED, "Tied: now a second cleat, ≤%s blocks")
                .add(RopeItem.KEY_ELSEWHERE, "First end lost: the rope is tied here")
                .add(RopeItem.KEY_SAME, "Use it on a second cleat, ≤%s blocks")
                .add(RopeItem.KEY_TOO_LONG, "Too far: %s blocks, a rope reaches %s")
                .add(RopeItem.KEY_TOO_FLAT, "Too flat: needs %s blocks of drop")
                .add(RopeItem.KEY_RIGGED, "Stay rigged: put a cleat below its top")
                .add(RopeItem.KEY_OTHER_BODY, "Both ends on one ship, or both on land")
                .add(RopeItem.KEY_NO_LINES, "Rope lines are off: cleat to cleat only")
                .add(RopeItem.KEY_ALREADY, "These two are already roped together")
                .add(RopeItem.KEY_FULL, "An anchor holds at most %s ropes"));
        WoodFamily<CleatBlock> cleats = TriangularSailContent.CLEATS;
        data.lang(lang -> cleats.lang(lang, "Cleat")); // WOOD3: "Dark Oak Cleat", ...
        data.models(TriangularSailData::models);
        cleats.dropSelf(data);
        data.blockTags(tags -> cleats.tag(tags, BlockTags.MINEABLE_WITH_AXE, CLEATS));
        // A cleat is a fist-sized fitting, mostly high up on the mast: like the yard, it gets its own small mass and volume
        // (refs/sable/wiki/Block Physics Properties.md; priority above Sable's tag definitions), so that a rig does not
        // make the tender test hull top-heavy. WOOD3: one file for every wood, selected by the cleats' tag (as the yards',
        // sailing.block.YardData); the file keeps the legacy path.
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", TriangularSailContent.CLEAT.id(), () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", 0.05);
            properties.addProperty("sable:volume", 0.05);
            JsonObject json = new JsonObject();
            json.addProperty("selector", "#" + CLEATS.location());
            json.addProperty("priority", 1001);
            json.add("properties", properties);
            return json;
        });
        // the wood's planks either side of an iron ingot give two cleats
        cleats.recipe(data, WoodRecipe.shaped(RecipeCategory.TRANSPORTATION, 2, "PIP")
                .define('P', WoodRecipe.planks()).define('I', WoodRecipe.item(Items.IRON_INGOT))
                .group(Constants.MOD_ID + ":cleat").unlockedBy("has_iron_ingot", WoodRecipe.item(Items.IRON_INGOT)));
        data.recipes(out -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, TriangularSailContent.ROPE.get())
                    .pattern("S").pattern("S").pattern("S")
                    .define('S', Items.STRING)
                    .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                    .save(out, TriangularSailContent.ROPE.id());
        });
    }

    private static void models(ModelContext m) {
        // The rope's item model is hand-made (art/models/rope.bbmodel, design.md §4.8)
        WoodFamily<CleatBlock> cleats = TriangularSailContent.CLEATS;
        cleats.childModels(m, CLEAT_MODEL, WoodFamily.Particle.LOG);
        cleats.itemModels(m, CLEAT_MODEL, WoodFamily.Particle.LOG);
        cleats.forEach((wood, cleat) -> cleat(m, cleat, WoodFamily.child(wood, CLEAT_MODEL)));
    }

    /**
     * The cleat: a hand-made Blockbench horn cleat (art/models/cleat.bbmodel, design.md §4.8) on the floor with its
     * horns along the facing (north-south for {@code facing=north}), so only the block state is generated. Turned for
     * the wall and the ceiling as vanilla turns buttons (x 90 / 180, then y by the facing). The cloth is drawn by the
     * stay renderer, so {@link CleatBlock#TRIM} does not change the model.
     */
    private static void cleat(ModelContext m, CleatBlock block, ResourceLocation model) {
        PropertyDispatch.C2<AttachFace, Direction> dispatch = PropertyDispatch.properties(CleatBlock.FACE, CleatBlock.FACING);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VariantProperties.Rotation y = rotation(d);
            dispatch.select(AttachFace.FLOOR, d, Variant.variant().with(VariantProperties.MODEL, model).with(VariantProperties.Y_ROT, y));
            dispatch.select(AttachFace.WALL, d, Variant.variant().with(VariantProperties.MODEL, model).with(VariantProperties.Y_ROT, y)
                    .with(VariantProperties.X_ROT, VariantProperties.Rotation.R90));
            dispatch.select(AttachFace.CEILING, d, Variant.variant().with(VariantProperties.MODEL, model)
                    .with(VariantProperties.Y_ROT, rotation(d.getOpposite())).with(VariantProperties.X_ROT, VariantProperties.Rotation.R180));
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
    }

    /** Y rotation of the north-facing model to {@code d} (north 0, east 90, south 180, west 270). */
    private static VariantProperties.Rotation rotation(Direction d) {
        return switch (d) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }
}
