package com.richardsenger.piratesnships.compat.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.api.IModPlugin;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How JEI finds our plugin (JEI1), checked without a client: on NeoForge JEI collects the classes annotated
 * {@code @JeiPlugin} from FML's scan data and calls their no-argument constructor ({@code ForgePluginFinder}); on
 * Fabric it reads the {@code jei_mod_plugin} entry point ({@code FabricPluginFinder}). And the rest of the mod never
 * names this package, so JEI's classes stay unloaded without JEI.
 */
class PiratesJeiPluginTest {

    @Test
    void neoForgeScanFindsTheAnnotation() throws Exception {
        // @JeiPlugin has class retention: FML's ASM scan sees it, reflection does not, so look in the class file
        try (InputStream in = PiratesJeiPlugin.class.getResourceAsStream("PiratesJeiPlugin.class")) {
            assertNotNull(in);
            String bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(bytes.contains("Lmezz/jei/api/JeiPlugin;"), "PiratesJeiPlugin carries @JeiPlugin");
        }
        assertTrue(IModPlugin.class.isAssignableFrom(PiratesJeiPlugin.class));
        IModPlugin plugin = PiratesJeiPlugin.class.getDeclaredConstructor().newInstance();
        assertEquals("pirates_n_ships:jei_plugin", plugin.getPluginUid().toString());
    }

    @Test
    void fabricEntryPointNamesThePlugin() throws Exception {
        Path json = Path.of("..", "fabric", "src", "main", "resources", "fabric.mod.json");
        JsonObject mod = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
        var plugins = mod.getAsJsonObject("entrypoints").getAsJsonArray("jei_mod_plugin");
        assertNotNull(plugins, "fabric.mod.json has a jei_mod_plugin entry point");
        assertEquals(PiratesJeiPlugin.class.getName(), plugins.get(0).getAsString());
        assertTrue(mod.getAsJsonObject("suggests").has("jei"), "JEI is only suggested on Fabric");
    }

    @Test
    void nothingOutsideThePackageNamesIt() throws Exception {
        Path root = Path.of("src", "main", "java");
        Path own = root.resolve(Path.of("com", "richardsenger", "piratesnships", "compat", "jei"));
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (f.startsWith(own)) continue;
                String src = Files.readString(f);
                // a qualified name (import or code); comments say "compat.jei" without the root package
                if (src.contains("piratesnships.compat.jei") || src.contains("mezz.jei")) offenders.add(f.toString());
            }
        }
        assertEquals(List.of(), offenders, "only compat/jei may name JEI or the plugin package");
    }
}
