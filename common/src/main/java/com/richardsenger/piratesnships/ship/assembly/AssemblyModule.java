package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult.Outcome;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.template.ShipTemplateCommands;
import com.richardsenger.piratesnships.ship.template.ShipTemplateGameTests;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import java.util.List;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Module {@code ship.assembly}: the helm, assembly into a Sable sub-level and disassembly back to blocks (spike 1), and
 * ship templates ({@code ship.template}: prebuilt ships placed by command, later by the shipwright). */
public final class AssemblyModule implements ModModule {

    @Override
    public String id() {
        return "ship.assembly";
    }

    @Override
    public void registerConfig() {
        AssemblyConfig.init();
    }

    @Override
    public void registerContent() {
        AssemblyContent.init();
        ShipTemplates.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> ShipTemplateCommands.register(dispatcher));
        // A ship destroyed for good (removed, emptied, /sable remove) loses its record; a plain unload keeps it.
        SableShips.onShipRemoved((level, id, destroyed) -> {
            if (destroyed) {
                ShipRegistry.get(level.getServer()).remove(id);
            }
        });
        // A ship that Sable splits: keeper, wrecks, tiny pieces (RS1)
        ShipSplits.register();
        CommonEvents.LEVEL_TICK_END.register(ShipSplits::onLevelTick);
        CommonEvents.SERVER_TICK_END.register(server -> ShipSplits.onServerTick());
        CommonEvents.SERVER_STOPPED.register(server -> ShipSplits.onServerStopped());
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> ShipInfoCommand.register(dispatcher));
    }

    @Override
    public void gatherData(DataContributions data) {
        Block helm = AssemblyContent.HELM.get();
        data.lang(lang -> {
            lang.block(AssemblyContent.HELM, "Helm");
            lang.add(Outcome.ASSEMBLED.key(), "Ship assembled: %s blocks");
            lang.add(Outcome.DISASSEMBLED.key(), "Ship disassembled: %s blocks placed back");
            lang.add(Outcome.NAMED.key(), "Ship named \"%s\"");
            lang.add(Outcome.DISABLED.key(), "Ship assembly is disabled on this server");
            lang.add(Outcome.TOO_MANY_BLOCKS.key(), "Too many connected blocks: a ship may have at most %s. Is the hull touching a dock or building?");
            lang.add(Outcome.NOTHING_TO_ASSEMBLE.key(), "Nothing to assemble: no ship blocks are connected to the helm");
            lang.add(Outcome.MOVING.key(), "The ship is still moving (%s m/s). Wait until it lies still");
            lang.add(Outcome.NOT_LEVEL.key(), "The ship is tilted %s°. It must be within %s° of level");
            lang.add(Outcome.OBSTRUCTED.key(), "Something is in the way at %s %s %s");
            lang.add(Outcome.OUT_OF_WORLD.key(), "The ship would end up outside the world at %s %s %s");
            lang.add(Outcome.NO_SHIP.key(), "This helm is not part of an assembled ship");
            lang.add(HelmBlock.KEY_DISASSEMBLE_HINT, "Sneak-use the helm with an empty hand to disassemble the ship");
            lang.add(Outcome.FAILED.key(), "Assembly failed, see the server log");
            ShipTemplateCommands.lang(lang);
            lang.add(ShipInfoCommand.KEY_NONE, "No ship here");
            lang.add(ShipInfoCommand.KEY_SHIP, "Ship %s, name %s, origin %s");
            lang.add(ShipInfoCommand.KEY_WRECK, "Wreck %s of %s, origin %s");
            lang.add(ShipInfoCommand.KEY_UNNAMED, "(unnamed)");
        });
        data.models(m -> {
            // Hand-made Blockbench model (art/models/helm.bbmodel, design.md §4.8): only the block state is generated.
            // The wheel faces north; the item model delegates to the block model.
            ResourceLocation model = ModelLocationUtils.getModelLocation(helm);
            m.blockStates().accept(MultiVariantGenerator.multiVariant(helm, Variant.variant().with(VariantProperties.MODEL, model))
                    .with(PropertyDispatch.property(HelmBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))));
        });
        data.definitions(ShipTemplates.TYPE, ShipTemplates.DEFAULTS);
        data.blockLoot(loot -> loot.dropSelf(helm));
        data.recipes(out -> ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, helm)
                .pattern("S S").pattern(" P ").pattern("S S")
                .define('S', Items.STICK).define('P', ItemTags.PLANKS)
                .unlockedBy("has_stick", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STICK))
                .save(out));
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(helm);
            tags.tag(ShipBlockRule.TERRAIN)
                    .add(Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL, Blocks.CLAY, Blocks.BEDROCK, Blocks.ICE,
                            Blocks.PACKED_ICE, Blocks.FROSTED_ICE, Blocks.MAGMA_BLOCK, Blocks.SEA_PICKLE, Blocks.KELP,
                            Blocks.KELP_PLANT, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.SOUL_SAND, Blocks.SOUL_SOIL,
                            Blocks.END_STONE, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE,
                            Blocks.SCULK, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST);
            // Vanilla tags are referenced as optional: the shared tags provider cannot see vanilla's tag files, so a
            // required reference fails validation. They always exist at runtime. c:ores covers modded ores.
            for (TagKey<Block> natural : List.of(BlockTags.DIRT, BlockTags.SAND, BlockTags.BASE_STONE_OVERWORLD,
                    BlockTags.BASE_STONE_NETHER, BlockTags.NYLIUM, BlockTags.COAL_ORES, BlockTags.IRON_ORES,
                    BlockTags.COPPER_ORES, BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES,
                    BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES, BlockTags.LEAVES, BlockTags.REPLACEABLE,
                    BlockTags.CORALS, BlockTags.CORAL_BLOCKS, BlockTags.WALL_CORALS, BlockTags.SNOW)) {
                tags.tag(ShipBlockRule.TERRAIN).addOptionalTag(natural.location());
            }
            tags.tag(ShipBlockRule.TERRAIN).addOptionalTag(ResourceLocation.fromNamespaceAndPath("c", "ores"));
            tags.tag(ShipBlockRule.NEVER_ASSEMBLE)
                    .add(Blocks.BEDROCK, Blocks.BARRIER, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY,
                            Blocks.NETHER_PORTAL, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
                            Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW, Blocks.REINFORCED_DEEPSLATE);
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(AssemblyGameTests.class, ShipTemplateGameTests.class, SplitGameTests.class);
    }
}
