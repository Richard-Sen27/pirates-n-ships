package com.richardsenger.piratesnships.combat.melee.weapon;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The three default swords (docs/design.md §8.1), generated into the mod's datapack. Damage in half hearts (vanilla
 * iron sword: 6), timings in ticks (vanilla sword cooldown: about 12.5), stamina out of 100.
 *
 * <ul>
 *   <li><b>Rapier</b>: fast (shortest wind-ups), long reach, strong thrust (9 damage, 3.6 reach), weak slash (4 damage,
 *       narrow 70° arc). Light: weakest guard (50%) and poise; its quick ripostes get a 1.2 multiplier.</li>
 *   <li><b>Cutlass</b>: shorter (shortest reach), heavier (longer wind-ups and recoveries, highest poise): strong
 *       slash (7 damage, wide 110° arc), weaker thrust (6.5 damage, 2.8 reach). Best guard (70%).</li>
 *   <li><b>Saber</b>: between the two in every number.</li>
 * </ul>
 */
public final class DefaultWeapons {

    public static final ResourceLocation RAPIER_ID = Constants.id("rapier");
    public static final ResourceLocation CUTLASS_ID = Constants.id("cutlass");
    public static final ResourceLocation SABER_ID = Constants.id("saber");

    public static final WeaponDefinition RAPIER = new WeaponDefinition(
            new WeaponDefinition.Slash(4, 2, 6, 4.0f, 2.8, 70.0, 0.6, 10f),
            new WeaponDefinition.Thrust(6, 3, 8, 8, 9.0f, 3.6, 0.25, 14f),
            new WeaponDefinition.Guard(0.5, 100.0, 0.25f, 8f, 1.5f),
            new WeaponDefinition.Parry(8f, 1.2),
            8.0f);

    public static final WeaponDefinition CUTLASS = new WeaponDefinition(
            new WeaponDefinition.Slash(5, 3, 7, 7.0f, 2.6, 110.0, 0.7, 14f),
            new WeaponDefinition.Thrust(8, 2, 12, 10, 6.5f, 2.8, 0.2, 16f),
            new WeaponDefinition.Guard(0.7, 120.0, 0.3f, 6f, 1.0f),
            new WeaponDefinition.Parry(10f, 1.0),
            11.0f);

    public static final WeaponDefinition SABER = new WeaponDefinition(
            new WeaponDefinition.Slash(4, 3, 6, 6.0f, 2.7, 90.0, 0.65, 12f),
            new WeaponDefinition.Thrust(7, 3, 10, 9, 7.5f, 3.2, 0.22, 15f),
            new WeaponDefinition.Guard(0.6, 110.0, 0.28f, 7f, 1.2f),
            new WeaponDefinition.Parry(9f, 1.1),
            10.0f);

    /** All defaults by id, in a stable order. */
    public static Map<ResourceLocation, WeaponDefinition> all() {
        Map<ResourceLocation, WeaponDefinition> m = new LinkedHashMap<>();
        m.put(RAPIER_ID, RAPIER);
        m.put(CUTLASS_ID, CUTLASS);
        m.put(SABER_ID, SABER);
        return m;
    }

    private DefaultWeapons() {
    }
}
