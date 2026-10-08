package com.richardsenger.piratesnships.rpg.quest;

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
 * The Quests tab protocol of the harbor master's desk (QST1, the SW1 Orders pattern). Server → client:
 * {@link Quests} (the tab's content and the last action's result), sent when a desk opens and after every
 * {@link QuestAction}. Client → server: {@link QuestAction}; the server checks the desk session (reach, binding) and
 * everything else in {@code rpg.quest.Quests}.
 */
public final class QuestPayloads {

    private QuestPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Constants.id(path));
    }

    /**
     * The Quests tab of {@code port}: today's offers, the viewer's active quests (from every port), the active cap and
     * the days a quest has once accepted.
     */
    public record QuestsView(ResourceLocation port, int maxActive, int deadlineDays, List<Quest> offers, List<Quest> mine) {
        public static final Codec<QuestsView> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(QuestsView::port),
                Codec.INT.fieldOf("max_active").forGetter(QuestsView::maxActive),
                Codec.INT.fieldOf("deadline_days").forGetter(QuestsView::deadlineDays),
                Quest.CODEC.listOf().fieldOf("offers").forGetter(QuestsView::offers),
                Quest.CODEC.listOf().fieldOf("mine").forGetter(QuestsView::mine)
        ).apply(i, QuestsView::new));

        public QuestsView {
            offers = List.copyOf(offers);
            mine = List.copyOf(mine);
        }
    }

    /** The answer to a {@link QuestAction}: done or not, a result id ({@link QuestText#result}) and the quest. */
    public record QuestResult(boolean done, String key, Optional<Quest> quest) {
        public static final Codec<QuestResult> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("done").forGetter(QuestResult::done),
                Codec.STRING.fieldOf("key").forGetter(QuestResult::key),
                Quest.CODEC.optionalFieldOf("quest").forGetter(QuestResult::quest)
        ).apply(i, QuestResult::new));

        public static QuestResult of(Quests.Result r) {
            return new QuestResult(r.done(), r.key(), r.quest());
        }
    }

    /** Server → client: the Quests tab (empty = none at this desk) and the result of the last action, if any. */
    public record QuestsPayload(Optional<QuestsView> view, Optional<QuestResult> result) implements CustomPacketPayload {
        public static final Type<QuestsPayload> TYPE = payloadType("quests");
        static final Codec<QuestsPayload> C = RecordCodecBuilder.create(i -> i.group(
                QuestsView.CODEC.optionalFieldOf("view").forGetter(QuestsPayload::view),
                QuestResult.CODEC.optionalFieldOf("result").forGetter(QuestsPayload::result)
        ).apply(i, QuestsPayload::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestsPayload> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<QuestsPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: accept the offer {@code quest} of {@code port} ({@code accept}), or drop the active quest. */
    public record QuestAction(ResourceLocation port, boolean accept, UUID quest) implements CustomPacketPayload {
        public static final Type<QuestAction> TYPE = payloadType("quest_action");
        static final Codec<QuestAction> C = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(QuestAction::port),
                Codec.BOOL.fieldOf("accept").forGetter(QuestAction::accept),
                UUIDUtil.STRING_CODEC.fieldOf("quest").forGetter(QuestAction::quest)
        ).apply(i, QuestAction::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestAction> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<QuestAction> type() {
            return TYPE;
        }
    }
}
