package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The capstan as a station (CRW3, docs/design.md §5.2, §6, §7.5). The block is {@code sailing.block.CapstanBlock}.
 *
 * <ul>
 *   <li>{@link AnchorOrder#DROP_ANCHOR}: {@code crew_stations.capstan.drop_ticks} of work while the anchor is stowed or
 *       coming up, then the anchor is let go ({@link ShipControls#dropAnchor}); nothing to do when it is out already;
 *       unable without ground within the chain straight below the hawse, or with the anchor switched off.</li>
 *   <li>{@link AnchorOrder#RAISE_ANCHOR}: the winding starts at once ({@link #begin}, {@link ShipControls#raiseAnchor})
 *       and the order lasts as long as the capstan needs for the chain out (AN2a's {@code anchor_chain.raise_speed}), at
 *       least {@code min_raise_ticks}; when it ends with the anchor still coming up, the crew member takes the next
 *       order by himself (the pump's pattern). Unable when the anchor is stowed. The winding is autonomous, as for a
 *       player: a crew member leaving mid-raise does not stop it.</li>
 * </ul>
 * {@link #durationTicks} only reads the anchor (the job board asks it through {@link Stations#workTicks}).
 */
public final class CapstanStation implements StationKind<AnchorOrder> {

    public static final CapstanStation INSTANCE = new CapstanStation();

    private CapstanStation() {
    }

    @Override
    public String id() {
        return "capstan";
    }

    @Override
    public Class<AnchorOrder> orderType() {
        return AnchorOrder.class;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, AnchorOrder order) {
        if (!CapstanConfig.ENABLED.get()) {
            return -1;
        }
        return switch (order) {
            case DROP_ANCHOR -> dropTicks(ShipControls.dropCheck(level, station.pos()).result(), CapstanConfig.DROP_TICKS.get());
            case RAISE_ANCHOR -> raiseTicks(ShipControls.anchorPhase(level, station.pos()),
                    ShipControls.raiseTicks(ShipControls.chainOut(level, station.pos())), CapstanConfig.MIN_RAISE_TICKS.get());
        };
    }

    /** The raise starts with the order: the crew's push is the winding. */
    @Override
    public void begin(ServerLevel level, StationRef station, AnchorOrder order) {
        if (order == AnchorOrder.RAISE_ANCHOR) {
            ShipControls.raiseAnchor(level, station.pos());
        }
    }

    /** Drop: the anchor goes. Raise: still coming up (a long chain, or let go again by hand meanwhile and re-raised): carry on. */
    @Override
    public void complete(ServerLevel level, StationRef station, AnchorOrder order) {
        switch (order) {
            case DROP_ANCHOR -> ShipControls.dropAnchor(level, station.pos());
            case RAISE_ANCHOR -> {
                if (ShipControls.anchorPhase(level, station.pos()) == AnchorState.Phase.RAISING) {
                    Stations.order(level, station, order);
                }
            }
        }
    }

    /**
     * Work time of a drop for {@link ShipControls#dropCheck}'s answer: {@code dropTicks} when the anchor can go, 0 when
     * it is out already, -1 when it cannot (no ground, anchor off, no ship). Pure.
     */
    static int dropTicks(ShipControls.AnchorResult check, int dropTicks) {
        return switch (check) {
            case DROPPING -> Math.max(1, dropTicks);
            case ALREADY_OUT -> 0;
            default -> -1;
        };
    }

    /**
     * Work time of a raise: the capstan's winding time {@code windTicks} rounded up, at least {@code minTicks}; -1 when
     * the anchor is stowed or there is no ship ({@code phase} RAISED or null). Pure.
     */
    static int raiseTicks(AnchorState.@Nullable Phase phase, double windTicks, int minTicks) {
        if (phase == null || phase == AnchorState.Phase.RAISED) {
            return -1;
        }
        double t = Double.isFinite(windTicks) ? Math.ceil(windTicks - 1e-9) : 0.0;
        return (int) Math.max(Math.max(1, minTicks), Math.min(Integer.MAX_VALUE, t));
    }
}
