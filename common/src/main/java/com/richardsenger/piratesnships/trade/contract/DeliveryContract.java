package com.richardsenger.piratesnships.trade.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A delivery contract (design.md §10.3): bring {@code quantity} of {@code good} from {@code origin} to
 * {@code destination} by the end of day {@code deadlineDay}. Days are whole in-game days. Immutable; every state
 * change is a method returning a {@link ContractResult}.
 *
 * <pre>
 *   OFFERED --accept--> ACCEPTED --deliver--> DELIVERED
 *      |                    |--deliver late / deadline passed / abandon--> FAILED
 *      `--offer lifetime over--> EXPIRED
 * </pre>
 *
 * @param offerExpiresDay last day the offer can be accepted
 * @param deposit         doubloons the holder pays on accepting; refunded with the reward on delivery, lost on failure
 * @param holder          who accepted it (empty while offered)
 */
public record DeliveryContract(UUID id, ResourceLocation good, int quantity, ResourceLocation origin, ResourceLocation destination,
                               long offeredDay, long offerExpiresDay, long deadlineDay, int reward, int deposit,
                               State state, Optional<UUID> holder) {

    public enum State implements StringRepresentable {
        OFFERED, ACCEPTED, DELIVERED, FAILED, EXPIRED;

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        public boolean finished() {
            return this == DELIVERED || this == FAILED || this == EXPIRED;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<DeliveryContract> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(DeliveryContract::id),
            ResourceLocation.CODEC.fieldOf("good").forGetter(DeliveryContract::good),
            Codec.INT.fieldOf("quantity").forGetter(DeliveryContract::quantity),
            ResourceLocation.CODEC.fieldOf("origin").forGetter(DeliveryContract::origin),
            ResourceLocation.CODEC.fieldOf("destination").forGetter(DeliveryContract::destination),
            Codec.LONG.fieldOf("offered_day").forGetter(DeliveryContract::offeredDay),
            Codec.LONG.fieldOf("offer_expires_day").forGetter(DeliveryContract::offerExpiresDay),
            Codec.LONG.fieldOf("deadline_day").forGetter(DeliveryContract::deadlineDay),
            Codec.INT.fieldOf("reward").forGetter(DeliveryContract::reward),
            Codec.INT.fieldOf("deposit").forGetter(DeliveryContract::deposit),
            State.CODEC.fieldOf("state").forGetter(DeliveryContract::state),
            UUIDUtil.STRING_CODEC.optionalFieldOf("holder").forGetter(DeliveryContract::holder)
    ).apply(i, DeliveryContract::new));

    public enum Outcome {
        ACCEPTED, DELIVERED, FAILED, EXPIRED, ABANDONED,
        /** No change: */
        UNCHANGED, NOT_OFFERED, NOT_ACCEPTED, WRONG_HOLDER, WRONG_PORT, NOT_ENOUGH,
        /** The holder already has the maximum number of accepted contracts (checked by the service). */
        TOO_MANY
    }

    /**
     * @param depositDue  doubloons the caller takes from the holder (accept)
     * @param payout      doubloons the caller gives the holder (reward + deposit refund on delivery)
     * @param consumed    units the caller removes from the delivered cargo
     */
    public record ContractResult(DeliveryContract contract, Outcome outcome, int depositDue, int payout, int consumed) {
        public boolean changed() {
            return switch (outcome) {
                case ACCEPTED, DELIVERED, FAILED, EXPIRED, ABANDONED -> true;
                default -> false;
            };
        }

        static ContractResult same(DeliveryContract c, Outcome o) {
            return new ContractResult(c, o, 0, 0, 0);
        }
    }

    private DeliveryContract with(State newState, Optional<UUID> newHolder) {
        return new DeliveryContract(id, good, quantity, origin, destination, offeredDay, offerExpiresDay, deadlineDay,
                reward, deposit, newState, newHolder);
    }

    /** Expires an old offer or fails an overdue accepted contract; otherwise unchanged. */
    public ContractResult update(long day) {
        if (state == State.OFFERED && day > offerExpiresDay) return ContractResult.same(with(State.EXPIRED, holder), Outcome.EXPIRED);
        if (state == State.ACCEPTED && day > deadlineDay) return ContractResult.same(with(State.FAILED, holder), Outcome.FAILED);
        return ContractResult.same(this, Outcome.UNCHANGED);
    }

    public ContractResult accept(UUID who, long day) {
        ContractResult u = update(day);
        if (u.changed()) return u;
        if (state != State.OFFERED) return ContractResult.same(this, Outcome.NOT_OFFERED);
        return new ContractResult(with(State.ACCEPTED, Optional.of(who)), Outcome.ACCEPTED, deposit, 0, 0);
    }

    /**
     * Delivers at {@code port} on {@code day} with {@code available} units of the good on hand. Succeeds only for the
     * holder, at the destination, by the deadline, with the full quantity; then {@code consumed == quantity}.
     */
    public ContractResult deliver(UUID who, ResourceLocation port, int available, long day) {
        ContractResult u = update(day);
        if (u.changed()) return u;
        if (state != State.ACCEPTED) return ContractResult.same(this, Outcome.NOT_ACCEPTED);
        if (holder.isEmpty() || !holder.get().equals(who)) return ContractResult.same(this, Outcome.WRONG_HOLDER);
        if (!port.equals(destination)) return ContractResult.same(this, Outcome.WRONG_PORT);
        if (available < quantity) return ContractResult.same(this, Outcome.NOT_ENOUGH);
        return new ContractResult(with(State.DELIVERED, holder), Outcome.DELIVERED, 0, reward + deposit, quantity);
    }

    /** The holder gives up: the contract fails and the deposit is lost. */
    public ContractResult abandon(UUID who) {
        if (state != State.ACCEPTED) return ContractResult.same(this, Outcome.NOT_ACCEPTED);
        if (holder.isEmpty() || !holder.get().equals(who)) return ContractResult.same(this, Outcome.WRONG_HOLDER);
        return ContractResult.same(with(State.FAILED, holder), Outcome.ABANDONED);
    }
}
