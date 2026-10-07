package com.richardsenger.piratesnships.law.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

/**
 * The notice board protocol (docs/design.md §13.2). Server → client: {@link Open} when a player uses a board, then a
 * {@link State} whenever what the board shows changes (checked once a second) and after every request. Client →
 * server: {@link Place} (a bounty from the form) and {@link Close}. The server checks everything (open session for
 * that board, distance, the bounty rules, the doubloons). Codecs go through NBT ({@code ByteBufCodecs.fromCodec});
 * the payloads are small and rare.
 */
public final class NoticeBoardPayloads {

    private NoticeBoardPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Constants.id(path));
    }

    /** The answer to a request: a translation key ({@code message.pirates_n_ships.notice_board.*}) and its arguments. */
    public record Result(boolean ok, String key, List<String> args) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("ok").forGetter(Result::ok),
                Codec.STRING.fieldOf("key").forGetter(Result::key),
                Codec.STRING.listOf().fieldOf("args").forGetter(Result::args)
        ).apply(i, Result::new));

        public Result {
            args = List.copyOf(args);
        }
    }

    /** Server → client: open the notice board screen for the board at {@code board}; it closes beyond {@code reach}. */
    public record Open(BlockPos board, double reach) implements CustomPacketPayload {
        public static final Type<Open> TYPE = payloadType("notice_board_open");
        static final Codec<Open> C = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("board").forGetter(Open::board),
                Codec.DOUBLE.fieldOf("reach").forGetter(Open::reach)
        ).apply(i, Open::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Server → client: what the board shows (empty = the session ended) and the answer to the last request. */
    public record State(Optional<NoticeBoardView> view, Optional<Result> result) implements CustomPacketPayload {
        public static final Type<State> TYPE = payloadType("notice_board_state");
        static final Codec<State> C = RecordCodecBuilder.create(i -> i.group(
                NoticeBoardView.CODEC.optionalFieldOf("view").forGetter(State::view),
                Result.CODEC.optionalFieldOf("result").forGetter(State::result)
        ).apply(i, State::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** Client → server: place a bounty of {@code amount} doubloons on the player or known target named {@code target}. */
    public record Place(BlockPos board, String target, int amount) implements CustomPacketPayload {
        public static final Type<Place> TYPE = payloadType("notice_board_place");
        static final Codec<Place> C = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("board").forGetter(Place::board),
                Codec.string(0, 64).fieldOf("target").forGetter(Place::target),
                Codec.INT.fieldOf("amount").forGetter(Place::amount)
        ).apply(i, Place::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Place> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Place> type() {
            return TYPE;
        }
    }

    /** Client → server: the screen was closed; stop sending updates. */
    public record Close() implements CustomPacketPayload {
        public static final Close INSTANCE = new Close();
        public static final Type<Close> TYPE = payloadType("notice_board_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Close> type() {
            return TYPE;
        }
    }
}
