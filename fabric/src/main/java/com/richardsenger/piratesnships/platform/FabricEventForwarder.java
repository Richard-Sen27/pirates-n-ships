package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * Forwards Fabric API events to {@link CommonEvents}, the twin of {@code NeoForgeEventForwarder}. One line per event,
 * no feature logic. Events without a Fabric API event are fired by this module's mixins
 * ({@code com.richardsenger.piratesnships.fabric.mixin}, {@code pirates_n_ships.fabric.mixins.json}); docs/fabric.md
 * lists every mapping and every difference in timing or cancellation.
 */
public final class FabricEventForwarder {

    private FabricEventForwarder() {
    }

    public static void attach() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> CommonEvents.SERVER_STARTING.invoker().on(s));
        ServerLifecycleEvents.SERVER_STARTED.register(s -> CommonEvents.SERVER_STARTED.invoker().on(s));
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> CommonEvents.SERVER_STOPPING.invoker().on(s));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> CommonEvents.SERVER_STOPPED.invoker().on(s));

        ServerTickEvents.START_SERVER_TICK.register(s -> CommonEvents.SERVER_TICK_START.invoker().on(s));
        ServerTickEvents.END_SERVER_TICK.register(s -> CommonEvents.SERVER_TICK_END.invoker().on(s));
        ServerTickEvents.START_WORLD_TICK.register(l -> CommonEvents.LEVEL_TICK_START.invoker().onTick(l));
        ServerTickEvents.END_WORLD_TICK.register(l -> CommonEvents.LEVEL_TICK_END.invoker().onTick(l));
        // PLAYER_TICK_END: no Fabric API event, fired by fabric.mixin.MixinPlayer (Player#tick, both sides)

        ServerPlayerEvents.JOIN.register(p -> CommonEvents.PLAYER_LOGIN.invoker().on(p));
        ServerPlayerEvents.LEAVE.register(p -> CommonEvents.PLAYER_LOGOUT.invoker().on(p));
        ServerPlayerEvents.COPY_FROM.register((o, n, alive) -> CommonEvents.PLAYER_CLONE.invoker().onClone(o, n, !alive));
        EntitySleepEvents.STOP_SLEEPING.register((e, pos) -> {
            if (e instanceof Player p) CommonEvents.PLAYER_WAKE_UP.invoker().onWake(p);
        });
        // HammockBlock's NeoForge IBlockExtension overrides (isBed & co.), asked through Fabric's sleep events
        FabricHammockBeds.attach();
        // CONTAINER_OPEN / CONTAINER_CLOSE: no Fabric API event, fired by fabric.mixin.MixinServerPlayer

        // Server side; the client side is FabricClientSetup's. After the entity was added, so not cancellable
        ServerEntityEvents.ENTITY_LOAD.register((e, l) -> CommonEvents.ENTITY_JOIN_LEVEL.invoker().onJoin(e, l));
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            // hit != null is the "interact at" variant (NeoForge's EntityInteractSpecific); ours is the plain interaction
            if (hit != null || player.isSpectator()) return InteractionResult.PASS;
            return CommonEvents.ENTITY_INTERACT.invoker().onInteract(player, target, hand);
        });
        // LIVING_INCOMING_DAMAGE and ITEM_USE_FINISH: fired by fabric.mixin.MixinLivingEntity
        // After the death, so the listeners' "cancel" result is ignored (none cancels); ALLOW_DEATH would fire before
        // a totem of undying saves the entity
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> CommonEvents.LIVING_DEATH.invoker().onDeath(e, src));

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !CommonEvents.BLOCK_BREAK.invoker().onBreak(level, pos, state, player));
        // BLOCK_PLACE: fired by fabric.mixin.MixinBlockItem (player block placement)

        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> CommonEvents.REGISTER_COMMANDS.invoker().register(d, ctx, env));
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((p, joined) -> CommonEvents.DATAPACK_SYNC.invoker().onSync(p, joined));
        FabricReloadListeners.attach();
    }
}
