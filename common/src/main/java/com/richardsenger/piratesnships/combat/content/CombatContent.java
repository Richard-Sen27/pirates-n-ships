package com.richardsenger.piratesnships.combat.content;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

/**
 * Basic combat items (design.md §8.1). Swords are plain vanilla swords for now; the melee system (§8.5) attaches to
 * them by id. Firearms, ammunition and the grappling hook have no behavior yet.
 */
public final class CombatContent {

    /** All swords use iron tier durability and enchantability; they differ in damage and speed only. */
    public static final Tier SWORD_TIER = Tiers.IRON;

    /** Attack damage passed to {@link SwordItem#createAttributes} (tooltip value = 1 + this + tier bonus 2). */
    public static final int RAPIER_DAMAGE = 2;
    public static final float RAPIER_SPEED = -2.0f;
    public static final int SABER_DAMAGE = 3;
    public static final float SABER_SPEED = -2.4f;
    public static final int CUTLASS_DAMAGE = 4;
    public static final float CUTLASS_SPEED = -2.8f;

    /** Fast and light: 5 damage, 2.0 attacks per second. */
    public static final RegistryEntry<Item, SwordItem> RAPIER = sword("rapier", RAPIER_DAMAGE, RAPIER_SPEED);
    /** Shorter and heavier: 7 damage, 1.2 attacks per second. */
    public static final RegistryEntry<Item, SwordItem> CUTLASS = sword("cutlass", CUTLASS_DAMAGE, CUTLASS_SPEED);
    /** Balanced (like an iron sword): 6 damage, 1.6 attacks per second. */
    public static final RegistryEntry<Item, SwordItem> SABER = sword("saber", SABER_DAMAGE, SABER_SPEED);

    public static final RegistryEntry<Item, Item> PISTOL = ModRegistry.item("pistol", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryEntry<Item, Item> MUSKET = ModRegistry.item("musket", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryEntry<Item, Item> LEAD_SHOT = ModRegistry.item("lead_shot", () -> new Item(new Item.Properties()));
    public static final RegistryEntry<Item, Item> CANNONBALL = ModRegistry.item("cannonball", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryEntry<Item, Item> GRAPPLING_HOOK = ModRegistry.item("grappling_hook", () -> new Item(new Item.Properties().stacksTo(1)));

    private CombatContent() {
    }

    private static RegistryEntry<Item, SwordItem> sword(String name, int damage, float speed) {
        return ModRegistry.item(name, () -> new SwordItem(SWORD_TIER,
                new Item.Properties().attributes(SwordItem.createAttributes(SWORD_TIER, damage, speed))));
    }

    public static void init() {
    }
}
