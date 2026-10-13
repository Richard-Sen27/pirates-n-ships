package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult.Outcome;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.SableSplits;
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
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Module {@code ship.assembly}: the helm, assembly into a Sable sub-level and disassembly back to blocks (spike 1), and
 * ship templates ({@code ship.template}: prebuilt ships placed by command, later by the shipwright). */
public final class AssemblyModule implements ModModule {

    /** The helm's hand-made pedestal, the parent of every wood's {@code block/<wood>_helm} (WOOD4). */
    public static final ResourceLocation HELM_MODEL = Constants.id("block/helm");

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
        // A ship destroyed for good (removed, emptied, /sable remove) loses its record; a plain unload keeps it. A ship
        // that Sable emptied by moving all its blocks into new bodies this tick keeps it until the split is processed:
        // the record follows the body that keeps the identity (HL1, ShipSplits.process).
        SableShips.onShipRemoved((level, id, destroyed) -> {
            if (destroyed && !SableSplits.isSplitPending(level, id)) {
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
        WoodFamily<HelmBlock> helms = AssemblyContent.HELMS;
        data.lang(lang -> {
            helms.lang(lang, "Helm"); // WOOD4: "Dark Oak Helm" (the legacy helm), "Spruce Helm", ...
            lang.add(Outcome.ASSEMBLED.key(), "Ship assembled: %s blocks");
            lang.add(Outcome.DISASSEMBLED.key(), "Disassembled: %s blocks placed back");
            lang.add(Outcome.NAMED.key(), "Ship named \"%s\"");
            lang.add(Outcome.DISABLED.key(), "Ship assembly is disabled on this server");
            lang.add(Outcome.TOO_MANY_BLOCKS.key(), "Over %s blocks: touching land?");
            lang.add(Outcome.NOTHING_TO_ASSEMBLE.key(), "Nothing to assemble: no blocks attached");
            lang.add(Outcome.MOVING.key(), "Still moving (%s m/s): wait until still");
            lang.add(Outcome.NOT_LEVEL.key(), "Tilted %s°, must be under %s°");
            lang.add(Outcome.OBSTRUCTED.key(), "Something is in the way at %s %s %s");
            lang.add(Outcome.OUT_OF_WORLD.key(), "Would leave the world at %s %s %s");
            lang.add(Outcome.NO_SHIP.key(), "This helm is on no assembled ship");
            lang.add(HelmBlock.KEY_DISASSEMBLE_HINT, "Sneak-use empty-handed to disassemble");
            lang.add(Outcome.ADOPTED.key(), "This helm now steers: %s blocks");
            lang.add(ShipHelm.KEY_ATTACHED, "This helm now steers %s");
            lang.add(ShipHelm.KEY_UNNAMED, "the ship");
            lang.add(ShipHelm.KEY_SECOND, "Steered from the ship's other helm");
            lang.add(Outcome.FAILED.key(), "Assembly failed, see the server log");
            lang.add(ShipInfoCommand.KEY_NONE, "No ship here");
            lang.add(ShipInfoCommand.KEY_SHIP, "Ship %s, name %s, origin %s");
            lang.add(ShipInfoCommand.KEY_WRECK, "Wreck %s of %s, origin %s");
            lang.add(ShipInfoCommand.KEY_UNNAMED, "(unnamed)");
            ShipTemplateCommands.lang(lang);
            rejoinLang(lang);
        });
        data.models(m -> {
            // Hand-made Blockbench model (art/models/helm.bbmodel, design.md §4.8) with the wood variables (WOOD4): one
            // child per wood (block/<wood>_helm) and its block state. The wheel faces north; the wheel and the item
            // models are sailing.helm's (HelmSetup).
            helms.childModels(m, HELM_MODEL);
            helms.forEach((wood, helm) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(helm,
                            Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, HELM_MODEL)))
                    .with(PropertyDispatch.property(HelmBlock.FACING)
                            .select(Direction.NORTH, Variant.variant())
                            .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                            .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                            .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270)))));
        });
        // carpenters_hammer, saw, nails and shipwright_toolkit are hand-made Blockbench item models (ART1c, art/models/)
        data.recipes(AssemblyModule::rejoinRecipes);
        data.definitions(ShipTemplates.TYPE, ShipTemplates.DEFAULTS);
        helms.dropSelf(data);
        // WOOD4: that wood's planks (was any planks); the dark oak helm keeps the recipe id helm
        helms.recipe(data, WoodRecipe.shaped(RecipeCategory.TRANSPORTATION, 1, "S S", " P ", "S S")
                .define('S', WoodRecipe.item(Items.STICK)).define('P', WoodRecipe.planks())
                .group(Constants.MOD_ID + ":helm").unlockedBy("has_stick", WoodRecipe.item(Items.STICK)));
        data.blockTags(tags -> {
            helms.tag(tags, BlockTags.MINEABLE_WITH_AXE);
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
        lang.add(ShipRejoin.Outcome.MARKED.key(), "Marked %s: now use on the other piece")
                .add(ShipRejoin.Outcome.STARTED.key(), "Hammering %s blocks into place…")
                .add(ShipRejoin.Outcome.DISABLED.key(), "Rejoining is off on this server")
                .add(ShipRejoin.Outcome.NOT_A_SHIP.key(), "This is not part of a ship")
                .add(ShipRejoin.Outcome.NO_MARK.key(), "Sneak-use the piece to keep first")
                .add(ShipRejoin.Outcome.SAME_PIECE.key(), "This is the marked piece: use the other")
                .add(ShipRejoin.Outcome.MARK_GONE.key(), "The marked piece is gone: mark again")
                .add(ShipRejoin.Outcome.BUSY.key(), "Still hammering")
                .add(ShipRejoin.Outcome.NO_TOOLKIT.key(), "You put the toolkit away")
                .add(ShipRejoin.Outcome.DIFFERENT_SHIP.key(), "These pieces belong to a different ship")
                .add(ShipRejoin.Outcome.TOO_BIG.key(), "Too big to nail: %s blocks, at most %s")
                .add(ShipRejoin.Outcome.NOT_ALIGNED.key(), "Not lined up: within %s° and %s blocks")
                .add(ShipRejoin.Outcome.ADD_PLANKS.key(), "Add planks to bridge the gap")
                .add(ShipRejoin.Outcome.TOO_FAR.key(), "Bring the pieces together")
                .add(ShipRejoin.Outcome.OVERLAP.key(), "The pieces overlap")
                .add(ShipRejoin.Outcome.OUT_OF_PLOT.key(), "This piece reaches too far to join")
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
        return List.of(AssemblyGameTests.class, SplitGameTests.class, ShipTemplateGameTests.class, RejoinGameTests.class,
                HelmReplaceGameTests.class, com.richardsenger.piratesnships.ship.template.BrigGameTests.class,
                com.richardsenger.piratesnships.ship.template.BrigTrimGameTests.class);
    }
}
