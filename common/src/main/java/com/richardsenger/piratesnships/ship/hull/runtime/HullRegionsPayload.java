package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.Constants;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the complete set of dry (water-occluded) cell sets of one ship, in plot coordinates. Replaces whatever
 * the client had for that ship; an empty list clears it. Sable does not network occlusion regions
 * (docs/sable-notes.md §4.4), so this is how the client's render mask and in-water checks match the server.
 */
public record HullRegionsPayload(UUID ship, List<CellSet> regions) implements CustomPacketPayload {

    public static final Type<HullRegionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "hull_regions"));

    /** At most this many regions per ship and payload (one per compartment). */
    static final int MAX_REGIONS = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, HullRegionsPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HullRegionsPayload::ship,
            CellSet.CODEC.apply(ByteBufCodecs.list(MAX_REGIONS)), HullRegionsPayload::regions,
            HullRegionsPayload::new);

    public HullRegionsPayload {
        regions = List.copyOf(regions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
