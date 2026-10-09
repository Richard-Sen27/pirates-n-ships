package com.richardsenger.piratesnships.crew.lean;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * RAIL5 (docs/design.md §4.8 "Railing extras"): idle crew walk to the ship's rail and lean on it. The ship is a 7×17
 * plank hull afloat (deck y 8, x 15..21, z 5..21) with a railing all round its deck edge at y 9, the helm near the bow
 * rail (so the rail cells beside it are no spots) and a winch on deck; crew members with AI stand on the deck. Each test
 * runs in a batch of its own (the lean reaches every ship of the level, config changes too).
 */
public final class RailLeanGameTests {

    private static final String BATCH = "pirates_n_ships_config_rail_lean_";

    static final BlockPos HELM = new BlockPos(18, 9, 6);
    static final BlockPos WINCH = new BlockPos(18, 9, 18);
    /** Ticks the crew get to land on the deck before anything is checked. */
    static final int SETTLE = 20;
    /** Ticks an idle crew member gets to reach the rail and lean. */
    static final int WITHIN = 200;

    private RailLeanGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RailLeanGameTests.class);
    }

    // ------------------------------------------------------------------ fixture

    record Ship(ShipBody body, BlockPos helm) {
        BlockPos plot(BlockPos rel) {
            return helm.offset(rel.subtract(HELM));
        }
    }

    private static void railing(GameTestHelper h, BlockPos rel, Direction... edges) {
        BlockState s = RailingContent.RAILINGS.get(ShipWood.OAK).defaultBlockState();
        for (Direction d : edges) s = s.setValue(RailingBlock.edge(d), true);
        h.setBlock(rel, s);
    }

    /** The basin, the railed hull with helm and winch, assembled. */
    static Ship ship(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        StationGameTests.basin(h, true);
        for (int x = 15; x <= 21; x++) {
            for (int z = 5; z <= 21; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == 15 || x == 21 || z == 5 || z == 21;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        for (int z = 5; z <= 21; z++) {
            for (int x : new int[] {15, 21}) {
                List<Direction> edges = new ArrayList<>(List.of(x == 15 ? Direction.WEST : Direction.EAST));
                if (z == 5) edges.add(Direction.NORTH);
                if (z == 21) edges.add(Direction.SOUTH);
                railing(h, new BlockPos(x, 9, z), edges.toArray(Direction[]::new));
            }
        }
        for (int x = 16; x <= 20; x++) {
            railing(h, new BlockPos(x, 9, 5), Direction.NORTH);
            railing(h, new BlockPos(x, 9, 21), Direction.SOUTH);
        }
        for (int x = 15; x <= 21; x++) {
            for (int z = 5; z <= 21; z++) {
                BlockPos abs = h.absolutePos(new BlockPos(x, 9, z));
                BlockState s = h.getLevel().getBlockState(abs);
                if (s.getBlock() instanceof RailingBlock) {
                    h.getLevel().setBlock(abs, RailingBlock.withPosts(s, h.getLevel(), abs), 3);
                }
            }
        }
        h.setBlock(HELM, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(WINCH, SailingBlocks.SAIL_WINCH.get());
        AssemblyResult r = ShipTestCleanup.assemble(h, HELM);
        if (r.shipId() == null) throw new AssertionError("assembly failed: " + r);
        ShipBody body = SableShips.byId(h.getLevel(), r.shipId());
        if (body == null) throw new AssertionError("no ship after assembly");
        return new Ship(body, StationGameTests.find(h, body, AssemblyContent.HELM.get()));
    }

    /** An idle crew member with AI on the deck at relative {@code rel}. */
    static CrewMember crew(GameTestHelper h, Ship s, BlockPos rel) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = s.body().toWorld(Vec3.atBottomCenterOf(s.plot(rel)).add(0, 0.1, 0));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    static void cleanup(List<CrewMember> crew) {
        for (CrewMember c : crew) {
            if (c.getVehicle() != null) c.getVehicle().discard();
            c.discard();
        }
    }

    /** What is wrong with {@code c}'s lean right now (null when it leans at a railing's spot, facing outboard). */
    static String leanProblem(GameTestHelper h, Ship s, CrewMember c, boolean checkFacing) {
        RailLean.Lean lean = c.lean();
        if (!c.isLeaning() || lean == null || !lean.leaning()) {
            return "not leaning (lean " + lean + ")";
        }
        ServerLevel level = h.getLevel();
        if (!(level.getBlockState(lean.spot().cell()).getBlock() instanceof RailingBlock)) {
            return "the spot " + lean.spot() + " is no railing";
        }
        if (!RailingBlock.edges(level.getBlockState(lean.spot().cell())).contains(lean.spot().outboard())) {
            return "the spot faces " + lean.spot().outboard() + ", not a railed edge";
        }
        Vec3 feet = s.body().toPlot(c.position());
        Vec3 at = RailSpots.leanPoint(lean.spot());
        double d = Math.hypot(feet.x - at.x, feet.z - at.z);
        if (d > 0.3) {
            return "stands " + d + " off its spot";
        }
        if (c.pose(false) != CrewPose.LEAN) {
            return "pose " + c.pose(false);
        }
        if (checkFacing) {
            float want = StationPoses.yaw(lean.spot().outboard(), s.body().orientation());
            float off = Math.abs(Mth.wrapDegrees(c.yBodyRot - want));
            if (off > 10) {
                return "faces " + c.yBodyRot + ", outboard is " + want;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tests

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 320, batch = BATCH + "one")
    public static void anIdleCrewMemberLeansOnTheRailFacingTheSea(GameTestHelper h) {
        Ship s = ship(h);
        CrewMember c = crew(h, s, new BlockPos(18, 9, 12));
        long[] leanedAt = {-1};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (leanedAt[0] < 0 && c.isLeaning()) {
                leanedAt[0] = t;
                String p = leanProblem(h, s, c, false);
                h.assertTrue(p == null, "at the start of the lean: " + p);
            }
            if (leanedAt[0] < 0) {
                h.assertTrue(t <= SETTLE + WITHIN, "not leaning within " + WITHIN + " ticks: lean " + c.lean()
                        + ", walk " + c.walk() + ", at " + s.body().toPlot(c.position()));
                return;
            }
            if (t == leanedAt[0] + 20) {
                String p = leanProblem(h, s, c, true);
                h.assertTrue(p == null, "20 ticks into the lean: " + p);
                // the head looks out to sea, a little down
                h.assertTrue(Math.abs(c.getXRot() - LeanRules.HEAD_PITCH) < 0.01f, "head pitch " + c.getXRot());
                cleanup(List.of(c));
                h.succeed();
            }
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "cap")
    public static void atMostThreeLeanAtOnceACellApart(GameTestHelper h) {
        Ship s = ship(h);
        List<CrewMember> crew = new ArrayList<>();
        for (BlockPos rel : List.of(new BlockPos(17, 9, 10), new BlockPos(18, 9, 12), new BlockPos(19, 9, 14),
                new BlockPos(17, 9, 16), new BlockPos(19, 9, 9))) {
            crew.add(crew(h, s, rel));
        }
        int cap = RailLeanConfig.LEANERS.get();
        h.onEachTick(() -> {
            long t = h.getTick();
            List<CrewMember> leaning = crew.stream().filter(CrewMember::isLeaning).toList();
            h.assertTrue(leaning.size() <= cap, "tick " + t + ": " + leaning.size() + " lean, at most " + cap);
            long claimed = crew.stream().filter(c -> c.lean() != null).count();
            h.assertTrue(claimed <= cap, "tick " + t + ": " + claimed + " spots claimed, at most " + cap);
            if (t < 300) {
                return;
            }
            h.assertTrue(leaning.size() == cap, "after 300 ticks " + leaning.size() + " lean, expected " + cap);
            for (int i = 0; i < leaning.size(); i++) {
                for (int j = i + 1; j < leaning.size(); j++) {
                    BlockPos a = leaning.get(i).lean().spot().cell(), b = leaning.get(j).lean().spot().cell();
                    h.assertTrue(RailSpots.apart(a, b), "two leaners side by side: " + a + " and " + b);
                }
            }
            for (CrewMember c : leaning) {
                String p = leanProblem(h, s, c, true);
                h.assertTrue(p == null, p);
                BlockPos cell = c.lean().spot().cell();
                h.assertTrue(cell.distManhattan(s.plot(HELM)) > 2, "a leaner at the helm: " + cell);
            }
            cleanup(crew);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 320, batch = BATCH + "order")
    public static void anOrderEndsTheLeanAtOnce(GameTestHelper h) {
        Ship s = ship(h);
        CrewMember c = crew(h, s, new BlockPos(18, 9, 12));
        long[] ordered = {-1};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (ordered[0] < 0) {
                h.assertTrue(t <= SETTLE + WITHIN, "not leaning within " + WITHIN + " ticks");
                if (c.isLeaning() && c.lean() != null && c.lean().leaning()) {
                    CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, s.plot(WINCH));
                    h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
                    ordered[0] = t;
                }
                return;
            }
            if (t == ordered[0] + 1) {
                h.assertTrue(!c.isLeaning() && c.lean() == null, "still leaning a tick after the order: " + c.lean());
                h.assertTrue(c.walk() != null || c.isAtStation(), "not on its way to the winch: walk " + c.walk());
                RailLean.Lean none = c.lean();
                h.assertTrue(none == null && RailLean.taken(h.getLevel(), s.body().id()).isEmpty(), "the spot is still taken");
                cleanup(List.of(c));
                h.succeed();
            }
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 260, batch = BATCH + "off")
    public static void railLeanOffKeepsThemOffTheRail(GameTestHelper h) {
        ConfigOverrides.during(h, RailLeanConfig.ENABLED, false);
        Ship s = ship(h);
        CrewMember c = crew(h, s, new BlockPos(18, 9, 12));
        h.onEachTick(() -> {
            h.assertTrue(!c.isLeaning() && c.lean() == null, "leaning with rail_lean off at tick " + h.getTick());
            if (h.getTick() >= SETTLE + WITHIN) {
                cleanup(List.of(c));
                h.succeed();
            }
        });
    }
}
