package com.richardsenger.piratesnships.hazards.waves.client.surface;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.waves.ClientWaves;
import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The visible sea surface (WAV3, docs/design.md §5.4; physical client only): the sea around the camera rises and falls
 * with the synced wave field ({@link ClientWaves}). Rendering only: collision, swimming, buoyancy and ship physics stay
 * on the flat sea level.
 *
 * <h2>How</h2>
 * <ul>
 *   <li><b>Vanilla's flat top is hidden</b> where the mesh draws: {@code mixin.MixinLiquidBlockRenderer} asks
 *       {@link #hidesTop} for every water block's up face while chunk sections are compiled (worker threads), and it
 *       answers yes for sea cells ({@link SurfaceRules}) while the surface is on. Turning it on or off re-renders the
 *       chunks ({@code LevelRenderer#allChanged}).</li>
 *   <li><b>The mesh</b> is built every frame by {@link SurfaceMesher} over the rings of {@link SurfaceLod}, from the
 *       cell cache {@link SeaSurfaceCells} and the waves of {@link SurfaceWaves} (the one global field the ships feel,
 *       WAV7), into one reused buffer, and drawn in one call right after the world's translucent layer
 *       ({@link ClientEvents#RENDER_AFTER_TRANSLUCENT}, the flood surface's stage).</li>
 *   <li><b>Look</b>: render type {@code translucent} (translucency, lightmap, vanilla's fog, the translucent target
 *       under Fabulous), with its texture swapped for {@link SeaFrameTexture}: the current frame of the animated
 *       {@code block/water_still} in a small repeating texture with the atlas's mipmaps, tiled once per block in every
 *       ring like vanilla's still water (WAV5; the atlas sprite cannot repeat, so WAV3 stretched it over a coarse cell
 *       and the far water looked different). The biome water colour and vanilla's light per cell, the shade of an up
 *       face. The shader is {@code rendertype_translucent} (it samples only {@code Sampler0} and the lightmap, no atlas
 *       uniform), the one Sable patches to discard fragments inside a dry hull's region, so a crest never shows inside a
 *       hull; cells wholly inside a hull are not even emitted.</li>
 *   <li><b>Off</b> with {@code wave_effects.surface = false}, with a renderer that replaces vanilla's chunk mesher
 *       ({@link SurfaceSupport}, logged once), and in a dimension with a ceiling or without a sky.</li>
 * </ul>
 */
public final class SeaSurface {

    /** Sections farther than this many chunks beyond the render distance keep their flat top (Sable's plots). */
    static final int WINDOW_MARGIN_CHUNKS = 8;
    private static final int OFF = Integer.MIN_VALUE;
    private static final int STATS_FRAMES = 600;

    /** Y of the blocks whose top face is hidden, or {@link #OFF}. Read by chunk compile threads. */
    private static volatile int hiddenTopY = OFF;
    /** Horizontal window around the camera in which tops are hidden (excludes Sable's far-away plots). */
    private static volatile int windowMinX, windowMinZ, windowMaxX, windowMaxZ;

    private static @Nullable Optional<String> blockingMod;
    private static final SeaSurfaceCells CELLS = new SeaSurfaceCells();
    private static final SurfaceMesher MESHER = new SurfaceMesher();
    private static final SurfaceWaves WAVES = new SurfaceWaves();
    private static final SurfaceWaves POINT_WAVES = new SurfaceWaves();
    private static final SeaFrameTexture FRAME = new SeaFrameTexture();
    private static double pointTime = Double.NaN;
    private static int fullDepth = 4;
    private static @Nullable ByteBufferBuilder bytes;
    private static long statFrames, statQuads, statVertices, statNanos, statMaxNanos;

    private SeaSurface() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(SeaSurface::tick);
        ClientEvents.RENDER_AFTER_TRANSLUCENT.register(SeaSurface::render);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> {
            CELLS.clear();
            pointTime = Double.NaN;
        });
    }

    /** Whether the surface is on in the current level. */
    public static boolean isActive() {
        return hiddenTopY != OFF;
    }

    // ------------------------------------------------------------------ state

    private static Optional<String> blockingMod() {
        if (blockingMod == null) {
            blockingMod = SurfaceSupport.blockingMod(Services.PLATFORM::isModLoaded);
            blockingMod.ifPresent(id -> Constants.LOG.info(
                    "Visible sea surface (wave_effects.surface) is off: '{}' replaces the chunk renderer, the water stays flat", id));
        }
        return blockingMod;
    }

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != null && !mc.isPaused()) {
            FRAME.tick(); // vanilla's texture animation ticks under the same condition (Minecraft#tick)
        }
        int want = OFF;
        Vec3 centre = null;
        if (level != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            centre = camera.isInitialized() ? camera.getPosition() : mc.player != null ? mc.player.position() : null;
            boolean sea = SurfaceSupport.dimensionHasSea(level.dimensionType().hasSkyLight(), level.dimensionType().hasCeiling());
            if (centre != null && SurfaceSupport.active(HazardConfig.SURFACE.get(), blockingMod(), sea)) {
                want = SurfaceRules.surfaceBlockY(level.getSeaLevel());
            }
        }
        if (centre != null) {
            int half = (mc.options.getEffectiveRenderDistance() + WINDOW_MARGIN_CHUNKS) * 16;
            int cx = Mth.floor(centre.x), cz = Mth.floor(centre.z);
            windowMinX = cx - half;
            windowMinZ = cz - half;
            windowMaxX = cx + half;
            windowMaxZ = cz + half;
        }
        fullDepth = SurfaceRules.clampFullDepth(HazardConfig.SURFACE_FULL_DEPTH.get());
        if (want != hiddenTopY) {
            hiddenTopY = want;
            if (want == OFF) {
                CELLS.clear();
            }
            if (level != null) {
                mc.levelRenderer.allChanged();
            }
        }
    }

    /**
     * {@code mixin.MixinLiquidBlockRenderer}, any chunk compile thread: whether the up face of the fluid at {@code pos}
     * (with {@code above} on top of it) is replaced by the moving surface. Marks the chunk for the cell cache.
     */
    public static boolean hidesTop(BlockGetter level, BlockPos pos, BlockState above) {
        int y = hiddenTopY;
        if (y == OFF || pos.getY() != y) {
            return false;
        }
        int x = pos.getX(), z = pos.getZ();
        if (x < windowMinX || x >= windowMaxX || z < windowMinZ || z >= windowMaxZ) {
            return false;
        }
        boolean still = level.getFluidState(pos).getType() == Fluids.WATER;
        if (!SurfaceRules.isSeaCell(pos.getY(), y + 1, still, above.isAir())) {
            return false;
        }
        SeaSurfaceCells.markDirty(x, z);
        return true;
    }

    // ------------------------------------------------------------------ the mesh

    private static void render(Camera camera, Frustum frustum, float partialTick) {
        int y = hiddenTopY;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (y == OFF || level == null) {
            return;
        }
        long t0 = System.nanoTime();
        int seaLevel = y + 1;
        double time = level.getGameTime() + partialTick;
        Vec3 cam = camera.getPosition();

        WaveField field = ClientWaves.hasData() ? ClientWaves.field(time) : WaveField.FLAT;
        WAVES.prepare(field, time);

        SurfaceLod.Layout layout = SurfaceLod.layout(cam.x, cam.z, HazardConfig.SURFACE_NEAR_RADIUS.get(),
                HazardConfig.SURFACE_MID_RADIUS.get(), HazardConfig.SURFACE_FAR_CELL.get(), mc.options.getEffectiveRenderDistance() * 16);
        CELLS.update(level, seaLevel, layout, cam.x, cam.z, level.getGameTime());
        double baseY = SurfaceRules.stillTop(seaLevel);
        double reach = field.maxHeight() + 0.5;
        CELLS.setDryTest(dryTest(level, baseY, layout.near().outer()));

        SurfaceMesher.Params params = new SurfaceMesher.Params(baseY, fullDepth,
                HazardConfig.SURFACE_UNDERSIDE.get() && cam.y < baseY + reach, Mth.floor(cam.x), Mth.floor(cam.z));
        float shade = level.getShade(Direction.UP, true);
        SurfaceMesher.Visibility vis = (x0, z0, x1, z1) -> frustum.isVisible(new AABB(x0, baseY - reach, z0, x1, baseY + reach, z1));

        if (bytes == null) {
            bytes = new ByteBufferBuilder(1 << 20);
        }
        BufferBuilder out = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        double cx = cam.x, cy = cam.y, cz = cam.z;
        SurfaceMesher.Stats stats = MESHER.build(layout, CELLS, WAVES, params, vis, (x, vy, z, rgb, u, v, light, up) ->
                out.addVertex((float) (x - cx), (float) (vy - cy), (float) (z - cz))
                        .setColor((int) (((rgb >> 16) & 255) * shade), (int) (((rgb >> 8) & 255) * shade), (int) ((rgb & 255) * shade), 255)
                        .setUv(u, v)
                        .setLight(light)
                        .setNormal(0.0f, up ? 1.0f : -1.0f, 0.0f));
        MeshData mesh = out.build();
        if (mesh != null) {
            int frame = FRAME.bindable(mc);
            RenderType type = RenderType.translucent();
            type.setupRenderState();
            RenderSystem.setShaderTexture(0, frame); // the repeating water frame instead of the block atlas
            BufferUploader.drawWithShader(mesh);
            type.clearRenderState();
        }
        stats(stats, System.nanoTime() - t0);
    }

    /** The dry-hull test for the near ring: a cell wholly inside a water occlusion region (its four corners at the still top). */
    private static SeaSurfaceCells.@Nullable DryTest dryTest(ClientLevel level, double baseY, SurfaceLod.Square near) {
        List<WaterRegions.View> views = WaterRegions.views(level);
        if (views.isEmpty()) {
            return null;
        }
        List<AABB> boxes = new ArrayList<>(views.size());
        for (WaterRegions.View v : views) {
            double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
            double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                Vec3 w = v.toWorld(new Vec3((i & 1) != 0 ? v.maxX + 1 : v.minX, (i & 2) != 0 ? v.maxY + 1 : v.minY,
                        (i & 4) != 0 ? v.maxZ + 1 : v.minZ));
                x0 = Math.min(x0, w.x); y0 = Math.min(y0, w.y); z0 = Math.min(z0, w.z);
                x1 = Math.max(x1, w.x); y1 = Math.max(y1, w.y); z1 = Math.max(z1, w.z);
            }
            if (baseY >= y0 && baseY <= y1 && x1 >= near.minX() && x0 <= near.maxX() && z1 >= near.minZ() && z0 <= near.maxZ()) {
                boxes.add(new AABB(x0, y0, z0, x1, y1, z1));
            }
        }
        if (boxes.isEmpty()) {
            return null;
        }
        double in = 0.05;
        return (x, z) -> {
            boolean near2 = false;
            for (AABB b : boxes) {
                if (x + 1 >= b.minX && x <= b.maxX && z + 1 >= b.minZ && z <= b.maxZ) {
                    near2 = true;
                    break;
                }
            }
            return near2
                    && WaterRegions.isOccluded(level, new Vec3(x + in, baseY, z + in))
                    && WaterRegions.isOccluded(level, new Vec3(x + 1 - in, baseY, z + in))
                    && WaterRegions.isOccluded(level, new Vec3(x + in, baseY, z + 1 - in))
                    && WaterRegions.isOccluded(level, new Vec3(x + 1 - in, baseY, z + 1 - in));
        };
    }

    private static void stats(SurfaceMesher.Stats s, long nanos) {
        statFrames++;
        statQuads += s.cells() + s.seams();
        statVertices += s.vertices();
        statNanos += nanos;
        statMaxNanos = Math.max(statMaxNanos, nanos);
        if (statFrames >= STATS_FRAMES) {
            if (Constants.LOG.isDebugEnabled()) {
                Constants.LOG.debug("Sea surface, last {} frames: {} quads and {} vertices per frame, {} µs per frame (max {} µs), {} chunks cached",
                        statFrames, statQuads / statFrames, statVertices / statFrames, statNanos / 1000 / statFrames,
                        statMaxNanos / 1000, CELLS.size());
            }
            statFrames = statQuads = statVertices = statNanos = statMaxNanos = 0;
        }
    }

    // ------------------------------------------------------------------ foam

    /**
     * World y of the visible surface at {@code (x, z)} and client time {@code time} (the foam rides on it, WD1), or NaN
     * while the surface is off. Within a block it interpolates the heights of the block's corners the way the near ring
     * computes them (wave × the smallest depth fade around the corner).
     */
    public static double surfaceY(double x, double z, double time) {
        int y = hiddenTopY;
        if (y == OFF) {
            return Double.NaN;
        }
        double base = SurfaceRules.stillTop(y + 1);
        SurfaceWaves w = pointWaves(time);
        if (w.isFlat()) {
            return base;
        }
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        double fx = x - x0, fz = z - z0;
        double h00 = cornerOffset(w, x0, z0), h10 = cornerOffset(w, x0 + 1, z0);
        double h01 = cornerOffset(w, x0, z0 + 1), h11 = cornerOffset(w, x0 + 1, z0 + 1);
        return base + Mth.lerp(fz, Mth.lerp(fx, h00, h10), Mth.lerp(fx, h01, h11));
    }

    private static SurfaceWaves pointWaves(double time) {
        if (time != pointTime) {
            pointTime = time;
            POINT_WAVES.prepare(ClientWaves.hasData() ? ClientWaves.field(time) : WaveField.FLAT, time);
        }
        return POINT_WAVES;
    }

    private static double cornerOffset(SurfaceWaves w, int x, int z) {
        double f = SurfaceRules.vertexFade(
                SurfaceRules.cellFade(CELLS.depth(x - 1, z - 1), fullDepth), SurfaceRules.cellFade(CELLS.depth(x, z - 1), fullDepth),
                SurfaceRules.cellFade(CELLS.depth(x - 1, z), fullDepth), SurfaceRules.cellFade(CELLS.depth(x, z), fullDepth));
        return f == 0.0 ? 0.0 : w.height(x, z) * f;
    }
}
