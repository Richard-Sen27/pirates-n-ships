package com.richardsenger.piratesnships.core.text;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard of FIX2, run from {@code common/}: no number in a player-facing package is formatted with the system
 * locale. Every {@code String.format(} there passes {@code Locale.ROOT} as its first argument (or goes through
 * {@link Num}), and {@code "...".formatted(} (which has no locale overload), {@code new DecimalFormat(} and a
 * locale-less {@code NumberFormat.get…Instance()} are not used. On a German system they would print "6,3".
 */
class LocaleSafeFormattingTest {

    private static final Path SOURCES = Path.of("src/main/java/com/richardsenger/piratesnships");

    /** The packages whose texts reach players: action bar, chat, screens, tooltips, HUD, lang arguments, commands. */
    static final List<String> PLAYER_FACING = List.of("sailing", "station", "ship", "combat", "crew", "trade", "law",
            "rpg", "chart", "almanac", "world", "seachest", "survival", "hazard", "hazards", "mob", "apparel", "core/text");

    /**
     * Files allowed a locale-dependent format, by path under the root package, each with its reason. Empty: the
     * debug strings in these packages (toString, log lines) use {@link Num#format} too, so a pasted log reads the same
     * on every machine. GameTest sources ({@code *GameTests*.java}) are skipped as a rule: their strings are assertion
     * and log messages of the test run, never shown to a player.
     */
    static final Map<String, String> ALLOWED = Map.of();

    /** {@code String.format(} not followed by {@code Locale.ROOT} or {@code java.util.Locale.ROOT} as first argument. */
    private static final Pattern FORMAT = Pattern.compile("\\bString\\.format\\((?!\\s*(?:java\\.util\\.)?Locale\\.ROOT\\s*,)");
    private static final Pattern FORMATTED = Pattern.compile("\\.formatted\\(");
    private static final Pattern DECIMAL_FORMAT = Pattern.compile("\\bnew\\s+DecimalFormat\\(");
    private static final Pattern NUMBER_FORMAT = Pattern.compile("\\bNumberFormat\\.get\\w*Instance\\(\\s*\\)");
    private static final List<Pattern> BAD = List.of(FORMAT, FORMATTED, DECIMAL_FORMAT, NUMBER_FORMAT);

    /**
     * Line comments and block comments blanked out (newlines kept, so line numbers stay right). The block comment
     * is matched with a character class, not {@code (?:.|\n)*?}: Java's regex engine recurses once per character
     * of an alternation under a quantifier, and a long javadoc overflowed the 1 MB thread stack of the Linux release
     * runner (the first v0.1.0 run) while passing on macOS.
     */
    static String stripComments(String src) {
        Matcher m = Pattern.compile("'(?:\\\\.|[^'\\\\\\n])+'|\"(?:\\\\.|[^\"\\\\\\n])*\"|//[^\\n]*|/\\*[\\s\\S]*?\\*/")
                .matcher(src);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String hit = m.group();
            // a literal becomes an empty one (a "String.format(" inside it is no call), a comment blanks
            String keep = hit.startsWith("\"") ? "\"\"" : hit.startsWith("'") ? "' '" : hit.replaceAll("[^\\n]", " ");
            m.appendReplacement(out, Matcher.quoteReplacement(keep));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The locale-dependent format calls in {@code src}, as "line: text". */
    static List<String> findings(String src) {
        String code = stripComments(src);
        List<String> out = new ArrayList<>();
        for (Pattern p : BAD) {
            Matcher m = p.matcher(code);
            while (m.find()) {
                int line = 1 + (int) code.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                out.add(line + ": " + code.lines().skip(line - 1).findFirst().orElse("").strip());
            }
        }
        return out;
    }

    @Test
    void theScannerTellsSafeFromUnsafe() {
        assertEquals(1, findings("String s = String.format(\"%.1f\", v);").size());
        assertEquals(1, findings("String s = String.format(\n    \"%.1f\", v, Locale.ROOT);").size());
        assertEquals(1, findings("String s = \"%.1f\".formatted(v);").size());
        assertEquals(1, findings("NumberFormat f = NumberFormat.getNumberInstance();").size());
        assertEquals(1, findings("var f = new DecimalFormat(\"0.0\");").size());
        assertEquals(0, findings("String s = String.format(Locale.ROOT, \"%.1f\", v);").size());
        assertEquals(0, findings("String s = String.format(java.util.Locale.ROOT,\n \"%.1f\", v);").size());
        assertEquals(0, findings("String s = Num.one(v); // not String.format(\"%.1f\", v)").size());
        assertEquals(0, findings("/** Like String.format(\"%s\"). */ String s = Num.format(\"%.1f\", v);").size());
        assertEquals(0, findings("String s = \"String.format(\";").size());
    }

    @Test
    void playerFacingNumbersIgnoreTheSystemLocale() throws IOException {
        List<String> problems = new ArrayList<>();
        int files = 0;
        for (String pkg : PLAYER_FACING) {
            Path dir = SOURCES.resolve(pkg);
            assertTrue(Files.isDirectory(dir), "no package " + dir);
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(dir)) {
                sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            for (Path p : sources) {
                String rel = SOURCES.relativize(p).toString().replace('\\', '/');
                if (p.getFileName().toString().contains("GameTests") || ALLOWED.containsKey(rel)) continue;
                files++;
                for (String f : findings(Files.readString(p))) problems.add(rel + ":" + f);
            }
        }
        assertTrue(files > 500, "only " + files + " player-facing sources scanned");
        assertTrue(problems.isEmpty(), "numbers formatted with the system locale (a German player reads \"6,3\"; use "
                + "core.text.Num or String.format(Locale.ROOT, ...)):\n" + String.join("\n", problems));
    }
}
