package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.hammock.RestRules;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.upkeep.ShipDayTick;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The crew's meals (CRW2, docs/design.md §7.4) on the 5×4×5 test ship of {@link StationGameTests} resting on land: the
 * pantry in the hold at relative (18, 6, 18), a water barrel at (20, 6, 18). Crew member {@code a} stands free on the
 * deck, {@code b} is at the winch. The clock tests set it five ticks before the only meal time (6000) and let it run
 * into it; every test changes config (and most the clock) and runs in a batch of its own. Meals last {@link #MEAL_TICKS}.
 */
public final class MealGameTests {

    private static final String CONFIG_BATCH = "pirates_n_ships_config_crew_";
    private static final int MEAL_TICKS = 20;
    /** By then the clock has passed the meal time. */
    private static final int SERVED = 10;

    private MealGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MealGameTests.class);
    }

    private record Ship(Fixture f, CrewMember a, CrewMember b, BlockPos pantry) { }

    private static Ship ship(GameTestHelper h) {
        ConfigOverrides.during(h, MealConfig.MEAL_TIMES, List.of("6000"));
        ConfigOverrides.during(h, MealConfig.MEAL_TICKS, MEAL_TICKS);
        morning(h);
        Fixture f = StationGameTests.ship(h, false, x -> {
            x.setBlock(new BlockPos(18, 6, 18), CrewContent.PANTRY.get());
            x.setBlock(new BlockPos(20, 6, 18), CrewContent.WATER_BARREL.get());
        });
        CrewMember a = onDeck(h, f, 20, 20);
        CrewMember b = onDeck(h, f, 18, 19);
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), b, f.winch());
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign b to the winch: " + r);
        return new Ship(f, a, b, StationGameTests.find(h, f.ship(), CrewContent.PANTRY.get()));
    }

    /** Sets the clock five ticks before the meal time 6000; the meal is served by {@link #SERVED}. */
    private static void beforeMeal(GameTestHelper h) {
        long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
        h.getLevel().setDayTime(today + 6000L - 5);
    }

    /**
     * Morning of today, before the crew exists. When the batch before left the level at night, this ends that night:
     * the ship day tick sees the dawn here, and not on the next tick, where it would feed the test's crew from the
     * pantry once.
     */
    private static void morning(GameTestHelper h) {
        long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
        h.getLevel().setDayTime(today + 1000L);
        ShipDayTick.observe(h.getLevel());
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

    private static List<MealSeat> seats(GameTestHelper h, Ship s) {
        return h.getLevel().getEntitiesOfClass(MealSeat.class, new AABB(s.pantry()).inflate(4), e -> !e.isRemoved());
    }

    private static void cleanup(Ship s) {
        for (CrewMember c : List.of(s.a(), s.b())) {
            if (c.getVehicle() != null) c.getVehicle().discard();
            c.discard();
        }
    }

    /**
     * At the meal time the free member sits on a meal seat beside the pantry (a cell next to it, at its height, facing
     * it) and shows the eating pose; the member at the winch stays there. After {@code meal_ticks} it gets up and the
     * seat is gone.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = CONFIG_BATCH + "meals")
    public static void freeCrewEatsBesideThePantry(GameTestHelper h) {
        Ship s = ship(h);
        beforeMeal(h);
        h.runAfterDelay(SERVED, () -> {
            h.assertTrue(s.a().getVehicle() instanceof MealSeat, "the free member is not at a meal: " + s.a().getVehicle());
            MealSeat seat = (MealSeat) s.a().getVehicle();
            h.assertTrue(seat.provisions().equals(s.pantry()), "the meal is not at the pantry but at " + seat.provisions());
            BlockPos spot = seat.blockPosition();
            h.assertTrue(spot.getY() == s.pantry().getY() && spot.distManhattan(s.pantry()) <= 2 && !spot.equals(s.pantry())
                    && Math.abs(spot.getX() - s.pantry().getX()) <= 1 && Math.abs(spot.getZ() - s.pantry().getZ()) <= 1,
                    "the seat is not beside the pantry: " + spot + " vs " + s.pantry());
            h.assertTrue(s.a().isEating(), "the eating flag is not set");
            float want = MealRules.facingYaw(spot, s.pantry(), s.f().ship().orientation());
            float diff = Math.abs(net.minecraft.util.Mth.wrapDegrees(s.a().getYRot() - want));
            h.assertTrue(diff < 1, "the diner does not face the pantry: yaw " + s.a().getYRot() + ", want " + want);
            h.assertTrue(s.b().isAtStation() && !s.b().isEating(), "the member at the winch left it for the meal");
            h.assertTrue(seats(h, s).size() == 1, "meal seats: " + seats(h, s));
        });
        h.runAfterDelay(SERVED + MEAL_TICKS + 5, () -> {
            h.assertTrue(s.a().getVehicle() == null, "still seated after the meal: " + s.a().getVehicle());
            h.assertTrue(!s.a().isEating(), "still eating after the meal");
            h.assertTrue(seats(h, s).isEmpty(), "meal seats left: " + seats(h, s));
            cleanup(s);
            h.succeed();
        });
    }

    /** An order during the meal seats the member at its station at once and the meal seat goes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "meals_order")
    public static void orderInterruptsTheMeal(GameTestHelper h) {
        Ship s = ship(h);
        beforeMeal(h);
        h.runAfterDelay(SERVED, () -> {
            h.assertTrue(s.a().getVehicle() instanceof MealSeat, "the free member is not at a meal: " + s.a().getVehicle());
            CrewStations.release(h.getLevel(), s.b());
            CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), s.a(), s.f().winch());
            h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
            h.assertTrue(s.a().isAtStation(), "the member is not at the winch");
        });
        h.runAfterDelay(SERVED + 2, () -> {
            h.assertTrue(seats(h, s).isEmpty(), "the meal seat is still there: " + seats(h, s));
            h.assertTrue(s.a().isAtStation() && !s.a().isEating(), "at station " + s.a().isAtStation() + ", eating " + s.a().isEating());
            cleanup(s);
            h.succeed();
        });
    }

    /** {@code crew.meals.enabled = false}: the clock passes the meal time and nobody moves. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "meals_off")
    public static void mealsOffNobodyMoves(GameTestHelper h) {
        ConfigOverrides.during(h, MealConfig.ENABLED, false);
        Ship s = ship(h);
        beforeMeal(h);
        Vec3 before = s.a().position();
        h.runAfterDelay(SERVED, () -> {
            h.assertTrue(s.a().getVehicle() == null && !s.a().isEating(), "a meal with meals off: " + s.a().getVehicle());
            h.assertTrue(seats(h, s).isEmpty(), "meal seats with meals off: " + seats(h, s));
            h.assertTrue(s.a().position().distanceTo(before) < 0.5, "the member moved: " + before + " -> " + s.a().position());
            cleanup(s);
            h.succeed();
        });
    }

    /** A meal seats only free crew: a member in a hammock or at a station stays put ({@link MealVisits#isFree}). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "meals_busy")
    public static void busyCrewSkipTheMeal(GameTestHelper h) {
        Ship s = ship(h);
        h.assertTrue(!MealVisits.isFree(s.b()), "a member at a station counts as free");
        h.assertTrue(MealVisits.isFree(s.a()), "a member on deck is not free");
        List<CrewMember> seated = MealVisits.serve(h.getLevel(), s.f().ship());
        h.assertTrue(seated.equals(List.of(s.a())), "seated: " + seated);
        h.assertTrue(!MealVisits.isFree(s.a()), "a diner counts as free for a second meal");
        h.assertTrue(MealVisits.serve(h.getLevel(), s.f().ship()).isEmpty(), "a second meal seated someone");
        cleanup(s);
        h.succeed();
    }
}
