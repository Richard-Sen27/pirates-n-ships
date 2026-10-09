package com.richardsenger.piratesnships.almanac.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.almanac.sync.AlmanacSyncPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Almanac's chapters generated from docs/almanac/ (ALM5, ALM6, tools/gen_almanac.py; run from {@code common/}): the
 * committed intermediate is what the generator writes now, the generated chapter files and lang are what datagen makes
 * of it, every page parses with the chapter codecs, every lang key exists, every item, link and illustration in the
 * text exists, and the whole book fits one sync payload. The item check of the full chapter codec needs our items
 * registered; AlmanacDataGameTests does that part in a running server.
 */
class AlmanacGuideTest {

    private static final String NS = "pirates_n_ships";
    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path INTERMEDIATE = REPO.resolve(AlmanacChapterProvider.INTERMEDIATE);
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path CHAPTERS = GENERATED.resolve("data/" + NS + "/" + AlmanacReloadListener.DIRECTORY);
    private static final Path LANG = GENERATED.resolve("assets/" + NS + "/lang/en_us.json");
    private static final Path ASSETS = Path.of("src/main/resources/assets/" + NS);
    private static final Path ILLUSTRATIONS = ASSETS.resolve("textures/almanac");

    /** The book is one payload; a vanilla custom payload may be up to 1 MiB, we keep half of that. */
    static final int MAX_WIRE_BYTES = 512 * 1024;
    static final long MAX_ILLUSTRATION_BYTES = 2L * 1024 * 1024;

    private static final Pattern SOURCE_FILE = Pattern.compile("\\d+-([a-z0-9-]+)\\.md");
    private static final ResourceLocation LEDGER = ResourceLocation.fromNamespaceAndPath(NS, "ledger");
    /** Item icons and chapter links: their ids are markup, not words on the page. */
    private static final Pattern MARKUP_TOKEN = Pattern.compile("\\{item:[^}]*}|\\[\\[[^\\]|]*\\|?");
    /** A command, a dotted config key, a snake_case word, a mod id or a Java/loader name. */
    private static final Pattern TECHNICAL = Pattern.compile(
            "/pirates|/locate|[a-z]+\\.[a-z_]+_[a-z_]+|\\b[a-z]+_[a-z_]+\\b|pirates_n_ships|\\bconfig\\b|gradle|neoforge|fabric|\\.java|GameTest",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_TOKEN = Pattern.compile("(?<!\\\\)\\{item:([a-z0-9_.-]+:[a-z0-9_/.-]+)}");
    private static final Pattern LINK_TOKEN = Pattern.compile("(?<!\\\\)\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?]]");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static AlmanacChapterProvider.Intermediate intermediate() {
        return AlmanacChapterProvider.read(INTERMEDIATE);
    }

    /** A chapter of the intermediate, its pages through the chapter codecs (the icon is checked separately). */
    private static Chapter chapter(ResourceLocation id, JsonObject body) {
        List<Page> pages = new ArrayList<>();
        for (JsonElement p : body.getAsJsonArray("pages")) pages.add(Page.CODEC.parse(JsonOps.INSTANCE, p).getOrThrow());
        int color = body.has("bookmark_color")
                ? AlmanacCodecs.COLOR.parse(JsonOps.INSTANCE, body.get("bookmark_color")).getOrThrow() : Chapter.DEFAULT_BOOKMARK_COLOR;
        int order = body.has("order") ? body.get("order").getAsInt() : Chapter.DEFAULT_ORDER;
        String title = AlmanacCodecs.LANG_KEY.parse(JsonOps.INSTANCE, body.get("title")).getOrThrow();
        return new Chapter(id, title, order, color, ResourceLocation.parse(body.get("icon").getAsString()), pages);
    }

    private static List<Chapter> chapters() {
        List<Chapter> out = new ArrayList<>();
        intermediate().bodies().forEach((id, body) -> out.add(chapter(id, body)));
        return out;
    }

    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
    }

    private record Run(int exit, String output) {
    }

