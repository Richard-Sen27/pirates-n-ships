package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.hammock.CrewInfo;
import com.richardsenger.piratesnships.crew.hammock.RestRules;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Deserters walking off at a port (CRW2, docs/design.md §7.3, §7.5) on the 5×4×5 test ship of {@link StationGameTests}
 * resting on land (hull x, z = 17..21 relative). Provisions and wages are off and the hammock rule is 0, so only the
 * morale set by the test counts. Every test changes config or the clock, so each runs in a batch of its own.
 * <p>
 * The fake port's box lies at relative (2..8, 2..8, 2..8), nine blocks off the ship; with {@code desert_port_radius}
 * 16 the ship is at the port, with 0 it is not. Its berth is at relative (5, 5, 5), on the basin's stone floor.
 */
public final class DesertionGameTests {

    private static final String CONFIG_BATCH = "pirates_n_ships_config_crew_";
    /** Ticks one forced dawn takes. */
    private static final int DAWN = 6;
    private static final BlockPos BERTH = new BlockPos(5, 5, 5);

    private DesertionGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(DesertionGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ship(Fixture f, CrewMember a, CrewMember b) { }

    /** Provisions, wages and the hammock rule off. */
    private static void isolate(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.WAGES_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.NO_HAMMOCK_PER_NIGHT, 0);
        ConfigOverrides.during(h, CrewConfig.HAMMOCK_REST_PER_NIGHT, 0);
    }

