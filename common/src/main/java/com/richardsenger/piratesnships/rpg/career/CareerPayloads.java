package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

/**
 * The navy officer's career screen protocol (docs/design.md §15, CAR1). Server → client: {@link View} (opens the
 * screen when {@code open}, else refreshes it, with the last action's result). Client → server: {@link Action}; the
 * server re-checks the officer (alive, a navy officer, within {@code careers.officer_reach}, not hostile) and the rules.
 */
public final class CareerPayloads {

    private CareerPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Constants.id(path));
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(s -> {
            try {
                return DataResult.success(Enum.valueOf(type, s));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + s);
            }
        }, Enum::name);
    }

    /** What the screen's buttons ask for. */
    public enum Kind { ENLIST, RESIGN, REQUEST_LETTER, COLLECT_PRIZE }

    /** A requirement line: translation suffix ({@link CareerText#requirement}), have, need. */
    public record Req(String key, long have, long need) {
        public static final Codec<Req> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Req::key),
                Codec.LONG.fieldOf("have").forGetter(Req::have),
                Codec.LONG.fieldOf("need").forGetter(Req::need)
        ).apply(i, Req::new));

        public static Req of(CareerRules.Requirement r) {
            return new Req(r.key(), r.have(), r.need());
        }

        public boolean met() {
            return have >= need;
        }
    }

    /** One ladder: the next rank's name key (empty at the top or when the ladder is closed) and its requirements. */
    public record Ladder(Optional<String> next, List<Req> requirements) {
        public static final Codec<Ladder> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("next").forGetter(Ladder::next),
                Req.CODEC.listOf().fieldOf("requirements").forGetter(Ladder::requirements)
        ).apply(i, Ladder::new));

        public Ladder {
            requirements = List.copyOf(requirements);
        }
    }

    /** The player's career as the screen shows it; {@code letterBlockedDays} > 0 while a voided letter blocks a new one. */
    public record Status(NavyRank navy, boolean enlisted, InfamyRank infamy, LetterState letter, long letterBlockedDays, long prize) {
        public static final Codec<Status> CODEC = RecordCodecBuilder.create(i -> i.group(
                NavyRank.CODEC.fieldOf("navy").forGetter(Status::navy),
                Codec.BOOL.fieldOf("enlisted").forGetter(Status::enlisted),
                InfamyRank.CODEC.fieldOf("infamy").forGetter(Status::infamy),
                LetterState.CODEC.fieldOf("letter").forGetter(Status::letter),
                Codec.LONG.fieldOf("letter_blocked_days").forGetter(Status::letterBlockedDays),
                Codec.LONG.fieldOf("prize").forGetter(Status::prize)
        ).apply(i, Status::new));
    }

    /** The answer to an {@link Action}: done or not, a translation key and its (plain text) arguments. */
    public record Result(boolean done, String key, List<String> args) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("done").forGetter(Result::done),
                Codec.STRING.fieldOf("key").forGetter(Result::key),
                Codec.STRING.listOf().fieldOf("args").forGetter(Result::args)
        ).apply(i, Result::new));

        public Result {
            args = List.copyOf(args);
        }

        public static Result of(boolean done, String key, Object... args) {
            return new Result(done, key, java.util.Arrays.stream(args).map(String::valueOf).toList());
        }
    }

    /** Server → client: the career screen for the officer with entity id {@code officer}. */
    public record View(int officer, boolean open, boolean enabled, Status status, int navyRep, int pirateRep, Ladder navyLadder,
                       Ladder infamyLadder, CareerRules.EnlistVerdict enlist, CareerRules.LetterVerdict letterVerdict, long letterFee,
                       Optional<Result> result) implements CustomPacketPayload {
        public static final Type<View> TYPE = payloadType("career_view");
        static final Codec<View> C = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("officer").forGetter(View::officer),
                Codec.BOOL.fieldOf("open").forGetter(View::open),
                Codec.BOOL.fieldOf("enabled").forGetter(View::enabled),
                Status.CODEC.fieldOf("status").forGetter(View::status),
                Codec.INT.fieldOf("navy_rep").forGetter(View::navyRep),
                Codec.INT.fieldOf("pirate_rep").forGetter(View::pirateRep),
                Ladder.CODEC.fieldOf("navy_ladder").forGetter(View::navyLadder),
                Ladder.CODEC.fieldOf("infamy_ladder").forGetter(View::infamyLadder),
                enumCodec(CareerRules.EnlistVerdict.class).fieldOf("enlist").forGetter(View::enlist),
                enumCodec(CareerRules.LetterVerdict.class).fieldOf("letter_verdict").forGetter(View::letterVerdict),
                Codec.LONG.fieldOf("letter_fee").forGetter(View::letterFee),
                Result.CODEC.optionalFieldOf("result").forGetter(View::result)
        ).apply(i, View::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<View> type() {
            return TYPE;
        }
    }

    /** Client → server: do {@code kind} at the officer with entity id {@code officer}. */
    public record Action(int officer, Kind kind) implements CustomPacketPayload {
        public static final Type<Action> TYPE = payloadType("career_action");
        static final Codec<Action> C = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("officer").forGetter(Action::officer),
                enumCodec(Kind.class).fieldOf("kind").forGetter(Action::kind)
        ).apply(i, Action::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }
}
