package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.BountyBoardData;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord.CrimeOutcome;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.proof.BountyProof;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.sync.WantedSync;
import com.richardsenger.piratesnships.law.sync.WantedSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.UUID;

/** The law system wired to real game events: combat crimes, theft, bounty proofs, wanted sync, navy hostility. */
public final class LawWorldGameTests {

    private LawWorldGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(LawWorldGameTests.class);
    }

    private static void hit(LivingEntity victim, Player player, float amount) {
        victim.hurt(player.damageSources().playerAttack(player), amount);
    }

    private static void clearBounties(GameTestHelper helper, UUID target) {
        BountyBoardData data = BountyBoardData.get(helper.getLevel().getServer());
        data.setBoard(data.board().clearTarget(target));
    }

    // --- Combat -------------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void hurtingVillagerCountsOnceAndKillingAddsKill(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 2, 1, 2);
        hit(villager, player, 1f);
        helper.assertValueEqual(LawService.record(player).displayScore(), 5, "score after the first hit");
        helper.assertValueEqual(CrimeLog.last(player.getUUID()).orElseThrow().type(), CrimeType.ATTACK_VILLAGER, "crime type");
        villager.invulnerableTime = 0;
        hit(villager, player, 1f);
        helper.assertValueEqual(CrimeLog.last(player.getUUID()).orElseThrow().outcome(), CrimeOutcome.REPEAT_IGNORED, "second hit");
        helper.assertValueEqual(LawService.record(player).displayScore(), 5, "repeat window holds");
        villager.invulnerableTime = 0;
        hit(villager, player, 1000f);
        helper.assertTrue(villager.isDeadOrDying(), "villager died");
        helper.assertValueEqual(LawService.record(player).displayScore(), 25, "attack + kill");
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 2, "two crimes counted");
        helper.assertValueEqual(CrimeLog.last(player.getUUID()).orElseThrow().type(), CrimeType.KILL_VILLAGER, "last crime");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void arrowFromPlayerIsTheShootersCrime(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob trader = helper.spawnWithNoFreeWill(EntityType.WANDERING_TRADER, 2, 1, 2);
        Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
        trader.hurt(helper.getLevel().damageSources().arrow(arrow, player), 1f);
        helper.assertValueEqual(LawService.record(player).displayScore(), 5, "shooting a trader is an attack");
        arrow.discard();
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void monstersGolemsAndPigsAreNoCrime(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 2, 1, 2);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 4);
        Mob golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, 6, 1, 6);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 6);
        Mob zombieVillager = helper.spawnWithNoFreeWill(EntityType.ZOMBIE_VILLAGER, 6, 1, 2);

        villager.hurt(zombie.damageSources().mobAttack(zombie), 1f);
        helper.assertValueEqual(LawService.record(zombie).totalCrimes(), 0, "zombies have no standing under the law");
        villager.invulnerableTime = 0;
        villager.hurt(golem.damageSources().mobAttack(golem), 1f);
        helper.assertValueEqual(LawService.record(golem).totalCrimes(), 0, "iron golems are law enforcers");
        hit(pig, player, 1f);
        hit(zombieVillager, player, 1f);
        villager.invulnerableTime = 0;
        villager.hurt(helper.getLevel().damageSources().generic(), 1f);
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 0, "pig and zombie villager are no crime");
        helper.assertFalse(CrimeLog.last(player.getUUID()).isPresent(), "nothing reported for the player");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_combat_off")
    public static void combatDetectionCanBeSwitchedOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.COMBAT_CRIMES, false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 2, 1, 2);
        hit(villager, player, 1000f);
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 0, "no crime with combat detection off");
        helper.succeed();
    }

    // --- Bounty proof -------------------------------------------------------------------------------------------

    private static ItemStack findProof(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (BountyProofItem.proofOf(s) != null) return s;
        }
        return ItemStack.EMPTY;
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void killingBountyTargetGivesProofThatClaims(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Player payer = helper.makeMockPlayer(GameType.SURVIVAL);
        Player hunter = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob target = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 2, 1, 2);
        helper.assertTrue(LawService.placeBounty(payer, target, 40).placed(), "bounty placed");
        hit(target, hunter, 1000f);
        helper.assertTrue(target.isDeadOrDying(), "target died");
        ItemStack proofStack = findProof(hunter);
        BountyProof proof = BountyProofItem.proofOf(proofStack);
        helper.assertTrue(proof != null, "hunter got a proof");
        helper.assertValueEqual(proof.target(), target.getUUID(), "proof names the target");
        helper.assertValueEqual(proof.targetName(), target.getName().getString(), "proof carries the name");

        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        var blank = LawService.claimWithProof(other, new ItemStack(Items.PAPER));
        helper.assertValueEqual(blank.outcome(), LawService.ProofClaimOutcome.NOT_A_PROOF, "paper is no proof");

        var claim = LawService.claimWithProof(hunter, proofStack);
        helper.assertTrue(claim.success(), "claim succeeds");
        helper.assertValueEqual(claim.payout(), 40, "dead payout = bounty");
        helper.assertTrue(proofStack.isEmpty(), "proof consumed");
        helper.assertFalse(LawService.hasBounty(server, target.getUUID()), "bounty cleared");

        // A second proof of the same kill finds nothing left
        var again = LawService.claimWithProof(hunter, BountyProofItem.create(proof));
        helper.assertValueEqual(again.outcome(), LawService.ProofClaimOutcome.NO_BOUNTY, "nothing left to claim");
        clearBounties(helper, target.getUUID());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void killWithoutBountyGivesNoProof(GameTestHelper helper) {
        Player hunter = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob target = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 2, 1, 2);
        hit(target, hunter, 1000f);
        helper.assertTrue(findProof(hunter).isEmpty(), "no bounty, no proof");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_proof_off")
    public static void proofDropsCanBeSwitchedOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.BOUNTY_PROOF_DROPS, false);
        Player payer = helper.makeMockPlayer(GameType.SURVIVAL);
        Player hunter = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob target = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 2, 1, 2);
        LawService.placeBounty(payer, target, 40);
        hit(target, hunter, 1000f);
        helper.assertTrue(findProof(hunter).isEmpty(), "no proof with drops off");
        clearBounties(helper, target.getUUID());
        helper.succeed();
    }

    // --- Theft --------------------------------------------------------------------------------------------------

    /** Chest with 10 diamonds at (1,1,1); a mock server player stands next to it. */
    private static ChestBlockEntity villageChest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
        return chest;
    }

    /**
     * A mock player next to the chest. Not a mock {@code ServerPlayer}: logging one in makes Sable send a payload to
     * the fake connection, which throws. So the menu is created directly and the container events are fired through
     * {@link CommonEvents} (the NeoForge forwarding line is covered by the playtest).
     */
    private static Player thief(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 3.5));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        return player;
    }

    /** Opens the chest, takes {@code take} diamonds and puts in {@code put} dirt, then closes. */
    private static void visit(Player player, ChestBlockEntity chest, int take, int put) {
        AbstractContainerMenu menu = chest.createMenu(++menuIds, player.getInventory(), player);
        if (menu == null) throw new GameTestAssertException("chest menu not created");
        CommonEvents.CONTAINER_OPEN.invoker().on(player, menu);
        if (take > 0) chest.removeItem(0, take);
        if (put > 0) chest.setItem(1, new ItemStack(Items.DIRT, put));
        menu.removed(player);
        CommonEvents.CONTAINER_CLOSE.invoker().on(player, menu);
    }

    private static int menuIds = 100;

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_theft_witnessed")
    public static void takingFromChestWithVillagerWatchingIsTheft(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.THEFT_REQUIRE_VILLAGE, false); // no village structure in a GameTest
        ChestBlockEntity chest = villageChest(helper);
        helper.spawnWithNoFreeWill(EntityType.VILLAGER, 4, 1, 5);
        Player player = thief(helper);
        visit(player, chest, 0, 5);
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 0, "putting items in is no theft");
        visit(player, chest, 4, 0);
        helper.assertValueEqual(LawService.record(player).displayScore(), 5, "taking diamonds is theft");
        helper.assertValueEqual(CrimeLog.last(player.getUUID()).orElseThrow().type(), CrimeType.THEFT, "crime type");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_theft_unwitnessed")
    public static void takingFromChestUnseenOrFromOwnChestIsNoTheft(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.THEFT_REQUIRE_VILLAGE, false);
        ChestBlockEntity chest = villageChest(helper);
        Player player = thief(helper);
        visit(player, chest, 4, 0);
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 0, "nobody saw it");

        helper.spawnWithNoFreeWill(EntityType.VILLAGER, 4, 1, 5);
        PlacedBlocks.markPlayerPlaced(helper.getLevel(), chest.getBlockPos());
        visit(player, chest, 4, 0);
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 0, "a chest a player placed is nobody's loot");
        PlacedBlocks.forget(helper.getLevel(), chest.getBlockPos());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_theft_off")
    public static void theftDetectionCanBeSwitchedOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, LawConfig.THEFT_REQUIRE_VILLAGE, false);
        ConfigOverrides.during(helper, LawConfig.THEFT_DETECTION, false);
        ChestBlockEntity chest = villageChest(helper);
        helper.spawnWithNoFreeWill(EntityType.VILLAGER, 4, 1, 5);
        Player player = thief(helper);
        visit(player, chest, 4, 0);
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 0, "no theft with detection off");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_law_theft_village")
    public static void chestOutsideVillageIsNoTheftByDefault(GameTestHelper helper) {
        // Default config: only containers inside a village structure count; the test area is no village.
        ChestBlockEntity chest = villageChest(helper);
        helper.spawnWithNoFreeWill(EntityType.VILLAGER, 4, 1, 5);
        Player player = thief(helper);
        visit(player, chest, 4, 0);
        helper.assertValueEqual(LawService.record(player).totalCrimes(), 0, "not inside a village");
        helper.succeed();
    }

    // --- Sync and hostility -------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void wantedSnapshotFollowsScore(GameTestHelper helper) {
        // Sending needs a real connection (playtest); the snapshot is what gets sent and compared
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertValueEqual(WantedSync.snapshot(player), new WantedSyncPayload(WantedLevel.CLEAN, 0), "clean");
        LawService.setScore(player, 60);
        helper.assertValueEqual(WantedSync.snapshot(player), new WantedSyncPayload(WantedLevel.WANTED, 60), "wanted");
        LawService.setScore(player, 12.5);
        helper.assertValueEqual(WantedSync.snapshot(player), new WantedSyncPayload(WantedLevel.SUSPECT, 13), "rounded up");
        clearBounties(helper, player.getUUID());
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void navyHostilityFollowsWantedLevel(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        Mob npc = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 2, 1, 2);
        helper.assertFalse(LawService.navyShouldAttack(null, player), "clean player is left alone");
        LawService.setScore(player, 60);
        LawService.setScore(creative, 60);
        LawService.setScore(npc, 60);
        helper.assertTrue(LawService.navyShouldAttack(null, player), "wanted player is attacked");
        helper.assertTrue(LawService.navyShouldAttack(null, npc), "wanted NPC is attacked");
        helper.assertFalse(LawService.navyShouldAttack(npc, npc), "never attacks itself");
        helper.assertFalse(LawService.navyShouldAttack(null, creative), "creative players are exempt");
        for (LivingEntity e : new LivingEntity[]{player, creative, npc}) clearBounties(helper, e.getUUID());
        helper.succeed();
    }
}
