package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.client.ClientWanted;
import com.richardsenger.piratesnships.law.client.LawClient;
import com.richardsenger.piratesnships.law.net.NoticeBoardBackend;
import com.richardsenger.piratesnships.law.net.NoticeBoardText;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.proof.ProofContent;
import com.richardsenger.piratesnships.law.proof.ProofDrops;
import com.richardsenger.piratesnships.law.sync.WantedSync;
import com.richardsenger.piratesnships.law.sync.WantedSyncPayload;
import com.richardsenger.piratesnships.law.turnin.OfficerTurnIns;
import com.richardsenger.piratesnships.law.world.CombatCrimeDetector;
import com.richardsenger.piratesnships.law.world.CrimeLog;
import com.richardsenger.piratesnships.law.world.FlagCrimes;
import com.richardsenger.piratesnships.law.world.FlagWorldGameTests;
import com.richardsenger.piratesnships.law.world.LawTags;
import com.richardsenger.piratesnships.law.world.LawWorldGameTests;
import com.richardsenger.piratesnships.law.world.PlacedBlocks;
import com.richardsenger.piratesnships.law.world.PlunderCrimeGameTests;
import com.richardsenger.piratesnships.law.world.PlunderCrimes;
import com.richardsenger.piratesnships.law.world.PlunderNotice;
import com.richardsenger.piratesnships.law.world.PlunderNoticeGameTests;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.world.TheftDetector;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/** The {@code law} module: criminal score, bounties and flag rules (docs/design.md §13, §4.7). */
public final class LawModule implements ModModule {

    @Override
    public String id() {
        return "law";
    }

    @Override
    public void registerConfig() {
        LawConfig.init();
    }

    @Override
    public void registerContent() {
        LawAttachments.init();
        PlacedBlocks.init();
        ProofContent.init();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(WantedSyncPayload.TYPE, WantedSyncPayload.CODEC, (p, player) -> ClientWanted.accept(p));
        NoticeBoardBackend.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(LawService::onServerTick);
        CommonEvents.SERVER_TICK_END.register(WantedSync::onServerTick);
        CommonEvents.SERVER_TICK_END.register(NoticeBoardBackend::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(player -> {
            LawService.onLogin(player);
            WantedSync.sendNow(player);
        });
        CommonEvents.PLAYER_LOGOUT.register(player -> {
            WantedSync.onLogout(player);
            TheftDetector.onLogout(player);
            NoticeBoardBackend.close(player);
        });
        CommonEvents.SERVER_STOPPED.register(server -> {
            WantedSync.onServerStopped(server);
            NoticeBoardBackend.onServerStopped(server);
            TheftDetector.onServerStopped();
            CrimeLog.clear();
        });
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> LawCommands.register(dispatcher));
        // World integration (docs/design.md §13.1, §13.2)
        CommonEvents.LIVING_INCOMING_DAMAGE.register(CombatCrimeDetector::onIncomingDamage);
        CommonEvents.LIVING_DEATH.register(CombatCrimeDetector::onDeath);
        CommonEvents.LIVING_DEATH.register(ProofDrops::onDeath);
        CommonEvents.CONTAINER_OPEN.register(TheftDetector::onContainerOpen);
        CommonEvents.CONTAINER_CLOSE.register(TheftDetector::onContainerClose);
        CommonEvents.BLOCK_PLACE.register(PlacedBlocks::onBlockPlace);
        CommonEvents.BLOCK_BREAK.register(PlacedBlocks::onBlockBreak);
        // Trade integration (docs/design.md §10.3): plunder a navy port noticed is a crime
        PlunderCrimes.register();
        // Flags in the world (FL2, docs/design.md §4.7): navy observation, false colours, crimes against ships
        FlagCrimes.register();
    }

