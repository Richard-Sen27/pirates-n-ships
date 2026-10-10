package com.richardsenger.piratesnships.ship.hull.pump;

import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
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

    /** The pump's body (the block model), the parent of every wood's {@code block/<wood>_bilge_pump} (WOOD4). */
    static final ResourceLocation PUMP_MODEL = Constants.id("block/bilge_pump");
    /** The whole pump (body, rod and handle) for the items. */
    static final ResourceLocation PUMP_ITEM_MODEL = Constants.id("block/bilge_pump_item");
    /** WOOD4: every bilge pump, the selector of the pump's Sable weight. */
    public static final TagKey<Block> PUMPS = TagKey.create(Registries.BLOCK, Constants.id("bilge_pumps"));

    private HullRepairData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<BilgePumpBlock> pumps = HullRepairContent.BILGE_PUMPS;
        data.lang(lang -> pumps.lang(lang, "Bilge Pump")); // WOOD4: "Dark Oak Bilge Pump" (the legacy pump), ...
        data.lang(lang -> lang
                .block(HullRepairContent.HULL_PATCH_BLOCK, "Hull Patch")
                .add(BilgePumps.KEY_PUMPING, "Pumping: %s blocks of water left")
                .add(BilgePumps.KEY_DRY, "The bilge is dry")
                .add(BilgePumps.KEY_NO_COMPARTMENT, "No hold within %s blocks below the pump")
                .add(BilgePumps.KEY_NOT_ON_SHIP, "Pumps only work on an assembled ship")
                .add(BilgePumps.KEY_DISABLED, "Bilge pumps are disabled on this server")
                .add(HullPatchItem.KEY_NOT_A_BREACH, "Use the patch on a hole in the hull")
                .add(HullPatchItem.KEY_NOT_ON_SHIP, "Patches only work on an assembled ship")
                .add(HullPatchItem.KEY_BLOCKED, "Something is stuck in this hole")
                .add(HullPatchItem.KEY_DISABLED, "Hull patches are disabled on this server"));
        data.models(HullRepairData::models);
        data.blockLoot(loot -> loot.dropSelf(HullRepairContent.HULL_PATCH_BLOCK.get()));
        pumps.dropSelf(data);
        data.blockTags(tags -> {
            pumps.tag(tags, BlockTags.MINEABLE_WITH_AXE, PUMPS);
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(HullRepairContent.HULL_PATCH_BLOCK.get());
            // a full cube is watertight anyway; the tag says it outright, whatever a pack does to the shape rules
            tags.tag(HullTags.WATERTIGHT).add(HullRepairContent.HULL_PATCH_BLOCK.get());
        });
        // Sable physics (refs/sable/wiki/Block Physics Properties.md): the patch weighs and displaces like a plank
        // (Sable's #sable:light, mass 0.5, volume 1); the pump is a post with an iron spout, a bit heavier than its volume.
        physics(data, HullRepairContent.HULL_PATCH_BLOCK.id(), 0.5, 1.0);
        // WOOD4: the entry keeps its file (bilge_pump) and selects every member through the family's tag (a mod wood's
        // member as an optional entry): Sable's loader reads no load conditions, so a per-member file of a mod's wood
        // would log an unknown block without the mod
        physics(data, HullRepairContent.BILGE_PUMP.id(), "#" + PUMPS.location(), 0.4, 0.3);
        data.recipes(out -> {
            // Pitch: charcoal (or coal) is the vanilla stand-in for pine tar, which is made by charring pine wood. It is
            // at hand from the first day, so a leak can be patched early; two boards and the pitch make two patches.
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, HullRepairContent.HULL_PATCH.get(), 2)
                    .requires(ItemTags.PLANKS).requires(ItemTags.PLANKS).requires(ItemTags.COALS)
                    .unlockedBy("has_coal", InventoryChangeTrigger.TriggerInstance.hasItems(Items.CHARCOAL))
                    .save(out, HullRepairContent.HULL_PATCH.id());
        });
        // A bucket as the pump barrel, a stick as the handle, that wood's planks as the stand (WOOD4; was any planks).
        pumps.recipe(data, WoodRecipe.shaped(RecipeCategory.TRANSPORTATION, 1, " S ", "PBP", " P ")
                .define('S', WoodRecipe.item(Items.STICK)).define('P', WoodRecipe.planks()).define('B', WoodRecipe.item(Items.BUCKET))
                .group(Constants.MOD_ID + ":bilge_pump").unlockedBy("has_bucket", WoodRecipe.item(Items.BUCKET)));
    }

    private static void physics(DataContributions data, ResourceLocation id, double mass, double volume) {
        physics(data, id, id.toString(), mass, volume);
    }

    /** The Sable entry {@code id} for {@code selector} (a block id, or {@code #} and a tag). */
    private static void physics(DataContributions data, ResourceLocation id, String selector, double mass, double volume) {
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", id, () -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("sable:mass", mass);
            properties.addProperty("sable:volume", volume);
            JsonObject json = new JsonObject();
            json.addProperty("selector", selector);
            json.addProperty("priority", 1001);
            json.add("properties", properties);
            return json;
        });
    }

    private static void models(ModelContext m) {
        // The hull patch's block and item models are hand-made (art/models/hull_patch.bbmodel and
        // hull_patch_item.bbmodel, design.md §4.8): datagen writes only the block state
        Block patch = HullRepairContent.HULL_PATCH_BLOCK.get();
        m.blockStates().accept(MultiVariantGenerator.multiVariant(patch,
                Variant.variant().with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(patch))));
        m.handMadeItem(HullRepairContent.HULL_PATCH.get());
        pumps(m);
    }

    /**
     * The bilge pump: a hand-made Blockbench model (art/models/bilge_pump.bbmodel, design.md §4.8) with its spout and
     * its handle to the north and the wood variables (WOOD4), so datagen writes one child per wood
     * ({@code block/<wood>_bilge_pump}, the body), its block state turned by {@link BilgePumpBlock#FACING} and the item
     * model. PMP1: the handle and the rod are drawn by client/PumpHandleRenderer from block/bilge_pump_handle and
     * block/bilge_pump_rod (iron and the oak accent, the same in every wood); the item shows the whole pump
     * (block/bilge_pump_item).
     */
    private static void pumps(ModelContext m) {
        WoodFamily<BilgePumpBlock> pumps = HullRepairContent.BILGE_PUMPS;
        pumps.childModels(m, PUMP_MODEL);
        pumps.itemModels(m, PUMP_ITEM_MODEL);
        pumps.forEach((wood, block) -> {
            ResourceLocation model = WoodFamily.child(wood, PUMP_MODEL);
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
        });
    }
}
