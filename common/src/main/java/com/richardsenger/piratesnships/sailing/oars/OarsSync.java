package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells each player whether the ship they stand on rows (OAR1), so the whistle's wheel and the helm's ship screen label
 * the one oars entry "Row" or "Ship oars" (the WHS1 toggle pattern). Sent on change only, checked every
 * {@link #INTERVAL} ticks; the server decides the toggle by its own state, the label is only the client's hint.
 */
public final class OarsSync {

    static final int INTERVAL = 5;

    /** Server → client: the ship the player stands on rows ({@code rowing}) or not. */
    public record Payload(boolean rowing) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Constants.id("oars_state"));
        public static final StreamCodec<ByteBuf, Payload> CODEC = ByteBufCodecs.BOOL.map(Payload::new, Payload::rowing);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    /** Per player: the entity id it had (a relog or a dimension change is a new entity) and the state it was sent. */
    private record Sent(int entityId, boolean rowing) { }

    private static final Map<UUID, Sent> SENT = new ConcurrentHashMap<>();
    /** The client's copy: the ship the player stands on rows. */
    private static volatile boolean clientRowing;

    private OarsSync() {
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToClient(Payload.TYPE, Payload.CODEC, (payload, player) -> clientRowing = payload.rowing());
    }

    /** Client: whether the ship the player stands on rows (as the server last said). */
    public static boolean clientRowing() {
        return clientRowing;
    }

    /** Client: forget on disconnect. */
    public static void clearClient() {
        clientRowing = false;
    }

    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            ShipBody on = ShipEntities.standingOrRiding(p);
            boolean rowing = on != null && Rowing.isRowing(on.id());
            Sent before = SENT.put(p.getUUID(), new Sent(p.getId(), rowing));
            if (before == null || before.entityId() != p.getId() || before.rowing() != rowing) {
                Services.NETWORK.sendToPlayer(p, new Payload(rowing));
            }
        }
    }

    public static void onServerStopped() {
        SENT.clear();
    }
}
