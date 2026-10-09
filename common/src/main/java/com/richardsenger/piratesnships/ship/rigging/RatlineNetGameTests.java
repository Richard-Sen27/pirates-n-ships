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
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.lookout.LookoutContent;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

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
 * vertical net with the assist off; and (RL3) a 62° net climbed on a hull sailing at 4 m/s, straight and turning at
 * 0.3 rad/s, boarded from the deck (Sable carries the climber) and from the ring (the assist carries it); and climbing
 * off the top of a 62° and a vertical net onto a crow's nest one block over the hoop, and back down from it.
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

    // ------------------------------------------------------------------ on a ship

    /**
     * A hull afloat in a basin with a log mast at x 13, z 10 from the deck (y 9) to y 14, a hoop on its west side at
     * {@code hoopY} and a chainplate on the deck at x {@code plateX}, z 10. Returns the helm.
     */
    private static BlockPos shipRig(GameTestHelper h, int plateX, int hoopY) {
        return shipRig(h, plateX, hoopY, 24);
    }

    /** {@link #shipRig(GameTestHelper, int, int)} in a basin {@code size} blocks long in x (24 wide in z). */
    private static BlockPos shipRig(GameTestHelper h, int plateX, int hoopY, int size) {
        DryHullGameTests.basin(h, 0, size - 1, true);
        SailingGameTestsShips.openSky(h, size);
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

    // ------------------------------------------------------------------ on a ship under way (RL3)

    /** Ticks before the climber boards during which the ship is brought up to its steady motion. */
    private static final int DRIVE_LEAD = 15;
    /** A climber this close to the net's line [blocks] is on it. */
    private static final double ON_LINE = 0.3;

    /** Distance from {@code p} to the segment from {@code a} to {@code b} (one frame). */
    static double lineDistance(Point a, Point b, Vec3 p) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 < 1e-12 ? 0 : ((p.x - a.x()) * dx + (p.y - a.y()) * dy + (p.z - a.z()) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        return p.distanceTo(new Vec3(a.x() + dx * t, a.y() + dy * t, a.z() + dz * t));
    }

    /** World heading [rad] of the ship's plot +x axis. */
    private static double heading(ShipBody ship) {
        Vec3 x = RatlineNetAssist.rotate(ship.orientation(), new Vec3(1, 0, 0));
        return Math.atan2(x.z, x.x);
    }

    /**
     * A 62° net on a hull afloat that is held at a steady motion from {@link #DRIVE_LEAD} ticks before the climber
     * boards: each tick its horizontal velocity is set to {@code linear} [m/s] and its yaw rate to {@code yawRate}
     * [rad/s] (heave, pitch and roll are left to the water). A mock player holding forward boards at the chainplate:
     * walking onto it from the deck block behind it ({@code fromDeck}; Sable tracks a player on the deck and keeps
     * tracking it among the net's cells, so Sable carries it) or put straight into the chainplate's ring, never touching
     * the deck (Sable does not track it; the assist carries it). Its feet are followed in the ship's plot, the net's own
     * frame, so a lagging climber shows as distance from the line: its closest approach to ring B must be within 0.7
     * blocks, and from the moment it is on the net (within {@link #ON_LINE} of the line) up to that approach its feet
     * must stay within 0.5 blocks of the line.
     */
    private static void underWayClimb(GameTestHelper h, Vec3 linear, double yawRate, boolean fromDeck, String what) {
        int plateX = 10, hoopY = 13;
        BlockPos helmRel = shipRig(h, plateX, hoopY, 40);
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos plate = plot(f, helmRel, new BlockPos(plateX, 9, 10));
        BlockPos hoopPos = plot(f, helmRel, new BlockPos(12, hoopY, 10));
        String r = string(level, plate, hoopPos, new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), what + ": not strung in the plot: " + r);
        ShipBody ship = f.ship();
        Net net = RatlineNets.netOf(level, plate);
        h.assertTrue(net != null, what + ": no net");
        Point a = net.ringA(), b = net.ringB();
        Vec3 ringB = new Vec3(b.x(), b.y(), b.z());
        Vec3 board = fromDeck ? new Vec3(plate.getX() - 0.5, plate.getY() + 0.02, plate.getZ() + 0.5)
                : new Vec3(a.x(), a.y() + 0.1, a.z());
        int forTicks = 90;
        Player[] player = {null};
        double[] best = {Double.POSITIVE_INFINITY};
        double[] worstLine = {0};
        double[] worstSoFar = {0};
        int[] tracked = {0};
        int[] tick = {0};
        int[] bestTick = {0};
        boolean[] onNet = {false};
        Vec3[] shipStart = {null};
        double[] headingStart = {0};
        double[] shipSpeed = {0};
        double[] turnRate = {0};
        Vector3d lin = new Vector3d(), ang = new Vector3d();
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t >= SETTLE - DRIVE_LEAD && t < SETTLE + forTicks) {
                ship.velocities(lin, ang);
                ship.addVelocity(new Vector3d(linear.x - lin.x, 0, linear.z - lin.z), new Vector3d(0, yawRate - ang.y, 0));
            }
            if (t == SETTLE) {
                // facing the plot's +x (towards the mast), however far the ship has turned
                Vec3 ahead = RatlineNetAssist.rotate(ship.orientation(), new Vec3(1, 0, 0));
                player[0] = playerInLevel(h, ship.toWorld(board), (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z)));
                shipStart[0] = ship.toWorld(ringB);
                headingStart[0] = heading(ship);
            }
            Player p = player[0];
            if (p == null || p.isRemoved() || t > SETTLE + forTicks) {
                return;
            }
            p.zza = 1f;
            Vec3 feet = ship.toPlot(p.position());
            double toB = feet.distanceTo(ringB);
            double line = lineDistance(a, b, feet);
            // on the net: climbing and pulled onto the line (stepping in from the deck starts half a block off it)
            onNet[0] |= p.onClimbable() && line < ON_LINE;
            if (onNet[0]) {
                worstSoFar[0] = Math.max(worstSoFar[0], line);
            }
            if (toB < best[0]) {
                best[0] = toB;
                worstLine[0] = worstSoFar[0];
                bestTick[0] = t - SETTLE;
            }
            ShipBody on = ShipEntities.standingOrRiding(p);
            if (on != null && on.id().equals(ship.id())) {
                tracked[0]++;
            }
            if (t == SETTLE + forTicks) {
                // the ship's actual mean speed at ring B and turn rate over the climb
                shipSpeed[0] = ship.toWorld(ringB).subtract(shipStart[0]).horizontalDistance() * 20 / forTicks;
                turnRate[0] = Math.toRadians(Mth.wrapDegrees(Math.toDegrees(heading(ship) - headingStart[0]))) * 20 / forTicks;
            }
        });
        h.runAfterDelay(SETTLE + forTicks + 1, () -> {
            h.assertTrue(player[0] != null, what + ": no player was spawned");
            Constants.LOG.info("[RL3] {}: closest to ring B {} (tick {}), farthest from the line before that {}, tracked by "
                            + "Sable {}/{} ticks; ring B moved at {} m/s, the ship turned at {} rad/s", what,
                    String.format("%.3f", best[0]), bestTick[0], String.format("%.3f", worstLine[0]), tracked[0],
                    forTicks + 1, String.format("%.2f", shipSpeed[0]), String.format("%.3f", turnRate[0]));
            h.assertTrue(fromDeck ? tracked[0] > forTicks / 2 : tracked[0] < forTicks / 2,
                    what + ": Sable tracked the climber " + tracked[0] + " ticks, not the case under test");
            h.assertTrue(onNet[0], what + ": never got onto the net");
            h.assertTrue(best[0] <= 0.7, what + ": came only within " + best[0] + " blocks of ring B");
            h.assertTrue(worstLine[0] <= 0.5, what + ": fell " + worstLine[0] + " blocks off the line on the way up");
            h.succeed();
        });
    }

    /** A 62° net on a ship sailing at a steady 4 m/s, boarded from the deck: Sable carries the climber. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 240)
    public static void playerClimbsANetOnAShipUnderWay(GameTestHelper h) {
        underWayClimb(h, new Vec3(4, 0, 0), 0, true, "62 degree net from the deck, ship at 4 m/s");
    }

    /**
     * A 62° net on a ship sailing at 4 m/s and turning at a steady 0.3 rad/s (17°/s), boarded from the deck. (Turning
     * alone moves the net too slowly to tell: ring B is 2 blocks from the turning axis, and the assist's pull kept even
     * an uncarried RL2 climber on it.)
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 240)
    public static void playerClimbsANetOnATurningShip(GameTestHelper h) {
        underWayClimb(h, new Vec3(4, 0, 0), 0.3, true, "62 degree net from the deck, ship at 4 m/s turning at 0.3 rad/s");
    }

    /** A 62° net on a ship at 4 m/s, boarded in the chainplate's ring: the assist carries the climber itself. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 240)
    public static void untrackedClimberKeepsUpWithAShipUnderWay(GameTestHelper h) {
        underWayClimb(h, new Vec3(4, 0, 0), 0, false, "62 degree net from the ring, ship at 4 m/s");
    }

    /** A 62° net on a ship sailing at 4 m/s and turning at 0.3 rad/s, boarded in the chainplate's ring. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 240)
    public static void untrackedClimberKeepsUpWithATurningShip(GameTestHelper h) {
        underWayClimb(h, new Vec3(4, 0, 0), 0.3, false, "62 degree net from the ring, ship at 4 m/s turning at 0.3 rad/s");
    }

    // ------------------------------------------------------------------ off the top (RL3)

    /** Height of the hoop on the masthead rigs: the mast ends here, the crow's nest stands on it one block up. */
    private static final int MASTHEAD = 13;

    /**
     * The ship rig with the mast ending at the hoop's height ({@link #MASTHEAD}) and a crow's nest on it, so the hoop
     * sits one block below the nest's floor; assembled, the net strung in the plot. Returns {chainplate, nest} in the plot.
     */
    private static BlockPos[] mastheadRig(GameTestHelper h, int plateX, String what) {
        BlockPos helmRel = shipRig(h, plateX, MASTHEAD);
        h.setBlock(new BlockPos(13, MASTHEAD + 1, 10), LookoutContent.CROWS_NEST.get());
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos plate = plot(f, helmRel, new BlockPos(plateX, 9, 10));
        BlockPos nest = plot(f, helmRel, new BlockPos(13, MASTHEAD + 1, 10));
        h.assertTrue(level.getBlockState(nest).is(LookoutContent.CROWS_NEST.get()), what + ": the crow's nest did not come along");
        String r = string(level, plate, plot(f, helmRel, new BlockPos(12, MASTHEAD, 10)), new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), what + ": not strung in the plot: " + r);
        return new BlockPos[] {plate, nest};
    }

    /**
     * A mock player on the deck at the chainplate holds forward, facing the mast, until it stands on the crow's nest's
     * floor: its feet in the nest's block, on the ground, within 0.3 of the floor.
     */
    private static void climbOntoTheNest(GameTestHelper h, int plateX, String what) {
        BlockPos[] rig = mastheadRig(h, plateX, what);
        BlockPos plate = rig[0], nest = rig[1];
        ServerLevel level = h.getLevel();
        ShipBody body = SableShips.containing(level, plate);
        Player[] player = {null};
        double[] highest = {Double.NEGATIVE_INFINITY};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == SETTLE) {
                Vec3 at = body.toWorld(new Vec3(plate.getX() + 0.5, plate.getY() + 0.02, plate.getZ() + 0.5));
                player[0] = playerInLevel(h, at, Direction.EAST.toYRot());
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) {
                return;
            }
            p.zza = 1f;
            Vec3 feet = body.toPlot(p.position());
            highest[0] = Math.max(highest[0], feet.y);
            if (BlockPos.containing(feet).equals(nest) && p.onGround() && feet.y - nest.getY() < 0.3) {
                Constants.LOG.info("[RL3] {}: standing on the crow's nest after {} ticks", what, t - SETTLE);
                h.succeed();
            }
        });
        h.runAfterDelay(SETTLE + 140, () -> h.fail(what + ": never stood on the crow's nest (feet rose to plot y "
                + highest[0] + ", the nest floor is at " + nest.getY() + ")"));
    }

    /** A 62° net up to a hoop one block below a crow's nest: the climber climbs off the top onto the nest's floor. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 240)
    public static void playerClimbsOffASixtyDegreeNetOntoTheCrowsNest(GameTestHelper h) {
        climbOntoTheNest(h, 10, "62 degree net to the crow's nest");
    }

    /** The same up a vertical net. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 240)
    public static void playerClimbsOffAVerticalNetOntoTheCrowsNest(GameTestHelper h) {
        climbOntoTheNest(h, 12, "vertical net to the crow's nest");
    }

    /**
     * Down from the top: a mock player standing in the crow's nest walks out over the hoop, is caught on the net and,
     * letting go of forward, slides down it to the deck at the chainplate (not straight down beside the mast).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 240)
    public static void playerClimbsDownFromTheCrowsNest(GameTestHelper h) {
        String what = "down a 62 degree net from the crow's nest";
        BlockPos[] rig = mastheadRig(h, 10, what);
        BlockPos plate = rig[0], nest = rig[1];
        ServerLevel level = h.getLevel();
        ShipBody body = SableShips.containing(level, plate);
        Player[] player = {null};
        boolean[] caught = {false};
        int[] climbing = {0};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == SETTLE) {
                Vec3 at = body.toWorld(new Vec3(nest.getX() + 0.5, nest.getY() + 0.1, nest.getZ() + 0.5));
                player[0] = playerInLevel(h, at, Direction.WEST.toYRot());
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) {
                return;
            }
            boolean onNet = p.onClimbable();
            caught[0] |= onNet;
            if (onNet) {
                climbing[0]++;
            }
            // walk west out of the nest until the net catches the player, then let go
            p.zza = caught[0] ? 0f : 1f;
            Vec3 feet = body.toPlot(p.position());
            if (caught[0] && p.onGround() && feet.y < plate.getY() + 0.5) {
                double off = Math.abs(feet.x - (plate.getX() + 0.5));
                Constants.LOG.info("[RL3] {}: on the deck after {} ticks, {} ticks on the net, {} blocks from the chainplate",
                        what, t - SETTLE, climbing[0], String.format("%.2f", off));
                h.assertTrue(off <= 0.8, what + ": came down " + off + " blocks from the chainplate, not along the net");
                h.assertTrue(climbing[0] >= 15, what + ": only " + climbing[0] + " ticks on the net, it fell");
                h.succeed();
            }
        });
        h.runAfterDelay(SETTLE + 160, () -> h.fail(what + ": never came down to the deck (caught " + caught[0] + ")"));
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
