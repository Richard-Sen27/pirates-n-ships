package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.upkeep.CrewReplacement;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.rpg.career.Careers;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.ShipsAtPort;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hiring and dismissing crew (CRW1, docs/design.md §7.1, §7.5), server side. A port's candidates live in
 * {@link HiringData} and are made lazily at its first look on a new day ({@link #candidates}). {@link #hire} checks,
 * in this order, the toggle, the port, the candidate, the player's standing ({@link HiringRules#eligible}), the player's
 * own ship moored at the port ({@link ShipsAtPort}, {@code crew.hiring.ship_radius}), a free bunk
 * ({@link ShipBunks}, {@link HiringRules#hasRoom}), the fee in the wallet and a free deck spot ({@link HireSpots});
 * then takes the fee and puts a named {@link CrewMember} on the deck spot nearest the player (who stands at the desk),
 * with the configured start morale and {@link CrewMember#hiredBy()}. {@link #dismiss} turns a crew member into a
 * neutral sailor for its hirer or its ship's owner (anyone on an ownerless ship).
 *
 * <pre>{@code
 * List<Candidate> today = Hiring.candidates(server, port);
 * Hiring.Result r = Hiring.hire(player, port.id(), today.get(0).id());
 * Hiring.Result d = Hiring.dismiss(player, r.crew());
 * }</pre>
 */
public final class Hiring {

    /**
     * The answer to a hire or dismissal: done or not, a result id ({@link HiringText#result}), the name it is about
     * (empty when unknown) and, after a hire, the new crew member.
     */
    public record Result(boolean done, String key, String name, @Nullable CrewMember crew) {
        static Result fail(String key, String name) {
            return new Result(false, key, name, null);
        }

        public Component message() {
            return Component.translatable(HiringText.result(key), name);
        }
    }

    private Hiring() {
    }

    public static boolean enabled() {
        return HiringConfig.enabled();
    }

    // --- Candidates ---------------------------------------------------------------------------------------------

    /** Today's candidates still looking for a berth at {@code port}, made first on its first look today. */
    public static List<Candidate> candidates(MinecraftServer server, Port port) {
        HiringData data = HiringData.get(server);
        long day = TradeService.day(server);
        Optional<HiringData.Board> board = data.board(port.id());
        if (board.isPresent() && board.get().day() == day) return board.get().candidates();
        CandidateKind kind = CandidateKind.at(port.kind());
        List<Candidate> fresh = HiringRules.candidates(kind, HiringRules.seed(port.id().toString(), day),
                HiringConfig.CANDIDATES_PER_PORT.get(), HiringConfig.fee(kind));
        data.setBoard(port.id(), new HiringData.Board(day, fresh));
        return fresh;
    }

    /** What the rules need to know about {@code player}. */
    public static HiringRules.Standing standing(Player player) {
        return new HiringRules.Standing(Reputation.villagersRefuse(player), Reputation.piratesFriendly(player),
                Careers.infamy(player), Careers.navyRank(player), Careers.enabled(), Reputation.enabled());
    }

    /** Whether {@code player} may hire at a port of {@code port}'s kind. */
    public static HiringRules.Verdict verdict(Player player, Port port) {
        return HiringRules.eligible(port.kind(), CandidateKind.at(port.kind()), standing(player), HiringConfig.settings());
    }

    public static String key(HiringRules.Verdict v) {
        return switch (v) {
            case OK -> HiringText.HIRED;
            case WRONG_PORT -> HiringText.WRONG_PORT;
            case VILLAGERS_REFUSE -> HiringText.VILLAGERS_REFUSE;
            case PIRATES_DISTRUST -> HiringText.PIRATES_DISTRUST;
            case NOT_ENLISTED -> HiringText.NOT_ENLISTED;
        };
    }

    public static Optional<Port> port(MinecraftServer server, ResourceLocation id) {
        return PortRegistry.get(server).index().byId(id);
    }

    // --- Hiring -------------------------------------------------------------------------------------------------

    /** Hires today's candidate {@code candidateId} of {@code portId} for {@code player} (the Crew tab). */
    public static Result hire(ServerPlayer player, ResourceLocation portId, UUID candidateId) {
        if (!enabled()) return Result.fail(HiringText.DISABLED, "");
        MinecraftServer server = player.server;
        Optional<Port> port = port(server, portId);
        if (port.isEmpty()) return Result.fail(HiringText.NO_PORT, "");
        Optional<Candidate> candidate = candidates(server, port.get()).stream().filter(c -> c.id().equals(candidateId)).findFirst();
        if (candidate.isEmpty()) return Result.fail(HiringText.UNKNOWN, "");
        Candidate c = candidate.get();
        HiringRules.Verdict v = HiringRules.eligible(port.get().kind(), c.kind(), standing(player), HiringConfig.settings());
        if (!v.ok()) return Result.fail(key(v), c.name());
        Result r = place(player, port.get(), c, true);
        if (r.done()) HiringData.get(server).remove(portId, c.id());
        return r;
    }

    /**
     * Operators ({@code /pirates crew hire}): a new candidate of {@code kind} signs on for {@code player} at
     * {@code port} without a fee and without the standing check; the ship and bunk checks still apply.
     */
    public static Result hireNew(ServerPlayer player, Port port, CandidateKind kind) {
        if (!enabled()) return Result.fail(HiringText.DISABLED, "");
        Candidate c = HiringRules.candidates(kind, player.getRandom().nextLong(), 1, 0).get(0);
        return place(player, port, c, false);
    }

    /** The ship, bunk, fee and deck-spot checks of a hire, then the recruit goes aboard. */
    private static Result place(ServerPlayer player, Port port, Candidate c, boolean charge) {
        ServerLevel level = player.server.getLevel(port.dimension());
        if (level == null) return Result.fail(HiringText.NO_PORT, c.name());
        Optional<ShipBody> ship = ShipsAtPort.nearest(level, player.getUUID(), port, HiringConfig.SHIP_RADIUS.get(), player.position());
        if (ship.isEmpty()) return Result.fail(HiringText.NO_SHIP, c.name());
        ShipBunks.Count count = ShipBunks.count(level, ship.get());
        if (!HiringRules.hasRoom(count.crew(), count.bunks(), HiringConfig.REQUIRE_BUNKS.get(), HiringConfig.MAX_WITHOUT_BUNKS.get())) {
            return Result.fail(HiringText.NO_BUNK, c.name());
        }
        if (charge && !Wallet.has(player, c.fee())) return Result.fail(HiringText.NO_COINS, c.name());
        Optional<Vec3> spot = HireSpots.nearest(level, ship.get(), player.position());
        if (spot.isEmpty()) return Result.fail(HiringText.NO_ROOM, c.name());
        if (charge && c.fee() > 0 && !Wallet.take(player, c.fee())) return Result.fail(HiringText.NO_COINS, c.name());
        CrewMember crew = StationContent.CREW_MEMBER.get().create(level);
        if (crew == null) return Result.fail(HiringText.NO_ROOM, c.name());
        Vec3 at = spot.get();
        Vec3 look = player.position().subtract(at);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0f;
        crew.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        crew.setYHeadRot(yaw);
        crew.setYBodyRot(yaw);
        crew.setCustomName(Component.literal(c.name()));
        crew.setHiredBy(Optional.of(player.getUUID()));
        // morale: a new crew member's stored morale is unset and reads as crew.morale.start (CrewMorale)
        level.addFreshEntity(crew);
        if (charge && c.fee() > 0) player.containerMenu.broadcastChanges();
        return new Result(true, HiringText.HIRED, c.name(), crew);
    }

    // --- Dismissal ----------------------------------------------------------------------------------------------

    /**
     * {@code player} dismisses {@code crew} (sneak-use with the whistle): its hirer, its ship's owner, or anyone when
     * its ship has no owner. The crew member becomes a neutral sailor where it stands
     * ({@link CrewReplacement#replace}).
     */
    public static Result dismiss(Player player, CrewMember crew) {
        String name = crew.getDisplayName().getString();
        if (!enabled()) return Result.fail(HiringText.DISABLED, name);
        if (!(crew.level() instanceof ServerLevel level)) return Result.fail(HiringText.NOT_CREW, name);
        Optional<UUID> owner = shipOwner(level, crew);
        if (!HiringRules.mayDismiss(player.getUUID(), crew.hiredBy(), owner)) return Result.fail(HiringText.NOT_YOURS, name);
        CrewReplacement.replace(level, crew, MobContent.SAILOR.get());
        return new Result(true, HiringText.DISMISSED, name, null);
    }

    /** Operators ({@code /pirates crew dismiss}): dismisses {@code crew} whoever hired it. */
    public static Result forceDismiss(CrewMember crew) {
        String name = crew.getDisplayName().getString();
        if (!(crew.level() instanceof ServerLevel level)) return Result.fail(HiringText.NOT_CREW, name);
        CrewReplacement.replace(level, crew, MobContent.SAILOR.get());
        return new Result(true, HiringText.DISMISSED, name, null);
    }

    /** The owner of the ship the crew member belongs to ({@link ShipBunks#shipIdOf}); empty for none. */
    public static Optional<UUID> shipOwner(ServerLevel level, CrewMember crew) {
        UUID ship = ShipBunks.shipIdOf(level, crew);
        if (ship == null) return Optional.empty();
        return ShipRegistry.get(level.getServer()).find(ship).flatMap(ShipData::owner);
    }
}
