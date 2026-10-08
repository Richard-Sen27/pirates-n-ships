package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonShipHits;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodReport;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * How a materialised voyage ends or changes (WS3b, design.md §10.4), checked once per {@link Materializer} update with
 * the pure {@link EndingRules}:
 *
 * <ul>
 *   <li><b>SUNK</b> (flooded, a wreck, or under the sea): the voyage ends {@link VoyageEnd#SUNK}, its cargo is lost
 *       with the record (the containers sink with the hull). The last player whose cannonball hit it within
 *       {@code shooter_memory_ticks} gets the deed {@code sink_<faction>} ({@link CannonShipHits}; a crew shot counts
 *       for the firing ship's owner); without one the faction event of the lost ship is reported as a world event.</li>
 *   <li><b>CAPTURED</b> (all fighters dead, a player aboard for {@code capture_hold_ticks}): the ship becomes the
 *       player's ({@code ShipData.withOwner}), the crew is released and stays aboard as ordinary crew, the voyage ends
 *       {@link VoyageEnd#CAPTURED}, the deed {@code capture_<faction>}; a merchant ship is the crime {@code piracy}
 *       instead (REP1 records that as {@code plunder_merchant}).</li>
 *   <li><b>PLUNDERED</b> (cargo taken with a player aboard): the deed {@code plunder_merchant} once per voyage (merchant
 *       ships), the record's cargo follows the containers, the voyage sails on.</li>
 * </ul>
 *
 * Double-counting rule (WS1): a player's act is a deed, never also a world event.
 */
public final class VoyageEndings {

    static final String KEY = "message." + Constants.MOD_ID + ".voyage.";
    public static final String KEY_CAPTURED = KEY + "captured";
    public static final String KEY_PLUNDERED = KEY + "plundered";

    /** What happened to a materialised voyage. */
    public enum Outcome { SUNK, CAPTURED, PLUNDERED }

    /**
     * One ending or plunder: the voyage as it was, who did it ({@code player}, empty for a sinking nobody caused), the
     * deed recorded (null when none) and the world event reported instead (null when none).
     */
    public record Ending(Voyage voyage, UUID ship, Outcome outcome, Optional<UUID> player, @Nullable VoyageDeed deed,
                         @Nullable com.richardsenger.piratesnships.worldsim.faction.FactionEvent worldEvent) { }

    /** Records a player's deed. */
    @FunctionalInterface
    public interface DeedRecorder {
        void record(MinecraftServer server, UUID player, VoyageDeed deed);
    }

    /**
     * The default: the REP1 deed for an online player ({@code Deeds.record}) and the faction side through
     * {@code Factions.reportDeed}. When a deed-to-faction adapter ({@code FactionDeeds}) lands, drop the second call.
     */
    public static final DeedRecorder DEFAULT = (server, player, deed) -> {
        net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            deed.reputationDeed().ifPresent(d -> com.richardsenger.piratesnships.rpg.deeds.Deeds.record(online, d,
                    com.richardsenger.piratesnships.rpg.deeds.DeedContext.NONE));
        }
        Factions.reportDeed(server, deed.factionEvent());
    };

    private static volatile DeedRecorder recorder = DEFAULT;
    private static final List<java.util.function.Consumer<Ending>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, EndingRules.Hit> HITS = new ConcurrentHashMap<>();

    private VoyageEndings() {
    }

    /** Replaces how deeds are recorded (REP1 adapter). */
    public static void setDeedRecorder(DeedRecorder r) {
        recorder = r == null ? DEFAULT : r;
    }

    /** Listens to every ending and plunder (WS4b, WS5, tests). */
    public static void onEnding(java.util.function.Consumer<Ending> listener) {
        LISTENERS.add(listener);
    }

    // ------------------------------------------------------------------ hits

    /** {@link CannonShipHits} listener: remembers the last player responsible for a hit on each ship. */
    public static void onShipHit(CannonShipHits.ShipHit hit) {
        UUID player = null;
        if (hit.shooter() instanceof Player p) {
            player = p.getUUID();
        } else if (hit.firingShip() != null) {
            player = ShipRegistry.get(hit.level().getServer()).find(hit.firingShip()).flatMap(ShipData::owner).orElse(null);
        }
        if (player != null) recordHit(hit.hitShip(), player, hit.level().getGameTime());
    }

    /** Remembers that {@code player} hit {@code ship} at {@code tick} (also the GameTest entry). */
    public static void recordHit(UUID ship, UUID player, long tick) {
        HITS.put(ship, new EndingRules.Hit(player, tick));
    }

    static void forgetShip(UUID ship) {
        HITS.remove(ship);
    }

    static void clear() {
        HITS.clear();
    }

    // ------------------------------------------------------------------ the check

    /** The flooded fraction of {@code ship}'s hull now (0 without a hull analysis). */
    public static double floodFraction(ServerLevel level, ShipBody ship) {
        HullRuntime rt = HullRuntimes.get(level, ship.id());
        if (rt == null) return 0.0;
        FloodReport report = rt.lastReport();
        if (report == null) return 0.0;
        double inside = 0;
        for (Compartment c : rt.simulation().analysis().compartments()) inside += c.volume();
        return EndingRules.floodFraction(report.floodVolume(), inside);
    }

    private static double seaY(ServerLevel level, ShipBody ship) {
        HullRuntime rt = HullRuntimes.get(level, ship.id());
        return rt != null && rt.seesSea() ? rt.seaWorldY() : level.getSeaLevel();
    }

    /** Players standing on {@code ship} (or riding something on it). */
    static List<Player> aboard(ServerLevel level, ShipBody ship) {
        return level.getEntitiesOfClass(Player.class, CrewStations.worldBox(ship, 2), p -> p.isAlive() && !p.isSpectator()
                && onShip(level, ship, p));
    }

    private static boolean onShip(ServerLevel level, ShipBody ship, Player p) {
        ShipBody on = CaptainsWhistleItem.shipOf(level, p);
        return on != null && on.id().equals(ship.id());
    }

    /**
     * Checks {@code v}'s ship once, {@code dt} ticks after the last check. Returns true when the voyage ended (sunk or
     * captured); the link is gone then.
     */
    static boolean check(MinecraftServer server, ServerLevel level, Voyage v, VoyageShips.Active a, ShipBody ship, int dt) {
        EndingRules.Params p = MaterializeConfig.endingParams();
        long now = level.getGameTime();
        boolean under = ship.worldBounds().maxY < seaY(level, ship);
        a.submerged = EndingRules.submergedTicks(a.submerged, under, dt);
        if (EndingRules.sunk(floodFraction(level, ship), ShipSplits.isWreck(ship), a.submerged, p)) {
            sink(server, level, v, ship.id(), ship, EndingRules.blame(HITS.get(ship.id()), now, p));
            return true;
        }
        List<Player> aboard = aboard(level, ship);
        int fighters = VoyageCrew.alive(level, ship, v.id(), VoyageCrew.FIGHTER_TAG).size();
        a.capture = EndingRules.captureTicks(a.capture, fighters, !aboard.isEmpty(), dt);
        if (EndingRules.captured(a.capture, p)) {
            capture(server, level, v, ship, aboard.get(0));
            return true;
        }
        trackCargo(server, level, v, a, ship, aboard);
        return false;
    }

    /** Follows the cargo aboard: the record's cargo mirrors the containers; goods taken by a player are plunder. */
    static void trackCargo(MinecraftServer server, ServerLevel level, Voyage v, VoyageShips.Active a, ShipBody ship, List<Player> aboard) {
        Map<ResourceLocation, Integer> held = VoyageShips.read(level, ship);
        int units = VoyageShips.units(held);
        if (a.lastUnits < 0) {
            a.lastUnits = units;
            return;
        }
        EndingRules.CargoChange change = EndingRules.cargoChange(a.lastUnits, units, !aboard.isEmpty(), a.plundered);
        if (change == EndingRules.CargoChange.NONE) return;
        a.lastUnits = units;
        Voyages.update(server, v.withCargo(VoyageShips.plus(held, a.overflow)));
        if (change == EndingRules.CargoChange.PLUNDERED && v.faction() == Faction.MERCHANTS) {
            a.plundered = true;
            VoyageShips.writeLink(ship, a.link());
            Player player = aboard.get(0);
            recorder.record(server, player.getUUID(), VoyageDeed.PLUNDER_MERCHANT);
            player.displayClientMessage(Component.translatable(KEY_PLUNDERED), true);
            fire(new Ending(v, ship.id(), Outcome.PLUNDERED, Optional.of(player.getUUID()), VoyageDeed.PLUNDER_MERCHANT, null));
        }
    }

    /** Ends {@code v} as sunk. */
    static void sink(MinecraftServer server, ServerLevel level, Voyage v, UUID shipId, @Nullable ShipBody ship, Optional<UUID> blame) {
        release(level, v, shipId, ship);
        Voyages.end(server, v.id(), VoyageEnd.SUNK);
        VoyageDeed deed = null;
        com.richardsenger.piratesnships.worldsim.faction.FactionEvent event = null;
        if (blame.isPresent()) {
            deed = VoyageDeed.sink(v.faction());
            recorder.record(server, blame.get(), deed);
        } else {
            event = EndingRules.lostEvent(v.faction());
            Factions.report(server, event);
        }
        Constants.LOG.debug("Voyage {} sank (ship {}), blamed on {}", v.shortId(), shipId, blame.orElse(null));
        fire(new Ending(v, shipId, Outcome.SUNK, blame, deed, event));
    }

    /** Ends {@code v} as captured by {@code player}. */
    static void capture(MinecraftServer server, ServerLevel level, Voyage v, ShipBody ship, Player player) {
        ShipRegistry registry = ShipRegistry.get(server);
        registry.find(ship.id()).ifPresent(d -> registry.put(d.withOwner(Optional.of(player.getUUID()))));
        release(level, v, ship.id(), ship);
        Voyages.end(server, v.id(), VoyageEnd.CAPTURED);
        VoyageDeed deed = VoyageDeed.capture(v.faction());
        recorder.record(server, player.getUUID(), deed);
        if (v.faction() == Faction.MERCHANTS) {
            // the law's piracy; REP1's LawDeeds turns it into plunder_merchant, so no capture_merchant deed here
            LawService.reportCrime(player, CrimeType.PIRACY, ship.id());
        }
        String name = registry.find(ship.id()).map(ShipData::name).orElse("");
        player.displayClientMessage(Component.translatable(KEY_CAPTURED, name), true);
        Constants.LOG.debug("Voyage {} captured by {} (ship {})", v.shortId(), player.getName().getString(), ship.id());
        fire(new Ending(v, ship.id(), Outcome.CAPTURED, Optional.of(player.getUUID()), deed, null));
    }

    /** The ship is not the voyage's any more: link, course and job board cleared, people let go. */
    private static void release(ServerLevel level, Voyage v, UUID shipId, @Nullable ShipBody ship) {
        VoyageShips.remove(v.id());
        if (ship != null) VoyageShips.clearLink(ship);
        HelmCourses.clear(level, shipId);
        JobBoard.clear(shipId);
        VoyageCrew.letGo(level, shipId, v.id());
        HITS.remove(shipId);
    }

    /**
     * The ship of {@code v} is gone for good without sinking first (every block shot away, or removed by other code):
     * the voyage ends as sunk, blamed like a sinking.
     */
    static void lost(MinecraftServer server, ServerLevel level, Voyage v, UUID shipId) {
        sink(server, level, v, shipId, null, EndingRules.blame(HITS.get(shipId), level.getGameTime(), MaterializeConfig.endingParams()));
    }

    private static void fire(Ending e) {
        for (var l : LISTENERS) {
            try {
                l.accept(e);
            } catch (RuntimeException ex) {
                Constants.LOG.error("Voyage ending listener failed on {}", e, ex);
            }
        }
    }

    public static void lang(com.richardsenger.piratesnships.core.datagen.LangBuilder lang) {
        lang.add(KEY_CAPTURED, "You took the %s! She is yours now")
                .add(KEY_PLUNDERED, "You plundered a merchant ship");
    }
}
