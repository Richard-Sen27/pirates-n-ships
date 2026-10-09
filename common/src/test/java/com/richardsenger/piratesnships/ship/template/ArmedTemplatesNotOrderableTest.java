package com.richardsenger.piratesnships.ship.template;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fighting ships come by rank or by capture, never from the shipwright (SHP1, design.md §15): every ship template whose
 * id contains "armed" is not orderable, both in the datagen source ({@link ShipTemplates#DEFAULTS}) and in the
 * generated definitions the game loads, so nobody flips it by accident.
 */
class ArmedTemplatesNotOrderableTest {

    private static final String GENERATED = "common/src/generated/resources/data/pirates_n_ships/pirates_n_ships/ship_template";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void armedDefaultsAreNotOrderable() {
        long armed = 0;
        for (Map.Entry<ResourceLocation, ShipTemplate> e : ShipTemplates.DEFAULTS.entrySet()) {
            if (!e.getKey().getPath().contains("armed")) continue;
            armed++;
            assertFalse(e.getValue().orderable(), e.getKey() + " is orderable");
        }
        assertTrue(armed >= 2, "the navy and pirate armed sloops are among the defaults");
    }

    @Test
    void armedGeneratedDefinitionsAreNotOrderable() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve(GENERATED))) dir = dir.getParent();
        assertTrue(dir != null, "generated ship templates not found above " + Path.of("").toAbsolutePath());
        List<Path> files;
        try (Stream<Path> s = Files.list(dir.resolve(GENERATED))) {
            files = s.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        long armed = 0;
        for (Path f : files) {
            if (!f.getFileName().toString().contains("armed")) continue;
            armed++;
            ShipTemplate t = ShipTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f))).getOrThrow();
            assertFalse(t.orderable(), f.getFileName() + " is orderable");
        }
        assertTrue(armed >= 2, "armed templates found: " + armed);
    }
}