    /**
     * Runs {@code body} once Sable has filled the ship's world bounds (they read empty for a tick or more after assembly,
     * longer under load), so the crew lookup by the ship's box finds the members placed on deck.
     */
    private static void whenReady(GameTestHelper h, Ship s, Runnable body) {
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            net.minecraft.world.phys.AABB b = s.f().ship().worldBounds();
            if (b.getXsize() > 1 && b.getZsize() > 1) body.run(); else h.runAfterDelay(1, poll[0]);
        };
        h.runAfterDelay(2, poll[0]);
    }

    private static Ship ship(GameTestHelper h) {
        isolate(h);
        morning(h);
        Fixture f = StationGameTests.ship(h, false, x -> { });
        return new Ship(f, onDeck(h, f, 20, 20), onDeck(h, f, 18, 19));
    }

    private static CrewMember onDeck(GameTestHelper h, Fixture f, int x, int z) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm().offset(x - 19, 0, z - 18)));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    private static void morning(GameTestHelper h) {
        long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
        h.getLevel().setDayTime(today + 1000L);
    }

    /** One forced dawn from tick {@code start} (morning, midnight, next morning); done by {@code start + DAWN}. */
    private static void dawnAt(GameTestHelper h, int start) {
        h.runAfterDelay(start + 1, () -> morning(h));
        h.runAfterDelay(start + 2, () -> {
            long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
            h.getLevel().setDayTime(today + 18000L);
        });
        h.runAfterDelay(start + 4, () -> {
            long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
            h.getLevel().setDayTime(today + RestRules.DAY + 1000L);
        });
    }

    /** The fake port: box at relative (2..8, 2..8, 2..8), one berth at {@link #BERTH}. */
    private static Port port(GameTestHelper h) {
        Port port = new Port(Constants.id("gametest/desertion_" + UUID.randomUUID().toString().substring(0, 8)),
                PortKind.SEAFARER_VILLAGE, h.getLevel().dimension(), h.absolutePos(new BlockPos(5, 5, 5)),
                BoundingBox.fromCorners(h.absolutePos(new BlockPos(2, 2, 2)), h.absolutePos(new BlockPos(8, 8, 8))),
                Climate.TEMPERATE, List.of(new Berth(h.absolutePos(BERTH), Direction.NORTH)));
        PortService.register(h.getLevel().getServer(), port);
        return port;
    }

    private static List<Sailor> sailors(GameTestHelper h, AABB box) {
        return h.getLevel().getEntitiesOfClass(Sailor.class, box, SeafarerMob::isAlive);
    }

    private static AABB around(GameTestHelper h, Ship s) {
        return CrewStations.worldBox(s.f().ship(), 4);
    }

    private static boolean told(List<Component> lines, String key) {
        return lines.stream().anyMatch(c -> containsKey(c, key));
    }

    private static boolean containsKey(Component c, String key, Object... args) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(key) && (args.length == 0 || Arrays.equals(t.getArgs(), args))) return true;
            for (Object a : t.getArgs()) {
                if (a instanceof Component inner && containsKey(inner, key, args)) return true;
            }
        }
        for (Component sib : c.getSiblings()) {
            if (containsKey(sib, key, args)) return true;
        }
        return false;
    }

    private static void cleanup(GameTestHelper h, Ship s) {
        for (CrewMember c : List.of(s.a(), s.b())) {
            if (c.getVehicle() != null) c.getVehicle().discard();
            c.discard();
        }
        h.getLevel().getEntitiesOfClass(SeafarerMob.class, new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(40, 20, 40),
                SeafarerMob::isAlive).forEach(SeafarerMob::discard);
    }

    // ------------------------------------------------------------------ tests

    /**
     * {@code at_port_only} (default): one member at morale 10, {@code desert_days} 2. After the second low dawn it is
     * marked as deserting instead of leaving: still crew, the owner told "… will walk off at the next port", its
     * whistle line says "deserting". A third low dawn counts one deserting day; away from any port it stays aboard.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 150, batch = CONFIG_BATCH + "desertion_mark")
    public static void dawnMarksTheDeserter(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.DESERT_PORT_RADIUS, 0);
        Ship s = ship(h);
        s.a().setStoredMorale(10);
        dawnAt(h, 0);
        dawnAt(h, DAWN + 2);
        h.runAfterDelay(2 * DAWN + 3, () -> {
            ShipDayTick.DayReport r = ShipDayTick.lastReport(s.f().ship().id());
            h.assertTrue(r != null, "no day tick ran");
            h.assertTrue(s.a().isAlive() && s.a().isDeserting(), "after two low dawns: alive " + s.a().isAlive() + ", deserting " + s.a().isDeserting());
            h.assertTrue(r.deserting().equals(List.of(s.a().getUUID())) && r.deserters().isEmpty(), "marked " + r.deserting() + ", left " + r.deserters());
            h.assertTrue(told(r.ownerLines(), UpkeepText.DESERTING), "owner lines: " + r.ownerLines());
            h.assertTrue(!s.b().isDeserting(), "the content member deserts");
            h.assertTrue(ShipBunks.crewOf(h.getLevel(), s.f().ship()).contains(s.a()), "the deserter is no longer crew");
            Component line = CrewInfo.crewLine(h.getLevel(), s.a());
            h.assertTrue(containsKey(line, UpkeepText.STATUS_DESERTING), "the whistle line does not say deserting: " + line);
        });
        dawnAt(h, 2 * DAWN + 4);
        h.runAfterDelay(3 * DAWN + 5, () -> {
            h.assertTrue(s.a().isDeserting() && s.a().desertingDays() == 1, "deserting days after one more dawn: " + s.a().desertingDays());
            Desertions.Result r = Desertions.run(h.getLevel());
            h.assertTrue(r.atPort().isEmpty() && r.anywhere().isEmpty(), "left away from a port: " + r);
            h.assertTrue(s.a().isAlive() && s.a().isDeserting(), "the deserter left the ship at sea");
            cleanup(h, s);
            h.succeed();
        });
    }

    /**
     * A deserting member on a ship away from any port stays aboard; once a port is registered nine blocks off the ship
     * ({@code desert_port_radius} 16) it walks off: a neutral sailor stands on the quay at the berth, the member is gone,
     * the other member stays.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "desertion_port")
    public static void deserterWalksOffAtThePort(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.DESERT_PORT_RADIUS, 16);
        Ship s = ship(h);
        s.a().setDeserting(true, 0);
        whenReady(h, s, () -> {
            h.assertTrue(Desertions.portOf(h.getLevel(), s.f().ship(), 16).isEmpty(), "a port near the test ship before the test registered one");
            Desertions.Result r = Desertions.run(h.getLevel());
            h.assertTrue(r.atPort().isEmpty() && r.anywhere().isEmpty(), "left without a port: " + r);
            h.assertTrue(s.a().isAlive() && s.a().isDeserting(), "the deserter is gone without a port");
        });
        h.runAfterDelay(4, () -> {
            Port port = port(h);
            try {
                h.assertTrue(Desertions.portOf(h.getLevel(), s.f().ship(), 0).isEmpty(), "the port box itself reaches the ship");
                h.assertTrue(Desertions.portOf(h.getLevel(), s.f().ship(), 16).isPresent(), "the ship is not at the port within 16 blocks");
                Desertions.Result r = Desertions.run(h.getLevel());
                h.assertTrue(r.atPort().equals(List.of(s.a().getUUID())), "walked off: " + r);
                h.assertTrue(s.a().isRemoved(), "the deserter is still aboard");
                h.assertTrue(s.b().isAlive(), "the other member left too");
                Vec3 berth = Vec3.atBottomCenterOf(h.absolutePos(BERTH));
                List<Sailor> quay = sailors(h, new AABB(berth, berth).inflate(1.5));
                h.assertTrue(quay.size() == 1, "sailors on the quay: " + quay);
                h.assertTrue(sailors(h, around(h, s)).isEmpty(), "a sailor stayed on the ship");
            } finally {
                PortRegistry.get(h.getLevel().getServer()).remove(port.id());
                cleanup(h, s);
            }
            h.succeed();
        });
    }

    /** {@code desert_anywhere_after_days} 3: a member deserting for 3 dawns leaves at sea, as a sailor where it stood. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "desertion_anywhere")
    public static void deserterLeavesAnywhereAfterWaiting(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.DESERT_PORT_RADIUS, 0);
        Ship s = ship(h);
        s.a().setDeserting(true, 2);
        s.b().setDeserting(true, 3);
        whenReady(h, s, () -> {
            Desertions.Result r = Desertions.run(h.getLevel());
            h.assertTrue(r.anywhere().equals(List.of(s.b().getUUID())) && r.atPort().isEmpty(), "left: " + r);
            h.assertTrue(s.a().isAlive() && s.b().isRemoved(), "a alive " + s.a().isAlive() + ", b removed " + s.b().isRemoved());
            h.assertTrue(sailors(h, around(h, s)).size() == 1, "sailors where it stood: " + sailors(h, around(h, s)));
            cleanup(h, s);
            h.succeed();
        });
    }

    /** {@code crew.desertion.enabled = false}: a marked member never leaves, even at a port. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "desertion_off_port")
    public static void desertionOffKeepsTheCrew(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.DESERTION_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.DESERT_PORT_RADIUS, 16);
        Ship s = ship(h);
        s.a().setDeserting(true, 5);
        whenReady(h, s, () -> {
            Port port = port(h);
            try {
                Desertions.Result r = Desertions.run(h.getLevel());
                h.assertTrue(r.atPort().isEmpty() && r.anywhere().isEmpty(), "left with desertion off: " + r);
                h.assertTrue(s.a().isAlive(), "the member is gone");
            } finally {
                PortRegistry.get(h.getLevel().getServer()).remove(port.id());
                cleanup(h, s);
            }
            h.succeed();
        });
    }
}
