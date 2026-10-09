package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonShipHits;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FalseColorsDetection;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.law.flag.FlagLaw;
import com.richardsenger.piratesnships.law.flag.Reaction;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.lookout.Lookouts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Flags acting in the world (FL2, docs/design.md §4.7 "In the world", §13.1). The world adapter between the ships'
 * allegiance ({@link ShipData#flag()}, kept by {@code ship.decor.flag.ShipAllegiance}), the navy and the criminal
 * record. Everything is off with {@code law.flags.enabled}.
 * <ul>
 *   <li><b>Stance</b> ({@link #stanceOf}): what the ship a player or crew member is aboard tells NPCs; the mob
 *       hostility rules read it through {@code LawService.shipStance}.</li>
 *   <li><b>Observation</b> ({@link #onServerTick}): every {@code observe_interval_ticks} each loaded ship under the
 *       Jolly Roger or a navy flag is looked at by the navy mobs within {@code observe_range} of its hull. Under the
 *       Jolly Roger its owner commits {@link CrimeType#SEEN_UNDER_JOLLY_ROGER} (the crime's repeat window keeps it to
 *       once per window and ship). Under a navy flag that is false colours ({@link LawService#fliesFalseColours},
 *       interim rule: the owner is wanted) each observer rolls {@link FalseColorsDetection} with its distance,
 *       whether its own ship has a manned crow's nest (LAW4, {@link #hasMannedNest}: such an observer also notices
 *       the flag from {@code crows_nest_range_factor} times {@code observe_range}) and the owner's score; a detection records
 *       {@link CrimeType#CAUGHT_FALSE_COLORS}, blows the ship's cover for {@code blown_cover_ticks} and tells the
 *       owner. Observers need no line of sight: a ship is big and the range is the limit.</li>
 *   <li><b>Plunder aboard</b> (LAW3, {@code law.plunder_notice}, independent of {@code law.flags.enabled}): the same
 *       observers look at every owned ship whatever it flies; more than {@code law.plunder_notice_units}
 *       plunder-marked units in its containers ({@link PlunderAboard}, {@link PlunderNotice}) records
 *       {@link CrimeType#SUSPECTED_PIRACY} once per ship and repeat window and tells the owner.</li>
 *   <li><b>Ship hits</b> ({@link #onShipHit}, from {@link CannonShipHits}): a ball from another ship or from land
 *       hitting a ship that struck its colours or flies a neutral flag is a crime of the shooter (a player), else of
 *       the firing ship's owner ({@link FlagLaw#crimeForShipHit}).</li>
 * </ul>
 * The owner of a ship must be loaded (online) to be charged; crimes of an offline owner's crew are not recorded.
 * Merchant NPC ships do not exist yet: when they do, they react through {@link #merchantReaction}.
 */
public final class FlagCrimes {

    public static final String COVER_BLOWN_KEY = "message." + Constants.MOD_ID + ".flags.cover_blown";

    private FlagCrimes() {
    }

    /** Registered by {@code LawModule#registerEvents}. */
    public static void register() {
        CannonShipHits.register(FlagCrimes::onShipHit);
        CommonEvents.SERVER_TICK_END.register(FlagCrimes::onServerTick);
    }

    // --- stance -------------------------------------------------------------------------------------------------

    /** What the ship {@code entity} stands on or rides tells NPCs; {@link ShipStance#NONE} off a ship or when disabled. */
    public static ShipStance stanceOf(LivingEntity entity) {
        if (!LawConfig.FLAGS_ENABLED.get() || !(entity.level() instanceof ServerLevel level)) return ShipStance.NONE;
        ShipBody ship = ShipEntities.standingOrRiding(entity);
        if (ship == null) return ShipStance.NONE;
        Optional<ShipData> data = ShipRegistry.get(level.getServer()).find(ship.id());
        return data.map(d -> stanceOf(d, LawService.now(level.getServer()))).orElse(ShipStance.NONE);
    }

    /** The stance of a ship record at {@code now} (overworld game time). */
    public static ShipStance stanceOf(ShipData ship, long now) {
        FlagReading flag = ship.flag();
        return ShipStance.of(flag.shown(), flag.isStruck(), ship.coverBlown(now));
    }

    /** Hook for merchant NPC ships (none yet, docs/design.md §4.7): how they would treat a ship showing {@code flag}. */
    public static Reaction merchantReaction(FlagReading flag) {
        return LawService.react(Faction.MERCHANTS, flag.shown());
    }

    // --- observation --------------------------------------------------------------------------------------------

    /** Throttled to {@code law.flags.observe_interval_ticks}. */
    public static void onServerTick(MinecraftServer server) {
        boolean flags = LawConfig.FLAGS_ENABLED.get();
        PlunderNotice.Params plunder = LawConfig.plunderNoticeParams();
        if (!flags && !plunder.enabled()) return;
        int interval = LawConfig.OBSERVE_INTERVAL_TICKS.get();
        if (server.getTickCount() % interval != 0) return;
        ShipRegistry registry = ShipRegistry.get(server);
        long now = LawService.now(server);
        for (ServerLevel level : server.getAllLevels()) {
            for (ShipBody ship : SableShips.all(level)) {
                Optional<ShipData> data = registry.find(ship.id());
                if (data.isEmpty() || data.get().owner().isEmpty()) continue;
                FlagKind shown = data.get().flag().shown();
                boolean watchFlag = flags && (shown == FlagKind.JOLLY_ROGER || shown == FlagKind.NAVY && !data.get().coverBlown(now));
                if (watchFlag || plunder.enabled()) {
                    observe(level, ship, data.get(), shown, now, interval, watchFlag, plunder);
                }
            }
        }
    }

    private static void observe(ServerLevel level, ShipBody ship, ShipData data, FlagKind shown, long now, int interval,
                                boolean watchFlag, PlunderNotice.Params plunder) {
        double range = LawConfig.OBSERVE_RANGE.get();
        FalseColorsDetection.Params params = LawConfig.detectionParams();
        AABB hull = ship.worldBounds();
        // LAW4: observers aboard a ship with a manned crow's nest notice flags from farther off
        double gather = watchFlag ? FalseColorsDetection.observeRange(params, range, nestsCount()) : range;
        Map<UUID, Boolean> nests = new HashMap<>();
        List<LivingEntity> navy = level.getEntitiesOfClass(LivingEntity.class, hull.inflate(gather),
                e -> e.isAlive() && LawService.isNavy(e) && distance(e.getEyePosition(), hull) <= gather);
        if (navy.isEmpty()) return;
        List<LivingEntity> inRange = navy.stream().filter(e -> distance(e.getEyePosition(), hull) <= range).toList();
        List<LivingEntity> flagObservers = navy.stream().filter(e -> distance(e.getEyePosition(), hull)
                <= FalseColorsDetection.observeRange(params, range, hasMannedNest(level, e, nests))).toList();
        if (inRange.isEmpty() && (!watchFlag || flagObservers.isEmpty())) return;
        LivingEntity owner = LawService.findLoaded(level.getServer(), data.owner().orElseThrow());
        if (owner == null) return;
        String name = victimName(data);
        // looking into the containers is no matter of eyesight: the plain observe range
        if (plunder.enabled() && !inRange.isEmpty()) observePlunder(ship, data, owner, name, now, plunder);
        if (!watchFlag || flagObservers.isEmpty()) return;
        if (shown == FlagKind.JOLLY_ROGER) {
            LawService.reportCrime(owner, CrimeType.SEEN_UNDER_JOLLY_ROGER, data.id(), name);
            return;
        }
        if (!LawService.fliesFalseColours(owner, shown)) return;
        double score = LawService.score(owner);
        for (LivingEntity observer : flagObservers) {
            double chance = detectionChance(level, observer, hull, params, score, interval, nests);
            if (FalseColorsDetection.roll(chance, level.getRandom())) {
                caught(level.getServer(), data, owner, name, now);
                return;
            }
        }
    }

    /** Whether a crow's nest can help any observer at all (the toggle on and a factor above 1). */
    private static boolean nestsCount() {
        return LawConfig.CROWS_NEST_OBSERVERS.get();
    }

    /**
     * LAW4: whether {@code observer} stands on or rides a ship with a manned crow's nest ({@link Lookouts#isManned}).
     * Off a ship (outposts, land, water) never; {@code law.flags_brig.crows_nest_observers} off never. {@code cache}
     * holds the answer per ship for one observation.
     */
    public static boolean hasMannedNest(ServerLevel level, LivingEntity observer, Map<UUID, Boolean> cache) {
        if (!nestsCount()) return false;
        ShipBody aboard = ShipEntities.standingOrRiding(observer);
        if (aboard == null) return false;
        return cache.computeIfAbsent(aboard.id(), id -> Lookouts.isManned(level, aboard));
    }

    /** The chance that {@code observer} sees through false colours on the ship with world bounds {@code hull} in one check. */
    public static double detectionChance(ServerLevel level, LivingEntity observer, AABB hull, FalseColorsDetection.Params params,
                                         double score, long interval, Map<UUID, Boolean> nests) {
        return FalseColorsDetection.chance(params, true, distance(observer.getEyePosition(), hull),
                hasMannedNest(level, observer, nests), score, interval);
    }

    private static void caught(MinecraftServer server, ShipData data, LivingEntity owner, String name, long now) {
        LawService.reportCrime(owner, CrimeType.CAUGHT_FALSE_COLORS, data.id(), name);
        ShipRegistry registry = ShipRegistry.get(server);
        // re-read: the crime report may not touch the ship record, but keep whatever else changed this tick
        ShipData current = registry.find(data.id()).orElse(data);
        registry.put(current.withBlownCoverUntil(now + LawConfig.BLOWN_COVER_TICKS.get()));
        owner.sendSystemMessage(Component.translatable(COVER_BLOWN_KEY).withStyle(ChatFormatting.RED));
    }

    /**
     * LAW3 (§13.4): the navy looks into the ship's containers; more than {@code law.plunder_notice_units} marked units
     * is {@link CrimeType#SUSPECTED_PIRACY} of the owner against the ship, once per ship and repeat window (one in-game
     * day). The containers are only counted while that window is closed.
     */
    private static void observePlunder(ShipBody ship, ShipData data, LivingEntity owner, String name, long now, PlunderNotice.Params p) {
        if (recentlyCharged(owner, CrimeType.SUSPECTED_PIRACY, data.id(), now)) return;
        long marked = PlunderAboard.markedUnits(ship.plotBlockEntities());
        if (!PlunderNotice.noticed(marked, p)) return;
        if (LawService.reportCrime(owner, CrimeType.SUSPECTED_PIRACY, data.id(), name).counted()) {
            owner.sendSystemMessage(Component.translatable(PlunderNotice.SPOTTED_KEY).withStyle(ChatFormatting.RED));
        }
    }

    /** Whether {@code offender} has a counted {@code type} against {@code victim} still inside its repeat window. */
    private static boolean recentlyCharged(LivingEntity offender, CrimeType type, UUID victim, long now) {
        if (!LawConfig.CRIMINAL_SCORE_ENABLED.get()) return false;
        long window = LawConfig.crimeRules().cooldownOf(type);
        return LawService.record(offender).recent().stream()
                .anyMatch(o -> o.type() == type && o.victim().filter(victim::equals).isPresent() && now - o.time() < window);
    }

    /** Distance from {@code p} to the nearest point of {@code box} (0 inside). */
    static double distance(Vec3 p, AABB box) {
        double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // --- ship hits ----------------------------------------------------------------------------------------------

    /** A cannonball hit a ship it was not fired from. */
    public static void onShipHit(CannonShipHits.ShipHit hit) {
        if (!LawConfig.FLAGS_ENABLED.get()) return;
        MinecraftServer server = hit.level().getServer();
        ShipRegistry registry = ShipRegistry.get(server);
        Optional<ShipData> target = registry.find(hit.hitShip());
        if (target.isEmpty() || !target.get().flag().hasFlag()) return;
        LivingEntity offender = offender(server, registry, hit.shooter(), hit.firingShip());
        if (offender == null) return;
        boolean own = hit.hitShip().equals(hit.firingShip())
                || target.get().owner().map(offender.getUUID()::equals).orElse(false);
        FlagReading flag = target.get().flag();
        CrimeType crime = FlagLaw.crimeForShipHit(flag.kind(), flag.isStruck(), own);
        if (crime != null) LawService.reportCrime(offender, crime, hit.hitShip(), victimName(target.get()));
    }

    /** The player who fired, else the loaded owner of the firing ship, else null. */
    private static @Nullable LivingEntity offender(MinecraftServer server, ShipRegistry registry, @Nullable net.minecraft.world.entity.Entity shooter,
                                                   @Nullable UUID firingShip) {
        if (shooter instanceof Player p) return p;
        if (firingShip == null) return null;
        Optional<UUID> owner = registry.find(firingShip).flatMap(ShipData::owner);
        return owner.map(id -> LawService.findLoaded(server, id)).orElse(null);
    }

    private static String victimName(ShipData ship) {
        return ship.name().isEmpty() ? ship.id().toString() : ship.name();
    }
}
