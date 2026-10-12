package com.richardsenger.piratesnships.core.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The advancement tab (ADV1) against its generated JSON, the generated lang and {@code docs/advancements.md}; run from
 * {@code common/}. On a mismatch with the doc the expected table is written to
 * {@code build/advancements-table.md}, ready to paste.
 */
class AdvancementTreeTest {

    private static final Path ADVANCEMENTS = Path.of("src/generated/resources/data/pirates_n_ships/advancement");
    private static final Path LANG = Path.of("src/generated/resources/assets/pirates_n_ships/lang/en_us.json");
    private static final Path DOC = Path.of("../docs/advancements.md");
    private static final Path EXPECTED_TABLE = Path.of("build/advancements-table.md");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------------------------ the tree itself

    @Test
    void oneRootUniqueIdsParentsFirstAndShortTexts() {
        Set<String> seen = new HashSet<>();
        int roots = 0;
        for (AdvancementTree.Node n : AdvancementTree.nodes()) {
            assertTrue(seen.add(n.path()), "duplicate id " + n.path());
            if (n.parent() == null) {
                roots++;
            } else {
                assertTrue(seen.contains(n.parent()), n.path() + ": parent " + n.parent() + " missing or after it");
            }
            assertTrue(n.title().length() <= 40, n.path() + ": title longer than 40");
            assertTrue(n.description().length() <= 120, n.path() + ": description longer than 120");
            assertFalse(n.criteria().isEmpty(), n.path() + ": no criteria");
            for (AdvancementTree.Crit c : n.criteria()) {
                if (c.type() == AdvancementTree.Type.MOD) {
                    assertTrue(TriggerIds.ALL.contains(c.trigger()), n.path() + ": unknown trigger " + c.trigger());
                }
            }
        }
        assertEquals(1, roots, "roots");
        assertEquals("root", AdvancementTree.nodes().get(0).path());
        assertEquals(TriggerIds.ALL.size(), new HashSet<>(TriggerIds.ALL).size(), "duplicate trigger ids");
    }

    @Test
    void everyTriggerIsUsedByTheTree() {
        Set<String> used = new HashSet<>();
        AdvancementTree.nodes().forEach(n -> n.criteria().forEach(c -> used.add(c.trigger())));
        for (String id : TriggerIds.ALL) assertTrue(used.contains(id), "trigger " + id + " drives no advancement");
    }

    @Test
    void hiddenAreTheKrakenTheTreasureAndTheCapture() {
        List<String> hidden = AdvancementTree.nodes().stream().filter(AdvancementTree.Node::hidden).map(AdvancementTree.Node::path).toList();
        assertEquals(List.of("combat/capture", "combat/kraken", "world/treasure"), hidden.stream().sorted().toList());
    }

    @Test
    void experienceScalesRoundsAndNeverGoesNegative() {
        AdvancementTree.Node task = AdvancementTree.byPath("shipwright/launch_ship").orElseThrow();
        assertEquals(AdvancementTree.XP_TASK, AdvancementTree.experience(task, 1.0));
        assertEquals(0, AdvancementTree.experience(task, 0.0));
        assertEquals(Math.round(AdvancementTree.XP_TASK * 1.5), AdvancementTree.experience(task, 1.5));
        assertEquals(0, AdvancementTree.experience(task, -2.0));
        assertEquals(0, AdvancementTree.experience(AdvancementTree.nodes().get(0), 1.0), "the root pays nothing");
    }

    // ------------------------------------------------------------------ pure parts

    @Test
    void instanceMatchesKindAndValue() {
        var any = new ModTrigger.Instance(Optional.empty(), Optional.empty(), MinMaxBounds.Ints.ANY);
        var navy = new ModTrigger.Instance(Optional.empty(), Optional.of("navy_outpost"), MinMaxBounds.Ints.ANY);
        var big = new ModTrigger.Instance(Optional.empty(), Optional.empty(), MinMaxBounds.Ints.atLeast(500));
        assertTrue(any.matches("", 0));
        assertTrue(navy.matches("navy_outpost", 1));
        assertFalse(navy.matches("pirate_island", 1));
        assertFalse(big.matches("", 499));
        assertTrue(big.matches("", 500));
    }

    @Test
    void recordCountsLoyalDaysDoubloonsAndChapters() {
        UUID ship = UUID.randomUUID();
        AdvancementRecord r = AdvancementRecord.EMPTY;
        for (int i = 0; i < 3; i++) r = r.crewDay(ship.toString(), false);
        assertEquals(3, r.loyalDays(ship.toString()));
        r = r.crewDay(ship.toString(), true);
        assertEquals(0, r.loyalDays(ship.toString()), "a desertion starts the count over");
        r = r.earned(5).earned(-3).earned(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, r.doubloons(), "saturates, ignores negatives");
        r = r.withChapter("a").withChapter("a").withChapter("b");
        assertEquals(List.of("a", "b"), r.chapters());
        assertEquals(List.of("x"), r.withRewarded("x").withRewarded("x").rewarded(), "rewarded once");
    }

    // ------------------------------------------------------------------ the generated files

