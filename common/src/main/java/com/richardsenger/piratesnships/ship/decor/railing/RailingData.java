package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe.Part;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.Condition;
import net.minecraft.data.models.blockstates.MultiPartGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.recipes.RecipeCategory;
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
 *
 * <p>RAIL4: the bulwark ({@code block/bulwark_edge} per edge, {@code block/bulwark_stanchion} per post, like the railing)
 * and the bulwark gun port ({@code block/bulwark_port} turned by {@code facing}, with {@code block/bulwark_port_lid} or
 * {@code _lid_open} by {@code open}); the bulwark in the railing's tags and {@code barrel_blocking}, the gun port in the
 * port's. Recipes: bulwark {@code SPS / PPP} gives 4 (vanilla's trapdoor is {@code PPP / PPP}); gun port, a bulwark and an
 * iron nugget, shapeless.
 *
 * <p>RAIL6: the railing's state adds two balusters per edge ({@code block/railing_baluster_west}, {@code _east}), each
 * only while no post stands at its end of the edge; the bulwark's posts are the railing's post; the rope railing
 * ({@code block/rope_railing_edge} per edge, the railing's post per post, its item {@code block/rope_railing_item}) in
 * the railing's weight tags and {@code grapple_latches}, recipe {@code TTT / P P} (string, that wood's planks) gives 4.
 * No model is turned with uvlock any more, so the cap rail's grain follows each edge.
 *
 * <p>RAIL7: the port's state is a multipart of its sill ({@code block/railing_port_sill}, the block's full width, so a
 * row of ports shares one sill) and the railing's post at each end where {@code post_left}/{@code post_right} stand; the
 * gate's of the same sill, the post it hangs on, the latch post unless {@code paired}, the leaf, and for a paired leaf
 * its run on to the middle ({@code block/railing_gate_left_meet}, {@code _right_meet} and their {@code _open}). The
 * port's own model {@code block/railing_port} stays as its item.
 */
public final class RailingData {

    static final ResourceLocation EDGE = Constants.id("block/railing_edge");
    static final ResourceLocation POST = Constants.id("block/railing_post");
    /** RAIL6: the edge's two balusters, each left out where a post stands at its end of the edge. */
    static final ResourceLocation BALUSTER_WEST = Constants.id("block/railing_baluster_west");
    static final ResourceLocation BALUSTER_EAST = Constants.id("block/railing_baluster_east");
    /** RAIL6: the rope railing's edge (stanchion and rope lines) and item; its posts are the railing's. */
    static final ResourceLocation ROPE_EDGE = Constants.id("block/rope_railing_edge");
    static final ResourceLocation ROPE_ITEM = Constants.id("block/rope_railing_item");
    static final ResourceLocation PORT = Constants.id("block/railing_port");
    /** RAIL7: the port's and the gate's sill across the whole block; their posts are the railing's post. */
    static final ResourceLocation PORT_SILL = Constants.id("block/railing_port_sill");
    /** RAIL7: a double gate's leaf from where the latch post would stand to the middle, closed and open. */
    static final ResourceLocation GATE_LEFT_MEET = Constants.id("block/railing_gate_left_meet");
    static final ResourceLocation GATE_RIGHT_MEET = Constants.id("block/railing_gate_right_meet");
    static final ResourceLocation GATE_LEFT_MEET_OPEN = Constants.id("block/railing_gate_left_meet_open");
    static final ResourceLocation GATE_RIGHT_MEET_OPEN = Constants.id("block/railing_gate_right_meet_open");
    static final ResourceLocation ITEM = Constants.id("block/railing_item");
    /** RAIL3: the gate's leaf, closed and open, hinged left or right (the posts and sill are the port's model). */
    static final ResourceLocation GATE_LEFT = Constants.id("block/railing_gate_left");
    static final ResourceLocation GATE_RIGHT = Constants.id("block/railing_gate_right");
    static final ResourceLocation GATE_LEFT_OPEN = Constants.id("block/railing_gate_left_open");
    static final ResourceLocation GATE_RIGHT_OPEN = Constants.id("block/railing_gate_right_open");
    static final ResourceLocation GATE_ITEM = Constants.id("block/railing_gate_item");
    /** RAIL4: the bulwark's wall per edge (RAIL6: under the railing's cap rail; its posts are the railing's), its item. */
    static final ResourceLocation BULWARK_EDGE = Constants.id("block/bulwark_edge");
    static final ResourceLocation BULWARK_ITEM = Constants.id("block/bulwark_item");
    /** RAIL4: the gun port's frame (jambs, cap ends, sill), its lid shut and open, its item. */
    static final ResourceLocation BULWARK_PORT = Constants.id("block/bulwark_port");
    static final ResourceLocation BULWARK_PORT_LID = Constants.id("block/bulwark_port_lid");
    static final ResourceLocation BULWARK_PORT_LID_OPEN = Constants.id("block/bulwark_port_lid_open");
    static final ResourceLocation BULWARK_PORT_ITEM = Constants.id("block/bulwark_port_item");

