package com.richardsenger.piratesnships.station.winch;

import com.mojang.authlib.GameProfile;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.OrderHints;
import com.richardsenger.piratesnships.station.StationCommands;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Q5 (the playtest report "0 of 0 crew carry it out" and "this ship has no sails" next to a big sail): the captain's
 * sequence through the real command handlers and the whistle's server handler, on a ship built the way players build
 * them: a 7×28 hull resting on land, the helm at the stern, the winch beside it and, 14 blocks forward, a log mast
 * through two 9-long yards 6 blocks apart. Nothing scans the ship's sailing state by hand: the sails are found the
 * way the game finds them. Every test runs in a batch of its own, since the order command reaches crew within
 * {@code order_radius} (64 blocks) and the tests of one batch run side by side.
 */
public final class WinchOrderGameTests {

    private static final int STEP = 10;
    private static final int MARGIN = 5;
    /** The default claim interval of the job board. */
    private static final int CLAIM = 20;
    private static final String BATCH = "pirates_n_ships_config_station_q5_";

    private WinchOrderGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(WinchOrderGameTests.class);
    }

    // ------------------------------------------------------------------ fixture

    /** The assembled ship: its body and the plot positions of winch and helm. */
    record Ship(ShipBody body, BlockPos winch, BlockPos helm) { }

    /** What stands on the deck amidships. */
    enum Rig { SAIL, BLOCKED_MAST, NONE }

    static final BlockPos HELM = new BlockPos(19, 9, 8);
    static final BlockPos WINCH = new BlockPos(18, 9, 10);
    static final BlockPos DECK = new BlockPos(20, 9, 12);
    static final int MAST_X = 19;
    static final int MAST_Z = 22;
    static final int LOWER_Y = 11;
    static final int UPPER_Y = 17;
    /** Where {@link Rig#BLOCKED_MAST} has a plank in the mast: 3 blocks below the upper yard. */
    static final int PLANK_Y = 14;

    /** The long hull on land with the sail rig and the winch, assembled. */
    static Ship ship(GameTestHelper h) {
        return ship(h, false, Rig.SAIL);
    }

    /** The long hull in water (afloat) or on land (resting on stone) with {@code rig} on deck, assembled. */
    static Ship ship(GameTestHelper h, boolean water, Rig rig) {
        StationGameTests.basin(h, water);
        SailingGameTestsShips.openSky(h, 40);
        for (int x = 16; x <= 22; x++) {
            for (int z = 6; z <= 33; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == 16 || x == 22 || z == 6 || z == 33;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        h.setBlock(HELM, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(WINCH, SailingBlocks.SAIL_WINCH.get());
        if (rig != Rig.NONE) {
            rig(rig, e -> h.setBlock(e.getKey(), e.getValue()));
        }
        AssemblyResult r = ShipTestCleanup.assemble(h, HELM);
        if (r.shipId() == null) throw new AssertionError("assembly failed: " + r);
        ShipBody body = SableShips.byId(h.getLevel(), r.shipId());
        if (body == null) throw new AssertionError("no ship after assembly");
        return new Ship(body, StationGameTests.find(h, body, SailingBlocks.SAIL_WINCH.get()), StationGameTests.find(h, body, AssemblyContent.HELM.get()));
    }

    /** Plot position on {@code s} of the relative position {@code rel} the fixture built it at. */
    static BlockPos plot(Ship s, BlockPos rel) {
        return s.helm().offset(rel.subtract(HELM));
    }

    /** The mast (logs) and the two yards along x, bottom up: relative positions and states. */
    static void rig(Rig rig, Consumer<Map.Entry<BlockPos, BlockState>> place) {
        for (int y = 9; y <= UPPER_Y + 1; y++) {
            if (y == LOWER_Y || y == UPPER_Y) {
                for (int x = MAST_X - 4; x <= MAST_X + 4; x++) {
                    place.accept(Map.entry(new BlockPos(x, y, MAST_Z),
                            SailingBlocks.YARD.get().defaultBlockState().setValue(YardBlock.AXIS, Direction.Axis.X)));
                }
            } else {
                BlockState mast = rig == Rig.BLOCKED_MAST && y == PLANK_Y ? Blocks.OAK_PLANKS.defaultBlockState()
                        : Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
                place.accept(Map.entry(new BlockPos(MAST_X, y, MAST_Z), mast));
            }
        }
    }

    /** The plot position of the upper yard's middle block (the sail's head). */
    static BlockPos head(Ship s) {
        return plot(s, new BlockPos(MAST_X, UPPER_Y, MAST_Z));
    }

    /** The trim on the sail's head. */
    static SailTrim headTrim(GameTestHelper h, Ship s) {
        BlockState state = h.getLevel().getBlockState(head(s));
        if (!(state.getBlock() instanceof YardBlock)) throw new AssertionError("no yard at the head " + head(s) + ": " + state);
        return state.getValue(YardBlock.TRIM);
    }

    /** A mock captain, whistle in hand, that keeps the lines it is shown. */
    static final class Captain extends Player {
        final List<Component> shown = new ArrayList<>();

        Captain(ServerLevel level) {
            super(level, BlockPos.ZERO, 0f, new GameProfile(UUID.randomUUID(), "q5-captain"));
            setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            shown.add(message);
        }
    }

    /** The captain standing on the deck of {@code s} above the relative deck cell {@code relDeck}. */
    static Captain captain(GameTestHelper h, Ship s, BlockPos relDeck) {
        Captain p = new Captain(h.getLevel());
        Vec3 at = s.body().toWorld(Vec3.atBottomCenterOf(plot(s, relDeck)));
        p.moveTo(at.x, at.y, at.z);
        return p;
    }

    /** Runs {@code command} as {@code p} (its entity, position and rotation, permission 4); returns the feedback. */
    static List<Component> run(GameTestHelper h, Player p, String command) {
        List<Component> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component component) {
                out.add(component);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack().withSource(capture)
                .withEntity(p).withLevel(h.getLevel()).withPosition(p.position()).withRotation(p.getRotationVector()).withPermission(4);
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        return out;
    }

    static String keys(List<Component> lines) {
        return lines.stream().map(WinchOrderGameTests::describe).collect(Collectors.joining(" | "));
    }

    private static String describe(Component c) {
        if (c.getContents() instanceof TranslatableContents t) {
            List<String> args = new ArrayList<>();
            for (Object a : t.getArgs()) args.add(a instanceof Component ac ? describe(ac) : String.valueOf(a));
            return t.getKey() + args;
        }
        return c.getString();
    }

    static @Nullable Component find(List<Component> lines, String key) {
        return lines.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)).findFirst().orElse(null);
    }

    static Object arg(Component c, int i) {
        return ((TranslatableContents) c.getContents()).getArgs()[i];
    }

    static String key(Object c) {
        return c instanceof Component comp && comp.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    /** Asserts the "Order %s: %s of %s crew carry it out" line with these counts. */
    static void assertOrdered(GameTestHelper h, List<Component> feedback, int started, int addressed) {
        Component c = find(feedback, StationCommands.KEY_ORDERED);
        h.assertTrue(c != null, "no order feedback: " + keys(feedback));
        h.assertTrue(arg(c, 1).equals(started) && arg(c, 2).equals(addressed),
                "expected " + started + " of " + addressed + " crew: " + keys(feedback));
    }

    /** {@code /pirates crew spawn} as the captain; returns the new crew member (the one at the captain's feet). */
    static CrewMember spawn(GameTestHelper h, Player p) {
        List<Component> out = run(h, p, "pirates crew spawn");
        List<CrewMember> crew = h.getLevel().getEntitiesOfClass(CrewMember.class, new AABB(p.blockPosition()).inflate(2));
        if (crew.size() != 1) throw new AssertionError("expected one crew member at the captain, found " + crew.size() + ": " + keys(out));
        return crew.get(0);
    }

    /** {@code /pirates crew assign <crew> <winch, world position>} as the captain. */
    static void assignToWinch(GameTestHelper h, Ship s, Player p, CrewMember c) {
        BlockPos w = BlockPos.containing(s.body().toWorld(Vec3.atCenterOf(s.winch())));
        List<Component> out = run(h, p, "pirates crew assign " + c.getStringUUID() + " " + w.getX() + " " + w.getY() + " " + w.getZ());
        h.assertTrue(c.assignment() != null && c.assignment().pos().equals(s.winch()), "assign failed: " + keys(out));
    }

    // ------------------------------------------------------------------ the reported sequence

    /**
     * The report: spawn a crew member where the captain stands on deck, assign it to the winch, order hoist, all with
     * the commands and in the tick the ship was assembled; it carries the order out ("1 of 1") and the sail is full
     * after the work time.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "assigned")
    public static void assignedCrewHoistsRightAfterAssembly(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        Ship s = ship(h);
        Captain p = captain(h, s, DECK);
        CrewMember c = spawn(h, p);
        assignToWinch(h, s, p, c);
        assertOrdered(h, run(h, p, "pirates crew order hoist"), 1, 1);
        h.runAfterDelay(2 * STEP + MARGIN, () -> {
            h.assertTrue(headTrim(h, s) == SailTrim.FULL, "sail not hoisted: " + headTrim(h, s));
            c.discard();
            h.succeed();
        });
    }

    /** The same sequence on a ship afloat, three seconds after assembly (risen, rocking). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "afloat")
    public static void assignedCrewHoistsOnAFloatingShip(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        Ship s = ship(h, true, Rig.SAIL);
        h.runAfterDelay(60, () -> {
            Captain p = captain(h, s, DECK);
            CrewMember c = spawn(h, p);
            assignToWinch(h, s, p, c);
            assertOrdered(h, run(h, p, "pirates crew order hoist"), 1, 1);
            h.runAfterDelay(2 * STEP + MARGIN, () -> {
                h.assertTrue(headTrim(h, s) == SailTrim.FULL, "sail not hoisted: " + headTrim(h, s));
                c.discard();
                h.succeed();
            });
        });
    }

    /** Mast and yards built on the ship after it was assembled and its sailing state exists are found by the order. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "rigafter")
    public static void sailsBuiltAfterAssemblyAreFound(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        Ship s = ship(h, false, Rig.NONE);
        h.runAfterDelay(12, () -> {
            SailingRuntime rt = SailingRuntimes.get(h.getLevel(), s.body().id());
            h.assertTrue(rt != null && rt.sailCount() == 0, "the scan did not create the sailing state of the bare ship");
            rig(Rig.SAIL, e -> h.getLevel().setBlock(plot(s, e.getKey()), e.getValue(), Block.UPDATE_ALL));
            h.assertTrue(rt.sailCount() == 1, "the sailing state did not pick up the new sail: " + rt.sailCount());
            Captain p = captain(h, s, DECK);
            CrewMember c = spawn(h, p);
            assignToWinch(h, s, p, c);
            assertOrdered(h, run(h, p, "pirates crew order hoist"), 1, 1);
            h.runAfterDelay(2 * STEP + MARGIN, () -> {
                h.assertTrue(headTrim(h, s) == SailTrim.FULL, "sail not hoisted: " + headTrim(h, s));
                c.discard();
                h.succeed();
            });
        });
    }

    /**
     * A sailing state that missed block changes (the yards were written without the chunk's block-change path, so
     * neither it nor the yard cloth heard of them) still finds the sail before the crew says "no sails": the winch reads
     * the rigging again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "stale")
    public static void aStaleSailingStateIsReadAgainBeforeNoSails(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        Ship s = ship(h, false, Rig.NONE);
        ServerLevel level = h.getLevel();
        SailingRuntime rt = SailingRuntimes.getOrCreate(s.body());
        h.assertTrue(rt != null && rt.sailCount() == 0, "bare ship has sails");
        // place the rig as planks through the normal path (Sable's plot bounds grow), then swap the yards in silently
        rig(Rig.SAIL, e -> level.setBlock(plot(s, e.getKey()), e.getValue().getBlock() instanceof YardBlock
                ? Blocks.OAK_PLANKS.defaultBlockState() : e.getValue(), Block.UPDATE_ALL));
        rig(Rig.SAIL, e -> {
            if (e.getValue().getBlock() instanceof YardBlock) {
                BlockPos p = plot(s, e.getKey());
                LevelChunk chunk = level.getChunkAt(p);
                chunk.getSection(chunk.getSectionIndex(p.getY())).setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15, e.getValue());
            }
        });
        h.assertTrue(level.getBlockState(head(s)).getBlock() instanceof YardBlock, "the silent swap failed");
        h.assertTrue(rt.sailCount() == 0, "the sailing state heard of the silent yards; the test proves nothing");
        Captain p = captain(h, s, DECK);
        CrewMember c = spawn(h, p);
        assignToWinch(h, s, p, c);
        assertOrdered(h, run(h, p, "pirates crew order hoist"), 1, 1);
        h.assertTrue(rt.sailCount() == 1, "the winch did not read the rigging again: " + rt.sailCount());
        h.runAfterDelay(2 * STEP + MARGIN, () -> {
            h.assertTrue(headTrim(h, s) == SailTrim.FULL, "sail not hoisted: " + headTrim(h, s));
            c.discard();
            h.succeed();
        });
    }

    /**
     * An unassigned crew member on deck and the command "hoist": nobody is at a station yet ("0 of 0"), the winch
     * becomes an open job (CR1) and the crew member claims it within one claim interval and hoists the sail.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "freecommand")
    public static void freeCrewTakeTheCommandOrder(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        Ship s = ship(h);
        Captain p = captain(h, s, DECK);
        CrewMember c = spawn(h, p);
        c.setNoAi(true); // stays on deck until the board's pass
        List<Component> out = run(h, p, "pirates crew order hoist");
        assertOrdered(h, out, 0, 0);
        h.assertTrue(find(out, JobBoard.KEY_POSTED) != null, "the winch was not posted as a job: " + keys(out));
        h.assertTrue(find(out, JobBoard.KEY_NO_FREE_HANDS) == null, "a free hand stands on deck, yet 'no free hands': " + keys(out));
        h.runAfterDelay(CLAIM + 3, () -> h.assertTrue(c.assignment() != null && c.assignment().pos().equals(s.winch()),
                "the free crew member did not claim the winch: " + c.assignment()));
        h.runAfterDelay(CLAIM + 2 * STEP + MARGIN, () -> {
            h.assertTrue(headTrim(h, s) == SailTrim.FULL, "sail not hoisted: " + headTrim(h, s));
            c.discard();
            h.succeed();
        });
    }

    /** The same with the whistle's "Hoist sails" right after spawning: one job opens and the sail goes up. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "freewhistle")
    public static void freeCrewTakeTheWhistleOrder(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, STEP);
        Ship s = ship(h);
        Captain p = captain(h, s, DECK);
        CrewMember c = spawn(h, p);
        c.setNoAi(true);
        h.assertTrue(CaptainsWhistleItem.shipOf(h.getLevel(), c) != null, "the fresh crew member is on no ship");
        WhistleOrders.Result r = WhistleOrders.handle(p, WhistleOrder.HOIST.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.ISSUED && r.crew() == 0 && r.jobs() == 1, "hoist: " + r + " " + keys(p.shown));
        h.assertTrue(find(p.shown, JobBoard.KEY_ORDER_POSTED) != null, "the captain did not hear of the open job: " + keys(p.shown));
        h.runAfterDelay(CLAIM + 2 * STEP + MARGIN, () -> {
            h.assertTrue(c.assignment() != null, "nobody claimed the winch");
            h.assertTrue(headTrim(h, s) == SailTrim.FULL, "sail not hoisted: " + headTrim(h, s));
            c.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ messages that say what is missing

    /**
     * A plank in the mast between the yards: no sail (rule F5a). The order says so with the reason (the plank, 3 blocks
     * below the upper yard), the rigging report names it, and the crew member at the winch answers with it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "blocked")
    public static void noSailsNamesTheBlockInTheMast(GameTestHelper h) {
        Ship s = ship(h, false, Rig.BLOCKED_MAST);
        Captain p = captain(h, s, DECK);
        List<Component> out = run(h, p, "pirates crew order hoist");
        assertOrdered(h, out, 0, 0);
        Component hint = find(out, OrderHints.KEY_NO_SAILS);
        h.assertTrue(hint != null, "no 'no sails' hint: " + keys(out));
        h.assertTrue(key(arg(hint, 0)).equals("message.pirates_n_ships.rigging.why.blocked"), "wrong reason: " + keys(out));
        Component why = (Component) arg(hint, 0);
        h.assertTrue(arg(why, 2).equals(UPPER_Y - PLANK_Y), "wrong distance to the plank: " + keys(out));
        Component winch = com.richardsenger.piratesnships.sailing.block.SailWinchBlock.use(h.getLevel(), s.winch());
        h.assertTrue(key(winch).equals(com.richardsenger.piratesnships.sailing.block.SailWinchBlock.KEY_NO_SAILS_WHY)
                && key(arg(winch, 0)).equals("message.pirates_n_ships.rigging.why.blocked"), "the winch's answer: " + describe(winch));
        RiggingReport report = RiggingReport.of(h.getLevel(), s.body());
        h.assertTrue(report.sails() == 0 && report.yards().size() == 2, "report: " + report.sails() + " sails, " + report.yards().size() + " yards");
        CrewMember c = spawn(h, p);
        assignToWinch(h, s, p, c);
        assertOrdered(h, run(h, p, "pirates crew order hoist"), 0, 1);
        h.assertTrue(CrewStations.order(h.getLevel(), c, SailOrder.HOIST) == com.richardsenger.piratesnships.station.Stations.OrderResult.NOT_APPLICABLE,
                "the crew member did not refuse");
        c.discard();
        h.succeed();
    }

    /** Off the ship: "No ship under you". A ship without a pump: "No station on this ship can pump". */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "noshipnostation")
    public static void hintsTellNoShipAndNoStation(GameTestHelper h) {
        Ship s = ship(h);
        Captain ashore = new Captain(h.getLevel());
        Vec3 stone = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 5, 3)));
        ashore.moveTo(stone.x, stone.y, stone.z);
        List<Component> out = run(h, ashore, "pirates crew order hoist");
        h.assertTrue(find(out, OrderHints.KEY_NO_SHIP) != null, "no 'no ship' hint ashore: " + keys(out));
        WhistleOrders.Result r = WhistleOrders.handle(ashore, WhistleOrder.HOIST.id());
        h.assertTrue(r.outcome() == WhistleOrders.Outcome.NOT_ON_SHIP, "whistle ashore: " + r);
        Captain p = captain(h, s, DECK);
        out = run(h, p, "pirates crew order pump");
        Component hint = find(out, OrderHints.KEY_NO_STATION);
        h.assertTrue(hint != null, "no 'no station' hint: " + keys(out));
        WhistleOrders.handle(p, WhistleOrder.PUMP.id());
        h.assertTrue(find(p.shown, OrderHints.KEY_NO_STATION) != null, "the whistle did not say 'no station': " + keys(p.shown));
        h.succeed();
    }

    /** Sails already furled, nobody aboard: "furl" has nothing to do, and the captain hears that, not "0 of 0". */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "nothingtodo")
    public static void hintTellsNothingToDo(GameTestHelper h) {
        Ship s = ship(h);
        Captain p = captain(h, s, DECK);
        List<Component> out = run(h, p, "pirates crew order furl");
        h.assertTrue(find(out, OrderHints.KEY_NOTHING_TO_DO) != null, "no 'nothing to do' hint: " + keys(out));
        h.succeed();
    }

    /** With the job board off and nobody at the winch: "Nobody to hoist the sails". */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "nocrew")
    public static void hintTellsNoCrew(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.JOB_BOARD_ENABLED, false);
        Ship s = ship(h);
        Captain p = captain(h, s, DECK);
        List<Component> out = run(h, p, "pirates crew order hoist");
        h.assertTrue(find(out, OrderHints.KEY_NO_CREW) != null, "no 'no crew' hint: " + keys(out));
        WhistleOrders.handle(p, WhistleOrder.HOIST.id());
        h.assertTrue(find(p.shown, OrderHints.KEY_NO_CREW) != null, "the whistle did not say 'no crew': " + keys(p.shown));
        h.succeed();
    }

    /**
     * {@code /pirates ship rigging}: the sail with its depth and trim on the upper yard, the lower yard as its foot,
     * the sailing state's count, and the crew aboard (one at the winch).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "rigging")
    public static void riggingCommandListsSailsYardsAndCrew(GameTestHelper h) {
        Ship s = ship(h);
        Captain p = captain(h, s, DECK);
        CrewMember c = spawn(h, p);
        assignToWinch(h, s, p, c);
        List<Component> out = run(h, p, "pirates ship rigging");
        Component header = find(out, RiggingReport.KEY_HEADER);
        h.assertTrue(header != null && arg(header, 1).equals(1) && arg(header, 2).equals(2), "header: " + keys(out));
        Component state = find(out, RiggingReport.KEY_STATE);
        h.assertTrue(state != null && arg(state, 0).equals(1), "sailing state: " + keys(out));
        List<Component> yards = out.stream().filter(l -> key(l).equals(RiggingReport.KEY_YARD)).toList();
        h.assertTrue(yards.size() == 2, "yard lines: " + keys(out));
        h.assertTrue(key(arg(yards.get(0), 3)).equals(RiggingReport.KEY_HEADS) && arg((Component) arg(yards.get(0), 3), 0).equals(UPPER_Y - LOWER_Y),
                "upper yard: " + keys(out));
        h.assertTrue(key(arg(yards.get(1), 3)).equals(RiggingReport.KEY_FOOT), "lower yard: " + keys(out));
        Component crew = find(out, RiggingReport.KEY_CREW);
        h.assertTrue(crew != null && arg(crew, 0).equals(1) && arg(crew, 1).equals(0), "crew: " + keys(out));
        c.discard();
        h.succeed();
    }
}
