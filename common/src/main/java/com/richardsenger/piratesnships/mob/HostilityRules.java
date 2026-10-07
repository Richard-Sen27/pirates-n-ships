package com.richardsenger.piratesnships.mob;

import org.jetbrains.annotations.Nullable;

/**
 * Who a humanoid mob attacks, keeps fighting or flees from (docs/design.md §9). Pure; the world adapter
 * ({@code entity.SeafarerMob#describe}) turns entities into {@link Target}s, and the law module answers whether the
 * navy wants a target ({@code LawService.navyShouldAttack}, threshold {@code law.world.navy_hostility_threshold}).
 *
 * <ul>
 *   <li>Pirates attack players ({@code mobs.pirates_hostile}) and navy mobs ({@code mobs.factions_fight}).</li>
 *   <li>Navy attacks pirates ({@code mobs.factions_fight}) and anyone the law wants ({@code mobs.navy_hostile}),
 *       never other navy.</li>
 *   <li>Sailors never attack; they flee from monsters, from pirates that are not peaceful and from anything that
 *       targets them.</li>
 *   <li>Peaceful types ({@code mobs.<type>_peaceful}) attack nobody and don't retaliate.</li>
 *   <li>Creative and spectator players are never targets.</li>
 *   <li>Retaliation: a fighter that is not peaceful keeps fighting an attacker outside its own faction for
 *       {@code mobs.grudge_ticks} after being hit, even one it would not attack on sight.</li>
 * </ul>
 */
public final class HostilityRules {

    private HostilityRules() {
    }

    /** Config switches that apply to the deciding mob. {@code peaceful} is the deciding mob's own type toggle. */
    public record Params(boolean piratesHostile, boolean navyHostile, boolean factionsFight, boolean peaceful) {
    }

    /**
     * What the deciding mob knows about another entity.
     *
     * @param faction      the other's faction if it is one of our mobs or navy-tagged, else {@code null}
     * @param player       it is a player
     * @param exempt       a creative or spectator player
     * @param wantedByNavy the law wants it ({@code LawService.navyShouldAttack}); only asked by navy mobs
     * @param monster      a vanilla monster ({@code Enemy})
     */
    public record Target(@Nullable MobFaction faction, boolean player, boolean exempt, boolean wantedByNavy, boolean monster) {
        public static Target ofPlayer(boolean exempt, boolean wanted) {
            return new Target(null, true, exempt, wanted, false);
        }

        public static Target ofMob(MobFaction faction) {
            return new Target(faction, false, false, false, false);
        }

        public static Target ofMonster() {
            return new Target(null, false, false, false, true);
        }
    }

    /** Whether {@code self} attacks {@code t} on sight. */
    public static boolean attacksOnSight(MobFaction self, Target t, Params p) {
        if (p.peaceful() || t.exempt()) return false;
        return switch (self) {
            case CIVILIAN -> false;
            case PIRATE -> t.player() ? p.piratesHostile() : t.faction() == MobFaction.NAVY && p.factionsFight();
            case NAVY -> {
                if (t.faction() == MobFaction.NAVY) yield false;
                if (t.faction() == MobFaction.PIRATE) yield p.factionsFight();
                yield p.navyHostile() && t.wantedByNavy();
            }
        };
    }

    /** Whether {@code self} fights back against an attacker described by {@code t}. */
    public static boolean retaliates(MobFaction self, Target t, Params p) {
        return self != MobFaction.CIVILIAN && !p.peaceful() && !t.exempt() && t.faction() != self;
    }

    /** Whether {@code self} keeps its current target: hostile on sight, or a grudge from being hit. */
    public static boolean keepsTarget(MobFaction self, Target t, Params p, boolean grudge) {
        return attacksOnSight(self, t, p) || grudge && retaliates(self, t, p);
    }

    /**
     * Whether a sailor ({@code self} civilian) runs from {@code t}.
     *
     * @param targetsMe          {@code t} currently targets the sailor
     * @param piratesThreatening pirates are not peaceful
     */
    public static boolean flees(MobFaction self, Target t, boolean targetsMe, boolean piratesThreatening) {
        if (self != MobFaction.CIVILIAN || t.exempt()) return false;
        return targetsMe || t.monster() || t.faction() == MobFaction.PIRATE && piratesThreatening;
    }
}
