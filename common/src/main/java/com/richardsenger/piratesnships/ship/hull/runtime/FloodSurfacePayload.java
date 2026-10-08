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
 * Server → client: the water surfaces of one ship's partly flooded compartments (FLD1, docs/design.md §4.4). Replaces
 * whatever the client had for that ship; an empty list clears it. The client draws one translucent water surface per
 * entry ({@code ship.hull.client.FloodSurfaceRenderer}).
 *
 * <p>Each surface carries the compartment's whole cell set (the surface is clipped to it, so it never shows outside the
 * hull) and the flood level as a height along the ship's analysis up vector ({@code upX/Y/Z}, plot frame), measured from
 * the cell set's min corner: the plane is {@code up · (p − min) = level}. Relative heights keep the float precise although
 * plot coordinates are in the millions. {@code intervalTicks} is the least time between two payloads while the water
 * moves; the client eases from the old level to the new one over that time.
 */
public record FloodSurfacePayload(UUID ship, float upX, float upY, float upZ, int intervalTicks, List<Surface> surfaces)
        implements CustomPacketPayload {

    public static final Type<FloodSurfacePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "flood_surfaces"));

    /** At most this many surfaces per ship and payload (one per compartment). */
    static final int MAX_SURFACES = 256;

    /** One flooded compartment: all its cells and its water level (blocks above the min corner along the up vector). */
    public record Surface(CellSet cells, float level) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Surface> CODEC = StreamCodec.composite(
                CellSet.CODEC.cast(), Surface::cells,
                ByteBufCodecs.FLOAT, Surface::level,
                Surface::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FloodSurfacePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, FloodSurfacePayload::ship,
            ByteBufCodecs.FLOAT, FloodSurfacePayload::upX,
            ByteBufCodecs.FLOAT, FloodSurfacePayload::upY,
            ByteBufCodecs.FLOAT, FloodSurfacePayload::upZ,
            ByteBufCodecs.VAR_INT, FloodSurfacePayload::intervalTicks,
            Surface.CODEC.apply(ByteBufCodecs.list(MAX_SURFACES)), FloodSurfacePayload::surfaces,
            FloodSurfacePayload::new);

    public FloodSurfacePayload {
        surfaces = List.copyOf(surfaces);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
