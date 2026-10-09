package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlanks;
import com.richardsenger.piratesnships.combat.boarding.PlankRun;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.cannon.CannonballEntity;
import com.richardsenger.piratesnships.combat.cannon.ShotKind;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlock;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.SwivelMount;
import com.richardsenger.piratesnships.combat.cannon.SwivelRules;
import com.richardsenger.piratesnships.combat.cannon.SwivelService;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.RatlinesBlock;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * The railing family (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)") in a real server: placing at the edge, the
 * posts and corners as a run grows, adding an edge, waterlogging, the drops, the toggle and the collision height; a
 * carriage cannon firing through a port and into a plain railing; a gangway laid from a port to a pier and walked; the
 * swivel gun on a railing; ratlines on a railing; and a railing assembled into a ship.
 */
public final class RailingGameTests {

    private static final String BATCH = "pirates_n_ships_config_ship_decor_railing_";
    /** Ticks after assembly before a plank is laid (Sable has the ship's bounds, the bobbing has died down). */
    private static final int SETTLE = 40;

    private RailingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RailingGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static RailingBlock railing() {
        return RailingContent.RAILINGS.get(ShipWood.OAK);
    }

    private static RailingPortBlock port() {
        return RailingContent.PORTS.get(ShipWood.OAK);
    }

    private static void floor(GameTestHelper h, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** A survival mock player looking {@code look} with {@code count} of {@code item} in hand. */
    private static Player holder(GameTestHelper h, Item item, int count, Direction look) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(0.5, 1, 0.5));
        p.moveTo(at.x, at.y, at.z, look.toYRot(), 30f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        return p;
    }

