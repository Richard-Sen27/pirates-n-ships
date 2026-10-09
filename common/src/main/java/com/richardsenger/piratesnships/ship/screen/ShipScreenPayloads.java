package com.richardsenger.piratesnships.ship.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The ship screen protocol (HGUI1). Server → client: {@link State}, which opens the screen ({@code open}), refreshes it,
 * answers an action with a message, or closes it (no view). Client → server: {@link Action}; {@link ShipScreens}
 * checks the session (enabled, ship present, helm in reach, the viewer's rights) and the rules of each action.
 */
public final class ShipScreenPayloads {

    /** Longest text an action carries (a ship name, an order id). */
    public static final int MAX_TEXT = 64;

    private ShipScreenPayloads() {
    }

    /**
     * Server → client.
     *
     * @param open    open the screen (the helm was sneak-used); false: an update of an open screen
     * @param view    what the screen shows; empty: the session ended, the screen closes
     * @param message the answer to the last action, or why the screen closed
     * @param ok      the action was done (green) or refused (red)
     */
    public record State(boolean open, Optional<ShipScreenView> view, Optional<Component> message, boolean ok)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Constants.id("ship_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::open,
                ByteBufCodecs.optional(ByteBufCodecs.fromCodecWithRegistries(ShipScreenView.CODEC)), State::view,
                ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), State::message,
                ByteBufCodecs.BOOL, State::ok,
                State::new);

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** What a client asks for. */
    public enum Kind {
        /** Rename the ship to {@code text}. */
        RENAME,
        /** Give the whistle order {@code text} ({@code WhistleOrder#id()}) to the ship. */
        ORDER,
        /** Release {@code crew} from its station. */
        RELEASE,
        /** Dismiss {@code crew} (the whistle's sneak-use). */
        DISMISS,
        /** Send {@code crew} to man the station at {@code pos}. */
        ASSIGN,
        /** Disassemble the ship at this helm. */
        DISASSEMBLE,
        /** The screen closed. */
        CLOSE
    }

    /**
     * Client → server: one action on the screen of {@code ship}.
     *
     * @param ship the ship the screen shows (a stale screen of another ship is refused)
     * @param kind what to do
     * @param text the name or order id, "" otherwise
     * @param crew the crew member, for release, dismiss and assign
     * @param pos  the station's plot position, for assign
     */
    public record Action(UUID ship, Kind kind, String text, Optional<UUID> crew, Optional<BlockPos> pos) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Constants.id("ship_screen_action"));
        static final Codec<Action> C = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("ship").forGetter(Action::ship),
                ShipScreenRules.enumCodec(Kind.class).fieldOf("kind").forGetter(Action::kind),
                Codec.string(0, MAX_TEXT).optionalFieldOf("text", "").forGetter(Action::text),
                UUIDUtil.STRING_CODEC.optionalFieldOf("crew").forGetter(Action::crew),
                BlockPos.CODEC.optionalFieldOf("pos").forGetter(Action::pos)
        ).apply(i, Action::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = ByteBufCodecs.fromCodecWithRegistries(C);

        public static Action of(UUID ship, Kind kind) {
            return new Action(ship, kind, "", Optional.empty(), Optional.empty());
        }

        public static Action text(UUID ship, Kind kind, String text) {
            return new Action(ship, kind, text, Optional.empty(), Optional.empty());
        }

        public static Action crew(UUID ship, Kind kind, UUID crew) {
            return new Action(ship, kind, "", Optional.of(crew), Optional.empty());
        }

        public static Action assign(UUID ship, UUID crew, BlockPos station) {
            return new Action(ship, Kind.ASSIGN, "", Optional.of(crew), Optional.of(station));
        }

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }
}
