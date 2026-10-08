package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;

import java.util.Optional;

/**
 * The pure quest rules (docs/design.md §15, QST1): how an event moves an active quest on, accepting an offer, offer
 * expiry, the accept checks and the reward deed of each port kind. No world access.
 */
public final class QuestRules {

    /** Refusal keys of {@link #canAccept} (translation keys come from {@link QuestText#result}). */
    public static final String TOO_MANY = "too_many";
    public static final String EXPIRED = "expired";
    public static final String NOT_OFFERED = "not_offered";

    private QuestRules() {
    }

    /**
     * {@code quest} after {@code event}. Only an {@link QuestState#ACTIVE} quest changes: progress counts up to
     * {@code needed} and then the quest is {@link QuestState#DONE}; a passed deadline, a failed or vanished contract
     * fail it; the quest's one victim brought down by the player completes it, lost otherwise fails it. Returns the
     * same instance when nothing changed.
     */
    public static Quest advance(Quest quest, QuestEvent event) {
        if (quest.state() != QuestState.ACTIVE) return quest;
        return switch (event) {
            case QuestEvent.DeedDone d -> counts(quest.type(), d.deed()) ? step(quest, 1) : quest;
            case QuestEvent.Killed k -> quest.type() == QuestType.KILL_MONSTER && quest.target() instanceof QuestTarget.Kill t
                    && t.entity().equals(k.entity()) ? step(quest, 1) : quest;
            case QuestEvent.VictimDown v -> isVictim(quest, v.victim()) ? done(quest) : quest;
            case QuestEvent.VictimLost v -> isVictim(quest, v.victim()) ? quest.withState(QuestState.FAILED) : quest;
            case QuestEvent.ContractChanged c -> contract(quest, c);
            case QuestEvent.TreasureLooted l -> quest.type() == QuestType.FIND_TREASURE && quest.target() instanceof QuestTarget.Treasure t
                    && t.port().equals(l.port()) && t.site().equals(l.site()) ? done(quest) : quest;
            case QuestEvent.ShipDefeated s -> countsShip(quest.type(), s) ? step(quest, 1) : quest;
            case QuestEvent.EscortSeen e -> escortSeen(quest, e);
            case QuestEvent.VoyageEnded e -> escortEnded(quest, e);
            case QuestEvent.Day d -> d.day() > quest.deadlineDay() ? quest.withState(QuestState.FAILED) : quest;
            case QuestEvent.Complete c -> done(quest);
        };
    }

    /**
     * Whether getting the better of a ship counts toward a quest of {@code type} (QST2): a convoy raid counts plundered
     * merchant convoys; a patrol hunt navy patrols sunk or captured; a ship hunt any ship under the pirates' colours sunk
     * or captured (pirate voyages and WS5's raiders alike).
     */
    public static boolean countsShip(QuestType type, QuestEvent.ShipDefeated s) {
        boolean beaten = s.how() == QuestEvent.How.SUNK || s.how() == QuestEvent.How.CAPTURED;
        return switch (type) {
            case PLUNDER_CONVOY -> s.how() == QuestEvent.How.PLUNDERED && s.kind() == VoyageKind.CONVOY && s.faction() == Faction.MERCHANTS;
            case HUNT_PATROL -> beaten && s.kind() == VoyageKind.PATROL;
            case HUNT_SHIP -> beaten && s.faction() == Faction.PIRATES;
            default -> false;
        };
    }

    /**
     * Legs of an escorted convoy's route the player must have been close by on: {@code fraction} of {@code legs},
     * rounded up, at least 1 and at most {@code legs}.
     */
    public static int escortLegsNeeded(int legs, double fraction) {
        int l = Math.max(1, legs);
        int n = (int) Math.ceil(l * Math.max(0.0, Math.min(1.0, fraction)) - 1e-9);
        return Math.max(1, Math.min(l, n));
    }

    /**
     * The player was close to the escorted convoy on leg {@code leg}: a leg after the last one counted adds one to the
     * progress (never more than {@code needed}); the quest stays active until the convoy arrives.
     */
    private static Quest escortSeen(Quest quest, QuestEvent.EscortSeen e) {
        if (quest.type() != QuestType.ESCORT || !(quest.target() instanceof QuestTarget.Escort t) || !t.follows(e.voyage())) return quest;
        if (e.leg() <= t.lastLeg()) return quest;
        return quest.withTarget(t.withLastLeg(e.leg())).withProgress(Math.min(quest.needed(), quest.progress() + 1));
    }

