package com.richardsenger.piratesnships.core.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard of the message audit (MSG1, docs/messages.md), run from {@code common/}: every kept action-bar message
 * fits the action bar at GUI scale 3 on a 1080p window, and ship handling never writes to the chat.
 */
class MessageAuditTest {

    private static final Path LANG = Path.of("src/generated/resources/assets/pirates_n_ships/lang/en_us.json");
    private static final Path KEPT = Path.of("src/test/resources/msg1/action_bar_keys.txt");
    private static final Path SOURCES = Path.of("src/main/java/com/richardsenger/piratesnships");

    /** Characters an action-bar line may have: 640 GUI px at scale 3 on 1920×1080, about 6 px a character, with a margin. */
    static final int MAX_CHARACTERS = 40;
    /** What a {@code %s} counts: a number such as "140". */
    static final int PLACEHOLDER_CHARACTERS = 3;
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");

    /** The ship-handling packages: their messages go to the action bar only. */
    static final List<String> SHIP_HANDLING = List.of("sailing", "station", "ship", "combat/cannon", "combat/grapple",
            "combat/boarding", "crew/npc");
    /**
     * Files allowed a chat line for now. The lookout's sighting is LKO2's (it becomes an action-bar cue there); remove the
     * entry when LKO2 lands.
     */
    static final Set<String> ALLOWED = Set.of("station/lookout/Lookouts.java");
    /** A chat send: {@code displayClientMessage(..., false)}, {@code sendSystemMessage(} or {@code sendMessage(}. */
    private static final Pattern CHAT = Pattern.compile(
            "displayClientMessage\\((?:[^;]|\\n)*?,\\s*false\\s*\\)|\\.sendSystemMessage\\(|\\.sendMessage\\(");

    static int length(String text) {
        Matcher m = PLACEHOLDER.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return m.replaceAll("").length() + n * PLACEHOLDER_CHARACTERS;
    }

    @Test
    void lengthCountsPlaceholdersAsNumbers() {
        assertEquals(37, length("Too deep: no ground within %s blocks"));
        assertEquals(15, length("%2$s crew to %1$s"));
    }

    @Test
    void keptActionBarMessagesFitTheScreen() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        List<String> keys = Files.readAllLines(KEPT).stream().map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        assertTrue(keys.size() > 100, "only " + keys.size() + " kept action-bar keys in " + KEPT);
        List<String> problems = new ArrayList<>();
        for (String key : keys) {
            if (!lang.has(key)) {
                problems.add(key + ": not in en_us.json (run ./gradlew :neoforge:runData, or remove it from " + KEPT + ")");
                continue;
            }
            String text = lang.get(key).getAsString();
            int n = length(text);
            if (n > MAX_CHARACTERS) problems.add(key + ": " + n + " characters: \"" + text + "\"");
        }
        assertTrue(problems.isEmpty(), "action-bar messages longer than " + MAX_CHARACTERS
                + " characters (shorten them, docs/messages.md):\n" + String.join("\n", problems));
    }

    @Test
    void shipHandlingNeverUsesTheChat() throws IOException {
        List<String> problems = new ArrayList<>();
        int files = 0;
        for (String pkg : SHIP_HANDLING) {
            Path dir = SOURCES.resolve(pkg);
            assertTrue(Files.isDirectory(dir), "no package " + dir);
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(dir)) {
                sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            for (Path p : sources) {
                String rel = SOURCES.relativize(p).toString().replace('\\', '/');
                String name = p.getFileName().toString();
                // commands answer in the chat by design; GameTests and tests catch the messages of their mock players
                if (name.endsWith("GameTests.java") || name.endsWith("Commands.java") || ALLOWED.contains(rel)) continue;
                files++;
                String src = Files.readString(p);
                Matcher m = CHAT.matcher(src);
                while (m.find()) {
                    int line = 1 + (int) src.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                    problems.add(rel + ":" + line + ": " + m.group().lines().findFirst().orElse(""));
                }
            }
        }
        assertTrue(files > 100, "only " + files + " ship-handling sources scanned");
        assertTrue(problems.isEmpty(), "chat lines in ship handling (use the action bar, MSG1):\n" + String.join("\n", problems));
    }
}
