package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: the recoil of the receiving player's own shot. The client owns its player's view and movement,
 * so the view kick and the backward push are applied there.
 *
 * @param pitchDegrees how far the view kicks up
 * @param push         backward push in blocks per tick, against the look direction (horizontal only)
 */
public record RecoilPayload(float pitchDegrees, float push) implements CustomPacketPayload {

    public static final Type<RecoilPayload> TYPE = new Type<>(Constants.id("firearm_recoil"));

    public static final StreamCodec<ByteBuf, RecoilPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RecoilPayload::pitchDegrees,
            ByteBufCodecs.FLOAT, RecoilPayload::push,
            RecoilPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client handler (also used on the server for shooters that are no networked player): kicks the view, pushes back. */
    public static void apply(RecoilPayload payload, Player player) {
        if (player == null) return;
        float pitch = Mth.clamp(player.getXRot() - payload.pitchDegrees(), -90.0f, 90.0f);
        player.setXRot(pitch);
        pushBack(player, payload.push());
    }

    /** Pushes an entity against its horizontal look direction. */
    static void pushBack(net.minecraft.world.entity.Entity entity, double push) {
        if (push <= 0) return;
        Vec3 look = entity.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) return;
        Vec3 back = flat.normalize().scale(-push);
        entity.push(back.x, 0, back.z);
    }
}
