package com.richardsenger.piratesnships.combat.cannon;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
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
 * Datagen of the cannon (docs/design.md §8.2): block states (the models are hand-made), loot, tags, physical weight,
 * recipe and lang.
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
     * Block states only: the models are hand-made in Blockbench (art/models/cannon*.bbmodel, design.md §4.8), with the
     * muzzle to the north. {@code cannon} is the empty gun, {@code cannon_powder} adds the rammer leaning against the
     * barrel, {@code cannon_loaded} also the ball in the muzzle. Turned by {@link CannonBlock#FACING} (north is
     * unrotated).
     */
    private static void cannon(ModelContext m, Block block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        PropertyDispatch.C2<Direction, CannonLoad> dispatch =
                PropertyDispatch.properties(CannonBlock.FACING, CannonBlock.LOAD);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VariantProperties.Rotation yRot = switch (d) {
                case EAST -> VariantProperties.Rotation.R90;
                case SOUTH -> VariantProperties.Rotation.R180;
                case WEST -> VariantProperties.Rotation.R270;
                default -> VariantProperties.Rotation.R0;
            };
            for (CannonLoad load : CannonLoad.values()) {
                ResourceLocation variant =
                        load == CannonLoad.EMPTY ? model : model.withSuffix("_" + load.getSerializedName());
                dispatch.select(d, load, Variant.variant().with(VariantProperties.MODEL, variant)
                        .with(VariantProperties.Y_ROT, yRot));
            }
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
    }

}
