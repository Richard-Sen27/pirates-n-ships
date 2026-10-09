package com.richardsenger.piratesnships.core.perf;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.order.BoardingParties;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.hazards.waves.SeaStates;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.sailing.oars.Rowing;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.rigging.ChainplateBlock;
import com.richardsenger.piratesnships.ship.rigging.MastHoopBlock;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetItem;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.ship.template.TemplatePlacement;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.materialize.MaterializeConfig;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageGuns;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PERF1: a headless performance test of the mod with ships near a player on the waves. Registered only with the system
 * property {@value ModGameTests#PERF_PROPERTY}{@code =true} (see {@link ModGameTests#perfEnabled()}), so the merge stage
 * and CI never run it: {@code ./gradlew :neoforge:runGameTestServer -Ppirates_n_ships.gametest.only=PerformanceGameTests
 * -Ppirates_n_ships.perf=true}.
 *
 * <p>The scene, in a 160 × 160 basin 10 blocks deep with a ROUGH sea held and a light wind: a player (the harness's
 * mock server player, so the mod's syncs reach a connection) stands on the quarterdeck of an assembled starter sloop
 * with railings along its waist, three ratline nets and a boarding net; six crew aboard, two rowing, two at stations
 * (sail winch, bilge pump), two idle (they lean on the rail). Around it {@code N} materialised voyages on the armed
 * templates (alternately pirate raiders and navy patrols, with their crews, fighters, stocked and loaded guns), plus a
 * merchant convoy and a navy patrol. The patrol and the first raider fire at each other (gunnery on a target), three
 * of the raider's fighters are boarding the convoy. After {@value #SETTLE} ticks of settling the test measures
 * {@value #MEASURE} ticks: every second it reads the last 20 tick times from the server's ring and reports
 * min/median/p95/max of the per-tick and the per-second MSPT, the entities and ships; the N = 8 run also records
 * vanilla's profiler (two 10 s metrics recordings, the {@code /perf} recorder) and samples the server thread's stack
 * for 20 s ({@link StackSampler}) to attribute the tick to the mod's methods. Reports go to the directory of
 * {@value #DIR_PROPERTY} (default {@code perf} in the run directory) and the log. The N = 4 run fails when its p95
 * per-second MSPT exceeds {@code perf.budget_ms}.
 */
public final class PerformanceGameTests {

    /** Directory for the reports (system property; default {@code perf} in the server's run directory). */
    public static final String DIR_PROPERTY = "pirates_n_ships.perf.dir";

    private static final String BATCH = "pirates_n_ships_config_perf_n";
    private static final int SIZE = 160;
    /** Water from y=2 up to this row (10 deep: the starter sloop draws 4.3). */
    private static final int SEA_TOP = 11;
    /** Ticks between the end of the setup and the start of the measurement (crew reach the rail, oars, guns). */
    static final int SETTLE = 300;
    /** Measured ticks: 60 s. */
    static final int MEASURE = 1200;
    /** The profiled window of the N = 8 run, from the start of the measurement [ticks]. */
    private static final int PROFILE_FROM = 300;
    private static final int PROFILE_TICKS = 400;
    private static final long SAMPLE_INTERVAL_NANOS = 2_000_000L;

    /** Template positions on the starter sloop (bow north, rotation NONE: template = world offset from the origin). */
    private static final BlockPos HELM = new BlockPos(4, 8, 22);
    private static final int MAIN_DECK = 5;
    private static final int WAIST_FROM = 10;
    private static final int WAIST_TO = 16;

    /** Results of this server's runs, by N: [per-second mean, per-second median, ships]. */
    private static final Map<Integer, double[]> RUNS = new ConcurrentHashMap<>();

    private PerformanceGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!ModGameTests.perfEnabled()) return List.of();
        return ModGameTests.of(PerformanceGameTests.class);
    }

    // ------------------------------------------------------------------ tests

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = SETUP_LIMIT + SETTLE + MEASURE + 400, batch = BATCH + "1")
    public static void oneShipNearThePlayer(GameTestHelper h) {
        run(h, 1);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = SETUP_LIMIT + SETTLE + MEASURE + 400, batch = BATCH + "4")
    public static void fourShipsNearThePlayer(GameTestHelper h) {
        run(h, 4);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = SETUP_LIMIT + SETTLE + MEASURE + 400, batch = BATCH + "8")
    public static void eightShipsNearThePlayer(GameTestHelper h) {
        run(h, 8);
    }

    /** The setup takes a few ticks (basin, player ship, voyages one per tick). */
    private static final int SETUP_LIMIT = 40;

    // ------------------------------------------------------------------ the scene

    /** The NPC slots: ship centres (test-relative x, z), bows north. The player's sloop lies at x 76..84, z 127..155. */
    private static final int[][] SLOTS = {
            {58, 141}, {36, 141}, {102, 141}, {124, 141}, {14, 141}, {146, 141},
            {14, 60}, {36, 60}, {58, 60}, {102, 60}, {124, 60}, {146, 60}};

    private record Npc(Voyage voyage, ShipBody ship, String role) { }

    private static final class Scene {
        final int n;
        Player player;
        boolean serverPlayer;
        ShipBody sloop;
        final List<CrewMember> crew = new ArrayList<>();
        final List<Npc> npcs = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        int nets;
        int boarders;
        final Set<UUID> balls = new HashSet<>();

        Scene(int n) {
            this.n = n;
        }
    }

    private static void run(GameTestHelper h, int n) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        ConfigOverrides.during(h, MaterializeConfig.MAX_MATERIALIZED, 16);
        long hold = SETUP_LIMIT + SETTLE + MEASURE + 600;
        Vec3 mid = h.absoluteVec(new Vec3(SIZE / 2.0, 0, SIZE / 2.0));
        SeaStates.set(level, SeaState.ROUGH, 90.0, level.getGameTime() + hold,
                new WaveField.Origin(level.getGameTime() - h.getTick(), mid.x, mid.z));
        WindOverride.set(level.dimension().location().toString(), 90.0, 3.0, level.getGameTime() + hold);
        basin(h);
        Scene s = new Scene(n);
        playerShip(h, s);
        // the voyages one per tick, so a slow assembly does not pile up in one tick
        int count = n + 2;
        for (int i = 0; i < count; i++) {
            final int index = i;
            h.runAtTickTime(2 + i, () -> s.npcs.add(npc(h, index)));
        }
        h.runAtTickTime(3 + count, () -> {
            engage(h, s);
            crew(h, s);
        });
        int start = 3 + count + SETTLE;
        measure(h, s, start);
        h.runAtTickTime(start + MEASURE + 5, () -> {
            try {
                finish(h, s);
            } finally {
                cleanup(h, s);
            }
        });
    }

    /** The 160 × 160 stone basin, water y=2..{@value #SEA_TOP}. */
    private static void basin(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean wall = x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1;
                level.setBlock(h.absolutePos(new BlockPos(x, 1, z)), stone, Block.UPDATE_CLIENTS);
                for (int y = 2; y <= SEA_TOP + 1; y++) {
                    level.setBlock(h.absolutePos(new BlockPos(x, y, z)), wall ? stone : y <= SEA_TOP ? water : air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * The player's starter sloop: placed bow north, the waist's bulwarks (x 0 and 8, z 10..16) turned into railings,
     * a boarding net on the port rail and three ratline nets to the mast, then assembled; the player on the quarterdeck.
     */
    private static void playerShip(GameTestHelper h, Scene s) {
        ServerLevel level = h.getLevel();
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(level, ShipTemplates.STARTER_SLOOP_ID,
                h.absolutePos(new BlockPos(80, SEA_TOP + 1, 157)), Direction.NORTH, true, false, null);
        if (!r.outcome().success || r.origin() == null) throw new GameTestAssertException("sloop not placed: " + r.outcome());
        BlockPos o = r.origin();
        if (r.rotation() != Rotation.NONE) throw new GameTestAssertException("sloop turned " + r.rotation());
        for (int z = WAIST_FROM; z <= WAIST_TO; z++) {
            railing(level, o.offset(0, MAIN_DECK, z), Direction.WEST);
            railing(level, o.offset(8, MAIN_DECK, z), Direction.EAST);
        }
        for (int z = WAIST_FROM; z <= WAIST_TO; z++) {
            for (int x : new int[] {0, 8}) {
                BlockPos p = o.offset(x, MAIN_DECK, z);
                level.setBlock(p, RailingBlock.withPosts(level.getBlockState(p), level, p), Block.UPDATE_ALL);
            }
        }
        // the boarding net: chainplates on the port rail at z 11 and 15
        BlockPos netA = o.offset(0, MAIN_DECK + 1, 11), netB = o.offset(0, MAIN_DECK + 1, 15);
        level.setBlock(netA, chainplate(Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(netB, chainplate(Direction.NORTH), Block.UPDATE_ALL);
        s.nets += string(level, netA, netB, "boarding net", RatlineNetItem.KEY_BOARDING_STRUNG, s);
        // three ratline nets from the main deck to hoops on the mast (x 4, z 13)
        int[][] ratlines = {{2, 13, 3, 13, 'W'}, {6, 13, 5, 13, 'E'}, {4, 16, 4, 14, 'S'}};
        for (int[] rl : ratlines) {
            BlockPos plate = o.offset(rl[0], MAIN_DECK, rl[1]);
            BlockPos hoop = o.offset(rl[2], MAIN_DECK + 3, rl[3]);
            Direction facing = rl[4] == 'W' ? Direction.WEST : rl[4] == 'E' ? Direction.EAST : Direction.SOUTH;
            level.setBlock(plate, chainplate(facing.getOpposite()), Block.UPDATE_ALL);
            level.setBlock(hoop, RiggingContent.MAST_HOOP.get().defaultBlockState().setValue(MastHoopBlock.FACING, facing), Block.UPDATE_ALL);
            s.nets += string(level, plate, hoop, "ratline net " + (char) rl[4], RatlineNetItem.KEY_STRUNG, s);
        }
        BlockPos helm = TemplatePlacement.toWorld(HELM, Rotation.NONE, o);
        AssemblyResult a = ShipAssembler.assemble(level, helm, null);
        if (a.shipId() == null) throw new GameTestAssertException("sloop not assembled: " + a);
        ShipTestCleanup.track(h, a.shipId());
        s.sloop = SableShips.byId(level, a.shipId());
        if (s.sloop == null) throw new GameTestAssertException("no sloop");
        // the player on the quarterdeck, two blocks forward of the helm
        Vec3 deck = onDeck(s.sloop, new BlockPos(4, 8, 20));
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.teleportTo(level, deck.x, deck.y, deck.z, 0, 0);
            s.player = p;
            s.serverPlayer = true;
        } catch (RuntimeException e) {
            s.notes.add("mock server player failed (" + e + "), a plain mock player stands in");
            Player p = h.makeMockPlayer(GameType.CREATIVE);
            p.moveTo(deck.x, deck.y, deck.z, 0, 0);
            level.addFreshEntity(p);
            s.player = p;
        }
    }

    private static BlockState chainplate(Direction facing) {
        return RiggingContent.CHAINPLATE.get().defaultBlockState().setValue(ChainplateBlock.FACE, AttachFace.FLOOR)
                .setValue(ChainplateBlock.FACING, facing);
    }

    private static void railing(ServerLevel level, BlockPos pos, Direction edge) {
        BlockState st = RailingContent.RAILINGS.get(ShipWood.OAK).defaultBlockState().setValue(RailingBlock.edge(edge), true);
        level.setBlock(pos, st, Block.UPDATE_ALL);
    }

    /** Strings a net from {@code a} to {@code b}; 1 when it answered {@code expect}, else 0 with a note. */
    private static int string(ServerLevel level, BlockPos a, BlockPos b, String what, String expect, Scene s) {
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get(), 4);
        String first = key(RatlineNetItem.use(level, stack, a, null));
        String second = first.equals(RatlineNetItem.KEY_MARKED) ? key(RatlineNetItem.use(level, stack, b, null)) : first;
        if (second.equals(expect)) return 1;
        s.notes.add(what + " not strung: " + second);
        return 0;
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** World position standing on template cell {@code cell} of a starter-sloop-shaped ship (helm at {@link #HELM}). */
    private static Vec3 onDeck(ShipBody ship, BlockPos cell) {
        BlockPos helm = ShipHelm.steering(ship);
        if (helm == null) throw new GameTestAssertException("helmless ship");
        return ship.toWorld(Vec3.atBottomCenterOf(helm.offset(cell.subtract(HELM))).add(0, 0.05, 0));
    }

    /**
     * NPC {@code index}: 0 the navy patrol, 1 the first pirate raider (they fire at each other), 2 the merchant convoy
     * (boarded), from 3 on the other armed ships, alternately navy and pirate. A SAILING test voyage through its slot,
     * heading north, materialised.
     */
    private static Npc npc(GameTestHelper h, int index) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        VoyageKind kind;
        Faction faction;
        ResourceLocation template;
        Map<ResourceLocation, Integer> cargo = Map.of();
        String role;
        if (index == 0) {
            kind = VoyageKind.PATROL; faction = Faction.NAVY; template = ShipTemplates.NAVY_SLOOP_ARMED_ID; role = "patrol (gunnery)";
        } else if (index == 2) {
            kind = VoyageKind.CONVOY; faction = Faction.MERCHANTS; template = ShipTemplates.STARTER_SLOOP_ID; role = "convoy (boarded)";
            Map<ResourceLocation, Integer> c = new LinkedHashMap<>();
            c.put(TradeGoods.SUGAR, 64);
            c.put(TradeGoods.IRON, 32);
            cargo = c;
        } else {
            boolean pirate = index == 1 || index % 2 == 0;
            kind = pirate ? VoyageKind.RAID : VoyageKind.PATROL;
            faction = pirate ? Faction.PIRATES : Faction.NAVY;
            template = pirate ? ShipTemplates.PIRATE_SLOOP_ARMED_ID : ShipTemplates.NAVY_SLOOP_ARMED_ID;
            role = (pirate ? "pirate raider" : "navy patrol") + (index == 1 ? " (gunnery, boarding)" : "");
        }
        BlockPos m = h.absolutePos(new BlockPos(SLOTS[index][0], SEA_TOP, SLOTS[index][1]));
        List<Lane.Point> route = List.of(new Lane.Point(m.getX(), m.getZ() + 100), new Lane.Point(m.getX(), m.getZ() - 100));
        ResourceLocation from = Constants.id("gametest/perf1_" + UUID.randomUUID().toString().substring(0, 8));
        Voyage v = Voyage.depart(UUID.randomUUID(), kind, faction, template, from, from, route, cargo, level.getGameTime())
                .withProgress(100);
        VoyageData.get(server).put(v);
        Materializer.Outcome o = Materializer.materialize(server, v.id());
        Voyage after = Voyages.get(server, v.id()).orElseThrow();
        after.shipId().ifPresent(id -> ShipTestCleanup.track(h, id));
        if (o != Materializer.Outcome.SPAWNED) throw new GameTestAssertException("voyage " + index + " (" + role + "): " + o);
        ShipBody ship = SableShips.byId(level, after.shipId().orElseThrow());
        if (ship == null) throw new GameTestAssertException("voyage " + index + ": no ship");
        return new Npc(after, ship, role);
    }

    /** The patrol and the first raider fire at each other; three of the raider's fighters board the convoy. */
    private static void engage(GameTestHelper h, Scene s) {
        ServerLevel level = h.getLevel();
        Npc patrol = s.npcs.get(0), raider = s.npcs.get(1), convoy = s.npcs.get(2);
        for (Npc[] pair : new Npc[][] {{patrol, raider}, {raider, patrol}}) {
            Vec3 t = Materializer.centre(pair[1].ship());
            int seated = VoyageGuns.manLoaded(level, pair[0].ship(), pair[0].voyage().id(), t.x, t.z);
            Gunnery.set(level, pair[0].ship(), GunneryState.target(pair[1].ship().id()));
            s.notes.add(pair[0].role() + ": " + seated + " gunners seated at loaded guns, firing at the " + pair[1].role());
        }
        List<LivingEntity> fighters = VoyageCrew.alive(level, raider.ship(), raider.voyage().id(), VoyageCrew.FIGHTER_TAG);
        BlockPos[] spots = {new BlockPos(3, 5, 15), new BlockPos(5, 5, 15), new BlockPos(3, 5, 18)};
        for (LivingEntity e : fighters) {
            if (s.boarders >= spots.length || !(e instanceof Mob mob)) continue;
            Vec3 at = onDeck(convoy.ship(), spots[s.boarders]);
            mob.teleportTo(at.x, at.y, at.z);
            BoardingParties.enlist(level, raider.ship().id(), convoy.ship().id(), mob, true);
            s.boarders++;
        }
        s.notes.add(s.boarders + " of the raider's " + fighters.size() + " fighters boarding the convoy");
    }

    /** Six crew on the player's sloop: two row, two at stations, two idle (they lean on the rail). */
    private static void crew(GameTestHelper h, Scene s) {
        ServerLevel level = h.getLevel();
        s.crew.add(hand(level, s.sloop, new BlockPos(2, MAIN_DECK, 14)));
        s.crew.add(hand(level, s.sloop, new BlockPos(6, MAIN_DECK, 14)));
        Rowing.Outcome row = Rowing.row(level, s.sloop);
        s.notes.add("row: " + row.result() + ", " + row.rowers() + " rowers");
        BlockPos helm = ShipHelm.steering(s.sloop);
        CrewMember winch = hand(level, s.sloop, new BlockPos(3, MAIN_DECK, 15));
        CrewMember pump = hand(level, s.sloop, new BlockPos(5, MAIN_DECK, 18));
        s.crew.add(winch);
        s.crew.add(pump);
        s.notes.add("winch: " + CrewStations.assign(level, winch, helm.offset(new BlockPos(4, 5, 14).subtract(HELM))));
        s.notes.add("bilge pump: " + CrewStations.assign(level, pump, helm.offset(new BlockPos(6, 5, 17).subtract(HELM))));
        s.crew.add(hand(level, s.sloop, new BlockPos(3, MAIN_DECK, 10)));
        s.crew.add(hand(level, s.sloop, new BlockPos(5, MAIN_DECK, 10)));
    }

    private static CrewMember hand(ServerLevel level, ShipBody ship, BlockPos cell) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(level);
        if (c == null) throw new GameTestAssertException("no crew member");
        Vec3 p = onDeck(ship, cell);
        c.moveTo(p.x, p.y, p.z, 0, 0);
        level.addFreshEntity(c);
        return c;
    }

    // ------------------------------------------------------------------ measuring

    private static final class Measurement {
        final List<Double> ticks = new ArrayList<>();
        final List<Double> seconds = new ArrayList<>();
        /** Per-second means outside the profiled window (equal to {@link #seconds} without profiling). */
        final List<Double> clean = new ArrayList<>();
        int lastServerTick;
        int maxEntities;
        int maxModEntities;
        int maxShips;
        final Map<String, Integer> byType = new LinkedHashMap<>();
        final List<List<PerfStats.Entry>> profilerWindows = new ArrayList<>();
        final List<String> profilerReports = new ArrayList<>();
        boolean recording;
        int recordings;
        StackSampler sampler;
        PerfStats.Samples samples;
        String rowing = "";
        String leaning = "";
    }

    private static void measure(GameTestHelper h, Scene s, int start) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        Measurement m = new Measurement();
        boolean profile = s.n == 8;
        h.runAtTickTime(start, () -> m.lastServerTick = server.getTickCount());
        h.onEachTick(() -> {
            long t = h.getTick() - start;
            if (t <= 0 || t > MEASURE) return;
            countBalls(level, s);
            if (profile) profileTick(server, m, (int) t);
            if (t % 20 != 0) return;
            int now = server.getTickCount();
            double[] last = PerfStats.ticksSince(server.getTickTimesNanos(), m.lastServerTick, now);
            m.lastServerTick = now;
            double sum = 0;
            for (double d : last) {
                m.ticks.add(d);
                sum += d;
            }
            double mean = last.length == 0 ? 0 : sum / last.length;
            m.seconds.add(mean);
            boolean profiled = profile && t > PROFILE_FROM && t <= PROFILE_FROM + PROFILE_TICKS + 20;
            if (!profiled) m.clean.add(mean);
            census(level, s, m);
        });
        STATE.put(s, m);
    }

    private static final Map<Scene, Measurement> STATE = new ConcurrentHashMap<>();

    /** Starts the stack sampler and vanilla's metrics recorder at the window's start, a second recording after the first. */
    private static void profileTick(MinecraftServer server, Measurement m, int t) {
        if (t == PROFILE_FROM) {
            m.sampler = new StackSampler(server.getRunningThread(), SAMPLE_INTERVAL_NANOS);
            m.sampler.start();
        }
        if (t == PROFILE_FROM + PROFILE_TICKS && m.sampler != null) {
            m.samples = m.sampler.stop();
            m.sampler = null;
        }
        boolean inWindow = t >= PROFILE_FROM && t < PROFILE_FROM + PROFILE_TICKS;
        if (inWindow && !m.recording && m.recordings < 2 && !server.isRecordingMetrics()) {
            m.recording = true;
            m.recordings++;
            server.startRecordingMetrics(results -> {
                m.recording = false;
                m.profilerWindows.add(modEntries(results));
                m.profilerReports.add(results.getProfilerResults());
            }, path -> Constants.LOG.info("[perf] metrics recording saved to {}", path));
        }
        if (t == PROFILE_FROM + PROFILE_TICKS && m.recording) server.finishRecordingMetrics();
    }

    /**
     * Every entry of vanilla's profiler tree under the tick whose path names the mod (entity and block entity types
     * {@code pirates_n_ships:…}), plus the tick's direct level sections, as shares of the tick.
     */
    private static List<PerfStats.Entry> modEntries(ProfileResults results) {
        List<PerfStats.Entry> out = new ArrayList<>();
        String tickPath = "root\u001etick";
        List<ResultField> top = results.getTimes(tickPath);
        if (top.isEmpty()) return out;
        double tickGlobal = top.get(0).globalPercentage;
        walk(results, tickPath, tickGlobal, 0, out);
        return out;
    }

    private static void walk(ProfileResults results, String path, double tickGlobal, int depth, List<PerfStats.Entry> out) {
        if (depth > 8) return;
        List<ResultField> fields = results.getTimes(path);
        for (int i = 1; i < fields.size(); i++) {
            ResultField f = fields.get(i);
            if (f.name.equals("unspecified")) continue;
            String child = path + '\u001e' + f.name;
            double share = tickGlobal <= 0 ? 0 : f.globalPercentage / tickGlobal;
            String shown = ProfileResults.demanglePath(child.substring("root\u001etick\u001e".length()));
            if (f.name.contains(Constants.MOD_ID) || depth <= 2) out.add(new PerfStats.Entry(shown, (int) f.count, share));
            if (share >= 0.002) walk(results, child, tickGlobal, depth + 1, out);
        }
    }

    private static void countBalls(ServerLevel level, Scene s) {
        for (Entity e : level.getEntities(CannonContent.CANNONBALL.get(), e -> true)) s.balls.add(e.getUUID());
    }

    private static void census(ServerLevel level, Scene s, Measurement m) {
        int all = 0, mod = 0;
        Map<String, Integer> types = new LinkedHashMap<>();
        for (Entity e : level.getAllEntities()) {
            all++;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if (id.getNamespace().equals(Constants.MOD_ID)) mod++;
            types.merge(id.toString(), 1, Integer::sum);
        }
        if (all > m.maxEntities) {
            m.maxEntities = all;
            m.byType.clear();
            m.byType.putAll(types);
        }
        m.maxModEntities = Math.max(m.maxModEntities, mod);
        m.maxShips = Math.max(m.maxShips, SableShips.all(level).size());
        long leaners = s.crew.stream().filter(CrewMember::isLeaning).count();
        m.rowing = Rowing.rowers(s.sloop.id()).size() + " rowing";
        m.leaning = leaners + " leaning";
    }

    // ------------------------------------------------------------------ the report

    private static void finish(GameTestHelper h, Scene s) {
        Measurement m = STATE.remove(s);
        if (m == null) throw new GameTestAssertException("no measurement");
        if (m.sampler != null) m.samples = m.sampler.stop();
        double[] ticks = m.ticks.stream().mapToDouble(Double::doubleValue).toArray();
        double[] seconds = m.seconds.stream().mapToDouble(Double::doubleValue).toArray();
        double[] clean = m.clean.stream().mapToDouble(Double::doubleValue).toArray();
        PerfStats.Summary perTick = PerfStats.summarize(ticks);
        PerfStats.Summary perSecond = PerfStats.summarize(seconds);
        PerfStats.Summary cleanSeconds = PerfStats.summarize(clean);
        int ships = m.maxShips;
        RUNS.put(s.n, new double[] {cleanSeconds.mean(), cleanSeconds.median(), ships});

        StringBuilder r = new StringBuilder();
        r.append(String.format(Locale.ROOT, "PERF1 N=%d (%d NPC armed ships + convoy + patrol + the player's sloop)%n", s.n, s.n));
        r.append(String.format(Locale.ROOT, "player: %s; sea ROUGH, wind 3 from the east; %d s measured after %d ticks of settling%n",
                s.serverPlayer ? "mock server player" : "mock player", MEASURE / 20, SETTLE));
        r.append("per-tick MSPT:              ").append(perTick.row()).append('\n');
        r.append("per-second MSPT:            ").append(perSecond.row()).append('\n');
        r.append("per-second MSPT, unprofiled: ").append(cleanSeconds.row()).append('\n');
        r.append(String.format(Locale.ROOT, "ships (Sable sub-levels): %d, entities: %d (mod: %d)%n", ships, m.maxEntities, m.maxModEntities));
        r.append("entities by type: ").append(m.byType).append('\n');
        r.append(String.format(Locale.ROOT, "player's crew: %s, %s; nets strung: %d of 4; boarders: %d; cannonballs fired: %d%n",
                m.rowing, m.leaning, s.nets, s.boarders, s.balls.size()));
        for (Npc npc : s.npcs) {
            Vec3 c = npc.ship().isRemoved() ? null : Materializer.centre(npc.ship());
            r.append("  ").append(npc.role()).append(c == null ? " removed" : String.format(Locale.ROOT, " at %.0f %.0f", c.x, c.z)).append('\n');
        }
        s.notes.forEach(note -> r.append("note: ").append(note).append('\n'));
        r.append(String.format(Locale.ROOT, "summary n=%d ships=%d mean_ms=%.3f median_ms=%.3f p95_ms=%.3f tick_p95_ms=%.3f%n",
                s.n, ships, cleanSeconds.mean(), cleanSeconds.median(), cleanSeconds.p95(), perTick.p95()));
        perShip(r, s.n);
        if (m.samples != null) {
            PerfStats.Samples smp = m.samples;
            r.append(String.format(Locale.ROOT, "%nstack samples (every %.0f ms over %d ticks): %d, %d in a tick; mod on the stack in %.1f%%, Sable in %.1f%%%n",
                    SAMPLE_INTERVAL_NANOS / 1e6, PROFILE_TICKS, smp.total(), smp.busy(), smp.modShare() * 100, smp.sableShare() * 100));
            r.append("top mod methods, inclusive (share of tick samples):\n");
            smp.topInclusive(40).forEach(e -> r.append("  ").append(e.row()).append('\n'));
            r.append("top mod methods, deepest mod frame (self + vanilla/JDK below):\n");
            smp.topSelf(25).forEach(e -> r.append("  ").append(e.row()).append('\n'));
            r.append("mod packages, inclusive:\n");
            smp.topAreas(25).forEach(e -> r.append("  ").append(e.row()).append('\n'));
        }
        if (!m.profilerWindows.isEmpty()) {
            r.append(String.format(Locale.ROOT, "%nvanilla profiler (%d metrics recordings of up to 10 s), sections under the tick, mean share:%n",
                    m.profilerWindows.size()));
            PerfStats.mergeWindows(m.profilerWindows, 40).forEach(e -> r.append("  ").append(e.row()).append('\n'));
        }
        String report = r.toString();
        Constants.LOG.info("[perf]\n{}", report);
        write("perf-" + s.n + ".txt", report);
        for (int i = 0; i < m.profilerReports.size(); i++) write("profile-n" + s.n + "-" + (i + 1) + ".txt", m.profilerReports.get(i));

        if (s.n == 4) {
            double budget = PerfConfig.BUDGET_MS.get();
            h.assertTrue(perSecond.p95() < budget, String.format(Locale.ROOT, "p95 MSPT %.2f ms at N=4 is over the budget of %.1f ms",
                    perSecond.p95(), budget));
        }
        h.succeed();
    }

    /** The per-ship cost once this server has measured N = 1 and N = 8 (else read from an earlier report file). */
    private static void perShip(StringBuilder r, int n) {
        double[] one = RUNS.containsKey(1) ? RUNS.get(1) : readSummary(1);
        double[] eight = RUNS.containsKey(8) ? RUNS.get(8) : readSummary(8);
        if (one == null || eight == null) return;
        double mean = PerfStats.perShip(eight[0], (int) eight[2], one[0], (int) one[2]);
        double median = PerfStats.perShip(eight[1], (int) eight[2], one[1], (int) one[2]);
        String line = String.format(Locale.ROOT, "per-ship cost (N=8 vs N=1, %d vs %d ships): %.3f ms/tick by mean, %.3f by median%n",
                (int) eight[2], (int) one[2], mean, median);
        r.append(line);
        if (n != 1 && n != 8) return;
        write("perf-summary.txt", line);
    }

    private static double[] readSummary(int n) {
        try {
            for (String line : Files.readAllLines(dir().resolve("perf-" + n + ".txt"))) {
                if (!line.startsWith("summary ")) continue;
                Map<String, String> kv = new LinkedHashMap<>();
                for (String part : line.substring(8).split(" ")) {
                    String[] p = part.split("=");
                    if (p.length == 2) kv.put(p[0], p[1]);
                }
                return new double[] {Double.parseDouble(kv.get("mean_ms")), Double.parseDouble(kv.get("median_ms")),
                        Double.parseDouble(kv.get("ships"))};
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static Path dir() {
        return Path.of(System.getProperty(DIR_PROPERTY, "perf"));
    }

    private static void write(String name, String text) {
        try {
            Path d = dir();
            Files.createDirectories(d);
            Files.writeString(d.resolve(name), text);
            Constants.LOG.info("[perf] wrote {}", d.resolve(name).toAbsolutePath());
        } catch (IOException e) {
            Constants.LOG.warn("[perf] could not write {}: {}", name, e.toString());
        }
    }

    private static void cleanup(GameTestHelper h, Scene s) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        if (server.isRecordingMetrics()) server.finishRecordingMetrics();
        for (Npc npc : s.npcs) {
            Gunnery.clear(npc.ship().id());
            BoardingParties.forget(level, npc.ship().id());
            Voyages.end(server, npc.voyage().id(), VoyageEnd.CANCELLED);
            VoyageCrew.discard(level, npc.voyage().id());
        }
        Rowing.stop(level, s.sloop.id());
        for (CrewMember c : s.crew) {
            if (c.getVehicle() != null) c.getVehicle().discard();
            c.discard();
        }
        if (s.player instanceof ServerPlayer sp) server.getPlayerList().remove(sp);
        else if (s.player != null) s.player.discard();
        level.getEntities(CannonContent.CANNONBALL.get(), e -> true).forEach(Entity::discard);
        SeaStates.clear(level);
        WindOverride.clear(level.dimension().location().toString());
    }
}