    private RailingData() {
    }

    public static void gather(DataContributions data) {
        WoodFamily<RailingBlock> railings = RailingContent.RAILINGS;
        WoodFamily<RailingPortBlock> ports = RailingContent.PORTS;
        WoodFamily<RailingGateBlock> gates = RailingContent.GATES;
        WoodFamily<BulwarkBlock> bulwarks = RailingContent.BULWARKS;
        WoodFamily<BulwarkPortBlock> gunPorts = RailingContent.BULWARK_PORTS;
        WoodFamily<RopeRailingBlock> ropes = RailingContent.ROPE_RAILINGS;
        data.lang(lang -> {
            railings.lang(lang, "Railing");
            ports.lang(lang, "Railing Port");
            gates.lang(lang, "Railing Gate");
            lang.add(RailingBlock.KEY_DISABLED, "Railings are disabled on this server");
            bulwarks.lang(lang, "Bulwark");
            gunPorts.lang(lang, "Bulwark Gun Port");
            ropes.lang(lang, "Rope Railing");
            lang.add(BulwarkPortBlock.KEY_HELD_OPEN, "The gun behind holds the lid open");
        });
        data.models(RailingData::models);
        // one item per railed edge: each edge was placed with one (railings and, RAIL4, bulwarks)
        railings.loot(data, RailingData::perEdge);
        bulwarks.loot(data, RailingData::perEdge);
        ropes.loot(data, RailingData::perEdge);
        ports.dropSelf(data);
        gates.dropSelf(data);
        gunPorts.dropSelf(data);
        data.blockTags(tags -> {
            railings.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, CannonContent.SWIVEL_MOUNTS, RiggingContent.RATLINES_ANCHORS, GrappleContent.GRAPPLE_LATCHES);
            ports.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, BoardingContent.GANGWAY_OPENINGS);
            gates.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, BoardingContent.GANGWAY_OPENINGS, GrappleContent.GRAPPLE_LATCHES);
            // RAIL4: a bulwark weighs and mounts like a railing, and stops a carriage cannon's barrel; its gun port
            // lets the barrel through and opens for the gangway like the railing port
            bulwarks.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, CannonContent.SWIVEL_MOUNTS, RiggingContent.RATLINES_ANCHORS, GrappleContent.GRAPPLE_LATCHES,
                    CannonContent.BARREL_BLOCKING);
            gunPorts.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, BoardingContent.GANGWAY_OPENINGS);
            // RAIL6: the rope railing weighs like a railing and holds the grappling hook; no swivel gun on a rope line
            ropes.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.SUPER_LIGHT, SableWeightTags.QUARTER_VOLUME,
                    CannonContent.BREAKABLE, GrappleContent.GRAPPLE_LATCHES);
        });
        Part planks = WoodRecipe.planks();
        Part stick = WoodRecipe.item(Items.STICK);
        Part nugget = WoodRecipe.item(Items.IRON_NUGGET);
        railings.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 4, "SSS", "PPP")
                .define('S', stick).define('P', planks).group(Constants.MOD_ID + ":railing").unlockedBy("has_planks", planks));
        ports.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 2, "S S", "PPP")
                .define('S', stick).define('P', planks).group(Constants.MOD_ID + ":railing_port").unlockedBy("has_planks", planks));
        gates.recipe(data, WoodRecipe.shaped(RecipeCategory.REDSTONE, 1, "SNS", "PPP")
                .define('S', stick).define('N', nugget).define('P', planks).group(Constants.MOD_ID + ":railing_gate")
                .unlockedBy("has_planks", planks));
        // RAIL4: SPS / PPP (vanilla's trapdoor is PPP / PPP); the gun port is a bulwark and an iron nugget (the
        // lid's hinges)
        bulwarks.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 4, "SPS", "PPP")
                .define('S', stick).define('P', planks).group(Constants.MOD_ID + ":bulwark").unlockedBy("has_planks", planks));
        // RAIL6: TTT / P P (string over two planks, the stanchions) gives 4
        Part string = WoodRecipe.item(Items.STRING);
        ropes.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 4, "TTT", "P P")
                .define('T', string).define('P', planks).group(Constants.MOD_ID + ":rope_railing").unlockedBy("has_planks", planks));
        Part bulwark = WoodRecipe.member(bulwarks);
        gunPorts.recipe(data, WoodRecipe.shapeless(RecipeCategory.DECORATIONS, 1).requires(bulwark).requires(nugget)
                .group(Constants.MOD_ID + ":bulwark_port").unlockedBy("has_bulwark", bulwark));
    }

    /** A loot table that drops one {@code block} per railed edge (railings and bulwarks). */
    private static LootTable.Builder perEdge(RailingBlock block) {
        LootTable.Builder table = LootTable.lootTable();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(block))
                    .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                            .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(RailingBlock.edge(d), true)))
                    .when(ExplosionCondition.survivesExplosion()));
        }
        return table;
    }

    private static void models(ModelContext m) {
        WoodFamily<RailingBlock> railings = RailingContent.RAILINGS;
        WoodFamily<RailingPortBlock> ports = RailingContent.PORTS;
        railings.childModels(m, EDGE);
        railings.childModels(m, POST);
        railings.childModels(m, BALUSTER_WEST);
        railings.childModels(m, BALUSTER_EAST);
        railings.itemModels(m, ITEM);
        ports.childModels(m, PORT); // block/<wood>_railing_port: the port's own model, which its item uses
        ports.childModels(m, PORT_SILL);
        WoodFamily<RailingGateBlock> gates = RailingContent.GATES;
        for (ResourceLocation leaf : List.of(GATE_LEFT, GATE_RIGHT, GATE_LEFT_OPEN, GATE_RIGHT_OPEN,
                GATE_LEFT_MEET, GATE_RIGHT_MEET, GATE_LEFT_MEET_OPEN, GATE_RIGHT_MEET_OPEN)) gates.childModels(m, leaf);
        gates.itemModels(m, GATE_ITEM);
        gates.forEach((wood, block) -> {
            MultiPartGenerator gen = MultiPartGenerator.multiPart(block);
            ResourceLocation post = WoodFamily.child(wood, POST);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(RailingGateBlock.FACING, d), turned(WoodFamily.child(wood, PORT_SILL), d));
                for (DoorHingeSide hinge : DoorHingeSide.values()) {
                    // RAIL7: the post the leaf hangs on always, the latch post only on a single gate (the railing's
                    // post turned to that corner: the edge's left end is the corner keyed by the edge, its right end
                    // the one keyed clockwise of it)
                    Direction hingeCorner = hinge == DoorHingeSide.LEFT ? d : d.getClockWise();
                    Direction latchCorner = hinge == DoorHingeSide.LEFT ? d.getClockWise() : d;
                    gen.with(Condition.condition().term(RailingGateBlock.FACING, d).term(RailingGateBlock.HINGE, hinge),
                            turned(post, hingeCorner));
                    gen.with(Condition.condition().term(RailingGateBlock.FACING, d).term(RailingGateBlock.HINGE, hinge)
                            .term(RailingGateBlock.PAIRED, false), turned(post, latchCorner));
                    for (boolean open : new boolean[]{false, true}) {
                        boolean left = hinge == DoorHingeSide.LEFT;
                        ResourceLocation leaf = left ? (open ? GATE_LEFT_OPEN : GATE_LEFT) : (open ? GATE_RIGHT_OPEN : GATE_RIGHT);
                        gen.with(Condition.condition().term(RailingGateBlock.FACING, d).term(RailingGateBlock.OPEN, open)
                                .term(RailingGateBlock.HINGE, hinge), turned(WoodFamily.child(wood, leaf), d));
                        ResourceLocation meet = left ? (open ? GATE_LEFT_MEET_OPEN : GATE_LEFT_MEET) : (open ? GATE_RIGHT_MEET_OPEN : GATE_RIGHT_MEET);
                        gen.with(Condition.condition().term(RailingGateBlock.FACING, d).term(RailingGateBlock.OPEN, open)
                                .term(RailingGateBlock.HINGE, hinge).term(RailingGateBlock.PAIRED, true), turned(WoodFamily.child(wood, meet), d));
                    }
                }
            }
            m.blockStates().accept(gen);
        });
        railings.forEach((wood, block) -> {
            MultiPartGenerator gen = edgesAndPosts(block, WoodFamily.child(wood, EDGE), WoodFamily.child(wood, POST));
            // RAIL6: each edge's two balusters, the one at an end with a post left out (a post never stands against one);
            // the edge's x-0 end is the corner keyed by the edge, its x-16 end the one keyed clockwise of it
            for (Direction d : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(RailingBlock.edge(d), true).term(RailingBlock.post(d), false),
                        turned(WoodFamily.child(wood, BALUSTER_WEST), d));
                gen.with(Condition.condition().term(RailingBlock.edge(d), true).term(RailingBlock.post(d.getClockWise()), false),
                        turned(WoodFamily.child(wood, BALUSTER_EAST), d));
            }
            m.blockStates().accept(gen);
        });
        // RAIL6: the rope railing like the railing (an edge model per edge, the railing's post per post)
        WoodFamily<RopeRailingBlock> ropes = RailingContent.ROPE_RAILINGS;
        ropes.childModels(m, ROPE_EDGE);
        ropes.itemModels(m, ROPE_ITEM);
        ropes.forEach((wood, block) -> m.blockStates().accept(edgesAndPosts(block, WoodFamily.child(wood, ROPE_EDGE),
                WoodFamily.child(wood, POST))));
        // RAIL4: the bulwark like the railing (a wall per edge, a stanchion per post); the gun port's frame by facing
        // and its lid shut or open
        WoodFamily<BulwarkBlock> bulwarks = RailingContent.BULWARKS;
        WoodFamily<BulwarkPortBlock> gunPorts = RailingContent.BULWARK_PORTS;
        bulwarks.childModels(m, BULWARK_EDGE);
        bulwarks.itemModels(m, BULWARK_ITEM);
        bulwarks.forEach((wood, block) -> m.blockStates().accept(edgesAndPosts(block, WoodFamily.child(wood, BULWARK_EDGE),
                WoodFamily.child(wood, POST))));
        for (ResourceLocation part : List.of(BULWARK_PORT, BULWARK_PORT_LID, BULWARK_PORT_LID_OPEN)) gunPorts.childModels(m, part);
        gunPorts.itemModels(m, BULWARK_PORT_ITEM);
        gunPorts.forEach((wood, block) -> {
            MultiPartGenerator gen = MultiPartGenerator.multiPart(block);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(BulwarkPortBlock.FACING, d), turned(WoodFamily.child(wood, BULWARK_PORT), d));
                for (boolean open : new boolean[]{false, true}) {
                    gen.with(Condition.condition().term(BulwarkPortBlock.FACING, d).term(BulwarkPortBlock.OPEN, open),
                            turned(WoodFamily.child(wood, open ? BULWARK_PORT_LID_OPEN : BULWARK_PORT_LID), d));
                }
            }
            m.blockStates().accept(gen);
        });
        // RAIL7: the sill always, each post while it stands (the left end of the edge is the corner keyed by the
        // facing, the right end the one keyed clockwise of it)
        ports.forEach((wood, block) -> {
            MultiPartGenerator gen = MultiPartGenerator.multiPart(block);
            ResourceLocation post = WoodFamily.child(wood, POST);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                gen.with(Condition.condition().term(RailingPortBlock.FACING, d), turned(WoodFamily.child(wood, PORT_SILL), d));
                gen.with(Condition.condition().term(RailingPortBlock.FACING, d).term(RailingPortBlock.POST_LEFT, true), turned(post, d));
                gen.with(Condition.condition().term(RailingPortBlock.FACING, d).term(RailingPortBlock.POST_RIGHT, true),
                        turned(post, d.getClockWise()));
            }
            m.blockStates().accept(gen);
        });
    }

    /** The multipart state of a railing or bulwark: {@code edge} turned to each railed edge, {@code post} to each post. */
    private static MultiPartGenerator edgesAndPosts(RailingBlock block, ResourceLocation edge, ResourceLocation post) {
        MultiPartGenerator gen = MultiPartGenerator.multiPart(block);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            gen.with(Condition.condition().term(RailingBlock.edge(d), true), turned(edge, d));
        }
        for (Direction corner : Direction.Plane.HORIZONTAL) {
            gen.with(Condition.condition().term(RailingBlock.post(corner), true), turned(post, corner));
        }
        return gen;
    }

    /**
     * {@code model} (north frame) turned to {@code d} by the block state. RAIL6: without uvlock, so the textures turn
     * with the model and the cap rail's grain runs along it on every edge (with uvlock a top face keeps the world's
     * grain, across the rail on the east and west edges).
     */
    private static Variant turned(ResourceLocation model, Direction d) {
        Variant v = Variant.variant().with(VariantProperties.MODEL, model);
        VariantProperties.Rotation r = switch (d) {
            case EAST -> VariantProperties.Rotation.R90;
            case SOUTH -> VariantProperties.Rotation.R180;
            case WEST -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
        return r == VariantProperties.Rotation.R0 ? v : v.with(VariantProperties.Y_ROT, r);
    }
}
