package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Careers in a real server (CAR1, docs/design.md §15): enlisting, promotions from deeds and quests, desertion and its
 * crime, infamy and the exclusion of the ladders, the letter of marque and its prize money, persistence through death
 * and NBT, the officer's screen gesture and actions, the commands, and the {@code careers.enabled} toggle. Tests that
 * change config run in batches of their own ({@code pirates_n_ships_config_career_*}).
 */
public final class CareerGameTests {

    private static final String BATCH = "pirates_n_ships_career_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_career_";

    private CareerGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CareerGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** A real server player with a mock connection, not added to the level or the player list. */
    private static ServerPlayer serverPlayer(GameTestHelper helper, UUID id, String name) {
        var profile = new com.mojang.authlib.GameProfile(id, name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        return p;
    }

    private static ServerPlayer serverPlayer(GameTestHelper helper) {
        return serverPlayer(helper, UUID.randomUUID(), "career_test");
    }

    /** A clean player with just enough navy reputation to enlist, enlisted. */
    private static ServerPlayer enlisted(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        Reputation.set(p, Faction.NAVY, CareerConfig.thresholds().step(NavyRank.MIDSHIPMAN).minNavyRep(), "test");
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.OK, "enlist");
        return p;
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static boolean hasCrime(ServerPlayer p, CrimeType type) {
        return LawService.record(p).recent().stream().anyMatch(o -> o.type() == type);
    }

    private static DeedContext victimKind(String kind) {
        return new DeedContext(Optional.of(Constants.id(kind)), Optional.empty(), Optional.empty(), 0L);
    }

    // ------------------------------------------------------------------ enlisting and promotion

    /** A clean player with the midshipman's navy reputation enlists as a midshipman; below it the navy refuses. */
    @ModGameTest(batch = BATCH + "enlist")
    public static void cleanPlayerEnlistsAsMidshipman(GameTestHelper h) {
        ServerPlayer low = serverPlayer(h);
        h.assertValueEqual(Careers.enlist(low), CareerRules.EnlistVerdict.LOW_STANDING, "enlist without navy reputation");
        h.assertValueEqual(Careers.navyRank(low), NavyRank.NONE, "rank after a refused enlistment");

        ServerPlayer p = enlisted(h);
        CareerRecord r = Careers.record(p);
        h.assertValueEqual(r.navy(), NavyRank.MIDSHIPMAN, "rank after enlisting");
        h.assertTrue(r.enlisted(), "not enlisted");
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.ALREADY_ENLISTED, "enlisting twice");

        ServerPlayer friend = serverPlayer(h);
        Reputation.set(friend, Faction.NAVY, 50, "test");
        Reputation.set(friend, Faction.PIRATES, CareerConfig.MAX_PIRATE_REP_TO_ENLIST.get() + 1, "test");
        h.assertValueEqual(Careers.enlist(friend), CareerRules.EnlistVerdict.PIRATE_FRIEND, "a friend of the pirates");

        h.assertTrue(Careers.resign(p), "resign");
        h.assertValueEqual(Careers.navyRank(p), NavyRank.NONE, "rank after resigning");
        h.assertFalse(hasCrime(p, CrimeType.DESERTION), "resigning is desertion");
        h.succeed();
    }

    /** Pirate kills, the navy reputation they bring and a navy quest promote a midshipman to lieutenant. */
    @ModGameTest(batch = BATCH + "promotion")
    public static void killsAndAQuestPromoteToLieutenant(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        CareerThresholds.NavyStep lt = CareerConfig.thresholds().step(NavyRank.LIEUTENANT);
        for (int i = 0; i < lt.piratesDefeated() - 1; i++) Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        Reputation.set(p, Faction.NAVY, lt.minNavyRep(), "test");
        for (int i = 0; i < lt.quests(); i++) Careers.recordQuest(p, Faction.NAVY);
        h.assertValueEqual(Careers.navyRank(p), NavyRank.MIDSHIPMAN, "promoted one kill short");
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        h.assertValueEqual(Careers.navyRank(p), NavyRank.LIEUTENANT, "rank after the last kill");
        h.assertValueEqual(Careers.record(p).count(CareerCounter.PIRATES_KILLED), lt.piratesDefeated(), "pirates killed");
        h.assertValueEqual(Careers.record(p).count(CareerCounter.NAVY_QUESTS), lt.quests(), "navy quests");
        // Reputation falling later does not demote
        Reputation.set(p, Faction.NAVY, 0, "test");
        Deeds.record(p, Deed.TURN_IN_PIRATE, DeedContext.NONE);
        h.assertValueEqual(Careers.navyRank(p), NavyRank.LIEUTENANT, "demoted by reputation");
        h.succeed();
    }

