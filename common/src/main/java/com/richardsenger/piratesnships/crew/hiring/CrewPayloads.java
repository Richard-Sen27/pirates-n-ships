package com.richardsenger.piratesnships.crew.hiring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Crew tab protocol of the harbor master's desk (CRW1, the QST1 Quests pattern). Server → client:
 * {@link CrewPayload} (the tab's content and the last hire's result), sent when a desk opens and after every
 * {@link CrewAction}. Client → server: {@link CrewAction}; the server checks the desk session (reach, binding) and
 * everything else in {@link Hiring}.
 */
public final class CrewPayloads {

    private CrewPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Constants.id(path));
    }

    /** The player's ship moored at the port: its name (empty when unnamed), crew and crew cap. */
    public record ShipLine(String name, int crew, int cap) {
        public static final Codec<ShipLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(ShipLine::name),
                Codec.INT.fieldOf("crew").forGetter(ShipLine::crew),
                Codec.INT.fieldOf("cap").forGetter(ShipLine::cap)
        ).apply(i, ShipLine::new));
    }

    /**
     * The Crew tab of {@code port}: the kind it offers, the daily wage ({@code crew.wages.per_day}, 0 while wages are
     * off), today's candidates (none while the player may not hire here: then {@code refusal} is the result id
     * saying why), and the player's ship moored there, if any.
     */
    public record CrewView(ResourceLocation port, CandidateKind kind, int wagePerDay, List<Candidate> candidates,
                           Optional<String> refusal, Optional<ShipLine> ship) {
        public static final Codec<CrewView> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(CrewView::port),
                CandidateKind.CODEC.fieldOf("kind").forGetter(CrewView::kind),
                Codec.INT.fieldOf("wage_per_day").forGetter(CrewView::wagePerDay),
                Candidate.CODEC.listOf().fieldOf("candidates").forGetter(CrewView::candidates),
                Codec.STRING.optionalFieldOf("refusal").forGetter(CrewView::refusal),
                ShipLine.CODEC.optionalFieldOf("ship").forGetter(CrewView::ship)
        ).apply(i, CrewView::new));

        public CrewView {
            candidates = List.copyOf(candidates);
        }
    }

    /** The answer to a {@link CrewAction}: done or not, a result id ({@link HiringText#result}) and the name. */
    public record CrewResult(boolean done, String key, String name) {
        public static final Codec<CrewResult> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("done").forGetter(CrewResult::done),
                Codec.STRING.fieldOf("key").forGetter(CrewResult::key),
                Codec.STRING.fieldOf("name").forGetter(CrewResult::name)
        ).apply(i, CrewResult::new));

        public static CrewResult of(Hiring.Result r) {
            return new CrewResult(r.done(), r.key(), r.name());
        }
    }

    /** Server → client: the Crew tab (empty = none at this desk) and the result of the last action, if any. */
    public record CrewPayload(Optional<CrewView> view, Optional<CrewResult> result) implements CustomPacketPayload {
        public static final Type<CrewPayload> TYPE = payloadType("crew_hiring");
        static final Codec<CrewPayload> C = RecordCodecBuilder.create(i -> i.group(
                CrewView.CODEC.optionalFieldOf("view").forGetter(CrewPayload::view),
                CrewResult.CODEC.optionalFieldOf("result").forGetter(CrewPayload::result)
        ).apply(i, CrewPayload::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, CrewPayload> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<CrewPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: hire today's candidate {@code candidate} of {@code port}. */
    public record CrewAction(ResourceLocation port, UUID candidate) implements CustomPacketPayload {
        public static final Type<CrewAction> TYPE = payloadType("crew_hire");
        static final Codec<CrewAction> C = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(CrewAction::port),
                UUIDUtil.STRING_CODEC.fieldOf("candidate").forGetter(CrewAction::candidate)
        ).apply(i, CrewAction::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, CrewAction> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<CrewAction> type() {
            return TYPE;
        }
    }
}
