package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.boarding.BoardingPlanks;
import com.richardsenger.piratesnships.combat.boarding.PlankRun;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.cannon.CannonballEntity;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlock;
import com.richardsenger.piratesnships.combat.cannon.SwivelMount;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.PartialCellRule;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.hull.world.HullBlockClassifier;
import com.richardsenger.piratesnships.ship.hull.world.HullGridSnapshotter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The bulwark and its gun port (RAIL4, docs/design.md §4.8 "Railing extras (RAIL3–RAIL5)") in a real server: placing
 * and the stanchions (a bulwark run ends against a railing), the shapes and drops; the hull analysis of a bulwarked deck
 * equal to a railed one's, on land and afloat; a carriage cannon firing through gun ports at every elevation and into a
 * plain bulwark at every elevation; the lid opening for a cannon and for a player; a gangway from a gun port's sill;
 * the swivel gun on a bulwark.
 */
public final class BulwarkGameTests {

    private static final String BATCH = "pirates_n_ships_config_ship_decor_bulwark_";

    private BulwarkGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BulwarkGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static BulwarkBlock bulwark() {
        return RailingContent.BULWARKS.get(ShipWood.OAK);
    }

    private static BulwarkPortBlock gunPort() {
        return RailingContent.BULWARK_PORTS.get(ShipWood.OAK);
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static Player holder(GameTestHelper h, Item item, int count, Direction look) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(0.5, 1, 0.5));
        p.moveTo(at.x, at.y, at.z, look.toYRot(), 30f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        return p;
    }

