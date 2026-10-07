package com.richardsenger.piratesnships.platform.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Loader-independent game events (both physical sides). Feature modules register listeners from
 * {@code registerEvents()}; the loader module fires them. Adding a new event = one field here + one forwarding
 * line per loader.
 *
 * <pre>{@code
 * CommonEvents.SERVER_TICK_END.register(server -> ShipTicker.tick(server));
 * CommonEvents.BLOCK_BREAK.register((level, pos, state, player) -> isProtected(pos)); // true cancels
 * }</pre>
 */
public final class CommonEvents {

    private CommonEvents() {
    }

    // --- Server lifecycle -------------------------------------------------------------------------------------

    public static final Event<ServerLifecycle> SERVER_STARTING = Event.create(ls -> s -> ls.forEach(l -> l.on(s)));
    public static final Event<ServerLifecycle> SERVER_STARTED = Event.create(ls -> s -> ls.forEach(l -> l.on(s)));
    public static final Event<ServerLifecycle> SERVER_STOPPING = Event.create(ls -> s -> ls.forEach(l -> l.on(s)));
    public static final Event<ServerLifecycle> SERVER_STOPPED = Event.create(ls -> s -> ls.forEach(l -> l.on(s)));

    // --- Ticks ------------------------------------------------------------------------------------------------

    public static final Event<ServerLifecycle> SERVER_TICK_START = Event.create(ls -> s -> ls.forEach(l -> l.on(s)));
    public static final Event<ServerLifecycle> SERVER_TICK_END = Event.create(ls -> s -> ls.forEach(l -> l.on(s)));
    /** Server levels only. */
    public static final Event<LevelTick> LEVEL_TICK_START = Event.create(ls -> lv -> ls.forEach(l -> l.onTick(lv)));
    /** Server levels only. */
    public static final Event<LevelTick> LEVEL_TICK_END = Event.create(ls -> lv -> ls.forEach(l -> l.onTick(lv)));
    /** Fires on both logical sides; check {@code player.level().isClientSide()}. */
    public static final Event<PlayerTick> PLAYER_TICK_END = Event.create(ls -> p -> ls.forEach(l -> l.onTick(p)));

    // --- Players ----------------------------------------------------------------------------------------------

    public static final Event<PlayerEvent> PLAYER_LOGIN = Event.create(ls -> p -> ls.forEach(l -> l.on(p)));
    public static final Event<PlayerEvent> PLAYER_LOGOUT = Event.create(ls -> p -> ls.forEach(l -> l.on(p)));
    /** A new player object replaces an old one (respawn or returning from the End). */
    public static final Event<PlayerClone> PLAYER_CLONE = Event.create(ls -> (o, n, d) -> ls.forEach(l -> l.onClone(o, n, d)));

    /**
     * Server only: a player opened a container menu ({@code ServerPlayer.openMenu} or a horse inventory), after the
     * menu was created and set as {@code player.containerMenu}. Loot tables of the container are already unpacked.
     */
    public static final Event<ContainerMenuEvent> CONTAINER_OPEN = Event.create(ls -> (p, m) -> ls.forEach(l -> l.on(p, m)));
    /**
     * Server only: a player's container menu was closed (by the player, by the server, or on logout), after
     * {@code menu.removed(player)} returned. The menu's slots still reflect the container's final contents.
     */
    public static final Event<ContainerMenuEvent> CONTAINER_CLOSE = Event.create(ls -> (p, m) -> ls.forEach(l -> l.on(p, m)));

    // --- Entities ---------------------------------------------------------------------------------------------

    /** Both logical sides. Returning {@code true} cancels the join. */
    public static final Event<EntityJoinLevel> ENTITY_JOIN_LEVEL = Event.create(ls -> (e, lv) -> {
        for (EntityJoinLevel l : ls) if (l.onJoin(e, lv)) return true;
        return false;
    });
    /** Server side, before armor and other reductions. Each listener gets the amount returned by the previous one. */
    public static final Event<LivingDamage> LIVING_INCOMING_DAMAGE = Event.create(ls -> (e, src, amount) -> {
        float a = amount;
        for (LivingDamage l : ls) a = l.onDamage(e, src, a);
        return a;
    });
    /** Returning {@code true} cancels the death. */
    public static final Event<LivingDeath> LIVING_DEATH = Event.create(ls -> (e, src) -> {
        for (LivingDeath l : ls) if (l.onDeath(e, src)) return true;
        return false;
    });
    /**
     * An entity finished using an item: ate, drank, or another use that runs to its end
     * ({@code LivingEntity.completeUsingItem}, after the item's {@code finishUsingItem}). {@code used} is a copy of the
     * stack as it was before the use, {@code result} what replaces it in the hand. Both logical sides; notification
     * only. Fired from NeoForge's {@code LivingEntityUseItemEvent.Finish}.
     */
    public static final Event<ItemUseFinish> ITEM_USE_FINISH = Event.create(ls -> (e, used, result) -> ls.forEach(l -> l.onFinish(e, used, result)));

