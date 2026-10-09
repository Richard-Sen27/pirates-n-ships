package com.richardsenger.piratesnships.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two {@code CommonEvents} hooks in {@link LivingEntity}, where NeoForge fires the matching events.
 * <ul>
 *   <li>{@code LIVING_INCOMING_DAMAGE}: in {@code hurt}, after the invulnerability, client, dead and fire-resistance
 *       checks, at the {@code isSleeping()} call (the spot of NeoForge's {@code LivingIncomingDamageEvent} and of
 *       Fabric API's {@code ALLOW_DAMAGE}). The listeners' amount replaces the argument; {@code <= 0} cancels the hurt.
 *       Why a mixin: Fabric API's {@code ALLOW_DAMAGE} can only cancel, not change the amount.</li>
 *   <li>{@code ITEM_USE_FINISH}: around {@code finishUsingItem} in {@code completeUsingItem}, with a copy of the stack
 *       from before the use and the result (NeoForge's {@code LivingEntityUseItemEvent.Finish}). Why a mixin: Fabric
 *       API has no item-use-finish event.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "hurt", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"))
    private void pirates_n_ships$incomingDamage(DamageSource source, float original, CallbackInfoReturnable<Boolean> cir,
                                                @Local(argsOnly = true) LocalFloatRef amount) {
        if (!CommonEvents.LIVING_INCOMING_DAMAGE.hasListeners()) return;
        float a = CommonEvents.LIVING_INCOMING_DAMAGE.invoker().onDamage((LivingEntity) (Object) this, source, amount.get());
        if (a <= 0) cir.setReturnValue(false);
        else amount.set(a);
    }

    @WrapOperation(method = "completeUsingItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack pirates_n_ships$itemUseFinish(ItemStack stack, Level level, LivingEntity entity, Operation<ItemStack> original) {
        ItemStack used = stack.copy();
        ItemStack result = original.call(stack, level, entity);
        CommonEvents.ITEM_USE_FINISH.invoker().onFinish(entity, used, result);
        return result;
    }
}
