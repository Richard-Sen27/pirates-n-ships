package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.TradeRandom;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic quest offers of one port and day (docs/design.md §15, QST1), without world access. The types come
 * from the port kind's list ({@link QuestParams#types}), filtered by {@link QuestType#allowedAt}, by what the world can
 * back (a delivery needs a {@link DeliveryOption}, a treasure hunt a {@link TreasureOption} and
 * {@code treasure_quest_enabled}) and by {@code deedsTracked}; the offers of one day differ in type while the pool
 * allows. Amounts and rewards:
 * <ul>
 *   <li>hunts: {@code huntMin..huntMax} kills × {@code perPirate}/{@code perNavy};</li>
 *   <li>turn-ins: {@code 1..turnInMax} prisoners × {@code perPrisoner};</li>
 *   <li>monsters: the kraken ({@code krakenChance}, always in a cold climate where sharks don't swim) for
 *       {@code kraken}, else {@code monsterMin..monsterMax} sharks × {@code perShark};</li>
 *   <li>deliveries: the option's contract reward × {@code deliverMultiplier};</li>
 *   <li>treasure: {@code treasure};</li>
 *   <li>captain hunts (QST1b): the nearest living captain ({@link CaptainOption}) within {@code captainRadius},
 *       {@code captain}; at most one captain hunt among one port's offers;</li>
 *   <li>sea quests (QST2, only while {@code sea.enabled}): an escort to a destination within
 *       {@code escortMaxDistance} for {@code escort + escortPer1000 × distance / 1000}; convoy raids, patrol hunts and
 *       ship hunts of {@code countMin..countMax} ships × {@code perConvoy}/{@code perPatrol}/{@code perShip}, each only
 *       while its prey is at sea ({@link SeaOptions});</li>
 * </ul>
 * every reward × {@code rewardScale}, at least 1. An offer is open from its day through {@code day + offerDays − 1}.
 */
public final class QuestGenerator {

    public static final ResourceLocation SHARK = Constants.id("shark");
    public static final ResourceLocation KRAKEN = Constants.id("kraken");

    /** A delivery the port could hand out (from the trade module's contract generator). */
    public record DeliveryOption(ResourceLocation good, int quantity, ResourceLocation destination, long baseReward) {
    }

    /** An unlooted treasure site a map could lead to. */
    public record TreasureOption(ResourceLocation port, BlockPos site) {
    }

    /**
     * A living pirate captain the port could send a player after (QST1b): his entity id and name, his post's
     * horizontal {@code distance} from the port and the compass {@code bearing} to it ({@link #bearing}).
     */
    public record CaptainOption(UUID id, String name, double distance, String bearing) {
    }

    /** A port an escorted convoy could sail to (QST2), {@code distance} blocks away in a straight line. */
    public record EscortOption(ResourceLocation destination, double distance) {
    }

    /**
     * What the sea can back (QST2): ports a convoy from here could sail to, and whether merchant convoys, navy patrols
     * and ships under the pirates' colours are at sea at all (a hunt is only offered while its prey sails).
     */
    public record SeaOptions(List<EscortOption> escorts, boolean convoys, boolean patrols, boolean pirates) {
        public static final SeaOptions NONE = new SeaOptions(List.of(), false, false, false);

        public SeaOptions {
            escorts = List.copyOf(escorts);
        }
    }

    /**
     * Where and when the offers are made, and what the world can back.
     *
     * @param captains the living captains in the port's dimension (any distance; the generator applies the radius)
     * @param sea      what the sea quests can follow (QST2)
     */
    public record Context(ResourceLocation port, PortKind kind, Climate climate, long day, long seed,
                          List<DeliveryOption> deliveries, List<TreasureOption> treasures, List<CaptainOption> captains, SeaOptions sea) {
        public Context {
            deliveries = List.copyOf(deliveries);
            treasures = List.copyOf(treasures);
            captains = List.copyOf(captains);
            if (sea == null) sea = SeaOptions.NONE;
        }

        public Context(ResourceLocation port, PortKind kind, Climate climate, long day, long seed,
                       List<DeliveryOption> deliveries, List<TreasureOption> treasures, List<CaptainOption> captains) {
            this(port, kind, climate, day, seed, deliveries, treasures, captains, SeaOptions.NONE);
        }

        public Context(ResourceLocation port, PortKind kind, Climate climate, long day, long seed,
                       List<DeliveryOption> deliveries, List<TreasureOption> treasures) {
            this(port, kind, climate, day, seed, deliveries, treasures, List.of());
        }

        /** The same context without captains (the port already offers a captain hunt). */
        public Context withoutCaptains() {
            return new Context(port, kind, climate, day, seed, deliveries, treasures, List.of(), sea);
        }

        /** The same context with {@code options} for the sea quests. */
        public Context withSea(SeaOptions options) {
            return new Context(port, kind, climate, day, seed, deliveries, treasures, captains, options);
        }
    }

    private QuestGenerator() {
    }

    /** The types {@code ctx}'s port can offer now, in the config's order. */
    public static List<QuestType> offerable(Context ctx, QuestParams p) {
        List<QuestType> out = new ArrayList<>();
        for (QuestType t : p.types(ctx.kind())) {
            if (out.contains(t) || !t.available() || !t.allowedAt(ctx.kind())) continue;
            if (t.deedTracked() && !p.deedsTracked()) continue;
            if (t == QuestType.DELIVER && ctx.deliveries().isEmpty()) continue;
            if (t == QuestType.FIND_TREASURE && (!p.treasureEnabled() || ctx.treasures().isEmpty())) continue;
            if (t == QuestType.HUNT_CAPTAIN && quarry(ctx, p).isEmpty()) continue;
            if (t.seaQuest() && !seaBacked(t, ctx, p)) continue;
            out.add(t);
        }
        return out;
    }

    /**
     * {@code count} offers for the port and day of {@code ctx} (fewer only if no type is offerable). A captain hunt is
     * made at most once (the port has one quarry); pass {@link Context#withoutCaptains} if the port already offers one.
     */
    public static List<Quest> offers(Context ctx, int count, QuestParams p) {
        List<QuestType> types = new ArrayList<>(offerable(ctx, p));
        if (types.isEmpty() || count <= 0) return List.of();
        TradeRandom.Stream rng = stream(ctx, "offers");
        List<Quest> out = new ArrayList<>();
        List<QuestType> pool = new ArrayList<>(types);
        while (out.size() < count) {
            if (pool.isEmpty()) {
                if (types.isEmpty()) break;
                pool.addAll(types);
            }
            QuestType t = pool.remove(rng.nextInt(pool.size()));
            if (t == QuestType.HUNT_CAPTAIN) types.remove(t);
            out.add(make(t, ctx, rng, p));
        }
        return List.copyOf(out);
    }

    /**
     * Whether the sea quest {@code type} can be offered (QST2): {@code quests.sea_quests} is on, and an escort has a
     * destination within {@code escortMaxDistance}, a convoy raid convoys, a patrol hunt patrols and a ship hunt
     * pirate ships at sea.
     */
    static boolean seaBacked(QuestType type, Context ctx, QuestParams p) {
        if (!p.sea().enabled()) return false;
        return switch (type) {
            case ESCORT -> !escortDestinations(ctx, p).isEmpty();
            case PLUNDER_CONVOY -> ctx.sea().convoys();
            case HUNT_PATROL -> ctx.sea().patrols();
            case HUNT_SHIP -> ctx.sea().pirates();
            default -> false;
        };
    }

    /** The destinations an escort from {@code ctx}'s port may go to: within {@code escortMaxDistance}, never the port itself. */
    public static List<EscortOption> escortDestinations(Context ctx, QuestParams p) {
        List<EscortOption> out = new ArrayList<>();
        for (EscortOption o : ctx.sea().escorts()) {
            if (o.destination().equals(ctx.port()) || o.distance() > p.sea().escortMaxDistance()) continue;
            out.add(o);
        }
        return out;
    }

    /** The captain a hunt from {@code ctx}'s port goes after: the nearest within {@code captainRadius}, if any. */
    public static Optional<CaptainOption> quarry(Context ctx, QuestParams p) {
        CaptainOption best = null;
        for (CaptainOption c : ctx.captains()) {
            if (c.distance() > p.captainRadius()) continue;
            if (best == null || c.distance() < best.distance()) best = c;
        }
        return Optional.ofNullable(best);
    }

    /**
     * The compass point (eight of them) of a direction {@code dx} east, {@code dz} south: {@code north},
     * {@code north_east}, {@code east}, ... ({@code north} for no offset).
     */
    public static String bearing(double dx, double dz) {
        String[] points = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        if (dx == 0 && dz == 0) return points[0];
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floorMod(Math.round(deg / 45.0), 8L);
        return points[i];
    }

    /** One offer of {@code type} (the operator command); empty if the port can't offer it now. */
    public static Optional<Quest> offer(QuestType type, Context ctx, long salt, QuestParams p) {
        if (!offerable(ctx, p).contains(type)) return Optional.empty();
        return Optional.of(make(type, ctx, stream(ctx, "single:" + salt), p));
    }

    private static TradeRandom.Stream stream(Context ctx, String what) {
        return new TradeRandom.Stream(TradeRandom.mix(TradeRandom.mix(TradeRandom.mix(ctx.seed(), "quests:" + ctx.port()), ctx.day()), what));
    }

    private static int range(TradeRandom.Stream rng, int lo, int hi) {
        int a = Math.max(1, Math.min(lo, hi));
        int b = Math.max(a, hi);
        return a + rng.nextInt(b - a + 1);
    }

    private static Quest make(QuestType type, Context ctx, TradeRandom.Stream rng, QuestParams p) {
        QuestTarget target = QuestTarget.None.INSTANCE;
        int needed;
        double reward;
        switch (type) {
            case HUNT_PIRATES -> {
                needed = range(rng, p.huntMin(), p.huntMax());
                reward = needed * (double) p.perPirate();
            }
            case HUNT_NAVY -> {
                needed = range(rng, p.huntMin(), p.huntMax());
                reward = needed * (double) p.perNavy();
            }
            case TURN_IN -> {
                needed = range(rng, 1, p.turnInMax());
                reward = needed * (double) p.perPrisoner();
            }
            case KILL_MONSTER -> {
                boolean kraken = ctx.climate() == Climate.COLD || rng.nextDouble() < p.krakenChance();
                if (kraken) {
                    target = new QuestTarget.Kill(KRAKEN);
                    needed = 1;
                    reward = p.kraken();
                } else {
                    target = new QuestTarget.Kill(SHARK);
                    needed = range(rng, p.monsterMin(), p.monsterMax());
                    reward = needed * (double) p.perShark();
                }
            }
            case DELIVER -> {
                DeliveryOption o = ctx.deliveries().get(rng.nextInt(ctx.deliveries().size()));
                target = new QuestTarget.Cargo(o.good(), o.quantity(), o.destination(), Optional.empty());
                needed = 1;
                reward = Math.ceil(o.baseReward() * Math.max(0.0, p.deliverMultiplier()));
            }
            case FIND_TREASURE -> {
                TreasureOption o = ctx.treasures().get(rng.nextInt(ctx.treasures().size()));
                target = new QuestTarget.Treasure(o.port(), o.site());
                needed = 1;
                reward = p.treasure();
            }
            case HUNT_CAPTAIN -> {
                CaptainOption c = quarry(ctx, p).orElseThrow(() -> new IllegalArgumentException("No captain in reach of " + ctx.port()));
                target = new QuestTarget.Victim(c.id(), c.name(), Optional.of(c.bearing()));
                needed = 1;
                reward = p.captain();
            }
            case ESCORT -> {
                List<EscortOption> dests = escortDestinations(ctx, p);
                EscortOption o = dests.get(rng.nextInt(dests.size()));
                target = new QuestTarget.Escort(o.destination());
                needed = 1; // the legs to escort are known once the convoy sails (QuestRules.bindEscort)
                reward = p.sea().escort() + p.sea().escortPer1000() * o.distance() / 1000.0;
            }
            case PLUNDER_CONVOY -> {
                needed = range(rng, p.sea().countMin(), p.sea().countMax());
                reward = needed * (double) p.sea().perConvoy();
            }
            case HUNT_PATROL -> {
                needed = range(rng, p.sea().countMin(), p.sea().countMax());
                reward = needed * (double) p.sea().perPatrol();
            }
            case HUNT_SHIP -> {
                needed = range(rng, p.sea().countMin(), p.sea().countMax());
                reward = needed * (double) p.sea().perShip();
            }
            default -> throw new IllegalArgumentException("Quest type " + type.id() + " is not offered");
        }
        long coins = Math.max(1L, Math.round(reward * Math.max(0.0, p.rewardScale())));
        UUID id = new UUID(rng.nextLong(), rng.nextLong());
        return new Quest(id, ctx.port(), ctx.kind(), type, target, needed, 0, coins, QuestRules.rewardDeed(ctx.kind()),
                ctx.day(), ctx.day() + Math.max(1, p.offerDays()) - 1, 0L, QuestState.OFFERED);
    }
}