    @Override
    public void initClient() {
        LawClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        String k = LawCommands.KEY;
        data.entityTypeTags(tags -> {
            tags.tag(LawTags.NAVY);
            tags.tag(LawTags.LAW_PROTECTED).add(EntityType.VILLAGER, EntityType.WANDERING_TRADER);
            tags.tag(LawTags.LAW_ENFORCERS).add(EntityType.IRON_GOLEM).addTag(LawTags.NAVY);
        });
        // The bounty proof has a hand-made item model (art/models/bounty_proof.bbmodel), so datagen writes none
        data.lang(lang -> {
            lang.item(ProofContent.BOUNTY_PROOF, "Bounty Proof")
                    .add(BountyProofItem.TOOLTIP_TARGET, "Proof of the death of %s")
                    .add(BountyProofItem.TOOLTIP_KILLER, "Slain by %s")
                    .add(BountyProofItem.TOOLTIP_BLANK, "Names no one")
                    .add(TheftDetector.THEFT_SEEN_KEY, "%s saw you stealing!")
                    .add(FlagCrimes.COVER_BLOWN_KEY, "The navy has seen through your colours")
                    .add(PlunderCrimes.REPORTED_KEY, "The harbor master reported you to the navy for offering stolen goods")
                    .add(PlunderNotice.SPOTTED_KEY, "Your cover is blown: the navy has spotted plunder in your hold")
                    .add(k + "last", "Last crime of %s: %s against %s, %s, +%s points (%s s ago)")
                    .add(k + "last.none", "No crime reported for %s since the server started")
                    .add(k + "hostile.yes", "The navy attacks %s on sight (%s)")
                    .add(k + "hostile.no", "The navy leaves %s alone (%s)");
        });
        data.lang(lang -> {
            lang.add(LawCommands.wantedKey(WantedLevel.CLEAN), "Clean")
                    .add(LawCommands.wantedKey(WantedLevel.SUSPECT), "Suspect")
                    .add(LawCommands.wantedKey(WantedLevel.WANTED), "Wanted")
                    .add(LawCommands.wantedKey(WantedLevel.NOTORIOUS), "Notorious")
                    .add(k + "not_living", "The target must be a living entity")
                    .add(k + "unknown_crime", "Unknown crime")
                    .add(k + "score.get", "%s has a criminal score of %s (%s, %s crimes on record)")
                    .add(k + "score.set", "Set the criminal score of %s to %s")
                    .add(k + "crime", "%s committed %s: %s, +%s points (score now %s)")
                    .add(k + "fine", "%s paid %s doubloons, %s points removed (%s, score now %s)")
                    .add(k + "bounty.list.empty", "There are no bounties")
                    .add(k + "bounty.list.header", "%s bounty targets:")
                    .add(k + "bounty.list.entry", "%s: %s doubloons (%s bounties)")
                    .add(k + "bounty.list.entry_navy", "%s: %s doubloons (%s bounties, wanted by the navy)")
                    .add(k + "bounty.place.success", "Placed a bounty of %s doubloons on %s (total now %s)")
                    .add(k + "bounty.place.failed", "Could not place the bounty: %s")
                    .add(k + "bounty.claim.success", "Claimed the bounty on %s: %s doubloons from %s bounties")
                    .add(k + "bounty.claim.failed", "Could not claim a bounty: %s")
                    .add(k + "bounty.clear", "Removed all bounties on %s");
        });
        data.lang(lang -> {
            String o = OfficerTurnIns.MSG;
            lang.add(o + "hostile", "The officer won't deal with a wanted criminal")
                    .add(o + "proof.paid", "The officer takes the proof of %s's death and pays you %s doubloons")
                    .add(o + "proof.blank", "This proof names no one")
                    .add(o + "proof.no_bounty", "There is no bounty left to claim on %s")
                    .add(o + "proof.self", "You can't claim the bounty on yourself")
                    .add(o + "prisoner.led_away", "%s is led away by the navy")
                    .add(o + "prisoner.paid", "The navy takes %s prisoner(s) off your hands and pays you %s doubloons")
                    .add(o + "prisoner.nothing", "The navy pays nothing for %s")
                    .add(o + "prisoner.released", "%s took you into custody. Your record is wiped and the shackles are off");
        });
        data.lang(lang -> {
            String m = NoticeBoardBackend.MSG;
            lang.add(NoticeBoardText.TITLE, "Notice Board")
                    .add(NoticeBoardText.COINS, "%s doubloons")
                    .add(NoticeBoardText.LOADING, "Reading the notices...")
                    .add(NoticeBoardText.EMPTY, "No bounties are posted")
                    .add(NoticeBoardText.OWN_BOUNTY, "There is a bounty of %s doubloons on your head!")
                    .add(NoticeBoardText.OWN_NONE, "There is no bounty on you")
                    .add(NoticeBoardText.COL_TARGET, "Wanted")
                    .add(NoticeBoardText.COL_AMOUNT, "Doubloons")
                    .add(NoticeBoardText.COL_PLACED_BY, "Placed by")
                    .add(NoticeBoardText.COL_SINCE, "Since")
                    .add(NoticeBoardText.NAVY, "The Navy")
                    .add(NoticeBoardText.TOTAL, " (%s in all)")
                    .add(NoticeBoardText.FORM, "Place a bounty:")
                    .add(NoticeBoardText.FORM_TARGET, "Name")
                    .add(NoticeBoardText.FORM_AMOUNT, "Amount")
                    .add(NoticeBoardText.FORM_PLACE, "Place")
                    .add(NoticeBoardText.FORM_MINIMUM, "(at least %s doubloons)")
                    .add(NoticeBoardText.FORM_DISABLED, "Players can't place bounties on this server")
                    .add(NoticeBoardText.CLOSED, "The notice board is out of reach")
                    .add(NoticeBoardText.age(NoticeBoardListing.AgeUnit.JUST_NOW), "just now")
                    .add(NoticeBoardText.age(NoticeBoardListing.AgeUnit.MINUTES), "%s min ago")
                    .add(NoticeBoardText.age(NoticeBoardListing.AgeUnit.HOURS), "%s h ago")
                    .add(NoticeBoardText.age(NoticeBoardListing.AgeUnit.DAYS), "%s d ago")
                    .add(m + "placed", "Posted a bounty of %s doubloons on %s (%s in all)")
                    .add(m + "refused.closed", "The notice board is out of reach")
                    .add(m + "refused.disabled", "Players can't place bounties on this server")
                    .add(m + "refused.below_minimum", "A bounty must be at least %s doubloons")
                    .add(m + "refused.unknown_target", "Nobody called %s is online or on the board")
                    .add(m + "refused.self", "You can't place a bounty on yourself")
                    .add(m + "refused.self_target", "You can't place a bounty on yourself")
                    .add(m + "refused.not_enough", "You need %s doubloons and carry %s");
        });
        data.lang(lang -> {
            for (CrimeType t : CrimeType.values()) {
                lang.add(t.nameKey(), switch (t) {
                    case ATTACK_NAVY -> "Attacking the navy";
                    case KILL_NAVY -> "Killing a navy sailor";
                    case ATTACK_VILLAGER -> "Attacking a villager";
                    case KILL_VILLAGER -> "Killing a villager";
                    case ATTACK_NEUTRAL_SHIP -> "Attacking a neutral ship";
                    case THEFT -> "Theft";
                    case PIRACY -> "Piracy";
                    case SEEN_UNDER_JOLLY_ROGER -> "Sailing under the Jolly Roger";
                    case CAUGHT_FALSE_COLORS -> "Flying false colors";
                    case ATTACK_STRUCK_COLORS -> "Attacking a ship that struck its colors";
                    case PRESS_GANG -> "Press-ganging a prisoner";
                    case DESERTION -> "Desertion";
                    case FENCE_PLUNDER -> "Selling noticed plunder (legacy)";
                    case SELLING_PLUNDER -> "Offering stolen goods";
                    case SUSPECTED_PIRACY -> "Suspected piracy";
                });
            }
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(LawGameTests.class, LawWorldGameTests.class, PlunderCrimeGameTests.class, PlunderNoticeGameTests.class,
                BountyTurnInGameTests.class, NoticeBoardGameTests.class, FlagWorldGameTests.class);
    }
}
