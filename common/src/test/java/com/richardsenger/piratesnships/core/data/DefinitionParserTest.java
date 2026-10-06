package com.richardsenger.piratesnships.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.CoreDefinitions.TestMarker;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure parsing step: JSON map in, values + errors out. No game needed. */
class DefinitionParserTest {

    private static final ResourceLocation A = Constants.id("a");
    private static final ResourceLocation B = Constants.id("b");
    private static final ResourceLocation C = ResourceLocation.fromNamespaceAndPath("other_mod", "c");

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    private static DefinitionParser.Result<TestMarker> parse(Map<ResourceLocation, JsonElement> input) {
        return DefinitionParser.parse(TestMarker.CODEC, JsonOps.INSTANCE, input);
    }

    @Test
    void validEntriesAreDecodedAndSorted() {
        Map<ResourceLocation, JsonElement> input = new HashMap<>();
        input.put(C, json("{\"label\": \"c\"}"));
        input.put(A, json("{\"label\": \"a\", \"weight\": 5}"));
        var result = parse(input);
        assertFalse(result.hasErrors(), result.errors().toString());
        assertEquals(new TestMarker("a", 5), result.values().get(A));
        assertEquals(new TestMarker("c", 1), result.values().get(C), "optional field takes its default");
        assertEquals(List.of(A, C), List.copyOf(result.values().keySet()), "sorted by ResourceLocation order (path, then namespace)");
        assertThrows(UnsupportedOperationException.class, () -> result.values().put(B, new TestMarker("b", 1)));
    }

    @Test
    void invalidEntriesAreSkippedWithIdAndReason() {
        Map<ResourceLocation, JsonElement> input = new HashMap<>();
        input.put(A, json("{\"label\": \"a\"}"));
        input.put(B, json("{\"weight\": 5}"));
        input.put(C, json("{\"label\": \"c\", \"weight\": 5000}"));
        var result = parse(input);
        assertEquals(Map.of(A, new TestMarker("a", 1)), result.values());
        assertEquals(2, result.errors().size());
        assertEquals(B, result.errors().get(0).id());
        assertTrue(result.errors().get(0).message().contains("label"), result.errors().get(0).message());
        assertEquals(C, result.errors().get(1).id());
        assertTrue(result.errors().get(1).message().contains("5000"), result.errors().get(1).message());
    }

    @Test
    void nullJsonAndThrowingDecodersAreErrorsNotCrashes() {
        Map<ResourceLocation, JsonElement> input = new HashMap<>();
        input.put(A, JsonNull.INSTANCE);
        input.put(B, json("{\"label\": \"b\"}"));
        var result = parse(input);
        assertEquals(List.of(B), List.copyOf(result.values().keySet()));
        assertEquals(A, result.errors().getFirst().id());

        Codec<String> throwing = Codec.STRING.xmap(s -> { throw new IllegalStateException("boom"); }, s -> s);
        var thrown = DefinitionParser.parse(throwing, JsonOps.INSTANCE, Map.of(A, json("\"x\"")));
        assertTrue(thrown.values().isEmpty());
        assertTrue(thrown.errors().getFirst().message().contains("boom"));
    }

    @Test
    void duplicateContentUnderDifferentIdsIsKeptPerId() {
        // Ids are unique by construction (map keys; the resource manager resolves datapack overrides by id).
        // Equal content under two ids is two independent entries.
        JsonElement same = json("{\"label\": \"same\"}");
        var result = parse(Map.of(A, same, B, same));
        assertEquals(2, result.values().size());
        assertEquals(result.values().get(A), result.values().get(B));
    }

    @Test
    void emptyInputGivesEmptyResult() {
        var result = parse(Map.of());
        assertTrue(result.values().isEmpty());
        assertFalse(result.hasErrors());
    }

    @Test
    void handBuiltDefinitionsWorkWithoutAnyLoader() {
        Definitions<TestMarker> defs = Definitions.of("test_marker", Map.of(A, new TestMarker("a", 2)));
        assertEquals(2, defs.require(A).weight());
        assertTrue(defs.get(B).isEmpty());
        var e = assertThrows(IllegalArgumentException.class, () -> defs.require(B));
        assertTrue(e.getMessage().contains("test_marker") && e.getMessage().contains(B.toString()), e.getMessage());
        assertEquals(1, defs.ids().size());
    }
}
