package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * RL6 in a real server (docs/design.md §4.8 "Wide ports, double gates and the net's foot"): at the foot of a ratline net
 * a player walks along the rail and steps inboard onto the deck instead of being held on the net, at a railing latch
 * (standing on the rail's top inside the latch's cell) and at a chainplate on the deck, on land, on a still ship and on a
 * ship sailing at 4 m/s; climbing from the foot still starts with forward towards the net (looking ahead) and with
 * jump, climbing down a latched net ends on the rail's top, and {@code rigging.net_foot_exit = false} holds as before.
 */
public final class RatlineFootGameTests {

    /** Ticks after assembly before a player boards. */
    private static final int SETTLE = 40;
    /** Ticks before the player is spawned during which a ship under way is brought up to its speed. */
    private static final int DRIVE_LEAD = 15;
    /** How far a player looks down to step off the net's foot [degrees]. */
    private static final float LOOK_DOWN = 45f;
    /** Ticks a walk lasts, input and settling. */
    private static final int WALK_TICKS = 50;

    /** The land rig: the rail's x (railed west), its height, the run's z range, the wall's x and the hoop's y and z. */
    private static final int RAIL_X = 2, RAIL_Y = 1, RAIL_Z0 = 0, RAIL_Z1 = 12, WALL_X = 6, HOOP_Y = 6, NET_Z = 4;
    /** The ship rig (plot offsets from the helm as in {@link RatlineNetGameTests#shipRig}): the hull at x 9..13, z 9..13. */
    private static final int SHIP_RAIL_X = 9, SHIP_PLATE_X = 10, SHIP_DECK_Y = 9, SHIP_NET_Z = 10, SHIP_HOOP_Y = 13;

