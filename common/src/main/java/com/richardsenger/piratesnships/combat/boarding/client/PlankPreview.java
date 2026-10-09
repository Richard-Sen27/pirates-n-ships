package com.richardsenger.piratesnships.combat.boarding.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.combat.boarding.BoardingClientConfig;
import com.richardsenger.piratesnships.combat.boarding.BoardingConfig;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlankBlock;
import com.richardsenger.piratesnships.combat.boarding.PlankPlanner;
import com.richardsenger.piratesnships.combat.boarding.PlankRun;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client: the boarding plank's placement preview (BRD2, BRD4). While the player holds a plank and looks at a block, the
 * run the server would lay there is planned with the same rule ({@link PlankPlanner}, from the client's view of the
 * world and the ships) and the straight board it would be is outlined in green, from its start to its landing; when a
 * cell is in the way, that cell is outlined in red. The server stays authoritative: the preview only shows. Planned
 * again when the looked-at block, face or facing changes and every {@link #REFRESH_TICKS} ticks; drawn every frame at
 * the ship's render pose.
 */
public final class PlankPreview {

    private static final int REFRESH_TICKS = 5;
    private static final double HALF = (8 - BoardingPlankBlock.INSET) / 16.0;
    private static final double THICK = BoardingPlankBlock.THICKNESS / 16.0;

    /**
     * What is shown: the board's underside from {@code start} to {@code end} (frame positions, its middle) laid
     * {@code facing}, or one cell in the way ({@code bad}, frame position).
     */
    private record Shown(@Nullable Vec3 start, @Nullable Vec3 end, @Nullable Direction facing, @Nullable BlockPos bad) {
    }

    private record Key(BlockPos pos, Direction face, Direction facing) {
    }

    private static @Nullable Shown shown;
    private static @Nullable Key key;
    private static long planned;

    private PlankPreview() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(PlankPreview::tick);
        ClientEvents.RENDER_AFTER_TRANSLUCENT.register(PlankPreview::render);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> {
            shown = null;
            key = null;
        });
    }

    private static boolean holdsPlank(LocalPlayer player) {
        return player.getMainHandItem().is(BoardingContent.PLANK_ITEM.get()) || player.getOffhandItem().is(BoardingContent.PLANK_ITEM.get());
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !BoardingClientConfig.PREVIEW.get() || !BoardingConfig.ENABLED.get()
                || !holdsPlank(player) || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            shown = null;
            key = null;
            return;
        }
        Key k = new Key(hit.getBlockPos().immutable(), hit.getDirection(), player.getDirection());
        long now = level.getGameTime();
        if (k.equals(key) && now - planned < REFRESH_TICKS) {
            return;
        }
        key = k;
        planned = now;
        PlankPlanner.Plan plan = PlankPlanner.plan(level, k.pos(), k.face(), k.facing(), PlankPlanner.settings());
        if (plan.found() && plan.facing() != null) {
            Direction f = plan.facing();
            BlockPos base = plan.cells().get(0);
            Vec3 start = new Vec3(base.getX() + 0.5 - 0.5 * f.getStepX(), base.getY() + plan.heights().get(0).near(),
                    base.getZ() + 0.5 - 0.5 * f.getStepZ());
            Vec3 end = start.add(f.getStepX() * (double) plan.columns(), plan.rise(), f.getStepZ() * (double) plan.columns());
            shown = new Shown(start, end, f, null);
        } else if (plan.failure() == PlankRun.Failure.BLOCKED && plan.blockedCell() != null) {
            shown = new Shown(null, null, null, plan.blockedCell());
        } else {
            shown = null;
        }
    }

    private static void render(Camera camera, Frustum frustum, float partialTick) {
        Shown s = shown;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (s == null || level == null) {
            return;
        }
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.lines();
        VertexConsumer vc = buffers.getBuffer(type);
        if (s.bad() != null) {
            AABB b = new AABB(0, 0, 0, 1, 1, 1).deflate(0.02);
            Vec3 o = Vec3.atLowerCornerOf(s.bad());
            Vec3[] c = new Vec3[8];
            for (int i = 0; i < 8; i++) {
                c[i] = o.add((i & 1) != 0 ? b.maxX : b.minX, (i & 2) != 0 ? b.maxY : b.minY, (i & 4) != 0 ? b.maxZ : b.minZ);
            }
            edges(level, vc, c, cam, partialTick, 1.0f, 0.25f, 0.2f);
        }
        if (s.start() != null && s.end() != null && s.facing() != null) {
            Direction f = s.facing();
            Vec3 across = new Vec3(f.getClockWise().getStepX(), 0, f.getClockWise().getStepZ()).scale(HALF);
            Vec3 up = new Vec3(0, THICK, 0);
            Vec3[] c = new Vec3[8];
            for (int i = 0; i < 8; i++) {
                Vec3 along = (i & 1) != 0 ? s.end() : s.start();
                c[i] = along.add((i & 4) != 0 ? across : across.reverse()).add((i & 2) != 0 ? up : Vec3.ZERO);
            }
            edges(level, vc, c, cam, partialTick, 0.35f, 1.0f, 0.45f);
        }
        buffers.endBatch(type);
    }

    /**
     * The twelve edges of a box given by its corners (frame positions; corner {@code i} differs from {@code i ^ 1},
     * {@code i ^ 2} and {@code i ^ 4} along one edge each), at the render pose of the ship holding them, if any.
     */
    private static void edges(ClientLevel level, VertexConsumer vc, Vec3[] local, Vec3 cam, float pt, float r, float g, float bl) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vec3 w = ClientShipPoses.toWorld(level, local[i], pt);
            c[i] = (w == null ? local[i] : w).subtract(cam);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            Vec3 a = c[e[0]], z = c[e[1]];
            Vec3 n = z.subtract(a).normalize();
            vc.addVertex((float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 0.9f).setNormal((float) n.x, (float) n.y, (float) n.z);
            vc.addVertex((float) z.x, (float) z.y, (float) z.z).setColor(r, g, bl, 0.9f).setNormal((float) n.x, (float) n.y, (float) n.z);
        }
    }
}
