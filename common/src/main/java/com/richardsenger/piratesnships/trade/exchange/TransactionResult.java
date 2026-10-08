package com.richardsenger.piratesnships.trade.exchange;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * What a market transaction did. Nothing moved unless {@link #done()}. {@code coins} is what the player paid (buy,
 * accept) or received (sell, deliver); {@code units} the goods moved.
 *
 * @param plunder        the plunder verdict of a sale ({@link PlunderRules.Outcome#NORMAL} for clean goods and buys)
 * @param noticed        a plunder sale the port noticed: the caller reports the crime (this package never calls the law)
 * @param contractOutcome the contract outcome for accept and deliver
 */
public record TransactionResult(Status status, ResourceLocation good, int units, long coins,
                                PlunderRules.Outcome plunder, boolean noticed, Optional<DeliveryContract.Outcome> contractOutcome) {

    public enum Status implements StringRepresentable {
        OK,
        /** Noticed plunder taken without payment: the goods are gone (still {@link #done()}). */
        CONFISCATED,
        NO_MARKET, NOT_TRADED, STOCK_LIMIT, INVALID_QUANTITY,
        NOT_ENOUGH_COINS, NOT_ENOUGH_SPACE, NOT_ENOUGH_GOODS,
        NO_CONTAINER, NO_CONTRACT, CONTRACT_REFUSED,
        /** The market won't deal with the player (REP1: villager reputation below {@code villager_trade_threshold}). */
        REPUTATION_REFUSED,
        /**
         * Plunder-marked goods offered at a village or navy outpost desk (LAW3, §13.4): refused, nothing moved. The
         * result is {@link #noticedPlunder() noticed}, so the law integration can report the seller.
         */
        PLUNDER_REFUSED;

        public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);

        public String translationKey() {
            return "pirates_n_ships.market.status." + getSerializedName();
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<TransactionResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            Status.CODEC.fieldOf("status").forGetter(TransactionResult::status),
            ResourceLocation.CODEC.fieldOf("good").forGetter(TransactionResult::good),
            Codec.INT.fieldOf("units").forGetter(TransactionResult::units),
            Codec.LONG.fieldOf("coins").forGetter(TransactionResult::coins),
            Codec.STRING.xmap(s -> PlunderRules.Outcome.valueOf(s), Enum::name).fieldOf("plunder").forGetter(TransactionResult::plunder),
            Codec.BOOL.fieldOf("noticed").forGetter(TransactionResult::noticed),
            Codec.STRING.xmap(s -> DeliveryContract.Outcome.valueOf(s), Enum::name).optionalFieldOf("contract").forGetter(TransactionResult::contractOutcome)
    ).apply(i, TransactionResult::new));

    public static TransactionResult failed(Status status, ResourceLocation good) {
        return new TransactionResult(status, good, 0, 0, PlunderRules.Outcome.NORMAL, false, Optional.empty());
    }

    /** LAW3: the port refused plundered goods and noticed them (nothing moved). */
    public static TransactionResult plunderRefused(ResourceLocation good) {
        return new TransactionResult(Status.PLUNDER_REFUSED, good, 0, 0, PlunderRules.Outcome.NORMAL, true, Optional.empty());
    }

    public static TransactionResult contract(Status status, ResourceLocation good, int units, long coins, DeliveryContract.Outcome outcome) {
        return new TransactionResult(status, good, units, coins, PlunderRules.Outcome.NORMAL, false, Optional.of(outcome));
    }

    /** Whether the transaction went through (goods and coins moved). */
    public boolean done() {
        return status == Status.OK || status == Status.CONFISCATED;
    }

    /** A sale the port noticed as plunder (sold, confiscated or refused): the law integration should report it. */
    public boolean noticedPlunder() {
        return noticed;
    }
}
