package com.richardsenger.piratesnships.fabric.mixin;

import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * Fires {@code CommonEvents.CONTAINER_OPEN} and {@code CONTAINER_CLOSE} where NeoForge fires
 * {@code PlayerContainerEvent.Open} / {@code .Close}: after {@code openMenu} (or {@code openHorseInventory}) made the
 * new menu the player's {@code containerMenu}, and in {@code doCloseContainer} after {@code menu.removed(player)} and
 * before the player's menu falls back to the inventory.
 *
 * <p>Why a mixin: Fabric API has no container open or close event.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Unique
    private Player pirates_n_ships$self() {
        return (Player) (Object) this;
    }

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void pirates_n_ships$afterOpenMenu(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        Player self = pirates_n_ships$self();
        if (cir.getReturnValue().isPresent() && self.containerMenu != self.inventoryMenu) {
            CommonEvents.CONTAINER_OPEN.invoker().on(self, self.containerMenu);
        }
    }

    @Inject(method = "openHorseInventory", at = @At("TAIL"))
    private void pirates_n_ships$afterOpenHorseInventory(AbstractHorse horse, Container inventory, CallbackInfo ci) {
        Player self = pirates_n_ships$self();
        CommonEvents.CONTAINER_OPEN.invoker().on(self, self.containerMenu);
    }

    @Inject(method = "doCloseContainer", at = @At(value = "FIELD", opcode = 181 /* PUTFIELD */,
            target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;"))
    private void pirates_n_ships$beforeMenuReset(CallbackInfo ci) {
        Player self = pirates_n_ships$self();
        CommonEvents.CONTAINER_CLOSE.invoker().on(self, self.containerMenu);
    }
}
