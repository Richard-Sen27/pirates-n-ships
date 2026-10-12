package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.world.WorldConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where every shown port name comes from (NAME3): {@link #label} is the port's proper name, or its readable id
 * ({@link PortNames#fromId}) while {@code world.ports.names} is off or the port is unknown.
 *
 * <p>On the server thread it reads the {@link PortRegistry} directly (installed from server start to stop). Anywhere
 * else (the client's screens, quest and market texts built on the client) it reads the names the server sent with
 * {@link Sync}: all of them when a player joins and again whenever a port registers. A single-player client reads its
 * synced copy too, never the integrated server's registry from the render thread.
 */
public final class PortNameBook {

    private static volatile @Nullable MinecraftServer server;
    private static volatile Map<ResourceLocation, String> client = Map.of();

    private PortNameBook() {
    }

    /** The port's shown name: its proper name, or the readable id. */
    public static String label(ResourceLocation port) {
        return name(port).orElseGet(() -> PortNames.fromId(port));
    }

    /** The port's proper name, empty while names are off or the port is unknown here. */
    public static Optional<String> name(ResourceLocation port) {
        MinecraftServer s = server;
        if (s != null && s.isSameThread()) {
            if (!WorldConfig.PORT_NAMES.get()) return Optional.empty();
            return PortRegistry.get(s).index().byId(port).map(Port::name).filter(n -> !n.isEmpty());
        }
        return Optional.ofNullable(client.get(port));
    }

    /** The name and the id, for operators: "Port Marlow (pirates_n_ships:village_1192_1530)". */
    public static String labelWithId(ResourceLocation port) {
        return name(port).map(n -> n + " (" + port + ")").orElseGet(port::toString);
    }

    // ------------------------------------------------------------------ server side

    /** Server start: the server thread reads names from the registry. */
    public static void installServer(MinecraftServer s) {
        server = s;
    }

    /** Server stop. */
    public static void removeServer() {
        server = null;
    }

    /** Every named port of the registry, as sent to clients (empty while names are off). */
    public static Map<ResourceLocation, String> snapshot(MinecraftServer s) {
        Map<ResourceLocation, String> out = new LinkedHashMap<>();
        if (!WorldConfig.PORT_NAMES.get()) return out;
        for (Port p : PortRegistry.get(s).index().all()) if (p.named()) out.put(p.id(), p.name());
        return out;
    }

    /** Sends every port name to {@code player} (on join). */
    public static void sendTo(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new Sync(snapshot(player.server)));
    }

    /** Sends every port name to every player (a port registered). */
    public static void sendToAll(MinecraftServer s) {
        if (s.getPlayerList().getPlayerCount() == 0) return;
        Services.NETWORK.sendToAll(new Sync(snapshot(s)));
    }

    // ------------------------------------------------------------------ client side

    /** The names the server sent (client thread). */
    public static void accept(Sync sync) {
        client = Map.copyOf(sync.names());
    }

    /** Leaving a world. */
    public static void clearClient() {
        client = Map.of();
    }

    /** Every port name of the server, id to name; an empty map while {@code world.ports.names} is off. */
    public record Sync(Map<ResourceLocation, String> names) implements CustomPacketPayload {

        public static final Type<Sync> TYPE = new Type<>(Constants.id("port_names"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.<ByteBuf, ResourceLocation, String, Map<ResourceLocation, String>>map(HashMap::new,
                        ResourceLocation.STREAM_CODEC, ByteBufCodecs.STRING_UTF8), Sync::names,
                Sync::new);

        public Sync {
            names = Map.copyOf(names);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
