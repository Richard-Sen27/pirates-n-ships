package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * The mod's advancement tab (ADV1), as plain data: every node with its parent, text, icon, frame, criteria and
 * experience. Pure (no registry access), so datagen ({@link AdvancementData}), the experience payout
 * ({@link AdvancementXp}) and the JUnit check against {@code docs/advancements.md} share one source.
 */
public final class AdvancementTree {

    /** The frame of a node (vanilla's {@code AdvancementType}, kept pure here). */
    public enum Frame {
        TASK, GOAL, CHALLENGE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a node's criteria combine. */
    public enum Join { ALL, ANY }

    /** What a criterion listens to. */
    public enum Type {
        /** One of the mod's triggers ({@link TriggerIds}). */
        MOD,
        /** {@code minecraft:tick}: granted at once. */
        TICK,
        /** {@code minecraft:inventory_changed} with any of {@code ids} (items). */
        HAS_ITEMS,
        /** {@code minecraft:placed_block} of {@code ids.get(0)}. */
        PLACED_BLOCK,
        /** {@code minecraft:player_killed_entity} of the entity type {@code ids.get(0)}. */
        KILLED,
        /** {@code minecraft:player_generates_container_loot} of the loot table {@code ids.get(0)}. */
        LOOT
    }

    /**
     * One criterion.
     *
     * @param name    the criterion's key in the advancement
     * @param trigger for {@link Type#MOD}: a {@link TriggerIds} id
     * @param ids     full ids ({@code ns:path}) for the vanilla types
     * @param label   the short form in {@code docs/advancements.md} for the vanilla types
     */
    public record Crit(String name, Type type, String trigger, Optional<String> kind, OptionalInt min, List<String> ids,
                       String label) {

        static Crit mod(String trigger) {
            return new Crit(trigger, Type.MOD, trigger, Optional.empty(), OptionalInt.empty(), List.of(), "");
        }

        static Crit kind(String trigger, String kind) {
            return new Crit(kind.replace('/', '_'), Type.MOD, trigger, Optional.of(kind), OptionalInt.empty(), List.of(), "");
        }

        static Crit atLeast(String trigger, int min) {
            return new Crit(trigger, Type.MOD, trigger, Optional.empty(), OptionalInt.of(min), List.of(), "");
        }

        /** The criterion as {@code docs/advancements.md} writes it, e.g. {@code ship_assembled value>=500}. */
        public String describe() {
            if (type != Type.MOD) return label;
            StringBuilder sb = new StringBuilder(trigger);
            kind.ifPresent(k -> sb.append(" kind=").append(k));
            min.ifPresent(m -> sb.append(" value>=").append(m));
            return sb.toString();
        }
    }

    /**
     * One advancement.
     *
     * @param path   the id's path ({@code pirates_n_ships:<path>}), e.g. {@code shipwright/launch_ship}
     * @param parent the parent's path, {@code null} for the root
     * @param icon   an item id ({@code ns:path})
     * @param xp     experience before {@code advancements.experience_scale}
     */
    public record Node(String path, @Nullable String parent, String title, String description, String icon, Frame frame,
                       boolean hidden, Join join, List<Crit> criteria, int xp) {

        public String id() {
            return Constants.MOD_ID + ":" + path;
        }

        public String titleKey() {
            return "advancements." + Constants.MOD_ID + "." + path.replace('/', '.') + ".title";
        }

        public String descriptionKey() {
            return "advancements." + Constants.MOD_ID + "." + path.replace('/', '.') + ".description";
        }

        /** The criteria as {@code docs/advancements.md} writes them. */
        public String describeCriteria() {
            return criteria.stream().map(Crit::describe).distinct()
                    .collect(Collectors.joining(join == Join.ALL ? " and " : " or "));
        }
    }

    /** Experience of each frame before scaling. */
    public static final int XP_TASK = 5;
    public static final int XP_GOAL = 15;
    public static final int XP_CHALLENGE = 40;

    /** The tab's background (tiled). */
    public static final String BACKGROUND = "minecraft:textures/block/spruce_planks.png";

    /** The vanilla woods whose members always exist (a mod's wood only with its mod). */
    static final List<String> VANILLA_WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
            "cherry", "bamboo", "crimson", "warped");

