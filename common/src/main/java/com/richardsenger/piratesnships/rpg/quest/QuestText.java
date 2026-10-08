package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.client.MarketLines;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Translation keys and texts of quests (no client classes, so the server, the Quests tab and datagen share them).
 * {@link #title} describes a quest in one line; goods and entities are named from the registries of the side asking.
 */
public final class QuestText {

    public static final String KEY = "quest." + Constants.MOD_ID + ".";
    public static final String COMMANDS = "commands." + Constants.MOD_ID + ".quest.";

    // the Quests tab
    public static final String OFFERS = KEY + "offers";
    public static final String NO_OFFERS = KEY + "no_offers";
    public static final String MINE = KEY + "mine";
    public static final String NO_MINE = KEY + "no_mine";
    public static final String ACCEPT = KEY + "accept";
    public static final String ABANDON = KEY + "abandon";
    public static final String OFFER_DETAIL = KEY + "offer_detail";
    public static final String ACTIVE_DETAIL = KEY + "active_detail";
    public static final String DELIVER_HINT = KEY + "deliver_hint";
    public static final String FULL = KEY + "full";

    // messages
    public static final String PROGRESS = KEY + "progress";
    public static final String COMPLETED = KEY + "completed";
    public static final String COMPLETED_PAID = KEY + "completed_paid";
    public static final String FAILED = KEY + "failed";
    public static final String MAP_GIVEN = KEY + "map_given";
    public static final String TARGET_LOST = KEY + "target_lost";

    // commands
    public static final String LIST_HEADER = COMMANDS + "list.header";
    public static final String LIST_LINE = COMMANDS + "list.line";
    public static final String LIST_NONE = COMMANDS + "list.none";
    public static final String UNKNOWN_ID = COMMANDS + "unknown_id";
    public static final String UNKNOWN_TYPE = COMMANDS + "unknown_type";
    public static final String OFFERED = COMMANDS + "offered";
    public static final String CANT_OFFER = COMMANDS + "cant_offer";
    public static final String FORCED = COMMANDS + "completed";

    private QuestText() {
    }

    /** The key of an accept/abandon result id ({@link Quests.Result#key}). */
    public static String result(String id) {
        return KEY + "result." + id;
    }

    public static String typeName(QuestType type) {
        return KEY + "type." + type.id();
    }

    private static String titleKey(QuestType type) {
        return KEY + "title." + type.id();
    }

    /** A captain hunt's title when the bearing to his island is known. */
    private static final String CAPTAIN_TITLE_BEARING = KEY + "title.hunt_captain.bearing";

    /** The eight compass points of {@code QuestGenerator.bearing}. */
    public static final List<String> BEARINGS = List.of("north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west");

    public static String bearingKey(String bearing) {
        return KEY + "bearing." + bearing;
    }

    /** A port's shown name. */
    public static String portName(ResourceLocation port) {
        return MarketLines.portName(port);
    }

    /** A trade good's item name as seen from the client ({@code client}) or the server. */
    public static Component goodName(ResourceLocation good, boolean client) {
        ResourceLocation item = TradeService.goods(client).tradeable().get(good).map(TradeGood::item).orElse(good);
        return BuiltInRegistries.ITEM.get(item).getDescription();
    }

    public static Component entityName(ResourceLocation entity) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(t -> (Component) t.getDescription())
                .orElse(Component.literal(entity.toString()));
    }

    /** One line naming what the quest asks for: "Hunt 5 pirates", "Deliver 32 × Sugar to Cane Bay", ... */
    public static Component title(Quest q, boolean client) {
        return switch (q.target()) {
            case QuestTarget.Kill k -> Component.translatable(titleKey(q.type()), q.needed(), entityName(k.entity()));
            case QuestTarget.Cargo c -> Component.translatable(titleKey(q.type()), c.quantity(), goodName(c.good(), client), portName(c.destination()));
            case QuestTarget.Treasure t -> Component.translatable(titleKey(q.type()), portName(t.port()));
            case QuestTarget.Victim v when q.type() == QuestType.HUNT_CAPTAIN && v.bearing().isPresent() ->
                    Component.translatable(CAPTAIN_TITLE_BEARING, v.name(), Component.translatable(bearingKey(v.bearing().get())));
            case QuestTarget.Victim v -> Component.translatable(titleKey(q.type()), v.name());
            case QuestTarget.None n -> Component.translatable(titleKey(q.type()), q.needed());
        };
    }

    /** The second line of an offer: reward, days to finish, last day of the offer. */
    public static Component offerDetail(Quest q, int deadlineDays) {
        return Component.translatable(OFFER_DETAIL, q.rewardCoins(), deadlineDays, q.offerExpiresDay());
    }

    /** The second line of an active quest: progress, deadline, reward, the giver. */
    public static Component activeDetail(Quest q) {
        return Component.translatable(ACTIVE_DETAIL, q.progress(), q.needed(), q.deadlineDay(), q.rewardCoins(), portName(q.port()));
    }

    public static void lang(LangBuilder lang) {
        lang.add(OFFERS, "Quests offered here (%s of %s active)")
                .add(NO_OFFERS, "No quests today")
                .add(MINE, "Your quests")
                .add(NO_MINE, "No active quests")
                .add(ACCEPT, "Accept")
                .add(ABANDON, "Drop")
                .add(OFFER_DETAIL, "reward %s doubloons, %s days to finish, open until day %s")
                .add(ACTIVE_DETAIL, "%s/%s, by day %s, reward %s (from %s)")
                .add(DELIVER_HINT, "Deliver it at the destination's desk, Contracts tab")
                .add(FULL, "You already have the most quests you can take")
                .add(PROGRESS, "Quest: %s (%s/%s)")
                .add(COMPLETED, "Quest complete: %s")
                .add(COMPLETED_PAID, "Quest complete: %s. %s doubloons paid")
                .add(FAILED, "Quest failed: %s")
                .add(MAP_GIVEN, "You received a treasure map")
                .add(TARGET_LOST, "%s is gone, and not by your hand")
                .add(CAPTAIN_TITLE_BEARING, "Bring down %s of the island to the %s");
        for (String b : BEARINGS) lang.add(bearingKey(b), b.replace("_", ""));
        for (QuestType t : QuestType.values()) {
            lang.add(typeName(t), switch (t) {
                case HUNT_PIRATES -> "Pirate hunt";
                case KILL_MONSTER -> "Monster hunt";
                case TURN_IN -> "Prisoner delivery";
                case DELIVER -> "Cargo run";
                case FIND_TREASURE -> "Treasure hunt";
                case HUNT_NAVY -> "Navy raid";
                case HUNT_CAPTAIN -> "Captain hunt";
                case ESCORT -> "Escort";
                case PLUNDER_CONVOY -> "Convoy raid";
                case HUNT_PATROL -> "Patrol hunt";
                case HUNT_SHIP -> "Ship hunt";
            });
            lang.add(titleKey(t), switch (t) {
                case HUNT_PIRATES -> "Hunt %s pirates";
                case KILL_MONSTER -> "Slay %s × %s";
                case TURN_IN -> "Bring %s captured pirates to a navy officer";
                case DELIVER -> "Deliver %s × %s to %s";
                case FIND_TREASURE -> "Find the buried treasure of %s";
                case HUNT_NAVY -> "Kill %s navy sailors";
                case HUNT_CAPTAIN -> "Bring down %s";
                case ESCORT, PLUNDER_CONVOY, HUNT_PATROL, HUNT_SHIP -> "%s";
            });
        }
        for (String id : List.of(Quests.ACCEPTED, Quests.ABANDONED, QuestRules.TOO_MANY, QuestRules.EXPIRED, QuestRules.NOT_OFFERED,
                Quests.DISABLED, Quests.NO_SESSION, Quests.TREASURE_GONE, Quests.NO_PORT, Quests.CAPTAIN_GONE, Quests.UNKNOWN)) {
            lang.add(result(id), switch (id) {
                case Quests.ACCEPTED -> "Quest accepted: %s";
                case Quests.ABANDONED -> "Quest dropped: %s";
                case QuestRules.TOO_MANY -> "You already have the most quests you can take";
                case QuestRules.EXPIRED -> "That offer has run out";
                case QuestRules.NOT_OFFERED -> "That quest is no longer offered";
                case Quests.DISABLED -> "Quests are off on this server";
                case Quests.NO_SESSION -> "Stand at the harbor master's desk";
                case Quests.TREASURE_GONE -> "Somebody already dug up that treasure";
                case Quests.NO_PORT -> "That port is gone";
                case Quests.CAPTAIN_GONE -> "That captain is already gone";
                default -> "No such quest";
            });
        }
        lang.add(LIST_HEADER, "Quests of %s (%s of %s active, %s completed):")
                .add(LIST_LINE, "[%s] %s: %s/%s, by day %s, reward %s")
                .add(LIST_NONE, "No active quests")
                .add(UNKNOWN_ID, "No active quest with that id")
                .add(UNKNOWN_TYPE, "Unknown quest type")
                .add(OFFERED, "Offered quest [%s] at %s: %s")
                .add(CANT_OFFER, "%s can't offer that quest now")
                .add(FORCED, "Completed quest [%s] of %s");
    }
}