    private static Run python(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("python3"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(REPO.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "python timed out: " + cmd);
        return new Run(p.exitValue(), output);
    }

    @Test
    void generatorUnitTestsPass() throws Exception {
        Run run = python(REPO.resolve("tools/test_gen_almanac.py").toString());
        assertEquals(0, run.exit(), "tools/test_gen_almanac.py failed:\n" + run.output());
    }

    @Test
    void committedIntermediateIsWhatTheGeneratorWrites(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("chapters.json");
        Run run = python(REPO.resolve("tools/gen_almanac.py").toString(), "--out", out.toString());
        assertEquals(0, run.exit(), "generator failed:\n" + run.output());
        assertEquals(Files.readString(out), Files.readString(INTERMEDIATE),
                AlmanacChapterProvider.INTERMEDIATE + " is out of date (run python3 tools/gen_almanac.py, then ./gradlew :neoforge:runData)");
    }

    @Test
    void oneChapterPerSourceFileInFileOrder() throws IOException {
        List<String> files;
        try (Stream<Path> list = Files.list(REPO.resolve(AlmanacChapterProvider.SOURCE))) {
            files = list.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".md")).sorted().toList();
        }
        List<Chapter> chapters = chapters();
        assertEquals(files.size(), chapters.size(), "one chapter per file of " + AlmanacChapterProvider.SOURCE);
        for (int i = 0; i < chapters.size(); i++) {
            Matcher m = SOURCE_FILE.matcher(files.get(i));
            assertTrue(m.matches(), files.get(i) + " is named <nn>-<chapter>.md");
            assertEquals(ResourceLocation.fromNamespaceAndPath(NS, m.group(1).replace('-', '_')), chapters.get(i).id(), files.get(i));
            if (i > 0) assertTrue(chapters.get(i).order() > chapters.get(i - 1).order(), chapters.get(i).id() + " order");
        }
        assertEquals(AlmanacChapterProvider.WELCOME, chapters.getFirst().id());
        assertEquals(ResourceLocation.fromNamespaceAndPath(NS, "not_yet"), chapters.getLast().id(), "'Not yet charted' comes last");
        Chapter combat = chapters.stream().filter(c -> c.id().equals(AlmanacChapterProvider.COMBAT)).findFirst().orElseThrow();
        assertTrue(combat.pages().stream().flatMap(p -> p.elements().stream()).anyMatch(e -> e instanceof PageElement.ShowMe s
                && s.scene().equals(AlmanacChapterProvider.CANNON_LOADING_SCENE)), "the cannon page's Show me");
    }

    /**
     * The player's chapters speak to a player (ALM6): no commands, config keys, mod ids or package names outside the
     * Quartermaster's ledger, the one chapter for server owners. Markup tokens (item icons, links) are left out.
     */
    @Test
    void onlyTheLedgerNamesCommandsAndSettings() {
        Map<String, String> text = intermediate().lang();
        List<String> problems = new ArrayList<>();
        for (Chapter c : chapters()) {
            if (c.id().equals(LEDGER)) continue;
            for (String key : AlmanacChapterProvider.keys(c)) {
                String plain = MARKUP_TOKEN.matcher(text.get(key)).replaceAll("");
                Matcher m = TECHNICAL.matcher(plain);
                if (m.find()) problems.add(key + ": '" + m.group() + "' in \"" + plain + "\"");
            }
        }
        assertTrue(problems.isEmpty(), "technical words in the player's chapters:\n" + String.join("\n", problems));
    }

    @Test
    void generatedChapterFilesAreTheIntermediate() throws IOException {
        Set<String> files = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(CHAPTERS)) {
            walk.filter(f -> f.toString().endsWith(".json"))
                    .forEach(f -> files.add(CHAPTERS.relativize(f).toString().replace('\\', '/').replace(".json", "")));
        }
        Map<ResourceLocation, JsonObject> bodies = intermediate().bodies();
        Set<String> expected = new TreeSet<>();
        bodies.keySet().forEach(id -> expected.add(id.getPath()));
        assertEquals(expected, files, "generated chapter files (run ./gradlew :neoforge:runData)");
        for (var e : bodies.entrySet()) {
            JsonObject generated = JsonParser.parseString(Files.readString(CHAPTERS.resolve(e.getKey().getPath() + ".json"))).getAsJsonObject();
            assertEquals(chapter(e.getKey(), e.getValue()), chapter(e.getKey(), generated),
                    e.getKey() + " differs from the intermediate (run ./gradlew :neoforge:runData)");
        }
    }

    @Test
    void everyLangKeyHasItsTextInEnUs() throws IOException {
        JsonObject lang = lang();
        Map<String, String> text = intermediate().lang();
        List<String> problems = new ArrayList<>();
        int keys = 0;
        for (Chapter c : chapters()) {
            for (String key : AlmanacChapterProvider.keys(c)) {
                keys++;
                if (!key.startsWith(AlmanacLang.prefix(c.id()) + ".")) problems.add(key + " breaks the key convention");
                if (!lang.has(key)) problems.add(key + " missing in en_us.json (run ./gradlew :neoforge:runData)");
                else if (!lang.get(key).getAsString().equals(text.get(key))) problems.add(key + " differs from the intermediate");
            }
        }
        assertTrue(keys > 300, "only " + keys + " keys");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void itemsLinksAndIllustrationsExist() throws IOException {
        JsonObject lang = lang();
        List<Chapter> chapters = chapters();
        Set<ResourceLocation> ids = new TreeSet<>();
        chapters.forEach(c -> ids.add(c.id()));
        List<String> problems = new ArrayList<>();
        int icons = 0;
        int links = 0;
        for (Chapter c : chapters) {
            String iconProblem = checkItem(c.icon(), lang);
            if (iconProblem != null) problems.add(c.id() + " icon: " + iconProblem);
            for (String key : AlmanacChapterProvider.keys(c)) {
                String value = intermediate().lang().get(key);
                Matcher item = ITEM_TOKEN.matcher(value);
                while (item.find()) {
                    icons++;
                    String p = checkItem(ResourceLocation.parse(item.group(1)), lang);
                    if (p != null) problems.add(key + ": " + p);
                }
                Matcher link = LINK_TOKEN.matcher(value);
                while (link.find()) {
                    links++;
                    if (!ids.contains(ResourceLocation.parse(link.group(1)))) problems.add(key + ": link to missing chapter " + link.group(1));
                }
            }
            for (Page page : c.pages()) {
                for (PageElement el : page.elements()) {
                    if (el instanceof PageElement.Illustration ill) {
                        Path file = Path.of("src/main/resources/assets/" + ill.texture().getNamespace() + "/" + ill.texture().getPath());
                        if (!Files.isRegularFile(file)) problems.add(c.id() + ": missing illustration " + file + " (run python3 tools/gen_almanac.py)");
                    }
                }
            }
        }
        assertTrue(icons > 50, "only " + icons + " item icons in the text");
        assertTrue(links > 10, "only " + links + " links in the text");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void illustrationsAreSmall() throws IOException {
        long total = 0;
        int n = 0;
        try (Stream<Path> files = Files.list(ILLUSTRATIONS)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                n++;
                total += Files.size(f);
                try (InputStream in = Files.newInputStream(f); DataInputStream data = new DataInputStream(in)) {
                    data.skipNBytes(16);
                    int w = data.readInt();
                    int h = data.readInt();
                    assertTrue(w <= 256 && h <= 256, f + " is " + w + "x" + h);
                }
            }
        }
        assertTrue(n >= 16, "only " + n + " illustrations");
        System.out.println("Almanac illustrations: " + n + " files, " + total + " bytes");
        assertTrue(total < MAX_ILLUSTRATION_BYTES, "illustrations take " + total + " bytes");
    }

    @Test
    void wholeBookFitsOneSyncPayload() {
        AlmanacBook book = AlmanacBook.of(chapters());
        ByteBuf buf = Unpooled.buffer();
        AlmanacSyncPayload.CODEC.encode(buf, new AlmanacSyncPayload(book));
        int size = buf.readableBytes();
        System.out.println("Almanac sync payload: " + book.size() + " chapters, " + size + " bytes");
        assertEquals(book, AlmanacSyncPayload.CODEC.decode(buf).book(), "the book survives the wire codec");
        assertTrue(size < MAX_WIRE_BYTES, "the book takes " + size + " bytes on the wire (max " + MAX_WIRE_BYTES + ")");
        assertFalse(book.isEmpty());
    }

    /** Ours: an item model and a lang name; vanilla: registered. Null when fine. */
    private static String checkItem(ResourceLocation id, JsonObject lang) {
        if (id.getNamespace().equals(NS)) {
            String path = "models/item/" + id.getPath() + ".json";
            boolean model = Files.isRegularFile(ASSETS.resolve(path)) || Files.isRegularFile(GENERATED.resolve("assets/" + NS + "/" + path));
            boolean name = lang.has("item." + NS + "." + id.getPath()) || lang.has("block." + NS + "." + id.getPath());
            return model && name ? null : id + (model ? " has no lang name" : " is no item of ours");
        }
        return BuiltInRegistries.ITEM.containsKey(id) ? null : id + " is no registered item";
    }
}
