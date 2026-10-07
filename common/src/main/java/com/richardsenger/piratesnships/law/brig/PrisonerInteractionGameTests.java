package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.world.CrimeLog;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * LA2 (docs/design.md §13.1, §13.3) on real officers, prisoners and a test ship: the fine at a navy officer, ransoming a
 * led navy soldier, press-ganging a sailor on the player's ship, releasing by hand, a hostile officer and the port rule.
 */
public final class PrisonerInteractionGameTests {

    private static volatile Field testInfoField;

    private PrisonerInteractionGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PrisonerInteractionGameTests.class);
    }

    // --- Helpers ------------------------------------------------------------------------------------------------

    private static NavyOfficer officer(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(MobContent.NAVY_OFFICER.get(), 1, 1, 1);
    }

    private static int count(Player player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static <T extends Mob> T captured(GameTestHelper helper, Player captor, T mob) {
        mob.setHealth(mob.getMaxHealth() * 0.2f);
        helper.assertValueEqual(BrigService.capture(captor, mob), CaptureRules.Result.OK, "captured " + mob.getName().getString());
        return mob;
    }

    private static InteractionResult use(Player player, LivingEntity target, ItemStack inMainHand) {
        player.setItemInHand(InteractionHand.MAIN_HAND, inMainHand);
        return CommonEvents.ENTITY_INTERACT.invoker().onInteract(player, target, InteractionHand.MAIN_HAND);
    }

    /** A survival mock player added to the level (the area query must find its prisoners), discarded at the end. */
    private static Player playerInLevel(GameTestHelper helper, Vec3 relative) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        helper.getLevel().addFreshEntity(player);
        testInfo(helper).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { player.discard(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { player.discard(); }
        });
        return player;
    }

    /** {@code GameTestHelper#testInfo} is private and has no getter in 1.21.1. */
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

    // --- Fines --------------------------------------------------------------------------------------------------

    /**
     * Score 5 and 20 doubloons in hand: the officer takes 15 (5 points at 3), 5 coins stay, the score is clean. Again:
     * "You owe the Crown nothing", no coins taken. Score 5 and 10 coins: 3 points for 9, score 2, one coin left.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void fineAtOfficerPaysWhatTheCoinsCover(GameTestHelper helper) {
        NavyOfficer officer = officer(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        LawService.setScore(player, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TradeContent.DOUBLOON.get(), 20));
        InteractionResult r = officer.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "the officer takes the fine, got " + r);
        helper.assertValueEqual(Wallet.count(player), 5L, "15 doubloons taken, 5 left");
        helper.assertValueEqual(LawService.displayScore(player), 0, "score paid off");

        InteractionResult again = officer.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(again.consumesAction(), "the officer answers with nothing owed, got " + again);
        helper.assertValueEqual(Wallet.count(player), 5L, "nothing taken without a score");

        LawService.setScore(player, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TradeContent.DOUBLOON.get(), 10));
        helper.assertTrue(officer.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "partial fine");
        helper.assertValueEqual(Wallet.count(player), 1L, "3 points for 9 doubloons, one left");
        helper.assertValueEqual(LawService.displayScore(player), 2, "2 points left");
        helper.succeed();
    }

    /** A wanted player (score 60, the navy is hostile) is refused: coins and score stay. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void hostileOfficerRefusesTheFine(GameTestHelper helper) {
        NavyOfficer officer = officer(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        LawService.setScore(player, 60);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TradeContent.DOUBLOON.get(), 64));
        helper.assertTrue(officer.attacksOnSight(player), "the officer is hostile to a wanted player");
        InteractionResult r = officer.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "refused, not passed on, got " + r);
        helper.assertValueEqual(Wallet.count(player), 64L, "no coins taken");
        helper.assertValueEqual(LawService.displayScore(player), 60, "score unchanged");
        LawService.setScore(player, 0); // withdraws the navy bounty
        helper.succeed();
    }

    // --- Ransom -------------------------------------------------------------------------------------------------

    /**
     * Leading a shackled navy soldier to an officer with an empty hand: the officer pays the common ransom, the soldier
     * is unshackled (still there, walking to the officer) and the shackles come back.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void ransomingALedNavySoldierPaysAndFreesIt(GameTestHelper helper) {
        NavyOfficer officer = officer(helper);
        Player player = playerInLevel(helper, new Vec3(3.5, 1, 1.5));
        NavySoldier soldier = captured(helper, player, helper.spawnWithNoFreeWill(MobContent.NAVY_SOLDIER.get(), 2, 1, 3));
        helper.assertTrue(BrigService.state(soldier).led(), "the soldier is led");
        InteractionResult r = officer.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "the officer ransoms the soldier, got " + r);
        helper.assertValueEqual(Wallet.count(player), (long) BrigConfig.RANSOM_COMMON.get(), "common ransom paid");
        helper.assertFalse(BrigService.isPrisoner(soldier), "the soldier is free");
        helper.assertFalse(soldier.isRemoved(), "the soldier stays and walks to the officer");
        helper.assertValueEqual(count(player, LawContent.SHACKLES.get()), 1, "shackles returned");
        soldier.discard();
        helper.succeed();
    }

    /** With {@code law.ransom_needs_port} and no navy outpost around, the officer refuses and the soldier stays a prisoner. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_ransom")
    public static void ransomNeedsAPortWhenConfigured(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.RANSOM_NEEDS_PORT, true);
        NavyOfficer officer = officer(helper);
        Player player = playerInLevel(helper, new Vec3(3.5, 1, 1.5));
        NavySoldier soldier = captured(helper, player, helper.spawnWithNoFreeWill(MobContent.NAVY_SOLDIER.get(), 2, 1, 3));
        InteractionResult r = officer.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "refused, got " + r);
        helper.assertValueEqual(Wallet.count(player), 0L, "no ransom without a port");
        helper.assertTrue(BrigService.isPrisoner(soldier), "the soldier stays a prisoner");
        BrigService.clear(soldier);
        soldier.discard();
        helper.succeed();
    }

    // --- Press-gang ---------------------------------------------------------------------------------------------

    /** A sailor without AI standing on the test ship's deck at relative (x, 9, z) (helm at (19, 9, 18)). */
    private static Sailor sailorOnDeck(GameTestHelper h, Fixture f, int x, int z) {
        Sailor s = MobContent.SAILOR.get().create(h.getLevel());
        if (s == null) throw new AssertionError("no sailor");
        Vec3 p = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm().offset(x - 19, 0, z - 18)));
        s.moveTo(p.x, p.y, p.z, 0, 0);
        s.setNoAi(true);
        h.getLevel().addFreshEntity(s);
        return s;
    }

    /**
     * The whistle on a shackled sailor on the test ship (no owner): refused while the ship belongs to someone else, then
     * the sailor becomes a crew member with morale 30 and the captain gets a press_gang crime. A shackled sailor on the
     * ground beside the ship is refused ("Bring them aboard your ship first").
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void pressGangOnTheOwnShipOnly(GameTestHelper helper) {
        Fixture f = StationGameTests.ship(helper, false, x -> { });
        Player captain = helper.makeMockPlayer(GameType.SURVIVAL);
        Sailor aboard = captured(helper, captain, sailorOnDeck(helper, f, 20, 20));
        Sailor ashore = captured(helper, captain, helper.spawnWithNoFreeWill(MobContent.SAILOR.get(), 5, 5, 5));
        ItemStack whistle = new ItemStack(StationContent.CAPTAINS_WHISTLE.get());
        helper.runAfterDelay(5, () -> {
            InteractionResult off = use(captain, ashore, whistle);
            helper.assertTrue(off.consumesAction(), "the whistle answers on a shackled sailor, got " + off);
            helper.assertTrue(BrigService.isPrisoner(ashore) && !ashore.isRemoved(), "a sailor off the ship is not press-ganged");
            helper.assertValueEqual(PrisonerOutcomes.checkPressGang(ashore, captain), PrisonerInteractionRules.PressGang.NOT_ON_SHIP, "ashore");

            ShipRegistry registry = ShipRegistry.get(helper.getLevel().getServer());
            UUID shipId = f.ship().id();
            Optional<ShipData> before = registry.find(shipId);
            registry.put(ShipData.create(shipId, Optional.of(UUID.randomUUID()), helper.getLevel().dimension().location()));
            helper.assertValueEqual(PrisonerOutcomes.checkPressGang(aboard, captain), PrisonerInteractionRules.PressGang.NOT_YOUR_SHIP, "someone else's ship");
            helper.assertTrue(use(captain, aboard, whistle).consumesAction() && BrigService.isPrisoner(aboard), "refused on someone else's ship");
            if (before.isPresent()) registry.put(before.get()); else registry.remove(shipId);

            Vec3 at = aboard.position();
            InteractionResult r = use(captain, aboard, whistle);
            helper.assertTrue(r.consumesAction(), "press-ganged, got " + r);
            helper.assertTrue(aboard.isRemoved(), "the sailor is replaced");
            List<CrewMember> crew = helper.getLevel().getEntitiesOfClass(CrewMember.class, new AABB(at, at).inflate(1.0));
            helper.assertValueEqual(crew.size(), 1, "one crew member where the sailor stood");
            helper.assertValueEqual(CrewMorale.get(crew.get(0)), 30, "press-gang morale");
            helper.assertValueEqual(LawService.displayScore(captain), CrimeType.PRESS_GANG.defaultSeverity(), "press_gang points");
            helper.assertValueEqual(CrimeLog.last(captain.getUUID()).map(CrimeLog.Entry::type).orElse(null), CrimeType.PRESS_GANG, "last crime");
            crew.forEach(Mob::discard);
            BrigService.clear(ashore);
            ashore.discard();
            helper.succeed();
        });
    }

    // --- Release ------------------------------------------------------------------------------------------------

    /**
     * Sneak-use with an empty hand on one's own shackled pirate: free, shackles back, one pirate release on the record.
     * Not sneaking, or someone else's prisoner: nothing happens.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void releaseUnshacklesAndRecords(GameTestHelper helper) {
        Player captor = helper.makeMockPlayer(GameType.SURVIVAL);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        Pirate pirate = captured(helper, captor, helper.spawnWithNoFreeWill(MobContent.PIRATE.get(), 2, 1, 2));

        helper.assertValueEqual(use(captor, pirate, ItemStack.EMPTY), InteractionResult.PASS, "not sneaking");
        stranger.setShiftKeyDown(true);
        helper.assertValueEqual(use(stranger, pirate, ItemStack.EMPTY), InteractionResult.PASS, "someone else's prisoner");
        helper.assertTrue(BrigService.isPrisoner(pirate), "still a prisoner");

        captor.setShiftKeyDown(true);
        InteractionResult r = use(captor, pirate, ItemStack.EMPTY);
        helper.assertTrue(r.consumesAction(), "released, got " + r);
        helper.assertFalse(BrigService.isPrisoner(pirate), "the pirate is free");
        helper.assertFalse(pirate.isRemoved(), "the pirate stays in the world");
        helper.assertValueEqual(count(captor, LawContent.SHACKLES.get()), 1, "shackles back");
        helper.assertValueEqual(LawService.releases(captor).count(Faction.PIRATES), 1, "pirate release recorded");
        helper.assertValueEqual(LawService.releases(captor).total(), 1, "one release in total");
        pirate.discard();
        helper.succeed();
    }
}
