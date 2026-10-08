package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/**
 * Named pirate captains (work package BOS1, docs/design.md §15, plan {@code docs/plans/rpg-layer.md}): every pirate
 * island's captain in his hut with a seeded name ({@link CaptainNames}, {@link IslandCaptains}, {@link CaptainRegistry}),
 * the navy's standing bounty on him, the duel ({@link DuelChallenge}), his drops, the captain's tier at the navy
 * turn-in, and his successor after {@code mobs.captain.respawn_days}. The entity type and its config section live with
 * the other mobs ({@code MobContent.PIRATE_CAPTAIN}, {@code mobs.captain}, {@link CaptainConfig}).
 */
public final class CaptainModule implements ModModule {

    @Override
    public String id() {
        return "mob.captain";
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> CaptainCommands.register(dispatcher));
        CommonEvents.LIVING_DEATH.register(DuelChallenge::onDeath);
        CommonEvents.SERVER_TICK_END.register(IslandCaptains::onServerTick);
        CommonEvents.SERVER_STOPPED.register(server -> DuelChallenge.clear());
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.mob.captain.client.DuelClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(DuelChallenge.KEY_ACCEPTED, "%s accepts your challenge! His crew stand back: this is between the two of you.")
                .add(DuelChallenge.MSG + "disabled", "%s won't duel anyone")
                .add(DuelChallenge.MSG + "not_sneaking", "Sneak to challenge %s")
                .add(DuelChallenge.MSG + "no_sword", "Draw a sword to challenge %s")
                .add(DuelChallenge.MSG + "exempt", "%s won't cross swords with a spirit")
                .add(DuelChallenge.MSG + "busy", "%s is already dueling someone else")
                .add(DuelChallenge.MSG + "grudge", "You struck %s first. No honour, no duel")
                .add(DuelChallenge.KEY_ENDED + "captain_died", "%s is beaten. The duel is yours!")
                .add(DuelChallenge.KEY_ENDED + "challenger_died", "You lost the duel with %s")
                .add(DuelChallenge.KEY_ENDED + "challenger_left", "You fled the duel with %s. His crew remember you")
                .add(DuelChallenge.KEY_ENDED + "timed_out", "The duel with %s is over. His crew stand back no longer")
                .add(CaptainCommands.KEY_SPAWNED, "Spawned captain %s of %s (bounty %s doubloons)")
                .add(CaptainCommands.KEY_REFUSED, "No captain spawned for %s: captains are disabled (mobs.captain.enabled) or its captain still lives")
                .add(CaptainCommands.KEY_NONE, "No pirate captains yet")
                .add(CaptainCommands.KEY_LINE_ALIVE, "%s of %s at %s, alive, bounty %s doubloons")
                .add(CaptainCommands.KEY_LINE_LOST, "%s of %s at %s, lost on day %s")
                .add(CaptainCommands.KEY_LINE_AT_SEA, "%s of %s at sea (voyage %s), bounty %s doubloons"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CaptainGameTests.class);
    }
}