    private static InteractionResult use(GameTestHelper h, Player p, BlockPos clicked, Direction face, double fx, double fy, double fz) {
        BlockPos abs = h.absolutePos(clicked);
        Vec3 hit = new Vec3(abs.getX() + fx, abs.getY() + fy, abs.getZ() + fz);
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, abs, false)));
    }

    private static InteractionResult clickFloor(GameTestHelper h, Player p, BlockPos cell, double fx, double fz) {
        return use(h, p, cell.below(), Direction.UP, fx, 1.0, fz);
    }

    /** A railing or bulwark ({@code block}) with {@code edges} at relative {@code rel}, its posts worked out. */
    private static BlockPos setEdges(GameTestHelper h, RailingBlock block, BlockPos rel, Direction... edges) {
        BlockState s = block.defaultBlockState();
        for (Direction d : edges) s = s.setValue(RailingBlock.edge(d), true);
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, RailingBlock.withPosts(s, h.getLevel(), abs), 3);
        return abs;
    }

    private static Set<Direction> set(Direction... ds) {
        return ds.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(ds[0], ds);
    }

    private static int items(GameTestHelper h, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds(), e -> e.isAlive() && e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** A two-block cannon with its master at {@code rel} facing {@code facing}, without the placement checks. */
    private static BlockPos cannon(GameTestHelper h, BlockPos rel, Direction facing) {
        BlockState master = CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, facing);
        h.setBlock(rel, master);
        h.setBlock(CannonRules.rearOf(rel, facing), CannonContent.CANNON.get().rearState(master));
        return h.absolutePos(rel);
    }

    /** Loads the cannon at {@code master} with powder and a ball, aims it at elevation step {@code step} and fires it. */
    private static CannonService.Use loadAimFire(GameTestHelper h, BlockPos master, int step) {
        ServerLevel level = h.getLevel();
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        CannonService.load(level, master, creative, new ItemStack(Items.GUNPOWDER));
        CannonService.load(level, master, creative, new ItemStack(CombatContent.CANNONBALL.get()));
        ((CannonBlockEntity) level.getBlockEntity(master)).setElevationStep(step);
        return CannonService.fire(level, master, null);
    }

    private static BlockState portFacing(Direction facing) {
        return gunPort().defaultBlockState().setValue(BulwarkPortBlock.FACING, facing);
    }

    private static InteractionResult useLid(GameTestHelper h, Player p, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return h.getBlockState(rel).useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(abs), Direction.WEST, abs, false));
    }

    // ------------------------------------------------------------------ placement, stanchions, shapes

    /**
     * Placed like a railing: a click near the north side walls the north edge (one item per edge); a run of two has a
     * stanchion only at its ends; a second edge turns a corner; a gun port on the same edge continues the run; against
     * a railing the bulwark ends in a stanchion and the railing in a post. Placed into water it is waterlogged.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void placingAndTheStanchions(GameTestHelper h) {
        floor(h);
        Player p = holder(h, bulwark().asItem(), 16, Direction.NORTH);
        BlockPos a = new BlockPos(2, 1, 2), b = new BlockPos(3, 1, 2);
        h.assertTrue(clickFloor(h, p, a, 0.5, 0.1).consumesAction(), "the bulwark was not placed");
        h.assertTrue(RailingBlock.edges(h.getBlockState(a)).equals(set(Direction.NORTH)), "edges " + h.getBlockState(a));
        h.assertTrue(RailingBlock.posts(h.getBlockState(a)).equals(set(Direction.NORTH, Direction.EAST)), "a lone wall has two stanchions");
        clickFloor(h, p, b, 0.5, 0.1);
        h.assertTrue(RailingBlock.posts(h.getBlockState(a)).equals(set(Direction.NORTH)), "a: only the west stanchion, got " + h.getBlockState(a));
        h.assertTrue(RailingBlock.posts(h.getBlockState(b)).equals(set(Direction.EAST)), "b: only the east stanchion, got " + h.getBlockState(b));
        h.assertTrue(clickFloor(h, p, b, 0.9, 0.5).consumesAction(), "the second edge was refused");
        h.assertTrue(RailingBlock.posts(h.getBlockState(b)).equals(set(Direction.EAST, Direction.SOUTH)), "b's corner: " + h.getBlockState(b));
        h.assertTrue(p.getMainHandItem().getCount() == 13, "three edges used three items");
        // a gun port east of a bulwark on the north edge: no stanchion against it
        h.setBlock(new BlockPos(6, 1, 5), portFacing(Direction.NORTH));
        BlockPos c = new BlockPos(5, 1, 5);
        clickFloor(h, p, c, 0.5, 0.1);
        h.assertTrue(RailingBlock.posts(h.getBlockState(c)).equals(set(Direction.NORTH)), "beside the gun port: " + h.getBlockState(c));
        // a railing west of it on the same edge: the bulwark gets its west stanchion back, the railing an east post
        BlockPos rail = new BlockPos(4, 1, 5);
        setEdges(h, RailingContent.RAILINGS.get(ShipWood.OAK), rail, Direction.NORTH);
        h.assertTrue(RailingBlock.posts(h.getBlockState(c)).equals(set(Direction.NORTH)), "against the railing: " + h.getBlockState(c));
        h.assertTrue(RailingBlock.posts(h.getBlockState(rail)).equals(set(Direction.NORTH, Direction.EAST)),
                "the railing runs on into the bulwark: " + h.getBlockState(rail));
        // into water
        BlockPos wet = new BlockPos(2, 1, 7);
        h.setBlock(wet, Blocks.WATER);
        clickFloor(h, p, wet, 0.5, 0.1);
        h.assertTrue(Waterlogging.isWaterlogged(h.getBlockState(wet)), "not waterlogged in water");
        h.succeed();
    }

    /**
     * The outline is the wall with its cap rail and the stanchions (to 18 px), the collision raised to 1.5 blocks, crew
     * do not path through it; breaking a bulwark of two edges drops two. Sable weighs it like a railing.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void shapesDropsAndWeight(GameTestHelper h) {
        floor(h);
        BlockPos abs = setEdges(h, bulwark(), new BlockPos(4, 1, 4), Direction.NORTH, Direction.EAST);
        BlockState s = h.getLevel().getBlockState(abs);
        double collision = s.getCollisionShape(h.getLevel(), abs, CollisionContext.empty()).max(Direction.Axis.Y);
        h.assertTrue(Math.abs(collision - 1.5) < 1e-6, "collision top " + collision);
        AABB outline = s.getShape(h.getLevel(), abs).bounds();
        h.assertTrue(Math.abs(outline.maxY - 18 / 16.0) < 1e-6, "outline top " + outline);
        h.assertFalse(s.isPathfindable(PathComputationType.LAND), "crew path through it");
        h.assertTrue(s.is(SableWeightTags.SUPER_LIGHT) && s.is(SableWeightTags.QUARTER_VOLUME), "not in Sable's light tags");
        h.assertTrue(s.is(CannonContent.BARREL_BLOCKING) && s.is(CannonContent.SWIVEL_MOUNTS) && s.is(CannonContent.BREAKABLE),
                "missing a cannon tag");
        h.assertFalse(portFacing(Direction.NORTH).is(CannonContent.BARREL_BLOCKING), "the gun port blocks the barrel");
        h.getLevel().destroyBlock(abs, true);
        h.runAfterDelay(2, () -> {
            h.assertTrue(items(h, bulwark().asItem()) == 2, "expected 2 bulwarks, got " + items(h, bulwark().asItem()));
            h.succeed();
        });
    }

    /** With railings off, neither the bulwark nor the gun port places. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "disabled")
    public static void disabledBulwarksDoNotPlace(GameTestHelper h) {
        ConfigOverrides.during(h, com.richardsenger.piratesnships.ship.decor.DecorConfig.RAILINGS_ENABLED, false);
        floor(h);
        Player p = holder(h, bulwark().asItem(), 1, Direction.NORTH);
        h.assertFalse(clickFloor(h, p, new BlockPos(4, 1, 4), 0.5, 0.1).consumesAction(), "a bulwark placed while disabled");
        Player q = holder(h, gunPort().asItem(), 1, Direction.NORTH);
        h.assertFalse(clickFloor(h, q, new BlockPos(5, 1, 4), 0.5, 0.1).consumesAction(), "a gun port placed while disabled");
        h.succeed();
    }

    // ------------------------------------------------------------------ the hull analysis

    /**
     * Three hulls (the DryHullGameTests box: 5 x 5, hold 3 x 2 x 3), one bare, one with railings round the deck and
     * one with bulwarks round it and a gun port: all three analyse to the same compartments with the same volumes and
     * the same partial cells (a bulwark, a railing and a gun port are air to the hull, open or shut).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void aBulwarkedDeckAnalysesLikeARailedOne(GameTestHelper h) {
        int[] xs = {1, 8, 15};
        for (int a : xs) DryHullGameTests.hull(h, a, false);
        rimDeck(h, xs[1], RailingContent.RAILINGS.get(ShipWood.OAK));
        rimDeck(h, xs[2], bulwark());
        h.setBlock(new BlockPos(xs[2] + 4, 9, 11), portFacing(Direction.EAST));
        List<String> results = new ArrayList<>();
        for (int a : xs) {
            HullAnalysis analysis = HullAnalyzer.analyze(HullGridSnapshotter.snapshot(h.getLevel(),
                    h.absolutePos(new BlockPos(a - 1, 4, 8)), h.absolutePos(new BlockPos(a + 5, 11, 14))));
            StringBuilder sb = new StringBuilder();
            for (Compartment c : analysis.compartments()) sb.append(c.volume()).append(' ');
            sb.append("| partial ").append(analysis.grid().partialCells().length)
                    .append(" candidates ").append(PartialCellRule.of(analysis).candidateCount());
            results.add(sb.toString());
        }
        h.assertTrue(results.get(0).startsWith("18 "), "the bare hull's hold is not 18 cells: " + results.get(0));
        h.assertTrue(results.get(1).equals(results.get(0)) && results.get(2).equals(results.get(0)), "the analyses differ: " + results);
        BlockPos portAbs = h.absolutePos(new BlockPos(xs[2] + 4, 9, 11));
        for (boolean open : new boolean[]{false, true}) {
            BlockState s = portFacing(Direction.EAST).setValue(BulwarkPortBlock.OPEN, open);
            h.assertTrue(HullBlockClassifier.classify(s, h.getLevel(), portAbs) == CellKind.AIR, "a gun port is a hull cell (open " + open + ")");
        }
        h.succeed();
    }

    /** Walls (railings or bulwarks) on every edge of the deck of the hull at x {@code a}..{@code a + 4}, at y 9. */
    private static void rimDeck(GameTestHelper h, int a, RailingBlock block) {
        for (int x = a; x <= a + 4; x++) {
            for (int z = 9; z <= 13; z++) {
                List<Direction> edges = new ArrayList<>();
                if (z == 9) edges.add(Direction.NORTH);
                if (z == 13) edges.add(Direction.SOUTH);
                if (x == a) edges.add(Direction.WEST);
                if (x == a + 4) edges.add(Direction.EAST);
                if (!edges.isEmpty() && !(x == a + 2 && z == 11)) setEdges(h, block, new BlockPos(x, 9, z), edges.toArray(Direction[]::new));
            }
        }
    }

    /**
     * Afloat: a railed and a bulwarked hull side by side in a basin, assembled; after they settle their hull runtimes
     * hold the same compartments, the same dry cells and no water, and the bulwarks weigh like railings.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void aBulwarkedShipFloodsLikeARailedOne(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos railedHelm = DryHullGameTests.hull(h, 3, false);
        BlockPos bulwarkedHelm = DryHullGameTests.hull(h, 14, false);
        rimDeck(h, 3, RailingContent.RAILINGS.get(ShipWood.OAK));
        rimDeck(h, 14, bulwark());
        Fixture railed = DryHullGameTests.assemble(h, railedHelm);
        Fixture bulwarked = DryHullGameTests.assemble(h, bulwarkedHelm);
        h.runAfterDelay(40, () -> {
            String r = describe(railed);
            String b = describe(bulwarked);
            h.assertTrue(r.equals(b), "railed " + r + " / bulwarked " + b);
            h.assertTrue(r.startsWith("18/18 "), "the hold is not 18 dry cells: " + r);
            ServerLevel level = h.getLevel();
            long walls = bulwarked.ship().plotBlocks().stream().filter(p -> level.getBlockState(p).getBlock() instanceof BulwarkBlock).count();
            h.assertTrue(walls == 16, "bulwarks in the plot: " + walls);
            h.succeed();
        });
    }

    /** Compartment volumes and dry cells, water held and region cells of a ship's hull runtime. */
    private static String describe(Fixture f) {
        FloodSimulation sim = f.runtime().simulation();
        StringBuilder sb = new StringBuilder();
        List<Compartment> cs = sim.analysis().compartments();
        for (int i = 0; i < cs.size(); i++) sb.append(sim.dryCells(i).cardinality()).append('/').append(cs.get(i).volume()).append(' ');
        sb.append(String.format(java.util.Locale.ROOT, "water %.2f regions %d", sim.totalVolume(), f.runtime().regionCells().size()));
        return sb.toString();
    }

    // ------------------------------------------------------------------ the cannon

    /**
     * Six lanes, each a carriage cannon facing east with a gun port on the block in front of it: the lid opens for the
     * cannon, and each cannon, aimed at one of the six default elevation steps (−5° to 20°), fires through the port; every
     * ball flies on past it and every port stands.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40)
    public static void aCannonFiresThroughAGunPortAtEveryElevation(GameTestHelper h) {
        floor(h);
        int steps = CannonConfig.ELEVATION_STEPS.get();
        List<CannonballEntity> balls = new ArrayList<>();
        int portX = h.absolutePos(new BlockPos(3, 1, 0)).getX();
        for (int lane = 0; lane < steps && lane < 9; lane++) {
            h.setBlock(new BlockPos(3, 1, lane), portFacing(Direction.EAST));
            BlockPos master = cannon(h, new BlockPos(2, 1, lane), Direction.EAST);
            h.assertTrue(h.getBlockState(new BlockPos(3, 1, lane)).getValue(BulwarkPortBlock.OPEN), "lane " + lane + ": the lid stays shut");
            CannonService.Use use = loadAimFire(h, master, lane);
            h.assertTrue(use.outcome() == CannonService.Outcome.FIRED && use.ball() != null, "lane " + lane + " did not fire: " + use.outcome());
            h.assertFalse(use.ball().isRemoved(), "lane " + lane + ": the ball was stopped in the barrel");
            balls.add(use.ball());
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < balls.size(); i++) {
                CannonballEntity b = balls.get(i);
                h.assertTrue(b.getX() > portX + 2.5 && !(b.isRemoved() && b.tickCount <= 1),
                        "lane " + i + ": the ball stopped at x " + b.getX() + " (port at " + portX + ", ticks " + b.tickCount + ")");
                h.assertTrue(h.getBlockState(new BlockPos(3, 1, i)).getBlock() instanceof BulwarkPortBlock, "lane " + i + ": the port is gone");
            }
        });
    }

    /**
     * The same six cannons behind a plain bulwark (east edge): at every elevation the barrel runs into the wall, so the
     * ball strikes it as it is fired. It is spent at once (removed before its first tick) and breaks the bulwark (wood,
     * cannon_breakable), which drops itself.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40)
    public static void aPlainBulwarkStopsTheBallAtEveryElevation(GameTestHelper h) {
        floor(h);
        int steps = CannonConfig.ELEVATION_STEPS.get();
        for (int lane = 0; lane < steps && lane < 9; lane++) {
            setEdges(h, bulwark(), new BlockPos(3, 1, lane), Direction.EAST);
            BlockPos master = cannon(h, new BlockPos(2, 1, lane), Direction.EAST);
            CannonService.Use use = loadAimFire(h, master, lane);
            h.assertTrue(use.outcome() == CannonService.Outcome.FIRED && use.ball() != null, "lane " + lane + " did not fire: " + use.outcome());
            h.assertTrue(use.ball().isRemoved() && use.ball().tickCount == 0, "lane " + lane + " ("
                    + CannonConfig.elevationDegrees(lane) + "°): the ball left the barrel");
            h.assertTrue(h.getBlockState(new BlockPos(3, 1, lane)).isAir(), "lane " + lane + ": the bulwark stands");
        }
        h.runAfterDelay(2, () -> {
            h.assertTrue(items(h, bulwark().asItem()) == steps, "bulwarks dropped: " + items(h, bulwark().asItem()));
            h.succeed();
        });
    }

    /** With {@code cannons.walls_block_barrel} off a level shot leaves the muzzle beyond the wall and flies on. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40, batch = BATCH + "barrel")
    public static void wallsBlockBarrelCanBeTurnedOff(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.WALLS_BLOCK_BARREL, false);
        floor(h);
        setEdges(h, bulwark(), new BlockPos(3, 1, 4), Direction.EAST);
        BlockPos master = cannon(h, new BlockPos(2, 1, 4), Direction.EAST);
        int level = CannonRules.levelStep(CannonConfig.ELEVATION_STEPS.get(), CannonConfig.MIN_ELEVATION.get(), CannonConfig.MAX_ELEVATION.get());
        CannonService.Use use = loadAimFire(h, master, level);
        h.assertFalse(use.ball().isRemoved(), "the ball was stopped in the barrel");
        CannonballEntity ball = use.ball();
        int wallX = h.absolutePos(new BlockPos(3, 1, 4)).getX();
        h.succeedWhen(() -> {
            h.assertTrue(ball.getX() > wallX + 2.5, "the ball stopped at x " + ball.getX());
            h.assertTrue(h.getBlockState(new BlockPos(3, 1, 4)).getBlock() instanceof BulwarkBlock, "the bulwark is gone");
        });
    }

    // ------------------------------------------------------------------ the lid

    /**
     * A gun port facing east: shut by itself; a cannon placed inboard facing it opens it, one facing along the wall does
     * not; a player cannot shut it while the cannon stands there; without the cannon it shuts; a player props it open and
     * shuts it again; a propped lid stays open when a cannon comes and goes.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void theLidOpensForACannonAndAPlayer(GameTestHelper h) {
        floor(h);
        BlockPos port = new BlockPos(4, 1, 4);
        h.setBlock(port, portFacing(Direction.EAST));
        java.util.function.Supplier<Boolean> open = () -> h.getBlockState(port).getValue(BulwarkPortBlock.OPEN);
        h.assertFalse(open.get(), "shut by itself");
        Player p = holder(h, Items.AIR, 0, Direction.EAST);
        // a cannon along the wall (facing north, its master inboard): stays shut
        cannon(h, new BlockPos(3, 1, 4), Direction.NORTH);
        h.assertFalse(open.get(), "opened for a cannon facing along the wall");
        h.setBlock(new BlockPos(3, 1, 4), Blocks.AIR);
        h.setBlock(new BlockPos(3, 1, 5), Blocks.AIR);
        // a cannon facing the port opens it
        cannon(h, new BlockPos(3, 1, 4), Direction.EAST);
        h.assertTrue(open.get(), "the cannon did not open the lid");
        useLid(h, p, port);
        h.assertTrue(open.get() && !h.getBlockState(port).getValue(BulwarkPortBlock.PROPPED), "a player shut the lid on the gun");
        h.setBlock(new BlockPos(3, 1, 4), Blocks.AIR);
        h.setBlock(new BlockPos(2, 1, 4), Blocks.AIR);
        h.assertFalse(open.get(), "the lid stays open without the cannon");
        // by hand
        h.assertTrue(useLid(h, p, port).consumesAction(), "the use was not taken");
        h.assertTrue(open.get(), "not propped open");
        cannon(h, new BlockPos(3, 1, 4), Direction.EAST);
        h.setBlock(new BlockPos(3, 1, 4), Blocks.AIR);
        h.setBlock(new BlockPos(2, 1, 4), Blocks.AIR);
        h.assertTrue(open.get(), "a propped lid shut when the cannon left");
        useLid(h, p, port);
        h.assertFalse(open.get(), "not shut by hand");
        // open, crew and players pass; shut, it collides like the wall
        BlockPos abs = h.absolutePos(port);
        h.assertFalse(h.getBlockState(port).isPathfindable(PathComputationType.LAND), "crew path through a shut port");
        double shutWidth = h.getBlockState(port).getCollisionShape(h.getLevel(), abs, CollisionContext.empty()).bounds().getZsize();
        h.assertTrue(Math.abs(shutWidth - 1.0) < 1e-6, "the shut port's collision is not the whole wall: " + shutWidth);
        h.succeed();
    }

    // ------------------------------------------------------------------ the gangway and the swivel gun

    /**
     * On land: a gun port on a stone pier edge facing east, a landing two blocks of air east of it; with the lid open a
     * plank used on the sill starts its run in the block beyond the port and lands level; with the lid shut the port is
     * no opening and the run does not start beyond it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aPlankFromAGunPortsSill(GameTestHelper h) {
        for (int x = 0; x <= 3; x++) for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int x = 6; x <= 8; x++) for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos port = new BlockPos(3, 2, 4);
        h.setBlock(port, portFacing(Direction.EAST));
        BoardingPlanks.Laid shut = BoardingPlanks.lay(h.getLevel(), h.absolutePos(port), Direction.UP, Direction.EAST);
        h.assertFalse(shut.outcome() == BoardingPlanks.Outcome.OK && shut.cells().get(0).equals(h.absolutePos(new BlockPos(4, 2, 4))),
                "a plank went out through a shut lid");
        for (BlockPos c : shut.outcome() == BoardingPlanks.Outcome.OK ? shut.cells() : List.<BlockPos>of()) h.getLevel().removeBlock(c, false);
        h.setBlock(port, portFacing(Direction.EAST).setValue(BulwarkPortBlock.PROPPED, true).setValue(BulwarkPortBlock.OPEN, true));
        BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), h.absolutePos(port), Direction.UP, Direction.EAST);
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "refused: " + laid.outcome());
        h.assertTrue(laid.cells().get(0).equals(h.absolutePos(new BlockPos(4, 2, 4))), "the run starts at " + laid.cells().get(0));
        h.assertTrue(laid.landing() == PlankRun.Landing.LEVEL, "landing " + laid.landing());
        h.succeed();
    }

    /** A swivel gun placed on a bulwark takes its walled edge (east), shifted onto the wall like on a railing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aSwivelGunSitsOnABulwark(GameTestHelper h) {
        floor(h);
        setEdges(h, bulwark(), new BlockPos(4, 1, 4), Direction.EAST);
        Player p = holder(h, CannonContent.SWIVEL_GUN.get().asItem(), 1, Direction.EAST);
        h.assertTrue(use(h, p, new BlockPos(4, 1, 4), Direction.UP, 0.5, 1.0, 0.5).consumesAction(), "the gun was not placed on the bulwark");
        BlockState s = h.getBlockState(new BlockPos(4, 2, 4));
        h.assertTrue(s.getBlock() instanceof SwivelGunBlock && s.getValue(SwivelGunBlock.MOUNT) == SwivelMount.EAST, "not mounted east: " + s);
        h.succeed();
    }
}
