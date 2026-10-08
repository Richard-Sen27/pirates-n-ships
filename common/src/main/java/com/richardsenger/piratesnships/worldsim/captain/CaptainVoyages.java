package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.mob.captain.CaptainEntry;
import com.richardsenger.piratesnships.mob.captain.CaptainRegistry;
import com.richardsenger.piratesnships.mob.captain.CaptainSeaHook;
import com.richardsenger.piratesnships.mob.captain.IslandCaptains;
import com.richardsenger.piratesnships.mob.captain.PirateCaptain;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.lane.BiomeSeaGrid;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import com.richardsenger.piratesnships.worldsim.materialize.MaterializeConfig;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageCrew;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageEndings;
import com.richardsenger.piratesnships.worldsim.voyage.ConvoyPlanner;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pirate captain's voyages (BOS2, design.md §10.4, §15): he puts to sea from his island on a pirate ship, sails
 * out and back, fights at sea, and comes home to his post.
 *
 * <ul>
 *   <li><b>Departure</b> ({@link #check}, every {@value #CHECK_INTERVAL} ticks; {@link #depart} for the command): a
 *       living captain at his post gets one chance every {@code voyage_days} days ({@link CaptainVoyageRules#decide}).
 *       He sails a {@link VoyageKind#CAPTAIN} voyage of the pirates from his island ({@code from} = the island's port
 *       id) on a ship from {@code voyages.pirate_templates}, out along the lane toward a village or outpost within
 *       {@code cruise_distance} and back, or out into open sea and back. The registry marks him at sea
 *       ({@code CaptainEntry.voyage}); his copy at the post is stowed (its state kept in {@link CaptainSeaData}) and
 *       removed, so his post stands empty. A copy that stayed in an unloaded chunk leaves when it loads.</li>
 *   <li><b>Aboard</b> ({@link #board}, after every materialisation): he takes the place of one of the ship's pirate
 *       fighters (he is its lead fighter), as the same entity (UUID, name, health, equipment) with the voyage's
 *       fighter tags, so the materialiser counts and removes him like any fighter. Removed with his ship when it
 *       becomes a record again, he is stowed, not lost ({@link CaptainSeaHook}); the next materialisation brings him
 *       back.</li>
 *   <li><b>Death at sea</b>: killed aboard (a duel, a cannonball, a fall), the island's registry marks him lost as on
 *       land, and a player's kill gives the bounty proof. A ship that sinks takes him down with it: he drowns, and the
 *       last player who hit the ship ({@code VoyageEndings}) counts as his killer. A ship captured with him dead is a
 *       capture like any other (WS3b). Taken alive in shackles, he leaves his voyage and is an ordinary prisoner.</li>
 *   <li><b>Homecoming</b>: when his voyage ends otherwise (arrived, lost, cancelled) the registry marks him home and
 *       he appears at his post from his stowed state as soon as the post is loaded.</li>
 * </ul>
 *
 * GameTest voyages ({@code gametest/…} origins) are left to their tests by the automatic checks.
 */
public final class CaptainVoyages implements CaptainSeaHook {

    public static final int CHECK_INTERVAL = 100;
    static final String KEY = "message." + Constants.MOD_ID + ".captain_voyage.";
    public static final CaptainVoyages HOOK = new CaptainVoyages();
    private static final String VOYAGE_TAG_PREFIX = "pirates_n_ships.voyage";
    /** A lane grid holding more cached cells than this is dropped and rebuilt (memory bound). */
    private static final long MAX_GRID_SAMPLES = 500_000;
    private static final Map<ResourceKey<Level>, BiomeSeaGrid> GRIDS = new ConcurrentHashMap<>();
    /** Captains' voyages that just sank, until their ending is announced (same call). */
    private static final Map<UUID, MinecraftServer> SINKING = new ConcurrentHashMap<>();

    /** What {@link #depart} did. */
    public enum Outcome {
        SAILED, DISABLED, NO_CAPTAIN, AWAY, NO_ISLAND, NO_ROUTE;

        public boolean sailed() {
            return this == SAILED;
        }
    }

    /** The outcome and the voyage, when he sailed. */
    public record Departure(Outcome outcome, Optional<Voyage> voyage) {
        static Departure failed(Outcome o) {
            return new Departure(o, Optional.empty());
        }
    }

    private CaptainVoyages() {
    }

    // ------------------------------------------------------------------ lookups

    /** Whether {@code v} is a captain's voyage. */
    public static boolean isCaptainVoyage(Voyage v) {
        return v.kind() == VoyageKind.CAPTAIN;
    }

    /** The island captain at sea on {@code v}, if he is still alive and on it. */
    public static Optional<CaptainEntry> captainOf(MinecraftServer server, Voyage v) {
        if (!isCaptainVoyage(v)) return Optional.empty();
        return CaptainRegistry.get(server).get(v.from()).filter(e -> e.atSea() && e.voyage().equals(Optional.of(v.id())));
    }

    /** The loaded copy of the captain {@code id} in {@code level}, if any. */
    public static @Nullable PirateCaptain loaded(ServerLevel level, UUID id) {
        Entity e = level.getEntity(id);
        return e instanceof PirateCaptain c && !c.isRemoved() ? c : null;
    }

    static long today(MinecraftServer server) {
        return IslandCaptains.today(server);
    }

    // ------------------------------------------------------------------ the periodic check

    /** {@code SERVER_TICK_END}: departures and homecomings every {@value #CHECK_INTERVAL} ticks. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        check(server, server.overworld().getRandom());
    }

    /**
     * One check: every island captain of a world port (not GameTest islands) gets his chance to put to sea; captains
     * coming home appear at their posts when loaded; the records of lost captains are dropped.
     */
    public static void check(MinecraftServer server, RandomSource rng) {
        CaptainRegistry registry = CaptainRegistry.get(server);
        CaptainSeaData sea = CaptainSeaData.get(server);
        for (Map.Entry<ResourceLocation, CaptainEntry> e : registry.all().entrySet()) {
            ResourceLocation port = e.getKey();
            if (!e.getValue().alive()) {
                sea.removeSea(port);
                continue;
            }
            Optional<CaptainSeaData.Sea> s = sea.sea(port);
            if (s.isPresent() && s.get().returning()) {
                comeHome(server, port);
                continue;
            }
            if (port.getPath().startsWith("gametest/")) continue;
            Optional<Port> island = PortRegistry.get(server).index().byId(port);
            if (island.isEmpty() || !ConvoyPlanner.isWorldPort(island.get())) continue;
            if (Voyages.active(server).size() >= WorldSimConfig.MAX_SIMULTANEOUS_VOYAGES.get()) continue;
            chance(server, port, rng.nextDouble(), rng);
        }
    }

    /**
     * The captain of {@code port} gets his chance to put to sea with {@code roll} (also the GameTest entry). Returns the
     * verdict; on {@link CaptainVoyageRules.Verdict#SAIL} he has sailed (or tried to: a failed departure still used his
     * chance).
     */
    public static CaptainVoyageRules.Verdict chance(MinecraftServer server, ResourceLocation port, double roll, RandomSource rng) {
        Optional<CaptainEntry> entry = CaptainRegistry.get(server).get(port);
        CaptainSeaData data = CaptainSeaData.get(server);
        long today = today(server);
        boolean alive = entry.map(CaptainEntry::alive).orElse(false);
        CaptainSeaData.Sea sea = alive ? data.seaOrNew(port, today) : null;
        boolean atPost = alive && !entry.get().atSea() && !sea.returning() && atPost(server, entry.get());
        CaptainVoyageRules.Verdict verdict = CaptainVoyageRules.decide(CaptainVoyageConfig.active(), alive, atPost, today,
                sea == null ? today : sea.lastChanceDay(), CaptainVoyageConfig.schedule(), roll);
        if (verdict.usesChance()) data.putSea(port, data.sea(port).orElse(sea).withLastChanceDay(today));
        if (verdict == CaptainVoyageRules.Verdict.SAIL) {
            Departure d = depart(server, port, rng);
            if (!d.outcome().sailed()) Constants.LOG.debug("Captain of {} could not put to sea: {}", port, d.outcome());
        }
        return verdict;
    }

    /** Whether the captain keeps his post ({@link CaptainVoyageRules#atPost}). */
    static boolean atPost(MinecraftServer server, CaptainEntry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) return false;
        PirateCaptain c = loaded(level, entry.id());
        boolean postLoaded = level.isPositionEntityTicking(entry.post());
        if (c == null) return !postLoaded; // nowhere to be seen, or his post unloaded (then he stands there)
        double d = Math.sqrt(c.distanceToSqr(Vec3.atBottomCenterOf(entry.post())));
        return CaptainVoyageRules.atPost(true, true, d, BrigService.isPrisoner(c), c.inDuel());
    }

    // ------------------------------------------------------------------ departure

    /**
     * The captain of {@code port} puts to sea now (the scheduler and {@code /pirates mob captain voyage}): the route is
     * planned from his island ({@link #route}); ignores the schedule, the chance and the voyage cap, not the toggles.
     */
    public static Departure depart(MinecraftServer server, ResourceLocation port, RandomSource rng) {
        if (!CaptainVoyageConfig.active()) return Departure.failed(Outcome.DISABLED);
        Optional<CaptainEntry> entry = CaptainRegistry.get(server).get(port);
        if (entry.isEmpty() || !entry.get().alive()) return Departure.failed(Outcome.NO_CAPTAIN);
        Optional<Port> island = PortRegistry.get(server).index().byId(port);
        if (island.isEmpty()) return Departure.failed(Outcome.NO_ISLAND);
        ServerLevel level = server.getLevel(island.get().dimension());
        if (level == null) return Departure.failed(Outcome.NO_ISLAND);
        Optional<Route> route = route(server, level, island.get(), rng);
        if (route.isEmpty()) return Departure.failed(Outcome.NO_ROUTE);
        return departOn(server, port, route.get().waypoints(), route.get().to(), rng);
    }

    /** A planned cruise: the waypoints and the port it heads for (the island itself for an open-sea cruise). */
    public record Route(List<Lane.Point> waypoints, ResourceLocation to) {
    }

    /**
     * The cruise of {@code island}'s captain: out along the cached lane toward a random village or navy outpost of its
     * dimension within {@code cruise_distance} and back; without one (or while its lane is still being found, which
     * this queues) out into open sea from the island's first berth and back.
     */
    static Optional<Route> route(MinecraftServer server, ServerLevel level, Port island, RandomSource rng) {
        double distance = CaptainVoyageConfig.CRUISE_DISTANCE.get();
        List<Port> targets = new ArrayList<>();
        for (Port p : PortRegistry.get(server).index().all()) {
            if (p.kind() == PortKind.PIRATE_ISLAND || !p.dimension().equals(island.dimension()) || !ConvoyPlanner.isWorldPort(p)) continue;
            if (horizontal(p.centre(), island.centre()) <= distance) targets.add(p);
        }
        if (!targets.isEmpty()) {
            Port target = targets.get(rng.nextInt(targets.size()));
            Optional<Lane> lane = Lanes.between(server, island.id(), target.id());
            if (lane.isPresent() && lane.get().waypoints().size() >= 2) {
                return Optional.of(new Route(CaptainVoyageRules.towardPort(lane.get().waypoints(), distance), target.id()));
            }
        }
        BlockPos start = island.berths().isEmpty() ? island.centre() : island.berths().get(0).pos();
        double first = island.berths().isEmpty() ? rng.nextDouble() * 360.0
                : Math.toDegrees(Math.atan2(island.berths().get(0).bow().getStepZ(), island.berths().get(0).bow().getStepX()))
                + (rng.nextDouble() - 0.5) * 90.0;
        List<Lane.Point> out = CaptainVoyageRules.outAndBack(grid(level), start.getX() + 0.5, start.getZ() + 0.5, first, distance);
        if (out.size() < 2 || out.get(0).equals(out.get(1))) return Optional.empty();
        return Optional.of(new Route(out, island.id()));
    }

    /**
     * The captain of {@code port} puts to sea now on {@code waypoints} toward {@code to} (also the GameTest entry, which
     * gives a route through its basin). His copy at the post is stowed and removed. Refused while voyages are off,
     * without a living captain, or while he is at sea or on his way home.
     */
    public static Departure departOn(MinecraftServer server, ResourceLocation port, List<Lane.Point> waypoints, ResourceLocation to,
                                     RandomSource rng) {
        if (!CaptainVoyageConfig.active()) return Departure.failed(Outcome.DISABLED);
        CaptainRegistry registry = CaptainRegistry.get(server);
        Optional<CaptainEntry> found = registry.get(port);
        if (found.isEmpty() || !found.get().alive()) return Departure.failed(Outcome.NO_CAPTAIN);
        CaptainEntry entry = found.get();
        CaptainSeaData data = CaptainSeaData.get(server);
        CaptainSeaData.Sea sea = data.seaOrNew(port, today(server));
        if (entry.atSea() || sea.returning() || waypoints.size() < 2) return Departure.failed(Outcome.AWAY);
        ServerLevel level = server.getLevel(entry.dimension());
        PirateCaptain ashore = level == null ? null : loaded(level, entry.id());
        if (ashore != null && BrigService.isPrisoner(ashore)) return Departure.failed(Outcome.AWAY);

        int fighters = Math.max(1, MaterializeConfig.fighters(Faction.PIRATES)); // he is one of them, the first
        Voyage v = Voyage.depart(UUID.randomUUID(), VoyageKind.CAPTAIN, Faction.PIRATES,
                Voyages.pickTemplate(VoyageConfig.PIRATE_TEMPLATES.get(), rng), port, to, waypoints, Map.of(),
                server.overworld().getGameTime()).withCrew(Voyage.UNMANNED, fighters);
        registry.setVoyage(port, entry.id(), Optional.of(v.id()));
        if (ashore != null) {
            data.putSea(port, sea.withStash(Optional.of(save(ashore))).withReturning(false));
            ashore.vanish();
        }
        Voyage spawned = Voyages.spawn(server, v);
        Constants.LOG.debug("Captain {} of {} puts to sea on voyage {} toward {}", entry.name(), port, spawned.shortId(), to);
        return new Departure(Outcome.SAILED, Optional.of(spawned));
    }

    // ------------------------------------------------------------------ aboard

    /**
     * Puts the captain aboard the ship of materialised voyage {@code voyageId} if he is not there yet (after every
     * materialisation; also the GameTest entry): he takes the place of a pirate fighter of the voyage, or stands by a
     * crew member when none is left. Returns him aboard, or empty when he is not at sea on it (lost, a prisoner), the
     * ship is not loaded, or there is nowhere to stand.
     */
    public static Optional<PirateCaptain> board(MinecraftServer server, UUID voyageId) {
        Optional<Voyage> found = Voyages.get(server, voyageId);
        if (found.isEmpty() || found.get().state() != Voyage.State.MATERIALISED || found.get().shipId().isEmpty()) return Optional.empty();
        Voyage v = found.get();
        Optional<CaptainEntry> entry = captainOf(server, v);
        if (entry.isEmpty()) return Optional.empty();
        ServerLevel level = Materializer.levelOf(server, v);
        ShipBody ship = SableShips.byId(level, v.shipId().get());
        if (ship == null || ship.isRemoved()) return Optional.empty();
        String tag = VoyageCrew.voyageTag(v.id());
        CaptainSeaData data = CaptainSeaData.get(server);
        ResourceLocation port = v.from();
        PirateCaptain existing = loaded(level, entry.get().id());
        if (existing != null) {
            if (existing.isAlive() && existing.getTags().contains(tag)) return Optional.of(existing);
            if (BrigService.isPrisoner(existing)) return Optional.empty();
            // his copy at the post (it was unloaded when he sailed) or a stale one: it goes, he keeps its state
            CaptainSeaData.Sea s = data.seaOrNew(port, today(server));
            if (s.stash().isEmpty()) data.putSea(port, s.withStash(Optional.of(save(existing))));
            existing.vanish();
        }
        LivingEntity slot = null;
        Vec3 at = null;
        for (LivingEntity f : VoyageCrew.alive(level, ship, v.id(), VoyageCrew.FIGHTER_TAG)) {
            if (f instanceof Pirate && !(f instanceof PirateCaptain)) {
                slot = f;
                break;
            }
        }
        if (slot != null) {
            at = slot.position();
        } else {
            List<LivingEntity> crew = VoyageCrew.alive(level, ship, v.id(), VoyageCrew.CREW_TAG);
            if (!crew.isEmpty()) at = crew.get(crew.size() - 1).position();
        }
        if (at == null) return Optional.empty();
        CaptainSeaData.Sea s = data.seaOrNew(port, today(server));
        PirateCaptain captain = IslandCaptains.recreate(level, port, entry.get(), s.stash().orElse(null));
        if (captain == null) return Optional.empty();
        float yaw = slot != null ? slot.getYRot() : level.getRandom().nextFloat() * 360f;
        captain.moveTo(at.x, at.y, at.z, yaw, 0f);
        captain.setYHeadRot(yaw);
        captain.setDeltaMovement(Vec3.ZERO);
        clearVoyageTags(captain);
        captain.addTag(tag);
        captain.addTag(VoyageCrew.FIGHTER_TAG);
        captain.setSeaVoyage(v.id());
        if (!level.addFreshEntity(captain)) {
            Constants.LOG.warn("Captain {} could not go aboard voyage {}", entry.get().name(), v.shortId());
            return Optional.empty();
        }
        if (slot != null) slot.discard();
        data.putSea(port, s.withStash(Optional.empty()));
        Constants.LOG.debug("Captain {} is aboard voyage {}", entry.get().name(), v.shortId());
        return Optional.of(captain);
    }

    // ------------------------------------------------------------------ the sea hook (PirateCaptain)

    /** A captain removed alive with his ship (dematerialised, or his voyage ended with it) is stowed, not lost. */
    @Override
    public boolean stows(PirateCaptain c) {
        UUID voyage = c.seaVoyage();
        MinecraftServer server = c.level().getServer();
        if (voyage == null || server == null || c.port() == null || !c.getTags().contains(VoyageCrew.voyageTag(voyage))) return false;
        return CaptainRegistry.get(server).get(c.port())
                .filter(e -> e.alive() && e.id().equals(c.getUUID()) && e.voyage().equals(Optional.of(voyage))).isPresent();
    }

    @Override
    public void stow(PirateCaptain c) {
        MinecraftServer server = c.level().getServer();
        if (server == null || c.port() == null) return;
        CaptainSeaData data = CaptainSeaData.get(server);
        data.putSea(c.port(), data.seaOrNew(c.port(), today(server)).withStash(Optional.of(save(c))));
    }

    /**
     * Every second for every loaded captain: a captain taken prisoner leaves his voyage; a copy at his post while he is
     * at sea, a copy at sea that is not aboard his voyage's ship, or one left at sea after his voyage ended goes (its
     * state kept when nothing newer is).
     */
    @Override
    public void check(PirateCaptain c) {
        MinecraftServer server = c.level().getServer();
        ResourceLocation port = c.port();
        if (server == null || port == null) return;
        Optional<CaptainEntry> found = CaptainRegistry.get(server).get(port);
        if (found.isEmpty() || !found.get().id().equals(c.getUUID()) || !found.get().alive()) return;
        CaptainEntry entry = found.get();
        if (BrigService.isPrisoner(c)) {
            if (c.seaVoyage() != null || entry.atSea()) takenPrisoner(server, c, entry);
            return;
        }
        CaptainSeaData data = CaptainSeaData.get(server);
        if (entry.atSea()) {
            UUID voyage = entry.voyage().get();
            boolean aboard = voyage.equals(c.seaVoyage()) && c.getTags().contains(VoyageCrew.voyageTag(voyage))
                    && Voyages.get(server, voyage).map(v -> v.state() == Voyage.State.MATERIALISED).orElse(false);
            if (aboard) return;
            CaptainSeaData.Sea s = data.seaOrNew(port, today(server));
            if (s.stash().isEmpty()) data.putSea(port, s.withStash(Optional.of(save(c))));
            Constants.LOG.debug("Captain {} is at sea; his copy at {} goes", entry.name(), c.blockPosition());
            c.vanish();
        } else if (c.seaVoyage() != null) {
            // left over at sea after his voyage ended: he comes home through his post
            CaptainSeaData.Sea s = data.seaOrNew(port, today(server));
            data.putSea(port, s.withStash(Optional.of(save(c))).withReturning(true));
            c.vanish();
            comeHome(server, port);
        }
    }

    /** Shackled at sea: he is no longer his voyage's fighter or at sea; an ordinary prisoner (lost when handed over). */
    static void takenPrisoner(MinecraftServer server, PirateCaptain c, CaptainEntry entry) {
        clearVoyageTags(c);
        c.setSeaVoyage(null);
        if (c.port() != null) {
            CaptainRegistry.get(server).setVoyage(c.port(), entry.id(), Optional.empty());
            CaptainSeaData data = CaptainSeaData.get(server);
            data.sea(c.port()).ifPresent(s -> data.putSea(c.port(), s.withStash(Optional.empty()).withReturning(false)));
        }
        Constants.LOG.debug("Captain {} was taken prisoner at sea", entry.name());
    }

    // ------------------------------------------------------------------ endings and homecoming

    /** {@code Voyages.onEnd}: a captain's voyage that ends with him alive (not sunk) brings him home. */
    public static void onVoyageEnded(MinecraftServer server, Voyage voyage, VoyageEnd reason) {
        if (!isCaptainVoyage(voyage)) return;
        CaptainSeaData.get(server).removeChase(voyage.id());
        CaptainHunt.forget(voyage.id());
        if (reason == VoyageEnd.SUNK) {
            SINKING.put(voyage.id(), server); // he goes down with his ship: the ending that follows says who sank it
            return;
        }
        Optional<CaptainEntry> entry = CaptainRegistry.get(server).get(voyage.from())
                .filter(e -> e.alive() && e.voyage().equals(Optional.of(voyage.id())));
        if (entry.isEmpty()) return;
        CaptainRegistry.get(server).setVoyage(voyage.from(), entry.get().id(), Optional.empty());
        CaptainSeaData data = CaptainSeaData.get(server);
        data.putSea(voyage.from(), data.seaOrNew(voyage.from(), today(server)).withReturning(true));
        Constants.LOG.debug("Captain {} comes home from voyage {} ({})", entry.get().name(), voyage.shortId(), reason.getSerializedName());
        comeHome(server, voyage.from());
    }

    /** {@code VoyageEndings.onEnding}: his ship sank with him aboard, so he drowns; the last player who hit it killed him. */
    public static void onEnding(VoyageEndings.Ending ending) {
        if (ending.outcome() != VoyageEndings.Outcome.SUNK || !isCaptainVoyage(ending.voyage())) return;
        Voyage v = ending.voyage();
        ResourceLocation port = v.from();
        // Voyages.end (onVoyageEnded) runs right before the ending is announced, and hands over the server
        MinecraftServer server = SINKING.remove(v.id());
        if (server == null) return;
        Optional<CaptainEntry> entry = CaptainRegistry.get(server).get(port)
                .filter(e -> e.alive() && e.voyage().equals(Optional.of(v.id())));
        if (entry.isEmpty()) return;
        ServerLevel level = Materializer.levelOf(server, v);
        PirateCaptain c = loaded(level, entry.get().id());
        Player killer = ending.player().map(id -> player(server, level, id)).orElse(null);
        if (c != null && c.isAlive()) {
            drown(level, c, killer);
        }
        if (c == null || !c.isDeadOrDying()) {
            // not in the world (or he would not die): lost with his ship all the same
            CaptainRegistry.get(server).markDead(port, entry.get().id(), today(server));
            if (killer == null) LawService.withdrawBounties(server, entry.get().id());
            if (c != null) c.vanish();
        }
        CaptainSeaData.get(server).removeSea(port);
        Constants.LOG.debug("Captain {} went down with his ship on voyage {}", entry.get().name(), v.shortId());
    }

    private static @Nullable Player player(MinecraftServer server, ServerLevel level, UUID id) {
        Player online = server.getPlayerList().getPlayer(id);
        if (online != null) return online;
        return level.getEntity(id) instanceof Player p ? p : null;
    }

    /** Drowns the captain; a player killer gets the kill (the proof, the deed). */
    static void drown(ServerLevel level, PirateCaptain c, @Nullable Player killer) {
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.DROWN);
        DamageSource source = killer != null ? new DamageSource(type, killer) : new DamageSource(type);
        c.hurt(source, Float.MAX_VALUE);
    }

    /**
     * Brings the captain of {@code port} home if his voyage ended: once his post is loaded he appears there from his
     * stowed state (or his copy that never left is found there). Returns whether he is home.
     */
    public static boolean comeHome(MinecraftServer server, ResourceLocation port) {
        CaptainSeaData data = CaptainSeaData.get(server);
        Optional<CaptainSeaData.Sea> sea = data.sea(port);
        Optional<CaptainEntry> found = CaptainRegistry.get(server).get(port);
        if (sea.isEmpty() || !sea.get().returning()) return false;
        if (found.isEmpty() || !found.get().alive() || found.get().atSea()) {
            data.putSea(port, sea.get().withReturning(false));
            return false;
        }
        CaptainEntry entry = found.get();
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null || !level.isPositionEntityTicking(entry.post())) return false;
        PirateCaptain existing = loaded(level, entry.id());
        CaptainSeaData.Sea s = sea.get();
        if (existing != null) {
            if (existing.seaVoyage() == null) {
                // he never left (his post was unloaded all along): home already
                data.putSea(port, s.withStash(Optional.empty()).withReturning(false));
                return true;
            }
            if (BrigService.isPrisoner(existing)) {
                data.putSea(port, s.withStash(Optional.empty()).withReturning(false));
                return false;
            }
            s = s.withStash(Optional.of(save(existing)));
            existing.vanish();
        }
        PirateCaptain captain = IslandCaptains.recreate(level, port, entry, s.stash().orElse(null));
        if (captain == null) return false;
        BlockPos post = entry.post();
        float yaw = entry.facing().toYRot();
        captain.moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, yaw, 0f);
        captain.setYHeadRot(yaw);
        captain.setYBodyRot(yaw);
        captain.setDeltaMovement(Vec3.ZERO);
        clearVoyageTags(captain);
        captain.setSeaVoyage(null);
        if (!level.addFreshEntity(captain)) return false;
        data.putSea(port, s.withStash(Optional.empty()).withReturning(false));
        Constants.LOG.debug("Captain {} is back at his post {}", entry.name(), post);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** His entity state, to bring him back as himself. */
    static CompoundTag save(PirateCaptain c) {
        CompoundTag tag = new CompoundTag();
        c.saveWithoutId(tag);
        return tag;
    }

    /** Removes every voyage tag (the voyage, crew and fighter tags of {@code VoyageCrew}). */
    static void clearVoyageTags(Entity e) {
        for (String t : List.copyOf(e.getTags())) {
            if (t.startsWith(VOYAGE_TAG_PREFIX)) e.removeTag(t);
        }
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** The open-sea grid of {@code level} from the biome map (never loads chunks). */
    static SeaGrid grid(ServerLevel level) {
        int cell = VoyageConfig.CELL_BLOCKS.get();
        BiomeSeaGrid g = GRIDS.get(level.dimension());
        if (g == null || g.cellBlocks() != cell || g.samples() > MAX_GRID_SAMPLES) {
            g = BiomeSeaGrid.of(level, cell);
            GRIDS.put(level.dimension(), g);
        }
        return g;
    }

    static void onServerStopped() {
        GRIDS.clear();
        SINKING.clear();
    }

    /** The label of a captain's voyage in {@code /pirates world voyages}: his name, and whether he is still aboard. */
    public static Optional<Component> label(MinecraftServer server, Voyage v) {
        if (!isCaptainVoyage(v)) return Optional.empty();
        Optional<CaptainEntry> entry = CaptainRegistry.get(server).get(v.from());
        if (entry.isEmpty()) return Optional.empty();
        boolean aboard = entry.get().atSea() && entry.get().voyage().equals(Optional.of(v.id()));
        return Optional.of(Component.translatable(KEY + (aboard ? "label" : "label_without"), entry.get().name()));
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "label", "captain %s aboard")
                .add(KEY + "label_without", "without captain %s");
    }
}
