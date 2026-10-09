package com.richardsenger.piratesnships.almanac;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

/**
 * The Captain's Almanac protocol (ALM1, docs/design.md §3.4). Server → client: {@link Open} when a player reads it,
 * from the placed tome (with its position) or in hand (without). Client → server: {@link Close} when the reader is
 * done, so the placed tome closes ({@link AlmanacOpenRule}). The screen (ALM3) opens from {@code AlmanacClient.open}.
 */
public final class AlmanacPayloads {

    private AlmanacPayloads() {
    }

    /** Server → client: open the almanac; {@code tome} is the placed tome being read, empty for the book in hand. */
    public record Open(Optional<BlockPos> tome) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Constants.id("almanac_open"));
        static final Codec<Open> C = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("tome").forGetter(Open::tome)
        ).apply(i, Open::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Client → server: the reader closed the almanac opened by an {@link Open} with the same {@code tome}. */
    public record Close(Optional<BlockPos> tome) implements CustomPacketPayload {
        public static final Type<Close> TYPE = new Type<>(Constants.id("almanac_close"));
        static final Codec<Close> C = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("tome").forGetter(Close::tome)
        ).apply(i, Close::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Close> type() {
            return TYPE;
        }
    }
}
