package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.rigging.client.RiggingClient;
import com.richardsenger.piratesnships.station.lookout.LookoutContent;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * The {@code ship.rigging} module (docs/design.md §4.8 "Visual backlog 2", item 1): RL1's ratlines, a climbable rope
 * net hung on a mast or laid sloped from the gunwale to the masthead ({@link RatlinesBlock}); RL2's ratline nets,
 * strung between a chainplate and a mast hoop at any pitch ({@link RatlineNets}) and climbed along their line
 * ({@link RatlineNetAssist}); RL4's fans, several nets to one hoop netted together into a triangle; RAIL5's boarding
 * nets, strung along the rail between two chainplates on railings ({@link BoardingNets}); RL5's sheets (rows of strands
 * joined into a rectangle or a trapezoid) and railing latches ({@link RailingLatches}).
 */
public final class RiggingModule implements ModModule {

    @Override
    public String id() {
        return "ship.rigging";
    }

    @Override
    public void registerConfig() {
        RiggingConfig.init();
    }

    @Override
    public void registerContent() {
        RiggingContent.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.PLAYER_TICK_END.register(RatlineNetAssist::onPlayerTick);
    }

    @Override
    public void initClient() {
        RiggingClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .block(RiggingContent.RATLINES, "Ratlines")
                .add(RatlinesItem.KEY_TOOLTIP, "Hang on a mast, or lay from the deck to climb up sloped")
                .add(RatlinesItem.KEY_DISABLED, "Ratlines are disabled on this server")
                .add(RatlinesBlock.KEY_IN_CLOTH, "Sail cloth here: use the yard's end")
                // RL2: every action bar text at most 40 characters (with the numbers filled in)
                .block(RiggingContent.CHAINPLATE, "Chainplate")
                .block(RiggingContent.MAST_HOOP, "Mast Hoop")
                .block(RiggingContent.NET_CELL, "Ratline Net")
                .item(RiggingContent.NET_ITEM, "Ratline Net")
                .add(RatlineNetItem.KEY_TOOLTIP, "Use on a chainplate or a railing's top rail, then on a mast hoop, to string a net to climb")
                .add(RatlineNetItem.KEY_MARKED, "Net tied: now use the other fitting")
                .add(RatlineNetItem.KEY_STRUNG, "Net strung through %s blocks")
                .add(RatlineNetItem.KEY_SAME, "Use the net on a second fitting")
                .add(RatlineNetItem.KEY_TOO_LONG, "Too long: %s of %s blocks")
                .add(RatlineNetItem.KEY_TOO_FLAT, "Too flat: %s°, at least %s°")
                .add(RatlineNetItem.KEY_OTHER_BODY, "Both ends on one ship or on land")
                .add(RatlineNetItem.KEY_BUSY, "That fitting already holds a net")
                .add(RatlineNetItem.KEY_BLOCKED, "Blocked at %s %s %s")
                .add(RatlineNetItem.KEY_DISABLED, "Ratline nets are disabled")
                // RL4: a hoop's fan
                .add(RatlineNetItem.KEY_FULL, "Hoop full: at most %s nets")
                .add(RatlineNetItem.KEY_APART, "Too far apart for one net: %s")
                // RAIL5: boarding nets along the rail
                .add(RatlineNetItem.KEY_BOARDING_STRUNG, "Boarding net strung along the rail")
                .add(RatlineNetItem.KEY_NOT_ALONG, "Boarding nets run along one rail edge")
                .add(RatlineNetItem.KEY_TOO_SHORT, "Leave a block between the two ends")
                // RL5: railing latches and sheets
                .block(RiggingContent.RAILING_LATCH, "Railing Latch")
                .add(RatlineNetItem.KEY_LATCHES_OFF, "No latches here: use a chainplate")
                .add(RatlineNetItem.KEY_NO_LATCH, "No latch on a gate or a port")
                .add(RatlineNetItem.KEY_NO_RAIL, "No rail on that edge")
                .add(RatlineNetItem.KEY_NOT_IN_ROW, "Strung alone: ends not in one row"));
        data.models(m -> {
            // hand-made Blockbench models (art/models/ratlines.bbmodel, design.md §4.8), drawn facing north: the hung net
            // against the south side, the sloped net rising to the north. Only the block state is generated; the item
            // has its own hand-made model (item/ratlines).
            RatlinesBlock block = RiggingContent.RATLINES.get();
            ResourceLocation wall = ModelLocationUtils.getModelLocation(block);
            ResourceLocation slope = ModelLocationUtils.getModelLocation(block, "_slope");
            m.blockStates().accept(MultiVariantGenerator.multiVariant(block)
                    .with(PropertyDispatch.properties(RatlinesBlock.KIND, RatlinesBlock.FACING)
                            .generate((kind, facing) -> Variant.variant()
                                    .with(VariantProperties.MODEL, kind == RatlinesRules.Kind.SLOPE ? slope : wall)
                                    .with(VariantProperties.Y_ROT, yRot(facing)))));
            m.handMadeItem(RiggingContent.RATLINES_ITEM.get());
            netModels(m);
        });
        data.blockLoot(loot -> {
            loot.dropSelf(RiggingContent.RATLINES.get());
            loot.dropSelf(RiggingContent.CHAINPLATE.get());
            loot.dropSelf(RiggingContent.MAST_HOOP.get());
            // the net cell has no loot (noLootTable): breaking the net drops one net item at fitting A (RatlineNets)
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.CLIMBABLE).add(RiggingContent.RATLINES.get()).add(RiggingContent.NET_CELL.get())
                    .add(RiggingContent.CHAINPLATE.get()).add(RiggingContent.MAST_HOOP.get()).add(RiggingContent.RAILING_LATCH.get());
            // RL1b: a net hangs on a yard and a sloped run leans on a yard or the crow's nest (RatlinesRules)
            tags.tag(RiggingContent.RATLINES_ANCHORS).addTag(BlockTags.FENCES).addTag(BlockTags.WALLS);
            // WOOD3: the yards and crow's nests of every wood (a mod's wood's member as an optional entry)
            SailingBlocks.YARDS.tag(tags, RiggingContent.RATLINES_ANCHORS);
            LookoutContent.CROWS_NESTS.tag(tags, RiggingContent.RATLINES_ANCHORS);
            // a rope net: light like a ladder, a quarter volume for buoyancy (Sable lists minecraft:ladder in both,
            // refs/sable common/src/main/resources/data/sable/tags/block/super_light.json and quarter_volume.json)
            tags.tag(SableWeightTags.SUPER_LIGHT).add(RiggingContent.RATLINES.get())
                    .add(RiggingContent.CHAINPLATE.get()).add(RiggingContent.MAST_HOOP.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(RiggingContent.RATLINES.get())
                    .add(RiggingContent.CHAINPLATE.get()).add(RiggingContent.MAST_HOOP.get())
                    .add(RiggingContent.NET_CELL.get()).add(RiggingContent.RAILING_LATCH.get());
            // RL2: a net cell weighs nothing (the drawn net is a few ropes across a block of air); RL5: nor does a
            // railing latch (a small eye bolt)
            tags.tag(RiggingContent.SABLE_WEIGHTLESS).add(RiggingContent.NET_CELL.get()).add(RiggingContent.RAILING_LATCH.get());
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(RiggingContent.CHAINPLATE.get()).add(RiggingContent.MAST_HOOP.get());
        });
        data.recipes(out -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RiggingContent.RATLINES_ITEM.get(), 4)
                    .pattern("T T").pattern("SSS").pattern("T T")
                    .define('T', Items.STICK).define('S', Items.STRING)
                    .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                    .save(out, RiggingContent.RATLINES_ITEM.id());
            // RL2: a ring of nuggets on an iron strap
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RiggingContent.CHAINPLATE.get(), 2)
                    .pattern(" N ").pattern("N N").pattern(" I ")
                    .define('N', Items.IRON_NUGGET).define('I', Items.IRON_INGOT)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, RiggingContent.CHAINPLATE.id());
            // a band of ingots with a ring of nuggets under it
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RiggingContent.MAST_HOOP.get(), 2)
                    .pattern("III").pattern("N N").pattern(" N ")
                    .define('N', Items.IRON_NUGGET).define('I', Items.IRON_INGOT)
                    .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                    .save(out, RiggingContent.MAST_HOOP.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, RiggingContent.NET_ITEM.get())
                    .pattern("TST").pattern("SSS").pattern("TST")
                    .define('T', Items.STICK).define('S', Items.STRING)
                    .unlockedBy("has_string", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STRING))
                    .save(out, RiggingContent.NET_ITEM.id());
        });
    }

    private static VariantProperties.Rotation yRot(Direction facing) {
        return switch (facing) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }

    /**
     * RL2: the fittings' hand-made Blockbench models (art/models/chainplate.bbmodel and mast_hoop.bbmodel), each carrying
     * the item's display entries, so the item models datagen writes for the block items point at them. The chainplate is
     * made for the floor and turned like a button (wall: x 90; the ceiling never occurs but needs a variant); the hoop is
     * made facing north (the mast to the south). The invisible net cell gets a particle-only model of the rope.
     */
    private static void netModels(ModelContext m) {
        Block plate = RiggingContent.CHAINPLATE.get();
        ResourceLocation plateModel = ModelLocationUtils.getModelLocation(plate);
        m.blockStates().accept(MultiVariantGenerator.multiVariant(plate, Variant.variant().with(VariantProperties.MODEL, plateModel))
                .with(PropertyDispatch.properties(BlockStateProperties.ATTACH_FACE, BlockStateProperties.HORIZONTAL_FACING)
                        .generate((face, facing) -> {
                            Variant v = Variant.variant();
                            if (face == AttachFace.WALL) v = v.with(VariantProperties.X_ROT, VariantProperties.Rotation.R90);
                            if (face == AttachFace.CEILING) v = v.with(VariantProperties.X_ROT, VariantProperties.Rotation.R180);
                            VariantProperties.Rotation y = yRot(face == AttachFace.CEILING ? facing.getOpposite() : facing);
                            return y == VariantProperties.Rotation.R0 ? v : v.with(VariantProperties.Y_ROT, y);
                        })));
        Block hoop = RiggingContent.MAST_HOOP.get();
        ResourceLocation hoopModel = ModelLocationUtils.getModelLocation(hoop);
        m.blockStates().accept(MultiVariantGenerator.multiVariant(hoop, Variant.variant().with(VariantProperties.MODEL, hoopModel))
                .with(PropertyDispatch.property(BlockStateProperties.HORIZONTAL_FACING).generate(facing -> {
                    VariantProperties.Rotation y = yRot(facing);
                    return y == VariantProperties.Rotation.R0 ? Variant.variant() : Variant.variant().with(VariantProperties.Y_ROT, y);
                })));
        Block cell = RiggingContent.NET_CELL.get();
        ResourceLocation cellModel = ModelTemplates.PARTICLE_ONLY.create(cell,
                TextureMapping.particle(ResourceLocation.fromNamespaceAndPath(com.richardsenger.piratesnships.Constants.MOD_ID, "block/rope")),
                m.models());
        m.blockStates().accept(MultiVariantGenerator.multiVariant(cell, Variant.variant().with(VariantProperties.MODEL, cellModel)));
        // RL5: the railing latch's hand-made eye bolt (tools/gen_railing_models.py, made on the north edge), turned by
        // facing; fixed palette UVs, so no uvlock
        Block latch = RiggingContent.RAILING_LATCH.get();
        ResourceLocation latchModel = ModelLocationUtils.getModelLocation(latch);
        m.blockStates().accept(MultiVariantGenerator.multiVariant(latch, Variant.variant().with(VariantProperties.MODEL, latchModel))
                .with(PropertyDispatch.property(BlockStateProperties.HORIZONTAL_FACING).generate(facing -> {
                    VariantProperties.Rotation y = yRot(facing);
                    return y == VariantProperties.Rotation.R0 ? Variant.variant() : Variant.variant().with(VariantProperties.Y_ROT, y);
                })));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(RatlinesGameTests.class, RatlineNetGameTests.class, BoardingNetGameTests.class, RatlineSheetGameTests.class);
    }
}
