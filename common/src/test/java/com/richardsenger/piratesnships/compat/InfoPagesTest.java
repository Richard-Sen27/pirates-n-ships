package com.richardsenger.piratesnships.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JEI info pages taken from the Almanac's lang (JEI1): the lookup rules, then the shipped en_us.json. */
class InfoPagesTest {

    private static Map<String, String> shipped;

    @BeforeAll
    static void loadShippedLang() throws Exception {
        try (InputStream in = InfoPagesTest.class.getResourceAsStream("/assets/pirates_n_ships/lang/en_us.json")) {
            assertNotNull(in, "the generated en_us.json is on the test classpath");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> m = new LinkedHashMap<>();
            json.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
            shipped = m;
        }
    }

    /** The shipped chapters' paths in book order, from their "order" fields. */
    private static List<String> bookOrder() throws Exception {
        URL dir = InfoPagesTest.class.getResource("/data/pirates_n_ships/almanac/chapters");
        assertNotNull(dir, "the generated chapters are on the test classpath");
        Map<String, Integer> order = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            for (Path f : files.toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                order.put(f.getFileName().toString().replace(".json", ""), json.has("order") ? json.get("order").getAsInt() : 1000);
            }
        }
        return order.keySet().stream().sorted(Comparator.comparing(order::get)).toList();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("pirates_n_ships", path);
    }

    private static Map<String, String> lang(String... keyValues) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) m.put(keyValues[i], keyValues[i + 1]);
        return m;
    }

    @Test
    void iconInTheInventoryChapterWinsOverEarlierChapters() {
        Map<String, String> lang = lang(
                "pirates_n_ships.almanac.ships.title", "Ships",
                "pirates_n_ships.almanac.ships.p1.text1", "Take the {item:pirates_n_ships:helm}**Helm** and sail.",
                "pirates_n_ships.almanac.blocks.title", "All blocks",
                "pirates_n_ships.almanac.blocks.p1.list1_1", "{item:pirates_n_ships:helm}**Helm** (recipe: 4 sticks (gives 2)): Steers. See [[pirates_n_ships:ships|Ships]].");
        InfoPages.Page page = InfoPages.build(lang, Map.of(id("helm"), "Helm")).get(id("helm"));
        assertEquals(new InfoPages.Page("Helm: Steers. See Ships.", "All blocks"), page);
    }

    @Test
    void theItemsChapterComesBeforeTheBlocksChapter() {
        Map<String, String> lang = lang(
                "pirates_n_ships.almanac.blocks.p1.list1_1", "{item:pirates_n_ships:hull_patch}The placed patch.",
                "pirates_n_ships.almanac.items.p1.list1_1", "{item:pirates_n_ships:hull_patch}The patch you hold.");
        assertEquals("The patch you hold.", InfoPages.build(lang, Map.of(id("hull_patch"), "Hull Patch")).get(id("hull_patch")).text());
    }

    @Test
    void withoutAnIconTheDisplayNameFindsTheParagraph() {
        Map<String, String> lang = lang(
                "pirates_n_ships.almanac.ships.p1.text1", "You saw the coast.",
                "pirates_n_ships.almanac.ships.p2.text1", "The **Saw** cuts a ship apart.",
                "pirates_n_ships.almanac.ships.p3.text1", "Hit the hull with a carpenter's hammer.");
        Map<ResourceLocation, InfoPages.Page> pages = InfoPages.build(lang,
                Map.of(id("saw"), "Saw", id("carpenters_hammer"), "Carpenter's Hammer"));
        assertEquals("The Saw cuts a ship apart.", pages.get(id("saw")).text(), "a one-word name matches its capitals only");
        assertEquals("Hit the hull with a carpenter's hammer.", pages.get(id("carpenters_hammer")).text(),
                "a longer name matches in any case");
    }

    @Test
    void pagesComeInNaturalOrderAndSkipServerChapters() {
        Map<String, String> lang = lang(
                "pirates_n_ships.almanac.commands.p1.text1", "{item:pirates_n_ships:rope}/give rope",
                "pirates_n_ships.almanac.sailing.p10.text1", "{item:pirates_n_ships:rope}Page ten.",
                "pirates_n_ships.almanac.sailing.p2.text10", "{item:pirates_n_ships:rope}Page two, tenth.",
                "pirates_n_ships.almanac.sailing.p2.text2", "{item:pirates_n_ships:rope}Page two, second.",
                "pirates_n_ships.almanac.sailing.p1.heading", "{item:pirates_n_ships:rope}A heading.");
        assertEquals("Page two, second.", InfoPages.build(lang, Map.of(id("rope"), "Rope")).get(id("rope")).text());
    }

    @Test
    void bookOrderBeforeAlphabeticalAndOneWordNamesInAnyCase() {
        Map<String, String> lang = lang(
                "pirates_n_ships.almanac.combat.p1.text1", "Board them with a paddle.",
                "pirates_n_ships.almanac.ships.p1.text1", "Row the chest with a paddle.");
        Map<ResourceLocation, String> paddle = Map.of(id("paddle"), "Paddle");
        assertEquals("Board them with a paddle.", InfoPages.build(lang, paddle).get(id("paddle")).text(), "alphabetical");
        assertEquals("Row the chest with a paddle.", InfoPages.build(lang, paddle, List.of("ships", "combat")).get(id("paddle")).text(),
                "book order");
    }

    @Test
    void noParagraphNoPage() {
        Map<String, String> lang = lang("pirates_n_ships.almanac.ships.p1.text1", "Nothing here.");
        assertTrue(InfoPages.build(lang, Map.of(id("nails"), "Nails")).isEmpty());
        assertTrue(InfoPages.build(Map.of(), Map.of(id("nails"), "Nails")).isEmpty(), "no lang at all");
    }

    @Test
    void recipeNotesAndPhraseBounds() {
        assertEquals("Flagpole: Flies a flag.", InfoPages.dropRecipeNote("Flagpole (recipe: 3 sticks (gives 2)): Flies a flag."));
        assertEquals("Broken (recipe: (", InfoPages.dropRecipeNote("Broken (recipe: ("), "unbalanced stays");
        assertTrue(InfoPages.containsPhrase("a Rope.", "Rope", false));
        assertFalse(InfoPages.containsPhrase("Ropes", "Rope", false));
        assertTrue(InfoPages.naturalCompare("text2", "text10") < 0);
        assertTrue(InfoPages.naturalCompare("list1_2", "list1_10") < 0);
    }

    /** The shipped Almanac: every item it draws an icon of gets a clean page, the helm and hull patch their lines. */
    @Test
    void shippedAlmanacGivesCleanPages() throws Exception {
        Map<ResourceLocation, String> items = new LinkedHashMap<>();
        Pattern name = Pattern.compile("^(item|block)\\.pirates_n_ships\\.([a-z0-9_]+)$");
        shipped.forEach((k, v) -> {
            Matcher m = name.matcher(k);
            if (m.matches()) items.putIfAbsent(id(m.group(2)), v);
        });
        Map<ResourceLocation, InfoPages.Page> pages = InfoPages.build(shipped, items, bookOrder());

        InfoPages.Page helm = pages.get(id("helm"));
        assertNotNull(helm, "the helm has a page");
        assertTrue(helm.text().startsWith("Helm: Assembles, steers and disassembles a ship."), helm.text());
        assertEquals("All blocks", helm.chapterTitle());
        InfoPages.Page patch = pages.get(id("hull_patch"));
        assertTrue(patch.text().contains("close the breach"), patch.text());
        assertEquals("All items", patch.chapterTitle());

        Pattern icon = Pattern.compile("\\{item:pirates_n_ships:([a-z0-9_]+)}");
        for (String text : shipped.values()) {
            Matcher m = icon.matcher(text);
            while (m.find()) {
                if (items.containsKey(id(m.group(1)))) assertTrue(pages.containsKey(id(m.group(1))), "page for " + m.group(1));
            }
        }
        for (Map.Entry<ResourceLocation, InfoPages.Page> e : pages.entrySet()) {
            String t = e.getValue().text();
            for (String markup : List.of("{item:", "**", "[[", "(recipe:")) {
                assertFalse(t.contains(markup), e.getKey() + " page still has " + markup + ": " + t);
            }
            assertFalse(t.isBlank(), e.getKey() + " page is empty");
        }
        System.out.println("InfoPagesTest: " + pages.size() + " of " + items.size() + " items have an Almanac page; without: "
                + items.keySet().stream().filter(i -> !pages.containsKey(i)).map(ResourceLocation::getPath).toList());
    }
}
