package com.richardsenger.piratesnships.almanac.ponder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.ship.decor.wood.WoodIds;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PND5: every block and item of ours that a scene shows is one of its subjects, so Ponder's inventory hint appears on
 * it. Reads each scene's schematic palette ({@code assets/pirates_n_ships/ponder/<id>.nbt}) and its storyboard's
 * {@code SceneKit.item/block} calls ({@code <Id>Scene.java}), expands wood families the way the game does (every
 * {@code <wood>_<suffix>} of the generated lang, the legacy {@code <suffix>} included) and prints the items no scene
 * shows (spawn eggs and plain ingredients may stay without one).
 */
class SceneSubjectsTest {

    private static final String MOD = "pirates_n_ships";
    private static final Pattern SCENE_KIT = Pattern.compile("SceneKit\\.(?:item|block)\\(\"([a-z0-9_]+)\"\\)");

    private static Path root;
    /** Every block and item path of ours (the generated lang's {@code block.<mod>.<path>} and {@code item.<mod>.<path>}). */
    private static Set<String> known;

    @BeforeAll
    static void setUp() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("common/src/generated/resources/assets/" + MOD + "/lang/en_us.json"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository root not found above the working directory");
        known = new TreeSet<>();
        Pattern key = Pattern.compile("^(?:block|item)\\." + MOD + "\\.([a-z0-9_]+)$");
        try (Reader r = Files.newBufferedReader(root.resolve("common/src/generated/resources/assets/" + MOD + "/lang/en_us.json"),
                StandardCharsets.UTF_8)) {
            JsonObject lang = JsonParser.parseReader(r).getAsJsonObject();
            for (String k : lang.keySet()) {
                Matcher m = key.matcher(k);
                if (m.matches()) known.add(m.group(1));
            }
        }
    }

    /** The wood family suffix of a path of ours: {@code <wood>_<suffix>}, or a legacy {@code <suffix>} with members. */
    private static String familySuffix(String path) {
        String suffix = WoodIds.suffixOf(path);
        if (suffix != null && known.stream().filter(p -> suffix.equals(WoodIds.suffixOf(p))).count() > 1) return suffix;
        if (known.stream().anyMatch(p -> path.equals(WoodIds.suffixOf(p)))) return path;
        return null;
    }

    /** The members of {@code id}'s family among our ids (data runs ship every wood), or {@code id} alone. */
    private static List<ResourceLocation> family(ResourceLocation id) {
        String suffix = familySuffix(id.getPath());
        if (suffix == null) return List.of(id);
        List<ResourceLocation> out = new ArrayList<>();
        for (String p : known) {
            if (p.equals(suffix) || suffix.equals(WoodIds.suffixOf(p))) out.add(ResourceLocation.fromNamespaceAndPath(MOD, p));
        }
        return out;
    }

    /** The paths of ours in {@code scene}'s schematic palette. */
    private static Set<String> palette(AlmanacScenes.Scene scene) throws IOException {
        Path file = root.resolve("common/src/main/resources/assets/" + MOD + "/ponder/" + scene.schematic().getPath() + ".nbt");
        CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        List<ListTag> palettes = new ArrayList<>();
        if (nbt.contains("palette")) palettes.add(nbt.getList("palette", Tag.TAG_COMPOUND));
        ListTag many = nbt.getList("palettes", Tag.TAG_LIST);
        for (int i = 0; i < many.size(); i++) palettes.add(many.getList(i));
        Set<String> out = new TreeSet<>();
        for (ListTag palette : palettes) {
            for (int i = 0; i < palette.size(); i++) {
                ResourceLocation name = ResourceLocation.parse(palette.getCompound(i).getString("Name"));
                if (name.getNamespace().equals(MOD)) out.add(name.getPath());
            }
        }
        return out;
    }