    /**
     * The escorted convoy's voyage ended: arrived with enough legs escorted completes the quest, arrived with too few,
     * sunk, captured, lost or cancelled fails it.
     */
    private static Quest escortEnded(Quest quest, QuestEvent.VoyageEnded e) {
        if (quest.type() != QuestType.ESCORT || !(quest.target() instanceof QuestTarget.Escort t) || !t.follows(e.voyage())) return quest;
        if (e.reason() == VoyageEnd.ARRIVED && quest.progress() >= quest.needed()) return done(quest);
        return quest.withState(QuestState.FAILED);
    }

    /** Whether {@code deed} counts toward a quest of {@code type}. */
    public static boolean counts(QuestType type, Deed deed) {
        return switch (type) {
            case HUNT_PIRATES -> deed == Deed.KILL_PIRATE;
            case HUNT_NAVY -> deed == Deed.KILL_NAVY;
            case TURN_IN -> deed == Deed.TURN_IN_PIRATE;
            default -> false;
        };
    }

    /** Whether {@code quest} is about the one entity {@code id} (a captain hunt's target; a successor is another id). */
    public static boolean isVictim(Quest quest, java.util.UUID id) {
        return quest.target() instanceof QuestTarget.Victim v && v.id().equals(id);
    }

    private static Quest contract(Quest quest, QuestEvent.ContractChanged c) {
        if (quest.type() != QuestType.DELIVER || !(quest.target() instanceof QuestTarget.Cargo cargo)) return quest;
        if (cargo.contract().isEmpty() || !cargo.contract().get().equals(c.contract())) return quest;
        if (c.state() == null) return quest.withState(QuestState.FAILED);
        return switch (c.state()) {
            case DELIVERED -> done(quest);
            case FAILED, EXPIRED -> quest.withState(QuestState.FAILED);
            default -> quest;
        };
    }

    private static Quest step(Quest quest, int n) {
        int p = Math.min(quest.needed(), quest.progress() + n);
        Quest next = quest.withProgress(p);
        return p >= quest.needed() ? next.withState(QuestState.DONE) : next;
    }

    private static Quest done(Quest quest) {
        return quest.withProgress(quest.needed()).withState(QuestState.DONE);
    }

    /** The offer, accepted on {@code day}: active, with its deadline {@code deadlineDays} days later. */
    public static Quest accept(Quest offer, long day, int deadlineDays) {
        return offer.withState(QuestState.ACTIVE).withProgress(0).withDeadline(day + Math.max(0, deadlineDays));
    }

    /**
     * An accepted escort bound to its convoy: the voyage {@code voyage} named {@code name} sailing {@code legs} legs;
     * {@code needed} becomes {@link #escortLegsNeeded}. Other quests are returned unchanged.
     */
    public static Quest bindEscort(Quest quest, java.util.UUID voyage, String name, int legs, double fraction) {
        if (!(quest.target() instanceof QuestTarget.Escort t)) return quest;
        QuestTarget.Escort bound = t.withConvoy(voyage, name, legs);
        return quest.withTarget(bound).withNeeded(escortLegsNeeded(bound.legs(), fraction)).withProgress(0);
    }

    /** Whether the offer can no longer be accepted on {@code day}. */
    public static boolean expired(Quest offer, long day) {
        return day > offer.offerExpiresDay();
    }

    /** Why {@code log}'s owner can't accept {@code offer} on {@code day} (a refusal id), or empty if they can. */
    public static Optional<String> canAccept(QuestLog log, Quest offer, long day, int maxActive) {
        if (offer.state() != QuestState.OFFERED) return Optional.of(NOT_OFFERED);
        if (expired(offer, day)) return Optional.of(EXPIRED);
        if (log.active().size() >= maxActive) return Optional.of(TOO_MANY);
        return Optional.empty();
    }

    /** The deed a completed quest of a port of {@code giver} is worth. */
    public static Deed rewardDeed(PortKind giver) {
        return switch (giver) {
            case NAVY_OUTPOST -> Deed.COMPLETE_NAVY_QUEST;
            case PIRATE_ISLAND -> Deed.COMPLETE_PIRATE_QUEST;
            case SEAFARER_VILLAGE -> Deed.COMPLETE_VILLAGE_QUEST;
        };
    }

    /**
     * Doubloons {@code QuestRewards} pays on completion: the quest's reward, except for a delivery, whose contract
     * already paid it on delivery.
     */
    public static long coinsOnCompletion(Quest quest) {
        return quest.type() == QuestType.DELIVER ? 0 : quest.rewardCoins();
    }
}
