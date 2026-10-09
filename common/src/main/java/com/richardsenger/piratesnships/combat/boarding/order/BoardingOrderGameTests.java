package com.richardsenger.piratesnships.combat.boarding.order;

import com.mojang.authlib.GameProfile;
import com.richardsenger.piratesnships.combat.boarding.BoardingConfig;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlanks;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.npc.Gunnery;
import com.richardsenger.piratesnships.combat.cannon.npc.GunneryState;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.rigging.BoardingNets;
import com.richardsenger.piratesnships.ship.rigging.ChainplateBlock;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetAssist;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetItem;
import com.richardsenger.piratesnships.ship.rigging.RiggingConfig;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The "Board!" order (BRD3, docs/design.md §8.4) in a real server. Two closed 5×5 plank hulls of
 * {@link DryHullGameTests} float in one basin (ship A at x 2..6, ship B with {@code gap} blocks of water east of it),
 * decks at relative y 8. A carries a swivel gun at x 3, z 12 (a gun station: its crew stays). After {@link #SETTLE}
 * ticks the gangway is laid (where the test wants one) and the crew stand on A's deck; the order follows
 * {@link #ORDER} ticks later. A boarder that the fallback set down across (it took longer than
 * {@code boarding.cross_timeout_ticks}) fails a test that wants it to cross by itself. Every test with crew runs in a
 * batch of its own with desertion off ({@link #noDesertion}).
 */
public final class BoardingOrderGameTests {

    private static final String BATCH = "pirates_n_ships_config_boarding_order_";
    /** Ticks after assembly before the plank and the crew. */
    private static final int SETTLE = 40;
    /** Ticks after {@link #SETTLE} before the order: the crew land on the deck (Sable tracking) and the gunner sits. */
    private static final int ORDER = 30;
    /** Ticks the boarders get from the order to stand on the other deck. */
    private static final int CROSS_WITHIN = 200;

    private BoardingOrderGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BoardingOrderGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ships(Fixture a, Fixture b) {
        BlockPos swivel() {
            return a.helmPlot().offset(-1, 0, 1);
        }

        BlockPos chest() {
            return a.helmPlot().offset(1, 0, -1);
        }
    }

    /**
     * The crew stay ashore-proof: another test of the run may have set the clock to the night, and a dawn during this
     * test would run the upkeep day over the crew, who have no provisions here and would desert at once (replaced by
     * sailors). So every test with crew has its own batch and switches desertion off.
     */
    private static void noDesertion(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.DESERTION_ENABLED, false);
    }

    /** Ship A (with a swivel gun and a chest on deck) at x 2..6 and ship B with {@code gap} blocks of water between. */
    private static Ships twoShips(GameTestHelper h, int gap) {
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        h.setBlock(new BlockPos(3, 9, 12), CannonContent.SWIVEL_GUN.get());
        h.setBlock(new BlockPos(5, 9, 10), Blocks.CHEST);
        BlockPos helmB = DryHullGameTests.hull(h, 7 + gap, false);
        return new Ships(DryHullGameTests.assemble(h, helmA), DryHullGameTests.assemble(h, helmB));
    }

    /** Lays the gangway from the top of A's east gunwale onto B, facing east. */
    private static void plank(GameTestHelper h, Ships s) {
        BoardingPlanks.Laid laid = BoardingPlanks.lay(h.getLevel(), s.a().helmPlot().offset(2, -1, 0), Direction.UP, Direction.EAST);
        h.assertTrue(laid.outcome() == BoardingPlanks.Outcome.OK, "the gangway was refused: " + laid.outcome());
        h.assertTrue(s.b().ship().id().equals(laid.farShip()), "the gangway does not land on B");
    }

    /** A crew member standing on {@code f}'s deck at {@code (dx, dz)} from the helm. */
    private static CrewMember crew(GameTestHelper h, Fixture f, int dx, int dz) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = f.ship().toWorld(Vec3.atBottomCenterOf(f.helmPlot().offset(dx, 0, dz)).add(0, 0.1, 0));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setStationary(true); // no strolling off the small deck before the order
        h.getLevel().addFreshEntity(c);
        return c;
    }

    /** {@code n} (at most five) free hands on A's deck. */
    private static List<CrewMember> hands(GameTestHelper h, Ships s, int n) {
        int[][] at = {{-2, -2}, {0, -2}, {1, 1}, {-2, 0}, {2, -2}};
        List<CrewMember> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(crew(h, s.a(), at[i][0], at[i][1]));
        return out;
    }

    /** A gunner on A's deck beside the swivel gun, assigned to it (it walks there and sits). */
    private static CrewMember gunner(GameTestHelper h, Ships s) {
        CrewMember g = crew(h, s.a(), -2, 1);
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), g, s.swivel());
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "the gunner was not assigned: " + r);
        return g;
    }

    /** A husk (a monster that does not burn in daylight) of {@code health} on B's deck. */
    private static Husk husk(GameTestHelper h, Ships s, float health) {
        Husk z = EntityType.HUSK.create(h.getLevel());
        if (z == null) throw new AssertionError("no husk");
        Vec3 p = s.b().ship().toWorld(Vec3.atBottomCenterOf(s.b().helmPlot().offset(1, 0, 1)).add(0, 0.1, 0));
        z.moveTo(p.x, p.y, p.z, 90, 0);
        z.setPersistenceRequired();
        h.getLevel().addFreshEntity(z);
        z.setHealth(health);
        // it stays on B's small deck, neither walking nor knocked off it (a monster in the sea is no longer aboard)
        z.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 9));
        AttributeInstance kb = z.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1.0);
        return z;
    }

    private static void assertAboard(GameTestHelper h, Mob m, ShipBody ship, String who) {
        h.assertTrue(BoardingFight.aboard(m, ship.id()), who + " does not stand on " + ship.id() + " but at " + m.position());
    }

    /** Where the boarders of the party of {@code home} are, for a failure message. */
    private static String where(GameTestHelper h, UUID home) {
        BoardingParties.Party p = BoardingParties.party(home);
        if (p == null) return "no party";
        StringBuilder out = new StringBuilder();
        for (UUID id : p.boarders()) {
            BoardingParties.Boarder b = p.boarder(id);
            net.minecraft.world.entity.Entity e = h.getLevel().getEntity(id);
            out.append(id.toString(), 0, 4).append(' ').append(b == null ? "?" : b.phase())
                    .append(" at ").append(e == null ? "unloaded" : h.relativeVec(e.position()))
                    .append(" on ").append(e == null ? "-" : BoardingFight.shipOf(e))
                    .append(" plank ").append(e instanceof Mob m && BoardingParties.onPlank(h.getLevel(), m))
                    .append(b == null ? "" : " " + b.describe()).append("; ");
        }
        return out.toString();
    }

    /**
     * Watches {@code mobs} every tick (registered in the test body) and remembers each that stood on B as a boarder of
     * A's party, fighting and across by itself: a deck held clear long enough sends them home, so the first across may
     * have left when the last arrives.
     */
    private static java.util.Set<UUID> crossed(GameTestHelper h, Ships s, List<? extends Mob> mobs) {
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        h.onEachTick(() -> {
            BoardingParties.Party p = BoardingParties.party(s.a().ship().id());
            if (p == null) return;
            for (Mob m : mobs) {
                BoardingParties.Boarder b = p.boarder(m.getUUID());
                if (b != null && b.phase() == BoardingParties.Phase.ABOARD && !b.wasSetDown()
                        && BoardingFight.aboard(m, s.b().ship().id())) {
                    seen.add(m.getUUID());
                }
            }
        });
        return seen;
    }

    private static void cleanup(GameTestHelper h, Ships s, Collection<? extends Mob> mobs) {
        BoardingParties.forget(h.getLevel(), s.a().ship().id());
        BoardingParties.forget(h.getLevel(), s.b().ship().id());
        for (Mob m : mobs) {
            if (m.getVehicle() != null) m.getVehicle().discard();
            m.discard();
        }
    }

    /** A mock captain that keeps the action-bar lines it is shown. */
    private static final class Captain extends Player {
        final List<Component> actionBar = new ArrayList<>();

        Captain(ServerLevel level) {
            super(level, BlockPos.ZERO, 0f, new GameProfile(UUID.randomUUID(), "test-captain"));
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
            if (overlay) actionBar.add(message);
        }

        long count(String key) {
            return actionBar.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)).count();
        }
    }

    // ------------------------------------------------------------------ the crossing

    /**
     * Over a gangway: three free hands answer "Board!" and stand on B within {@link #CROSS_WITHIN} ticks, having walked
     * the board; the gunner keeps his swivel gun.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + CROSS_WITHIN + 20, batch = BATCH + "gangway")
    public static void boardersCrossTheGangwayAndTheGunnerStays(GameTestHelper h) {
        noDesertion(h);
        Ships s = twoShips(h, 3);
        List<Mob> people = new ArrayList<>();
        List<CrewMember> hands = new ArrayList<>();
        CrewMember[] gunner = new CrewMember[1];
        h.runAtTickTime(SETTLE, () -> {
            plank(h, s);
            hands.addAll(hands(h, s, 3));
            gunner[0] = gunner(h, s);
            people.addAll(hands);
            people.add(gunner[0]);
        });
        h.runAtTickTime(SETTLE + ORDER, () -> {
            BoardingParties.Outcome o = BoardingParties.boardWithCrew(h.getLevel(), s.a().ship(), null);
            h.assertTrue(o.result() == BoardingParties.Result.BOARDING && o.boarders() == 3, "the order: " + o);
            h.assertTrue(s.b().ship().id().equals(o.target()), "boarding the wrong ship: " + o.target());
            h.assertTrue(BoardingParties.boarder(gunner[0]) == null, "the gunner left his gun");
        });
        java.util.Set<UUID> across = crossed(h, s, hands);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE + ORDER, "not yet ordered");
            h.assertTrue(across.size() == hands.size(), across.size() + " of " + hands.size() + " hands crossed: "
                    + where(h, s.a().ship().id()));
            h.assertTrue(gunner[0].assignment() != null && gunner[0].assignment().pos().equals(s.swivel()), "the gunner lost his gun");
            assertAboard(h, gunner[0], s.a().ship(), "the gunner");
            cleanup(h, s, people);
        });
    }

    /**
     * Five hands on one gangway (BRD5): they cross in file, a block apart, and every one steps off the board onto B's
     * deck by itself; the first across clear the landing for the next (one used to stay on the board's lip behind a hand
     * standing on the landing, tracked by A, and never arrived).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + CROSS_WITHIN + 120, batch = BATCH + "file")
    public static void fiveBoardersCrossOneGangwayInFile(GameTestHelper h) {
        noDesertion(h);
        Ships s = twoShips(h, 3);
        List<CrewMember> hands = new ArrayList<>();
        h.runAtTickTime(SETTLE, () -> {
            plank(h, s);
            hands.addAll(hands(h, s, 5));
        });
        h.runAtTickTime(SETTLE + ORDER, () -> {
            BoardingParties.Outcome o = BoardingParties.boardWithCrew(h.getLevel(), s.a().ship(), null);
            h.assertTrue(o.result() == BoardingParties.Result.BOARDING && o.boarders() == 5, "the order: " + o);
        });
        java.util.Set<UUID> across = crossed(h, s, hands);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE + ORDER, "not yet ordered");
            h.assertTrue(across.size() == hands.size(), across.size() + " of " + hands.size() + " hands crossed: "
                    + where(h, s.a().ship().id()));
            cleanup(h, s, hands);
        });
    }

    /** Without a plank, over two blocks of water: the hands jump across. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + CROSS_WITHIN + 20, batch = BATCH + "gap")
    public static void boardersJumpATwoBlockGap(GameTestHelper h) {
        noDesertion(h);
        Ships s = twoShips(h, 2);
        List<CrewMember> hands = new ArrayList<>();
        h.runAtTickTime(SETTLE, () -> hands.addAll(hands(h, s, 3)));
        h.runAtTickTime(SETTLE + ORDER, () -> {
            BoardingParties.Outcome o = BoardingParties.boardWithCrew(h.getLevel(), s.a().ship(), null);
            h.assertTrue(o.result() == BoardingParties.Result.BOARDING && o.boarders() == 3, "the order: " + o);
            List<BoardingWays.Crossing> ways = BoardingWorld.crossings(h.getLevel(), s.a().ship(), s.b().ship());
            h.assertTrue(!ways.isEmpty() && ways.stream().allMatch(w -> w.kind() == BoardingWays.Kind.GAP && w.gap() == 2),
                    "expected two-block gap crossings: " + ways);
        });
        java.util.Set<UUID> across = crossed(h, s, hands);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE + ORDER, "not yet ordered");
            h.assertTrue(across.size() == hands.size(), across.size() + " of " + hands.size() + " hands jumped across: "
                    + where(h, s.a().ship().id()));
            cleanup(h, s, hands);
        });
    }

    /** Three blocks of water and no plank: no way across, the order is refused and nobody moves. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + 20, batch = BATCH + "refused")
    public static void boardIsRefusedWithoutAWayAcross(GameTestHelper h) {
        noDesertion(h);
        Ships s = twoShips(h, 3);
        List<CrewMember> hands = new ArrayList<>();
        h.runAtTickTime(SETTLE, () -> hands.addAll(hands(h, s, 2)));
        h.runAtTickTime(SETTLE + ORDER, () -> {
            BoardingParties.Outcome o = BoardingParties.boardWithCrew(h.getLevel(), s.a().ship(), null);
            h.assertTrue(o.result() == BoardingParties.Result.NO_WAY, "the order: " + o);
            h.assertTrue(o.message().getContents() instanceof TranslatableContents t && t.getKey().equals(BoardingParties.KEY_NO_WAY),
                    "the line: " + o.message());
            h.assertTrue(!BoardingParties.out(s.a().ship().id()), "a party went out");
            cleanup(h, s, hands);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the fight

    /**
     * A husk on B is fought and killed by the boarders; the one hand with an empty hand and a cutlass in A's chest takes
     * it from the chest first.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + 400, batch = BATCH + "fight")
    public static void aHostileMobAboardIsFoughtAndKilled(GameTestHelper h) {
        noDesertion(h);
        Ships s = twoShips(h, 3);
        List<Mob> people = new ArrayList<>();
        List<CrewMember> hands = new ArrayList<>();
        Husk[] husk = new Husk[1];
        h.runAtTickTime(SETTLE, () -> {
            plank(h, s);
            if (h.getLevel().getBlockEntity(s.chest()) instanceof Container box) {
                box.setItem(0, new ItemStack(CombatContent.CUTLASS.get()));
            } else {
                throw new AssertionError("no chest on A at " + s.chest());
            }
            hands.addAll(hands(h, s, 2));
            husk[0] = husk(h, s, 6f);
            people.addAll(hands);
            people.add(husk[0]);
        });
        h.runAtTickTime(SETTLE + ORDER, () -> {
            BoardingParties.Outcome o = BoardingParties.boardWithCrew(h.getLevel(), s.a().ship(), null);
            h.assertTrue(o.result() == BoardingParties.Result.BOARDING, "the order: " + o);
            long armed = hands.stream().filter(c -> c.getMainHandItem().is(CombatContent.CUTLASS.get())).count();
            h.assertTrue(armed == 1, armed + " hands took a cutlass from the chest, expected 1");
            Container box = (Container) h.getLevel().getBlockEntity(s.chest());
            h.assertTrue(box != null && box.getItem(0).isEmpty(), "the cutlass is still in the chest");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE + ORDER, "not yet ordered");
            h.assertTrue(!husk[0].isAlive(), "the husk aboard B lives (" + husk[0].getHealth() + " health) at " + husk[0].position());
            cleanup(h, s, people);
        });
    }

    /** Once the deck is clear for capture_seconds, the captain who gave the order is told: "Her deck is yours". */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + 400, batch = BATCH + "capture")
    public static void captureTellsTheCaptain(GameTestHelper h) {
        noDesertion(h);
        ConfigOverrides.during(h, BoardingConfig.CAPTURE_SECONDS, 1);
        Ships s = twoShips(h, 3);
        List<Mob> people = new ArrayList<>();
        Captain captain = new Captain(h.getLevel());
        Husk[] husk = new Husk[1];
        h.runAtTickTime(SETTLE, () -> {
            plank(h, s);
            people.addAll(hands(h, s, 2));
            husk[0] = husk(h, s, 4f);
            people.add(husk[0]);
        });
        h.runAtTickTime(SETTLE + ORDER, () -> {
            BoardingParties.Outcome o = BoardingParties.boardWithCrew(h.getLevel(), s.a().ship(), captain);
            h.assertTrue(o.result() == BoardingParties.Result.BOARDING, "the order: " + o);
        });
        h.runAtTickTime(SETTLE + ORDER + 5, () -> h.assertTrue(captain.count(BoardingParties.KEY_CAPTURED) == 0,
                "told of a capture while the husk lives"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE + ORDER + 5, "not yet ordered");
            h.assertTrue(!husk[0].isAlive(), "the husk lives at " + husk[0].position() + ", aboard B "
                    + BoardingFight.aboard(husk[0], s.b().ship().id()));
            BoardingParties.Party p = BoardingParties.party(s.a().ship().id());
            h.assertTrue(p == null || p.captured(), "the deck is not taken");
            h.assertTrue(captain.count(BoardingParties.KEY_CAPTURED) == 1, "the captain was told "
                    + captain.count(BoardingParties.KEY_CAPTURED) + " times");
            cleanup(h, s, people);
        });
    }

    // ------------------------------------------------------------------ the recall

    /**
     * Every hand goes ({@code boarders_keep_stations} off), the gunner too, on the whistle's entry; the same entry, now
     * "Back aboard", given from her deck by the captain who boarded along, brings them home over the gangway and the
     * gunner sits at his swivel gun again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + 2 * CROSS_WITHIN + 100, batch = BATCH + "recall")
    public static void backAboardReturnsThemToTheirStations(GameTestHelper h) {
        noDesertion(h);
        ConfigOverrides.during(h, BoardingConfig.BOARDERS_KEEP_STATIONS, false);
        ConfigOverrides.during(h, BoardingConfig.CAPTURE_SECONDS, 600); // they hold the empty deck until called back
        Ships s = twoShips(h, 3);
        List<Mob> people = new ArrayList<>();
        CrewMember[] gunner = new CrewMember[1];
        CrewMember[] hand = new CrewMember[1];
        Captain captain = new Captain(h.getLevel());
        int[] stage = {0};
        long[] recalledAt = {0};
        h.runAtTickTime(SETTLE, () -> {
            plank(h, s);
            hand[0] = hands(h, s, 1).get(0);
            gunner[0] = gunner(h, s);
            people.add(hand[0]);
            people.add(gunner[0]);
        });
        h.runAtTickTime(SETTLE + ORDER, () -> {
            h.assertTrue(gunner[0].isAtStation(), "the gunner did not reach his gun");
            // the whistle's toggle: "Board!" while no party is out
            WhistleOrders.Issued o = WhistleOrders.issue(h.getLevel(), s.a().ship(), WhistleOrder.BOARD, null, captain);
            h.assertTrue(o.result().outcome() == WhistleOrders.Outcome.ISSUED && o.result().crew() == 2, "the order: " + o.result());
            h.assertTrue(o.message().getContents() instanceof TranslatableContents t && t.getKey().equals(BoardingParties.KEY_BOARD),
                    "the line: " + o.message());
            h.assertTrue(gunner[0].assignment() == null, "the gunner kept his station");
            stage[0] = 1;
        });
        h.onEachTick(() -> {
            if (stage[0] != 1) return;
            BoardingParties.Party p = BoardingParties.party(s.a().ship().id());
            if (p == null) return;
            BoardingParties.Boarder g = p.boarder(gunner[0].getUUID());
            BoardingParties.Boarder c = p.boarder(hand[0].getUUID());
            if (g != null && c != null && g.phase() == BoardingParties.Phase.ABOARD && c.phase() == BoardingParties.Phase.ABOARD) {
                // the same entry again, given from B's deck by the captain who boarded along: "Back aboard"
                WhistleOrders.Issued o = WhistleOrders.issue(h.getLevel(), s.b().ship(), WhistleOrder.BOARD, null, captain);
                h.assertTrue(o.result().outcome() == WhistleOrders.Outcome.ISSUED, "the recall: " + o.result());
                h.assertTrue(o.message().getContents() instanceof TranslatableContents t && t.getKey().equals(BoardingParties.KEY_BACK),
                        "the line: " + o.message());
                h.assertTrue(p.recalled(), "the party was not called back");
                recalledAt[0] = h.getTick();
                stage[0] = 2;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stage[0] == 2, "the boarders never stood on B together (stage " + stage[0] + "): " + where(h, s.a().ship().id())
                    + (gunner[0] == null ? "" : " gunner " + h.relativeVec(gunner[0].position()) + " removed " + gunner[0].getRemovalReason()
                    + " found " + (h.getLevel().getEntity(gunner[0].getUUID()) != null) + " alive " + gunner[0].isAlive()
                    + ", hand " + h.relativeVec(hand[0].position()) + " removed " + hand[0].getRemovalReason()));
            h.assertTrue(!BoardingParties.out(s.a().ship().id()), "the party is still out: " + where(h, s.a().ship().id()));
            h.assertTrue(gunner[0].isAtStation() && gunner[0].assignment().pos().equals(s.swivel()),
                    "the gunner is not back at his gun: " + gunner[0].assignment() + " at " + gunner[0].position());
            assertAboard(h, hand[0], s.a().ship(), "the hand");
            h.assertTrue(!hand[0].isBoarding() && !gunner[0].isBoarding(), "still marked as boarders");
            cleanup(h, s, people);
        });
    }

    /**
     * The gangway breaks while the boarders are across: called back, they stay on B and fight on; a new gangway brings
     * them home.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + ORDER + 3 * CROSS_WITHIN + 100, batch = BATCH + "stranded")
    public static void aBrokenGangwayStrandsThemAndANewOneBringsThemBack(GameTestHelper h) {
        noDesertion(h);
        ConfigOverrides.during(h, BoardingConfig.CAPTURE_SECONDS, 600); // they hold the empty deck until called back
        Ships s = twoShips(h, 3);
        List<CrewMember> hands = new ArrayList<>();
        int[] stage = {0};
        long[] at = {0};
        h.runAtTickTime(SETTLE, () -> {
            plank(h, s);
            hands.addAll(hands(h, s, 2));
        });
        h.runAtTickTime(SETTLE + ORDER, () -> {
            BoardingParties.Outcome o = BoardingParties.boardWithCrew(h.getLevel(), s.a().ship(), null);
            h.assertTrue(o.result() == BoardingParties.Result.BOARDING, "the order: " + o);
            stage[0] = 1;
        });
        h.onEachTick(() -> {
            ServerLevel level = h.getLevel();
            BoardingParties.Party p = BoardingParties.party(s.a().ship().id());
            switch (stage[0]) {
                case 1 -> {
                    if (p != null && hands.stream().allMatch(c -> p.boarder(c.getUUID()) != null
                            && p.boarder(c.getUUID()).phase() == BoardingParties.Phase.ABOARD)) {
                        // break the gangway (any cell breaks the run) and call them back
                        BlockPos base = s.a().helmPlot().offset(3, 0, 0);
                        h.assertTrue(level.getBlockState(base).is(BoardingContent.PLANK.get()), "no plank at " + base);
                        level.destroyBlock(base, false);
                        BoardingParties.recall(level, s.a().ship().id());
                        at[0] = h.getTick();
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (h.getTick() - at[0] < 60) return;
                    h.assertTrue(p != null, "the party came home without a way across");
                    for (CrewMember c : hands) {
                        h.assertTrue(p.boarder(c.getUUID()) != null && p.boarder(c.getUUID()).phase() == BoardingParties.Phase.ABOARD,
                                "a stranded boarder is not holding B's deck");
                        assertAboard(h, c, s.b().ship(), "a stranded boarder");
                    }
                    plank(h, s);
                    stage[0] = 3;
                }
                default -> {
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stage[0] == 3, "stage " + stage[0] + ": " + where(h, s.a().ship().id()));
            h.assertTrue(!BoardingParties.out(s.a().ship().id()), "the party is still out: " + where(h, s.a().ship().id()));
            for (CrewMember c : hands) assertAboard(h, c, s.a().ship(), "a returned hand");
            cleanup(h, s, hands);
        });
    }

    // ------------------------------------------------------------------ boarding nets (RAIL5)

    /** Push towards inboard each tick [blocks/tick], as in RAIL5's net tests. */
    private static final double NET_PUSH = 0.12;

    private static void railing(GameTestHelper h, BlockPos rel, Direction edge) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, RailingContent.RAILINGS.get(ShipWood.OAK).defaultBlockState().setValue(RailingBlock.edge(edge), true), 3);
    }

    private static void still(Mob mob) {
        for (Goal.Flag f : Goal.Flag.values()) {
            mob.goalSelector.disableControlFlag(f);
            mob.targetSelector.disableControlFlag(f);
        }
    }

    /**
     * Ship B carries a boarding net along her north rail (RAIL5). A boarder of A's party standing on her rail outboard and
     * pushed inboard is held like any attacker, then climbs over; a boarder of B's own party coming home over the same
     * rail is not held.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 260, batch = BATCH + "net")
    public static void aBoardingNetHoldsBoardersButNotItsOwnShipsCrew(GameTestHelper h) {
        noDesertion(h);
        ConfigOverrides.during(h, BoardingConfig.ORDER_CHECK_TICKS, 200); // the parties stay as placed for the test
        ServerLevel level = h.getLevel();
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmA = DryHullGameTests.hull(h, 2, false);
        BlockPos helmB = DryHullGameTests.hull(h, 10, false);
        for (int x = 10; x <= 14; x++) railing(h, new BlockPos(x, 9, 9), Direction.NORTH);
        for (int x = 10; x <= 14; x++) {
            BlockPos abs = h.absolutePos(new BlockPos(x, 9, 9));
            level.setBlock(abs, RailingBlock.withPosts(level.getBlockState(abs), level, abs), 3);
        }
        BlockState plate = RiggingContent.CHAINPLATE.get().defaultBlockState().setValue(ChainplateBlock.FACE, AttachFace.FLOOR)
                .setValue(ChainplateBlock.FACING, Direction.EAST);
        h.setBlock(new BlockPos(10, 10, 9), plate);
        h.setBlock(new BlockPos(14, 10, 9), plate);
        ItemStack net = new ItemStack(RiggingContent.NET_ITEM.get());
        RatlineNetItem.use(level, net, h.absolutePos(new BlockPos(10, 10, 9)), null);
        Component strung = RatlineNetItem.use(level, net, h.absolutePos(new BlockPos(14, 10, 9)), null);
        h.assertTrue(strung.getContents() instanceof TranslatableContents t && t.getKey().equals(RatlineNetItem.KEY_BOARDING_STRUNG),
                "no boarding net strung: " + strung);
        Fixture a = DryHullGameTests.assemble(h, helmA);
        Fixture b = DryHullGameTests.assemble(h, helmB);
        java.util.function.Function<BlockPos, BlockPos> plotB = rel -> b.helmPlot().offset(rel.subtract(helmB));
        double face = plotB.apply(new BlockPos(11, 10, 9)).getZ();
        CrewMember[] who = new CrewMember[2]; // 0: A's boarder (an attacker), 1: B's boarder coming home
        h.runAtTickTime(SETTLE, () -> {
            for (int i = 0; i < 2; i++) {
                Vec3 at = b.ship().toWorld(Vec3.atLowerCornerOf(plotB.apply(new BlockPos(i == 0 ? 11 : 13, 9, 9))).add(0.5, 1.6, -0.2));
                CrewMember c = StationContent.CREW_MEMBER.get().create(level);
                if (c == null) throw new AssertionError("no crew member");
                c.moveTo(at.x, at.y, at.z, 180f, 0f);
                still(c);
                level.addFreshEntity(c);
                who[i] = c;
            }
            BoardingParties.enlist(level, a.ship().id(), b.ship().id(), who[0], false);
            BoardingParties.enlist(level, b.ship().id(), a.ship().id(), who[1], false);
        });
        int hold = RiggingConfig.BOARDING_NET_HOLD.get();
        int push = SETTLE + 10;
        boolean[] held = {false, false};
        h.onEachTick(() -> {
            if (who[0] == null || h.getTick() < push) return;
            Vec3 in = RatlineNetAssist.rotate(b.ship().orientation(), new Vec3(0, 0, NET_PUSH));
            for (int i = 0; i < 2; i++) {
                who[i].setDeltaMovement(in.x, who[i].getDeltaMovement().y, in.z);
                if (BoardingNets.holds(level, who[i])) held[i] = true;
            }
        });
        h.runAtTickTime(push + 30, () -> {
            h.assertTrue(!held[1], "the net held a boarder of its own ship coming home");
            double z = b.ship().toPlot(who[1].position()).z;
            h.assertTrue(z > face + 1.0, "B's own boarder did not cross its net: plot z " + (z - face));
        });
        h.runAtTickTime(SETTLE + hold + 70, () -> {
            h.assertTrue(held[0], "the net never held A's boarder");
            double z = b.ship().toPlot(who[0].position()).z;
            h.assertTrue(z > face + 1.0, "A's boarder did not climb over after the hold: plot z " + (z - face));
            BoardingParties.forget(level, a.ship().id());
            BoardingParties.forget(level, b.ship().id());
            for (CrewMember c : who) c.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ NPC boarding

    /** A pirate ship two blocks off a player's ship, guns on her, three pirates on deck. */
    private record NpcSetup(Ships s, List<Pirate> pirates, UUID owner) {
    }

    private static NpcSetup npcAlongside(GameTestHelper h) {
        Ships s = twoShips(h, 2);
        UUID owner = UUID.randomUUID();
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        registry.find(s.a().ship().id()).ifPresent(d -> registry.put(d.withOwner(Optional.of(owner))));
        List<Pirate> pirates = new ArrayList<>();
        return new NpcSetup(s, pirates, owner);
    }

    private static void manPirateShip(GameTestHelper h, NpcSetup n) {
        Fixture b = n.s().b();
        int[][] at = {{-1, -1}, {1, -1}, {0, 1}};
        for (int[] d : at) {
            Pirate p = MobContent.PIRATE.get().create(h.getLevel());
            if (p == null) throw new AssertionError("no pirate");
            Vec3 w = b.ship().toWorld(Vec3.atBottomCenterOf(b.helmPlot().offset(d[0], 0, d[1])).add(0, 0.1, 0));
            p.moveTo(w.x, w.y, w.z, 270, 0);
            p.setStationary(true);
            p.setPersistenceRequired();
            h.getLevel().addFreshEntity(p);
            n.pirates().add(p);
        }
        NpcBoarding.adopt(b.ship().id(), Faction.PIRATES);
        Gunnery.set(h.getLevel(), b.ship(), GunneryState.target(n.s().a().ship().id()));
    }

    private static void cleanupNpc(GameTestHelper h, NpcSetup n) {
        NpcBoarding.release(n.s().b().ship().id());
        Gunnery.clear(n.s().b().ship());
        cleanup(h, n.s(), n.pirates());
    }

    /**
     * A pirate ship lying two blocks off a player's ship, her guns on him: after {@code npc_board_delay} (2 s here) and
     * not before, the captain sends his pirates across; they stand on the player's deck.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + 60 + CROSS_WITHIN + 20, batch = BATCH + "npc")
    public static void aPirateShipAlongsideBoardsAfterTheDelay(GameTestHelper h) {
        ConfigOverrides.during(h, BoardingConfig.NPC_BOARD_DELAY, 2);
        NpcSetup n = npcAlongside(h);
        long[] manned = {0};
        h.runAtTickTime(SETTLE, () -> {
            manPirateShip(h, n);
            manned[0] = h.getTick();
        });
        h.runAtTickTime(SETTLE + 25, () -> h.assertTrue(!BoardingParties.out(n.s().b().ship().id()),
                "the pirates boarded before the delay"));
        h.succeedWhen(() -> {
            h.assertTrue(manned[0] > 0, "not manned yet");
            BoardingParties.Party p = BoardingParties.party(n.s().b().ship().id());
            h.assertTrue(p != null && p.npc(), "no NPC boarding party out");
            h.assertTrue(p.target().equals(n.s().a().ship().id()), "boarding the wrong ship");
            long across = n.pirates().stream().filter(x -> p.boarder(x.getUUID()) != null
                    && p.boarder(x.getUUID()).phase() == BoardingParties.Phase.ABOARD && BoardingFight.aboard(x, n.s().a().ship().id())).count();
            h.assertTrue(across == n.pirates().size(), across + " of " + n.pirates().size() + " pirates on the player's deck");
            cleanupNpc(h, n);
        });
    }

    /** The same with {@code npc_boarding} off: nobody comes across. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = SETTLE + 160, batch = BATCH + "npc_off")
    public static void npcBoardingCanBeSwitchedOff(GameTestHelper h) {
        ConfigOverrides.during(h, BoardingConfig.NPC_BOARD_DELAY, 1);
        ConfigOverrides.during(h, BoardingConfig.NPC_BOARDING, false);
        NpcSetup n = npcAlongside(h);
        h.runAtTickTime(SETTLE, () -> manPirateShip(h, n));
        h.runAtTickTime(SETTLE + 140, () -> {
            h.assertTrue(!BoardingParties.out(n.s().b().ship().id()), "the pirates boarded with npc_boarding off");
            for (Pirate p : n.pirates()) assertAboard(h, p, n.s().b().ship(), "a pirate");
            cleanupNpc(h, n);
            h.succeed();
        });
    }
}
