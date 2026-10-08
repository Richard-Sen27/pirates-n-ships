package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.rpg.career.CareerTitles;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import com.richardsenger.piratesnships.rpg.career.NavyRank;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the rank box shows and where (HON1, pure, tested with JUnit). Rows top to bottom: the rank (the navy rank in
 * service, else "Privateer" with a letter, else the infamy rank), the navy and pirate reputation once the server sent
 * them, the letter of marque unless the player never held one, and prize money waiting when there is any.
 */
public final class RankHudLayout {

    public static final String HUD = "hud." + Constants.MOD_ID + ".career.";
    public static final String KEY_REPUTATION = HUD + "reputation";
    public static final String KEY_LETTER = HUD + "letter";
    public static final String KEY_PRIZE = HUD + "prize";

    /** Inner padding and line height (font line 9 + 1). */
    public static final int PAD = 3;
    public static final int LINE_H = 10;

    public static final int COLOUR_NAVY = 0xFF55FFFF;
    public static final int COLOUR_PRIVATEER = 0xFF55FF55;
    public static final int COLOUR_PIRATE = 0xFFFF5555;
    public static final int COLOUR_PLAIN = 0xFFEEE0BC;

    public enum Row { TITLE, REPUTATION, LETTER, PRIZE }

    /** The client's career as the box needs it. */
    public record View(NavyRank navy, boolean enlisted, InfamyRank infamy, LetterState letter, long prizeMoney,
                       boolean reputationKnown) {
    }

    /** A screen rectangle. */
    public record Rect(int x, int y, int w, int h) {
    }

    private RankHudLayout() {
    }

    public static List<Row> rows(View v) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.TITLE);
        if (v.reputationKnown()) rows.add(Row.REPUTATION);
        if (v.letter() != LetterState.NONE) rows.add(Row.LETTER);
        if (v.prizeMoney() > 0) rows.add(Row.PRIZE);
        return rows;
    }

    /** Translation key of the title row: the rank's name, or the privateer's title. */
    public static String titleKey(View v) {
        if (v.enlisted() && v.navy() != NavyRank.NONE) return v.navy().nameKey();
        if (v.letter() == LetterState.ACTIVE) return CareerTitles.Title.PRIVATEER.key();
        return v.infamy().nameKey();
    }

    /** Colour of the title row: the side of the career's title, plain without one. */
    public static int titleColour(View v) {
        Optional<CareerTitles.Title> t = CareerTitles.of(v.navy(), v.enlisted(), v.infamy(), v.letter());
        if (t.isEmpty()) return COLOUR_PLAIN;
        return switch (t.get().side()) {
            case NAVY -> COLOUR_NAVY;
            case PRIVATEER -> COLOUR_PRIVATEER;
            case PIRATE -> COLOUR_PIRATE;
        };
    }

    /** Box size for rows of the given text widths. */
    public static int width(int widestText) {
        return Math.max(0, widestText) + 2 * PAD;
    }

    public static int height(int rowCount) {
        return Math.max(1, rowCount) * LINE_H - 1 + 2 * PAD;
    }

    /**
     * The box's rectangle: {@code x}/{@code y} from the left/top edge, or from the right/bottom edge when negative
     * (client config {@code career_hud.x/y}); always kept on screen (a box larger than the screen keeps its top left
     * corner visible).
     */
    public static Rect place(int x, int y, int w, int h, int guiWidth, int guiHeight) {
        int px = x >= 0 ? x : guiWidth + x - w;
        int py = y >= 0 ? y : guiHeight + y - h;
        px = Math.max(0, Math.min(px, guiWidth - w));
        py = Math.max(0, Math.min(py, guiHeight - h));
        return new Rect(px, py, w, h);
    }

    /** Signed reputation as shown ("+25", "-10", "0"). */
    public static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY_REPUTATION, "Navy %s  Pirates %s")
                .add(KEY_LETTER, "Letter of marque: %s")
                .add(KEY_PRIZE, "Prize money: %s");
    }
}
