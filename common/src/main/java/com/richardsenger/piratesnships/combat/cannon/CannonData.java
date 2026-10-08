package com.richardsenger.piratesnships.combat.cannon;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
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
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Datagen of the cannon (docs/design.md §8.2): block states (P2; the models are hand-made, F7g), loot, tags, physical weight,
 * recipe and lang. Called from {@code CannonModule.gatherData}.
 */
public final class CannonData {

    static final String IRON = "minecraft:block/anvil";

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
            // the crew order (G13); the whistle entry's lang is the station module's
            CannonStation.CannonOrder fire = CannonStation.CannonOrder.FIRE;
            lang.add(fire.nameKey(), "fire the cannons")
                    .add(fire.ackKey(), "Aye, firing!")
                    .add(fire.nothingToDoKey(), "The gun is not loaded, captain!")
                    .add(fire.unableKey(), "This gun won't fire, captain!");
            // crew loading (C9): the order, and its whistle entry (next to "Fire!", whose lang is the station module's)
            CannonStation.CannonOrder load = CannonStation.CannonOrder.LOAD;
            lang.add(load.nameKey(), "load the guns")
                    .add(load.ackKey(), "Aye, loading!")
                    .add(load.nothingToDoKey(), "The gun is already loaded, captain!")
                    .add(load.unableKey(), "No powder and shot within reach, captain!")
                    .add(WhistleOrder.LOAD.nameKey(), "Load!")
                    .add(WhistleOrder.LOAD.descriptionKey(), "Crew at the guns load them from powder and shot nearby");
            // NPC gunnery (WS4a): the order and its whistle entry
            CannonStation.CannonOrder atWill = CannonStation.CannonOrder.FIRE_AT_WILL;
            lang.add(atWill.nameKey(), "fire at will")
                    .add(atWill.ackKey(), "Aye, firing at will!")
                    .add(WhistleOrder.FIRE_AT_WILL.nameKey(), "Fire at will")
                    .add(WhistleOrder.FIRE_AT_WILL.descriptionKey(),
                            "Gun crews aim and fire at hostile ships in their arc until you release the crew");
        });
        data.models(CannonData::models);
        data.blockLoot(loot -> loot.add(CannonContent.CANNON.get(), masterOnly(CannonContent.CANNON.get())));
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(CannonContent.CANNON.get());
            // what a ball may destroy where it is not part of a ship (ship blocks always break, see CannonRules)
            tags.tag(CannonContent.BREAKABLE).addTag(BlockTags.PLANKS).addTag(BlockTags.LOGS).addTag(BlockTags.WOOL)
                    .addTag(BlockTags.WOODEN_SLABS).addTag(BlockTags.WOODEN_STAIRS).addTag(BlockTags.WOODEN_FENCES)
                    .addTag(BlockTags.WOODEN_DOORS).addTag(BlockTags.WOODEN_TRAPDOORS).addTag(BlockTags.FENCE_GATES);
            tags.tag(CannonContent.PROOF).add(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN).addTag(BlockTags.WITHER_IMMUNE);
        });
        // Sable physics (refs/sable/wiki/Block Physics Properties.md): an iron barrel on a wooden carriage, heavy (4 in
        // all; Sable's #sable:light planks weigh 0.5). The entry is per block id, so each of the two halves gets half:
        // mass 2 and a quarter block of volume.
        physics(data, CannonContent.CANNON.id(), 2.0, 0.25);
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, CannonContent.CANNON.get())
                .pattern("NIN").pattern("LPL")
                .define('N', Items.IRON_INGOT).define('I', Items.IRON_BLOCK).define('L', ItemTags.LOGS).define('P', ItemTags.PLANKS)
                .unlockedBy("has_iron_block", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_BLOCK))
                .save(out, CannonContent.CANNON.id()));
    }

    static void physics(DataContributions data, ResourceLocation id, double mass, double volume) {
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

    /** Like vanilla's bed loot: only the master (front) half drops the cannon, so breaking either half yields one. */
    private static LootTable.Builder masterOnly(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(block).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CannonBlock.PART, CannonPart.FRONT))))
                .when(ExplosionCondition.survivesExplosion()));
    }

    private static void models(ModelContext m) {
        cannon(m, CannonContent.CANNON.get());
    }

    static VariantProperties.Rotation yRotation(Direction d) {
        return switch (d) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }

    /**
     * The two-block cannon (P2, models F7g): the master (front) shows the whole gun, drawn across both blocks with the
     * barrel reaching one block ahead (the hand-made {@code cannon}, {@code cannon_powder}, {@code cannon_loaded} from
     * art/models/cannon*.bbmodel, muzzle to the north, model z −16..32); the rear half shows nothing ({@code cannon_rear},
     * generated: only a particle texture). Turned by {@link CannonBlock#FACING} (north is unrotated). {@code cannon} is
     * also the item's model (its display entries fit the whole gun into the slot).
     */
    private static void cannon(ModelContext m, Block block) {
        ResourceLocation base = ModelLocationUtils.getModelLocation(block);
        ResourceLocation rear = base.withSuffix("_rear");
        m.models().accept(rear, () -> {
            JsonObject textures = new JsonObject();
            textures.addProperty("particle", IRON);
            JsonObject json = new JsonObject();
            json.add("textures", textures);
            return json;
        });

        PropertyDispatch.C3<Direction, CannonLoad, CannonPart> dispatch =
                PropertyDispatch.properties(CannonBlock.FACING, CannonBlock.LOAD, CannonBlock.PART);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (CannonLoad load : CannonLoad.values()) {
                ResourceLocation front = load == CannonLoad.EMPTY ? base : base.withSuffix("_" + load.getSerializedName());
                dispatch.select(d, load, CannonPart.FRONT, Variant.variant().with(VariantProperties.MODEL, front)
                        .with(VariantProperties.Y_ROT, yRotation(d)));
                dispatch.select(d, load, CannonPart.REAR, Variant.variant().with(VariantProperties.MODEL, rear)
                        .with(VariantProperties.Y_ROT, yRotation(d)));
            }
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
    }
}
