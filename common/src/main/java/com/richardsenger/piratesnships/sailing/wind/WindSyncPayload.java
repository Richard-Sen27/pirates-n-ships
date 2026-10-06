package com.richardsenger.piratesnships.sailing.wind;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the wind at the player's position (docs/design.md §5.1, for the HUD indicator and flag/sail
 * visuals).
 *
 * <p>Why the sample and not the inputs: the client can't recompute the wind itself because it doesn't know the world
 * seed (vanilla never sends it), and gust and regional tuning live in the server config. A sample is 17 bytes, sent
 * once per {@code sync_interval_ticks}, so it costs nothing.
 *
 * @param towardDegrees     compass bearing the wind blows toward
 * @param strength          wind speed [blocks/s]
 * @param weatherMultiplier weather factor included in {@code strength}
 * @param gust              gust intensity 0..1
 * @param intervalTicks     ticks until the next sync; the client blends toward this sample over that time
 */
public record WindSyncPayload(float towardDegrees, float strength, float weatherMultiplier, float gust,
                              int intervalTicks) implements CustomPacketPayload {

    public static final Type<WindSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "wind_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WindSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, WindSyncPayload::towardDegrees,
            ByteBufCodecs.FLOAT, WindSyncPayload::strength,
            ByteBufCodecs.FLOAT, WindSyncPayload::weatherMultiplier,
            ByteBufCodecs.FLOAT, WindSyncPayload::gust,
            ByteBufCodecs.VAR_INT, WindSyncPayload::intervalTicks,
            WindSyncPayload::new);

    public static WindSyncPayload of(WindSample s, int intervalTicks) {
        return new WindSyncPayload((float) s.towardDegrees(), (float) s.strength(), (float) s.weatherMultiplier(),
                (float) s.gust(), intervalTicks);
    }

    public WindSample toSample() {
        return WindSample.of(towardDegrees, strength, weatherMultiplier, gust);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
