package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.DisassemblyMath;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Net;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Ratline nets (RL2, docs/design.md §4.8 "Ratline nets between fittings") in a real server: stringing a net between a
 * chainplate and a mast hoop with the item, on land and in a ship's plot; the refusals (a blocked line, a flat line,
 * fittings on different bodies); breaking a cell, a fitting or placing a block into the net; a net through assembly and
 * a turned disassembly; and a mock player climbing a near-vertical, a 60° and a 45° net from bottom to top on land and
 * on an assembled hull afloat (the climbing assist, {@code rigging.net_assist}), plus the vanilla-only climb of a
 * vertical net with the assist off. RL4's fans: three strands to one hoop on land and on a ship (the triangle's fill
 * cells, their climb direction, the top cell), a player climbing a fill to the hoop, breaking a chainplate, a fill cell
 * or the hoop, a strand beyond the spread and a full hoop, a fan through assembly and a turned disassembly.
 */
public final class RatlineNetGameTests {

    private static final String BATCH = "pirates_n_ships_config_rigging_";
    /** Ticks after assembly before a player boards. */
    private static final int SETTLE = 40;

    private RatlineNetGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RatlineNetGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    static BlockState chainplate(AttachFace face, Direction facing) {
        return RiggingContent.CHAINPLATE.get().defaultBlockState().setValue(ChainplateBlock.FACE, face)
                .setValue(ChainplateBlock.FACING, facing);
    }

