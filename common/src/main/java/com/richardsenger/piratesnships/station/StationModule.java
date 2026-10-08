package com.richardsenger.piratesnships.station;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.station.capstan.CapstanGameTests;
import com.richardsenger.piratesnships.station.capstan.CapstanLang;
import com.richardsenger.piratesnships.station.capstan.CapstanPoses;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.station.helm.HelmPoses;
import com.richardsenger.piratesnships.station.helm.HelmStationGameTests;
import com.richardsenger.piratesnships.station.helm.HelmStationLang;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.jobs.JobBoardGameTests;
import com.richardsenger.piratesnships.station.order.WhistleMenu;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import com.richardsenger.piratesnships.station.pump.PumpOrder;
import com.richardsenger.piratesnships.station.pump.PumpOrderGameTests;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Crew stations, spike 4 (docs/design.md §6, §7.2, roadmap milestone 4): the station contract, the sail winch as a
 * station, the invisible seat inside the ship's plot, the test crew member, the captain's whistle with its radial
 * order menu ({@code station/order}, screen in {@code station/client}) and {@code /pirates crew}. WS3a: the helm as a
 * station and the NPC helmsman's courses ({@code station/helm}).
 */
public final class StationModule implements ModModule {

    /** Sable keeps entities of these types inside plots instead of kicking them to world space (sable-notes §6). */
    public static final TagKey<EntityType<?>> SABLE_RETAIN = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("sable", "retain_in_sub_level"));
    /** Sable removes entities of these types when it empties a plot (disassembly, removal; {@code ServerLevelPlot#kickAllEntities}). */
    public static final TagKey<EntityType<?>> SABLE_DESTROY_WITH_SUB_LEVEL = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("sable", "destroy_with_sub_level"));

    @Override
    public String id() {
        return "station";
    }

    @Override
    public void registerConfig() {
        StationConfig.init();
        com.richardsenger.piratesnships.station.helm.CourseConfig.init();
        com.richardsenger.piratesnships.station.capstan.CapstanConfig.init(); // CRW3
    }

    @Override
    public void registerContent() {
        StationContent.init();
    }

    @Override
    public void registerPayloads() {
        WhistleOrders.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(Stations::onLevelTick);
        CommonEvents.LEVEL_TICK_END.register(JobBoard::onLevelTick);
        CommonEvents.LEVEL_TICK_END.register(HelmCourses::onLevelTick);
        HelmPoses.register(); // ART7: the helmsman's pose
        CapstanPoses.register(); // CRW3: the capstan crew pushes the bars
        CommonEvents.SERVER_STOPPED.register(server -> {
            Stations.onServerStopped();
            CaptainsWhistleItem.onServerStopped();
            JobBoard.onServerStopped();
            HelmCourses.onServerStopped();
            HelmPoses.clear();
        });
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> StationCommands.register(dispatcher));
        SableShips.onShipRemoved(Stations::onShipRemoved);
        SableShips.onShipRemoved(JobBoard::onShipRemoved);
        SableShips.onShipRemoved(HelmCourses::onShipRemoved);
        ShipSplits.onSplit(JobBoard::onSplit);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.station.client.StationClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.entityTypeTags(tags -> {
            tags.tag(SABLE_RETAIN).add(StationContent.STATION_SEAT.get());
            tags.tag(SABLE_DESTROY_WITH_SUB_LEVEL).add(StationContent.STATION_SEAT.get());
        });
        // The whistle's item model is hand-made (art/models/captains_whistle.bbmodel), so datagen writes none
        data.lang(OrderHints::lang); // Q5: order hints, /pirates ship rigging
        data.lang(HelmStationLang::lang); // WS3a: the NPC helmsman
        data.lang(CapstanLang::lang); // CRW3: the capstan station
        data.lang(lang -> {
            lang.item(StationContent.CAPTAINS_WHISTLE, "Captain's Whistle")
                    .add(StationContent.CREW_MEMBER.get().getDescriptionId(), "Crew Member")
                    .add(StationContent.STATION_SEAT.get().getDescriptionId(), "Station Seat")
                    .add(SailOrder.HOIST.nameKey(), "hoist the sails")
                    .add(SailOrder.REEF.nameKey(), "reef the sails")
                    .add(SailOrder.FURL.nameKey(), "furl the sails")
                    .add(SailOrder.HOIST.ackKey(), "Aye, hoisting the sails!")
                    .add(SailOrder.REEF.ackKey(), "Aye, reefing the sails!")
                    .add(SailOrder.FURL.ackKey(), "Aye, furling the sails!")
                    .add(PumpOrder.PUMP.nameKey(), "pump the bilge")
                    .add(PumpOrder.PUMP.ackKey(), "Aye, manning the pump!")
                    .add(PumpOrder.PUMP.nothingToDoKey(), "The bilge is dry, captain")
                    .add(PumpOrder.PUMP.unableKey(), "This pump won't draw, captain!")
                    .add(CrewStations.KEY_WRONG_STATION, "I can't %s from this station, captain!")
                    .add(CrewStations.KEY_ASSIGNED, "%s mans the station")
                    .add(CrewStations.KEY_RELEASED, "%s leaves the station")
                    .add(CrewStations.KEY_TAKEN, "Somebody already mans this station")
                    .add(CrewStations.KEY_NOT_A_STATION, "That is not a station on an assembled ship")
                    .add(CrewStations.KEY_NOTHING_TO_DO, "The sails are already set, captain (%s)")
                    .add(CrewStations.KEY_NO_SAILS, "This ship has no sails, captain!")
                    .add(CrewStations.KEY_DISABLED, "Crew stations are disabled on this server")
                    .add(CaptainsWhistleItem.KEY_SELECTED, "%s awaits orders: use the whistle on a station")
                    .add(CaptainsWhistleItem.KEY_NO_SELECTION, "Use the whistle on a crew member first")
                    .add(CaptainsWhistleItem.KEY_ORDER, "Order: %s (%s crew carry it out)")
                    .add(CaptainsWhistleItem.KEY_NOT_ON_SHIP, "You must stand on a ship to give orders")
                    .add(WhistleOrders.KEY_RELEASED_ALL, "%s crew members leave their stations")
                    .add(JobBoard.KEY_NO_FREE_HANDS, "No free hands to %s")
                    .add(JobBoard.KEY_ORDER_POSTED, "Order: %s (%s crew carry it out, %s stations open for free hands)")
                    .add(JobBoard.KEY_POSTED, "%s open jobs posted: %s")
                    .add(WhistleMenu.KEY_TITLE, "Orders")
                    .add(WhistleMenu.KEY_HINT, "Point at an order, then click or release the use key. Esc closes.")
                    .add(WhistleMenu.KEY_LAST, "Last order: %s")
                    .add(WhistleOrder.HOIST.nameKey(), "Hoist sails")
                    .add(WhistleOrder.HOIST.descriptionKey(), "Crew at the winches set the sails to full")
                    .add(WhistleOrder.REEF.nameKey(), "Reef sails")
                    .add(WhistleOrder.REEF.descriptionKey(), "Crew at the winches take the sails in to half")
                    .add(WhistleOrder.FURL.nameKey(), "Furl sails")
                    .add(WhistleOrder.FURL.descriptionKey(), "Crew at the winches furl the sails")
                    .add(WhistleOrder.PUMP.nameKey(), "Man the pumps")
                    .add(WhistleOrder.PUMP.descriptionKey(), "Crew at the bilge pumps pump until the bilge is dry")
                    .add(WhistleOrder.FIRE.nameKey(), "Fire!")
                    .add(WhistleOrder.FIRE.descriptionKey(), "Crew at loaded cannons fire them")
                    .add(WhistleOrder.RELEASE.nameKey(), "Release crew")
                    .add(WhistleOrder.RELEASE.descriptionKey(), "All crew of this ship leave their stations; open jobs are dropped")
                    .add(StationCommands.KEY + "spawned", "Crew member spawned")
                    .add(StationCommands.KEY + "not_crew", "That entity is not a crew member")
                    .add(StationCommands.KEY + "released", "%s crew members released")
                    .add(StationCommands.KEY_UNKNOWN_ORDER, "Unknown order: use hoist, reef, furl, pump, fire, load, fire_at_will, drop_anchor or raise_anchor")
                    .add(StationCommands.KEY_ORDERED, "Order %s: %s of %s crew carry it out");
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(StationGameTests.class, PumpOrderGameTests.class, JobBoardGameTests.class, com.richardsenger.piratesnships.station.winch.WinchOrderGameTests.class, com.richardsenger.piratesnships.crew.npc.CrewPoseGameTests.class, HelmStationGameTests.class, CapstanGameTests.class);
    }
}
