package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlanks;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.combat.cannon.CannonService;
import com.richardsenger.piratesnships.combat.cannon.CannonballEntity;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import com.richardsenger.piratesnships.crew.hammock.RestRules;
import com.richardsenger.piratesnships.crew.morale.NightOutcome;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.walk.WalkConfig;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.Shark;
import com.richardsenger.piratesnships.sailing.anchor.AnchorEntities;
import com.richardsenger.piratesnships.sailing.anchor.AnchorEntity;
import com.richardsenger.piratesnships.sailing.ballast.BallastRule;
import com.richardsenger.piratesnships.sailing.ballast.ShipBallast;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.decor.railing.RailingGateBlock;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.rigging.RailingLatchBlock;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.lookout.CrowsNestBlock;
import com.richardsenger.piratesnships.station.lookout.Lookouts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The brig (SHIP1, art/schematics/brig.py, {@code BrigLayoutTest} for the layout rules) in a world: placed and
 * assembled facing north and east with its nets strung, afloat level and dry in three compartments, sailing and
 * steering, the anchor at the capstan, a crew of eight at the stations, the guns firing through their ports, the lookout
 * in the nest, a player up a net into the nest and down again, a plank from a gate, the hammocks, and the ship's records
 * after a reload; and (on the structure placed on land, where the pathfinder runs in the world's own doubles) every
 * station, door and hatch reachable on foot from the helm. Every test has its own batch.
 */
public final class BrigGameTests {

    private static final String BATCH = "pirates_n_ships_ship_brig_";
    /**
     * Water y 2..SURFACE in the 48 basin; the brig's waterline row 4 sits there, her keel two blocks over the floor (SHIP2:
     * with her ballast she draws about five rows).
     */
    private static final int SURFACE = 8;
    private static final int SETTLE = 60;
    private static final BlockPos HELM = ShipTemplates.BRIG_HELM;
    private static final int WALK_TIMEOUT = 400;
    /** Ticks between two orders in {@link #aCrewOfEightTakesTheStations}. */
    private static final int STAGGER = 15;

    private BrigGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BrigGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A 48 × 48 basin: stone floor, water y 2..8, no barrier ceiling up to y 40 (the main mast's nest). */
    public static void basin(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= SURFACE + 1; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = SURFACE + 2; y <= 40; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    /** The brig {@code id} placed and assembled in the basin, bow north, its stern near the south wall. */
    public static ShipBody brig(GameTestHelper h, ResourceLocation id) {
        basin(h);
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), id, h.absolutePos(new BlockPos(24, SURFACE + 1, 46)),
                Direction.NORTH, true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) throw new GameTestAssertException("brig not placed and assembled: " + r.outcome() + " " + a);
        ShipTestCleanup.track(h, a.shipId());
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        if (ship == null) throw new GameTestAssertException("no ship body");
        return ship;
    }

    /** The plot cell of template cell {@code t} on a brig placed bow north (assembly only translates). */
    public static BlockPos plot(ShipBody ship, BlockPos t) {
        return ShipHelm.steering(ship).offset(t.subtract(HELM));
    }

    private static BlockPos plot(ShipBody ship, int x, int y, int z) {
        return plot(ship, new BlockPos(x, y, z));
    }

    /** Roll (+ = starboard up) and pitch (+ = bow up) [degrees], from the weather deck's row. */
    private static double[] attitude(ShipBody ship) {
        Vec3 port = ship.toWorld(Vec3.atCenterOf(plot(ship, 0, 8, 19)));
        Vec3 starboard = ship.toWorld(Vec3.atCenterOf(plot(ship, 8, 8, 19)));
        Vec3 bow = ship.toWorld(Vec3.atCenterOf(plot(ship, 4, 8, 6)));
        Vec3 stern = ship.toWorld(Vec3.atCenterOf(plot(ship, 4, 8, 29)));
        double roll = Math.toDegrees(Math.atan2(starboard.y - port.y, Math.hypot(starboard.x - port.x, starboard.z - port.z)));
        double pitch = Math.toDegrees(Math.atan2(bow.y - stern.y, Math.hypot(bow.x - stern.x, bow.z - stern.z)));
        return new double[] {roll, pitch};
    }

    private static CrewMember crew(GameTestHelper h, ShipBody ship, BlockPos template, boolean ai) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new GameTestAssertException("no crew member");
        Vec3 p = ship.toWorld(Vec3.atBottomCenterOf(plot(ship, template)).add(0, 0.1, 0));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setStationary(true);
        if (!ai) c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    private static int count(ShipBody ship, ServerLevel level, java.util.function.Predicate<BlockState> what) {
        int n = 0;
        for (BlockPos p : ship.plotBlocks()) if (what.test(level.getBlockState(p))) n++;
        return n;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    /** The brig's structure placed on land in the test area at relative {@code at} (not assembled), its nets strung. */
    private static BlockPos onLand(GameTestHelper h, String structure, BlockPos at) {
        ServerLevel level = h.getLevel();
        StructureTemplate t = level.getStructureManager().get(Constants.id("ships/" + structure)).orElse(null);
        if (t == null) throw new GameTestAssertException("no " + structure + " structure");
        BlockPos origin = h.absolutePos(at);
        Vec3i size = t.getSize();
        for (int x = -2; x <= size.getX() + 1; x++) {
            for (int z = -2; z <= size.getZ() + 1; z++) {
                for (int y = 0; y <= size.getY() + 4; y++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (level.getBlockState(p).is(Blocks.BARRIER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        t.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        ShipTemplate template = ShipTemplates.DEFAULTS.get(Constants.id(structure));
        ShipTemplatePlacer.stringNets(level, Constants.id(structure), template, Rotation.NONE, origin);
        return origin;
    }

    // ------------------------------------------------------------------ placing and assembling

    /**
     * The merchant brig placed and assembled facing north: every template block in the ship, none in the world, the helm
     * where the definition says, the waterline row on the surface; she has the four square sails of her two masts.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "north")
    public static void theBrigPlacesAndAssemblesFacingNorth(GameTestHelper h) {
        basin(h);
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.BRIG_ID, h.absolutePos(new BlockPos(24, SURFACE + 1, 46)),
                Direction.NORTH, false, true, null);
        AssemblyResult a = r.assembly();
        if (a != null && a.shipId() != null) ShipTestCleanup.track(h, a.shipId());
        h.assertTrue(r.outcome() == ShipTemplatePlacer.Outcome.PLACED, "outcome " + r.outcome() + " at " + r.where());
        h.assertTrue(r.rotation() == Rotation.NONE, "rotation " + r.rotation());
        h.assertTrue(r.surfaceY() == h.absolutePos(new BlockPos(0, SURFACE, 0)).getY(), "surface " + r.surfaceY());
        h.assertTrue(r.origin().getY() + ShipTemplates.BRIG_WATERLINE == r.surfaceY(), "the waterline row is not at the surface");
        h.assertTrue(a != null && a.outcome() == AssemblyResult.Outcome.ASSEMBLED, "assembly " + a);
        h.assertTrue(a.count() == r.blocks(), "assembled " + a.count() + " of " + r.blocks() + " blocks");
        h.assertTrue(h.getLevel().getBlockState(r.helm()).isAir(), "the helm stayed in the world");
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        h.runAtTickTime(20, () -> {
            SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
            h.assertTrue(rt != null && rt.sailCount() == 4, "square sails " + (rt == null ? "-" : rt.sailPositions()));
            h.succeed();
        });
    }

    /**
     * The armed navy brig placed and assembled facing east (turned a quarter clockwise): assembled whole with the
     * template's six nets strung before assembly (every latch holds its net, the two hoops three each, net cells in the
     * ship), its eight guns and the crow's nest aboard.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "east")
    public static void theArmedBrigPlacesAndAssemblesFacingEastWithItsNets(GameTestHelper h) {
        basin(h);
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.NAVY_BRIG_ARMED_ID,
                h.absolutePos(new BlockPos(1, SURFACE + 1, 24)), Direction.EAST, true, true, null);
        AssemblyResult a = r.assembly();
        if (a != null && a.shipId() != null) ShipTestCleanup.track(h, a.shipId());
        h.assertTrue(r.outcome() == ShipTemplatePlacer.Outcome.PLACED, "outcome " + r.outcome() + " at " + r.where());
        h.assertTrue(r.rotation() == Rotation.CLOCKWISE_90, "rotation " + r.rotation());
        h.assertTrue(a != null && a.outcome() == AssemblyResult.Outcome.ASSEMBLED, "assembly " + a);
        ServerLevel level = h.getLevel();
        ShipBody ship = SableShips.byId(level, a.shipId());
        int cells = count(ship, level, s -> s.is(RiggingContent.NET_CELL.get()));
        h.assertTrue(a.count() == r.blocks() + cells, "assembled " + a.count() + " of " + r.blocks() + " blocks and " + cells
                + " net cells; left in the world: " + leftovers(h));
        h.assertTrue(cells > 48, "net cells " + cells);
        int latches = 0, held = 0;
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockState(p).getBlock() instanceof RailingLatchBlock) {
                latches++;
                if (RatlineNets.netsOf(level, p).size() == 1) held++;
            }
        }
        h.assertTrue(latches == 6 && held == 6, "latches " + latches + ", holding a net " + held);
        h.assertTrue(count(ship, level, s -> s.getBlock() instanceof CannonBlock && CannonBlock.isMaster(s)) == 8, "guns");
        h.assertTrue(count(ship, level, s -> s.getBlock() instanceof CrowsNestBlock) == 1, "no nest");
        h.succeed();
    }

    // ------------------------------------------------------------------ afloat

    /**
     * The armed brig (the heavier variant) afloat: after settling she lies level (roll and pitch within 2 degrees over
     * the last 40 ticks: the auto-ballast makes good what trim is left, uncapped), the gun ports' sills at least half a
     * block over the water and the keel under it, three or more compartments (the three holds), and not a drop inside.
     * (SHIP2: with her ballast she floats deeper, the surface about at the gun deck floor; BrigTrimGameTests.)
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = BATCH + "afloat")
    public static void theBrigFloatsLevelAndStaysDry(GameTestHelper h) {
        ShipBody ship = brig(h, ShipTemplates.NAVY_BRIG_ARMED_ID);
        ServerLevel level = h.getLevel();
        double water = h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() + 1.0;
        double[] max = {0, 0};
        h.onEachTick(() -> {
            if (h.getTick() < 200 || h.getTick() > 240 || ship.isRemoved()) return;
            double[] at = attitude(ship);
            max[0] = Math.max(max[0], Math.abs(at[0]));
            max[1] = Math.max(max[1], Math.abs(at[1]));
        });
        h.runAtTickTime(241, () -> {
            double floor = ship.toWorld(Vec3.atLowerCornerOf(plot(ship, 4, 6, 19))).y;
            double keel = ship.toWorld(Vec3.atLowerCornerOf(plot(ship, 4, 0, 19))).y;
            BallastRule.Result b = ShipBallast.result(level, ship.id());
            HullRuntime hull = HullRuntimes.get(level, ship.id());
            int compartments = hull == null ? -1 : hull.simulation().analysis().compartments().size();
            Constants.LOG.info("[SHIP1] armed brig afloat: |roll| {}°, |pitch| {}°, gun port sills {} and keel {} above the water, "
                    + "{} compartments, flood {}, ballast {}", fmt(max[0]), fmt(max[1]), fmt(floor - water), fmt(keel - water),
                    compartments, hull == null ? "-" : fmt(hull.simulation().totalVolume()), b);
            h.assertTrue(max[0] < 2.0 && max[1] < 2.0, "not level: roll " + max[0] + "°, pitch " + max[1] + "°");
            h.assertTrue(floor > water + 0.5, "the gun ports are within half a block of the water: " + (floor - water));
            h.assertTrue(keel < water, "the keel is out of the water: " + (keel - water));
            h.assertTrue(b != null && !b.capped(), "the ballast is capped: " + b);
            h.assertTrue(hull != null && hull.seesSea(), "the hull runtime does not see the sea");
            h.assertTrue(compartments >= 3, "compartments " + compartments);
            h.assertTrue(hull.simulation().totalVolume() == 0.0, "water inside: " + hull.simulation().totalVolume());
            h.succeed();
        });
    }

    /**
     * Sailing in the 160 basin: the wind from astern, every sail set, she gathers way; then the rudder hard to port and
     * she turns to port.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 700, batch = BATCH + "sailing")
    public static void theBrigSailsWithTheWindAndSteers(GameTestHelper h) {
        int seaTop = 11;
        for (int x = 0; x < 160; x++) {
            for (int z = 0; z < 160; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 159 || z == 0 || z == 159;
                for (int y = 2; y <= seaTop + 1; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= seaTop ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.BRIG_ID, h.absolutePos(new BlockPos(80, seaTop + 1, 157)),
                Direction.NORTH, true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) throw new GameTestAssertException("not assembled: " + r.outcome() + " " + a);
        ShipTestCleanup.track(h, a.shipId());
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        String dim = h.getLevel().dimension().location().toString();
        double[] speed = {0};
        double[] heading = {0, 0}; // last heading, turned to port so far
        int turnAt = 360;
        h.onEachTick(() -> {
            long t = h.getTick();
            SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
            if (rt == null || ship.isRemoved()) return;
            double hd = rt.headingDegrees(ship);
            if (t >= 40) WindOverride.set(dim, WindSample.normalizeDegrees(hd + 180.0), 9.0, h.getLevel().getGameTime() + 40);
            if (t == 40) {
                for (BlockPos p : rt.sailPositions()) SailingRuntimes.setTrim(h.getLevel(), p, SailTrim.FULL);
            }
            if (t == turnAt) {
                speed[0] = rt.shipFrameVelocity(ship, new Vector3d()).z;
                heading[0] = hd;
                ShipControls.setRudderAngle(h.getLevel(), rt.helm(), -360.0);
            } else if (t > turnAt) {
                heading[1] += WindSample.normalizeDegrees(hd - heading[0] + 180.0) - 180.0;
                heading[0] = hd;
            }
        });
        h.runAtTickTime(turnAt + 240, () -> {
            WindOverride.clear(dim);
            Constants.LOG.info("[SHIP1] brig under all sail before the wind: {} blocks/s; turned {}° in 240 ticks with the helm hard over",
                    fmt(speed[0]), fmt(-heading[1]));
            h.assertTrue(speed[0] > 0.5, "she does not gather way: " + speed[0] + " blocks/s");
            h.assertTrue(-heading[1] > 10.0, "she does not answer the helm: turned " + (-heading[1]) + "°");
            h.succeed();
        });
    }

    /** The capstan forward lets the anchor go (it bites on the basin's floor) and weighs it again until it is stowed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 600, batch = BATCH + "anchor")
    public static void theAnchorDropsAndRaisesAtTheCapstan(GameTestHelper h) {
        ShipBody ship = brig(h, ShipTemplates.BRIG_ID);
        ServerLevel level = h.getLevel();
        BlockPos capstan = plot(ship, 4, 9, 7);
        h.assertTrue(level.getBlockState(capstan).is(SailingBlocks.CAPSTAN.get()), "no capstan at " + capstan + ": " + level.getBlockState(capstan));
        int[] raised = {-1};
        h.onEachTick(() -> {
            long t = h.getTick();
            SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
            if (rt == null) return;
            if (t == SETTLE) {
                ShipControls.CapstanResult c = ShipControls.useCapstan(level, capstan);
                h.assertTrue(c.result() == ShipControls.AnchorResult.DROPPING, "drop refused: " + c.result());
            } else if (t > SETTLE && raised[0] < 0 && rt.anchor() != null && rt.anchor().state().phase() == AnchorState.Phase.HOLDING) {
                ShipControls.CapstanResult c = ShipControls.useCapstan(level, capstan);
                h.assertTrue(c.result() == ShipControls.AnchorResult.RAISING, "raise refused: " + c.result());
                raised[0] = (int) t;
            } else if (raised[0] >= 0 && rt.anchor() == null) {
                AnchorEntity e = AnchorEntities.of(level, ship.id());
                h.assertTrue(e == null || !e.isOut(), "not stowed after raising");
                h.succeed();
            }
        });
    }

    // ------------------------------------------------------------------ crew

    /**
     * A crew of eight, standing on the quarterdeck beside the helm with their AI, is sent to the stations: the helm, both
     * sail winches, the capstan, the bilge pump and two guns on the gun deck walk there (through the companion) and are
     * seated before the walk's timeout; the lookout goes up to the nest (seated there: the pathfinder has no ladders).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 800, batch = BATCH + "crew")
    public static void aCrewOfEightTakesTheStations(GameTestHelper h) {
        // the walk's timeout (default 200 ticks) is a fallback; the gun deck lies some 30 blocks of walking from the
        // quarterdeck, down the companion, so this test gives the walk 400 ticks and checks every walker arrives
        ConfigOverrides.during(h, WalkConfig.TIMEOUT_TICKS, WALK_TIMEOUT);
        ShipBody ship = brig(h, ShipTemplates.NAVY_BRIG_ARMED_ID);
        ServerLevel level = h.getLevel();
        Map<String, BlockPos> stations = new LinkedHashMap<>();
        stations.put("helm", HELM);
        stations.put("fore winch", new BlockPos(4, 9, 12));
        stations.put("main winch", new BlockPos(4, 9, 20));
        stations.put("capstan", new BlockPos(4, 9, 7));
        stations.put("pump", new BlockPos(7, 6, 19));
        stations.put("port gun", new BlockPos(1, 6, 21));
        stations.put("starboard gun", new BlockPos(7, 6, 13));
        stations.put("lookout", new BlockPos(4, 26, 19));
        List<CrewMember> crew = new ArrayList<>();
        Map<String, Integer> seatedAt = new LinkedHashMap<>();
        Map<String, Boolean> walked = new LinkedHashMap<>();
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == SETTLE) {
                int i = 0;
                for (Map.Entry<String, BlockPos> e : stations.entrySet()) {
                    CrewMember c = crew(h, ship, new BlockPos(2 + (i % 5), 10, 26 + i / 5 * 2), true);
                    crew.add(c);
                    i++;
                }
            }
            if (t >= SETTLE + 20 && t < SETTLE + 20 + STAGGER * stations.size() && (t - SETTLE - 20) % STAGGER == 0) {
                // one order every STAGGER ticks: the quarterdeck's stairs are one block wide, and two walkers sent down
                // the same stair in the same tick can jam in the rail's gap above it (seen once on Fabric)
                int i = (int) ((t - SETTLE - 20) / STAGGER);
                Map.Entry<String, BlockPos> e = new ArrayList<>(stations.entrySet()).get(i);
                CrewMember c = crew.get(i);
                CrewStations.AssignResult res = CrewStations.assign(level, c, plot(ship, e.getValue()));
                h.assertTrue(res == CrewStations.AssignResult.ASSIGNED, e.getKey() + ": " + res);
                walked.put(e.getKey(), c.walk() != null);
            }
            if (t > SETTLE + 20) {
                int i = 0;
                for (String name : stations.keySet()) {
                    CrewMember c = crew.get(i++);
                    if (!walked.containsKey(name)) continue;
                    if (c.isAtStation()) seatedAt.putIfAbsent(name, (int) (t - SETTLE - 20 - STAGGER * (i - 1)));
                    else if ((t - SETTLE - 20) % 100 == 0) {
                        Vec3 local = ship.toPlot(c.position()).subtract(Vec3.atLowerCornerOf(ShipHelm.steering(ship).subtract(HELM)));
                        Constants.LOG.info("[SHIP1] {} still walking after {} ticks, at template {} toward {}", name, t - SETTLE - 20,
                                String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", local.x, local.y, local.z),
                                c.walk() == null ? "-" : c.walk().spot().subtract(ShipHelm.steering(ship).subtract(HELM)).toShortString());
                    }
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(seatedAt.size() == stations.size(), "seated " + seatedAt + ", walking " + walked);
            Constants.LOG.info("[SHIP1] crew of eight: walked {}, seated after {}", walked, seatedAt);
            for (String name : stations.keySet()) {
                if (name.equals("lookout") || name.equals("helm")) continue;
                h.assertTrue(walked.get(name), name + ": seated at once, did not walk");
                h.assertTrue(seatedAt.get(name) < WALK_TIMEOUT, name + ": seated only by the walk's timeout (" + seatedAt.get(name) + " ticks)");
            }
            for (CrewMember c : crew) {
                if (c.assignment() != null) CrewStations.release(level, c);
                c.discard();
            }
        });
    }

    /**
     * The pathfinder on the brig's structure placed on land (the world's doubles, not a ship's plot): from the helmsman's
     * place every station of the weather deck, the quarterdeck and the gun deck, both doors and the cell over every hold
     * hatch is reachable on foot (the companion stairs and the quarterdeck stairs), within the crew's follow range.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 100, batch = BATCH + "paths")
    public static void everyStationDoorAndHatchIsReachableFromTheHelm(GameTestHelper h) {
        BlockPos origin = onLand(h, "navy_brig_armed", new BlockPos(19, 1, 8));
        ServerLevel level = h.getLevel();
        CrewMember c = StationContent.CREW_MEMBER.get().create(level);
        if (c == null) throw new GameTestAssertException("no crew member");
        Vec3 at = Vec3.atBottomCenterOf(origin.offset(4, 10, 28));
        c.moveTo(at.x, at.y, at.z, 0, 0);
        c.setStationary(true);
        level.addFreshEntity(c);
        Map<String, BlockPos> targets = new LinkedHashMap<>();
        targets.put("fore winch (its spot)", new BlockPos(4, 9, 13));
        targets.put("main winch (its spot)", new BlockPos(4, 9, 21));
        targets.put("capstan", new BlockPos(4, 9, 7));
        targets.put("pump", new BlockPos(7, 6, 19));
        for (int z : new int[] {13, 17, 21, 25}) {
            targets.put("port gun " + z + " (its crew)", new BlockPos(3, 6, z));
            targets.put("starboard gun " + z + " (its crew)", new BlockPos(5, 6, z));
        }
        targets.put("port swivel", new BlockPos(1, 11, 29));
        targets.put("starboard swivel", new BlockPos(7, 11, 29));
        targets.put("cabin door (before it)", new BlockPos(4, 6, 25));
        targets.put("great cabin", new BlockPos(4, 6, 28));
        targets.put("fore hatch", new BlockPos(4, 6, 12));
        targets.put("main hatch", new BlockPos(4, 6, 20));
        targets.put("aft hatch", new BlockPos(4, 6, 24));
        // before the pantry: a solid target is moved up to the first free cell over it (GroundPathNavigation#createPath),
        // which since SHIP2's tumblehome is over the rail, not the gun deck
        targets.put("galley (before the pantry)", new BlockPos(2, 6, 12));
        targets.put("port gate", new BlockPos(1, 9, 17));
        targets.put("starboard gate", new BlockPos(7, 9, 17));
        h.runAfterDelay(10, () -> {
            c.setOnGround(true);
            List<String> unreachable = new ArrayList<>();
            for (Map.Entry<String, BlockPos> e : targets.entrySet()) {
                c.getNavigation().stop();
                Path p = c.getNavigation().createPath(origin.offset(e.getValue()), 1);
                if (p == null || !p.canReach()) unreachable.add(e.getKey() + " " + e.getValue().toShortString()
                        + (p == null ? " (no path)" : " (ends at " + p.getEndNode().asBlockPos().subtract(origin).toShortString() + ")"));
            }
            c.discard();
            h.assertTrue(unreachable.isEmpty(), "not reachable from the helm: " + unreachable);
            h.succeed();
        });
    }

    /**
     * Each of the eight guns, loaded by hand, fires through its own open port: a ball leaves the muzzle and is not stopped
     * in the barrel, and the port is still there with its lid open.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 300, batch = BATCH + "guns")
    public static void everyGunLoadsAndFiresThroughItsPort(GameTestHelper h) {
        ShipBody ship = brig(h, ShipTemplates.NAVY_BRIG_ARMED_ID);
        ServerLevel level = h.getLevel();
        List<CannonballEntity> balls = new ArrayList<>();
        h.runAtTickTime(SETTLE, () -> {
            Player creative = h.makeMockPlayer(GameType.CREATIVE);
            creative.getAbilities().instabuild = true;
            for (int x : new int[] {1, 7}) {
                for (int z : new int[] {13, 17, 21, 25}) {
                    BlockPos gun = plot(ship, x, 6, z);
                    h.assertTrue(CannonBlock.isMaster(level.getBlockState(gun)), "no gun at template " + x + ", 6, " + z);
                    h.assertTrue(CannonService.load(level, gun, creative, new ItemStack(Items.GUNPOWDER)).outcome() == CannonService.Outcome.POWDER_IN, "powder");
                    h.assertTrue(CannonService.load(level, gun, creative, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                            == CannonService.Outcome.BALL_IN, "ball");
                    CannonService.Use use = CannonService.fire(level, gun, null);
                    h.assertTrue(use.outcome() == CannonService.Outcome.FIRED && use.ball() != null, "gun " + x + "/" + z + ": " + use.outcome());
                    h.assertFalse(use.ball().isRemoved(), "gun " + x + "/" + z + ": the ball was stopped in the barrel");
                    h.assertTrue(level.getBlockState(gun).getValue(CannonBlock.LOAD) == CannonLoad.EMPTY, "still loaded");
                    BlockState port = level.getBlockState(plot(ship, x == 1 ? 0 : 8, 6, z));
                    h.assertTrue(port.getBlock() instanceof com.richardsenger.piratesnships.ship.decor.railing.BulwarkPortBlock
                            && port.getValue(com.richardsenger.piratesnships.ship.decor.railing.BulwarkPortBlock.OPEN), "the port of " + x + "/" + z + ": " + port);
                    balls.add(use.ball());
                }
            }
            creative.discard();
        });
        // scheduled up front: scheduling from a scheduled task changes the task map while it is being iterated
        h.runAtTickTime(SETTLE + 4, () -> {
            h.assertTrue(balls.size() == 8, "balls " + balls.size());
            BlockPos helm = ShipHelm.steering(ship);
            for (CannonballEntity b : balls) {
                Vec3 local = ship.toPlot(b.position());
                double out = Math.abs(local.x - (helm.getX() + 0.5));
                h.assertTrue(b.isRemoved() || out > 5.0, "a ball is still inside the hull: " + local + " (" + fmt(out) + " off the centre line)");
                b.discard();
            }
            h.succeed();
        });
    }

    /** A crew member sits the watch in the crow's nest and calls a shark 30 blocks off the beam. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "lookout")
    public static void theLookoutSightsFromTheNest(GameTestHelper h) {
        ShipBody ship = brig(h, ShipTemplates.NAVY_BRIG_ARMED_ID);
        ServerLevel level = h.getLevel();
        h.runAtTickTime(SETTLE, () -> {
            CrewMember c = crew(h, ship, new BlockPos(4, 9, 21), false);
            BlockPos nest = plot(ship, 4, 26, 19);
            h.assertTrue(CrewStations.assign(level, c, nest) == CrewStations.AssignResult.ASSIGNED, "assign");
            h.assertTrue(c.isAtStation() && Lookouts.isManned(level, ship), "the nest is not manned");
            Shark s = MobContent.SHARK.get().create(level);
            if (s == null) throw new GameTestAssertException("no shark");
            Vec3 floor = ship.toWorld(Vec3.atBottomCenterOf(nest));
            s.moveTo(floor.x - 30, h.absolutePos(new BlockPos(0, 4, 0)).getY(), floor.z, 0, 0);
            s.setNoAi(true);
            level.addFreshEntity(s);
            Lookouts.forget(ship.id());
            List<Lookouts.Observer> obs = Lookouts.observers(level, List.of()).getOrDefault(ship.id(), List.of());
            List<Lookouts.Report> reports = Lookouts.scan(level, ship, obs, List.of(), level.getGameTime());
            String key = "entity:" + s.getUUID();
            h.assertTrue(reports.stream().anyMatch(r -> r.sighting().key().equals(key)), "the shark was not called: " + reports.size() + " reports");
            s.discard();
            CrewStations.release(level, c);
            c.discard();
            Lookouts.forget(ship.id());
            h.succeed();
        });
    }

    /**
     * RL5 nets on the brig's structure placed on land: a player at the port foot (in the latch's cell over the rail)
     * holds forward toward the mast, the net carries him up to the hoop under the nest and he steps over into the crow's
     * nest without a jump; then he walks back out over the hoop until the net catches him, lets go and comes down to the
     * rail or the weather deck.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 500, batch = BATCH + "climb")
    public static void aPlayerClimbsANetToTheNestAndBackDown(GameTestHelper h) {
        BlockPos origin = onLand(h, "navy_brig_armed", new BlockPos(19, 1, 8));
        ServerLevel level = h.getLevel();
        BlockPos latch = origin.offset(1, 11, 27);
        BlockPos nest = origin.offset(4, 26, 19);
        RatlineNets.Net net = RatlineNets.netOf(level, latch);
        h.assertTrue(net != null, "no net at the port latch");
        h.assertTrue(level.getBlockState(nest).getBlock() instanceof CrowsNestBlock, "no nest");
        float up = (float) Math.toDegrees(Math.atan2(-(net.ringB().x() - net.ringA().x()), net.ringB().z() - net.ringA().z()));
        Player[] player = {null};
        String[] phase = {"up"};
        boolean[] caught = {false};
        int[] tick = {0};
        double[] lowest = {Double.MAX_VALUE};
        h.onEachTick(() -> {
            int t = tick[0]++;
            if (t == 5) {
                Player p = h.makeMockPlayer(GameType.SURVIVAL);
                p.moveTo(net.ringA().x() + 0.55, net.ringA().y() + 0.05, net.ringA().z(), up, 0f);
                level.addFreshEntity(p);
                player[0] = p;
            }
            Player p = player[0];
            if (p == null || p.isRemoved()) return;
            p.setJumping(false);
            switch (phase[0]) {
                case "up" -> {
                    // along the net toward the mast (SHIP2: the net runs forward from the quarterdeck rail, and the foot lets go
                    // of a climber pushing more than 60 degrees off its heading), then east off the hoop into the nest
                    p.setYRot(p.getY() < net.ringB().y() ? up : Direction.EAST.toYRot());
                    p.zza = 1f;
                    if (p.blockPosition().equals(nest) && p.onGround()) phase[0] = "down";
                }
                default -> {
                    p.setYRot(Direction.WEST.toYRot());
                    caught[0] |= p.onClimbable();
                    p.zza = caught[0] ? 0f : 1f;
                    lowest[0] = Math.min(lowest[0], p.getY() - origin.getY());
                }
            }
        });
        h.succeedWhen(() -> {
            Player p = player[0];
            if (p == null) throw new GameTestAssertException("not spawned yet");
            Vec3 local = p.position().subtract(Vec3.atLowerCornerOf(origin));
            h.assertTrue(phase[0].equals("down"), "not in the nest yet: at " + local);
            h.assertTrue(caught[0] && p.onGround() && local.y < 11.6, "not down yet: at " + local + ", caught " + caught[0]);
            Constants.LOG.info("[SHIP1] up the port net into the nest and down again to y {}", fmt(local.y));
            p.discard();
        });
    }

    /** Blocks in the basin that are neither its stone, water nor air (what an assembly left behind). */
    private static List<String> leftovers(GameTestHelper h) {
        List<String> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(46, 40, 46))) {
            BlockState s = h.getBlockState(p);
            if (!s.isAir() && !s.is(Blocks.WATER) && !s.is(Blocks.STONE) && !s.is(Blocks.BARRIER)) out.add(p.toShortString() + " " + s);
        }
        return out;
    }

    /** A boarding plank laid from the port gate (opened) bridges two blocks to a pier at the deck's height. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 100, batch = BATCH + "plank")
    public static void aPlankLaysFromAGate(GameTestHelper h) {
        BlockPos origin = onLand(h, "brig", new BlockPos(19, 1, 8));
        ServerLevel level = h.getLevel();
        BlockPos gate = origin.offset(1, 9, 17);
        BlockState g = level.getBlockState(gate);
        h.assertTrue(g.getBlock() instanceof RailingGateBlock, "no gate at " + gate + ": " + g);
        level.setBlock(gate, g.setValue(RailingGateBlock.OPEN, true), Block.UPDATE_ALL);
        for (int x = -4; x <= -2; x++) {
            for (int dz = -1; dz <= 1; dz++) level.setBlock(origin.offset(x, 8, 17 + dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        BoardingPlanks.Laid laid = BoardingPlanks.lay(level, gate, Direction.UP, Direction.WEST);
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "no plank from the gate: " + laid.outcome());
        h.assertTrue(laid.cells().get(0).equals(gate.west()), "the run starts at " + laid.cells().get(0));
        h.succeed();
    }

    /**
     * Nightfall aboard: eight crew members (no AI, so they lie down at once) take the brig's eight hammocks, each sleeps
     * in one of its own (SLEPT), none is left without.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "hammocks")
    public static void theCrewSleepsInTheHammocks(GameTestHelper h) {
        ShipBody ship = brig(h, ShipTemplates.BRIG_ID);
        ServerLevel level = h.getLevel();
        h.runAtTickTime(SETTLE, () -> {
            int hammocks = count(ship, level, s -> s.getBlock() instanceof HammockBlock
                    && s.getValue(HammockBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT);
            h.assertTrue(hammocks == 8, "hammocks " + hammocks);
            List<CrewMember> crew = new ArrayList<>();
            for (int i = 0; i < 8; i++) crew.add(crew(h, ship, new BlockPos(2 + i % 5, 9, 18 + i / 5), false));
            long today = level.getDayTime() / RestRules.DAY * RestRules.DAY;
            level.setDayTime(today + 18000L);
            CrewRest.turnIn(level, ship);
            List<String> outcomes = new ArrayList<>();
            for (CrewMember c : crew) outcomes.add(c.nightOutcome() + (c.rest() == null ? "" : "@" + c.rest().foot()));
            for (CrewMember c : crew) {
                h.assertTrue(c.nightOutcome() == NightOutcome.SLEPT && c.rest() != null, "not every crew member has a hammock: " + outcomes);
            }
            for (CrewMember c : crew) {
                CrewRest.getUp(c, false);
                c.discard();
            }
            level.setDayTime((level.getDayTime() / RestRules.DAY + 1) * RestRules.DAY + 1000L);
            h.succeed();
        });
    }

    /**
     * A navy patrol voyage on the armed brig materialises in the basin (the world simulation's own placement): eight
     * guns, all loaded, four shot lockers stocked, the crow's nest manned by a lookout, the helm manned.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "patrol")
    public static void aNavyPatrolSailsTheArmedBrig(GameTestHelper h) {
        basin(h);
        net.minecraft.server.MinecraftServer server = h.getLevel().getServer();
        BlockPos m = h.absolutePos(new BlockPos(24, SURFACE, 24));
        List<com.richardsenger.piratesnships.worldsim.lane.Lane.Point> route = List.of(
                new com.richardsenger.piratesnships.worldsim.lane.Lane.Point(m.getX(), m.getZ() + 100),
                new com.richardsenger.piratesnships.worldsim.lane.Lane.Point(m.getX(), m.getZ() - 100));
        ResourceLocation from = Constants.id("gametest/ship1_" + UUID.randomUUID().toString().substring(0, 8));
        com.richardsenger.piratesnships.worldsim.voyage.Voyage v = com.richardsenger.piratesnships.worldsim.voyage.Voyage.depart(
                UUID.randomUUID(), com.richardsenger.piratesnships.worldsim.voyage.VoyageKind.PATROL,
                com.richardsenger.piratesnships.law.flag.Faction.NAVY, ShipTemplates.NAVY_BRIG_ARMED_ID, from, from, route, Map.of(),
                h.getLevel().getGameTime()).withProgress(100);
        com.richardsenger.piratesnships.worldsim.voyage.VoyageData.get(server).put(v);
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> {
            com.richardsenger.piratesnships.worldsim.materialize.Materializer.Outcome o =
                    com.richardsenger.piratesnships.worldsim.materialize.Materializer.materialize(server, v.id());
            com.richardsenger.piratesnships.worldsim.voyage.Voyage now =
                    com.richardsenger.piratesnships.worldsim.voyage.Voyages.get(server, v.id()).orElseThrow();
            now.shipId().ifPresent(id -> ShipTestCleanup.track(h, id));
            h.assertTrue(o == com.richardsenger.piratesnships.worldsim.materialize.Materializer.Outcome.SPAWNED, "materialize: " + o);
            ship[0] = SableShips.byId(h.getLevel(), now.shipId().orElseThrow());
        });
        h.runAtTickTime(30, () -> {
            ServerLevel level = h.getLevel();
            List<BlockPos> guns = com.richardsenger.piratesnships.worldsim.materialize.VoyageGuns.guns(level, ship[0]);
            List<BlockPos> lockers = com.richardsenger.piratesnships.worldsim.materialize.VoyageGuns.lockers(level, ship[0]);
            h.assertTrue(guns.size() == 8, "guns " + guns.size());
            h.assertTrue(lockers.size() == 4, "lockers " + lockers.size());
            h.assertTrue(guns.stream().allMatch(g -> level.getBlockState(g).getValue(CannonBlock.LOAD) == CannonLoad.LOADED), "a gun is not loaded");
            for (BlockPos l : lockers) {
                h.assertTrue(level.getBlockEntity(l) instanceof net.minecraft.world.Container c && !c.isEmpty(), "an empty locker at " + l);
            }
            h.assertTrue(Lookouts.isManned(level, ship[0]), "no lookout in the nest");
            com.richardsenger.piratesnships.worldsim.voyage.Voyages.end(server, v.id(), com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd.CANCELLED);
            com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew.discard(level, v.id());
            h.succeed();
        });
    }

    /**
     * The world's records of the assembled armed brig after a reload: the ship registry's saved data reads back with
     * her, and fresh sailing and hull runtimes (as after an unload) find her four square sails and three or more
     * compartments again; her nets still hang between their fittings (block entity links survive, they are relative).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 200, batch = BATCH + "reload")
    public static void theBrigSurvivesASaveAndReload(GameTestHelper h) {
        ShipBody ship = brig(h, ShipTemplates.NAVY_BRIG_ARMED_ID);
        ServerLevel level = h.getLevel();
        UUID id = ship.id();
        h.runAtTickTime(SETTLE, () -> {
            CompoundTag saved = ShipRegistry.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
            List<ShipData> loaded = ShipData.CODEC.listOf().parse(NbtOps.INSTANCE, saved.get("ships")).getOrThrow();
            h.assertTrue(loaded.stream().anyMatch(s -> s.id().equals(id)), "the brig is not in the saved ship registry");
            SailingRuntimes.onShipRemoved(level, id, false);
            HullRuntimes.onShipRemoved(level, id, false);
        });
        h.runAtTickTime(SETTLE + 40, () -> {
            ShipBody body = SableShips.byId(level, id);
            h.assertTrue(body != null, "the body is gone");
            SailingRuntime rt = SailingRuntimes.getOrCreate(body);
            h.assertTrue(rt != null && rt.sailCount() == 4, "sails after the reload " + (rt == null ? "-" : rt.sailPositions()));
            HullRuntime hull = HullRuntimes.get(level, id);
            if (hull == null) hull = HullRuntimes.onAssembled(body);
            hull.reanalyseNow(body);
            int compartments = hull.simulation().analysis().compartments().size();
            h.assertTrue(compartments >= 3, "compartments after the reload " + compartments);
            BlockPos latch = plot(body, 1, 11, 26);
            h.assertTrue(RatlineNets.netsOf(level, latch).size() == 1, "the net at the port latch is gone");
            h.succeed();
        });
    }
}
