package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The ART2 decor blocks (design.md §4.8): registration and recipes, placement and states (lantern on floor, wall and
 * ceiling; bell rings and stops; rope coils stack to four; window shutters; the cot's two halves), drops, and a round
 * trip through assembly and disassembly on a ship. Test hulls stand on stone like {@code AssemblyGameTests}.
 */
public final class DecorGameTests {

    static final List<String> BLOCK_IDS = List.of("ship_lantern", "ships_bell", "rope_coil", "stern_window", "chart_table", "sea_cot");

    private DecorGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(DecorGameTests.class);
    }

    @ModGameTest
    public static void furnishingsAreRegisteredWithRecipes(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, List.of(), BLOCK_IDS);
        for (String id : BLOCK_IDS) {
            ContentTestSupport.assertRecipe(helper, id, BuiltInRegistries.ITEM.get(Constants.id(id)), id.equals("stern_window") ? 2 : 1);
        }
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void furnishingsPlaceAndDropThemselves(GameTestHelper helper) {
        List<Block> blocks = ShipDecor.furnishings();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = new BlockPos(1 + i, 2, 2);
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos.south(), Blocks.STONE);
            ContentTestSupport.assertPlacesAndDropsSelf(helper, blocks.get(i), pos);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ lantern

    /** Clicking the top, the side and the underside of a block stands, brackets and hangs the lantern. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void lanternStandsHangsAndBrackets(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos post = new BlockPos(4, 2, 4);
        helper.setBlock(post, Blocks.OAK_PLANKS);
        Block lantern = ShipDecor.SHIP_LANTERN.get();

        BlockPos floor = place(helper, player, lantern, post, Direction.UP);
        helper.assertBlockProperty(floor, ShipLanternBlock.FACE, AttachFace.FLOOR);
        BlockPos ceiling = place(helper, player, lantern, post, Direction.DOWN);
        helper.assertBlockProperty(ceiling, ShipLanternBlock.FACE, AttachFace.CEILING);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos wall = place(helper, player, lantern, post, side);
            helper.assertBlockProperty(wall, ShipLanternBlock.FACE, AttachFace.WALL);
            helper.assertBlockProperty(wall, ShipLanternBlock.FACING, side);
        }
        BlockState state = helper.getBlockState(floor);
        helper.assertTrue(state.getLightEmission() == 14, "lantern light " + state.getLightEmission());
        helper.assertTrue(!state.getValue(ShipLanternBlock.WATERLOGGED), "dry lantern is waterlogged");
        // losing the block it hangs on brings every lantern down
        helper.setBlock(post, Blocks.AIR);
        for (BlockPos p : List.of(floor, ceiling, post.north(), post.east(), post.south(), post.west())) {
            helper.assertBlockNotPresent(lantern, p);
        }
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void lanternAndCoilAreWaterloggable(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 2; i++) {
            BlockPos ground = new BlockPos(2 + 3 * i, 1, 4);
            helper.setBlock(ground, Blocks.STONE);
            helper.setBlock(ground.above(), Blocks.WATER);
            Block block = i == 0 ? ShipDecor.SHIP_LANTERN.get() : ShipDecor.ROPE_COIL.get();
            BlockPos placed = place(helper, player, block, ground, Direction.UP);
            helper.assertBlockProperty(placed, BlockStateProperties.WATERLOGGED, true);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ bell

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void bellRingsAndStops(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos post = new BlockPos(4, 1, 4);
        helper.setBlock(post, Blocks.OAK_FENCE);
        helper.setBlock(post.above().north(), Blocks.OAK_PLANKS);
        BlockPos bell = place(helper, player, ShipDecor.SHIPS_BELL.get(), post, Direction.UP);
        helper.assertBlockProperty(bell, ShipsBellBlock.FACE, AttachFace.FLOOR);
        BlockPos side = place(helper, player, ShipDecor.SHIPS_BELL.get(), post.above().north(), Direction.WEST);
        helper.assertBlockProperty(side, ShipsBellBlock.FACE, AttachFace.WALL);
        helper.assertBlockProperty(side, ShipsBellBlock.FACING, Direction.WEST);
        long[] rungAt = new long[1];
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertBlockProperty(bell, ShipsBellBlock.RINGING, false);
                    use(helper, player, bell);
                    rungAt[0] = helper.getTick();
                    helper.assertBlockProperty(bell, ShipsBellBlock.RINGING, true);
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(bell, ShipsBellBlock.RINGING, false))
                .thenExecute(() -> {
                    long swung = helper.getTick() - rungAt[0];
                    int expected = DecorConfig.BELL_RING_TICKS.get();
                    helper.assertTrue(swung >= expected - 1 && swung <= expected + 2, "bell swung " + swung + " ticks, expected " + expected);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ rope coil

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void ropeCoilsStackToFour(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos deck = new BlockPos(4, 1, 4);
        helper.setBlock(deck, Blocks.OAK_PLANKS);
        Block coil = ShipDecor.ROPE_COIL.get();
        BlockPos pos = place(helper, player, coil, deck, Direction.UP);
        helper.assertBlockProperty(pos, RopeCoilBlock.LAYERS, 1);
        for (int n = 2; n <= RopeCoilBlock.MAX_LAYERS; n++) {
            InteractionResult r = tryPlace(helper, player, coil, pos, Direction.UP);
            helper.assertTrue(r.consumesAction(), "stacking coil " + n + " failed: " + r);
            helper.assertBlockProperty(pos, RopeCoilBlock.LAYERS, n);
        }
        // a fifth coil does not fit, and nothing lands on top of the stack
        tryPlace(helper, player, coil, pos, Direction.UP);
        helper.assertBlockProperty(pos, RopeCoilBlock.LAYERS, RopeCoilBlock.MAX_LAYERS);
        helper.assertBlockNotPresent(coil, pos.above());
        // breaking the stack gives back every coil
        BlockPos abs = helper.absolutePos(pos);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), helper.getLevel(), abs, null, player, axe);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(coil.asItem()) && drops.getFirst().getCount() == 4,
                "four stacked coils dropped " + drops);
        helper.succeed();
    }

    // ------------------------------------------------------------------ stern window

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void sternWindowShuttersToggle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, ShipDecor.STERN_WINDOW.get().defaultBlockState().setValue(SternWindowBlock.FACING, Direction.EAST));
        helper.assertBlockProperty(pos, SternWindowBlock.SHUTTERS, false);
        use(helper, player, pos);
        helper.assertBlockProperty(pos, SternWindowBlock.SHUTTERS, true);
        helper.assertBlockProperty(pos, SternWindowBlock.FACING, Direction.EAST);
        use(helper, player, pos);
        helper.assertBlockProperty(pos, SternWindowBlock.SHUTTERS, false);
        helper.succeed();
    }

    // ------------------------------------------------------------------ sea cot

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void seaCotPlacesBothHalvesAndBreaksAsOne(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Block cot = ShipDecor.SEA_COT.get();
        int x = 1;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos floor = new BlockPos(x, 1, 4);
            x += 2;
            helper.setBlock(floor, Blocks.STONE);
            player.setYRot(facing.toYRot());
            BlockPos foot = place(helper, player, cot, floor, Direction.UP);
            BlockPos head = foot.relative(facing);
            helper.assertBlockProperty(foot, SeaCotBlock.PART, BedPart.FOOT);
            helper.assertBlockProperty(foot, SeaCotBlock.FACING, facing);
            helper.assertBlockPresent(cot, head);
            helper.assertBlockProperty(head, SeaCotBlock.PART, BedPart.HEAD);
            helper.assertBlockProperty(head, SeaCotBlock.FACING, facing);
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(head)) == null, "the cot must have no block entity");
            if (facing == Direction.NORTH) {
                // either half going takes the other along
                helper.setBlock(head, Blocks.AIR);
                helper.assertBlockNotPresent(cot, foot);
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ on a ship

    private static final BlockPos HELM = new BlockPos(4, 3, 4);

    /**
     * Every decor block goes up with the ship and comes back on disassembly with its state: a standing lantern, a bell
     * on its post, three stacked coils, a window with closed shutters, the chart table and both halves of a cot.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 300)
    public static void furnishingsSurviveAssemblyAndDisassembly(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) helper.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
        }
        helper.setBlock(HELM, AssemblyContent.HELM.get());
        Map<BlockPos, BlockState> decor = new HashMap<>();
        decor.put(new BlockPos(2, 3, 2), ShipDecor.SHIP_LANTERN.get().defaultBlockState());
        decor.put(new BlockPos(2, 3, 6), ShipDecor.SHIPS_BELL.get().defaultBlockState().setValue(ShipsBellBlock.FACING, Direction.EAST));
        decor.put(new BlockPos(6, 3, 2), ShipDecor.ROPE_COIL.get().defaultBlockState().setValue(RopeCoilBlock.LAYERS, 3));
        decor.put(new BlockPos(6, 3, 6), ShipDecor.STERN_WINDOW.get().defaultBlockState().setValue(SternWindowBlock.SHUTTERS, true));
        decor.put(new BlockPos(5, 3, 5), ShipDecor.CHART_TABLE.get().defaultBlockState().setValue(ChartTableBlock.FACING, Direction.SOUTH));
        BlockState cot = ShipDecor.SEA_COT.get().defaultBlockState().setValue(SeaCotBlock.FACING, Direction.NORTH);
        decor.put(new BlockPos(3, 3, 3), cot.setValue(SeaCotBlock.PART, BedPart.FOOT));
        decor.put(new BlockPos(3, 3, 2), cot.setValue(SeaCotBlock.PART, BedPart.HEAD));
        decor.forEach((p, s) -> helper.setBlock(p, s));

        AssemblyResult r = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(r.success() && r.shipId() != null, "assembly failed: " + r);
        UUID id = r.shipId();
        ShipBody ship = SableShips.byId(helper.getLevel(), id);
        helper.assertTrue(ship != null, "no sub-level for " + id);
        BlockPos plotHelm = find(ship, s -> s.is(AssemblyContent.HELM.get()));
        helper.assertTrue(plotHelm != null, "helm not in the plot");
        // on the ship: every block sits at its offset from the helm with its state
        decor.forEach((p, s) -> {
            BlockState onShip = ship.level().getBlockState(plotHelm.offset(p.subtract(HELM)));
            helper.assertTrue(onShip.equals(s), "on the ship " + s + " became " + onShip);
        });
        BlockPos[] offset = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    ShipBody s = SableShips.byId(helper.getLevel(), id);
                    helper.assertTrue(s != null, "ship vanished before disassembly");
                    AssemblyResult d = ShipAssembler.disassemble(s, find(s, st -> st.is(AssemblyContent.HELM.get())), null);
                    if (!d.success()) throw new GameTestAssertException("not yet disassembled: " + d.outcome());
                })
                .thenExecute(() -> {
                    // the ship may have settled a little: find the helm within a block of its old spot
                    for (BlockPos d : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
                        if (helper.getBlockState(HELM.offset(d)).is(AssemblyContent.HELM.get())) offset[0] = d.immutable();
                    }
                    helper.assertTrue(offset[0] != null, "helm not back near " + HELM);
                    decor.forEach((p, s) -> {
                        BlockState back = helper.getBlockState(p.offset(offset[0]));
                        helper.assertTrue(back.equals(s), "after disassembly " + s + " became " + back);
                    });
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ helpers

    private static BlockPos find(ShipBody ship, Predicate<BlockState> what) {
        for (BlockPos p : ship.plotBlocks()) {
            if (what.test(ship.level().getBlockState(p))) return p;
        }
        return null;
    }

    /** Places {@code block} from the player's hand against {@code face} of {@code clicked}; returns the new position. */
    private static BlockPos place(GameTestHelper helper, Player player, Block block, BlockPos clicked, Direction face) {
        InteractionResult r = tryPlace(helper, player, block, clicked, face);
        helper.assertTrue(r.consumesAction(), "placing " + block + " on " + face + " of " + clicked + " failed: " + r);
        BlockPos placed = clicked.relative(face);
        helper.assertBlockPresent(block, placed);
        return placed;
    }

    private static InteractionResult tryPlace(GameTestHelper helper, Player player, Block block, BlockPos clicked, Direction face) {
        ItemStack stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(clicked);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, abs, false);
        return ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
    }

    /** A right click with an empty hand on {@code pos}. */
    private static void use(GameTestHelper helper, Player player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(abs);
        state.useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }
}
