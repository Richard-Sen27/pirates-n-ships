package com.richardsenger.piratesnships.crew.hammock;

import com.mojang.authlib.GameProfile;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.hammock.PlayerSleepRules.Refusal;
import com.richardsenger.piratesnships.crew.morale.NightOutcome;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import com.richardsenger.piratesnships.ship.decor.SeaCotBlock;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

/**
 * SLP1: players sleep in hammocks and in the sea cot aboard (docs/design.md §7.1). The ship is the 5×4×5 hull of
 * {@link StationGameTests} resting on land (deck top at relative y = 9, the hold's floor at y = 6); a hammock hangs in
 * the hold at y = 7 between a fence post at (18, 7, 19) and the east wall, foot (19, 7, 19), head (20, 7, 19), facing
 * east, as in {@link HammockGameTests}. The sleeper is a real {@link ServerPlayer} in the level (vanilla's sleeping
 * list counts only those) with a connection that drops everything sent to it; the test ticks it each tick ({@code doTick}), as the
 * server's network tick does for a connected player.
 * <p>
 * The clock is shared by the whole level: every test that sets it runs in a batch of its own and leaves it at day.
 */
public final class PlayerSleepGameTests {

    private static final String BATCH = "pirates_n_ships_crew_slp1_";
    private static final long MIDNIGHT = 18000L;
    /** Ticks after setting the clock until the level's sky darkness follows ({@code updateSkyBrightness}). */
    private static final int DUSK = 3;

    private PlayerSleepGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PlayerSleepGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A survival server player that keeps its action bar lines. */
    private static final class Sleeper extends ServerPlayer {
        final List<Component> actionBar = new ArrayList<>();

        Sleeper(ServerLevel level, GameProfile profile, ClientInformation info) {
            super(level.getServer(), level, profile, info);
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            if (overlay) actionBar.add(message);
        }

        boolean told(String key) {
            for (Component c : actionBar) {
                if (c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) return true;
            }
            return false;
        }
    }

    /**
     * A sleeper standing at world position {@code at}, added to the level, ticked every test tick, and removed when
     * the test ends however it ends.
     */
    private static Sleeper sleeper(GameTestHelper h, Vec3 at) {
        ServerLevel level = h.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-sleeper");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        Sleeper p = new Sleeper(level, profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // A connection that goes nowhere and drops what is sent: Sable sends its payloads to every player tracking a
        // ship, and NeoForge refuses payloads on a connection that never negotiated its channels.
        new ServerGamePacketListenerImpl(level.getServer(), connection, p, cookie) {
            @Override
            public void send(Packet<?> packet) {
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            }
        };
        p.moveTo(at.x, at.y, at.z, 0f, 0f);
        // No gravity: doTick moves this player on the server, where it sinks through a ship's deck at about 0.08
        // blocks a tick (measured); a real player is moved by its client, which collides with the ship. The tests check
        // where the sleeper is put, not that fall.
        p.setNoGravity(true);
        level.addNewPlayer(p);
        removeWhenDone(h, p);
        h.onEachTick(() -> {
            if (!p.isRemoved()) p.doTick();
        });
        return p;
    }

