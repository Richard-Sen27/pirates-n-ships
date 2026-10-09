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

import java.util.List;

/**
 * Client: the boarding plank's placement preview (BRD2). While the player holds a plank and looks at a block, the run
 * the server would lay there is planned with the same rule ({@link PlankPlanner}, from the client's view of the world and
 * the ships) and its cells are outlined in green, the steps of the ramps included; when a cell is in the way, that cell
 * is outlined in red. The server stays authoritative: the preview only shows. Planned again when the looked-at block,
 * face or facing changes and every {@link #REFRESH_TICKS} ticks; drawn every frame at the ship's render pose.
 */
public final class PlankPreview {

    private static final int REFRESH_TICKS = 5;

    /** What is shown: the cells (frame positions) with pitches, or one cell in the way. */
    private record Shown(List<BlockPos> cells, List<PlankRun.Pitch> pitches, @Nullable Direction facing, @Nullable BlockPos bad) {
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
        if (plan.found()) {
            shown = new Shown(plan.cells(), plan.pitches(), plan.facing(), null);
        } else if (plan.failure() == PlankRun.Failure.BLOCKED && plan.blockedAt() >= 0) {
            PlankRun.Start start = PlankRun.start(k.pos(), k.face(), k.facing());
            shown = start == null ? null : new Shown(List.of(), List.of(), null, start.cell(plan.blockedAt()));
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
            box(level, vc, s.bad(), new AABB(0, 0, 0, 1, 1, 1).deflate(0.02), cam, partialTick, 1.0f, 0.25f, 0.2f);
        }
        for (int i = 0; i < s.cells().size(); i++) {
            BlockPos cell = s.cells().get(i);
            for (AABB b : BoardingPlankBlock.shape(s.pitches().get(i), s.facing()).toAabbs()) {
                box(level, vc, cell, b, cam, partialTick, 0.35f, 1.0f, 0.45f);
            }
        }
        buffers.endBatch(type);
    }

    /** The edges of box {@code b} (block-local) of cell {@code cell}, at the render pose of the ship holding it, if any. */
    private static void box(ClientLevel level, VertexConsumer vc, BlockPos cell, AABB b, Vec3 cam, float pt,
                            float r, float g, float bl) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vec3 local = new Vec3(cell.getX() + ((i & 1) != 0 ? b.maxX : b.minX), cell.getY() + ((i & 2) != 0 ? b.maxY : b.minY),
                    cell.getZ() + ((i & 4) != 0 ? b.maxZ : b.minZ));
            Vec3 w = ClientShipPoses.toWorld(level, local, pt);
            c[i] = (w == null ? local : w).subtract(cam);
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
