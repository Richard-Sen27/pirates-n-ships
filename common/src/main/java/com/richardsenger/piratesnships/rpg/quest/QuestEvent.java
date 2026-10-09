package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
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

    /**
     * The player brought down the entity {@code victim}: killed it ({@code LIVING_DEATH} with the player as offender,
     * or the {@code kill_pirate} deed) or turned it in alive (the {@code turn_in_pirate} deed). QST1b.
     */
    record VictimDown(UUID victim) implements QuestEvent {
    }

    /** The entity {@code victim} is gone and not by the player's hand (another killer, handed over by someone else). */
    record VictimLost(UUID victim) implements QuestEvent {
    }

    /** The delivery contract {@code contract} is in {@code state}; {@code null} = the contract is gone. */
    record ContractChanged(UUID contract, @Nullable DeliveryContract.State state) implements QuestEvent {
    }

    /** The treasure at {@code site} of {@code port} is looted. */
    record TreasureLooted(ResourceLocation port, BlockPos site) implements QuestEvent {
    }

    /** How a player got the better of an NPC ship (QST2, from the WS3b endings). */
    enum How { SUNK, CAPTURED, PLUNDERED }

    /**
     * The player sank (the last shooter), captured or plundered the NPC ship of the voyage {@code voyage}, a voyage of
     * {@code kind} under {@code faction}'s colours (QST2).
     */
    record ShipDefeated(UUID voyage, VoyageKind kind, Faction faction, How how) implements QuestEvent {
    }

    /** The player was within the escort radius of the voyage {@code voyage} while it sailed its leg {@code leg} (QST2). */
    record EscortSeen(UUID voyage, int leg) implements QuestEvent {
    }

    /** The voyage {@code voyage} ended for {@code reason} (QST2: an escorted convoy arrived or was lost). */
    record VoyageEnded(UUID voyage, VoyageEnd reason) implements QuestEvent {
    }

    /** It is day {@code day} now (deadlines). */
    record Day(long day) implements QuestEvent {
    }

    /** An operator completes the quest. */
    record Complete() implements QuestEvent {
    }
}
