package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * One quest (docs/design.md §15, QST1): offered by {@code port} (a port of kind {@code giver}), asking for
 * {@code needed} of what {@code type} and {@code target} say, paying {@code rewardCoins} doubloons and the
 * {@code rewardDeed} on completion. Days are whole in-game days ({@code TradeService.day}). Immutable; the pure rules
 * are in {@link QuestRules}.
 *
 * @param offerExpiresDay last day the offer can be accepted
 * @param deadlineDay     last day to finish it (set on accepting; 0 while offered)
 */
public record Quest(UUID id, ResourceLocation port, PortKind giver, QuestType type, QuestTarget target, int needed, int progress,
                    long rewardCoins, Deed rewardDeed, long offeredDay, long offerExpiresDay, long deadlineDay, QuestState state) {

    public static final Codec<Deed> DEED_CODEC = Codec.STRING.comapFlatMap(
            s -> Deed.byId(s).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown deed " + s)), Deed::id);

    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Quest::id),
            ResourceLocation.CODEC.fieldOf("port").forGetter(Quest::port),
            PortKind.CODEC.fieldOf("giver").forGetter(Quest::giver),
            QuestType.CODEC.fieldOf("type").forGetter(Quest::type),
            QuestTarget.CODEC.fieldOf("target").forGetter(Quest::target),
            Codec.INT.fieldOf("needed").forGetter(Quest::needed),
            Codec.INT.fieldOf("progress").forGetter(Quest::progress),
            Codec.LONG.fieldOf("reward_coins").forGetter(Quest::rewardCoins),
            DEED_CODEC.fieldOf("reward_deed").forGetter(Quest::rewardDeed),
            Codec.LONG.fieldOf("offered_day").forGetter(Quest::offeredDay),
            Codec.LONG.fieldOf("offer_expires_day").forGetter(Quest::offerExpiresDay),
            Codec.LONG.fieldOf("deadline_day").forGetter(Quest::deadlineDay),
            QuestState.CODEC.fieldOf("state").forGetter(Quest::state)
    ).apply(i, Quest::new));

    public Quest {
        needed = Math.max(1, needed);
        progress = Math.max(0, Math.min(progress, needed));
    }

    /** The first eight hex digits of the id: what commands and messages show. */
    public String shortId() {
        return shortId(id);
    }

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public Quest withProgress(int p) {
        return new Quest(id, port, giver, type, target, needed, p, rewardCoins, rewardDeed, offeredDay, offerExpiresDay, deadlineDay, state);
    }

    public Quest withState(QuestState s) {
        return new Quest(id, port, giver, type, target, needed, progress, rewardCoins, rewardDeed, offeredDay, offerExpiresDay, deadlineDay, s);
    }

    public Quest withTarget(QuestTarget t) {
        return new Quest(id, port, giver, type, t, needed, progress, rewardCoins, rewardDeed, offeredDay, offerExpiresDay, deadlineDay, state);
    }

    public Quest withNeeded(int n) {
        return new Quest(id, port, giver, type, target, n, progress, rewardCoins, rewardDeed, offeredDay, offerExpiresDay, deadlineDay, state);
    }

    public Quest withDeadline(long day) {
        return new Quest(id, port, giver, type, target, needed, progress, rewardCoins, rewardDeed, offeredDay, offerExpiresDay, day, state);
    }
}
