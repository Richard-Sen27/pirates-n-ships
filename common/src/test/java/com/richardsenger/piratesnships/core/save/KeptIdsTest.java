package com.richardsenger.piratesnships.core.save;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The id guard of the save-compatibility note (SAV1, {@code docs/release/save-compatibility.md}), run from
 * {@code common/}: every block, item and entity id in the note's "kept ids" tables (between the {@code kept-ids:start}
 * and {@code kept-ids:end} markers) must still have its name in the generated {@code en_us.json}
 * ({@code block.pirates_n_ships.<id>}, {@code item.…}, {@code entity.…}), the lang file standing in for the registries.
 * A renamed or removed id turns every saved block, item stack or entity of it into air or nothing, so it must not slip
 * in unnoticed: either keep the id, or take it out of the table and describe the loss in the note.
 */
class KeptIdsTest {

    private static final Path NOTE = Path.of("../docs/release/save-compatibility.md");
    private static final Path LANG = Path.of("src/generated/resources/assets/pirates_n_ships/lang/en_us.json");
    private static final Pattern ID = Pattern.compile("`([a-z0-9_]+)`");

    /** Kind ({@code block}, {@code item}, {@code entity}) to the ids the note's tables list for it. */
    static Map<String, List<String>> keptIds(List<String> lines) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.contains("kept-ids:start")) {
                inside = true;
                continue;
            }
            if (line.contains("kept-ids:end")) {
                break;
            }
            if (!inside || !line.startsWith("|")) {
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length < 3) {
                continue;
            }
            String kind = cells[1].trim().toLowerCase(java.util.Locale.ROOT);
            if (!kind.equals("block") && !kind.equals("item") && !kind.equals("entity")) {
                continue;
            }
            Matcher m = ID.matcher(cells[2]);
            while (m.find()) {
                out.computeIfAbsent(kind, k -> new ArrayList<>()).add(m.group(1));
            }
        }
        return out;
    }

    @Test
    void parsesTheTableRowsOnlyBetweenTheMarkers() {
        List<String> lines = List.of("| Block | `before` |", "<!-- kept-ids:start -->", "| Kind | Ids |", "|---|---|",
                "| Block | `nameplate`, `oak_oar_bench` |", "| Item | `oar` |", "| Entity | `crew_member` |",
                "| Note | `ignored` |", "<!-- kept-ids:end -->", "| Item | `after` |");
        Map<String, List<String>> ids = keptIds(lines);
        assertEquals(Map.of("block", List.of("nameplate", "oak_oar_bench"), "item", List.of("oar"), "entity", List.of("crew_member")), ids);
    }

    @Test
    void everyKeptIdIsStillRegistered() throws IOException {
        assertTrue(Files.exists(NOTE), NOTE.toAbsolutePath() + " is missing");
        Map<String, List<String>> ids = keptIds(Files.readAllLines(NOTE, StandardCharsets.UTF_8));
        JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        int count = ids.values().stream().mapToInt(List::size).sum();
        // the 1.0.0 table lists 47 blocks, 57 items and 22 entities; a parse that finds far fewer is a broken table
        assertTrue(count >= 120, "only " + count + " kept ids found in " + NOTE + " (markers or table format broken?)");
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : ids.entrySet()) {
            for (String id : e.getValue()) {
                String key = e.getKey() + ".pirates_n_ships." + id;
                if (!lang.has(key)) missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "ids in the save-compatibility note's kept-ids table are no longer registered (a rename "
                + "or removal loses them in every saved world; keep the id or document the loss in the note): " + missing);
    }
}
