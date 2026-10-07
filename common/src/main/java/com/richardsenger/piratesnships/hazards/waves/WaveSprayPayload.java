package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → clients near a ship: its bow dug into a wave (WV1). The client throws spray and plays a splash if its
 * {@code wave_effects.spray} is on. The server decides when, because only it knows the bow and the sea level at the
 * hull; it rate-limits per ship.
 *
 * @param x        world x of the bow point
 * @param y        world y of the water surface at the bow
 * @param z        world z of the bow point
 * @param dirX     world x of the bow direction (horizontal unit vector)
 * @param dirZ     world z of the bow direction
 * @param strength how far the crest rose above the bow point [blocks], for the amount of spray
 */
public record WaveSprayPayload(double x, double y, double z, float dirX, float dirZ, float strength) implements CustomPacketPayload {

    public static final Type<WaveSprayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "wave_spray"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WaveSprayPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, WaveSprayPayload::x,
            ByteBufCodecs.DOUBLE, WaveSprayPayload::y,
            ByteBufCodecs.DOUBLE, WaveSprayPayload::z,
            ByteBufCodecs.FLOAT, WaveSprayPayload::dirX,
            ByteBufCodecs.FLOAT, WaveSprayPayload::dirZ,
            ByteBufCodecs.FLOAT, WaveSprayPayload::strength,
            WaveSprayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
