package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.voyage.ConvoyPlanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The rising raid chance (WS5, design.md §10.4): once a minute ({@value #MINUTE} ticks) every settlement that can be
 * raided ({@link RaidPlanner#target}) with a player inside its box counts one more minute of presence (one without
 * anyone starts over), and rolls once with {@link RaidRules#chance}; a hit starts a raid ({@link RaidPlanner#start}).
 * No minimum number of players. Off with {@code world_simulation.enabled} or {@code raids.enabled}.
 */
public final class RaidTracker {

    static final int MINUTE = 1200;

    /** One settlement's minute: whether a player was there, the minutes now, the chance rolled, and the raid started. */
    public record Minute(boolean present, int minutes, double chance, boolean raided) {
    }

    private RaidTracker() {
    }

    /** Server tick: one minute for every settlement that has a player inside or a presence count. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % MINUTE != 0 || !RaidConfig.active()) return;
        PortRegistry ports = PortRegistry.get(server);
        Map<ResourceLocation, Port> visited = new LinkedHashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            ports.index().containing(p.level().dimension(), p.blockPosition())
                    .filter(port -> ConvoyPlanner.isWorldPort(port) && RaidPlanner.target(port))
                    .ifPresent(port -> visited.put(port.id(), port));
        }
        RaidData data = RaidData.get(server);
        for (ResourceLocation id : data.counted()) {
            if (!visited.containsKey(id)) {
                Optional<Port> port = ports.index().byId(id);
                if (port.isPresent()) step(server, port.get(), false, 1.0);
                else data.setMinutes(id, 0);
            }
        }
        for (Port port : visited.values()) step(server, port, true, server.overworld().getRandom().nextDouble());
    }

    /**
     * One minute of {@code port} with a player inside (looked up among the entities in its box, so GameTest mock
     * players count) and the roll {@code roll} in [0, 1). The GameTest entry.
     */
    public static Minute minute(MinecraftServer server, Port port, double roll) {
        return step(server, port, present(server, port), roll);
    }

    /** Whether a living, non-spectating player is inside {@code port}'s box. */
    public static boolean present(MinecraftServer server, Port port) {
        ServerLevel level = server.getLevel(port.dimension());
        if (level == null) return false;
        return !level.getEntitiesOfClass(Player.class, AABB.of(port.box()), p -> p.isAlive() && !p.isSpectator()).isEmpty();
    }

    /** One minute of {@code port}: counts the presence and, with a player there, rolls. */
    static Minute step(MinecraftServer server, Port port, boolean present, double roll) {
        RaidData data = RaidData.get(server);
        if (data.raid(port.id()).isPresent()) return new Minute(present, data.minutes(port.id()), 0.0, false);
        int minutes = RaidRules.nextMinutes(data.minutes(port.id()), present);
        data.setMinutes(port.id(), minutes);
        if (!present) return new Minute(false, 0, 0.0, false);
        double chance = chance(server, port.id());
        boolean raided = RaidRules.rolls(chance, roll) && RaidPlanner.start(server, port, false, server.overworld().getRandom()).isPresent();
        return new Minute(true, minutes, chance, raided);
    }

    /** The raid chance per minute of a settlement now (its presence, the cooldown and the retaliation multiplier). */
    public static double chance(MinecraftServer server, ResourceLocation port) {
        RaidData data = RaidData.get(server);
        return RaidRules.chance(data.minutes(port), RaidConfig.params(server), data.lastRaidDay(port), Factions.currentDay(server));
    }

    /** Days left on the settlement's cooldown. */
    public static double cooldownLeft(MinecraftServer server, ResourceLocation port) {
        return RaidRules.cooldownLeft(RaidData.get(server).lastRaidDay(port), Factions.currentDay(server),
                RaidConfig.params(server).cooldownDays());
    }
}
