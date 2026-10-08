package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the officer's career screen (docs/design.md §15, CAR1): opens it ({@link #open}, from
 * {@link CareerInteractions}) and answers the screen's {@link CareerPayloads.Action}s after re-checking the officer
 * ({@link #officerFor}): a living navy officer in the player's level within {@code careers.officer_reach} that is not
 * hostile to the player. Every answer is a fresh {@link CareerPayloads.View} with the action's result.
 */
public final class CareerBackend {

    /** The officer whose screen each player opened last (entity id), for tests and logging. */
    private static final Map<UUID, Integer> OPENED = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CustomPacketPayload>> RECORDINGS = new ConcurrentHashMap<>();

    private CareerBackend() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(CareerPayloads.Action.TYPE, CareerPayloads.Action.CODEC,
                (p, player) -> handle((ServerPlayer) player, p));
        Services.NETWORK.registerToClient(CareerPayloads.View.TYPE, CareerPayloads.View.CODEC, (p, player) -> ClientCareer.accept(p));
        Services.NETWORK.registerToClient(CareerSyncPayload.TYPE, CareerSyncPayload.CODEC, (p, player) -> ClientCareer.accept(p));
    }

    /** Whether {@code officer} refuses {@code player}: it attacks the player on sight or holds a grudge. */
    public static boolean hostile(NavyOfficer officer, Player player) {
        return officer.attacksOnSight(player) || officer.hasGrudge(player);
    }

    /** Opens the career screen at {@code officer} for {@code player}. */
    public static void open(ServerPlayer player, NavyOfficer officer) {
        OPENED.put(player.getUUID(), officer.getId());
        deliver(player, view(player, officer.getId(), true, Optional.empty()));
    }

    /** The officer with entity id {@code id} if the player may deal with it now, else empty. */
    public static Optional<NavyOfficer> officerFor(ServerPlayer player, int id) {
        Entity e = player.serverLevel().getEntity(id);
        if (!(e instanceof NavyOfficer officer) || !officer.isAlive()) return Optional.empty();
        double reach = CareerConfig.OFFICER_REACH.get();
        if (officer.distanceToSqr(player) > reach * reach) return Optional.empty();
        return Optional.of(officer);
    }

    /** Handles a screen action; returns the result sent back (tests). */
    public static CareerPayloads.Result handle(ServerPlayer player, CareerPayloads.Action action) {
        Optional<NavyOfficer> officer = officerFor(player, action.officer());
        CareerPayloads.Result result;
        if (officer.isEmpty()) {
            result = CareerPayloads.Result.of(false, CareerText.OFFICER_GONE);
        } else if (hostile(officer.get(), player)) {
            result = CareerPayloads.Result.of(false, CareerText.HOSTILE);
        } else if (!Careers.enabled()) {
            result = CareerPayloads.Result.of(false, CareerText.DISABLED);
        } else {
            result = perform(player, action.kind());
        }
        deliver(player, view(player, action.officer(), false, Optional.of(result)));
        return result;
    }

    private static CareerPayloads.Result perform(ServerPlayer player, CareerPayloads.Kind kind) {
        CareerThresholds t = CareerConfig.thresholds();
        return switch (kind) {
            case ENLIST -> {
                CareerRules.EnlistVerdict v = Careers.enlist(player);
                yield v == CareerRules.EnlistVerdict.OK
                        ? CareerPayloads.Result.of(true, CareerText.ENLISTED, Careers.record(player).navy().nameKey())
                        : CareerPayloads.Result.of(false, CareerText.enlistVerdict(v), t.step(NavyRank.MIDSHIPMAN).minNavyRep());
            }
            case RESIGN -> Careers.resign(player)
                    ? CareerPayloads.Result.of(true, CareerText.RESIGNED)
                    : CareerPayloads.Result.of(false, CareerText.NOT_ENLISTED);
            case REQUEST_LETTER -> {
                CareerRules.LetterVerdict v = Careers.grantLetter(player);
                Object arg = switch (v) {
                    case BLOCKED -> CareerRules.blockedDays(Careers.record(player), Careers.now(player));
                    case LOW_STANDING -> t.letter().minNavyRep();
                    case OK, TOO_POOR -> t.letter().fee();
                    default -> "";
                };
                yield v == CareerRules.LetterVerdict.OK
                        ? CareerPayloads.Result.of(true, CareerText.LETTER_GRANTED, arg)
                        : CareerPayloads.Result.of(false, CareerText.letterVerdict(v), arg);
            }
            case COLLECT_PRIZE -> {
                long paid = Careers.collectPrize(player);
                yield paid > 0 ? CareerPayloads.Result.of(true, CareerText.PRIZE_COLLECTED, paid)
                        : CareerPayloads.Result.of(false, CareerText.PRIZE_NONE);
            }
        };
    }

    /** The screen's content for {@code player}. */
    public static CareerPayloads.View view(ServerPlayer player, int officer, boolean open, Optional<CareerPayloads.Result> result) {
        CareerThresholds t = CareerConfig.thresholds();
        CareerRecord r = Careers.record(player);
        int navyRep = Reputation.get(player, Faction.NAVY);
        int pirateRep = Reputation.get(player, Faction.PIRATES);
        long now = Careers.now(player);
        CareerPayloads.Status status = new CareerPayloads.Status(r.navy(), r.enlisted(), r.infamy(), r.letter(),
                r.letter() == LetterState.VOIDED ? CareerRules.blockedDays(r, now) : 0L, r.prizeMoney());

        // The navy ladder: in service the next rank, outside it what enlisting needs (closed to a pirate)
        Optional<NavyRank> nextNavy = r.enlisted() ? r.navy().next()
                : r.infamy() == InfamyRank.DECKHAND ? Optional.of(NavyRank.MIDSHIPMAN) : Optional.empty();
        CareerPayloads.Ladder navy = ladder(nextNavy.map(NavyRank::nameKey),
                nextNavy.map(n -> CareerRules.requirements(n, r, navyRep, t)).orElse(List.of()));
        Optional<InfamyRank> nextInfamy = r.enlisted() ? Optional.empty() : r.infamy().next();
        CareerPayloads.Ladder infamy = ladder(nextInfamy.map(InfamyRank::nameKey),
                nextInfamy.map(n -> CareerRules.requirements(n, r, pirateRep, t)).orElse(List.of()));

        return new CareerPayloads.View(officer, open, Careers.enabled(), status, navyRep, pirateRep, navy, infamy,
                Careers.enlistVerdict(player), Careers.letterVerdict(player), t.letter().fee(), result);
    }

    private static CareerPayloads.Ladder ladder(Optional<String> next, List<CareerRules.Requirement> reqs) {
        return new CareerPayloads.Ladder(next, reqs.stream().map(CareerPayloads.Req::of).toList());
    }

    // ------------------------------------------------------------------ delivery and test support

    /** Sends to the player, or records for a GameTest ({@link #record}); dropped for a player not online. */
    public static void deliver(ServerPlayer player, CustomPacketPayload payload) {
        List<CustomPacketPayload> rec = RECORDINGS.get(player.getUUID());
        if (rec != null) {
            rec.add(payload);
            return;
        }
        if (CareerSync.online(player)) Services.NETWORK.sendToPlayer(player, payload);
    }

    /** GameTests: every career screen payload for {@code player} goes into the returned list from now on. */
    public static List<CustomPacketPayload> record(UUID player) {
        return RECORDINGS.computeIfAbsent(player, id -> Collections.synchronizedList(new ArrayList<>()));
    }

    public static void stopRecording(UUID player) {
        RECORDINGS.remove(player);
    }

    /** The officer whose screen {@code player} opened last, if any. */
    public static Optional<Integer> opened(UUID player) {
        return Optional.ofNullable(OPENED.get(player));
    }

    public static void onLogout(@Nullable ServerPlayer player) {
        if (player != null) OPENED.remove(player.getUUID());
    }

    public static void clear() {
        OPENED.clear();
        RECORDINGS.clear();
    }
}
