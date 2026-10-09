package com.richardsenger.piratesnships.combat.boarding.order;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who fights whom in a boarding (BRD3, docs/design.md §8.4, §8.5). Two sides only, everyone else keeps the peacetime
 * rules ({@code mob.HostilityRules}):
 * <ul>
 *   <li>a <b>boarder</b> standing on the boarded ship fights the <em>combatants</em> standing on it who are not of its
 *       party;</li>
 *   <li>a <b>defender</b> (any combatant standing on a boarded ship, not a boarder itself) fights the boarders
 *       standing on its deck, never a boarder still on its own ship.</li>
 * </ul>
 * A combatant is a fighter (a pirate, a navy soldier or officer, a captain; not a sailor), a crew member that is not at a
 * station, asleep or a prisoner, a hostile monster, and, against boarders sent by an NPC captain only, a player in
 * survival or adventure. A player's boarders never attack players. A boarder on its way across or back
 * ({@link #holdsFire}) fights nobody.
 */
public final class BoardingFight {

    private BoardingFight() {
    }

    /** Whether {@code self} and {@code other} are boarding foes: {@code self} attacks {@code other} on sight. */
    public static boolean foes(LivingEntity self, LivingEntity other) {
        if (!BoardingParties.any() || self == other) return false;
        BoardingParties.Party mine = BoardingParties.partyOf(self);
        BoardingParties.Party theirs = BoardingParties.partyOf(other);
        if (mine != null && theirs != null && mine.home().equals(theirs.home())) return false; // one party
        if (mine != null && aboard(other, mine.target()) && combatant(other, mine.npc())) {
            return theirs == null || !theirs.target().equals(mine.target()); // two parties boarding one ship are allies
        }
        return mine == null && theirs != null && aboard(self, theirs.target()) && aboard(other, theirs.target())
                && combatant(self, theirs.npc());
    }

    /** Whether crew member {@code crew} keeps fighting {@code target}: a foe, and it is not crossing. */
    public static boolean keepsTarget(CrewMember crew, LivingEntity target) {
        return !holdsFire(crew) && foes(crew, target);
    }

    /** Whether {@code e} is a boarder on its way across or back: it does not stop to fight. */
    public static boolean holdsFire(Entity e) {
        BoardingParties.Boarder b = BoardingParties.boarder(e);
        return b != null && b.inTransit();
    }

    /** Whether {@code e} takes part in a fight on a deck (see the class comment). */
    public static boolean combatant(LivingEntity e, boolean againstNpcBoarders) {
        if (!e.isAlive() || BrigService.isPrisoner(e)) return false;
        if (e instanceof Player p) return againstNpcBoarders && !p.isCreative() && !p.isSpectator();
        if (e instanceof SeafarerMob m) return m.faction() != MobFaction.CIVILIAN;
        if (e instanceof CrewMember c) return !c.isPassenger() && !c.isResting();
        return e instanceof Enemy;
    }

    /** Whether {@code e} stands on (or rides something of) ship {@code ship}. */
    public static boolean aboard(Entity e, UUID ship) {
        ShipBody on = ShipEntities.standingOrRiding(e);
        return on != null && on.id().equals(ship);
    }

    /** The ship {@code e} stands on, or null. */
    static @Nullable UUID shipOf(Entity e) {
        ShipBody on = ShipEntities.standingOrRiding(e);
        return on == null ? null : on.id();
    }
}
