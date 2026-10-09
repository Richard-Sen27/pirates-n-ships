package com.richardsenger.piratesnships.almanac.ponder;

/**
 * The text of the Ponder scenes for crew, combat and life aboard (PND2, docs/design.md §3.4, docs/almanac.md
 * "Scenes"), without any Ponder class: one enum of lines per scene, in the order they appear, each with its scene's
 * path and title. Every fact is the game's (the code each scene names); numbers are the default server config.
 */
public final class CrewSceneText {

    public static final String CREW_ORDERS = "crew_orders";
    public static final String BOARDING = "boarding";
    public static final String FLAGS_AND_ALLEGIANCE = "flags_and_allegiance";
    public static final String CROWS_NEST = "crows_nest";
    public static final String SHIPS_BELL = "ships_bell";
    public static final String GALLEY_AND_PROVISIONS = "galley_and_provisions";
    public static final String HAMMOCKS_AND_REST = "hammocks_and_rest";
    public static final String SWIVEL_GUN = "swivel_gun";
    public static final String OARS = "oars";
    /** PND4: crew at the rail and boarding nets. */
    public static final String ON_DECK = "on_deck";

    private CrewSceneText() {
    }

    /** {@link CrewOrdersScene} (crew/hiring, the whistle of station/order and station/winch, station/jobs, design.md §7). */
    public enum Orders implements SceneLine {
        HIRE("Hands are hired at a harbor master's desk: its Crew tab offers a few each day, for a fee and then a daily wage"),
        ABOARD("Lie moored at the port first: the new hand comes straight aboard. Every hand needs a free hammock"),
        WHEEL("Use the captain's whistle in the air, standing on your ship: the order wheel opens. Point at an order and click"),
        ASSIGN("Use the whistle on a hand, then on a station: he walks there and takes it until you release him"),
        ORDER("An order goes to every hand at a station that takes it: 'Aye, hoisting the sails!'"),
        JOBS("Unmanned stations that take the order become open jobs: a free hand on deck takes the nearest one by himself"),
        AT_WILL("Fire at will: gun crews aim and fire by themselves at hostile ships within 15° of the barrel and 64 blocks"),
        RELEASE("Release crew sends every hand off his station, drops the open jobs and ends fire at will"),
        SCREEN("The ship screen at the helm (sneak and use it) gives the same orders and shows every hand's morale");

        static final String TITLE = "Crew and Orders";
        private final String english;

        Orders(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return CREW_ORDERS;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link BoardingScene} (combat/grapple, combat/boarding, the capture of worldsim/materialize, design.md §8.3, §8.4). */
    public enum Boarding implements SceneLine {
        HOOK("Throw your grappling hook, or shoot it from a musket, onto the other ship: it bites into her hull"),
        HAUL("While you stand on your deck holding the line, it hauls both hulls together until they lie alongside"),
        PLANK("Use a boarding plank on the top of your gunwale: one straight board, up to 6 blocks, onto her deck"),
        LAID("It lies rail top to rail top. Three wooden slabs over two iron nuggets make one"),
        BOARD("Or blow Board! on the whistle: your hands cross the plank or a two-block gap and fight; Back aboard calls them home"),
        CROSS("Cross over: the plank is a thin board you walk on like a deck"),
        FIGHT("Her crew fights with the same swordplay as you: watch the raised arm, parry just before the blow, riposte"),
        CREW_FIGHT("Your boarders fence by the same rules; a hand with an empty fist takes a sword from your chests"),
        PRIZE("A ship of the seas is yours once none of her fighters is left and you have stood aboard for 5 seconds"),
        LAW("Taking a merchant is piracy; a pirate ship taken under a letter of marque pays prize money"),
        PART("When the hulls drift more than 1.5 blocks apart, the plank breaks and drops");

        static final String TITLE = "Boarding";
        private final String english;

