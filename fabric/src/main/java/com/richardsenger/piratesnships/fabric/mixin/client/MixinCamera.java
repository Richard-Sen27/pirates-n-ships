package com.richardsenger.piratesnships.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Camera roll on Fabric: fires {@code ClientEvents.COMPUTE_CAMERA_ROLL} in {@code Camera#setup} and rolls the camera
 * by the result, as NeoForge's patched camera does with {@code ViewportEvent.ComputeCameraAngles} (FAB2, used by the
 * wave sway WV1).
 *
 * <p>Why a mixin: vanilla's camera has no roll at all (its {@code setRotation(yaw, pitch)} builds the rotation with a
 * zero roll angle) and Fabric API has no camera event. NeoForge adds a roll argument: the first
 * {@code setRotation} of {@code setup} gets the event's roll, the mirrored one of the reversed third-person view gets
 * the negated roll, the sleeping one none; its rotation is {@code rotationYXZ(pi - yaw, -pitch, -roll)} in radians.
 * This mixin keeps that: it wraps the first two {@code setRotation} calls of {@code setup} to hold the roll in a field
 * while the call runs, and {@code setRotation}'s {@code rotationYXZ} takes {@code -roll} as its z angle. Any other
 * {@code setRotation} call (other mods, the sleeping view) sees no roll. Vanilla's view matrix is built from the
 * camera's rotation ({@code GameRenderer#renderLevel}), so the roll shows as on NeoForge.
 * The targets are checked headlessly by {@code ClientMixinTargetsTest} (fabric tests, HV1b).
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    /** The roll in degrees that the running {@code setRotation} applies; 0 outside {@code setup}. */
    @Unique
    private float pirates_n_ships$pendingRoll;

    /** The roll of this frame's {@code setup}, for the mirrored third-person rotation. */
    @Unique
    private float pirates_n_ships$frameRoll;

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
    private void pirates_n_ships$rollView(Camera camera, float yRot, float xRot, Operation<Void> original,
                                          @Local(argsOnly = true) float partialTick) {
        float roll = ClientEvents.COMPUTE_CAMERA_ROLL.invoker().modify(partialTick, 0.0F);
        pirates_n_ships$frameRoll = roll;
        pirates_n_ships$pendingRoll = roll;
        try {
            original.call(camera, yRot, xRot);
        } finally {
            pirates_n_ships$pendingRoll = 0.0F;
        }
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private void pirates_n_ships$rollMirroredView(Camera camera, float yRot, float xRot, Operation<Void> original) {
        pirates_n_ships$pendingRoll = -pirates_n_ships$frameRoll;
        try {
            original.call(camera, yRot, xRot);
        } finally {
            pirates_n_ships$pendingRoll = 0.0F;
        }
    }

    @ModifyArg(method = "setRotation(FF)V", index = 2, at = @At(value = "INVOKE",
            target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"))
    private float pirates_n_ships$roll(float angleZ) {
        float roll = pirates_n_ships$pendingRoll;
        return roll == 0.0F ? angleZ : -roll * Mth.DEG_TO_RAD;
    }
}
