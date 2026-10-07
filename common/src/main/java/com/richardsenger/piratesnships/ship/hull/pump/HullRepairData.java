package com.richardsenger.piratesnships.ship.hull.pump;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.flag.ElementModel;
import com.richardsenger.piratesnships.ship.hull.HullTags;
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
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Datagen of the bilge pump and the hull patch (docs/design.md §4.5): models, block states, loot, tags, physical
 * weight, recipes and lang. Called from {@code HullModule.gatherData}.
 */
public final class HullRepairData {

    private HullRepairData() {
    }

    public static void gather(DataContributions data) {
        data.lang(lang -> lang
                .block(HullRepairContent.BILGE_PUMP, "Bilge Pump")
                .block(HullRepairContent.HULL_PATCH_BLOCK, "Hull Patch")
                .add(BilgePumps.KEY_PUMPING, "Pumping: %s blocks of water left")
                .add(BilgePumps.KEY_DRY, "The bilge is dry")
                .add(BilgePumps.KEY_NO_COMPARTMENT, "The pump draws air: no hold within %s blocks below it")
                .add(BilgePumps.KEY_NOT_ON_SHIP, "A bilge pump only works on an assembled ship")
                .add(BilgePumps.KEY_DISABLED, "Bilge pumps are disabled on this server")
                .add(HullPatchItem.KEY_NOT_A_BREACH, "Use the patch on a hole in the hull")
                .add(HullPatchItem.KEY_NOT_ON_SHIP, "Hull patches only close holes in an assembled ship")
                .add(HullPatchItem.KEY_BLOCKED, "Something is stuck in this hole")
                .add(HullPatchItem.KEY_DISABLED, "Hull patches are disabled on this server"));
        data.models(HullRepairData::models);
        data.blockLoot(loot -> {
            loot.dropSelf(HullRepairContent.BILGE_PUMP.get());
            loot.dropSelf(HullRepairContent.HULL_PATCH_BLOCK.get());
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(HullRepairContent.BILGE_PUMP.get(), HullRepairContent.HULL_PATCH_BLOCK.get());
            // a full cube is watertight anyway; the tag says it outright, whatever a pack does to the shape rules
            tags.tag(HullTags.WATERTIGHT).add(HullRepairContent.HULL_PATCH_BLOCK.get());
        });
        // Sable physics (refs/sable/wiki/Block Physics Properties.md): the patch weighs and displaces like a plank
        // (Sable's #sable:light, mass 0.5, volume 1); the pump is a post with an iron spout, a bit heavier than its volume.
        physics(data, HullRepairContent.HULL_PATCH_BLOCK.id(), 0.5, 1.0);
        physics(data, HullRepairContent.BILGE_PUMP.id(), 0.4, 0.3);
        data.recipes(out -> {
            // Pitch: charcoal (or coal) is the vanilla stand-in for pine tar, which is made by charring pine wood. It is
            // at hand from the first day, so a leak can be patched early; two boards and the pitch make two patches.
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, HullRepairContent.HULL_PATCH.get(), 2)
                    .requires(ItemTags.PLANKS).requires(ItemTags.PLANKS).requires(ItemTags.COALS)
                    .unlockedBy("has_coal", InventoryChangeTrigger.TriggerInstance.hasItems(Items.CHARCOAL))
                    .save(out, HullRepairContent.HULL_PATCH.id());
            // A bucket as the pump barrel, a stick as the handle, planks as the stand.
            ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, HullRepairContent.BILGE_PUMP.get())
                    .pattern(" S ").pattern("PBP").pattern(" P ")
                    .define('S', Items.STICK).define('P', ItemTags.PLANKS).define('B', Items.BUCKET)
                    .unlockedBy("has_bucket", InventoryChangeTrigger.TriggerInstance.hasItems(Items.BUCKET))
                    .save(out, HullRepairContent.BILGE_PUMP.id());
        });
    }

    private static void physics(DataContributions data, ResourceLocation id, double mass, double volume) {
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", id, () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", mass);
            properties.addProperty("sable:volume", volume);
            JsonObject json = new JsonObject();
            json.addProperty("selector", id.toString());
            json.addProperty("priority", 1001);
            json.add("properties", properties);
            return json;
        });
    }

    private static void models(ModelContext m) {
        m.blocks().createTrivialCube(HullRepairContent.HULL_PATCH_BLOCK.get());
        pump(m, HullRepairContent.BILGE_PUMP.get());
    }

    /**
     * The bilge pump with its spout to the north: a plank foot, a log barrel with two iron bands, an iron spout and a
     * wooden handle on a pivot. Turned by {@link BilgePumpBlock#FACING}. A placeholder until a Blockbench model.
     */
    private static void pump(ModelContext m, Block block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        ElementModel e = new ElementModel().texture("wood", "minecraft:block/dark_oak_planks")
                .texture("barrel", "minecraft:block/stripped_spruce_log").texture("iron", "minecraft:block/iron_block")
                .texture("particle", "minecraft:block/stripped_spruce_log");
        box(e, 3, 0, 3, 13, 2, 13, "#wood");
        box(e, 5, 2, 5, 11, 14, 11, "#barrel");
        box(e, 4.5f, 4, 4.5f, 11.5f, 5, 11.5f, "#iron");
        box(e, 4.5f, 11, 4.5f, 11.5f, 12, 11.5f, "#iron");
        box(e, 7, 10, 2, 9, 12, 5, "#iron");
        box(e, 7, 14, 7, 9, 16, 9, "#iron");
        box(e, 7.5f, 15, 3, 8.5f, 16, 14, "#wood");
        m.models().accept(model, () -> {
            JsonObject json = e.build();
            json.addProperty("parent", "minecraft:block/block"); // display transforms for the item
            return json;
        });
        PropertyDispatch.C1<Direction> dispatch = PropertyDispatch.property(BilgePumpBlock.FACING);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            dispatch.select(d, Variant.variant().with(VariantProperties.MODEL, model).with(VariantProperties.Y_ROT, switch (d) {
                case EAST -> VariantProperties.Rotation.R90;
                case SOUTH -> VariantProperties.Rotation.R180;
                case WEST -> VariantProperties.Rotation.R270;
                default -> VariantProperties.Rotation.R0;
            }));
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
    }

    /** A cuboid with all six faces, each mapped to its own projection on the texture. */
    private static void box(ElementModel e, float x0, float y0, float z0, float x1, float y1, float z1, String tex) {
        e.element(x0, y0, z0, x1, y1, z1)
                .face(ElementModel.Face.DOWN, x0, z0, x1, z1, tex)
                .face(ElementModel.Face.UP, x0, z0, x1, z1, tex)
                .face(ElementModel.Face.NORTH, 16 - x1, 16 - y1, 16 - x0, 16 - y0, tex)
                .face(ElementModel.Face.SOUTH, x0, 16 - y1, x1, 16 - y0, tex)
                .face(ElementModel.Face.WEST, z0, 16 - y1, z1, 16 - y0, tex)
                .face(ElementModel.Face.EAST, 16 - z1, 16 - y1, 16 - z0, 16 - y0, tex)
                .end();
    }
}