        Boarding(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return BOARDING;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link FlagsAndAllegianceScene} (ship/decor/flag, law flags, design.md §4.7). */
    public enum Flags implements SceneLine {
        POLE("A flagpole flies your colours. Stack up to 6 for a tall pole: the flag always flies at the top"),
        PEDESTAL("A flag pedestal carved from a log seats the pole on any deck, and you can work the flag from it"),
        HOIST("Use the pole with a flag: in 3 seconds it runs up and flies, and the old one comes back to you"),
        COLOURS("The highest flag on her poles is the flag she flies: no flag or a merchant flag is neutral to all"),
        JOLLY("Under the Jolly Roger pirates leave you be, but the navy attacks on sight, and being seen is a crime"),
        FALSE("A navy flag lets you pass the navy, unless you are a suspect or worse: then navy watchers may see through it"),
        CAUGHT("Caught under false colours, you are charged heavily and the navy hunts your ship for five minutes"),
        NEST("A navy ship with a manned crow's nest sees your flag half again as far and half again as fast"),
        STRIKE("Use the pole with an empty hand to strike your colours: you surrender, and navy and pirates stop attacking"),
        STRUCK("Firing on a ship that has struck, or on one under a merchant flag or a banner, is a crime"),
        BANNER("Any banner flies as a custom flag with its colours and patterns, and counts as neutral");

        static final String TITLE = "Flags and Allegiance";
        private final String english;

        Flags(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return FLAGS_AND_ALLEGIANCE;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link CrowsNestScene} (station/lookout, ship/rigging ratlines, design.md §6). */
    public enum Nest implements SceneLine {
        NEST("The crow's nest stands on top of a mast: a log, a fence, a wall or any block with a solid top"),
        RATLINES("Ratlines lead up to it: sloped nets from the deck, then nets hung on the mast like a ladder"),
        RIM("Only its floor is solid: climb up and step in over the rim, sneaking to stay put"),
        POST("Use the whistle on a hand, then on the nest: he takes his post up there and keeps watch"),
        SCAN("Every 3 seconds the lookout scans 160 blocks round the ship and calls what is new to everyone aboard"),
        CALL("'Sail two points to starboard, 140 blocks' over the hotbar, a bell from the nest, a mark on the compass"),
        WHAT("He calls ships by their flags, land, sharks and the kraken; again when they close in, or after 2 minutes"),
        SELF("Stand in the nest yourself and you keep the lookout"),
        POLE("A flagpole may stand in the nest: the flag flies above it, and the nest still keeps the lookout");

        static final String TITLE = "The Crow's Nest";
        private final String english;

        Nest(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return CROWS_NEST;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link ShipsBellScene} (ship/decor {@code ShipsBellBlock}, design.md §4.8). */
    public enum Bell implements SceneLine {
        MOUNT("Hang the ship's bell on a wall, or stand it in its frame on a post or any block with a firm middle"),
        RING("Use it to ring it: the bell swings away from you and rings out"),
        SIDE("Struck from the side, it rocks at half the swing"),
        MEANING("Nothing else is tied to it: no watch, no order, and your crew does not ring it. It is your signal"),
        ALARM("In a navy outpost every bell rings the alarm when pirates come to raid it"),
        RECIPE("Three sticks over three gold ingots and an iron nugget make one");

        static final String TITLE = "The Ship's Bell";
        private final String english;

        Bell(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return SHIPS_BELL;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link GalleyAndProvisionsScene} (crew/galley, crew/provisions, crew/upkeep, design.md §7.3, §7.4). */
    public enum Galley implements SceneLine {
        PANTRY("The pantry is the ship's food store: anything edible counts, by its nourishment"),
        WATER("A water barrel holds 16 rations of fresh water: a water bucket adds 3, a bottle 1"),
        DAWN("Every dawn each hand eats a loaf and a bit and drinks one ration from the stores aboard"),
        MEALS("At noon and at sunset the free hands sit down by the pantry for a few seconds: the meal is only a sight"),
        SHORT("Hungry hands lose morale and work at 75% speed; thirsty ones lose it faster and work at half speed"),
        RUM("A ration of rum cheers them; more than the ration slows them down for a while"),
        SCURVY("After 8 days without fresh food comes scurvy: weakness and lost morale. A lime, any fruit or fresh food cures it"),
        SPOIL("Fresh food spoils after 5 days in the pantry; hardtack, salted fish, salt pork and bread keep");

