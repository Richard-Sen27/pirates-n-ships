package com.richardsenger.piratesnships.sailing.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The one anchor of a ship, as persisted in the ship's Sable user data ({@code pirates_n_ships_sailing.anchor}):
 * state machine, the world point where it lies on the ground, the plot position of the capstan that works it, the
 * plot position of the hawse (where the chain leaves the ship: the ring of the stowed anchor, at the hull side), and
 * the drop and raise times of this trip ({@code sailing.anchor.AnchorTravel}). The anchor's position along the chain
 * is {@code state.hold()}: 0 at the hawse, 1 on the ground.
 *
 * <p>Saves from before the visible anchor have no hawse and no times: the hawse falls back to the capstan's centre
 * and the times to the old defaults (40 and 100 ticks).
 */
public record ShipAnchor(AnchorState state, Vec3 point, BlockPos capstan, Vec3 hawse, int dropTicks, int raiseTicks) {

    public static final int LEGACY_DROP_TICKS = 40;
    public static final int LEGACY_RAISE_TICKS = 100;

    public static final Codec<AnchorState> STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(AnchorState.Phase::valueOf, AnchorState.Phase::name).fieldOf("phase").forGetter(AnchorState::phase),
            Codec.DOUBLE.fieldOf("hold").forGetter(AnchorState::hold)
    ).apply(i, AnchorState::new));

    /** Encoded hawse; empty on legacy saves. */
    private record Stored(AnchorState state, Vec3 point, BlockPos capstan, java.util.Optional<Vec3> hawse, int dropTicks, int raiseTicks) { }

    public static final Codec<ShipAnchor> CODEC = RecordCodecBuilder.<Stored>create(i -> i.group(
            STATE_CODEC.fieldOf("state").forGetter(Stored::state),
            Vec3.CODEC.fieldOf("point").forGetter(Stored::point),
            BlockPos.CODEC.fieldOf("capstan").forGetter(Stored::capstan),
            Vec3.CODEC.optionalFieldOf("hawse").forGetter(Stored::hawse),
            Codec.intRange(1, 100_000).optionalFieldOf("drop_ticks", LEGACY_DROP_TICKS).forGetter(Stored::dropTicks),
            Codec.intRange(1, 100_000).optionalFieldOf("raise_ticks", LEGACY_RAISE_TICKS).forGetter(Stored::raiseTicks)
    ).apply(i, Stored::new)).xmap(
            s -> new ShipAnchor(s.state(), s.point(), s.capstan(), s.hawse().orElse(Vec3.atCenterOf(s.capstan())), s.dropTicks(), s.raiseTicks()),
            a -> new Stored(a.state(), a.point(), a.capstan(), java.util.Optional.of(a.hawse()), a.dropTicks(), a.raiseTicks()));

    public ShipAnchor {
        dropTicks = Math.max(1, dropTicks);
        raiseTicks = Math.max(1, raiseTicks);
    }

    /** An anchor as before the visible anchor: hawse at the capstan's centre, the old default times. */
    public ShipAnchor(AnchorState state, Vec3 point, BlockPos capstan) {
        this(state, point, capstan, Vec3.atCenterOf(capstan), LEGACY_DROP_TICKS, LEGACY_RAISE_TICKS);
    }

    public ShipAnchor withState(AnchorState s) {
        return new ShipAnchor(s, point, capstan, hawse, dropTicks, raiseTicks);
    }

    /** {@code p} with this trip's drop and raise times, so {@link AnchorState#tick} moves in step with the chain. */
    public SailingParams.AnchorParams travelParams(SailingParams.AnchorParams p) {
        return new SailingParams.AnchorParams(p.stiffness(), p.damping(), p.maxAcceleration(), p.slack(), dropTicks, raiseTicks);
    }
}
