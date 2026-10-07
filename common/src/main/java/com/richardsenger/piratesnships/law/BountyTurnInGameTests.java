package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.brig.CaptureRules;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.proof.BountyProof;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.proof.ProofContent;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.UUID;

/**
 * Turning in at a navy officer (docs/design.md §9, §13.2) through the officer's own interaction ({@code interact} →
 * {@code mobInteract}), with real officers, pirates and prisoners: bounty proofs, alive delivery of a pirate and of a
 * player, refusals and the config switches.
 */
public final class BountyTurnInGameTests {

    private static volatile Field testInfoField;

    private BountyTurnInGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BountyTurnInGameTests.class);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private static NavyOfficer officer(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(MobContent.NAVY_OFFICER.get(), 1, 1, 1);
    }

    private static int count(Player player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** A weakened pirate at the test-relative position, put in {@code captor}'s shackles. */
    private static Pirate capturedPirate(GameTestHelper helper, Player captor, int x, int z) {
        Pirate pirate = helper.spawnWithNoFreeWill(MobContent.PIRATE.get(), x, 1, z);
        pirate.setHealth(pirate.getMaxHealth() * 0.2f);
        helper.assertValueEqual(BrigService.capture(captor, pirate), CaptureRules.Result.OK, "pirate captured");
        return pirate;
    }

    private static void clearBounties(GameTestHelper helper, UUID... targets) {
        BountyBoardData data = BountyBoardData.get(helper.getLevel().getServer());
        for (UUID t : targets) data.setBoard(data.board().clearTarget(t));
    }

    /**
     * A survival mock player that is in the level (a prisoner must be found by the area query), discarded when the
     * test ends. A plain {@link Player}: nothing is ever sent to it.
     */
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

    // --- bounty proofs ------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void proofHandedToOfficerPaysAndWipesTheScore(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        NavyOfficer officer = officer(helper);
        Mob target = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 6, 1, 6);
        LawService.setScore(target, 60); // navy bounty of 2 per point
        long bounty = LawService.bountyTotal(server, target.getUUID());
        helper.assertValueEqual(bounty, 120L, "navy bounty on the target");

        Player hunter = helper.makeMockPlayer(GameType.SURVIVAL);
        hunter.setItemInHand(InteractionHand.MAIN_HAND, BountyProofItem.create(
                new BountyProof(target.getUUID(), target.getName().getString(), LawService.now(server), "hunter")));
        InteractionResult r = officer.interact(hunter, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "the officer takes the proof, got " + r);
        helper.assertValueEqual(Wallet.count(hunter), bounty, "paid the bounty in doubloons");
        helper.assertValueEqual(count(hunter, ProofContent.BOUNTY_PROOF.get()), 0, "proof consumed");
        helper.assertFalse(LawService.hasBounty(server, target.getUUID()), "bounty gone");
        helper.assertValueEqual(LawService.displayScore(target), 0, "target's score wiped");

        // The same proof again (from the off hand): nothing left, refused and kept
        ItemStack again = BountyProofItem.create(new BountyProof(target.getUUID(), "x", LawService.now(server), "hunter"));
        hunter.setItemInHand(InteractionHand.OFF_HAND, again);
        helper.assertTrue(officer.interact(hunter, InteractionHand.OFF_HAND).consumesAction(), "refusal is handled");
        helper.assertValueEqual(count(hunter, ProofContent.BOUNTY_PROOF.get()), 1, "refused proof is kept");
        helper.assertValueEqual(Wallet.count(hunter), bounty, "no second payout");
        clearBounties(helper, target.getUUID());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void wantedPlayerIsRefused(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        NavyOfficer officer = officer(helper);
        Mob target = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 6, 1, 6);
        LawService.setScore(target, 60);
        Player outlaw = helper.makeMockPlayer(GameType.SURVIVAL);
        LawService.setScore(outlaw, 100);
        helper.assertTrue(officer.attacksOnSight(outlaw), "the navy hunts the outlaw");
        outlaw.setItemInHand(InteractionHand.MAIN_HAND, BountyProofItem.create(
                new BountyProof(target.getUUID(), "x", LawService.now(server), "outlaw")));
        helper.assertTrue(officer.interact(outlaw, InteractionHand.MAIN_HAND).consumesAction(), "refusal is handled");
        helper.assertFalse(outlaw.getMainHandItem().isEmpty(), "proof kept");
        helper.assertValueEqual(Wallet.count(outlaw), 0L, "nothing paid");
        helper.assertTrue(LawService.hasBounty(server, target.getUUID()), "bounty still on the board");

        // A wanted captor can't deliver a prisoner either
        Pirate pirate = capturedPirate(helper, outlaw, 2, 2);
        outlaw.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        officer.interact(outlaw, InteractionHand.MAIN_HAND);
        helper.assertFalse(pirate.isRemoved(), "the navy keeps out of the outlaw's business");
        helper.assertTrue(BrigService.isPrisoner(pirate), "still the outlaw's prisoner");
        pirate.discard();
        clearBounties(helper, target.getUUID(), outlaw.getUUID());
        helper.succeed();
    }

    // --- prisoners ----------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void shackledPirateIsTurnedInForTheReward(GameTestHelper helper) {
        NavyOfficer officer = officer(helper);
        Player captor = helper.makeMockPlayer(GameType.SURVIVAL);
        Pirate near = capturedPirate(helper, captor, 2, 2);
        Pirate far = capturedPirate(helper, captor, 7, 7); // about 8.5 blocks from the officer
        int reward = LawConfig.TURN_IN_REWARDS.get(PirateTier.DECKHAND).get();
        helper.assertTrue(reward > 0, "the default pirate reward pays something");

        InteractionResult r = officer.interact(captor, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "delivered, got " + r);
        helper.assertValueEqual(Wallet.count(captor), (long) reward, "paid the pirate turn-in reward");
        helper.assertTrue(near.isRemoved(), "the navy leads the pirate away");
        helper.assertFalse(far.isRemoved(), "a prisoner out of range stays");
        helper.assertTrue(BrigService.isPrisoner(far), "and stays shackled");
        helper.assertValueEqual(count(captor, LawContent.SHACKLES.get()), 1, "the shackles come back");
        far.discard();
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_pirate_turn_in")
    public static void zeroRewardSwitchesThePirateTurnInOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.TURN_IN_REWARDS.get(PirateTier.DECKHAND), 0);
        NavyOfficer officer = officer(helper);
        Player captor = helper.makeMockPlayer(GameType.SURVIVAL);
        Pirate pirate = capturedPirate(helper, captor, 2, 2);
        officer.interact(captor, InteractionHand.MAIN_HAND);
        helper.assertFalse(pirate.isRemoved(), "the navy pays nothing for a pirate and leaves it");
        helper.assertTrue(BrigService.isPrisoner(pirate), "still a prisoner");
        helper.assertValueEqual(Wallet.count(captor), 0L, "nothing paid");
        helper.assertValueEqual(count(captor, LawContent.SHACKLES.get()), 0, "no shackles back");
        pirate.discard();
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void bountiedPlayerDeliveredAlivePaysTheAliveFactor(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        NavyOfficer officer = officer(helper);
        Player captor = helper.makeMockPlayer(GameType.SURVIVAL);
        Player prisoner = playerInLevel(helper, new Vec3(2.5, 1, 2.5));
        LawService.setScore(prisoner, 30);
        var placed = LawService.placeBounty(server, UUID.randomUUID(), "Tester",
                BountyTarget.player(prisoner.getUUID(), prisoner.getName().getString()), 40);
        helper.assertTrue(placed.placed(), "bounty placed: " + placed.outcome());
        prisoner.setHealth(4.0f);
        helper.assertValueEqual(BrigService.capture(captor, prisoner), CaptureRules.Result.OK, "player captured");

        InteractionResult r = officer.interact(captor, InteractionHand.MAIN_HAND);
        helper.assertTrue(r.consumesAction(), "delivered, got " + r);
        long expected = Math.round(40 * LawConfig.ALIVE_FACTOR.get());
        helper.assertValueEqual(Wallet.count(captor), expected, "alive pays the alive factor");
        helper.assertFalse(BrigService.isPrisoner(prisoner), "released from the shackles");
        helper.assertFalse(prisoner.isRemoved(), "a player stays in the world");
        helper.assertValueEqual(LawService.displayScore(prisoner), 0, "score wiped by the claim");
        helper.assertFalse(LawService.hasBounty(server, prisoner.getUUID()), "bounty claimed");
        helper.assertValueEqual(count(captor, LawContent.SHACKLES.get()), 1, "the shackles come back");
        clearBounties(helper, prisoner.getUUID());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void emptyHandWithoutPrisonerPasses(GameTestHelper helper) {
        NavyOfficer officer = officer(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertValueEqual(officer.interact(player, InteractionHand.MAIN_HAND), InteractionResult.PASS,
                "nothing to turn in");
        // A prisoner held by somebody else is not ours to deliver
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        Pirate pirate = capturedPirate(helper, other, 2, 2);
        helper.assertValueEqual(officer.interact(player, InteractionHand.MAIN_HAND), InteractionResult.PASS,
                "someone else's prisoner");
        helper.assertFalse(pirate.isRemoved(), "left alone");
        pirate.discard();
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_turn_in_officers")
    public static void officerTurnInsCanBeSwitchedOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.TURN_IN_OFFICERS, false);
        var server = helper.getLevel().getServer();
        NavyOfficer officer = officer(helper);
        Mob target = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 6, 1, 6);
        LawService.setScore(target, 60);
        Player hunter = helper.makeMockPlayer(GameType.SURVIVAL);
        hunter.setItemInHand(InteractionHand.MAIN_HAND, BountyProofItem.create(
                new BountyProof(target.getUUID(), "x", LawService.now(server), "hunter")));
        helper.assertValueEqual(officer.interact(hunter, InteractionHand.MAIN_HAND), InteractionResult.PASS, "officer ignores proofs");
        helper.assertFalse(hunter.getMainHandItem().isEmpty(), "proof kept");
        Pirate pirate = capturedPirate(helper, hunter, 2, 2);
        hunter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertValueEqual(officer.interact(hunter, InteractionHand.MAIN_HAND), InteractionResult.PASS, "and prisoners");
        helper.assertFalse(pirate.isRemoved(), "pirate kept");
        helper.assertValueEqual(Wallet.count(hunter), 0L, "nothing paid");
        pirate.discard();
        clearBounties(helper, target.getUUID());
        helper.succeed();
    }
}
