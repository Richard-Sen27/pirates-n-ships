package com.richardsenger.piratesnships.ship.decor.railing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAIL6 (docs/design.md "Railing rework (RAIL6)"): the contracts between the railing family's part list
 * ({@code tools/gen_railing_models.py}) and the rest of the mod. The cap rail's top, which RL5's latch stands on and
 * RAIL5's leaners rest on, stays at {@link RailingRules#RAIL_TOP} on the railing, the gate and the bulwark, and the
 * rope railing's top line ends there too; the generated block states leave out a baluster exactly where a post stands
 * at its end of the edge, give the bulwark and the rope railing the railing's turned post, and turn nothing with uvlock
 * (so the cap's grain follows every edge). Run from {@code common/}.
 */
class RailingModelsTest {

    private static final Path MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models/block");
    private static final Path STATES = Path.of("src/generated/resources/assets/pirates_n_ships/blockstates");

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    /** The highest point of the elements named {@code prefix}* of a parent model, in pixels. */
    private static double top(String model, String prefix) throws IOException {
        double top = Double.NEGATIVE_INFINITY;
        for (JsonElement e : json(MODELS.resolve(model + ".json")).getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            if (el.get("name").getAsString().startsWith(prefix)) top = Math.max(top, el.getAsJsonArray("to").get(1).getAsDouble());
        }
        return top;
    }

    @Test
    void theCapsTopStaysWhereTheLatchAndTheLeanersExpectIt() throws IOException {
        for (String model : List.of("railing_edge", "bulwark_edge", "railing_gate_left", "railing_gate_right")) {
            assertEquals(RailingRules.RAIL_TOP, top(model, "cap"), model + ": the cap's top");
        }
        assertEquals(RailingRules.RAIL_TOP, top("rope_railing_edge", "rope_top"), "the rope railing's top line");
        // nothing of an edge stands above the cap where the latch's washer sits (x 7..9)
        for (String model : List.of("railing_edge", "railing_baluster_west", "railing_baluster_east", "bulwark_edge", "rope_railing_edge")) {
            for (JsonElement e : json(MODELS.resolve(model + ".json")).getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                JsonArray f = el.getAsJsonArray("from"), t = el.getAsJsonArray("to");
                boolean underWasher = f.get(0).getAsDouble() < 9 && t.get(0).getAsDouble() > 7;
                assertFalse(underWasher && t.get(1).getAsDouble() > RailingRules.RAIL_TOP, model + ": " + el.get("name") + " over the cap");
            }
        }
    }

    /** The multipart entries of a block state: (model, when) pairs. */
    private static List<JsonObject> parts(String block) throws IOException {
        JsonArray mp = json(STATES.resolve(block + ".json")).getAsJsonArray("multipart");
        return mp.asList().stream().map(JsonElement::getAsJsonObject).toList();
    }

    private static String model(JsonObject part) {
        return part.getAsJsonObject("apply").get("model").getAsString();
    }

    @Test
    void aBalusterIsLeftOutWhereAPostStandsAtItsEnd() throws IOException {
        // edge -> {the post at its x-0 end, the post at its x-16 end}
        Map<String, String[]> ends = new HashMap<>();
        ends.put("north", new String[] {"post_nw", "post_ne"});
        ends.put("east", new String[] {"post_ne", "post_se"});
        ends.put("south", new String[] {"post_se", "post_sw"});
        ends.put("west", new String[] {"post_sw", "post_nw"});
        int found = 0;
        for (JsonObject part : parts("oak_railing")) {
            String m = model(part);
            if (!m.contains("baluster")) continue;
            JsonObject when = part.getAsJsonObject("when");
            assertEquals(2, when.size(), m + ": " + when);
            String edge = ends.keySet().stream().filter(when::has).findFirst().orElseThrow();
            assertEquals("true", when.get(edge).getAsString());
            String post = ends.get(edge)[m.endsWith("_west") ? 0 : 1];
            assertEquals("false", when.get(post).getAsString(), m + " on " + edge + ": " + when);
            found++;
        }
        assertEquals(8, found, "two balusters per edge");
    }

    @Test
    void theBulwarkAndTheRopeRailingUseTheRailingsPostAndNothingIsTurnedWithUvlock() throws IOException {
        for (String block : List.of("oak_bulwark", "oak_rope_railing")) {
            assertTrue(parts(block).stream().anyMatch(p -> model(p).equals("pirates_n_ships:block/oak_railing_post")), block);
        }
        for (String block : List.of("oak_railing", "oak_bulwark", "oak_rope_railing", "oak_railing_gate", "oak_bulwark_port")) {
            for (JsonObject p : parts(block)) {
                assertFalse(p.getAsJsonObject("apply").has("uvlock"), block + ": uvlock on " + model(p));
            }
        }
    }
}