    private static void remove(ServerLevel level, ServerPlayer p) {
        if (!p.isRemoved()) {
            p.stopRiding();
            level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
    }

    private static void removeWhenDone(GameTestHelper h, ServerPlayer p) {
        ServerLevel level = h.getLevel();
        testInfo(h).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) { }
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { remove(level, p); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { remove(level, p); }
            @Override public void testAddedForRerun(GameTestInfo old, GameTestInfo fresh, GameTestRunner runner) { remove(level, p); }
        });
    }

    private static volatile Field testInfoField;

    /** {@code GameTestHelper#testInfo} is private in 1.21.1 (same accessor as {@code ConfigOverrides}, {@code ShipTestCleanup}). */
    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            Field f = testInfoField;
            if (f == null) {
                for (Field candidate : GameTestHelper.class.getDeclaredFields()) {
                    if (candidate.getType() == GameTestInfo.class) {
                        candidate.setAccessible(true);
                        testInfoField = f = candidate;
                        break;
                    }
                }
            }
            if (f == null) throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
            return (GameTestInfo) f.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The test ship with the hammock in its hold (and {@code extra} blocks). */
    private static Fixture ship(GameTestHelper h, java.util.function.Consumer<GameTestHelper> extra) {
        return StationGameTests.ship(h, false, x -> {
            x.setBlock(new BlockPos(18, 7, 19), Blocks.OAK_FENCE);
            BlockState hammock = CrewContent.HAMMOCK.get().defaultBlockState().setValue(HammockBlock.FACING, Direction.EAST);
            x.setBlock(new BlockPos(19, 7, 19), hammock.setValue(HammockBlock.PART, BedPart.FOOT));
            x.setBlock(new BlockPos(20, 7, 19), hammock.setValue(HammockBlock.PART, BedPart.HEAD));
            extra.accept(x);
        });
    }

    /** The plot cell at relative (x, y, z): the ship rests on land, so plot = helm + offset (helm at (19, 9, 18)). */
    private static BlockPos plot(Fixture f, int x, int y, int z) {
        return f.helm().offset(x - 19, y - 9, z - 18);
    }

    private static Vec3 worldOf(Fixture f, Vec3 plot) {
        return f.ship().toWorld(plot);
    }

    /** A sleeper standing on the hold's floor beside the hammock. */
    private static Sleeper inTheHold(GameTestHelper h, Fixture f) {
        return sleeper(h, worldOf(f, Vec3.atBottomCenterOf(plot(f, 19, 6, 18))));
    }

    /** Uses the block at {@code pos} (absolute) empty-handed, as a right click does. */
    private static InteractionResult use(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockState state = h.getLevel().getBlockState(pos);
        return state.useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void setTimeOfDay(GameTestHelper h, long timeOfDay) {
        long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
        h.getLevel().setDayTime(today + timeOfDay);
    }

    /** The next morning, for the next batch. */
    private static void morning(GameTestHelper h) {
        long now = h.getLevel().getDayTime();
        h.getLevel().setDayTime((now / RestRules.DAY + 1) * RestRules.DAY + 1000L);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void assertAsleepOnSeat(GameTestHelper h, ServerPlayer p, BlockPos foot, BlockPos head, String when) {
        h.assertTrue(p.isSleeping(), when + ": not sleeping");
        h.assertTrue(p.getSleepingPos().equals(Optional.of(head)), when + ": sleeping position " + p.getSleepingPos() + ", not the head half " + head);
        h.assertTrue(p.getPose() == Pose.SLEEPING, when + ": pose " + p.getPose());
        h.assertTrue(p.getVehicle() instanceof HammockSeat seat && seat.forPlayer() && seat.foot().equals(foot),
                when + ": not on the bunk's seat: " + p.getVehicle());
    }

    /** The sleeper stands awake on its own feet, off the seat, the seat gone. */
    private static void assertUp(GameTestHelper h, ServerPlayer p, BlockPos foot, String when) {
        h.assertTrue(!p.isSleeping() && p.getSleepingPos().isEmpty(), when + ": still sleeping");
        h.assertTrue(p.getVehicle() == null, when + ": still riding " + p.getVehicle());
        h.assertTrue(p.getPose() != Pose.SLEEPING, when + ": still lying, pose " + p.getPose());
        h.assertTrue(HammockSeat.at(h.getLevel(), foot).isEmpty(), when + ": the seat stayed");
    }

    // ------------------------------------------------------------------ hammock on a ship

    /**
     * At night a player uses the hold's hammock: it sleeps (sleeping position the head half, sleeping pose, on the
     * hammock's seat at the head half), sets an unforced respawn point there, stays asleep while it is ticked (vanilla's
     * bed check accepts the hammock), and the hammock is no bunk for the crew: a crew member finds none at nightfall.
     * Sneaking gets it off the seat and wakes it on the hold's floor beside the hammock.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = BATCH + "hammock")
    public static void hammockAboardSleepsAndSneakingWakes(GameTestHelper h) {
        Fixture f = ship(h, x -> { });
        ServerLevel level = h.getLevel();
        BlockPos foot = plot(f, 19, 7, 19), head = plot(f, 20, 7, 19);
        Sleeper p = inTheHold(h, f);
        CrewMember[] crew = new CrewMember[1];
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            h.assertTrue(use(h, p, head).consumesAction(), "the hammock did nothing");
            assertAsleepOnSeat(h, p, foot, head, "lying down");
            Vec3 lying = worldOf(f, PlayerSleep.lyingSpot(head, CrewContent.HAMMOCK.get()));
            h.assertTrue(p.position().distanceTo(lying) < 0.25, "feet at " + p.position() + ", not on the hammock at " + lying);
            h.assertTrue(head.equals(p.getRespawnPosition()) && !p.isRespawnForced(),
                    "respawn " + p.getRespawnPosition() + " forced " + p.isRespawnForced() + ", expected the head half " + head);
            h.assertTrue(!ShipBunks.isFree(level, foot) && ShipBunks.freeHammocks(level, f.ship()).isEmpty(), "the hammock still counts as free");
        });
        h.runAfterDelay(DUSK + 6, () -> {
            assertAsleepOnSeat(h, p, foot, head, "six ticks later");
            CrewMember c = StationContent.CREW_MEMBER.get().create(level);
            if (c == null) throw new AssertionError("no crew member");
            Vec3 deck = worldOf(f, Vec3.atBottomCenterOf(plot(f, 20, 9, 20)));
            c.moveTo(deck.x, deck.y, deck.z, 0, 0);
            c.setNoAi(true);
            level.addFreshEntity(c);
            crew[0] = c;
            CrewRest.turnIn(level, f.ship());
            h.assertTrue(c.rest() == null && c.nightOutcome() == NightOutcome.NO_HAMMOCK,
                    "a crew member took the player's hammock: " + c.rest() + ", " + c.nightOutcome());
            p.setShiftKeyDown(true);
        });
        h.runAfterDelay(DUSK + 10, () -> {
            assertUp(h, p, foot, "after sneaking");
            Vec3 bunk = worldOf(f, Vec3.atCenterOf(head));
            h.assertTrue(horizontal(p.position(), bunk) <= 2.6, "woke " + horizontal(p.position(), bunk) + " blocks from the hammock: " + p.position());
            // measured in the ship's frame: the hold's floor is plot y = 6 (the resting hull may lean a little)
            Vec3 local = f.ship().toPlot(p.position());
            double floor = plot(f, 19, 6, 19).getY();
            h.assertTrue(local.y > floor - 0.1 && local.y < floor + 0.6, "woke at plot y " + local.y + " (" + p.position()
                    + "), not on the hold's floor at plot y " + floor + "; ship " + f.ship().orientation());
            h.assertTrue(ShipBunks.isFree(level, foot), "the hammock is still taken");
            crew[0].discard();
            morning(h);
            h.succeed();
        });
    }

    /**
     * The ship is moved and turned under the sleeper: it lies at the hammock's new world position; "Leave Bed" (vanilla's
     * stopSleepInBed, as the client's button sends it) gets it up beside the hammock where the ship is now.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = BATCH + "moving")
    public static void sleeperMovesWithTheShip(GameTestHelper h) {
        Fixture f = ship(h, x -> { });
        BlockPos foot = plot(f, 19, 7, 19), head = plot(f, 20, 7, 19);
        Sleeper p = inTheHold(h, f);
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            h.assertTrue(PlayerSleep.use(p, foot, h.getLevel().getBlockState(foot)) == Refusal.NONE, "refused: " + p.actionBar);
            Vec3 helm = Vec3.atBottomCenterOf(f.helm());
            f.ship().placeAt(helm, f.ship().toWorld(helm).add(-3, 0.5, 2), new Quaterniond().rotateY(Math.toRadians(90)));
        });
        h.runAfterDelay(DUSK + 4, () -> {
            assertAsleepOnSeat(h, p, foot, head, "on the moved ship");
            Vec3 lying = worldOf(f, PlayerSleep.lyingSpot(head, CrewContent.HAMMOCK.get()));
            h.assertTrue(p.position().distanceTo(lying) < 0.35, "feet at " + p.position() + ", the hammock is now at " + lying);
            p.stopSleepInBed(false, true);
        });
        h.runAfterDelay(DUSK + 7, () -> {
            assertUp(h, p, foot, "after Leave Bed");
            Vec3 bunk = worldOf(f, Vec3.atCenterOf(head));
            h.assertTrue(horizontal(p.position(), bunk) <= 2.6, "woke " + horizontal(p.position(), bunk) + " blocks from the moved hammock: " + p.position());
            h.assertTrue(head.equals(p.getRespawnPosition()), "respawn " + p.getRespawnPosition());
            morning(h);
            h.succeed();
        });
    }

    /** A crew member lies in the hammock: the player is told so and stays awake; the crew member keeps it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "crew")
    public static void aCrewMemberInTheHammockRefusesThePlayer(GameTestHelper h) {
        Fixture f = ship(h, x -> { });
        ServerLevel level = h.getLevel();
        BlockPos foot = plot(f, 19, 7, 19);
        Sleeper p = inTheHold(h, f);
        CrewMember c = StationContent.CREW_MEMBER.get().create(level);
        if (c == null) throw new AssertionError("no crew member");
        Vec3 deck = worldOf(f, Vec3.atBottomCenterOf(plot(f, 20, 9, 20)));
        c.moveTo(deck.x, deck.y, deck.z, 0, 0);
        c.setNoAi(true);
        level.addFreshEntity(c);
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            if (c.rest() == null) {
                h.assertTrue(CrewRest.lieDown(level, c, f.ship().id(), foot), "the crew member did not lie down");
            }
            h.assertTrue(c.getVehicle() instanceof HammockSeat, "the crew member is not in the hammock");
            use(h, p, foot);
            h.assertTrue(!p.isSleeping() && p.getVehicle() == null, "the player lay down in a crew member's hammock");
            h.assertTrue(p.told(PlayerSleep.KEY_CREW_IN_IT), "the player was not told why: " + p.actionBar);
            h.assertTrue(c.getVehicle() instanceof HammockSeat seat && !seat.forPlayer(), "the crew member lost its hammock");
            CrewRest.getUp(c, false);
            c.discard();
            morning(h);
            h.succeed();
        });
    }

    /** By day the hammock refuses ("You can only sleep at night") but, as a bed does, sets the respawn point. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "day")
    public static void daytimeRefuses(GameTestHelper h) {
        Fixture f = ship(h, x -> { });
        BlockPos foot = plot(f, 19, 7, 19), head = plot(f, 20, 7, 19);
        Sleeper p = inTheHold(h, f);
        setTimeOfDay(h, 6000L);
        h.runAfterDelay(DUSK, () -> {
            use(h, p, foot);
            h.assertTrue(!p.isSleeping() && p.getVehicle() == null, "slept by day");
            h.assertTrue(p.told("block.minecraft.bed.no_sleep"), "not told it is day: " + p.actionBar);
            h.assertTrue(HammockSeat.at(h.getLevel(), foot).isEmpty(), "a seat was left");
            h.assertTrue(head.equals(p.getRespawnPosition()), "respawn not set by day: " + p.getRespawnPosition());
            h.succeed();
        });
    }

    /** A zombie on the deck above: "You may not rest now; there are monsters nearby". */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "monsters")
    public static void monstersNearbyRefuse(GameTestHelper h) {
        Fixture f = ship(h, x -> { });
        BlockPos foot = plot(f, 19, 7, 19);
        Sleeper p = inTheHold(h, f);
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        if (z == null) throw new AssertionError("no zombie");
        Vec3 deck = worldOf(f, Vec3.atBottomCenterOf(plot(f, 20, 9, 20)));
        z.moveTo(deck.x, deck.y, deck.z, 0, 0);
        z.setNoAi(true);
        h.getLevel().addFreshEntity(z);
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            use(h, p, foot);
            h.assertTrue(!p.isSleeping(), "slept with a zombie on deck");
            h.assertTrue(p.told("block.minecraft.bed.not_safe"), "not told about the monsters: " + p.actionBar);
            z.discard();
            morning(h);
            h.succeed();
        });
    }

    /**
     * Vanilla's sleeping list counts the sleeper: alone in the level and asleep long enough (100 ticks), the night is
     * skipped and the sleeper wakes, off the seat beside the hammock.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 220, batch = BATCH + "night_skip")
    public static void theSleeperSkipsTheNight(GameTestHelper h) {
        Fixture f = ship(h, x -> { });
        ServerLevel level = h.getLevel();
        BlockPos foot = plot(f, 19, 7, 19), head = plot(f, 20, 7, 19);
        Sleeper p = inTheHold(h, f);
        long[] night = new long[1];
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            h.assertTrue(level.players().size() == 1, "other players in the level: " + level.players());
            h.assertTrue(PlayerSleep.use(p, foot, level.getBlockState(foot)) == Refusal.NONE, "refused: " + p.actionBar);
            night[0] = level.getDayTime();
        });
        h.runAfterDelay(DUSK + 50, () -> {
            assertAsleepOnSeat(h, p, foot, head, "halfway");
            h.assertTrue(level.getDayTime() < night[0] + 100, "the night was skipped too early");
        });
        h.runAfterDelay(DUSK + 130, () -> {
            h.assertTrue(level.getDayTime() >= (night[0] / RestRules.DAY + 1) * RestRules.DAY,
                    "the night was not skipped: day time " + level.getDayTime() + " from " + night[0]);
            assertUp(h, p, foot, "after the night");
            Vec3 bunk = worldOf(f, Vec3.atCenterOf(head));
            h.assertTrue(horizontal(p.position(), bunk) <= 2.6, "woke " + horizontal(p.position(), bunk) + " blocks from the hammock");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ hammock on land

    /**
     * On land the hammock works the same way on a seat in the world; the respawn point is the head half, and the
     * hammock finds a spot beside it to respawn at, as a bed does.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 100, batch = BATCH + "land")
    public static void hammockOnLand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(new BlockPos(4, 2, 6), Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.OAK_FENCE);
        BlockState hammock = CrewContent.HAMMOCK.get().defaultBlockState().setValue(HammockBlock.FACING, Direction.NORTH);
        h.setBlock(new BlockPos(4, 2, 5), hammock.setValue(HammockBlock.PART, BedPart.FOOT));
        h.setBlock(new BlockPos(4, 2, 4), hammock.setValue(HammockBlock.PART, BedPart.HEAD));
        BlockPos foot = h.absolutePos(new BlockPos(4, 2, 5)), head = h.absolutePos(new BlockPos(4, 2, 4));
        Sleeper p = sleeper(h, h.absoluteVec(new Vec3(5.5, 2, 5.5)));
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            use(h, p, foot);
            assertAsleepOnSeat(h, p, foot, head, "on land");
            Vec3 lying = PlayerSleep.lyingSpot(head, CrewContent.HAMMOCK.get());
            h.assertTrue(p.position().distanceTo(lying) < 0.25, "feet at " + p.position() + ", not on the hammock at " + lying);
            h.assertTrue(head.equals(p.getRespawnPosition()) && !p.isRespawnForced(), "respawn " + p.getRespawnPosition());
            var spawn = CrewContent.HAMMOCK.get().getRespawnPosition(level.getBlockState(head), EntityType.PLAYER, level, head, 0f);
            h.assertTrue(spawn.isPresent() && horizontal(spawn.get().position(), Vec3.atCenterOf(head)) <= 2.6,
                    "no respawn spot near the hammock: " + spawn);
        });
        h.runAfterDelay(DUSK + 4, () -> {
            assertAsleepOnSeat(h, p, foot, head, "four ticks later");
            p.stopSleepInBed(false, true);
        });
        h.runAfterDelay(DUSK + 7, () -> {
            assertUp(h, p, foot, "after Leave Bed");
            // the hammock hangs a block above the floor: the sleeper gets up on the floor beside it, not on the canvas
            BlockPos at = p.blockPosition();
            h.assertTrue(horizontal(p.position(), Vec3.atCenterOf(head)) <= 2.6 && !at.equals(head) && !at.equals(foot)
                    && p.getY() < head.getY() + 0.05, "woke at " + p.position() + ", not on the floor beside the hammock");
            morning(h);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the sea cot aboard

    /** A sea cot on the deck: foot (20, 9, 20), head (21, 9, 20), facing east. */
    private static void cotOnDeck(GameTestHelper h) {
        BlockState cot = ShipDecor.SEA_COT.get().defaultBlockState().setValue(SeaCotBlock.FACING, Direction.EAST);
        h.setBlock(new BlockPos(20, 9, 20), cot.setValue(SeaCotBlock.PART, BedPart.FOOT));
        h.setBlock(new BlockPos(21, 9, 20), cot.setValue(SeaCotBlock.PART, BedPart.HEAD));
    }

    /**
     * The sea cot on an assembled ship: the player sleeps on a seat at the head half, the cot is occupied; "Leave Bed"
     * gets it up on the deck beside the cot, which is free again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "cot")
    public static void seaCotAboardSleeps(GameTestHelper h) {
        Fixture f = ship(h, PlayerSleepGameTests::cotOnDeck);
        ServerLevel level = h.getLevel();
        BlockPos foot = plot(f, 20, 9, 20), head = plot(f, 21, 9, 20);
        h.assertTrue(level.getBlockState(head).getBlock() instanceof SeaCotBlock, "no cot on the ship: " + level.getBlockState(head));
        Sleeper p = sleeper(h, worldOf(f, Vec3.atBottomCenterOf(plot(f, 19, 9, 21))));
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            use(h, p, foot);
            h.assertTrue(p.isSleeping(), "not asleep in the cot: " + p.actionBar);
            assertAsleepOnSeat(h, p, foot, head, "in the cot");
            Vec3 lying = worldOf(f, PlayerSleep.lyingSpot(head, ShipDecor.SEA_COT.get()));
            h.assertTrue(p.position().distanceTo(lying) < 0.25, "feet at " + p.position() + ", not in the cot at " + lying);
            h.assertTrue(level.getBlockState(head).getValue(BedBlock.OCCUPIED), "the cot is not occupied");
            h.assertTrue(head.equals(p.getRespawnPosition()), "respawn " + p.getRespawnPosition());
        });
        h.runAfterDelay(DUSK + 4, () -> {
            assertAsleepOnSeat(h, p, foot, head, "four ticks later");
            p.stopSleepInBed(false, true);
        });
        h.runAfterDelay(DUSK + 7, () -> {
            assertUp(h, p, foot, "after Leave Bed");
            double deck = worldOf(f, Vec3.atBottomCenterOf(head)).y;
            h.assertTrue(horizontal(p.position(), worldOf(f, Vec3.atCenterOf(head))) <= 2.6 && Math.abs(p.getY() - deck) < 0.3,
                    "woke at " + p.position() + ", not on the deck beside the cot");
            h.assertTrue(!level.getBlockState(head).getValue(BedBlock.OCCUPIED) && !level.getBlockState(foot).getValue(BedBlock.OCCUPIED),
                    "the cot is still occupied");
            morning(h);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ toggles

    /** {@code crew.hammock.player_sleep = false}: the hammock is crew-only again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = "pirates_n_ships_config_crew_hammock_player_sleep")
    public static void playerSleepOffKeepsHammocksForTheCrew(GameTestHelper h) {
        ConfigOverrides.during(h, HammockConfig.PLAYER_SLEEP, false);
        Fixture f = ship(h, x -> { });
        BlockPos foot = plot(f, 19, 7, 19);
        Sleeper p = inTheHold(h, f);
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            use(h, p, foot);
            h.assertTrue(!p.isSleeping() && p.getVehicle() == null, "slept with player_sleep off");
            h.assertTrue(p.told(HammockBlock.KEY_CREW_ONLY), "not told hammocks are for the crew: " + p.actionBar);
            morning(h);
            h.succeed();
        });
    }

    /** {@code ship_decor.sea_cot_sleeping_aboard = false}: the cot on a ship is for show, as before SLP1. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = "pirates_n_ships_config_ship_decor_sea_cot_aboard")
    public static void seaCotAboardOffIsForShow(GameTestHelper h) {
        ConfigOverrides.during(h, DecorConfig.SEA_COT_SLEEPING_ABOARD, false);
        Fixture f = ship(h, PlayerSleepGameTests::cotOnDeck);
        BlockPos foot = plot(f, 20, 9, 20);
        Sleeper p = sleeper(h, worldOf(f, Vec3.atBottomCenterOf(plot(f, 19, 9, 21))));
        setTimeOfDay(h, MIDNIGHT);
        h.runAfterDelay(DUSK, () -> {
            use(h, p, foot);
            h.assertTrue(!p.isSleeping() && p.getVehicle() == null, "slept in the cot aboard with the toggle off");
            h.assertTrue(p.told(SeaCotBlock.KEY_ON_SHIP), "not told the cot is for show aboard: " + p.actionBar);
            morning(h);
            h.succeed();
        });
    }
}
