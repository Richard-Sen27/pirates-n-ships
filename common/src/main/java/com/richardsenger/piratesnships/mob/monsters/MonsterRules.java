package com.richardsenger.piratesnships.mob.monsters;

import com.richardsenger.piratesnships.trade.market.PortKind;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Monsters and the mod's people (MOB3), pure. The world adapters are {@link MonsterTargets} (the monsters' side),
 * {@link CrewDefence} (the crew's side) and {@code mob.harbor.HarborMaster} (the fence).
 * <ul>
 *   <li>The vanilla monsters that hunt villagers ({@code #pirates_n_ships:hunts_npcs}: the zombie family, the illagers,
 *       the ravager) hunt crew members, sailors and harbor masters too ({@code mobs.monsters_attack_npcs}). Pirates
 *       and navy are fighters, not prey: they are only hit by accident and fight back (the retaliation rule of
 *       {@code HostilityRules}).</li>
 *   <li>A person aboard a ship is hunted only by a monster aboard the same ship: a drowned in the sea or a zombie on
 *       the quay leaves the deck alone (it could not reach it, and a trident through a sleeping crew every night is no
 *       fun). A monster that climbed aboard is a danger to everyone on that deck.</li>
 *   <li>A crew member that a monster hunts or hit fights it with a weapon in hand and runs from it without one
 *       ({@code mobs.crew_fight_monsters}); one at a station keeps it. One asleep in a hammock is woken by the blow.</li>
 *   <li>The harbor master of a pirate island is the pirates' fence: he does not fear pirates
 *       ({@code mobs.harbor_master.fears_pirates_at_islands} off); everywhere else he runs from them as sailors do.</li>
 * </ul>
 */
public final class MonsterRules {

    private MonsterRules() {
    }

    /** What a crew member does about a monster that hunts it. */
    public enum Response { FIGHT, FLEE, STAND }

    /**
     * Whether a hunting monster may pick a person as its target.
     *
     * @param enabled      {@code mobs.monsters_attack_npcs}
     * @param person       the candidate is a crew member, sailor or harbor master
     * @param targetShip   the ship the person stands on or rides, {@code null} on land or in the water
     * @param monsterShip  the ship the monster stands on or rides, {@code null} on land or in the water
     */
    public static boolean hunts(boolean enabled, boolean person, @Nullable UUID targetShip, @Nullable UUID monsterShip) {
        if (!enabled || !person) return false;
        return targetShip == null || targetShip.equals(monsterShip);
    }

    /**
     * How a crew member answers a monster that hunts or hit it.
     *
     * @param enabled {@code mobs.crew_fight_monsters}
     * @param armed   a weapon in its main hand
     * @param busy    at its station, boarding, or held (a prisoner): it leaves the monster to others
     */
    public static Response respond(boolean enabled, boolean armed, boolean busy) {
        if (!enabled || busy) return Response.STAND;
        return armed ? Response.FIGHT : Response.FLEE;
    }

    /**
     * Whether a harbor master fears pirates where he stands: always outside pirate islands and when his port is
     * unknown; at a pirate island only with {@code fearsAtIslands}.
     */
    public static boolean fearsPirates(@Nullable PortKind port, boolean fearsAtIslands) {
        return port != PortKind.PIRATE_ISLAND || fearsAtIslands;
    }
}
