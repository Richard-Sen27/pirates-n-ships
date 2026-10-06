package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.crime.CrimeType;

import java.util.Optional;

/**
 * Pure mapping "who hurt whom" to a crime (docs/design.md §13.1). The world adapter ({@link CombatCrimeDetector})
 * turns entities into {@link Party} flags; this class decides.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>No attacker, or the attacker hurt itself: no crime.</li>
 *   <li>Law enforcers (navy, iron golems, see {@link LawTags#LAW_ENFORCERS}) never commit combat crimes: the law
 *       does not prosecute itself, and golems defending a village must not become criminals.</li>
 *   <li>Monsters ({@code Enemy}, e.g. zombies, pillagers) have no standing under the law and get no record unless
 *       {@code prosecuteMonsters} is set.</li>
 *   <li>Navy victims beat protected victims (an entity in both tags counts as navy).</li>
 *   <li>Members of the same group hurting each other (navy on navy, villager on villager) is no crime.</li>
 * </ol>
 */
public final class CombatCrimes {

    private CombatCrimes() {
    }

    /** What the law knows about one side of a fight. */
    public record Party(boolean navy, boolean protectedCivilian, boolean enforcer, boolean monster) {
        public static final Party NOBODY = new Party(false, false, false, false);

        public static Party asNavy() {
            return new Party(true, false, true, false);
        }

        public static Party asCivilian() {
            return new Party(false, true, false, false);
        }

        public static Party asMonster() {
            return new Party(false, false, false, true);
        }
    }

    /**
     * @param attacker          the responsible attacker, {@code null} if there is none
     * @param sameEntity        attacker and victim are the same entity
     * @param kill              the victim died (otherwise it was hurt)
     * @param prosecuteMonsters whether monsters get criminal records
     */
    public static Optional<CrimeType> classify(Party attacker, Party victim, boolean sameEntity, boolean kill,
                                               boolean prosecuteMonsters) {
        if (attacker == null || sameEntity) return Optional.empty();
        if (attacker.enforcer() || attacker.navy()) return Optional.empty();
        if (attacker.monster() && !prosecuteMonsters) return Optional.empty();
        if (victim.navy()) {
            return Optional.of(kill ? CrimeType.KILL_NAVY : CrimeType.ATTACK_NAVY);
        }
        if (victim.protectedCivilian()) {
            if (attacker.protectedCivilian()) return Optional.empty();
            return Optional.of(kill ? CrimeType.KILL_VILLAGER : CrimeType.ATTACK_VILLAGER);
        }
        return Optional.empty();
    }
}
