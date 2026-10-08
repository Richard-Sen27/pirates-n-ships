package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * <pre>
 *   OFFERED --accept--> ACTIVE --progress reaches needed / target done--> DONE
 *      |                   `--deadline passed / target failed / abandoned--> FAILED
 *      `--offer days over: dropped from the port
 * </pre>
 */
public enum QuestState implements StringRepresentable {
    OFFERED, ACTIVE, DONE, FAILED;

    public static final Codec<QuestState> CODEC = StringRepresentable.fromEnum(QuestState::values);

    public boolean finished() {
        return this == DONE || this == FAILED;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
