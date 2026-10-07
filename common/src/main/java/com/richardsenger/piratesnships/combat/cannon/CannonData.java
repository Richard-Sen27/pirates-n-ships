package com.richardsenger.piratesnships.combat.cannon;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.flag.ElementModel;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Datagen of the cannon (docs/design.md §8.2): model, block states, loot, tags, physical weight, recipe and lang.
 * Called from {@code CannonModule.gatherData}.
 */
public final class CannonData {

    private CannonData() {
    }

    public static void gather(DataContributions data) {
        data.lang(lang -> {
            lang.block(CannonContent.CANNON, "Cannon")
                    .add(CannonContent.CANNONBALL.get().getDescriptionId(), "Cannonball");
            for (CannonService.Outcome o : CannonService.Outcome.values()) {
                lang.add(o.key(), switch (o) {
                    case POWDER_IN -> "Powder in. Now load a cannonball";
                    case BALL_IN -> "Loaded. Use with an empty hand to fire";
                    case NEEDS_POWDER_FIRST -> "Powder first, then the ball";
                    case ALREADY_POWDERED -> "The powder is in. Load a cannonball";
                    case ALREADY_LOADED -> "Already loaded. Use with an empty hand to fire";
                    case RELOADING -> "The barrel is still hot: %s s";
                    case AIMED -> "Elevation: %s°";
                    case FIRED -> "Fire!";
                    case NOT_LOADED -> "Not loaded: put in gunpowder, then a cannonball";
                    case DISABLED -> "Cannons are disabled on this server";
                    case NOT_A_CANNON -> "That is not a cannon";
                });
            }
        });
        data.models(CannonData::models);
        data.blockLoot(loot -> loot.dropSelf(CannonContent.CANNON.get()));
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(CannonContent.CANNON.get());
            // what a ball may destroy where it is not part of a ship (ship blocks always break, see CannonRules)
            tags.tag(CannonContent.BREAKABLE).addTag(BlockTags.PLANKS).addTag(BlockTags.LOGS).addTag(BlockTags.WOOL)
                    .addTag(BlockTags.WOODEN_SLABS).addTag(BlockTags.WOODEN_STAIRS).addTag(BlockTags.WOODEN_FENCES)
                    .addTag(BlockTags.WOODEN_DOORS).addTag(BlockTags.WOODEN_TRAPDOORS).addTag(BlockTags.FENCE_GATES);
            tags.tag(CannonContent.PROOF).add(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN).addTag(BlockTags.WITHER_IMMUNE);
        });
        // Sable physics (refs/sable/wiki/Block Physics Properties.md): an iron barrel on a wooden carriage: heavy
        // (mass 4; Sable's #sable:light planks weigh 0.5), about half a block of volume.
        physics(data, CannonContent.CANNON.id(), 4.0, 0.5);
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CannonContent.CANNON.get())
                .pattern("NIN").pattern("LPL")
                .define('N', Items.IRON_INGOT).define('I', Items.IRON_BLOCK).define('L', ItemTags.LOGS).define('P', ItemTags.PLANKS)
                .unlockedBy("has_iron_block", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_BLOCK))
                .save(out, CannonContent.CANNON.id()));
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
        cannon(m, CannonContent.CANNON.get());
    }

    /**
     * The cannon with its muzzle to the north: an octagonal iron barrel (a core with two crossed bars) on two plank
     * cheeks with a bed, an iron axle and two log wheels, a muzzle ring and a knob at the breech. Turned by
     * {@link CannonBlock#FACING}; one model for every load and elevation. A placeholder until a Blockbench model.
     */
    private static void cannon(ModelContext m, Block block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        ElementModel e = new ElementModel().texture("wood", "minecraft:block/dark_oak_planks")
                .texture("wheel", "minecraft:block/stripped_spruce_log_top").texture("iron", "minecraft:block/anvil")
                .texture("axle", "minecraft:block/iron_block").texture("particle", "minecraft:block/anvil");
        // carriage: two cheeks and the bed between them
        box(e, 4, 1, 3, 6, 8, 14, "#wood");
        box(e, 10, 1, 3, 12, 8, 14, "#wood");
        box(e, 6, 2, 4, 10, 4, 14, "#wood");
        // axle and wheels
        box(e, 2, 2.5f, 8, 14, 4.5f, 10, "#axle");
        box(e, 1, 0, 6, 3, 7, 12, "#wheel");
        box(e, 13, 0, 6, 15, 7, 12, "#wheel");
        // barrel: octagon from a core and two crossed bars, axis at y = 9.5 (CannonRules.PIVOT_HEIGHT)
        box(e, 5.5f, 7.5f, 0.5f, 10.5f, 11.5f, 15, "#iron");
        box(e, 6.5f, 6.5f, 0.5f, 9.5f, 12.5f, 15, "#iron");
        box(e, 4.5f, 8.5f, 0.5f, 11.5f, 10.5f, 15, "#iron");
        // muzzle ring and breech knob
        box(e, 5, 7, 0, 11, 12, 1.5f, "#iron");
        box(e, 7, 8.5f, 15, 9, 10.5f, 16, "#iron");
        m.models().accept(model, () -> {
            JsonObject json = e.build();
            json.addProperty("parent", "minecraft:block/block"); // display transforms for the item
            return json;
        });
        PropertyDispatch.C1<Direction> dispatch = PropertyDispatch.property(CannonBlock.FACING);
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
