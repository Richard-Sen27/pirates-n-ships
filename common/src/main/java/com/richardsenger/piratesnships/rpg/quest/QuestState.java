package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * <pre>
 *   OFFERED --accept--> ACTIVE --progress reaches needed / target done--> DONE
 *      |                   `--deadline passed / target failed / abandoned--> FAILED
 *      |--accept an escort whose lane is not charted yet--> CHARTING --lane ready--> ACTIVE
 *      |                                                        `--lane failed / chart timeout--> cancelled (dropped)
 *      `--offer days over: dropped from the port
 * </pre>
 * A {@link #CHARTING} quest (QST2b) waits for the harbor master's lane; no event moves it on.
 */
public enum QuestState implements StringRepresentable {
    OFFERED, CHARTING, ACTIVE, DONE, FAILED;

    public static final Codec<QuestState> CODEC = StringRepresentable.fromEnum(QuestState::values);

    public boolean finished() {
        return this == DONE || this == FAILED;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
