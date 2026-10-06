package com.richardsenger.piratesnships.sailing.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The one anchor of a ship, as persisted in the ship's Sable user data ({@code pirates_n_ships_sailing.anchor}):
 * state machine, the world point where it lies on the ground, and the plot position of the capstan it hangs from
 * (the hawse).
 */
public record ShipAnchor(AnchorState state, Vec3 point, BlockPos capstan) {

    public static final Codec<AnchorState> STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(AnchorState.Phase::valueOf, AnchorState.Phase::name).fieldOf("phase").forGetter(AnchorState::phase),
            Codec.DOUBLE.fieldOf("hold").forGetter(AnchorState::hold)
    ).apply(i, AnchorState::new));

    public static final Codec<ShipAnchor> CODEC = RecordCodecBuilder.create(i -> i.group(
            STATE_CODEC.fieldOf("state").forGetter(ShipAnchor::state),
            Vec3.CODEC.fieldOf("point").forGetter(ShipAnchor::point),
            BlockPos.CODEC.fieldOf("capstan").forGetter(ShipAnchor::capstan)
    ).apply(i, ShipAnchor::new));

    public ShipAnchor withState(AnchorState s) {
        return new ShipAnchor(s, point, capstan);
    }
}
