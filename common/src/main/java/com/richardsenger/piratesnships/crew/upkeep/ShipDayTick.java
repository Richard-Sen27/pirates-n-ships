package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.galley.ProvisionContainer;
import com.richardsenger.piratesnships.crew.galley.ShipProvisions;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.hammock.RestRules;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.morale.CrewMorale;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.ProvisionEffects;
import com.richardsenger.piratesnships.crew.provisions.ProvisionOutcome;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The ship day tick of crew upkeep (CR2, docs/design.md §7.3, §7.4): once per level at dawn (the night of
 * {@link CrewRest#isNight} ends), for every loaded ship with crew aboard, in this order:
 * <ol>
 *   <li><b>Provisions</b>: one day ({@link RestRules#DAY} ticks) of {@link ShipProvisions#advance} over the pantries and
 *       water barrels in the ship's plot, for the crew plus the prisoners aboard (they eat {@code prisoner_share}). The
 *       morale change goes to every member through {@link CrewMorale#adjust} ("provisions"); the work-speed factor is
 *       kept for {@code station.Stations} until the next dawn; scurvy gives weakness for a day to the crew and, with
 *       {@code provisions.scurvy_affects_players}, to the owner aboard.</li>
 *   <li><b>Wages</b> ({@link WageRules}): from doubloons in the ship's cargo crates and barrels and any other container
 *       aboard except provisions, nearest the helm first; then (CRW2, {@code crew.wages.from_wallet}) from the doubloons
 *       the owner carries while online in this level ({@link PayKey}).</li>
 *   <li><b>Mutiny, then desertion</b> ({@link UpkeepDay}). Since CRW2 a deserter is only marked
 *       ({@link CrewMember#isDeserting()}, {@link DesertionRules}) and walks off at a port ({@link Desertions}); with
 *       {@code crew.desertion.at_port_only} off it leaves at once as before.</li>
 * </ol>
 * HM1's hammock rule settles each member's night afterwards: {@link #observe} runs at the start of every crew member's
 * tick (before {@link CrewRest#dawn}) and at the end of the level tick, so the first one to notice the dawn runs the day
 * tick for the whole level.
 * <p>
 * One day tick is always one day of upkeep, however long the level's clock jumped (a night slept through, a
 * {@code /time set}); ships that are not loaded at dawn have no day tick. The owner aboard gets one action-bar line
 * ("The crew has no water · Paid 2 crew, 4 doubloons"); desertions and mutiny go to the owner's chat wherever it is.
 */
public final class ShipDayTick {

    /** One day of upkeep, in ticks. */
    public static final long DAY = RestRules.DAY;

    /** Whether the level was night when last looked at, to see the dawn once. */
    private static final Map<ResourceKey<Level>, Boolean> NIGHT = new ConcurrentHashMap<>();
    /** The last day report of each ship (server memory; for tests and debugging). */
    private static final Map<UUID, DayReport> LAST = new ConcurrentHashMap<>();

    private ShipDayTick() {
    }

    /**
     * What one day tick did on one ship.
     *
     * @param provisions  the provisions outcome of the day
     * @param moraleDelta the provisions' morale change per member
     * @param day         the settled crew day (pay, desertion, mutiny)
     * @param deserters   the members that left the ship at this dawn
     * @param deserting   the members marked as deserting at this dawn (CRW2; they leave at a port)
     * @param ownerLines  everything the owner was told (action-bar lines first, then chat lines)
     */
    public record DayReport(ProvisionOutcome provisions, int moraleDelta, UpkeepDay.Result day, List<UUID> deserters,
                            List<UUID> deserting, List<Component> ownerLines) {
        public DayReport {
            deserters = List.copyOf(deserters);
            deserting = List.copyOf(deserting);
            ownerLines = List.copyOf(ownerLines);
        }

        public ProvisionEffects effects() {
            return provisions.effects();
        }
    }

    // ------------------------------------------------------------------ the clock

    /** Looks at the level's clock; runs {@link #dawn} once when a night has just ended. */
    public static void observe(ServerLevel level) {
        boolean night = CrewRest.isNight(level);
        Boolean before = NIGHT.put(level.dimension(), night);
        if (!night && Boolean.TRUE.equals(before)) {
            dawn(level);
        }
    }

    public static void onServerStopped() {
        NIGHT.clear();
        LAST.clear();
    }

    /** The last day report of {@code ship} since the server started, or null. */
    public static @Nullable DayReport lastReport(UUID ship) {
        return LAST.get(ship);
    }

    /** Dawn in {@code level}: the day tick of every loaded ship with crew aboard. Public for the GameTests. */
    public static void dawn(ServerLevel level) {
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        ShipUpkeepData.get(level.getServer()).removeIf(id -> registry.find(id).isEmpty());
        for (ShipBody ship : List.copyOf(SableShips.all(level))) {
            List<CrewMember> crew = ShipBunks.crewOf(level, ship);
            if (!crew.isEmpty()) {
                LAST.put(ship.id(), run(level, ship, crew));
            }
        }
    }

    // ------------------------------------------------------------------ one ship

    /** The ship's containers: provisions and coin sources (plot positions). */
    private record Scan(List<BlockPos> provisions, List<WageRules.Source<BlockPos>> coins) { }

    /** Runs the day tick of one ship with {@code crew} aboard. */
    public static DayReport run(ServerLevel level, ShipBody ship, List<CrewMember> crew) {
        return run(level, ship, crew, payer(level, ship));
    }

    /**
     * The player whose purse pays what the coins aboard cannot (CRW2): the ship's owner while it is a player in
     * {@code level} (online in this dimension); null when there is none.
     */
    public static @Nullable ServerPlayer payer(ServerLevel level, ShipBody ship) {
        return ShipRegistry.get(level.getServer()).find(ship.id()).flatMap(ShipData::owner).map(level::getPlayerByUUID)
                .filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast).orElse(null);
    }

    /**
     * Runs the day tick of one ship with {@code crew} aboard; {@code payer} ({@link #payer}) pays the rest of the wages
     * from its purse while {@code crew.wages.from_wallet} is on. Public for the GameTests, which cannot put a player
     * into a level (Sable sends it packets a mock connection refuses).
     */
    public static DayReport run(ServerLevel level, ShipBody ship, List<CrewMember> crew, @Nullable ServerPlayer payer) {
        ShipUpkeepData data = ShipUpkeepData.get(level.getServer());
        ShipUpkeep before = data.get(ship.id());
        UpkeepSettings s = Upkeep.settings();
        Scan scan = scan(level, ship);

        // 1. provisions
        CrewHeadcount head = CrewHeadcount.crew(crew.size()).withPrisoners(prisoners(level, ship));
        ShipProvisions.Result pr = ShipProvisions.advance(level, scan.provisions(), before.provisioning(), head, DAY);
        ProvisionOutcome outcome = pr.update().outcome();
        ProvisionEffects fx = outcome.effects();
        int provisionsDelta = UpkeepDay.provisionsDelta(fx);

        Optional<ShipData> record = ShipRegistry.get(level.getServer()).find(ship.id());
        @Nullable ServerPlayer owner = record.flatMap(ShipData::owner)
                .map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
        boolean ownerAboard = owner != null && owner.serverLevel() == level && isAboard(level, ship, owner);

        // 2. wages (the owner's wallet last, CRW2), 3. mutiny and desertion (pure)
        if (!s.wagesEnabled() || !CrewConfig.WAGES_FROM_WALLET.get()) {
            payer = null;
        }
        List<WageRules.Source<PayKey>> sources = PayKey.sources(scan.coins(), payer != null ? payer.getUUID() : null,
                payer != null ? Wallet.count(payer) : 0);
        WageRules.Payment<PayKey> payment = s.wagesEnabled()
                ? WageRules.pay(crew.size(), s.wagePerDay(), sources)
                : WageRules.Payment.none();
        List<UpkeepDay.Member> members = new ArrayList<>();
        for (CrewMember c : crew) {
            members.add(new UpkeepDay.Member(c.getUUID(), c.storedMorale(), c.lowMoraleDays()));
        }
        UpkeepDay.Result day = UpkeepDay.settle(CrewMorale.settings(), s, members, provisionsDelta, payment.paid(),
                before.lowMoraleDays());

        // apply to the crew
        for (int i = 0; i < crew.size(); i++) {
            CrewMember c = crew.get(i);
            UpkeepDay.MemberResult r = day.members().get(i);
            if (r.provisionsDelta() != 0) CrewMorale.adjust(c, r.provisionsDelta(), "provisions");
            if (r.wageDelta() != 0) CrewMorale.adjust(c, r.wageDelta(), r.pay() == UpkeepDay.Pay.PAID ? "wages paid" : "wages unpaid");
            c.setUnpaid(r.pay() == UpkeepDay.Pay.UNPAID);
            c.setLowMoraleDays(r.lowDays());
            if (fx.scurvy()) scurvy(c);
            if (r.pay() == UpkeepDay.Pay.UNPAID && !r.deserts() && !day.mutiny()) {
                CrewStations.say(level, c, Component.translatable(UpkeepText.SAY_UNPAID));
            }
        }
        ShipCoins.take(level, PayKey.containers(payment));
        long fromWallet = PayKey.walletCoins(payment);
        if (fromWallet > 0 && !Wallet.take(payer, fromWallet)) {
            fromWallet = 0; // counted in this same tick, so this cannot happen; never book coins nobody gave
        }
        if (fx.scurvy() && ownerAboard && ProvisionsConfig.SCURVY_AFFECTS_PLAYERS.get()) {
            scurvy(owner);
        }

        // owner lines: provisions and pay on the action bar
        List<Component> bar = new ArrayList<>();
        if (outcome.hungry()) bar.add(Component.translatable(UpkeepText.NO_FOOD));
        if (outcome.thirsty()) bar.add(Component.translatable(UpkeepText.NO_WATER));
        if (fx.scurvy()) bar.add(Component.translatable(UpkeepText.SCURVY));
        if (s.wagesEnabled()) {
            bar.add(day.unpaid() > 0
                    ? Component.translatable(UpkeepText.UNPAID, day.unpaid())
                    : fromWallet > 0
                    ? Component.translatable(UpkeepText.PAID_WALLET, day.paid(), payment.coinsTaken(), fromWallet)
                    : Component.translatable(UpkeepText.PAID, day.paid(), payment.coinsTaken()));
        }
        List<Component> chat = new ArrayList<>();
        if (fromWallet > 0 && !ownerAboard) {
            chat.add(Component.translatable(UpkeepText.WALLET_PAID, fromWallet));
        }

        // 3. mutiny or desertion
        List<UUID> deserters = new ArrayList<>();
        List<UUID> marked = new ArrayList<>();
        DesertionRules.Settings ds = Desertions.settings();
        if (day.mutiny()) {
            Component name = shipName(record);
            for (CrewMember c : crew) {
                CrewStations.say(level, c, Component.translatable(UpkeepText.SAY_MUTINY));
                CrewReplacement.replace(level, c, MobContent.PIRATE.get());
            }
            record.ifPresent(d -> ShipRegistry.get(level.getServer()).put(d.withOwner(Optional.empty())));
            chat.add(Component.translatable(UpkeepText.MUTINY, name));
        } else {
            for (int i = 0; i < crew.size(); i++) {
                UpkeepDay.MemberResult r = day.members().get(i);
                CrewMember c = crew.get(i);
                Component name = c.getDisplayName();
                if (r.deserts() && !c.isDeserting() && DesertionRules.leavesAtOnce(ds)) {
                    CrewStations.say(level, c, Component.translatable(UpkeepText.SAY_DESERT));
                    deserters.add(c.getUUID());
                    CrewReplacement.replace(level, c, MobContent.SAILOR.get());
                    chat.add(Component.translatable(UpkeepText.DESERTED, name));
                    continue;
                }
                DesertionRules.Mark was = new DesertionRules.Mark(c.isDeserting(), c.desertingDays());
                DesertionRules.Mark now = DesertionRules.afterDawn(was, r.deserts(), r.lowDays());
                c.setDeserting(now.deserting(), now.days());
                if (now.deserting() && !was.deserting()) {
                    CrewStations.say(level, c, Component.translatable(UpkeepText.SAY_DESERTING));
                    marked.add(c.getUUID());
                    chat.add(Component.translatable(UpkeepText.DESERTING, name));
                } else if (was.deserting() && !now.deserting()) {
                    chat.add(Component.translatable(UpkeepText.STAYS, name));
                }
            }
        }

        if (owner != null) {
            if (ownerAboard && !bar.isEmpty()) owner.displayClientMessage(join(bar), true);
            for (Component line : chat) owner.sendSystemMessage(line);
        }

        data.put(ship.id(), new ShipUpkeep(pr.state(), fx.workSpeedMultiplier(), fx.scurvy(), outcome.hungry(), outcome.thirsty(),
                day.shipLowDays(), s.wagesEnabled()
                        ? new ShipUpkeep.PayRecord(true, day.paid(), day.unpaid(), payment.coinsTaken(), fromWallet)
                        : ShipUpkeep.PayRecord.NONE));
        List<Component> lines = new ArrayList<>(bar);
        lines.addAll(chat);
        return new DayReport(outcome, provisionsDelta, day, deserters, marked, lines);
    }

    /** Joins the action-bar lines into one. */
    private static Component join(List<Component> lines) {
        Component out = lines.get(0);
        for (int i = 1; i < lines.size(); i++) {
            out = Component.translatable(UpkeepText.JOIN, out, lines.get(i));
        }
        return out;
    }

    private static Component shipName(Optional<ShipData> record) {
        return record.map(ShipData::name).filter(n -> !n.isBlank())
                .<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable(UpkeepText.UNNAMED_SHIP));
    }

    /** Weakness until the next dawn. */
    private static void scurvy(LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) DAY, 0, false, true));
    }

    private static boolean isAboard(ServerLevel level, ShipBody ship, ServerPlayer player) {
        ShipBody on = CaptainsWhistleItem.shipOf(level, player);
        return on != null && on.id().equals(ship.id());
    }

    /** Prisoners held on {@code ship}: they eat {@code prisoner_share}. */
    private static int prisoners(ServerLevel level, ShipBody ship) {
        int n = 0;
        for (LivingEntity p : BrigService.prisonersWithin(level, CrewStations.worldBox(ship, 0))) {
            ShipBody on = CaptainsWhistleItem.shipOf(level, p);
            if (on != null && on.id().equals(ship.id())) n++;
        }
        return n;
    }

    /** The ship's containers: its pantries and water barrels, and its coin sources ({@link ShipCoins#scan}). */
    private static Scan scan(ServerLevel level, ShipBody ship) {
        List<BlockPos> provisions = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockEntity(p) instanceof ProvisionContainer) provisions.add(p.immutable());
        }
        return new Scan(provisions, ShipCoins.scan(level, ship));
    }
}