    @Test
    void generatedAdvancementsMatchTheTree() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> expectedFiles = new HashSet<>();
        for (AdvancementTree.Node n : AdvancementTree.nodes()) {
            Path file = ADVANCEMENTS.resolve(n.path() + ".json");
            expectedFiles.add(ADVANCEMENTS.relativize(file).toString().replace('\\', '/'));
            assertTrue(Files.exists(file), "missing " + file + " (run ./gradlew :neoforge:runData)");
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (n.parent() == null) {
                assertNull(json.get("parent"), "the root has a parent");
            } else {
                assertEquals("pirates_n_ships:" + n.parent(), json.get("parent").getAsString(), n.path() + " parent");
            }
            JsonObject display = json.getAsJsonObject("display");
            String icon = display.getAsJsonObject("icon").get("id").getAsString();
            assertEquals(n.icon(), icon, n.path() + " icon");
            assertIconNamed(lang, icon, n.path());
            assertEquals(n.titleKey(), display.getAsJsonObject("title").get("translate").getAsString());
            assertEquals(n.title(), lang.get(n.titleKey()).getAsString(), n.path() + " title lang");
            assertEquals(n.description(), lang.get(n.descriptionKey()).getAsString(), n.path() + " description lang");
            String frame = display.has("frame") ? display.get("frame").getAsString() : "task";
            assertEquals(n.frame().id(), frame, n.path() + " frame");
            boolean hidden = display.has("hidden") && display.get("hidden").getAsBoolean();
            assertEquals(n.hidden(), hidden, n.path() + " hidden");
            assertFalse(json.has("rewards") && json.getAsJsonObject("rewards").has("experience"),
                    n.path() + ": experience belongs to AdvancementXp, not the file");
            JsonObject criteria = json.getAsJsonObject("criteria");
            assertEquals(n.criteria().size(), criteria.size(), n.path() + " criteria");
            for (Map.Entry<String, JsonElement> e : criteria.entrySet()) {
                ResourceLocation trigger = ResourceLocation.parse(e.getValue().getAsJsonObject().get("trigger").getAsString());
                if (trigger.getNamespace().equals("pirates_n_ships")) {
                    assertTrue(TriggerIds.ALL.contains(trigger.getPath()), n.path() + ": unregistered trigger " + trigger);
                } else {
                    assertTrue(BuiltInRegistries.TRIGGER_TYPES.containsKey(trigger), n.path() + ": unknown trigger " + trigger);
                }
            }
        }
        try (Stream<Path> files = Files.walk(ADVANCEMENTS)) {
            List<String> extra = files.filter(Files::isRegularFile)
                    .map(p -> ADVANCEMENTS.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.startsWith("recipes/") && !expectedFiles.contains(p)).toList();
            assertTrue(extra.isEmpty(), "advancements not in the tree: " + extra);
        }
    }

    private static void assertIconNamed(JsonObject lang, String icon, String path) {
        ResourceLocation id = ResourceLocation.parse(icon);
        if (!id.getNamespace().equals("pirates_n_ships")) {
            assertTrue(BuiltInRegistries.ITEM.containsKey(id), path + ": unknown vanilla icon " + icon);
            return;
        }
        String suffix = "pirates_n_ships." + id.getPath();
        assertTrue(lang.has("item." + suffix) || lang.has("block." + suffix), path + ": icon " + icon + " has no name in the lang");
    }

    // ------------------------------------------------------------------ the doc

    @Test
    void docTableMatchesTheTree() throws IOException {
        String expected = table();
        Files.createDirectories(EXPECTED_TABLE.getParent());
        Files.writeString(EXPECTED_TABLE, expected, StandardCharsets.UTF_8);
        Map<String, String> rows = docRows(Files.readAllLines(DOC, StandardCharsets.UTF_8));
        Map<String, String> want = new LinkedHashMap<>();
        for (String line : expected.split("\n")) {
            if (line.startsWith("| `")) want.put(id(line), line.trim());
        }
        assertEquals(new ArrayList<>(want.keySet()), new ArrayList<>(rows.keySet()),
                "docs/advancements.md rows (expected table in common/" + EXPECTED_TABLE + ")");
        for (Map.Entry<String, String> e : want.entrySet()) {
            assertEquals(e.getValue(), rows.get(e.getKey()), "docs/advancements.md row " + e.getKey()
                    + " (expected table in common/" + EXPECTED_TABLE + ")");
        }
    }

    /** The table as docs/advancements.md writes it. */
    static String table() {
        StringBuilder sb = new StringBuilder();
        sb.append("| id | parent | title | description | icon | frame | criteria | reward |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        for (AdvancementTree.Node n : AdvancementTree.nodes()) {
            String icon = n.icon().startsWith("pirates_n_ships:") ? n.icon().substring("pirates_n_ships:".length()) : n.icon();
            sb.append("| `").append(n.path()).append("` | ")
                    .append(n.parent() == null ? "-" : "`" + n.parent() + "`").append(" | ")
                    .append(n.title()).append(" | ")
                    .append(n.description()).append(" | ")
                    .append('`').append(icon).append("` | ")
                    .append(n.frame().id()).append(n.hidden() ? ", hidden" : "").append(" | ")
                    .append('`').append(n.describeCriteria()).append("` | ")
                    .append(n.xp() == 0 ? "-" : n.xp() + " XP").append(" |\n");
        }
        return sb.toString();
    }

    /** The rows between the {@code tree:start} and {@code tree:end} markers. */
    private static Map<String, String> docRows(List<String> lines) {
        Map<String, String> rows = new LinkedHashMap<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.contains("tree:start")) inside = true;
            else if (line.contains("tree:end")) break;
            else if (inside && line.startsWith("| `")) rows.put(id(line), line.trim());
        }
        return rows;
    }

    private static String id(String row) {
        int a = row.indexOf('`') + 1;
        return row.substring(a, row.indexOf('`', a));
    }

    @Test
    void tableHelperIsStable() {
        assertNotNull(table());
        assertTrue(table().contains("| `root` | - | Pirates 'n' Ships |"));
    }
}
