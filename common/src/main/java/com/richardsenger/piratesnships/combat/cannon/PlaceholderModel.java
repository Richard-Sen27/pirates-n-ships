package com.richardsenger.piratesnships.combat.cannon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small builder for the placeholder element models of the guns (P2; the Blockbench models follow in F7g). Each box
 * gets all six faces with explicit UVs inside 0..16 (vanilla derives a face's UV from the element position, which
 * leaves the sprite for elements outside the block, art/README.md), so the elements may use the full −16..32 range.
 * Datagen only.
 */
final class PlaceholderModel {

    private final Map<String, String> textures = new LinkedHashMap<>();
    private final JsonArray elements = new JsonArray();

    /** A model whose particle texture is {@code particle} (a texture id). */
    PlaceholderModel(String particle) {
        textures.put("particle", particle);
    }

    /** Declares a texture variable {@code key} → texture id. */
    PlaceholderModel texture(String key, String id) {
        textures.put(key, id);
        return this;
    }

    /** A box from {@code (x1, y1, z1)} to {@code (x2, y2, z2)} in pixels, every face with texture variable {@code tex}. */
    PlaceholderModel box(double x1, double y1, double z1, double x2, double y2, double z2, String tex) {
        JsonObject e = new JsonObject();
        e.add("from", vec(x1, y1, z1));
        e.add("to", vec(x2, y2, z2));
        JsonObject faces = new JsonObject();
        for (Direction d : Direction.values()) {
            double w, h;
            switch (d.getAxis()) {
                case X -> { w = z2 - z1; h = y2 - y1; }
                case Y -> { w = x2 - x1; h = z2 - z1; }
                default -> { w = x2 - x1; h = y2 - y1; }
            }
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray();
            uv.add(0);
            uv.add(0);
            uv.add(Math.min(16, Math.max(0.5, w)));
            uv.add(Math.min(16, Math.max(0.5, h)));
            face.add("uv", uv);
            face.addProperty("texture", "#" + tex);
            faces.add(d.getSerializedName(), face);
        }
        e.add("faces", faces);
        elements.add(e);
        return this;
    }

    /** Copies every element of {@code other} into this model (textures must be declared here too). */
    PlaceholderModel include(PlaceholderModel other) {
        other.elements.forEach(elements::add);
        other.textures.forEach(textures::putIfAbsent);
        return this;
    }

    PlaceholderModel copy() {
        PlaceholderModel m = new PlaceholderModel(textures.get("particle"));
        return m.include(this);
    }

    JsonElement json() {
        JsonObject json = new JsonObject();
        JsonObject tex = new JsonObject();
        textures.forEach(tex::addProperty);
        json.add("textures", tex);
        if (!elements.isEmpty()) json.add("elements", elements.deepCopy());
        return json;
    }

    private static JsonArray vec(double x, double y, double z) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        a.add(z);
        return a;
    }
}
