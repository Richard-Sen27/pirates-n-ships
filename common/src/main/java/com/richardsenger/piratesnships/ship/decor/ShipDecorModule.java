package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.flag.FlagCommands;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.decor.flag.FlagConfig;
import com.richardsenger.piratesnships.ship.decor.flag.FlagData;
import com.richardsenger.piratesnships.ship.decor.flag.FlagGameTests;
import com.richardsenger.piratesnships.ship.decor.flag.FlagModelGameTests;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFooting;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.Condition;
import net.minecraft.data.models.blockstates.MultiPartGenerator;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * The {@code ship.decor} module: figureheads, name board, flagpole, cargo crate and cargo barrel. The four figureheads
 * (skull with crossed bones, spread-winged eagle, maned lion with its paws on a scroll, mermaid with her tail curling
 * under the bow), the name board (NAME2, the nameplate's successor) and the flagpole use hand-made Blockbench models ({@code art/models/}, design.md §4.8;
 * datagen writes only their block states). Flags (items, the flagpole's block entity, block state and look, config,
 * commands) are in the {@code flag} sub-package. The ART2 decor blocks (lantern, bell, rope coil, stern window, chart
 * table, sea cot) have their datagen in {@link DecorData}; the railing family (RAIL1, {@code railing}, registered per
 * wood through {@code wood.WoodFamily}) in {@code railing.RailingData}.
 */
public final class ShipDecorModule implements ModModule {

    @Override
    public String id() {
        return "ship.decor";
    }

    @Override
    public void registerConfig() {
        FlagConfig.init();
        com.richardsenger.piratesnships.ship.decor.flag.FlagVisualsConfig.init(); // VIS1a, client section
        ShipIdentityConfig.init();
        DecorConfig.init();
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.ship.decor.client.ShipDecorClient.init();
    }