    static BlockState hoop(Direction facing) {
        return RiggingContent.MAST_HOOP.get().defaultBlockState().setValue(MastHoopBlock.FACING, facing);
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static int droppedNets(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(2),
                        e -> e.isAlive() && e.getItem().is(RiggingContent.NET_ITEM.get()))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /**
     * A land rig in a 9 block test: a log mast at x 6, z 4 from y 1 to 7, a hoop on its west side at {@code hoopY}
     * (ring x 5.7) and a chainplate on the floor at x {@code plateX} (ring at the block centre, 0.3 up).
     */
    private static void landRig(GameTestHelper h, int plateX, int hoopY) {
        floor(h, 9);
        for (int y = 1; y <= 7; y++) {
            h.setBlock(new BlockPos(6, y, 4), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(5, hoopY, 4), hoop(Direction.WEST));
        h.setBlock(new BlockPos(plateX, 1, 4), chainplate(AttachFace.FLOOR, Direction.EAST));
    }

    /** Strings a net with a fresh stack: first {@code a}, then {@code b} (absolute positions); returns the second answer. */
    private static String string(ServerLevel level, BlockPos a, BlockPos b, ItemStack stack) {
        String first = key(RatlineNetItem.use(level, stack, a, null));
        if (!first.equals(RatlineNetItem.KEY_MARKED)) {
            return first;
        }
        return key(RatlineNetItem.use(level, stack, b, null));
    }

    /** A survival mock player in the level at the world position, discarded when the test ends. */
    private static Player playerInLevel(GameTestHelper h, Vec3 world, float yRot) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(world.x, world.y, world.z, yRot, 0f);
        h.getLevel().addFreshEntity(player);
        testInfo(h).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { player.discard(); }
        });
        return player;
    }

    /** {@code GameTestHelper#testInfo} is private and has no getter in 1.21.1. */
    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            for (Field f : GameTestHelper.class.getDeclaredFields()) {
                if (f.getType() == GameTestInfo.class) {
                    f.setAccessible(true);
                    return (GameTestInfo) f.get(helper);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
    }

    /**
     * At tick {@code start} spawns a player with {@code spawn}, then each tick holds forward (or jump) and tracks its
     * feet in the net's frame ({@code frame}: the world, or the ship's plot); {@code forTicks} later the highest feet
     * must be within 1.2 blocks under ring B's height and the feet must have come within 1.5 blocks of ring B across.
     * Everything is scheduled up front (scheduling from a scheduled task changes the task map while it is iterated).
     */
    private static void climb(GameTestHelper h, int start, Supplier<Player> spawn, Supplier<Net> net,
                              Function<Vec3, Vec3> frame, boolean jump, boolean forward, int forTicks, String what) {
        Player[] player = {null};
        double[] y0 = {0};
        double[] max = {Double.NEGATIVE_INFINITY};
        double[] across = {Double.POSITIVE_INFINITY};
        Net[] n = {null};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == start) {
                n[0] = net.get();
                player[0] = spawn.get();
                y0[0] = frame.apply(player[0].position()).y;
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) {
                return;
            }
            p.setJumping(jump);
            p.zza = forward ? 1f : 0f;
            Vec3 feet = frame.apply(p.position());
            if (feet.y > max[0]) {
                max[0] = feet.y;
                Point b = n[0].ringB();
                across[0] = Math.hypot(feet.x - b.x(), feet.z - b.z());
            }
        });
        h.runAfterDelay(start + forTicks, () -> {
            h.assertTrue(player[0] != null, what + ": no player was spawned");
            Point b = n[0].ringB();
            Constants.LOG.info("[RL2] {}: pitch {}, start y {}, highest {} ({} across from ring B at y {})", what,
                    String.format("%.1f", RatlineNetRules.pitch(n[0].ringA(), b)), y0[0], max[0], across[0], b.y());
            h.assertTrue(max[0] >= b.y() - 1.2, what + ": climbed only to y " + max[0] + " (ring B at " + b.y() + ", from " + y0[0] + ")");
            h.assertTrue(across[0] <= 1.5, what + ": at the top " + across[0] + " blocks across from ring B");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ stringing on land

    /**
     * A chainplate on the floor and a hoop on the mast 45° up: the item ties A, then strings the net through every cell
     * of the line (the walk without the fittings), the fittings name each other, the cells are climbable and name A,
     * and one net item is used up.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void stringsANetFromAChainplateToAHoop(GameTestHelper h) {
        landRig(h, 1, 5);
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 4)), b = h.absolutePos(new BlockPos(5, 5, 4));
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get(), 2);
        h.assertTrue(key(RatlineNetItem.use(level, stack, a, null)).equals(RatlineNetItem.KEY_MARKED), "A was not tied");
        h.assertTrue(stack.has(RiggingContent.NET_START.get()), "the item does not remember A");
        h.assertTrue(key(RatlineNetItem.use(level, stack, b, null)).equals(RatlineNetItem.KEY_STRUNG), "the net was not strung");
        h.assertTrue(stack.getCount() == 1 && !stack.has(RiggingContent.NET_START.get()), "the item was not used up");
        Net net = RatlineNets.netOf(level, a);
        h.assertTrue(net != null && net.a().equals(a) && net.b().equals(b), "fitting A does not hold the net: " + net);
        h.assertTrue(RatlineNets.netOf(level, b) != null, "fitting B does not hold the net");
        h.assertTrue(Math.abs(RatlineNetRules.pitch(net.ringA(), net.ringB()) - 45) < 1e-6, "not a 45 degree net");
        List<BlockPos> cells = RatlineNets.standingCells(level, net);
        h.assertTrue(cells.size() == 7 && cells.size() == net.cells().size(), "expected 7 cells, found " + cells.size());
        for (BlockPos c : cells) {
            h.assertTrue(level.getBlockState(c).is(BlockTags.CLIMBABLE), "a cell is not climbable");
            h.assertTrue(level.getBlockEntity(c) instanceof RatlineNetCellBlockEntity be && a.equals(be.fittingA()),
                    "a cell does not name A");
        }
        h.succeed();
    }

    /** A line through a block is refused, naming the blocked cell; nothing is placed, A stays tied. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aBlockedLineIsRefused(GameTestHelper h) {
        landRig(h, 1, 5);
        h.setBlock(new BlockPos(3, 3, 4), Blocks.OAK_PLANKS);
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 4)), b = h.absolutePos(new BlockPos(5, 5, 4));
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        RatlineNetItem.use(level, stack, a, null);
        Component answer = RatlineNetItem.use(level, stack, b, null);
        h.assertTrue(key(answer).equals(RatlineNetItem.KEY_BLOCKED), "not refused as blocked: " + answer.getString());
        BlockPos blocked = h.absolutePos(new BlockPos(3, 3, 4));
        Object[] args = ((TranslatableContents) answer.getContents()).getArgs();
        h.assertTrue(args.length == 3 && args[0].equals(blocked.getX()) && args[1].equals(blocked.getY()) && args[2].equals(blocked.getZ()),
                "the refusal does not name the blocked cell " + blocked + ": " + java.util.Arrays.toString(args));
        h.assertTrue(stack.getCount() == 1 && stack.has(RiggingContent.NET_START.get()), "the item was used or forgot A");
        h.assertTrue(RatlineNets.netOf(level, a) == null, "A holds a net");
        h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(2, 1, 4))).isAir(), "a cell was placed");
        h.succeed();
    }

    /** A 20 degree line is too flat, a 13 block one too long; both refused and nothing placed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void aFlatOrLongLineIsRefused(GameTestHelper h) {
        floor(h, 24);
        ServerLevel level = h.getLevel();
        for (int y = 1; y <= 16; y++) {
            h.setBlock(new BlockPos(20, y, 4), Blocks.OAK_LOG);
        }
        // flat: ring A (14.5, 1.3), ring B (19.7, 3.5): 5.2 across, 2.2 up, 23 degrees: too flat but short enough
        h.setBlock(new BlockPos(19, 3, 4), hoop(Direction.WEST));
        h.setBlock(new BlockPos(14, 1, 4), chainplate(AttachFace.FLOOR, Direction.EAST));
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        String flat = string(level, h.absolutePos(new BlockPos(14, 1, 4)), h.absolutePos(new BlockPos(19, 3, 4)), stack);
        h.assertTrue(flat.equals(RatlineNetItem.KEY_TOO_FLAT), "a 23 degree net was not refused as flat: " + flat);
        // long: a vertical net 14 blocks up
        h.setBlock(new BlockPos(19, 16, 4), hoop(Direction.WEST));
        h.setBlock(new BlockPos(19, 1, 4), chainplate(AttachFace.FLOOR, Direction.EAST));
        ItemStack second = new ItemStack(RiggingContent.NET_ITEM.get());
        String tall = string(level, h.absolutePos(new BlockPos(19, 1, 4)), h.absolutePos(new BlockPos(19, 16, 4)), second);
        h.assertTrue(tall.equals(RatlineNetItem.KEY_TOO_LONG), "a 15 block net was not refused as too long: " + tall);
        h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(19, 5, 4))).isAir(), "a cell was placed");
        h.assertTrue(stack.getCount() == 1 && second.getCount() == 1, "an item was used up");
        h.succeed();
    }

    /** {@code rigging.nets_enabled = false}: nothing is tied or strung. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "nets_off")
    public static void disabledNetsStringNothing(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.NETS_ENABLED, false);
        landRig(h, 1, 5);
        ServerLevel level = h.getLevel();
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        String r = string(level, h.absolutePos(new BlockPos(1, 1, 4)), h.absolutePos(new BlockPos(5, 5, 4)), stack);
        h.assertTrue(r.equals(RatlineNetItem.KEY_DISABLED), "not refused while disabled: " + r);
        h.assertTrue(stack.getCount() == 1 && RatlineNets.netOf(level, h.absolutePos(new BlockPos(1, 1, 4))) == null,
                "a net was strung while disabled");
        h.succeed();
    }

    // ------------------------------------------------------------------ breaking

    /** Breaking one cell takes every cell down, drops one net item and leaves both fittings without a net. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingACellDropsTheNetAndClearsTheRun(GameTestHelper h) {
        landRig(h, 1, 5);
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 4)), b = h.absolutePos(new BlockPos(5, 5, 4));
        string(level, a, b, new ItemStack(RiggingContent.NET_ITEM.get()));
        Net net = RatlineNets.netOf(level, a);
        h.assertTrue(net != null, "no net");
        level.destroyBlock(h.absolutePos(new BlockPos(3, 3, 4)), true);
        h.assertTrue(RatlineNets.standingCells(level, net).isEmpty(), "cells stayed: " + RatlineNets.standingCells(level, net));
        h.assertTrue(level.getBlockState(a).is(RiggingContent.CHAINPLATE.get()) && level.getBlockState(b).is(RiggingContent.MAST_HOOP.get()),
                "a fitting went with the net");
        h.assertTrue(RatlineNets.netOf(level, a) == null && RatlineNets.netOf(level, b) == null, "a fitting still holds the net");
        h.runAfterDelay(2, () -> {
            h.assertTrue(droppedNets(h) == 1, "expected one dropped net, found " + droppedNets(h));
            h.succeed();
        });
    }

    /** Breaking fitting B (the hoop) breaks the net; a block placed into a second net's cell breaks that one too. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingAFittingOrBlockingACellBreaksTheNet(GameTestHelper h) {
        landRig(h, 1, 5);
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 4)), b = h.absolutePos(new BlockPos(5, 5, 4));
        string(level, a, b, new ItemStack(RiggingContent.NET_ITEM.get()));
        Net net = RatlineNets.netOf(level, a);
        level.destroyBlock(b, false);
        h.assertTrue(RatlineNets.standingCells(level, net).isEmpty(), "cells stayed after the hoop broke");
        h.assertTrue(RatlineNets.netOf(level, a) == null, "the chainplate still holds the net");
        // a second, vertical net on the north side of the mast; a block set into its middle cell
        h.setBlock(new BlockPos(6, 5, 3), hoop(Direction.NORTH));
        h.setBlock(new BlockPos(6, 1, 3), chainplate(AttachFace.FLOOR, Direction.EAST));
        BlockPos a2 = h.absolutePos(new BlockPos(6, 1, 3)), b2 = h.absolutePos(new BlockPos(6, 5, 3));
        String r = string(level, a2, b2, new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "the vertical net was not strung: " + r);
        Net second = RatlineNets.netOf(level, a2);
        h.assertTrue(RatlineNets.standingCells(level, second).size() == 3, "the vertical net has "
                + RatlineNets.standingCells(level, second).size() + " cells, expected 3");
        level.setBlockAndUpdate(h.absolutePos(new BlockPos(6, 3, 3)), Blocks.OAK_PLANKS.defaultBlockState());
        h.assertTrue(RatlineNets.standingCells(level, second).isEmpty(), "cells stayed after a block was placed into the net");
        h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(6, 3, 3))).is(Blocks.OAK_PLANKS), "the placed block went");
        h.runAfterDelay(2, () -> {
            h.assertTrue(droppedNets(h) == 2, "expected two dropped nets, found " + droppedNets(h));
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ climbing on land

    private static Supplier<Net> netAt(GameTestHelper h, BlockPos rel) {
        return () -> RatlineNets.netOf(h.getLevel(), h.absolutePos(rel));
    }

    private static void landClimb(GameTestHelper h, int plateX, int hoopY, boolean jump, boolean forward, String what) {
        landRig(h, plateX, hoopY);
        BlockPos plate = new BlockPos(plateX, 1, 4);
        String r = string(h.getLevel(), h.absolutePos(plate), h.absolutePos(new BlockPos(5, hoopY, 4)),
                new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), what + ": not strung: " + r);
        climb(h, 0, () -> playerInLevel(h, h.absoluteVec(new Vec3(plateX + 0.5, 1.0, 4.5)), Direction.EAST.toYRot()),
                netAt(h, plate), v -> v, jump, forward, 100, what);
    }

    /** A near-vertical net (87°) on land: a player holding forward climbs it to the top. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 140)
    public static void playerClimbsAVerticalNetOnLand(GameTestHelper h) {
        landClimb(h, 5, 5, false, true, "vertical net on land");
    }

    /** A 62° net on land. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 140)
    public static void playerClimbsASixtyDegreeNetOnLand(GameTestHelper h) {
        landClimb(h, 3, 5, false, true, "60 degree net on land");
    }

    /** A 45° net on land, climbed holding only jump this time (vanilla's jump climb, carried along the line). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 140)
    public static void playerClimbsAFortyFiveDegreeNetOnLand(GameTestHelper h) {
        landClimb(h, 1, 5, true, false, "45 degree net on land");
    }

    /** With the assist off, vanilla ladder climbing alone (holding jump) still takes a player up a vertical net. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 140, batch = BATCH + "assist_off")
    public static void vanillaClimbsAVerticalNetWithoutTheAssist(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.NET_ASSIST, false);
        landClimb(h, 5, 5, true, false, "vertical net on land, no assist");
    }

    /**
     * With the assist off, vanilla alone takes a player up a 45° net only while it holds jump and forward together (the
     * cells have no collision, so forward alone never climbs): the jump climb lifts it out of each cell and forward
     * carries it into the next one. The assist makes forward alone climb and keeps the climber on the line.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 140, batch = BATCH + "assist_off")
    public static void vanillaClimbsAFortyFiveDegreeNetWithJumpAndForward(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.NET_ASSIST, false);
        landClimb(h, 1, 5, true, true, "45 degree net on land, no assist, jump and forward");
    }

    // ------------------------------------------------------------------ RL4: fans

    /** The chainplates' z of the land fan: two blocks apart on the floor at x 2, so the rail cells between them are fill. */
    private static final int[] LAND_FAN_Z = {2, 4, 6};
    /** The chainplates' z of the ship fan: on the deck at x 9, two blocks apart. */
    private static final int[] SHIP_FAN_Z = {9, 11, 13};

    /**
     * A land fan in a 9 block test: a log mast at x 6, z 4 from y 1 to 7, a hoop on its west side at y 6 (ring 5.7, 6.5,
     * 4.5) and chainplates on the floor at x 2, z 2, 4 and 6; all three strung to the hoop with one stack of three.
     * Returns the hoop's absolute position.
     */
    private static BlockPos landFan(GameTestHelper h) {
        floor(h, 9);
        for (int y = 1; y <= 7; y++) {
            h.setBlock(new BlockPos(6, y, 4), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(5, 6, 4), hoop(Direction.WEST));
        ServerLevel level = h.getLevel();
        BlockPos hub = h.absolutePos(new BlockPos(5, 6, 4));
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get(), 3);
        for (int z : LAND_FAN_Z) {
            h.setBlock(new BlockPos(2, 1, z), chainplate(AttachFace.FLOOR, Direction.EAST));
            String r = string(level, h.absolutePos(new BlockPos(2, 1, z)), hub, stack);
            h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "strand at z " + z + " not strung: " + r);
        }
        h.assertTrue(stack.isEmpty(), "three strands did not use three nets: " + stack.getCount() + " left");
        return hub;
    }

    /**
     * Null when every cell the fan at {@code hub} wants stands as a net cell owned as it should be (strand or fill, the
     * right strand, the hub), else what is wrong. {@code fills} receives the number of fill cells.
     */
    private static String fanProblem(ServerLevel level, BlockPos hub, int[] fills) {
        RatlineNets.Fan fan = RatlineNets.fanAt(level, hub);
        fills[0] = 0;
        for (var e : fan.cells().entrySet()) {
            BlockPos p = e.getKey();
            Net n = e.getValue().owner().net();
            if (!level.getBlockState(p).is(RiggingContent.NET_CELL.get())
                    || !(level.getBlockEntity(p) instanceof RatlineNetCellBlockEntity cell)
                    || !cell.ownedBy(n.a(), n.b(), hub, e.getValue().fill())) {
                return "cell " + p + " (" + e.getValue() + ") is " + level.getBlockState(p);
            }
            if (e.getValue().fill()) {
                fills[0]++;
            }
        }
        return null;
    }

    private static int netCount(ServerLevel level, BlockPos fitting) {
        return level.getBlockEntity(fitting) instanceof NetFittingBlockEntity be ? be.netCount() : -1;
    }

    /**
     * Three chainplates two blocks apart and one hoop on land: three strands, one net item each; the hoop holds all
     * three in fan order, joined; every strand's cells stand and the rail cells between the chainplates are fill cells
     * of the triangles, each knowing its climb direction towards the hoop; the cell under the hoop is a top cell.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void threeStrandsToOneHoopMakeATriangle(GameTestHelper h) {
        BlockPos hub = landFan(h);
        ServerLevel level = h.getLevel();
        h.assertTrue(netCount(level, hub) == 3, "the hoop holds " + netCount(level, hub) + " nets");
        List<RatlineNets.Strand> strands = RatlineNets.hubStrands(level, hub);
        h.assertTrue(strands.size() == 3, "the hoop is the hub of " + strands.size() + " strands");
        // fan order by bearing: z 6, 4, 2; the first two join the next
        h.assertTrue(strands.get(0).foot().equals(h.absolutePos(new BlockPos(2, 1, 6)))
                && strands.get(2).foot().equals(h.absolutePos(new BlockPos(2, 1, 2))), "not in fan order: " + strands);
        NetFittingBlockEntity be = (NetFittingBlockEntity) level.getBlockEntity(hub);
        h.assertTrue(be.joinsNext(strands.get(0).foot()) && be.joinsNext(strands.get(1).foot())
                && !be.joinsNext(strands.get(2).foot()), "the strands do not join as one net");
        for (RatlineNets.Strand s : strands) {
            h.assertTrue(RatlineNets.standingCells(level, s.net()).size() == s.net().cells().size(), "a strand misses cells");
        }
        int[] fills = {0};
        String problem = fanProblem(level, hub, fills);
        h.assertTrue(problem == null, "the fan does not stand: " + problem);
        h.assertTrue(fills[0] == 4, "expected 4 fill cells, found " + fills[0]);
        for (int z : new int[] {3, 5}) {
            BlockPos rail = h.absolutePos(new BlockPos(2, 1, z));
            h.assertTrue(level.getBlockState(rail).is(BlockTags.CLIMBABLE)
                    && level.getBlockEntity(rail) instanceof RatlineNetCellBlockEntity cell && cell.isFill(),
                    "the rail cell at z " + z + " is not a fill cell");
            RatlineNetCellBlockEntity cell = (RatlineNetCellBlockEntity) level.getBlockEntity(rail);
            Vec3 d = cell.climbDirection();
            Point ring = RatlineNets.ring(level, hub);
            Vec3 want = new Vec3(ring.x(), ring.y(), ring.z()).subtract(Vec3.atCenterOf(rail)).normalize();
            h.assertTrue(d != null && d.distanceTo(want) < 1e-6, "the climb direction at z " + z + " is " + d + ", expected " + want);
            h.assertTrue(!cell.isTop(), "a rail cell is a top cell");
        }
        BlockPos under = h.absolutePos(new BlockPos(5, 5, 4));
        h.assertTrue(level.getBlockEntity(under) instanceof RatlineNetCellBlockEntity top && top.isTop(),
                "the cell under the hoop is not a top cell");
        h.succeed();
    }

    /**
     * Breaking the middle chainplate drops its net only; the outer two strands stay and re-fan into one triangle (the
     * middle strand's cells now fill it, the broken chainplate's own block too once the hoop checked its fan again).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingTheMiddleChainplateReFansTheOuterStrands(GameTestHelper h) {
        BlockPos hub = landFan(h);
        ServerLevel level = h.getLevel();
        BlockPos middle = h.absolutePos(new BlockPos(2, 1, 4));
        Net gone = RatlineNets.net(level, middle, hub);
        level.destroyBlock(middle, false);
        h.assertTrue(netCount(level, hub) == 2, "the hoop holds " + netCount(level, hub) + " nets, expected 2");
        h.runAfterDelay(3, () -> {
            h.assertTrue(droppedNets(h) == 1, "expected one dropped net, found " + droppedNets(h));
            for (int z : new int[] {2, 6}) {
                h.assertTrue(RatlineNets.net(level, h.absolutePos(new BlockPos(2, 1, z)), hub) != null, "the strand at z " + z + " went");
            }
            int[] fills = {0};
            String problem = fanProblem(level, hub, fills);
            h.assertTrue(problem == null, "the re-fanned net does not stand: " + problem);
            for (var c : gone.cells()) {
                BlockPos p = new BlockPos(c.x(), c.y(), c.z());
                h.assertTrue(level.getBlockState(p).is(RiggingContent.NET_CELL.get()), "the middle strand's cell " + p + " left a hole");
            }
            h.assertTrue(level.getBlockEntity(middle) instanceof RatlineNetCellBlockEntity cell && cell.isFill(),
                    "the broken chainplate's block is not filled");
            h.succeed();
        });
    }

    /** Breaking a fill cell drops the strand that owns it (the middle one owns the fill at z 3) and re-fans the rest. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingAFillCellDropsItsStrand(GameTestHelper h) {
        BlockPos hub = landFan(h);
        ServerLevel level = h.getLevel();
        level.destroyBlock(h.absolutePos(new BlockPos(2, 1, 3)), false);
        h.assertTrue(RatlineNets.net(level, h.absolutePos(new BlockPos(2, 1, 4)), hub) == null, "the middle strand stayed");
        h.runAfterDelay(3, () -> {
            h.assertTrue(droppedNets(h) == 1, "expected one dropped net, found " + droppedNets(h));
            h.assertTrue(netCount(level, hub) == 2, "the hoop holds " + netCount(level, hub) + " nets, expected 2");
            int[] fills = {0};
            String problem = fanProblem(level, hub, fills);
            h.assertTrue(problem == null, "the re-fanned net does not stand: " + problem);
            h.succeed();
        });
    }

    /** Breaking the hoop drops all three strands, each at its chainplate; no cell is left. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingTheHoopDropsEveryStrand(GameTestHelper h) {
        BlockPos hub = landFan(h);
        ServerLevel level = h.getLevel();
        level.destroyBlock(hub, false);
        h.runAfterDelay(3, () -> {
            h.assertTrue(droppedNets(h) == 3, "expected three dropped nets, found " + droppedNets(h));
            for (int z : LAND_FAN_Z) {
                h.assertTrue(netCount(level, h.absolutePos(new BlockPos(2, 1, z))) == 0, "a chainplate still holds a net");
            }
            BlockPos.betweenClosedStream(h.getBounds()).forEach(p -> h.assertTrue(
                    !level.getBlockState(p).is(RiggingContent.NET_CELL.get()), "a net cell stayed at " + p));
            h.succeed();
        });
    }

    /**
     * A fourth strand whose chainplate lies more than {@code rigging.net_max_spread} (6) from its neighbour's is strung
     * but stays a separate net ("Too far apart for one net"), with no fill towards it; with {@code net_max_strands} at 4
     * the hoop is then full.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = BATCH + "strands_4")
    public static void aStrandBeyondTheSpreadStaysSeparateAndTheHoopFills(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.NET_MAX_STRANDS, 4);
        floor(h, 24);
        for (int y = 1; y <= 9; y++) {
            h.setBlock(new BlockPos(12, y, 12), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(11, 8, 12), hoop(Direction.WEST));
        ServerLevel level = h.getLevel();
        BlockPos hub = h.absolutePos(new BlockPos(11, 8, 12));
        for (int z : new int[] {10, 12, 14}) {
            h.setBlock(new BlockPos(8, 1, z), chainplate(AttachFace.FLOOR, Direction.EAST));
            String r = string(level, h.absolutePos(new BlockPos(8, 1, z)), hub, new ItemStack(RiggingContent.NET_ITEM.get()));
            h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "strand at z " + z + " not strung: " + r);
        }
        // 6.3 blocks from the chainplate at z 14
        h.setBlock(new BlockPos(10, 1, 20), chainplate(AttachFace.FLOOR, Direction.EAST));
        BlockPos far = h.absolutePos(new BlockPos(10, 1, 20));
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        RatlineNetItem.use(level, stack, far, null);
        Component answer = RatlineNetItem.use(level, stack, hub, null);
        h.assertTrue(key(answer).equals(RatlineNetItem.KEY_APART), "the far strand was not strung apart: " + answer.getString());
        h.assertTrue(answer.getString().endsWith("6.3"), "the message does not name the gap: " + answer.getString());
        h.assertTrue(netCount(level, hub) == 4, "the hoop holds " + netCount(level, hub) + " nets, expected 4");
        NetFittingBlockEntity be = (NetFittingBlockEntity) level.getBlockEntity(hub);
        List<RatlineNets.Strand> strands = RatlineNets.hubStrands(level, hub);
        for (int k = 0; k + 1 < strands.size(); k++) {
            boolean farPair = strands.get(k).foot().equals(far) || strands.get(k + 1).foot().equals(far);
            h.assertTrue(be.joinsNext(strands.get(k).foot()) != farPair, "strand " + k + " joins wrongly: " + strands);
        }
        int[] fills = {0};
        String problem = fanProblem(level, hub, fills);
        h.assertTrue(problem == null, "the fan does not stand: " + problem);
        h.assertTrue(fills[0] > 0, "the first three strands lost their fill");
        int lastNear = h.absolutePos(new BlockPos(8, 1, 14)).getZ();
        for (var e : RatlineNets.fanAt(level, hub).cells().entrySet()) {
            h.assertTrue(!e.getValue().fill() || e.getKey().getZ() <= lastNear,
                    "a fill cell reaches towards the far strand at " + e.getKey());
        }
        // full: a fifth chainplate is refused at the hoop, and the hoop refuses to be tied first
        h.setBlock(new BlockPos(8, 1, 8), chainplate(AttachFace.FLOOR, Direction.EAST));
        String full = string(level, h.absolutePos(new BlockPos(8, 1, 8)), hub, new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(full.equals(RatlineNetItem.KEY_FULL), "a fifth strand was not refused as full: " + full);
        h.assertTrue(key(RatlineNetItem.use(level, new ItemStack(RiggingContent.NET_ITEM.get()), hub, null)).equals(RatlineNetItem.KEY_FULL),
                "a full hoop could be tied");
        h.succeed();
    }

    /** Climbing the middle of a fill on land: a player at the rail between two chainplates climbs to the hoop. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 160)
    public static void playerClimbsAFillToTheHoopOnLand(GameTestHelper h) {
        BlockPos hub = landFan(h);
        BlockPos plate = h.absolutePos(new BlockPos(2, 1, 4));
        climb(h, 0, () -> playerInLevel(h, h.absoluteVec(new Vec3(2.5, 1.0, 3.5)), Direction.EAST.toYRot()),
                () -> RatlineNets.net(h.getLevel(), plate, hub), v -> v, false, true, 120, "fill on land");
    }

    /**
     * A hull afloat with a log mast at x 13, z 11 from the deck (y 9) to y 14, a hoop on its west side at y 13 and
     * chainplates on the deck at x 9, z 9, 11 and 13. Returns the helm.
     */
    private static BlockPos shipFanRig(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        for (int y = 9; y <= 14; y++) {
            h.setBlock(new BlockPos(13, y, 11), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(12, 13, 11), hoop(Direction.WEST));
        for (int z : SHIP_FAN_Z) {
            h.setBlock(new BlockPos(9, 9, z), chainplate(AttachFace.FLOOR, Direction.EAST));
        }
        return helm;
    }

    /**
     * Three strands strung to one hoop in a ship's plot: three items, the fan stands with its fill; a player standing on
     * the deck in the fill between the first two chainplates holds forward and climbs to the hoop.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 260)
    public static void threeStrandsOnAShipAndAPlayerClimbsTheFill(GameTestHelper h) {
        BlockPos helmRel = shipFanRig(h);
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos hub = plot(f, helmRel, new BlockPos(12, 13, 11));
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get(), 3);
        for (int z : SHIP_FAN_Z) {
            String r = string(level, plot(f, helmRel, new BlockPos(9, 9, z)), hub, stack);
            h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "strand at z " + z + " not strung in the plot: " + r);
        }
        h.assertTrue(stack.isEmpty() && netCount(level, hub) == 3, "three strands did not take three nets");
        int[] fills = {0};
        String problem = fanProblem(level, hub, fills);
        h.assertTrue(problem == null && fills[0] > 0, "the fan does not stand in the plot: " + problem + ", " + fills[0] + " fill cells");
        BlockPos start = plot(f, helmRel, new BlockPos(9, 9, 10));
        h.assertTrue(level.getBlockEntity(start) instanceof RatlineNetCellBlockEntity cell && cell.isFill(),
                "the deck cell between the first two chainplates is not a fill cell");
        BlockPos middle = plot(f, helmRel, new BlockPos(9, 9, 11));
        ShipBody ship = f.ship();
        climb(h, SETTLE, () -> playerInLevel(h, ship.toWorld(new Vec3(start.getX() + 0.5, start.getY() + 0.02, start.getZ() + 0.5)),
                        Direction.EAST.toYRot()),
                () -> RatlineNets.net(level, middle, hub), ship::toPlot, false, true, 130, "fill on a ship");
    }

    /** A strung fan survives assembly and a disassembly turned a quarter round: strands, order, joins and fill. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void aFanSurvivesAssemblyAndATurnedDisassembly(GameTestHelper h) {
        BlockPos helmRel = shipFanRig(h);
        ServerLevel level = h.getLevel();
        BlockPos hubRel = new BlockPos(12, 13, 11);
        for (int z : SHIP_FAN_Z) {
            String r = string(level, h.absolutePos(new BlockPos(9, 9, z)), h.absolutePos(hubRel), new ItemStack(RiggingContent.NET_ITEM.get()));
            h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "strand at z " + z + " not strung on land: " + r);
        }
        int[] fills = {0};
        h.assertTrue(fanProblem(level, h.absolutePos(hubRel), fills) == null, "the fan does not stand on land");
        int landFills = fills[0];
        h.assertTrue(landFills > 0, "no fill on land");

        Fixture f = DryHullGameTests.assemble(h, helmRel);
        BlockPos hub = plot(f, helmRel, hubRel);
        h.assertTrue(netCount(level, hub) == 3, "the hoop holds " + netCount(level, hub) + " nets after assembly");
        String problem = fanProblem(level, hub, fills);
        h.assertTrue(problem == null && fills[0] == landFills, "the fan did not survive assembly: " + problem + ", " + fills[0] + " fills");

        ShipBody ship = f.ship();
        ship.setOrientation(new Quaterniond().rotateAxis(Math.toRadians(88), 0, 1, 0));
        BlockPos goal = BlockPos.containing(ship.toWorld(Vec3.atCenterOf(f.helmPlot())));
        int turns = DisassemblyMath.quarterTurns(ship.orientation());
        BlockPos hubW = DisassemblyMath.target(hub, f.helmPlot(), goal, turns);
        AssemblyResult d = ShipAssembler.disassemble(ship, f.helmPlot(), null);
        h.assertTrue(d.outcome() == AssemblyResult.Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + d);
        h.runAfterDelay(2, () -> {
            h.assertTrue(netCount(level, hubW) == 3, "the hoop holds " + netCount(level, hubW) + " nets after the turned disassembly");
            List<RatlineNets.Strand> strands = RatlineNets.hubStrands(level, hubW);
            NetFittingBlockEntity be = (NetFittingBlockEntity) level.getBlockEntity(hubW);
            h.assertTrue(strands.size() == 3 && be.joinsNext(strands.get(0).foot()) && be.joinsNext(strands.get(1).foot()),
                    "the fan lost its order or joins: " + strands);
            int[] after = {0};
            String p = fanProblem(level, hubW, after);
            h.assertTrue(p == null && after[0] == landFills, "the fan did not survive the turned disassembly: " + p + ", " + after[0] + " fills");
            h.assertTrue(droppedNets(h) == 0, "a net broke on the way");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ on a ship

    /**
     * A hull afloat in a basin with a log mast at x 13, z 10 from the deck (y 9) to y 14, a hoop on its west side at
     * {@code hoopY} and a chainplate on the deck at x {@code plateX}, z 10. Returns the helm.
     */
    private static BlockPos shipRig(GameTestHelper h, int plateX, int hoopY) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        for (int y = 9; y <= 14; y++) {
            h.setBlock(new BlockPos(13, y, 10), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(12, hoopY, 10), hoop(Direction.WEST));
        h.setBlock(new BlockPos(plateX, 9, 10), chainplate(AttachFace.FLOOR, Direction.EAST));
        return helm;
    }

    private static BlockPos plot(Fixture f, BlockPos helmRel, BlockPos rel) {
        return f.helmPlot().offset(rel.subtract(helmRel));
    }

    /**
     * The rig is assembled bare, then the net is strung in the plot with the item; a mock player stands on the deck at
     * the chainplate and holds forward.
     */
    private static void shipClimb(GameTestHelper h, int plateX, int hoopY, String what) {
        BlockPos helmRel = shipRig(h, plateX, hoopY);
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos plate = plot(f, helmRel, new BlockPos(plateX, 9, 10));
        BlockPos hoopPos = plot(f, helmRel, new BlockPos(12, hoopY, 10));
        h.assertTrue(level.getBlockState(plate).is(RiggingContent.CHAINPLATE.get())
                && level.getBlockState(hoopPos).is(RiggingContent.MAST_HOOP.get()), what + ": the fittings did not come along");
        String r = string(level, plate, hoopPos, new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), what + ": not strung in the plot: " + r);
        ShipBody ship = f.ship();
        climb(h, SETTLE, () -> {
            Vec3 at = ship.toWorld(new Vec3(plate.getX() + 0.5, plate.getY() + 0.02, plate.getZ() + 0.5));
            return playerInLevel(h, at, Direction.EAST.toYRot());
        }, () -> RatlineNets.netOf(level, plate), ship::toPlot, false, true, 110, what);
    }

    /** A near-vertical (88°) net in a ship's plot, strung there and climbed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 240)
    public static void playerClimbsAVerticalNetOnAShip(GameTestHelper h) {
        shipClimb(h, 12, 13, "vertical net on a ship");
    }

    /** A 62° net in a ship's plot. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 240)
    public static void playerClimbsASixtyDegreeNetOnAShip(GameTestHelper h) {
        shipClimb(h, 10, 13, "60 degree net on a ship");
    }

    /** A 45° net in a ship's plot. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 240)
    public static void playerClimbsAFortyFiveDegreeNetOnAShip(GameTestHelper h) {
        shipClimb(h, 9, 12, "45 degree net on a ship");
    }

    /**
     * A net strung across a hull on land survives assembly (cells and fittings move into the plot, the fittings still
     * name each other, the cells A), a disassembly turned a quarter round and the next assembly. A chainplate in the
     * plot and one on land refuse to take a net together.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void aNetSurvivesAssemblyAndATurnedDisassembly(GameTestHelper h) {
        BlockPos helmRel = shipRig(h, 10, 13);
        ServerLevel level = h.getLevel();
        BlockPos plateRel = new BlockPos(10, 9, 10), hoopRel = new BlockPos(12, 13, 10);
        String r = string(level, h.absolutePos(plateRel), h.absolutePos(hoopRel), new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "not strung on land: " + r);
        int cells = RatlineNets.standingCells(level, RatlineNets.netOf(level, h.absolutePos(plateRel))).size();
        h.assertTrue(cells >= 4, "only " + cells + " cells");

        Fixture f = DryHullGameTests.assemble(h, helmRel);
        BlockPos plate = plot(f, helmRel, plateRel), hoopPos = plot(f, helmRel, hoopRel);
        Net net = RatlineNets.netOf(level, plate);
        h.assertTrue(net != null && net.b().equals(hoopPos), "the net did not survive assembly: " + net);
        h.assertTrue(RatlineNets.standingCells(level, net).size() == cells, "cells were lost in assembly");
        for (BlockPos c : RatlineNets.standingCells(level, net)) {
            h.assertTrue(level.getBlockEntity(c) instanceof RatlineNetCellBlockEntity be && plate.equals(be.fittingA()),
                    "a cell lost A in assembly");
        }
        // a chainplate on land and the hoop in the plot are on different bodies
        h.setBlock(new BlockPos(2, 9, 2), Blocks.STONE);
        h.setBlock(new BlockPos(2, 10, 2), chainplate(AttachFace.FLOOR, Direction.EAST));
        BlockPos spareHoop = plot(f, helmRel, new BlockPos(12, 11, 10));
        level.setBlockAndUpdate(spareHoop, hoop(Direction.WEST));
        String other = string(level, h.absolutePos(new BlockPos(2, 10, 2)), spareHoop, new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(other.equals(RatlineNetItem.KEY_OTHER_BODY), "a net between land and ship was not refused: " + other);
        level.setBlockAndUpdate(spareHoop, Blocks.AIR.defaultBlockState());

        ShipBody ship = f.ship();
        ship.setOrientation(new Quaterniond().rotateAxis(Math.toRadians(88), 0, 1, 0));
        BlockPos goal = BlockPos.containing(ship.toWorld(Vec3.atCenterOf(f.helmPlot())));
        int turns = DisassemblyMath.quarterTurns(ship.orientation());
        h.assertTrue(turns == 1, "expected one quarter turn, got " + turns);
        BlockPos plateW = DisassemblyMath.target(plate, f.helmPlot(), goal, turns);
        BlockPos hoopW = DisassemblyMath.target(hoopPos, f.helmPlot(), goal, turns);
        AssemblyResult d = ShipAssembler.disassemble(ship, f.helmPlot(), null);
        h.assertTrue(d.outcome() == AssemblyResult.Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + d);
        h.runAfterDelay(2, () -> {
            Net back = RatlineNets.netOf(level, plateW);
            h.assertTrue(back != null && back.b().equals(hoopW), "the net did not survive the turned disassembly: " + back);
            h.assertTrue(RatlineNets.standingCells(level, back).size() == cells, "cells were lost in disassembly");
            for (BlockPos c : RatlineNets.standingCells(level, back)) {
                h.assertTrue(level.getBlockEntity(c) instanceof RatlineNetCellBlockEntity be && plateW.equals(be.fittingA()),
                        "a cell lost A in the turned disassembly");
            }
            AssemblyResult again = ShipAssembler.assemble(level, goal, null);
            h.assertTrue(again.shipId() != null, "reassembly failed: " + again);
            ShipTestCleanup.track(h, again.shipId());
            int fittings = 0;
            for (BlockPos p : SableShips.byId(level, again.shipId()).plotBlocks()) {
                if (level.getBlockEntity(p) instanceof NetFittingBlockEntity be && be.hasNet()) {
                    fittings++;
                }
            }
            h.assertTrue(fittings == 2, "after reassembly " + fittings + " fittings hold the net, expected 2");
            h.assertTrue(droppedNets(h) == 0, "a net broke on the way");
            h.succeed();
        });
    }
}