        static final String TITLE = "The Galley and Provisions";
        private final String english;

        Galley(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return GALLEY_AND_PROVISIONS;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link HammocksAndRestScene} (crew/hammock, crew/morale, design.md §7.1). */
    public enum Rest implements SceneLine {
        HANG("Hang a hammock between two supports at the same height: fence posts, walls, logs or the hull's side"),
        BUNKS("Each hammock is a bunk: a ship takes on as many hands as she has hammocks"),
        NIGHTFALL("At nightfall every hand not at a station turns in to the nearest free hammock"),
        DAWN("At dawn he gets up: a night in a hammock gives 5 morale, a night aboard without one costs 10"),
        DUTY("Hands at a station stay on duty all night, and an order at night gets sleepers up at once"),
        PLAYER("You sleep in a hammock too, ashore or aboard, and you wake and respawn beside it"),
        COT("The sea cot is a bed for you, ashore or on an assembled ship; the crew sleep only in hammocks");

        static final String TITLE = "Hammocks and Rest";
        private final String english;

        Rest(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return HAMMOCKS_AND_REST;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link SwivelGunScene} (combat/cannon {@code SwivelGunBlock}, design.md §8.2). */
    public enum Swivel implements SceneLine {
        MOUNT("The swivel gun mounts on a fence, a wall, iron bars, brig bars or any block with a solid top"),
        POWDER("Load it like the cannon: gunpowder first"),
        SHOT("Then a cannonball"),
        AIM("Hold use with an empty hand: it turns wherever you look, all round, from 30° down to 45° up"),
        FIRE("Let go to fire"),
        WEAKER("It hits less hard and less far than the cannon, breaks no planks, and cools for 3 seconds before new powder"),
        AMMO("Chain shot and grapeshot are for the cannon only: the swivel takes a cannonball"),
        CREW("A hand at the swivel fires it on the whistle's Fire!, and loads it on Load! from powder and shot nearby");

        static final String TITLE = "The Swivel Gun";
        private final String english;

        Swivel(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return SWIVEL_GUN;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link OarsScene} (sailing/oars, design.md §5.3b). */
    public enum Oars implements SceneLine {
        ROW("Row on the whistle: every hand who is not at a station takes an oar. No block is needed"),
        RAIL("Without a bench a hand rows standing at the rail, the sides taken in turn so the ship runs straight"),
        PUSH("Each oar pushes along the bow, in a calm or against the wind, up to 3 blocks a second; sails add to it"),
        BENCH("An oar bench at the rail is a station: a hand seated on it pulls a quarter harder than one standing"),
        SHIP("Use the same entry again, now Ship oars, or give any other order: the oars come in and the hands go back"),
        FASTER("The hands ship the oars by themselves once the sails drive the ship faster than the oars can");

        static final String TITLE = "Oars";
        private final String english;

        Oars(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return OARS;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link OnDeckScene} (crew/lean and the boarding nets of ship/rigging, RAIL5; design.md §4.8). */
    public enum OnDeck implements SceneLine {
        IDLE("A hand with nothing to do may lean on the railing a while"),
        LOOK("He watches the sea, turning his head now and then"),
        CALLED("Any order calls him off at once"),
        NET("String a ratline net between two chainplates on top of one railing"),
        ALONG("It hangs along the rail as a boarding net"),
        HELD("A boarder coming over the rail is held a moment"),
        OVER("Then he may climb over: have your blades ready"),
        PASS("Your own crew pass it freely: Board! on the whistle sends them over"),
        BACK("The same entry, now Back aboard, calls them home"),
        CUT("Three cutlass blows cut a boarding net down");

        static final String TITLE = "On Deck";
        private final String english;

        OnDeck(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return ON_DECK;
        }

        @Override
        public String english() {
            return english;
        }
    }
}