    /** The storyboard class of a scene: {@code cannon_loading} is {@code CannonLoadingScene.java}. */
    private static Path storyboard(AlmanacScenes.Scene scene) {
        StringBuilder name = new StringBuilder();
        for (String part : scene.id().getPath().split("_")) {
            name.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return root.resolve("common/src/main/java/com/richardsenger/piratesnships/almanac/ponder/" + name + "Scene.java");
    }

    /** The paths the storyboard names through {@code SceneKit.item("...")} and {@code SceneKit.block("...")}. */
    private static Set<String> sceneKitPaths(AlmanacScenes.Scene scene) throws IOException {
        Path file = storyboard(scene);
        assertTrue(Files.exists(file), "the storyboard of " + scene.id() + " is " + file.getFileName());
        Matcher m = SCENE_KIT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        Set<String> out = new TreeSet<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyBlockAndItemAScenesShowsIsASubject() throws IOException {
        List<String> missing = new ArrayList<>();
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            List<ResourceLocation> expanded = scene.expand(SceneSubjectsTest::family);
            Set<String> shown = new TreeSet<>(palette(scene));
            shown.addAll(sceneKitPaths(scene));
            for (String path : shown) {
                if (!expanded.contains(ResourceLocation.fromNamespaceAndPath(MOD, path))) {
                    missing.add(scene.id().getPath() + ": " + path);
                }
            }
        }
        assertEquals(List.of(), missing, "blocks and items scenes show but do not list as subjects");
    }

    @Test
    void everySubjectIsOneOfOursAndAppearsOnce() {
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            assertEquals(scene.component(), scene.subjects().get(0), scene.id() + ": the component leads");
            assertEquals(scene.subjects().size(), new TreeSet<>(scene.subjects()).size(), scene.id() + ": no subject twice");
            for (ResourceLocation subject : scene.subjects()) {
                assertEquals(MOD, subject.getNamespace(), scene.id() + ": " + subject);
                assertTrue(known.contains(subject.getPath()), scene.id() + ": " + subject + " is a block or item of ours");
            }
        }
    }

    @Test
    void everyWoodFamilyMemberOfASubjectIsASubject() {
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            List<ResourceLocation> expanded = scene.expand(SceneSubjectsTest::family);
            for (ResourceLocation subject : scene.subjects()) {
                String suffix = familySuffix(subject.getPath());
                if (suffix == null) {
                    assertTrue(expanded.contains(subject), scene.id() + ": " + subject);
                    continue;
                }
                long members = known.stream().filter(p -> p.equals(suffix) || suffix.equals(WoodIds.suffixOf(p))).count();
                assertTrue(members >= 11, subject + "'s family has every vanilla wood at least: " + members);
                for (String p : known) {
                    if (p.equals(suffix) || suffix.equals(WoodIds.suffixOf(p))) {
                        assertTrue(expanded.contains(ResourceLocation.fromNamespaceAndPath(MOD, p)),
                                scene.id() + ": " + p + " (the family of " + subject + ") is a subject");
                    }
                }
            }
        }
        // the families the human named (2026-10-10): every helm steers, every railing variant is in the railings scene
        List<ResourceLocation> steering = AlmanacScenes.byId(AlmanacScenes.STEERING).orElseThrow().expand(SceneSubjectsTest::family);
        assertTrue(steering.contains(ResourceLocation.fromNamespaceAndPath(MOD, "spruce_helm")), "the spruce helm steers");
        List<ResourceLocation> railings = AlmanacScenes.byId(AlmanacScenes.RAILINGS).orElseThrow().expand(SceneSubjectsTest::family);
        for (String p : List.of("spruce_railing", "spruce_railing_port", "spruce_railing_gate", "spruce_bulwark",
                "spruce_bulwark_port", "spruce_rope_railing", "cannon", "spruce_cannon", "swivel_gun")) {
            assertTrue(railings.contains(ResourceLocation.fromNamespaceAndPath(MOD, p)), "the railings scene shows " + p);
        }
    }

    /** Not a check: the blocks and items no scene shows, one line per wood family, for the hand-back. */
    @Test
    void printTheItemsWithoutAScene() {
        Set<ResourceLocation> covered = new TreeSet<>();
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) covered.addAll(scene.expand(SceneSubjectsTest::family));
        Function<String, String> label = p -> {
            String suffix = familySuffix(p);
            return suffix == null ? p : "<wood>_" + suffix + " (family)";
        };
        TreeMap<String, Integer> without = new TreeMap<>();
        for (String p : known) {
            if (!covered.contains(ResourceLocation.fromNamespaceAndPath(MOD, p))) without.merge(label.apply(p), 1, Integer::sum);
        }
        System.out.println("PND5: " + without.size() + " blocks/items (families counted once) with no Ponder scene:");
        without.keySet().forEach(p -> System.out.println("PND5 no scene: " + p));
    }
}
