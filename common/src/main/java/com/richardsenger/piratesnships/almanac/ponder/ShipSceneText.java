package com.richardsenger.piratesnships.almanac.ponder;

/**
 * The text of the ship mechanics' Ponder scenes (PND1, docs/design.md §3.4, docs/almanac.md "Scenes"), without any
 * Ponder class: one enum of lines per scene, in the order they appear, each with its scene's path and title. Every
 * fact is the game's (docs/guide.md and the code each scene names); numbers are the default server config.
 */
public final class ShipSceneText {

    public static final String SHIP_ASSEMBLY = "ship_assembly";
    public static final String SAILS_AND_WINCH = "sails_and_winch";
    public static final String STEERING = "steering";
    public static final String ANCHOR_AND_CAPSTAN = "anchor_and_capstan";
    public static final String PUMPS_AND_FLOODING = "pumps_and_flooding";
    public static final String HULL_REPAIR = "hull_repair";
    public static final String MOORING = "mooring";
    public static final String CARGO_AND_WEIGHT = "cargo_and_weight";

    private ShipSceneText() {
    }

    /** {@link ShipAssemblyScene} (ship/assembly, design.md §4.1). */
    public enum Assembly implements SceneLine {
        BUILD("Build your ship from any blocks, right on the water: this hull keeps a dry hold below the waterline"),
        GAP("Everything that touches the hull becomes part of the ship, so leave a gap of water to the shore or a dock"),
        HELM("Place a helm on deck. Its helmsman stands behind it, and the way he looks is the bow"),
        USE("Use the helm: every block connected to it, up to 2048, becomes one ship"),
        TERRAIN("Air, water and terrain such as sand, dirt and stone are never taken along"),
        AFLOAT("Now she is one ship: she floats, rolls with the sea and keeps her hold dry"),
        ABOARD("Blocks placed on her afterwards become part of her at once"),
        DISASSEMBLE("To take her apart, sneak and use the helm with an empty hand and press Disassemble on the ship screen twice"),
        SNAP("She must lie nearly still and level: her heading is rounded to the nearest quarter turn and her blocks are set back into the world");

        static final String TITLE = "Assembling a Ship";
        private final String english;

        Assembly(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return SHIP_ASSEMBLY;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link SailsAndWinchScene} (sailing/block yards and winch, the sail runtime, design.md §5.2). */
    public enum Sails implements SceneLine {
        MAST("A square sail hangs between two yards on a mast of logs or fences"),
        YARDS("A yard is a straight row of yard blocks, and its middle block sits in the mast column"),
        PAIR("A second yard of the same direction 2 to 8 blocks straight below the first makes the pair one sail"),
        AREA("The cloth is drawn between the yards, not built from blocks: its area is the mean yard length times the drop, 5 × 4 = 20 here"),
        WINCH("The sail winch sets the trim of every sail on its ship"),
        FURLED("Furled, the cloth is a roll under the upper yard and gives no force"),
        HALF("Use the winch once: half sail, the cloth hangs half way down"),
        FULL("Use it again: full sail, down to the lower yard. A third time furls it again"),
        WIND("The wind fills the cloth and it bellies out to leeward. Square sails like the wind from astern; within 30° of the wind no sail drives"),
        REEF("When the wind freshens, reef to half sail (from full the winch goes round furled, then half): less cloth, less force, less heel"),
        YARD("Using an upper yard with an empty hand trims that one sail only"),
        CREW("A crew member at the winch hoists, reefs and furls on the captain's whistle");

        static final String TITLE = "Sails and the Sail Winch";
        private final String english;

        Sails(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return SAILS_AND_WINCH;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link SteeringScene} (sailing/helm, station/helm courses, design.md §5.3). */
    public enum Steering implements SceneLine {
        WHEEL("On an assembled ship, hold use on the helm to take the wheel"),
        TURN("Move the mouse or hold A and D to turn it: right turns her to starboard, left to port"),
        RUDDER("The rudder follows the wheel, up to 35° each way at three quarters of a turn"),
        LET_GO("Let go and the wheel stays where you left it; the line above the hotbar shows the rudder angle"),
        WAY("The rudder only bites while she moves through the water, and it works the other way when she goes astern"),
        CIRCLE("Hard over she turns on a circle three to four ship lengths across, at any speed: slower only takes longer"),
        HELMSMAN("A crew member can hold a course: use the captain's whistle on him, then on the helm"),
        COURSE("The whistle and the ship screen give no course yet, only a server operator can: he then steers for each mark in turn"),
        TAKE_OVER("Take the wheel yourself at any time and he lets go; release it and he steers on");

        static final String TITLE = "Steering at the Helm";
        private final String english;

