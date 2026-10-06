package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.bounty.Bounty;
import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.UUID;

/** Law system on real entities in a real server: attachment, decay over game time, navy bounties and claims. */
public final class LawGameTests {

    private LawGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(LawGameTests.class);
    }

    private static Mob criminal(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 1, 1);
    }

    private static void cleanup(GameTestHelper helper, UUID target) {
        BountyBoardData data = BountyBoardData.get(helper.getLevel().getServer());
        data.setBoard(data.board().clearTarget(target));
    }

    @ModGameTest
    public static void reportingCrimeRaisesScore(GameTestHelper helper) {
        Mob mob = criminal(helper);
        UUID villager = UUID.randomUUID();
        var first = LawService.reportCrime(mob, CrimeType.ATTACK_VILLAGER, villager);
        helper.assertTrue(first.counted(), "first attack should count");
        helper.assertValueEqual(LawService.record(mob).displayScore(), 5, "score after one attack");
        var repeat = LawService.reportCrime(mob, CrimeType.ATTACK_VILLAGER, villager);
        helper.assertValueEqual(repeat.outcome(), CriminalRecord.CrimeOutcome.REPEAT_IGNORED, "repeat outcome");
        LawService.reportCrime(mob, CrimeType.KILL_VILLAGER, villager);
        helper.assertValueEqual(LawService.record(mob).displayScore(), 25, "score after the kill");
        helper.assertValueEqual(LawService.wantedLevel(mob), WantedLevel.SUSPECT, "wanted level");
        helper.assertFalse(LawService.hasBounty(helper.getLevel().getServer(), mob.getUUID()), "no bounty below threshold");
        // Stored in the real attachment
        CriminalRecord stored = Services.ATTACHMENTS.get(mob, LawAttachments.CRIMINAL_RECORD);
        helper.assertValueEqual(stored.totalCrimes(), 2, "crimes stored on the entity");
        helper.succeed();
    }

    @ModGameTest
    public static void recordIsSavedWithTheEntity(GameTestHelper helper) {
        Mob mob = criminal(helper);
        LawService.reportCrime(mob, CrimeType.THEFT, (UUID) null);
        CompoundTag tag = mob.saveWithoutId(new CompoundTag());
        Mob copy = EntityType.PILLAGER.create(helper.getLevel());
        helper.assertTrue(copy != null, "pillager created");
        copy.load(tag);
        CriminalRecord loaded = Services.ATTACHMENTS.get(copy, LawAttachments.CRIMINAL_RECORD);
        helper.assertValueEqual(loaded.displayScore(), 5, "score after NBT round trip");
        helper.assertValueEqual(loaded.totalCrimes(), 1, "crime count after NBT round trip");
        copy.discard();
        helper.succeed();
    }

    @ModGameTest
    public static void crossingThresholdPlacesNavyBountyAndClaimPays(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Mob mob = criminal(helper);
        Player hunter = helper.makeMockPlayer(GameType.SURVIVAL);
        LawService.reportCrime(mob, CrimeType.KILL_NAVY, UUID.randomUUID());
        helper.assertFalse(LawService.hasBounty(server, mob.getUUID()), "30 points: no bounty yet");
        LawService.reportCrime(mob, CrimeType.KILL_NAVY, UUID.randomUUID());
        Bounty navy = LawService.board(server).navyBounty(mob.getUUID()).orElse(null);
        helper.assertTrue(navy != null, "60 points: navy bounty placed");
        helper.assertValueEqual(navy.amount(), 120, "navy bounty = 2 doubloons per point");
        helper.assertValueEqual(LawService.wantedLevel(mob), WantedLevel.WANTED, "wanted level");

        var self = LawService.claimBounty(server, mob.getUUID(), mob.getUUID(), BountyBoard.ClaimMethod.ALIVE);
        helper.assertValueEqual(self.outcome(), BountyBoard.ClaimOutcome.SELF_CLAIM, "self claim refused");

        var claim = LawService.claimBounty(mob, hunter, BountyBoard.ClaimMethod.ALIVE);
        helper.assertTrue(claim.success(), "claim succeeds");
        helper.assertValueEqual(claim.payout(), 180, "alive payout = 120 * 1.5");
        helper.assertFalse(LawService.hasBounty(server, mob.getUUID()), "bounty removed after claim");
        helper.assertValueEqual(LawService.record(mob).displayScore(), 0, "score cleared after claim");
        cleanup(helper, mob.getUUID());
        helper.succeed();
    }

    @ModGameTest
    public static void playerBountiesStackAndPayDead(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Mob mob = criminal(helper);
        Player payer = helper.makeMockPlayer(GameType.SURVIVAL);
        UUID hunter = UUID.randomUUID();
        helper.assertTrue(LawService.placeBounty(payer, mob, 20).placed(), "first bounty placed");
        helper.assertTrue(LawService.placeBounty(payer, mob, 30).placed(), "second bounty placed");
        helper.assertValueEqual(LawService.placeBounty(payer, mob, 1).outcome(), BountyBoard.PlaceOutcome.BELOW_MINIMUM, "minimum");
        helper.assertValueEqual(LawService.bountyTotal(server, mob.getUUID()), 50L, "bounties add up");
        var claim = LawService.claimBounty(server, mob.getUUID(), hunter, BountyBoard.ClaimMethod.DEAD_WITH_PROOF);
        helper.assertValueEqual(claim.payout(), 50, "dead payout = total");
        helper.assertValueEqual(LawService.bountyTotal(server, mob.getUUID()), 0L, "board cleared");
        cleanup(helper, mob.getUUID());
        helper.succeed();
    }

    @ModGameTest(batch = "pirates_n_ships_law_decay", timeoutTicks = 200)
    public static void decayLowersScoreAsGameTimePasses(GameTestHelper helper) {
        // 24000 points per day = 1 point per tick, no delay: the score must drop as real game ticks pass
        LawConfig.DECAY_PER_DAY.set(24000.0);
        LawConfig.DECAY_DELAY_SECONDS.set(0);
        Mob mob = criminal(helper);
        LawService.reportCrime(mob, CrimeType.KILL_NAVY, UUID.randomUUID());
        long start = LawService.now(helper.getLevel().getServer());
        helper.runAfterDelay(10, () -> {
            try {
                long elapsed = LawService.now(helper.getLevel().getServer()) - start;
                double score = LawService.score(mob);
                helper.assertTrue(elapsed > 0, "game time advanced");
                helper.assertTrue(Math.abs(score - Math.max(0, 30 - elapsed)) < 1e-6,
                        "score " + score + " should be 30 - " + elapsed);
                helper.succeed();
            } finally {
                LawConfig.DECAY_PER_DAY.set(LawConfig.DECAY_PER_DAY.defaultValue());
                LawConfig.DECAY_DELAY_SECONDS.set(LawConfig.DECAY_DELAY_SECONDS.defaultValue());
            }
        });
    }

    @ModGameTest(batch = "pirates_n_ships_law_disabled")
    public static void disabledScoreRecordsNothing(GameTestHelper helper) {
        LawConfig.CRIMINAL_SCORE_ENABLED.set(false);
        try {
            Mob mob = criminal(helper);
            var result = LawService.reportCrime(mob, CrimeType.PIRACY, UUID.randomUUID());
            helper.assertValueEqual(result.outcome(), CriminalRecord.CrimeOutcome.DISABLED, "outcome");
            helper.assertValueEqual(LawService.score(mob), 0.0, "score stays 0");
            helper.assertValueEqual(LawService.wantedLevel(mob), WantedLevel.CLEAN, "nobody is wanted");
            helper.assertFalse(LawService.hasBounty(helper.getLevel().getServer(), mob.getUUID()), "no navy bounty");
        } finally {
            LawConfig.CRIMINAL_SCORE_ENABLED.set(true);
        }
        helper.succeed();
    }
}