    @Override
    public void registerContent() {
        ShipDecor.init();
        Flags.init();
        com.richardsenger.piratesnships.ship.decor.railing.RailingContent.init(); // RAIL1
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> FlagCommands.register(dispatcher));
        ShipAllegiance.register(); // FL2: refresh the keeper's flag after a split or rejoin
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(ShipDecor.FIGUREHEAD_MERMAID, "Mermaid Figurehead")
                .block(ShipDecor.FIGUREHEAD_LION, "Lion Figurehead")
                .block(ShipDecor.FIGUREHEAD_EAGLE, "Eagle Figurehead")
                .block(ShipDecor.FIGUREHEAD_SKULL, "Skull Figurehead")
                .block(ShipDecor.NAMEPLATE, "Name Board")
                .add(NameplateText.KEY_WRECK_OF, "Wreck of %s")
                .block(ShipDecor.FLAGPOLE, "Flagpole")
                .block(ShipDecor.FLAG_PEDESTAL, "Flag Pedestal")
                .block(ShipDecor.CARGO_CRATE, "Cargo Crate")
                .block(ShipDecor.CARGO_BARREL, "Cargo Barrel"));
        data.models(m -> {
            for (RegistryEntry<Block, FigureheadBlock> f : ShipDecor.figureheads()) figurehead(m, f.get());
            nameBoard(m, ShipDecor.NAMEPLATE.get());
            // flagpole: model and block state in FlagData
            flagPedestal(m, ShipDecor.FLAG_PEDESTAL.get());
            // hand-made Blockbench models (art/models/cargo_{crate,barrel}.bbmodel, design.md §4.8): only the block
            // states are generated; the load does not show (the load property is physics only, CW1: every value uses the model)
            for (Block cargo : List.of(ShipDecor.CARGO_CRATE.get(), ShipDecor.CARGO_BARREL.get())) {
                m.blockStates().accept(MultiVariantGenerator.multiVariant(cargo, Variant.variant()
                        .with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(cargo))));
            }
        });
        FlagData.gather(data);
        DecorData.gather(data);
        com.richardsenger.piratesnships.ship.decor.railing.RailingData.gather(data); // RAIL1
        data.blockLoot(loot -> {
            for (RegistryEntry<Block, FigureheadBlock> f : ShipDecor.figureheads()) loot.dropSelf(f.get());
            loot.dropSelf(ShipDecor.NAMEPLATE.get());
            loot.dropSelf(ShipDecor.FLAGPOLE.get());
            loot.dropSelf(ShipDecor.FLAG_PEDESTAL.get());
            loot.add(ShipDecor.CARGO_CRATE.get(), com.richardsenger.piratesnships.trade.cargo.CargoContainers.lootTable(ShipDecor.CARGO_CRATE.get()));
            loot.add(ShipDecor.CARGO_BARREL.get(), com.richardsenger.piratesnships.trade.cargo.CargoContainers.lootTable(ShipDecor.CARGO_BARREL.get()));
        });
        data.blockTags(tags -> {
            for (RegistryEntry<Block, FigureheadBlock> f : ShipDecor.figureheads()) {
                tags.tag(BlockTags.MINEABLE_WITH_AXE).add(f.get());
                tags.tag(SableWeightTags.LIGHT).add(f.get());
            }
            tags.tag(BlockTags.MINEABLE_WITH_AXE).add(ShipDecor.NAMEPLATE.get(), ShipDecor.FLAGPOLE.get(), ShipDecor.FLAG_PEDESTAL.get(),
                    ShipDecor.CARGO_CRATE.get(), ShipDecor.CARGO_BARREL.get());
            // Thin blocks: like ladders and fences in Sable's own tags
            tags.tag(SableWeightTags.SUPER_LIGHT).add(ShipDecor.NAMEPLATE.get(), ShipDecor.FLAGPOLE.get(), ShipDecor.FLAG_PEDESTAL.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(ShipDecor.NAMEPLATE.get(), ShipDecor.FLAGPOLE.get(), ShipDecor.FLAG_PEDESTAL.get());
            // Wooden containers: like vanilla barrels and chests
            tags.tag(SableWeightTags.LIGHT).add(ShipDecor.CARGO_CRATE.get(), ShipDecor.CARGO_BARREL.get());
        });
        data.recipes(out -> {
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_MERMAID, Items.PRISMARINE_SHARD);
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_LION, Items.GOLD_INGOT);
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_EAGLE, Items.FEATHER);
            figureheadRecipe(out, ShipDecor.FIGUREHEAD_SKULL, Items.BONE);
            // NAME2: a row needs several boards
            ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ShipDecor.NAMEPLATE.get(), 3)
                    .requires(ItemTags.SIGNS).requires(Items.GOLD_NUGGET)
                    .unlockedBy("has_gold_nugget", InventoryChangeTrigger.TriggerInstance.hasItems(Items.GOLD_NUGGET))
                    .save(out, ShipDecor.NAMEPLATE.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.FLAGPOLE.get(), 2)
                    .pattern("S").pattern("S").pattern("S")
                    .define('S', Items.STICK)
                    .unlockedBy("has_stick", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STICK))
                    .save(out, ShipDecor.FLAGPOLE.id());
            // FLG3: a log carved down over a slab of planks as its foot gives two pedestals
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.FLAG_PEDESTAL.get(), 2)
                    .pattern("L").pattern("S")
                    .define('L', ItemTags.LOGS).define('S', ItemTags.WOODEN_SLABS)
                    .unlockedBy("has_flagpole", InventoryChangeTrigger.TriggerInstance.hasItems(ShipDecor.FLAGPOLE.get()))
                    .save(out, ShipDecor.FLAG_PEDESTAL.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.CARGO_CRATE.get())
                    .pattern("SPS").pattern("P P").pattern("SPS")
                    .define('S', Items.STICK).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_stick", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STICK))
                    .save(out, ShipDecor.CARGO_CRATE.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ShipDecor.CARGO_BARREL.get())
                    .pattern("PIP").pattern("P P").pattern("PIP")
                    .define('P', ItemTags.PLANKS).define('I', Items.IRON_NUGGET)
                    .unlockedBy("has_iron_nugget", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_NUGGET))
                    .save(out, ShipDecor.CARGO_BARREL.id());
        });
    }

    /**
     * Hand-made Blockbench model ({@code art/models/<name>.bbmodel}, design.md §4.8): the carved figure looks north
     * and stands out up to a block ahead of and a little below its block, mounted on a plate against the south side
     * (the hull behind it), rotated like a furnace. Only the block state is generated; the item model delegates to the
     * block model.
     */
    private static void figurehead(ModelContext m, Block block) {
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block,
                Variant.variant().with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(block))).with(horizontalFacing()));
    }

    /** NAME2: the name board's hand-made parent models ({@code tools/gen_name_board_models.py}, {@code art/models/name_board.bbmodel}). */
    static final ResourceLocation NAME_BOARD = Constants.id("block/name_board");
    static final ResourceLocation NAME_BOARD_CAP_LEFT = Constants.id("block/name_board_cap_left");
    static final ResourceLocation NAME_BOARD_CAP_RIGHT = Constants.id("block/name_board_cap_right");
    static final ResourceLocation NAME_BOARD_ITEM = Constants.id("block/name_board_item");
    /** The wood the name board comes in (it keeps the nameplate's single registry id; RAIL2 may widen it to a family). */
    static final ShipWood NAME_BOARD_WOOD = ShipWood.OAK;

    /**
     * NAME2: the name board's block state (multipart, the north-facing parent models turned by {@code facing}): the
     * panel with the frame's top and bottom always, the frame's end on the viewer's left where no board continues the
     * row there ({@code left=false}) and likewise on the right, so the frame runs round a whole row. The parents use the
     * wood family's texture variables; datagen writes their oak children ({@link WoodFamily#childJson}), and the item
     * model is the oak child of {@code block/name_board_item} (a small framed board).
     */
    private static void nameBoard(ModelContext m, Block block) {
        ResourceLocation board = WoodFamily.child(NAME_BOARD_WOOD, NAME_BOARD);
        ResourceLocation capLeft = WoodFamily.child(NAME_BOARD_WOOD, NAME_BOARD_CAP_LEFT);
        ResourceLocation capRight = WoodFamily.child(NAME_BOARD_WOOD, NAME_BOARD_CAP_RIGHT);
        for (ResourceLocation parent : List.of(NAME_BOARD, NAME_BOARD_CAP_LEFT, NAME_BOARD_CAP_RIGHT)) {
            m.models().accept(WoodFamily.child(NAME_BOARD_WOOD, parent), () -> WoodFamily.childJson(NAME_BOARD_WOOD, parent));
        }
        m.models().accept(ModelLocationUtils.getModelLocation(block.asItem()), () -> WoodFamily.childJson(NAME_BOARD_WOOD, NAME_BOARD_ITEM));
        MultiPartGenerator gen = MultiPartGenerator.multiPart(block);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            gen.with(Condition.condition().term(NameBoardBlock.FACING, d), turned(board, d));
            gen.with(Condition.condition().term(NameBoardBlock.FACING, d).term(NameBoardBlock.LEFT, false), turned(capLeft, d));
            gen.with(Condition.condition().term(NameBoardBlock.FACING, d).term(NameBoardBlock.RIGHT, false), turned(capRight, d));
        }
        m.blockStates().accept(gen);
    }

    /** {@code model} (north-facing) turned to {@code d} by the block state. */
    private static Variant turned(ResourceLocation model, Direction d) {
        Variant v = Variant.variant().with(VariantProperties.MODEL, model);
        return switch (d) {
            case EAST -> v.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90);
            case SOUTH -> v.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180);
            case WEST -> v.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270);
            default -> v;
        };
    }

    /**
     * FLG3: hand-made Blockbench models ({@code art/models/flag_pedestal.bbmodel}): {@code block/flag_pedestal} (the
     * plinth with its empty socket; it carries the item's display entries and datagen's block-item model delegates to
     * it) and, while a flagpole stands on it, {@code block/flag_pedestal_pole} (plus the pole stub, the halyard's cleat
     * and the halyard). Round, never turned.
     */
    private static void flagPedestal(ModelContext m, Block block) {
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(PropertyDispatch.property(FlagpoleFooting.POLE)
                .select(false, Variant.variant().with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(block)))
                .select(true, Variant.variant().with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(block, "_pole")))));
    }

    /** Model faces north; rotate for the other directions. */
    private static PropertyDispatch horizontalFacing() {
        return PropertyDispatch.property(BlockStateProperties.HORIZONTAL_FACING)
                .select(Direction.NORTH, Variant.variant())
                .select(Direction.EAST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                .select(Direction.SOUTH, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                .select(Direction.WEST, Variant.variant().with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270));
    }

    private static void figureheadRecipe(RecipeOutput out, RegistryEntry<Block, FigureheadBlock> figurehead, Item key) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, figurehead.get())
                .pattern(" P ").pattern("PXP").pattern(" P ")
                .define('P', ItemTags.PLANKS).define('X', key)
                .unlockedBy("has_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key).getPath(),
                        InventoryChangeTrigger.TriggerInstance.hasItems(key))
                .save(out, figurehead.id());
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ShipDecorGameTests.class, FlagPedestalGameTests.class, FlagGameTests.class, FlagModelGameTests.class, com.richardsenger.piratesnships.ship.decor.flag.FlagStackGameTests.class, NameplateGameTests.class,
                DecorGameTests.class, ShipsBellGameTests.class, com.richardsenger.piratesnships.ship.decor.railing.RailingGameTests.class);
    }
}
