package com.richardsenger.piratesnships.core.data;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A kind of data-driven definition loaded from datapacks (weapons, trade goods, ...). Declare one as a
 * {@code static final} in your module and touch the class from {@code registerContent()}:
 *
 * <pre>{@code
 * public static final DefinitionType<WeaponDefinition> WEAPONS = DefinitionType.createSynced("weapon", WeaponDefinition.CODEC);
 * // server logic:   WEAPONS.server().require(id)       client rendering:  WEAPONS.client().get(id)
 * // either side:    WEAPONS.of(level).get(id)          after a reload:    WEAPONS.onServerReload(defs -> ...)
 * // datagen:        data.definitions(WEAPONS, Map.of(Constants.id("cutlass"), CUTLASS));
 * }</pre>
 *
 * <p><b>Files</b> live at {@code data/<namespace>/pirates_n_ships/<name>/<path>.json} and get the id
 * {@code <namespace>:<path>}. Any datapack or mod can add entries in its own namespace, or replace one of ours by
 * shipping the same path in {@code data/pirates_n_ships/pirates_n_ships/<name>/}. An invalid file is logged and
 * skipped; the rest of the reload goes on.
 *
 * <p><b>Sides.</b> The server store is filled by the datapack reload. Types with a stream codec are also sent to
 * clients (on login and after every {@code /reload}) and land in a separate client store, so in single-player the
 * client copy never overwrites the server copy. Types without a stream codec have an empty client store.
 *
 * <p>Pure logic should take {@link Definitions} or single entries as parameters, never read these stores itself.
 */
public final class DefinitionType<T> {

    private static final Map<String, DefinitionType<?>> BY_NAME = new LinkedHashMap<>();

    private final String name;
    private final Codec<T> codec;
    private final @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec;
    private volatile Definitions<T> server;
    private volatile Definitions<T> client;
    private final List<Consumer<Definitions<T>>> serverListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Definitions<T>>> clientListeners = new CopyOnWriteArrayList<>();

    private DefinitionType(String name, Codec<T> codec, @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        this.name = name;
        this.codec = codec;
        this.streamCodec = streamCodec;
        this.server = Definitions.empty(name);
        this.client = Definitions.empty(name);
    }

    /** A server-only type (not synced to clients). */
    public static <T> DefinitionType<T> create(String name, Codec<T> codec) {
        return register(new DefinitionType<>(name, codec, null));
    }

    /** A type synced to clients with an explicit stream codec. */
    public static <T> DefinitionType<T> create(String name, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return register(new DefinitionType<>(name, codec, streamCodec));
    }

    /** A type synced to clients through its codec (NBT on the wire). Fine for small definitions. */
    public static <T> DefinitionType<T> createSynced(String name, Codec<T> codec) {
        return create(name, codec, ByteBufCodecs.fromCodecWithRegistries(codec));
    }

    private static synchronized <T> DefinitionType<T> register(DefinitionType<T> type) {
        if (!ResourceLocation.isValidPath(type.name) || type.name.isEmpty()) {
            throw new IllegalArgumentException("Invalid definition type name '" + type.name + "' (use [a-z0-9_./-])");
        }
        if (BY_NAME.putIfAbsent(type.name, type) != null) {
            throw new IllegalStateException("Duplicate definition type " + type.name);
        }
        return type;
    }

    /** Every declared type, in declaration order. */
    public static synchronized Collection<DefinitionType<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_NAME.values()));
    }

    public static synchronized Optional<DefinitionType<?>> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    public String name() {
        return name;
    }

    public Codec<T> codec() {
        return codec;
    }

    public Optional<StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec() {
        return Optional.ofNullable(streamCodec);
    }

    public boolean isSynced() {
        return streamCodec != null;
    }

    /** Folder inside {@code data/<namespace>/}: {@code pirates_n_ships/<name>}. */
    public String directory() {
        return Constants.MOD_ID + "/" + name;
    }

    /** Entries loaded by the (logical) server from datapacks. Empty before the first reload. */
    public Definitions<T> server() {
        return server;
    }

    /** Entries received by the (logical) client. Empty for unsynced types and before the first sync. */
    public Definitions<T> client() {
        return client;
    }

    /** {@link #client()} for a client level, else {@link #server()}. */
    public Definitions<T> of(Level level) {
        return of(level.isClientSide());
    }

    public Definitions<T> of(boolean clientSide) {
        return clientSide ? client : server;
    }

    /** Called on the server thread after every datapack (re)load, with the new entries. */
    public void onServerReload(Consumer<Definitions<T>> listener) {
        serverListeners.add(listener);
    }

    /** Called on the client thread after every sync from the server, with the new entries. */
    public void onClientSync(Consumer<Definitions<T>> listener) {
        clientListeners.add(listener);
    }

    /** Replaces the server store (reload listener, tests). */
    public void acceptServer(Map<ResourceLocation, ? extends T> entries) {
        Definitions<T> defs = Definitions.of(name, entries);
        server = defs;
        notify(serverListeners, defs);
    }

    /** Replaces the client store (sync payload). */
    public void acceptClient(Map<ResourceLocation, ? extends T> entries) {
        Definitions<T> defs = Definitions.of(name, entries);
        client = defs;
        notify(clientListeners, defs);
    }

    private void notify(List<Consumer<Definitions<T>>> listeners, Definitions<T> defs) {
        for (Consumer<Definitions<T>> l : listeners) {
            try {
                l.accept(defs);
            } catch (RuntimeException e) {
                Constants.LOG.error("{} definition listener failed", name, e);
            }
        }
    }

    /** Clears the server store (server stopped). Listeners are not called. */
    void clearServer() {
        server = Definitions.empty(name);
    }

    @Override
    public String toString() {
        return "DefinitionType[" + name + "]";
    }
}
