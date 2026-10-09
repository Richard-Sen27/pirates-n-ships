package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.combat.cannon.CannonShipHits;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;

import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * The {@code worldsim.materialize} module (WS3b, design.md §10.4): voyages near players become real ships and records
 * again ({@link Materializer}), with sunk, captured and plundered endings ({@link VoyageEndings}). Config
 * {@code world_simulation.materialize.*}.
 */
public final class MaterializeModule implements ModModule {

    @Override
    public String id() {
        return "worldsim.materialize";
    }

    @Override
    public void registerConfig() {
        MaterializeConfig.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(Materializer::onServerTick);
        CommonEvents.SERVER_STOPPED.register(server -> Materializer.onServerStopped());
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> MaterializeCommands.register(dispatcher));
        HelmCourses.onEvent(Materializer::onCourseEvent);
        Voyages.onEnd(Materializer::onVoyageEnded);
        CannonShipHits.register(VoyageEndings::onShipHit);
        ShipSplits.onSplit(Materializer::onSplit);
        ShipSplits.carryOnIdentityMove(VoyageLink.USER_DATA_KEY, tag -> tag);
        SableShips.onShipRemoved((level, id, destroyed) -> {
            if (destroyed && VoyageShips.voyageOf(id).isEmpty()) VoyageEndings.forgetShip(id);
        });
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(MaterializeCommands::lang);
        data.lang(VoyageEndings::lang);
        data.blockTags(tags -> tags.tag(VoyageGuns.SHOT_LOCKERS).add(Blocks.BARREL, Blocks.CHEST, Blocks.TRAPPED_CHEST));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(MaterializeGameTests.class, ArmedShipGameTests.class);
    }
}
