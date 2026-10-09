package com.richardsenger.piratesnships.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.platform.FabricRenderLayers.Layer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@link FabricRenderLayers} against our real resources (the Fabric module's processed resources: common's assets,
 * generated ones included) and against small hand-made cases.
 */
class FabricRenderLayersTest {

    private static final String NS = "pirates_n_ships";

    private static Optional<JsonObject> fromClasspath(String path) {
        return FabricRenderLayers.readJson(path, p -> FabricRenderLayersTest.class.getClassLoader().getResourceAsStream(p));
    }

    @Test
    void sternWindowIsCutout() {
        FabricRenderLayers.Result result = FabricRenderLayers.layerOf(NS, "stern_window", FabricRenderLayersTest::fromClasspath);
        assertEquals(Optional.of(Layer.CUTOUT), result.layer());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
    }

    @Test
    void blockWithoutRenderTypeStaysSolid() {
        assertEquals(Optional.empty(), FabricRenderLayers.layerOf(NS, "no_such_block", FabricRenderLayersTest::fromClasspath).layer());
    }

    /**
     * Every block model of ours that names a {@code render_type} is reached from some blockstate, so its block gets the
     * layer on Fabric too; no blockstate of ours yields a problem (unknown name, disagreeing models).
     */
    @Test
    void everyRenderTypeModelReachesItsBlock() throws IOException, URISyntaxException {
        Path assets = resourceDir("assets/" + NS);
        Set<String> modelsWithType = new TreeSet<>();
        try (Stream<Path> files = Files.walk(assets.resolve("models/block"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (json.has("render_type")) {
                    String rel = assets.resolve("models").relativize(file).toString().replace('\\', '/');
                    modelsWithType.add(NS + ":" + rel.substring(0, rel.length() - ".json".length()));
                }
            }
        }
        assertFalse(modelsWithType.isEmpty(), "expected the stern window models at least");

        Set<String> reached = new TreeSet<>();
        Function<String, Optional<JsonObject>> recording = path -> {
            Optional<JsonObject> json = fromClasspath(path);
            if (json.isPresent() && json.get().has("render_type") && path.contains("/models/")) {
                reached.add(NS + ":" + path.substring(("assets/" + NS + "/models/").length(), path.length() - ".json".length()));
            }
            return json;
        };
        Map<String, Layer> layers = new HashMap<>();
        try (Stream<Path> states = Files.list(assets.resolve("blockstates"))) {
            for (Path state : states.filter(f -> f.toString().endsWith(".json")).toList()) {
                String block = state.getFileName().toString().replace(".json", "");
                FabricRenderLayers.Result result = FabricRenderLayers.layerOf(NS, block, recording);
                assertTrue(result.problems().isEmpty(), block + ": " + result.problems());
                result.layer().ifPresent(l -> layers.put(block, l));
            }
        }
        assertEquals(modelsWithType, reached, "models with a render_type that no blockstate reaches");
        assertEquals(Layer.CUTOUT, layers.get("stern_window"));
    }

    @Test
    void parentsAndMultipartAndConflicts() {
        Map<String, String> files = Map.of(
                "assets/x/blockstates/a.json", "{\"multipart\":[{\"apply\":{\"model\":\"x:block/child\"}},{\"apply\":[{\"model\":\"x:block/plain\"}]}]}",
                "assets/x/models/block/child.json", "{\"parent\":\"x:block/base\"}",
                "assets/x/models/block/base.json", "{\"parent\":\"minecraft:block/cube_all\",\"render_type\":\"cutout_mipped\"}",
                "assets/x/models/block/plain.json", "{\"parent\":\"minecraft:block/cube_all\"}",
                "assets/x/blockstates/b.json", "{\"variants\":{\"\":[{\"model\":\"x:block/glass\"},{\"model\":\"x:block/child\"}]}}",
                "assets/x/models/block/glass.json", "{\"render_type\":\"minecraft:translucent\"}",
                "assets/x/blockstates/c.json", "{\"variants\":{\"\":{\"model\":\"x:block/odd\"}}}",
                "assets/x/models/block/odd.json", "{\"render_type\":\"neoforge:glowing\"}");
        Function<String, Optional<JsonObject>> reader = p -> Optional.ofNullable(files.get(p)).map(s -> JsonParser.parseString(s).getAsJsonObject());

        FabricRenderLayers.Result a = FabricRenderLayers.layerOf("x", "a", reader);
        assertEquals(Optional.of(Layer.CUTOUT_MIPPED), a.layer(), "inherited from the parent, through a multipart");
        assertTrue(a.problems().isEmpty());

        FabricRenderLayers.Result b = FabricRenderLayers.layerOf("x", "b", reader);
        assertEquals(Optional.of(Layer.TRANSLUCENT), b.layer(), "the most transparent layer wins");
        assertEquals(1, b.problems().size());

        FabricRenderLayers.Result c = FabricRenderLayers.layerOf("x", "c", reader);
        assertEquals(Optional.empty(), c.layer());
        assertEquals(List.of("unknown render_type 'neoforge:glowing' in model x:block/odd"), c.problems());
    }

    private static Path resourceDir(String path) throws URISyntaxException {
        URL url = FabricRenderLayersTest.class.getClassLoader().getResource(path + "/blockstates");
        assertTrue(url != null && "file".equals(url.getProtocol()), "resources are not a directory on the test classpath: " + url);
        return Path.of(url.toURI()).getParent();
    }
}
