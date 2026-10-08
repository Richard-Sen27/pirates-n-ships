package com.richardsenger.piratesnships.ship.hull.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.hull.runtime.CellSet;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullConfig;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Client: the water surface inside flooded compartments (FLD1, docs/design.md §4.4). Sable's water occlusion hides the
 * world's water in a hull's dry cells, and the world's water has no faces inside its own volume, so a flooding hold showed
 * no water at all. For every synced surface ({@link FloodSurfaceStore}) of every visible ship this draws one translucent
 * sheet of water right after the world's translucent layer.
 *
 * <p>Geometry ({@link FloodSurfaceGeometry}): the server's level is a plane along the ship's analysis up vector. The
 * client turns that plane level with the world (the ship's render pose maps world up into the plot frame), pivoting on
 * the surface's own centre, so the water stays flat while the ship rolls; then it cuts the plane into one polygon per
 * compartment cell it crosses, which keeps the sheet inside the compartment's cells and so inside the hull. Each polygon
 * is drawn from both sides (the sheet is seen from above and from below).
 *
 * <p>Look: vanilla's still-water sprite from the block atlas (animated with the atlas), the biome water tint at the
 * compartment, full vertex alpha with the sprite's own alpha like vanilla water, the shade of an upward face, and the
 * light of each cell the way Sable lights block entities on ships ({@link ClientShipPoses#plotLight}). Render type
 * {@code translucent_moving_block} (block format, block atlas, translucency, lightmap): deliberately not
 * {@code rendertype_translucent}, whose shader Sable patches to discard fragments inside the dry region, which is
 * exactly where the surface's dry side lies.
 *
 * <p>Cost: per frame and surface, one pass over the compartment's cells (eight dot products per cell) and two quads per
 * crossed cell (one per side; a tilted plane may cut a cell into up to six corners, two quads per side), i.e. for an
 * upright ship two quads per block of water surface. All surfaces of all ships go into one buffer and one draw call.
 */
public final class FloodSurfaceRenderer {

    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");

    // debug counters, logged every STATS_FRAMES frames that drew something
    private static final int STATS_FRAMES = 600;
    private static long statFrames, statSurfaces, statQuads, statNanos;

    private FloodSurfaceRenderer() {
    }

    public static void init() {
        ClientEvents.RENDER_AFTER_TRANSLUCENT.register(FloodSurfaceRenderer::render);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> FloodSurfaceStore.CLIENT.clear());
    }

    /** The plane of one surface in its local frame (cell set min corner, plot axes): {@code n · p = d}. */
    private record Plane(double nx, double ny, double nz, double d) {
    }

    /**
     * The world-level plane of {@code surface} at time {@code now}: world up in the ship's plot frame through the
     * surface's anchor on the synced plane.
     */
    private static Plane plane(FloodSurfaceStore.Ship ship, FloodSurfaceStore.Surface surface, Pose3dc pose, double now) {
        double level = surface.level(now);
        double[] a = surface.anchor(level, ship.upX(), ship.upY(), ship.upZ());
        Vector3d up = pose.transformNormalInverse(new Vector3d(0, 1, 0), new Vector3d());
        double l = up.length();
        if (!(l > 1e-9)) {
            return new Plane(ship.upX(), ship.upY(), ship.upZ(), level);
        }
        up.div(l);
        return new Plane(up.x, up.y, up.z, up.x * a[0] + up.y * a[1] + up.z * a[2]);
    }

    private static Vec3 minCorner(CellSet c) {
        return new Vec3(c.minX(), c.minY(), c.minZ());
    }

    private static Vec3 cellCenter(CellSet c) {
        return new Vec3(c.minX() + c.sizeX() / 2.0, c.minY() + c.sizeY() / 2.0, c.minZ() + c.sizeZ() / 2.0);
    }

    // ------------------------------------------------------------------ rendering

    private static void render(Camera camera, Frustum frustum, float partialTick) {
        FloodSurfaceStore store = FloodSurfaceStore.CLIENT;
        if (store.isEmpty() || !DryHullConfig.FLOOD_SURFACE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long t0 = System.nanoTime();
        double now = level.getGameTime() + partialTick;
        Vec3 cam = camera.getPosition();
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER_STILL);
        float shade = level.getShade(Direction.UP, true);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.translucentMovingBlock();
        VertexConsumer out = null;
        int surfaces = 0;
        int[] quads = {0};
        for (FloodSurfaceStore.Ship ship : store.ships()) {
            for (FloodSurfaceStore.Surface s : ship.surfaces()) {
                CellSet c = s.cells();
                Pose3dc pose = ClientShipPoses.renderPose(level, cellCenter(c), partialTick);
                if (pose == null) {
                    continue;
                }
                AABB box = worldBox(pose, c);
                if (!frustum.isVisible(box)) {
                    continue;
                }
                Plane p = plane(ship, s, pose, now);
                int tint = BiomeColors.getAverageWaterColor(level, BlockPos.containing(box.getCenter()));
                int r = (int) (((tint >> 16) & 255) * shade), g = (int) (((tint >> 8) & 255) * shade), b = (int) ((tint & 255) * shade);
                if (out == null) {
                    out = buffers.getBuffer(type);
                }
                VertexConsumer vc = out;
                Vector3d v = new Vector3d();
                double[] uv = new double[2];
                BlockPos.MutableBlockPos cellPos = new BlockPos.MutableBlockPos();
                surfaces++;
                FloodSurfaceGeometry.polygons(c, p.nx(), p.ny(), p.nz(), p.d(), (cx, cy, cz, xyz, count) -> {
                    int light = ClientShipPoses.plotLight(level, cellPos.set(c.minX() + cx, c.minY() + cy, c.minZ() + cz));
                    int[] idx = FloodSurfaceGeometry.quadIndices(count);
                    for (int side = 0; side < 2; side++) {
                        float normalY = side == 0 ? 1 : -1;
                        for (int q = 0; q < idx.length; q += 4) {
                            for (int k = 0; k < 4; k++) {
                                // the back side runs the quad backwards so it faces down
                                int i = idx[q + (side == 0 ? k : 3 - k)];
                                double lx = xyz[3 * i], ly = xyz[3 * i + 1], lz = xyz[3 * i + 2];
                                pose.transformPosition(v.set(c.minX() + lx, c.minY() + ly, c.minZ() + lz));
                                FloodSurfaceGeometry.uv(lx, ly, lz, cx, cy, cz, p.nx(), p.ny(), p.nz(), uv);
                                vc.addVertex((float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z))
                                        .setColor(r, g, b, 255)
                                        .setUv(sprite.getU((float) uv[0]), sprite.getV((float) uv[1]))
                                        .setLight(light)
                                        .setNormal(0, normalY, 0);
                            }
                            quads[0]++;
                        }
                    }
                });
            }
        }
        if (out != null) {
            buffers.endBatch(type);
            stats(surfaces, quads[0], System.nanoTime() - t0);
        }
    }

    /** World bounds of the cell set's box at the render pose (its eight corners). */
    private static AABB worldBox(Pose3dc pose, CellSet c) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        Vector3d v = new Vector3d();
        for (int i = 0; i < 8; i++) {
            pose.transformPosition(v.set(c.minX() + ((i & 1) != 0 ? c.sizeX() : 0), c.minY() + ((i & 2) != 0 ? c.sizeY() : 0),
                    c.minZ() + ((i & 4) != 0 ? c.sizeZ() : 0)));
            x0 = Math.min(x0, v.x); y0 = Math.min(y0, v.y); z0 = Math.min(z0, v.z);
            x1 = Math.max(x1, v.x); y1 = Math.max(y1, v.y); z1 = Math.max(z1, v.z);
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    private static void stats(int surfaces, int quads, long nanos) {
        statFrames++;
        statSurfaces += surfaces;
        statQuads += quads;
        statNanos += nanos;
        if (statFrames >= STATS_FRAMES) {
            if (Constants.LOG.isDebugEnabled()) {
                Constants.LOG.debug("Flood surfaces, last {} frames: {} surfaces and {} quads per frame, {} µs per frame",
                        statFrames, statSurfaces / statFrames, statQuads / statFrames, statNanos / 1000 / statFrames);
            }
            statFrames = statSurfaces = statQuads = statNanos = 0;
        }
    }

    // ------------------------------------------------------------------ below the surface

    /**
     * Whether world point {@code world} is in a cell of a flooded compartment and below the surface drawn there this
     * frame (the client's own view, FLD1). False while {@code dry_hull_view.flood_surface} is off. Used by
     * {@code mixin.MixinCamera} (underwater fog) and {@code mixin.MixinScreenEffectRenderer} (underwater overlay).
     */
    public static boolean isBelowSurface(Level level, Vec3 world, float partialTick) {
        FloodSurfaceStore store = FloodSurfaceStore.CLIENT;
        if (store.isEmpty() || !DryHullConfig.FLOOD_SURFACE.get()) {
            return false;
        }
        double now = level.getGameTime() + partialTick;
        for (FloodSurfaceStore.Ship ship : store.ships()) {
            for (FloodSurfaceStore.Surface s : ship.surfaces()) {
                CellSet c = s.cells();
                Pose3dc pose = ClientShipPoses.renderPose(level, cellCenter(c), partialTick);
                if (pose == null) {
                    continue;
                }
                Vec3 local = pose.transformPositionInverse(world).subtract(minCorner(c));
                int bx = (int) Math.floor(local.x), by = (int) Math.floor(local.y), bz = (int) Math.floor(local.z);
                if (!c.contains(c.minX() + bx, c.minY() + by, c.minZ() + bz)) {
                    continue;
                }
                Plane p = plane(ship, s, pose, now);
                if (FloodSurfaceGeometry.below(c, local.x, local.y, local.z, p.nx(), p.ny(), p.nz(), p.d())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The partial tick of the frame being rendered (what the level renderer uses). */
    public static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }
}