        Steering(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return STEERING;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link AnchorAndCapstanScene} (station/capstan, sailing/anchor, design.md §5.3 and §6). */
    public enum Anchor implements SceneLine {
        CAPSTAN("The capstan works the anchor. It hangs outside the hull just below the deck, on the side nearer the capstan"),
        DROP("Use the capstan to let go: the anchor falls by its own weight and the chain runs out after it"),
        LANDS("It sinks about 4 blocks a second and lands on the seabed; with way on, she leaves it a few blocks astern"),
        HOLD("Once it bites, the chain pulls her up softly, never to a dead stop, and she swings to lie head to her chain"),
        DRAG("Pulling harder than it holds drags the anchor over the seabed; if the chain runs out first, it does not hold at all"),
        DEEP("Over water deeper than the chain is long (32 blocks) the capstan refuses to let go: no ground to hold"),
        RAISE("Use the capstan again to weigh anchor: the chain comes in at 2.5 blocks a second"),
        CREW("A crew member at the capstan works it on the whistle's Drop anchor and Weigh anchor"),
        WALK("While he works, the bars turn and he walks round the drum pushing them"),
        STOWED("Anchor up and stowed: she is free to sail");

        static final String TITLE = "The Anchor and the Capstan";
        private final String english;

        Anchor(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return ANCHOR_AND_CAPSTAN;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link PumpsAndFloodingScene} (ship/hull flooding, ship/hull/pump, design.md §4.5). */
    public enum Pumps implements SceneLine {
        DRY("While the hull is whole, the hold below the waterline stays dry"),
        BREACH("A hull block destroyed below the waterline is a breach: the sea runs into the room behind it"),
        RATE("The bigger and deeper the hole, the faster it comes: a 1×1 hole one block down lets in about a block a second"),
        WEIGHT("Flood water weighs her down. Rooms joined by an open door or hatch level out; a closed door holds it back"),
        PUMP("Hold use on a bilge pump to pump about a block of water a second from the highest flooded room below it"),
        REACH("Its pipe reaches 4 blocks down through the planks, so it may stand on the hold floor or on the deck above"),
        LEVEL("Against an open breach one pump only holds the level: close the hole"),
        PATCH("Use a hull patch on the edge of the hole: the inflow stops at once"),
        DRAIN("Now the pump wins, and the water goes down until the bilge is dry"),
        CREW("A crew member at the pump works it on the whistle's Man the pumps until the bilge is dry");

        static final String TITLE = "Pumps and Flooding";
        private final String english;

        Pumps(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return PUMPS_AND_FLOODING;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link HullRepairScene} (ship/hull/pump {@code HullPatchItem}, design.md §4.5). */
    public enum Repair implements SceneLine {
        BREACH("A breach lets the sea in until it is closed"),
        RECIPE("Two planks and a coal or charcoal make two hull patches"),
        AIM("Stand inside, look at the edge of the hole (the face of a hull block beside it) and use the patch"),
        CLOSED("A tarred plank fills the hole and the inflow stops at once: there is no repair time to wait out"),
        WATER("The water already inside stays until it is pumped out"),
        ONLY("A patch only goes into a real hole in an assembled ship; anywhere else it says so and is not used up"),
        SWAP("Any watertight block in the hole closes it too. Swapping the patch for a plank later opens the hole while you swap");

        static final String TITLE = "Patching a Breach";
        private final String english;

        Repair(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return HULL_REPAIR;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link MooringScene} (combat/grapple {@code MooringRingBlock} and the tie-off, design.md §8.3). */
    public enum Mooring implements SceneLine {
        ALONGSIDE("Bring her in alongside the quay"),
        PORT("Lying within 32 blocks of a port she counts as moored there: hired hands walk straight aboard"),
        RINGS("To hold her alongside, use the grappling hook with a mooring ring (four iron ingots make two) or a cleat"),
        THROW("From the deck, throw your grappling hook onto the quay: the hook bites into the stone"),
        TIE("Use the mooring ring on your own ship: the line is tied off there and you can let go"),
        HOLD("The tied line holds her to the quay, and if she drifts off it hauls her back toward the hook"),
        ASHORE("From the quay it works the other way round: hook the ship and tie off at the quay's ring. A hook caught on a ring holds twice as far"),
        CAST_OFF("To cast off, sneak and use with an empty hand: the line lets go and the hook comes back"),
        CLEATS("Cleats hold a line as rings do, and sliding along a rope needs it tied off on one of them");

        static final String TITLE = "Mooring";
        private final String english;

        Mooring(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return MOORING;
        }

        @Override
        public String english() {
            return english;
        }
    }

    /** {@link CargoAndWeightScene} (trade/cargo, sailing/ballast, design.md §4.9 and §5.2 TRM1). */
    public enum Cargo implements SceneLine {
        BALLAST("Every ship is ballasted when she is assembled, so that she floats level with her holds empty"),
        CRATES("Cargo crates, cargo barrels, pantries and water barrels grow heavier as they fill"),
        FULL("A full crate weighs as much as some forty planks"),
        AFT("Stowed aft, the load trims her by the stern, and she sits lower in the water"),
        NOT_BALLAST("Her ballast does not count the cargo: trim her yourself, with heavy loads low and amidships"),
        LEVEL("Restowed amidships she floats level again, only deeper"),
        SPEED("Laden, she gathers way and turns more slowly, and with less freeboard a breach floods her sooner"),
        LEVELS("At the wheel the rudder line shows the load: Light, Laden, Heavily laden or Overloaded");

        static final String TITLE = "Cargo and Weight";
        private final String english;

        Cargo(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return CARGO_AND_WEIGHT;
        }

        @Override
        public String english() {
            return english;
        }
    }
}
