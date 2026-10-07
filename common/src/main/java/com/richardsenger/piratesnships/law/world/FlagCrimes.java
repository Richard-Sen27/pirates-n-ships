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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
 *       interim rule: the owner is wanted) each observer rolls {@link FalseColorsDetection} with its distance, no
 *       crow's nest (that station does not exist yet) and the owner's score; a detection records
 *       {@link CrimeType#CAUGHT_FALSE_COLORS}, blows the ship's cover for {@code blown_cover_ticks} and tells the
 *       owner. Observers need no line of sight: a ship is big and the range is the limit.</li>
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
        if (!LawConfig.FLAGS_ENABLED.get()) return;
        int interval = LawConfig.OBSERVE_INTERVAL_TICKS.get();
        if (server.getTickCount() % interval != 0) return;
        ShipRegistry registry = ShipRegistry.get(server);
        long now = LawService.now(server);
        for (ServerLevel level : server.getAllLevels()) {
            for (ShipBody ship : SableShips.all(level)) {
                Optional<ShipData> data = registry.find(ship.id());
                if (data.isEmpty() || data.get().owner().isEmpty()) continue;
                FlagKind shown = data.get().flag().shown();
                if (shown == FlagKind.JOLLY_ROGER || shown == FlagKind.NAVY && !data.get().coverBlown(now)) {
                    observe(level, ship, data.get(), shown, now, interval);
                }
            }
        }
    }

    private static void observe(ServerLevel level, ShipBody ship, ShipData data, FlagKind shown, long now, int interval) {
        double range = LawConfig.OBSERVE_RANGE.get();
        AABB hull = ship.worldBounds();
        List<LivingEntity> observers = level.getEntitiesOfClass(LivingEntity.class, hull.inflate(range),
                e -> e.isAlive() && LawService.isNavy(e) && distance(e.getEyePosition(), hull) <= range);
        if (observers.isEmpty()) return;
        LivingEntity owner = LawService.findLoaded(level.getServer(), data.owner().orElseThrow());
        if (owner == null) return;
        String name = victimName(data);
        if (shown == FlagKind.JOLLY_ROGER) {
            LawService.reportCrime(owner, CrimeType.SEEN_UNDER_JOLLY_ROGER, data.id(), name);
            return;
        }
        if (!LawService.fliesFalseColours(owner, shown)) return;
        FalseColorsDetection.Params params = LawConfig.detectionParams();
        double score = LawService.score(owner);
        for (LivingEntity observer : observers) {
            double chance = FalseColorsDetection.chance(params, true, distance(observer.getEyePosition(), hull), false, score, interval);
            if (FalseColorsDetection.roll(chance, level.getRandom())) {
                caught(level.getServer(), data, owner, name, now);
                return;
            }
        }
    }

    private static void caught(MinecraftServer server, ShipData data, LivingEntity owner, String name, long now) {
        LawService.reportCrime(owner, CrimeType.CAUGHT_FALSE_COLORS, data.id(), name);
        ShipRegistry registry = ShipRegistry.get(server);
        // re-read: the crime report may not touch the ship record, but keep whatever else changed this tick
        ShipData current = registry.find(data.id()).orElse(data);
        registry.put(current.withBlownCoverUntil(now + LawConfig.BLOWN_COVER_TICKS.get()));
        owner.sendSystemMessage(Component.translatable(COVER_BLOWN_KEY).withStyle(ChatFormatting.RED));
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
