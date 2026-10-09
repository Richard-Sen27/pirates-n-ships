package com.richardsenger.piratesnships.hazards.waves.client.surface;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Client cache of the sea cells around the camera (WAV3, physical client only, client thread): per loaded chunk the
 * depth of every sea cell ({@link SurfaceRules}), its water colour and light, and whether a 4×4 block or the chunk holds
 * any sea cell, for the coarse rings (whose vertices read the depth of their corner's columns, WAV5). Reading 256 columns is cheap, but not every frame for every chunk in view, so:
 * <ul>
 *   <li>a chunk is read once when it comes into the mesh's area (at most {@link #FILL_BUDGET} per frame, nearest
 *       first);</li>
 *   <li>a chunk is read again when one of its sections at sea level was recompiled: the mixin that hides vanilla's top
 *       face marks it ({@link #markDirty}, any thread), so a placed or broken block shows within a frame or two
 *       (at most {@link #DIRTY_BUDGET} per frame);</li>
 *   <li>as a safety net every chunk is read again after {@link #STALE_TICKS} ticks ({@link #NEAR_STALE_TICKS} next to
 *       the camera), a few per frame; this catches the one change the mixin cannot see, the last sea cell of a section
 *       disappearing;</li>
 *   <li>a chunk that unloads (or is replaced by a reloaded one) is dropped at once, so no sea is drawn where the world
 *       has no chunk.</li>
 * </ul>
 */
final class SeaSurfaceCells implements SurfaceMesher.Cells {

    static final int FILL_BUDGET = 64;
    static final int DIRTY_BUDGET = 32;
    static final int STALE_BUDGET = 4;
    static final long STALE_TICKS = 200;
    static final long NEAR_STALE_TICKS = 20;

    /** Whether a 1-block cell lies wholly inside a dry hull. */
    @FunctionalInterface
    interface DryTest {
        boolean dry(int x, int z);
    }

    /** Chunks whose sea-level section was recompiled since the last frame ({@link ChunkPos#asLong} keys). */
    private static final Set<Long> DIRTY = ConcurrentHashMap.newKeySet();

    /** One chunk's columns, indexed {@code (z & 15) * 16 + (x & 15)}. */
    static final class Chunk {
        final LevelChunk source;
        final byte[] depth = new byte[256];
        final int[] color = new int[256];
        final int[] light = new int[256];
        /** Bit {@code ((z & 15) >> 2) * 4 + ((x & 15) >> 2)}: the 4×4 block holds a sea cell. */
        int any4;
        boolean any16;
        int repColor, repLight;
        long tick;

        Chunk(LevelChunk source) {
            this.source = source;
        }
    }

    private final Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private @Nullable ClientLevel level;
    private int seaLevel;
    private long lastKey = Long.MIN_VALUE;
    private @Nullable Chunk lastChunk;
    private int frames;
    private @Nullable DryTest dryTest;

    /** Called by the mixin (chunk compile threads) when it hid a sea cell's top in this block's chunk. */
    static void markDirty(int blockX, int blockZ) {
        DIRTY.add(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
    }

    void clear() {
        chunks.clear();
        DIRTY.clear();
        level = null;
        lastChunk = null;
        lastKey = Long.MIN_VALUE;
    }

    int size() {
        return chunks.size();
    }

    /** Brings the cache up to date for this frame's mesh area (client thread). */
    void update(ClientLevel level, int seaLevel, SurfaceLod.Layout layout, double camX, double camZ, long tick) {
        if (level != this.level || seaLevel != this.seaLevel) {
            clear();
            this.level = level;
            this.seaLevel = seaLevel;
        }
        lastChunk = null;
        lastKey = Long.MIN_VALUE;
        SurfaceLod.Square area = layout.far() != null ? layout.far().outer() : layout.mid().outer();
        int minCX = area.minX() >> 4, minCZ = area.minZ() >> 4, maxCX = (area.maxX() - 1) >> 4, maxCZ = (area.maxZ() - 1) >> 4;
        int ccx = (int) Math.floor(camX) >> 4, ccz = (int) Math.floor(camZ) >> 4;

        int dirty = 0;
        if (!DIRTY.isEmpty()) {
            Iterator<Long> it = DIRTY.iterator();
            while (it.hasNext() && dirty < DIRTY_BUDGET) {
                long key = it.next();
                it.remove();
                int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
                if (cx < minCX || cx > maxCX || cz < minCZ || cz > maxCZ) {
                    continue;
                }
                LevelChunk c = level.getChunkSource().getChunk(cx, cz, false);
                if (c == null) {
                    chunks.remove(key);
                } else {
                    chunks.put(key, read(c, tick));
                    dirty++;
                }
            }
        }

        // nearest first: square rings of chunks around the camera's chunk
        int filled = 0, refreshed = 0;
        int reach = Math.max(Math.max(ccx - minCX, maxCX - ccx), Math.max(ccz - minCZ, maxCZ - ccz));
        for (int r = 0; r <= reach; r++) {
            for (int cz = ccz - r; cz <= ccz + r; cz++) {
                boolean edgeRow = cz == ccz - r || cz == ccz + r;
                int step = edgeRow ? 1 : Math.max(1, 2 * r);
                for (int cx = ccx - r; cx <= ccx + r; cx += step) {
                    if (cx < minCX || cx > maxCX || cz < minCZ || cz > maxCZ) {
                        continue;
                    }
                    long key = ChunkPos.asLong(cx, cz);
                    LevelChunk c = level.getChunkSource().getChunk(cx, cz, false);
                    Chunk cached = chunks.get(key);
                    if (c == null) {
                        if (cached != null) {
                            chunks.remove(key);
                        }
                        continue;
                    }
                    if (cached == null || cached.source != c) {
                        if (filled < FILL_BUDGET) {
                            chunks.put(key, read(c, tick));
                            filled++;
                        } else if (cached != null) {
                            chunks.remove(key); // a reloaded chunk: never draw the old one's sea
                        }
                    } else if (refreshed < STALE_BUDGET && tick - cached.tick > (r <= 2 ? NEAR_STALE_TICKS : STALE_TICKS)) {
                        chunks.put(key, read(c, tick));
                        refreshed++;
                    }
                }
            }
        }

        if (++frames % 200 == 0) {
            ObjectIterator<Long2ObjectMap.Entry<Chunk>> it = chunks.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                long key = it.next().getLongKey();
                int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
                if (cx < minCX - 2 || cx > maxCX + 2 || cz < minCZ - 2 || cz > maxCZ + 2) {
                    it.remove();
                }
            }
        }
    }

    /** Reads one chunk's sea cells. */
    private Chunk read(LevelChunk c, long tick) {
        ClientLevel lv = level;
        Chunk out = new Chunk(c);
        out.tick = tick;
        int y = SurfaceRules.surfaceBlockY(seaLevel);
        int baseX = c.getPos().getMinBlockX(), baseZ = c.getPos().getMinBlockZ();
        if (lv == null || y < lv.getMinBuildHeight() || y + 1 >= lv.getMaxBuildHeight()) {
            return out;
        }
        int minY = lv.getMinBuildHeight();
        int bestRep = -1;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = baseX + lx, z = baseZ + lz, i = lz * 16 + lx;
                boolean still = c.getFluidState(x, y, z).getType() == Fluids.WATER;
                boolean air = c.getBlockState(pos.set(x, y + 1, z)).isAir();
                int depth = 0;
                if (SurfaceRules.isSeaCell(y, seaLevel, still, air)) {
                    depth = 1;
                    while (depth < SurfaceRules.MAX_DEPTH && y - depth >= minY
                            && c.getFluidState(x, y - depth, z).is(FluidTags.WATER)) {
                        depth++;
                    }
                    out.color[i] = BiomeColors.getAverageWaterColor(lv, pos.set(x, y, z));
                    out.light[i] = waterLight(lv, pos.set(x, y, z));
                    int centre = Math.abs(lx - 8) + Math.abs(lz - 8);
                    if (bestRep < 0 || centre < bestRep) {
                        bestRep = centre;
                        out.repColor = out.color[i];
                        out.repLight = out.light[i];
                    }
                }
                out.depth[i] = (byte) depth;
            }
        }
        if (bestRep < 0) {
            out.repColor = BiomeColors.getAverageWaterColor(lv, pos.set(baseX + 8, y, baseZ + 8));
            out.repLight = waterLight(lv, pos.set(baseX + 8, y + 1, baseZ + 8));
        }
        for (int b = 0; b < 16; b++) {
            int bx = (b & 3) * 4, bz = (b >> 2) * 4;
            boolean any = false;
            for (int dz = 0; dz < 4; dz++) {
                for (int dx = 0; dx < 4; dx++) {
                    any |= out.depth[(bz + dz) * 16 + bx + dx] > 0;
                }
            }
            if (any) {
                out.any4 |= 1 << b;
            }
        }
        out.any16 = out.any4 != 0;
        for (int i = 0; i < 256; i++) {
            if (out.depth[i] == 0) {
                out.color[i] = out.repColor;
                out.light[i] = out.repLight;
            }
        }
        return out;
    }

    /** Vanilla's light of a water face: the brighter of the block and the one above, per channel. */
    private static int waterLight(ClientLevel level, BlockPos.MutableBlockPos p) {
        int a = LevelRenderer.getLightColor(level, p);
        int b = LevelRenderer.getLightColor(level, p.move(0, 1, 0));
        p.move(0, -1, 0);
        return Math.max(a & 0xFFFF, b & 0xFFFF) | Math.max(a >>> 16, b >>> 16) << 16;
    }

    private @Nullable Chunk chunk(int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        if (key == lastKey) {
            return lastChunk;
        }
        Chunk c = chunks.get(key);
        lastKey = key;
        lastChunk = c;
        return c;
    }

    @Override
    public int depth(int x, int z) {
        Chunk c = chunk(x, z);
        return c == null ? 0 : c.depth[(z & 15) * 16 + (x & 15)];
    }

    @Override
    public boolean anySea(int x, int z, int size) {
        if (size <= 1) {
            return depth(x, z) > 0;
        }
        if (size < 16) {
            for (int bz = z; bz < z + size; bz += 4) {
                for (int bx = x; bx < x + size; bx += 4) {
                    Chunk c = chunk(bx, bz);
                    if (c != null && (c.any4 & (1 << (((bz & 15) >> 2) * 4 + ((bx & 15) >> 2)))) != 0) {
                        return true;
                    }
                }
            }
            return false;
        }
        for (int cz = z; cz < z + size; cz += 16) {
            for (int cx = x; cx < x + size; cx += 16) {
                Chunk c = chunk(cx, cz);
                if (c != null && c.any16) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int color(int x, int z) {
        Chunk c = chunk(x, z);
        return c == null ? 0x3F76E4 : c.color[(z & 15) * 16 + (x & 15)];
    }

    @Override
    public int light(int x, int z) {
        Chunk c = chunk(x, z);
        return c == null ? LightTexture.FULL_BRIGHT : c.light[(z & 15) * 16 + (x & 15)];
    }

    /** The dry-hull test of this frame ({@link SeaSurface}); null: no dry hull near. */
    void setDryTest(@Nullable DryTest dry) {
        this.dryTest = dry;
    }

    @Override
    public boolean dry(int x, int z) {
        return dryTest != null && dryTest.dry(x, z);
    }
}
