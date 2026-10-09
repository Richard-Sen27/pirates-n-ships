package com.richardsenger.piratesnships.compat.jei;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads our own lang files from the client's resources (en_us, then the selected language over it; resource packs
 * included), the way vanilla's {@code ClientLanguage} does: the loaded language cannot list its keys, and the info
 * pages need every Almanac key. Client only.
 */
final class AlmanacLangReader {

    private AlmanacLangReader() {
    }

    /** Every key of our namespace with its text; empty when nothing could be read. */
    static Map<String, String> read() {
        Map<String, String> out = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        ResourceManager resources = mc.getResourceManager();
        Set<String> codes = new LinkedHashSet<>();
        codes.add("en_us");
        codes.add(mc.getLanguageManager().getSelected());
        for (String code : codes) {
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "lang/" + code + ".json");
            for (Resource resource : resources.getResourceStack(file)) {
                try (InputStream in = resource.open()) {
                    Language.loadFromJson(in, out::put);
                } catch (Exception e) {
                    Constants.LOG.warn("JEI info pages: could not read {} from {}", file, resource.sourcePackId(), e);
                }
            }
        }
        return out;
    }
}
