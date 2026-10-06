package com.richardsenger.piratesnships.ship.decor.flag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * A small builder for vanilla block model JSON made of {@code elements} (pure, Gson only), for models that no vanilla
 * {@code ModelTemplate} covers. It checks what vanilla's model loader would reject: element coordinates must lie in
 * {@code -16..32} ({@code BlockElement.MIN_EXTENT/MAX_EXTENT}) and {@code from <= to}; face UVs are in vanilla's
 * {@code 0..16} space (16 units span the whole texture, whatever its pixel size).
 * <p>
 * Write the result through {@code ModelContext.models().accept(id, builder::build)}.
 */
public final class ElementModel {

    public static final float MIN_EXTENT = -16f;
    public static final float MAX_EXTENT = 32f;

    /** Face names as vanilla writes them. */
    public enum Face { DOWN, UP, NORTH, SOUTH, WEST, EAST;
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    private final JsonObject root = new JsonObject();
    private final JsonObject textures = new JsonObject();
    private final JsonArray elements = new JsonArray();

    public ElementModel texture(String key, String location) {
        textures.addProperty(key, location);
        return this;
    }

    /** NeoForge reads {@code render_type} (e.g. {@code minecraft:cutout}); Fabric needs a render layer registration. */
    public ElementModel renderType(String renderType) {
        root.addProperty("render_type", renderType);
        return this;
    }

    public ElementModel ambientOcclusion(boolean ao) {
        root.addProperty("ambientocclusion", ao);
        return this;
    }

    /** Starts a cuboid element from {@code (x0,y0,z0)} to {@code (x1,y1,z1)}; add faces, then call {@link Element#end()}. */
    public Element element(float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] from = {x0, y0, z0};
        float[] to = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            checkExtent(from[i]);
            checkExtent(to[i]);
            if (from[i] > to[i]) throw new IllegalArgumentException("element from > to on axis " + i + ": " + from[i] + " > " + to[i]);
        }
        return new Element(from, to);
    }

    public JsonObject build() {
        JsonObject json = root.deepCopy();
        json.add("textures", textures.deepCopy());
        json.add("elements", elements.deepCopy());
        return json;
    }

    private static void checkExtent(float v) {
        if (v < MIN_EXTENT || v > MAX_EXTENT) throw new IllegalArgumentException("element coordinate " + v + " outside " + MIN_EXTENT + ".." + MAX_EXTENT);
    }

    private static void checkUv(float v) {
        if (v < 0f || v > 16f) throw new IllegalArgumentException("uv " + v + " outside 0..16");
    }

    private static JsonArray array(float... values) {
        JsonArray a = new JsonArray();
        for (float v : values) a.add(v);
        return a;
    }

    public final class Element {
        private final JsonObject json = new JsonObject();
        private final JsonObject faces = new JsonObject();

        private Element(float[] from, float[] to) {
            json.add("from", array(from));
            json.add("to", array(to));
        }

        public Element shade(boolean shade) {
            json.addProperty("shade", shade);
            return this;
        }

        /**
         * A face with explicit UVs {@code [u0, v0, u1, v1]} (0..16). {@code u0 > u1} mirrors the texture horizontally.
         * {@code texture} is a reference such as {@code "#cloth"}.
         */
        public Element face(Face face, float u0, float v0, float u1, float v1, String texture) {
            checkUv(u0);
            checkUv(v0);
            checkUv(u1);
            checkUv(v1);
            JsonObject f = new JsonObject();
            f.add("uv", array(u0, v0, u1, v1));
            f.addProperty("texture", texture);
            faces.add(face.key(), f);
            return this;
        }

        public ElementModel end() {
            if (faces.isEmpty()) throw new IllegalStateException("element without faces");
            json.add("faces", faces);
            elements.add(json);
            return ElementModel.this;
        }
    }
}
