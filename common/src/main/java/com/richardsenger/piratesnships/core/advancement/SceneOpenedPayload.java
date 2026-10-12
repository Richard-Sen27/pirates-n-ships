package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Client → server (ADV1): a Ponder scene was opened from the Almanac's "Show me" button. Ponder runs on the client
 * only, so this is how the server learns of it ({@code scene_opened}). Sent by {@code AlmanacSceneOpener.open}.
 */
public record SceneOpenedPayload(ResourceLocation scene) implements CustomPacketPayload {

    public static final Type<SceneOpenedPayload> TYPE = new Type<>(Constants.id("advancement_scene_opened"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SceneOpenedPayload> CODEC =
            ResourceLocation.STREAM_CODEC.map(SceneOpenedPayload::new, SceneOpenedPayload::scene).cast();

    @Override
    public Type<SceneOpenedPayload> type() {
        return TYPE;
    }

    /** Server side handler. */
    public static void handle(SceneOpenedPayload payload, Player player) {
        ModAdvancements.sceneOpened(player, payload.scene());
    }
}
