package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The squad fights as one (MOB2, docs/design.md §9). Every tick of the officer:
 * <ul>
 *     <li><b>Alert:</b> a squad mob that was hit (vanilla's last attacker, {@code getLastHurtByMob}) alerts the squad:
 *     everyone takes the grudge against the attacker ({@link SeafarerMob#takeGrudge}: the same retaliation rule as
 *     being hit, so nobody turns on a creative player or on the navy) and, without a target, targets him.</li>
 *     <li><b>The officer's target becomes everyone's:</b> a member without a target takes the officer's, if he would
 *     fight it himself ({@link SeafarerMob#keepsTarget}) or the officer holds a grudge against it (shared).</li>
 *     <li>While anyone has a target the squad is {@link SquadState#FIGHTING}; afterwards it re-forms
 *     ({@link Squad#combat}).</li>
 * </ul>
 * Nothing else of the hostility rules changes: who is attacked on sight stays {@code HostilityRules}'.
 */
public final class SquadCombat {

    private SquadCombat() {
    }

    static void tick(Squad squad, ServerLevel level, long now) {
        List<SeafarerMob> all = squad.mobs(level);
        for (SeafarerMob m : all) {
            int stamp = m.getLastHurtByMobTimestamp();
            Integer seen = squad.hurtSeen.put(m.getUUID(), stamp);
            if (seen == null || seen == stamp) continue; // first look (older hits don't count) or no new hit
            LivingEntity attacker = m.getLastHurtByMob();
            if (attacker != null && attacker.isAlive() && !isNavy(attacker)) alert(all, attacker);
        }
        LivingEntity target = squad.leader().getTarget();
        if (target != null && target.isAlive()) {
            for (SeafarerMob m : all) {
                if (m == squad.leader() || m.getTarget() != null && m.getTarget().isAlive()) continue;
                if (m.keepsTarget(target)) m.setTarget(target);
                else if (squad.leader().hasGrudge(target)) m.takeGrudge(target);
            }
        }
        boolean fighting = false;
        for (SeafarerMob m : all) {
            LivingEntity t = m.getTarget();
            if (t != null && t.isAlive()) {
                fighting = true;
                break;
            }
        }
        squad.combat(fighting, now);
    }

    /** Everyone of the squad takes the grudge against {@code attacker} and, if idle, targets him. */
    static void alert(List<SeafarerMob> squad, LivingEntity attacker) {
        for (SeafarerMob m : squad) {
            if (m != attacker) m.takeGrudge(attacker);
        }
    }

    private static boolean isNavy(LivingEntity e) {
        return e instanceof SeafarerMob m && m.faction() == MobFaction.NAVY;
    }
}
