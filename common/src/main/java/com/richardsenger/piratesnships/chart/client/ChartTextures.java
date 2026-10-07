package com.richardsenger.piratesnships.chart.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.render.ChartRaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * GPU textures of chart regions for the chart screen (client only, work package MAP1): one texture per region and
 * zoom step, {@code 64 * pixelsPerCell} pixels square, drawn by {@link ChartRaster}. A region that arrives marks
 * itself and its eight neighbours stale (coast strokes read across the border); a stale texture is redrawn when it is
 * next needed, at most {@link #REBUILDS_PER_FRAME} per frame. At most {@link #MAX_TEXTURES} are kept, least
 * recently used released first. Render thread only.
 */
public final class ChartTextures {

    public static final int MAX_TEXTURES = 64;
    public static final int REBUILDS_PER_FRAME = 4;

    private record Key(long region, int px) {
    }

    private static final class Entry {
        final ResourceLocation id;
        final DynamicTexture texture;
        boolean stale;

        Entry(ResourceLocation id, DynamicTexture texture) {
            this.id = id;
            this.texture = texture;
        }
    }

    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Set<Long> STALE = new HashSet<>();
    private static boolean clearAll;
    private static int rebuiltThisFrame;

    private ChartTextures() {
    }

    /** {@link ClientChart}'s region listener (any thread): {@code Long.MIN_VALUE} drops every texture. */
    public static void onRegion(long key) {
        synchronized (STALE) {
            if (key == Long.MIN_VALUE) {
                clearAll = true;
                return;
            }
            int rx = ChartRegion.keyX(key);
            int rz = ChartRegion.keyZ(key);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    STALE.add(ChartRegion.key(rx + dx, rz + dz));
                }
            }
        }
    }

    /** Call once per frame before drawing. */
    public static void beginFrame() {
        rebuiltThisFrame = 0;
        synchronized (STALE) {
            if (clearAll) {
                releaseAll();
                clearAll = false;
                STALE.clear();
            }
            if (STALE.isEmpty()) return;
            for (Map.Entry<Key, Entry> e : CACHE.entrySet()) {
                if (STALE.contains(e.getKey().region())) e.getValue().stale = true;
            }
            STALE.clear();
        }
    }

    /** The texture of region {@code (rx, rz)} at {@code px} pixels per cell, or {@code null} if it waits for a rebuild budget. */
    public static ResourceLocation texture(int rx, int rz, int px) {
        Key key = new Key(ChartRegion.key(rx, rz), px);
        Entry e = CACHE.get(key);
        if (e != null && !e.stale) return e.id;
        if (rebuiltThisFrame >= REBUILDS_PER_FRAME) return e == null ? null : e.id;
        rebuiltThisFrame++;
        int[] argb = ChartRaster.renderRegion(ClientChart.lookup(), rx, rz, px);
        int size = ChartRegion.SIZE * px;
        if (e == null) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = Constants.id("chart/region_" + rx + "_" + rz + "_" + px);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            e = new Entry(id, texture);
            CACHE.put(key, e);
            trim();
        }
        NativeImage pixels = e.texture.getPixels();
        if (pixels != null) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels.setPixelRGBA(x, y, toAbgr(argb[y * size + x]));
                }
            }
            e.texture.upload();
        }
        e.stale = false;
        return e.id;
    }

    static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    private static void trim() {
        Iterator<Map.Entry<Key, Entry>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && it.hasNext()) {
            Entry e = it.next().getValue();
            Minecraft.getInstance().getTextureManager().release(e.id);
            it.remove();
        }
    }

    public static void releaseAll() {
        for (Entry e : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(e.id);
        }
        CACHE.clear();
    }
}
