package com.richardsenger.piratesnships.combat.melee.weapon;

import com.richardsenger.piratesnships.core.data.DefinitionType;
import com.richardsenger.piratesnships.core.data.Definitions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * The {@code weapon} datapack definition type and the item lookup. A weapon definition applies to the item with the
 * same registry id ({@code pirates_n_ships:rapier} → the rapier item), so a datapack can make any item a
 * skill-based sword by adding {@code data/<item ns>/pirates_n_ships/weapon/<item path>.json}.
 */
public final class MeleeWeapons {

    /** Synced so the client can predict timings later. */
    public static final DefinitionType<WeaponDefinition> WEAPONS = DefinitionType.createSynced("weapon", WeaponDefinition.CODEC);

    private MeleeWeapons() {
    }

    /** Touches the class so the type is declared. Called from {@code MeleeModule.registerContent()}. */
    public static void init() {
    }

    /** The weapon definition of an item id, or empty. */
    public static Optional<WeaponDefinition> forItem(ResourceLocation itemId, Definitions<WeaponDefinition> defs) {
        return defs.get(itemId);
    }

    /** The weapon definition of the stack's item, or empty for an empty stack or any non-weapon item. */
    public static Optional<WeaponDefinition> forStack(ItemStack stack, Definitions<WeaponDefinition> defs) {
        if (stack.isEmpty()) return Optional.empty();
        return forItem(BuiltInRegistries.ITEM.getKey(stack.getItem()), defs);
    }
}
