package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.combat.cannon.ShotKind;

/**
 * Which shot a gun crew firing by itself wants (CAN3, docs/design.md §8.2): pure, unit tested. The crew then takes the
 * first of that shot, the ball and chain shot its locker holds ({@code ShotRules.gunneryPreference}).
 *
 * <ol>
 *   <li><b>Grapeshot</b> when at least {@code minFighters} fighters stand on the target's deck and the target is within
 *       {@code grapeRange}: clear the deck before boarding or being boarded.</li>
 *   <li><b>Chain shot</b> when the gunnery chases one chosen quarry ({@link GunneryState.Mode#TARGET}: a navy patrol's
 *       hunt, WS4b, or a pirate captain's chase, BOS2), {@code prefersChain} is on and the quarry still makes way
 *       ({@code targetSpeed} at least {@code chainMinSpeed}): shoot its sails away so it cannot run.</li>
 *   <li>Else the <b>ball</b>: firing at will at any hostile ship sinks it.</li>
 * </ol>
 * A shot whose server toggle is off is never wanted.
 */
public final class ShotChoice {

    /** What the crew sees of its target this time. */
    public record Situation(GunneryState.Mode mode, double targetSpeed, int fighters, double distance) {
    }

    /** The config of the rule ({@code cannons.npc.*}) and the shot toggles ({@code cannons.chain_shot/grapeshot.enabled}). */
    public record Rules(boolean prefersChain, double chainMinSpeed, int minFighters, double grapeRange,
                        boolean chainEnabled, boolean grapeEnabled) {
    }

    private ShotChoice() {
    }

    public static ShotKind wanted(Situation s, Rules r) {
        if (r.grapeEnabled() && s.fighters() >= r.minFighters() && s.distance() <= r.grapeRange()) {
            return ShotKind.GRAPE;
        }
        if (r.chainEnabled() && r.prefersChain() && s.mode() == GunneryState.Mode.TARGET && s.targetSpeed() >= r.chainMinSpeed()) {
            return ShotKind.CHAIN;
        }
        return ShotKind.BALL;
    }
}
