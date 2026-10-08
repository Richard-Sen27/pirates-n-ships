package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.ShipsAtPort;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Server side of the Crew tab (CRW1). {@code MarketBackend.openDesk} sends {@link #view} with the market;
 * {@code MarketBackend} routes {@link CrewPayloads.CrewAction} to {@link #handle}, which refuses without a valid desk
 * session for the port ({@link MarketBackend#canUse}: reach and binding) and answers with the tab's new content and the
 * result.
 */
public final class HiringBackend {

    private HiringBackend() {
    }

    /** The Crew tab of {@code port} for {@code player}; empty while hiring is off or the port is unknown (no tab). */
    public static Optional<CrewPayloads.CrewView> view(ServerPlayer player, ResourceLocation portId) {
        if (!Hiring.enabled()) return Optional.empty();
        Optional<Port> port = Hiring.port(player.server, portId);
        if (port.isEmpty()) return Optional.empty();
        HiringRules.Verdict verdict = Hiring.verdict(player, port.get());
        List<Candidate> candidates = verdict.ok() ? Hiring.candidates(player.server, port.get()) : List.of();
        int wage = CrewConfig.WAGES_ENABLED.get() ? CrewConfig.WAGE_PER_DAY.get() : 0;
        return Optional.of(new CrewPayloads.CrewView(portId, CandidateKind.at(port.get().kind()), wage, candidates,
                verdict.ok() ? Optional.empty() : Optional.of(Hiring.key(verdict)), shipLine(player, port.get())));
    }

    private static Optional<CrewPayloads.ShipLine> shipLine(ServerPlayer player, Port port) {
        ServerLevel level = player.server.getLevel(port.dimension());
        if (level == null) return Optional.empty();
        Optional<ShipBody> ship = ShipsAtPort.nearest(level, player.getUUID(), port, HiringConfig.SHIP_RADIUS.get(), player.position());
        if (ship.isEmpty()) return Optional.empty();
        ShipBunks.Count count = ShipBunks.count(level, ship.get());
        String name = ShipRegistry.get(player.server).find(ship.get().id()).map(ShipData::name).orElse("");
        int cap = HiringRules.crewCap(count.bunks(), HiringConfig.REQUIRE_BUNKS.get(), HiringConfig.MAX_WITHOUT_BUNKS.get());
        return Optional.of(new CrewPayloads.ShipLine(name, count.crew(), cap));
    }

    /** Runs a tab action; returns the payload to send back. */
    public static CrewPayloads.CrewPayload handle(ServerPlayer player, CrewPayloads.CrewAction p) {
        if (!MarketBackend.canUse(player, p.port())) {
            return new CrewPayloads.CrewPayload(Optional.empty(),
                    Optional.of(CrewPayloads.CrewResult.of(Hiring.Result.fail(HiringText.NO_SESSION, ""))));
        }
        Hiring.Result r = Hiring.hire(player, p.port(), p.candidate());
        return new CrewPayloads.CrewPayload(view(player, p.port()), Optional.of(CrewPayloads.CrewResult.of(r)));
    }
}
