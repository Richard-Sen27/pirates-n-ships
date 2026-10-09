package com.richardsenger.piratesnships.core.block;

import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.core.block.WaterloggingRules.Verdict;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.hammock.HammockRules;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.item.RopeItem;
import com.richardsenger.piratesnships.sailing.rope.RopeLines;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.DisassemblyMath;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.ShipsBellBlock;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusSync;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairContent;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.station.lookout.LookoutContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * WLOG1 (docs/design.md §4.8 "Waterlogging"): every partial block of ours is waterloggable (the registry walk, with
 * {@link WaterloggingRules}), each block family placed by its item into water is waterlogged in every cell it fills and
 * leaves water when broken, placed in air it is dry (and takes and gives water by bucket), a waterlogged cleat and
 * pump aboard a ship are no flood water and keep their water through assembly and disassembly, and a waterlogged cleat
 * and bell still work.
 */
public final class WaterloggingGameTests {

    private WaterloggingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(WaterloggingGameTests.class);
    }

    // ------------------------------------------------------------------ the rule

    /**
     * Every block we register: partial in any state (outline or collision not a full cube) means waterloggable with a
     * dry default state, unless named in {@link WaterloggingRules#EXCEPTIONS}; every exception names a registered,
     * partial, dry block.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_3)
    public static void everyPartialBlockIsWaterloggable(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (RegistryEntry<Block, ? extends Block> entry : ModRegistry.blocks()) {
            Block block = entry.get();
            String path = entry.id().getPath();
            paths.add(path);
            boolean fullCube = block.getStateDefinition().getPossibleStates().stream().allMatch(WaterloggingGameTests::isFullCube);
            boolean waterloggable = block instanceof SimpleWaterloggedBlock && block.defaultBlockState().hasProperty(Waterlogging.WATERLOGGED);
            Verdict verdict = WaterloggingRules.check(path, fullCube, waterloggable);
            if (WaterloggingRules.violates(verdict)) {
                problems.add(path + " (" + verdict + ")");
            }
            if (waterloggable && Waterlogging.isWaterlogged(block.defaultBlockState())) {
                problems.add(path + " (waterlogged by default)");
            }
        }
        for (String exception : WaterloggingRules.EXCEPTIONS.keySet()) {
            if (!paths.contains(exception)) problems.add(exception + " (an exception that is not registered)");
        }
        h.assertTrue(paths.size() > 20, "only " + paths.size() + " blocks registered");
        h.assertTrue(problems.isEmpty(), "partial blocks that are not waterloggable: " + problems);
        h.succeed();
    }

    private static boolean isFullCube(BlockState state) {
        return Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                && Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    // ------------------------------------------------------------------ placing and breaking

    /** One representative per block family: its item, the cell to place at, and every cell the placed block fills. */
    private record Family(String name, Supplier<? extends Item> item, BlockPos at, List<BlockPos> cells) {
    }

    /**
     * The families on a 24×24 stone floor (y=1), placed at y=2 by a player looking down and north. Two-block ones
     * (cannon: rear to the south; hammock and sea cot: head to the north) list both cells; the hammock hangs between
     * two stone posts.
     */
    private static List<Family> families() {
        Supplier<Item> cleat = () -> TriangularSailContent.CLEAT.get().asItem();
        List<Family> f = new ArrayList<>();
        f.add(one("cleat", cleat, new BlockPos(3, 2, 3)));
        BlockPos cannon = new BlockPos(6, 2, 3);
        f.add(new Family("cannon", () -> CannonContent.CANNON.get().asItem(), cannon, List.of(cannon, CannonRules.rearOf(cannon, Direction.NORTH))));
        f.add(one("yard", () -> SailingBlocks.YARD.get().asItem(), new BlockPos(9, 2, 3)));
        BlockPos hammock = new BlockPos(12, 2, 6);
        f.add(new Family("hammock", CrewContent.HAMMOCK_ITEM, hammock, List.of(hammock, HammockRules.head(hammock, Direction.NORTH))));
        f.add(one("bilge pump", () -> HullRepairContent.BILGE_PUMP.get().asItem(), new BlockPos(15, 2, 3)));
        f.add(one("ship's bell", () -> ShipDecor.SHIPS_BELL.get().asItem(), new BlockPos(18, 2, 3)));
        f.add(one("flagpole", () -> ShipDecor.FLAGPOLE.get().asItem(), new BlockPos(3, 2, 10)));
        f.add(one("ship's lantern", () -> ShipDecor.SHIP_LANTERN.get().asItem(), new BlockPos(6, 2, 10)));
        BlockPos cot = new BlockPos(9, 2, 12);
        f.add(new Family("sea cot", () -> ShipDecor.SEA_COT.get().asItem(), cot, List.of(cot, cot.relative(Direction.NORTH))));
        f.add(one("crow's nest", () -> LookoutContent.CROWS_NEST.get().asItem(), new BlockPos(15, 2, 10)));
        f.add(one("swivel gun", () -> CannonContent.SWIVEL_GUN.get().asItem(), new BlockPos(18, 2, 10)));
        // not the sea chest: its item launches a floating chest in world water by design (SeaChestItem), aboard it
        // places the block like these; the registry walk covers the block
        f.add(one("notice board", () -> LawContent.NOTICE_BOARD.get().asItem(), new BlockPos(3, 2, 17)));
        f.add(one("chart table", () -> ShipDecor.CHART_TABLE.get().asItem(), new BlockPos(6, 2, 17)));
        return f;
    }

    private static Family one(String name, Supplier<? extends Item> item, BlockPos at) {
        return new Family(name, item, at, List.of(at));
    }

    /** Stone floor, the hammock's two posts, and (with {@code water}) one layer of still water at y=2 in a stone rim. */
    private static void pool(GameTestHelper h, boolean water) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean rim = x == 0 || z == 0 || x == 23 || z == 23;
                h.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE : water ? Blocks.WATER : Blocks.AIR);
            }
        }
        BlockPos hammock = new BlockPos(12, 2, 6);
        h.setBlock(hammock.relative(Direction.SOUTH), Blocks.STONE);
        h.setBlock(HammockRules.head(hammock, Direction.NORTH).relative(Direction.NORTH), Blocks.STONE);
    }

    /** A survival player looking down and north. */
    private static Player placer(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setYRot(180f);
        p.setXRot(90f);
        p.yHeadRot = 180f;
        return p;
    }

    /** Uses {@code item} on the top of the block below {@code rel}, as a player placing it there. */
    private static void place(GameTestHelper h, Player player, Item item, BlockPos rel) {
        BlockPos below = h.absolutePos(rel.below());
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** A player breaking the block at {@code pos} by hand, as {@code ServerPlayerGameMode#destroyBlock} does. */
    private static void breakByHand(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        state.getBlock().playerWillDestroy(level, pos, state, player);
        level.removeBlock(pos, false);
    }

    private static void assertPlaced(GameTestHelper h, Family f, boolean water) {
        Block block = null;
        for (BlockPos rel : f.cells()) {
            BlockState s = h.getBlockState(rel);
            h.assertTrue(s.getBlock() instanceof SimpleWaterloggedBlock, f.name() + ": no waterloggable block at " + rel + " but " + s);
            if (block == null) block = s.getBlock();
            h.assertTrue(s.getBlock() == block, f.name() + ": the parts are different blocks: " + s);
            h.assertTrue(Waterlogging.isWaterlogged(s) == water, f.name() + " at " + rel + (water ? " is dry in water: " : " is wet in air: ") + s);
            h.assertTrue(s.getFluidState().is(Fluids.WATER) == water, f.name() + " at " + rel + " has the fluid " + s.getFluidState());
        }
    }

    /** Placed into still water, every family's block is waterlogged in every cell; broken by hand, water stays. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void placedInWaterIsWaterloggedAndBreakingLeavesWater(GameTestHelper h) {
        pool(h, true);
        Player player = placer(h);
        ServerLevel level = h.getLevel();
        for (Family f : families()) {
            place(h, player, f.item().get(), f.at());
            assertPlaced(h, f, true);
            breakByHand(level, h.absolutePos(f.at()), player);
            // checked in the same tick, before the pool's sources could refill a lost cell
            for (BlockPos rel : f.cells()) {
                BlockState s = h.getBlockState(rel);
                h.assertTrue(s.is(Blocks.WATER) && s.getFluidState().isSource(), f.name() + ": broken, it left " + s + " at " + rel);
            }
        }
        player.discard();
        h.succeed();
    }

    /**
     * Placed in air, every family's block is dry and breaking it leaves air. A water bucket fills it
     * ({@code placeLiquid} from the interface) and an empty one takes the water back ({@code pickupBlock}).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void placedInAirIsDryAndTakesWaterByBucket(GameTestHelper h) {
        pool(h, false);
        Player player = placer(h);
        ServerLevel level = h.getLevel();
        for (Family f : families()) {
            place(h, player, f.item().get(), f.at());
            assertPlaced(h, f, false);
            BlockPos at = h.absolutePos(f.at());
            BlockState dry = level.getBlockState(at);
            SimpleWaterloggedBlock wl = (SimpleWaterloggedBlock) dry.getBlock();
            h.assertTrue(wl.canPlaceLiquid(player, level, at, dry, Fluids.WATER), f.name() + " takes no water");
            h.assertTrue(wl.placeLiquid(level, at, dry, Fluids.WATER.getSource(false)), f.name() + ": the bucket did not fill it");
            h.assertTrue(Waterlogging.isWaterlogged(level.getBlockState(at)), f.name() + " is dry after the bucket");
            ItemStack bucket = wl.pickupBlock(player, level, at, level.getBlockState(at));
            h.assertTrue(bucket.is(Items.WATER_BUCKET), f.name() + ": the empty bucket got " + bucket);
            h.assertFalse(Waterlogging.isWaterlogged(level.getBlockState(at)), f.name() + " is still wet after the bucket took the water");
            breakByHand(level, at, player);
            for (BlockPos rel : f.cells()) {
                h.assertTrue(h.getBlockState(rel).isAir(), f.name() + ": broken, it left " + h.getBlockState(rel) + " at " + rel);
            }
        }
        player.discard();
        h.succeed();
    }

    // ------------------------------------------------------------------ aboard a ship

    /**
     * The closed 5×4×5 hull of {@link DryHullGameTests} afloat, with a waterlogged cleat and a waterlogged bilge pump on
     * the hold floor: both keep their water through assembly, the hold stays one dry 18-cell compartment with one dry
     * region, the flooding holds no water and the ship status shows none; after disassembly both blocks are still
     * waterlogged in the world.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400)
    public static void waterloggedBlocksAboardAreNoFloodWater(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helmRel = DryHullGameTests.hull(h, 9, false);
        BlockPos cleatRel = new BlockPos(10, 6, 10), pumpRel = new BlockPos(12, 6, 12);
        h.setBlock(cleatRel, TriangularSailContent.CLEAT.get().defaultBlockState()
                .setValue(com.richardsenger.piratesnships.sailing.block.CleatBlock.FACE, AttachFace.FLOOR)
                .setValue(Waterlogging.WATERLOGGED, true));
        h.setBlock(pumpRel, HullRepairContent.BILGE_PUMP.get().defaultBlockState().setValue(Waterlogging.WATERLOGGED, true));
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos cleat = f.hold(-1, -3, -1), pump = f.hold(1, -3, 1);
        h.assertTrue(level.getBlockState(cleat).is(TriangularSailContent.CLEAT.get()) && Waterlogging.isWaterlogged(level.getBlockState(cleat)),
                "the cleat lost its water in assembly: " + level.getBlockState(cleat));
        h.assertTrue(level.getBlockState(pump).is(HullRepairContent.BILGE_PUMP.get()) && Waterlogging.isWaterlogged(level.getBlockState(pump)),
                "the pump lost its water in assembly: " + level.getBlockState(pump));
        h.assertTrue(f.runtime().simulation().analysis().compartments().size() == 1
                        && f.runtime().simulation().analysis().compartments().get(0).volume() == 18,
                "the waterlogged blocks changed the hold: " + f.runtime().simulation().analysis().compartments());
        boolean[] dry = {false};
        h.runAfterDelay(60, () -> {
            h.assertTrue(f.runtime().simulation().totalVolume() == 0, "the waterlogged blocks flood the hold: "
                    + f.runtime().simulation().totalVolume());
            h.assertTrue(f.runtime().regionCount() == 1 && f.runtime().regionCells().get(0).count() == 18,
                    "the hold is not one dry 18-cell region");
            ShipStatusPayload status = ShipStatusSync.build(level, f.ship());
            h.assertTrue(status.cells().size() == 1, "expected one status cell, got " + status.cells());
            ShipStatusPayload.Cell c = status.cells().get(0);
            h.assertTrue(c.volume() == 18 && c.water() == 0f && c.fraction() == 0f,
                    "the ship status shows water in the hold: " + c.water() + " of " + c.volume());
            dry[0] = true;
        });
        // disassembly waits until the floating ship has settled (it refuses while MOVING)
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (!dry[0] || done[0] || h.getTick() % 5 != 0) return;
            int turns = DisassemblyMath.quarterTurns(f.ship().orientation());
            BlockPos goal = BlockPos.containing(f.ship().toWorld(Vec3.atCenterOf(f.helmPlot())));
            BlockPos cleatWorld = DisassemblyMath.target(cleat, f.helmPlot(), goal, turns);
            BlockPos pumpWorld = DisassemblyMath.target(pump, f.helmPlot(), goal, turns);
            AssemblyResult r = ShipAssembler.disassemble(f.ship(), f.helmPlot(), null);
            if (r.outcome() == AssemblyResult.Outcome.MOVING) return;
            done[0] = true;
            h.assertTrue(r.outcome() == AssemblyResult.Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + r);
            BlockState cw = level.getBlockState(cleatWorld), pw = level.getBlockState(pumpWorld);
            h.assertTrue(cw.is(TriangularSailContent.CLEAT.get()) && Waterlogging.isWaterlogged(cw), "the cleat after disassembly: " + cw);
            h.assertTrue(pw.is(HullRepairContent.BILGE_PUMP.get()) && Waterlogging.isWaterlogged(pw), "the pump after disassembly: " + pw);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ still working when wet

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** Two cleats placed in water at the same height still take a rope and make a line between them. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void waterloggedCleatStillTiesARope(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 2, z), x == 0 || z == 0 || x == 8 || z == 8 ? Blocks.STONE : Blocks.WATER);
            }
        }
        Player player = placer(h);
        BlockPos aRel = new BlockPos(2, 2, 4), bRel = new BlockPos(6, 2, 4);
        place(h, player, TriangularSailContent.CLEAT.get().asItem(), aRel);
        place(h, player, TriangularSailContent.CLEAT.get().asItem(), bRel);
        h.assertTrue(Waterlogging.isWaterlogged(h.getBlockState(aRel)) && Waterlogging.isWaterlogged(h.getBlockState(bRel)),
                "the cleats are not waterlogged: " + h.getBlockState(aRel) + ", " + h.getBlockState(bRel));
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(aRel), b = h.absolutePos(bRel);
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 4);
        Component tied = RopeItem.use(level, rope, a, null);
        h.assertTrue(key(tied).equals(RopeItem.KEY_TIED), "no tie at the wet cleat: " + tied.getString());
        Component line = RopeItem.use(level, rope, b, null);
        h.assertTrue(key(line).equals(RopeItem.KEY_LINE), "two wet cleats made no line: " + line.getString());
        h.assertTrue(RopeLines.joined(level, a, b), "the wet cleats do not agree on the line");
        h.assertTrue(Waterlogging.isWaterlogged(level.getBlockState(a)) && Waterlogging.isWaterlogged(level.getBlockState(b)),
                "tying the rope dried a cleat");
        player.discard();
        h.succeed();
    }

    /** A bell placed in water rings (RINGING, then back) and stays waterlogged throughout. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void waterloggedBellStillRings(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 2, z), x == 0 || z == 0 || x == 8 || z == 8 ? Blocks.STONE : Blocks.WATER);
            }
        }
        Player player = placer(h);
        BlockPos rel = new BlockPos(4, 2, 4);
        place(h, player, ShipDecor.SHIPS_BELL.get().asItem(), rel);
        h.assertTrue(h.getBlockState(rel).is(ShipDecor.SHIPS_BELL.get()) && Waterlogging.isWaterlogged(h.getBlockState(rel)),
                "the bell is not waterlogged: " + h.getBlockState(rel));
        BlockPos abs = h.absolutePos(rel);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.startSequence()
                .thenExecute(() -> {
                    BlockState state = h.getLevel().getBlockState(abs);
                    state.useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, 0.3), Direction.SOUTH, abs, false));
                    h.assertBlockProperty(rel, ShipsBellBlock.RINGING, true);
                    h.assertTrue(Waterlogging.isWaterlogged(h.getBlockState(rel)), "ringing dried the bell");
                })
                .thenWaitUntil(() -> h.assertBlockProperty(rel, ShipsBellBlock.RINGING, false))
                .thenExecute(() -> {
                    h.assertTrue(Waterlogging.isWaterlogged(h.getBlockState(rel)), "the bell dried after ringing");
                    player.discard();
                })
                .thenSucceed();
    }
}
