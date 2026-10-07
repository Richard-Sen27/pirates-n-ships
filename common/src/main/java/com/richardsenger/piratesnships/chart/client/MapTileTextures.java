package com.richardsenger.piratesnships.chart.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.render.ChartSheet;
import com.richardsenger.piratesnships.chart.render.MapTileRaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GPU textures of map tile drawings (client only, work package MAP2): one {@link DynamicTexture} per distinct drawing
 * (tiles showing the same drawing share it), the picture of {@link MapTileRaster#picture} with the compass rose and
 * marker icons taken from the chart sheet. A block entity whose drawing changes simply asks for the new drawing's
 * texture; the old one ages out. At most {@link #MAX_TEXTURES} are kept (least recently used released first), at most
 * {@link #BUILDS_PER_TICK} are built per client tick. Render thread only.
 */
public final class MapTileTextures {

    public static final int MAX_TEXTURES = 64;
    public static final int BUILDS_PER_TICK = 8;

    private record Entry(ResourceLocation id, DynamicTexture texture) {
    }

    private static final Map<MapTileDrawing, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int nextId;
    private static int builtThisTick;
    private static int[] sheet;
    private static boolean sheetLoaded;

    private MapTileTextures() {
    }

    /** The texture of {@code d}, or {@code null} while it waits for this tick's build budget. */
    public static ResourceLocation texture(MapTileDrawing d) {
        Entry e = CACHE.get(d);
        if (e != null) return e.id();
        if (builtThisTick >= BUILDS_PER_TICK) return null;
        builtThisTick++;
        int scale = MapTileRaster.pictureScale(d.size());
        int w = d.size() * scale;
        int[] argb = MapTileRaster.picture(d, scale, sheet(), ChartSheet.WIDTH, ChartSheet.HEIGHT);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, w, false);
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(x, y, ChartTextures.toAbgr(argb[y * w + x]));
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation id = Constants.id("map_tile/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        CACHE.put(d, new Entry(id, texture));
        trim();
        return id;
    }

    /** {@code CLIENT_TICK_END}: a new build budget. */
    public static void onClientTick() {
        builtThisTick = 0;
    }

    /** The chart sheet as ARGB (read once), or {@code null} if it cannot be read. */
    private static int[] sheet() {
        if (sheetLoaded) return sheet;
        sheetLoaded = true;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(ChartSheet.TEXTURE);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            if (img.getWidth() != ChartSheet.WIDTH || img.getHeight() != ChartSheet.HEIGHT) return null;
            int[] out = new int[ChartSheet.WIDTH * ChartSheet.HEIGHT];
            for (int y = 0; y < ChartSheet.HEIGHT; y++) {
                for (int x = 0; x < ChartSheet.WIDTH; x++) {
                    out[y * ChartSheet.WIDTH + x] = ChartTextures.toAbgr(img.getPixelRGBA(x, y));
                }
            }
            sheet = out;
        } catch (IOException e) {
            Constants.LOG.warn("Map tiles: cannot read the chart sheet {}", ChartSheet.TEXTURE, e);
        }
        return sheet;
    }

    private static void trim() {
        Iterator<Map.Entry<MapTileDrawing, Entry>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && it.hasNext()) {
            Minecraft.getInstance().getTextureManager().release(it.next().getValue().id());
            it.remove();
        }
    }

    /** Leaving the server (or a resource reload): every texture goes, and the sheet is read again. */
    public static void releaseAll() {
        for (Entry e : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(e.id());
        }
        CACHE.clear();
        sheet = null;
        sheetLoaded = false;
    }
}
