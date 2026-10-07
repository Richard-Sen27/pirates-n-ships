package com.richardsenger.piratesnships.sailing.sail;

import com.google.gson.JsonObject;
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

    private TriangularSailData() {
    }

    public static void gather(DataContributions data) {
        data.lang(lang -> lang
                .block(TriangularSailContent.CLEAT, "Cleat")
                .item(TriangularSailContent.ROPE, "Rope")
                .add(CleatBlock.KEY_NO_SAIL, "This cleat heads no sail: rig a rope stay from it down to a lower cleat, and put a third cleat straight below it")
                .add(RopeItem.KEY_TIED, "Rope tied to this cleat: now use it on a second, higher or lower cleat up to %s blocks away")
                .add(RopeItem.KEY_ELSEWHERE, "The first cleat is gone or on another ship: the rope is now tied to this cleat (stays reach %s blocks)")
                .add(RopeItem.KEY_SAME, "Use the rope on a second cleat, up to %s blocks away")
                .add(RopeItem.KEY_TOO_LONG, "Too far: the cleats are %s blocks apart, a stay reaches %s")
                .add(RopeItem.KEY_TOO_FLAT, "Too flat: the two ends of a stay must be at least %s blocks apart in height")
                .add(RopeItem.KEY_RIGGED, "Stay rigged. A cleat straight below its upper end makes the sail"));
        data.models(TriangularSailData::models);
        data.blockLoot(loot -> loot.dropSelf(TriangularSailContent.CLEAT.get()));
        data.blockTags(tags -> tags.tag(BlockTags.MINEABLE_WITH_AXE).add(TriangularSailContent.CLEAT.get()));
        // A cleat is a fist-sized fitting, mostly high up on the mast: like the yard, it gets its own small mass and volume
        // (refs/sable/wiki/Block Physics Properties.md; priority above Sable's tag definitions), so that a rig does not
        // make the tender test hull top-heavy.
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", TriangularSailContent.CLEAT.id(), () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", 0.05);
            properties.addProperty("sable:volume", 0.05);
            JsonObject json = new JsonObject();
            json.addProperty("selector", TriangularSailContent.CLEAT.id().toString());
            json.addProperty("priority", 1001);
            json.add("properties", properties);
            return json;
        });
        data.recipes(out -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, TriangularSailContent.CLEAT.get(), 2)
                    .pattern("PIP")
                    .define('P', ItemTags.PLANKS).define('I', Items.IRON_INGOT)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, TriangularSailContent.CLEAT.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, TriangularSailContent.ROPE.get())
                    .pattern("S").pattern("S").pattern("S")
                    .define('S', Items.STRING)
                    .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                    .save(out, TriangularSailContent.ROPE.id());
        });
    }

    private static void models(ModelContext m) {
        // The rope's item model is hand-made (art/models/rope.bbmodel, design.md §4.8)
        cleat(m, TriangularSailContent.CLEAT.get());
    }

    /**
     * The cleat: a hand-made Blockbench horn cleat (art/models/cleat.bbmodel, design.md §4.8) on the floor with its
     * horns along the facing (north-south for {@code facing=north}), so only the block state is generated. Turned for
     * the wall and the ceiling as vanilla turns buttons (x 90 / 180, then y by the facing). The cloth is drawn by the
     * stay renderer, so {@link CleatBlock#TRIM} does not change the model.
     */
    private static void cleat(ModelContext m, CleatBlock block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
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
