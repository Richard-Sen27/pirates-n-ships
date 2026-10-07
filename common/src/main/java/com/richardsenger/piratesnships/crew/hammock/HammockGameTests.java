package com.richardsenger.piratesnships.crew.hammock;

import com.mojang.authlib.GameProfile;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.morale.NightOutcome;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import java.util.ArrayList;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The hammock, bunks and the hammock rule of HM1 (docs/design.md §7.1). The ship is the 5×4×5 hull of
 * {@link StationGameTests} resting on land (deck top at relative y = 9, the winch at relative (18, 9, 20)); one hammock
 * hangs in the hold at relative y = 7 between a fence post at (18, 7, 19) and the hull's east wall, its foot at
 * (19, 7, 19) facing east. Crew stand on the deck without AI.
 * <p>
 * Tests that set the day time run in batches of their own: the clock is shared by the whole level. They end at
 * daytime.
 */
public final class HammockGameTests {

    private static final String NIGHT_BATCH = "pirates_n_ships_crew_hammock_night_";
    /** Midnight. */
    private static final long MIDNIGHT = 18000L;
    private static final int SETTLE = 3;
    /** Delay of the nightfall after {@link #night}. */
    private static final int NIGHT_AT = 2;

    private HammockGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HammockGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A mock player that keeps every message it is shown. */
    private static final class Captain extends Player {
        final List<Component> chat = new ArrayList<>();
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
            (overlay ? actionBar : chat).add(message);
        }
    }

    /** The hammock in the hold: a fence post, then foot and head toward the east wall (relative positions). */
    private static void hangInHold(GameTestHelper h) {
        h.setBlock(new BlockPos(18, 7, 19), Blocks.OAK_FENCE);
        BlockState foot = CrewContent.HAMMOCK.get().defaultBlockState().setValue(HammockBlock.FACING, Direction.EAST);
        h.setBlock(new BlockPos(19, 7, 19), foot.setValue(HammockBlock.PART, BedPart.FOOT));
        h.setBlock(new BlockPos(20, 7, 19), foot.setValue(HammockBlock.PART, BedPart.HEAD));
    }

    /** The test ship with the hammock in its hold (and {@code extra} blocks). */
    private static Fixture ship(GameTestHelper h, java.util.function.Consumer<GameTestHelper> extra) {
        return StationGameTests.ship(h, false, x -> {
            hangInHold(x);
            extra.accept(x);
        });
    }

    /** The plot cell at relative (x, y, z): the ship rests on land, so plot = helm + offset (helm at (19, 9, 18)). */
    private static BlockPos plot(Fixture f, int x, int y, int z) {
        return f.helm().offset(x - 19, y - 9, z - 18);
    }

    /** A crew member standing on the deck at relative (x, 9, z), without AI. */
    private static CrewMember onDeck(GameTestHelper h, Fixture f, int x, int z) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = f.ship().toWorld(Vec3.atBottomCenterOf(plot(f, x, 9, z)));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    /** The captain on the deck next to the helm with a whistle in hand, looking {@code yRot}. */
    private static Captain captain(GameTestHelper h, Fixture f, float yRot) {
        Captain p = new Captain(h.getLevel());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        Vec3 deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm()).add(1, 0, 1));
        p.moveTo(deck.x, deck.y, deck.z, yRot, 0);
        return p;
    }

    /** Uses a hammock item on {@code pos} (absolute) as {@code player} would, looking where it looks. */
    private static InteractionResult place(GameTestHelper h, Player player, BlockPos pos) {
        ItemStack stack = new ItemStack(CrewContent.HAMMOCK_ITEM.get());
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        BlockPlaceContext ctx = new BlockPlaceContext(player, InteractionHand.OFF_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return CrewContent.HAMMOCK_ITEM.get().place(ctx);
    }

    private static boolean isHammock(GameTestHelper h, BlockPos abs, BedPart part, Direction facing) {
        BlockState s = h.getLevel().getBlockState(abs);
        return s.is(CrewContent.HAMMOCK.get()) && s.getValue(HammockBlock.PART) == part && s.getValue(HammockBlock.FACING) == facing;
    }

    private static int droppedHammocks(GameTestHelper h) {
        AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(12);
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(CrewContent.HAMMOCK_ITEM.get()))) {
            n += e.getItem().getCount();
        }
        return n;
    }

    private static boolean hasKey(List<Component> lines, String key, Object... args) {
        for (Component c : lines) {
            if (containsKey(c, key, args)) return true;
        }
        return false;
    }

    private static boolean containsKey(Component c, String key, Object... args) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(key) && (args.length == 0 || Arrays.equals(t.getArgs(), args))) return true;
            for (Object a : t.getArgs()) {
                if (a instanceof Component inner && containsKey(inner, key, args)) return true;
            }
        }
        for (Component s : c.getSiblings()) {
            if (containsKey(s, key, args)) return true;
        }
        return false;
    }

    /** The next day at {@code timeOfDay} (always later than now). */
    private static long nextDay(GameTestHelper h, long timeOfDay) {
        long now = h.getLevel().getDayTime();
        return (now / RestRules.DAY + 1) * RestRules.DAY + timeOfDay;
    }

    /**
     * Morning of today now, midnight two ticks later: the level sees a nightfall even when the shared clock had already
     * reached the night before the test. Checks of the night run from {@link #NIGHT_AT} + {@link #SETTLE} on.
     */
    private static void night(GameTestHelper h) {
        long today = h.getLevel().getDayTime() / RestRules.DAY * RestRules.DAY;
        h.getLevel().setDayTime(today + 1000L);
        h.runAfterDelay(NIGHT_AT, () -> h.getLevel().setDayTime(today + MIDNIGHT));
    }

    /**
     * CR2: the ship day tick runs at the same dawn; without provisions and wages only the hammock rule moves morale.
     * These tests run in batches of their own, so they may change the config.
     */
    private static void upkeepOff(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.WAGES_ENABLED, false);
        ConfigOverrides.during(h, com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig.CONSUMPTION_ENABLED, false);
    }

    private static void cleanup(CrewMember... crew) {
        for (CrewMember c : crew) {
            if (c.getVehicle() != null) c.getVehicle().discard();
            c.discard();
        }
    }

    // ------------------------------------------------------------------ placing and breaking

    /**
     * On the test ship's deck: a hammock hangs between two fence posts (foot at the clicked block, head one further in
     * the look direction), and is refused where nothing holds its ends.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void hangsBetweenTwoFencePostsAndNotWithoutSupports(GameTestHelper h) {
        Fixture f = StationGameTests.ship(h, false, x -> {
            x.setBlock(new BlockPos(17, 9, 21), Blocks.OAK_FENCE);
            x.setBlock(new BlockPos(20, 9, 21), Blocks.OAK_FENCE);
        });
        Captain p = captain(h, f, -90f); // looking east
        h.assertTrue(p.getDirection() == Direction.EAST, "the captain looks " + p.getDirection());
        BlockPos foot = plot(f, 18, 9, 21);
        h.assertTrue(place(h, p, foot).consumesAction(), "the hammock was not placed between the posts");
        h.assertTrue(isHammock(h, foot, BedPart.FOOT, Direction.EAST), "no foot at the clicked block: " + h.getLevel().getBlockState(foot));
        h.assertTrue(isHammock(h, foot.east(), BedPart.HEAD, Direction.EAST), "no head east of the foot: " + h.getLevel().getBlockState(foot.east()));
        h.assertTrue(ShipBunks.hammocks(h.getLevel(), f.ship()).equals(List.of(foot)), "bunks: " + ShipBunks.hammocks(h.getLevel(), f.ship()));
        // the bow row (relative z = 17) has nothing at either end
        BlockPos loose = plot(f, 18, 9, 17);
        h.assertTrue(place(h, p, loose) == InteractionResult.FAIL, "a hammock hung without supports");
        h.assertTrue(h.getLevel().getBlockState(loose).isAir() && h.getLevel().getBlockState(loose.east()).isAir(), "blocks were placed anyway");
        h.assertTrue(p.actionBar.stream().anyMatch(c -> containsKey(c, HammockItem.KEY_NO_SUPPORT)), "the player was not told why: " + p.actionBar);
        h.succeed();
    }

    /** On land: breaking a support post brings the hammock down, and exactly one hammock drops. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void breakingASupportDropsOneHammock(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 2, 4), b = new BlockPos(4, 2, 4);
        h.setBlock(a, Blocks.OAK_FENCE);
        h.setBlock(b, Blocks.COBBLESTONE_WALL);
        Captain p = new Captain(h.getLevel());
        Vec3 at = h.absoluteVec(new Vec3(2.5, 2, 2.5));
        p.moveTo(at.x, at.y, at.z, -90f, 0); // looking east
        h.assertTrue(place(h, p, h.absolutePos(new BlockPos(2, 2, 4))).consumesAction(), "not placed between fence and wall");
        h.assertBlockPresent(CrewContent.HAMMOCK.get(), new BlockPos(3, 2, 4));
        h.getLevel().destroyBlock(h.absolutePos(b), true); // the wall's own drop is no hammock
        h.runAfterDelay(2, () -> {
            h.assertBlockNotPresent(CrewContent.HAMMOCK.get(), new BlockPos(2, 2, 4));
            h.assertBlockNotPresent(CrewContent.HAMMOCK.get(), new BlockPos(3, 2, 4));
            h.assertTrue(droppedHammocks(h) == 1, "dropped hammocks: " + droppedHammocks(h));
            h.succeed();
        });
    }

    /** On land: breaking the head of one hammock and the foot of another drops one hammock each. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void breakingEitherHalfDropsOneHammock(GameTestHelper h) {
        Captain p = new Captain(h.getLevel());
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2, 1.5));
        p.moveTo(at.x, at.y, at.z, 180f, 0); // looking north
        for (int x : new int[] {2, 6}) {
            h.setBlock(new BlockPos(x, 2, 6), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(x, 2, 3), Blocks.OAK_PLANKS); // a full solid face
            h.assertTrue(place(h, p, h.absolutePos(new BlockPos(x, 2, 5))).consumesAction(), "not placed at x = " + x);
        }
        for (int x : new int[] {2, 6}) {
            h.assertTrue(isHammock(h, h.absolutePos(new BlockPos(x, 2, 4)), BedPart.HEAD, Direction.NORTH), "no head north of the foot at x = " + x);
        }
        // broken as by a player in survival, with drops (GameTestHelper#destroyBlock drops nothing)
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(2, 2, 4)), true); // head
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(6, 2, 5)), true); // foot
        h.runAfterDelay(2, () -> {
            for (int x : new int[] {2, 6}) {
                h.assertBlockNotPresent(CrewContent.HAMMOCK.get(), new BlockPos(x, 2, 4));
                h.assertBlockNotPresent(CrewContent.HAMMOCK.get(), new BlockPos(x, 2, 5));
            }
            h.assertTrue(droppedHammocks(h) == 2, "dropped hammocks: " + droppedHammocks(h));
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ bunks and info

    /** Two crew members and one hammock: the whistle's crew line reads "morale 70 … crew 2 / bunks 1". */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void whistleShowsCrewAndBunks(GameTestHelper h) {
        Fixture f = ship(h, x -> { });
        CrewMember a = onDeck(h, f, 20, 20);
        CrewMember b = onDeck(h, f, 18, 18);
        Captain p = captain(h, f, 0f);
        ItemStack whistle = p.getMainHandItem();
        whistle.getItem().interactLivingEntity(whistle, p, a, InteractionHand.MAIN_HAND);
        h.assertTrue(hasKey(p.chat, CrewInfo.KEY_SHIP_LINE, 2, 1), "no 'crew 2 / bunks 1' in " + p.chat);
        h.assertTrue(hasKey(p.chat, CrewInfo.KEY_CREW_LINE), "no crew line in " + p.chat);
        h.assertTrue(CrewMorale.get(a) == CrewConfig.MORALE_START.get(), "morale of a new crew member: " + CrewMorale.get(a));
        ShipBunks.Count n = ShipBunks.count(h.getLevel(), f.ship());
        h.assertTrue(n.equals(new ShipBunks.Count(2, 1, 1)), "count: " + n);
        cleanup(a, b);
        h.succeed();
    }

    /** Morale, the hammock and the night are saved with the crew member. */
    @ModGameTest(timeoutTicks = 20)
    public static void moraleAndRestAreSaved(GameTestHelper h) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        CrewMember copy = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null || copy == null) throw new AssertionError("no crew member");
        HammockRef ref = new HammockRef(UUID.randomUUID(), new BlockPos(1, 2, 3));
        c.setStoredMorale(42);
        c.setRest(ref);
        c.setNightOutcome(NightOutcome.NO_HAMMOCK);
        copy.load(c.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(copy.storedMorale() == 42, "morale: " + copy.storedMorale());
        h.assertTrue(ref.equals(copy.rest()) && copy.isResting(), "rest: " + copy.rest());
        h.assertTrue(copy.nightOutcome() == NightOutcome.NO_HAMMOCK, "night: " + copy.nightOutcome());
        CrewMember fresh = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (fresh == null) throw new AssertionError("no crew member");
        fresh.load(StationContent.CREW_MEMBER.get().create(h.getLevel()).saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(fresh.storedMorale() == com.richardsenger.piratesnships.crew.morale.MoraleRules.UNSET && fresh.rest() == null
                && fresh.nightOutcome() == NightOutcome.NONE, "a new crew member saved state it never had");
        h.succeed();
    }

    // ------------------------------------------------------------------ the night

    /**
     * At nightfall the free crew member nearer the only hammock lies in it, the other finds none, the one at the winch
     * stays on duty. At dawn the sleeper gets up with morale 75, the other grumbles with 60, the one on duty keeps 70.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = NIGHT_BATCH + "rule")
    public static void hammockRuleAtNightAndDawn(GameTestHelper h) {
        upkeepOff(h);
        Fixture f = ship(h, x -> { });
        ServerLevel level = h.getLevel();
        CrewMember sleeper = onDeck(h, f, 20, 20);
        CrewMember other = onDeck(h, f, 21, 21);
        CrewMember onDuty = onDeck(h, f, 18, 18);
        h.assertTrue(CrewStations.assign(level, onDuty, f.winch()) == CrewStations.AssignResult.ASSIGNED, "assign at the winch");
        BlockPos foot = plot(f, 19, 7, 19);
        h.assertTrue(ShipBunks.hammocks(level, f.ship()).equals(List.of(foot)), "bunks: " + ShipBunks.hammocks(level, f.ship()));
        night(h);
        h.runAfterDelay(NIGHT_AT + SETTLE, () -> {
            h.assertTrue(sleeper.rest() != null && sleeper.rest().foot().equals(foot), "the free crew member did not turn in: " + sleeper.rest());
            h.assertTrue(sleeper.getVehicle() instanceof HammockSeat, "it does not lie in the hammock: " + sleeper.getVehicle());
            h.assertTrue(sleeper.isResting(), "not resting");
            h.assertTrue(sleeper.nightOutcome() == NightOutcome.SLEPT, "night: " + sleeper.nightOutcome());
            h.assertTrue(other.rest() == null && other.getVehicle() == null, "the second crew member got a hammock");
            h.assertTrue(other.nightOutcome() == NightOutcome.NO_HAMMOCK, "night of the second: " + other.nightOutcome());
            h.assertTrue(onDuty.isAtStation() && onDuty.rest() == null, "the crew member on duty left the winch");
            h.assertTrue(onDuty.nightOutcome() == NightOutcome.ON_DUTY, "night on duty: " + onDuty.nightOutcome());
            h.assertTrue(ShipBunks.count(level, f.ship()).crew() == 3, "crew on board: " + ShipBunks.count(level, f.ship()));
            level.setDayTime(nextDay(h, 1000L));
        });
        h.runAfterDelay(NIGHT_AT + 2 * SETTLE + 1, () -> {
            h.assertTrue(CrewMorale.get(sleeper) == 75, "morale after a night in the hammock: " + CrewMorale.get(sleeper));
            h.assertTrue(CrewMorale.get(other) == 60, "morale after a night without: " + CrewMorale.get(other));
            h.assertTrue(CrewMorale.get(onDuty) == 70, "morale after a night on duty: " + CrewMorale.get(onDuty));
            h.assertTrue(sleeper.getVehicle() == null && sleeper.rest() == null && !sleeper.isResting(), "the sleeper is still in the hammock");
            h.assertTrue(HammockSeat.at(level, foot).isEmpty(), "the hammock seat stayed");
            h.assertTrue(onDuty.isAtStation(), "the crew member on duty left the winch at dawn");
            for (CrewMember c : List.of(sleeper, other, onDuty)) {
                h.assertTrue(c.nightOutcome() == NightOutcome.NONE, "the night was not settled: " + c.nightOutcome());
            }
            cleanup(sleeper, other, onDuty);
            h.succeed();
        });
    }

    /** At night an order gets the sleeper up at once: the job board seats it at the winch, the hammock seat goes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = NIGHT_BATCH + "order")
    public static void anOrderAtNightGetsTheSleeperUp(GameTestHelper h) {
        upkeepOff(h);
        Fixture f = ship(h, x -> { });
        ServerLevel level = h.getLevel();
        CrewMember sleeper = onDeck(h, f, 20, 20);
        BlockPos foot = plot(f, 19, 7, 19);
        night(h);
        h.runAfterDelay(NIGHT_AT + SETTLE, () -> {
            h.assertTrue(sleeper.getVehicle() instanceof HammockSeat, "not in the hammock: " + sleeper.getVehicle());
            WhistleOrders.Result r = WhistleOrders.handle(captain(h, f, 0f), WhistleOrder.HOIST.id());
            h.assertTrue(r.jobs() == 1, "hoist: " + r);
            JobBoard.pass(level, f.ship().id());
            h.assertTrue(sleeper.assignment() != null && sleeper.assignment().pos().equals(f.winch()), "the sleeper did not take the winch: " + sleeper.assignment());
            h.assertTrue(sleeper.isAtStation(), "not seated at the winch");
            h.assertTrue(sleeper.rest() == null && !sleeper.isResting(), "still resting");
            h.assertTrue(sleeper.nightOutcome() == NightOutcome.ON_DUTY, "night: " + sleeper.nightOutcome());
        });
        h.runAfterDelay(NIGHT_AT + 2 * SETTLE, () -> {
            h.assertTrue(HammockSeat.at(level, foot).isEmpty(), "the hammock seat stayed");
            level.setDayTime(nextDay(h, 1000L));
        });
        h.runAfterDelay(NIGHT_AT + 3 * SETTLE, () -> {
            h.assertTrue(CrewMorale.get(sleeper) == 70, "morale after a night called to duty: " + CrewMorale.get(sleeper));
            h.assertTrue(sleeper.isAtStation(), "it left the winch at dawn");
            JobBoard.clear(f.ship().id());
            cleanup(sleeper);
            h.succeed();
        });
    }

    /** {@code crew.morale.enabled = false}: nobody turns in, and morale stays at start through the night. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = "pirates_n_ships_config_crew_morale")
    public static void disabledMoraleIsFrozen(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.MORALE_ENABLED, false);
        Fixture f = ship(h, x -> { });
        ServerLevel level = h.getLevel();
        CrewMember a = onDeck(h, f, 20, 20);
        CrewMember b = onDeck(h, f, 21, 21);
        a.setStoredMorale(40);
        h.assertTrue(CrewMorale.get(a) == 70, "disabled morale must read as start: " + CrewMorale.get(a));
        h.assertTrue(CrewMorale.adjust(a, -20, "test") == 70 && a.storedMorale() == 40, "adjust changed disabled morale");
        night(h);
        h.runAfterDelay(NIGHT_AT + SETTLE, () -> {
            h.assertTrue(a.rest() == null && a.getVehicle() == null && b.rest() == null, "a crew member turned in with morale off");
            h.assertTrue(a.nightOutcome() == NightOutcome.NONE && b.nightOutcome() == NightOutcome.NONE, "a night was recorded");
            level.setDayTime(nextDay(h, 1000L));
        });
        h.runAfterDelay(NIGHT_AT + 2 * SETTLE + 1, () -> {
            h.assertTrue(CrewMorale.get(a) == 70 && CrewMorale.get(b) == 70, "morale moved: " + CrewMorale.get(a) + ", " + CrewMorale.get(b));
            h.assertTrue(a.storedMorale() == 40, "the stored value changed: " + a.storedMorale());
            cleanup(a, b);
            h.succeed();
        });
    }
}
