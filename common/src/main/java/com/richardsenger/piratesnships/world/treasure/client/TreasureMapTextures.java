package com.richardsenger.piratesnships.world.treasure.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.render.ChartRaster;
import com.richardsenger.piratesnships.world.treasure.TreasureMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GPU textures of treasure map pictures (client only, TM1): the map's cells drawn by the chart's {@link ChartRaster}
 * at {@link #PX} pixels per cell (unknown cells transparent, so the parchment under it shows). One texture per
 * distinct picture, at most {@link #MAX_TEXTURES}, least recently used released first. Render thread only.
 */
public final class TreasureMapTextures {

    public static final int PX = 3;
    public static final int SIZE = TreasureMapData.SIZE * PX;
    public static final int MAX_TEXTURES = 8;

    private record Entry(ResourceLocation id, DynamicTexture texture) {
    }

    /** Keyed by the whole component (equal maps share a texture; a map that turns found gets a new one once). */
    private static final Map<TreasureMapData, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int nextId;

    private TreasureMapTextures() {
    }

    public static ResourceLocation texture(TreasureMapData d) {
        Entry e = CACHE.get(d);
        if (e != null) return e.id();
        int[] argb = ChartRaster.render(d::cell, d.minCx(), d.minCz(), TreasureMapData.SIZE, TreasureMapData.SIZE, PX);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixelRGBA(x, y, toAbgr(argb[y * SIZE + x]));
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation id = Constants.id("treasure_map/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        CACHE.put(d, new Entry(id, texture));
        trim();
        return id;
    }

    static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    private static void trim() {
        Iterator<Map.Entry<TreasureMapData, Entry>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && it.hasNext()) {
            Minecraft.getInstance().getTextureManager().release(it.next().getValue().id());
            it.remove();
        }
    }

    /** Leaving the server: every texture goes. */
    public static void releaseAll() {
        for (Entry e : CACHE.values()) Minecraft.getInstance().getTextureManager().release(e.id());
        CACHE.clear();
    }
}