    private static final List<Node> NODES = new ArrayList<>();

    static {
        String m = Constants.MOD_ID + ":";
        root();
        // --- Shipwright
        task("shipwright/craft_helm", "root", "Hand on the Wheel", "Craft a ship's helm, in any wood.", "helm",
                hasItems("craft_helm", "inventory_changed helm (any wood)", woodMembers("helm", "dark_oak")));
        task("shipwright/launch_ship", "shipwright/craft_helm", "Launched!", "Assemble a ship at her helm.", "nameplate",
                Crit.mod(TriggerIds.SHIP_ASSEMBLED));
        task("shipwright/set_sail", "shipwright/launch_ship", "Canvas Aloft", "Set a sail at a winch, a yard or a cleat.",
                "sail_winch", Crit.mod(TriggerIds.SAIL_SET));
        node("shipwright/sail_with_wind", "shipwright/set_sail", "Following Wind",
                "Make way under sail with the wind in your canvas.", "yard", Frame.GOAL, false,
                Crit.atLeast(TriggerIds.SAILED_WITH_WIND, 3));
        task("shipwright/drop_anchor", "shipwright/set_sail", "Let Go the Anchor", "Drop the anchor at a capstan.", "capstan",
                Crit.mod(TriggerIds.ANCHOR_DROPPED));
        task("shipwright/fix_leak", "shipwright/launch_ship", "Stop the Leak", "Patch a breach in your hull.", "hull_patch",
                Crit.mod(TriggerIds.LEAK_FIXED));
        task("shipwright/order_ship", "shipwright/launch_ship", "Built to Order",
                "Order a ship from a shipwright at a harbor master's desk.", "ship_receipt", Crit.mod(TriggerIds.SHIP_ORDERED));
        node("shipwright/launch_brig", "shipwright/order_ship", "Two Masts", "Take delivery of a brig.", "shipwright_toolkit",
                Frame.GOAL, false, Crit.kind(TriggerIds.SHIP_LAUNCHED, "brig"));
        node("shipwright/big_ship", "shipwright/launch_ship", "Ship of the Line", "Assemble a ship of 500 blocks or more.",
                "figurehead_lion", Frame.CHALLENGE, false, Crit.atLeast(TriggerIds.SHIP_ASSEMBLED, 500));
        anyOf("shipwright/rope_railing", "shipwright/craft_helm", "Mind the Rail", "Rig a rope railing.", "oak_rope_railing",
                placedBlocks("placed_block rope_railing (any wood)", woodMembers("rope_railing", null)));
        task("shipwright/helm_wood", "shipwright/craft_helm", "Fine Joinery", "Craft a helm in another wood than dark oak.",
                "spruce_helm", hasItems("helm_wood", "inventory_changed helm (not dark oak)",
                        woodMembers("helm", "dark_oak").stream().filter(id -> !id.equals(m + "helm")).toList()));
        // --- Crew
        task("crew/hire_hand", "root", "Signed On", "Hire a hand at a harbor master's desk.", "bandana",
                Crit.mod(TriggerIds.CREW_HIRED));
        task("crew/give_order", "crew/hire_hand", "Whistle Up", "Give your crew an order.", "captains_whistle",
                Crit.mod(TriggerIds.ORDER_GIVEN));
        task("crew/hammock_night", "crew/hire_hand", "Swinging Sleep", "Sleep through the night in a hammock.", "hammock",
                Crit.mod(TriggerIds.HAMMOCK_NIGHT));
        node("crew/full_crew", "crew/hire_hand", "A Full Complement", "Have eight hands aboard one ship.", "ships_bell",
                Frame.GOAL, false, Crit.atLeast(TriggerIds.CREW_HIRED, 8));
        task("crew/wages_paid", "crew/hire_hand", "Payday", "Pay your crew's wages at dawn.", "sea_chest",
                Crit.mod(TriggerIds.WAGES_PAID));
        task("crew/deserter_returned", "crew/wages_paid", "Second Thoughts", "Win back a hand who meant to desert.", "rum",
                Crit.mod(TriggerIds.DESERTER_RETURNED));
        node("crew/loyal_crew", "crew/wages_paid", "Loyal to a Man", "Keep one ship's crew ten days without a deserter.",
                "figurehead_mermaid", Frame.GOAL, false, Crit.atLeast(TriggerIds.CREW_LOYAL, 10));
        // --- Trade
        task("trade/first_doubloon", "root", "Pieces of Eight", "Earn your first doubloon.", "doubloon",
                Crit.mod(TriggerIds.DOUBLOONS_EARNED));
        task("trade/contract", "trade/first_doubloon", "Signed and Delivered", "Deliver a trade contract.", "cargo_crate",
                Crit.mod(TriggerIds.CONTRACT_DELIVERED));
        task("trade/quest", "trade/first_doubloon", "Harbour Business", "Complete a quest from a harbor master.",
                "notice_board", Crit.mod(TriggerIds.QUEST_COMPLETED));
        node("trade/convoy", "trade/contract", "Safe Passage", "See a merchant convoy safely into port.", "merchant_flag",
                Frame.GOAL, false, Crit.mod(TriggerIds.CONVOY_ESCORTED));
        node("trade/thousand", "trade/first_doubloon", "A Fat Purse", "Earn 1,000 doubloons in all.", "cargo_barrel",
                Frame.GOAL, false, Crit.atLeast(TriggerIds.DOUBLOONS_EARNED, 1000));
        node("trade/ten_thousand", "trade/thousand", "A King's Ransom", "Earn 10,000 doubloons in all.", "spices",
                Frame.CHALLENGE, false, Crit.atLeast(TriggerIds.DOUBLOONS_EARNED, 10000));
        // --- Combat
        task("combat/fire_cannon", "root", "Fire in the Hole", "Fire a cannon or a swivel gun.", "cannon",
                Crit.mod(TriggerIds.CANNON_FIRED));
        task("combat/hit_ship", "combat/fire_cannon", "A Palpable Hit", "Hit another ship with a cannonball.", "cannonball",
                Crit.mod(TriggerIds.SHIP_HIT));
        task("combat/grapple", "combat/fire_cannon", "Grappled", "Latch a grappling hook onto another ship.",
                "grappling_hook", Crit.mod(TriggerIds.GRAPPLE_LATCHED));
        task("combat/board", "combat/grapple", "Away Boarders", "Lay a plank to another ship or send a boarding party.",
                "boarding_plank", Crit.mod(TriggerIds.SHIP_BOARDED));
        node("combat/capture", "combat/board", "Prize Taken", "Hold the deck of a ship you boarded.", "cutlass",
                Frame.GOAL, true, Crit.mod(TriggerIds.SHIP_CAPTURED));
        task("combat/shark", "combat/fire_cannon", "Shark Bait", "Kill a shark.", "shark_spawn_egg",
                killed("shark", "player_killed_entity shark", m + "shark"));
        node("combat/kraken", "combat/shark", "Release the Kraken", "Slay the kraken.", "kraken_beak", Frame.CHALLENGE, true,
                killed("kraken", "player_killed_entity kraken", m + "kraken"));
        // --- The world
        task("world/landfall", "root", "Landfall", "Visit a port: a village, a navy outpost or a pirate island.",
                "mooring_ring", Crit.mod(TriggerIds.PORT_VISITED));
        nodeAll("world/all_ports", "world/landfall", "Ports of Call",
                "Visit a seafarer village, a navy outpost and a pirate island.", "chart", Frame.CHALLENGE, false,
                Crit.kind(TriggerIds.PORT_VISITED, "seafarer_village"), Crit.kind(TriggerIds.PORT_VISITED, "navy_outpost"),
                Crit.kind(TriggerIds.PORT_VISITED, "pirate_island"));
        node("world/treasure", "world/landfall", "X Marks the Spot", "Dig up a buried treasure.", "treasure_map", Frame.GOAL,
                true, Crit.mod(TriggerIds.TREASURE_DUG));
        task("world/wreck", "world/landfall", "Davy Jones' Locker", "Open the chest of a sunken wreck.", "ship_lantern",
                loot("wreck", "player_generates_container_loot chests/wreck", m + "chests/wreck"));
        task("world/fly_flag", "world/landfall", "Show Your Colours", "Hoist a flag on a flagpole.", "flagpole",
                Crit.mod(TriggerIds.FLAG_HOISTED));
        node("world/chart_markers", "world/landfall", "Cartographer", "Have ten markers on your chart.", "chart_table",
                Frame.GOAL, false, Crit.atLeast(TriggerIds.MARKERS_PLACED, 10));
        node("world/storm", "world/landfall", "Weathered the Storm", "Stay aboard your ship through a minute of storm.",
                "oar", Frame.GOAL, false, Crit.atLeast(TriggerIds.STORM_WEATHERED, 60));
        // --- The law
        task("law/bounty_paid", "root", "Square with the Crown", "Pay a fine to a navy officer.", "bounty_proof",
                Crit.mod(TriggerIds.BOUNTY_PAID));
        task("law/shackled", "law/bounty_paid", "In Irons", "Put someone in shackles.", "shackles",
                Crit.kind(TriggerIds.PRISONER_TAKEN, "shackled"));
        task("law/brigged", "law/shackled", "Under Lock and Key", "Lead a prisoner into a brig cell.", "brig_key",
                Crit.kind(TriggerIds.PRISONER_TAKEN, "brigged"));
        node("law/letter", "law/shackled", "Letter of Marque", "Buy a letter of marque from a navy officer.", "navy_flag",
                Frame.GOAL, false, Crit.kind(TriggerIds.RANK_REACHED, "letter"));
        task("law/black_flag", "law/shackled", "Under the Black Flag", "Hoist the Jolly Roger.", "jolly_roger_flag",
                Crit.kind(TriggerIds.FLAG_HOISTED, "jolly_roger"));
        // --- Career
        task("career/midshipman", "root", "Midshipman", "Enlist in the navy.", "navy_hat",
                Crit.kind(TriggerIds.RANK_REACHED, "navy/midshipman"));
        task("career/lieutenant", "career/midshipman", "Lieutenant", "Earn a lieutenant's commission.", "officer_hat",
                Crit.kind(TriggerIds.RANK_REACHED, "navy/lieutenant"));
        node("career/captain", "career/lieutenant", "Captain", "Reach the rank of captain.", "officers_coat", Frame.GOAL,
                false, Crit.kind(TriggerIds.RANK_REACHED, "navy/captain"));
        node("career/commodore", "career/captain", "Commodore", "Reach the rank of commodore.", "saber", Frame.GOAL, false,
                Crit.kind(TriggerIds.RANK_REACHED, "navy/commodore"));
        node("career/admiral", "career/commodore", "Admiral", "Reach the navy's highest rank.", "ship_commission",
                Frame.CHALLENGE, false, Crit.kind(TriggerIds.RANK_REACHED, "navy/admiral"));
        task("career/buccaneer", "root", "Buccaneer", "Grow infamous enough to be called a buccaneer.", "pirate_hat",
                Crit.kind(TriggerIds.RANK_REACHED, "infamy/buccaneer"));
        node("career/dread_captain", "career/buccaneer", "Dread Captain", "Become a dread captain of the brethren.",
                "pistol", Frame.GOAL, false, Crit.kind(TriggerIds.RANK_REACHED, "infamy/dread_captain"));
        node("career/pirate_lord", "career/dread_captain", "Pirate Lord", "Become a lord of the pirates.", "captains_hat",
                Frame.CHALLENGE, false, Crit.kind(TriggerIds.RANK_REACHED, "infamy/pirate_lord"));
        // --- The Almanac
        task("almanac/open", "root", "Required Reading", "Open the Captain's Almanac.", "captains_almanac",
                Crit.mod(TriggerIds.ALMANAC_OPENED));
        task("almanac/scene", "almanac/open", "Show Me", "Watch a Show me scene in the Almanac.", "map_tile",
                Crit.mod(TriggerIds.SCENE_OPENED));
        node("almanac/all_chapters", "almanac/open", "Cover to Cover", "Read every chapter of the Almanac.",
                "captains_almanac", Frame.GOAL, false, Crit.kind(TriggerIds.CHAPTERS_READ, "all"));
    }

