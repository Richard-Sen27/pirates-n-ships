package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;

/** The wearable apparel (design.md §9, §15): hats that copy the hat of a seafarer mob, and the officer's coat. */
public final class ApparelContent {

    /** A black tricorn with a skull on the front: the soldier's hat in pirate colours. */
    public static final RegistryEntry<Item, HatItem> PIRATE_HAT = hat("pirate_hat");
    /** The pirate's red bandana with its knot and tails. */
    public static final RegistryEntry<Item, HatItem> BANDANA = hat("bandana");
    /** The navy soldier's tricorn, edged in white, with a black cockade. */
    public static final RegistryEntry<Item, HatItem> NAVY_HAT = hat("navy_hat");
    /** The navy officer's bicorne, worn athwart, edged in gold, with a gold loop and cockade. */
    public static final RegistryEntry<Item, HatItem> OFFICER_HAT = hat("officer_hat");
    /**
     * The pirate captain's hat (ART6): wide black brim edged in gold and cocked up on the left, red band, white plume.
     * Not craftable: the named captain (BOS1) wears it and drops it.
     */
    public static final RegistryEntry<Item, HatItem> CAPTAINS_HAT = hat("captains_hat");

    /** Every hat. */
    public static final List<RegistryEntry<Item, HatItem>> HATS = List.of(PIRATE_HAT, BANDANA, NAVY_HAT, OFFICER_HAT, CAPTAINS_HAT);
    /** The hats with a crafting recipe (the captain's hat is a drop). */
    public static final List<RegistryEntry<Item, HatItem>> CRAFTED_HATS = List.of(PIRATE_HAT, BANDANA, NAVY_HAT, OFFICER_HAT);

    /**
     * The coat's armour material: only its layer texture ({@code textures/models/armor/officers_coat_layer_1.png})
     * and equip sound matter; the armour points come from the config ({@link CoatItem}).
     */
    public static final RegistryEntry<ArmorMaterial, ArmorMaterial> OFFICERS_COAT_MATERIAL = Services.REGISTRY.register(
            Registries.ARMOR_MATERIAL, "officers_coat", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), m -> m.put(ArmorItem.Type.CHESTPLATE, 0)),
                    15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.BLUE_WOOL),
                    List.of(new ArmorMaterial.Layer(Constants.id("officers_coat"))), 0f, 0f));

    /** The navy officer's coat (ART6): blue, white facings, gold epaulettes and lace, red cuffs; chest slot. */
    public static final RegistryEntry<Item, CoatItem> OFFICERS_COAT = ModRegistry.item("officers_coat",
            () -> new CoatItem(OFFICERS_COAT_MATERIAL.holder(), new Item.Properties().stacksTo(1)));

    private ApparelContent() {
    }

    private static RegistryEntry<Item, HatItem> hat(String name) {
        return ModRegistry.item(name, () -> new HatItem(new Item.Properties().stacksTo(1)));
    }

    public static void init() {
    }
}
