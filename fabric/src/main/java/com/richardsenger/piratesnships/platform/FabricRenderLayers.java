package com.richardsenger.piratesnships.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Block render layers on Fabric (FAB2). Our block models name their layer the NeoForge way, with a {@code render_type}
 * key in the model file (Blockbench models, e.g. {@code block/stern_window.json}: {@code "minecraft:cutout"}); NeoForge
 * reads it, vanilla and Fabric ignore it and draw such a block in the solid layer (transparent pixels turn black). This
 * class reads the same key from our own resources and returns the layer per block, which {@code FabricClientSetup}
 * hands to {@code BlockRenderLayerMap}. So a model's {@code render_type} stays the single source for both loaders.
 *
 * <p>Pure logic, no Minecraft client classes: a block's blockstate file ({@code variants} and {@code multipart}) names its
 * models; a model without the key inherits its parent's (NeoForge's rule), followed while the parent is readable (our
 * namespace; vanilla parents have none). Fabric's layer is per block, NeoForge's per model: if a block's models disagree
 * the most transparent one wins ({@link Layer#ordinal()} order) and the conflict is reported.
 */
public final class FabricRenderLayers {

    /** The block layers a {@code render_type} can name, least to most transparent. */
    public enum Layer { SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT, TRIPWIRE }

    private static final int MAX_PARENT_DEPTH = 16;

    private FabricRenderLayers() {
    }

    /** The result for one block: the layer, plus any problem worth a log line (unknown names, conflicts). */
    public record Result(Optional<Layer> layer, List<String> problems) { }

    /**
     * The layer of block {@code namespace:path} from its blockstate file, or empty for the solid default.
     *
     * @param reader reads a resource by its path in the jar ({@code assets/<ns>/blockstates/<path>.json}), empty if absent
     */
    public static Result layerOf(String namespace, String path, Function<String, Optional<JsonObject>> reader) {
        List<String> problems = new ArrayList<>();
        Optional<JsonObject> state = reader.apply("assets/" + namespace + "/blockstates/" + path + ".json");
        if (state.isEmpty()) return new Result(Optional.empty(), problems);
        Set<String> models = new LinkedHashSet<>();
        collectModels(state.get(), models);
        TreeMap<Layer, String> found = new TreeMap<>();
        for (String model : models) {
            String name = renderType(model, reader, 0);
            if (name == null) continue;
            Layer layer = parse(name);
            if (layer == null) {
                problems.add("unknown render_type '" + name + "' in model " + model);
                continue;
            }
            found.putIfAbsent(layer, model);
        }
        if (found.size() > 1) {
            problems.add("models disagree on the render_type " + found + "; Fabric uses one layer per block");
        }
        Layer layer = found.isEmpty() ? null : found.lastKey();
        return new Result(layer == null || layer == Layer.SOLID ? Optional.empty() : Optional.of(layer), problems);
    }

    /** A {@code render_type} value (NeoForge's names, with or without the {@code minecraft:} namespace). */
    static Layer parse(String renderType) {
        String name = renderType.startsWith("minecraft:") ? renderType.substring("minecraft:".length()) : renderType;
        return switch (name) {
            case "solid" -> Layer.SOLID;
            case "cutout" -> Layer.CUTOUT;
            case "cutout_mipped", "cutout_mipped_all" -> Layer.CUTOUT_MIPPED;
            case "translucent" -> Layer.TRANSLUCENT;
            case "tripwire" -> Layer.TRIPWIRE;
            default -> null;
        };
    }

    private static void collectModels(JsonObject state, Set<String> out) {
        if (state.has("variants")) {
            for (Map.Entry<String, JsonElement> variant : state.getAsJsonObject("variants").entrySet()) {
                addModels(variant.getValue(), out);
            }
        }
        if (state.has("multipart")) {
            for (JsonElement part : state.getAsJsonArray("multipart")) {
                if (part.isJsonObject() && part.getAsJsonObject().has("apply")) {
                    addModels(part.getAsJsonObject().get("apply"), out);
                }
            }
        }
    }

    /** A variant or {@code apply} entry: one model object, or an array of weighted ones. */
    private static void addModels(JsonElement entry, Set<String> out) {
        if (entry.isJsonArray()) {
            for (JsonElement e : (JsonArray) entry) addModels(e, out);
        } else if (entry.isJsonObject() && entry.getAsJsonObject().has("model")) {
            out.add(entry.getAsJsonObject().get("model").getAsString());
        }
    }

    /** The model's {@code render_type}, inherited from its parents, or null if none sets one (or none is readable). */
    private static String renderType(String model, Function<String, Optional<JsonObject>> reader, int depth) {
        if (depth > MAX_PARENT_DEPTH) return null;
        int colon = model.indexOf(':');
        String ns = colon < 0 ? "minecraft" : model.substring(0, colon);
        String path = colon < 0 ? model : model.substring(colon + 1);
        Optional<JsonObject> json = reader.apply("assets/" + ns + "/models/" + path + ".json");
        if (json.isEmpty()) return null;
        JsonObject m = json.get();
        if (m.has("render_type")) return m.get("render_type").getAsString();
        return m.has("parent") ? renderType(m.get("parent").getAsString(), reader, depth + 1) : null;
    }

    /** Reads a JSON resource through {@code open} (empty if absent or unreadable). */
    public static Optional<JsonObject> readJson(String path, Function<String, InputStream> open) {
        try (InputStream in = open.apply(path)) {
            if (in == null) return Optional.empty();
            return Optional.of(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            Constants.LOG.warn("Could not read {} for the block render layers: {}", path, e.toString());
            return Optional.empty();
        }
    }
}