    /**
     * A player right-clicks an entity, before the entity's own interaction ({@code Entity.interact}, e.g. a villager
     * opening its trades) and before the held item's {@code interactLivingEntity}. Fires on <b>both logical sides</b>,
     * once per hand (main hand first); the client fires it for its own player after sending the interaction to the
     * server, the server when it handles that packet. Not fired for spectators.
     *
     * <p>Return {@link InteractionResult#PASS} to let the vanilla interaction run. Any other result cancels it: no
     * later listener, no entity interaction, no item interaction; the result goes back to vanilla
     * ({@code consumesAction()} results swing the arm and stop trying the other hand). The first non-PASS listener
     * wins. Server-authoritative logic should return PASS on the client unless it can predict the server's answer.
     */
    public static final Event<EntityInteract> ENTITY_INTERACT = Event.create(ls -> (p, t, h) -> {
        for (EntityInteract l : ls) {
            InteractionResult r = l.onInteract(p, t, h);
            if (r != InteractionResult.PASS) return r;
        }
        return InteractionResult.PASS;
    });

    // --- Blocks -----------------------------------------------------------------------------------------------

    /** A player is about to break a block (server). Returning {@code true} cancels it. */
    public static final Event<BlockBreak> BLOCK_BREAK = Event.create(ls -> (lv, pos, st, p) -> {
        for (BlockBreak l : ls) if (l.onBreak(lv, pos, st, p)) return true;
        return false;
    });
    /** An entity placed a block (server). Returning {@code true} cancels it. */
    public static final Event<BlockPlace> BLOCK_PLACE = Event.create(ls -> (lv, pos, st, e) -> {
        for (BlockPlace l : ls) if (l.onPlace(lv, pos, st, e)) return true;
        return false;
    });

    // --- Registration-style server events ---------------------------------------------------------------------

    public static final Event<RegisterCommands> REGISTER_COMMANDS = Event.create(ls -> (d, c, s) -> ls.forEach(l -> l.register(d, c, s)));
    /** Add server data reload listeners (datapack JSON loaders). Fires on every {@code /reload}. */
    public static final Event<AddReloadListeners> ADD_RELOAD_LISTENERS = Event.create(ls -> (sink, ra) -> ls.forEach(l -> l.add(sink, ra)));

    /**
     * Server data must be (re)sent to a player: once when the player joins (after tags and recipes), and for every
     * online player after a datapack reload ({@code joined == false}). Fires once per player.
     */
    public static final Event<DatapackSync> DATAPACK_SYNC = Event.create(ls -> (p, j) -> ls.forEach(l -> l.onSync(p, j)));

    // --- Listener types ---------------------------------------------------------------------------------------

    @FunctionalInterface public interface ServerLifecycle { void on(MinecraftServer server); }
    @FunctionalInterface public interface LevelTick { void onTick(ServerLevel level); }
    @FunctionalInterface public interface PlayerTick { void onTick(Player player); }
    @FunctionalInterface public interface PlayerEvent { void on(ServerPlayer player); }
    @FunctionalInterface public interface ContainerMenuEvent { void on(Player player, AbstractContainerMenu menu); }
    @FunctionalInterface public interface PlayerClone { void onClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wasDeath); }
    @FunctionalInterface public interface EntityJoinLevel { boolean onJoin(Entity entity, Level level); }
    /** Returns the new damage amount; a result {@code <= 0} cancels the damage. */
    @FunctionalInterface public interface LivingDamage { float onDamage(LivingEntity entity, DamageSource source, float amount); }
    @FunctionalInterface public interface EntityInteract { InteractionResult onInteract(Player player, Entity target, InteractionHand hand); }
    @FunctionalInterface public interface LivingDeath { boolean onDeath(LivingEntity entity, DamageSource source); }
    @FunctionalInterface public interface ItemUseFinish { void onFinish(LivingEntity entity, ItemStack used, ItemStack result); }
    @FunctionalInterface public interface BlockBreak { boolean onBreak(Level level, BlockPos pos, BlockState state, Player player); }
    @FunctionalInterface public interface BlockPlace { boolean onPlace(Level level, BlockPos pos, BlockState placed, @Nullable Entity placer); }
    @FunctionalInterface public interface RegisterCommands { void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection); }
    @FunctionalInterface public interface DatapackSync { void onSync(ServerPlayer player, boolean joined); }
    @FunctionalInterface public interface AddReloadListeners { void add(Consumer<PreparableReloadListener> sink, RegistryAccess registryAccess); }
}
