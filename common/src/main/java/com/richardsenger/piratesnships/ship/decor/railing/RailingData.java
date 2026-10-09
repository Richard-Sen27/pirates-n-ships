package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.Condition;
import net.minecraft.data.models.blockstates.MultiPartGenerator;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;

/**
 * Datagen of the railing family (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)"), for every shipped wood through
 * {@link WoodFamily}: the hand-made parent models ({@code block/railing_edge}, {@code _post}, {@code _port},
 * {@code _item}, from {@code tools/gen_railing_models.py}) get one child per wood with that wood's planks and stripped
 * log; the railing's multipart block state draws an edge model per railed edge and a post model per post, both turned
 * from their north frame; lang ("Oak Railing", "Oak Railing Port"), loot (a railing drops one item per railed edge),
 * tags and recipes.
 *
 * <p>Recipes, per wood, from that wood's planks and sticks (no vanilla or mod recipe has these shapes): railing
 * {@code SSS / PPP} gives 4; railing port {@code S S / PPP} gives 2; railing gate (RAIL3) {@code SNS / PPP} with an
 * iron nugget gives 1.
 *
 * <p>RAIL3: the gate's multipart block state draws the port's model (posts and sill) and one leaf model chosen by
 * {@code open} and {@code hinge}, turned from the north frame by {@code facing}. Railings and gates are in
 * {@code #pirates_n_ships:grapple_latches} (the hook holds on them like on a cleat); the gate also in
 * {@code gangway_openings} (read for the open gate only).
 */
public final class RailingData {

    static final ResourceLocation EDGE = Constants.id("block/railing_edge");
    static final ResourceLocation POST = Constants.id("block/railing_post");
    static final ResourceLocation PORT = Constants.id("block/railing_port");
    static final ResourceLocation ITEM = Constants.id("block/railing_item");
    /** RAIL3: the gate's leaf, closed and open, hinged left or right (the posts and sill are the port's model). */
    static final ResourceLocation GATE_LEFT = Constants.id("block/railing_gate_left");
    static final ResourceLocation GATE_RIGHT = Constants.id("block/railing_gate_right");
    static final ResourceLocation GATE_LEFT_OPEN = Constants.id("block/railing_gate_left_open");
    static final ResourceLocation GATE_RIGHT_OPEN = Constants.id("block/railing_gate_right_open");
    static final ResourceLocation GATE_ITEM = Constants.id("block/railing_gate_item");