    private RatlineFootGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RatlineFootGameTests.class);
    }

    // ------------------------------------------------------------------ the walker

    /**
     * A walker's input: forward and strafe, jump and sneak, the direction it faces and its pitch (in the net's frame);
     * the input is let go once the feet are {@code stopAfter} blocks (horizontally) from where they started.
     */
    record Input(float forward, float strafe, boolean jump, boolean sneak, Vec3 facing, float pitch, double stopAfter) {
        static Input walk(Direction facing, float pitch, double stopAfter) {
            return new Input(1f, 0f, false, false, Vec3.atLowerCornerOf(facing.getNormal()), pitch, stopAfter);
        }
    }

    /** What a walk did, its feet followed in the net's frame. */
    static final class Track {
        Vec3 start, end;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        int climbing;
        boolean onGround;

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "start (%.2f, %.2f, %.2f), end (%.2f, %.2f, %.2f), y %.2f..%.2f, %d ticks climbing, %s",
                    start.x, start.y, start.z, end.x, end.y, end.z, minY, maxY, climbing, onGround ? "on the ground" : "in the air");
        }
    }

    /**
     * At tick {@code start} spawns a mock player at {@code at} (the net's frame) and gives it {@code in} every tick for
     * {@link #WALK_TICKS} ticks (facing turned into the world each tick, so a turning ship keeps it facing the same way
     * on deck); {@code drive} runs every tick from {@link #DRIVE_LEAD} before the spawn to the end (a ship under way).
     * Then {@code check} gets the track.
     */
    private static void walk(GameTestHelper h, int start, Vec3 at, Function<Vec3, Vec3> toWorld, Function<Vec3, Vec3> toFrame,
                             Function<Vec3, Vec3> dirToWorld, Input in, Runnable drive, String what, Consumer<Track> check) {
        Player[] player = {null};
        Track track = new Track();
        boolean[] stopped = {false};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t >= start - DRIVE_LEAD && t <= start + WALK_TICKS) {
                drive.run();
            }
            if (t == start) {
                player[0] = RatlineNetGameTests.playerInLevel(h, toWorld.apply(at), yaw(dirToWorld.apply(in.facing())));
            }
            Player p = player[0];
            if (p == null || p.isRemoved() || t > start + WALK_TICKS) {
                return;
            }
            Vec3 feet = toFrame.apply(p.position());
            if (track.start == null) {
                track.start = feet;
            }
            stopped[0] |= Math.hypot(feet.x - track.start.x, feet.z - track.start.z) >= in.stopAfter();
            boolean go = !stopped[0];
            p.setYRot(yaw(dirToWorld.apply(in.facing())));
            p.setXRot(in.pitch());
            p.zza = go ? in.forward() : 0f;
            p.xxa = go ? in.strafe() : 0f;
            p.setJumping(go && in.jump());
            p.setShiftKeyDown(in.sneak());
            track.end = feet;
            track.minY = Math.min(track.minY, feet.y);
            track.maxY = Math.max(track.maxY, feet.y);
            track.climbing += p.onClimbable() ? 1 : 0;
            track.onGround = p.onGround();
        });
        h.runAfterDelay(start + WALK_TICKS + 1, () -> {
            h.assertTrue(track.start != null, what + ": no player was spawned");
            Constants.LOG.info("[RL6] {}: {}", what, track);
            check.accept(track);
            h.succeed();
        });
    }

    /** The world yaw of a player facing {@code dir}. */
    private static float yaw(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    // ------------------------------------------------------------------ land rigs

    /**
     * A land rig in a 24 block test: a stone floor, a run of west-railed railings at x {@link #RAIL_X} along z
     * {@link #RAIL_Z0}..{@link #RAIL_Z1}, a log wall at x {@link #WALL_X} with a hoop on its west face at y
     * {@link #HOOP_Y}, z {@link #NET_Z}; a net strung from the railing at z {@link #NET_Z} to the hoop (a 43° net from a
     * latch). Returns the latch's position.
     */
    private static BlockPos landLatch(GameTestHelper h) {
        RatlineNetGameTests.floor(h, 16);
        for (int z = RAIL_Z0; z <= RAIL_Z1; z++) {
            h.setBlock(new BlockPos(RAIL_X, RAIL_Y, z), RatlineSheetGameTests.railing(Direction.WEST));
        }
        for (int y = 1; y <= HOOP_Y; y++) {
            h.setBlock(new BlockPos(WALL_X, y, NET_Z), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(WALL_X - 1, HOOP_Y, NET_Z), RatlineNetGameTests.hoop(Direction.WEST));
        ServerLevel level = h.getLevel();
        BlockPos rail = h.absolutePos(new BlockPos(RAIL_X, RAIL_Y, NET_Z));
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        String first = RatlineNetGameTests.key(RatlineNetItem.useOnRailing(level, stack, rail, Direction.WEST, null));
        String r = first.equals(RatlineNetItem.KEY_MARKED)
                ? RatlineNetGameTests.key(RatlineNetItem.use(level, stack, h.absolutePos(new BlockPos(WALL_X - 1, HOOP_Y, NET_Z)), null))
                : first;
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "the latched net was not strung: " + r);
        return rail.above();
    }

    /** A land rig with a chainplate on the floor at x {@link #RAIL_X} (facing east) instead; returns the chainplate. */
    private static BlockPos landChainplate(GameTestHelper h) {
        RatlineNetGameTests.floor(h, 16);
        for (int y = 1; y <= HOOP_Y; y++) {
            h.setBlock(new BlockPos(WALL_X, y, NET_Z), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(WALL_X - 1, HOOP_Y, NET_Z), RatlineNetGameTests.hoop(Direction.WEST));
        BlockPos plate = new BlockPos(RAIL_X, 1, NET_Z);
        h.setBlock(plate, RatlineNetGameTests.chainplate(AttachFace.FLOOR, Direction.EAST));
        String r = RatlineNetGameTests.string(h.getLevel(), h.absolutePos(plate), h.absolutePos(new BlockPos(WALL_X - 1, HOOP_Y, NET_Z)),
                new ItemStack(RiggingContent.NET_ITEM.get()));
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "the chainplate's net was not strung: " + r);
        return h.absolutePos(plate);
    }

    /** Standing on the rail's top in the latch's cell at {@code latch} (the rail's collision is 1.5 blocks high). */
    private static Vec3 onTheRail(BlockPos latch) {
        Point ring = RailingLatchBlock.ring(latch.below(), Direction.WEST);
        return new Vec3(ring.x() + 0.06, latch.getY() - 1 + RailingBlock.collisionHeightPx() / 16.0 + 0.02, ring.z());
    }

    private static void landWalk(GameTestHelper h, Vec3 at, Input in, String what, Consumer<Track> check) {
        walk(h, 0, at, v -> v, v -> v, v -> v, in, () -> { }, what, check);
    }

    // ------------------------------------------------------------------ on land

    /** On the rail's top at a latched net's foot, a player walks two blocks along the rail and stays on it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void walksAlongTheRailFromALatchOnLand(GameTestHelper h) {
        BlockPos latch = landLatch(h);
        Vec3 at = onTheRail(latch);
        landWalk(h, at, Input.walk(Direction.SOUTH, 0f, 2.2), "along the rail from a latch on land", t -> {
            h.assertTrue(t.end.z - t.start.z >= 2.0, "walked only " + (t.end.z - t.start.z) + " blocks along the rail: " + t);
            h.assertTrue(t.minY >= at.y - 0.1 && t.maxY <= at.y + 0.3, "left the rail's top: " + t);
        });
    }

    /** The same, strafing along the rail while facing the net. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void strafesAlongTheRailFromALatchOnLand(GameTestHelper h) {
        BlockPos latch = landLatch(h);
        Vec3 at = onTheRail(latch);
        // facing east, strafing right (negative strafe) goes south
        landWalk(h, at, new Input(0f, -1f, false, false, new Vec3(1, 0, 0), 0f, 2.2), "strafing along the rail from a latch on land", t -> {
            h.assertTrue(t.end.z - t.start.z >= 2.0, "strafed only " + (t.end.z - t.start.z) + " blocks along the rail: " + t);
            h.assertTrue(t.minY >= at.y - 0.1 && t.maxY <= at.y + 0.3, "left the rail's top: " + t);
        });
    }

    /** With {@code rigging.net_foot_exit} off the foot holds a strafing player on the line as before RL6. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = "pirates_n_ships_config_rigging_foot_exit_off")
    public static void footExitOffHoldsAsBefore(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.NET_FOOT_EXIT, false);
        BlockPos latch = landLatch(h);
        Vec3 at = onTheRail(latch);
        landWalk(h, at, new Input(0f, -1f, false, false, new Vec3(1, 0, 0), 0f, 2.2), "strafing at a latch, foot exit off", t -> {
            h.assertTrue(t.end.z - t.start.z < 1.0, "the foot let go with the foot exit off: " + t);
        });
    }

    /** At a latched net's foot a player looking down at the deck steps inboard off the rail onto the floor. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void stepsInboardFromALatchOnLand(GameTestHelper h) {
        BlockPos latch = landLatch(h);
        Vec3 at = onTheRail(latch);
        landWalk(h, at, Input.walk(Direction.EAST, LOOK_DOWN, 1.6), "inboard from a latch on land", t -> {
            h.assertTrue(t.maxY <= at.y + 0.3, "climbed the net instead: " + t);
            h.assertTrue(t.onGround && Math.abs(t.end.y - (latch.getY() - 1)) < 0.1 && t.end.x >= latch.getX() + 1.0,
                    "did not come down onto the floor inboard: " + t);
        });
    }

    /**
     * Climbing down a latched net (back, facing the net) from 1.5 blocks up the line ends standing on the rail's top at
     * the latch (the line is followed down to the ground, not left over the side); there the player, letting go of back,
     * stays.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void climbingDownEndsOnTheRailAtALatch(GameTestHelper h) {
        BlockPos latch = landLatch(h);
        RatlineNets.Net net = RatlineNets.netOf(h.getLevel(), latch);
        Point a = net.ringA(), b = net.ringB();
        double k = 1.5 / (b.y() - a.y());
        Vec3 start = new Vec3(a.x() + (b.x() - a.x()) * k, a.y() + 1.5, a.z() + (b.z() - a.z()) * k);
        double railTop = onTheRail(latch).y - 0.02;
        Player[] player = {null};
        boolean[] landed = {false};
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == 0) {
                player[0] = RatlineNetGameTests.playerInLevel(h, start, Direction.EAST.toYRot());
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) {
                return;
            }
            landed[0] |= p.onGround();
            p.zza = landed[0] ? 0f : -1f;
        });
        h.runAfterDelay(60, () -> {
            Player p = player[0];
            Constants.LOG.info(String.format(Locale.ROOT, "[RL6] down to the rail: start (%.2f, %.2f, %.2f), end (%.2f, %.2f, %.2f), rail top %.2f",
                    start.x, start.y, start.z, p.getX(), p.getY(), p.getZ(), railTop));
            h.assertTrue(landed[0] && p.onGround() && Math.abs(p.getY() - railTop) < 0.1,
                    "did not come down onto the rail's top: feet at y " + p.getY() + ", the rail's top at " + railTop);
            h.succeed();
        });
    }

    /** From the rail's top at a latch, forward towards the net (looking ahead) climbs it to the top. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160)
    public static void climbsFromTheRailTopWithForward(GameTestHelper h) {
        BlockPos latch = landLatch(h);
        RatlineNetGameTests.climb(h, 0, () -> RatlineNetGameTests.playerInLevel(h, onTheRail(latch), Direction.EAST.toYRot()),
                () -> RatlineNets.netOf(h.getLevel(), latch), v -> v, false, true, 120, "forward from the rail's top");
    }

    /** From the rail's top at a latch, jump alone climbs it to the top. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 160)
    public static void climbsFromTheRailTopWithJump(GameTestHelper h) {
        BlockPos latch = landLatch(h);
        RatlineNetGameTests.climb(h, 0, () -> RatlineNetGameTests.playerInLevel(h, onTheRail(latch), Direction.EAST.toYRot()),
                () -> RatlineNets.netOf(h.getLevel(), latch), v -> v, true, false, 120, "jump from the rail's top");
    }

    /** At a chainplate on the floor a player walks two blocks sideways along the deck. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void walksAroundAChainplateOnLand(GameTestHelper h) {
        BlockPos plate = landChainplate(h);
        Vec3 at = new Vec3(plate.getX() + 0.5, plate.getY() + 0.02, plate.getZ() + 0.5);
        landWalk(h, at, Input.walk(Direction.SOUTH, 0f, 2.2), "along the deck from a chainplate on land", t -> {
            h.assertTrue(t.end.z - t.start.z >= 2.0, "walked only " + (t.end.z - t.start.z) + " blocks: " + t);
            h.assertTrue(t.maxY <= plate.getY() + 0.7, "climbed the net instead: " + t);
        });
    }

    /** At a chainplate on the floor a player looking down walks inboard under the net. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void stepsInboardFromAChainplateOnLand(GameTestHelper h) {
        BlockPos plate = landChainplate(h);
        Vec3 at = new Vec3(plate.getX() + 0.5, plate.getY() + 0.02, plate.getZ() + 0.5);
        landWalk(h, at, Input.walk(Direction.EAST, LOOK_DOWN, 2.0), "inboard from a chainplate on land", t -> {
            h.assertTrue(t.end.x - t.start.x >= 1.9, "walked only " + (t.end.x - t.start.x) + " blocks inboard: " + t);
            h.assertTrue(t.maxY <= plate.getY() + 0.7, "climbed the net instead: " + t);
        });
    }

    /** Sneaking at a chainplate, forward towards the net walks on instead of climbing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100)
    public static void sneakingAtTheFootDoesNotClimb(GameTestHelper h) {
        BlockPos plate = landChainplate(h);
        Vec3 at = new Vec3(plate.getX() + 0.5, plate.getY() + 0.02, plate.getZ() + 0.5);
        landWalk(h, at, new Input(1f, 0f, false, true, new Vec3(1, 0, 0), 0f, 1.0), "sneaking at a chainplate on land", t -> {
            h.assertTrue(t.maxY <= plate.getY() + 0.7, "climbed the net while sneaking: " + t);
            h.assertTrue(t.end.x - t.start.x >= 0.9, "sneaking did not walk on: " + t);
        });
    }

    // ------------------------------------------------------------------ on a ship

    /**
     * The ship rig: a hull afloat with a log mast at x 13, z 10, a hoop on its west side at y {@link #SHIP_HOOP_Y}; with
     * {@code latch} a run of west-railed railings along the hull's west edge (x {@link #SHIP_RAIL_X}, z 9..13) and the
     * net from the latch at z {@link #SHIP_NET_Z}, else a chainplate on the deck at x {@link #SHIP_PLATE_X}. Assembled,
     * the net strung in the plot. Returns the fixture and the foot's plot position (the latch or the chainplate).
     */
    private record ShipFoot(Fixture fixture, BlockPos foot) {
    }

    private static ShipFoot shipFoot(GameTestHelper h, boolean latch, int size) {
        DryHullGameTests.basin(h, 0, size - 1, true);
        SailingGameTestsShips.openSky(h, size);
        BlockPos helmRel = DryHullGameTests.hull(h, 9, false);
        for (int y = SHIP_DECK_Y; y <= SHIP_HOOP_Y + 1; y++) {
            h.setBlock(new BlockPos(13, y, SHIP_NET_Z), Blocks.OAK_LOG);
        }
        BlockPos hoopRel = new BlockPos(12, SHIP_HOOP_Y, SHIP_NET_Z);
        h.setBlock(hoopRel, RatlineNetGameTests.hoop(Direction.WEST));
        BlockPos footRel = latch ? new BlockPos(SHIP_RAIL_X, SHIP_DECK_Y, SHIP_NET_Z) : new BlockPos(SHIP_PLATE_X, SHIP_DECK_Y, SHIP_NET_Z);
        if (latch) {
            for (int z = 9; z <= 13; z++) {
                h.setBlock(new BlockPos(SHIP_RAIL_X, SHIP_DECK_Y, z), RatlineSheetGameTests.railing(Direction.WEST));
            }
        } else {
            h.setBlock(footRel, RatlineNetGameTests.chainplate(AttachFace.FLOOR, Direction.EAST));
        }
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ServerLevel level = h.getLevel();
        BlockPos foot = RatlineNetGameTests.plot(f, helmRel, footRel);
        BlockPos hoop = RatlineNetGameTests.plot(f, helmRel, hoopRel);
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        String r;
        if (latch) {
            String first = RatlineNetGameTests.key(RatlineNetItem.useOnRailing(level, stack, foot, Direction.WEST, null));
            r = first.equals(RatlineNetItem.KEY_MARKED) ? RatlineNetGameTests.key(RatlineNetItem.use(level, stack, hoop, null)) : first;
            foot = foot.above();
        } else {
            r = RatlineNetGameTests.string(level, foot, hoop, stack);
        }
        h.assertTrue(r.equals(RatlineNetItem.KEY_STRUNG), "not strung in the plot: " + r);
        return new ShipFoot(f, foot);
    }

    /**
     * A walk at the foot of a net on a ship held at {@code speed} m/s along the world's x (0: a still ship): the walker is
     * followed in the plot; at the end it must still stand on the ship (feet in the hull's columns, on the deck or the
     * rail, on the ground), then {@code check}.
     */
    private static void shipWalk(GameTestHelper h, boolean latch, double speed, Input in, String what, Consumer<Track> check) {
        ShipFoot rig = shipFoot(h, latch, speed > 0 ? 40 : 24);
        ShipBody ship = rig.fixture().ship();
        BlockPos foot = rig.foot();
        Vec3 at = latch ? onTheRail(foot) : new Vec3(foot.getX() + 0.5, foot.getY() + 0.02, foot.getZ() + 0.5);
        Vector3d lin = new Vector3d(), ang = new Vector3d();
        Runnable drive = speed <= 0 ? () -> { } : () -> {
            ship.velocities(lin, ang);
            ship.addVelocity(new Vector3d(speed - lin.x, 0, -lin.z), new Vector3d(0, -ang.y, 0));
        };
        // the hull's columns in the plot: the helm stands on the deck at x 11, z 11 of the hull's 9..13
        BlockPos hull0 = rig.fixture().helmPlot().offset(-2, 0, -2);
        // the ship's actual speed over the walk, measured at the net's foot
        Vec3 footCentre = Vec3.atCenterOf(foot);
        Vec3[] footStart = {null};
        h.runAfterDelay(SETTLE, () -> footStart[0] = ship.toWorld(footCentre));
        walk(h, SETTLE, at, ship::toWorld, ship::toPlot, d -> RatlineNetAssist.rotate(ship.orientation(), d), in, drive, what, t -> {
            double moved = ship.toWorld(footCentre).subtract(footStart[0]).horizontalDistance() * 20 / (WALK_TICKS + 1);
            Constants.LOG.info(String.format(Locale.ROOT, "[RL6] %s: the ship moved at %.2f m/s", what, moved));
            // under way the water's drag between the drive's pushes keeps the mean a little under the set speed
            h.assertTrue(speed <= 0 ? moved < 0.5 : moved >= speed * 0.75, what + ": the ship moved at " + moved + " m/s");
            boolean onShip = t.end.x >= hull0.getX() - 0.3 && t.end.x <= hull0.getX() + 5.3
                    && t.end.z >= hull0.getZ() - 0.3 && t.end.z <= hull0.getZ() + 5.3 && t.end.y >= hull0.getY() - 0.1;
            h.assertTrue(onShip && t.onGround, what + ": fell off the ship: " + t + " (hull from " + hull0 + ")");
            check.accept(t);
        });
    }

    private static void shipAlongTheRail(GameTestHelper h, double speed, String what) {
        shipWalk(h, true, speed, Input.walk(Direction.SOUTH, 0f, 2.2), what, t -> {
            h.assertTrue(t.end.z - t.start.z >= 2.0, what + ": walked only " + (t.end.z - t.start.z) + " blocks along the rail: " + t);
            h.assertTrue(t.minY >= t.start.y - 0.15 && t.maxY <= t.start.y + 0.3, what + ": left the rail's top: " + t);
        });
    }

    private static void shipInboardFromTheLatch(GameTestHelper h, double speed, String what) {
        shipWalk(h, true, speed, Input.walk(Direction.EAST, LOOK_DOWN, 1.6), what, t -> {
            h.assertTrue(t.maxY <= t.start.y + 0.3, what + ": climbed the net instead: " + t);
            h.assertTrue(t.end.y < t.start.y - 1.0 && t.end.x >= t.start.x + 0.9, what + ": did not come down onto the deck inboard: " + t);
        });
    }

    private static void shipAroundTheChainplate(GameTestHelper h, double speed, String what) {
        shipWalk(h, false, speed, Input.walk(Direction.SOUTH, 0f, 2.2), what, t -> {
            h.assertTrue(t.end.z - t.start.z >= 2.0, what + ": walked only " + (t.end.z - t.start.z) + " blocks: " + t);
            h.assertTrue(t.maxY <= t.start.y + 0.7, what + ": climbed the net instead: " + t);
        });
    }

    private static void shipInboardFromTheChainplate(GameTestHelper h, double speed, String what) {
        shipWalk(h, false, speed, Input.walk(Direction.EAST, LOOK_DOWN, 2.0), what, t -> {
            h.assertTrue(t.end.x - t.start.x >= 1.9, what + ": walked only " + (t.end.x - t.start.x) + " blocks inboard: " + t);
            h.assertTrue(t.maxY <= t.start.y + 0.7, what + ": climbed the net instead: " + t);
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void walksAlongTheRailFromALatchOnAStillShip(GameTestHelper h) {
        shipAlongTheRail(h, 0, "along the rail from a latch on a still ship");
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void walksAlongTheRailFromALatchUnderWay(GameTestHelper h) {
        shipAlongTheRail(h, 4, "along the rail from a latch, ship at 4 m/s");
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void stepsInboardFromALatchOnAStillShip(GameTestHelper h) {
        shipInboardFromTheLatch(h, 0, "inboard from a latch on a still ship");
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void stepsInboardFromALatchUnderWay(GameTestHelper h) {
        shipInboardFromTheLatch(h, 4, "inboard from a latch, ship at 4 m/s");
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void walksAroundAChainplateOnAStillShip(GameTestHelper h) {
        shipAroundTheChainplate(h, 0, "along the deck from a chainplate on a still ship");
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void walksAroundAChainplateUnderWay(GameTestHelper h) {
        shipAroundTheChainplate(h, 4, "along the deck from a chainplate, ship at 4 m/s");
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void stepsInboardFromAChainplateOnAStillShip(GameTestHelper h) {
        shipInboardFromTheChainplate(h, 0, "inboard from a chainplate on a still ship");
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void stepsInboardFromAChainplateUnderWay(GameTestHelper h) {
        shipInboardFromTheChainplate(h, 4, "inboard from a chainplate, ship at 4 m/s");
    }

    /** From the rail's top at a latch on a ship at 4 m/s, forward towards the net still climbs (two blocks up within the walk). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void climbsFromTheRailTopUnderWay(GameTestHelper h) {
        String what = "forward from the rail's top, ship at 4 m/s";
        ShipFoot rig = shipFoot(h, true, 40);
        ShipBody ship = rig.fixture().ship();
        Vector3d lin = new Vector3d(), ang = new Vector3d();
        Runnable drive = () -> {
            ship.velocities(lin, ang);
            ship.addVelocity(new Vector3d(4 - lin.x, 0, -lin.z), new Vector3d(0, -ang.y, 0));
        };
        walk(h, SETTLE, onTheRail(rig.foot()), ship::toWorld, ship::toPlot, d -> RatlineNetAssist.rotate(ship.orientation(), d),
                Input.walk(Direction.EAST, 0f, 99), drive, what, t -> {
                    h.assertTrue(t.maxY >= t.start.y + 2.0, what + ": rose only " + (t.maxY - t.start.y) + " blocks: " + t);
                    h.assertTrue(t.climbing >= 15, what + ": only " + t.climbing + " ticks on the net: " + t);
                });
    }
}