    /** A pirate captain counts captain_weight kills; a navy officer is a capture for the infamy ladder; fences count coins. */
    @ModGameTest(batch = BATCH + "counters")
    public static void deedsFeedTheCounters(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        Deeds.record(p, Deed.KILL_PIRATE, victimKind("pirate_captain"));
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.victim(UUID.randomUUID()));
        Deeds.record(p, Deed.KILL_NAVY, victimKind("navy_officer"));
        Deeds.record(p, Deed.FENCE_PLUNDER, DeedContext.amount(42));
        Deeds.record(p, Deed.PLUNDER_MERCHANT, DeedContext.NONE);
        Deeds.record(p, Deed.TURN_IN_PIRATE, DeedContext.NONE);
        CareerRecord r = Careers.record(p);
        h.assertValueEqual(r.count(CareerCounter.PIRATES_KILLED), CareerConfig.CAPTAIN_WEIGHT.get() + 1L, "weighted pirate kills");
        h.assertValueEqual(r.count(CareerCounter.CAPTAINS_KILLED), 1L, "captains killed");
        h.assertValueEqual(r.count(CareerCounter.NAVY_KILLED), 1L, "navy killed");
        h.assertValueEqual(r.count(CareerCounter.OFFICERS_KILLED), 1L, "officers killed");
        h.assertValueEqual(r.count(CareerCounter.PLUNDER_COINS), 42L, "plunder coins");
        h.assertValueEqual(r.count(CareerCounter.MERCHANTS_PLUNDERED), 1L, "merchants plundered");
        h.assertValueEqual(r.count(CareerCounter.PIRATES_TURNED_IN), 1L, "pirates turned in");
        h.assertValueEqual(CareerRules.captures(r), 2L, "captures");
        h.succeed();
    }

    // ------------------------------------------------------------------ desertion

    /** Attacking the navy in service is desertion: the rank is gone and the desertion crime is on the record. */
    @ModGameTest(batch = BATCH + "desertion")
    public static void attackingTheNavyInServiceIsDesertion(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        h.assertFalse(hasCrime(p, CrimeType.DESERTION), "a crime before deserting");
        Deeds.record(p, Deed.ATTACK_NAVY, DeedContext.NONE);
        CareerRecord r = Careers.record(p);
        h.assertValueEqual(r.navy(), NavyRank.NONE, "rank after desertion");
        h.assertFalse(r.enlisted(), "still enlisted");
        h.assertTrue(hasCrime(p, CrimeType.DESERTION), "no desertion crime on the record");
        // Outside the service the same deed is no desertion
        ServerPlayer civilian = serverPlayer(h);
        Deeds.record(civilian, Deed.ATTACK_NAVY, DeedContext.NONE);
        h.assertFalse(hasCrime(civilian, CrimeType.DESERTION), "a civilian deserted");
        h.succeed();
    }

    /** With {@code desertion_is_crime} off, desertion only costs the rank. */
    @ModGameTest(batch = CONFIG_BATCH + "desertion_no_crime")
    public static void desertionWithoutCrime(GameTestHelper h) {
        ConfigOverrides.during(h, CareerConfig.DESERTION_IS_CRIME, false);
        ServerPlayer p = enlisted(h);
        Deeds.record(p, Deed.PLUNDER_MERCHANT, DeedContext.NONE);
        h.assertValueEqual(Careers.navyRank(p), NavyRank.NONE, "rank after desertion");
        h.assertFalse(hasCrime(p, CrimeType.DESERTION), "desertion crime with desertion_is_crime off");
        h.succeed();
    }

    // ------------------------------------------------------------------ infamy

    /** Plunder fenced and pirate reputation make a Buccaneer; never while in service. */
    @ModGameTest(batch = BATCH + "infamy")
    public static void fencedPlunderMakesABuccaneerButNotInService(GameTestHelper h) {
        CareerThresholds.InfamyStep b = CareerConfig.thresholds().step(InfamyRank.BUCCANEER);
        ServerPlayer p = serverPlayer(h);
        Reputation.set(p, Faction.PIRATES, b.minPirateRep(), "test");
        Deeds.record(p, Deed.FENCE_PLUNDER, DeedContext.amount(b.plunderCoins()));
        h.assertValueEqual(Careers.infamy(p), InfamyRank.BUCCANEER, "infamy after fencing");

        ServerPlayer officer = enlisted(h);
        Reputation.set(officer, Faction.PIRATES, b.minPirateRep(), "test");
        Deeds.record(officer, Deed.FENCE_PLUNDER, DeedContext.amount(b.plunderCoins()));
        h.assertValueEqual(Careers.infamy(officer), InfamyRank.DECKHAND, "infamy gained in service");
        h.assertTrue(Careers.record(officer).enlisted(), "fencing in service ended the service");

        // A Buccaneer cannot enlist
        Reputation.set(p, Faction.PIRATES, 0, "test");
        Reputation.set(p, Faction.NAVY, 50, "test");
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.INFAMOUS, "a Buccaneer enlisted");
        h.succeed();
    }

    /** Turning pirate (infamy set to Buccaneer) while in service is desertion. */
    @ModGameTest(batch = BATCH + "turn_pirate")
    public static void turningPirateInServiceIsDesertion(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        Careers.setInfamy(p, InfamyRank.BUCCANEER);
        CareerRecord r = Careers.record(p);
        h.assertValueEqual(r.infamy(), InfamyRank.BUCCANEER, "infamy");
        h.assertValueEqual(r.navy(), NavyRank.NONE, "navy rank of a pirate");
        h.assertTrue(hasCrime(p, CrimeType.DESERTION), "no desertion crime");
        h.succeed();
    }

    // ------------------------------------------------------------------ letter of marque

    /** The letter costs the fee, pirate kills under it earn prize money paid at the officer, plundering voids it. */
    @ModGameTest(batch = BATCH + "letter")
    public static void letterOfMarqueEarnsPrizeAndIsVoidedByPlunder(GameTestHelper h) {
        CareerThresholds.LetterTerms terms = CareerConfig.thresholds().letter();
        ServerPlayer p = serverPlayer(h);
        Reputation.set(p, Faction.NAVY, terms.minNavyRep(), "test");
        h.assertValueEqual(Careers.grantLetter(p), terms.fee() > 0 ? CareerRules.LetterVerdict.TOO_POOR : CareerRules.LetterVerdict.OK, "without coins");
        Wallet.give(p, terms.fee());
        h.assertValueEqual(Careers.grantLetter(p), CareerRules.LetterVerdict.OK, "grant");
        h.assertValueEqual(Wallet.count(p), 0L, "fee taken");
        h.assertValueEqual(Careers.record(p).letter(), LetterState.ACTIVE, "letter state");

        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        Deeds.record(p, Deed.KILL_PIRATE, victimKind("pirate_captain"));
        long prize = terms.prizeDeckhand() + terms.prizeCaptain();
        h.assertValueEqual(Careers.record(p).prizeMoney(), prize, "prize money");
        h.assertValueEqual(Careers.collectPrize(p), prize, "collected");
        h.assertValueEqual(Wallet.count(p), prize, "coins after collecting");
        h.assertValueEqual(Careers.record(p).prizeMoney(), 0L, "prize after collecting");

        Deeds.record(p, Deed.PLUNDER_MERCHANT, DeedContext.NONE);
        h.assertValueEqual(Careers.record(p).letter(), LetterState.VOIDED, "letter after plunder");
        h.assertFalse(hasCrime(p, CrimeType.DESERTION), "voiding a letter is desertion");
        Reputation.set(p, Faction.NAVY, 100, "test");
        Wallet.give(p, terms.fee());
        h.assertValueEqual(Careers.grantLetter(p), terms.voidTicks() > 0 ? CareerRules.LetterVerdict.BLOCKED : CareerRules.LetterVerdict.OK,
                "a new letter right after a void");
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        h.assertValueEqual(Careers.record(p).prizeMoney(), 0L, "prize under a void letter");

        // Navy officers get no letter
        ServerPlayer officer = enlisted(h);
        Reputation.set(officer, Faction.NAVY, 100, "test");
        h.assertValueEqual(Careers.grantLetter(officer), CareerRules.LetterVerdict.ENLISTED, "letter in service");
        h.succeed();
    }

    // ------------------------------------------------------------------ persistence

    /** The career survives death (the respawn clone) and an NBT round trip (relog). */
    @ModGameTest(batch = BATCH + "persistence")
    public static void careerSurvivesDeathAndRelog(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        ServerPlayer p = serverPlayer(h, id, "career_persist");
        Reputation.set(p, Faction.NAVY, 10, "test");
        Careers.enlist(p);
        Careers.setNavy(p, NavyRank.CAPTAIN);
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        CareerRecord before = Careers.record(p);

        ServerPlayer respawned = serverPlayer(h, id, "career_persist");
        respawned.restoreFrom(p, false);
        h.assertValueEqual(Careers.record(respawned), before, "career after death");

        CompoundTag tag = respawned.saveWithoutId(new CompoundTag());
        ServerPlayer loaded = serverPlayer(h, id, "career_persist");
        loaded.load(tag);
        h.assertValueEqual(Careers.record(loaded), before, "career after NBT round trip");
        h.succeed();
    }

    // ------------------------------------------------------------------ the officer

    /**
     * An empty-handed click on a friendly officer opens the career screen; a hostile one opens nothing; sneaking or a
     * full hand is left to the officer's turn-ins. The screen's actions re-check the officer's reach.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "officer")
    public static void officerOpensTheScreenUnlessHostile(GameTestHelper h) {
        floor(h, 9);
        NavyOfficer officer = h.spawnWithNoFreeWill(MobContent.NAVY_OFFICER.get(), 2, 1, 2);
        ServerPlayer p = serverPlayer(h);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 1, 2))));
        Reputation.set(p, Faction.NAVY, CareerConfig.thresholds().step(NavyRank.MIDSHIPMAN).minNavyRep(), "test");
        List<CustomPacketPayload> sent = CareerBackend.record(p.getUUID());
        try {
            InteractionResult r = CommonEvents.ENTITY_INTERACT.invoker().onInteract(p, officer, InteractionHand.MAIN_HAND);
            h.assertTrue(r.consumesAction(), "the click was not taken: " + r);
            h.assertValueEqual(CareerBackend.opened(p.getUUID()), Optional.of(officer.getId()), "screen opened at");
            h.assertTrue(sent.size() == 1 && sent.getFirst() instanceof CareerPayloads.View v && v.open()
                    && v.enlist() == CareerRules.EnlistVerdict.OK, "open view " + sent);

            // The enlist button
            CareerPayloads.Result enlisted = CareerBackend.handle(p, new CareerPayloads.Action(officer.getId(), CareerPayloads.Kind.ENLIST));
            h.assertTrue(enlisted.done(), "enlist refused: " + enlisted);
            h.assertValueEqual(Careers.navyRank(p), NavyRank.MIDSHIPMAN, "rank after the enlist button");
            h.assertTrue(sent.getLast() instanceof CareerPayloads.View v && !v.open() && v.status().enlisted(), "refresh " + sent.getLast());

            // Out of reach: the officer is not there for the screen
            p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 1, 2))).add(CareerConfig.OFFICER_REACH.get() + 2, 0, 0));
            CareerPayloads.Result far = CareerBackend.handle(p, new CareerPayloads.Action(officer.getId(), CareerPayloads.Kind.RESIGN));
            h.assertValueEqual(far.key(), CareerText.OFFICER_GONE, "far result");
            h.assertTrue(Careers.record(p).enlisted(), "resigned out of reach");
            p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 1, 2))));

            // Sneaking or holding something: the turn-ins' gestures
            p.setShiftKeyDown(true);
            h.assertFalse(CareerInteractions.isCareerGesture(p, officer, InteractionHand.MAIN_HAND), "sneaking is the career gesture");
            p.setShiftKeyDown(false);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            h.assertFalse(CareerInteractions.isCareerGesture(p, officer, InteractionHand.MAIN_HAND), "a full hand is the career gesture");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            // A hostile officer opens nothing
            ServerPlayer hated = serverPlayer(h);
            hated.setPos(p.position());
            Reputation.set(hated, Faction.NAVY, -100, "test");
            List<CustomPacketPayload> hatedSent = CareerBackend.record(hated.getUUID());
            h.assertTrue(CareerBackend.hostile(officer, hated), "the officer is not hostile to a hated player");
            CommonEvents.ENTITY_INTERACT.invoker().onInteract(hated, officer, InteractionHand.MAIN_HAND);
            h.assertTrue(CareerBackend.opened(hated.getUUID()).isEmpty(), "a hostile officer opened the screen");
            h.assertTrue(hatedSent.isEmpty(), "a hostile officer sent " + hatedSent);
            CareerPayloads.Result refused = CareerBackend.handle(hated, new CareerPayloads.Action(officer.getId(), CareerPayloads.Kind.ENLIST));
            h.assertValueEqual(refused.key(), CareerText.HOSTILE, "action at a hostile officer");
            CareerBackend.stopRecording(hated.getUUID());
        } finally {
            CareerBackend.stopRecording(p.getUUID());
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ commands

    private static final class Recorder implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override public void sendSystemMessage(Component component) { lines.add(component.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    private static Recorder run(GameTestHelper h, ServerPlayer p, int permission, String command) {
        Recorder out = new Recorder();
        var source = p.createCommandSourceStack().withSource(out).withPermission(permission);
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        return out;
    }

    /** {@code /pirates career} shows; operators set ranks and letters; {@code /pirates rep} carries the career line. */
    @ModGameTest(batch = BATCH + "commands")
    public static void commandsShowAndSetCareers(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        run(h, p, 0, "pirates career set @s navy captain");
        h.assertValueEqual(Careers.navyRank(p), NavyRank.NONE, "a non-operator set the rank");
        run(h, p, 2, "pirates career set @s navy captain");
        h.assertValueEqual(Careers.navyRank(p), NavyRank.CAPTAIN, "rank after career set navy");
        run(h, p, 2, "pirates career set @s navy none");
        run(h, p, 2, "pirates career set @s infamy dread_captain");
        h.assertValueEqual(Careers.infamy(p), InfamyRank.DREAD_CAPTAIN, "infamy after career set infamy");
        run(h, p, 2, "pirates career letter @s grant");
        h.assertValueEqual(Careers.record(p).letter(), LetterState.ACTIVE, "letter after grant");
        run(h, p, 2, "pirates career letter @s void");
        h.assertValueEqual(Careers.record(p).letter(), LetterState.VOIDED, "letter after void");

        Recorder own = run(h, p, 0, "pirates career");
        h.assertValueEqual(own.lines.size(), 1, "career lines " + own.lines);
        Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        Recorder other = run(h, p, 2, "pirates career @s");
        h.assertTrue(other.lines.size() >= 2, "career <player> shows no counters " + other.lines);
        Recorder rep = run(h, p, 0, "pirates rep");
        h.assertValueEqual(rep.lines.size(), 1, "rep lines " + rep.lines);
        h.succeed();
    }

    // ------------------------------------------------------------------ toggle

    /** {@code careers.enabled = false}: deeds count nothing, nobody is promoted, deserts, enlists or gets a letter. */
    @ModGameTest(batch = CONFIG_BATCH + "disabled")
    public static void disabledCareersChangeNothing(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        ConfigOverrides.during(h, CareerConfig.ENABLED, false);
        for (int i = 0; i < 100; i++) Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        Careers.recordQuest(p, Faction.NAVY);
        Reputation.set(p, Faction.NAVY, 100, "test");
        h.assertFalse(Careers.promoteIfEligible(p), "promoted while disabled");
        CareerRecord r = Careers.record(p);
        h.assertValueEqual(r.navy(), NavyRank.MIDSHIPMAN, "rank while disabled");
        h.assertValueEqual(r.count(CareerCounter.PIRATES_KILLED), 0L, "counted while disabled");
        Deeds.record(p, Deed.KILL_NAVY, DeedContext.NONE);
        h.assertValueEqual(Careers.navyRank(p), NavyRank.MIDSHIPMAN, "deserted while disabled");

        ServerPlayer other = serverPlayer(h);
        Reputation.set(other, Faction.NAVY, 100, "test");
        Wallet.give(other, 10_000);
        h.assertValueEqual(Careers.enlist(other), CareerRules.EnlistVerdict.DISABLED, "enlist while disabled");
        h.assertValueEqual(Careers.grantLetter(other), CareerRules.LetterVerdict.DISABLED, "letter while disabled");
        h.succeed();
    }
}
