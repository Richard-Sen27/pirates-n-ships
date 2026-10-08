package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Something that may move a quest on, for the pure {@link QuestRules#advance}. */
public sealed interface QuestEvent {

    /** The player did {@code deed} ({@code Deeds.listen}). */
    record DeedDone(Deed deed) implements QuestEvent {
    }

    /** The player killed an entity of type {@code entity} ({@code LIVING_DEATH}). */
    record Killed(ResourceLocation entity) implements QuestEvent {
    }

    /** The delivery contract {@code contract} is in {@code state}; {@code null} = the contract is gone. */
    record ContractChanged(UUID contract, @Nullable DeliveryContract.State state) implements QuestEvent {
    }

    /** The treasure at {@code site} of {@code port} is looted. */
    record TreasureLooted(ResourceLocation port, BlockPos site) implements QuestEvent {
    }

    /** It is day {@code day} now (deadlines). */
    record Day(long day) implements QuestEvent {
    }

    /** An operator completes the quest. */
    record Complete() implements QuestEvent {
    }
}
