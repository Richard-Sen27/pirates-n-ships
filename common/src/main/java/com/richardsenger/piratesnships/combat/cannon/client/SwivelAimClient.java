package com.richardsenger.piratesnships.combat.cannon.client;

import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlock;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.SwivelReleasePayload;
import com.richardsenger.piratesnships.combat.cannon.SwivelRules;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Client side of aiming a swivel gun (docs/design.md §8.2, P2). Pressing use with an empty hand (not sneaking) on a
 * swivel gun lets vanilla send the block use (the server makes the player the gun's operator) and remembers the gun;
 * while the button stays down, vanilla's repeated uses are cancelled so nothing else is used while aiming, and the
 * renderer shows the aim from the local view ({@link #localAim}); when the button is let go, a
 * {@link SwivelReleasePayload} asks the server to fire. The server turns the gun from the player's view by itself.
 */
public final class SwivelAimClient {

    private static @Nullable BlockPos aiming;

    private SwivelAimClient() {
    }

    public static void init() {
        ClientEvents.INTERACTION_KEY.register(SwivelAimClient::onInteraction);
        ClientEvents.CLIENT_TICK_END.register(SwivelAimClient::onTick);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> aiming = null);
    }

    private static ClientEvents.InteractionKeyResult onInteraction(Minecraft mc, ClientEvents.InteractionInput input, InteractionHand hand) {
        if (input != ClientEvents.InteractionInput.USE) {
            return ClientEvents.InteractionKeyResult.PASS;
        }
        if (aiming != null) {
            return ClientEvents.InteractionKeyResult.CANCEL; // held: the gun is being aimed, no other use
        }
        if (hand == InteractionHand.MAIN_HAND && mc.player != null && mc.level != null && mc.player.getMainHandItem().isEmpty()
                && !mc.player.isSecondaryUseActive() && mc.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof SwivelGunBlock) {
            aiming = hit.getBlockPos(); // a plot position on a ship, as the block use the server receives
        }
        return ClientEvents.InteractionKeyResult.PASS;
    }

    private static void onTick(Minecraft mc) {
        if (aiming == null) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            aiming = null;
            return;
        }
        if (!mc.options.keyUse.isDown()) {
            Services.NETWORK.sendToServer(new SwivelReleasePayload(aiming));
            aiming = null;
        }
    }

    /** The aim of {@code be} from the local player's view while that player aims it, else null. */
    static @Nullable SwivelRules.Aim localAim(SwivelGunBlockEntity be, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (aiming == null || mc.player == null || be.getLevel() == null || !aiming.equals(be.getBlockPos())) {
            return null;
        }
        Vec3 look = mc.player.getViewVector(partialTick);
        Quaterniond q = ClientShipPoses.orientation(be.getLevel(), Vec3.atCenterOf(be.getBlockPos()), partialTick);
        if (q != null) {
            Vector3d v = q.transformInverse(new Vector3d(look.x, look.y, look.z));
            look = new Vec3(v.x, v.y, v.z);
        }
        return SwivelRules.aimFromLook(look, be.yaw(), CannonConfig.SWIVEL_MIN_ELEVATION.get(), CannonConfig.SWIVEL_MAX_ELEVATION.get());
    }
}
