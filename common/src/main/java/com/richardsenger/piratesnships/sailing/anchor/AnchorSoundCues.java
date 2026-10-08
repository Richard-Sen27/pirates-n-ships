package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.sailing.force.AnchorState;

/**
 * Which chain sounds one ship's anchor makes this game tick (docs/design.md §5.2, AN2b). Pure state machine, one per
 * ship, fed by {@link AnchorEntities} on the server; all chain sounds are server-side so every player nearby hears the
 * same thing, the taut jolt included (the server sees the exact edge of the chain's stretch, a client only the
 * synced flag a tick late).
 *
 * <ul>
 *   <li>{@link #RUNNING}: the chain runs out of the hawse, only while the paid-out length grows (the fall), or is wound
 *       in (raising); every {@link #CHAIN_INTERVAL} ticks.</li>
 *   <li>{@link #SCRAPE}: the anchor drags over the seabed (played as the chain at a low pitch), every
 *       {@link #CHAIN_INTERVAL} ticks, when the chain is not running.</li>
 *   <li>{@link #CAPSTAN}: the capstan's pawl clanks while it winds the chain in, every {@link #CAPSTAN_INTERVAL} ticks.</li>
 *   <li>{@link #JOLT}: the chain snaps taut: falling, when the anchor reaches the chain's end; resting, when the ship
 *       stretches the chain by {@link #JOLT_STRETCH}. Once per landing; it re-arms when the chain goes clearly slack
 *       again ({@link #REARM_SLACK}) or the anchor is dropped again.</li>
 * </ul>
 */
public final class AnchorSoundCues {

    public static final int RUNNING = 1, SCRAPE = 2, CAPSTAN = 4, JOLT = 8;

    /** Ticks between two chain sounds. */
    static final int CHAIN_INTERVAL = 4;
    /** Ticks between two capstan clanks. */
    static final int CAPSTAN_INTERVAL = 10;
    /** Growth of the paid-out length per tick that counts as running out [blocks]. */
    static final double PAY_OUT_EPSILON = 0.01;
    /** Stretch of a resting anchor's chain beyond its paid-out length that jolts [blocks]. */
    static final double JOLT_STRETCH = 0.1;
    /** Slack after which a resting chain can jolt again [blocks]. */
    static final double REARM_SLACK = 1.0;

    private double lastPaidOut = Double.NaN;
    private AnchorState.Phase lastPhase = AnchorState.Phase.RAISED;
    private boolean wasResting;
    private boolean wasTaut;
    private boolean armed;
    private int chainCooldown;
    private int capstanCooldown;

    /** The stowed anchor: forget the chain. */
    public void stowed() {
        lastPaidOut = Double.NaN;
        lastPhase = AnchorState.Phase.RAISED;
        wasResting = false;
        wasTaut = false;
        armed = false;
        chainCooldown = 0;
        capstanCooldown = 0;
    }

    /** An anchor found out without a history (a fresh entity after a load): take its state as it is, no cues. */
    public void prime(AnchorState.Phase phase, double paidOut, boolean resting, boolean taut) {
        stowed();
        lastPaidOut = paidOut;
        lastPhase = phase;
        wasResting = resting;
        wasTaut = taut;
    }

    /**
     * One game tick of an anchor that is out.
     *
     * @param phase    the anchor's phase after this tick
     * @param paidOut  chain paid out [blocks]
     * @param distance hawse to ring [blocks]
     * @param resting  the anchor rests on the ground
     * @param taut     the chain is taut ({@link AnchorStatus#taut()})
     * @param dragging the anchor drags over the seabed
     * @return the sounds to play, a set of the flag bits
     */
    public int tick(AnchorState.Phase phase, double paidOut, double distance, boolean resting, boolean taut, boolean dragging) {
        int cues = 0;
        boolean dropped = phase == AnchorState.Phase.DROPPING && lastPhase != AnchorState.Phase.DROPPING
                && lastPhase != AnchorState.Phase.HOLDING;
        if (dropped || (resting && !wasResting)) {
            armed = true; // a new drop or a landing
        }
        boolean payingOut = !Double.isNaN(lastPaidOut) && paidOut > lastPaidOut + PAY_OUT_EPSILON;
        boolean raising = phase == AnchorState.Phase.RAISING;
        chainCooldown--;
        if ((payingOut || raising || dragging) && chainCooldown <= 0) {
            chainCooldown = CHAIN_INTERVAL;
            cues |= payingOut || raising ? RUNNING : SCRAPE;
        }
        capstanCooldown--;
        if (raising && capstanCooldown <= 0) {
            capstanCooldown = CAPSTAN_INTERVAL;
            cues |= CAPSTAN;
        }
        if (resting && distance < paidOut - REARM_SLACK) {
            armed = true;
        }
        boolean jolt = phase == AnchorState.Phase.DROPPING ? taut && !wasTaut
                : phase == AnchorState.Phase.HOLDING && resting && distance - paidOut >= JOLT_STRETCH;
        if (armed && jolt) {
            armed = false;
            cues |= JOLT;
        }
        lastPaidOut = paidOut;
        lastPhase = phase;
        wasResting = resting;
        wasTaut = taut;
        return cues;
    }
}
