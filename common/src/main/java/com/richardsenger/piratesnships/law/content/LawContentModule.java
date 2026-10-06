package com.richardsenger.piratesnships.law.content;

import com.google.gson.JsonObject;
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
import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.models.model.TextureSlot;
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
 * The {@code law.content} module: shackles, brig bars and brig door (design.md §13.3). Block states and models are
 * built from vanilla's glass pane and door templates, the same way vanilla generates its own panes and doors.
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
                .block(LawContent.BRIG_BARS, "Brig Bars")
                .block(LawContent.BRIG_DOOR, "Brig Door"));
        data.models(m -> {
            m.flatItem(LawContent.SHACKLES.get());
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
     * Bars like vanilla glass panes (an IronBarsBlock has the same states): the pane templates with
     * {@code pane = <name>} and {@code edge = <name>_edge}, a multipart block state and a flat item model.
     * The models get NeoForge's {@code render_type: cutout} so the gaps between the bars are transparent; Fabric
     * ignores the key and needs a render layer registration in the Fabric port.
     */
    private static void bars(ModelContext m, Block block) {
        ResourceLocation tex = TextureMapping.getBlockTexture(block);
        TextureMapping textures = new TextureMapping().put(TextureSlot.PANE, tex).put(TextureSlot.EDGE, TextureMapping.getBlockTexture(block, "_edge"));
        ResourceLocation post = cutout(ModelTemplates.STAINED_GLASS_PANE_POST, block, textures, m);
        ResourceLocation side = cutout(ModelTemplates.STAINED_GLASS_PANE_SIDE, block, textures, m);
        ResourceLocation sideAlt = cutout(ModelTemplates.STAINED_GLASS_PANE_SIDE_ALT, block, textures, m);
        ResourceLocation noSide = cutout(ModelTemplates.STAINED_GLASS_PANE_NOSIDE, block, textures, m);
        ResourceLocation noSideAlt = cutout(ModelTemplates.STAINED_GLASS_PANE_NOSIDE_ALT, block, textures, m);
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(block.asItem()), TextureMapping.layer0(tex), m.models());
        m.blockStates().accept(MultiPartGenerator.multiPart(block)
                .with(Variant.variant().with(VariantProperties.MODEL, post))
                .with(Condition.condition().term(BlockStateProperties.NORTH, true), Variant.variant().with(VariantProperties.MODEL, side))
                .with(Condition.condition().term(BlockStateProperties.EAST, true),
                        Variant.variant().with(VariantProperties.MODEL, side).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                .with(Condition.condition().term(BlockStateProperties.SOUTH, true), Variant.variant().with(VariantProperties.MODEL, sideAlt))
                .with(Condition.condition().term(BlockStateProperties.WEST, true),
                        Variant.variant().with(VariantProperties.MODEL, sideAlt).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                .with(Condition.condition().term(BlockStateProperties.NORTH, false), Variant.variant().with(VariantProperties.MODEL, noSide))
                .with(Condition.condition().term(BlockStateProperties.EAST, false), Variant.variant().with(VariantProperties.MODEL, noSideAlt))
                .with(Condition.condition().term(BlockStateProperties.SOUTH, false),
                        Variant.variant().with(VariantProperties.MODEL, noSideAlt).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                .with(Condition.condition().term(BlockStateProperties.WEST, false),
                        Variant.variant().with(VariantProperties.MODEL, noSide).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270)));
    }

    private static ResourceLocation cutout(ModelTemplate template, Block block, TextureMapping textures, ModelContext m) {
        return template.create(template.getDefaultModelLocation(block), textures, m.models(), (id, slots) -> {
            JsonObject json = template.createBaseTemplate(id, slots);
            json.addProperty("render_type", "minecraft:cutout");
            return json;
        });
    }

    /**
     * A door like vanilla's: the eight door templates with {@code <name>_top} / {@code <name>_bottom}, the same
     * facing/half/hinge/open rotations vanilla uses, and a flat item model using {@code item/<name>}. A locked lower
     * half uses four more models ({@code *_locked}) with the padlock texture {@code <name>_bottom_locked}.
     */
    private static void door(ModelContext m, Block block) {
        TextureMapping textures = TextureMapping.door(block);
        TextureMapping lockedTextures = TextureMapping.door(block).put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom_locked"));
        ResourceLocation[] lower = {
                ModelTemplates.DOOR_BOTTOM_LEFT.create(block, textures, m.models()),
                ModelTemplates.DOOR_BOTTOM_LEFT_OPEN.create(block, textures, m.models()),
                ModelTemplates.DOOR_BOTTOM_RIGHT.create(block, textures, m.models()),
                ModelTemplates.DOOR_BOTTOM_RIGHT_OPEN.create(block, textures, m.models())};
        ResourceLocation[] lowerLocked = {
                ModelTemplates.DOOR_BOTTOM_LEFT.createWithSuffix(block, "_locked", lockedTextures, m.models()),
                ModelTemplates.DOOR_BOTTOM_LEFT_OPEN.createWithSuffix(block, "_locked", lockedTextures, m.models()),
                ModelTemplates.DOOR_BOTTOM_RIGHT.createWithSuffix(block, "_locked", lockedTextures, m.models()),
                ModelTemplates.DOOR_BOTTOM_RIGHT_OPEN.createWithSuffix(block, "_locked", lockedTextures, m.models())};
        ResourceLocation[] upper = {
                ModelTemplates.DOOR_TOP_LEFT.create(block, textures, m.models()),
                ModelTemplates.DOOR_TOP_LEFT_OPEN.create(block, textures, m.models()),
                ModelTemplates.DOOR_TOP_RIGHT.create(block, textures, m.models()),
                ModelTemplates.DOOR_TOP_RIGHT_OPEN.create(block, textures, m.models())};
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
