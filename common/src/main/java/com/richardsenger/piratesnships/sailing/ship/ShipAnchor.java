package com.richardsenger.piratesnships.sailing.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.sailing.anchor.AnchorMotion;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The one anchor of a ship, as persisted in the ship's Sable user data ({@code pirates_n_ships_sailing.anchor}), AN2a:
 * the phase, the anchor's body (its crown's world position, its velocity, the chain paid out from the hawse to its
 * ring, whether it rests on the ground), the plot position of the capstan that works it and the plot position of the
 * hawse (where the chain leaves the ship: the ring of the stowed anchor, at the hull side).
 *
 * <p>Saves from before AN2a (a timed drop to a fixed point: {@code state}, {@code point}, optional {@code hawse}) load
 * with the anchor at that point, resting when it held, and an unknown paid-out length ({@link #UNKNOWN_CHAIN}) that
 * the next tick sets to the distance it finds; the hawse falls back to the capstan's centre.
 */
public record ShipAnchor(AnchorState state, Vec3 position, Vec3 velocity, double paidOut, boolean resting,
                         BlockPos capstan, Vec3 hawse) {

    /** Paid-out length of an anchor loaded from an old save: the next tick takes the current distance. */
    public static final double UNKNOWN_CHAIN = -1.0;

    private static final Codec<AnchorState.Phase> PHASE = Codec.STRING.xmap(AnchorState.Phase::valueOf, AnchorState.Phase::name);

    private static final Codec<ShipAnchor> CURRENT = RecordCodecBuilder.create(i -> i.group(
            PHASE.fieldOf("phase").forGetter(a -> a.state().phase()),
            Vec3.CODEC.fieldOf("pos").forGetter(ShipAnchor::position),
            Vec3.CODEC.optionalFieldOf("vel", Vec3.ZERO).forGetter(ShipAnchor::velocity),
            Codec.DOUBLE.fieldOf("paid_out").forGetter(ShipAnchor::paidOut),
            Codec.BOOL.fieldOf("resting").forGetter(ShipAnchor::resting),
            BlockPos.CODEC.fieldOf("capstan").forGetter(ShipAnchor::capstan),
            Vec3.CODEC.fieldOf("hawse").forGetter(ShipAnchor::hawse)
    ).apply(i, (phase, pos, vel, paidOut, resting, capstan, hawse) ->
            new ShipAnchor(AnchorState.of(phase), pos, vel, paidOut, resting, capstan, hawse)));

    /** The format before AN2a: {@code state {phase, hold}}, {@code point}, {@code capstan}, optional {@code hawse}. */
    private record Legacy(AnchorState.Phase phase, Vec3 point, BlockPos capstan, Optional<Vec3> hawse) {

        static final Codec<Legacy> CODEC = RecordCodecBuilder.create(i -> i.group(
                PHASE.fieldOf("phase").codec().fieldOf("state").forGetter(Legacy::phase),
                Vec3.CODEC.fieldOf("point").forGetter(Legacy::point),
                BlockPos.CODEC.fieldOf("capstan").forGetter(Legacy::capstan),
                Vec3.CODEC.optionalFieldOf("hawse").forGetter(Legacy::hawse)
        ).apply(i, Legacy::new));

        ShipAnchor upgrade() {
            AnchorState.Phase p = phase == AnchorState.Phase.RAISED ? AnchorState.Phase.RAISING : phase;
            return new ShipAnchor(AnchorState.of(p), point, Vec3.ZERO, UNKNOWN_CHAIN, p == AnchorState.Phase.HOLDING,
                    capstan, hawse.orElse(Vec3.atCenterOf(capstan)));
        }
    }

    public static final Codec<ShipAnchor> CODEC = Codec.withAlternative(CURRENT,
            Legacy.CODEC.xmap(Legacy::upgrade, a -> new Legacy(a.state().phase(), a.position(), a.capstan(), Optional.of(a.hawse()))));

    /** A new drop: the anchor leaves the hawse (its ring at {@code hawseWorld}) with the hawse's velocity. */
    public static ShipAnchor dropped(BlockPos capstan, Vec3 hawsePlot, Vec3 hawseWorld, Vec3 hawseVelocity) {
        return new ShipAnchor(AnchorState.DROPPING, AnchorMotion.crown(hawseWorld), hawseVelocity, 0.0, false,
                capstan.immutable(), hawsePlot);
    }

    public ShipAnchor withState(AnchorState s) {
        return new ShipAnchor(s, position, velocity, paidOut, resting, capstan, hawse);
    }

    /** The anchor's body for {@link AnchorMotion}. */
    public AnchorMotion.Body body() {
        return new AnchorMotion.Body(position, velocity, Math.max(0.0, paidOut), resting, false);
    }

    /** This anchor with the body {@code b}. */
    public ShipAnchor withBody(AnchorMotion.Body b) {
        return new ShipAnchor(state, b.pos(), b.vel(), b.paidOut(), b.resting(), capstan, hawse);
    }

    /** The anchor's ring (where the chain is shackled), world. */
    public Vec3 ring() {
        return AnchorMotion.ring(position);
    }
}
