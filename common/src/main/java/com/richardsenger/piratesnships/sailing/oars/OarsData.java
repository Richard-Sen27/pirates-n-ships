package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import com.richardsenger.piratesnships.station.StationModule;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * Datagen of the oars (OAR1): the oar bench per wood through {@link WoodFamily} (children of the hand-made
 * {@code block/oar_bench} from {@code tools/gen_oar_models.py}, turned by {@code facing}; the item uses the block model),
 * lang, loot, tags and recipes; the oar's item model is hand-made ({@code item/oar}), so only its name and recipe are
 * written here; the rail rower's seat is kept in the ship's plot by Sable's tags.
 *
 * <p>Recipes: oar bench {@code PPP / S S} (that wood's planks over two stick legs) gives 1; oar {@code "  P" / " S " /
 * "S  "} (a blade of planks on a long diagonal loom of two sticks; the paddle is two planks over one stick).
 */
public final class OarsData {

    static final ResourceLocation BENCH = Constants.id("block/oar_bench");

    private OarsData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<OarBenchBlock> benches = OarsContent.BENCHES;
        data.lang(lang -> {
            benches.lang(lang, "Oar Bench");
            lang.item(OarsContent.OAR, "Oar")
                    .add(OarsContent.OAR_SEAT.get().getDescriptionId(), "Oar Seat")
                    .add(RowOrder.ROW.nameKey(), "row")
                    .add(RowOrder.ROW.nothingToDoKey(), "Nothing to row, captain")
                    .add(Rowing.KEY_DISABLED, "Oars are off on this server")
                    .add(Rowing.KEY_ROWING, "%s hands to the oars")
                    .add(Rowing.KEY_SHIPPED, "Oars shipped")
                    .add(Rowing.KEY_NO_HANDS, "No free hands to row")
                    .add(WhistleOrder.ROW.nameKey(), "Row")
                    .add(WhistleOrder.ROW.descriptionKey(), "Free hands take oars at the benches and the rail and row")
                    .add(WhistleOrder.ROW.toggledNameKey(), "Ship oars")
                    .add(WhistleOrder.ROW.toggledDescriptionKey(), "The rowers ship their oars and go back to their places");
        });
        data.models(OarsData::models);
        benches.dropSelf(data);
        data.blockTags(tags -> benches.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT));
        data.entityTypeTags(tags -> {
            tags.tag(StationModule.SABLE_RETAIN).add(OarsContent.OAR_SEAT.get());
            tags.tag(StationModule.SABLE_DESTROY_WITH_SUB_LEVEL).add(OarsContent.OAR_SEAT.get());
        });
        benches.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "PPP", "S S")
                .define('P', WoodRecipe.planks()).define('S', WoodRecipe.item(Items.STICK))
                .group(Constants.MOD_ID + ":oar_bench").unlockedBy("has_planks", WoodRecipe.planks()));
        data.recipes(out -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, OarsContent.OAR.get())
                    .pattern("  P").pattern(" S ").pattern("S  ")
                    .define('P', ItemTags.PLANKS).define('S', Items.STICK)
                    .unlockedBy("has_stick", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STICK))
                    .save(out, OarsContent.OAR.id());
        });
    }

    private static void models(ModelContext m) {
        WoodFamily<OarBenchBlock> benches = OarsContent.BENCHES;
        benches.childModels(m, BENCH);
        benches.itemModels(m, BENCH);
        benches.forEach((wood, block) -> {
            ResourceLocation model = WoodFamily.child(wood, BENCH);
            m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(PropertyDispatch.property(OarBenchBlock.FACING)
                    .generate(facing -> turned(model, facing))));
        });
    }

    /** {@code model} (north frame) turned to {@code d} by the block state, textures kept on the world grid. */
    private static Variant turned(ResourceLocation model, Direction d) {
        Variant v = Variant.variant().with(VariantProperties.MODEL, model);
        VariantProperties.Rotation r = switch (d) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
        return r == VariantProperties.Rotation.R0 ? v : v.with(VariantProperties.Y_ROT, r).with(VariantProperties.UV_LOCK, true);
    }
}
