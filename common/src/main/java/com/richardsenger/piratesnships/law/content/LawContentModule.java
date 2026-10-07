package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.Condition;
import net.minecraft.data.models.blockstates.MultiPartGenerator;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;

/**
 * The {@code law.content} module: shackles, brig bars, brig door and brig key (design.md §13.3). The bars and the door have
 * hand-made Blockbench models; their block states follow vanilla's panes and doors.
 */
public final class LawContentModule implements ModModule {

    @Override
    public String id() {
        return "law.content";
    }

    @Override
    public void registerContent() {
        LawContent.init();
    }

    @Override
    public void registerEvents() {
        // Before the target's own interaction, so shackles also work on villagers and wandering traders
        CommonEvents.ENTITY_INTERACT.register(ShacklesItem::onEntityInteract);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .item(LawContent.SHACKLES, "Shackles")
                .item(LawContent.BRIG_KEY, "Brig Key")
                .add("item.pirates_n_ships.brig_key.tooltip", "Use on a brig door to lock or unlock it")
                .add(BrigDoorBlock.NEEDS_KEY, "You need a brig key to lock or unlock this door")
                .block(LawContent.BRIG_BARS, "Brig Bars")
                .block(LawContent.BRIG_DOOR, "Brig Door"));
        data.models(m -> {
            // The shackles' item model is hand-made (art/models/shackles.bbmodel), so datagen writes none
            // Flat placeholder sprite from tools/gen_law_textures.py until the 3D item batch (F8f)
            m.flatItem(LawContent.BRIG_KEY.get());
            bars(m, LawContent.BRIG_BARS.get());
            door(m, LawContent.BRIG_DOOR.get());
        });
        data.blockLoot(loot -> {
            loot.dropSelf(LawContent.BRIG_BARS.get());
            loot.add(LawContent.BRIG_DOOR.get(), doorTable(LawContent.BRIG_DOOR.get()));
        });
        data.blockTags(tags -> {
            tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(LawContent.BRIG_BARS.get(), LawContent.BRIG_DOOR.get());
            // #minecraft:doors also puts the door into Sable's super_light and quarter_volume tags
            tags.tag(BlockTags.DOORS).add(LawContent.BRIG_DOOR.get());
            // Like iron bars in Sable's own tags
            tags.tag(SableWeightTags.SUPER_LIGHT).add(LawContent.BRIG_BARS.get());
            tags.tag(SableWeightTags.QUARTER_VOLUME).add(LawContent.BRIG_BARS.get());
        });
        data.itemTags(tags -> tags.tag(ItemTags.DOORS).add(LawContent.BRIG_DOOR.get().asItem()));
        data.recipes(out -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, LawContent.SHACKLES.get())
                    .pattern("ICI")
                    .define('I', Items.IRON_INGOT).define('C', Items.CHAIN)
                    .unlockedBy("has_chain", InventoryChangeTrigger.TriggerInstance.hasItems(Items.CHAIN))
                    .save(out, LawContent.SHACKLES.id());
            // An iron ingot (the bow and shaft) over an iron nugget (the bit)
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, LawContent.BRIG_KEY.get())
                    .pattern("I").pattern("N")
                    .define('I', Items.IRON_INGOT).define('N', Items.IRON_NUGGET)
                    .unlockedBy("has_iron_bars", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_BARS))
                    .save(out, LawContent.BRIG_KEY.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, LawContent.BRIG_BARS.get(), 6)
                    .pattern("BPB").pattern("BPB")
                    .define('B', Items.IRON_BARS).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_iron_bars", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_BARS))
                    .save(out, LawContent.BRIG_BARS.id());
            ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, LawContent.BRIG_DOOR.get())
                    .pattern("II").pattern("BB").pattern("II")
                    .define('I', Items.IRON_INGOT).define('B', Items.IRON_BARS)
                    .unlockedBy("has_iron_bars", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_BARS))
                    .save(out, LawContent.BRIG_DOOR.id());
        });
    }

    /**
     * Bars: hand-made Blockbench models (art/models/brig_bars*.bbmodel, design.md §4.8), so only the block state is
     * generated. A multipart state like vanilla's panes: the post always, {@code brig_bars_side} (the arm to the north)
     * for north and, turned 90°, east, {@code brig_bars_side_alt} (the arm to the south) for south and, turned 90°, west.
     * An unconnected side shows nothing (the pane's {@code noside} parts have no counterpart: the post stands alone).
     * The item uses {@code block/brig_bars}, a straight piece of post and both arms. The models are opaque (iron and
     * anvil textures, no transparent gaps), so they need no render type on either loader.
     */
    private static void bars(ModelContext m, Block block) {
        ResourceLocation post = ModelLocationUtils.getModelLocation(block, "_post");
        ResourceLocation side = ModelLocationUtils.getModelLocation(block, "_side");
        ResourceLocation sideAlt = ModelLocationUtils.getModelLocation(block, "_side_alt");
        m.blockStates().accept(MultiPartGenerator.multiPart(block)
                .with(Variant.variant().with(VariantProperties.MODEL, post))
                .with(Condition.condition().term(BlockStateProperties.NORTH, true), Variant.variant().with(VariantProperties.MODEL, side))
                .with(Condition.condition().term(BlockStateProperties.EAST, true),
                        Variant.variant().with(VariantProperties.MODEL, side).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                .with(Condition.condition().term(BlockStateProperties.SOUTH, true), Variant.variant().with(VariantProperties.MODEL, sideAlt))
                .with(Condition.condition().term(BlockStateProperties.WEST, true),
                        Variant.variant().with(VariantProperties.MODEL, sideAlt).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90)));
    }

    /**
     * A door: hand-made Blockbench models (art/models/brig_door_*.bbmodel, design.md §4.8), so only the block state and
     * the flat item model ({@code item/<name>}) are generated, with the same facing/half/hinge/open rotations vanilla
     * uses. Each model is the leaf of a door facing east (x 0..3); the "left" models have the hinge at z 0, the "right"
     * ones at z 16 (mirrored in z). Opening turns the leaf about its hinge, which in vanilla's scheme (open adds 90° or
     * 270°) needs the leaf turned by 180° in the model: a left door's open model is the right model and the other way
     * round. The unlocked halves are symmetric front to back, so that turned leaf is the mirrored one; the locked lower
     * half carries a padlock on the side the placing player faced (x < 0) and has its own four models, so the padlock
     * stays on the same side of the leaf when the door swings.
     */
    private static void door(ModelContext m, Block block) {
        ResourceLocation bottomLeft = ModelLocationUtils.getModelLocation(block, "_bottom_left");
        ResourceLocation bottomRight = ModelLocationUtils.getModelLocation(block, "_bottom_right");
        ResourceLocation topLeft = ModelLocationUtils.getModelLocation(block, "_top_left");
        ResourceLocation topRight = ModelLocationUtils.getModelLocation(block, "_top_right");
        // indexed like the loop below: left, left open, right, right open
        ResourceLocation[] lower = {bottomLeft, bottomRight, bottomRight, bottomLeft};
        ResourceLocation[] lowerLocked = {
                ModelLocationUtils.getModelLocation(block, "_bottom_left_locked"),
                ModelLocationUtils.getModelLocation(block, "_bottom_left_open_locked"),
                ModelLocationUtils.getModelLocation(block, "_bottom_right_locked"),
                ModelLocationUtils.getModelLocation(block, "_bottom_right_open_locked")};
        ResourceLocation[] upper = {topLeft, topRight, topRight, topLeft};
        m.flatItem(block.asItem());
        PropertyDispatch.C5<Direction, DoubleBlockHalf, DoorHingeSide, Boolean, Boolean> dispatch = PropertyDispatch.properties(
                BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.DOUBLE_BLOCK_HALF, BlockStateProperties.DOOR_HINGE,
                BlockStateProperties.OPEN, BrigDoorBlock.LOCKED);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                for (DoorHingeSide hinge : DoorHingeSide.values()) {
                    for (boolean open : new boolean[]{false, true}) {
                        for (boolean locked : new boolean[]{false, true}) {
                            boolean left = hinge == DoorHingeSide.LEFT;
                            ResourceLocation[] set = half == DoubleBlockHalf.UPPER ? upper : locked ? lowerLocked : lower;
                            ResourceLocation model = set[(left ? 0 : 2) + (open ? 1 : 0)];
                            // Vanilla: closed east = 0°, then +90° per clockwise step; open adds +90° (left hinge) or +270° (right)
                            int degrees = ((int) facing.toYRot() + 90 + (open ? (left ? 90 : 270) : 0)) % 360;
                            Variant variant = Variant.variant().with(VariantProperties.MODEL, model);
                            if (degrees != 0) {
                                variant = variant.with(VariantProperties.Y_ROT, VariantProperties.Rotation.valueOf("R" + degrees));
                            }
                            dispatch.select(facing, half, hinge, open, locked, variant);
                        }
                    }
                }
            }
        }
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(dispatch));
    }

    /** Like vanilla's door loot: only the lower half drops the door, so breaking either half yields one door. */
    private static LootTable.Builder doorTable(Block door) {
        return LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(door).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(door)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoorBlock.HALF, DoubleBlockHalf.LOWER))))
                .when(ExplosionCondition.survivesExplosion()));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(LawContentGameTests.class);
    }
}