    private AdvancementTree() {
    }

    /** Every node, root first, each after its parent. */
    public static List<Node> nodes() {
        return Collections.unmodifiableList(NODES);
    }

    /** The node of {@code path}, if any. */
    public static Optional<Node> byPath(String path) {
        for (Node n : NODES) if (n.path().equals(path)) return Optional.of(n);
        return Optional.empty();
    }

    /** Nodes per branch (the first path segment; the root is its own branch). */
    public static Map<String, Integer> branchCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Node n : NODES) {
            String b = n.path().contains("/") ? n.path().substring(0, n.path().indexOf('/')) : n.path();
            out.merge(b, 1, Integer::sum);
        }
        return out;
    }

    /** Pure: the experience a node pays at {@code scale} (rounded, never negative). */
    public static int experience(Node node, double scale) {
        return (int) Math.max(0, Math.round(node.xp() * scale));
    }

    /**
     * The ids of a wood family's members in the vanilla woods: {@code <wood>_<suffix>}, the legacy wood's member under
     * the bare suffix (WOOD1).
     */
    static List<String> woodMembers(String suffix, @Nullable String legacyWood) {
        List<String> out = new ArrayList<>();
        for (String w : VANILLA_WOODS) {
            out.add(Constants.MOD_ID + ":" + (w.equals(legacyWood) ? suffix : w + "_" + suffix));
        }
        return out;
    }

    // ------------------------------------------------------------------ builders

    private static void root() {
        NODES.add(new Node("root", null, "Pirates 'n' Ships", "Sailing, piracy and life at sea.", Constants.MOD_ID + ":helm",
                Frame.TASK, false, Join.ALL,
                List.of(new Crit("joined", Type.TICK, "", Optional.empty(), OptionalInt.empty(), List.of(), "minecraft:tick")),
                0));
    }

    private static void task(String path, String parent, String title, String description, String icon, Crit... criteria) {
        node(path, parent, title, description, icon, Frame.TASK, false, criteria);
    }

    private static void node(String path, String parent, String title, String description, String icon, Frame frame,
                             boolean hidden, Crit... criteria) {
        add(path, parent, title, description, icon, frame, hidden, Join.ALL, criteria);
    }

    private static void nodeAll(String path, String parent, String title, String description, String icon, Frame frame,
                                boolean hidden, Crit... criteria) {
        add(path, parent, title, description, icon, frame, hidden, Join.ALL, criteria);
    }

    private static void anyOf(String path, String parent, String title, String description, String icon, List<Crit> criteria) {
        add(path, parent, title, description, icon, Frame.TASK, false, Join.ANY, criteria.toArray(Crit[]::new));
    }

    private static void task(String path, String parent, String title, String description, String icon, List<Crit> criteria) {
        add(path, parent, title, description, icon, Frame.TASK, false, Join.ALL, criteria.toArray(Crit[]::new));
    }

    private static void add(String path, String parent, String title, String description, String icon, Frame frame,
                            boolean hidden, Join join, Crit... criteria) {
        String iconId = icon.contains(":") ? icon : Constants.MOD_ID + ":" + icon;
        int xp = switch (frame) {
            case TASK -> XP_TASK;
            case GOAL -> XP_GOAL;
            case CHALLENGE -> XP_CHALLENGE;
        };
        NODES.add(new Node(path, parent, title, description, iconId, frame, hidden, join, List.of(criteria), xp));
    }

    private static List<Crit> hasItems(String name, String label, List<String> items) {
        return List.of(new Crit(name, Type.HAS_ITEMS, "", Optional.empty(), OptionalInt.empty(), List.copyOf(items), label));
    }

    private static List<Crit> placedBlocks(String label, List<String> blocks) {
        List<Crit> out = new ArrayList<>();
        for (String b : blocks) {
            out.add(new Crit("placed_" + b.substring(b.indexOf(':') + 1), Type.PLACED_BLOCK, "", Optional.empty(),
                    OptionalInt.empty(), List.of(b), label));
        }
        return out;
    }

    private static Crit killed(String name, String label, String entity) {
        return new Crit(name, Type.KILLED, "", Optional.empty(), OptionalInt.empty(), List.of(entity), label);
    }

    private static Crit loot(String name, String label, String table) {
        return new Crit(name, Type.LOOT, "", Optional.empty(), OptionalInt.empty(), List.of(table), label);
    }
}
