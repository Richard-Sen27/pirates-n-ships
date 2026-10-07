package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Forwards NeoForge game-bus events to {@link CommonEvents}. One line per event, no feature logic. */
public final class NeoForgeEventForwarder {

    private NeoForgeEventForwarder() {
    }

    public static void attach(IEventBus bus) {
        bus.addListener(ServerStartingEvent.class, e -> CommonEvents.SERVER_STARTING.invoker().on(e.getServer()));
        bus.addListener(ServerStartedEvent.class, e -> CommonEvents.SERVER_STARTED.invoker().on(e.getServer()));
        bus.addListener(ServerStoppingEvent.class, e -> CommonEvents.SERVER_STOPPING.invoker().on(e.getServer()));
        bus.addListener(ServerStoppedEvent.class, e -> CommonEvents.SERVER_STOPPED.invoker().on(e.getServer()));

        bus.addListener(ServerTickEvent.Pre.class, e -> CommonEvents.SERVER_TICK_START.invoker().on(e.getServer()));
        bus.addListener(ServerTickEvent.Post.class, e -> CommonEvents.SERVER_TICK_END.invoker().on(e.getServer()));
        bus.addListener(LevelTickEvent.Pre.class, e -> {
            if (e.getLevel() instanceof ServerLevel l) CommonEvents.LEVEL_TICK_START.invoker().onTick(l);
        });
        bus.addListener(LevelTickEvent.Post.class, e -> {
            if (e.getLevel() instanceof ServerLevel l) CommonEvents.LEVEL_TICK_END.invoker().onTick(l);
        });
        bus.addListener(PlayerTickEvent.Post.class, e -> CommonEvents.PLAYER_TICK_END.invoker().onTick(e.getEntity()));

        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.PLAYER_LOGIN.invoker().on(p);
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.PLAYER_LOGOUT.invoker().on(p);
        });
        bus.addListener(PlayerEvent.Clone.class, e -> {
            if (e.getOriginal() instanceof ServerPlayer o && e.getEntity() instanceof ServerPlayer n) {
                CommonEvents.PLAYER_CLONE.invoker().onClone(o, n, e.isWasDeath());
            }
        });
        bus.addListener(PlayerContainerEvent.Open.class, e -> CommonEvents.CONTAINER_OPEN.invoker().on(e.getEntity(), e.getContainer()));
        bus.addListener(PlayerContainerEvent.Close.class, e -> CommonEvents.CONTAINER_CLOSE.invoker().on(e.getEntity(), e.getContainer()));

        bus.addListener(EntityJoinLevelEvent.class, e -> {
            if (CommonEvents.ENTITY_JOIN_LEVEL.invoker().onJoin(e.getEntity(), e.getLevel())) e.setCanceled(true);
        });
        bus.addListener(PlayerInteractEvent.EntityInteract.class, e -> {
            InteractionResult r = CommonEvents.ENTITY_INTERACT.invoker().onInteract(e.getEntity(), e.getTarget(), e.getHand());
            if (r != InteractionResult.PASS) {
                e.setCancellationResult(r);
                e.setCanceled(true);
            }
        });
        bus.addListener(LivingIncomingDamageEvent.class, e -> {
            if (!CommonEvents.LIVING_INCOMING_DAMAGE.hasListeners()) return;
            float amount = CommonEvents.LIVING_INCOMING_DAMAGE.invoker().onDamage(e.getEntity(), e.getSource(), e.getAmount());
            if (amount <= 0) e.setCanceled(true);
            else e.setAmount(amount);
        });
        bus.addListener(LivingDeathEvent.class, e -> {
            if (CommonEvents.LIVING_DEATH.invoker().onDeath(e.getEntity(), e.getSource())) e.setCanceled(true);
        });
        bus.addListener(LivingEntityUseItemEvent.Finish.class, e -> CommonEvents.ITEM_USE_FINISH.invoker().onFinish(e.getEntity(), e.getItem(), e.getResultStack()));

        bus.addListener(BlockEvent.BreakEvent.class, e -> {
            if (e.getLevel() instanceof Level l && CommonEvents.BLOCK_BREAK.invoker().onBreak(l, e.getPos(), e.getState(), e.getPlayer())) e.setCanceled(true);
        });
        bus.addListener(BlockEvent.EntityPlaceEvent.class, e -> {
            if (e.getLevel() instanceof Level l && CommonEvents.BLOCK_PLACE.invoker().onPlace(l, e.getPos(), e.getPlacedBlock(), e.getEntity())) e.setCanceled(true);
        });

        bus.addListener(RegisterCommandsEvent.class, e -> CommonEvents.REGISTER_COMMANDS.invoker().register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection()));
        bus.addListener(OnDatapackSyncEvent.class, e -> e.getRelevantPlayers().forEach(p -> CommonEvents.DATAPACK_SYNC.invoker().onSync(p, e.getPlayer() != null)));
        bus.addListener(AddReloadListenerEvent.class, e -> CommonEvents.ADD_RELOAD_LISTENERS.invoker().add(e::addListener, e.getRegistryAccess()));
    }
}
