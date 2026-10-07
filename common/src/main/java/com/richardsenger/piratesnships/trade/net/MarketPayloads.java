package com.richardsenger.piratesnships.trade.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * The market protocol. Client → server: {@link Refresh}, {@link Trade}, {@link ContractAction}; the server checks
 * everything (open session for that port, distance, quantity, container) and answers each with a {@link State}.
 * {@link CloseMarket} (client to server) ends the session when the screen closes. The server also sends a
 * {@link State} without a result when an open session's view changed (another player's trade, prices, doubloons).
 * Codecs go through NBT ({@code ByteBufCodecs.fromCodec}); the payloads are small and rare.
 */
public final class MarketPayloads {

    private MarketPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Constants.id(path));
    }

    /** Re-send the state with quotes for {@code quantity} units. */
    public record Refresh(ResourceLocation port, int quantity) implements CustomPacketPayload {
        public static final Type<Refresh> TYPE = payloadType("market_refresh");
        static final Codec<Refresh> C = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(Refresh::port),
                Codec.INT.fieldOf("quantity").forGetter(Refresh::quantity)
        ).apply(i, Refresh::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Refresh> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Refresh> type() {
            return TYPE;
        }
    }

    /** Buy ({@code buy}) or sell; {@code container} = a nearby cargo container instead of the inventory. */
    public record Trade(ResourceLocation port, boolean buy, ResourceLocation good, int quantity, boolean plundered,
                        Optional<BlockPos> container) implements CustomPacketPayload {
        public static final Type<Trade> TYPE = payloadType("market_trade");
        static final Codec<Trade> C = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(Trade::port),
                Codec.BOOL.fieldOf("buy").forGetter(Trade::buy),
                ResourceLocation.CODEC.fieldOf("good").forGetter(Trade::good),
                Codec.INT.fieldOf("quantity").forGetter(Trade::quantity),
                Codec.BOOL.fieldOf("plundered").forGetter(Trade::plundered),
                BlockPos.CODEC.optionalFieldOf("container").forGetter(Trade::container)
        ).apply(i, Trade::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Trade> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Trade> type() {
            return TYPE;
        }
    }

    /** Accept an offer, or ({@code deliver}) deliver an accepted contract at this port. */
    public record ContractAction(ResourceLocation port, boolean deliver, UUID contract, Optional<BlockPos> container)
            implements CustomPacketPayload {
        public static final Type<ContractAction> TYPE = payloadType("market_contract");
        static final Codec<ContractAction> C = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(ContractAction::port),
                Codec.BOOL.fieldOf("deliver").forGetter(ContractAction::deliver),
                UUIDUtil.STRING_CODEC.fieldOf("contract").forGetter(ContractAction::contract),
                BlockPos.CODEC.optionalFieldOf("container").forGetter(ContractAction::container)
        ).apply(i, ContractAction::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, ContractAction> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<ContractAction> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: a harbor master's desk at {@code desk} opened {@code port}'s market; the client opens the market
     * screen and closes it beyond {@code reach} blocks from the desk. A {@link State} follows.
     */
    public record OpenMarket(ResourceLocation port, BlockPos desk, double reach) implements CustomPacketPayload {
        public static final Type<OpenMarket> TYPE = payloadType("market_open");
        static final Codec<OpenMarket> C = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(OpenMarket::port),
                BlockPos.CODEC.fieldOf("desk").forGetter(OpenMarket::desk),
                Codec.DOUBLE.fieldOf("reach").forGetter(OpenMarket::reach)
        ).apply(i, OpenMarket::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMarket> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<OpenMarket> type() {
            return TYPE;
        }
    }

    /** Server → client: the market state (empty = no open market, e.g. after a refused request) and the last result. */
    public record State(Optional<MarketView> view, Optional<TransactionResult> result) implements CustomPacketPayload {
        public static final Type<State> TYPE = payloadType("market_state");
        static final Codec<State> C = RecordCodecBuilder.create(i -> i.group(
                MarketView.CODEC.optionalFieldOf("view").forGetter(State::view),
                TransactionResult.CODEC.optionalFieldOf("result").forGetter(State::result)
        ).apply(i, State::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** Client to server: the market screen was closed; the session ends. */
    public record CloseMarket() implements CustomPacketPayload {
        public static final CloseMarket INSTANCE = new CloseMarket();
        public static final Type<CloseMarket> TYPE = payloadType("market_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, CloseMarket> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<CloseMarket> type() {
            return TYPE;
        }
    }
}
