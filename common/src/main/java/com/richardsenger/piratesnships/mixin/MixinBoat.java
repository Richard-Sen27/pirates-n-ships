package com.richardsenger.piratesnships.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.richardsenger.piratesnships.hazards.waves.boats.BoatWaveRule;
import com.richardsenger.piratesnships.hazards.waves.boats.BoatWaves;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla boats ride the simulated waves (WAV6, docs/design.md §5.4; the rule is {@link BoatWaveRule}, the world lookup
 * {@link BoatWaves}). Over the open sea the boat's water level is the moving surface instead of the flat still level;
 * everywhere else (lakes, rivers, shallows, decks, dry hulls, a flat sea, {@code waves.boats} off) every handler returns
 * without touching vanilla.
 *
 * <p>Three small hooks into the float logic, the same on the server and on the client that drives the boat:
 * <ul>
 *   <li>{@code getStatus} (HEAD, cancellable): vanilla decides "in water" from the water blocks under the hull's bottom
 *       and "under water" from those at its top, so a boat lifted onto a crest above the real water would read as in the
 *       air and fall, and one in a trough as under water. Over the moving sea the boat is {@code IN_WATER} with the
 *       moving level as its {@code waterLevel} while its bottom lies below that level, and otherwise on land or in the
 *       air, by vanilla's own ground test;</li>
 *   <li>{@code getWaterLevelAbove} (return value): the level vanilla sets a boat to when it drops into water from the
 *       air;</li>
 *   <li>{@code floatBoat} (TAIL): vanilla's buoyancy damps the vertical speed relative to still water; the carry
 *       ({@link BoatWaveRule#carry}) makes it relative to the moving surface, so the boat keeps up with a rising crest.
 *       Not after vanilla's drop-in snap, and only when vanilla's buoyancy acted (the boat's y below the water level).</li>
 * </ul>
 *
 * <p>Why a mixin: neither loader has an event or API for a boat's float level or status (NeoForge's
 * {@code IBoatExtension#canBoatInFluid} only chooses which fluids carry a boat; Fabric has nothing), and the status
 * and level are private to {@code Boat}. NeoForge 21.1 keeps all three methods with vanilla's signatures (it only
 * swaps the fluid test inside them); both loaders' {@code ClientMixinTargetsTest} check the targets.
 */
@Mixin(Boat.class)
public abstract class MixinBoat {

    @Shadow
    private double waterLevel;
    @Shadow
    private float landFriction;
    @Shadow
    private Boat.Status status;
    @Shadow
    private Boat.Status oldStatus;

    @Shadow
    public abstract float getGroundFriction();

    /** The moving surface's rise over the coming tick, from this tick's status; NaN where vanilla's water applies. */
    @Unique
    private double pirates_n_ships$surfaceSpeed = Double.NaN;

    @Inject(method = "getStatus", at = @At("HEAD"), cancellable = true)
    private void pirates_n_ships$waveStatus(CallbackInfoReturnable<Boat.Status> cir) {
        Boat self = (Boat) (Object) this;
        AABB box = self.getBoundingBox();
        BoatWaves.Surface s = BoatWaves.forBoat(self.level(), self.getX(), box.minY, self.getZ(), self.onGround());
        if (s == null) {
            pirates_n_ships$surfaceSpeed = Double.NaN;
            return;
        }
        pirates_n_ships$surfaceSpeed = s.speed();
        if (box.minY < s.y()) {
            waterLevel = s.y();
            cir.setReturnValue(Boat.Status.IN_WATER);
            return;
        }
        float friction = getGroundFriction();
        if (friction > 0.0f) {
            landFriction = friction;
            cir.setReturnValue(Boat.Status.ON_LAND);
        } else {
            cir.setReturnValue(Boat.Status.IN_AIR);
        }
    }

    @ModifyReturnValue(method = "getWaterLevelAbove", at = @At("RETURN"))
    private float pirates_n_ships$waveLevelAbove(float original) {
        Boat self = (Boat) (Object) this;
        BoatWaves.Surface s = BoatWaves.forBoat(self.level(), self.getX(), self.getBoundingBox().minY, self.getZ(), self.onGround());
        return s == null ? original : (float) s.y();
    }

    @Inject(method = "floatBoat", at = @At("TAIL"))
    private void pirates_n_ships$carry(CallbackInfo ci) {
        Boat self = (Boat) (Object) this;
        double u = pirates_n_ships$surfaceSpeed;
        if (Double.isNaN(u) || status != Boat.Status.IN_WATER || oldStatus == Boat.Status.IN_AIR || waterLevel <= self.getY()) {
            return;
        }
        Vec3 v = self.getDeltaMovement();
        self.setDeltaMovement(v.x, BoatWaveRule.carry(v.y, u), v.z);
    }
}
