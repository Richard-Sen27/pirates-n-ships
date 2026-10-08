package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.galley.PantryBlockEntity;
import com.richardsenger.piratesnships.crew.galley.WaterBarrelBlockEntity;
import com.richardsenger.piratesnships.crew.hammock.RestRules;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Crew upkeep at dawn (CR2, docs/design.md §7.3, §7.4) on the 5×4×5 test ship of {@link StationGameTests} resting on
 * land (helm at relative (19, 9, 18), deck at y = 9, hold y = 6..7 inside x, z = 18..20). The pantry stands in the hold
 * at (18, 6, 18), the water barrel at (20, 6, 18), the pay chest at (18, 6, 20). Two crew stand on the deck without AI.
 * <p>
 * Every test moves the level's clock (morning, midnight, next morning = one dawn), so each runs in a batch of its own.
 * The hammock rule is set to 0 in all of them, so only upkeep changes morale.
 */
public final class UpkeepGameTests {

    private static final String BATCH = "pirates_n_ships_crew_upkeep_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_crew_";
    /** Ticks one forced dawn takes ({@link #dawnAt}); checks of that dawn run from then on. */
    private static final int DAWN = 6;

    private UpkeepGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(UpkeepGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ship(Fixture f, CrewMember a, CrewMember b) {
        UUID id() {
            return f.ship().id();
        }
    }

    /** No hammock rule: only upkeep moves morale. */
    private static void isolate(GameTestHelper h) {
        ConfigOverrides.during(h, CrewConfig.NO_HAMMOCK_PER_NIGHT, 0);
        ConfigOverrides.during(h, CrewConfig.HAMMOCK_REST_PER_NIGHT, 0);
    }

    /** The test ship with the galley and chest blocks {@code extra} places, and two crew on deck. */
    private static Ship ship(GameTestHelper h, Consumer<GameTestHelper> extra) {
        isolate(h);
        morning(h);
        Fixture f = StationGameTests.ship(h, false, extra);
        return new Ship(f, onDeck(h, f, 20, 20), onDeck(h, f, 18, 19));
    }

    private static void pantry(GameTestHelper h) {
        h.setBlock(new BlockPos(18, 6, 18), CrewContent.PANTRY.get());
    }

    private static void barrel(GameTestHelper h) {
        h.setBlock(new BlockPos(20, 6, 18), CrewContent.WATER_BARREL.get());
    }

    private static void chest(GameTestHelper h) {
        h.setBlock(new BlockPos(18, 6, 20), Blocks.CHEST);
    }

    private static void galleyAndChest(GameTestHelper h) {
        pantry(h);
        barrel(h);
        chest(h);
    }

    private static PantryBlockEntity pantryOf(GameTestHelper h, Ship s) {
        return (PantryBlockEntity) h.getLevel().getBlockEntity(StationGameTests.find(h, s.f().ship(), CrewContent.PANTRY.get()));
    }

    private static WaterBarrelBlockEntity barrelOf(GameTestHelper h, Ship s) {
        return (WaterBarrelBlockEntity) h.getLevel().getBlockEntity(StationGameTests.find(h, s.f().ship(), CrewContent.WATER_BARREL.get()));
    }

    private static ChestBlockEntity chestOf(GameTestHelper h, Ship s) {
        return (ChestBlockEntity) h.getLevel().getBlockEntity(StationGameTests.find(h, s.f().ship(), Blocks.CHEST));
    }

    private static void coins(GameTestHelper h, Ship s, int n) {
        chestOf(h, s).setItem(0, new ItemStack(TradeContent.DOUBLOON.get(), n));
    }

    /** A crew member standing on the deck at relative (x, 9, z), without AI. */
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

    /**
     * Forces one dawn from tick {@code start}: morning of today, midnight two ticks later, the next morning two ticks
     * after that. The day tick has run by {@code start + DAWN}.
     */
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

    private static ShipDayTick.DayReport report(GameTestHelper h, Ship s) {
        ShipDayTick.DayReport r = ShipDayTick.lastReport(s.id());
        if (r == null) throw new AssertionError("no day tick ran on the ship");
        return r;
    }

    private static boolean told(ShipDayTick.DayReport r, String key, Object... args) {
        return r.ownerLines().stream().anyMatch(c -> containsKey(c, key, args));
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

    private static <T extends SeafarerMob> List<T> mobs(GameTestHelper h, Ship s, Class<T> type) {
        return h.getLevel().getEntitiesOfClass(type, CrewStations.worldBox(s.f().ship(), 4), SeafarerMob::isAlive);
    }

    private static void cleanup(GameTestHelper h, Ship s) {
        for (CrewMember c : List.of(s.a(), s.b())) {
            if (c.getVehicle() != null) c.getVehicle().discard();
            c.discard();
        }
        mobs(h, s, SeafarerMob.class).forEach(SeafarerMob::discard);
    }

    // ------------------------------------------------------------------ provisions

    /**
     * Pantry with hardtack, a full water barrel, 10 doubloons, two crew: after one dawn the crew ate (fewer hardtack, less
     * water), the scurvy clock advanced, morale rose by the pay (+1), and the owner line says "Paid 2 crew, 4
     * doubloons".
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "fed")
    public static void fedCrewEatsAndIsPaid(GameTestHelper h) {
        Ship s = ship(h, UpkeepGameTests::galleyAndChest);
        pantryOf(h, s).setItem(0, new ItemStack(CrewContent.HARDTACK.get(), 10));
        barrelOf(h, s).setRations(16, ProvisionsConfig.settings());
        coins(h, s, 10);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            ShipDayTick.DayReport r = report(h, s);
            int hardtack = pantryOf(h, s).getItem(0).getCount();
            h.assertTrue(hardtack == 7, "two crew eat 12 nutrition a day = 3 hardtack; left: " + hardtack);
            h.assertTrue(barrelOf(h, s).rations() == 14, "water left: " + barrelOf(h, s).rations());
            h.assertTrue(!r.provisions().hungry() && !r.provisions().thirsty(), "fed crew is hungry or thirsty: " + r.provisions());
            ShipUpkeep up = ShipUpkeepData.get(h.getLevel().getServer()).get(s.id());
            h.assertTrue(up.provisioning().ticksSinceAntiScurvy() >= ShipDayTick.DAY, "the provisioning state did not advance: " + up.provisioning());
            h.assertTrue(up.workSpeed() == 1.0, "work speed of a fed crew: " + up.workSpeed());
            for (CrewMember c : List.of(s.a(), s.b())) {
                h.assertTrue(CrewMorale.get(c) == 71, "morale after a fed, paid day: " + CrewMorale.get(c));
                h.assertTrue(!c.isUnpaid(), "a paid member is marked unpaid");
            }
            h.assertTrue(Wallet.count(chestOf(h, s)) == 6, "coins left: " + Wallet.count(chestOf(h, s)));
            h.assertTrue(told(r, UpkeepText.PAID, 2, 4L), "owner lines: " + r.ownerLines());
            h.assertTrue(up.lastPay().equals(new ShipUpkeep.PayRecord(true, 2, 0, 4)), "pay record: " + up.lastPay());
            cleanup(h, s);
            h.succeed();
        });
    }

    /**
     * Empty pantry and barrel: the crew go hungry and thirsty (−35 morale), a winch hoist takes longer, and the owner
     * hears that there is no food and no water.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "starving")
    public static void emptyGalleyCostsMoraleAndSlowsWork(GameTestHelper h) {
        Ship s = ship(h, UpkeepGameTests::galleyAndChest);
        coins(h, s, 10);
        ServerLevel level = h.getLevel();
        StationRef winch = Stations.at(level, s.f().winch());
        h.assertTrue(winch != null, "no winch station");
        int before = Stations.workTicks(level, winch, SailOrder.HOIST);
        h.assertTrue(before > 0, "nothing to hoist: " + before);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            ShipDayTick.DayReport r = report(h, s);
            h.assertTrue(r.provisions().hungry() && r.provisions().thirsty(), "not hungry and thirsty: " + r.provisions());
            h.assertTrue(r.moraleDelta() == -35, "provisions morale: " + r.moraleDelta());
            for (CrewMember c : List.of(s.a(), s.b())) {
                h.assertTrue(CrewMorale.get(c) == 36, "morale after a day without food and water (and paid): " + CrewMorale.get(c));
            }
            double speed = ShipUpkeepData.get(level.getServer()).get(s.id()).workSpeed();
            h.assertTrue(Math.abs(speed - 0.375) < 1e-9, "work speed of a hungry, thirsty crew: " + speed);
            int after = Stations.workTicks(level, winch, SailOrder.HOIST);
            h.assertTrue(after == Stations.scaledTicks(before, speed) && after > before, "hoist ticks " + before + " -> " + after);
            h.assertTrue(told(r, UpkeepText.NO_FOOD) && told(r, UpkeepText.NO_WATER), "owner lines: " + r.ownerLines());
            cleanup(h, s);
            h.succeed();
        });
    }

    /** {@code scurvy_onset_days = 0.5}: a day on hardtack alone gives the crew scurvy, and weakness. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "upkeep_scurvy")
    public static void scurvyGivesWeakness(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.SCURVY_ONSET_DAYS, 0.5);
        ConfigOverrides.during(h, CrewConfig.WAGES_ENABLED, false);
        Ship s = ship(h, x -> {
            pantry(x);
            barrel(x);
        });
        pantryOf(h, s).setItem(0, new ItemStack(CrewContent.HARDTACK.get(), 10));
        barrelOf(h, s).setRations(16, ProvisionsConfig.settings());
        h.assertTrue(!s.a().hasEffect(MobEffects.WEAKNESS), "weak before the voyage");
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            ShipDayTick.DayReport r = report(h, s);
            h.assertTrue(r.effects().scurvy(), "no scurvy after a day without citrus: " + r.effects());
            for (CrewMember c : List.of(s.a(), s.b())) {
                h.assertTrue(c.hasEffect(MobEffects.WEAKNESS), "no weakness with scurvy");
            }
            h.assertTrue(told(r, UpkeepText.SCURVY), "owner lines: " + r.ownerLines());
            cleanup(h, s);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ wages

    /**
     * Provisions off, 3 doubloons, two crew: one is paid (+1), the other is not (−8, marked unpaid, "unpaid" on its
     * whistle line); 1 coin stays; {@code /pirates crew info} shows the pay.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "wages")
    public static void shortPayLeavesOneUnpaid(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        Ship s = ship(h, UpkeepGameTests::chest);
        coins(h, s, 3);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            ShipDayTick.DayReport r = report(h, s);
            h.assertTrue(r.day().paid() == 1 && r.day().unpaid() == 1, "paid / unpaid: " + r.day());
            List<Integer> morale = List.of(CrewMorale.get(s.a()), CrewMorale.get(s.b()));
            h.assertTrue(morale.contains(71) && morale.contains(62), "morale: " + morale);
            CrewMember unpaid = CrewMorale.get(s.a()) == 62 ? s.a() : s.b();
            h.assertTrue(unpaid.isUnpaid(), "the unpaid member is not marked");
            h.assertTrue(Wallet.count(chestOf(h, s)) == 1, "coins left: " + Wallet.count(chestOf(h, s)));
            h.assertTrue(told(r, UpkeepText.UNPAID, 1), "owner lines: " + r.ownerLines());
            Component line = com.richardsenger.piratesnships.crew.hammock.CrewInfo.crewLine(h.getLevel(), unpaid);
            h.assertTrue(containsKey(line, UpkeepText.STATUS_UNPAID), "the whistle line does not say unpaid: " + line);
            List<Component> info = UpkeepInfo.lines(h.getLevel(), s.f().ship());
            h.assertTrue(info.stream().anyMatch(c -> containsKey(c, UpkeepText.INFO_PAY, 1, 1, 2L)), "info: " + info);
            h.assertTrue(info.stream().anyMatch(c -> containsKey(c, UpkeepText.INFO_SUPPLIES)), "info: " + info);
            cleanup(h, s);
            h.succeed();
        });
    }

    /** Provisions off, 10 doubloons, two crew: 4 coins go, both gain 1 morale. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "wages_paid")
    public static void fullPayRaisesMorale(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        Ship s = ship(h, UpkeepGameTests::chest);
        coins(h, s, 10);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            ShipDayTick.DayReport r = report(h, s);
            h.assertTrue(r.day().paid() == 2 && r.day().unpaid() == 0, "paid / unpaid: " + r.day());
            h.assertTrue(CrewMorale.get(s.a()) == 71 && CrewMorale.get(s.b()) == 71, "morale: " + CrewMorale.get(s.a()) + ", " + CrewMorale.get(s.b()));
            h.assertTrue(Wallet.count(chestOf(h, s)) == 6, "coins left: " + Wallet.count(chestOf(h, s)));
            cleanup(h, s);
            h.succeed();
        });
    }

    /** {@code crew.wages.enabled = false}: nothing is paid and morale does not move. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "wages_off")
    public static void wagesOffPayNothing(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.WAGES_ENABLED, false);
        Ship s = ship(h, UpkeepGameTests::chest);
        coins(h, s, 10);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            ShipDayTick.DayReport r = report(h, s);
            h.assertTrue(r.day().paid() == 0 && r.day().unpaid() == 0, "paid / unpaid: " + r.day());
            h.assertTrue(CrewMorale.get(s.a()) == 70 && CrewMorale.get(s.b()) == 70, "morale moved");
            h.assertTrue(!s.a().isUnpaid() && !s.b().isUnpaid(), "marked unpaid with wages off");
            h.assertTrue(Wallet.count(chestOf(h, s)) == 10, "coins taken: " + Wallet.count(chestOf(h, s)));
            h.assertTrue(!told(r, UpkeepText.PAID) && !told(r, UpkeepText.UNPAID), "pay lines with wages off: " + r.ownerLines());
            cleanup(h, s);
            h.succeed();
        });
    }

    /**
     * A real server player in the player list (online in the test level), owner of the ship, carrying {@code coins}
     * doubloons. Taken out of the player list again by {@link #logOut}. The GameTest helper is deprecated for removal
     * in a later Minecraft; 1.21.1 still has it.
     */
    @SuppressWarnings("removal")
    private static ServerPlayer owner(GameTestHelper h, Ship s, int coins) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        registry.put(registry.find(s.id()).orElseThrow(() -> new AssertionError("no ship record")).withOwner(Optional.of(p.getUUID())));
        return p;
    }

    private static void logOut(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    /**
     * CRW2, {@code crew.wages.from_wallet} (default on): provisions off, 3 doubloons in the chest, the owner online with
     * 10: the chest pays first (all 3), the owner's purse the last 1; both crew paid; the pay record says 4 coins, 1 of
     * them from the wallet, and so do the owner line and {@code /pirates crew info}.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "wages_wallet")
    public static void walletPaysWhatTheChestCannot(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        Ship s = ship(h, UpkeepGameTests::chest);
        coins(h, s, 3);
        ServerPlayer owner = owner(h, s, 10);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            try {
                ShipDayTick.DayReport r = report(h, s);
                h.assertTrue(r.day().paid() == 2 && r.day().unpaid() == 0, "paid / unpaid: " + r.day());
                h.assertTrue(Wallet.count(chestOf(h, s)) == 0, "coins left in the chest: " + Wallet.count(chestOf(h, s)));
                h.assertTrue(Wallet.count(owner) == 9, "coins left in the purse: " + Wallet.count(owner));
                ShipUpkeep up = ShipUpkeepData.get(h.getLevel().getServer()).get(s.id());
                h.assertTrue(up.lastPay().equals(new ShipUpkeep.PayRecord(true, 2, 0, 4, 1)), "pay record: " + up.lastPay());
                h.assertTrue(told(r, UpkeepText.PAID_WALLET, 2, 4L, 1L), "owner lines: " + r.ownerLines());
                List<Component> info = UpkeepInfo.lines(h.getLevel(), s.f().ship());
                h.assertTrue(info.stream().anyMatch(c -> containsKey(c, UpkeepText.INFO_PAY_WALLET, 2, 0, 4L, 1L)), "info: " + info);
                h.assertTrue(CrewMorale.get(s.a()) == 71 && CrewMorale.get(s.b()) == 71, "morale: " + CrewMorale.get(s.a()) + ", " + CrewMorale.get(s.b()));
            } finally {
                logOut(h, owner);
                cleanup(h, s);
            }
            h.succeed();
        });
    }

    /** {@code crew.wages.from_wallet = false}: an empty chest and an owner with 10 doubloons: nobody is paid, the purse stays full. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = CONFIG_BATCH + "wages_wallet_off")
    public static void walletOffLeavesCrewUnpaid(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.WAGES_FROM_WALLET, false);
        Ship s = ship(h, UpkeepGameTests::chest);
        ServerPlayer owner = owner(h, s, 10);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            try {
                ShipDayTick.DayReport r = report(h, s);
                h.assertTrue(r.day().paid() == 0 && r.day().unpaid() == 2, "paid / unpaid: " + r.day());
                h.assertTrue(Wallet.count(owner) == 10, "coins taken from the purse: " + Wallet.count(owner));
                ShipUpkeep up = ShipUpkeepData.get(h.getLevel().getServer()).get(s.id());
                h.assertTrue(up.lastPay().equals(new ShipUpkeep.PayRecord(true, 0, 2, 0, 0)), "pay record: " + up.lastPay());
                h.assertTrue(s.a().isUnpaid() && s.b().isUnpaid(), "not marked unpaid");
            } finally {
                logOut(h, owner);
                cleanup(h, s);
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ desertion and mutiny

    /**
     * {@code crew.desertion.at_port_only = false} (the behaviour before CRW2; with it on see {@code DesertionGameTests}):
     * provisions and wages off; one member at morale 10: after the first dawn it is still crew (one low day), after
     * the second it has deserted: a sailor stands in its place, it is no longer crew, the other member stays.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = CONFIG_BATCH + "desertion")
    public static void lowMoraleDeserts(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.WAGES_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.DESERT_AT_PORT_ONLY, false);
        Ship s = ship(h, x -> { });
        s.a().setStoredMorale(10);
        dawnAt(h, 0);
        h.runAfterDelay(DAWN + 1, () -> {
            h.assertTrue(s.a().isAlive() && s.a().lowMoraleDays() == 1, "after one low dawn: alive " + s.a().isAlive() + ", days " + s.a().lowMoraleDays());
            h.assertTrue(s.b().lowMoraleDays() == 0, "the content member counts low days");
        });
        dawnAt(h, DAWN + 2);
        h.runAfterDelay(2 * DAWN + 3, () -> {
            ShipDayTick.DayReport r = report(h, s);
            h.assertTrue(s.a().isRemoved(), "the unhappy member is still there");
            h.assertTrue(r.deserters().equals(List.of(s.a().getUUID())), "deserters: " + r.deserters());
            List<CrewMember> crew = ShipBunks.crewOf(h.getLevel(), s.f().ship());
            h.assertTrue(crew.equals(List.of(s.b())), "crew after the desertion: " + crew);
            h.assertTrue(mobs(h, s, Sailor.class).size() == 1, "sailors: " + mobs(h, s, Sailor.class));
            h.assertTrue(told(r, UpkeepText.DESERTED), "owner lines: " + r.ownerLines());
            cleanup(h, s);
            h.succeed();
        });
    }

    /**
     * {@code crew.mutiny.enabled}: provisions and wages off, both members at morale 10 (average below 15). Two dawns
     * pass without desertion (they plot), at the third the crew turns into pirates and the ship loses its owner but
     * keeps its name.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 150, batch = CONFIG_BATCH + "mutiny")
    public static void mutinyTakesTheShip(GameTestHelper h) {
        ConfigOverrides.during(h, ProvisionsConfig.CONSUMPTION_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.WAGES_ENABLED, false);
        ConfigOverrides.during(h, CrewConfig.MUTINY_ENABLED, true);
        Ship s = ship(h, x -> { });
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        ShipData data = registry.find(s.id()).orElseThrow(() -> new AssertionError("no ship record"));
        registry.put(data.withName("Sea Wolf").withOwner(Optional.of(UUID.randomUUID())));
        s.a().setStoredMorale(10);
        s.b().setStoredMorale(10);
        dawnAt(h, 0);
        dawnAt(h, DAWN + 2);
        h.runAfterDelay(2 * DAWN + 3, () -> {
            h.assertTrue(s.a().isAlive() && s.b().isAlive(), "a member left before the mutiny");
            h.assertTrue(ShipUpkeepData.get(h.getLevel().getServer()).get(s.id()).lowMoraleDays() == 2, "ship low days: "
                    + ShipUpkeepData.get(h.getLevel().getServer()).get(s.id()).lowMoraleDays());
        });
        dawnAt(h, 2 * DAWN + 4);
        h.runAfterDelay(3 * DAWN + 5, () -> {
            ShipDayTick.DayReport r = report(h, s);
            h.assertTrue(r.day().mutiny(), "no mutiny: " + r.day());
            h.assertTrue(s.a().isRemoved() && s.b().isRemoved(), "crew members are still there");
            h.assertTrue(mobs(h, s, Pirate.class).size() == 2, "pirates: " + mobs(h, s, Pirate.class));
            ShipData after = registry.find(s.id()).orElseThrow();
            h.assertTrue(after.owner().isEmpty(), "the ship kept its owner");
            h.assertTrue(after.name().equals("Sea Wolf"), "the ship lost its name: " + after.name());
            h.assertTrue(told(r, UpkeepText.MUTINY, Component.literal("Sea Wolf")), "owner lines: " + r.ownerLines());
            cleanup(h, s);
            h.succeed();
        });
    }
}
