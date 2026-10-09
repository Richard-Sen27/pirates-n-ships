package com.richardsenger.piratesnships.mixin;

import com.richardsenger.piratesnships.hazards.waves.boats.BoatWaveRule;
import com.richardsenger.piratesnships.hazards.waves.boats.BoatWaves;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Items floating on the open sea bob with the simulated waves (WAV6, docs/design.md §5.4, {@link BoatWaveRule}). Vanilla
 * floats an item with a buoyancy of 0.0005 blocks per tick², far too weak to follow a wave that rises at up to 0.1 blocks
 * per tick, so near the moving surface ({@link BoatWaveRule#itemCaptured}) the item's vertical speed for this tick's
 * move becomes the surface's speed plus a capped pull to its float level ({@link BoatWaveRule#itemVelocity}); the
 * horizontal speed stays vanilla's. The new speed is also stored, so vanilla's gravity does not pile up underneath it.
 * Everywhere else, and with {@code waves.floating_items} off, the move is vanilla's.
 *
 * <p>How: {@code ItemEntity#tick} calls {@code move(SELF, getDeltaMovement())} once (vanilla and NeoForge 21.1, which
 * only adds its fluid-type branch before it); the {@code @ModifyArg} changes that call's motion. Both loaders'
 * {@code ClientMixinTargetsTest} check the call.
 *
 * <p>Why a mixin: neither loader has an event for an item's motion (NeoForge's {@code FluidType#setItemMovement} is for
 * modded fluid types only; Fabric has nothing).
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntity {

    @ModifyArg(method = "tick", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private Vec3 pirates_n_ships$rideTheSea(Vec3 motion) {
        ItemEntity self = (ItemEntity) (Object) this;
        double minY = self.getBoundingBox().minY;
        BoatWaves.Surface s = BoatWaves.forItem(self.level(), self.getX(), minY, self.getZ(), self.onGround());
        if (s == null || !BoatWaveRule.itemCaptured(minY, s.y())) {
            return motion;
        }
        Vec3 ride = new Vec3(motion.x, BoatWaveRule.itemVelocity(minY, s.y(), s.speed()), motion.z);
        self.setDeltaMovement(ride);
        return ride;
    }
}
