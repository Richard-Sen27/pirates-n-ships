package com.richardsenger.piratesnships.sailing.anchor;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Pure chain rule of a resting anchor (docs/design.md §5.2, AN2a): the force the chain puts on the ship at the hawse,
 * the taut test, the holding cap and how far the anchor drags.
 *
 * <p>With {@code d} the vector from the hawse to the anchor's ring, {@code L} the paid-out length and {@code s} the
 * stretch {@code |d| − L}: the chain is <b>taut</b> when {@code s ≥ 0}. A taut chain pulls along {@code d̂} with
 * {@code mass · (stiffness · s + damping · w)}, {@code w} being the speed at which the hawse moves away from the anchor
 * (never pushing: negative pulls are cut to 0). While the anchor rests, the chain on the seabed also brakes the ship
 * mildly ({@code − mass · settleDrag · v}, {@code v} the hawse's horizontal velocity, slack or taut). Only the
 * horizontal part is applied, so the chain never pulls the ship under, and the sum is capped at {@code mass · holding}:
 * the anchor's holding power. Units: blocks, seconds, mass in kpg (= tons of displacement), forces in kpg·blocks/s².
 *
 * <p><b>Dragging:</b> the spring's horizontal pull reaches the cap at the stretch {@code holding / (stiffness · cos φ)},
 * {@code φ} being the chain's angle below the horizontal; any stretch beyond it means the ship pulls harder than the
 * anchor holds, and the anchor scrapes toward the ship by that excess (rate limited, {@link #dragStep}), while the
 * paid-out length stays. A chain straight up and down drags nothing (it only lifts).
 */
public final class AnchorChain {

    /** Slack below which a chain still counts as taut, in blocks (the spring takes over from here). */
    public static final double TAUT_TOLERANCE = 0.05;
    /** Largest excess stretch of a dragging chain beyond the holding stretch, in blocks ({@link #dragStep}). */
    public static final double DRAG_SLACK = 1.0;

    /**
     * Chain tuning, every value per ton of displacement (= per kpg of Sable mass).
     *
     * @param stiffness  pull per block of stretch [1/s²]
     * @param damping    pull per block/s of separating speed [1/s]
     * @param holding    largest total pull, the anchor's holding power [blocks/s²]; 0 = the anchor never holds
     * @param settleDrag brake on the hawse's horizontal velocity while the anchor rests [1/s]
     */
    public record Params(double stiffness, double damping, double holding, double settleDrag) {

        /** The defaults; the server config declares its defaults from this instance. */
        public static final Params DEFAULTS = new Params(5.0, 2.0, 10.0, 0.2);

        public Params {
            stiffness = Math.max(0.0, stiffness);
            damping = Math.max(0.0, damping);
            holding = Math.max(0.0, holding);
            settleDrag = Math.max(0.0, settleDrag);
        }
    }

    /**
     * One evaluation of the chain.
     *
     * @param force    horizontal world force on the ship at the hawse [kpg·blocks/s²]
     * @param distance distance from the hawse to the ring [blocks]
     * @param stretch  {@code distance − paidOut} [blocks], negative when slack
     * @param taut     whether the chain is taut ({@link #taut})
     * @param capped   whether the holding power limited the force
     */
    public record Result(Vector3d force, double distance, double stretch, boolean taut, boolean capped) { }

    private AnchorChain() {
    }

    /** Whether a chain of {@code paidOut} blocks between two points {@code distance} apart is taut. */
    public static boolean taut(double distance, double paidOut) {
        return distance >= paidOut - TAUT_TOLERANCE;
    }

    /**
     * The chain's force on the ship while its anchor rests on the seabed.
     *
     * @param hawse         hawse, world [blocks]
     * @param ring          the anchor's ring (where the chain is shackled), world [blocks]
     * @param paidOut       chain paid out [blocks]
     * @param hawseVelocity world velocity of the hawse point [blocks/s]
     * @param mass          ship mass [kpg]
     */
    public static Result force(Vector3dc hawse, Vector3dc ring, double paidOut, Vector3dc hawseVelocity, double mass, Params p) {
        Vector3d d = new Vector3d(ring).sub(hawse);
        double distance = d.length();
        double stretch = distance - paidOut;
        boolean taut = taut(distance, paidOut);
        Vector3d f = new Vector3d();
        if (!(mass > 0.0) || p.holding() <= 0.0) {
            return new Result(f, distance, stretch, taut, false);
        }
        if (stretch > 0.0 && distance > 1.0e-9) {
            Vector3d u = d.div(distance);
            double away = -hawseVelocity.dot(u);
            double pull = Math.max(0.0, p.stiffness() * stretch + p.damping() * away);
            f.set(u).mul(pull * mass);
        }
        f.sub(hawseVelocity.x() * p.settleDrag() * mass, 0.0, hawseVelocity.z() * p.settleDrag() * mass);
        f.y = 0.0;
        double cap = p.holding() * mass;
        double len = f.length();
        boolean capped = len > cap;
        if (capped) {
            f.mul(cap / len);
        }
        return new Result(f, distance, stretch, taut, capped);
    }

    /**
     * How far the resting anchor must drag toward the ship to bring the chain back to the stretch at which the spring's
     * horizontal pull just reaches the holding power; 0 when it holds. With no holding power any taut stretch drags it.
     *
     * @param horizontal the horizontal fraction of the chain's direction, {@code cos φ} (0..1)
     */
    public static double dragExcess(double stretch, double horizontal, Params p) {
        if (stretch <= 0.0) {
            return 0.0;
        }
        if (p.holding() <= 0.0) {
            return stretch;
        }
        double pullPerBlock = p.stiffness() * horizontal;
        double atCap = pullPerBlock > 1.0e-6 ? p.holding() / pullPerBlock : Double.POSITIVE_INFINITY;
        return Math.max(0.0, stretch - atCap);
    }

    /**
     * How far a dragging anchor moves this tick: the excess ({@link #dragExcess}), at most {@code scrapeRate · dt}, so a
     * heavy ship slides the anchor rather than jumps it; but never so little that the excess stays above
     * {@link #DRAG_SLACK}: once the chain is that far past the holding stretch the anchor follows the ship at its pace,
     * and the chain never gets longer than it is paid out by more than that.
     */
    public static double dragStep(double stretch, double horizontal, Params p, double scrapeRate, double dt) {
        double excess = dragExcess(stretch, horizontal, p);
        if (excess <= 0.0) {
            return 0.0;
        }
        return Math.max(Math.min(excess, Math.max(0.0, scrapeRate) * dt), excess - DRAG_SLACK);
    }
}
