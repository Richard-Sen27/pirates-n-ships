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
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
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
        // Rejoining split pieces with the Shipwright's Toolkit (RS2)
        CommonEvents.LEVEL_TICK_END.register(ShipRejoin::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> ShipRejoin.onServerStopped());
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
            lang.add(ShipInfoCommand.KEY_NONE, "No ship here");
            lang.add(ShipInfoCommand.KEY_SHIP, "Ship %s, name %s, origin %s");
            lang.add(ShipInfoCommand.KEY_WRECK, "Wreck %s of %s, origin %s");
            lang.add(ShipInfoCommand.KEY_UNNAMED, "(unnamed)");
            ShipTemplateCommands.lang(lang);
            rejoinLang(lang);
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
        // carpenters_hammer, saw, nails and shipwright_toolkit are hand-made Blockbench item models (ART1c, art/models/)
        data.recipes(AssemblyModule::rejoinRecipes);
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

    private static void rejoinLang(LangBuilder lang) {
        lang.item(AssemblyContent.CARPENTERS_HAMMER, "Carpenter's Hammer")
                .item(AssemblyContent.SAW, "Saw")
                .item(AssemblyContent.NAILS, "Nails")
                .item(AssemblyContent.SHIPWRIGHT_TOOLKIT, "Shipwright's Toolkit")
                .add(ShipwrightToolkitItem.KEY_TOOLTIP, "Sneak-use on the piece to keep, then use on a split-off piece lying against it to nail it back on")
                .add(ShipwrightToolkitItem.KEY_MARKED, "Marked for repair: %s")
                .add(ShipwrightToolkitItem.KEY_MARKED_UNNAMED, "Marked for repair: a piece without a name");
        lang.add(ShipRejoin.Outcome.MARKED.key(), "Marked for repair: %s. Now use the toolkit on the piece to join to it")
                .add(ShipRejoin.Outcome.STARTED.key(), "Hammering %s blocks into place…")
                .add(ShipRejoin.Outcome.REJOINED.key(), "Rejoined %s blocks")
                .add(ShipRejoin.Outcome.DISABLED.key(), "Rejoining ship pieces is disabled on this server")
                .add(ShipRejoin.Outcome.NOT_A_SHIP.key(), "This is not part of a ship")
                .add(ShipRejoin.Outcome.NO_MARK.key(), "First sneak-use the toolkit on the piece to keep")
                .add(ShipRejoin.Outcome.SAME_PIECE.key(), "This is the marked piece. Use the toolkit on the piece to join to it")
                .add(ShipRejoin.Outcome.MARK_GONE.key(), "The marked piece is gone. Mark the piece to keep again")
                .add(ShipRejoin.Outcome.BUSY.key(), "Still hammering")
                .add(ShipRejoin.Outcome.NO_TOOLKIT.key(), "You put the toolkit away")
                .add(ShipRejoin.Outcome.DIFFERENT_SHIP.key(), "These pieces belong to a different ship")
                .add(ShipRejoin.Outcome.TOO_BIG.key(), "This piece is too big to nail on: %s blocks, at most %s. A shipwright can join bigger halves")
                .add(ShipRejoin.Outcome.NOT_ALIGNED.key(), "The pieces are not lined up: bring them within %s° and %s blocks of their places")
                .add(ShipRejoin.Outcome.ADD_PLANKS.key(), "Add planks to bridge the gap")
                .add(ShipRejoin.Outcome.TOO_FAR.key(), "Bring the pieces together")
                .add(ShipRejoin.Outcome.OVERLAP.key(), "The pieces overlap: something is in the way")
                .add(ShipRejoin.Outcome.OUT_OF_PLOT.key(), "This piece reaches too far from the ship to join it")
                .add(ShipRejoin.Outcome.NO_NAILS.key(), "You need %s nails")
                .add(ShipRejoin.Outcome.FAILED.key(), "Rejoining failed, see the server log");
    }

    private static void rejoinRecipes(net.minecraft.data.recipes.RecipeOutput out) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, AssemblyContent.CARPENTERS_HAMMER.get())
                .pattern("II").pattern(" S")
                .define('I', Items.IRON_INGOT).define('S', Items.STICK)
                .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                .save(out, AssemblyContent.CARPENTERS_HAMMER.id());
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, AssemblyContent.SAW.get())
                .pattern("SI").pattern("S ")
                .define('I', Items.IRON_INGOT).define('S', Items.STICK)
                .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                .save(out, AssemblyContent.SAW.id());
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, AssemblyContent.NAILS.get(), 8)
                .requires(Items.IRON_NUGGET, 3)
                .unlockedBy("has_iron_nugget", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_NUGGET))
                .save(out, AssemblyContent.NAILS.id());
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, AssemblyContent.SHIPWRIGHT_TOOLKIT.get())
                .requires(AssemblyContent.CARPENTERS_HAMMER.get()).requires(AssemblyContent.SAW.get())
                .requires(AssemblyContent.NAILS.get()).requires(Items.LEATHER)
                .unlockedBy("has_hammer", InventoryChangeTrigger.TriggerInstance.hasItems(AssemblyContent.CARPENTERS_HAMMER.get()))
                .save(out, AssemblyContent.SHIPWRIGHT_TOOLKIT.id());
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(AssemblyGameTests.class, SplitGameTests.class, ShipTemplateGameTests.class, RejoinGameTests.class);
    }
}
