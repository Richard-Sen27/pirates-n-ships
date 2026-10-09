package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.mob.captain.PirateCaptain;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageEndings;
import com.richardsenger.piratesnships.worldsim.navy.Hunting;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageCommands;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageScheduler;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * The {@code worldsim.captain} module (BOS2, design.md §10.4, §15, plan {@code docs/plans/rpg-layer.md} wave 3): the
 * pirate captain's hunted voyage. Captains put to sea from their islands ({@link CaptainVoyages}), aboard their own
 * ship as its lead fighter through every materialisation, hunt players who carry a letter of marque or bounty proofs
 * ({@link CaptainHunt}), die at sea as on land, and come home to their posts; {@code /pirates mob captain voyage}.
 * Config {@code world_simulation.captain.*}. Registered after {@code worldsim.navy}, so on a voyage check the
 * materialiser has run before the captain goes aboard and hunts.
 */
public final class CaptainVoyageModule implements ModModule {

    @Override
    public String id() {
        return "worldsim.captain";
    }

    @Override
    public void registerConfig() {
        CaptainVoyageConfig.init();
    }

    @Override
    public void registerEvents() {
        VoyageScheduler.register(VoyageKind.CAPTAIN, new CaptainVoyagePlanner());
        PirateCaptain.setSeaHook(CaptainVoyages.HOOK);
        CommonEvents.SERVER_TICK_END.register(CaptainVoyages::onServerTick);
        CommonEvents.SERVER_TICK_END.register(CaptainVoyageModule::onVoyageCheck);
        CommonEvents.SERVER_STOPPED.register(server -> {
            CaptainVoyages.onServerStopped();
            CaptainHunt.clear();
        });
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> CaptainVoyageCommands.register(dispatcher));
        Voyages.onEnd(CaptainVoyages::onVoyageEnded);
        VoyageEndings.onEnding(CaptainVoyages::onEnding);
        VoyageCommands.label(CaptainVoyages::label);
    }

    /** With every voyage check, after the materialiser: each materialised captain's voyage gets him aboard and hunts. */
    static void onVoyageCheck(MinecraftServer server) {
        if (server.getTickCount() % VoyageConfig.TICK_INTERVAL.get() != 0) return;
        for (Voyage v : Voyages.active(server)) {
            if (v.kind() != VoyageKind.CAPTAIN || v.state() != Voyage.State.MATERIALISED || Hunting.isTest(v)) continue;
            CaptainVoyages.board(server, v.id());
            CaptainHunt.updateMaterialised(server, v.id());
        }
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(CaptainVoyages::lang);
        data.lang(CaptainHunt::lang);
        data.lang(CaptainVoyageCommands::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CaptainVoyageGameTests.class);
    }
}