    /**
     * Uses the held item on the face {@code face} of the block at relative {@code clicked}, at the point
     * {@code (fx, fy, fz)} of that block (0..1 each).
     */
    private static InteractionResult use(GameTestHelper h, Player p, BlockPos clicked, Direction face, double fx, double fy, double fz) {
        BlockPos abs = h.absolutePos(clicked);
        Vec3 hit = new Vec3(abs.getX() + fx, abs.getY() + fy, abs.getZ() + fz);
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, abs, false)));
    }

    /** Clicks the top of the floor block under {@code cell} at {@code (fx, fz)} with the held item. */
    private static InteractionResult clickFloor(GameTestHelper h, Player p, BlockPos cell, double fx, double fz) {
        return use(h, p, cell.below(), Direction.UP, fx, 1.0, fz);
    }

    /** A railing with {@code edges} set at relative {@code rel}, its posts worked out from the neighbours. */
    private static BlockPos setRailing(GameTestHelper h, BlockPos rel, Direction... edges) {
        BlockState s = railing().defaultBlockState();
        for (Direction d : edges) s = s.setValue(RailingBlock.edge(d), true);
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, RailingBlock.withPosts(s, h.getLevel(), abs), 3);
        return abs;
    }

    private static Set<Direction> set(Direction... ds) {
        return ds.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(ds[0], ds);
    }

    private static Set<Direction> edgesAt(GameTestHelper h, BlockPos rel) {
        return RailingBlock.edges(h.getBlockState(rel));
    }

    private static Set<Direction> postsAt(GameTestHelper h, BlockPos rel) {
        return RailingBlock.posts(h.getBlockState(rel));
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

    /** A ball as the cannon at {@code master} (facing east) fires it at elevation step {@code step}. */
    private static CannonballEntity shot(GameTestHelper h, BlockPos master, int step) {
        CannonService.Barrel b = CannonService.barrel(master, Direction.EAST, CannonConfig.elevationDegrees(step));
        Vec3 v = CannonRules.ballVelocity(b.direction(), CannonConfig.muzzleVelocity(ShotKind.BALL), Vec3.ZERO);
        CannonballEntity ball = new CannonballEntity(h.getLevel(), b.muzzle(), v, CannonConfig.entityDamage(ShotKind.BALL), 60);
        h.getLevel().addFreshEntity(ball);
        return ball;
    }

    // ------------------------------------------------------------------ placement, posts, edges

    /** A click near the north side of the floor rails the north edge of the block above; a lone edge has two end posts. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void placingRailsTheEdgeNearestTheClick(GameTestHelper h) {
        floor(h, 0, 8);
        Player p = holder(h, railing().asItem(), 4, Direction.SOUTH);
        BlockPos cell = new BlockPos(4, 1, 4);
        h.assertTrue(clickFloor(h, p, cell, 0.5, 0.1).consumesAction(), "the railing was not placed");
        h.assertTrue(edgesAt(h, cell).equals(set(Direction.NORTH)), "edges " + edgesAt(h, cell));
        h.assertTrue(postsAt(h, cell).equals(set(Direction.NORTH, Direction.EAST)), "posts " + postsAt(h, cell));
        h.assertTrue(p.getMainHandItem().getCount() == 3, "one item per edge");
        // near the centre: the edge the player faces (south)
        BlockPos centre = new BlockPos(6, 1, 6);
        h.assertTrue(clickFloor(h, p, centre, 0.55, 0.45).consumesAction(), "the second railing was not placed");
        h.assertTrue(edgesAt(h, centre).equals(set(Direction.SOUTH)), "a centre click rails " + edgesAt(h, centre));
        h.succeed();
    }

    /**
     * A run grows: two blocks along the north edge share no post between them; a second edge in the east block turns it
     * (one corner post); a block beyond a one-block gap gets posts on both its ends, and the run's end before the gap
     * keeps its post.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void postsFollowTheRunTheCornerAndTheGap(GameTestHelper h) {
        floor(h, 0, 8);
        Player p = holder(h, railing().asItem(), 16, Direction.NORTH);
        BlockPos a = new BlockPos(2, 1, 4), b = new BlockPos(3, 1, 4), c = new BlockPos(5, 1, 4);
        clickFloor(h, p, a, 0.5, 0.1);
        clickFloor(h, p, b, 0.5, 0.1);
        h.assertTrue(postsAt(h, a).equals(set(Direction.NORTH)), "a: only the west end post, got " + postsAt(h, a));
        h.assertTrue(postsAt(h, b).equals(set(Direction.EAST)), "b: only the east end post, got " + postsAt(h, b));
        // a second click into b, near its east side, adds the east edge: one corner post at the north-east
        h.assertTrue(clickFloor(h, p, b, 0.9, 0.5).consumesAction(), "the second edge was refused");
        h.assertTrue(edgesAt(h, b).equals(set(Direction.NORTH, Direction.EAST)), "b's edges " + edgesAt(h, b));
        h.assertTrue(postsAt(h, b).equals(set(Direction.EAST, Direction.SOUTH)),
                "b: the corner post and the east edge's south end, got " + postsAt(h, b));
        // beyond a gap of one block (x 4): both ends of c get posts
        clickFloor(h, p, c, 0.5, 0.1);
        h.assertTrue(postsAt(h, c).equals(set(Direction.NORTH, Direction.EAST)), "c: " + postsAt(h, c));
        // a fourth edge on the railing block itself, clicked directly: the nearest free edge (south)
        h.assertTrue(use(h, p, b, Direction.UP, 0.5, 1.0, 0.95).consumesAction(), "clicking the railing added nothing");
        h.assertTrue(edgesAt(h, b).equals(set(Direction.NORTH, Direction.EAST, Direction.SOUTH)), "b's edges " + edgesAt(h, b));
        h.assertTrue(p.getMainHandItem().getCount() == 11, "five edges used five items, have " + p.getMainHandItem().getCount());
        h.succeed();
    }

    /** A railing placed into a water source is waterlogged; a run next to a port facing the same edge ends against it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void waterloggedAndEndsAgainstAPort(GameTestHelper h) {
        floor(h, 0, 8);
        BlockPos wet = new BlockPos(2, 1, 2);
        h.setBlock(wet, Blocks.WATER);
        Player p = holder(h, railing().asItem(), 4, Direction.NORTH);
        h.assertTrue(clickFloor(h, p, wet, 0.5, 0.1).consumesAction(), "not placed in water");
        h.assertTrue(Waterlogging.isWaterlogged(h.getBlockState(wet)), "the railing in water is not waterlogged");
        // a port east of a railing, both on the north edge: the railing draws no post against the port's own
        h.setBlock(new BlockPos(5, 1, 6), port().defaultBlockState().setValue(RailingPortBlock.FACING, Direction.NORTH));
        BlockPos next = new BlockPos(4, 1, 6);
        clickFloor(h, p, next, 0.5, 0.1);
        h.assertTrue(postsAt(h, next).equals(set(Direction.NORTH)), "posts beside the port: " + postsAt(h, next));
        h.succeed();
    }

    /** Breaking a railing of three edges drops three railings. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void breakingDropsOnePerEdge(GameTestHelper h) {
        floor(h, 0, 8);
        BlockPos abs = setRailing(h, new BlockPos(4, 1, 4), Direction.NORTH, Direction.EAST, Direction.WEST);
        h.getLevel().destroyBlock(abs, true);
        h.runAfterDelay(2, () -> {
            int n = items(h, railing().asItem());
            h.assertTrue(n == 3, "expected 3 railings, got " + n);
            h.succeed();
        });
    }

    /** With railings off, the item places nothing and is kept. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "disabled")
    public static void disabledRailingsDoNotPlace(GameTestHelper h) {
        ConfigOverrides.during(h, DecorConfig.RAILINGS_ENABLED, false);
        floor(h, 0, 8);
        Player p = holder(h, railing().asItem(), 1, Direction.NORTH);
        h.assertFalse(clickFloor(h, p, new BlockPos(4, 1, 4), 0.5, 0.1).consumesAction(), "placed while disabled");
        h.assertTrue(h.getBlockState(new BlockPos(4, 1, 4)).isAir(), "a railing stands while disabled");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "the item was used while disabled");
        h.succeed();
    }

    /** The collision reaches 1.5 blocks by default (a fence), the outline only the model's top. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void collisionIsAFencesHeight(GameTestHelper h) {
        BlockPos abs = setRailing(h, new BlockPos(4, 1, 4), Direction.NORTH);
        BlockState s = h.getLevel().getBlockState(abs);
        double top = s.getCollisionShape(h.getLevel(), abs, CollisionContext.empty()).max(Direction.Axis.Y);
        h.assertTrue(Math.abs(top - 1.5) < 1e-6, "collision top " + top);
        double outline = s.getShape(h.getLevel(), abs).max(Direction.Axis.Y);
        h.assertTrue(Math.abs(outline - 17 / 16.0) < 1e-6, "outline top " + outline);
        h.assertFalse(s.isPathfindable(net.minecraft.world.level.pathfinder.PathComputationType.LAND), "crew path through it");
        h.succeed();
    }

    /** At 1.0 a player can vault it: the collision ends at the block's top. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "vault")
    public static void collisionFollowsTheConfig(GameTestHelper h) {
        ConfigOverrides.during(h, DecorConfig.RAILING_COLLISION_BLOCKS, 1.0);
        BlockPos abs = setRailing(h, new BlockPos(4, 1, 4), Direction.NORTH);
        double top = h.getLevel().getBlockState(abs).getCollisionShape(h.getLevel(), abs, CollisionContext.empty()).max(Direction.Axis.Y);
        h.assertTrue(Math.abs(top - 1.0) < 1e-6, "collision top " + top);
        h.succeed();
    }

    // ------------------------------------------------------------------ the port and the cannon

    /**
     * Seven lanes, each a carriage cannon facing east with a railing port on the block in front of its muzzle end: the
     * first fires for real (loaded, at the level step), the others get a ball spawned as the gun fires it at each of the
     * elevation steps. Every ball flies on past the port, and every port stands.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40)
    public static void aCannonFiresThroughAPortAtEveryElevation(GameTestHelper h) {
        floor(h, 0, 3);
        ServerLevel level = h.getLevel();
        int steps = CannonConfig.ELEVATION_STEPS.get();
        List<CannonballEntity> balls = new ArrayList<>();
        int portX = h.absolutePos(new BlockPos(3, 1, 0)).getX();
        for (int lane = 0; lane <= steps && lane < 8; lane++) {
            BlockPos master = cannon(h, new BlockPos(2, 1, lane), Direction.EAST);
            h.setBlock(new BlockPos(3, 1, lane), port().defaultBlockState().setValue(RailingPortBlock.FACING, Direction.EAST));
            if (lane == 0) {
                Player creative = h.makeMockPlayer(GameType.CREATIVE);
                creative.getAbilities().instabuild = true;
                CannonService.load(level, master, creative, new ItemStack(Items.GUNPOWDER));
                CannonService.load(level, master, creative, new ItemStack(CombatContent.CANNONBALL.get()));
                CannonService.Use use = CannonService.fire(level, master, null);
                h.assertTrue(use.outcome() == CannonService.Outcome.FIRED && use.ball() != null, "the cannon did not fire: " + use.outcome());
                balls.add(use.ball());
            } else {
                balls.add(shot(h, master, lane - 1));
            }
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < balls.size(); i++) {
                CannonballEntity b = balls.get(i);
                // a ball hit in its first tick is discarded but still makes that tick's move (ThrowableProjectile.tick)
                h.assertTrue(b.getX() > portX + 2.5 && !(b.isRemoved() && b.tickCount <= 1),
                        "lane " + i + ": the ball stopped at x " + b.getX() + " (port at " + portX + ", ticks " + b.tickCount + ")");
                h.assertTrue(h.getBlockState(new BlockPos(3, 1, i)).getBlock() instanceof RailingPortBlock, "lane " + i + ": the port is gone");
            }
        });
    }

    /**
     * The same cannon behind a plain railing on the east edge (1.5-block collision), with balls at every elevation step:
     * vanilla's projectile clip tests the blocks the line runs through from the muzzle's own block on, and the muzzle
     * lies inside the rail's collision only with the barrel depressed (−5°), which stops that ball in its first tick and
     * breaks the railing (wood, cannon_breakable); level, the ball
     * starts on the railing block's outer face, and raised, in the block above it, so those fly on. Pinned beside the
     * port's test (see {@code RailingRulesTest}).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40)
    public static void aPlainRailingStopsOnlyADepressedShot(GameTestHelper h) {
        floor(h, 0, 3);
        int steps = CannonConfig.ELEVATION_STEPS.get();
        List<CannonballEntity> balls = new ArrayList<>();
        List<Boolean> stopped = new ArrayList<>();
        List<Vec3> starts = new ArrayList<>();
        List<String> clips = new ArrayList<>();
        int railX = h.absolutePos(new BlockPos(3, 1, 0)).getX();
        for (int i = 0; i < steps && i < 9; i++) {
            BlockPos master = cannon(h, new BlockPos(2, 1, i), Direction.EAST);
            setRailing(h, new BlockPos(3, 1, i), Direction.EAST);
            CannonballEntity ball = shot(h, master, i);
            balls.add(ball);
            starts.add(ball.position());
            clips.add(h.getLevel().clip(new net.minecraft.world.level.ClipContext(ball.position(), ball.position().add(ball.getDeltaMovement()),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, ball)).getType()
                    + " " + h.getLevel().getBlockState(BlockPos.containing(ball.position())));
            stopped.add(CannonConfig.elevationDegrees(i) < 0);
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < balls.size(); i++) {
                CannonballEntity b = balls.get(i);
                // a ball hit in its first tick is discarded but still makes that tick's move (ThrowableProjectile.tick)
                boolean hitAtOnce = b.isRemoved() && b.tickCount <= 1;
                boolean standing = h.getBlockState(new BlockPos(3, 1, i)).getBlock() instanceof RailingBlock;
                if (stopped.get(i)) {
                    h.assertTrue(hitAtOnce && !standing, "step " + i + ": the depressed ball was not stopped by the railing (removed "
                            + b.isRemoved() + ", ticks " + b.tickCount + ", start " + starts.get(i) + ", clip " + clips.get(i) + ")");
                } else {
                    h.assertTrue(!hitAtOnce && standing && b.getX() > railX + 2.5, "step " + i + ": the ball was stopped at x "
                            + b.getX() + " (removed " + b.isRemoved() + ", ticks " + b.tickCount + ")");
                }
            }
        });
    }

    // ------------------------------------------------------------------ the gangway through a port

    /**
     * A railed ship (railings on her east gunwale either side of a port) and a stone pier three blocks of water east of
     * it, level with the gunwale: a plank used on the port's sill facing east lays a level gangway from the block beyond
     * the port to the pier, and a player walks from the deck through the port and along the plank onto the pier.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void aGangwayFromAPortReachesAPier(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helm = DryHullGameTests.hull(h, 2, false); // x 2..6, z 9..13, deck y 8, helm (4, 9, 11)
        h.setBlock(new BlockPos(6, 9, 11), port().defaultBlockState().setValue(RailingPortBlock.FACING, Direction.EAST));
        setRailing(h, new BlockPos(6, 9, 10), Direction.EAST);
        setRailing(h, new BlockPos(6, 9, 12), Direction.EAST);
        Fixture a = DryHullGameTests.assemble(h, helm);
        h.runAfterDelay(SETTLE, () -> {
            ServerLevel level = h.getLevel();
            BlockPos portPlot = a.helmPlot().offset(2, 0, 0);
            h.assertTrue(level.getBlockState(portPlot).getBlock() instanceof RailingPortBlock, "the port is not in the plot");
            BlockPos rail = BlockPos.containing(a.ship().toWorld(Vec3.atCenterOf(portPlot.below())));
            // the pier: three blocks of water, then stone up to the gunwale's row
            int floorY = h.absolutePos(new BlockPos(0, 2, 0)).getY();
            for (int x = rail.getX() + 4; x <= rail.getX() + 7; x++) {
                for (int z = rail.getZ() - 1; z <= rail.getZ() + 1; z++) {
                    for (int y = floorY; y <= rail.getY(); y++) level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            BlockPos edge = new BlockPos(rail.getX() + 4, rail.getY(), rail.getZ());
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BoardingContent.PLANK_ITEM.get()));
            player.setYRot(Direction.EAST.toYRot());
            InteractionResult r = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(portPlot).add(0, 0.5, 0), Direction.UP, portPlot, false)));
            h.assertTrue(r.consumesAction(), "the plank on the port's sill was refused: " + r);
            BlockPos first = portPlot.east();
            h.assertTrue(level.getBlockState(first).is(BoardingContent.PLANK.get()), "no plank in the block beyond the port");
            h.assertTrue(level.getBlockState(first.above()).isAir(), "the plank was laid a block up");
            // walk it: from the deck inboard of the port, east, onto the pier
            Player walker = h.makeMockPlayer(GameType.SURVIVAL);
            Vec3 deck = a.ship().toWorld(Vec3.atBottomCenterOf(portPlot.west())).add(0, 0.05, 0);
            walker.moveTo(deck.x, deck.y, deck.z, -90f, 0f);
            walker.setOnGround(true);
            double lowest = walker.getY();
            for (int step = 0; step < 200 && walker.getX() < edge.getX() + 1.5; step++) {
                walker.move(MoverType.SELF, new Vec3(0.12, -0.1, 0.0));
                lowest = Math.min(lowest, walker.getY());
            }
            h.assertTrue(walker.getX() >= edge.getX() + 1.4, "the walker got stuck at " + walker.position() + " (pier edge " + edge + ", deck " + deck + ", port " + a.ship().toWorld(Vec3.atBottomCenterOf(portPlot)) + ")");
            h.assertTrue(Math.abs(walker.getY() - (edge.getY() + 1.0)) < 0.3, "the walker ended at y " + walker.getY());
            h.assertTrue(lowest > deck.y - 0.3, "the walker dropped to y " + lowest + " on the way (deck " + deck.y + ")");
            h.succeed();
        });
    }

    /**
     * On land: a port on a stone edge, a stone landing two blocks of air east of it; a plank used on the port's sill
     * facing east starts its run in the block beyond the port at the port's height and lands level.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aPlankOnAPortsSillStartsBeyondThePortOnLand(GameTestHelper h) {
        // a port on a stone pier edge, a stone landing two blocks of air east of it
        for (int x = 0; x <= 3; x++) for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int x = 6; x <= 8; x++) for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        h.setBlock(new BlockPos(3, 2, 4), port().defaultBlockState().setValue(RailingPortBlock.FACING, Direction.EAST));
        BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), h.absolutePos(new BlockPos(3, 2, 4)), Direction.UP, Direction.EAST);
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "refused: " + laid.outcome());
        h.assertTrue(laid.cells().get(0).equals(h.absolutePos(new BlockPos(4, 2, 4))), "the run starts at " + laid.cells().get(0));
        h.assertTrue(laid.landing() == PlankRun.Landing.LEVEL, "landing " + laid.landing());
        h.succeed();
    }

    // ------------------------------------------------------------------ the swivel gun on a railing

    /**
     * A swivel gun placed on a railing takes its railed edge (east): its shape shifts 6 px east, its crew stands on the
     * deck inboard (west) of the railing, it fires from the shifted muzzle, and breaking the railing drops the gun.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40)
    public static void aSwivelGunSitsOnTheRailedEdge(GameTestHelper h) {
        floor(h, 0, 8);
        BlockPos rail = setRailing(h, new BlockPos(4, 1, 4), Direction.EAST);
        Player p = holder(h, CannonContent.SWIVEL_GUN.get().asItem(), 1, Direction.EAST);
        h.assertTrue(use(h, p, new BlockPos(4, 1, 4), Direction.UP, 0.5, 1.0, 0.5).consumesAction(), "the gun was not placed on the railing");
        BlockPos gunRel = new BlockPos(4, 2, 4);
        BlockPos gun = h.absolutePos(gunRel);
        BlockState s = h.getBlockState(gunRel);
        h.assertTrue(s.getBlock() instanceof SwivelGunBlock, "no gun on the railing");
        h.assertTrue(s.getValue(SwivelGunBlock.MOUNT) == SwivelMount.EAST, "mount " + s.getValue(SwivelGunBlock.MOUNT));
        AABB box = s.getShape(h.getLevel(), gun).bounds();
        h.assertTrue(Math.abs(box.minX - 11 / 16.0) < 1e-6 && Math.abs(box.maxX - 1.0) < 1e-6 && Math.abs(box.minZ - 5 / 16.0) < 1e-6,
                "the shape is not shifted east: " + box);
        BlockPos seat = ((SwivelGunBlock) s.getBlock()).seatSpot(s, gun);
        h.assertTrue(rail.west().equals(seat), "the crew stands at " + seat + ", not inboard of the railing");
        // fire east along the level: the shot leaves the shifted muzzle
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ServerLevel level = h.getLevel();
        SwivelService.load(level, gun, creative, new ItemStack(Items.GUNPOWDER));
        SwivelService.load(level, gun, creative, new ItemStack(SwivelService.ammoItem(), 64));
        ((SwivelGunBlockEntity) level.getBlockEntity(gun)).setAim(new SwivelRules.Aim(-90, 0));
        SwivelService.Use use = SwivelService.fire(level, gun, null);
        h.assertTrue(use.outcome() == SwivelService.Outcome.FIRED && use.ball() != null, "the gun did not fire: " + use.outcome());
        Vec3 expected = SwivelRules.muzzle(gun.getX(), gun.getY(), gun.getZ(), SwivelMount.EAST, -90, 0);
        h.assertTrue(use.ball().position().distanceTo(expected) < 1e-6, "muzzle " + use.ball().position() + ", expected " + expected);
        use.ball().discard();
        level.destroyBlock(rail, false);
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getBlockState(gunRel).isAir(), "the gun stays without its railing");
            h.assertTrue(items(h, CannonContent.SWIVEL_GUN.get().asItem()) == 1, "the gun was not dropped");
            h.succeed();
        });
    }

    /** On a corner railing the gun takes the railed edge nearest the click; on a fence it sits in the middle as before. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aSwivelGunTakesTheNearestEdgeAndAFenceKeepsTheMiddle(GameTestHelper h) {
        floor(h, 0, 8);
        setRailing(h, new BlockPos(2, 1, 2), Direction.NORTH, Direction.EAST);
        Player p = holder(h, CannonContent.SWIVEL_GUN.get().asItem(), 2, Direction.NORTH);
        use(h, p, new BlockPos(2, 1, 2), Direction.UP, 0.5, 1.0, 0.1);
        BlockState corner = h.getBlockState(new BlockPos(2, 2, 2));
        h.assertTrue(corner.getBlock() instanceof SwivelGunBlock && corner.getValue(SwivelGunBlock.MOUNT) == SwivelMount.NORTH,
                "corner mount: " + corner);
        h.setBlock(new BlockPos(6, 1, 6), Blocks.OAK_FENCE);
        use(h, p, new BlockPos(6, 1, 6), Direction.UP, 0.9, 1.0, 0.5);
        BlockState fence = h.getBlockState(new BlockPos(6, 2, 6));
        h.assertTrue(fence.getBlock() instanceof SwivelGunBlock && fence.getValue(SwivelGunBlock.MOUNT) == SwivelMount.NONE,
                "fence mount: " + fence);
        AABB box = fence.getShape(h.getLevel(), h.absolutePos(new BlockPos(6, 2, 6))).bounds();
        h.assertTrue(box.equals(new AABB(5 / 16.0, 0, 5 / 16.0, 11 / 16.0, 9 / 16.0, 11 / 16.0)), "fence shape " + box);
        h.assertTrue(SwivelRules.mountOffset(fence.getValue(SwivelGunBlock.MOUNT)).equals(Vec3.ZERO), "the fence gun is drawn off centre");
        h.succeed();
    }

    // ------------------------------------------------------------------ ratlines on a railing

    /** A sloped ratline laid from the top of a railing stands on it; a hung one hangs on its side. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void ratlinesStandOnAndHangOnARailing(GameTestHelper h) {
        floor(h, 0, 8);
        setRailing(h, new BlockPos(4, 1, 4), Direction.EAST);
        Player p = holder(h, RiggingContent.RATLINES_ITEM.get(), 4, Direction.WEST);
        h.assertTrue(use(h, p, new BlockPos(4, 1, 4), Direction.UP, 0.5, 1.0, 0.5).consumesAction(), "no ratline on the railing's top");
        BlockState slope = h.getBlockState(new BlockPos(4, 2, 4));
        h.assertTrue(slope.getBlock() instanceof RatlinesBlock && slope.getValue(RatlinesBlock.KIND) == RatlinesRules.Kind.SLOPE
                && slope.getValue(RatlinesBlock.FACING) == Direction.WEST, "not a sloped ratline rising west: " + slope);
        h.assertTrue(use(h, p, new BlockPos(4, 1, 4), Direction.WEST, 0.0, 0.5, 0.5).consumesAction(), "no ratline on the railing's side");
        BlockState hung = h.getBlockState(new BlockPos(3, 1, 4));
        h.assertTrue(hung.getBlock() instanceof RatlinesBlock && hung.getValue(RatlinesBlock.KIND) == RatlinesRules.Kind.WALL,
                "not hung on the railing: " + hung);
        h.succeed();
    }

    // ------------------------------------------------------------------ on a ship

    /** A railing on a hull's gunwale is assembled into the ship, keeps its state and weighs like a fence for Sable. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void aRailingAssembledIntoAShipStaysAndIsSuperLight(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        setRailing(h, new BlockPos(13, 9, 10), Direction.EAST);
        setRailing(h, new BlockPos(13, 9, 11), Direction.EAST);
        Fixture a = DryHullGameTests.assemble(h, helm);
        h.runAfterDelay(10, () -> {
            ServerLevel level = h.getLevel();
            List<BlockPos> rails = a.ship().plotBlocks().stream().filter(p -> level.getBlockState(p).getBlock() instanceof RailingBlock).toList();
            h.assertTrue(rails.size() == 2, "railings in the plot: " + rails.size());
            for (BlockPos p : rails) {
                BlockState s = level.getBlockState(p);
                h.assertTrue(RailingBlock.edges(s).equals(set(Direction.EAST)), "edges changed: " + s);
                h.assertTrue(s.is(SableWeightTags.SUPER_LIGHT) && s.is(SableWeightTags.QUARTER_VOLUME), "not in Sable's light tags");
                // refs/sable PhysicsBlockPropertyHelper#getMass (l.24) and #getVolume (l.70)
                double mass = PhysicsBlockPropertyHelper.getMass(level, p, s);
                double volume = PhysicsBlockPropertyHelper.getVolume(s);
                h.assertTrue(Math.abs(mass - 0.25) < 1e-6 && Math.abs(volume - 0.25) < 1e-6, "mass " + mass + ", volume " + volume);
            }
            h.assertTrue(RailingBlock.posts(level.getBlockState(rails.get(0))).size() == 1
                    && RailingBlock.posts(level.getBlockState(rails.get(1))).size() == 1, "the run's posts changed in the plot");
            h.succeed();
        });
    }
}
