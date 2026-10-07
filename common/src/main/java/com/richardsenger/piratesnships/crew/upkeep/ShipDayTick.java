package com.richardsenger.piratesnships.crew.upkeep;

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
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
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
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
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
 *       aboard except provisions, nearest the helm first.</li>
 *   <li><b>Mutiny, then desertion</b> ({@link UpkeepDay}).</li>
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
     * @param deserters   the deserted members
     * @param ownerLines  everything the owner was told (action-bar lines first, then chat lines)
     */
    public record DayReport(ProvisionOutcome provisions, int moraleDelta, UpkeepDay.Result day, List<UUID> deserters,
                            List<Component> ownerLines) {
        public DayReport {
            deserters = List.copyOf(deserters);
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

        // 2. wages, 3. mutiny and desertion (pure)
        WageRules.Payment<BlockPos> payment = s.wagesEnabled()
                ? WageRules.pay(crew.size(), s.wagePerDay(), scan.coins())
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
        takeCoins(level, payment);

        Optional<ShipData> record = ShipRegistry.get(level.getServer()).find(ship.id());
        @Nullable ServerPlayer owner = record.flatMap(ShipData::owner)
                .map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
        boolean ownerAboard = owner != null && owner.serverLevel() == level && isAboard(level, ship, owner);
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
                    : Component.translatable(UpkeepText.PAID, day.paid(), payment.coinsTaken()));
        }
        List<Component> chat = new ArrayList<>();

        // 3. mutiny or desertion
        List<UUID> deserters = new ArrayList<>();
        if (day.mutiny()) {
            Component name = shipName(record);
            for (CrewMember c : crew) {
                CrewStations.say(level, c, Component.translatable(UpkeepText.SAY_MUTINY));
                replace(level, c, MobContent.PIRATE.get());
            }
            record.ifPresent(d -> ShipRegistry.get(level.getServer()).put(d.withOwner(Optional.empty())));
            chat.add(Component.translatable(UpkeepText.MUTINY, name));
        } else {
            for (int i = 0; i < crew.size(); i++) {
                if (!day.members().get(i).deserts()) continue;
                CrewMember c = crew.get(i);
                Component name = c.getDisplayName();
                CrewStations.say(level, c, Component.translatable(UpkeepText.SAY_DESERT));
                deserters.add(c.getUUID());
                replace(level, c, MobContent.SAILOR.get());
                chat.add(Component.translatable(UpkeepText.DESERTED, name));
            }
        }

        if (owner != null) {
            if (ownerAboard && !bar.isEmpty()) owner.displayClientMessage(join(bar), true);
            for (Component line : chat) owner.sendSystemMessage(line);
        }

        data.put(ship.id(), new ShipUpkeep(pr.state(), fx.workSpeedMultiplier(), fx.scurvy(), outcome.hungry(), outcome.thirsty(),
                day.shipLowDays(), s.wagesEnabled()
                        ? new ShipUpkeep.PayRecord(true, day.paid(), day.unpaid(), payment.coinsTaken())
                        : ShipUpkeep.PayRecord.NONE));
        List<Component> lines = new ArrayList<>(bar);
        lines.addAll(chat);
        return new DayReport(outcome, provisionsDelta, day, deserters, lines);
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

    /**
     * The ship's pantries and water barrels, and its coin sources with their distance to the helm (to the plot
     * center without a helm): cargo crates and barrels holding doubloons, and every other container block (chests,
     * barrels, ...) that is not a provisions container.
     */
    private static Scan scan(ServerLevel level, ShipBody ship) {
        List<BlockPos> blocks = ship.plotBlocks();
        Vec3 helm = null;
        for (BlockPos p : blocks) {
            if (level.getBlockState(p).is(AssemblyContent.HELM.get())) {
                helm = Vec3.atCenterOf(p);
                break;
            }
        }
        if (helm == null) {
            BlockPos[] b = ship.plotBounds();
            helm = Vec3.atLowerCornerOf(b[0]).add(Vec3.atLowerCornerOf(b[1]).add(1, 1, 1)).scale(0.5);
        }
        List<BlockPos> provisions = new ArrayList<>();
        List<WageRules.Source<BlockPos>> coins = new ArrayList<>();
        for (BlockPos p : blocks) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            if (be instanceof ProvisionContainer) {
                provisions.add(p.immutable());
                continue;
            }
            long n = coinsIn(be);
            if (n > 0) coins.add(new WageRules.Source<>(p.immutable(), Vec3.atCenterOf(p).distanceTo(helm), n));
        }
        return new Scan(provisions, coins);
    }

    /** Doubloons in a block entity that may pay wages (never a provisions container). */
    private static long coinsIn(BlockEntity be) {
        if (be instanceof ProvisionContainer) return 0;
        if (be instanceof CargoContainerBlockEntity cargo) {
            return Wallet.isCoin(cargo.heldKind()) ? cargo.count() : 0;
        }
        return be instanceof Container c ? Wallet.count(c) : 0;
    }

    private static void takeCoins(ServerLevel level, WageRules.Payment<BlockPos> payment) {
        for (Map.Entry<BlockPos, Long> e : payment.takes().entrySet()) {
            BlockEntity be = level.getBlockEntity(e.getKey());
            int n = (int) Math.min(Integer.MAX_VALUE, e.getValue());
            if (be instanceof CargoContainerBlockEntity cargo) {
                cargo.extract(n);
            } else if (be instanceof Container c) {
                Wallet.take(c, n);
            }
        }
    }

    /**
     * Replaces a crew member by a {@code type} mob at its place (desertion: a neutral sailor; mutiny: a hostile pirate):
     * released from its station and hammock, name and AI flag carried over, then removed. The mob's own look (GL1's
     * seafarer rig) replaces the crew look.
     */
    static @Nullable SeafarerMob replace(ServerLevel level, CrewMember crew, EntityType<? extends SeafarerMob> type) {
        if (crew.assignment() != null) CrewStations.release(level, crew);
        CrewRest.getUp(crew, false);
        crew.stopRiding();
        SeafarerMob mob = type.create(level);
        if (mob != null) {
            mob.moveTo(crew.getX(), crew.getY(), crew.getZ(), crew.getYRot(), crew.getXRot());
            mob.setYHeadRot(crew.getYHeadRot());
            if (crew.hasCustomName()) mob.setCustomName(crew.getCustomName());
            mob.setNoAi(crew.isNoAi());
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
        crew.discard();
        return mob;
    }
}
