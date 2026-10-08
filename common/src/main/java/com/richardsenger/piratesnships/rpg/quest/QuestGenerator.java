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

    /** Where and when the offers are made, and what the world can back. */
    public record Context(ResourceLocation port, PortKind kind, Climate climate, long day, long seed,
                          List<DeliveryOption> deliveries, List<TreasureOption> treasures) {
        public Context {
            deliveries = List.copyOf(deliveries);
            treasures = List.copyOf(treasures);
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
            out.add(t);
        }
        return out;
    }

    /** {@code count} offers for the port and day of {@code ctx} (fewer only if no type is offerable). */
    public static List<Quest> offers(Context ctx, int count, QuestParams p) {
        List<QuestType> types = offerable(ctx, p);
        if (types.isEmpty() || count <= 0) return List.of();
        TradeRandom.Stream rng = stream(ctx, "offers");
        List<Quest> out = new ArrayList<>();
        List<QuestType> pool = new ArrayList<>(types);
        while (out.size() < count) {
            if (pool.isEmpty()) pool.addAll(types);
            QuestType t = pool.remove(rng.nextInt(pool.size()));
            out.add(make(t, ctx, rng, p));
        }
        return List.copyOf(out);
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
            default -> throw new IllegalArgumentException("Quest type " + type.id() + " is not offered yet");
        }
        long coins = Math.max(1L, Math.round(reward * Math.max(0.0, p.rewardScale())));
        UUID id = new UUID(rng.nextLong(), rng.nextLong());
        return new Quest(id, ctx.port(), ctx.kind(), type, target, needed, 0, coins, QuestRules.rewardDeed(ctx.kind()),
                ctx.day(), ctx.day() + Math.max(1, p.offerDays()) - 1, 0L, QuestState.OFFERED);
    }
}
