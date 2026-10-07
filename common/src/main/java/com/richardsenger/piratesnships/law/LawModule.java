package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.client.ClientWanted;
import com.richardsenger.piratesnships.law.client.LawClient;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.proof.ProofContent;
import com.richardsenger.piratesnships.law.proof.ProofDrops;
import com.richardsenger.piratesnships.law.sync.WantedSync;
import com.richardsenger.piratesnships.law.sync.WantedSyncPayload;
import com.richardsenger.piratesnships.law.world.CombatCrimeDetector;
import com.richardsenger.piratesnships.law.world.CrimeLog;
import com.richardsenger.piratesnships.law.world.LawTags;
import com.richardsenger.piratesnships.law.world.LawWorldGameTests;
import com.richardsenger.piratesnships.law.world.PlacedBlocks;
import com.richardsenger.piratesnships.law.world.PlunderCrimeGameTests;
import com.richardsenger.piratesnships.law.world.PlunderCrimes;
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
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(LawService::onServerTick);
        CommonEvents.SERVER_TICK_END.register(WantedSync::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(player -> {
            LawService.onLogin(player);
            WantedSync.sendNow(player);
        });
        CommonEvents.PLAYER_LOGOUT.register(player -> {
            WantedSync.onLogout(player);
            TheftDetector.onLogout(player);
        });
        CommonEvents.SERVER_STOPPED.register(server -> {
            WantedSync.onServerStopped(server);
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
                    case FENCE_PLUNDER -> "Selling plunder";
                });
            }
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(LawGameTests.class, LawWorldGameTests.class, PlunderCrimeGameTests.class);
    }
}
