package com.richardsenger.piratesnships.ship.screen;

import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.galley.ProvisionContainer;
import com.richardsenger.piratesnships.crew.galley.ShipProvisions;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.hiring.Hiring;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.SuppliesLeft;
import com.richardsenger.piratesnships.crew.upkeep.ShipUpkeep;
import com.richardsenger.piratesnships.crew.upkeep.ShipUpkeepData;
import com.richardsenger.piratesnships.crew.upkeep.Upkeep;
import com.richardsenger.piratesnships.crew.upkeep.UpkeepSettings;
import com.richardsenger.piratesnships.rpg.career.CareerShipTitles;
import com.richardsenger.piratesnships.rpg.career.CareerTitles;
import com.richardsenger.piratesnships.rpg.career.Careers;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusSync;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the {@link ShipScreenView} of a ship for one viewer on the server (HGUI1) from what the other modules already
 * know: the record ({@link ShipRegistry}), the HUD's status ({@link ShipStatusSync#build}), the sailing runtime
 * (sails, anchor), the crew aboard ({@link ShipBunks#crewOf}), the stations ({@link Stations}) and open jobs
 * ({@link JobBoard}), the upkeep data (supplies left as {@code /pirates crew info} counts them, the last payday).
 */
public final class ShipScreenViews {

    private ShipScreenViews() {
    }

    public static ShipScreenView build(ServerLevel level, ShipBody ship, BlockPos helm, ServerPlayer viewer) {
        MinecraftServer server = level.getServer();
        Optional<ShipData> data = ShipRegistry.get(server).find(ship.id());
        Optional<UUID> owner = data.flatMap(ShipData::owner);
        List<CrewMember> crew = ShipBunks.crewOf(level, ship);
        return new ShipScreenView(ship.id(), helm.immutable(), header(server, data, owner, viewer), status(level, ship),
                upkeep(level, ship, crew.size()), crewLines(level, crew, owner, viewer), stationLines(level, ship),
                new ShipScreenView.Toggles(StationConfig.ENABLED.get(), Hiring.enabled(),
                        ShipScreenRules.mayManage(viewer.getUUID(), owner)));
    }

    private static ShipScreenView.Header header(MinecraftServer server, Optional<ShipData> data, Optional<UUID> owner, ServerPlayer viewer) {
        String name = data.map(ShipData::name).orElse("");
        String bare = CareerShipTitles.enabled() ? CareerTitles.stripTitle(name) : name;
        FlagReading flag = data.map(ShipData::flag).orElse(FlagReading.NO_FLAG);
        boolean blown = data.isPresent() && data.get().coverBlown(server.overworld().getGameTime());
        Optional<String> ownerName = Optional.empty();
        Optional<String> title = Optional.empty();
        if (owner.isPresent()) {
            ServerPlayer online = owner.get().equals(viewer.getUUID()) ? viewer : server.getPlayerList().getPlayer(owner.get());
            if (online != null) {
                ownerName = Optional.of(online.getGameProfile().getName());
                if (Careers.enabled()) title = CareerTitles.of(Careers.record(online)).map(CareerTitles.Title::key);
            } else {
                ownerName = profileName(server, owner.get());
            }
        }
        return new ShipScreenView.Header(name, bare, flag.id(), ShipScreenRules.allegiance(flag), blown, ownerName, owner.isEmpty(), title);
    }

    private static ShipScreenView.Status status(ServerLevel level, ShipBody ship) {
        ShipStatusPayload hud = ShipStatusSync.build(level, ship);
        SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
        int full = 0, half = 0, sails = 0;
        ShipScreenRules.Anchor anchor = ShipScreenRules.anchor(false, null, false);
        if (rt != null) {
            for (BlockPos p : rt.sailPositions()) {
                sails++;
                SailTrim t = rt.trimAt(p);
                if (t == SailTrim.FULL) full++;
                else if (t == SailTrim.HALF) half++;
            }
            ShipAnchor a = rt.anchor();
            anchor = ShipScreenRules.anchor(true, a == null ? null : a.state().phase(), rt.isAnchored());
        }
        return new ShipScreenView.Status(ShipScreenRules.hull(hud.cells()), hud.load(), hud.speed(), anchor, sails, full, half);
    }

    private static ShipScreenView.Upkeep upkeep(ServerLevel level, ShipBody ship, int crew) {
        ShipUpkeep upkeep = ShipUpkeepData.get(level.getServer()).get(ship.id());
        List<BlockPos> containers = new ArrayList<>();
        int hammocks = 0;
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockEntity(p) instanceof ProvisionContainer) containers.add(p.immutable());
        }
        hammocks = ShipBunks.hammocks(level, ship).size();
        SuppliesLeft left = ShipProvisions.suppliesLeft(level, containers, upkeep.provisioning(), CrewHeadcount.crew(crew));
        ShipUpkeep.PayRecord pay = upkeep.lastPay();
        return new ShipScreenView.Upkeep(crew, ShipBunks.limit(hammocks, CrewConfig.MAX_CREW_MULTIPLIER.get()),
                left.foodDays(), left.waterDays(), left.rumDays(), Upkeep.settings().wagesEnabled(), pay.wagesOn(),
                pay.paid(), pay.unpaid(), pay.coins());
    }

    private static List<ShipScreenView.CrewLine> crewLines(ServerLevel level, List<CrewMember> crew, Optional<UUID> owner, ServerPlayer viewer) {
        UpkeepSettings s = Upkeep.settings();
        List<ShipScreenView.CrewLine> out = new ArrayList<>();
        for (CrewMember c : crew) {
            StationRef ref = c.assignment();
            ShipScreenRules.CrewState state = ref != null ? ShipScreenRules.CrewState.STATION
                    : c.rest() != null ? ShipScreenRules.CrewState.RESTING : ShipScreenRules.CrewState.FREE;
            String stationKey = ref == null ? "" : level.getBlockState(ref.pos()).getBlock().getDescriptionId();
            String order = ref == null ? "" : orderKey(Stations.state(ref));
            Optional<UUID> hirer = c.hiredBy();
            Optional<String> hirerName = hirer.flatMap(h -> h.equals(viewer.getUUID()) ? Optional.of(viewer.getGameProfile().getName())
                    : playerName(level.getServer(), h));
            int morale = CrewMorale.get(c);
            out.add(new ShipScreenView.CrewLine(c.getUUID(), c.getDisplayName().getString(), morale, state,
                    ref == null ? Optional.empty() : Optional.of(ref.pos()), stationKey, order, hirerName,
                    hirer.isPresent() && hirer.get().equals(viewer.getUUID()), c.isUnpaid(),
                    ShipScreenRules.desertion(s.desertionEnabled(), s.desertBelow(), s.desertDays(), morale, c.lowMoraleDays()),
                    c.lowMoraleDays(), ShipScreenRules.mayCommand(viewer.getUUID(), hirer, owner)));
        }
        out.sort(Comparator.comparing(ShipScreenView.CrewLine::name).thenComparing(l -> l.id().toString()));
        return out;
    }

    private static List<ShipScreenView.StationLine> stationLines(ServerLevel level, ShipBody ship) {
        Map<BlockPos, JobBoard.Job> jobs = JobBoard.jobs(ship.id());
        List<ShipScreenView.StationLine> out = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof StationBlock block) || !block.stationPos(state, p).equals(p)) continue;
            StationRef ref = new StationRef(ship.id(), p.immutable());
            StationState<Object> st = Stations.state(ref);
            StationState.Occupant who = st == null ? null : st.occupant();
            String whoName = "";
            if (who != null) {
                if (who.player()) {
                    whoName = playerName(level.getServer(), who.id()).orElse("");
                } else {
                    Entity e = level.getEntity(who.id());
                    whoName = e == null ? "" : e.getDisplayName().getString();
                }
            }
            JobBoard.Job job = jobs.get(p);
            out.add(new ShipScreenView.StationLine(p.immutable(), state.getBlock().getDescriptionId(),
                    who == null ? Optional.empty() : Optional.of(who.id()), whoName, who != null && who.player(),
                    orderKey(st), job == null ? "" : job.order().nameKey()));
        }
        out.sort(Comparator.comparing(ShipScreenView.StationLine::blockKey).thenComparing(l -> l.pos().asLong()));
        return out;
    }

    private static String orderKey(StationState<Object> state) {
        return state != null && state.order() instanceof CrewOrder o ? o.nameKey() : "";
    }

    private static Optional<String> playerName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        return online != null ? Optional.of(online.getGameProfile().getName()) : profileName(server, id);
    }

    private static Optional<String> profileName(MinecraftServer server, UUID id) {
        var cache = server.getProfileCache();
        return cache == null ? Optional.empty() : cache.get(id).map(com.mojang.authlib.GameProfile::getName);
    }
}
