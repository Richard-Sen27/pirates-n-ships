package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;

import java.util.Locale;
import java.util.Optional;

/**
 * A captain's title (docs/design.md §15, HON1), pure: which title a career earns and how it goes in front of a name.
 *
 * <p>Precedence: a navy rank in service ("Lt."), then a letter of marque ("Privateer"), then infamy from Buccaneer up
 * ("Dread Pirate"). The navy rank and infamy exclude each other already (CAR1); a letter is only granted up to
 * Buccaneer and voided by the deeds that raise infamy, so the letter ranking above infamy only matters for a Buccaneer
 * who holds one: the navy's licence is the title they show. A midshipman's or deckhand's career without a letter still
 * has the midshipman's "Mid."; a plain deckhand has no title.
 *
 * <p>The English text is fixed here (ship names are stored strings); the scoreboard prefix uses {@link Title#key()}
 * with this text as the fallback, so it can be translated.
 */
public final class CareerTitles {

    /** Whose side a title belongs to (the prefix colour). */
    public enum Side { NAVY, PRIVATEER, PIRATE }

    public enum Title {
        MIDSHIPMAN("Mid.", Side.NAVY),
        LIEUTENANT("Lt.", Side.NAVY),
        CAPTAIN("Capt.", Side.NAVY),
        COMMODORE("Cdre.", Side.NAVY),
        ADMIRAL("Adm.", Side.NAVY),
        PRIVATEER("Privateer", Side.PRIVATEER),
        BUCCANEER("Buccaneer", Side.PIRATE),
        DREAD_PIRATE("Dread Pirate", Side.PIRATE),
        PIRATE_LORD("Pirate Lord", Side.PIRATE);

        private final String text;
        private final Side side;

        Title(String text, Side side) {
            this.text = text;
            this.side = side;
        }

        /** English text, e.g. {@code Capt.} */
        public String text() {
            return text;
        }

        public Side side() {
            return side;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String key() {
            return Constants.MOD_ID + ".career.title." + id();
        }
    }

    private CareerTitles() {
    }

    /** The title of a career, or empty for none. */
    public static Optional<Title> of(NavyRank navy, boolean enlisted, InfamyRank infamy, LetterState letter) {
        if (enlisted && navy != NavyRank.NONE) {
            return Optional.of(switch (navy) {
                case NONE, MIDSHIPMAN -> Title.MIDSHIPMAN;
                case LIEUTENANT -> Title.LIEUTENANT;
                case CAPTAIN -> Title.CAPTAIN;
                case COMMODORE -> Title.COMMODORE;
                case ADMIRAL -> Title.ADMIRAL;
            });
        }
        if (letter == LetterState.ACTIVE) return Optional.of(Title.PRIVATEER);
        return switch (infamy) {
            case DECKHAND -> Optional.empty();
            case BUCCANEER -> Optional.of(Title.BUCCANEER);
            case DREAD_CAPTAIN -> Optional.of(Title.DREAD_PIRATE);
            case PIRATE_LORD -> Optional.of(Title.PIRATE_LORD);
        };
    }

    public static Optional<Title> of(CareerRecord r) {
        return of(r.navy(), r.enlisted(), r.infamy(), r.letter());
    }

    public static Optional<Title> byId(String id) {
        for (Title t : Title.values()) if (t.id().equals(id)) return Optional.of(t);
        return Optional.empty();
    }

    /**
     * {@code name} without a leading title ("Capt. Black Gull" → "Black Gull"), so a renamed ship never carries two
     * titles and nobody can type a title they do not hold. Only whole titles followed by a space count; repeated
     * titles are all removed.
     */
    public static String stripTitle(String name) {
        String s = name.strip();
        boolean found = true;
        while (found) {
            found = false;
            for (Title t : Title.values()) {
                String p = t.text() + " ";
                if (s.regionMatches(true, 0, p, 0, p.length()) && s.length() > p.length()) {
                    s = s.substring(p.length()).strip();
                    found = true;
                }
            }
        }
        return s;
    }

    /**
     * The ship name stored when a player with {@code title} names a ship {@code typed}: any typed title removed, then
     * the player's own title in front ("Capt. Black Gull"). An empty name stays empty.
     */
    public static String shipName(Optional<Title> title, String typed) {
        String bare = stripTitle(typed);
        if (bare.isEmpty() || title.isEmpty()) return bare;
        return title.get().text() + " " + bare;
    }

    /** Lang entries (datagen): the titles' text under {@link Title#key()}. */
    public static void lang(LangBuilder lang) {
        for (Title t : Title.values()) lang.add(t.key(), t.text());
    }
}