    private RailingData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<RailingBlock> railings = RailingContent.RAILINGS;
        WoodFamily<RailingPortBlock> ports = RailingContent.PORTS;
        WoodFamily<RailingGateBlock> gates = RailingContent.GATES;
        data.lang(lang -> {
            railings.lang(lang, "Railing");
            ports.lang(lang, "Railing Port");
            gates.lang(lang, "Railing Gate");
            lang.add(RailingBlock.KEY_DISABLED, "Railings are disabled on this server");
        });
        data.models(RailingData::models);
        data.blockLoot(loot -> {
            railings.forEach((wood, block) -> {
                // one item per railed edge: each edge was placed with one
                LootTable.Builder table = LootTable.lootTable();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(block))
                            .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                                    .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(RailingBlock.edge(d), true)))
                            .when(ExplosionCondition.survivesExplosion()));
                }
                loot.add(block, table);
            });
            ports.forEach((wood, block) -> loot.dropSelf(block));
            gates.forEach((wood, block) -> loot.dropSelf(block));
        });
        data.blockTags(tags -> {
            railings.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, CannonContent.SWIVEL_MOUNTS, RiggingContent.RATLINES_ANCHORS, GrappleContent.GRAPPLE_LATCHES);
            ports.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, BoardingContent.GANGWAY_OPENINGS);
            gates.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, BoardingContent.GANGWAY_OPENINGS, GrappleContent.GRAPPLE_LATCHES);
        });
        data.recipes(out -> {
            railings.forEach((wood, block) -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, block, 4)
                    .pattern("SSS").pattern("PPP")
                    .define('S', Items.STICK).define('P', wood.planks())
                    .group(Constants.MOD_ID + ":railing")
                    .unlockedBy("has_planks", InventoryChangeTrigger.TriggerInstance.hasItems(wood.planks()))
                    .save(out, railings.entry(wood).id()));
            ports.forEach((wood, block) -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, block, 2)
                    .pattern("S S").pattern("PPP")
                    .define('S', Items.STICK).define('P', wood.planks())
                    .group(Constants.MOD_ID + ":railing_port")
                    .unlockedBy("has_planks", InventoryChangeTrigger.TriggerInstance.hasItems(wood.planks()))
                    .save(out, ports.entry(wood).id()));
            gates.forEach((wood, block) -> ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, block, 1)
                    .pattern("SNS").pattern("PPP")
                    .define('S', Items.STICK).define('N', Items.IRON_NUGGET).define('P', wood.planks())
                    .group(Constants.MOD_ID + ":railing_gate")
                    .unlockedBy("has_planks", InventoryChangeTrigger.TriggerInstance.hasItems(wood.planks()))
                    .save(out, gates.entry(wood).id()));
        });
    }

    private static void models(ModelContext m) {
        WoodFamily<RailingBlock> railings = RailingContent.RAILINGS;
        WoodFamily<RailingPortBlock> ports = RailingContent.PORTS;
        railings.childModels(m, EDGE);
        railings.childModels(m, POST);
        railings.itemModels(m, ITEM);
        ports.childModels(m, PORT); // block/<wood>_railing_port: the port's own model, which its item uses too
        WoodFamily<RailingGateBlock> gates = RailingContent.GATES;
        for (ResourceLocation leaf : List.of(GATE_LEFT, GATE_RIGHT, GATE_LEFT_OPEN, GATE_RIGHT_OPEN)) gates.childModels(m, leaf);
        gates.itemModels(m, GATE_ITEM);
        gates.forEach((wood, block) -> {
            MultiPartGenerator gen = MultiPartGenerator.multiPart(block);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(RailingGateBlock.FACING, d), turned(WoodFamily.child(wood, PORT), d));
                for (boolean open : new boolean[]{false, true}) {
                    for (DoorHingeSide hinge : DoorHingeSide.values()) {
                        ResourceLocation leaf = hinge == DoorHingeSide.LEFT ? (open ? GATE_LEFT_OPEN : GATE_LEFT) : (open ? GATE_RIGHT_OPEN : GATE_RIGHT);
                        gen.with(Condition.condition().term(RailingGateBlock.FACING, d).term(RailingGateBlock.OPEN, open)
                                .term(RailingGateBlock.HINGE, hinge), turned(WoodFamily.child(wood, leaf), d));
                    }
                }
            }
            m.blockStates().accept(gen);
        });
        railings.forEach((wood, block) -> {
            ResourceLocation edge = WoodFamily.child(wood, EDGE);
            ResourceLocation post = WoodFamily.child(wood, POST);
            MultiPartGenerator gen = MultiPartGenerator.multiPart(block);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(RailingBlock.edge(d), true), turned(edge, d));
            }
            for (Direction corner : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(RailingBlock.post(corner), true), turned(post, corner));
            }
            m.blockStates().accept(gen);
        });
        ports.forEach((wood, block) -> {
            ResourceLocation model = WoodFamily.child(wood, PORT);
            m.blockStates().accept(MultiVariantGenerator.multiVariant(block).with(PropertyDispatch.property(RailingPortBlock.FACING)
                    .generate(facing -> turned(model, facing))));
        });
    }

    /** {@code model} (north frame) turned to {@code d} by the block state, textures kept on the world grid. */
    private static Variant turned(ResourceLocation model, Direction d) {
        Variant v = Variant.variant().with(VariantProperties.MODEL, model);
        VariantProperties.Rotation r = switch (d) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
        return r == VariantProperties.Rotation.R0 ? v : v.with(VariantProperties.Y_ROT, r).with(VariantProperties.UV_LOCK, true);
    }
}
