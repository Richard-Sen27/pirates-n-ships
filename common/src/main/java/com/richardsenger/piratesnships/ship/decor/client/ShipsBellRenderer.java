package com.richardsenger.piratesnships.ship.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import com.richardsenger.piratesnships.ship.decor.ShipsBellBlock;
import com.richardsenger.piratesnships.ship.decor.ShipsBellBlockEntity;
import com.richardsenger.piratesnships.ship.decor.ShipsBellSwing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Matrix4f;

/**
 * Draws the ship's bell and its clapper (BELL1, design.md §4.8 "Decor") from {@link ShipsBellBlockEntity}; the block
 * model is only the mount ({@code ships_bell_post}, {@code ships_bell_wall}). Both hand-made stand-alone models
 * ({@code ships_bell_bell}: yoke and bell, {@code ships_bell_clapper}: clapper and lanyard) are built hanging from the
 * post's pin and turn about the x axis through it ({@link ShipsBellSwing#PIN_Y}, {@link ShipsBellSwing#PIN_Z} of the
 * north-facing model) by {@link ShipsBellSwing#bellDegrees} and {@link ShipsBellSwing#clapperDegrees} over
 * {@code ship_decor.bell_ring_ticks} since the synced ring start; on the wall bracket they hang
 * {@link ShipsBellSwing#WALL_DROP} px lower. Lit by the block's light, untinted on the block atlas, turned to the
 * block's facing like the block state. With {@code bell_visuals.enabled} off the bell hangs still.
 *
 * <p>Works on ships like {@code CannonBarrelRenderer}: Sable draws a sub-level's block entities with the ship's pose
 * (docs/sable-notes.md §9.0g). The swing stays inside the block, so the default culling box fits.
 */
public class ShipsBellRenderer implements BlockEntityRenderer<ShipsBellBlockEntity> {

    /** The hand-made stand-alone parts this renderer draws (models/block/*.json). */
    static final ResourceLocation BELL_MODEL = Constants.id("block/ships_bell_bell");
    static final ResourceLocation CLAPPER_MODEL = Constants.id("block/ships_bell_clapper");

    public ShipsBellRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShipsBellBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ShipsBellBlock)) return;
        Direction facing = state.getValue(ShipsBellBlock.FACING);
        double bell = 0.0;
        double clapper = 0.0;
        if (DecorConfig.BELL_SWING_ENABLED.get() && be.getLevel() != null) {
            double t = be.sinceRing(be.getLevel().getGameTime() + partialTick);
            int ticks = DecorConfig.BELL_RING_TICKS.get();
            if (ShipsBellSwing.swinging(t, ticks)) {
                double amplitude = DecorConfig.BELL_SWING_DEGREES.get();
                double sign = ShipsBellSwing.direction(facing, be.strike());
                bell = ShipsBellSwing.bellDegrees(t, ticks, amplitude, sign);
                clapper = ShipsBellSwing.clapperDegrees(t, ticks, amplitude, sign);
            }
        }
        ModelBlockRenderer models = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        boolean wall = state.getValue(ShipsBellBlock.FACE) == AttachFace.WALL;
        draw(models, pose, buffers, BELL_MODEL, ShipsBellSwing.partPose(facing, wall, bell), light, overlay);
        draw(models, pose, buffers, CLAPPER_MODEL, ShipsBellSwing.partPose(facing, wall, clapper), light, overlay);
    }

    private static void draw(ModelBlockRenderer models, PoseStack pose, MultiBufferSource buffers, ResourceLocation id,
                             Matrix4f part, int light, int overlay) {
        pose.pushPose();
        pose.mulPose(part);
        models.renderModel(pose.last(), buffers.getBuffer(Sheets.solidBlockSheet()), null, ClientEvents.additionalModel(id),
                1f, 1f, 1f, light, overlay);
        pose.popPose();
    }
}
